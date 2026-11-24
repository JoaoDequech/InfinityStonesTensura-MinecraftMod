package com.infinitystones.tensura.skill.ability.extra;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.compat.ClaimsCompat;
import it.unimi.dsi.fastutil.longs.Long2BooleanOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Terrain destruction for powers. Having the mod is the permission: the vanilla mobGriefing rule is NOT consulted.
 * What still stops a change: Open Parties and Claims (claims of somebody outside the caster's party), spawn protection,
 * the world border, unbreakable blocks (hardness &lt; 0) and block entities. Blocks removed here never drop items.
 * Big jobs are carved over several ticks (per-tick caps) so the server keeps its tick rate.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class Terrain {
    /** Decides what a position becomes: the new state, or null to leave it alone. */
    public interface Edit {
        @Nullable BlockState apply(ServerLevel level, BlockPos pos, BlockState old, RandomSource rnd);
    }

    /** Turns anything breakable into air. */
    public static final Edit AIR = (l, p, old, r) -> Blocks.AIR.defaultBlockState();

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int GLOBAL_BUDGET = 3200;
    private static final List<Job> JOBS = new ArrayList<>();

    // ---------------------------------------------------------------- permission

    /** May the caster change blocks here? Claims, spawn protection and the world border only (no mobGriefing, no OP). */
    public static boolean may(ServerPlayer caster, ServerLevel level, BlockPos pos) {
        return level.getWorldBorder().isWithinBounds(pos)
                && !level.getServer().isUnderSpawnProtection(level, pos, caster)
                && ClaimsCompat.canModify(caster, level, pos);
    }

    /** Same without a player entity (for jobs that outlive a logout): the owner is judged by UUID. */
    static boolean may(UUID actor, @Nullable ServerPlayer online, ServerLevel level, BlockPos pos, Long2BooleanOpenHashMap claimCache) {
        if (!level.getWorldBorder().isWithinBounds(pos)) return false;
        MinecraftServer server = level.getServer();
        if (online != null ? server.isUnderSpawnProtection(level, pos, online) : underSpawn(server, level, pos)) return false;
        long key = ChunkPos.asLong(pos);
        if (claimCache.containsKey(key)) return claimCache.get(key);
        boolean ok = ClaimsCompat.canModify(actor, level, pos);
        claimCache.put(key, ok);
        return ok;
    }

    private static boolean underSpawn(MinecraftServer server, ServerLevel level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD || server.getPlayerList().getOps().isEmpty()) return false;
        int r = server.getSpawnProtectionRadius();
        if (r <= 0) return false;
        BlockPos sp = level.getSharedSpawnPos();
        return Math.max(Math.abs(pos.getX() - sp.getX()), Math.abs(pos.getZ() - sp.getZ())) <= r;
    }

    /** Solid enough to be a target: not air, not unbreakable (bedrock, barriers...), no block entity (chests, machines). */
    public static boolean breakable(ServerLevel level, BlockPos pos, BlockState s) {
        return !s.isAir() && s.getDestroySpeed(level, pos) >= 0 && !s.hasBlockEntity();
    }

    // ---------------------------------------------------------------- immediate

    /** Removes one block now (no drops). */
    public static boolean remove(ServerPlayer caster, ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (!breakable(level, pos, s) || !may(caster, level, pos)) return false;
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
        return true;
    }

    /** Immediate edits for one caster with the claim answer cached per chunk (cheap enough for thousands of blocks). */
    public static final class Session {
        private final ServerPlayer p;
        private final ServerLevel level;
        private final Long2BooleanOpenHashMap claims = new Long2BooleanOpenHashMap();
        public Session(ServerPlayer p) { this.p = p; this.level = p.serverLevel(); }

        public boolean may(BlockPos pos) { return Terrain.may(p.getUUID(), p, level, pos, claims); }

        /** Removes a breakable block (no drops) whose blast resistance is at most maxResistance. */
        public boolean remove(BlockPos pos, float maxResistance) {
            if (!level.hasChunkAt(pos)) return false;
            BlockState s = level.getBlockState(pos);
            if (!breakable(level, pos, s) || s.getBlock().getExplosionResistance() > maxResistance || !may(pos)) return false;
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
            return true;
        }
    }

    // ---------------------------------------------------------------- queued

    /**
     * Carves the packed positions over several ticks (the caller sorts them: first entries go first).
     * @param perTick block changes per tick for this job (about 400-1500; a global cap also applies)
     */
    public static Job queue(ServerLevel level, ServerPlayer actor, long[] positions, int perTick, Edit edit) {
        Job j = new Job(level, actor.getUUID(), positions, Math.max(50, perTick), edit);
        if (positions.length > 0) JOBS.add(j); else j.idx = 0;
        return j;
    }

    public static Job queue(ServerLevel level, ServerPlayer actor, List<BlockPos> positions, int perTick, Edit edit) {
        long[] a = new long[positions.size()];
        for (int i = 0; i < a.length; i++) a[i] = positions.get(i).asLong();
        return queue(level, actor, a, perTick, edit);
    }

    /** Sphere of the given radius (slightly ragged edge) carved from the centre outwards, breaking at most maxResistance. */
    public static Job blastSphere(ServerLevel level, ServerPlayer actor, Vec3 c, double radius, float maxResistance, int perTick) {
        return blastShell(level, actor, c, 0, radius, maxResistance, perTick);
    }

    /** The part of a sphere between two radii (a growing black hole eats shell after shell), nearest first. */
    public static Job blastShell(ServerLevel level, ServerPlayer actor, Vec3 c, double inner, double outer, float maxResistance, int perTick) {
        BlockPos o = BlockPos.containing(c);
        int ir = (int) Math.ceil(outer + 1);
        List<long[]> list = new ArrayList<>();
        for (int dx = -ir; dx <= ir; dx++) for (int dy = -ir; dy <= ir; dy++) for (int dz = -ir; dz <= ir; dz++) {
            double d2 = dx * dx + dy * dy + dz * dz;
            double jag = 1 + 0.12 * Math.sin(dx * 1.9 + dy * 0.7) * Math.cos(dz * 1.3 - dy * 1.1);
            double lim = outer * jag;
            if (d2 <= lim * lim && d2 >= inner * inner) list.add(new long[]{o.offset(dx, dy, dz).asLong(), (long) d2});
        }
        list.sort((a, b) -> Long.compare(a[1], b[1]));
        long[] packed = new long[list.size()];
        for (int i = 0; i < packed.length; i++) packed[i] = list.get(i)[0];
        return queue(level, actor, packed, perTick, (l, p, old, r) -> old.getBlock().getExplosionResistance() > maxResistance ? null : Blocks.AIR.defaultBlockState());
    }

    /**
     * Terrain explosion of a power: carve a sphere (no drops, claims respected) plus the usual bang. Entity damage is NOT
     * done here (the power applies its own damage through {@link Rules}).
     */
    public static void boom(ServerLevel level, ServerPlayer actor, Vec3 c, float power, float maxResistance) {
        carve(level, actor, c, power, maxResistance);
        bang(level, c, power);
    }

    /** The block part of {@link #boom}: a sphere of power * 1.15 blocks. */
    public static Job carve(ServerLevel level, ServerPlayer actor, Vec3 c, float power, float maxResistance) {
        return blastSphere(level, actor, c, power * 1.15, maxResistance, 700);
    }

    /** Particles and sound of an explosion of this power (no blocks, no damage). */
    public static void bang(ServerLevel level, Vec3 c, float power) {
        if (power >= 6) level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        else level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, Math.max(1, (int) power), power * 0.3, power * 0.3, power * 0.3, 0);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, Math.min(2F, 0.6F + power * 0.12F), 0.9F - Math.min(0.4F, power * 0.02F));
    }

    public static final class Job {
        final ServerLevel level; final UUID actor; final long[] pos; final int perTick; final Edit edit;
        final Long2BooleanOpenHashMap claims = new Long2BooleanOpenHashMap();
        int idx;
        boolean cancelled;
        /** True once every position was processed (or the job was dropped). */
        public boolean done() { return cancelled || idx >= pos.length; }
        Job(ServerLevel level, UUID actor, long[] pos, int perTick, Edit edit) {
            this.level = level; this.actor = actor; this.pos = pos; this.perTick = perTick; this.edit = edit;
        }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        if (JOBS.isEmpty()) return;
        int budget = GLOBAL_BUDGET;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (Iterator<Job> it = JOBS.iterator(); it.hasNext() && budget > 0; ) {
            Job j = it.next();
            if (j.level.getServer() != e.getServer()) { j.cancelled = true; it.remove(); continue; }
            ServerPlayer online = e.getServer().getPlayerList().getPlayer(j.actor);
            RandomSource rnd = j.level.random;
            int changes = 0, limit = Math.min(j.perTick, budget), checks = 0, maxChecks = limit * 8;
            while (j.idx < j.pos.length && changes < limit && checks < maxChecks) {
                m.set(j.pos[j.idx++]);
                checks++;
                if (!j.level.hasChunkAt(m)) continue;
                BlockState old = j.level.getBlockState(m);
                if (!breakable(j.level, m, old)) continue;
                if (!may(j.actor, online, j.level, m, j.claims)) continue;
                BlockState now = j.edit.apply(j.level, m.immutable(), old, rnd);
                if (now == null || now == old) continue;
                j.level.setBlock(m, now, FLAGS);
                changes++;
            }
            budget -= changes + checks / 8;
            if (j.idx >= j.pos.length) it.remove();
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) { for (Job j : JOBS) j.cancelled = true; JOBS.clear(); }

    /** Convenience for callers that build a sorted position list themselves. */
    public static long[] sorted(List<long[]> entries) {
        long[][] arr = entries.toArray(new long[0][]);
        Arrays.sort(arr, (a, b) -> Long.compare(a[1], b[1]));
        long[] out = new long[arr.length];
        for (int i = 0; i < out.length; i++) out[i] = arr[i][0];
        return out;
    }

    private Terrain() {}
}
