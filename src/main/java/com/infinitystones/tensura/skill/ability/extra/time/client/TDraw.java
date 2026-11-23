package com.infinitystones.tensura.skill.ability.extra.time.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * POSITION_COLOR quad primitives in world coordinates for the Time renderer. Works for the additive pass
 * (RenderType.lightning, culled → both windings) and the translucent pass (RenderType.debugQuads, no cull → one winding).
 */
final class TDraw {
    private final VertexConsumer vc;
    private final Matrix4f m;
    final Vec3 cam;
    private final boolean both;

    TDraw(VertexConsumer vc, Matrix4f m, Vec3 cam, boolean both) { this.vc = vc; this.m = m; this.cam = cam; this.both = both; }

    static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255), g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255), bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    static int lighten(int c, float k) { return mix(c, 0xFFFFFF, k); }

    void v(Vec3 p, int rgb, float al) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z)
                .setColor(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F, Mth.clamp(al, 0, 1));
    }

    void quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, float al) { quad4(a, b, c, d, rgb, al, rgb, al, rgb, al, rgb, al); }

    /** Quad with a colour/alpha per vertex. */
    void quad4(Vec3 a, Vec3 b, Vec3 c, Vec3 d, int ca, float aa, int cb, float ab, int cc, float ac, int cd, float ad) {
        v(a, ca, aa); v(b, cb, ab); v(c, cc, ac); v(d, cd, ad);
        if (both) { v(d, cd, ad); v(c, cc, ac); v(b, cb, ab); v(a, ca, aa); }
    }

    /** Camera-facing strip; colour/alpha may differ at both ends (gradients, fading lines). */
    void ribbon(Vec3 p0, Vec3 p1, float half, int c0, float a0, int c1, float a1) {
        Vec3 seg = p1.subtract(p0);
        Vec3 toCam = cam.subtract(p0.add(seg.scale(0.5)));
        Vec3 n = seg.cross(toCam);
        if (n.lengthSqr() < 1e-10) return;
        n = n.normalize().scale(half);
        quad4(p0.add(n), p1.add(n), p1.subtract(n), p0.subtract(n), c0, a0, c1, a1, c1, a1, c0, a0);
    }

    void ribbon(Vec3 p0, Vec3 p1, float half, int c, float al) { ribbon(p0, p1, half, c, al, c, al); }

    /** Glowing line: soft halo + bright core (additive pass). */
    void line(Vec3 a, Vec3 b, float width, int c, float al) {
        ribbon(a, b, width * 2.4F, c, al * 0.16F);
        ribbon(a, b, width, c, al * 0.5F);
        ribbon(a, b, width * 0.35F, lighten(c, 0.55F), al * 0.95F);
    }

    void fadeLine(Vec3 a, Vec3 b, float width, int c, float a0, float a1) {
        ribbon(a, b, width * 2.2F, c, a0 * 0.15F, c, a1 * 0.15F);
        ribbon(a, b, width * 0.4F, lighten(c, 0.5F), a0, lighten(c, 0.5F), a1);
    }

    void spark(Vec3 p, float s, int c, float al) {
        Vec3 up = new Vec3(0, s, 0);
        int w = lighten(c, 0.6F);
        ribbon(p.subtract(up), p.add(up), s * 0.4F, w, al);
        Vec3 toCam = cam.subtract(p).normalize();
        Vec3 side = toCam.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() > 1e-6) { side = side.normalize().scale(s); ribbon(p.subtract(side), p.add(side), s * 0.4F, w, al); }
    }

    /** Horizontal ring: flat band + bright rim. */
    void ring(Vec3 c, float radius, float width, int col, float al, float phase) {
        int n = Mth.clamp((int) (radius * 8), 24, 192);
        for (int i = 0; i < n; i++) {
            double a0 = phase + Math.PI * 2 * i / n, a1 = phase + Math.PI * 2 * (i + 1) / n;
            Vec3 o0 = c.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius), o1 = c.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius);
            float ri = Math.max(0, radius - width);
            Vec3 i0 = c.add(Math.cos(a0) * ri, 0, Math.sin(a0) * ri), i1 = c.add(Math.cos(a1) * ri, 0, Math.sin(a1) * ri);
            quad(i0, o0, o1, i1, col, al * 0.5F);
            ribbon(o0, o1, Math.max(0.015F, width * 0.18F), lighten(col, 0.5F), al);
        }
    }

    /** Dashed horizontal ring (gaps), rotating with phase. */
    void dashed(Vec3 c, float radius, float half, int col, float al, float phase, int dashes, float fill) {
        for (int i = 0; i < dashes; i++) {
            double a0 = phase + Math.PI * 2 * i / dashes, a1 = a0 + Math.PI * 2 / dashes * fill;
            int segs = 4;
            for (int s = 0; s < segs; s++) {
                double b0 = Mth.lerp(s / (double) segs, a0, a1), b1 = Mth.lerp((s + 1) / (double) segs, a0, a1);
                ribbon(c.add(Math.cos(b0) * radius, 0, Math.sin(b0) * radius), c.add(Math.cos(b1) * radius, 0, Math.sin(b1) * radius), half, col, al);
            }
        }
    }

    /** Ring in the plane with normal n. */
    void ringN(Vec3 c, Vec3 n, float radius, float width, int col, float al, float phase) {
        Vec3 u = perp(n), w = n.cross(u).normalize();
        int segs = Mth.clamp((int) (radius * 10), 20, 140);
        for (int i = 0; i < segs; i++) {
            double a0 = phase + Math.PI * 2 * i / segs, a1 = phase + Math.PI * 2 * (i + 1) / segs;
            Vec3 p0 = c.add(u.scale(Math.cos(a0) * radius)).add(w.scale(Math.sin(a0) * radius));
            Vec3 p1 = c.add(u.scale(Math.cos(a1) * radius)).add(w.scale(Math.sin(a1) * radius));
            ribbon(p0, p1, width, col, al * 0.5F);
            ribbon(p0, p1, width * 0.3F, lighten(col, 0.5F), al);
        }
    }

    /** Clock face in the plane with normal n: rim, 12 ticks, two hands at the given angles. */
    void dial(Vec3 c, Vec3 n, float r, int col, float al, double minute, double hour) {
        Vec3 u = perp(n), w = n.cross(u).normalize();
        ringN(c, n, r, 0.05F, col, al, 0);
        ringN(c, n, r * 0.84F, 0.02F, col, al * 0.6F, 0);
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            Vec3 dir = u.scale(Math.cos(a)).add(w.scale(Math.sin(a)));
            float in = i % 3 == 0 ? 0.62F : 0.72F;
            ribbon(c.add(dir.scale(r * in)), c.add(dir.scale(r * 0.82)), i % 3 == 0 ? 0.05F : 0.025F, lighten(col, 0.5F), al);
        }
        Vec3 hm = u.scale(Math.cos(minute)).add(w.scale(Math.sin(minute)));
        Vec3 hh = u.scale(Math.cos(hour)).add(w.scale(Math.sin(hour)));
        line(c, c.add(hm.scale(r * 0.78)), 0.04F, col, al);
        line(c, c.add(hh.scale(r * 0.48)), 0.06F, col, al);
        spark(c, 0.08F, col, al);
    }

    /** Low-poly ellipsoid puff (translucent pass) shaded from top (light) to bottom (dark). */
    void puff(Vec3 c, double rx, double ry, double rz, int top, int bottom, float al) {
        int lat = 4, lon = 8;
        for (int j = 0; j < lat; j++) {
            double t0 = Math.PI * j / lat, t1 = Math.PI * (j + 1) / lat;
            int c0 = mix(top, bottom, (float) j / lat), c1 = mix(top, bottom, (float) (j + 1) / lat);
            for (int k = 0; k < lon; k++) {
                double p0 = Math.PI * 2 * k / lon, p1 = Math.PI * 2 * (k + 1) / lon;
                quad4(sph(c, rx, ry, rz, t0, p0), sph(c, rx, ry, rz, t0, p1), sph(c, rx, ry, rz, t1, p1), sph(c, rx, ry, rz, t1, p0),
                        c0, al, c0, al, c1, al, c1, al);
            }
        }
    }

    private static Vec3 sph(Vec3 c, double rx, double ry, double rz, double th, double ph) {
        return c.add(Math.sin(th) * Math.cos(ph) * rx, Math.cos(th) * ry, Math.sin(th) * Math.sin(ph) * rz);
    }

    static Vec3 perp(Vec3 dir) {
        Vec3 s = dir.cross(new Vec3(0, 1, 0));
        if (s.lengthSqr() < 1e-4) s = dir.cross(new Vec3(1, 0, 0));
        return s.normalize();
    }
}
