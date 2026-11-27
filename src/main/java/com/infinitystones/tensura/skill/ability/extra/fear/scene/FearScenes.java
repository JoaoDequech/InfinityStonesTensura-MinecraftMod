package com.infinitystones.tensura.skill.ability.extra.fear.scene;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fear.FearApi;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Builds the horror scenes of the fear realm. A scene is drawn into an {@link Arena} buffer in small time slices, copied into the
 * world in batches (at most {@link #BUDGET} blocks per tick, no neighbour updates), then kept alive by one task that runs the
 * flickering lights, the teleport triggers and the scene behaviours. Nothing here logs or talks to anybody.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class FearScenes implements FearApi.Scenes {
    private static final int BUDGET = 3000;
    private static final long GEN_NANOS = 8_000_000L;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private static final List<Scene> SCENES = List.of(new Mansion(), new Hospital(), new Church(), new Fair(), new Well(), new Home(),
            new Graveyard(), new Backrooms(), new Mountain(), new Forest(), new Claims());
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Set<Long> CLEAN = new HashSet<>();
    private static final Map<UUID, Source> SOURCES = new HashMap<>();

    private record Source(ResourceKey<Level> dim, BlockPos pos) {}

    private static int budgetTick = -1, budgetUsed;

    public static void register() { FearApi.scenes(new FearScenes()); }

    /**
     * A session caught mid-clear by a shutdown is never ticked again but stayed in SESSIONS, so the next world opened in the
     * same game queued its scene behind it forever. Statics outlive the server in singleplayer: drop them all.
     */
    @SubscribeEvent
    public static void stopped(ServerStoppedEvent e) {
        SESSIONS.clear();
        CLEAN.clear();
        SOURCES.clear();
    }

    /**
     * Tells the scenes where the victim really was (used by "home" and "claims"). Only needed when the victim was already
     * moved into the fear dimension before {@link #build} runs; otherwise their current position is used.
     */
    public static void source(UUID victim, ResourceKey<Level> dim, BlockPos pos) { SOURCES.put(victim, new Source(dim, pos)); }

    // ------------------------------------------------------------------ API

    @Override
    public List<String> ids() {
        List<String> l = new ArrayList<>();
        for (Scene s : SCENES) l.add(s.id());
        return l;
    }

    @Override
    public void build(ServerLevel fear, ServerPlayer victim, String sceneId, BlockPos origin, Consumer<BlockPos> onReady) {
        Scene sc = null;
        for (Scene s : SCENES) if (s.id().equals(sceneId)) sc = s;
        if (sc == null) return;
        MinecraftServer server = fear.getServer();
        ResourceKey<Level> dim;
        BlockPos pos;
        Source src = SOURCES.get(victim.getUUID());
        if (victim.level() != fear) {
            dim = victim.level().dimension();
            pos = victim.blockPosition();
        } else if (src != null) {
            dim = src.dim();
            pos = src.pos();
        } else {
            dim = victim.getRespawnDimension();
            pos = victim.getRespawnPosition();
            if (pos == null) {
                ServerLevel ow = server.overworld();
                dim = ow.dimension();
                pos = ow.getSharedSpawnPos();
            }
        }
        Scene.Ctx ctx = new Scene.Ctx(server, fear, victim.getUUID(), origin.immutable(), dim, pos.immutable());
        Session s = new Session(sc, ctx, onReady);
        Session old = SESSIONS.get(victim.getUUID());
        if (old == null) start(s);
        else {
            old.next = s;
            old.beginClear();
        }
    }

    @Override
    public void clear(ServerLevel fear, UUID victim) {
        Session s = SESSIONS.get(victim);
        if (s == null) return;
        s.next = null;
        s.beginClear();
    }

    private static void start(Session s) {
        SESSIONS.put(s.ctx.victim(), s);
        s.task = Tasks.add(s);
    }

    private static boolean take(MinecraftServer server) {
        int t = server.getTickCount();
        if (t != budgetTick) { budgetTick = t; budgetUsed = 0; }
        return budgetUsed < BUDGET;
    }

    // ------------------------------------------------------------------ session

    private enum Phase { WIPE, GEN, FLUSH, FIXUP, LIVE, CLEAR }

    private static final class Session implements Tasks.Task {
        final Scene scene;
        final Scene.Ctx ctx;
        final Consumer<BlockPos> onReady;
        Tasks.Handle task;
        Session next;
        Phase phase;

        Arena arena;
        List<Runnable> steps;
        int step;
        int[] order;
        int flushed, readyAt;
        boolean readyFired;
        final LongArrayList placed = new LongArrayList();
        final LongArrayList fixups = new LongArrayList();
        int wipeCol, fixIdx, clearIdx, bandIdx;

        int[] until;
        boolean[] on;
        int[] burst;
        int lastTrigger = -100;
        int liveAge;
        final Random r = new Random();

        Session(Scene scene, Scene.Ctx ctx, Consumer<BlockPos> onReady) {
            this.scene = scene;
            this.ctx = ctx;
            this.onReady = onReady;
            this.phase = CLEAN.contains(ctx.origin().asLong()) ? Phase.GEN : Phase.WIPE;
            CLEAN.remove(ctx.origin().asLong());
        }

        void beginClear() {
            if (phase == Phase.CLEAR) return;
            phase = Phase.CLEAR;
            clearIdx = 0;
            bandIdx = 0;
            arena = null;
            steps = null;
        }

        @Override
        public boolean tick(int age) {
            try {
                switch (phase) {
                    case WIPE -> wipe();
                    case GEN -> gen();
                    case FLUSH -> flush();
                    case FIXUP -> fixup();
                    case LIVE -> live();
                    case CLEAR -> {
                        if (clear()) return false;
                    }
                }
            } catch (RuntimeException ex) {
                if (phase != Phase.CLEAR && phase != Phase.LIVE) {
                    // a broken scene must not take the server down: keep whatever was built and go on
                    arena = null;
                    phase = Phase.LIVE;
                    fireReady();
                }
            }
            return true;
        }

        @Override
        public void end() {
            if (SESSIONS.get(ctx.victim()) == this && phase != Phase.CLEAR) SESSIONS.remove(ctx.victim());
        }

        // -------------------------------------------------------------- phases

        private void wipe() {
            ServerLevel lv = ctx.fear();
            int oy = ctx.origin().getY();
            int cx0 = (ctx.origin().getX() - Arena.HALF) >> 4, cz0 = (ctx.origin().getZ() - Arena.HALF) >> 4;
            int cols = Arena.N / 16;
            if (wipeCol >= cols * cols) { phase = Phase.GEN; return; }
            int cx = cx0 + wipeCol % cols, cz = cz0 + wipeCol / cols;
            wipeCol++;
            LevelChunk ch = lv.getChunk(cx, cz);
            int lo = oy + scene.minY(), hi = oy + scene.maxY();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            LevelChunkSection[] secs = ch.getSections();
            for (int i = 0; i < secs.length; i++) {
                LevelChunkSection sec = secs[i];
                if (sec.hasOnlyAir()) continue;
                int y0 = lv.getSectionYFromSectionIndex(i) << 4;
                for (int ly = 0; ly < 16; ly++) {
                    int y = y0 + ly;
                    if (y < lo || y > hi || (y >= oy - 3 && y <= oy)) continue;
                    for (int lz = 0; lz < 16; lz++)
                        for (int lx = 0; lx < 16; lx++) {
                            p.set(cx * 16 + lx, y, cz * 16 + lz);
                            if (!ch.getBlockState(p).isAir()) lv.setBlock(p, Blocks.AIR.defaultBlockState(), FLAGS);
                        }
                }
            }
        }

        private void gen() {
            if (arena == null) {
                long seed = ctx.victim().getMostSignificantBits() ^ scene.id().hashCode() * 31L ^ System.nanoTime();
                arena = new Arena(scene.minY(), scene.maxY(), seed);
                steps = new ArrayList<>();
                scene.plan(arena, ctx, steps);
            }
            long t0 = System.nanoTime();
            while (step < steps.size()) {
                Runnable r = steps.get(step++);
                try {
                    r.run();
                } catch (RuntimeException ex) {
                    // one broken step only loses its own props
                }
                if (System.nanoTime() - t0 > GEN_NANOS) return;
            }
            prepareFlush();
            phase = Phase.FLUSH;
        }

        private void prepareFlush() {
            int cols = Arena.N / 16;
            double sx = arena.spawnX + Arena.HALF, sz = arena.spawnZ + Arena.HALF;
            Integer[] idx = new Integer[cols * cols];
            for (int i = 0; i < idx.length; i++) idx[i] = i;
            Arrays.sort(idx, (a, b) -> Double.compare(dist(a, sx, sz, cols), dist(b, sx, sz, cols)));
            int[] counts = new int[idx.length];
            BlockState[] d = arena.data;
            int total = 0;
            for (int ci = 0; ci < idx.length; ci++) {
                int col = idx[ci], x0 = col % cols * 16, z0 = col / cols * 16;
                int n = 0;
                for (int y = arena.minY; y <= arena.maxY; y++)
                    for (int z = z0; z < z0 + 16; z++)
                        for (int x = x0; x < x0 + 16; x++)
                            if (d[((y - arena.minY) * Arena.N + z) * Arena.N + x] != null) n++;
                counts[ci] = n;
                total += n;
            }
            order = new int[total];
            int k = 0;
            for (int ci = 0; ci < idx.length; ci++) {
                int col = idx[ci], x0 = col % cols * 16, z0 = col / cols * 16;
                for (int y = arena.minY; y <= arena.maxY; y++)
                    for (int z = z0; z < z0 + 16; z++)
                        for (int x = x0; x < x0 + 16; x++) {
                            int i = ((y - arena.minY) * Arena.N + z) * Arena.N + x;
                            if (d[i] != null) order[k++] = i;
                        }
                if (ci == 3) readyAt = k;
            }
            if (idx.length <= 3) readyAt = total;
        }

        private double dist(int col, double sx, double sz, int cols) {
            double cx = col % cols * 16 + 8, cz = col / cols * 16 + 8;
            return (cx - sx) * (cx - sx) + (cz - sz) * (cz - sz);
        }

        private void fireReady() {
            if (readyFired) return;
            readyFired = true;
            if (onReady == null) return;
            Arena a = arena;
            int sx = a == null ? 0 : a.spawnX, sy = a == null ? 1 : a.spawnY, sz = a == null ? 0 : a.spawnZ;
            onReady.accept(ctx.origin().offset(sx, sy, sz));
        }

        private void flush() {
            ServerLevel lv = ctx.fear();
            BlockPos o = ctx.origin();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            BlockState[] d = arena.data;
            while (flushed < order.length && take(ctx.server())) {
                int i = order[flushed++];
                budgetUsed++;
                BlockState s = d[i];
                int lx = i % Arena.N - Arena.HALF, rest = i / Arena.N, lz = rest % Arena.N - Arena.HALF, ly = rest / Arena.N + arena.minY;
                p.set(o.getX() + lx, o.getY() + ly, o.getZ() + lz);
                if (s.isAir()) {
                    if (!lv.getBlockState(p).isAir()) lv.setBlock(p, s, FLAGS);
                    continue;
                }
                lv.setBlock(p, s, FLAGS);
                placed.add(p.asLong());
                Block b = s.getBlock();
                if (b instanceof IronBarsBlock || b instanceof FenceBlock || b instanceof WallBlock || b instanceof StairBlock) fixups.add(p.asLong());
                if (!readyFired && flushed >= readyAt) fireReady();
            }
            if (!readyFired && flushed >= readyAt) fireReady();
            if (flushed >= order.length) {
                fireReady();
                phase = Phase.FIXUP;
            }
        }

        private void fixup() {
            ServerLevel lv = ctx.fear();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            while (fixIdx < fixups.size() && take(ctx.server())) {
                budgetUsed++;
                p.set(fixups.getLong(fixIdx++));
                BlockState cur = lv.getBlockState(p);
                BlockState upd = Block.updateFromNeighbourShapes(cur, lv, p);
                if (upd != cur && !upd.isAir()) lv.setBlock(p, upd, FLAGS);
            }
            if (fixIdx >= fixups.size()) {
                fixups.clear();
                order = null;
                int n = arena.flicks.size();
                until = new int[n];
                on = new boolean[n];
                burst = new int[n];
                for (int i = 0; i < n; i++) {
                    on[i] = arena.flicks.get(i).kind() != Arena.FLICK_BAD;
                    until[i] = 20 + r.nextInt(400);
                }
                arena.release();
                phase = Phase.LIVE;
            }
        }

        private void live() {
            liveAge++;
            ServerLevel lv = ctx.fear();
            ServerPlayer p = ctx.server().getPlayerList().getPlayer(ctx.victim());
            boolean here = p != null && p.level() == lv;
            BlockPos o = ctx.origin();
            Arena a = arena;
            if (a == null) return;
            if (here) {
                double px = p.getX() - o.getX(), py = p.getY() - o.getY(), pz = p.getZ() - o.getZ();
                for (Arena.Trigger t : a.triggers) {
                    if (px >= t.x1 && px < t.x2 + 1 && py >= t.y1 && py < t.y2 + 1.8 && pz >= t.z1 && pz < t.z2 + 1 && liveAge - lastTrigger > 12) {
                        lastTrigger = liveAge;
                        double nx = t.relative ? p.getX() + t.tx : o.getX() + t.tx, ny = t.relative ? p.getY() + t.ty : o.getY() + t.ty,
                                nz = t.relative ? p.getZ() + t.tz : o.getZ() + t.tz;
                        p.teleportTo(lv, nx, ny, nz, t.setYaw ? t.yaw : p.getYRot(), p.getXRot());
                        break;
                    }
                }
                for (Arena.Behavior b : a.behaviors) b.tick(lv, p, o, liveAge);
                flickers(lv, p, a, o);
            }
        }

        private void flickers(ServerLevel lv, ServerPlayer p, Arena a, BlockPos o) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int i = 0; i < a.flicks.size(); i++) {
                if (liveAge < until[i]) continue;
                Arena.Flick f = a.flicks.get(i);
                pos.set(o.getX() + f.x(), o.getY() + f.y(), o.getZ() + f.z());
                double dx = p.getX() - pos.getX(), dz = p.getZ() - pos.getZ();
                if (dx * dx + dz * dz > 60 * 60 || !lv.hasChunkAt(pos)) { until[i] = liveAge + 40; continue; }
                switch (f.kind()) {
                    case Arena.FLICK_BAD, Arena.FLICK_DYING -> {
                        boolean bad = f.kind() == Arena.FLICK_BAD;
                        if (burst[i] == 0) {
                            burst[i] = 5 + r.nextInt(10);
                            if (dx * dx + dz * dz < 28 * 28) lv.playSound(null, pos, SoundEvents.COPPER_BULB_TURN_ON, SoundSource.BLOCKS, 0.2F, 0.6F + r.nextFloat() * 0.5F);
                        }
                        on[i] = !on[i];
                        burst[i]--;
                        setLit(lv, pos, on[i]);
                        if (burst[i] == 0) {
                            on[i] = !bad;
                            setLit(lv, pos, on[i]);
                            until[i] = liveAge + (bad ? 80 + r.nextInt(500) : 120 + r.nextInt(700));
                        } else until[i] = liveAge + 1 + r.nextInt(4);
                    }
                    case Arena.FLICK_GUST -> {
                        if (on[i]) {
                            on[i] = false;
                            setLit(lv, pos, false);
                            until[i] = liveAge + 8 + r.nextInt(30);
                        } else {
                            on[i] = true;
                            setLit(lv, pos, true);
                            until[i] = liveAge + 200 + r.nextInt(1400);
                        }
                    }
                    default -> {
                        on[i] = !on[i];
                        BlockState cur = lv.getBlockState(pos);
                        if (cur.hasProperty(BlockStateProperties.LEVEL)) lv.setBlock(pos, cur.setValue(BlockStateProperties.LEVEL, on[i] ? f.b() : f.a()), FLAGS);
                        until[i] = liveAge + 4 + r.nextInt(40);
                    }
                }
            }
        }

        private void setLit(ServerLevel lv, BlockPos pos, boolean lit) {
            BlockState cur = lv.getBlockState(pos);
            if (cur.hasProperty(BlockStateProperties.LIT) && cur.getValue(BlockStateProperties.LIT) != lit)
                lv.setBlock(pos, cur.setValue(BlockStateProperties.LIT, lit), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }

        /** @return true when the arena is empty again and this session is finished. */
        private boolean clear() {
            ServerLevel lv = ctx.fear();
            BlockPos o = ctx.origin();
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            BlockState air = Blocks.AIR.defaultBlockState();
            while (clearIdx < placed.size() && take(ctx.server())) {
                budgetUsed++;
                p.set(placed.getLong(clearIdx++));
                int dy = p.getY() - o.getY();
                if (dy >= -3 && dy <= 0) continue;
                if (!lv.getBlockState(p).isAir()) lv.setBlock(p, air, FLAGS);
            }
            if (clearIdx < placed.size()) return false;
            // put the plain base of the dimension back under the whole arena
            int total = Arena.N * Arena.N * 4;
            while (bandIdx < total && take(ctx.server())) {
                budgetUsed++;
                int i = bandIdx++;
                int x = i % Arena.N - Arena.HALF, z = i / Arena.N % Arena.N - Arena.HALF, dy = -3 + i / (Arena.N * Arena.N);
                BlockState want = dy == -3 ? Blocks.BEDROCK.defaultBlockState() : dy == 0 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState();
                p.set(o.getX() + x, o.getY() + dy, o.getZ() + z);
                if (lv.getBlockState(p) != want) lv.setBlock(p, want, FLAGS);
            }
            if (bandIdx < total) return false;
            placed.clear();
            SESSIONS.remove(ctx.victim(), this);
            CLEAN.add(o.asLong());
            if (next != null) start(next);
            return true;
        }
    }

    private FearScenes() {}
}
