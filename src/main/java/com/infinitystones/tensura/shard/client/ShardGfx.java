package com.infinitystones.tensura.shard.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/** Smooth 2D primitives for the mini-gem menu: discs, rings, radial glows, gradient lines, glints. */
final class ShardGfx {
    private static boolean additive;

    static void additive(boolean on) { additive = on; }

    static int alpha(int c, float a) { return (Mth.clamp((int) (a * 255), 0, 255) << 24) | (c & 0xFFFFFF); }

    static int withA(int c, int a) { return (Mth.clamp(a, 0, 255) << 24) | (c & 0xFFFFFF); }

    static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255), g = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255), bl = (int) Mth.lerp(t, a & 255, b & 255);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    static int lighten(int c, float f) { return mix(c, 0xFFFFFF, f); }

    static int darken(int c, float f) { return mix(c, 0x000000, f); }

    static float easeOutBack(float t) {
        t = Mth.clamp(t, 0, 1);
        float c1 = 1.70158F, c3 = c1 + 1;
        return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    static float easeOutCubic(float t) { t = Mth.clamp(t, 0, 1); return 1 - (1 - t) * (1 - t) * (1 - t); }

    static float easeInOut(float t) { t = Mth.clamp(t, 0, 1); return t * t * (3 - 2 * t); }

    private static void draw(BufferBuilder bb) {
        RenderSystem.enableBlend();
        if (additive) RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        else RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(bb.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }

    /** Annulus sector, angles in radians (0 = up, clockwise). */
    static void ring(GuiGraphics g, float cx, float cy, float rIn, float rOut, float a0, float a1, int argb) {
        if ((argb >>> 24) == 0 || a1 <= a0 || rOut <= rIn) return;
        Matrix4f m = g.pose().last().pose();
        int steps = Math.max(3, (int) Math.ceil((a1 - a0) / 0.07F));
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < steps; i++) {
            float t0 = a0 + (a1 - a0) * i / steps - Mth.HALF_PI, t1 = a0 + (a1 - a0) * (i + 1) / steps - Mth.HALF_PI;
            float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
            bb.addVertex(m, cx + c0 * rIn, cy + s0 * rIn, 0).setColor(argb);
            bb.addVertex(m, cx + c0 * rOut, cy + s0 * rOut, 0).setColor(argb);
            bb.addVertex(m, cx + c1 * rOut, cy + s1 * rOut, 0).setColor(argb);
            bb.addVertex(m, cx + c0 * rIn, cy + s0 * rIn, 0).setColor(argb);
            bb.addVertex(m, cx + c1 * rOut, cy + s1 * rOut, 0).setColor(argb);
            bb.addVertex(m, cx + c1 * rIn, cy + s1 * rIn, 0).setColor(argb);
        }
        draw(bb);
    }

    static void circle(GuiGraphics g, float cx, float cy, float r, float thick, int argb) {
        ring(g, cx, cy, r - thick * 0.5F, r + thick * 0.5F, 0, Mth.TWO_PI, argb);
    }

    static void disc(GuiGraphics g, float cx, float cy, float r, int argb) { ring(g, cx, cy, 0, r, 0, Mth.TWO_PI, argb); }

    /** Radial glow: opaque-ish centre fading to nothing at the edge. */
    static void glow(GuiGraphics g, float cx, float cy, float r, int rgb, float a) {
        a *= ShardShaders.gui();
        if (a <= 0.003F || r <= 0) return;
        Matrix4f m = g.pose().last().pose();
        int steps = 28;
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int in = alpha(rgb, a), out = alpha(rgb, 0);
        for (int i = 0; i < steps; i++) {
            float t0 = Mth.TWO_PI * i / steps, t1 = Mth.TWO_PI * (i + 1) / steps;
            bb.addVertex(m, cx, cy, 0).setColor(in);
            bb.addVertex(m, cx + Mth.cos(t0) * r, cy + Mth.sin(t0) * r, 0).setColor(out);
            bb.addVertex(m, cx + Mth.cos(t1) * r, cy + Mth.sin(t1) * r, 0).setColor(out);
        }
        draw(bb);
    }

    /** Line with a colour gradient along its length. */
    static void line(GuiGraphics g, float x0, float y0, float x1, float y1, float thick, int c0, int c1) {
        float dx = x1 - x0, dy = y1 - y0, len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 0.01F) return;
        float nx = -dy / len * thick * 0.5F, ny = dx / len * thick * 0.5F;
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        bb.addVertex(m, x0 + nx, y0 + ny, 0).setColor(c0);
        bb.addVertex(m, x0 - nx, y0 - ny, 0).setColor(c0);
        bb.addVertex(m, x1 - nx, y1 - ny, 0).setColor(c1);
        bb.addVertex(m, x0 + nx, y0 + ny, 0).setColor(c0);
        bb.addVertex(m, x1 - nx, y1 - ny, 0).setColor(c1);
        bb.addVertex(m, x1 + nx, y1 + ny, 0).setColor(c1);
        draw(bb);
    }

    /** Four-point glint (a star with long horizontal and vertical rays). */
    static void glint(GuiGraphics g, float cx, float cy, float len, float rot, int rgb, float a) {
        a *= ShardShaders.gui();
        if (a <= 0.01F) return;
        int core = alpha(rgb, a), tip = alpha(rgb, 0);
        for (int k = 0; k < 2; k++) {
            float ang = rot + k * Mth.HALF_PI;
            float ux = Mth.cos(ang) * len, uy = Mth.sin(ang) * len, vx = -Mth.sin(ang) * len * 0.08F, vy = Mth.cos(ang) * len * 0.08F;
            Matrix4f m = g.pose().last().pose();
            BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
            bb.addVertex(m, cx - ux, cy - uy, 0).setColor(tip);
            bb.addVertex(m, cx + vx, cy + vy, 0).setColor(core);
            bb.addVertex(m, cx - vx, cy - vy, 0).setColor(core);
            bb.addVertex(m, cx + ux, cy + uy, 0).setColor(tip);
            bb.addVertex(m, cx - vx, cy - vy, 0).setColor(core);
            bb.addVertex(m, cx + vx, cy + vy, 0).setColor(core);
            draw(bb);
        }
    }

    /** Vertical gradient rectangle. */
    static void gradient(GuiGraphics g, float x, float y, float w, float h, int top, int bottom) {
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        bb.addVertex(m, x, y, 0).setColor(top);
        bb.addVertex(m, x, y + h, 0).setColor(bottom);
        bb.addVertex(m, x + w, y + h, 0).setColor(bottom);
        bb.addVertex(m, x, y, 0).setColor(top);
        bb.addVertex(m, x + w, y + h, 0).setColor(bottom);
        bb.addVertex(m, x + w, y, 0).setColor(top);
        draw(bb);
    }

    /** Horizontal gradient rectangle. */
    static void gradientH(GuiGraphics g, float x, float y, float w, float h, int left, int right) {
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        bb.addVertex(m, x, y, 0).setColor(left);
        bb.addVertex(m, x, y + h, 0).setColor(left);
        bb.addVertex(m, x + w, y + h, 0).setColor(right);
        bb.addVertex(m, x, y, 0).setColor(left);
        bb.addVertex(m, x + w, y + h, 0).setColor(right);
        bb.addVertex(m, x + w, y, 0).setColor(right);
        draw(bb);
    }

    private ShardGfx() {}
}
