package com.infinitystones.tensura.skill.ability.extra.fear.scene;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Random;

import static com.infinitystones.tensura.skill.ability.extra.fear.scene.St.*;

/** Abandoned rotten manor: grand hall with a broken staircase, furnished wings, an endless upstairs corridor, doors that lead to the wrong room. */
public final class Mansion extends Scene {
    private static final int X1 = -20, X2 = 19, Z1 = -14, Z2 = 13;
    private static final Pal LOWER = Pal.of(Blocks.STONE_BRICKS, 5, Blocks.CRACKED_STONE_BRICKS, 3, Blocks.MOSSY_STONE_BRICKS, 3, Blocks.COBBLESTONE, 1);
    private static final Pal UPPER = Pal.of(Blocks.DARK_OAK_PLANKS, 5, Blocks.SPRUCE_PLANKS, 2, Blocks.STRIPPED_DARK_OAK_LOG, 1);
    private static final Pal INNER = Pal.of(Blocks.DARK_OAK_PLANKS, 6, Blocks.SPRUCE_PLANKS, 2, Blocks.STRIPPED_SPRUCE_LOG, 1);
    private static final Pal FLOOR = Pal.of(Blocks.DARK_OAK_PLANKS, 6, Blocks.SPRUCE_PLANKS, 3, Blocks.COARSE_DIRT, 1);

    @Override
    public String id() { return "mansion"; }

    @Override
    public int minY() { return -8; }

    @Override
    public int maxY() { return 36; }

    @Override
    public void plan(Arena a, Ctx c, List<Runnable> s) {
        a.spawn(0, 1, 34);
        s.add(() -> yard(a));
        s.add(() -> shell(a));
        s.add(() -> hall(a));
        s.add(() -> partitions(a));
        s.add(() -> groundRooms(a));
        s.add(() -> upperRooms(a));
        s.add(() -> corridor(a));
        s.add(() -> roof(a));
        s.add(() -> ruin(a));
        s.add(() -> wrongDoors(a));
    }

    // ------------------------------------------------------------------ yard

    private static void yard(Arena a) {
        Random r = a.rnd;
        Kit.groundFlat(a, Pal.of(Blocks.PODZOL, 5, Blocks.COARSE_DIRT, 4, Blocks.MOSS_BLOCK, 2, Blocks.MUD, 1, Blocks.DIRT, 1), Pal.of(Blocks.DIRT, 3, Blocks.COARSE_DIRT, 1), 5);
        a.fill(X1, 0, Z1, X2, 0, Z2, FLOOR);
        for (int z = 14; z <= 44; z++)
            for (int x = -1; x <= 1; x++) if (r.nextInt(7) != 0) a.set(x, 0, z, Pal.of(Blocks.STONE_BRICKS, 3, Blocks.CRACKED_STONE_BRICKS, 3, Blocks.COBBLESTONE, 2, Blocks.MOSSY_COBBLESTONE, 2).pick(r));
        // perimeter fence with a gate
        Kit.fenceLine(a, -42, -42, 42, -42, 1, 3, Blocks.IRON_BARS, Blocks.STONE_BRICKS, 5, 0.05f);
        Kit.fenceLine(a, -42, -42, -42, 42, 1, 3, Blocks.IRON_BARS, Blocks.STONE_BRICKS, 5, 0.05f);
        Kit.fenceLine(a, 42, -42, 42, 42, 1, 3, Blocks.IRON_BARS, Blocks.STONE_BRICKS, 5, 0.05f);
        Kit.fenceLine(a, -42, 42, -5, 42, 1, 3, Blocks.IRON_BARS, Blocks.STONE_BRICKS, 5, 0.05f);
        Kit.fenceLine(a, 5, 42, 42, 42, 1, 3, Blocks.IRON_BARS, Blocks.STONE_BRICKS, 5, 0.05f);
        for (int sx : new int[]{-4, 4}) {
            for (int y = 1; y <= 6; y++) a.set(sx, y, 42, y == 6 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
            a.set(sx, 7, 42, Blocks.SKELETON_SKULL.defaultBlockState());
        }
        a.line(-3, 1, 42, -3, 4, 42, Blocks.IRON_BARS);
        a.line(-2, 1, 42, -2, 5, 42, Blocks.IRON_BARS);
        a.line(3, 1, 42, 3, 5, 42, Blocks.IRON_BARS);
        a.line(-3, 5, 42, 3, 5, 42, Blocks.IRON_BARS);
        a.line(1, 1, 42, 3, 1, 42, Blocks.IRON_BARS);
        // dead trees
        for (int i = 0; i < 26; i++) {
            int x = r.nextInt(78) - 39, z = r.nextInt(78) - 39;
            if ((Math.abs(x) < 26 && z > -20 && z < 20) || Math.abs(x) < 4 && z > 0) continue;
            Kit.deadTree(a, x, 1, z, 6 + r.nextInt(7), Blocks.DARK_OAK_LOG);
        }
        // hedges eaten by rot
        for (int k = 0; k < 6; k++) {
            int hx = -36 + k * 6, hz = 24 + (k % 2) * 8;
            if (Math.abs(hx) < 6) continue;
            for (int i = 0; i < 6; i++) {
                if (r.nextInt(4) == 0) continue;
                int hh = 1 + r.nextInt(2);
                for (int y = 1; y <= hh + 1; y++) a.setIfAir(hx + i, y, hz, leaf(Blocks.DARK_OAK_LEAVES));
            }
        }
        // lamp posts along the path
        for (int z : new int[]{20, 32}) {
            Kit.lampPost(a, -3, 1, z, 3, Blocks.DARK_OAK_FENCE);
            if (z == 20) Kit.lampPost(a, 3, 1, z, 3, Blocks.DARK_OAK_FENCE);
            else { a.set(3, 1, z, Blocks.DARK_OAK_FENCE); a.set(3, 2, z, Blocks.DARK_OAK_FENCE); }
        }
        // dry fountain
        int fx = -16, fz = 24;
        a.ring(fx, 1, fz, 4f, 0.8f, Pal.of(Blocks.STONE_BRICKS, 3, Blocks.MOSSY_STONE_BRICKS, 3, Blocks.CRACKED_STONE_BRICKS, 2));
        a.ring(fx, 2, fz, 4f, 0.6f, Pal.of(Blocks.STONE_BRICK_SLAB, 2, Blocks.AIR, 1));
        a.disc(fx, 0, fz, 3f, Pal.of(Blocks.MUD, 3, Blocks.PACKED_MUD, 2));
        for (int y = 1; y <= 3; y++) a.set(fx, y, fz, y == 3 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : Blocks.MOSSY_STONE_BRICKS.defaultBlockState());
        Kit.bones(a, fx + 1, 1, fz + 1);
        Kit.webs(a, fx - 4, 1, fz - 4, fx + 4, 4, fz + 4, 0.35f);
    }

    private static void ruin(Arena a) {
        // the looping corridor (x -4..3, z < 4, upper floor) is left untouched so every period stays identical
        int[][] zones = {{X1, 1, Z1, -6, 12, Z2}, {5, 1, Z1, X2, 12, Z2}, {-4, 1, Z1, 3, 5, Z2}, {-4, 7, 5, 3, 12, Z2}};
        int i = 0;
        for (int[] z : zones) {
            Kit.decay(a, z[0], z[1], z[2], z[3], z[4], z[5], 0.2f, 11 + i);
            Kit.age(a, z[0], z[1], z[2], z[3], z[4], z[5], Blocks.STONE_BRICKS, Pal.of(Blocks.CRACKED_STONE_BRICKS, 1, Blocks.MOSSY_STONE_BRICKS, 1), 0.3f);
            Kit.webs(a, z[0], z[1], z[2], z[3], z[4], z[5], 0.18f);
            Kit.decay(a, z[0], 6, z[2], z[3], 6, z[5], 0.28f, 23 + i++);
        }
    }

    // ------------------------------------------------------------------ shell

    private static void shell(Arena a) {
        a.walls(X1, 1, Z1, X2, 5, Z2, LOWER);
        a.walls(X1, 7, Z1, X2, 11, Z2, UPPER);
        a.fill(X1, 6, Z1, X2, 6, Z2, FLOOR);
        a.walls(X1 - 0, 6, Z1, X2, 6, Z2, Pal.one(Blocks.STRIPPED_DARK_OAK_LOG));
        a.fill(X1, 12, Z1, X2, 12, Z2, Pal.of(Blocks.DARK_OAK_PLANKS, 3, Blocks.SPRUCE_PLANKS, 1));
        a.walls(X1, 12, Z1, X2, 12, Z2, Pal.one(Blocks.STRIPPED_DARK_OAK_LOG));
        for (int cx : new int[]{X1, X2})
            for (int cz : new int[]{Z1, Z2})
                for (int y = 1; y <= 12; y++) a.set(cx, y, cz, axis(Blocks.DARK_OAK_LOG, Direction.Axis.Y));
        for (int x = X1 + 8; x < X2; x += 8) for (int y = 1; y <= 12; y++) { a.set(x, y, Z2, axis(Blocks.DARK_OAK_LOG, Direction.Axis.Y)); a.set(x, y, Z1, axis(Blocks.DARK_OAK_LOG, Direction.Axis.Y)); }
        Kit.windows(a, X1, Z1, X2, Z2, 2, 3, 5, 0, 0.3f);
        Kit.windows(a, X1, Z1, X2, Z2, 8, 3, 5, 0, 0.35f);
        // porch
        for (int x : new int[]{-5, 4}) for (int y = 1; y <= 6; y++) { a.set(x, y, 17, axis(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.Y)); }
        a.fill(-6, 7, 14, 5, 7, 18, Pal.of(Blocks.DARK_OAK_SLAB, 1));
        a.fill(-5, 6, 14, 4, 6, 17, Pal.one(Blocks.DARK_OAK_PLANKS));
        for (int x = -3; x <= 2; x++) a.set(x, 0, 18, stair(Blocks.COBBLESTONE_STAIRS, Direction.SOUTH, false));
        for (int x = -3; x <= 2; x++) a.set(x, 0, 19, stair(Blocks.COBBLESTONE_STAIRS, Direction.SOUTH, false));
        a.fill(-4, 0, 14, 3, 0, 17, Pal.of(Blocks.STONE_BRICKS, 2, Blocks.MOSSY_STONE_BRICKS, 1));
        // front doors
        Kit.door(a, -1, 1, Z2, Direction.SOUTH, Blocks.DARK_OAK_DOOR, true, true);
        Kit.door(a, 0, 1, Z2, Direction.SOUTH, Blocks.DARK_OAK_DOOR, false, true);
        a.carve(-1, 3, Z2);
        a.carve(0, 3, Z2);
        a.set(-1, 3, Z2, Blocks.IRON_BARS);
        a.set(0, 3, Z2, Blocks.IRON_BARS);
        a.set(0, 1, 15, candle(Blocks.CANDLE, 3, true));
        a.flick(0, 1, 15, Arena.FLICK_GUST, 3, 0);
    }

    // ------------------------------------------------------------------ hall

    private static void hall(Arena a) {
        // checkerboard floor, chipped
        for (int z = -13; z <= 12; z++)
            for (int x = -4; x <= 3; x++) {
                a.set(x, 0, z, ((x + z) & 1) == 0 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : Blocks.CALCITE.defaultBlockState());
                if (a.rnd.nextInt(14) == 0) a.set(x, 0, z, Blocks.COBBLED_DEEPSLATE);
            }
        // atrium hole
        a.clearBox(-4, 6, 5, 3, 6, 12);
        a.clearBox(-4, 12, 5, 3, 12, 12);
        // grand staircase on the west side, broken railings
        for (int i = 0; i < 6; i++) {
            int z = 10 - i;
            for (int x = -4; x <= -3; x++) {
                a.set(x, 1 + i, z, stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, false));
                for (int y = 1; y < 1 + i; y++) a.set(x, y, z, Blocks.DARK_OAK_PLANKS);
            }
        }
        for (int z = 5; z <= 11; z++) if (a.rnd.nextInt(5) != 0) a.set(-2, 2 + (10 - z) + (z > 10 ? 1 : 0), z, Blocks.DARK_OAK_FENCE);
        for (int x = -2; x <= 3; x++) if (x != 0 && a.rnd.nextInt(6) != 0) a.set(x, 7, 4, Blocks.DARK_OAK_FENCE);
        // upper gallery rail on the open side
        a.set(-2, 7, 12, Blocks.AIR);
        // chandelier, one half fallen
        Kit.chandelier(a, 0, 11, 8, 3, 2);
        a.fill(-1, 1, 7, 2, 1, 9, Pal.of(Blocks.AIR, 3, Blocks.IRON_BARS, 1));
        a.set(-1, 1, 8, Blocks.IRON_BARS);
        a.set(1, 1, 8, candle(Blocks.CANDLE, 2, false));
        Kit.blood(a, 0, 1, 6, 3f);
        Kit.bones(a, 2, 1, 6);
        // dusty portrait wall (trapdoor frames) and hanging armour stands made of blocks
        for (int z : new int[]{-8, -2, 2}) {
            a.set(-4, 3, z, trap(Blocks.DARK_OAK_TRAPDOOR, Direction.EAST, false, true));
            a.set(-4, 4, z, trap(Blocks.DARK_OAK_TRAPDOOR, Direction.EAST, false, true));
            a.set(3, 3, z + 2, trap(Blocks.SPRUCE_TRAPDOOR, Direction.WEST, false, true));
        }
        for (int z : new int[]{-10, -3}) {
            a.set(3, 1, z, Blocks.IRON_BLOCK);
            a.set(3, 2, z, Blocks.CHAIN);
            a.set(3, 3, z, Blocks.SKELETON_SKULL.defaultBlockState());
        }
        a.pulseLight(0, 4, -6, 3, 6);
        a.pulseLight(-1, 3, 6, 3, 5);
        Kit.soulLantern(a, 3, 4, -10, false);
        a.set(3, 3, -10, Blocks.DARK_OAK_PLANKS);
        for (int x = -3; x <= 3; x += 2) for (int z : new int[]{-12, -6, 0}) if (a.rnd.nextInt(3) == 0) a.set(x, 1, z, Blocks.COBWEB);
    }

    // ------------------------------------------------------------------ partitions and rooms

    private static void partitions(Arena a) {
        for (int x : new int[]{-5, 4}) {
            a.fill(x, 1, Z1, x, 5, Z2, INNER);
            a.fill(x, 7, Z1, x, 11, Z2, INNER);
        }
        a.fill(X1, 1, 0, -5, 5, 0, INNER);
        a.fill(4, 1, 0, X2, 5, 0, INNER);
        a.fill(X1, 7, -5, -5, 11, -5, INNER);
        a.fill(4, 7, -5, X2, 11, -5, INNER);
        a.fill(X1, 7, 4, -5, 11, 4, INNER);
        a.fill(4, 7, 4, X2, 11, 4, INNER);
        for (int x : new int[]{-5, 4}) {
            for (int z : new int[]{8, -4}) doorway(a, x, 1, z, x < 0 ? Direction.WEST : Direction.EAST, Direction.Axis.Z);
            for (int z : new int[]{-9, 0, 8}) doorway(a, x, 7, z, x < 0 ? Direction.WEST : Direction.EAST, Direction.Axis.Z);
        }
        doorway(a, -12, 1, 0, Direction.NORTH, Direction.Axis.X);
        doorway(a, 11, 1, 0, Direction.NORTH, Direction.Axis.X);
    }

    private static void doorway(Arena a, int x, int y, int z, Direction facing, Direction.Axis along) {
        for (int w = 0; w < 2; w++) {
            int dx = along == Direction.Axis.X ? w : 0, dz = along == Direction.Axis.Z ? w : 0;
            a.carve(x + dx, y, z + dz);
            a.carve(x + dx, y + 1, z + dz);
            a.carve(x + dx, y + 2, z + dz);
            a.set(x + dx, y + 3, z + dz, Blocks.DARK_OAK_PLANKS);
        }
        int r = a.rnd.nextInt(4);
        if (r == 0) return;
        a.set(x, y, z, doorLower(Blocks.DARK_OAK_DOOR, facing, r != 1, true));
        a.set(x, y + 1, z, doorUpper(Blocks.DARK_OAK_DOOR, facing, r != 1, true));
    }

    private static void groundRooms(Arena a) {
        Random r = a.rnd;
        // dining room (x -19..-6, z 1..12)
        Kit.table(a, -16, 1, 6, 6, Direction.Axis.Z, Blocks.DARK_OAK_SLAB, Blocks.DARK_OAK_FENCE);
        for (int z = 6; z <= 11; z++) {
            if (r.nextInt(4) != 0) Kit.chair(a, -17, 1, z, Direction.EAST, Blocks.SPRUCE_STAIRS);
            if (r.nextInt(3) != 0) Kit.chair(a, -14, 1, z, Direction.WEST, Blocks.SPRUCE_STAIRS);
        }
        a.set(-16, 2, 7, candle(Blocks.CANDLE, 4, true));
        a.flick(-16, 2, 7, Arena.FLICK_GUST, 4, 0);
        a.set(-16, 2, 10, candle(Blocks.RED_CANDLE, 3, true));
        a.flick(-16, 2, 10, Arena.FLICK_GUST, 3, 0);
        Kit.blood(a, -15, 1, 8, 2.6f);
        Kit.bones(a, -16, 1, 9);
        Kit.shelf(a, -19, 1, 3, 3);
        Kit.shelf(a, -19, 1, 4, 3);
        a.set(-18, 1, 11, Blocks.CAULDRON);
        a.pulseLight(-15, 4, 8, 3, 5);
        // kitchen (x -19..-6, z -13..-1)
        for (int x = -19; x <= -10; x++) { a.set(x, 1, -13, r.nextInt(8) == 0 ? Blocks.AIR.defaultBlockState() : Blocks.SMOOTH_STONE.defaultBlockState()); if (r.nextInt(3) == 0) a.set(x, 2, -13, Blocks.CAULDRON); }
        a.set(-19, 1, -12, Blocks.COMPOSTER);
        Kit.table(a, -15, 1, -7, 5, Direction.Axis.X, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_FENCE);
        a.set(-14, 2, -7, Blocks.CAKE);
        Kit.bones(a, -13, 1, -6);
        Kit.blood(a, -12, 1, -8, 3.4f);
        Kit.bloodTrail(a, -12, -8, -6, -4, 1);
        a.set(-18, 1, -3, Blocks.IRON_BARS);
        a.set(-18, 2, -3, Blocks.CHAIN);
        a.set(-18, 3, -3, Blocks.CHAIN);
        a.deadLamp(-17, 5, -7);
        a.badLamp(-12, 5, -6);
        // library (x 5..18, z 1..12)
        for (int x = 6; x <= 17; x += 3) { Kit.shelf(a, x, 1, 12, 4); Kit.shelf(a, x + 1, 1, 12, 4); }
        for (int z = 2; z <= 11; z++) if (r.nextInt(3) != 0) Kit.shelf(a, 18, 1, z, 3 + r.nextInt(2));
        a.set(12, 1, 7, Blocks.LECTERN.defaultBlockState());
        Kit.chair(a, 12, 1, 5, Direction.NORTH, Blocks.DARK_OAK_STAIRS);
        a.set(12, 2, 7, candle(Blocks.WHITE_CANDLE, 2, true));
        a.flick(12, 2, 7, Arena.FLICK_GUST, 2, 0);
        a.set(14, 1, 5, Blocks.BOOKSHELF);
        a.set(8, 1, 4, Blocks.AIR);
        a.set(9, 1, 5, Blocks.BOOKSHELF);
        Kit.bones(a, 8, 1, 8);
        a.pulseLight(10, 3, 6, 2, 4);
        for (int i = 0; i < 6; i++) a.setIfAir(7 + r.nextInt(10), 1, 2 + r.nextInt(9), Blocks.BROWN_CARPET);
        // parlor (x 5..18, z -13..-1) with a piano and a mirror wall
        a.fill(8, 1, -12, 12, 1, -12, Blocks.BLACK_CONCRETE);
        a.fill(8, 2, -12, 12, 2, -12, Pal.of(Blocks.BLACK_CONCRETE, 3, Blocks.AIR, 1));
        a.set(10, 1, -10, stair(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, false));
        for (int x = 8; x <= 12; x++) a.set(x, 3, -12, slab(Blocks.SMOOTH_QUARTZ_SLAB, true));
        for (int z = -12; z <= -3; z++) a.setIfAir(18, 2, z, r.nextInt(3) == 0 ? Blocks.AIR.defaultBlockState() : Blocks.GLASS_PANE.defaultBlockState());
        Kit.chair(a, 14, 1, -6, Direction.WEST, Blocks.DARK_OAK_STAIRS);
        Kit.chair(a, 14, 1, -4, Direction.WEST, Blocks.DARK_OAK_STAIRS);
        a.set(16, 1, -5, Blocks.DARK_OAK_PLANKS);
        a.set(16, 2, -5, candle(Blocks.BLACK_CANDLE, 3, true));
        a.flick(16, 2, -5, Arena.FLICK_GUST, 3, 0);
        Kit.blood(a, 14, 1, -8, 2.2f);
        a.dyingLamp(9, 5, -7);
    }

    private static void upperRooms(Arena a) {
        Random r = a.rnd;
        // west bedroom A (x -19..-6, z -13..-6)
        Kit.bed(a, -18, 7, -12, Direction.EAST, Blocks.RED_BED);
        a.set(-18, 7, -11, Blocks.DARK_OAK_PLANKS);
        a.set(-18, 8, -11, candle(Blocks.CANDLE, 2, true));
        a.flick(-18, 8, -11, Arena.FLICK_GUST, 2, 0);
        a.fill(-14, 7, -13, -11, 7, -13, Pal.of(Blocks.DARK_OAK_PLANKS, 2, Blocks.BOOKSHELF, 1));
        Kit.blood(a, -16, 7, -10, 3f);
        // nursery (x -19..-6, z -4..3): cradles, toys, a rocking chair that rocks (stairs + slabs), crayon on the wall
        a.set(-17, 7, 0, trap(Blocks.OAK_TRAPDOOR, Direction.EAST, false, false));
        a.set(-17, 7, 1, trap(Blocks.OAK_TRAPDOOR, Direction.EAST, false, false));
        a.set(-16, 7, 0, slab(Blocks.SPRUCE_SLAB, false));
        Kit.skull(a, -17, 8, 0, Blocks.SKELETON_SKULL);
        a.set(-10, 7, 1, stair(Blocks.OAK_STAIRS, Direction.WEST, false));
        a.set(-10, 7, 0, Blocks.WHITE_CARPET);
        for (int i = 0; i < 5; i++) a.setIfAir(-18 + r.nextInt(11), 7, -3 + r.nextInt(6), r.nextBoolean() ? Blocks.PINK_CARPET.defaultBlockState() : Blocks.WHITE_CARPET.defaultBlockState());
        a.set(-8, 7, -3, Blocks.JACK_O_LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, Direction.WEST));
        a.set(-8, 7, 2, Blocks.CARVED_PUMPKIN.defaultBlockState());
        a.badLamp(-13, 11, 0);
        // west bedroom B (z 5..12)
        Kit.bed(a, -18, 7, 11, Direction.EAST, Blocks.BLACK_BED);
        Kit.bed(a, -18, 7, 8, Direction.EAST, Blocks.GRAY_BED);
        a.fill(-17, 7, 9, -17, 7, 10, Pal.of(Blocks.AIR, 1, Blocks.DARK_OAK_PLANKS, 1));
        Kit.bloodTrail(a, -18, 7, -8, 9, 7);
        // east master bedroom (x 5..18, z -13..-6)
        Kit.bed(a, 16, 7, -12, Direction.WEST, Blocks.PURPLE_BED);
        Kit.bed(a, 16, 7, -10, Direction.WEST, Blocks.PURPLE_BED);
        a.set(17, 7, -11, Blocks.DARK_OAK_PLANKS);
        a.set(17, 8, -11, candle(Blocks.PURPLE_CANDLE, 3, true));
        a.flick(17, 8, -11, Arena.FLICK_GUST, 3, 0);
        a.fill(6, 7, -13, 9, 9, -13, Pal.of(Blocks.DARK_OAK_PLANKS, 3, Blocks.DARK_OAK_TRAPDOOR, 1));
        a.fill(10, 7, -8, 12, 7, -7, Blocks.RED_CARPET);
        // bathroom (x 5..18, z -4..3)
        for (int x = 6; x <= 9; x++) { a.set(x, 7, 2, Blocks.CAULDRON); }
        a.set(16, 7, 0, Blocks.CAULDRON);
        a.fill(12, 7, -3, 17, 7, -1, Pal.of(Blocks.WHITE_CONCRETE_POWDER, 1, Blocks.CALCITE, 2, Blocks.LIGHT_GRAY_CARPET, 1));
        a.fill(14, 7, -2, 16, 7, -2, Blocks.RED_CONCRETE);
        a.fill(14, 8, -2, 16, 8, -2, Pal.of(Blocks.RED_CARPET, 1));
        Kit.blood(a, 10, 7, 0, 3f);
        Kit.bones(a, 15, 7, -3);
        a.dyingLamp(11, 11, -1);
        // study (z 5..12)
        a.fill(6, 7, 11, 17, 7, 11, Pal.of(Blocks.DARK_OAK_SLAB, 1));
        Kit.chair(a, 12, 7, 10, Direction.SOUTH, Blocks.DARK_OAK_STAIRS);
        a.set(12, 8, 11, candle(Blocks.CANDLE, 4, true));
        a.flick(12, 8, 11, Arena.FLICK_GUST, 4, 0);
        for (int x = 6; x <= 17; x += 2) Kit.shelf(a, x, 7, 5, 4);
        a.pulseLight(12, 9, 9, 2, 4);
    }

    // ------------------------------------------------------------------ endless corridor (period 5, x -4..3, z -13..3)

    private static void corridor(Arena a) {
        for (int z = -13; z <= 3; z++) {
            int m = Math.floorMod(z, 5);
            for (int x = -4; x <= 3; x++) {
                if (x >= -1 && x <= 0) a.set(x, 6, z, Blocks.RED_WOOL);
                if (x >= -1 && x <= 0 && m == 2) a.set(x, 6, z, Blocks.BLACK_WOOL);
                if (m == 0 && (x == -4 || x == 3)) {
                    for (int y = 7; y <= 11; y++) a.set(x, y, z, y == 11 || y == 7 ? Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState() : Blocks.DARK_OAK_LOG.defaultBlockState());
                }
                if (m == 2 && (x == -4 || x == 3)) {
                    a.set(x, 8, z, trap(Blocks.DARK_OAK_TRAPDOOR, x < 0 ? Direction.EAST : Direction.WEST, false, true));
                    a.set(x, 9, z, trap(Blocks.DARK_OAK_TRAPDOOR, x < 0 ? Direction.EAST : Direction.WEST, false, true));
                }
                if (m == 4 && (x == -3 || x == 2)) {
                    a.set(x, 7, z, Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.CANDLES, 3).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, false));
                    if (a.get(x, 6, z) != null) a.set(x, 6, z, Blocks.DARK_OAK_PLANKS);
                }
            }
            if (m == 2) {
                a.set(-2, 7, z, Blocks.BLACK_CARPET);
                a.set(1, 7, z, Blocks.RED_CARPET);
                a.set(-1, 11, z, Blocks.CHAIN);
                a.set(-1, 10, z, lantern(Blocks.SOUL_LANTERN, true));
            }
        }
        // the loop: leaving through the north end drops you back two periods
        a.trigger(-4, 7, -13, 3, 7, -11, 0, 0, 10);
        a.pulseLight(0, 9, -5, 3, 5);
        a.pulseLight(0, 9, 0, 3, 5);
        Kit.soulLantern(a, 2, 7, -10, false);
    }

    // ------------------------------------------------------------------ roof, attic

    private static void roof(Arena a) {
        Kit.roof(a, X1, Z1, X2, Z2, 13, true, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Pal.of(Blocks.DARK_OAK_PLANKS, 4, Blocks.SPRUCE_PLANKS, 1));
        // chimneys
        for (int cx : new int[]{-12, 11}) {
            for (int y = 7; y <= 26; y++) { a.set(cx, y, -8, Blocks.BRICKS); a.set(cx + 1, y, -8, Blocks.BRICKS); a.set(cx, y, -7, Blocks.BRICKS); a.set(cx + 1, y, -7, Blocks.BRICKS); }
            a.clearBox(cx, 27, -8, cx, 27, -8);
            a.set(cx, 25, -8, Blocks.BRICKS);
        }
        Random r = a.rnd;
        // attic junk and dolls
        for (int i = 0; i < 20; i++) {
            int x = -17 + r.nextInt(34), z = -4 + r.nextInt(9);
            if (r.nextInt(3) == 0) { a.setIfAir(x, 13, z, Blocks.DARK_OAK_PLANKS); a.setIfAir(x, 14, z, Blocks.DARK_OAK_TRAPDOOR); }
            else if (r.nextInt(2) == 0) Kit.bones(a, x, 13, z);
            else a.setIfAir(x, 13, z, Blocks.WHITE_CARPET);
        }
        for (int x = -16; x <= 16; x += 8) { a.set(x, 13, -1, Blocks.OAK_FENCE); a.set(x, 14, -1, Blocks.CARVED_PUMPKIN); }
        Kit.hangLantern(a, 0, 19, 0, 2, Blocks.SOUL_LANTERN);
        Kit.webs(a, X1, 13, Z1, X2, 24, Z2, 0.22f);
        Kit.decay(a, X1 - 1, 13, Z1 - 1, X2 + 1, 27, Z2 + 1, 0.23f, 31);
    }

    // ------------------------------------------------------------------ doors to the wrong room

    private static void wrongDoors(Arena a) {
        // stepping into the dining room or the library from the hall lands you in the kitchen / parlor
        a.trigger(-7, 1, 8, -6, 3, 9, -8.5, 1, -3.5, 90f);
        a.trigger(6, 1, 8, 7, 3, 9, 9.5, 1, -3.5, 270f);
        // upstairs the nursery pulls you into the south bedroom
        a.trigger(-7, 7, 0, -6, 9, 1, -8.5, 7, 9.5, 90f);
    }
}
