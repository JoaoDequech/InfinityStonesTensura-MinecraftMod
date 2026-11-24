package com.infinitystones.tensura.skill.ability.extra.v2a;

import net.minecraft.util.Mth;

/** Timelines shared by the server logic and the client animation (so impact, capture and sound line up with the models). */
public final class V2aAnim {
    public static float ease(float t) {
        t = Mth.clamp(t, 0, 1);
        return t < 0.5F ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
    }

    public static float easeIn(float t) { t = Mth.clamp(t, 0, 1); return t * t * t; }

    public static float easeOut(float t) { t = Mth.clamp(t, 0, 1); float u = 1 - t; return 1 - u * u * u; }

    public static double lerp(double a, double b, float k) { return a + (b - a) * k; }

    /** Sky Fist: the fist leaves the circle at FIST_START, lands at FIST_IMPACT, stays, then climbs back up. */
    public static final int FIST_START = 22, FIST_IMPACT = 56, FIST_STAY = 66, FIST_END = 96, FIST_LIFE = 106, FIST_CURL = 32;

    /** Creator's Hand states (also the message state). */
    public static final int G_DESC = 0, G_OPEN = 1, G_CLOSE = 2, G_RISE = 3, G_HOLD = 4, G_SQUEEZE = 5, G_RELEASE = 6;
    public static final int G_DESC_TICKS = 56, G_OPEN_TICKS = 16, G_CLOSE_TICKS = 22, G_RISE_TICKS = 40, G_RELEASE_TICKS = 44, G_SQUEEZE_TICKS = 12;

    /** Sculptor's Hands: rise from the ground, work, sink. */
    public static final int H_RISE = 16, H_SINK = 18;

    private V2aAnim() {}
}
