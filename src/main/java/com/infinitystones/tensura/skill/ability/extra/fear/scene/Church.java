package com.infinitystones.tensura.skill.ability.extra.fear.scene;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;
import java.util.Random;

import static com.infinitystones.tensura.skill.ability.extra.fear.scene.St.*;

/** Desecrated gothic church: broken pews, an inverted cross behind a candlelit altar, a bell tower, a crypt full of opened sarcophagi, a graveyard outside. */
public final class Church extends Scene {
    private static final Pal STONE = Pal.of(Blocks.STONE_BRICKS, 5, Blocks.CRACKED_STONE_BRICKS, 3, Blocks.MOSSY_STONE_BRICKS, 3, Blocks.DEEPSLATE_BRICKS, 1);
    private static final Pal[] GLASS = {Pal.one(Blocks.RED_STAINED_GLASS_PANE), Pal.one(Blocks.PURPLE_STAINED_GLASS_PANE), Pal.one(Blocks.BLUE_STAINED_GLASS_PANE), Pal.one(Blocks.ORANGE_STAINED_GLASS_PANE)};

    @Override
    public String id() { return "church"; }

    @Override
    public int minY() { return -10; }

    @Override
    public int maxY() { return 40; }

    @Override
    public void plan(Arena a, Ctx c, List<Runnable> s) {
        a.spawn(0, 1, 36);
        s.add(() -> yard(a));
        s.add(() -> nave(a));
        s.add(() -> apse(a));
        s.add(() -> tower(a));
        s.add(() -> pews(a));
        s.add(() -> altar(a));
        s.add(() -> crypt(a));
        s.add(() -> cemetery(a));
        s.add(() -> ruin(a));
    }

    private static void yard(Arena a) {
        Random r = a.rnd;
        Kit.groundFlat(a, Pal.of(Blocks.PODZOL, 4, Blocks.COARSE_DIRT, 4, Blocks.MOSS_BLOCK, 2, Blocks.MUD, 1), Pal.of(Blocks.DIRT, 3, Blocks.STONE, 1), 8);
        for (int z = 24; z <= 47; z++) for (int x = -2; x <= 2; x++) if (r.nextInt(6) != 0) a.set(x, 0, z, Pal.of(Blocks.STONE_BRICKS, 3, Blocks.CRACKED_STONE_BRICKS, 3, Blocks.COBBLESTONE, 2).pick(r));
        Kit.fenceLine(a, -44, -44, 44, -44, 1, 3, Blocks.IRON_BARS, Blocks.STONE_BRICKS, 5, 0.06f);
        Kit.fenceLine(a, -44, -44, -44, 44, 1, 3, Blocks.IRON_BARS, Blocks.STONE_BRICKS, 5, 0.06f);
        Kit.fenceLine(a, 44, -44, 44, 44, 1, 3, Blocks.IRON_BARS, Blocks.STONE_BRICKS, 5, 0.06f);
        Kit.fenceLine(a, -44, 44, -4, 44, 1, 3, Blocks.IRON_BARS, Blocks.STONE_BRICKS, 5, 0.06f);
        Kit.fenceLine(a, 4, 44, 44, 44, 1, 3, Blocks.IRON_BARS, Blocks.STONE_BRICKS, 5, 0.06f);
        for (int sx : new int[]{-4, 4}) { for (int y = 1; y <= 5; y++) a.set(sx, y, 44, Blocks.MOSSY_STONE_BRICKS); Kit.soulLantern(a, sx, 6, 44, false); }
        for (int i = 0; i < 18; i++) {
            int x = r.nextInt(84) - 42, z = r.nextInt(84) - 42;
            if ((Math.abs(x) < 13 && z > -34 && z < 28) || (Math.abs(x) < 4 && z > 0)) continue;
            Kit.deadTree(a, x, 1, z, 6 + r.nextInt(7), Blocks.DARK_OAK_LOG);
        }
        Kit.lampPost(a, -4, 1, 28, 3, Blocks.DARK_OAK_FENCE);
        Kit.lampPost(a, 4, 1, 32, 3, Blocks.DARK_OAK_FENCE);
    }

    // ------------------------------------------------------------------ building

    private static void nave(Arena a) {
        Random r = a.rnd;
        // floor
        for (int x = -7; x <= 7; x++) for (int z = -22; z <= 14; z++)
            a.set(x, 0, z, ((x + z) & 1) == 0 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
        // outer aisle walls and south wall
        a.fill(-7, 1, -22, -7, 8, 14, STONE);
        a.fill(7, 1, -22, 7, 8, 14, STONE);
        a.fill(-7, 1, 14, 7, 8, 14, STONE);
        // buttresses outside
        for (int z = -18; z <= 10; z += 4) for (int sx : new int[]{-8, 8}) for (int y = 1; y <= 7; y++) a.set(sx, y, z, y > 5 ? Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, sx < 0 ? Direction.WEST : Direction.EAST) : STONE.pick(r));
        // arcade: pillars, lintel, clerestory
        for (int sx : new int[]{-4, 4}) {
            a.fill(sx, 8, -22, sx, 8, 13, Blocks.STONE_BRICKS);
            a.fill(sx, 9, -22, sx, 12, 13, STONE);
            for (int z = -20; z <= 12; z += 4) {
                for (int y = 1; y <= 8; y++) a.set(sx, y, z, y == 1 || y == 8 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        // aisle roofs
        for (int sx : new int[]{-1, 1}) {
            for (int i = 0; i < 3; i++) {
                int x = sx * (7 - i);
                for (int z = -22; z <= 14; z++) a.set(x, 9 + i, z, stair(Blocks.STONE_BRICK_STAIRS, sx < 0 ? Direction.WEST : Direction.EAST, false));
            }
        }
        // nave roof, ridge along z
        Kit.roof(a, -4, -22, 4, 14, 13, false, Blocks.DEEPSLATE_TILE_STAIRS, Blocks.DEEPSLATE_TILE_SLAB, STONE);
        // windows: clerestory and aisle
        for (int z = -18; z <= 10; z += 4) {
            for (int sx : new int[]{-4, 4}) for (int y = 10; y <= 12; y++) a.set(sx, y, z, GLASS[r.nextInt(4)].first());
            for (int sx : new int[]{-7, 7}) {
                for (int y = 3; y <= 6; y++) {
                    if (r.nextInt(5) == 0) a.carve(sx, y, z);
                    else a.set(sx, y, z, y == 6 ? GLASS[r.nextInt(4)].first() : GLASS[(z / 4 + 8) % 4].first());
                }
                a.pulseLight(sx < 0 ? -9 : 9, 4, z + 2, 2, 5);
            }
        }
        // entrance wall to the tower
        a.carve(-1, 1, 14); a.carve(0, 1, 14); a.carve(1, 1, 14);
        for (int x = -1; x <= 1; x++) for (int y = 1; y <= 5; y++) a.carve(x, y, 14);
        a.set(-1, 1, 14, doorLower(Blocks.DARK_OAK_DOOR, Direction.NORTH, true, false));
        a.set(-1, 2, 14, doorUpper(Blocks.DARK_OAK_DOOR, Direction.NORTH, true, false));
        // hanging chandeliers, one fallen
        Kit.chandelier(a, 0, 12, -2, 4, 2);
        Kit.chandelier(a, 0, 12, 8, 5, 2);
        a.fill(-1, 1, -10, 1, 1, -9, Pal.of(Blocks.IRON_BARS, 1, Blocks.AIR, 2));
        Kit.blood(a, 0, 1, -9, 2.5f);
        a.pulseLight(0, 6, -2, 3, 6);
        a.pulseLight(0, 6, 8, 3, 5);
    }

    private static void apse(Arena a) {
        for (int z = -29; z <= -22; z++)
            for (int x = -7; x <= 7; x++) {
                double d = Math.sqrt(x * x + (z + 22) * (z + 22));
                if (d <= 7.4 && d > 6.2) for (int y = 1; y <= 8; y++) a.set(x, y, z, STONE.pick(a.rnd));
                if (d <= 6.2) a.set(x, 0, z, Blocks.POLISHED_BLACKSTONE);
                if (d <= 5.3 && z <= -24) a.set(x, 1, z, Blocks.STONE_BRICKS);
                for (int y = 9; y <= 14; y++) {
                    double rr = 7 - (y - 8) * 1.15;
                    if (rr > 0 && Math.abs(d - rr) < 0.8) a.set(x, y, z, Blocks.DEEPSLATE_TILES);
                }
            }
        for (int x = -3; x <= 3; x++) a.set(x, 1, -23, stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false));
        for (int deg = 0; deg < 5; deg++) {
            double ang = Math.PI * (0.15 + 0.7 * deg / 4.0);
            int wx = (int) Math.round(Math.cos(ang) * 6.8), wz = -22 - (int) Math.round(Math.sin(ang) * 6.8);
            for (int y = 3; y <= 7; y++) a.set(wx, y, wz, y == 7 ? GLASS[deg % 4].first() : GLASS[(deg + 1) % 4].first());
            a.pulseLight((int) Math.round(Math.cos(ang) * 8.5), 5, -22 - (int) Math.round(Math.sin(ang) * 8.5), 3, 6);
        }
    }

    private static void tower(Arena a) {
        Random r = a.rnd;
        for (int x = -5; x <= 5; x++) for (int z = 15; z <= 23; z++) a.set(x, 0, z, Blocks.STONE_BRICKS);
        a.walls(-5, 1, 15, 5, 24, 23, STONE);
        a.clearBox(-4, 1, 16, 4, 23, 22);
        a.fill(-5, 24, 15, 5, 24, 23, Blocks.DEEPSLATE_BRICKS);
        for (int y : new int[]{8, 16}) { a.fill(-4, y, 16, 4, y, 22, Pal.of(Blocks.DARK_OAK_PLANKS, 3, Blocks.SPRUCE_PLANKS, 1)); a.carve(4, y, 19); }
        for (int y = 1; y <= 23; y++) a.set(4, y, 19, facing(Blocks.LADDER, Direction.WEST));
        // door and belfry openings
        for (int x = -1; x <= 1; x++) for (int y = 1; y <= 5; y++) a.carve(x, y, 23);
        a.set(-1, 6, 23, Blocks.STONE_BRICK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST).setValue(BlockStateProperties.HALF, net.minecraft.world.level.block.state.properties.Half.TOP));
        a.set(-1, 1, 23, doorLower(Blocks.DARK_OAK_DOOR, Direction.SOUTH, true, true));
        a.set(-1, 2, 23, doorUpper(Blocks.DARK_OAK_DOOR, Direction.SOUTH, true, true));
        a.set(1, 1, 23, doorLower(Blocks.DARK_OAK_DOOR, Direction.SOUTH, false, false));
        a.set(1, 2, 23, doorUpper(Blocks.DARK_OAK_DOOR, Direction.SOUTH, false, false));
        for (int y = 20; y <= 23; y++) for (int w = -1; w <= 1; w++) { a.carve(w, y, 15); a.carve(w, y, 23); a.carve(-5, y, 19 + w); a.carve(5, y, 19 + w); }
        // bell
        a.set(0, 24, 19, Blocks.DEEPSLATE_BRICKS);
        a.set(0, 23, 19, st(Blocks.BELL, BlockStateProperties.BELL_ATTACHMENT, BellAttachType.CEILING, BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        a.set(-1, 23, 19, Blocks.OAK_FENCE);
        // spire
        for (int i = 0; i <= 5; i++) a.fill(-5 + i, 25 + i, 15 + i, 5 - i, 25 + i, 23 - i, Blocks.DEEPSLATE_TILES);
        a.set(0, 31, 19, Blocks.IRON_BARS);
        a.set(0, 32, 19, Blocks.IRON_BARS);
        // lights and bones in the tower
        Kit.hangLantern(a, 0, 7, 19, 1, Blocks.SOUL_LANTERN);
        a.set(-3, 9, 21, candle(Blocks.CANDLE, 2, true));
        a.flick(-3, 9, 21, Arena.FLICK_GUST, 2, 0);
        Kit.bones(a, -3, 17, 18);
        Kit.blood(a, 1, 17, 20, 2f);
        Kit.webs(a, -4, 1, 16, 4, 23, 22, 0.25f);
        for (int y = 17; y <= 22; y++) a.set(0, y, 19, St.chain(Direction.Axis.Y));
    }

    // ------------------------------------------------------------------ furniture

    private static void pews(Arena a) {
        for (int z = -16; z <= 10; z += 2) {
            Kit.pew(a, -3, 1, z, 3, Direction.Axis.X, Direction.NORTH, Blocks.DARK_OAK_STAIRS, 0.22f);
            Kit.pew(a, 1, 1, z, 3, Direction.Axis.X, Direction.NORTH, Blocks.DARK_OAK_STAIRS, 0.22f);
        }
        Random r = a.rnd;
        // overturned pews, rubble
        for (int i = 0; i < 6; i++) {
            int x = -3 + r.nextInt(7), z = -14 + r.nextInt(24);
            a.setIfAir(x, 1, z, Blocks.DARK_OAK_PLANKS);
            a.setIfAir(x + 1, 1, z, stair(Blocks.DARK_OAK_STAIRS, Direction.EAST, true));
        }
        for (int z = -20; z <= 12; z++) if (r.nextInt(5) != 0) a.set(0, 1, z, r.nextInt(4) == 0 ? Blocks.BLACK_CARPET.defaultBlockState() : Blocks.RED_CARPET.defaultBlockState());
        Kit.bloodTrail(a, 0, 12, 0, -20, 1);
        for (int i = 0; i < 8; i++) Kit.bones(a, -3 + r.nextInt(7), 1, -12 + r.nextInt(22));
        // cobwebs hanging off the arcade
        Kit.webs(a, -7, 1, -22, 7, 12, 14, 0.14f);
        // aisle candles in wall niches
        for (int z = -16; z <= 8; z += 8) for (int sx : new int[]{-6, 6}) { a.set(sx, 2, z, Blocks.STONE_BRICK_SLAB); a.candle(sx, 3, z, 3, Blocks.CANDLE); }
        // pulpit
        for (int y = 1; y <= 2; y++) a.set(-3, y, -16, Blocks.DARK_OAK_PLANKS);
        a.set(-3, 3, -16, Blocks.LECTERN.defaultBlockState());
        a.set(-2, 1, -16, stair(Blocks.DARK_OAK_STAIRS, Direction.WEST, false));
    }

    private static void altar(Arena a) {
        // altar block with candles, inverted cross behind
        a.fill(-2, 2, -27, 2, 2, -26, Pal.of(Blocks.BLACKSTONE, 3, Blocks.POLISHED_BLACKSTONE_BRICKS, 2));
        a.fill(-2, 3, -27, 2, 3, -26, Pal.one(Blocks.POLISHED_BLACKSTONE_SLAB));
        for (int x = -2; x <= 2; x++) { a.candle(x, 4, -27, 1 + Math.abs(x) % 4, x % 2 == 0 ? Blocks.CANDLE : Blocks.BLACK_CANDLE); }
        a.candle(-1, 4, -26, 3, Blocks.RED_CANDLE);
        a.candle(1, 4, -26, 4, Blocks.RED_CANDLE);
        for (int y = 3; y <= 11; y++) a.set(0, y, -29, Blocks.GILDED_BLACKSTONE);
        for (int x = -2; x <= 2; x++) a.set(x, 5, -29, Blocks.GILDED_BLACKSTONE);
        Kit.skull(a, -2, 2, -25, Blocks.SKELETON_SKULL);
        Kit.skull(a, 2, 2, -25, Blocks.WITHER_SKELETON_SKULL);
        Kit.blood(a, 0, 2, -25, 3.5f);
        Kit.bones(a, 3, 2, -24);
        // candelabra posts around the apse
        for (int x : new int[]{-4, 4}) for (int z : new int[]{-24, -27}) {
            for (int y = 2; y <= 3; y++) a.set(x, y, z, Blocks.IRON_BARS);
            a.candle(x, 4, z, 3, Blocks.CANDLE);
        }
        a.pulseLight(0, 6, -26, 4, 7);
    }

    // ------------------------------------------------------------------ crypt

    private static void crypt(Arena a) {
        Random r = a.rnd;
        a.fill(-7, -8, -20, 7, -1, 9, Pal.of(Blocks.DEEPSLATE_BRICKS, 4, Blocks.CRACKED_DEEPSLATE_BRICKS, 3, Blocks.MOSSY_COBBLESTONE, 1, Blocks.STONE_BRICKS, 1));
        a.clearBox(-6, -7, -12, 6, -1, 8);
        a.fill(-6, -7, -12, 6, -7, 8, Pal.of(Blocks.DEEPSLATE_TILES, 3, Blocks.COBBLED_DEEPSLATE, 2, Blocks.POLISHED_DEEPSLATE, 2));
        // stairs down along the east aisle
        for (int i = 0; i < 7; i++) for (int x = 5; x <= 6; x++) {
            int z = -19 + i;
            for (int y = -i + 1; y <= 0; y++) a.carve(x, y, z);
            a.set(x, -i, z, stair(Blocks.STONE_BRICK_STAIRS, Direction.SOUTH, false));
            if (i > 0) a.set(x, -i - 1, z, Blocks.DEEPSLATE_BRICKS);
        }
        // sarcophagi along both sides, lids askew
        for (int row = 0; row < 4; row++) for (int sx : new int[]{-1, 1}) {
            int z = -9 + row * 4;
            int x0 = sx < 0 ? -5 : 3;
            for (int k = 0; k < 3; k++) {
                a.set(x0 + k, -6, z, Blocks.STONE_BRICKS);
                a.set(x0 + k, -5, z, k == 1 && r.nextInt(2) == 0 ? Blocks.AIR.defaultBlockState() : slab(Blocks.STONE_BRICK_SLAB, false));
            }
            a.set(x0 + (sx < 0 ? 3 : -1), -6, z, stair(Blocks.STONE_BRICK_STAIRS, sx < 0 ? Direction.WEST : Direction.EAST, false));
            if (r.nextInt(2) == 0) Kit.bones(a, x0 + 1, -4, z);
            a.candle(x0 + (sx < 0 ? 0 : 2), -4, z, 2, Blocks.CANDLE);
        }
        // iron bar cell with a prisoner
        a.walls(-5, -6, 4, 5, -4, 8, Pal.one(Blocks.IRON_BARS));
        a.clearBox(-4, -6, 5, 4, -1, 7);
        a.fill(-5, -6, 8, 5, -1, 8, Blocks.DEEPSLATE_BRICKS);
        a.carve(0, -6, 4); a.carve(0, -5, 4);
        a.set(0, -6, 4, doorLower(Blocks.IRON_DOOR, Direction.NORTH, true, true));
        a.set(0, -5, 4, doorUpper(Blocks.IRON_DOOR, Direction.NORTH, true, true));
        a.set(-4, -6, 7, Blocks.BONE_BLOCK);
        a.set(-4, -5, 7, Blocks.SKELETON_SKULL.defaultBlockState());
        a.set(-4, -4, 8, Blocks.CHAIN); a.set(-4, -3, 8, Blocks.CHAIN);
        a.set(-3, -4, 8, Blocks.CHAIN);
        Kit.bones(a, 2, -6, 6);
        Kit.bloodTrail(a, 5, -12, 0, 6, -6);
        // light: hanging soul lanterns, a gust of a candle, ghost glow
        for (int z : new int[]{-10, -2, 6}) Kit.hangLantern(a, 0, -1, z, 0, Blocks.SOUL_LANTERN);
        a.pulseLight(0, -3, -6, 2, 5);
        a.pulseLight(-3, -3, 6, 2, 4);
        Kit.webs(a, -6, -7, -12, 6, -1, 8, 0.2f);
    }

    // ------------------------------------------------------------------ graveyard

    private static void cemetery(Arena a) {
        Random r = a.rnd;
        for (int i = 0; i < 38; i++) {
            int x = 11 + r.nextInt(30), z = -26 + r.nextInt(46);
            if (x < 11) continue;
            Direction f = r.nextInt(4) == 0 ? Direction.EAST : Direction.WEST;
            Kit.tomb(a, x, 1, z, f, r.nextInt(4));
            if (r.nextInt(5) == 0) Kit.bones(a, x + 1, 1, z + 1);
            if (r.nextInt(7) == 0) { a.set(x + 1, 0, z, Blocks.AIR); a.set(x + 1, -1, z, Blocks.AIR); a.set(x + 2, 0, z, Blocks.AIR); a.set(x + 2, -1, z, Blocks.AIR); }
        }
        for (int i = 0; i < 20; i++) {
            int x = -41 + r.nextInt(26), z = -26 + r.nextInt(46);
            if (x > -10) continue;
            Kit.tomb(a, x, 1, z, Direction.EAST, r.nextInt(4));
        }
        // iron fence around the burial ground
        Kit.fenceLine(a, 10, -28, 42, -28, 1, 2, Blocks.IRON_BARS, Blocks.COBBLESTONE_WALL, 4, 0.1f);
        Kit.fenceLine(a, 10, -28, 10, 22, 1, 2, Blocks.IRON_BARS, Blocks.COBBLESTONE_WALL, 4, 0.1f);
        Kit.lampPost(a, 10, 1, -10, 2, Blocks.COBBLESTONE_WALL);
        Kit.blood(a, 20, 1, -4, 2f);
    }

    private static void ruin(Arena a) {
        Kit.decay(a, -9, 1, -30, 9, 17, 15, 0.2f, 5);
        Kit.decay(a, -5, 1, 15, 5, 30, 23, 0.14f, 9);
        Kit.age(a, -9, 1, -30, 9, 30, 23, Blocks.STONE_BRICKS, Pal.of(Blocks.CRACKED_STONE_BRICKS, 1, Blocks.MOSSY_STONE_BRICKS, 1), 0.25f);
    }
}
