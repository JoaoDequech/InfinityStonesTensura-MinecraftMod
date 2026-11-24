package com.infinitystones.tensura.skill.ability.extra.v2b;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cosmic Scale (Space, hidden). Left on a build: the connected structure (non-natural blocks, up to 4000) swells to 2x or
 * 3x around its base: every block becomes a cube, laid down layer by layer inside a blue wave of distorted space. Right:
 * the opposite, the structure shrinks to 1/2 or 1/3 by sampling each cell. Sneak + left/right on two blocks selects an A/B
 * box instead of a connected build. Blocks owned by claims of others, bedrock and other unbreakable blocks are never
 * touched; block entities keep only their block state (a replaced container spills its items like any broken one).
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class CosmicScale implements Ability {
    public static final CosmicScale INSTANCE = new CosmicScale();
    public static final String MODE = "space.cosmic_scale";
    private static final String K = "infinity.extra.v2b.scale.";
    private static final int CAP = 4000, BOX_DIM = 28, MAX_CELLS = 120_000, BUDGET = 1100, PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int BLUE = 0x3F6BFF, CYAN = 0x7FD8FF;
    private static final double REACH = 48;

    @Override public Stone stone() { return Stone.SPACE; }

    private record Corner(ServerLevel level, BlockPos pos) {}
    private record Step(BlockPos pos, BlockState state) {}

    private static final Map<UUID, Corner> CORNER = new HashMap<>();
    private static final Set<UUID> RUNNING = new HashSet<>();

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        if (RUNNING.contains(p.getUUID())) return Fx.fail(p, K + "busy");
        ServerLevel l = p.serverLevel();
        BlockHitResult hit = Aim.block(p, REACH);
        if (hit.getType() == HitResult.Type.MISS) return Fx.fail(p, K + "aim");
        BlockPos at = hit.getBlockPos();
        Map<BlockPos, BlockState> sel;
        Corner a = CORNER.get(p.getUUID());
        if (a != null && a.level() != l) { CORNER.remove(p.getUUID()); a = null; }
        if (p.isShiftKeyDown() && a == null) {
            CORNER.put(p.getUUID(), new Corner(l, at));
            V2BNet.to(p, V2BNet.msg(V2BNet.SELECT, p.getId(), CYAN, 3600, 0, at.getX(), at.getY(), at.getZ(), at.getX() + 1, at.getY() + 1, at.getZ() + 1));
            Fx.sound(l, Vec3.atCenterOf(at), SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.2F);
            Fx.bar(p, Stone.SPACE, K + "corner_a");
            return true;
        }
        if (a != null) {
            CORNER.remove(p.getUUID());
            V2BNet.to(p, V2BNet.msg(V2BNet.SELECT_CLEAR, p.getId(), 0, 0, 0));
            sel = box(l, a.pos(), at);
            if (sel == null) return Fx.fail(p, K + "box_big", BOX_DIM);
        } else {
            BlockState st = l.getBlockState(at);
            if (!structural(l, at, st)) return Fx.fail(p, K + "natural");
            sel = flood(l, at);
            if (sel == null) return Fx.fail(p, K + "too_big", CAP);
        }
        if (sel.isEmpty()) return Fx.fail(p, K + "empty");
        int k = ExtraSettings.get(p, MODE, 2, 2, 3, 1);
        return start(p, l, sel, k, alt);
    }

    // ------------------------------------------------------------------ selection

    private static boolean natural(BlockState s) {
        return s.isAir() || s.getBlock() instanceof LiquidBlock || s.canBeReplaced()
                || s.is(BlockTags.DIRT) || s.is(BlockTags.SAND) || s.is(BlockTags.BASE_STONE_OVERWORLD) || s.is(BlockTags.BASE_STONE_NETHER)
                || s.is(BlockTags.LEAVES) || s.is(BlockTags.SNOW) || s.is(BlockTags.ICE) || s.is(BlockTags.FLOWERS)
                || s.is(Tags.Blocks.ORES) || s.is(Tags.Blocks.GRAVELS) || s.is(Tags.Blocks.STONES) || s.is(Tags.Blocks.NETHERRACKS)
                || s.is(Tags.Blocks.END_STONES) || s.is(net.minecraft.world.level.block.Blocks.CLAY);
    }

    private static boolean breakable(ServerLevel l, BlockPos pos, BlockState s) {
        return !s.isAir() && !(s.getBlock() instanceof LiquidBlock) && s.getDestroySpeed(l, pos) >= 0
                && l.isInWorldBounds(pos) && l.getWorldBorder().isWithinBounds(pos);
    }

    private static boolean structural(ServerLevel l, BlockPos pos, BlockState s) {
        return l.isLoaded(pos) && breakable(l, pos, s) && !natural(s);
    }

    /** Connected non-natural blocks (face and edge neighbours); null when more than the cap. */
    private static Map<BlockPos, BlockState> flood(ServerLevel l, BlockPos start) {
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        q.add(start);
        seen.add(start.asLong());
        while (!q.isEmpty()) {
            BlockPos c = q.poll();
            BlockState s = l.getBlockState(c);
            if (!structural(l, c, s)) continue;
            out.put(c, s);
            if (out.size() > CAP) return null;
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                if ((dx == 0 ? 1 : 0) + (dy == 0 ? 1 : 0) + (dz == 0 ? 1 : 0) < 1) continue;
                BlockPos n = c.offset(dx, dy, dz);
                if (seen.add(n.asLong())) q.add(n);
            }
        }
        return out;
    }

    private static Map<BlockPos, BlockState> box(ServerLevel l, BlockPos a, BlockPos b) {
        int x0 = Math.min(a.getX(), b.getX()), x1 = Math.max(a.getX(), b.getX());
        int y0 = Math.min(a.getY(), b.getY()), y1 = Math.max(a.getY(), b.getY());
        int z0 = Math.min(a.getZ(), b.getZ()), z1 = Math.max(a.getZ(), b.getZ());
        if (x1 - x0 >= BOX_DIM || y1 - y0 >= BOX_DIM || z1 - z0 >= BOX_DIM) return null;
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!l.isLoaded(pos)) continue;
            BlockState s = l.getBlockState(pos);
            if (!breakable(l, pos, s)) continue;
            out.put(pos, s);
            if (out.size() > CAP) return null;
        }
        return out;
    }

    // ------------------------------------------------------------------ plan

    private static BlockState cell(BlockState s, int j) {
        if (s.hasProperty(BlockStateProperties.BED_PART)) return null;
        if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) s = s.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, j % 2 == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
        if (s.hasProperty(BlockStateProperties.CHEST_TYPE)) s = s.setValue(BlockStateProperties.CHEST_TYPE, ChestType.SINGLE);
        if (s.hasProperty(BlockStateProperties.EXTENDED)) s = s.setValue(BlockStateProperties.EXTENDED, false);
        return s;
    }

    private static boolean start(ServerPlayer p, ServerLevel l, Map<BlockPos, BlockState> src, int k, boolean shrink) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos b : src.keySet()) {
            minX = Math.min(minX, b.getX()); maxX = Math.max(maxX, b.getX());
            minY = Math.min(minY, b.getY()); maxY = Math.max(maxY, b.getY());
            minZ = Math.min(minZ, b.getZ()); maxZ = Math.max(maxZ, b.getZ());
        }
        int ax = (minX + maxX) >> 1, ay = minY, az = (minZ + maxZ) >> 1;
        Map<BlockPos, BlockState> dest = new LinkedHashMap<>();
        if (!shrink) {
            if ((long) src.size() * k * k * k > MAX_CELLS) return Fx.fail(p, K + "too_big", CAP);
            for (Map.Entry<BlockPos, BlockState> en : src.entrySet()) {
                BlockPos b = en.getKey();
                int dx = b.getX() - ax, dy = b.getY() - ay, dz = b.getZ() - az;
                for (int j = 0; j < k; j++) {
                    BlockState st = cell(en.getValue(), j);
                    if (st == null) continue;
                    for (int i = 0; i < k; i++) for (int m = 0; m < k; m++)
                        dest.put(new BlockPos(ax + dx * k + i, ay + dy * k + j, az + dz * k + m), st);
                }
            }
        } else {
            Map<BlockPos, Map<BlockState, Integer>> cells = new HashMap<>();
            for (Map.Entry<BlockPos, BlockState> en : src.entrySet()) {
                BlockPos b = en.getKey();
                BlockPos c = new BlockPos(ax + Math.floorDiv(b.getX() - ax, k), ay + Math.floorDiv(b.getY() - ay, k), az + Math.floorDiv(b.getZ() - az, k));
                BlockState st = cell(en.getValue(), 0);
                if (st != null) cells.computeIfAbsent(c, x -> new HashMap<>()).merge(st, 1, Integer::sum);
            }
            for (Map.Entry<BlockPos, Map<BlockState, Integer>> en : cells.entrySet()) {
                int n = 0, bestN = 0;
                BlockState best = null;
                for (Map.Entry<BlockState, Integer> c : en.getValue().entrySet()) {
                    n += c.getValue();
                    if (c.getValue() > bestN) { bestN = c.getValue(); best = c.getKey(); }
                }
                if (best != null && n >= k * k) dest.put(en.getKey(), best);
            }
            if (dest.isEmpty()) return Fx.fail(p, K + "tiny");
        }

        List<Step> steps = new ArrayList<>();
        int dMinX = Integer.MAX_VALUE, dMinY = Integer.MAX_VALUE, dMinZ = Integer.MAX_VALUE, dMaxX = Integer.MIN_VALUE, dMaxY = Integer.MIN_VALUE, dMaxZ = Integer.MIN_VALUE;
        for (Map.Entry<BlockPos, BlockState> en : dest.entrySet()) {
            BlockPos b = en.getKey();
            dMinX = Math.min(dMinX, b.getX()); dMaxX = Math.max(dMaxX, b.getX());
            dMinY = Math.min(dMinY, b.getY()); dMaxY = Math.max(dMaxY, b.getY());
            dMinZ = Math.min(dMinZ, b.getZ()); dMaxZ = Math.max(dMaxZ, b.getZ());
            if (src.get(b) != en.getValue()) steps.add(new Step(b, en.getValue()));
        }
        for (BlockPos b : src.keySet()) if (!dest.containsKey(b)) steps.add(new Step(b, null));
        if (steps.isEmpty()) return Fx.fail(p, K + "tiny");

        int cMinX = Math.min(minX, dMinX), cMaxX = Math.max(maxX, dMaxX), cMinY = Math.min(minY, dMinY), cMaxY = Math.max(maxY, dMaxY);
        int cMinZ = Math.min(minZ, dMinZ), cMaxZ = Math.max(maxZ, dMaxZ);
        if (!permitted(p, l, src, cMinX, cMaxX, ay, cMinZ, cMaxZ)) return Fx.fail(p, K + "claimed");

        steps.sort(Comparator.<Step>comparingInt(s -> s.pos().getY()).thenComparingInt(s -> s.pos().getX()).thenComparingInt(s -> s.pos().getZ()));
        int layers = 0, lastY = Integer.MIN_VALUE;
        for (Step s : steps) {
            if (s.pos().getY() != lastY) { lastY = s.pos().getY(); layers++; }
        }
        int lpt = Math.max(1, (layers + 89) / 90);
        int ticks = Math.max((layers + lpt - 1) / lpt, steps.size() / BUDGET) + 6;

        // visuals: the blue wave of distorted space around old and new bounds, layered magic circles at the base
        Vec3 base = new Vec3((minX + maxX + 1) / 2.0, minY, (minZ + maxZ + 1) / 2.0);
        float reach = (float) Math.max(3, Math.max(cMaxX - cMinX, cMaxZ - cMinZ) * 0.75 + 2);
        int boxId = FxNet.newId();
        V2BNet.near(l, base, 160, V2BNet.msg(V2BNet.SCALE, boxId, BLUE, ticks + 30, (k << 1) | (shrink ? 1 : 0),
                minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1, dMinX, dMinY, dMinZ, dMaxX + 1, dMaxY + 1, dMaxZ + 1));
        FxNet.runes(l, FxNet.newId(), base.add(0, 0.05, 0), reach, BLUE, ticks + 24, 0.5F);
        FxNet.runes(l, FxNet.newId(), base.add(0, 0.08, 0), reach * 0.68F, CYAN, ticks + 24, -0.9F);
        FxNet.runes(l, FxNet.newId(), base.add(0, 0.11, 0), reach * 1.35F, 0x6D4CFF, ticks + 24, 0.25F);
        FxNet.shockwave(l, base.add(0, 0.1, 0), reach * 1.6F, CYAN, 26);
        FxNet.shockwave(l, base.add(0, 0.1, 0), reach, BLUE, 18);
        FxNet.flash(p, BLUE, 0.4F, 14);
        FxNet.shake(l, base, 64, 0.5F, ticks + 10);
        Fx.sound(l, base, SoundEvents.BEACON_ACTIVATE, 0.7F, 0.55F);
        Fx.sound(l, base, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 0.5F);
        Fx.sound(l, base, SoundEvents.PORTAL_TRIGGER, 0.35F, 1.4F);
        Fx.bar(p, Stone.SPACE, K + (shrink ? "shrinking" : "growing"), src.size(), k);

        UUID id = p.getUUID();
        RUNNING.add(id);
        MinecraftServer server = l.getServer();
        Tasks.add(new Run(l, id, server, steps, src, dest, base, reach, lpt));
        return true;
    }

    /** Claims of others, spawn protection and protection mods (a cancelled break) stop the whole operation. */
    private static boolean permitted(ServerPlayer p, ServerLevel l, Map<BlockPos, BlockState> src, int x0, int x1, int y, int z0, int z1) {
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++) for (int cz = z0 >> 4; cz <= z1 >> 4; cz++) {
            BlockPos probe = new BlockPos(Mth.clamp(cx * 16 + 8, x0, x1), y, Mth.clamp(cz * 16 + 8, z0, z1));
            if (V2BClaims.claimedByOther(p, l, probe) || !l.mayInteract(p, probe)) return false;
        }
        for (Map.Entry<BlockPos, BlockState> en : src.entrySet())
            if (NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(l, en.getKey(), en.getValue(), p)).isCanceled()) return false;
        return true;
    }

    // ------------------------------------------------------------------ animation

    private static final class Run implements Tasks.Task {
        final ServerLevel l; final UUID owner; final MinecraftServer server;
        final List<Step> steps; final Map<BlockPos, BlockState> src, dest;
        final Vec3 base; final float reach; final int lpt;
        int idx, skipped;
        boolean finished;

        Run(ServerLevel l, UUID owner, MinecraftServer server, List<Step> steps, Map<BlockPos, BlockState> src, Map<BlockPos, BlockState> dest, Vec3 base, float reach, int lpt) {
            this.l = l; this.owner = owner; this.server = server; this.steps = steps; this.src = src; this.dest = dest; this.base = base; this.reach = reach; this.lpt = lpt;
        }

        @Override
        public boolean tick(int age) {
            if (age < 6) {
                if (age % 2 == 0) Fx.sound(l, base, SoundEvents.AMETHYST_BLOCK_CHIME, 0.4F, 0.5F + age * 0.1F);
                return true;
            }
            int done = 0, layersDone = 0;
            while (idx < steps.size() && done < BUDGET && layersDone++ < lpt) {
                int y = steps.get(idx).pos().getY();
                int from = idx;
                while (idx < steps.size() && steps.get(idx).pos().getY() == y) { apply(steps.get(idx)); idx++; done++; }
                layerFx(from, idx);
            }
            if (idx % 3 == 0) Fx.sound(l, base, SoundEvents.AMETHYST_BLOCK_PLACE, 0.3F, 0.7F + (idx % 7) * 0.05F);
            return idx < steps.size();
        }

        private void apply(Step s) {
            BlockPos pos = s.pos();
            if (!l.isLoaded(pos) || !l.isInWorldBounds(pos)) { skipped++; return; }
            BlockState cur = l.getBlockState(pos);
            if (s.state() == null) {
                if (cur.getDestroySpeed(l, pos) >= 0 && !cur.isAir()) l.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), PLACE_FLAGS | Block.UPDATE_SUPPRESS_DROPS);
                return;
            }
            boolean free = src.containsKey(pos) || cur.isAir() || cur.canBeReplaced() || cur.getBlock() instanceof LiquidBlock
                    || (!cur.hasBlockEntity() && cur.getCollisionShape(l, pos).isEmpty() && cur.getDestroySpeed(l, pos) == 0);
            if (!free || cur.getDestroySpeed(l, pos) < 0) { skipped++; return; }
            l.setBlock(pos, s.state(), PLACE_FLAGS);
        }

        private void layerFx(int from, int to) {
            int n = to - from;
            int count = Math.min(26, 4 + n / 12);
            for (int i = 0; i < count; i++) {
                BlockPos b = steps.get(from + l.random.nextInt(n)).pos();
                Vec3 c = Vec3.atCenterOf(b);
                int r = l.random.nextInt(4);
                if (r == 0) Fx.at(l, ParticleTypes.REVERSE_PORTAL, c, 1, 0.3, 0.05);
                else if (r == 1) Fx.at(l, ParticleTypes.END_ROD, c, 1, 0.25, 0.04);
                else Fx.at(l, Fx.dust(r == 2 ? BLUE : CYAN, 1.1F), c, 2, 0.4, 0);
            }
            if (n > 40 && (from / 7) % 2 == 0) {
                Vec3 c = new Vec3(base.x, steps.get(from).pos().getY() + 0.5, base.z);
                FxNet.shockwave(l, c, reach * 0.9F, CYAN, 10);
            }
        }

        @Override
        public void end() {
            if (finished) return;
            finished = true;
            while (idx < steps.size()) { apply(steps.get(idx)); idx++; }
            RUNNING.remove(owner);
            FxNet.flare(l, base.add(0, 2, 0), reach, 0xFFFFFF, 12);
            FxNet.shockwave(l, base.add(0, 0.1, 0), reach * 2F, BLUE, 24);
            FxNet.shake(l, base, 48, 0.9F, 12);
            Fx.sound(l, base, SoundEvents.BEACON_DEACTIVATE, 0.6F, 0.6F);
            Fx.sound(l, base, SoundEvents.GENERIC_EXPLODE, 0.3F, 1.6F);
            ServerPlayer p = server.getPlayerList().getPlayer(owner);
            if (p != null) Fx.bar(p, Stone.SPACE, K + "done", skipped);
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { CORNER.remove(e.getEntity().getUUID()); }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) { CORNER.clear(); RUNNING.clear(); }

    private CosmicScale() {}
}
