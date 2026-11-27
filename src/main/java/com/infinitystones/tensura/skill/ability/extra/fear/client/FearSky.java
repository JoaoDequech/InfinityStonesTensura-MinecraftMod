package com.infinitystones.tensura.skill.ability.extra.fear.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** The sky of the fear realm: a blood moon (or eclipse / red storm / void), drifting black clouds and rare silhouettes on the horizon. */
public final class FearSky extends DimensionSpecialEffects {
    public FearSky() { super(Float.NaN, false, SkyType.NONE, true, false); }

    @Override public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) { return color; }

    @Override public boolean isFoggyAt(int x, int y) { return false; }

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX, double camY, double camZ, Matrix4f modelViewMatrix, Matrix4f projectionMatrix) { return true; }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float pt, Matrix4f mv, Camera camera, Matrix4f projection, boolean isFoggy, Runnable setupFog) {
        setupFog.run();
        float t = ticks + pt;
        int mode = FearState.sky;
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        dome(mv, mode, t);
        if (mode == 0) { moon(mv, t, 0.82F, 0.08F, 0.06F, 1F); clouds(mv, t, 7); }
        else if (mode == 2) { eclipse(mv, t); clouds(mv, t, 4); }
        else if (mode == 3) { stormBands(mv, t); clouds(mv, t, 10); }
        else clouds(mv, t, 3);
        silhouette(mv, t);
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        return true;
    }

    private static void dome(Matrix4f mv, int mode, float t) {
        float hr = mode == 3 ? 0.30F : mode == 1 ? 0.02F : mode == 2 ? 0.12F : 0.2F;
        float hg = mode == 3 ? 0.02F : 0.015F, hb = mode == 2 ? 0.1F : 0.025F;
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        int rings = 7, segs = 24;
        for (int r = 0; r < rings; r++) {
            float e0 = (float) Math.toRadians(-8 + r * 98F / rings), e1 = (float) Math.toRadians(-8 + (r + 1) * 98F / rings);
            float k0 = Mth.clamp((float) Math.pow(1F - (e0 + 0.14F) / 1.7F, 2.2), 0, 1), k1 = Mth.clamp((float) Math.pow(1F - (e1 + 0.14F) / 1.7F, 2.2), 0, 1);
            for (int s = 0; s <= segs; s++) {
                float a = (float) (Math.PI * 2 * s / segs);
                float pulse = 1F + 0.08F * Mth.sin(t * 0.01F + s);
                bb.addVertex(mv, Mth.cos(a) * Mth.cos(e0) * 120, Mth.sin(e0) * 120, Mth.sin(a) * Mth.cos(e0) * 120).setColor(hr * k0 * pulse, hg * k0, hb * k0, 1F);
                bb.addVertex(mv, Mth.cos(a) * Mth.cos(e1) * 120, Mth.sin(e1) * 120, Mth.sin(a) * Mth.cos(e1) * 120).setColor(hr * k1 * pulse, hg * k1, hb * k1, 1F);
            }
        }
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }

    /** Disc facing the origin in direction (az, el). */
    private static void disc(Matrix4f mv, float az, float el, float dist, float radius, float[] inner, float[] outer, boolean additive) {
        Vec3 n = new Vec3(Mth.cos(az) * Mth.cos(el), Mth.sin(el), Mth.sin(az) * Mth.cos(el));
        Vec3 right = n.cross(new Vec3(0, 1, 0)).normalize(), up = right.cross(n).normalize();
        if (additive) RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE); else RenderSystem.defaultBlendFunc();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        Vec3 c = n.scale(dist);
        bb.addVertex(mv, (float) c.x, (float) c.y, (float) c.z).setColor(inner[0], inner[1], inner[2], inner[3]);
        for (int i = 0; i <= 28; i++) {
            double a = Math.PI * 2 * i / 28;
            Vec3 p = c.add(right.scale(Math.cos(a) * radius)).add(up.scale(Math.sin(a) * radius));
            bb.addVertex(mv, (float) p.x, (float) p.y, (float) p.z).setColor(outer[0], outer[1], outer[2], outer[3]);
        }
        BufferUploader.drawWithShader(bb.buildOrThrow());
        RenderSystem.defaultBlendFunc();
    }

    private static void moon(Matrix4f mv, float t, float r, float g, float b, float a) {
        float az = 0.9F + t * 0.00006F, el = 0.5F;
        float glow = FearCompat.glow();
        disc(mv, az, el, 110, 70, new float[]{r, g * 0.4F, b * 0.4F, 0.22F * glow}, new float[]{r, 0F, 0F, 0F}, true);
        disc(mv, az, el, 108, 34, new float[]{r, g, b, 0.3F * glow}, new float[]{r * 0.8F, 0F, 0F, 0F}, true);
        disc(mv, az, el, 105, 13, new float[]{r, g * 1.2F, b * 1.2F, a}, new float[]{r * 0.6F, g * 0.3F, b * 0.3F, a}, false);
        for (int i = 0; i < 5; i++) {
            float dx = (FearGfx.hash(i, 3) - 0.5F) * 0.12F, dy = (FearGfx.hash(i, 4) - 0.5F) * 0.12F;
            disc(mv, az + dx, el + dy, 104, 2 + 2 * FearGfx.hash(i, 5), new float[]{0.25F, 0F, 0F, 0.35F}, new float[]{0.2F, 0F, 0F, 0.2F}, false);
        }
    }

    private static void eclipse(Matrix4f mv, float t) {
        float az = 0.9F + t * 0.00004F, el = 0.62F, glow = FearCompat.glow();
        disc(mv, az, el, 110, 52, new float[]{0.9F, 0.2F, 0.35F, 0.28F * glow}, new float[]{0.5F, 0F, 0.1F, 0F}, true);
        disc(mv, az, el, 108, 20, new float[]{1F, 0.9F, 0.8F, 0.55F * glow}, new float[]{0.7F, 0.1F, 0.2F, 0F}, true);
        disc(mv, az, el, 105, 14, new float[]{0F, 0F, 0F, 1F}, new float[]{0F, 0F, 0F, 1F}, false);
    }

    private static void stormBands(Matrix4f mv, float t) {
        for (int i = 0; i < 6; i++) {
            float az = i * 1.05F + t * 0.0009F * (1 + i % 3), el = 0.25F + 0.14F * i;
            disc(mv, az, el, 100, 55 + 10 * i, new float[]{0.45F, 0.02F, 0.02F, 0.28F * FearCompat.glow()}, new float[]{0.2F, 0F, 0F, 0F}, true);
        }
    }

    private static void clouds(Matrix4f mv, float t, int n) {
        for (int i = 0; i < n; i++) {
            float speed = 0.00035F + 0.0002F * FearGfx.hash(i, 1);
            float az = FearGfx.hash(i, 2) * 6.283F + t * speed;
            float el = 0.2F + FearGfx.hash(i, 3) * 0.9F;
            float rad = 26 + 30 * FearGfx.hash(i, 4);
            for (int k = 0; k < 4; k++)
                disc(mv, az + (k - 1.5F) * 0.07F, el + (FearGfx.hash(i, 10 + k) - 0.5F) * 0.07F, 98, rad * (0.55F + 0.3F * FearGfx.hash(i, 20 + k)),
                        new float[]{0.01F, 0.0F, 0.0F, 0.62F}, new float[]{0.02F, 0F, 0F, 0F}, false);
        }
    }

    private static void silhouette(Matrix4f mv, float t) {
        float cycle = t % 5400F;
        if (cycle > 700F) return;
        float k = Math.min(Math.min(cycle / 120F, 1F), (700F - cycle) / 160F);
        float az = FearGfx.hash((int) (t / 5400F), 7) * 6.283F;
        Vec3 n = new Vec3(Mth.cos(az), 0.02, Mth.sin(az)).normalize();
        Vec3 right = n.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 base = n.scale(100).add(0, -4, 0);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float w = 1.1F, h = 15F;
        Vec3[] p = {base.add(right.scale(-w)), base.add(right.scale(w)), base.add(right.scale(w * 0.6)).add(0, h, 0), base.add(right.scale(-w * 0.6)).add(0, h, 0)};
        for (Vec3 v : p) bb.addVertex(mv, (float) v.x, (float) v.y, (float) v.z).setColor(0F, 0F, 0F, 0.9F * k);
        Vec3 head = base.add(0, h + 1.8F, 0);
        for (Vec3 v : new Vec3[]{head.add(right.scale(-1.5)).add(0, -1.8, 0), head.add(right.scale(1.5)).add(0, -1.8, 0), head.add(right.scale(1.2)).add(0, 1.8, 0), head.add(right.scale(-1.2)).add(0, 1.8, 0)})
            bb.addVertex(mv, (float) v.x, (float) v.y, (float) v.z).setColor(0F, 0F, 0F, 0.9F * k);
        BufferUploader.drawWithShader(bb.buildOrThrow());
    }
}
