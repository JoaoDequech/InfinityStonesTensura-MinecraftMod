package com.infinitystones.tensura.skill.ability.extra.fear.scene;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Random;

import static com.infinitystones.tensura.skill.ability.extra.fear.scene.St.*;

/** Snowy dark peak with cliffs, a cave entrance, wind-swept ridges and frozen bodies. */
public final class Mountain extends Scene {
    private static final int R = 40;

    @Override
    public String id() { return "mountain"; }

    @Override
    public int minY() { return -12; }

    @Override
    public int maxY() { return 52; }

    @Override
    public void plan(Arena a, Ctx c, List<Runnable> s) {
        a.spawn(0, 1, 34);
        s.add(() -> terrain(a));
        s.add(() -> cliff(a));
        s.add(() -> cave(a));
        s.add(() -> peak(a));
        s.add(() -> bodies(a));
        s.add(() -> trees(a));
    }

    private static void terrain(Arena a) {
        Random r = a.rnd;
        // conical mountain with noise
        for (int z = -R; z < R; z++)
            for (int x = -R; x < R; x++) {
                float d = (float) Math.sqrt(x * x + z * z);
                if (d > R) continue;
                float edge = Math.max(0f, 1f - d / R);
                int h = (int) Math.round((30f - d * 0.6f) * edge + Noise.fbm(x * 0.05f, z * 0.05f, 3, 3) * 8f * edge);
                h = Math.max(-8, Math.min(48, h));
                for (int y = -12; y < h; y++) {
                    Block b = y < h - 3 ? Blocks.STONE : y < h - 1 ? Blocks.COBBLED_DEEPSLATE : Blocks.SNOW_BLOCK;
                    a.set(x, y, z, b);
                }
                a.setGy(x, z, h);
                // snow layer on top
                if (h > 0 && r.nextInt(4) != 0) a.set(x, h + 1, z, Blocks.SNOW);
            }
    }

    private static void cliff(Arena a) {
        Random r = a.rnd;
        // sheer drop on one side
        int cx = 18, cz = 0;
        for (int y = -12; y < 20; y++)
            for (int z = -10; z <= 10; z++) {
                a.carve(cx, y, cz + z);
                if (r.nextInt(5) == 0) a.set(cx - 1, y, cz + z, Blocks.STONE);
            }
        // icicles hanging from overhangs
        for (int y = 15; y < 25; y++)
            for (int z = -8; z <= 8; z += 2)
                if (r.nextInt(3) == 0) {
                    int len = 1 + r.nextInt(4);
                    for (int k = 0; k < len; k++) a.setIfAir(cx - 1, y - k, cz + z, Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.VERTICAL_DIRECTION, net.minecraft.world.level.block.state.properties.VerticalDirection.DOWN));
                }
    }

    private static void cave(Arena a) {
        Random r = a.rnd;
        int cx = -20, cy = 2, cz = 10;
        // cave mouth
        for (int dy = -3; dy <= 3; dy++)
            for (int dz = -3; dz <= 3; dz++) {
                float d = (float) Math.sqrt(dy * dy + dz * dz);
                if (d < 3.5f) a.carve(cx, cy + dy, cz + dz);
            }
        // tunnel inward
        for (int i = 0; i < 12; i++) {
            int tx = cx - i, ty = cy + (int) Math.round(Math.sin(i * 0.3) * 2), tz = cz + (int) Math.round(Math.cos(i * 0.2) * 3);
            for (int dy = -2; dy <= 2; dy++)
                for (int dz = -2; dz <= 2; dz++)
                    if (dy * dy + dz * dz < 5) a.carve(tx, ty + dy, tz + dz);
            if (i % 3 == 0) {
                a.set(tx, ty + 3, tz, Blocks.GLOW_LICHEN);
                a.set(tx, ty - 3, tz, Blocks.GLOW_LICHEN);
            }
        }
        // frozen pool at the end
        int ex = cx - 12, ey = cy - 1, ez = cz + 3;
        a.disc(ex, ey, ez, 4f, Pal.one(Blocks.ICE));
        a.set(ex, ey + 1, ez, Blocks.AIR);
        Kit.bones(a, ex + 2, ey + 1, ez);
        Kit.skull(a, ex - 1, ey + 1, ez - 1, Blocks.WITHER_SKELETON_SKULL);
    }

    private static void peak(Arena a) {
        Random r = a.rnd;
        int px = 0, py = 38, pz = -10;
        // rocky summit
        for (int dy = -2; dy <= 4; dy++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dx = -2; dx <= 2; dx++)
                    if (dx * dx + dz * dz + dy * dy < 10) a.set(px + dx, py + dy, pz + dz, Blocks.STONE);
        // cross or marker
        for (int y = py + 5; y <= py + 9; y++) a.set(px, y, pz, Blocks.COBBLESTONE_WALL);
        a.set(px - 1, py + 8, pz, Blocks.COBBLESTONE_WALL);
        a.set(px + 1, py + 8, pz, Blocks.COBBLESTONE_WALL);
        // flag tattered
        a.set(px + 1, py + 9, pz, Blocks.WHITE_WOOL);
        a.set(px + 2, py + 9, pz, Blocks.AIR);
        // soul lantern at the top
        Kit.soulLantern(a, px, py + 10, pz, false);
        a.flick(px, py + 10, pz, Arena.FLICK_GUST, 4, 0);
    }

    private static void bodies(Arena a) {
        Random r = a.rnd;
        for (int i = 0; i < 6; i++) {
            int x = r.nextInt(30) - 15, z = r.nextInt(30) - 15;
            int y = Kit.top(a, x, z);
            if (y < -5) continue;
            a.set(x, y + 1, z, Blocks.SNOW);
            a.set(x, y + 1, z, Blocks.WHITE_CARPET);
            Kit.bones(a, x, y + 1, z);
            if (r.nextInt(3) == 0) Kit.skull(a, x + 1, y + 1, z, Blocks.SKELETON_SKULL);
            Kit.blood(a, x, y + 1, z, 1.2f);
        }
    }

    private static void trees(Arena a) {
        Random r = a.rnd;
        for (int i = 0; i < 12; i++) {
            int x = r.nextInt(60) - 30, z = r.nextInt(60) - 30;
            int y = Kit.top(a, x, z);
            if (y < -2 || y > 20) continue;
            Kit.deadTree(a, x, y + 1, z, 4 + r.nextInt(5), Blocks.SPRUCE_LOG);
        }
    }
}