package com.infinitystones.tensura.skill.ability.extra.fear.scene;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.List;
import java.util.Random;

import static com.infinitystones.tensura.skill.ability.extra.fear.scene.St.*;

/** Super-dense dead/dark forest with fog-thick canopy, narrow winding paths, hidden cabins and hanging things. */
public final class Forest extends Scene {
    private static final int R = 42;

    @Override
    public String id() { return "forest"; }

    @Override
    public int minY() { return -8; }

    @Override
    public int maxY() { return 32; }

    @Override
    public void plan(Arena a, Ctx c, List<Runnable> s) {
        a.spawn(0, 1, 36);
        s.add(() -> ground(a));
        s.add(() -> trees(a));
        s.add(() -> paths(a));
        s.add(() -> cabin(a, -18, 12));
        s.add(() -> cabin(a, 20, -16));
        s.add(() -> shrines(a));
        s.add(() -> hanging(a));
    }

    private static void ground(Arena a) {
        Random r = a.rnd;
        Kit.groundHills(a, 3f, 0.04f, 5, Pal.of(Blocks.PODZOL, 4, Blocks.COARSE_DIRT, 3, Blocks.MOSS_BLOCK, 2, Blocks.MUD, 1), Pal.of(Blocks.DIRT, 3, Blocks.STONE, 1), 6);
        // dense undergrowth
        for (int z = -R; z < R; z++)
            for (int x = -R; x < R; x++) {
                int y = Kit.top(a, x, z);
                if (y < -4) continue;
                if (r.nextInt(6) == 0) a.setIfAir(x, y + 1, z, Blocks.DEAD_BUSH);
                if (r.nextInt(8) == 0) a.setIfAir(x, y + 1, z, Blocks.FERN);
                if (r.nextInt(10) == 0) a.setIfAir(x, y + 1, z, Blocks.BROWN_MUSHROOM);
            }
    }

    private static void trees(Arena a) {
        Random r = a.rnd;
        // very dense: ~1 tree per 3×3 area
        for (int z = -R + 2; z < R - 2; z += 3)
            for (int x = -R + 2; x < R - 2; x += 3) {
                if (r.nextInt(3) != 0) continue; // some gaps for paths
                int ox = x + r.nextInt(2) - 1, oz = z + r.nextInt(2) - 1;
                int y = Kit.top(a, ox, oz);
                if (y < -3) continue;
                Kit.crownTree(a, ox, y + 1, oz, 8 + r.nextInt(8), Blocks.DARK_OAK_LOG, Blocks.DARK_OAK_LEAVES, 3 + r.nextInt(2));
                // vines hanging down
                for (int vy = y + 4; vy < y + 10; vy++)
                    if (r.nextInt(3) == 0) a.setIfAir(ox + r.nextInt(3) - 1, vy, oz + r.nextInt(3) - 1, Blocks.VINE.defaultBlockState().setValue(BlockStateProperties.NORTH, r.nextBoolean()));
            }
    }

    private static void paths(Arena a) {
        Random r = a.rnd;
        // winding path from spawn through the forest
        int px = 0, pz = 36;
        for (int step = 0; step < 60; step++) {
            int y = Kit.top(a, px, pz);
            if (y >= -4) {
                a.set(px, y, pz, Blocks.COARSE_DIRT);
                a.set(px, y + 1, pz, Blocks.AIR); // clear overhead
                a.set(px, y + 2, pz, Blocks.AIR);
            }
            // meander
            if (r.nextInt(3) == 0) px += r.nextBoolean() ? 1 : -1;
            pz -= 1;
            if (pz < -R + 4) break;
        }
        // secondary branching paths
        for (int branch = 0; branch < 4; branch++) {
            int bx = r.nextInt(30) - 15, bz = r.nextInt(30) - 15;
            for (int i = 0; i < 15; i++) {
                int y = Kit.top(a, bx, bz);
                if (y >= -4) {
                    a.set(bx, y, bz, Blocks.COARSE_DIRT);
                    a.set(bx, y + 1, bz, Blocks.AIR);
                }
                bx += r.nextInt(3) - 1;
                bz += r.nextInt(3) - 1;
            }
        }
    }

    private static void cabin(Arena a, int cx, int cz) {
        Random r = a.rnd;
        int y = Kit.top(a, cx, cz);
        if (y < -3) return;
        int w = 5 + r.nextInt(3), d = 5 + r.nextInt(3), h = 4 + r.nextInt(2);
        // floor
        a.fill(cx - w / 2, y, cz - d / 2, cx + w / 2, y, cz + d / 2, Pal.of(Blocks.SPRUCE_PLANKS, 3, Blocks.DARK_OAK_PLANKS, 1));
        // walls
        a.walls(cx - w / 2, y + 1, cz - d / 2, cx + w / 2, y + h, cz + d / 2, Pal.of(Blocks.SPRUCE_LOG, 3, Blocks.DARK_OAK_LOG, 1));
        a.clearBox(cx - w / 2 + 1, y + 1, cz - d / 2 + 1, cx + w / 2 - 1, y + h - 1, cz + d / 2 - 1);
        // roof
        Kit.roof(a, cx - w / 2 - 1, cz - d / 2 - 1, cx + w / 2 + 1, cz + d / 2 + 1, y + h, false, Blocks.SPRUCE_STAIRS, Blocks.SPRUCE_SLAB, Pal.of(Blocks.SPRUCE_PLANKS, 1));
        // door
        Direction facing = Direction.SOUTH;
        Kit.door(a, cx, y + 1, cz + d / 2, facing, Blocks.SPRUCE_DOOR, r.nextBoolean(), true);
        // window
        a.set(cx + w / 2, y + 2, cz, Blocks.GLASS_PANE);
        a.set(cx + w / 2, y + 3, cz, Blocks.GLASS_PANE);
        // interior: sparse, creepy
        a.set(cx - 1, y + 1, cz, stair(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
        a.set(cx + 1, y + 1, cz, slab(Blocks.SPRUCE_SLAB, false));
        Kit.candles(a, cx, y + 2, cz - 1, 2, true);
        a.flick(cx, y + 2, cz - 1, Arena.FLICK_DYING, 3, 0);
        // cobwebs inside
        Kit.webs(a, cx - w / 2 + 1, y + 1, cz - d / 2 + 1, cx + w / 2 - 1, y + h - 1, cz + d / 2 - 1, 0.3f);
        // blood near the door
        Kit.blood(a, cx, y + 1, cz + d / 2 + 1, 2f);
    }

    private static void shrines(Arena a) {
        Random r = a.rnd;
        for (int i = 0; i < 3; i++) {
            int x = r.nextInt(50) - 25, z = r.nextInt(50) - 25;
            int y = Kit.top(a, x, z);
            if (y < -3) continue;
            // small stone altar
            a.set(x, y + 1, z, Blocks.COBBLESTONE);
            a.set(x, y + 2, z, Blocks.COBBLESTONE_WALL);
            a.set(x, y + 3, z, Blocks.CANDLE);
            a.flick(x, y + 3, z, Arena.FLICK_BAD, 2, 0);
            Kit.bones(a, x + 1, y + 1, z);
            Kit.blood(a, x, y + 1, z - 1, 1.5f);
        }
    }

    private static void hanging(Arena a) {
        Random r = a.rnd;
        for (int i = 0; i < 5; i++) {
            int x = r.nextInt(50) - 25, z = r.nextInt(50) - 25;
            int y = Kit.top(a, x, z);
            if (y < 5) continue;
            // rope/chain from a high branch
            int chainLen = 3 + r.nextInt(4);
            for (int k = 0; k < chainLen; k++) a.setIfAir(x, y + 5 - k, z, St.chain(Direction.Axis.Y));
            // what hangs: could be a skull, could be something worse
            if (r.nextInt(3) == 0) a.set(x, y + 5 - chainLen, z, St.skull(Blocks.SKELETON_SKULL, r.nextInt(16)));
            else if (r.nextInt(2) == 0) a.set(x, y + 5 - chainLen, z, Blocks.HAY_BLOCK); // ambiguous shape
            else a.set(x, y + 5 - chainLen, z, Blocks.WHITE_WOOL);
            Kit.blood(a, x, y + 1, z, 1f);
        }
    }
}