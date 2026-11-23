package com.infinitystones.tensura.client.will;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Hard-light geometry in the Reality Stone's red, drawn as flat quads (lightning render type, additive glow):
 * layered "magic circle" seals — double outer ring, rotating glyph band, inner counter-rotating ring, hexagram,
 * radial spokes and a pulsing core — plus helpers for rings and bars. Everything is procedural (no textures),
 * cheap (a few hundred quads per seal) and shared by the grip beam, the lock seal and construct summon / implode.
 */
final class HardLightFx {
    static float R = 1F, G = 0.2F, B = 0.27F;

    /**
     * A full seal on the plane through {@code c} with normal {@code n}. {@code radius} = outer radius in blocks,
     * {@code time} in seconds (drives the rotations), {@code a} = overall alpha (0..1), {@code spin} = direction.
     */
    static void seal(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 n, double radius, float time, float a, float spin) {
        if (a <= 0.01F || radius < 0.05) return;
        Vec3[] basis = basis(n);
        Vec3 u = basis[0], v = basis[1];
        double r = radius;
        int segs = Mth.clamp((int) (r * 18), 24, 96);
        // outer double ring
        ring(vc, m, c, u, v, r, r * 0.022 + 0.01, segs, 0, a * 0.95F);
        ring(vc, m, c, u, v, r * 0.93, r * 0.012 + 0.006, segs, 0, a * 0.7F);
        // glyph band between them: short bars of varying length, the whole band turns
        int glyphs = Mth.clamp((int) (r * 14), 12, 64);
        double band = time * 0.6 * spin;
        for (int i = 0; i < glyphs; i++) {
            double ang = band + Math.PI * 2 * i / glyphs;
            int kind = (i * 7 + 3) % 5;
            double len = r * (kind == 0 ? 0.05 : kind == 1 ? 0.025 : 0.035);
            double mid = r * 0.965;
            Vec3 dir = u.scale(Math.cos(ang)).add(v.scale(Math.sin(ang)));
            Vec3 tan = u.scale(-Math.sin(ang)).add(v.scale(Math.cos(ang)));
            Vec3 p = c.add(dir.scale(mid));
            double w = r * (kind == 2 ? 0.02 : 0.009);
            plane(vc, m, p.add(tan.scale(-w)).add(dir.scale(-len / 2)), p.add(tan.scale(w)).add(dir.scale(-len / 2)),
                    p.add(tan.scale(w)).add(dir.scale(len / 2)), p.add(tan.scale(-w)).add(dir.scale(len / 2)), a * (kind == 2 ? 0.9F : 0.6F));
        }
        // inner ring, counter-rotating dashes
        dashed(vc, m, c, u, v, r * 0.72, r * 0.012 + 0.005, 24, -time * 1.1 * spin, a * 0.75F);
        ring(vc, m, c, u, v, r * 0.66, r * 0.008 + 0.004, segs, 0, a * 0.55F);
        // hexagram (two triangles), slowly turning
        double hex = time * 0.35 * spin;
        polygon(vc, m, c, u, v, r * 0.66, 3, hex, r * 0.01 + 0.005, a * 0.8F);
        polygon(vc, m, c, u, v, r * 0.66, 3, hex + Math.PI / 3, r * 0.01 + 0.005, a * 0.8F);
        // radial spokes from the inner ring to the outer ring
        for (int i = 0; i < 12; i++) {
            double ang = -hex * 0.5 + Math.PI * 2 * i / 12;
            Vec3 dir = u.scale(Math.cos(ang)).add(v.scale(Math.sin(ang)));
            bar(vc, m, c.add(dir.scale(r * 0.74)), c.add(dir.scale(r * (i % 3 == 0 ? 0.93 : 0.84))), n, r * 0.006 + 0.003, a * 0.5F);
        }
        // core
        ring(vc, m, c, u, v, r * 0.22, r * 0.012 + 0.004, 32, 0, a * 0.8F);
        polygon(vc, m, c, u, v, r * 0.2, 4, -hex * 2, r * 0.008 + 0.003, a * 0.6F);
        float pulse = 0.5F + 0.5F * Mth.sin(time * 6);
        disc(vc, m, c, u, v, r * (0.08 + 0.03 * pulse), a * (0.35F + 0.25F * pulse));
        // soft halo
        ring(vc, m, c, u, v, r * 1.04, r * 0.06, segs, 0, a * 0.12F);
    }

    /** A smaller, simpler seal (hand / beam ends). */
    static void sigil(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 n, double r, float time, float a) {
        Vec3[] b = basis(n);
        ring(vc, m, c, b[0], b[1], r, r * 0.05 + 0.006, 32, 0, a);
        dashed(vc, m, c, b[0], b[1], r * 0.75, r * 0.04 + 0.004, 12, time * 2.2, a * 0.8F);
        polygon(vc, m, c, b[0], b[1], r * 0.7, 3, -time * 1.4, r * 0.03 + 0.003, a * 0.7F);
        polygon(vc, m, c, b[0], b[1], r * 0.7, 3, -time * 1.4 + Math.PI / 3, r * 0.03 + 0.003, a * 0.7F);
        disc(vc, m, c, b[0], b[1], r * 0.18, a * 0.5F);
    }

    static Vec3[] basis(Vec3 n) {
        n = n.normalize();
        Vec3 u = n.cross(Math.abs(n.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0)).normalize();
        Vec3 v = n.cross(u).normalize();
        return new Vec3[]{u, v};
    }

    static void ring(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 u, Vec3 v, double r, double w, int segs, double phase, float a) {
        for (int i = 0; i < segs; i++) {
            double a0 = phase + Math.PI * 2 * i / segs, a1 = phase + Math.PI * 2 * (i + 1) / segs;
            arc(vc, m, c, u, v, r, w, a0, a1, a);
        }
    }

    static void dashed(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 u, Vec3 v, double r, double w, int dashes, double phase, float a) {
        for (int i = 0; i < dashes; i++) {
            double a0 = phase + Math.PI * 2 * i / dashes, a1 = a0 + Math.PI * 2 / dashes * 0.6;
            arc(vc, m, c, u, v, r, w, a0, (a0 + a1) / 2, a);
            arc(vc, m, c, u, v, r, w, (a0 + a1) / 2, a1, a);
        }
    }

    private static void arc(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 u, Vec3 v, double r, double w, double a0, double a1, float a) {
        Vec3 d0 = u.scale(Math.cos(a0)).add(v.scale(Math.sin(a0))), d1 = u.scale(Math.cos(a1)).add(v.scale(Math.sin(a1)));
        plane(vc, m, c.add(d0.scale(r - w)), c.add(d1.scale(r - w)), c.add(d1.scale(r + w)), c.add(d0.scale(r + w)), a);
    }

    static void polygon(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 u, Vec3 v, double r, int sides, double phase, double w, float a) {
        Vec3 n = u.cross(v);
        for (int i = 0; i < sides; i++) {
            double a0 = phase + Math.PI * 2 * i / sides, a1 = phase + Math.PI * 2 * (i + 1) / sides;
            bar(vc, m, c.add(u.scale(Math.cos(a0) * r)).add(v.scale(Math.sin(a0) * r)),
                    c.add(u.scale(Math.cos(a1) * r)).add(v.scale(Math.sin(a1) * r)), n, w, a);
        }
    }

    static void disc(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 u, Vec3 v, double r, float a) {
        for (int i = 0; i < 16; i++) {
            double a0 = Math.PI * 2 * i / 16, a1 = Math.PI * 2 * (i + 1) / 16;
            Vec3 p0 = c.add(u.scale(Math.cos(a0) * r)).add(v.scale(Math.sin(a0) * r)), p1 = c.add(u.scale(Math.cos(a1) * r)).add(v.scale(Math.sin(a1) * r));
            plane(vc, m, c, p0, p1, c, a);
        }
    }

    /** Flat bar from a to b lying in the plane with normal n. */
    static void bar(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 n, double w, float alpha) {
        Vec3 d = b.subtract(a);
        Vec3 s = d.cross(n);
        if (s.lengthSqr() < 1e-10) return;
        s = s.normalize().scale(w);
        plane(vc, m, a.add(s), b.add(s), b.subtract(s), a.subtract(s), alpha);
    }

    /** Double-sided quad. */
    static void plane(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float alpha) {
        v(vc, m, a, alpha); v(vc, m, b, alpha); v(vc, m, c, alpha); v(vc, m, d, alpha);
        v(vc, m, d, alpha); v(vc, m, c, alpha); v(vc, m, b, alpha); v(vc, m, a, alpha);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Vec3 p, float a) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(R, G, B, Mth.clamp(a, 0F, 1F));
    }

    private HardLightFx() {}
}
