package com.infinitystones.tensura.skill.ability.extra.fear.scene;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Random;

import static com.infinitystones.tensura.skill.ability.extra.fear.scene.St.*;

/**
 * Endless yellowed rooms. The layout is a torus (10 x 10 cells of 8 blocks, 80-block period) and the buffer repeats it, so walking
 * out of one edge puts you on the opposite edge with nothing changing: the rooms never end.
 */
public final class Backrooms extends Scene {
    private static final int PERIOD = 80, CELL = 8, CELLS = 10, CEIL = 5;
    private static final Pal WALL_A = Pal.of(Blocks.YELLOW_TERRACOTTA, 12, Blocks.BROWN_TERRACOTTA, 1, Blocks.TERRACOTTA, 1);
    private static final Pal WALL_B = Pal.of(Blocks.SMOOTH_SANDSTONE, 12, Blocks.SANDSTONE, 2, Blocks.TERRACOTTA, 1);
    private static final Pal FLOOR = Pal.of(Blocks.BROWN_WOOL, 12, Blocks.GRAY_WOOL, 1, Blocks.BROWN_TERRACOTTA, 1, Blocks.MUD, 1);
    private static final Pal TILE = Pal.of(Blocks.CALCITE, 8, Blocks.LIGHT_GRAY_CONCRETE, 3, Blocks.SMOOTH_QUARTZ, 2);

    @Override
    public String id() { return "backrooms"; }

    @Override
    public int minY() { return -4; }

    @Override
    public int maxY() { return CEIL + 1; }

    @Override
    public void plan(Arena a, Ctx c, List<Runnable> s) {
        Maze maze = new Maze(CELLS, CELLS, true, 70, a.rnd);
        a.spawn(4, 1, 4);
        s.add(() -> structure(a, maze));
        s.add(() -> props(a));
        s.add(() -> lamps(a));
        s.add(() -> wrap(a));
    }

    private static int cell(int v) { return Math.floorMod(Math.floorDiv(v + 40, CELL), CELLS); }

    private static int loc(int v) { return Math.floorMod(v + 40, CELL); }

    private static int openStart(int cx, int cy, int axis) {
        int ow = openWidth(cx, cy, axis);
        return 1 + (int) (Noise.hash(cx, cy, 500 + axis) * (CELL - ow));
    }

    private static int openWidth(int cx, int cy, int axis) { return 4 + (int) (Noise.hash(cx, cy, 700 + axis) * 3); }

    private static void structure(Arena a, Maze m) {
        Random r = a.rnd;
        for (int z = -Arena.HALF; z < Arena.HALF; z++)
            for (int x = -Arena.HALF; x < Arena.HALF; x++) {
                int cx = cell(x), cy = cell(z), lx = loc(x), lz = loc(z);
                a.set(x, 0, z, FLOOR.pick(a.rnd));
                a.set(x, CEIL, z, ((x & 1) == 0 && (z & 1) == 0) ? Blocks.LIGHT_GRAY_CONCRETE.defaultBlockState() : TILE.pick(a.rnd));
                a.setGy(x, z, 0);
                boolean wall;
                if (lx == 0 && lz == 0) wall = true;
                else if (lx == 0) {
                    boolean open = m.east(cx - 1, cy) && lz >= openStart(cx, cy, 0) && lz < openStart(cx, cy, 0) + openWidth(cx, cy, 0);
                    wall = !open;
                } else if (lz == 0) {
                    boolean open = m.south(cx, cy - 1) && lx >= openStart(cx, cy, 1) && lx < openStart(cx, cy, 1) + openWidth(cx, cy, 1);
                    wall = !open;
                } else wall = false;
                if (!wall) continue;
                for (int y = 1; y <= CEIL - 1; y++) {
                    float h = Noise.hash3(x, y, z, 9);
                    a.set(x, y, z, y == 1 ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : ((x + z) & 1) == 0 ? WALL_A.pick(r) : WALL_B.pick(r));
                    if (y >= 2 && h < 0.03f) a.set(x, y, z, Blocks.BROWN_TERRACOTTA);
                }
            }
    }

    private static void props(Arena a) {
        for (int bx = -48; bx < 48; bx += CELL) for (int bz = -48; bz < 48; bz += CELL) {
            // align to the real cell grid: walls sit on multiples of 8
            int cx = cell(bx), cy = cell(bz);
            Random r = new Random(Noise.hash(cx, cy, 1234) * 1_000_000L + cx * 31L + cy);
            int x0 = bx + 1, z0 = bz + 1;
            int roll = r.nextInt(30);
            if (roll == 0) { // abandoned desk
                Kit.table(a, x0 + 1, 1, z0 + 2, 4, Direction.Axis.X, Blocks.OAK_SLAB, Blocks.OAK_FENCE);
                Kit.chair(a, x0 + 2, 1, z0 + 3, Direction.NORTH, Blocks.OAK_STAIRS);
                if (r.nextBoolean()) a.set(x0 + 3, 2, z0 + 2, Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.CANDLES, 1));
            } else if (roll == 1) { // stained carpet
                Kit.blood(a, x0 + 3, 1, z0 + 3, 2.5f);
            } else if (roll == 2) { // pillars
                for (int k = 0; k < 4; k++) for (int y = 1; y <= CEIL - 1; y++) a.set(x0 + 1 + (k % 2) * 4, y, z0 + 1 + (k / 2) * 4, y == 1 ? Blocks.SMOOTH_SANDSTONE.defaultBlockState() : WALL_A.pick(r));
            } else if (roll == 3) { // boxes
                for (int k = 0; k < 3; k++) a.setIfAir(x0 + r.nextInt(6), 1, z0 + r.nextInt(6), Blocks.SMOOTH_SANDSTONE);
                a.setIfAir(x0 + 2, 2, z0 + 2, Blocks.SMOOTH_SANDSTONE_SLAB);
            } else if (roll == 4) { // a sealed-off dark closet corner with a single candle
                a.set(x0 + 5, 1, z0 + 5, Blocks.BLACK_CONCRETE);
                a.set(x0 + 5, 2, z0 + 5, Blocks.BLACK_CONCRETE);
                a.candle(x0 + 5, 3, z0 + 5, 1, Blocks.CANDLE);
            } else if (roll == 5) { // water-stained patch on the floor and a spreading mould on the wall
                for (int k = 0; k < 9; k++) a.set(x0 + 1 + r.nextInt(5), 0, z0 + 1 + r.nextInt(5), r.nextBoolean() ? Blocks.MUD.defaultBlockState() : Blocks.GRAY_WOOL.defaultBlockState());
            } else if (roll == 6) {
                Kit.bones(a, x0 + 3, 1, z0 + 3);
            }
        }
    }

    private static void lamps(Arena a) {
        for (int x = -48; x < 48; x++) for (int z = -48; z < 48; z++) {
            if (((x + 40) % 4 != 2) || ((z + 40) % 4 != 2)) continue;
            if (loc(x) == 0 || loc(z) == 0) continue;
            float h = Noise.hash(Math.floorMod(x, PERIOD), Math.floorMod(z, PERIOD), 33);
            if (a.solid(x, CEIL - 1, z)) continue;
            if (h < 0.55f) a.deadLamp(x, CEIL, z);
            else if (h < 0.80f) a.badLamp(x, CEIL, z);
            else if (h < 0.93f) a.dyingLamp(x, CEIL, z);
            else a.pulseLight(x, CEIL - 1, z, 3, 6);
            if (h >= 0.93f) a.deadLamp(x, CEIL, z);
        }
    }

    private static void wrap(Arena a) {
        int e = PERIOD / 2;
        a.trigger(e, 1, -48, 47, 4, 47, -PERIOD, 0, 0);
        a.trigger(-48, 1, -48, -e - 1, 4, 47, PERIOD, 0, 0);
        a.trigger(-48, 1, e, 47, 4, 47, 0, 0, -PERIOD);
        a.trigger(-48, 1, -48, 47, 4, -e - 1, 0, 0, PERIOD);
    }
}
