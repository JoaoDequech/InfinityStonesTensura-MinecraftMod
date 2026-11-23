package com.infinitystones.tensura.skill.ability.extra.power.client;

import com.infinitystones.tensura.skill.ability.extra.power.PowerNet;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Additive glow primitives for the Power effects (camera-relative coordinates, single-sided quads on a no-cull type).
 * The centrepiece is {@link #magic}: a layered magic circle (double rims, pixel-rune bands, octagram, satellite circles,
 * tick ring, hexagram, spokes, core) with every layer turning at its own speed/direction.
 */
final class PDraw {
    static final Vec3 UP = new Vec3(0, 1, 0);

    private final VertexConsumer vc;
    private final Matrix4f m;
    final Vec3 cam;
    private float r, g, b;

    PDraw(VertexConsumer vc, Matrix4f m, Vec3 cam) { this.vc = vc; this.m = m; this.cam = cam; }

    void color(int c) { r = ((c >> 16) & 255) / 255F; g = ((c >> 8) & 255) / 255F; b = (c & 255) / 255F; }

    void mix(int c1, int c2, float t) {
        t = Mth.clamp(t, 0, 1);
        r = Mth.lerp(t, ((c1 >> 16) & 255) / 255F, ((c2 >> 16) & 255) / 255F);
        g = Mth.lerp(t, ((c1 >> 8) & 255) / 255F, ((c2 >> 8) & 255) / 255F);
        b = Mth.lerp(t, (c1 & 255) / 255F, (c2 & 255) / 255F);
    }

    private void v(double x, double y, double z, float al, float wk) {
        vc.addVertex(m, (float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z))
                .setColor(r + (1 - r) * wk, g + (1 - g) * wk, b + (1 - b) * wk, Mth.clamp(al, 0, 1));
    }

    private void v(Vec3 p, float al, float wk) { v(p.x, p.y, p.z, al, wk); }

    void quad(Vec3 a, Vec3 b2, Vec3 c, Vec3 d, float al, float wk) {
        if (al <= 0.003F) return;
        v(a, al, wk); v(b2, al, wk); v(c, al, wk); v(d, al, wk);
    }

    // ------------------------------------------------------------------ camera facing

    void ribbon(Vec3 p0, Vec3 p1, double half, float al, float wk) {
        if (al <= 0.003F) return;
        Vec3 seg = p1.subtract(p0);
        Vec3 toCam = cam.subtract(p0.add(seg.scale(0.5)));
        Vec3 n = seg.cross(toCam);
        if (n.lengthSqr() < 1e-10) return;
        n = n.normalize().scale(half);
        v(p0.add(n), al, wk); v(p1.add(n), al, wk); v(p1.subtract(n), al, wk); v(p0.subtract(n), al, wk);
    }

    void line(Vec3 a, Vec3 b2, double width, float al) {
        ribbon(a, b2, width * 2.4, al * 0.16F, 0);
        ribbon(a, b2, width, al * 0.5F, 0.1F);
        ribbon(a, b2, width * 0.35, al * 0.95F, 0.7F);
    }

    void spark(Vec3 p, double s, float al) {
        ribbon(p.subtract(0, s, 0), p.add(0, s, 0), s * 0.35, al, 0.8F);
        Vec3 side = cam.subtract(p).cross(UP);
        if (side.lengthSqr() > 1e-6) { side = side.normalize().scale(s); ribbon(p.subtract(side), p.add(side), s * 0.35, al, 0.8F); }
    }

    // ------------------------------------------------------------------ planes

    /** A drawing plane: centre + orthonormal basis (u, w) with normal n. */
    static final class P {
        final Vec3 c, n, u, w;
        P(Vec3 c, Vec3 normal) {
            this.c = c;
            this.n = normal.lengthSqr() < 1e-8 ? UP : normal.normalize();
            Vec3 s = n.cross(Math.abs(n.y) > 0.95 ? new Vec3(1, 0, 0) : UP).normalize();
            this.u = s;
            this.w = n.cross(s).normalize();
        }
        private P(Vec3 c, P basis) { this.c = c; this.n = basis.n; this.u = basis.u; this.w = basis.w; }
        P at(Vec3 c2) { return new P(c2, this); }
        P shift(double along) { return new P(c.add(n.scale(along)), this); }
        Vec3 pt(double rad, double ang) {
            double cs = Math.cos(ang) * rad, sn = Math.sin(ang) * rad;
            return new Vec3(c.x + u.x * cs + w.x * sn, c.y + u.y * cs + w.y * sn, c.z + u.z * cs + w.z * sn);
        }
        Vec3 tangent(double ang) { return w.scale(Math.cos(ang)).subtract(u.scale(Math.sin(ang))); }
    }

    /** Annular sector (flat). */
    void band(P p, double r1, double r2, double a0, double a1, float al, float wk) {
        if (al <= 0.003F || r2 <= 0) return;
        r1 = Math.max(0, r1);
        int full = Mth.clamp((int) (r2 * 7), 28, 180);
        int n = Mth.clamp((int) Math.ceil(Math.abs(a1 - a0) / (Math.PI * 2) * full), 1, 180);
        for (int i = 0; i < n; i++) {
            double t0 = a0 + (a1 - a0) * i / n, t1 = a0 + (a1 - a0) * (i + 1) / n;
            quad(p.pt(r1, t0), p.pt(r2, t0), p.pt(r2, t1), p.pt(r1, t1), al, wk);
        }
    }

    /** Glowing rim: halo + body + bright core. */
    void ring(P p, double rad, double width, float al) {
        band(p, rad - width * 2.2, rad + width * 2.2, 0, Math.PI * 2, al * 0.14F, 0);
        band(p, rad - width * 0.5, rad + width * 0.5, 0, Math.PI * 2, al * 0.7F, 0.25F);
        band(p, rad - width * 0.15, rad + width * 0.15, 0, Math.PI * 2, al, 0.8F);
    }

    /** Flat strip between two points, its width along side. */
    void strip(Vec3 a, Vec3 b2, Vec3 side, double half, float al, float wk) {
        Vec3 s = side.scale(half);
        quad(a.add(s), b2.add(s), b2.subtract(s), a.subtract(s), al, wk);
    }

    /** Flat glowing segment lying in the plane. */
    void seg(P p, Vec3 a, Vec3 b2, double width, float al) {
        Vec3 d = b2.subtract(a);
        if (d.lengthSqr() < 1e-8) return;
        Vec3 side = p.n.cross(d).normalize();
        strip(a, b2, side, width * 2.2, al * 0.14F, 0);
        strip(a, b2, side, width * 0.5, al * 0.75F, 0.3F);
        strip(a, b2, side, width * 0.15, al, 0.85F);
    }

    void disk(P p, double rad, float al) {
        if (al <= 0.003F) return;
        int n = Mth.clamp((int) (rad * 5), 16, 96);
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            quad(p.c, p.c, p.pt(rad, a0), p.pt(rad, a1), al, 0);
        }
    }

    /** Star polygon {pts/step}. */
    void polygram(P p, double rad, int pts, int step, double phase, double width, float al) {
        for (int i = 0; i < pts; i++) {
            Vec3 a = p.pt(rad, phase + Math.PI * 2 * i / pts), b2 = p.pt(rad, phase + Math.PI * 2 * ((i + step) % pts) / pts);
            seg(p, a, b2, width, al);
        }
    }

    private static int hash(int x) {
        x ^= x >>> 16; x *= 0x7feb352d; x ^= x >>> 15; x *= 0x846ca68b; x ^= x >>> 16;
        return x;
    }

    /** Band of pixel runes: each slot is a 3×2 block glyph picked by hash, like a sigil script. */
    void glyphs(P p, double rIn, double rOut, int count, double phase, float al, int seed) {
        if (al <= 0.003F) return;
        double slot = Math.PI * 2 / count, dr = (rOut - rIn) / 2, gapR = dr * 0.12;
        for (int k = 0; k < count; k++) {
            int h = hash(seed * 7919 + k * 104729);
            double a = phase + slot * k, da = slot * 0.82 / 3, gapA = da * 0.14;
            if ((h & 0x300) == 0) {          // long bar glyph
                band(p, rIn + dr * 0.6, rIn + dr * 1.4, a, a + slot * 0.8, al, 0.55F);
                continue;
            }
            int bits = h & 63;
            if (Integer.bitCount(bits) < 2) bits |= 0b100001;
            for (int i = 0; i < 3; i++) for (int j = 0; j < 2; j++) {
                if ((bits & (1 << (i * 2 + j))) == 0) continue;
                band(p, rIn + j * dr + gapR, rIn + (j + 1) * dr - gapR, a + i * da + gapA, a + (i + 1) * da - gapA, al, 0.55F);
            }
        }
    }

    /** Ladder rays from R0 out to R1 (lifted along the normal), with rune blocks between the rails. */
    void rays(P p, double r0, double r1, int count, double phase, double lift, double width, float al, int seed) {
        for (int i = 0; i < count; i++) {
            double a = phase + Math.PI * 2 * i / count;
            boolean big = i % 2 == 0;
            double end = big ? r1 : r0 + (r1 - r0) * 0.62;
            Vec3 s = p.pt(r0, a), e = p.pt(end, a).add(p.n.scale(lift * (end - r0)));
            Vec3 side = p.tangent(a);
            double rail = width * (big ? 2.2 : 1.6);
            strip(s.add(side.scale(rail)), e.add(side.scale(rail * 0.4)), side, width * 0.35, al * 0.9F, 0.6F);
            strip(s.subtract(side.scale(rail)), e.subtract(side.scale(rail * 0.4)), side, width * 0.35, al * 0.9F, 0.6F);
            strip(s, e, side, rail * 1.6, al * 0.1F, 0);
            int blocks = Mth.clamp((int) ((end - r0) / (width * 3.2)), 3, 40);
            for (int k = 0; k < blocks; k++) {
                int h = hash(seed * 31 + i * 977 + k * 131);
                if ((h & 3) == 0) continue;
                double t0 = (k + 0.15) / blocks, t1 = (k + 0.85) / blocks;
                Vec3 q0 = s.lerp(e, t0), q1 = s.lerp(e, t1);
                double wd = rail * (1 - 0.6 * (t0 + t1) / 2) * ((h & 4) == 0 ? 0.55 : 0.9);
                strip(q0, q1, side, wd, al * 0.75F, 0.5F);
            }
            // tip
            ribbon(e.subtract(side.scale(width * 2)), e.add(side.scale(width * 2)), width * 1.5, al, 0.9F);
        }
    }

    private int layer(int style, int idx, int c1, int c2, double ang) {
        if ((style & PowerNet.RAINBOW) != 0) return PowerNet.STONE_COLORS[Math.floorMod(idx + (int) (ang * 1.5), 6)];
        return idx % 2 == 0 ? c1 : c2;
    }

    /**
     * Layered magic circle of radius R in plane p. ang = base rotation (radians, accumulated by the caller).
     * style: PowerNet.RAINBOW / RAYS / SIMPLE.
     */
    void magic(P p, double R, float al, double ang, int seed, int style, int c1, int c2) {
        if (R < 0.05 || al <= 0.01F) return;
        boolean simple = (style & PowerNet.SIMPLE) != 0;
        double thin = R * 0.006 + 0.025, thick = R * 0.018 + 0.05;
        // outer double rim
        color(layer(style, 0, c1, c2, ang));
        ring(p, R, thick, al);
        ring(p, R * 0.965, thin, al * 0.7F);
        // outer rune band
        color(layer(style, 1, c1, c2, ang));
        glyphs(p, R * 0.855, R * 0.945, Mth.clamp((int) (R * 2.6), 12, 96), ang * 0.6, al * 0.95F, seed);
        color(layer(style, 2, c1, c2, ang));
        ring(p, R * 0.84, thin, al * 0.85F);
        // octagram + squares + satellite circles
        double oa = -ang * 0.9;
        polygram(p, R * 0.82, 8, 3, oa, thin, al * 0.85F);
        polygram(p, R * 0.82, 8, 2, oa + Math.PI / 8, thin * 0.8, al * 0.45F);
        color(layer(style, 3, c1, c2, ang));
        for (int i = 0; i < 8; i++) {
            P sat = p.at(p.pt(R * 0.82, oa + Math.PI * 2 * i / 8));
            ring(sat, R * 0.07, thin * 0.8, al * 0.9F);
            if (!simple) glyphs(sat, R * 0.03, R * 0.055, 6, -ang * 2 + i, al * 0.7F, seed + i * 13);
            disk(sat, R * 0.025, al * 0.5F);
        }
        if (!simple) {
            // tick ring
            color(layer(style, 4, c1, c2, ang));
            ring(p, R * 0.6, thin, al * 0.8F);
            ring(p, R * 0.555, thin * 0.7, al * 0.6F);
            double ta = ang * 1.4;
            for (int i = 0; i < 48; i++) {
                double a = ta + Math.PI * 2 * i / 48;
                double w = i % 4 == 0 ? 0.045 : 0.02;
                band(p, R * 0.565, R * (i % 4 == 0 ? 0.6 : 0.585), a - w * 0.5, a + w * 0.5, al * 0.85F, 0.5F);
            }
            // hexagram
            color(layer(style, 5, c1, c2, ang));
            polygram(p, R * 0.55, 6, 2, -ang * 1.7, thin, al * 0.8F);
            // inner rune band
            color(layer(style, 6, c1, c2, ang));
            ring(p, R * 0.45, thin * 0.8, al * 0.7F);
            glyphs(p, R * 0.365, R * 0.44, Mth.clamp((int) (R * 1.3), 8, 48), -ang * 1.1, al * 0.9F, seed + 7);
            ring(p, R * 0.355, thin * 0.8, al * 0.7F);
            // spokes
            color(layer(style, 7, c1, c2, ang));
            double sa = ang * 0.3;
            for (int i = 0; i < 16; i++) {
                double a = sa + Math.PI * 2 * i / 16;
                seg(p, p.pt(R * 0.22, a), p.pt(R * (i % 2 == 0 ? 0.84 : 0.6), a), thin * 0.5, al * 0.35F);
            }
        }
        // core
        color(layer(style, 8, c1, c2, ang));
        ring(p, R * 0.2, thin * 1.2, al);
        polygram(p, R * 0.19, 4, 1, ang * 2.2, thin * 0.7, al * 0.8F);
        ring(p, R * 0.1, thin, al * 0.9F);
        disk(p, R * 0.2, al * 0.12F);
        color(c1);
        disk(p, R, al * 0.035F);
        if ((style & PowerNet.RAYS) != 0) {
            color(layer(style, 9, c1, c2, ang));
            rays(p, R * 1.01, R * 1.7, 16, -ang * 0.4, 0.35, thin * 1.2, al * 0.85F, seed);
        }
    }

    /** Colossal vertical-ish beam: stacked halos, white core, descending rings, spiral strands. */
    void colossal(Vec3 top, Vec3 bot, double w, float al, float time, int c1, int c2) {
        if (w <= 0.02 || al <= 0.01F) return;
        color(c1);
        ribbon(top, bot, w * 2.0, al * 0.1F, 0);
        ribbon(top, bot, w * 1.3, al * 0.22F, 0.1F);
        ribbon(top, bot, w * 0.85, al * 0.45F, 0.35F);
        color(c2);
        ribbon(top, bot, w * 0.5, al * 0.85F, 0.75F);
        ribbon(top, bot, w * 0.2, al, 1F);
        Vec3 axis = bot.subtract(top);
        P base = new P(top, axis.scale(-1));
        color(c1);
        for (int i = 0; i < 9; i++) {
            double f = (time * 0.11 + i / 9.0) % 1.0;
            ring(base.at(top.add(axis.scale(f))), w * (1.1 + 0.25 * Math.sin(time * 3 + i)), w * 0.05, al * 0.8F * (float) (1 - f * 0.3));
        }
        for (int s = 0; s < 4; s++) {
            Vec3 prev = null;
            for (int j = 0; j <= 40; j++) {
                double f = j / 40.0, a = s * Math.PI / 2 + f * 14 - time * 6;
                Vec3 q = base.at(top.add(axis.scale(f))).pt(w * 0.95, a);
                if (prev != null) ribbon(prev, q, w * 0.06, al * 0.6F, 0.5F);
                prev = q;
            }
        }
    }

    /** Glowing straight beam (thin). */
    void beam(Vec3 a, Vec3 b2, double width, float al, float time) {
        float flick = 0.85F + 0.15F * Mth.sin(time * 41F);
        ribbon(a, b2, width * 2.4, al * 0.14F * flick, 0);
        ribbon(a, b2, width, al * 0.45F * flick, 0.2F);
        ribbon(a, b2, width * 0.32, al * 0.95F, 0.85F);
    }

    /** Braided beam of several colours twisted around a white core. reveal = 0..1 of the length drawn. */
    void braid(Vec3 a, Vec3 b2, double w, int mask, float al, float time, float reveal) {
        Vec3 d = b2.subtract(a);
        double len = d.length();
        if (len < 0.2) return;
        Vec3 end = a.add(d.scale(reveal));
        color(0xFFFFFF);
        ribbon(a, end, w * 0.35, al, 1F);
        ribbon(a, end, w * 1.4, al * 0.12F, 0);
        P base = new P(a, d);
        int n = Integer.bitCount(mask & 63);
        if (n == 0) return;
        int segs = Mth.clamp((int) (len * 2 * reveal), 4, 220);
        int k = 0;
        for (int s = 0; s < 6; s++) {
            if ((mask & (1 << s)) == 0) continue;
            color(PowerNet.STONE_COLORS[s]);
            double ph = Math.PI * 2 * k / n;
            Vec3 prev = null;
            for (int j = 0; j <= segs; j++) {
                double f = reveal * j / segs, ang = ph + f * len * 0.9 - time * 9;
                Vec3 q = base.at(a.add(d.scale(f))).pt(w * 0.75, ang);
                if (prev != null) { ribbon(prev, q, w * 0.2, al * 0.9F, 0.35F); ribbon(prev, q, w * 0.55, al * 0.15F, 0); }
                prev = q;
            }
            k++;
        }
        // rings marching along
        for (int i = 0; i < 8; i++) {
            double f = ((time * 0.6 + i / 8.0) % 1.0) * reveal;
            color(PowerNet.STONE_COLORS[i % 6]);
            ring(base.at(a.add(d.scale(f))), w * 1.25, w * 0.05, al * 0.7F);
        }
    }
}
