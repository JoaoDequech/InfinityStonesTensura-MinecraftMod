package com.infinitystones.tensura.skill.ability.extra.fear.scene;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Random;

import static com.infinitystones.tensura.skill.ability.extra.fear.scene.St.*;

/** Fenced, fog-bound cemetery: crooked headstones, open graves, three mausoleums (one with an ossuary below), an angel statue, gallows and a caretaker's shed. */
public final class Graveyard extends Scene {
    private static final Pal STONE = Pal.of(Blocks.STONE_BRICKS, 4, Blocks.CRACKED_STONE_BRICKS, 3, Blocks.MOSSY_STONE_BRICKS, 4, Blocks.DEEPSLATE_BRICKS, 1);

    @Override
    public String id() { return "graveyard"; }

    @Override
    public int minY() { return -12; }

    @Override
    public int maxY() { return 30; }

    @Override
    public void plan(Arena a, Ctx c, List<Runnable> s) {
        a.spawn(0, 1, 38);
        s.add(() -> ground(a));
        s.add(() -> fence(a));
        s.add(() -> paths(a));
        s.add(() -> mausoleum(a, -30, -4, 9, 8, false));
        s.add(() -> mausoleum(a, 22, -24, 9, 8, false));
        s.add(() -> mausoleum(a, 20, 4, 11, 10, true));
        s.add(() -> angel(a, 0, -8));
        s.add(() -> shed(a, -36, 24));
        s.add(() -> gallows(a, -26, -28));
        s.add(() -> graves(a));
        s.add(() -> trees(a));
    }

    private static void flatten(Arena a, int x1, int z1, int x2, int z2, int top) {
        for (int z = z1; z <= z2; z++) for (int x = x1; x <= x2; x++) {
            for (int y = a.gy(x, z); y > 0; y--) a.carve(x, y, z);
            for (int y = a.gy(x, z); y <= 0; y++) a.set(x, y, z, Blocks.DIRT);
            for (int y = 1; y <= top; y++) a.carve(x, y, z);
            a.setGy(x, z, 0);
        }
    }

    private static void ground(Arena a) {
        Kit.groundHills(a, 3f, 0.06f, 12, Pal.of(Blocks.PODZOL, 4, Blocks.COARSE_DIRT, 4, Blocks.MOSS_BLOCK, 3, Blocks.MUD, 1, Blocks.DIRT, 1), Pal.of(Blocks.DIRT, 3, Blocks.COARSE_DIRT, 1), 12);
    }

    private static void fence(Arena a) {
        for (int[] l : new int[][]{{-42, -42, 42, -42}, {-42, -42, -42, 42}, {42, -42, 42, 42}, {-42, 42, -5, 42}, {5, 42, 42, 42}}) {
            Kit.fenceLine(a, l[0], l[1], l[2], l[3], 1, 3, Blocks.IRON_BARS, Blocks.STONE_BRICKS, 5, 0.05f);
        }
        // follow the terrain: lift each fence column onto the ground
        for (int sx : new int[]{-5, 5}) {
            flatten(a, sx - 1, 41, sx + 1, 43, 8);
            for (int y = 1; y <= 7; y++) a.set(sx, y, 42, y == 7 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : STONE.pick(a.rnd));
            Kit.soulLantern(a, sx, 8, 42, false);
        }
        for (int x = -4; x <= 4; x++) a.set(x, 8, 42, x % 2 == 0 ? Blocks.STONE_BRICK_SLAB.defaultBlockState() : Blocks.AIR.defaultBlockState());
        for (int y = 1; y <= 6; y++) { a.set(-4, y, 42, Blocks.IRON_BARS); a.set(-3, y, 42, y < 5 ? Blocks.IRON_BARS.defaultBlockState() : Blocks.AIR.defaultBlockState()); }
        a.set(4, 1, 42, Blocks.AIR);
        a.pulseLight(0, 6, 40, 2, 4);
    }

    private static void paths(Arena a) {
        Random r = a.rnd;
        for (int z = -12; z <= 41; z++) for (int x = -1; x <= 1; x++) pathAt(a, x, z, r);
        for (int x = -34; x <= 34; x++) for (int z = 14; z <= 15; z++) pathAt(a, x, z, r);
        for (int x = -30; x <= 22; x++) for (int z = -14; z <= -13; z++) pathAt(a, x, z, r);
        // lamp posts at the crossings
        for (int[] p : new int[][]{{3, 38}, {-3, 30}, {3, 13}, {-3, 16}, {3, -12}}) {
            flatten(a, p[0], p[1], p[0], p[1], 6);
            Kit.lampPost(a, p[0], 1, p[1], 3, Blocks.DARK_OAK_FENCE);
        }
        for (int[] p : new int[][]{{3, 13}, {-3, 30}}) a.pulseLight(p[0], 5, p[1], 2, 4);
    }

    private static void pathAt(Arena a, int x, int z, Random r) {
        if (r.nextInt(6) == 0) return;
        a.set(x, a.gy(x, z), z, Pal.of(Blocks.STONE_BRICKS, 3, Blocks.CRACKED_STONE_BRICKS, 3, Blocks.COBBLESTONE, 2, Blocks.GRAVEL, 1).pick(r));
    }

    // ------------------------------------------------------------------ mausoleums

    private static void mausoleum(Arena a, int x0, int z0, int w, int d, boolean crypt) {
        Random r = a.rnd;
        int x1 = x0 + w - 1, z1 = z0 + d - 1, h = 5;
        flatten(a, x0 - 2, z0 - 2, x1 + 2, z1 + 5, h + 8);
        a.fill(x0 - 1, 0, z0 - 1, x1 + 1, 0, z1 + 4, Pal.of(Blocks.COBBLESTONE, 2, Blocks.STONE_BRICKS, 3, Blocks.MOSSY_COBBLESTONE, 1));
        a.walls(x0, 1, z0, x1, h, z1, STONE);
        for (int cx : new int[]{x0, x1}) for (int cz : new int[]{z0, z1}) for (int y = 1; y <= h + 1; y++) a.set(cx, y, cz, Blocks.CHISELED_STONE_BRICKS);
        a.fill(x0, h + 1, z0, x1, h + 1, z1, Blocks.STONE_BRICK_SLAB);
        Kit.roof(a, x0 - 1, z0 - 1, x1 + 1, z1 + 1, h + 1, false, Blocks.STONE_BRICK_STAIRS, Blocks.STONE_BRICK_SLAB, STONE);
        // front (south) door with pillars and steps
        int mx = (x0 + x1) / 2;
        for (int y = 1; y <= 3; y++) { a.carve(mx, y, z1); a.carve(mx + 1, y, z1); }
        a.set(mx, 1, z1, doorLower(Blocks.IRON_DOOR, Direction.SOUTH, r.nextBoolean(), true));
        a.set(mx, 2, z1, doorUpper(Blocks.IRON_DOOR, Direction.SOUTH, r.nextBoolean(), true));
        a.carve(mx + 1, 1, z1); a.carve(mx + 1, 2, z1);
        for (int sx : new int[]{mx - 1, mx + 2}) for (int y = 1; y <= 4; y++) a.set(sx, y, z1 + 1, y == 4 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICK_WALL.defaultBlockState());
        for (int x = mx - 1; x <= mx + 2; x++) a.set(x, 5, z1 + 1, Blocks.STONE_BRICK_SLAB);
        for (int x = mx - 1; x <= mx + 2; x++) a.set(x, 0, z1 + 2, stair(Blocks.COBBLESTONE_STAIRS, Direction.SOUTH, false));
        // interior: sarcophagi, candles, a hanging soul lantern
        for (int z = z0 + 2; z <= z1 - 3; z += 3) for (int sx : new int[]{x0 + 1, x1 - 3}) {
            for (int k = 0; k < 3; k++) { a.set(sx + k, 1, z, Blocks.STONE_BRICKS); a.set(sx + k, 2, z, k == 1 && r.nextBoolean() ? Blocks.AIR.defaultBlockState() : slab(Blocks.STONE_BRICK_SLAB, false)); }
            if (r.nextBoolean()) Kit.bones(a, sx + 1, 2, z);
            a.candle(sx + (sx == x0 + 1 ? 0 : 2), 2, z, 2 + r.nextInt(3), Blocks.CANDLE);
        }
        a.set(mx, h, (z0 + z1) / 2, Blocks.CHAIN);
        a.set(mx, h - 1, (z0 + z1) / 2, lantern(Blocks.SOUL_LANTERN, true));
        a.pulseLight(mx, 2, z0 + 2, 2, 4);
        Kit.blood(a, mx, 1, z1 - 2, 2f);
        // a window slit on the sides
        for (int z = z0 + 2; z < z1 - 1; z += 3) { a.set(x0, 3, z, Blocks.IRON_BARS); a.set(x1, 3, z, Blocks.IRON_BARS); }
        Kit.decay(a, x0 - 1, 1, z0 - 1, x1 + 1, h + 6, z1 + 3, 0.18f, x0 + 5);
        Kit.webs(a, x0 + 1, 1, z0 + 1, x1 - 1, h, z1 - 1, 0.25f);
        if (!crypt) return;
        // hatch in the floor and an ossuary below
        int hx = x0 + 2, hz = z0 + 1;
        a.fill(x0 - 3, -9, z0 - 3, x1 + 3, -1, z1 + 3, Pal.of(Blocks.DEEPSLATE_BRICKS, 4, Blocks.CRACKED_DEEPSLATE_BRICKS, 3, Blocks.COBBLED_DEEPSLATE, 1));
        a.clearBox(x0 + 1, -7, z0 + 1, x1 - 1, -1, z1 - 1);
        a.fill(x0 + 1, -8, z0 + 1, x1 - 1, -8, z1 - 1, Pal.of(Blocks.DEEPSLATE_TILES, 2, Blocks.COBBLED_DEEPSLATE, 1));
        for (int i = 0; i < 8; i++) {
            int x = hx + i;
            if (x > x1 - 1) break;
            for (int y = -i + 1; y <= 0; y++) a.carve(x, y, hz);
            a.set(x, -i, hz, stair(Blocks.STONE_BRICK_STAIRS, Direction.EAST, false));
            if (i > 0) a.set(x, -i - 1, hz, Blocks.DEEPSLATE_BRICKS);
        }
        for (int z = z0 + 1; z <= z1 - 1; z++) for (int y = -7; y <= -3; y++) {
            if ((y + z) % 2 == 0) a.set(x1 - 1, y, z, Blocks.BONE_BLOCK);
            else if (y % 2 == 0) a.set(x1 - 1, y, z, wallSkull(Blocks.SKELETON_WALL_SKULL, Direction.WEST));
        }
        for (int x = x0 + 1; x <= x1 - 2; x++) for (int y = -7; y <= -4; y += 2) if ((x + y) % 3 == 0) a.set(x, y, z1 - 1, wallSkull(Blocks.SKELETON_WALL_SKULL, Direction.NORTH));
        for (int i = 0; i < 14; i++) Kit.bones(a, x0 + 3 + r.nextInt(w - 5), -7, z0 + 2 + r.nextInt(d - 4));
        int lx = (x0 + x1) / 2, lz = (z0 + z1) / 2 + 1;
        a.set(lx, -1, lz, Blocks.CHAIN);
        a.set(lx, -2, lz, lantern(Blocks.SOUL_LANTERN, true));
        a.pulseLight(lx + 1, -3, lz, 2, 5);
        Kit.webs(a, x0 + 1, -7, z0 + 1, x1 - 1, -1, z1 - 1, 0.2f);
    }

    // ------------------------------------------------------------------ props

    private static void angel(Arena a, int cx, int cz) {
        Random r = a.rnd;
        flatten(a, cx - 6, cz - 6, cx + 6, cz + 6, 12);
        a.disc(cx, 0, cz, 6, Pal.of(Blocks.STONE_BRICKS, 3, Blocks.CRACKED_STONE_BRICKS, 3, Blocks.MOSSY_STONE_BRICKS, 2));
        a.ring(cx, 1, cz, 5.5f, 0.6f, Pal.of(Blocks.STONE_BRICK_WALL, 3, Blocks.AIR, 1));
        a.fill(cx - 1, 1, cz - 1, cx + 1, 2, cz + 1, STONE);
        a.fill(cx, 3, cz, cx, 6, cz, Blocks.STONE_BRICKS);
        a.set(cx, 7, cz, Blocks.CHISELED_STONE_BRICKS);
        a.set(cx, 8, cz, Blocks.SKELETON_SKULL);
        for (int sx : new int[]{-1, 1}) {
            for (int k = 0; k < 4; k++) a.set(cx + sx * (k + 1), 5 + k, cz, stair(Blocks.QUARTZ_STAIRS, sx < 0 ? Direction.WEST : Direction.EAST, false));
            if (r.nextBoolean()) a.carve(cx + sx * 3, 7, cz);
            a.set(cx + sx, 5, cz + 1, Blocks.STONE_BRICK_WALL);
        }
        for (int sx : new int[]{-2, 2}) {
            a.set(cx + sx, 1, cz + 2, Blocks.STONE_BRICK_WALL);
            a.set(cx + sx, 2, cz + 2, slab(Blocks.STONE_BRICK_SLAB, false));
            a.candle(cx + sx, 3, cz + 2, 3 + (sx > 0 ? 1 : 0), sx > 0 ? Blocks.BLACK_CANDLE : Blocks.CANDLE);
        }
        Kit.blood(a, cx, 1, cz + 3, 3f);
        a.pulseLight(cx, 4, cz + 3, 2, 4);
        Kit.decay(a, cx - 4, 3, cz - 2, cx + 4, 9, cz + 2, 0.12f, 91);
    }

    private static void shed(Arena a, int x0, int z0) {
        flatten(a, x0 - 1, z0 - 1, x0 + 7, z0 + 6, 8);
        a.fill(x0, 0, z0, x0 + 6, 0, z0 + 5, Pal.of(Blocks.SPRUCE_PLANKS, 3, Blocks.COARSE_DIRT, 1));
        a.walls(x0, 1, z0, x0 + 6, 4, z0 + 5, Pal.of(Blocks.SPRUCE_PLANKS, 4, Blocks.DARK_OAK_PLANKS, 2, Blocks.OAK_PLANKS, 1));
        for (int cx : new int[]{x0, x0 + 6}) for (int cz : new int[]{z0, z0 + 5}) for (int y = 1; y <= 4; y++) a.set(cx, y, cz, Blocks.DARK_OAK_LOG);
        Kit.roof(a, x0 - 1, z0 - 1, x0 + 7, z0 + 6, 5, true, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Pal.one(Blocks.SPRUCE_PLANKS));
        a.carve(x0 + 3, 1, z0 + 5); a.carve(x0 + 3, 2, z0 + 5);
        a.set(x0 + 3, 1, z0 + 5, doorLower(Blocks.SPRUCE_DOOR, Direction.SOUTH, true, false));
        a.set(x0 + 3, 2, z0 + 5, doorUpper(Blocks.SPRUCE_DOOR, Direction.SOUTH, true, false));
        a.set(x0 + 6, 2, z0 + 2, Blocks.GLASS_PANE);
        a.set(x0, 2, z0 + 2, Blocks.IRON_BARS);
        Kit.table(a, x0 + 1, 1, z0 + 1, 3, Direction.Axis.X, Blocks.SPRUCE_SLAB, Blocks.SPRUCE_FENCE);
        a.set(x0 + 2, 2, z0 + 1, Blocks.CANDLE.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.CANDLES, 3).setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.LIT, true));
        a.flick(x0 + 2, 2, z0 + 1, Arena.FLICK_GUST, 3, 0);
        for (int y = 1; y <= 3; y++) { a.set(x0 + 5, y, z0 + 1, Blocks.OAK_FENCE); }
        a.set(x0 + 5, 4, z0 + 1, Blocks.IRON_BARS);
        Kit.bed(a, x0 + 5, 1, z0 + 3, Direction.NORTH, Blocks.GRAY_BED);
        Kit.blood(a, x0 + 3, 1, z0 + 3, 2.5f);
        Kit.bones(a, x0 + 1, 1, z0 + 4);
        a.pulseLight(x0 + 2, 3, z0 + 2, 2, 4);
        Kit.decay(a, x0, 1, z0, x0 + 6, 8, z0 + 5, 0.15f, 66);
        Kit.webs(a, x0 + 1, 1, z0 + 1, x0 + 5, 4, z0 + 4, 0.25f);
    }

    private static void gallows(Arena a, int x, int z) {
        flatten(a, x - 1, z - 1, x + 6, z + 4, 9);
        a.fill(x, 1, z, x + 4, 1, z + 2, Pal.of(Blocks.SPRUCE_PLANKS, 3, Blocks.DARK_OAK_PLANKS, 1));
        a.fill(x, 2, z, x + 4, 2, z + 2, Blocks.AIR);
        for (int y = 2; y <= 8; y++) a.set(x + 3, y, z + 1, Blocks.DARK_OAK_LOG);
        for (int k = 0; k <= 3; k++) a.set(x + 3 - k, 8, z + 1, axis(Blocks.DARK_OAK_LOG, Direction.Axis.X));
        a.set(x + 2, 7, z + 1, stair(Blocks.DARK_OAK_STAIRS, Direction.WEST, true));
        a.set(x + 3, 7, z, stair(Blocks.DARK_OAK_STAIRS, Direction.WEST, true));
        for (int y = 7; y >= 4; y--) a.set(x, y, z + 1, St.chain(Direction.Axis.Y));
        a.set(x, 3, z + 1, Blocks.SKELETON_SKULL);
        a.set(x + 1, 2, z + 1, trap(Blocks.DARK_OAK_TRAPDOOR, Direction.EAST, false, true));
        for (int sx = 0; sx <= 4; sx += 2) a.set(x + sx, 0, z + 3, Blocks.COARSE_DIRT);
        Kit.blood(a, x + 1, 2, z + 4, 2f);
        a.set(x - 1, 1, z + 1, Blocks.CHAIN.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, Direction.Axis.X));
        Kit.bones(a, x + 2, 1, z + 3);
    }

    // ------------------------------------------------------------------ graves and trees

    private static void graves(Arena a) {
        Random r = a.rnd;
        for (int gx = -38; gx <= 38; gx += 4) for (int gz = -38; gz <= 36; gz += 4) {
            int x = gx + r.nextInt(2), z = gz + r.nextInt(2);
            if (Math.abs(x) <= 3 || (z >= 12 && z <= 17) || (z >= -16 && z <= -11) && x > -32 && x < 24) continue;
            if (Math.abs(x) < 8 && Math.abs(z + 8) < 8) continue;
            if (r.nextInt(6) == 0) continue;
            if (blocked(a, x, z)) continue;
            int y = a.gy(x, z) + 1;
            Direction f = r.nextInt(5) == 0 ? Direction.WEST : Direction.SOUTH;
            Kit.tomb(a, x, y, z, f, r.nextInt(4));
            int roll = r.nextInt(12);
            if (roll == 0) { // open grave beside it
                int gy = a.gy(x + 2, z);
                for (int xx = x + 2; xx <= x + 3; xx++) for (int zz = z; zz <= z + 2; zz++) for (int yy = gy; yy >= gy - 3; yy--) a.carve(xx, yy, zz);
                a.set(x + 2, gy - 2, z + 1, slab(Blocks.DARK_OAK_SLAB, false));
                a.set(x + 3, gy - 2, z + 1, Blocks.DARK_OAK_PLANKS);
                a.set(x + 4, gy + 1, z, Blocks.COARSE_DIRT);
                a.set(x + 4, gy + 1, z + 1, Blocks.COARSE_DIRT);
                Kit.bones(a, x + 3, gy - 3, z + 2);
            } else if (roll == 1) a.candle(x + 1, y, z + 1, 1 + r.nextInt(3), Blocks.CANDLE);
            else if (roll == 2) Kit.bones(a, x + 1, y, z + 2);
            else if (roll == 3) a.setIfAir(x - 1, y, z, Blocks.DEAD_BUSH);
            else if (roll == 4) a.setIfAir(x + 1, y, z + 1, Blocks.COBWEB);
            else if (roll == 5) Kit.skull(a, x + 1, y, z + 1, Blocks.SKELETON_SKULL);
        }
        for (int i = 0; i < 4; i++) Kit.blood(a, r.nextInt(60) - 30, 1, r.nextInt(60) - 30, 2f);
    }

    private static boolean blocked(Arena a, int x, int z) {
        int y = a.gy(x, z) + 1;
        return a.solid(x, y, z) || a.solid(x, y - 1, z) && !a.is(x, y - 1, z, Blocks.PODZOL) && !a.is(x, y - 1, z, Blocks.COARSE_DIRT) && !a.is(x, y - 1, z, Blocks.MOSS_BLOCK) && !a.is(x, y - 1, z, Blocks.MUD) && !a.is(x, y - 1, z, Blocks.DIRT);
    }

    private static void trees(Arena a) {
        Random r = a.rnd;
        for (int i = 0; i < 40; i++) {
            int x = r.nextInt(84) - 42, z = r.nextInt(84) - 42;
            if (Math.abs(x) <= 3 || blocked(a, x, z) || a.air(x, a.gy(x, z), z)) continue;
            Kit.deadTree(a, x, a.gy(x, z) + 1, z, 6 + r.nextInt(8), r.nextInt(3) == 0 ? Blocks.SPRUCE_LOG : Blocks.DARK_OAK_LOG);
        }
    }
}
