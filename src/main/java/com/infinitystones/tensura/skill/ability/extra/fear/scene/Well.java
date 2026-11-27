package com.infinitystones.tensura.skill.ability.extra.fear.scene;

import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;

import java.util.List;
import java.util.Random;

import static com.infinitystones.tensura.skill.ability.extra.fear.scene.St.*;

/** A fogbound clearing in a dead forest with a well that has no bottom: whoever falls in keeps falling through darkness, then is spat out at the rim. */
public final class Well extends Scene {
    private static final int SHAFT_BOTTOM = -60, LOOP_AT = -52;
    private static final Pal STONE = Pal.of(Blocks.COBBLESTONE, 3, Blocks.MOSSY_COBBLESTONE, 4, Blocks.STONE_BRICKS, 2, Blocks.CRACKED_STONE_BRICKS, 2);

    @Override
    public String id() { return "well"; }

    @Override
    public int minY() { return -62; }

    @Override
    public int maxY() { return 30; }

    @Override
    public void plan(Arena a, Ctx c, List<Runnable> s) {
        a.spawn(0, 1, 18);
        s.add(() -> ground(a));
        s.add(() -> forest(a));
        s.add(() -> well(a));
        s.add(() -> shaft(a));
        s.add(() -> offerings(a));
        s.add(() -> behaviour(a));
    }

    private static void ground(Arena a) {
        Random r = a.rnd;
        Kit.groundHills(a, 2.5f, 0.07f, 31, Pal.of(Blocks.PODZOL, 5, Blocks.COARSE_DIRT, 3, Blocks.MOSS_BLOCK, 3, Blocks.MUD, 1, Blocks.DIRT, 1), Pal.of(Blocks.DIRT, 3, Blocks.COARSE_DIRT, 1), 6);
        // flatten the clearing so the well and offerings sit level
        for (int z = -26; z <= 26; z++) for (int x = -26; x <= 26; x++) {
            double d = Math.sqrt(x * x + z * z);
            if (d < 22) {
                for (int y = a.gy(x, z); y > 0; y--) a.carve(x, y, z);
                for (int y = a.gy(x, z); y < 0; y++) a.set(x, y, z, Blocks.DIRT);
                a.set(x, 0, z, Pal.of(Blocks.PODZOL, 4, Blocks.COARSE_DIRT, 3, Blocks.MOSS_BLOCK, 3, Blocks.MUD, 1).pick(r));
                a.setGy(x, z, 0);
            }
        }
        // a worn path from the edge of the clearing
        for (int z = 6; z <= 26; z++) for (int x = -1; x <= 1; x++) if (r.nextInt(5) != 0) a.set(x + (int) Math.round(Math.sin(z * 0.3) * 1.5), 0, z, Pal.of(Blocks.COBBLESTONE, 2, Blocks.GRAVEL, 1, Blocks.MOSSY_COBBLESTONE, 2).pick(r));
    }

    private static void forest(Arena a) {
        Random r = a.rnd;
        for (int z = -46; z <= 46; z += 2) for (int x = -46; x <= 46; x += 2) {
            int tx = x + r.nextInt(3) - 1, tz = z + r.nextInt(3) - 1;
            double d = Math.sqrt(tx * tx + tz * tz);
            if (d < 25 || r.nextInt(3) == 0) continue;
            if (d < 30 && r.nextInt(2) == 0) continue;
            int gy = a.gy(tx, tz);
            if (!a.solid(tx, gy, tz)) continue;
            Kit.deadTree(a, tx, gy + 1, tz, 6 + r.nextInt(9), r.nextInt(4) == 0 ? Blocks.SPRUCE_LOG : Blocks.DARK_OAK_LOG);
            if (r.nextInt(4) == 0) a.setIfAir(tx + 1, gy + 1, tz, Blocks.DEAD_BUSH);
        }
        for (int i = 0; i < 40; i++) {
            int x = r.nextInt(90) - 45, z = r.nextInt(90) - 45;
            if (Math.sqrt(x * x + z * z) < 24) continue;
            a.setIfAir(x, a.gy(x, z) + 1, z, r.nextBoolean() ? Blocks.DEAD_BUSH.defaultBlockState() : Blocks.BROWN_MUSHROOM.defaultBlockState());
        }
    }

    private static void well(Arena a) {
        Random r = a.rnd;
        // low wall
        a.ring(0, 1, 0, 3.8f, 0.8f, STONE);
        a.ring(0, 2, 0, 3.8f, 0.8f, STONE);
        a.ring(0, 3, 0, 3.8f, 0.8f, Pal.of(Blocks.STONE_BRICK_SLAB, 3, Blocks.AIR, 1));
        a.ring(0, 0, 0, 5.4f, 1.1f, Pal.of(Blocks.COBBLESTONE, 2, Blocks.STONE_BRICKS, 2, Blocks.MOSSY_COBBLESTONE, 2));
        Kit.decay(a, -5, 1, -5, 5, 3, 5, 0.2f, 2);
        // posts and roof
        for (int sx : new int[]{-4, 4}) for (int y = 1; y <= 6; y++) { a.set(sx, y, 0, axis(Blocks.DARK_OAK_LOG, Direction.Axis.Y)); a.set(sx, y, 1, y > 4 ? Blocks.DARK_OAK_FENCE.defaultBlockState() : Blocks.AIR.defaultBlockState()); }
        a.fill(-4, 7, 0, 4, 7, 0, axis(Blocks.STRIPPED_DARK_OAK_LOG, Direction.Axis.X));
        Kit.roof(a, -5, -3, 5, 3, 7, true, Blocks.DARK_OAK_STAIRS, Blocks.DARK_OAK_SLAB, Pal.one(Blocks.DARK_OAK_PLANKS));
        Kit.decay(a, -6, 7, -4, 6, 11, 4, 0.3f, 7);
        // the rope runs from the beam down into the well (finished in shaft())
        for (int y = 6; y >= 1; y--) a.set(0, y, 0, St.chain(Direction.Axis.Y));
        a.pulseLight(1, 3, 1, 3, 6);
        // the bucket lies next to the wall
        a.set(4, 1, 3, Blocks.CAULDRON);
        a.set(5, 1, 3, Blocks.OAK_FENCE);
        a.set(3, 1, 4, Blocks.CHAIN.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS, Direction.Axis.X));
        Kit.bones(a, -5, 1, -3);
        Kit.blood(a, 3, 1, -5, 2.5f);
        Kit.blood(a, 0, 1, 6, 2f);
        for (int i = 0; i < 4; i++) Kit.webs(a, -4, 3, -3, 4, 6, 3, 0.2f);
    }

    private static void shaft(Arena a) {
        Random r = a.rnd;
        for (int y = 0; y >= SHAFT_BOTTOM; y--) {
            for (int z = -5; z <= 5; z++) for (int x = -5; x <= 5; x++) {
                double d = Math.sqrt(x * x + z * z);
                if (d <= 3.4) a.carve(x, y, z);
                else if (d <= 4.6 && y > -6) a.setIfAir(x, y, z, STONE.pick(r));
                else if (d <= 4.5) {
                    float keep = y > -24 ? 1f : Math.max(0f, 1f - (-24 - y) / 24f);
                    Pal deep = y > -24 ? STONE : Pal.of(Blocks.DEEPSLATE_BRICKS, 3, Blocks.COBBLED_DEEPSLATE, 3, Blocks.BLACKSTONE, 2 + (-y) / 12);
                    if (r.nextFloat() < keep || y > -42) a.set(x, y, z, deep.pick(r));
                    else a.set(x, y, z, Blocks.BLACK_CONCRETE);
                }
            }
        }
        // iron rungs down one side, blood handprints, and things clinging to the wall
        for (int y = -1; y >= -34; y--) if (y % 3 != 0 || r.nextInt(3) != 0) a.set(3, y, 0, Blocks.IRON_BARS);
        for (int i = 0; i < 28; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            int y = -3 - r.nextInt(36);
            int x = (int) Math.round(Math.cos(ang) * 4), z = (int) Math.round(Math.sin(ang) * 4);
            if (a.solid(x, y, z)) a.set(x, y, z, r.nextInt(3) == 0 ? Blocks.RED_TERRACOTTA : Blocks.NETHER_WART_BLOCK);
        }
        for (int y = -8; y >= -48; y -= 8) {
            int x = (int) Math.round(Math.cos(y) * 3), z = (int) Math.round(Math.sin(y) * 3);
            a.set(x, y, z, Blocks.BONE_BLOCK);
            a.setIfAir(x, y + 1, z, St.skull(Blocks.SKELETON_SKULL, y & 15));
        }
        for (int y = 0; y >= -9; y--) a.set(0, y, 0, St.chain(Direction.Axis.Y));
        a.set(0, -10, 0, lantern(Blocks.SOUL_LANTERN, true));
        a.pulseLight(1, -10, 0, 2, 5);
        a.pulseLight(2, -30, 1, 0, 2);
    }

    private static void offerings(Arena a) {
        Random r = a.rnd;
        // ring of candles
        for (int i = 0; i < 14; i++) {
            double ang = i * Math.PI * 2 / 14;
            int x = (int) Math.round(Math.cos(ang) * 8.5), z = (int) Math.round(Math.sin(ang) * 8.5);
            if (i % 5 == 4) continue;
            a.candle(x, 1, z, 1 + r.nextInt(4), i % 3 == 0 ? Blocks.BLACK_CANDLE : Blocks.CANDLE);
        }
        // stakes with skulls
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4 + 0.3;
            int x = (int) Math.round(Math.cos(ang) * 13), z = (int) Math.round(Math.sin(ang) * 13);
            for (int y = 1; y <= 3; y++) a.set(x, y, z, Blocks.DARK_OAK_FENCE);
            a.set(x, 4, z, St.skull(r.nextInt(3) == 0 ? Blocks.WITHER_SKELETON_SKULL : Blocks.SKELETON_SKULL, r.nextInt(16)));
            a.set(x, 0, z, Blocks.COARSE_DIRT);
        }
        // standing stones
        for (int i = 0; i < 6; i++) {
            double ang = i * Math.PI / 3 + 0.9;
            int x = (int) Math.round(Math.cos(ang) * 17), z = (int) Math.round(Math.sin(ang) * 17);
            int h = 3 + r.nextInt(3);
            for (int y = 1; y <= h; y++) a.set(x, y, z, Pal.of(Blocks.COBBLED_DEEPSLATE_WALL, 3, Blocks.MOSSY_COBBLESTONE_WALL, 2).pick(r));
            a.set(x, h + 1, z, Blocks.AIR);
            if (i % 2 == 0) a.pulseLight(x, h, z, 0, 3);
        }
        for (int i = 0; i < 9; i++) Kit.bones(a, r.nextInt(30) - 15, 1, r.nextInt(30) - 15);
        Kit.bloodTrail(a, 0, 26, 0, 6, 1);
        for (int i = 0; i < 14; i++) {
            int x = r.nextInt(40) - 20, z = r.nextInt(40) - 20;
            if (Math.sqrt(x * x + z * z) < 20 && a.air(x, 1, z)) a.set(x, 1, z, r.nextInt(3) == 0 ? Blocks.COBWEB.defaultBlockState() : Blocks.DEAD_BUSH.defaultBlockState());
        }
    }

    // ------------------------------------------------------------------ the fall

    private static void behaviour(Arena a) {
        int[] inside = {0};
        a.behaviors.add((lv, p, o, age) -> {
            double x = p.getX() - o.getX(), y = p.getY() - o.getY(), z = p.getZ() - o.getZ();
            double d = Math.sqrt(x * x + z * z);
            if (d < 9 && age % 160 == 0 && lv.random.nextInt(2) == 0)
                lv.playSound(null, p.blockPosition(), SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, 0.35F, 0.5F + lv.random.nextFloat() * 0.3F);
            if (d < 3.3 && y < -1.5) {
                inside[0]++;
                p.resetFallDistance();
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false, false));
                if (inside[0] > 20 * 16) {
                    inside[0] = 0;
                    p.removeEffect(MobEffects.DARKNESS);
                    p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false, false));
                    p.teleportTo(lv, o.getX() + 7.5, o.getY() + 1, o.getZ() + 0.5, 90f, 0f);
                } else if (y < LOOP_AT) {
                    p.teleportTo(lv, o.getX() + 0.5 + (x - 0.0) * 0.2, o.getY() - 3, o.getZ() + 0.5 + z * 0.2, p.getYRot(), p.getXRot());
                    p.resetFallDistance();
                }
            } else if (y > -1) inside[0] = 0;
        });
    }
}
