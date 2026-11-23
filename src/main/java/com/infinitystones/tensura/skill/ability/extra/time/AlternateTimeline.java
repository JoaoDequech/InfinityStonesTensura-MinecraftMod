package com.infinitystones.tensura.skill.ability.extra.time;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Displays;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Alternate Timeline (Time + Reality). Left: a ghost world opens around you (radius 8–20, Alt+scroll) for 10 s under a
 * red-and-green dome: the real blocks are remembered, and inside it everything may be destroyed — digging, TNT, creepers —
 * because when the time is over (or you use it again) every block that was broken flies back into place from the inside out
 * with its original state (only where the spot is empty: builds made meanwhile are never overwritten). To keep it
 * dupe-free, blocks broken inside drop nothing, falling blocks do not form, the items explosions throw are erased and no
 * loose item can be picked up until the end. Containers and other block entities are immune inside the zone. One zone per
 * player; works with the Reality Stone in your inventory.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class AlternateTimeline implements Ability {
    public static final AlternateTimeline INSTANCE = new AlternateTimeline();
    private static final String MODE = "time.alternate_timeline", K = "infinity.extra.timeline.";
    private static final String LOCK_TAG = "infinity_timeline_item";
    private static final int GREEN = Stone.TIME.color() & 0xFFFFFF, RED = Stone.REALITY.color() & 0xFFFFFF;
    private static final int DURATION = 200, FLIGHT = 7, MAX_DISPLAYS_PER_TICK = 8, MAX_PER_TICK = 400, RETRY = 80, LOCK = 32767;

    private static final class Zone {
        final UUID owner; final ServerLevel level; final Vec3 c; final float r; final int domeId = FxNet.newId(), domeId2 = FxNet.newId();
        long[] pos = new long[0]; BlockState[] states = new BlockState[0];
        final List<ItemEntity> items = new ArrayList<>();
        boolean active = true, collapse;
        Zone(UUID owner, ServerLevel level, Vec3 c, float r) { this.owner = owner; this.level = level; this.c = c; this.r = r; }
        boolean contains(BlockPos p) { return Vec3.atCenterOf(p).distanceToSqr(c) <= (r + 0.5) * (r + 0.5); }
    }

    private static final Map<UUID, Zone> ZONES = new HashMap<>();

    @Override public Stone stone() { return Stone.TIME; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        Zone live = ZONES.get(p.getUUID());
        if (live != null) { live.collapse = true; return true; }
        if (!Stone.REALITY.isHeldBy(p)) return Fx.fail(p, K + "need_reality");
        int r = ExtraSettings.get(p, MODE, 14, 8, 20, 1);
        ServerLevel l = p.serverLevel();
        Vec3 c = Ground.below(l, p.position(), 3).add(0, 0.5, 0);
        Zone z = new Zone(p.getUUID(), l, c, r);
        snapshot(z);
        ZONES.put(p.getUUID(), z);
        open(l, z);
        Tasks.add(new Tasks.Task() {
            int restoreStart = -1, cursor;
            final List<Integer> retry = new ArrayList<>();
            List<int[]> order;
            final List<Flying> flying = new ArrayList<>();

            @Override public boolean tick(int age) {
                if (restoreStart < 0) {
                    if (z.collapse || age >= DURATION) {
                        restoreStart = age;
                        z.active = false;
                        order = sorted(z);
                        FxNet.kill(l, z.c, z.domeId);
                        FxNet.kill(l, z.c, z.domeId2);
                        close(l, z);
                        return true;
                    }
                    ambient(l, z, age);
                    return true;
                }
                int t = age - restoreStart;
                double front = 1 + t * 1.8;
                int done = 0, shown = 0;
                while (cursor < order.size() && done < MAX_PER_TICK) {
                    int idx = order.get(cursor)[0];
                    BlockPos bp = BlockPos.of(z.pos[idx]);
                    if (Math.sqrt(Vec3.atCenterOf(bp).distanceToSqr(z.c)) > front) break;
                    cursor++; done++;
                    if (!free(l, bp)) continue;
                    Display.BlockDisplay d = null;
                    if (shown < MAX_DISPLAYS_PER_TICK && l.random.nextInt(4) == 0) {
                        Vec3 from = Vec3.atCenterOf(bp).add((l.random.nextDouble() - 0.5) * 4, 2 + l.random.nextDouble() * 3, (l.random.nextDouble() - 0.5) * 4);
                        d = Displays.block(l, z.states[idx], from, 0.9F, l.random.nextBoolean() ? GREEN : RED, FLIGHT);
                        shown++;
                    }
                    flying.add(new Flying(idx, d, t + 1));
                }
                for (Flying f : flying) if (f.d != null && t == f.at) f.d.setPos(Vec3.atCenterOf(BlockPos.of(z.pos[f.idx])));
                List<Flying> landed = new ArrayList<>();
                for (Flying f : flying) if (t >= f.at + (f.d == null ? 0 : FLIGHT)) landed.add(f);
                for (Flying f : landed) {
                    flying.remove(f);
                    Displays.remove(f.d);
                    BlockPos bp = BlockPos.of(z.pos[f.idx]);
                    if (place(l, z, f.idx)) {
                        if (l.random.nextInt(4) == 0) Fx.at(l, l.random.nextBoolean() ? Fx.dust(GREEN, 1F) : Fx.dust(RED, 1F), Vec3.atCenterOf(bp), 3, 0.4, 0);
                    } else if (l.isLoaded(bp) && free(l, bp)) retry.add(f.idx);
                }
                if (t % 4 == 0 && t > 0) {
                    FxNet.shockwave(l, z.c.add(0, 0.1, 0), (float) Math.min(z.r, front), (t / 4) % 2 == 0 ? GREEN : RED, 16);
                    Fx.sound(l, z.c, SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, 0.8F + Math.min(1F, t * 0.02F));
                }
                if (cursor >= order.size() && flying.isEmpty()) {
                    if (retry.isEmpty() || t > order.size() / MAX_PER_TICK + RETRY) return false;
                    List<Integer> again = new ArrayList<>(retry);
                    retry.clear();
                    for (int idx : again) if (!place(l, z, idx) && free(l, BlockPos.of(z.pos[idx]))) retry.add(idx);
                    return !retry.isEmpty();
                }
                return true;
            }

            @Override public void end() {
                for (Flying f : flying) Displays.remove(f.d);
                if (restoreStart < 0) { z.active = false; FxNet.kill(l, z.c, z.domeId); FxNet.kill(l, z.c, z.domeId2); }
                // anything still missing goes back at once (server stop, cut short)
                for (int i = 0; i < z.pos.length; i++) place(l, z, i);
                ZONES.remove(z.owner, z);
                unlock(z);
                Fx.at(l, ParticleTypes.END_ROD, z.c.add(0, 2, 0), 30, z.r * 0.3, 0.1);
                Fx.sound(l, z.c, SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.3F);
            }
        });
        Fx.bar(p, Stone.TIME, K + "on", z.pos.length);
        return true;
    }

    private record Flying(int idx, Display.BlockDisplay d, int at) {}

    // ------------------------------------------------------------------ world

    private static void snapshot(Zone z) {
        ServerLevel l = z.level;
        BlockPos o = BlockPos.containing(z.c);
        int r = (int) Math.ceil(z.r);
        List<Long> p = new ArrayList<>();
        List<BlockState> s = new ArrayList<>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -r; dy <= r; dy++)
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > z.r * z.r) continue;
                    m.set(o.getX() + dx, o.getY() + dy, o.getZ() + dz);
                    if (!l.isInWorldBounds(m) || !l.isLoaded(m)) continue;
                    BlockState st = l.getBlockState(m);
                    if (st.isAir() || st.hasBlockEntity() || !st.getFluidState().isEmpty() || st.getDestroySpeed(l, m) < 0) continue;
                    p.add(m.asLong());
                    s.add(st);
                }
        z.pos = new long[p.size()];
        for (int i = 0; i < z.pos.length; i++) z.pos[i] = p.get(i);
        z.states = s.toArray(new BlockState[0]);
    }

    private static List<int[]> sorted(Zone z) {
        List<int[]> list = new ArrayList<>();
        for (int i = 0; i < z.pos.length; i++) list.add(new int[]{i});
        list.sort(Comparator.<int[]>comparingDouble(a -> Vec3.atCenterOf(BlockPos.of(z.pos[a[0]])).distanceToSqr(z.c))
                .thenComparingInt(a -> BlockPos.getY(z.pos[a[0]])));
        return list;
    }

    private static boolean free(ServerLevel l, BlockPos pos) {
        if (!l.isLoaded(pos)) return false;
        BlockState cur = l.getBlockState(pos);
        return cur.isAir() || cur.canBeReplaced() || cur.getBlock() instanceof LiquidBlock;
    }

    /** Puts the remembered block back if the spot is empty and nobody stands in it. */
    private static boolean place(ServerLevel l, Zone z, int i) {
        BlockPos pos = BlockPos.of(z.pos[i]);
        BlockState st = z.states[i];
        if (st == null || !free(l, pos)) return false;
        if (l.getBlockState(pos) == st) return false;
        if (!z.active && !l.isUnobstructed(st, pos, CollisionContext.empty())) return false;
        l.setBlock(pos, st, 2 | 16);
        z.states[i] = null;
        return true;
    }

    // ------------------------------------------------------------------ fx

    private static void open(ServerLevel l, Zone z) {
        Vec3 c = z.c, g = c.add(0, 0.06, 0);
        FxNet.dome(l, z.domeId, c, z.r, GREEN, DURATION + 10);
        FxNet.dome(l, z.domeId2, c, z.r + 0.6F, RED, DURATION + 10);
        FxNet.runes(l, 0, g, z.r * 0.9F, GREEN, DURATION, 0.6F);
        FxNet.runes(l, 0, g.add(0, 0.02, 0), z.r * 0.6F, RED, DURATION, -1F);
        FxNet.runes(l, 0, g.add(0, 0.04, 0), z.r * 0.3F, 0xFFFFFF, DURATION, 1.8F);
        FxNet.clock(l, 0, g.add(0, 0.06, 0), z.r * 0.45F, GREEN, DURATION, -2F);
        FxNet.shockwave(l, g, z.r * 1.3F, GREEN, 24);
        FxNet.shockwave(l, g.add(0, 0.02, 0), z.r, RED, 30);
        FxNet.pillar(l, 0, g, 0.7F, 16, GREEN, 40);
        FxNet.flashAround(l, c, z.r + 6, 0xD8FFE8, 0.3F, 10);
        FxNet.shake(l, c, z.r + 10, 0.45F, 12);
        Fx.sound(l, c, SoundEvents.BEACON_ACTIVATE, 0.7F, 0.7F);
        Fx.sound(l, c, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.6F, 0.6F);
        Fx.sound(l, c, SoundEvents.BELL_RESONATE, 0.6F, 0.9F);
    }

    private static void ambient(ServerLevel l, Zone z, int age) {
        if (age % 4 == 0) {
            Fx.shell(l, z.c, z.r * 0.98, 6, age % 8 == 0 ? Fx.dust(GREEN, 1.3F) : Fx.dust(RED, 1.3F));
            Fx.at(l, ParticleTypes.REVERSE_PORTAL, z.c.add((l.random.nextDouble() - 0.5) * z.r, 1 + l.random.nextDouble() * 3, (l.random.nextDouble() - 0.5) * z.r), 6, 1.5, 0.05);
        }
        if (age % 20 == 0) {
            Fx.sound(l, z.c, SoundEvents.NOTE_BLOCK_HAT.value(), 0.35F, 0.8F);
            FxNet.shockwave(l, z.c.add(0, 0.1, 0), z.r, age % 40 == 0 ? GREEN : RED, 20);
        }
        if (age == DURATION - 40) Fx.sound(l, z.c, SoundEvents.BELL_BLOCK, 0.5F, 0.5F);
    }

    private static void close(ServerLevel l, Zone z) {
        FxNet.shockwave(l, z.c.add(0, 0.1, 0), z.r * 0.8F, RED, 20);
        FxNet.flashAround(l, z.c, z.r + 6, 0xD8FFE8, 0.25F, 8);
        FxNet.clock(l, 0, z.c.add(0, 0.06, 0), z.r * 0.5F, GREEN, 40, 8F);
        Fx.sound(l, z.c, SoundEvents.BEACON_DEACTIVATE, 0.7F, 0.8F);
        Fx.sound(l, z.c, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 0.5F, 1.2F);
    }

    // ------------------------------------------------------------------ dupe protection

    private static Zone zoneAt(Level level, BlockPos pos) {
        if (ZONES.isEmpty() || level.isClientSide()) return null;
        for (Zone z : ZONES.values()) if (z.active && z.level == level && z.contains(pos)) return z;
        return null;
    }

    private static void unlock(Zone z) {
        for (ItemEntity it : z.items) {
            if (it.isRemoved()) continue;
            if (it.getTags().contains(com.infinitystones.tensura.skill.ability.extra.time.ExplosionLog.DROP_TAG)) { it.discard(); continue; }
            it.removeTag(LOCK_TAG);
            it.setPickUpDelay(10);
        }
        z.items.clear();
    }

    @SubscribeEvent
    public static void drops(BlockDropsEvent e) {
        if (zoneAt(e.getLevel(), e.getPos()) == null) return;
        e.getDrops().clear();
        e.setDroppedExperience(0);
    }

    /** Containers and other block entities are not part of the ghost world: they cannot be broken inside. */
    @SubscribeEvent
    public static void breaking(BlockEvent.BreakEvent e) {
        if (e.getLevel() instanceof Level lv && zoneAt(lv, e.getPos()) != null && lv.getBlockEntity(e.getPos()) != null) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void boom(ExplosionEvent.Detonate e) {
        Level lv = e.getLevel();
        if (ZONES.isEmpty() || lv.isClientSide()) return;
        e.getAffectedBlocks().removeIf(pos -> zoneAt(lv, pos) != null && lv.getBlockEntity(pos) != null);
    }

    @SubscribeEvent
    public static void join(EntityJoinLevelEvent e) {
        if (ZONES.isEmpty() || e.getLevel().isClientSide()) return;
        if (e.getEntity() instanceof FallingBlockEntity f) {
            if (zoneAt(e.getLevel(), f.blockPosition()) != null) e.setCanceled(true);
        } else if (e.getEntity() instanceof ItemEntity it && it.getOwner() == null) {
            Zone z = zoneAt(e.getLevel(), it.blockPosition());
            if (z != null && z.items.size() < 1024) {
                it.addTag(LOCK_TAG);
                it.setPickUpDelay(LOCK);
                z.items.add(it);
            }
        }
    }
}
