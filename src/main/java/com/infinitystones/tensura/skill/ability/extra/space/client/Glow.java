package com.infinitystones.tensura.skill.ability.extra.space.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Additive glow primitives for the Space client renderers (RenderType.lightning, world coordinates). */
final class Glow {
    private final VertexConsumer vc;
    private final Matrix4f m;
    private final Vec3 cam;
    private float r, g, b;

    Glow(VertexConsumer vc, Matrix4f m, Vec3 cam) { this.vc = vc; this.m = m; this.cam = cam; }

    Glow color(int c) { r = ((c >> 16) & 255) / 255F; g = ((c >> 8) & 255) / 255F; b = (c & 255) / 255F; return this; }

    private void v(Vec3 p, float al, boolean white) {
        float k = white ? 0.6F : 0;
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r + (1 - r) * k, g + (1 - g) * k, b + (1 - b) * k, Mth.clamp(al, 0, 1));
    }

    void quad(Vec3 a, Vec3 b2, Vec3 c, Vec3 d, float al) {
        v(a, al, false); v(b2, al, false); v(c, al, false); v(d, al, false);
        v(d, al, false); v(c, al, false); v(b2, al, false); v(a, al, false);
    }

    void ribbon(Vec3 p0, Vec3 p1, float half, float al, boolean white) {
        Vec3 seg = p1.subtract(p0);
        Vec3 toCam = cam.subtract(p0.add(seg.scale(0.5)));
        Vec3 n = seg.cross(toCam);
        if (n.lengthSqr() < 1e-10) return;
        n = n.normalize().scale(half);
        v(p0.add(n), al, white); v(p1.add(n), al, white); v(p1.subtract(n), al, white); v(p0.subtract(n), al, white);
    }

    void line(Vec3 a, Vec3 b2, float width, float al) {
        ribbon(a, b2, width * 2.4F, al * 0.16F, false);
        ribbon(a, b2, width, al * 0.5F, false);
        ribbon(a, b2, width * 0.35F, al * 0.95F, true);
    }

    void spark(Vec3 p, float s, float al) {
        ribbon(p.add(0, -s, 0), p.add(0, s, 0), s * 0.35F, al, true);
        Vec3 side = cam.subtract(p).normalize().cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() > 1e-6) { side = side.normalize().scale(s); ribbon(p.subtract(side), p.add(side), s * 0.35F, al, true); }
    }

    /** Glowing box edges with a travelling light pulse along each edge. */
    void box(Vec3 lo, Vec3 hi, float width, float al, float time) {
        Vec3[] c = corners(lo, hi);
        int[][] e = {{0, 1}, {1, 3}, {3, 2}, {2, 0}, {4, 5}, {5, 7}, {7, 6}, {6, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int i = 0; i < e.length; i++) {
            Vec3 a = c[e[i][0]], b2 = c[e[i][1]];
            line(a, b2, width, al);
            float t = (time * 0.7F + i * 0.137F) % 1F;
            Vec3 p = a.lerp(b2, t);
            spark(p, width * 3.5F, al * 0.9F);
        }
    }

    /** Faint faces of a box (sheet of light). */
    void faces(Vec3 lo, Vec3 hi, float al) {
        Vec3[] c = corners(lo, hi);
        quad(c[0], c[1], c[3], c[2], al); quad(c[4], c[5], c[7], c[6], al);
        quad(c[0], c[1], c[5], c[4], al); quad(c[2], c[3], c[7], c[6], al);
        quad(c[0], c[2], c[6], c[4], al); quad(c[1], c[3], c[7], c[5], al);
    }

    static Vec3[] corners(Vec3 lo, Vec3 hi) {
        return new Vec3[]{
                new Vec3(lo.x, lo.y, lo.z), new Vec3(hi.x, lo.y, lo.z), new Vec3(lo.x, lo.y, hi.z), new Vec3(hi.x, lo.y, hi.z),
                new Vec3(lo.x, hi.y, lo.z), new Vec3(hi.x, hi.y, lo.z), new Vec3(lo.x, hi.y, hi.z), new Vec3(hi.x, hi.y, hi.z)};
    }

    /** Pulsing corner marker: small glowing cube + star. */
    void corner(Vec3 p, float s, float al) {
        Vec3 lo = p.subtract(s, s, s), hi = p.add(s, s, s);
        Vec3[] c = corners(lo, hi);
        int[][] e = {{0, 1}, {1, 3}, {3, 2}, {2, 0}, {4, 5}, {5, 7}, {7, 6}, {6, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] k : e) ribbon(c[k[0]], c[k[1]], s * 0.08F, al, true);
        spark(p, s * 1.6F, al);
    }

    /** Horizontal ring. */
    void ring(Vec3 c, float radius, float width, float al, float phase) {
        int n = Mth.clamp((int) (radius * 10), 24, 120);
        for (int i = 0; i < n; i++) {
            if (i % 8 == 7) continue;
            double a0 = phase + Math.PI * 2 * i / n, a1 = phase + Math.PI * 2 * (i + 1) / n;
            Vec3 p0 = c.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius), p1 = c.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius);
            ribbon(p0, p1, width, al * 0.55F, false);
            ribbon(p0, p1, width * 0.3F, al, true);
        }
    }
}
