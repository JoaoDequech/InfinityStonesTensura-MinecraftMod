package com.infinitystones.tensura.skill.ability.extra.v2d.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Additive glow primitives for the v2d clocks (RenderType.lightning: position + colour). World coordinates. */
final class V2dDraw {
    private static final int[] SEGMENTS = {0b1110111, 0b0100100, 0b1011101, 0b1101101, 0b0101110, 0b1101011, 0b1111011, 0b0100101, 0b1111111, 0b1101111};

    private final VertexConsumer vc;
    private final Matrix4f m;
    final Vec3 cam;
    private float r, g, b;

    V2dDraw(VertexConsumer vc, Matrix4f m, Vec3 cam) { this.vc = vc; this.m = m; this.cam = cam; }

    void color(int c) { r = ((c >> 16) & 255) / 255F; g = ((c >> 8) & 255) / 255F; b = (c & 255) / 255F; }

    private void v(Vec3 p, float al, boolean white) {
        float k = white ? 0.55F : 0;
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r + (1 - r) * k, g + (1 - g) * k, b + (1 - b) * k, Mth.clamp(al, 0, 1));
    }

    void quad(Vec3 a, Vec3 b2, Vec3 c, Vec3 d, float al) {
        v(a, al, false); v(b2, al, false); v(c, al, false); v(d, al, false);
        v(d, al, false); v(c, al, false); v(b2, al, false); v(a, al, false);
    }

    void ribbon(Vec3 p0, Vec3 p1, float half, float al, boolean white) {
        Vec3 seg = p1.subtract(p0);
        Vec3 n = seg.cross(cam.subtract(p0.add(seg.scale(0.5))));
        if (n.lengthSqr() < 1e-10) return;
        n = n.normalize().scale(half);
        v(p0.add(n), al, white); v(p1.add(n), al, white); v(p1.subtract(n), al, white); v(p0.subtract(n), al, white);
    }

    void line(Vec3 a, Vec3 b2, float width, float al) {
        ribbon(a, b2, width * 2.2F, al * 0.18F, false);
        ribbon(a, b2, width, al * 0.5F, false);
        ribbon(a, b2, width * 0.35F, al * 0.9F, true);
    }

    void spark(Vec3 p, float s, float al) {
        Vec3 up = new Vec3(0, s, 0);
        ribbon(p.subtract(up), p.add(up), s * 0.4F, al, true);
        Vec3 side = cam.subtract(p).cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() > 1e-6) { side = side.normalize().scale(s); ribbon(p.subtract(side), p.add(side), s * 0.4F, al, true); }
    }

    static Vec3 at(Vec3 c, Vec3 u, Vec3 w, double x, double y) { return c.add(u.scale(x)).add(w.scale(y)); }

    /** Ring in the plane spanned by u (right) and w (up). Dashed when dash > 0 (number of dashes). */
    void ring(Vec3 c, Vec3 u, Vec3 w, float radius, float width, float al, float phase, int dash) {
        int n = Mth.clamp((int) (radius * 14), 32, 120);
        if (dash > 0) n = dash * 4;
        for (int i = 0; i < n; i++) {
            if (dash > 0 && (i & 3) == 3) continue;
            double a0 = phase + Math.PI * 2 * i / n, a1 = phase + Math.PI * 2 * (i + 1) / n;
            Vec3 p0 = at(c, u, w, Math.cos(a0) * radius, Math.sin(a0) * radius), p1 = at(c, u, w, Math.cos(a1) * radius, Math.sin(a1) * radius);
            ribbon(p0, p1, width, al * 0.5F, false);
            ribbon(p0, p1, width * 0.3F, al, true);
        }
    }

    /** Clock dial. hand1/hand2 are the minute / hour hand angles (radians, 0 = twelve o'clock, clockwise). */
    void face(Vec3 c, Vec3 u, Vec3 w, float rad, float al, float hand1, float hand2, float spin) {
        ring(c, u, w, rad, rad * 0.045F, al, 0, 0);
        ring(c, u, w, rad * 0.9F, rad * 0.015F, al * 0.7F, 0, 0);
        ring(c, u, w, rad * 1.12F, rad * 0.02F, al * 0.8F, spin, 24);
        int fan = 32;
        for (int i = 0; i < fan; i++) {
            double a0 = Math.PI * 2 * i / fan, a1 = Math.PI * 2 * (i + 1) / fan;
            quad(c, c, at(c, u, w, Math.cos(a0) * rad * 0.9, Math.sin(a0) * rad * 0.9), at(c, u, w, Math.cos(a1) * rad * 0.9, Math.sin(a1) * rad * 0.9), al * 0.1F);
        }
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            double in = i % 3 == 0 ? 0.72 : 0.8;
            ribbon(at(c, u, w, Math.sin(a) * rad * in, Math.cos(a) * rad * in), at(c, u, w, Math.sin(a) * rad * 0.88, Math.cos(a) * rad * 0.88),
                    i % 3 == 0 ? rad * 0.03F : rad * 0.015F, al, true);
        }
        Vec3 front = c.add(w.cross(u).normalize().scale(rad * 0.01));
        line(front, at(front, u, w, Math.sin(hand1) * rad * 0.78, Math.cos(hand1) * rad * 0.78), rad * 0.025F, al);
        line(front, at(front, u, w, Math.sin(hand2) * rad * 0.5, Math.cos(hand2) * rad * 0.5), rad * 0.04F, al);
        spark(front, rad * 0.06F, al);
    }

    /** Seven-segment number (0..99) centred on c. h = digit height. */
    void number(Vec3 c, Vec3 u, Vec3 w, int value, float h, float al) {
        value = Mth.clamp(value, 0, 99);
        String s = Integer.toString(value);
        float cell = h * 0.75F;
        float x0 = -(s.length() - 1) * cell * 0.5F;
        for (int i = 0; i < s.length(); i++) digit(at(c, u, w, x0 + i * cell, 0), u, w, s.charAt(i) - '0', h, al);
    }

    private void digit(Vec3 c, Vec3 u, Vec3 w, int d, float h, float al) {
        int mask = SEGMENTS[d];
        float hw = h * 0.28F, hh = h * 0.5F, t = h * 0.07F;
        Vec3 tl = at(c, u, w, -hw, hh), tr = at(c, u, w, hw, hh), ml = at(c, u, w, -hw, 0), mr = at(c, u, w, hw, 0),
                bl = at(c, u, w, -hw, -hh), br = at(c, u, w, hw, -hh);
        // bit order: top, upper-left, upper-right, middle, lower-left, lower-right, bottom
        if ((mask & 1) != 0) line(tl, tr, t, al);
        if ((mask & 2) != 0) line(tl, ml, t, al);
        if ((mask & 4) != 0) line(tr, mr, t, al);
        if ((mask & 8) != 0) line(ml, mr, t, al);
        if ((mask & 16) != 0) line(ml, bl, t, al);
        if ((mask & 32) != 0) line(mr, br, t, al);
        if ((mask & 64) != 0) line(bl, br, t, al);
    }
}
