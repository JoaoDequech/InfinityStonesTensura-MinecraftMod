package com.infinitystones.tensura.skill.ability.extra.reality.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Additive glow primitives for the hologram chest (RenderType.lightning, camera-relative world coordinates). */
final class HoloGlow {
    private final VertexConsumer vc;
    private final Matrix4f m;
    private final Vec3 cam;
    private float r, g, b;

    HoloGlow(VertexConsumer vc, Matrix4f m, Vec3 cam) { this.vc = vc; this.m = m; this.cam = cam; }

    HoloGlow color(int c) { r = ((c >> 16) & 255) / 255F; g = ((c >> 8) & 255) / 255F; b = (c & 255) / 255F; return this; }

    private void v(Vec3 p, float al, boolean white) {
        float k = white ? 0.6F : 0;
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r + (1 - r) * k, g + (1 - g) * k, b + (1 - b) * k, Mth.clamp(al, 0, 1));
    }

    /** Double-sided quad with its own alpha at the a-b edge and at the c-d edge. */
    void quad(Vec3 a, Vec3 b2, Vec3 c, Vec3 d, float alAB, float alCD) {
        v(a, alAB, false); v(b2, alAB, false); v(c, alCD, false); v(d, alCD, false);
        v(d, alCD, false); v(c, alCD, false); v(b2, alAB, false); v(a, alAB, false);
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

    /** Circle lying flat; dashed = alternate gaps. */
    void circle(Vec3 c, double radius, int segs, double phase, float width, float al, boolean dashed) {
        for (int i = 0; i < segs; i++) {
            if (dashed && i % 4 == 3) continue;
            double a0 = phase + Math.PI * 2 * i / segs, a1 = phase + Math.PI * 2 * (i + 1) / segs;
            line(c.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius), c.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius), width, al);
        }
    }

    /** Regular polygon star {n/step} lying flat. */
    void star(Vec3 c, double radius, int n, int step, double phase, float width, float al) {
        for (int i = 0; i < n; i++) {
            double a0 = phase + Math.PI * 2 * i / n, a1 = phase + Math.PI * 2 * ((i + step) % n) / n;
            line(c.add(Math.cos(a0) * radius, 0, Math.sin(a0) * radius), c.add(Math.cos(a1) * radius, 0, Math.sin(a1) * radius), width, al);
        }
    }

    /** Short radial ticks between two radii (rune marks). */
    void ticks(Vec3 c, double r0, double r1, int n, double phase, float width, float al) {
        for (int i = 0; i < n; i++) {
            double a = phase + Math.PI * 2 * i / n;
            double rr1 = (i % 3 == 0) ? r1 : (r0 + r1) / 2;
            line(c.add(Math.cos(a) * r0, 0, Math.sin(a) * r0), c.add(Math.cos(a) * rr1, 0, Math.sin(a) * rr1), width, al * 0.8F);
        }
    }
}
