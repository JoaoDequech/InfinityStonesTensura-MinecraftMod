package com.infinitystones.tensura.skill.ability.extra.v2b;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.compat.ClaimsCompat;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.Terrain;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cosmic Scale (Space, hidden). Left on a build: the connected structure (non-natural blocks, up to 1,000,000) swells to 2x or
 * 3x around its base: every block becomes a cube, laid down layer by layer inside a blue wave of distorted space. Right: the
 * opposite, the structure shrinks to 1/2 or 1/3 by sampling each cell. Sneak + left/right on two blocks selects an A/B box
 * instead of a connected build. Blocks owned by claims of others, bedrock and other unbreakable blocks are never touched;
 * block entities keep only their block state (a replaced container spills its items like any broken one).
 *
 * <p>Big builds: the flood fill (or box scan) and the scaling itself run over many ticks under a shared time budget. Positions
 * are packed in longs (y-major, so sorting them yields layers), block states live in a small palette, and the scaled result
 * is never materialised: each destination cell is derived on the fly from the source snapshot.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class CosmicScale implements Ability {
    public static final CosmicScale INSTANCE = new CosmicScale();
    public static final String MODE = "space.cosmic_scale";
    private static final String K = "infinity.extra.v2b.scale.";
    private static final int CAP = 1_000_000, BOX_DIM = 100, PLACE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int CELLS_PER_TICK = 6000, MAX_TICKS = 36000, SHUTDOWN_CELLS = 60_000;
    private static final long TICK_NANOS = 9_000_000L, MIN_NANOS = 1_500_000L;
    private static final int BLUE = 0x3F6BFF, CYAN = 0x7FD8FF;
    private static final double REACH = 48;
    private static final int Y_OFF = 512;

    @Override public Stone stone() { return Stone.SPACE; }

    private record Corner(ServerLevel level, BlockPos pos) {}

    private static final Map<UUID, Corner> CORNER = new HashMap<>();
    private static final Set<UUID> RUNNING = new HashSet<>();

    // ------------------------------------------------------------------ packed positions

    /** y-major packing: sorting the longs sorts by layer, then x, then z. */
    private static long key(int x, int y, int z) { return ((long) (y + Y_OFF) << 52) | ((long) (x & 0x3FFFFFF) << 26) | (z & 0x3FFFFFFL); }
    private static int kx(long k) { return (int) ((k << 12) >> 38); }
    private static int ky(long k) { return (int) (k >>> 52) - Y_OFF; }
    private static int kz(long k) { return (int) ((k << 38) >> 38); }

    // ------------------------------------------------------------------ work budget shared by every scan and run

    private static long budgetTick = -1, budgetUsed;

    private static long budget(MinecraftServer server) {
        int t = server.getTickCount();
        if (t != budgetTick) { budgetTick = t; budgetUsed = 0; }
        return Math.max(MIN_NANOS, TICK_NANOS - budgetUsed);
    }

    private static void spent(long nanos) { budgetUsed += nanos; }

    // ------------------------------------------------------------------ palette

    private static final class Pal {
        final List<BlockState> list = new ArrayList<>();
        final Object2IntOpenHashMap<BlockState> map = new Object2IntOpenHashMap<>();
        Pal() { map.defaultReturnValue(-1); }
        int id(BlockState s) {
            int i = map.getInt(s);
            if (i < 0) { i = list.size(); list.add(s); map.put(s, i); }
            return i;
        }
    }

    // ------------------------------------------------------------------ entry

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        if (RUNNING.contains(p.getUUID())) return Fx.fail(p, K + "busy");
        ServerLevel l = p.serverLevel();
        BlockHitResult hit = Aim.block(p, REACH);
        if (hit.getType() == HitResult.Type.MISS) return Fx.fail(p, K + "aim");
        BlockPos at = hit.getBlockPos();
        Corner a = CORNER.get(p.getUUID());
        if (a != null && a.level() != l) { CORNER.remove(p.getUUID()); a = null; }
        if (p.isShiftKeyDown() && a == null) {
            CORNER.put(p.getUUID(), new Corner(l, at));
            V2BNet.to(p, V2BNet.msg(V2BNet.SELECT, p.getId(), CYAN, 3600, 0, at.getX(), at.getY(), at.getZ(), at.getX() + 1, at.getY() + 1, at.getZ() + 1));
            Fx.sound(l, Vec3.atCenterOf(at), SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.2F);
            Fx.bar(p, Stone.SPACE, K + "corner_a");
            return true;
        }
        int k = ExtraSettings.get(p, MODE, 2, 2, 3, 1);
        Scan scan;
        if (a != null) {
            CORNER.remove(p.getUUID());
            V2BNet.to(p, V2BNet.msg(V2BNet.SELECT_CLEAR, p.getId(), 0, 0, 0));
            int x0 = Math.min(a.pos().getX(), at.getX()), x1 = Math.max(a.pos().getX(), at.getX());
            int y0 = Math.min(a.pos().getY(), at.getY()), y1 = Math.max(a.pos().getY(), at.getY());
            int z0 = Math.min(a.pos().getZ(), at.getZ()), z1 = Math.max(a.pos().getZ(), at.getZ());
            if (x1 - x0 >= BOX_DIM || y1 - y0 >= BOX_DIM || z1 - z0 >= BOX_DIM) return Fx.fail(p, K + "box_big", BOX_DIM);
            scan = Scan.box(p, l, k, alt, x0, y0, z0, x1, y1, z1);
        } else {
            BlockState st = l.getBlockState(at);
            if (!structural(l, at, st)) return Fx.fail(p, K + "natural");
            scan = Scan.flood(p, l, k, alt, at, st);
        }
        RUNNING.add(p.getUUID());
        Fx.bar(p, Stone.SPACE, K + "scanning", 0);
        Tasks.add(scan);
        return true;
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

    // ------------------------------------------------------------------ plan helpers

    private static BlockState cell(BlockState s, int j) {
        if (s.hasProperty(BlockStateProperties.BED_PART)) return null;
        if (s.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) s = s.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, j % 2 == 0 ? DoubleBlockHalf.LOWER : DoubleBlockHalf.UPPER);
        if (s.hasProperty(BlockStateProperties.CHEST_TYPE)) s = s.setValue(BlockStateProperties.CHEST_TYPE, ChestType.SINGLE);
        if (s.hasProperty(BlockStateProperties.EXTENDED)) s = s.setValue(BlockStateProperties.EXTENDED, false);
        return s;
    }

    // ------------------------------------------------------------------ scan (flood fill or box), batched over ticks

    private static final class Scan implements Tasks.Task {
        final ServerLevel l; final UUID owner; final MinecraftServer server; final int k; final boolean shrink;
        final Pal pal = new Pal();
        final Long2IntOpenHashMap states = new Long2IntOpenHashMap();
        final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        LongArrayFIFOQueue queue;
        int bx0, by0, bz0, bw, bh, bd;
        long boxIdx, boxTotal;
        boolean done, tooBig;

        private Scan(ServerPlayer p, ServerLevel l, int k, boolean shrink) {
            this.l = l; this.owner = p.getUUID(); this.server = l.getServer(); this.k = k; this.shrink = shrink;
            states.defaultReturnValue(-1);
        }

        static Scan flood(ServerPlayer p, ServerLevel l, int k, boolean shrink, BlockPos start, BlockState first) {
            Scan s = new Scan(p, l, k, shrink);
            s.queue = new LongArrayFIFOQueue();
            long sk = key(start.getX(), start.getY(), start.getZ());
            s.states.put(sk, s.pal.id(first));
            s.queue.enqueue(sk);
            return s;
        }

        static Scan box(ServerPlayer p, ServerLevel l, int k, boolean shrink, int x0, int y0, int z0, int x1, int y1, int z1) {
            Scan s = new Scan(p, l, k, shrink);
            s.bx0 = x0; s.by0 = y0; s.bz0 = z0; s.bw = x1 - x0 + 1; s.bh = y1 - y0 + 1; s.bd = z1 - z0 + 1;
            s.boxTotal = (long) s.bw * s.bh * s.bd;
            return s;
        }

        @Override
        public boolean tick(int age) {
            ServerPlayer p = server.getPlayerList().getPlayer(owner);
            if (p == null || !p.isAlive() || p.serverLevel() != l) { RUNNING.remove(owner); return false; }
            long t0 = System.nanoTime();
            long deadline = t0 + budget(server);
            if (queue != null) floodStep(deadline); else boxStep(deadline);
            spent(System.nanoTime() - t0);
            if (tooBig) { RUNNING.remove(owner); Fx.fail(p, K + "too_big", CAP); return false; }
            if (!done) {
                if (age % 10 == 0) Fx.bar(p, Stone.SPACE, K + "scanning", states.size());
                return true;
            }
            if (!plan(p)) RUNNING.remove(owner);
            return false;
        }

        @Override public void end() { if (!done) RUNNING.remove(owner); }

        private void floodStep(long deadline) {
            int n = 0;
            while (!queue.isEmpty()) {
                if ((++n & 31) == 0 && System.nanoTime() > deadline) return;
                long c = queue.dequeueLong();
                int cx = kx(c), cy = ky(c), cz = kz(c);
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    long nk = key(cx + dx, cy + dy, cz + dz);
                    if (states.containsKey(nk)) continue;
                    m.set(cx + dx, cy + dy, cz + dz);
                    if (!l.isLoaded(m)) continue;
                    BlockState s = l.getBlockState(m);
                    if (!structural(l, m, s)) continue;
                    states.put(nk, pal.id(s));
                    queue.enqueue(nk);
                    if (states.size() > CAP) { tooBig = true; return; }
                }
            }
            done = true;
        }

        private void boxStep(long deadline) {
            int n = 0;
            while (boxIdx < boxTotal) {
                if ((++n & 63) == 0 && System.nanoTime() > deadline) return;
                long i = boxIdx++;
                int x = bx0 + (int) (i % bw), y = by0 + (int) (i / bw % bh), z = bz0 + (int) (i / ((long) bw * bh));
                m.set(x, y, z);
                if (!l.isLoaded(m)) continue;
                BlockState s = l.getBlockState(m);
                if (breakable(l, m, s)) states.put(key(x, y, z), pal.id(s));
            }
            done = true;
        }

        /** Scan finished: pack the snapshot into layers, check claims and hand over to the run. */
        private boolean plan(ServerPlayer p) {
            if (states.isEmpty()) return Fx.fail(p, K + "empty");
            long[] sorted = states.keySet().toLongArray();
            Arrays.sort(sorted);
            int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            IntArrayList layerY = new IntArrayList(), layerStart = new IntArrayList();
            Int2IntOpenHashMap layerOf = new Int2IntOpenHashMap();
            layerOf.defaultReturnValue(-1);
            int lastY = Integer.MIN_VALUE;
            for (int i = 0; i < sorted.length; i++) {
                long q = sorted[i];
                int x = kx(q), y = ky(q), z = kz(q);
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
                if (y != lastY) { lastY = y; layerOf.put(y, layerY.size()); layerY.add(y); layerStart.add(i); }
            }
            layerStart.add(sorted.length);
            int minY = layerY.getInt(0), maxY = layerY.getInt(layerY.size() - 1);
            int ax = (minX + maxX) >> 1, ay = minY, az = (minZ + maxZ) >> 1;
            int n = sorted.length;

            int dMinX, dMaxX, dMinY, dMaxY, dMinZ, dMaxZ;
            if (!shrink) {
                dMinX = ax + (minX - ax) * k; dMaxX = ax + (maxX - ax) * k + k - 1;
                dMinY = ay; dMaxY = ay + (maxY - ay) * k + k - 1;
                dMinZ = az + (minZ - az) * k; dMaxZ = az + (maxZ - az) * k + k - 1;
            } else {
                dMinX = ax + Math.floorDiv(minX - ax, k); dMaxX = ax + Math.floorDiv(maxX - ax, k);
                dMinY = ay; dMaxY = ay + Math.floorDiv(maxY - ay, k);
                dMinZ = az + Math.floorDiv(minZ - az, k); dMaxZ = az + Math.floorDiv(maxZ - az, k);
            }
            int cMinX = Math.min(minX, dMinX), cMaxX = Math.max(maxX, dMaxX), cMinZ = Math.min(minZ, dMinZ), cMaxZ = Math.max(maxZ, dMaxZ);
            int cMaxY = Math.max(maxY, dMaxY);

            // claims of others stop the whole operation (block changes are the one thing claims guard)
            for (int cx = cMinX >> 4; cx <= cMaxX >> 4; cx++) for (int cz = cMinZ >> 4; cz <= cMaxZ >> 4; cz++) {
                BlockPos probe = new BlockPos(Mth.clamp(cx * 16 + 8, cMinX, cMaxX), ay, Mth.clamp(cz * 16 + 8, cMinZ, cMaxZ));
                if (!ClaimsCompat.canModify(p, l, probe)) return Fx.fail(p, K + "claimed");
            }

            Run run = new Run(this, p, sorted, layerY, layerStart, layerOf, ax, ay, az, cMaxY);
            if (shrink && run.noCell()) return Fx.fail(p, K + "tiny");

            long est = shrink ? n : (long) n * k * k * k;
            int layers = layerY.size();
            int ticks = (int) Math.min(MAX_TICKS, Math.max(layers, est / (CELLS_PER_TICK / 2)) + 6);

            Vec3 base = new Vec3((minX + maxX + 1) / 2.0, minY, (minZ + maxZ + 1) / 2.0);
            float reach = (float) Math.max(3, Math.max(cMaxX - cMinX, cMaxZ - cMinZ) * 0.75 + 2);
            run.base = base; run.reach = reach;
            int boxId = FxNet.newId();
            V2BNet.near(l, base, 160, V2BNet.msg(V2BNet.SCALE, boxId, BLUE, ticks + 30, (k << 1) | (shrink ? 1 : 0),
                    minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1, dMinX, dMinY, dMinZ, dMaxX + 1, dMaxY + 1, dMaxZ + 1));
            FxNet.runes(l, FxNet.newId(), base.add(0, 0.05, 0), reach, BLUE, ticks + 24, 0.5F);
            FxNet.runes(l, FxNet.newId(), base.add(0, 0.08, 0), reach * 0.68F, CYAN, ticks + 24, -0.9F);
            FxNet.runes(l, FxNet.newId(), base.add(0, 0.11, 0), reach * 1.35F, 0x6D4CFF, ticks + 24, 0.25F);
            FxNet.shockwave(l, base.add(0, 0.1, 0), reach * 1.6F, CYAN, 26);
            FxNet.shockwave(l, base.add(0, 0.1, 0), reach, BLUE, 18);
            FxNet.flash(p, BLUE, 0.4F, 14);
            FxNet.shake(l, base, 64, 0.5F, Math.min(ticks + 10, 200));
            Fx.sound(l, base, SoundEvents.BEACON_ACTIVATE, 0.7F, 0.55F);
            Fx.sound(l, base, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 0.5F);
            Fx.sound(l, base, SoundEvents.PORTAL_TRIGGER, 0.35F, 1.4F);
            Fx.bar(p, Stone.SPACE, K + (shrink ? "shrinking" : "growing"), n, k);

            Tasks.add(run);
            return true;
        }
    }

    // ------------------------------------------------------------------ animation + block changes

    private static final class Run implements Tasks.Task {
        final ServerLevel l; final UUID owner; final MinecraftServer server;
        final int k; final boolean shrink;
        final Pal pal; final Long2IntOpenHashMap states;
        final long[] sorted; final IntArrayList layerY, layerStart; final Int2IntOpenHashMap layerOf;
        final int ax, ay, az, yMax;
        final int[][] cellPal;
        final Terrain.Session session;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final int[] tallyId = new int[27], tallyN = new int[27];
        final LongOpenHashSet seen = new LongOpenHashSet();
        final long totalWork;
        Vec3 base = Vec3.ZERO; float reach = 8;

        // cursor
        int y, stage, rangeCount, rangeIdx, cur, curEnd;
        final int[] rs, re;
        long work, cells, skipped;
        boolean finished, started;
        int lastShockY = Integer.MIN_VALUE;
        final long[] fxPos = new long[24];
        int fxN;

        Run(Scan s, ServerPlayer p, long[] sorted, IntArrayList layerY, IntArrayList layerStart, Int2IntOpenHashMap layerOf, int ax, int ay, int az, int yMax) {
            this.l = s.l; this.owner = s.owner; this.server = s.server; this.k = s.k; this.shrink = s.shrink;
            this.pal = s.pal; this.states = s.states; this.sorted = sorted; this.layerY = layerY; this.layerStart = layerStart; this.layerOf = layerOf;
            this.ax = ax; this.ay = ay; this.az = az; this.yMax = Math.min(yMax, l.getMaxBuildHeight() - 1);
            this.session = new Terrain.Session(p);
            int base0 = pal.list.size();
            this.cellPal = new int[k][base0];
            for (int j = 0; j < k; j++) for (int i = 0; i < base0; i++) {
                BlockState c = cell(pal.list.get(i), j);
                cellPal[j][i] = c == null ? -1 : pal.id(c);
            }
            this.rs = new int[k]; this.re = new int[k];
            this.y = ay - 1;
            this.stage = 0;
            long n = sorted.length;
            this.totalWork = shrink ? n * 2 : n * k + n;
        }

        /** Shrinking only: true when not a single destination cell would exist (checked on a sample of the structure). */
        boolean noCell() {
            int limit = Math.min(sorted.length, 20000);
            for (int i = 0; i < limit; i++) {
                long q = sorted[i];
                int cx = ax + Math.floorDiv(kx(q) - ax, k), cy = ay + Math.floorDiv(ky(q) - ay, k), cz = az + Math.floorDiv(kz(q) - az, k);
                if (destState(cx, cy, cz) >= 0) return false;
            }
            return sorted.length <= 20000;
        }

        /** Palette id of the block that ends up at (x, y, z), or -1 for nothing. */
        int destState(int x, int y, int z) {
            if (!shrink) {
                int dy = y - ay;
                if (dy < 0) return -1;
                int s = states.get(key(ax + Math.floorDiv(x - ax, k), ay + Math.floorDiv(dy, k), az + Math.floorDiv(z - az, k)));
                return s < 0 ? -1 : cellPal[Math.floorMod(dy, k)][s];
            }
            int n = 0, kinds = 0, bx = ax + (x - ax) * k, by = ay + (y - ay) * k, bz = az + (z - az) * k;
            for (int t = 0; t < k; t++) for (int i = 0; i < k; i++) for (int j = 0; j < k; j++) {
                int s = states.get(key(bx + i, by + t, bz + j));
                if (s < 0) continue;
                int c = cellPal[0][s];
                if (c < 0) continue;
                n++;
                int f = 0;
                while (f < kinds && tallyId[f] != c) f++;
                if (f == kinds) { tallyId[kinds] = c; tallyN[kinds] = 0; kinds++; }
                tallyN[f]++;
            }
            if (n < k * k) return -1;
            int best = 0;
            for (int f = 1; f < kinds; f++) if (tallyN[f] > tallyN[best]) best = f;
            return tallyId[best];
        }

        private void range(int sy) {
            int li = layerOf.get(sy);
            if (li < 0) return;
            rs[rangeCount] = layerStart.getInt(li); re[rangeCount] = layerStart.getInt(li + 1); rangeCount++;
        }

        @Override
        public boolean tick(int age) {
            if (age < 6) {
                if (age % 2 == 0) Fx.sound(l, base, SoundEvents.AMETHYST_BLOCK_CHIME, 0.4F, 0.5F + age * 0.1F);
                return true;
            }
            long t0 = System.nanoTime();
            work(t0 + budget(server), CELLS_PER_TICK);
            spent(System.nanoTime() - t0);
            fxBurst();
            if (age % 4 == 0) Fx.sound(l, base, SoundEvents.AMETHYST_BLOCK_PLACE, 0.3F, 0.7F + (age % 7) * 0.05F);
            if (age % 20 == 0 && !finished) {
                ServerPlayer p = server.getPlayerList().getPlayer(owner);
                if (p != null) Fx.bar(p, Stone.SPACE, K + "progress", (int) Math.min(99, work * 100 / Math.max(1, totalWork)));
            }
            return !finished;
        }

        /** Runs the cursor until the deadline or the cell cap; sets finished when every layer is done. */
        private void work(long deadline, int maxCells) {
            long startCells = cells;
            int n = 0;
            while (!finished) {
                if ((++n & 15) == 0 && (System.nanoTime() > deadline || cells - startCells >= maxCells)) return;
                step();
            }
        }

        private void step() {
            if (stage == 0) { // next layer
                y++;
                if (y > yMax) { finished = true; return; }
                rangeCount = 0; rangeIdx = 0;
                if (!shrink) { if (y >= ay) range(ay + Math.floorDiv(y - ay, k)); }
                else for (int t = 0; t < k; t++) range(ay + (y - ay) * k + t);
                seen.clear();
                if (rangeCount == 0) { beginErase(); return; }
                cur = rs[0]; curEnd = re[0]; stage = 1;
                if (lastShockY == Integer.MIN_VALUE || y - lastShockY >= 6) { lastShockY = y; FxNet.shockwave(l, new Vec3(base.x, y + 0.5, base.z), reach * 0.9F, CYAN, 10); }
                return;
            }
            if (stage == 1) { // place destination cells of this layer
                if (cur >= curEnd) {
                    if (++rangeIdx >= rangeCount) { beginErase(); return; }
                    cur = rs[rangeIdx]; curEnd = re[rangeIdx];
                    return;
                }
                long q = sorted[cur++];
                work++;
                int x = kx(q), z = kz(q);
                if (!shrink) {
                    int c = cellPal[Math.floorMod(y - ay, k)][states.get(q)];
                    if (c < 0) return;
                    int bx = ax + (x - ax) * k, bz = az + (z - az) * k;
                    for (int i = 0; i < k; i++) for (int j = 0; j < k; j++) place(bx + i, y, bz + j, c);
                } else {
                    int cx = ax + Math.floorDiv(x - ax, k), cz = az + Math.floorDiv(z - az, k);
                    if (!seen.add(key(cx, 0, cz))) return;
                    int c = destState(cx, y, cz);
                    if (c >= 0) place(cx, y, cz, c);
                }
                return;
            }
            // stage 2: source blocks of this layer that nothing replaces are cleared
            if (cur >= curEnd) { stage = 0; return; }
            long q = sorted[cur++];
            work++;
            int x = kx(q), z = kz(q);
            if (destState(x, y, z) < 0) erase(x, z);
        }

        private void beginErase() {
            int li = layerOf.get(y);
            if (li < 0) { stage = 0; return; }
            cur = layerStart.getInt(li); curEnd = layerStart.getInt(li + 1);
            stage = 2;
        }

        private void place(int x, int py, int z, int palIdx) {
            pos.set(x, py, z);
            if (!l.isInWorldBounds(pos) || !l.isLoaded(pos)) { skipped++; return; }
            BlockState want = pal.list.get(palIdx), cur = l.getBlockState(pos);
            if (cur == want) return;
            boolean free = states.containsKey(key(x, py, z)) || cur.isAir() || cur.canBeReplaced() || cur.getBlock() instanceof LiquidBlock
                    || (!cur.hasBlockEntity() && cur.getCollisionShape(l, pos).isEmpty() && cur.getDestroySpeed(l, pos) == 0);
            if (!free || cur.getDestroySpeed(l, pos) < 0 || !session.may(pos)) { skipped++; return; }
            l.setBlock(pos, want, PLACE_FLAGS);
            if ((++cells & 63) == 0 && fxN < fxPos.length) fxPos[fxN++] = pos.asLong();
        }

        private void erase(int x, int z) {
            pos.set(x, y, z);
            if (!l.isInWorldBounds(pos) || !l.isLoaded(pos)) { skipped++; return; }
            BlockState cur = l.getBlockState(pos);
            if (cur.isAir() || cur.getDestroySpeed(l, pos) < 0) return;
            if (!session.may(pos)) { skipped++; return; }
            l.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), PLACE_FLAGS | Block.UPDATE_SUPPRESS_DROPS);
            cells++;
        }

        private void fxBurst() {
            for (int i = 0; i < fxN; i++) {
                Vec3 c = Vec3.atCenterOf(BlockPos.of(fxPos[i]));
                int r = l.random.nextInt(4);
                if (r == 0) Fx.at(l, ParticleTypes.REVERSE_PORTAL, c, 1, 0.3, 0.05);
                else if (r == 1) Fx.at(l, ParticleTypes.END_ROD, c, 1, 0.25, 0.04);
                else Fx.at(l, Fx.dust(r == 2 ? BLUE : CYAN, 1.1F), c, 2, 0.4, 0);
            }
            fxN = 0;
        }

        @Override
        public void end() {
            if (started) return;
            started = true;
            if (!finished) work(Long.MAX_VALUE, SHUTDOWN_CELLS); // server stopping: finish what is cheap, never freeze the shutdown
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
