package com.infinitystones.tensura.skill.ability.extra.fear.scene;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Random;

import static com.infinitystones.tensura.skill.ability.extra.fear.scene.St.*;

/** Reusable construction helpers: terrain, ruin, props, furniture, lights. Coordinates are arena-local. */
public final class Kit {
    public static final Direction[] H = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    // ------------------------------------------------------------ terrain

    /** Flat ground: surface block at y = 0 from {@code top}, {@code depth} layers of {@code under} below. */
    public static void groundFlat(Arena a, Pal top, Pal under, int depth) {
        for (int z = -Arena.HALF; z < Arena.HALF; z++)
            for (int x = -Arena.HALF; x < Arena.HALF; x++) {
                a.set(x, 0, z, top.pick(a.rnd));
                for (int y = -1; y >= -depth; y--) a.set(x, y, z, under.pick(a.rnd));
                a.setGy(x, z, 0);
            }
    }

    /** Rolling ground that fades back to y = 0 at the arena border. */
    public static void groundHills(Arena a, float amp, float scale, int seed, Pal top, Pal under, int depth) {
        for (int z = -Arena.HALF; z < Arena.HALF; z++)
            for (int x = -Arena.HALF; x < Arena.HALF; x++) {
                float edge = Math.min(1f, Math.max(0f, (Arena.HALF - 1 - Math.max(Math.abs(x), Math.abs(z))) / 6f));
                int h = Math.round((Noise.fbm(x * scale + 100, z * scale + 100, seed, 3) - 0.5f) * 2f * amp * edge);
                for (int y = -depth; y < h; y++) a.set(x, y, z, under.pick(a.rnd));
                a.set(x, h, z, top.pick(a.rnd));
                a.setGy(x, z, h);
            }
    }

    /** Highest solid block of a column, or minY - 1 when empty. */
    public static int top(Arena a, int x, int z) {
        for (int y = a.maxY; y >= a.minY; y--) if (a.solid(x, y, z)) return y;
        return a.minY - 1;
    }

    // ------------------------------------------------------------ ruin

    /** Clumpy removal of exposed blocks so walls look eaten away. */
    public static void decay(Arena a, int x1, int y1, int z1, int x2, int y2, int z2, float p, int seed) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                    if (!a.solid(x, y, z)) continue;
                    float n = Noise.value(x * 0.33f + y * 0.21f, z * 0.33f - y * 0.17f, seed);
                    if (n < p && exposed(a, x, y, z)) a.carve(x, y, z);
                }
    }

    public static boolean exposed(Arena a, int x, int y, int z) {
        return a.air(x + 1, y, z) || a.air(x - 1, y, z) || a.air(x, y + 1, z) || a.air(x, y - 1, z) || a.air(x, y, z + 1) || a.air(x, y, z - 1);
    }

    /** Randomly swaps {@code from} for {@code to} inside the box. */
    public static void age(Arena a, int x1, int y1, int z1, int x2, int y2, int z2, Block from, Pal to, float chance) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
                    if (a.is(x, y, z, from) && a.rnd.nextFloat() < chance) a.set(x, y, z, to.pick(a.rnd));
    }

    /** Cobwebs in the corners and under ceilings of the box. */
    public static void webs(Arena a, int x1, int y1, int z1, int x2, int y2, int z2, float chance) {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
                    if (!a.air(x, y, z)) continue;
                    int n = (a.solid(x + 1, y, z) ? 1 : 0) + (a.solid(x - 1, y, z) ? 1 : 0) + (a.solid(x, y, z + 1) ? 1 : 0) + (a.solid(x, y, z - 1) ? 1 : 0);
                    boolean roof = a.solid(x, y + 1, z);
                    if (((roof && n >= 1) || n >= 2) && a.rnd.nextFloat() < chance) a.set(x, y, z, Blocks.COBWEB);
                }
    }

    /** Dried blood on the floor (y = first air above the floor). */
    public static void blood(Arena a, int cx, int y, int cz, float r) {
        int ri = (int) Math.ceil(r);
        for (int z = -ri; z <= ri; z++)
            for (int x = -ri; x <= ri; x++) {
                float d = (float) Math.sqrt(x * x + z * z);
                float noise = Noise.value((cx + x) * 0.9f, (cz + z) * 0.9f, 77);
                if (d <= r * (0.55f + noise * 0.6f) && a.air(cx + x, y, cz + z) && a.solid(cx + x, y - 1, cz + z)) {
                    a.set(cx + x, y, cz + z, a.rnd.nextInt(5) == 0 ? Blocks.BROWN_CARPET : Blocks.RED_CARPET);
                }
            }
        if (r >= 2 && a.solid(cx, y - 1, cz)) a.set(cx, y - 1, cz, a.rnd.nextBoolean() ? Blocks.NETHER_WART_BLOCK : Blocks.RED_TERRACOTTA);
    }

    public static void bloodTrail(Arena a, int x1, int z1, int x2, int z2, int y) {
        int n = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
        for (int i = 0; i <= n; i++) {
            int x = x1 + (n == 0 ? 0 : (x2 - x1) * i / n), z = z1 + (n == 0 ? 0 : (z2 - z1) * i / n);
            if (a.rnd.nextInt(4) != 0) blood(a, x, y, z, 0.9f + a.rnd.nextFloat());
        }
    }

    public static void bones(Arena a, int x, int y, int z) {
        Random r = a.rnd;
        a.setIfAir(x, y, z, axis(Blocks.BONE_BLOCK, r.nextBoolean() ? Direction.Axis.X : Direction.Axis.Z));
        if (r.nextInt(2) == 0) a.setIfAir(x + 1, y, z + r.nextInt(3) - 1, Blocks.SKELETON_SKULL.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ROTATION_16, r.nextInt(16)));
        if (r.nextInt(3) == 0) a.setIfAir(x - 1, y, z, axis(Blocks.BONE_BLOCK, Direction.Axis.X));
        if (r.nextInt(3) == 0) a.setIfAir(x, y, z + 1, Blocks.BONE_BLOCK);
    }

    public static void skull(Arena a, int x, int y, int z, Block skull) {
        a.setIfAir(x, y, z, St.skull(skull, a.rnd.nextInt(16)));
    }

    // ------------------------------------------------------------ lights

    public static void candles(Arena a, int x, int y, int z, int n, boolean flicker) {
        Block c = a.rnd.nextInt(3) == 0 ? Blocks.BLACK_CANDLE : a.rnd.nextInt(3) == 0 ? Blocks.RED_CANDLE : Blocks.CANDLE;
        if (flicker) a.candle(x, y, z, n, c);
        else a.set(x, y, z, candle(c, n, true));
    }

    public static void soulLantern(Arena a, int x, int y, int z, boolean hanging) {
        a.set(x, y, z, lantern(Blocks.SOUL_LANTERN, hanging));
    }

    /** Post with a soul lantern on top. */
    public static void lampPost(Arena a, int x, int y, int z, int h, Block post) {
        for (int i = 0; i < h; i++) a.set(x, y + i, z, post);
        a.set(x, y + h, z, lantern(Blocks.SOUL_LANTERN, false));
    }

    /** Hanging light: chain from {@code yTop} down to a lantern. */
    public static void hangLantern(Arena a, int x, int yTop, int z, int len, Block lantern) {
        for (int i = 0; i < len; i++) a.set(x, yTop - i, z, chain(Direction.Axis.Y));
        a.set(x, yTop - len, z, lantern(lantern, true));
    }

    public static void chandelier(Arena a, int x, int yTop, int z, int chainLen, int arm) {
        for (int i = 0; i < chainLen; i++) a.set(x, yTop - i, z, chain(Direction.Axis.Y));
        int yb = yTop - chainLen;
        a.set(x, yb, z, Blocks.IRON_BARS);
        for (int i = 1; i <= arm; i++) {
            a.set(x + i, yb, z, Blocks.IRON_BARS);
            a.set(x - i, yb, z, Blocks.IRON_BARS);
            a.set(x, yb, z + i, Blocks.IRON_BARS);
            a.set(x, yb, z - i, Blocks.IRON_BARS);
        }
        int[][] ends = {{arm, 0}, {-arm, 0}, {0, arm}, {0, -arm}, {0, 0}};
        for (int[] e : ends) {
            if (a.rnd.nextInt(5) == 0) continue;
            Block c = a.rnd.nextInt(2) == 0 ? Blocks.CANDLE : Blocks.WHITE_CANDLE;
            a.candle(x + e[0], yb + 1, z + e[1], 2 + a.rnd.nextInt(3), c);
        }
        if (a.rnd.nextInt(3) == 0) a.set(x + arm, yb, z, Blocks.AIR);
    }

    // ------------------------------------------------------------ trees

    public static void deadTree(Arena a, int x, int y, int z, int h, Block log) {
        Random r = a.rnd;
        int px = x, pz = z;
        int lx = 0, lz = 0;
        for (int i = 0; i < h; i++) {
            a.set(px, y + i, pz, axis(log, Direction.Axis.Y));
            if (i > 2 && r.nextInt(4) == 0) {
                if (lx == 0 && lz == 0) { lx = r.nextInt(3) - 1; lz = lx == 0 ? r.nextInt(3) - 1 : 0; }
                px += lx;
                pz += lz;
            }
            if (i >= h / 2 && i < h - 1 && r.nextInt(3) == 0) branch(a, px, y + i, pz, log, 2 + r.nextInt(4));
        }
        for (int i = 0; i < 3; i++) {
            Direction d = H[r.nextInt(4)];
            int rl = 1 + r.nextInt(3);
            for (int k = 1; k <= rl; k++) a.setIfAir(x + d.getStepX() * k, y + (k > 1 ? -1 : 0) + 0, z + d.getStepZ() * k, axis(log, d.getAxis()));
        }
    }

    private static void branch(Arena a, int x, int y, int z, Block log, int len) {
        Direction d = H[a.rnd.nextInt(4)];
        int cx = x, cy = y, cz = z;
        for (int k = 0; k < len; k++) {
            cx += d.getStepX();
            cz += d.getStepZ();
            if (k % 2 == 1) cy++;
            a.setIfAir(cx, cy, cz, axis(log, d.getAxis()));
            if (k == len - 1 && a.rnd.nextBoolean()) a.setIfAir(cx, cy + 1, cz, axis(log, Direction.Axis.Y));
        }
    }

    /** Dark crown tree: tall trunk and a lumpy canopy of persistent leaves. */
    public static void crownTree(Arena a, int x, int y, int z, int h, Block log, Block leaves, int rad) {
        Random r = a.rnd;
        for (int i = 0; i < h; i++) a.set(x, y + i, z, axis(log, Direction.Axis.Y));
        BlockState lf = leaf(leaves);
        int cy = y + h - 2;
        for (int dy = -1; dy <= rad; dy++) {
            float rr = rad - Math.max(0, dy) * 0.9f + (dy < 0 ? -0.5f : 0f);
            int ri = (int) Math.ceil(rr);
            for (int dz = -ri; dz <= ri; dz++)
                for (int dx = -ri; dx <= ri; dx++)
                    if (dx * dx + dz * dz <= rr * rr + r.nextFloat() * 1.5f - 0.7f) a.setIfAir(x + dx, cy + dy, z + dz, lf);
        }
        for (int i = 0; i < 2; i++) {
            Direction d = H[r.nextInt(4)];
            for (int k = 1; k <= 2; k++) a.setIfAir(x + d.getStepX() * k, y + h / 2 + k - 1, z + d.getStepZ() * k, axis(log, d.getAxis()));
        }
    }

    // ------------------------------------------------------------ fences and gravestones

    public static void fenceLine(Arena a, int x1, int z1, int x2, int z2, int y, int h, Block bars, Block post, int spacing, float gap) {
        int n = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
        for (int i = 0; i <= n; i++) {
            int x = x1 + (n == 0 ? 0 : (x2 - x1) * i / n), z = z1 + (n == 0 ? 0 : (z2 - z1) * i / n);
            if (a.rnd.nextFloat() < gap) { if (a.rnd.nextInt(3) == 0) a.set(x, y, z, bars); continue; }
            if (i % spacing == 0) {
                for (int k = 0; k <= h; k++) a.set(x, y + k, z, post);
                if (a.rnd.nextInt(3) == 0) a.set(x, y + h + 1, z, a.rnd.nextBoolean() ? Blocks.SKELETON_SKULL.defaultBlockState() : Blocks.AIR.defaultBlockState());
            } else {
                int hh = a.rnd.nextInt(6) == 0 ? Math.max(0, h - 1 - a.rnd.nextInt(2)) : h - 1;
                for (int k = 0; k <= hh; k++) a.set(x, y + k, z, bars);
            }
        }
    }

    public static void tomb(Arena a, int x, int y, int z, Direction face, int kind) {
        Random r = a.rnd;
        Pal stone = Pal.of(Blocks.COBBLESTONE_WALL, 3, Blocks.MOSSY_COBBLESTONE_WALL, 3, Blocks.DEEPSLATE_TILE_WALL, 2, Blocks.STONE_BRICK_WALL, 2);
        Direction side = face.getClockWise();
        switch (kind) {
            case 0 -> {
                a.set(x, y, z, stone.pick(r));
                a.set(x, y + 1, z, stone.pick(r));
                a.set(x + face.getStepX(), y, z + face.getStepZ(), Blocks.COARSE_DIRT);
            }
            case 1 -> {
                for (int i = 0; i < 3; i++) a.set(x, y + i, z, Blocks.MOSSY_STONE_BRICK_WALL);
                a.set(x + side.getStepX(), y + 2, z + side.getStepZ(), Blocks.MOSSY_STONE_BRICK_WALL);
                a.set(x - side.getStepX(), y + 2, z - side.getStepZ(), Blocks.MOSSY_STONE_BRICK_WALL);
                a.set(x + face.getStepX(), y, z + face.getStepZ(), Blocks.PODZOL);
            }
            case 2 -> {
                a.set(x, y, z, stair(Blocks.COBBLED_DEEPSLATE_STAIRS, face.getOpposite(), false));
                a.set(x, y + 1, z, slab(Blocks.COBBLED_DEEPSLATE_SLAB, false));
                for (int i = 1; i <= 2; i++) a.set(x + face.getStepX() * i, y, z + face.getStepZ() * i, a.rnd.nextBoolean() ? Blocks.COARSE_DIRT : Blocks.PODZOL);
                a.setIfAir(x + face.getStepX() * 2, y + 1, z + face.getStepZ() * 2, Blocks.DEAD_BUSH);
            }
            default -> {
                a.set(x, y, z, Blocks.STONE_BRICKS);
                a.set(x, y + 1, z, Blocks.CHISELED_STONE_BRICKS);
                a.set(x, y + 2, z, slab(Blocks.STONE_BRICK_SLAB, false));
                a.set(x + side.getStepX(), y, z + side.getStepZ(), stair(Blocks.STONE_BRICK_STAIRS, side.getOpposite(), false));
                a.set(x - side.getStepX(), y, z - side.getStepZ(), stair(Blocks.STONE_BRICK_STAIRS, side, false));
            }
        }
    }

    // ------------------------------------------------------------ furniture

    public static void chair(Arena a, int x, int y, int z, Direction facing, Block stairs) {
        a.set(x, y, z, stair(stairs, facing, false));
    }

    /** Table: slabs on top of the cell, fence legs at both ends. */
    public static void table(Arena a, int x, int y, int z, int len, Direction.Axis along, Block slabBlock, Block leg) {
        for (int i = 0; i < len; i++) {
            int tx = x + (along == Direction.Axis.X ? i : 0), tz = z + (along == Direction.Axis.Z ? i : 0);
            if (i == 0 || i == len - 1) a.set(tx, y, tz, leg);
            else a.set(tx, y, tz, slab(slabBlock, true));
        }
    }

    public static void bed(Arena a, int x, int y, int z, Direction head, Block bed) {
        a.set(x, y, z, bedPart(bed, head, false));
        a.set(x + head.getStepX(), y, z + head.getStepZ(), bedPart(bed, head, true));
    }

    public static void pew(Arena a, int x, int y, int z, int len, Direction.Axis along, Direction facing, Block stairs, float broken) {
        for (int i = 0; i < len; i++) {
            int tx = x + (along == Direction.Axis.X ? i : 0), tz = z + (along == Direction.Axis.Z ? i : 0);
            float f = a.rnd.nextFloat();
            if (f < broken * 0.5f) continue;
            if (f < broken) a.set(tx, y, tz, slab(Blocks.SPRUCE_SLAB, false));
            else a.set(tx, y, tz, stair(stairs, facing, false));
        }
    }

    public static void shelf(Arena a, int x, int y, int z, int h) {
        for (int i = 0; i < h; i++) a.set(x, y + i, z, a.rnd.nextInt(7) == 0 ? Blocks.AIR.defaultBlockState() : Blocks.BOOKSHELF.defaultBlockState());
    }

    public static void cage(Arena a, int x, int y, int z, boolean skull) {
        a.shell(x - 1, y, z - 1, x + 1, y + 2, z + 1, Pal.one(Blocks.IRON_BARS));
        a.clearBox(x, y, z, x, y + 1, z);
        a.set(x, y + 3, z, chain(Direction.Axis.Y));
        a.set(x, y + 4, z, chain(Direction.Axis.Y));
        if (skull) a.set(x, y, z, St.skull(a.rnd.nextBoolean() ? Blocks.SKELETON_SKULL : Blocks.WITHER_SKELETON_SKULL, a.rnd.nextInt(16)));
    }

    // ------------------------------------------------------------ buildings

    /** Gable roof from stairs. Ridge runs along X when {@code ridgeX}; rows rise one block per block towards the ridge. */
    public static void roof(Arena a, int x1, int z1, int x2, int z2, int yBase, boolean ridgeX, Block stairs, Block slab, Pal gable) {
        int lo = ridgeX ? z1 : x1, hi = ridgeX ? z2 : x2;
        int a1 = ridgeX ? x1 : z1, a2 = ridgeX ? x2 : z2;
        for (int i = 0; lo + i <= hi - i; i++) {
            int l = lo + i, h = hi - i, y = yBase + i;
            for (int t = a1 - 1; t <= a2 + 1; t++) {
                if (l == h) {
                    a.set(ridgeX ? t : l, y, ridgeX ? l : t, slab(slab, false));
                    continue;
                }
                Direction fl = ridgeX ? Direction.NORTH : Direction.WEST, fh = ridgeX ? Direction.SOUTH : Direction.EAST;
                a.set(ridgeX ? t : l, y, ridgeX ? l : t, stair(stairs, fl, false));
                a.set(ridgeX ? t : h, y, ridgeX ? h : t, stair(stairs, fh, false));
            }
            for (int t : new int[]{a1, a2}) {
                for (int s = l + 1; s <= h - 1; s++) a.set(ridgeX ? t : s, y, ridgeX ? s : t, gable.pick(a.rnd));
                if (l < h) {
                    for (int yy = yBase; yy < y; yy++) {
                        a.set(ridgeX ? t : l, yy, ridgeX ? l : t, gable.pick(a.rnd));
                        a.set(ridgeX ? t : h, yy, ridgeX ? h : t, gable.pick(a.rnd));
                    }
                }
            }
        }
    }

    /** Window openings on the outer wall of a rectangle: panes, bars or nothing, every {@code step} blocks. */
    public static void windows(Arena a, int x1, int z1, int x2, int z2, int y, int h, int step, int phase, float missing) {
        Random r = a.rnd;
        for (int x = x1 + 2 + phase; x <= x2 - 2; x += step) {
            window(a, x, y, z1, h, Direction.Axis.X, missing, r);
            window(a, x, y, z2, h, Direction.Axis.X, missing, r);
        }
        for (int z = z1 + 2 + phase; z <= z2 - 2; z += step) {
            window(a, x1, y, z, h, Direction.Axis.Z, missing, r);
            window(a, x2, y, z, h, Direction.Axis.Z, missing, r);
        }
    }

    private static void window(Arena a, int x, int y, int z, int h, Direction.Axis along, float missing, Random r) {
        for (int k = 0; k < h; k++) {
            for (int w = 0; w < 2; w++) {
                int wx = x + (along == Direction.Axis.X ? w : 0), wz = z + (along == Direction.Axis.Z ? w : 0);
                float f = r.nextFloat();
                if (f < missing) a.carve(wx, y + k, wz);
                else if (f < missing + 0.2f) a.set(wx, y + k, wz, Blocks.IRON_BARS);
                else a.set(wx, y + k, wz, Blocks.BLACK_STAINED_GLASS_PANE);
            }
        }
    }

    /** A doorway in a wall cell column, optionally with a (half-open) door. */
    public static void door(Arena a, int x, int y, int z, Direction facing, Block door, boolean open, boolean hang) {
        a.carve(x, y, z);
        a.carve(x, y + 1, z);
        if (door == null) return;
        if (hang) {
            boolean right = a.rnd.nextBoolean();
            a.set(x, y, z, doorLower(door, facing, open, right));
            a.set(x, y + 1, z, doorUpper(door, facing, open, right));
        }
    }

    private Kit() {}
}
