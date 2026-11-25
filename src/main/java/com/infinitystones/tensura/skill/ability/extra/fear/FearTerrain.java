package com.infinitystones.tensura.skill.ability.extra.fear;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The scenery of the fear realm as a pure function of (x, z): a flat plaza in the middle of every arena (rebuilt as the
 * rotten copy of the victim's surroundings when someone is sent), and around it three sectors with warped borders:
 * dead forest, endless decayed rooms, sea of blood with lava lakes. A tall wall closes the arena at {@link #RING}.
 */
public final class FearTerrain {
    public static final int CELL = 2048, BASE = 64, PLAZA = 50, RING = 118, WALL_TOP = BASE + 52;
    public static final int FOREST = 1, ROOMS = 2, BLOOD = 3, PLAZA_ID = 0;
    private static final int BLOOD_Y = BASE - 2, ROOM_H = 6, ROOM_CELL = 9;

    // ------------------------------------------------------------------ maths

    static long mix(long a, long b, long c) {
        long h = a * 0x9E3779B97F4A7C15L ^ b * 0xC2B2AE3D27D4EB4FL ^ c * 0x165667B19E3779F9L;
        h ^= h >>> 29; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 32;
        return h;
    }

    static double rnd(long h) { return (h >>> 11) * (1.0 / (1L << 53)); }

    static double noise(double x, double z, int seed) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double fx = x - x0, fz = z - z0;
        fx = fx * fx * (3 - 2 * fx); fz = fz * fz * (3 - 2 * fz);
        double a = rnd(mix(x0, z0, seed)), b = rnd(mix(x0 + 1, z0, seed)), c = rnd(mix(x0, z0 + 1, seed)), d = rnd(mix(x0 + 1, z0 + 1, seed));
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fz;
    }

    public static int center(int v) { return Math.floorDiv(v + CELL / 2, CELL) * CELL; }

    public static double dist(int x, int z) {
        double dx = x - center(x), dz = z - center(z);
        return Math.sqrt(dx * dx + dz * dz);
    }

    public static int scene(int x, int z) {
        double dx = x - center(x), dz = z - center(z);
        if (Math.sqrt(dx * dx + dz * dz) < PLAZA + 6) return PLAZA_ID;
        double a = Math.atan2(dz, dx) + (noise(x / 70.0, z / 70.0, 7) - 0.5) * 1.4;
        int s = Math.floorMod((int) Math.floor(a / (Math.PI * 2) * 3 + 3), 3);
        return 1 + s;
    }

    static int forestHeight(int x, int z) {
        return BASE + (int) Math.round((noise(x / 30.0, z / 30.0, 1) - 0.5) * 8 + (noise(x / 9.0, z / 9.0, 2) - 0.5) * 3);
    }

    /** Rough surface height used by the generator for heightmaps and spawn queries. */
    public static int height(int x, int z) {
        double d = dist(x, z);
        if (d >= RING) return BASE;
        return switch (scene(x, z)) {
            case FOREST -> forestHeight(x, z);
            case ROOMS -> BASE + ROOM_H;
            case BLOOD -> BLOOD_Y + island(x, z);
            default -> BASE;
        };
    }

    private static int island(int x, int z) {
        double n = noise(x / 38.0, z / 38.0, 3);
        return n > 0.6 ? (int) ((n - 0.6) * 38) : 0;
    }

    private static boolean lava(int x, int z) {
        return noise(x / 26.0, z / 26.0, 4) > 0.66 && noise(x / 38.0, z / 38.0, 3) < 0.55;
    }

    // ------------------------------------------------------------------ chunk fill

    public static void fill(ChunkAccess chunk) {
        Heightmap hm = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap hw = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        boolean fallback = com.infinitystones.tensura.skill.ability.extra.fear.FearApi.scenes() == null;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = x0 + lx, z = z0 + lz;
                double d = dist(x, z);
                int top;
                if (!fallback || d >= RING + 3) top = plain(chunk, p, x, z);
                else if (d >= RING) top = wall(chunk, p, x, z);
                else switch (scene(x, z)) {
                    case FOREST -> top = forest(chunk, p, x, z);
                    case ROOMS -> top = rooms(chunk, p, x, z);
                    case BLOOD -> top = blood(chunk, p, x, z);
                    default -> top = plaza(chunk, p, x, z);
                }
                hm.update(lx, top, lz, Blocks.STONE.defaultBlockState());
                hw.update(lx, top, lz, Blocks.STONE.defaultBlockState());
            }
        }
        if (fallback) trees(chunk);
    }

    private static void set(ChunkAccess c, BlockPos.MutableBlockPos p, int x, int y, int z, BlockState s) {
        if (y < 0 || y > 255) return;
        p.set(x, y, z);
        c.setBlockState(p, s, false);
    }

    private static int plain(ChunkAccess c, BlockPos.MutableBlockPos p, int x, int z) {
        set(c, p, x, BASE - 3, z, Blocks.BEDROCK.defaultBlockState());
        for (int y = BASE - 2; y < BASE; y++) set(c, p, x, y, z, Blocks.DEEPSLATE.defaultBlockState());
        set(c, p, x, BASE, z, Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        return BASE;
    }

    private static int wall(ChunkAccess c, BlockPos.MutableBlockPos p, int x, int z) {
        plain(c, p, x, z);
        for (int y = BASE + 1; y <= WALL_TOP; y++) {
            double r = rnd(mix(x, y, z));
            BlockState s = r < 0.07 ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                    : r < 0.3 ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                    : r < 0.4 ? Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState() : Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
            set(c, p, x, y, z, s);
        }
        return WALL_TOP;
    }

    private static int plaza(ChunkAccess c, BlockPos.MutableBlockPos p, int x, int z) {
        set(c, p, x, BASE - 5, z, Blocks.BEDROCK.defaultBlockState());
        for (int y = BASE - 4; y < BASE; y++) set(c, p, x, y, z, Blocks.BLACKSTONE.defaultBlockState());
        set(c, p, x, BASE, z, rnd(mix(x, 5, z)) < 0.2 ? Blocks.SOUL_SAND.defaultBlockState() : Blocks.SOUL_SOIL.defaultBlockState());
        return BASE;
    }

    private static int forest(ChunkAccess c, BlockPos.MutableBlockPos p, int x, int z) {
        int h = forestHeight(x, z);
        set(c, p, x, 1, z, Blocks.BEDROCK.defaultBlockState());
        set(c, p, x, h - 5, z, Blocks.BEDROCK.defaultBlockState());
        for (int y = h - 4; y < h; y++) set(c, p, x, y, z, y > h - 3 ? Blocks.DIRT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState());
        double r = rnd(mix(x, 9, z));
        set(c, p, x, h, z, r < 0.45 ? Blocks.SOUL_SOIL.defaultBlockState() : r < 0.8 ? Blocks.COARSE_DIRT.defaultBlockState() : r < 0.92 ? Blocks.PODZOL.defaultBlockState() : Blocks.SOUL_SAND.defaultBlockState());
        double d = rnd(mix(x, 13, z));
        if (d < 0.05) set(c, p, x, h + 1, z, Blocks.DEAD_BUSH.defaultBlockState());
        else if (d < 0.065) set(c, p, x, h + 1, z, Blocks.COBWEB.defaultBlockState());
        else if (d < 0.0705) {
            int hh = 2 + (int) (rnd(mix(x, 14, z)) * 3);
            for (int i = 1; i <= hh; i++) set(c, p, x, h + i, z, Blocks.CRYING_OBSIDIAN.defaultBlockState());
        }
        return h + 1;
    }

    private static int rooms(ChunkAccess c, BlockPos.MutableBlockPos p, int x, int z) {
        set(c, p, x, BASE - 3, z, Blocks.BEDROCK.defaultBlockState());
        for (int y = BASE - 2; y < BASE; y++) set(c, p, x, y, z, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        double fr = rnd(mix(x, 21, z));
        set(c, p, x, BASE, z, fr < 0.5 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : fr < 0.8 ? Blocks.DARK_OAK_PLANKS.defaultBlockState() : Blocks.BROWN_WOOL.defaultBlockState());
        int cx = Math.floorDiv(x, ROOM_CELL), cz = Math.floorDiv(z, ROOM_CELL);
        int mx = Math.floorMod(x, ROOM_CELL), mz = Math.floorMod(z, ROOM_CELL);
        boolean wallX = mx == 0, wallZ = mz == 0;
        boolean wallHere = false;
        if (wallX && wallZ) wallHere = true;
        else if (wallZ) wallHere = !(mx >= 3 && mx <= 5 && rnd(mix(cx, cz, 31)) < 0.62);
        else if (wallX) wallHere = !(mz >= 3 && mz <= 5 && rnd(mix(cx, cz, 32)) < 0.62);
        if (wallHere) {
            for (int y = BASE + 1; y < BASE + ROOM_H; y++) {
                double r = rnd(mix(x, y, z));
                BlockState s = r < 0.08 ? Blocks.MOSS_BLOCK.defaultBlockState()
                        : r < 0.3 ? Blocks.BROWN_WOOL.defaultBlockState()
                        : r < 0.5 ? Blocks.STRIPPED_DARK_OAK_WOOD.defaultBlockState() : Blocks.SPRUCE_PLANKS.defaultBlockState();
                if (wallX && wallZ) s = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState();
                set(c, p, x, y, z, s);
            }
        } else if (mx == ROOM_CELL / 2 && mz == ROOM_CELL / 2) {
            double r = rnd(mix(cx, cz, 41));
            if (r < 0.07) set(c, p, x, BASE + ROOM_H - 1, z, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
            else if (r < 0.2) set(c, p, x, BASE + 1, z, Blocks.COBWEB.defaultBlockState());
        }
        double cr = rnd(mix(x, 51, z));
        set(c, p, x, BASE + ROOM_H, z, cr < 0.7 ? Blocks.SPRUCE_PLANKS.defaultBlockState() : Blocks.DARK_OAK_PLANKS.defaultBlockState());
        if (rnd(mix(x, 52, z)) < 0.01 && !wallHere) set(c, p, x, BASE + ROOM_H - 1, z, Blocks.COBWEB.defaultBlockState());
        return BASE + ROOM_H;
    }

    private static int blood(ChunkAccess c, BlockPos.MutableBlockPos p, int x, int z) {
        int isl = island(x, z);
        set(c, p, x, BLOOD_Y - 6, z, Blocks.BEDROCK.defaultBlockState());
        for (int y = BLOOD_Y - 5; y < BLOOD_Y; y++) set(c, p, x, y, z, rnd(mix(x, y, z)) < 0.12 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState());
        if (isl > 0) {
            for (int y = BLOOD_Y; y < BLOOD_Y + isl; y++) {
                double r = rnd(mix(x, y, z));
                set(c, p, x, y, z, r < 0.1 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : r < 0.5 ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState());
            }
            set(c, p, x, BLOOD_Y + isl, z, rnd(mix(x, 61, z)) < 0.2 ? Blocks.SMOOTH_BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState());
            return BLOOD_Y + isl + 1;
        }
        if (lava(x, z)) {
            set(c, p, x, BLOOD_Y, z, Blocks.LAVA.defaultBlockState());
            set(c, p, x, BLOOD_Y - 1, z, Blocks.OBSIDIAN.defaultBlockState());
            return BLOOD_Y;
        }
        double r = rnd(mix(x, 71, z));
        set(c, p, x, BLOOD_Y, z, r < 0.1 ? Blocks.NETHER_WART_BLOCK.defaultBlockState() : r < 0.14 ? Blocks.MAGMA_BLOCK.defaultBlockState() : r < 0.2 ? Blocks.RED_NETHER_BRICKS.defaultBlockState() : Blocks.RED_CONCRETE.defaultBlockState());
        return BLOOD_Y + 1;
    }

    // ------------------------------------------------------------------ dead trees (tiled, so they cross chunk borders)

    private static void trees(ChunkAccess chunk) {
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        int t0x = Math.floorDiv(x0 - 6, 7), t1x = Math.floorDiv(x0 + 21, 7);
        int t0z = Math.floorDiv(z0 - 6, 7), t1z = Math.floorDiv(z0 + 21, 7);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int tx = t0x; tx <= t1x; tx++) {
            for (int tz = t0z; tz <= t1z; tz++) {
                long h = mix(tx, tz, 101);
                if (rnd(h) > 0.55) continue;
                int px = tx * 7 + (int) (rnd(h * 31 + 1) * 7), pz = tz * 7 + (int) (rnd(h * 17 + 3) * 7);
                double d = dist(px, pz);
                if (d >= RING - 6 || scene(px, pz) != FOREST) continue;
                tree(chunk, p, px, forestHeight(px, pz), pz, h);
            }
        }
    }

    private static void put(ChunkAccess c, BlockPos.MutableBlockPos p, int x, int y, int z, BlockState s) {
        int cx = c.getPos().getMinBlockX(), cz = c.getPos().getMinBlockZ();
        if (x < cx || x > cx + 15 || z < cz || z > cz + 15) return;
        set(c, p, x, y, z, s);
    }

    private static void tree(ChunkAccess c, BlockPos.MutableBlockPos p, int x, int gy, int z, long h) {
        BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
        int H = 6 + (int) (rnd(h * 7 + 5) * 6);
        for (int i = 1; i <= H; i++) put(c, p, x, gy + i, z, log);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (rnd(mix(h, dir.get3DDataValue(), 3)) < 0.7) put(c, p, x + dir.getStepX(), gy + 1, z + dir.getStepZ(), log.setValue(RotatedPillarBlock.AXIS, dir.getAxis()));
        }
        int branches = 3 + (int) (rnd(h * 3 + 9) * 3);
        for (int b = 0; b < branches; b++) {
            long bh = mix(h, b, 77);
            Direction dir = Direction.from2DDataValue((int) (rnd(bh) * 4));
            int by = gy + H - 1 - (int) (rnd(bh * 5 + 1) * (H - 3));
            int len = 2 + (int) (rnd(bh * 11 + 2) * 3);
            int bx = x, bz = z, yy = by;
            for (int i = 1; i <= len; i++) {
                bx += dir.getStepX(); bz += dir.getStepZ();
                if (i == 2 || i == 3) yy++;
                put(c, p, bx, yy, bz, log.setValue(RotatedPillarBlock.AXIS, dir.getAxis()));
                if (rnd(mix(bh, i, 5)) < 0.45) put(c, p, bx, yy + 1, bz, Blocks.COBWEB.defaultBlockState());
            }
            put(c, p, bx, yy + 1, bz, Blocks.COBWEB.defaultBlockState());
            if (rnd(bh * 13 + 4) < 0.12) put(c, p, bx, yy - 1, bz, Blocks.SOUL_LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        }
        put(c, p, x, gy + H + 1, z, Blocks.COBWEB.defaultBlockState());
    }

    private FearTerrain() {}
}
