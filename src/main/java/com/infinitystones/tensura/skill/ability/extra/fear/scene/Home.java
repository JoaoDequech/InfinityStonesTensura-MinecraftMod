package com.infinitystones.tensura.skill.ability.extra.fear.scene;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.List;
import java.util.Random;

import static com.infinitystones.tensura.skill.ability.extra.fear.scene.St.*;

/** Distorted copy of the area around the victim's real-world origin, rotted and wrong. */
public final class Home extends Scene {
    private static final int HW = 24, HH = 16, HD = 24; // ~48×32×48 centred on origin

    @Override
    public String id() { return "home"; }

    @Override
    public int minY() { return -HH / 2; }

    @Override
    public int maxY() { return HH / 2; }

    @Override
    public void plan(Arena a, Ctx c, List<Runnable> s) {
        a.spawn(0, 1, 0);
        s.add(() -> copy(a, c));
        s.add(() -> rot(a));
        s.add(() -> props(a));
    }

    private static void copy(Arena a, Ctx c) {
        ServerLevel src = c.server().getLevel(c.srcDim());
        if (src == null) { emptyGround(a); return; }
        BlockPos sp = c.srcPos();
        Random r = a.rnd;
        for (int dy = -HH / 2; dy < HH / 2; dy++) {
            int sy = sp.getY() + dy;
            if (sy < src.getMinBuildHeight() || sy >= src.getMaxBuildHeight()) continue;
            for (int dz = -HD / 2; dz < HD / 2; dz++) {
                for (int dx = -HW / 2; dx < HW / 2; dx++) {
                    int sx = sp.getX() + dx, sz = sp.getZ() + dz;
                    LevelChunk ch = src.getChunk(sx >> 4, sz >> 4);
                    BlockState bs = ch.getBlockState(new BlockPos(sx & 15, sy, sz & 15));
                    if (bs.isAir()) continue;
                    // skip block entities with loot or complex state
                    if (!bs.canSurvive(src, new BlockPos(sx, sy, sz))) continue;
                    a.set(dx, dy, dz, bs);
                }
            }
        }
    }

    private static void emptyGround(Arena a) {
        Kit.groundFlat(a, Pal.of(Blocks.COARSE_DIRT, 3, Blocks.DIRT, 1), Pal.of(Blocks.STONE, 1), 4);
    }

    private static void rot(Arena a) {
        Random r = a.rnd;
        // palette swap: wood → dark/rotten, stone → cracked/mossy, glass → broken
        for (int y = a.minY; y <= a.maxY; y++)
            for (int z = -HD / 2; z < HD / 2; z++)
                for (int x = -HW / 2; x < HW / 2; x++) {
                    BlockState bs = a.get(x, y, z);
                    if (bs == null || bs.isAir()) continue;
                    String name = bs.getBlock().getDescriptionId();
                    if (name.contains("oak_planks") || name.contains("spruce_planks") || name.contains("birch_planks")) {
                        a.set(x, y, z, r.nextBoolean() ? Blocks.DARK_OAK_PLANKS : Blocks.SPRUCE_PLANKS);
                    } else if (name.contains("stone_bricks")) {
                        a.set(x, y, z, r.nextInt(3) == 0 ? Blocks.CRACKED_STONE_BRICKS : r.nextInt(2) == 0 ? Blocks.MOSSY_STONE_BRICKS : Blocks.STONE_BRICKS);
                    } else if (name.contains("glass_pane") || name.contains("glass") && !name.contains("stained")) {
                        if (r.nextInt(3) == 0) a.carve(x, y, z);
                    } else if (name.contains("cobblestone")) {
                        a.set(x, y, z, r.nextInt(2) == 0 ? Blocks.MOSSY_COBBLESTONE : Blocks.COBBLESTONE);
                    }
                }
        // missing walls: carve random holes
        Kit.decay(a, -HW / 2, a.minY, -HD / 2, HW / 2 - 1, a.maxY, HD / 2 - 1, 0.15f, 7);
        // cobwebs everywhere
        Kit.webs(a, -HW / 2, a.minY, -HD / 2, HW / 2 - 1, a.maxY, HD / 2 - 1, 0.25f);
    }

    private static void props(Arena a) {
        Random r = a.rnd;
        // blood stains on floors
        for (int i = 0; i < 8; i++) {
            int x = r.nextInt(HW) - HW / 2, z = r.nextInt(HD) - HD / 2;
            int y = Kit.top(a, x, z);
            if (y > a.minY) Kit.blood(a, x, y + 1, z, 1.5f + r.nextFloat() * 2f);
        }
        // bones in corners
        for (int i = 0; i < 5; i++) Kit.bones(a, r.nextInt(HW) - HW / 2, a.minY + 1, r.nextInt(HD) - HD / 2);
        // flickering lights where there were lamps
        for (int i = 0; i < 4; i++) {
            int x = r.nextInt(HW) - HW / 2, z = r.nextInt(HD) - HD / 2;
            int y = Kit.top(a, x, z);
            if (y > a.minY + 2) {
                a.set(x, y + 1, z, Blocks.REDSTONE_LAMP);
                a.flick(x, y + 1, z, Arena.FLICK_BAD, 5, 0);
            }
        }
    }
}