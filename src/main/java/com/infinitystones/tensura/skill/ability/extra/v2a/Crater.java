package com.infinitystones.tensura.skill.ability.extra.v2a;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The temporary crater of the Sky Fist. Blocks are removed without drops (no neighbour updates, block entities,
 * unbreakable blocks, fluids and protected chunks are left alone), the original states are written to the level's saved
 * data, and everything is put back after ~10 s: staged from the centre outwards, only into air/replaceable spots (what a
 * player built meanwhile is kept), after lifting anything standing in the hole. A crash or stop never loses blocks: the
 * saved data is restored when the server starts again.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class Crater implements Tasks.Task {
    static final int HOLD_TICKS = 200, STAGES = 8;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private final ServerLevel l;
    private final long batch;
    private final BlockPos center;
    private final double radius;
    private final List<Entry> entries;
    private boolean done;

    private record Entry(long pos, BlockState state) {}

    private Crater(ServerLevel l, long batch, BlockPos center, double radius, List<Entry> entries) {
        this.l = l; this.batch = batch; this.center = center; this.radius = radius; this.entries = entries;
    }

    /** Carves the bowl and schedules its restoration. Returns the number of blocks removed. */
    static int carve(ServerPlayer p, ServerLevel l, BlockPos c, double radius, int depth) {
        Perm perm = new Perm(p, l);
        List<Entry> removed = new ArrayList<>();
        int ir = (int) Math.ceil(radius);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -ir; dx <= ir; dx++) for (int dz = -ir; dz <= ir; dz++) {
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > radius) continue;
            double t = d / radius;
            int cut = Math.max(1, (int) Math.round(depth * (1 - t * t)));
            for (int dy = 2; dy >= -cut + 1; dy--) {
                m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                if (!l.hasChunkAt(m)) continue;
                BlockState s = l.getBlockState(m);
                if (s.isAir() || s.hasBlockEntity() || !s.getFluidState().isEmpty() || s.getDestroySpeed(l, m) < 0) continue;
                if (dy > 0 && s.isSolidRender(l, m)) continue;
                if (!perm.can(m)) continue;
                removed.add(new Entry(m.asLong(), s));
            }
        }
        if (removed.isEmpty()) return 0;
        long batch = Store.get(l).add(removed);
        for (Entry e : removed) l.setBlock(m.set(e.pos), Blocks.AIR.defaultBlockState(), FLAGS);
        Tasks.add(new Crater(l, batch, c, radius, removed));
        return removed.size();
    }

    @Override
    public boolean tick(int age) {
        if (age < HOLD_TICKS) {
            if (age % 10 == 0) l.sendParticles(ParticleTypes.SMOKE, center.getX() + 0.5, center.getY() + 1, center.getZ() + 0.5, 3, radius * 0.4, 0.2, radius * 0.4, 0.01);
            return true;
        }
        if (age == HOLD_TICKS) liftEntities();
        int stage = age - HOLD_TICKS;
        double lo = radius * stage / STAGES, hi = radius * (stage + 1) / STAGES;
        restore(lo, hi);
        if (stage >= STAGES - 1) { finish(); return false; }
        return true;
    }

    @Override
    public void end() {
        if (!done) { liftEntities(); restore(0, Double.MAX_VALUE); finish(); }
    }

    private void restore(double lo, double hi) {
        List<Entry> ring = new ArrayList<>();
        for (Entry e : entries) {
            BlockPos p = BlockPos.of(e.pos);
            double d = Math.sqrt((p.getX() - center.getX()) * (double) (p.getX() - center.getX()) + (p.getZ() - center.getZ()) * (double) (p.getZ() - center.getZ()));
            if (d >= lo && (d < hi || hi == Double.MAX_VALUE)) ring.add(e);
        }
        ring.sort(Comparator.comparingInt(e -> BlockPos.of(e.pos).getY()));
        boolean fx = hi != Double.MAX_VALUE;
        for (Entry e : ring) {
            BlockPos p = BlockPos.of(e.pos);
            BlockState cur = l.getBlockState(p);
            if (cur.isAir() || cur.canBeReplaced()) {
                l.setBlock(p, e.state, FLAGS);
                if (fx && l.random.nextInt(6) == 0)
                    l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, e.state), p.getX() + 0.5, p.getY() + 0.9, p.getZ() + 0.5, 2, 0.3, 0.2, 0.3, 0.04);
            }
        }
        if (fx && stageSound()) l.playSound(null, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, SoundEvents.GRAVEL_PLACE, net.minecraft.sounds.SoundSource.PLAYERS, 0.6F, 0.6F);
    }

    private int soundTick;
    private boolean stageSound() { return soundTick++ % 3 == 0; }

    /** Anything standing in the hole goes back to the original ground level before it is refilled. */
    private void liftEntities() {
        AABB box = new AABB(center).inflate(radius + 0.5, 4, radius + 0.5);
        double top = center.getY() + 1.0;
        for (Entity e : l.getEntitiesOfClass(Entity.class, box, x -> (x instanceof LivingEntity || x instanceof ItemEntity) && !x.isSpectator())) {
            if (e.getY() < top - 0.2 && !(e instanceof ServerPlayer sp && sp.isSpectator())) {
                Bits.place(e, new Vec3(e.getX(), top, e.getZ()));
            }
        }
    }

    private void finish() {
        done = true;
        Store.get(l).remove(batch);
    }

    // ------------------------------------------------------------------ persistence

    static final class Store extends SavedData {
        static final String NAME = "infinity_v2a_craters";
        static final SavedData.Factory<Store> FACTORY = new SavedData.Factory<>(Store::new, Store::load, null);

        private final Map<Long, List<Entry>> batches = new HashMap<>();
        private long next = 1;

        static Store get(ServerLevel l) { return l.getDataStorage().computeIfAbsent(FACTORY, NAME); }

        long add(List<Entry> list) {
            long id = next++;
            batches.put(id, new ArrayList<>(list));
            setDirty();
            return id;
        }

        void remove(long id) { if (batches.remove(id) != null) setDirty(); }

        static Store load(CompoundTag tag, HolderLookup.Provider reg) {
            Store s = new Store();
            s.next = Math.max(1, tag.getLong("next"));
            ListTag all = tag.getList("batches", Tag.TAG_COMPOUND);
            for (int i = 0; i < all.size(); i++) {
                CompoundTag b = all.getCompound(i);
                List<Entry> list = new ArrayList<>();
                ListTag es = b.getList("e", Tag.TAG_COMPOUND);
                for (int k = 0; k < es.size(); k++) {
                    CompoundTag e = es.getCompound(k);
                    BlockState st = NbtUtils.readBlockState(reg.lookupOrThrow(Registries.BLOCK), e.getCompound("s"));
                    list.add(new Entry(e.getLong("p"), st));
                }
                s.batches.put(b.getLong("id"), list);
            }
            return s;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
            tag.putLong("next", next);
            ListTag all = new ListTag();
            for (Map.Entry<Long, List<Entry>> b : batches.entrySet()) {
                CompoundTag bt = new CompoundTag();
                bt.putLong("id", b.getKey());
                ListTag es = new ListTag();
                for (Entry e : b.getValue()) {
                    CompoundTag et = new CompoundTag();
                    et.putLong("p", e.pos);
                    et.put("s", NbtUtils.writeBlockState(e.state));
                    es.add(et);
                }
                bt.put("e", es);
                all.add(bt);
            }
            tag.put("batches", all);
            return tag;
        }
    }

    /** Craters left over by a crash are refilled as soon as the server is up. */
    @SubscribeEvent
    public static void started(ServerStartedEvent e) {
        for (ServerLevel l : e.getServer().getAllLevels()) {
            Store s = Store.get(l);
            if (s.batches.isEmpty()) continue;
            for (List<Entry> list : new ArrayList<>(s.batches.values())) {
                list.sort(Comparator.comparingInt(x -> BlockPos.of(x.pos).getY()));
                for (Entry en : list) {
                    BlockPos p = BlockPos.of(en.pos);
                    BlockState cur = l.getBlockState(p);
                    if (cur.isAir() || cur.canBeReplaced()) l.setBlock(p, en.state, FLAGS);
                }
            }
            s.batches.clear();
            s.setDirty();
        }
    }
}
