package com.infinitystones.tensura.skill.ability.extra.fear.client;

import com.infinitystones.tensura.InfinityStones;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Screaming, bloodied faces painted pixel by pixel at first use (no image files); three variants. */
final class FearFace {
    static final int SIZE = 192;
    private static final ResourceLocation[] LOC = new ResourceLocation[3];

    static ResourceLocation get(int variant) {
        int v = Math.floorMod(variant, 3);
        if (LOC[v] == null) {
            NativeImage img = new NativeImage(SIZE, SIZE, true);
            paint(img, v);
            DynamicTexture tex = new DynamicTexture(img);
            tex.setFilter(true, false);
            LOC[v] = InfinityStones.id("fear_face_" + v);
            Minecraft.getInstance().getTextureManager().register(LOC[v], tex);
        }
        return LOC[v];
    }

    static void reset() { java.util.Arrays.fill(LOC, null); }

    private static double h(int x, int y, int s) {
        long k = x * 73856093L ^ y * 19349663L ^ s * 83492791L;
        k ^= k >>> 13; k *= 0x9E3779B97F4A7C15L; k ^= k >>> 29;
        return (k >>> 11) * (1.0 / (1L << 53));
    }

    private static double n(double x, double y, int s) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        double fx = x - x0, fy = y - y0;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
        double a = h(x0, y0, s), b = h(x0 + 1, y0, s), c = h(x0, y0 + 1, s), d = h(x0 + 1, y0 + 1, s);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy;
    }

    private static int abgr(int a, int r, int g, int b) {
        return (Mth.clamp(a, 0, 255) << 24) | (Mth.clamp(b, 0, 255) << 16) | (Mth.clamp(g, 0, 255) << 8) | Mth.clamp(r, 0, 255);
    }

    private static double tri(double x) { return Math.abs(x - Math.floor(x) - 0.5) * 2; }

    private static void paint(NativeImage img, int v) {
        double mouthRx = 0.27 + 0.05 * v, mouthRy = 0.28 + 0.07 * (v == 1 ? 1.4 : 1), mouthY = 0.5;
        double eyeRx = v == 1 ? 0.12 : 0.17, eyeRy = v == 1 ? 0.15 : 0.22;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double u = (x + 0.5) / SIZE * 2 - 1, w = (y + 0.5) / SIZE * 2 - 1;
                double fw = w > 0.25 ? 0.60 - (w - 0.25) * 0.55 : 0.60;
                double edge = (u / fw) * (u / fw) + ((w + 0.05) / 0.92) * ((w + 0.05) / 0.92);
                int rr = 0, gg = 0, bb = 0, aa = 0;
                if (edge < 1.0) {
                    double shade = 1 - 0.6 * edge * edge * edge;
                    double skin = 205 + (n(u * 9, w * 9, 3) - 0.5) * 55 + (h(x, y, 5) - 0.5) * 18;
                    skin *= shade;
                    rr = (int) skin; gg = (int) (skin * 0.93); bb = (int) (skin * 0.86);
                    aa = (int) Mth.clamp((1 - edge) * 9, 0, 1) * 255;
                    if (aa == 0) aa = (int) (255 * Mth.clamp((1 - edge) * 9, 0, 1));
                    // cracks and veins
                    double crack = Math.abs(n(u * 7 + 3, w * 7, 8) - 0.5);
                    if (crack < 0.014) { rr *= 0.25; gg *= 0.2; bb *= 0.2; }
                    double vein = Math.abs(n(u * 14, w * 14 + 9, 11) - 0.5);
                    if (vein < 0.01 && w < 0.1) { rr = (int) (rr * 0.6 + 70); gg *= 0.45; bb *= 0.5; }
                    for (int s = -1; s <= 1; s += 2) {
                        double ex = s * 0.27, ey = -0.18, dx = (u - ex) / eyeRx, dy = (w - ey) / eyeRy;
                        double d2 = dx * dx + dy * dy;
                        if (d2 < 1.25) {
                            double k = Mth.clamp((1.25 - d2) * 4, 0, 1);
                            int er = v == 2 ? 235 : 4, eg = v == 2 ? 225 : 2, eb = v == 2 ? 215 : 2;
                            if (d2 < 1.0 && v == 2) { double pu = (u - ex) / 0.05, pw = (w - ey - 0.02) / 0.07; if (pu * pu + pw * pw < 1) { er = 8; eg = 0; eb = 0; } }
                            if (d2 < 0.25 && v == 0) { er = 255; eg = 240; eb = 230; }
                            rr = (int) (rr + (er - rr) * k); gg = (int) (gg + (eg - gg) * k); bb = (int) (bb + (eb - bb) * k);
                        }
                        double dripLen = 0.2 + 0.75 * h((int) (ex * 100), s + 5, v);
                        if (Math.abs(u - ex - (n(w * 5, s, 2) - 0.5) * 0.05) < 0.03 && w > ey && w < ey + dripLen) {
                            double k = 1 - (w - ey) / dripLen;
                            rr = (int) (rr * (1 - k) + 70 * k); gg = (int) (gg * (1 - k) + 0); bb = (int) (bb * (1 - k) + 4 * k);
                        }
                    }
                    double nu = Math.abs(u) - 0.05, nw = (w - 0.13) / 0.06;
                    if (nu * nu * 900 + nw * nw < 1 && Math.abs(u) > 0.015) { rr = 20; gg = 8; bb = 8; }
                    double mx = u / mouthRx, my = (w - mouthY) / mouthRy;
                    double md = mx * mx + my * my;
                    if (md < 1.1) {
                        double k = Mth.clamp((1.1 - md) * 6, 0, 1);
                        int mr = (int) (30 + 38 * Math.max(0, my)), mg = 0, mb = 2;
                        double top = (w - (mouthY - mouthRy)), bot = ((mouthY + mouthRy) - w);
                        if (top < 0.1 * (0.35 + 0.65 * tri(u * 8.5)) && md < 1.0) { mr = 235; mg = 226; mb = 200; }
                        if (bot < 0.085 * (0.35 + 0.65 * tri(u * 7.3 + 0.4)) && md < 1.0) { mr = 225; mg = 215; mb = 190; }
                        rr = (int) (rr + (mr - rr) * k); gg = (int) (gg + (mg - gg) * k); bb = (int) (bb + (mb - bb) * k);
                    }
                    // blood running out of the mouth and over the chin
                    double flow = Math.abs(u - (n(w * 4, 7, 4) - 0.5) * 0.18);
                    if (w > mouthY + mouthRy * 0.6 && flow < 0.16 * (1 - (w - mouthY) * 0.5) && edge < 0.96) {
                        double k = Mth.clamp(0.85 - (n(u * 6, w * 6, 6) * 0.3), 0, 1);
                        rr = (int) (rr * (1 - k) + 105 * k); gg = (int) (gg * (1 - k) + 2 * k); bb = (int) (bb * (1 - k) + 6 * k);
                    }
                    // splatter
                    if (n(u * 5 + 20 * v, w * 5, 13) > 0.78) { rr = (int) (rr * 0.45 + 70); gg = (int) (gg * 0.2); bb = (int) (bb * 0.2); }
                } else {
                    // stringy hair around the skull
                    int col = x / 2;
                    if (Math.abs(u) < 0.9 && h(col, 0, 21 + v) < 0.5) {
                        double len = 0.35 + 0.65 * h(col, 1, 22 + v);
                        double top = -0.95;
                        double sway = (n(w * 3, col * 0.3, 9) - 0.5) * 0.04;
                        if (w < top + len * 2.0 && w > top && edge > 0.9 && Math.abs(u - (Math.floor(col) * 2.0 / SIZE * 2 - 1) - sway) < 0.02 && !(Math.abs(u) < 0.2 && w > -0.2)) {
                            aa = (int) (210 * (1 - (w - top) / (len * 2.0)));
                            rr = 6; gg = 4; bb = 5;
                        }
                    }
                }
                img.setPixelRGBA(x, y, abgr(aa, rr, gg, bb));
            }
        }
    }

    private FearFace() {}
}
