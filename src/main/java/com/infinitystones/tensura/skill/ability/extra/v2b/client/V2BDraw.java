package com.infinitystones.tensura.skill.ability.extra.v2b.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Additive glow primitives for the v2b effects (position + colour quads in world coordinates). */
final class V2BDraw {
    private final VertexConsumer vc;
    private final Matrix4f m;
    private final Vec3 cam;
    private float r, g, b, scale = 1F;

    V2BDraw(VertexConsumer vc, Matrix4f m, Vec3 cam) { this.vc = vc; this.m = m; this.cam = cam; }

    void color(int c) { r = ((c >> 16) & 255) / 255F; g = ((c >> 8) & 255) / 255F; b = (c & 255) / 255F; }

    /** Global alpha multiplier (the see-through pass is dimmer). */
    void scale(float s) { scale = s; }

    private void v(Vec3 p, float al, boolean white) {
        float k = white ? 0.55F : 0;
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r + (1 - r) * k, g + (1 - g) * k, b + (1 - b) * k, Mth.clamp(al * scale, 0, 1));
    }

    void quad(Vec3 a, Vec3 b2, Vec3 c, Vec3 d, float al, boolean white) {
        v(a, al, white); v(b2, al, white); v(c, al, white); v(d, al, white);
        v(d, al, white); v(c, al, white); v(b2, al, white); v(a, al, white);
    }

    void ribbon(Vec3 p0, Vec3 p1, float half, float al, boolean white) {
        Vec3 seg = p1.subtract(p0);
        Vec3 toCam = cam.subtract(p0.add(seg.scale(0.5)));
        Vec3 n = seg.cross(toCam);
        if (n.lengthSqr() < 1e-10) return;
        n = n.normalize().scale(half);
        v(p0.add(n), al, white); v(p1.add(n), al, white); v(p1.subtract(n), al, white); v(p0.subtract(n), al, white);
    }

    /** Glowing line: soft halo, body and a bright core. */
    void line(Vec3 a, Vec3 b2, float width, float al) {
        ribbon(a, b2, width * 2.4F, al * 0.16F, false);
        ribbon(a, b2, width, al * 0.5F, false);
        ribbon(a, b2, width * 0.35F, al * 0.95F, true);
    }

    /** Four-pointed star glint. */
    void spark(Vec3 p, float s, float al) {
        Vec3 up = new Vec3(0, s, 0);
        ribbon(p.subtract(up), p.add(up), s * 0.28F, al, true);
        Vec3 toCam = cam.subtract(p).normalize();
        Vec3 side = toCam.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() > 1e-6) { side = side.normalize().scale(s); ribbon(p.subtract(side), p.add(side), s * 0.28F, al, true); }
    }

    void ring(Vec3 c, double radius, float width, float al, double phase) {
        int n = Mth.clamp((int) (radius * 10), 24, 120);
        Vec3 prev = null;
        for (int i = 0; i <= n; i++) {
            double a = phase + Math.PI * 2 * i / n;
            Vec3 p = c.add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            if (prev != null) line(prev, p, width, al);
            prev = p;
        }
    }

    void box(double x0, double y0, double z0, double x1, double y1, double z1, float width, float al) {
        Vec3[] c = {new Vec3(x0, y0, z0), new Vec3(x1, y0, z0), new Vec3(x1, y0, z1), new Vec3(x0, y0, z1),
                new Vec3(x0, y1, z0), new Vec3(x1, y1, z0), new Vec3(x1, y1, z1), new Vec3(x0, y1, z1)};
        for (int i = 0; i < 4; i++) {
            line(c[i], c[(i + 1) % 4], width, al);
            line(c[4 + i], c[4 + (i + 1) % 4], width, al);
            line(c[i], c[4 + i], width, al);
        }
    }

    /** Eight-sided diamond (octahedron) outline-and-faces marker. */
    void diamond(Vec3 c, float h, float w, double spin, float al) {
        Vec3 top = c.add(0, h, 0), bot = c.subtract(0, h, 0);
        Vec3[] e = new Vec3[4];
        for (int i = 0; i < 4; i++) {
            double a = spin + i * Math.PI / 2;
            e[i] = c.add(Math.cos(a) * w, 0, Math.sin(a) * w);
        }
        for (int i = 0; i < 4; i++) {
            Vec3 p = e[i], q = e[(i + 1) % 4];
            quad(top, p, q, top, al * 0.35F, false);
            quad(bot, p, q, bot, al * 0.35F, false);
            line(top, p, 0.025F, al);
            line(bot, p, 0.025F, al);
            line(p, q, 0.025F, al);
        }
    }
}
