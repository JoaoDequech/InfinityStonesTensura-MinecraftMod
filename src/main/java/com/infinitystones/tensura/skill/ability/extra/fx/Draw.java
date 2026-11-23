package com.infinitystones.tensura.skill.ability.extra.fx;

import com.infinitystones.tensura.client.render.GlowFx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Additive glow primitives (RenderType.lightning: position + colour, blended with ONE). World coordinates. */
final class Draw {
    private final VertexConsumer vc;
    private final Matrix4f m;
    private final Vec3 cam;
    private float r, g, b;
    private int rgb;

    Draw(VertexConsumer vc, Matrix4f m, Vec3 cam) { this.vc = vc; this.m = m; this.cam = cam; }

    void color(int c) { rgb = c; r = ((c >> 16) & 255) / 255F; g = ((c >> 8) & 255) / 255F; b = (c & 255) / 255F; }

    // ------------------------------------------------------------------ basics

    private void v(Vec3 p, float al, boolean white) {
        float k = white ? 0.55F : 0;
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r + (1 - r) * k, g + (1 - g) * k, b + (1 - b) * k, Mth.clamp(al, 0, 1));
    }

    void quad(Vec3 a, Vec3 b2, Vec3 c, Vec3 d, float al) { quad(a, b2, c, d, al, false); }

    void quad(Vec3 a, Vec3 b2, Vec3 c, Vec3 d, float al, boolean white) {
        v(a, al, white); v(b2, al, white); v(c, al, white); v(d, al, white);
        v(d, al, white); v(c, al, white); v(b2, al, white); v(a, al, white);
    }

    /** Camera-facing strip between two points. */
    void ribbon(Vec3 p0, Vec3 p1, float half, float al, boolean white) {
        Vec3 seg = p1.subtract(p0);
        Vec3 toCam = cam.subtract(p0.add(seg.scale(0.5)));
        Vec3 n = seg.cross(toCam);
        if (n.lengthSqr() < 1e-10) return;
        n = n.normalize().scale(half);
        v(p0.add(n), al, white); v(p1.add(n), al, white); v(p1.subtract(n), al, white); v(p0.subtract(n), al, white);
    }

    /** Glowing line: soft halo + bright core. */
    void line(Vec3 a, Vec3 b2, float width, float al) {
        ribbon(a, b2, width * 2.2F, al * 0.18F, false);
        ribbon(a, b2, width, al * 0.5F, false);
        ribbon(a, b2, width * 0.35F, al * 0.9F, true);
    }

    void spark(Vec3 p, float s, float al) {
        Vec3 up = new Vec3(0, s, 0);
        ribbon(p.subtract(up), p.add(up), s * 0.4F, al, true);
        Vec3 toCam = cam.subtract(p).normalize();
        Vec3 side = toCam.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() > 1e-6) { side = side.normalize().scale(s); ribbon(p.subtract(side), p.add(side), s * 0.4F, al, true); }
    }

    // ------------------------------------------------------------------ shapes

    void beam(Vec3 a, Vec3 b2, float width, float al, float time) {
        Vec3 d = b2.subtract(a);
        double len = d.length();
        if (len < 0.05) return;
        Vec3 dir = d.scale(1 / len);
        Vec3 side = perp(dir), up = side.cross(dir).normalize();
        int segs = Mth.clamp((int) (len * 2.5), 4, 160);
        float flick = 0.85F + 0.15F * Mth.sin(time * 41F);
        for (int i = 0; i < segs; i++) {
            double t0 = (double) i / segs, t1 = (double) (i + 1) / segs;
            Vec3 p0 = a.add(d.scale(t0)), p1 = a.add(d.scale(t1));
            ribbon(p0, p1, width * 2.4F, al * 0.14F * flick, false);
            ribbon(p0, p1, width, al * 0.45F * flick, false);
            ribbon(p0, p1, width * 0.32F, al * 0.95F, true);
            for (int s = 0; s < 2; s++) {
                double a0 = time * 8 + t0 * len * 2.2 + s * Math.PI, a1 = time * 8 + t1 * len * 2.2 + s * Math.PI;
                double rr = width * 1.6;
                Vec3 h0 = p0.add(side.scale(Math.cos(a0) * rr)).add(up.scale(Math.sin(a0) * rr));
                Vec3 h1 = p1.add(side.scale(Math.cos(a1) * rr)).add(up.scale(Math.sin(a1) * rr));
                ribbon(h0, h1, width * 0.22F, al * 0.5F, false);
            }
        }
    }

    void chain(Vec3 a, Vec3 b2, float al, float time) {
        Vec3 d = b2.subtract(a);
        double len = d.length();
        if (len < 0.1) return;
        Vec3 dir = d.scale(1 / len), side = perp(dir), up = side.cross(dir).normalize();
        double sag = Math.min(1.2, len * 0.08);
        int links = Mth.clamp((int) (len / 0.42), 2, 120);
        Vec3 prev = a;
        for (int i = 1; i <= links; i++) {
            double t = (double) i / links;
            Vec3 p = a.add(d.scale(t)).add(0, -Math.sin(Math.PI * t) * sag + Math.sin(time * 4 + t * 12) * 0.03, 0);
            // alternate link orientation
            Vec3 axis = (i % 2 == 0 ? side : up).scale(0.09);
            Vec3 mid = prev.add(p).scale(0.5);
            ribbon(prev, p, 0.05F, al * 0.35F, false);
            ribbon(mid.subtract(axis), mid.add(axis), 0.05F, al * 0.9F, true);
            prev = p;
        }
        ribbon(a, b2, 0.12F, al * 0.08F, false);
    }

    /** Horizontal ring (normal = +Y). */
    void ring(Vec3 c, float radius, float width, float al, float phase) {
        int n = Mth.clamp((int) (radius * 10), 24, 160);
        for (int i = 0; i < n; i++) {
            double a0 = phase + Math.PI * 2 * i / n, a1 = phase + Math.PI * 2 * (i + 1) / n;
            Vec3 o0 = c.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius), o1 = c.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius);
            Vec3 i0 = c.add(Math.cos(a0) * (radius - width), 0, Math.sin(a0) * (radius - width));
            Vec3 i1 = c.add(Math.cos(a1) * (radius - width), 0, Math.sin(a1) * (radius - width));
            quad(i0, o0, o1, i1, al * 0.55F);
            ribbon(o0, o1, Math.max(0.02F, width * 0.18F), al, true);
        }
    }

    /** Ring in the plane with normal n. */
    void ringN(Vec3 c, Vec3 n, float radius, float width, float al, float phase) {
        Vec3 u = perp(n), w = n.cross(u).normalize();
        int segs = Mth.clamp((int) (radius * 10), 20, 140);
        for (int i = 0; i < segs; i++) {
            double a0 = phase + Math.PI * 2 * i / segs, a1 = phase + Math.PI * 2 * (i + 1) / segs;
            Vec3 p0 = c.add(u.scale(Math.cos(a0) * radius)).add(w.scale(Math.sin(a0) * radius));
            Vec3 p1 = c.add(u.scale(Math.cos(a1) * radius)).add(w.scale(Math.sin(a1) * radius));
            ribbon(p0, p1, width, al * 0.5F, false);
            ribbon(p0, p1, width * 0.3F, al, true);
        }
    }

    void dome(Vec3 c, float r, float al, float time) {
        int mer = 14, par = 7;
        for (int k = 0; k < mer; k++) {
            double phi = Math.PI * 2 * k / mer + time * 0.15;
            Vec3 prev = null;
            for (int j = 0; j <= 16; j++) {
                double th = Math.PI * j / 16;
                Vec3 p = c.add(Math.sin(th) * Math.cos(phi) * r, Math.cos(th) * r, Math.sin(th) * Math.sin(phi) * r);
                if (prev != null) ribbon(prev, p, 0.035F, al * 0.45F, false);
                prev = p;
            }
        }
        for (int j = 1; j < par; j++) {
            double th = Math.PI * j / par;
            ring(c.add(0, Math.cos(th) * r, 0), (float) (Math.sin(th) * r), 0.03F, al * 0.4F, time * 0.2F);
        }
        // shimmer band sweeping down
        double sweep = (time * 0.6) % 2.0 - 1.0;
        float sr = (float) Math.sqrt(Math.max(0, 1 - sweep * sweep)) * r;
        ring(c.add(0, sweep * r, 0), sr, 0.12F, al * 0.8F, 0);
        // faint shell
        int lat = 10, lon = 20;
        for (int j = 0; j < lat; j++) {
            double t0 = Math.PI * j / lat, t1 = Math.PI * (j + 1) / lat;
            for (int k = 0; k < lon; k++) {
                double p0 = Math.PI * 2 * k / lon, p1 = Math.PI * 2 * (k + 1) / lon;
                quad(sph(c, r, t0, p0), sph(c, r, t0, p1), sph(c, r, t1, p1), sph(c, r, t1, p0), al * 0.05F);
            }
        }
    }

    private static Vec3 sph(Vec3 c, double r, double th, double ph) {
        return c.add(Math.sin(th) * Math.cos(ph) * r, Math.cos(th) * r, Math.sin(th) * Math.sin(ph) * r);
    }

    void cube(Vec3 c, float h, float al, float time) {
        float pulse = 0.75F + 0.25F * Mth.sin(time * 5);
        Vec3[] v = new Vec3[8];
        for (int i = 0; i < 8; i++) v[i] = c.add((i & 1) == 0 ? -h : h, (i & 2) == 0 ? -h : h, (i & 4) == 0 ? -h : h);
        int[][] e = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] k : e) line(v[k[0]], v[k[1]], 0.05F, al * pulse);
        int[][] faces = {{0, 1, 3, 2}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};
        for (int[] f : faces) quad(v[f[0]], v[f[1]], v[f[2]], v[f[3]], al * 0.09F);
        // scanning plane
        double y = ((time * 0.8) % 1.0) * 2 * h - h;
        quad(c.add(-h, y, -h), c.add(h, y, -h), c.add(h, y, h), c.add(-h, y, h), al * 0.12F);
    }

    void portal(Vec3 c, Vec3 normal, float s, float al, float time) {
        Vec3 n = normal.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : normal.normalize();
        Vec3 u = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side = n.cross(u).normalize(), up = side.cross(n).normalize();
        int seg = 64;
        for (int ring = 0; ring < 4; ring++) {
            float k = 1 - ring * 0.18F;
            for (int i = 0; i < seg; i++) {
                double a0 = Math.PI * 2 * i / seg + time * (ring % 2 == 0 ? 1.4 : -1.1), a1 = Math.PI * 2 * (i + 1) / seg + time * (ring % 2 == 0 ? 1.4 : -1.1);
                double wob0 = 1 + 0.06 * Math.sin(a0 * 5 + time * 6), wob1 = 1 + 0.06 * Math.sin(a1 * 5 + time * 6);
                Vec3 p0 = c.add(side.scale(Math.cos(a0) * s * 0.7 * k * wob0)).add(up.scale(Math.sin(a0) * s * k * wob0));
                Vec3 p1 = c.add(side.scale(Math.cos(a1) * s * 0.7 * k * wob1)).add(up.scale(Math.sin(a1) * s * k * wob1));
                ribbon(p0, p1, ring == 0 ? 0.12F : 0.05F, al * (ring == 0 ? 0.9F : 0.5F), ring == 0);
            }
        }
        // swirl arms
        for (int arm = 0; arm < 5; arm++) {
            Vec3 prev = c;
            for (int j = 1; j <= 20; j++) {
                double t = j / 20.0, ang = arm * Math.PI * 2 / 5 + t * 3.5 - time * 2.4;
                Vec3 p = c.add(side.scale(Math.cos(ang) * s * 0.7 * t)).add(up.scale(Math.sin(ang) * s * t));
                ribbon(prev, p, 0.04F, al * 0.45F * (float) t, false);
                prev = p;
            }
        }
        // inner fill
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            quad(c, c, c.add(side.scale(Math.cos(a0) * s * 0.7)).add(up.scale(Math.sin(a0) * s)),
                    c.add(side.scale(Math.cos(a1) * s * 0.7)).add(up.scale(Math.sin(a1) * s)), al * 0.12F);
        }
    }

    void crown(Vec3 top, float r, float al, float time) {
        ring(top, r, 0.05F, al, time);
        int spikes = 8;
        for (int i = 0; i < spikes; i++) {
            double a0 = time * 1.6 + Math.PI * 2 * i / spikes;
            Vec3 base = top.add(Math.cos(a0) * r, 0, Math.sin(a0) * r);
            ribbon(base, base.add(0, 0.22 + 0.05 * Math.sin(time * 6 + i), 0), 0.035F, al, true);
        }
    }

    void pillar(Vec3 base, float radius, float height, float al, float time) {
        Vec3 top = base.add(0, height, 0);
        ribbon(base, top, radius, al * 0.25F, false);
        ribbon(base, top, radius * 0.4F, al * 0.6F, false);
        ribbon(base, top, radius * 0.12F, al, true);
        for (int i = 0; i < 4; i++) {
            float y = (float) (((time * 0.35 + i * 0.25) % 1.0) * height);
            ring(base.add(0, y, 0), radius * 1.6F, 0.04F, al * 0.7F * (1 - y / height), time * (i % 2 == 0 ? 1 : -1));
        }
    }

    void tunnel(Vec3 a, Vec3 b2, float radius, float al, float prog, float time) {
        Vec3 d = b2.subtract(a);
        double len = d.length();
        if (len < 0.5) return;
        Vec3 n = d.scale(1 / len);
        int rings = Mth.clamp((int) (len / 2.2), 3, 40);
        for (int i = 0; i <= rings; i++) {
            double t = (double) i / rings;
            float reveal = Mth.clamp((float) (prog * 3 - t), 0, 1);
            if (reveal <= 0) continue;
            float k = (float) (0.6 + 0.4 * Math.sin(Math.PI * t));
            ringN(a.add(d.scale(t)), n, radius * k * reveal, 0.05F, al * reveal, time * 2 + (float) t * 3);
        }
        beam(a, b2, 0.05F, al * 0.6F, time);
    }

    void panel(Vec3 c, Vec3 normal, float h, float al, float time) {
        Vec3 n = normal.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : normal.normalize();
        Vec3 side = n.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 1e-6) side = new Vec3(1, 0, 0);
        side = side.normalize();
        Vec3 up = side.cross(n).normalize();
        Vec3 p00 = c.subtract(side.scale(h)).subtract(up.scale(h)), p10 = c.add(side.scale(h)).subtract(up.scale(h));
        Vec3 p11 = c.add(side.scale(h)).add(up.scale(h)), p01 = c.subtract(side.scale(h)).add(up.scale(h));
        line(p00, p10, 0.05F, al); line(p10, p11, 0.05F, al); line(p11, p01, 0.05F, al); line(p01, p00, 0.05F, al);
        quad(p00, p10, p11, p01, al * 0.1F);
        // ripples from the centre
        for (int i = 0; i < 3; i++) {
            float rr = (float) (((time * 0.7 + i / 3.0) % 1.0) * h * 1.3);
            if (rr > h) continue;
            ringN(c, n, rr, 0.025F, al * 0.6F * (1 - rr / h), 0);
        }
        // grid shimmer
        for (int i = -3; i <= 3; i++) {
            double k = i / 3.0 * h;
            float w = 0.4F + 0.6F * Mth.sin(time * 3 + i);
            ribbon(c.add(side.scale(k)).subtract(up.scale(h)), c.add(side.scale(k)).add(up.scale(h)), 0.01F, al * 0.25F * w, false);
            ribbon(c.add(up.scale(k)).subtract(side.scale(h)), c.add(up.scale(k)).add(side.scale(h)), 0.01F, al * 0.25F * w, false);
        }
    }

    void clock(Vec3 c, float r, float al, float time) {
        ring(c, r, 0.08F, al, 0);
        ring(c, r * 0.88F, 0.025F, al * 0.6F, 0);
        for (int i = 0; i < 12; i++) {
            double a0 = Math.PI * 2 * i / 12;
            Vec3 o = c.add(Math.cos(a0) * r * 0.86, 0, Math.sin(a0) * r * 0.86);
            Vec3 in = c.add(Math.cos(a0) * r * (i % 3 == 0 ? 0.68 : 0.76), 0, Math.sin(a0) * r * (i % 3 == 0 ? 0.68 : 0.76));
            ribbon(in.add(0, 0.01, 0), o.add(0, 0.01, 0), i % 3 == 0 ? 0.06F : 0.03F, al, true);
        }
        double hm = time * 1.2, hh = time * 0.1;
        Vec3 c1 = c.add(0, 0.03, 0);
        line(c1, c1.add(Math.cos(hm) * r * 0.75, 0, Math.sin(hm) * r * 0.75), 0.05F, al);
        line(c1, c1.add(Math.cos(hh) * r * 0.45, 0, Math.sin(hh) * r * 0.45), 0.08F, al);
    }

    void spiral(Vec3 base, float radius, float height, float al, float prog, float time) {
        boolean down = height < 0;
        float hgt = Math.abs(height);
        int arms = 3;
        for (int arm = 0; arm < arms; arm++) {
            Vec3 prev = null;
            for (int j = 0; j <= 30; j++) {
                double t = j / 30.0;
                double vis = down ? 1 - prog : prog;
                if (Math.abs(t - vis) > 0.35) { prev = null; continue; }
                double ang = arm * Math.PI * 2 / arms + t * 9 + time * 3;
                double rr = radius * (1 - t * 0.5);
                Vec3 p = base.add(Math.cos(ang) * rr, t * hgt, Math.sin(ang) * rr);
                if (prev != null) ribbon(prev, p, 0.06F, al * (float) (1 - Math.abs(t - vis) / 0.35), true);
                prev = p;
            }
        }
    }

    void orb(Vec3 c, float s, float al, float time) {
        for (int i = 0; i < 3; i++) {
            Vec3 n = new Vec3(Math.sin(time * (1.3 + i) + i), Math.cos(time * (0.9 + i * 0.5)), Math.sin(time * 0.7 + i * 2)).normalize();
            ringN(c, n, s * (1.15F + i * 0.12F), 0.03F, al * 0.8F, time * 3);
        }
        for (int k = 0; k < 12; k++) {
            double a0 = time * 2 + k * 0.52, a1 = k * 1.7 + time;
            Vec3 p = c.add(Math.cos(a0) * Math.sin(a1) * s * 0.8, Math.cos(a1) * s * 0.8, Math.sin(a0) * Math.sin(a1) * s * 0.8);
            spark(p, 0.07F, al);
        }
    }

    // ------------------------------------------------------------------ textured (eyes render type)

    void ground(PoseStack ps, MultiBufferSource buf, ResourceLocation tex, Vec3 c, float radius, int color, float al, float spin) {
        ps.pushPose();
        ps.translate(c.x, c.y, c.z);
        GlowFx.groundQuad(ps, buf, tex, radius, color, al, spin);
        ps.popPose();
    }

    void flare(PoseStack ps, MultiBufferSource buf, Vec3 c, float size, int color, float al, float spin) {
        GlowFx.billboard(ps, buf, GlowFx.FLARE, true, (float) c.x, (float) c.y, (float) c.z, size, color, al, spin);
        GlowFx.billboard(ps, buf, GlowFx.HALO, true, (float) c.x, (float) c.y, (float) c.z, size * 0.7F, color, al * 0.6F, 0);
    }

    static Vec3 perp(Vec3 dir) {
        Vec3 s = dir.cross(new Vec3(0, 1, 0));
        if (s.lengthSqr() < 1e-4) s = dir.cross(new Vec3(1, 0, 0));
        return s.normalize();
    }
}
