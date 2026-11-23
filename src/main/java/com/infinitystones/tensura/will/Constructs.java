package com.infinitystones.tensura.will;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneProgress;
import com.infinitystones.tensura.wheel.WheelPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Will — hard-light constructs (server, multiplayer, no OP). Design: every block you place while designing is free and
 * glows in the stone's colour; save turns it into a blueprint (the blocks dissolve). Summon rebuilds it in front of you
 * as a Create Aeronautics / Sable physics body, so Create machines on it keep working like any Aeronautics build.
 * Constructs never drop blocks (no dupes). No time limit, no block limit, no cost per summon.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class Constructs {
    private static final DustParticleOptions LIGHT = new DustParticleOptions(new Vector3f(1F, 0.2F, 0.27F), 1.1F);

    /** editIndex / editName: the saved construct being edited (-1 / "" for a new one). */
    private record Design(ResourceKey<Level> dim, LinkedHashSet<BlockPos> blocks, int editIndex, String editName) {}
    /** Summoned constructs changed: tell every client now (glow + summon / implode seals). */
    private static boolean bodiesDirty;
    /** Last construct id list each viewer was sent, so the 2-second refresh only talks when something changed. */
    private static final Map<UUID, List<String>> LAST_BODIES = new HashMap<>();
    private static final Map<UUID, Design> DESIGNS = new HashMap<>();
    private static final List<Runnable> NEXT_TICK = new ArrayList<>();

    // ---------------- entry point (validated) ----------------

    /** 0 list · 1 start design · 2 cancel design · 3 save design(name) · 4 copy aimed contraption(name) · 5 summon(i) ·
     *  6 dismiss all · 7 delete(i) · 8 rename(i,name) · 9 move up(i) · 10 dismiss summoned copies of (i). */
    public static void action(ServerPlayer p, int action, int index, String name) {
        if (!allowed(p)) { if (action != 0) p.displayClientMessage(Component.translatable("infinity.wheel.locked").withStyle(ChatFormatting.GRAY), true); sync(p); return; }
        name = clean(name);
        List<ConstructStore.Blueprint> list = ConstructStore.get(p.server).of(p.getUUID());
        switch (action) {
            case 1 -> startDesign(p);
            case 2 -> endDesign(p, false, "");
            case 3 -> endDesign(p, true, name);
            case 4 -> copyContraption(p, name);
            case 5 -> { if (index >= 0 && index < list.size()) summon(p, list.get(index)); }
            case 6 -> dismiss(p, null);
            case 7 -> { if (index >= 0 && index < list.size()) { list.remove(index); ConstructStore.get(p.server).changed(); } }
            case 8 -> { if (index >= 0 && index < list.size() && !name.isEmpty()) {
                String old = list.get(index).name();
                list.set(index, new ConstructStore.Blueprint(name, list.get(index).blocks()));
                ConstructStore.get(p.server).changed();
                // summoned copies follow the new name (so "dismiss copies" still finds them)
                for (UUID id : Bodies.constructsOf(p.serverLevel(), p.getUUID())) {
                    CompoundTag t = Bodies.userData(p.serverLevel(), id);
                    if (t != null && old.equals(t.getString("name"))) t.putString("name", name);
                }
            } }
            case 9 -> { if (index > 0 && index < list.size()) { list.add(index - 1, list.remove(index)); ConstructStore.get(p.server).changed(); } }
            case 10 -> { if (index >= 0 && index < list.size()) dismiss(p, list.get(index).name()); }
            case 11 -> { if (index >= 0 && index < list.size()) edit(p, index, list.get(index)); }
            default -> { }
        }
        sync(p);
    }

    private static boolean allowed(ServerPlayer p) {
        return Stone.REALITY.isHeldBy(p) && StoneProgress.isUnlocked(p, Stone.REALITY, "will_construct");
    }

    private static String clean(String s) {
        s = s == null ? "" : s.replaceAll("[\\p{Cntrl}]", "").trim();
        return s.length() > 32 ? s.substring(0, 32) : s;
    }

    // ---------------- design ----------------

    private static void startDesign(ServerPlayer p) {
        if (DESIGNS.containsKey(p.getUUID())) return;
        WillMode.setActive(p, false); // hands free: left / right place and break blocks while designing
        DESIGNS.put(p.getUUID(), new Design(p.level().dimension(), new LinkedHashSet<>(), -1, ""));
        p.displayClientMessage(Component.translatable("infinity.construct.design.start").withStyle(ChatFormatting.RED), true);
        WillMode.sound(p.serverLevel(), p.position(), SoundEvents.BEACON_ACTIVATE, 0.6F, 1.9F);
        WillMode.ring(p.serverLevel(), p.position().add(0, 0.1, 0), 1.6, 40);
    }

    private static void endDesign(ServerPlayer p, boolean save, String name) {
        Design d = DESIGNS.remove(p.getUUID());
        if (d == null) return;
        ServerLevel level = p.server.getLevel(d.dim());
        if (level == null) return;
        List<BlockPos> blocks = new ArrayList<>();
        for (BlockPos pos : d.blocks()) if (!level.getBlockState(pos).isAir()) blocks.add(pos);
        if (save && !blocks.isEmpty()) {
            List<ConstructStore.Blueprint> list = ConstructStore.get(p.server).of(p.getUUID());
            ConstructStore.Blueprint bp;
            if (d.editIndex() >= 0 && d.editIndex() < list.size() && list.get(d.editIndex()).name().equals(d.editName())) {
                // editing: the saved construct is replaced in place (same slot, same name unless a new one was typed)
                bp = new ConstructStore.Blueprint(name.isEmpty() ? d.editName() : name, capture(level, blocks));
                list.set(d.editIndex(), bp);
            } else {
                bp = new ConstructStore.Blueprint(name.isEmpty() ? (d.editName().isEmpty() ? "Construct " + (list.size() + 1) : d.editName()) : name, capture(level, blocks));
                list.add(bp);
            }
            ConstructStore.get(p.server).changed();
            p.displayClientMessage(Component.translatable("infinity.construct.saved", bp.name(), blocks.size()).withStyle(ChatFormatting.RED), true);
        }
        for (BlockPos pos : blocks) dissolveBlock(level, pos);
        WillMode.sound(level, p.position(), SoundEvents.BEACON_DEACTIVATE, 0.6F, 1.6F);
        PacketDistributor.sendToPlayer(p, new WheelPayloads.DesignBlocks(List.of()));
    }

    @SubscribeEvent
    public static void placed(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        Design d = DESIGNS.get(p.getUUID());
        if (d == null || p.level().dimension() != d.dim()) return;
        d.blocks().add(e.getPos().immutable());
        if (p.level() instanceof ServerLevel sl) {
            Vec3 c = Vec3.atCenterOf(e.getPos());
            sl.sendParticles(LIGHT, c.x, c.y, c.z, 10, 0.35, 0.35, 0.35, 0.01);
            WillMode.sound(sl, c, SoundEvents.AMETHYST_BLOCK_PLACE, 0.6F, 1.5F);
        }
        // hard light is free: hand the block back (survival)
        if (!p.getAbilities().instabuild) {
            Item item = e.getPlacedBlock().getBlock().asItem();
            if (item != Items.AIR) NEXT_TICK.add(() -> { if (!p.getInventory().add(new ItemStack(item))) p.drop(new ItemStack(item), false); });
        }
        PacketDistributor.sendToPlayer(p, new WheelPayloads.DesignBlocks(List.copyOf(d.blocks())));
    }

    @SubscribeEvent
    public static void broken(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        for (Map.Entry<UUID, Design> en : DESIGNS.entrySet()) {
            if (en.getValue().dim() != level.dimension() || !en.getValue().blocks().remove(e.getPos())) continue;
            e.setCanceled(true);
            dissolveBlock(level, e.getPos());
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(en.getKey());
            if (owner != null) PacketDistributor.sendToPlayer(owner, new WheelPayloads.DesignBlocks(List.copyOf(en.getValue().blocks())));
            return;
        }
    }

    /** Construct blocks are hard light: breaking them never drops anything. */
    @SubscribeEvent
    public static void drops(BlockDropsEvent e) {
        if (Bodies.constructAt(e.getLevel(), e.getPos()) != null) e.setCanceled(true);
    }

    // ---------------- capture / summon ----------------

    private static void copyContraption(ServerPlayer p, String name) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(128)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        UUID body = hit.getType() == HitResult.Type.MISS ? null : Bodies.at(level, hit.getBlockPos());
        if (body == null) { p.displayClientMessage(Component.translatable("infinity.construct.no_contraption").withStyle(ChatFormatting.GRAY), true); return; }
        List<BlockPos> blocks = Bodies.blocksOf(level, body);
        if (blocks.isEmpty()) return;
        List<ConstructStore.Blueprint> list = ConstructStore.get(p.server).of(p.getUUID());
        list.add(new ConstructStore.Blueprint(name.isEmpty() ? "Contraption " + (list.size() + 1) : name, capture(level, blocks)));
        ConstructStore.get(p.server).changed();
        Vec3 c = Bodies.center(level, body, 1F);
        if (c != null) WillMode.burst(level, c, 40);
        WillMode.sound(level, p.position(), SoundEvents.BEACON_POWER_SELECT, 0.7F, 1.4F);
        p.displayClientMessage(Component.translatable("infinity.construct.saved", list.get(list.size() - 1).name(), blocks.size()).withStyle(ChatFormatting.RED), true);
    }

    /** Blocks (with their block-entity data, so Create machines keep their setup) relative to the min corner. */
    private static ListTag capture(ServerLevel level, List<BlockPos> blocks) {
        int mx = Integer.MAX_VALUE, my = Integer.MAX_VALUE, mz = Integer.MAX_VALUE;
        for (BlockPos b : blocks) { mx = Math.min(mx, b.getX()); my = Math.min(my, b.getY()); mz = Math.min(mz, b.getZ()); }
        ListTag out = new ListTag();
        for (BlockPos b : blocks) {
            CompoundTag t = new CompoundTag();
            t.putInt("x", b.getX() - mx); t.putInt("y", b.getY() - my); t.putInt("z", b.getZ() - mz);
            t.put("s", NbtUtils.writeBlockState(level.getBlockState(b)));
            BlockEntity be = level.getBlockEntity(b);
            if (be != null) t.put("e", stripItems(be.saveWithoutMetadata(level.registryAccess())));
            out.add(t);
        }
        return out;
    }

    /** Hard light copies shapes and machine setups, never the items stored inside (no dupes). */
    private static CompoundTag stripItems(CompoundTag t) {
        for (String k : new String[]{"Items", "Item", "Inventory", "inventory", "HeldItem", "Buffer", "InputItems", "OutputItems", "RecordItem", "Book", "item"}) t.remove(k);
        return t;
    }

    private static int[] size(ConstructStore.Blueprint bp) {
        int sx = 0, sy = 0, sz = 0;
        for (var t : bp.blocks()) { CompoundTag c = (CompoundTag) t; sx = Math.max(sx, c.getInt("x")); sy = Math.max(sy, c.getInt("y")); sz = Math.max(sz, c.getInt("z")); }
        return new int[]{sx, sy, sz};
    }

    /**
     * Where a blueprint fits in front of the player: on the aim line, not inside blocks and not inside any other
     * physics body (other constructs, ships) — tries further along the aim, then sideways, then upwards.
     */
    private static BlockPos findSpot(ServerPlayer p, ConstructStore.Blueprint bp, boolean atFeet) {
        ServerLevel level = p.serverLevel();
        int[] s = size(bp);
        double reach = atFeet ? 2.5 + Math.max(s[0], s[2]) * 0.5 : 3 + Math.max(s[0], Math.max(s[1], s[2])) * 0.75;
        // editing: in front of you at your own height (within reach, any aim); summoning: where you aim
        Vec3 look = atFeet ? Vec3.directionFromRotation(0, p.getYRot()) : p.getLookAngle(), side = new Vec3(-look.z, 0, look.x).normalize();
        if (side.lengthSqr() < 1e-4) side = new Vec3(1, 0, 0);
        double step = Math.max(2, Math.max(s[0], s[2]) + 1.5);
        for (int along = 0; along < 4; along++)
            for (int lat : new int[]{0, 1, -1, 2, -2})
                for (int up = 0; up < 24; up += 2) {
                    Vec3 at = (atFeet ? p.position().add(0, s[1] / 2.0, 0) : p.getEyePosition()).add(look.scale(reach + along * step * 0.6)).add(side.scale(lat * step)).add(0, up, 0);
                    BlockPos origin = BlockPos.containing(at.x - s[0] / 2.0, at.y - s[1] / 2.0, at.z - s[2] / 2.0);
                    if (free(level, bp, origin)) return origin;
                }
        return null;
    }

    /** Places the blueprint's blocks (with their saved block-entity data) and returns where they went. */
    private static List<BlockPos> place(ServerLevel level, ConstructStore.Blueprint bp, BlockPos origin) {
        var registries = level.registryAccess();
        var lookup = level.holderLookup(Registries.BLOCK);
        List<BlockPos> placed = new ArrayList<>();
        for (var t : bp.blocks()) {
            CompoundTag c = (CompoundTag) t;
            BlockPos pos = origin.offset(c.getInt("x"), c.getInt("y"), c.getInt("z"));
            BlockState state = NbtUtils.readBlockState(lookup, c.getCompound("s"));
            if (state.isAir()) continue;
            level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            placed.add(pos);
        }
        for (var t : bp.blocks()) {
            CompoundTag c = (CompoundTag) t;
            if (!c.contains("e")) continue;
            BlockPos pos = origin.offset(c.getInt("x"), c.getInt("y"), c.getInt("z"));
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) continue;
            CompoundTag data = c.getCompound("e").copy();
            data.putInt("x", pos.getX()); data.putInt("y", pos.getY()); data.putInt("z", pos.getZ());
            be.loadWithComponents(data, registries);
            be.setChanged();
        }
        return placed;
    }

    private static void summon(ServerPlayer p, ConstructStore.Blueprint bp) {
        if (!Bodies.present()) { p.displayClientMessage(Component.translatable("infinity.construct.need_sable").withStyle(ChatFormatting.GRAY), true); return; }
        ServerLevel level = p.serverLevel();
        BlockPos origin = findSpot(p, bp, false);
        if (origin == null) { p.displayClientMessage(Component.translatable("infinity.construct.no_space").withStyle(ChatFormatting.GRAY), true); return; }
        List<BlockPos> placed = place(level, bp, origin);
        if (placed.isEmpty()) return;
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("infinity_construct", true);
        tag.putUUID("owner", p.getUUID());
        tag.putString("name", bp.name());
        UUID id = Bodies.assemble(level, origin, placed, tag);
        if (id == null) { for (BlockPos pos : placed) dissolveBlock(level, pos); return; }
        bodiesDirty = true;
        int[] s = size(bp);
        // form: light streams from the caster's hand into the shape
        for (int i = 0; i < Math.min(placed.size(), 120); i++) {
            Vec3 c = Vec3.atCenterOf(placed.get(level.random.nextInt(placed.size())));
            Vec3 v = c.subtract(p.getEyePosition()).normalize().scale(-0.3);
            level.sendParticles(LIGHT, c.x, c.y, c.z, 0, v.x, v.y, v.z, 1);
        }
        Vec3 mid = Vec3.atLowerCornerOf(origin).add((s[0] + 1) / 2.0, (s[1] + 1) / 2.0, (s[2] + 1) / 2.0);
        WillMode.burst(level, mid, 30);
        WillMode.sound(level, mid, SoundEvents.BEACON_ACTIVATE, 0.6F, 1.7F);
        WillMode.sound(level, mid, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 0.9F);
    }

    /** EDIT blocks: the saved construct appears in front of you as an editable design; save replaces it in place. */
    private static void edit(ServerPlayer p, int index, ConstructStore.Blueprint bp) {
        if (DESIGNS.containsKey(p.getUUID())) endDesign(p, false, "");
        BlockPos origin = findSpot(p, bp, true);
        if (origin == null) { p.displayClientMessage(Component.translatable("infinity.construct.no_space").withStyle(ChatFormatting.GRAY), true); return; }
        WillMode.setActive(p, false);
        ServerLevel level = p.serverLevel();
        List<BlockPos> placed = place(level, bp, origin);
        DESIGNS.put(p.getUUID(), new Design(level.dimension(), new LinkedHashSet<>(placed), index, bp.name()));
        for (BlockPos pos : placed) { Vec3 c = Vec3.atCenterOf(pos); level.sendParticles(LIGHT, c.x, c.y, c.z, 3, 0.3, 0.3, 0.3, 0.01); }
        p.displayClientMessage(Component.translatable("infinity.construct.editing", bp.name()).withStyle(ChatFormatting.RED), true);
        WillMode.sound(level, p.position(), SoundEvents.BEACON_ACTIVATE, 0.5F, 1.9F);
        PacketDistributor.sendToPlayer(p, new WheelPayloads.DesignBlocks(List.copyOf(placed)));
    }

    private static boolean free(ServerLevel level, ConstructStore.Blueprint bp, BlockPos origin) {
        int[] s = size(bp);
        if (Bodies.anyBodyIn(level, new net.minecraft.world.phys.AABB(origin.getX() - 0.5, origin.getY() - 0.5, origin.getZ() - 0.5,
                origin.getX() + s[0] + 1.5, origin.getY() + s[1] + 1.5, origin.getZ() + s[2] + 1.5))) return false;
        for (var t : bp.blocks()) {
            CompoundTag c = (CompoundTag) t;
            BlockPos pos = origin.offset(c.getInt("x"), c.getInt("y"), c.getInt("z"));
            if (level.isOutsideBuildHeight(pos) || !level.getBlockState(pos).canBeReplaced()) return false;
        }
        return true;
    }

    /** name == null → every construct of the player; otherwise only summoned copies of that blueprint. */
    private static void dismiss(ServerPlayer p, String name) {
        ServerLevel level = p.serverLevel();
        int n = 0;
        for (UUID id : Bodies.constructsOf(level, p.getUUID())) {
            var b = Bodies.bounds(level, id);
            if (name != null) {
                CompoundTag tag = Bodies.userData(level, id);
                if (tag == null || !name.equals(tag.getString("name"))) continue;
            }
            if (b != null) implode(level, b);
            Bodies.remove(level, id);
            n++;
        }
        if (n > 0) { bodiesDirty = true; WillMode.sound(level, p.position(), SoundEvents.BEACON_DEACTIVATE, 0.6F, 1.5F); }
    }

    /** The hard light collapses into its own centre: sparks rush inward from the shell, then a small flash. */
    private static void implode(ServerLevel level, net.minecraft.world.phys.AABB b) {
        Vec3 c = b.getCenter();
        double r = Math.max(1, Math.max(b.getXsize(), Math.max(b.getYsize(), b.getZsize())) / 2 + 0.8);
        for (int i = 0; i < 90; i++) {
            Vec3 dir = new Vec3(level.random.nextGaussian(), level.random.nextGaussian(), level.random.nextGaussian()).normalize();
            Vec3 at = c.add(dir.scale(r));
            Vec3 in = dir.scale(-1);
            // count 0 = the x/y/z offsets are a direction and the last value its speed
            level.sendParticles(i % 3 == 0 ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 0, in.x, in.y, in.z, r * 0.09);
            if (i % 2 == 0) level.sendParticles(LIGHT, at.x * 0.5 + c.x * 0.5, at.y * 0.5 + c.y * 0.5, at.z * 0.5 + c.z * 0.5, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(LIGHT, c.x, c.y, c.z, 30, 0.15, 0.15, 0.15, 0.01);
        WillMode.sound(level, c, SoundEvents.BEACON_DEACTIVATE, 0.9F, 1.3F);
        WillMode.sound(level, c, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.7F, 0.6F);
    }

    /** Remove a hard-light block without drops (containers are emptied first, so nothing spills). */
    private static void dissolveBlock(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof Container c) c.clearContent();
        level.removeBlockEntity(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
        Vec3 c = Vec3.atCenterOf(pos);
        level.sendParticles(LIGHT, c.x, c.y, c.z, 6, 0.3, 0.3, 0.3, 0.02);
    }

    // ---------------- sync / lifecycle ----------------

    public static void sync(ServerPlayer p) {
        List<ConstructStore.Blueprint> list = ConstructStore.get(p.server).of(p.getUUID());
        List<String> names = new ArrayList<>();
        List<Integer> counts = new ArrayList<>();
        List<List<Integer>> previews = new ArrayList<>();
        var lookup = p.serverLevel().holderLookup(Registries.BLOCK);
        for (ConstructStore.Blueprint b : list) {
            names.add(b.name()); counts.add(b.count());
            // compact preview for the construct wheel: x,y,z,stateId per block (first 700 blocks)
            List<Integer> flat = new ArrayList<>();
            for (int i = 0; i < Math.min(700, b.blocks().size()); i++) {
                CompoundTag c = b.blocks().getCompound(i);
                flat.add(c.getInt("x")); flat.add(c.getInt("y")); flat.add(c.getInt("z"));
                flat.add(Block.getId(NbtUtils.readBlockState(lookup, c.getCompound("s"))));
            }
            previews.add(flat);
        }
        Design d = DESIGNS.get(p.getUUID());
        PacketDistributor.sendToPlayer(p, new WheelPayloads.ConstructList(names, counts, d != null,
                d == null ? 0 : d.blocks().size(), Bodies.constructsOf(p.serverLevel(), p.getUUID()).size(), previews));
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        if (!NEXT_TICK.isEmpty()) { List<Runnable> run = new ArrayList<>(NEXT_TICK); NEXT_TICK.clear(); run.forEach(Runnable::run); }
        if (Bodies.present() && (bodiesDirty || e.getServer().getTickCount() % 40 == 0)) {
            bodiesDirty = false;
            // every summoned construct (any owner) glows for every player in its level
            for (ServerLevel level : e.getServer().getAllLevels()) {
                if (level.players().isEmpty()) continue;
                List<String> ids = new ArrayList<>();
                for (ServerPlayer owner : e.getServer().getPlayerList().getPlayers())
                    for (UUID id : Bodies.constructsOf(level, owner.getUUID())) ids.add(id.toString());
                for (ServerPlayer viewer : level.players()) {
                    List<String> sent = LAST_BODIES.get(viewer.getUUID());
                    if (ids.equals(sent)) continue;
                    LAST_BODIES.put(viewer.getUUID(), ids);
                    PacketDistributor.sendToPlayer(viewer, new WheelPayloads.ConstructBodies(ids));
                }
            }
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        LAST_BODIES.remove(e.getEntity().getUUID());
        if (e.getEntity() instanceof ServerPlayer p) endDesign(p, false, "");
    }

    @SubscribeEvent
    public static void changedDim(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) endDesign(p, false, "");
    }

    private Constructs() {}
}
