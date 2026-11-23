package com.infinitystones.tensura.skill.ability.extra.time;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * In-memory log of blocks destroyed by explosions (for Restoration): the original state of every block an explosion
 * removes, per dimension, at most {@link #CAP} entries (oldest dropped), forgotten after 5 minutes / on restart.
 * Item drops spawned by an explosion are tagged so Restoration can take them back (no dupes).
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class ExplosionLog {
    public static final int CAP = 8192;
    public static final long MAX_AGE = 20L * 60 * 5;
    public static final String DROP_TAG = "infinity_expl_drop";

    public record Entry(BlockPos pos, BlockState state, long time) {}
    private record Pending(ResourceKey<Level> dim, Vec3 c, double r, long time) {}

    private static final Map<ResourceKey<Level>, ArrayDeque<Entry>> LOG = new HashMap<>();
    private static final List<Pending> PENDING = new ArrayList<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void detonate(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel l) || !e.getExplosion().interactsWithBlocks()) return;
        long now = l.getGameTime();
        ArrayDeque<Entry> q = LOG.computeIfAbsent(l.dimension(), k -> new ArrayDeque<>());
        for (BlockPos pos : e.getAffectedBlocks()) {
            BlockState s = l.getBlockState(pos);
            if (s.isAir() || s.getBlock() instanceof TntBlock || s.getBlock() instanceof BaseFireBlock || s.getBlock() instanceof LiquidBlock
                    || s.is(Blocks.BEDROCK) || s.getDestroySpeed(l, pos) < 0) continue;
            q.addLast(new Entry(pos.immutable(), s, now));
            if (q.size() > CAP) q.removeFirst();
        }
        PENDING.removeIf(p -> p.time != now);
        PENDING.add(new Pending(l.dimension(), e.getExplosion().center(), e.getExplosion().radius() * 2 + 3, now));
    }

    @SubscribeEvent
    public static void drop(EntityJoinLevelEvent e) {
        if (PENDING.isEmpty() || !(e.getEntity() instanceof ItemEntity it) || !(e.getLevel() instanceof ServerLevel l)) return;
        long now = l.getGameTime();
        for (Pending p : PENDING)
            if (p.time == now && p.dim == l.dimension() && it.position().distanceToSqr(p.c) <= p.r * p.r) { it.addTag(DROP_TAG); return; }
    }

    /** Removes and returns the logged blocks within radius of c (newest 5 minutes), oldest first, one per position. */
    public static List<Entry> take(ServerLevel l, Vec3 c, double radius) {
        ArrayDeque<Entry> q = LOG.get(l.dimension());
        List<Entry> out = new ArrayList<>();
        if (q == null) return out;
        long now = l.getGameTime();
        q.removeIf(en -> now - en.time > MAX_AGE);
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        for (Iterator<Entry> it = q.iterator(); it.hasNext(); ) {
            Entry en = it.next();
            if (Vec3.atCenterOf(en.pos).distanceToSqr(c) > radius * radius) continue;
            it.remove();
            if (seen.add(en.pos)) out.add(en);
        }
        return out;
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) { LOG.clear(); PENDING.clear(); }

    private ExplosionLog() {}
}
