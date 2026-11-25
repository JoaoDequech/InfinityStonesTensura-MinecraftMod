package com.infinitystones.tensura.shard;

import com.infinitystones.tensura.skill.CuriosCompat;
import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.InventoryCarrier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes dead mini gems (revoked, expired or unknown) from everything the server can reach: player inventories,
 * ender chests, accessory slots, open menus, loaded block entities, entities, ground items, and from inside other items
 * (container, bundle, custom data and any modded component that serialises to NBT).
 */
public final class ShardSweep {
    private static final String ITEM_ID = "infinity:mini_joia";
    private static final String COMPONENT_ID = "infinity:shard";
    static final int KEEP = 0, MODIFIED = 1, DEAD = 2;

    /** Where a kill happened (for the shatter effect) and whether nested stacks are searched. */
    static final class Ctx {
        final ServerLevel level;
        final Vec3 pos;
        final HolderLookup.Provider regs;
        final boolean deep;
        int killed, fxLeft = 3;

        Ctx(ServerLevel level, Vec3 pos, boolean deep) {
            this.level = level; this.pos = pos; this.deep = deep; this.regs = level.registryAccess();
        }

        void kill(int stone) {
            killed++;
            if (fxLeft-- > 0) shatter(level, pos, stone);
        }
    }

    // ---------------------------------------------------------------- predicates

    static boolean isShard(ItemStack s) { return !s.isEmpty() && s.is(ShardRegistries.MINI_JOIA.get()); }

    static boolean dead(ItemStack s) {
        if (!isShard(s)) return false;
        ShardData d = s.get(ShardRegistries.SHARD.get());
        return d == null || ShardRegistry.live(d.id()) == null;
    }

    // ---------------------------------------------------------------- one stack

    /** KEEP / MODIFIED (something nested changed, in place) / DEAD (the caller must clear this very stack). */
    static int scrub(ItemStack st, Ctx ctx) {
        if (st.isEmpty()) return KEEP;
        if (isShard(st)) {
            if (!dead(st)) return KEEP;
            ShardData d = st.get(ShardRegistries.SHARD.get());
            ctx.kill(d == null ? 0 : d.stone());
            return DEAD;
        }
        return ctx.deep ? nested(st, ctx) : KEEP;
    }

    private static int nested(ItemStack st, Ctx ctx) {
        boolean changed = false;
        try {
            ItemContainerContents cc = st.get(DataComponents.CONTAINER);
            if (cc != null && cc != ItemContainerContents.EMPTY) {
                NonNullList<ItemStack> list = NonNullList.withSize(cc.getSlots(), ItemStack.EMPTY);
                cc.copyInto(list);
                boolean ch = false;
                for (int i = 0; i < list.size(); i++) {
                    int r = scrub(list.get(i), ctx);
                    if (r == DEAD) { list.set(i, ItemStack.EMPTY); ch = true; } else if (r == MODIFIED) ch = true;
                }
                if (ch) { st.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(list)); changed = true; }
            }
            BundleContents bc = st.get(DataComponents.BUNDLE_CONTENTS);
            if (bc != null && !bc.isEmpty()) {
                List<ItemStack> list = new ArrayList<>();
                for (ItemStack s : bc.items()) list.add(s.copy());
                boolean ch = false;
                for (int i = list.size() - 1; i >= 0; i--) {
                    int r = scrub(list.get(i), ctx);
                    if (r == DEAD) { list.remove(i); ch = true; } else if (r == MODIFIED) ch = true;
                }
                if (ch) { st.set(DataComponents.BUNDLE_CONTENTS, new BundleContents(list)); changed = true; }
            }
            CustomData cd = st.get(DataComponents.CUSTOM_DATA);
            if (cd != null && !cd.isEmpty()) {
                CompoundTag tag = cd.copyTag();
                if (scrubTag(tag)) { st.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); changed = true; }
            }
            List<TypedDataComponent<?>> others = new ArrayList<>();
            for (TypedDataComponent<?> t : st.getComponents()) others.add(t);
            var ops = ctx.regs.createSerializationContext(NbtOps.INSTANCE);
            for (TypedDataComponent<?> t : others) {
                DataComponentType<?> type = t.type();
                var key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
                if (key == null || "minecraft".equals(key.getNamespace()) || type.isTransient()) continue;
                @SuppressWarnings("unchecked") Codec<Object> codec = (Codec<Object>) type.codec();
                if (codec == null) continue;
                Tag enc = codec.encodeStart(ops, t.value()).result().orElse(null);
                if (enc == null || !scrubTag(enc)) continue;
                Object parsed = codec.parse(ops, enc).result().orElse(null);
                if (parsed != null) {
                    @SuppressWarnings("unchecked") DataComponentType<Object> ot = (DataComponentType<Object>) type;
                    st.set(ot, parsed);
                    changed = true;
                }
            }
        } catch (RuntimeException ignored) {
            // an odd modded component must never break the sweep
        }
        return changed ? MODIFIED : KEEP;
    }

    /** Removes dead mini gems written as item compounds anywhere inside a tag. */
    static boolean scrubTag(Tag t) {
        boolean ch = false;
        if (t instanceof CompoundTag c) {
            for (String k : new ArrayList<>(c.getAllKeys())) {
                Tag child = c.get(k);
                if (child instanceof CompoundTag cc && isShardTag(cc)) {
                    if (deadTag(cc)) { c.put(k, new CompoundTag()); ch = true; }
                } else if (child != null && scrubTag(child)) ch = true;
            }
        } else if (t instanceof ListTag l) {
            for (int i = l.size() - 1; i >= 0; i--) {
                Tag e = l.get(i);
                if (e instanceof CompoundTag cc && isShardTag(cc)) {
                    if (deadTag(cc)) { l.remove(i); ch = true; }
                } else if (scrubTag(e)) ch = true;
            }
        }
        return ch;
    }

    private static boolean isShardTag(CompoundTag c) { return ITEM_ID.equals(c.getString("id")); }

    private static boolean deadTag(CompoundTag c) {
        try {
            Tag id = c.getCompound("components").getCompound(COMPONENT_ID).get("id");
            return id == null || ShardRegistry.live(NbtUtils.loadUUID(id)) == null;
        } catch (RuntimeException e) {
            return true;
        }
    }

    // ---------------------------------------------------------------- holders

    static void scrubContainer(Container c, Ctx ctx) {
        boolean ch = false;
        for (int i = 0, n = c.getContainerSize(); i < n; i++) {
            ItemStack s = c.getItem(i);
            if (s.isEmpty()) continue;
            int r = scrub(s, ctx);
            if (r == DEAD) { c.setItem(i, ItemStack.EMPTY); ch = true; } else if (r == MODIFIED) ch = true;
        }
        if (ch) c.setChanged();
    }

    private static void scrubHandler(IItemHandler h, Ctx ctx) {
        for (int i = 0, n = h.getSlots(); i < n; i++) {
            ItemStack s = h.getStackInSlot(i);
            if (s.isEmpty()) continue;
            if (scrub(s, ctx) == DEAD) h.extractItem(i, s.getCount(), false);
        }
    }

    /** Everything a player carries or has open. Returns how many were destroyed. */
    public static int scrubPlayer(ServerPlayer p, boolean deep) {
        Ctx ctx = new Ctx(p.serverLevel(), p.position().add(0, 1.0, 0), deep);
        scrubContainer(p.getInventory(), ctx);
        scrubContainer(p.getEnderChestInventory(), ctx);
        CuriosCompat.forEach(p, s -> { if (scrub(s, ctx) == DEAD) s.setCount(0); });
        scrubMenu(p.inventoryMenu, ctx);
        if (p.containerMenu != p.inventoryMenu) scrubMenu(p.containerMenu, ctx);
        if (ctx.killed > 0) {
            p.inventoryMenu.broadcastChanges();
            p.containerMenu.broadcastChanges();
            p.displayClientMessage(Component.translatable("infinity.shard.shattered"), true);
        }
        return ctx.killed;
    }

    /** Menu slots (an open chest, shulker, backpack screen) and the stack on the cursor. */
    static void scrubMenu(AbstractContainerMenu menu, Ctx ctx) {
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty() && scrub(carried, ctx) == DEAD) menu.setCarried(ItemStack.EMPTY);
        for (Slot slot : menu.slots) {
            ItemStack s = slot.getItem();
            if (s.isEmpty()) continue;
            int r = scrub(s, ctx);
            if (r == DEAD) slot.set(ItemStack.EMPTY);
            else if (r == MODIFIED) slot.setChanged();
        }
    }

    static void scrubEntity(Entity e, boolean deep) {
        if (e.isRemoved() || e instanceof Player || !(e.level() instanceof ServerLevel level)) return;
        Ctx ctx = new Ctx(level, e.position().add(0, e.getBbHeight() * 0.5, 0), deep);
        try {
            if (e instanceof ItemEntity ie) {
                ItemStack s = ie.getItem();
                int r = scrub(s, ctx);
                if (r == DEAD) ie.discard(); else if (r == MODIFIED) ie.setItem(s);
                return;
            }
            if (e instanceof ItemFrame f) {
                ItemStack s = f.getItem();
                if (scrub(s, ctx) == DEAD) f.setItem(ItemStack.EMPTY);
            }
            if (e instanceof LivingEntity le) {
                for (EquipmentSlot es : EquipmentSlot.values()) {
                    ItemStack s = le.getItemBySlot(es);
                    if (!s.isEmpty() && scrub(s, ctx) == DEAD) le.setItemSlot(es, ItemStack.EMPTY);
                }
            }
            if (e instanceof Container c) scrubContainer(c, ctx);
            if (e instanceof InventoryCarrier ic) scrubContainer(ic.getInventory(), ctx);
            IItemHandler h = e.getCapability(Capabilities.ItemHandler.ENTITY);
            if (h != null) scrubHandler(h, ctx);
        } catch (RuntimeException ignored) {
            // modded entity with a broken inventory: skip it
        }
    }

    public static void scrubBlockEntity(BlockEntity be, boolean deep) {
        if (be.isRemoved() || !(be.getLevel() instanceof ServerLevel level)) return;
        Ctx ctx = new Ctx(level, Vec3.atCenterOf(be.getBlockPos()), deep);
        try {
            if (be instanceof Container c) scrubContainer(c, ctx);
            else {
                IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, be.getBlockPos(), null);
                if (h != null) scrubHandler(h, ctx);
            }
        } catch (RuntimeException ignored) {
            // modded block entity with a broken inventory: skip it
        }
    }

    public static void scrubChunk(LevelChunk chunk) {
        for (BlockEntity be : new ArrayList<>(chunk.getBlockEntities().values())) scrubBlockEntity(be, true);
    }

    // ---------------------------------------------------------------- the active sweep

    /** Everything online and loaded. Offline players are cleaned on their next login. */
    public static void sweepAll(MinecraftServer server) {
        for (ServerPlayer p : new ArrayList<>(server.getPlayerList().getPlayers())) scrubPlayer(p, true);
        for (ServerLevel level : server.getAllLevels()) {
            List<Entity> entities = new ArrayList<>();
            for (Entity e : level.getAllEntities()) entities.add(e);
            for (Entity e : entities) scrubEntity(e, true);
            try {
                List<LevelChunk> chunks = new ArrayList<>();
                for (ChunkHolder h : loadedHolders(level)) {
                    LevelChunk c = h.getTickingChunk();
                    if (c != null) chunks.add(c);
                }
                for (LevelChunk c : chunks) scrubChunk(c);
            } catch (RuntimeException ignored) {
                // chunk map changed under us: the chunk-watch hook cleans the rest lazily
            }
        }
    }

    private static java.lang.reflect.Method getChunks;

    @SuppressWarnings("unchecked")
    private static Iterable<ChunkHolder> loadedHolders(ServerLevel level) {
        try {
            if (getChunks == null) {
                getChunks = net.minecraft.server.level.ChunkMap.class.getDeclaredMethod("getChunks");
                getChunks.setAccessible(true);
            }
            return (Iterable<ChunkHolder>) getChunks.invoke(level.getChunkSource().chunkMap);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return List.of();
        }
    }

    // ---------------------------------------------------------------- effect

    /** Glass-like shatter in the stone's colour (server particles + sound + a flare for far viewers). */
    public static void shatter(ServerLevel level, Vec3 pos, int stone) {
        int rgb = stone >= 0 && stone < com.infinitystones.tensura.skill.Stone.values().length
                ? com.infinitystones.tensura.skill.Stone.values()[stone].color() & 0xFFFFFF : 0xFFFFFF;
        var dust = new DustParticleOptions(new Vector3f((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F), 1.0F);
        level.sendParticles(dust, pos.x, pos.y, pos.z, 26, 0.22, 0.22, 0.22, 0.12);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.x, pos.y, pos.z, 14, 0.2, 0.2, 0.2, 0.25);
        level.sendParticles(ParticleTypes.END_ROD, pos.x, pos.y, pos.z, 6, 0.15, 0.15, 0.15, 0.08);
        level.sendParticles(ParticleTypes.CRIT, pos.x, pos.y, pos.z, 10, 0.2, 0.2, 0.2, 0.3);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.PLAYERS, 0.8F, 1.5F);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.45F, 1.7F);
        com.infinitystones.tensura.skill.ability.extra.fx.FxNet.flare(level, pos, 0.9F, rgb, 10);
    }

    private ShardSweep() {}
}
