package com.infinitystones.tensura.skill.ability.extra.fear.client;

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

/** Small immediate-mode drawing kit (gradients, discs, rings, lines) for the fear screens and overlays. */
final class FearGfx {
    static int argb(float a, int rgb) { return (Mth.clamp((int) (a * 255), 0, 255) << 24) | (rgb & 0xFFFFFF); }

    static void begin(boolean additive) {
        RenderSystem.enableBlend();
        if (additive) RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE); else RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    static void end() { RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend(); }

    /** Filled ellipse with a colour at the centre and another at the rim. */
    static void disc(GuiGraphics g, float cx, float cy, float rx, float ry, int inner, int outer, int segs, boolean additive) {
        Matrix4f m = g.pose().last().pose();
        begin(additive);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        bb.addVertex(m, cx, cy, 0F).setColor(inner);
        for (int i = 0; i <= segs; i++) {
            double a = Math.PI * 2 * i / segs;
            bb.addVertex(m, cx + (float) Math.cos(a) * rx, cy + (float) Math.sin(a) * ry, 0F).setColor(outer);
        }
        BufferUploader.drawWithShader(bb.buildOrThrow());
        end();
    }

    /** Concentric elliptical rings: radii[i] / colours[i]; the last ring is drawn out to {@code far} with the last colour. */
    static void rings(GuiGraphics g, float cx, float cy, float[] radii, int[] colors, float xs, int segs) {
        Matrix4f m = g.pose().last().pose();
        begin(false);
        for (int k = 0; k < radii.length - 1; k++) {
            BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
            for (int i = 0; i <= segs; i++) {
                double a = Math.PI * 2 * i / segs;
                float c = (float) Math.cos(a), s = (float) Math.sin(a);
                bb.addVertex(m, cx + c * radii[k] * xs, cy + s * radii[k], 0F).setColor(colors[k]);
                bb.addVertex(m, cx + c * radii[k + 1] * xs, cy + s * radii[k + 1], 0F).setColor(colors[k + 1]);
            }
            BufferUploader.drawWithShader(bb.buildOrThrow());
        }
        end();
    }

    /** Thick line as a quad. */
    static void line(GuiGraphics g, float x0, float y0, float x1, float y1, float w, int color) {
        float dx = x1 - x0, dy = y1 - y0, len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 0.01F) return;
        float nx = -dy / len * w * 0.5F, ny = dx / len * w * 0.5F;
        Matrix4f m = g.pose().last().pose();
        begin(false);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bb.addVertex(m, x0 + nx, y0 + ny, 0F).setColor(color);
        bb.addVertex(m, x0 - nx, y0 - ny, 0F).setColor(color);
        bb.addVertex(m, x1 - nx, y1 - ny, 0F).setColor(color);
        bb.addVertex(m, x1 + nx, y1 + ny, 0F).setColor(color);
        BufferUploader.drawWithShader(bb.buildOrThrow());
        end();
    }

    /** Quad with a vertical colour gradient. */
    static void vgrad(GuiGraphics g, float x0, float y0, float x1, float y1, int top, int bottom) {
        Matrix4f m = g.pose().last().pose();
        begin(false);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        bb.addVertex(m, x0, y0, 0F).setColor(top);
        bb.addVertex(m, x0, y1, 0F).setColor(bottom);
        bb.addVertex(m, x1, y1, 0F).setColor(bottom);
        bb.addVertex(m, x1, y0, 0F).setColor(top);
        BufferUploader.drawWithShader(bb.buildOrThrow());
        end();
    }

    static float easeOutBack(float t) { float c1 = 1.70158F, c3 = c1 + 1; float u = t - 1; return 1 + c3 * u * u * u + c1 * u * u; }

    static float easeOutCubic(float t) { float u = 1 - t; return 1 - u * u * u; }

    static float easeInOut(float t) { return t < 0.5F ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2; }

    static float hash(int a, int b) {
        int h = a * 374761393 + b * 668265263; h = (h ^ (h >>> 13)) * 1274126177; h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private FearGfx() {}
}
