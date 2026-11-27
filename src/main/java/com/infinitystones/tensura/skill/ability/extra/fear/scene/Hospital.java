package com.infinitystones.tensura.skill.ability.extra.fear.scene;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Random;

import static com.infinitystones.tensura.skill.ability.extra.fear.scene.St.*;

/** Derelict asylum: barred wards, padded cells, failing fluorescent lights, a gurney-lined corridor, a morgue in the basement. */
public final class Hospital extends Scene {
    private static final int X1 = -36, X2 = 35, Z1 = -11, Z2 = 12;
    private static final Pal OUTER = Pal.of(Blocks.LIGHT_GRAY_CONCRETE, 5, Blocks.GRAY_CONCRETE, 3, Blocks.STONE_BRICKS, 2, Blocks.CRACKED_STONE_BRICKS, 1);
    private static final Pal WAIN = Pal.of(Blocks.GREEN_TERRACOTTA, 5, Blocks.CYAN_TERRACOTTA, 2, Blocks.GRAY_TERRACOTTA, 1);
    private static final Pal PLASTER = Pal.of(Blocks.WHITE_TERRACOTTA, 5, Blocks.LIGHT_GRAY_TERRACOTTA, 3, Blocks.CALCITE, 1);
    private static final Pal TILE = Pal.of(Blocks.LIGHT_GRAY_CONCRETE, 4, Blocks.POLISHED_ANDESITE, 3, Blocks.GRAY_CONCRETE, 2, Blocks.WHITE_CONCRETE, 1);

    private static final String[] NORTH = {"padded", "padded", "ward", "operating", "office", "ward", "padded", "store"};
    private static final String[] SOUTH = {"ward", "padded", "store", "lobby", "lobby", "ward", "office", "padded"};

    @Override
    public String id() { return "hospital"; }

    @Override
    public int minY() { return -10; }

    @Override
    public int maxY() { return 20; }

    @Override
    public void plan(Arena a, Ctx c, List<Runnable> s) {
        a.spawn(0, 1, 38);
        s.add(() -> grounds(a));
        s.add(() -> shell(a));
        s.add(() -> walls(a));
        s.add(() -> rooms(a, 1));
        s.add(() -> rooms(a, 7));
        s.add(() -> corridorProps(a, 1));
        s.add(() -> corridorProps(a, 7));
        s.add(() -> lobby(a));
        s.add(() -> stairs(a));
        s.add(() -> morgue(a));
        s.add(() -> roof(a));
        s.add(() -> ruin(a));
    }

    // ------------------------------------------------------------------ site

    private static void grounds(Arena a) {
        Random r = a.rnd;
        Kit.groundFlat(a, Pal.of(Blocks.COARSE_DIRT, 4, Blocks.PODZOL, 3, Blocks.GRAVEL, 1, Blocks.MUD, 1), Pal.of(Blocks.DIRT, 3, Blocks.STONE, 1), 8);
        // asphalt drive and car park
        for (int z = 26; z <= 47; z++) for (int x = -4; x <= 4; x++) a.set(x, 0, z, r.nextInt(9) == 0 ? Blocks.GRAVEL.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState());
        for (int z = 30; z <= 42; z++) for (int x = 5; x <= 20; x++) if (r.nextInt(5) != 0) a.set(x, 0, z, r.nextInt(6) == 0 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState());
        for (int z = 31; z <= 41; z += 5) a.line(6, 0, z, 6, 0, z + 3, Blocks.LIGHT_GRAY_CONCRETE);
        // fence
        Kit.fenceLine(a, -44, -44, 44, -44, 1, 3, Blocks.IRON_BARS, Blocks.STONE_BRICKS, 6, 0.04f);
        Kit.fenceLine(a, -44, -44, -44, 44, 1, 3, Blocks.IRON_BARS, Blocks.STONE_BRICKS, 6, 0.04f);
        Kit.fenceLine(a, 44, -44, 44, 44, 1, 3, Blocks.IRON_BARS, Blocks.STONE_BRICKS, 6, 0.04f);
        Kit.fenceLine(a, -44, 44, -6, 44, 1, 3, Blocks.IRON_BARS, Blocks.STONE_BRICKS, 6, 0.04f);
        Kit.fenceLine(a, 6, 44, 44, 44, 1, 3, Blocks.IRON_BARS, Blocks.STONE_BRICKS, 6, 0.04f);
        for (int sx : new int[]{-5, 5}) { for (int y = 1; y <= 5; y++) a.set(sx, y, 44, Blocks.STONE_BRICKS); Kit.soulLantern(a, sx, 6, 44, false); }
        // dead trees and a bench
        for (int i = 0; i < 12; i++) {
            int x = r.nextInt(80) - 40, z = 14 + r.nextInt(28);
            if (Math.abs(x) < 8 || (x > 3 && x < 22 && z > 28 && z < 44)) continue;
            Kit.deadTree(a, x, 1, z, 5 + r.nextInt(6), Blocks.DARK_OAK_LOG);
        }
        for (int x = -30; x <= -24; x++) a.set(x, 1, 32, stair(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
        // wrecked ambulance
        a.fill(12, 1, 33, 18, 3, 35, Pal.of(Blocks.WHITE_CONCRETE, 5, Blocks.LIGHT_GRAY_CONCRETE, 2));
        a.clearBox(13, 1, 34, 17, 2, 34);
        a.fill(12, 3, 34, 12, 3, 34, Blocks.GLASS_PANE);
        a.fill(15, 1, 33, 15, 2, 33, Blocks.RED_CONCRETE);
        a.set(16, 1, 34, Blocks.RED_CARPET);
        a.set(14, 1, 34, bedPart(Blocks.WHITE_BED, Direction.EAST, false));
        a.set(15, 1, 34, bedPart(Blocks.WHITE_BED, Direction.EAST, true));
        a.set(12, 1, 32, Blocks.BLACK_CONCRETE);
        a.set(18, 1, 32, Blocks.BLACK_CONCRETE);
        a.clearBox(17, 3, 33, 18, 3, 35);
        Kit.blood(a, 20, 1, 34, 2f);
    }

    // ------------------------------------------------------------------ structure

    private static void shell(Arena a) {
        for (int x = X1; x <= X2; x++) for (int z = Z1; z <= Z2; z++) a.set(x, 0, z, TILE.pick(a.rnd));
        a.walls(X1, 1, Z1, X2, 11, Z2, OUTER);
        a.fill(X1, 6, Z1, X2, 6, Z2, Pal.of(Blocks.LIGHT_GRAY_CONCRETE, 3, Blocks.GRAY_CONCRETE, 1));
        a.walls(X1, 6, Z1, X2, 6, Z2, Pal.one(Blocks.POLISHED_ANDESITE));
        a.fill(X1, 12, Z1, X2, 12, Z2, Pal.of(Blocks.GRAY_CONCRETE, 3, Blocks.STONE, 1));
        a.walls(X1, 13, Z1, X2, 13, Z2, Pal.of(Blocks.STONE_BRICKS, 1, Blocks.CRACKED_STONE_BRICKS, 1));
        for (int x = X1; x <= X2; x += 9) for (int z : new int[]{Z1, Z2}) for (int y = 1; y <= 12; y++) a.set(x, y, z, Blocks.POLISHED_ANDESITE);
        Kit.windows(a, X1, Z1, X2, Z2, 2, 3, 4, 0, 0.12f);
        Kit.windows(a, X1, Z1, X2, Z2, 8, 3, 4, 0, 0.2f);
    }

    private static void wallLine(Arena a, int x1, int z1, int x2, int z2, int y) {
        a.fill(x1, y, z1, x2, y + 1, z2, WAIN);
        a.fill(x1, y + 2, z1, x2, y + 4, z2, PLASTER);
    }

    private static void walls(Arena a) {
        for (int y : new int[]{1, 7}) {
            wallLine(a, X1, -2, X2, -2, y);
            wallLine(a, X1, 3, X2, 3, y);
            for (int x = X1 + 9; x < X2; x += 9) {
                wallLine(a, x, Z1, x, -2, y);
                wallLine(a, x, 3, x, Z2, y);
            }
        }
        // doors: two wide, to every room from the corridor
        for (int y : new int[]{1, 7}) {
            for (int k = 0; k < 8; k++) {
                int x = X1 + 9 * k + (k == 7 ? 6 : 4);
                doorway(a, x, y, -2, Direction.SOUTH, k < 8 && NORTH[k].equals("padded"));
                if (y == 7 || !SOUTH[k].equals("lobby")) doorway(a, x, y, 3, Direction.NORTH, SOUTH[k].equals("padded"));
            }
        }
        // lobby: the two south rooms next to the entrance become one open hall
        for (int y = 1; y <= 5; y++) {
            a.clearBox(0, y, 4, 0, y, 11);
            a.clearBox(-3, y, 3, 3, y, 3);
            a.clearBox(-3, y, 12, 3, y, 12);
        }
        a.clearBox(-3, 6, 3, 3, 6, 3);
    }

    private static void doorway(Arena a, int x, int y, int z, Direction facing, boolean padded) {
        for (int w = 0; w < 2; w++) {
            a.clearBox(x + w, y, z, x + w, y + 2, z);
            a.set(x + w, y + 3, z, Blocks.IRON_BARS);
        }
        Random r = a.rnd;
        int m = r.nextInt(4);
        if (padded) {
            a.set(x, y, z, doorLower(Blocks.IRON_DOOR, facing, m == 0, true));
            a.set(x, y + 1, z, doorUpper(Blocks.IRON_DOOR, facing, m == 0, true));
        } else if (m != 0) {
            a.set(x, y, z, doorLower(Blocks.SPRUCE_DOOR, facing, m == 1, false));
            a.set(x, y + 1, z, doorUpper(Blocks.SPRUCE_DOOR, facing, m == 1, false));
        }
    }

    // ------------------------------------------------------------------ rooms

    private static void rooms(Arena a, int y) {
        for (int k = 0; k < 8; k++) {
            int x0 = X1 + 9 * k + 1, x1 = Math.min(x0 + 7, X2 - 1);
            furnish(a, (y == 1 ? NORTH[k] : (k % 3 == 1 ? "padded" : "ward")), x0, -10, x1, -3, y, true);
            if (y == 1) { if (!SOUTH[k].equals("lobby")) furnish(a, SOUTH[k], x0, 4, x1, 11, y, false); }
            else furnish(a, k % 3 == 2 ? "padded" : k % 4 == 0 ? "store" : "ward", x0, 4, x1, 11, y, false);
        }
    }

    private static void furnish(Arena a, String type, int x1, int z1, int x2, int z2, int y, boolean north) {
        Random r = a.rnd;
        int cx = (x1 + x2) / 2;
        int back = north ? z1 : z2, dirZ = north ? 1 : -1;
        switch (type) {
            case "padded" -> {
                for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
                    boolean edge = x == x1 || x == x2 || z == back;
                    if (edge) for (int h = 0; h < 3; h++) a.set(x, y + h, z, r.nextInt(7) == 0 ? Blocks.RED_WOOL.defaultBlockState() : Blocks.LIGHT_GRAY_WOOL.defaultBlockState());
                }
                a.fill(cx - 1, y, back + dirZ * 2, cx, y, back + dirZ * 2, Blocks.GRAY_CARPET);
                a.set(x1 + 1, y + 2, back + dirZ, Blocks.CHAIN);
                a.set(x1 + 1, y + 1, back + dirZ, Blocks.CHAIN);
                a.set(x2 - 1, y + 2, back + dirZ, Blocks.CHAIN);
                Kit.blood(a, cx, y, back + dirZ * 3, 1.8f);
                if (r.nextInt(3) == 0) Kit.bones(a, cx - 1, y, back + dirZ * 3);
                if (r.nextInt(2) == 0) a.dyingLamp(cx, y + 4, (z1 + z2) / 2);
                else a.deadLamp(cx, y + 4, (z1 + z2) / 2);
            }
            case "ward" -> {
                for (int i = 0; i < 3; i++) {
                    int bx = x1 + 1 + i * 2;
                    if (bx + 0 > x2) break;
                    if (r.nextInt(5) == 0) continue;
                    Kit.bed(a, bx, y, back + dirZ, north ? Direction.NORTH : Direction.SOUTH, r.nextInt(3) == 0 ? Blocks.RED_BED : Blocks.WHITE_BED);
                    if (r.nextInt(3) == 0) Kit.blood(a, bx, y, back + dirZ * 2, 1.5f);
                    if (r.nextInt(2) == 0) { a.set(bx + 1, y, back, Blocks.IRON_BARS); a.set(bx + 1, y + 1, back, Blocks.IRON_BARS); a.set(bx + 1, y + 2, back, Blocks.IRON_BARS); a.set(bx + 1, y + 2, back + dirZ, Blocks.GLASS_PANE); }
                }
                a.set(x2, y, (z1 + z2) / 2, Blocks.CAULDRON);
                a.set(x1, y, z2, r.nextBoolean() ? Blocks.BROWN_CARPET.defaultBlockState() : Blocks.WHITE_CARPET.defaultBlockState());
                if (r.nextInt(2) == 0) a.badLamp(cx, y + 4, (z1 + z2) / 2);
                else a.deadLamp(cx, y + 4, (z1 + z2) / 2);
            }
            case "operating" -> {
                int cz = (z1 + z2) / 2;
                a.fill(cx - 1, y, cz, cx, y, cz, Blocks.IRON_BARS);
                a.fill(cx - 1, y + 1, cz, cx, y + 1, cz, Blocks.WHITE_CARPET);
                a.set(cx, y + 1, cz, Blocks.RED_CARPET);
                a.set(cx - 1, y + 2, cz, Blocks.SKELETON_SKULL.defaultBlockState());
                Kit.blood(a, cx, y, cz, 3f);
                Kit.bloodTrail(a, cx, cz, cx, back + dirZ * -1 + (north ? 7 : -7), y);
                for (int x = x1; x <= x2; x++) { a.set(x, y, back, Blocks.SMOOTH_QUARTZ); if (r.nextInt(3) == 0) a.set(x, y + 1, back, Blocks.IRON_TRAPDOOR.defaultBlockState()); }
                Kit.hangLantern(a, cx, y + 4, cz, 0, Blocks.SOUL_LANTERN);
                a.badLamp(cx - 1, y + 4, cz - 1);
                a.dyingLamp(cx + 1, y + 4, cz + 1);
                a.pulseLight(cx, y + 3, cz, 4, 8);
            }
            case "office" -> {
                Kit.table(a, cx - 1, y, (z1 + z2) / 2, 3, Direction.Axis.X, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_FENCE);
                Kit.chair(a, cx, y, (z1 + z2) / 2 + dirZ, north ? Direction.NORTH : Direction.SOUTH, Blocks.SPRUCE_STAIRS);
                for (int z = z1; z <= z2; z++) if (r.nextInt(3) != 0) Kit.shelf(a, x1, y, z, 3);
                a.set(cx, y + 1, (z1 + z2) / 2, candle(Blocks.CANDLE, 2, true));
                a.flick(cx, y + 1, (z1 + z2) / 2, Arena.FLICK_GUST, 2, 0);
                Kit.bones(a, x2 - 1, y, z1 + 1);
            }
            default -> {
                for (int x = x1; x <= x2; x += 2) for (int h = 0; h < 3; h++) if (r.nextInt(5) != 0) a.set(x, y + h, back, h == 0 ? Blocks.IRON_BARS.defaultBlockState() : Blocks.LIGHT_GRAY_CARPET.defaultBlockState());
                for (int i = 0; i < 4; i++) a.setIfAir(x1 + r.nextInt(8), y, z1 + r.nextInt(8), Blocks.DARK_OAK_PLANKS);
                if (r.nextInt(2) == 0) a.deadLamp(cx, y + 4, (z1 + z2) / 2);
            }
        }
        Kit.webs(a, x1, y, z1, x2, y + 3, z2, 0.15f);
    }

    private static void corridorProps(Arena a, int y) {
        Random r = a.rnd;
        for (int x = X1 + 2; x < X2 - 1; x += 5) {
            int m = r.nextInt(10);
            if (m < 5) a.deadLamp(x, y + 4, 0);
            else if (m < 8) a.badLamp(x, y + 4, 0);
            else a.dyingLamp(x, y + 4, 0);
        }
        // gurneys, wheelchairs, fallen drips
        for (int i = 0; i < 9; i++) {
            int x = X1 + 4 + r.nextInt(X2 - X1 - 8), z = r.nextBoolean() ? -1 : 2;
            if (a.solid(x, y, z) || a.solid(x + 1, y, z)) continue;
            a.set(x, y, z, Blocks.IRON_BARS);
            a.set(x + 1, y, z, Blocks.IRON_BARS);
            a.set(x, y + 1, z, r.nextInt(3) == 0 ? Blocks.RED_CARPET.defaultBlockState() : Blocks.WHITE_CARPET.defaultBlockState());
            a.set(x + 1, y + 1, z, Blocks.WHITE_CARPET);
            if (r.nextInt(3) == 0) a.set(x + 1, y + 2, z, Blocks.SKELETON_SKULL.defaultBlockState());
        }
        for (int i = 0; i < 7; i++) Kit.blood(a, X1 + 3 + r.nextInt(X2 - X1 - 6), y, r.nextInt(4) - 1, 1.2f + r.nextFloat());
        Kit.bloodTrail(a, X1 + 5, 1, X1 + 26, 0, y);
        // wheelchair
        int wx = X1 + 30;
        if (a.air(wx, y, 1)) { a.set(wx, y, 1, stair(Blocks.STONE_BRICK_STAIRS, Direction.WEST, false)); a.set(wx + 1, y, 1, Blocks.IRON_TRAPDOOR.defaultBlockState()); }
        // exit sign: a faint green glow
        a.set(X2 - 1, y + 3, 0, Blocks.LIME_STAINED_GLASS);
        a.pulseLight(X2 - 2, y + 3, 0, 3, 6);
    }

    // ------------------------------------------------------------------ lobby / reception

    private static void lobby(Arena a) {
        Random r = a.rnd;
        // reception hall x -8..8, z 13..25, two floors high
        a.walls(-9, 1, 12, 9, 11, 26, OUTER);
        a.clearBox(-8, 1, 13, 8, 11, 25);
        a.fill(-9, 12, 12, 9, 12, 26, Pal.of(Blocks.GRAY_CONCRETE, 3, Blocks.STONE, 1));
        a.clearBox(-3, 1, 12, 3, 4, 12);
        a.fill(-8, 0, 13, 8, 0, 25, Pal.of(Blocks.POLISHED_ANDESITE, 3, Blocks.LIGHT_GRAY_CONCRETE, 3, Blocks.CALCITE, 1));
        for (int x = -8; x <= 8; x++) for (int z = 13; z <= 25; z++) if (((x + z) & 1) == 0 && r.nextInt(3) != 0) a.set(x, 0, z, Blocks.POLISHED_DIORITE);
        // entrance, red cross
        a.clearBox(-1, 1, 26, 0, 3, 26);
        for (int x = -1; x <= 0; x++) { a.set(x, 1, 26, doorLower(Blocks.IRON_DOOR, Direction.SOUTH, x == -1, x == 0)); a.set(x, 2, 26, doorUpper(Blocks.IRON_DOOR, Direction.SOUTH, x == -1, x == 0)); }
        a.set(-1, 3, 26, Blocks.IRON_BARS);
        a.set(0, 3, 26, Blocks.IRON_BARS);
        for (int dy = 0; dy < 5; dy++) a.set(0, 6 + dy, 27, Blocks.RED_STAINED_GLASS);
        for (int dx = -2; dx <= 2; dx++) a.set(dx, 8, 27, Blocks.RED_STAINED_GLASS);
        a.pulseLight(0, 8, 28, 4, 7);
        // desk, benches, debris
        for (int x = -5; x <= 3; x++) { a.set(x, 1, 17, Blocks.SMOOTH_QUARTZ); a.set(x, 2, 17, slab(Blocks.QUARTZ_SLAB, false)); }
        for (int z = 14; z <= 17; z++) a.set(4, 1, z, Blocks.SMOOTH_QUARTZ);
        a.set(-2, 3, 17, candle(Blocks.WHITE_CANDLE, 3, true));
        a.flick(-2, 3, 17, Arena.FLICK_GUST, 3, 0);
        Kit.chair(a, -3, 1, 15, Direction.SOUTH, Blocks.SPRUCE_STAIRS);
        for (int row = 0; row < 3; row++) {
            Kit.pew(a, -7, 1, 20 + row * 2, 5, Direction.Axis.X, Direction.NORTH, Blocks.SPRUCE_STAIRS, 0.3f);
            Kit.pew(a, 2, 1, 20 + row * 2, 6, Direction.Axis.X, Direction.NORTH, Blocks.SPRUCE_STAIRS, 0.3f);
        }
        for (int i = 0; i < 12; i++) a.setIfAir(-7 + r.nextInt(15), 1, 14 + r.nextInt(11), r.nextBoolean() ? Blocks.LIGHT_GRAY_CARPET.defaultBlockState() : Blocks.COBBLED_DEEPSLATE.defaultBlockState());
        Kit.blood(a, 0, 1, 20, 3.5f);
        Kit.bloodTrail(a, 0, 20, 0, 4, 1);
        Kit.bones(a, 1, 1, 19);
        Kit.hangLantern(a, -5, 11, 20, 4, Blocks.SOUL_LANTERN);
        a.badLamp(4, 11, 21);
        a.dyingLamp(-4, 11, 14);
        a.badLamp(0, 11, 24);
        a.pulseLight(0, 4, 17, 3, 6);
        Kit.webs(a, -8, 1, 13, 8, 5, 25, 0.18f);
        Kit.decay(a, -9, 7, 26, 9, 11, 26, 0.3f, 3);
    }

    // ------------------------------------------------------------------ stairs

    private static void stairs(Arena a) {
        // up, at the east end of the corridor
        a.clearBox(27, 6, -1, 32, 6, 2);
        for (int i = 0; i < 6; i++) {
            for (int z = -1; z <= 2; z++) {
                a.set(26 + i, 1 + i, z, stair(Blocks.POLISHED_ANDESITE_STAIRS, Direction.WEST, false));
                for (int y = 1; y < 1 + i; y++) a.set(26 + i, y, z, Blocks.POLISHED_ANDESITE);
            }
        }
        a.set(-9, 1, -1, Blocks.IRON_BARS);
        a.set(-9, 1, 2, Blocks.IRON_BARS);
    }

    // ------------------------------------------------------------------ morgue

    private static void morgue(Arena a) {
        Random r = a.rnd;
        a.fill(-35, -8, -13, -8, -1, 13, Pal.of(Blocks.WHITE_TERRACOTTA, 4, Blocks.LIGHT_GRAY_TERRACOTTA, 3, Blocks.SMOOTH_QUARTZ, 2, Blocks.CALCITE, 1));
        a.clearBox(-34, -7, -12, -9, -1, 12);
        a.fill(-34, -7, -12, -10, -7, 12, Pal.of(Blocks.LIGHT_GRAY_CONCRETE, 3, Blocks.WHITE_CONCRETE, 3, Blocks.GRAY_CONCRETE, 1));
        // the stair hole is carved after this step, so reopen it
        for (int i = 0; i < 7; i++) for (int z = 0; z <= 1; z++) {
            a.set(-9 - i, -i, z, stair(Blocks.STONE_BRICK_STAIRS, Direction.WEST, false));
            for (int y = -i + 1; y <= 0; y++) a.carve(-9 - i, y, z);
            a.set(-9 - i, -i, z, stair(Blocks.STONE_BRICK_STAIRS, Direction.WEST, false));
        }
        for (int i = 1; i < 7; i++) for (int z = 0; z <= 1; z++) a.set(-9 - i, -i - 1, z, Blocks.STONE_BRICKS);
        a.clearBox(-9, 1, 0, -9, 1, 1);
        // drawer wall, west
        for (int z = -11; z <= 11; z += 2) for (int row = 0; row < 3; row++) {
            int y = -6 + row * 2;
            a.set(-34, y, z, trap(Blocks.IRON_TRAPDOOR, Direction.EAST, false, true));
            a.set(-34, y + 1, z, trap(Blocks.IRON_TRAPDOOR, Direction.EAST, false, true));
        }
        // steel tables with covered bodies
        for (int i = 0; i < 3; i++) for (int j = 0; j < 2; j++) {
            int tx = -28 + j * 8, tz = -8 + i * 8;
            for (int k = 0; k < 3; k++) { a.set(tx + k, -6, tz, Blocks.IRON_BARS); a.set(tx + k, -5, tz, slab(Blocks.SMOOTH_QUARTZ_SLAB, true)); }
            if (r.nextInt(4) != 0) { a.set(tx + 1, -4, tz, Blocks.WHITE_CARPET); a.set(tx + 2, -4, tz, r.nextBoolean() ? Blocks.RED_CARPET.defaultBlockState() : Blocks.WHITE_CARPET.defaultBlockState()); a.set(tx, -4, tz, Blocks.SKELETON_SKULL.defaultBlockState()); }
            Kit.blood(a, tx + 1, -6, tz + 1, 1.8f);
            a.set(tx + 1, -1, tz, Blocks.CHAIN);
        }
        for (int z = -9; z <= 9; z += 6) { a.badLamp(-31, -1, z); a.badLamp(-25, -1, z); a.dyingLamp(-19, -1, z); a.deadLamp(-13, -1, z); }
        a.pulseLight(-22, -3, 0, 2, 5);
        Kit.bloodTrail(a, -30, 11, -16, 0, -6);
        Kit.bones(a, -18, -6, 8);
        for (int x = -33; x <= -12; x += 3) a.set(x, -6, 12, Blocks.CAULDRON);
        // cold room at the far end, a door that is slightly open
        a.fill(-18, -6, -12, -18, -1, -12, Blocks.AIR);
        Kit.webs(a, -34, -6, -12, -10, -1, 12, 0.12f);
    }

    // ------------------------------------------------------------------ roof and ruin

    private static void roof(Arena a) {
        Random r = a.rnd;
        // water tank and chimney
        a.fill(-22, 14, -8, -18, 14, -4, Blocks.STONE_BRICKS);
        for (int x : new int[]{-22, -18}) for (int z : new int[]{-8, -4}) for (int y = 14; y <= 16; y++) a.set(x, y, z, Blocks.OAK_FENCE);
        a.fill(-23, 17, -9, -17, 17, -3, Pal.of(Blocks.SPRUCE_PLANKS, 3, Blocks.DARK_OAK_PLANKS, 1));
        a.fill(-22, 18, -8, -18, 19, -4, Blocks.SPRUCE_PLANKS);
        a.clearBox(-21, 18, -7, -19, 19, -5);
        for (int y = 14; y <= 24; y++) { a.set(20, y, -6, Blocks.BRICKS); a.set(21, y, -6, Blocks.BRICKS); }
        // cross on the roof
        for (int y = 14; y <= 19; y++) a.set(0, y, 0, Blocks.IRON_BLOCK);
        for (int x = -2; x <= 2; x++) a.set(x, 17, 0, Blocks.IRON_BLOCK);
        Kit.decay(a, X1, 14, Z1, X2, 24, Z2, 0.25f, 7);
        for (int i = 0; i < 20; i++) Kit.bones(a, X1 + r.nextInt(70), 13, Z1 + 1 + r.nextInt(21));
    }

    private static void ruin(Arena a) {
        Kit.decay(a, X1, 1, Z1, X2, 12, Z2, 0.22f, 41);
        Kit.age(a, X1, 1, Z1, X2, 13, Z2, Blocks.STONE_BRICKS, Pal.of(Blocks.CRACKED_STONE_BRICKS, 1, Blocks.MOSSY_STONE_BRICKS, 1), 0.3f);
        Kit.webs(a, X1, 1, Z1, X2, 12, Z2, 0.1f);
    }
}
