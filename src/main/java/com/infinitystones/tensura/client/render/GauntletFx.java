package com.infinitystones.tensura.client.render;

import com.infinitystones.tensura.registry.ModItems;
import com.infinitystones.tensura.skill.Stone;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;

/**
 * Client hook that makes the Infinity Gauntlet react when a power is used.
 *
 * <p>Call {@link #pulse(int)} on the client (e.g. from the wheel right after a stone's ability is fired) with the
 * {@link Stone#ordinal()} of the stone that was used (MIND=0, POWER=1, SPACE=2, REALITY=3, SOUL=4, TIME=5). For the
 * next ~0.9 s that socket flares (forced lit, big halo + star flare), the gauntlet kicks slightly in first person, and,
 * if the local player holds the gauntlet, a small burst of sparks in the stone's colour leaves the hand.
 * Safe to call any time on the client thread (does nothing without a player; out-of-range ordinals are ignored).
 */
public final class GauntletFx {
    public static final long DURATION_MS = 900;
    private static final long[] PULSED_AT = new long[Stone.values().length];
    private static final RandomSource RNG = RandomSource.create();

    public static void pulse(int stoneOrdinal) {
        if (stoneOrdinal < 0 || stoneOrdinal >= PULSED_AT.length) return;
        PULSED_AT[stoneOrdinal] = Util.getMillis();
        burst(Stone.values()[stoneOrdinal]);
    }

    /** 1 right after a pulse, easing to 0 over {@link #DURATION_MS}. */
    public static float strength(int stoneOrdinal) {
        long at = PULSED_AT[stoneOrdinal];
        if (at == 0) return 0;
        float t = (Util.getMillis() - at) / (float) DURATION_MS;
        if (t >= 1 || t < 0) return 0;
        float k = 1 - t;
        return k * k;
    }

    /** Strongest active pulse (drives the first-person kick). */
    public static float strongest() {
        float m = 0;
        for (int i = 0; i < PULSED_AT.length; i++) m = Math.max(m, strength(i));
        return m;
    }

    private static void burst(Stone stone) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return;
        InteractionHand hand = p.getMainHandItem().is(ModItems.INFINITY_GAUNTLET.get()) ? InteractionHand.MAIN_HAND
                : p.getOffhandItem().is(ModItems.INFINITY_GAUNTLET.get()) ? InteractionHand.OFF_HAND : null;
        if (hand == null) return;
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? p.getMainArm() : p.getMainArm().getOpposite();
        Vec3 look = p.getViewVector(1F);
        Vec3 side = look.cross(new Vec3(0, 1, 0)).normalize().scale(arm == HumanoidArm.RIGHT ? 0.35 : -0.35);
        Vec3 c = p.getEyePosition().add(look.scale(0.7)).add(side).add(0, -0.35, 0);
        int color = stone.color();
        for (int i = 0; i < 14; i++) {
            Vec3 v = new Vec3(RNG.nextGaussian(), RNG.nextGaussian() + 0.6, RNG.nextGaussian()).normalize().scale(0.08 + RNG.nextDouble() * 0.08).add(look.scale(0.05));
            RelicParticles.Mote m = new RelicParticles.Mote(mc.level, c.x, c.y, c.z, i % 3 == 0 ? 0xFFFFFFFF : color,
                    RelicParticles.sprite(i % 2 == 0 ? "stone_spark" : "stone_mote"), false);
            m.rise = 0.004F;
            mc.particleEngine.add(m.push(v.x, v.y, v.z));
        }
    }

    private GauntletFx() {}
}
