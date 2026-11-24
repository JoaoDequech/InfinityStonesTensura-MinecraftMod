package com.infinitystones.tensura.skill.ability.extra.v2a.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Textured cuboids for the giant models: texture repeats every {@code tile} model units, so pixels stay square. */
final class Geo {
    static final int FULL = 0xF000F0;

    static void box(PoseStack ps, VertexConsumer vc, float x0, float y0, float z0, float x1, float y1, float z1,
                    int light, float r, float g, float b, float tile) {
        PoseStack.Pose p = ps.last();
        float t = 1F / tile;
        // +X
        quad(p, vc, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, z0 * t, y0 * t, z1 * t, y1 * t, 1, 0, 0, light, r * 0.86F, g * 0.86F, b * 0.86F);
        // -X
        quad(p, vc, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, z0 * t, y0 * t, z1 * t, y1 * t, -1, 0, 0, light, r * 0.86F, g * 0.86F, b * 0.86F);
        // +Y
        quad(p, vc, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, x0 * t, z0 * t, x1 * t, z1 * t, 0, 1, 0, light, r, g, b);
        // -Y
        quad(p, vc, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, x0 * t, z0 * t, x1 * t, z1 * t, 0, -1, 0, light, r * 0.6F, g * 0.6F, b * 0.6F);
        // +Z
        quad(p, vc, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0 * t, y0 * t, x1 * t, y1 * t, 0, 0, 1, light, r * 0.93F, g * 0.93F, b * 0.93F);
        // -Z
        quad(p, vc, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, x0 * t, y0 * t, x1 * t, y1 * t, 0, 0, -1, light, r * 0.93F, g * 0.93F, b * 0.93F);
    }

    /** Cube of half-size h around the origin with the same texture patch (u0..u1, v0..v1) on every face. */
    static void uvCube(PoseStack ps, VertexConsumer vc, float h, float u0, float v0, float u1, float v1,
                       int light, float r, float g, float b) {
        PoseStack.Pose p = ps.last();
        float n = -h;
        quad(p, vc, h, n, n, h, n, h, h, h, h, h, h, n, u0, v0, u1, v1, 1, 0, 0, light, r * 0.86F, g * 0.86F, b * 0.86F);
        quad(p, vc, n, n, h, n, n, n, n, h, n, n, h, h, u0, v0, u1, v1, -1, 0, 0, light, r * 0.86F, g * 0.86F, b * 0.86F);
        quad(p, vc, n, h, h, h, h, h, h, h, n, n, h, n, u0, v0, u1, v1, 0, 1, 0, light, r, g, b);
        quad(p, vc, n, n, n, h, n, n, h, n, h, n, n, h, u0, v0, u1, v1, 0, -1, 0, light, r * 0.6F, g * 0.6F, b * 0.6F);
        quad(p, vc, n, n, h, h, n, h, h, h, h, n, h, h, u0, v0, u1, v1, 0, 0, 1, light, r * 0.93F, g * 0.93F, b * 0.93F);
        quad(p, vc, h, n, n, n, n, n, n, h, n, h, h, n, u0, v0, u1, v1, 0, 0, -1, light, r * 0.93F, g * 0.93F, b * 0.93F);
    }

    /** Box of half-sizes hx, hy, hz around the origin with the same texture patch on every face. */
    static void uvBox(PoseStack ps, VertexConsumer vc, float hx, float hy, float hz, float u0, float v0, float u1, float v1,
            int light, float r, float g, float b) {
        PoseStack.Pose p = ps.last();
        float nx = -hx, ny = -hy, nz = -hz;
        quad(p, vc, hx, ny, nz, hx, ny, hz, hx, hy, hz, hx, hy, nz, u0, v0, u1, v1, 1, 0, 0, light, r * 0.86F, g * 0.86F, b * 0.86F);
        quad(p, vc, nx, ny, hz, nx, ny, nz, nx, hy, nz, nx, hy, hz, u0, v0, u1, v1, -1, 0, 0, light, r * 0.86F, g * 0.86F, b * 0.86F);
        quad(p, vc, nx, hy, hz, hx, hy, hz, hx, hy, nz, nx, hy, nz, u0, v0, u1, v1, 0, 1, 0, light, r, g, b);
        quad(p, vc, nx, ny, nz, hx, ny, nz, hx, ny, hz, nx, ny, hz, u0, v0, u1, v1, 0, -1, 0, light, r * 0.6F, g * 0.6F, b * 0.6F);
        quad(p, vc, nx, ny, hz, hx, ny, hz, hx, hy, hz, nx, hy, hz, u0, v0, u1, v1, 0, 0, 1, light, r * 0.93F, g * 0.93F, b * 0.93F);
        quad(p, vc, hx, ny, nz, nx, ny, nz, nx, hy, nz, hx, hy, nz, u0, v0, u1, v1, 0, 0, -1, light, r * 0.93F, g * 0.93F, b * 0.93F);
    }
    /** Corners a, b, c, d (counter-clockwise seen from outside); u0..u1 / v0..v1 = texture span. */
    private static void quad(PoseStack.Pose p, VertexConsumer vc,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float v0, float u1, float v1, float nx, float ny, float nz,
                             int light, float r, float g, float b) {
        vert(p, vc, ax, ay, az, u0, v1, nx, ny, nz, light, r, g, b, 1F);
        vert(p, vc, bx, by, bz, u1, v1, nx, ny, nz, light, r, g, b, 1F);
        vert(p, vc, cx, cy, cz, u1, v0, nx, ny, nz, light, r, g, b, 1F);
        vert(p, vc, dx, dy, dz, u0, v0, nx, ny, nz, light, r, g, b, 1F);
    }

    private static void vert(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float u, float v,
                             float nx, float ny, float nz, int light, float r, float g, float b, float a) {
        vc.addVertex(p, x, y, z).setColor(Math.min(1F, r), Math.min(1F, g), Math.min(1F, b), a).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p, nx, ny, nz);
    }

    /** Translucent disc seen from below (the dark patch of sky). World coordinates relative to the current pose. */
    static void discDown(PoseStack ps, VertexConsumer vc, double cx, double cy, double cz, float radius, float alpha) {
        PoseStack.Pose p = ps.last();
        int n = 40;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            vert(p, vc, (float) cx, (float) cy, (float) cz, 0, 0, 0, -1, 0, FULL, 0, 0, 0, alpha);
            vert(p, vc, (float) cx, (float) cy, (float) cz, 0, 0, 0, -1, 0, FULL, 0, 0, 0, alpha);
            vert(p, vc, (float) (cx + Math.cos(a1) * radius), (float) cy, (float) (cz + Math.sin(a1) * radius), 0, 0, 0, -1, 0, FULL, 0, 0, 0, alpha);
            vert(p, vc, (float) (cx + Math.cos(a0) * radius), (float) cy, (float) (cz + Math.sin(a0) * radius), 0, 0, 0, -1, 0, FULL, 0, 0, 0, alpha);
        }
    }

    private Geo() {}
}
