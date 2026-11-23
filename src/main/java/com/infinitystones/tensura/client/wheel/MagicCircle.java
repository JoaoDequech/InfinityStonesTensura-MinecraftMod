package com.infinitystones.tensura.client.wheel;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Layered magic circle in a stone's colour (outer double ring, rotating rune band, counter-rotating hexagram, orbiting
 * sigils, spokes, inner ring, glow core). The same geometry is drawn flat on the HUD (aim reticle) and lying in the
 * world (aim point, switch/cast flashes). ~1.5k vertices per circle, one draw call, additive — lag-safe.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class MagicCircle {
    /** Short-lived circles in the world (local player only). */
    private record Flash(Vec3 at, float radius, int color, long start, long ms, boolean grow) {}
    private static final List<Flash> FLASHES = new ArrayList<>();

    /** Point + size of the live aim circle (set every frame by AimMode, null when not aiming). */
    static Vec3 aimAt;
    static float aimRadius;
    static int aimColor;
    static boolean aimLocked;

    /** A circle that blooms at the player's feet: power cast or switch flipped. on=false → shrinking, dimmer (switch off). */
    public static void flashAtFeet(int stone, boolean on) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || stone < 0 || stone >= Stone.values().length) return;
        if (FLASHES.size() > 6) FLASHES.remove(0);
        FLASHES.add(new Flash(mc.player.position().add(0, 0.03, 0), 1.9F, Stone.values()[stone].color(), System.currentTimeMillis(), on ? 900 : 600, on));
    }

    // ------------------------------------------------------------ world

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        if (aimAt == null && FLASHES.isEmpty()) return;
        long now = System.currentTimeMillis();
        Vec3 cp = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(-cp.x, -cp.y, -cp.z);
        Matrix4f m = ps.last().pose();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;
        if (aimAt != null) {
            float r = aimRadius * (aimLocked ? 0.92F + 0.05F * Mth.sin(now / 90F) : 1F);
            emit(bb, m, true, (float) aimAt.x, (float) aimAt.y + 0.04F, (float) aimAt.z, r, aimColor, now / 1000F, 0.9F);
            any = true;
        }
        for (Iterator<Flash> it = FLASHES.iterator(); it.hasNext(); ) {
            Flash f = it.next();
            float t = (now - f.start()) / (float) f.ms();
            if (t >= 1) { it.remove(); continue; }
            float ease = 1 - (1 - t) * (1 - t);
            float r = f.grow() ? f.radius() * (0.35F + 0.75F * ease) : f.radius() * (1.1F - 0.8F * ease);
            float a = (t < 0.15F ? t / 0.15F : 1 - (t - 0.15F) / 0.85F) * (f.grow() ? 1F : 0.6F);
            emit(bb, m, true, (float) f.at().x, (float) f.at().y, (float) f.at().z, r, f.color(), now / 1000F + t * 2, a);
            any = true;
        }
        MeshData mesh = bb.build();
        if (any && mesh != null) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.disableCull();
            RenderSystem.depthMask(false);
            RenderSystem.enableDepthTest();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            BufferUploader.drawWithShader(mesh);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
        } else if (mesh != null) mesh.close();
        ps.popPose();
    }

    // ------------------------------------------------------------ HUD

    /** Reticle at (0,0) of the current GUI pose. */
    static void hud(GuiGraphics g, float radius, int color, float alpha, boolean locked) {
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        float r = radius * (locked ? 0.85F + 0.04F * Mth.sin(System.currentTimeMillis() / 90F) : 1F);
        emit(bb, g.pose().last().pose(), false, 0, 0, 0, r, locked ? lighten(color, 0.35F) : color, System.currentTimeMillis() / 1000F, alpha);
        MeshData mesh = bb.build();
        if (mesh == null) return;
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(mesh);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }

    // ------------------------------------------------------------ geometry

    /** world = lying flat in XZ at (ox,oy,oz); else HUD plane (x right, y down). t = seconds (animation). */
    private static void emit(BufferBuilder bb, Matrix4f m, boolean world, float ox, float oy, float oz, float r, int col, float t, float alpha) {
        Sink s = new Sink(bb, m, world, ox, oy, oz);
        int hot = lighten(col, 0.55F), white = lighten(col, 0.85F);
        int a = (int) (255 * Mth.clamp(alpha, 0, 1));
        float w = r * 0.018F + (world ? 0.012F : 0.35F); // line width
        // 1. outer double ring + soft halo
        s.ring(r * 1.0F, r * 1.0F + w * 1.6F, 0, Mth.TWO_PI, withA(hot, a));
        s.ring(r * 0.955F, r * 0.955F + w, 0, Mth.TWO_PI, withA(col, a * 3 / 4));
        s.ring(r * 1.0F + w * 1.6F, r * 1.09F, 0, Mth.TWO_PI, withA(col, a / 7));
        // 2. rune band: dashes of varying length, slow rotation
        float spin = t * 0.45F;
        for (int i = 0; i < 40; i++) {
            float a0 = spin + i * Mth.TWO_PI / 40, len = (0.25F + 0.55F * hash(i)) * Mth.TWO_PI / 40;
            float rin = r * (0.84F + 0.03F * hash(i * 7 + 3)), rout = r * 0.92F;
            s.ring(rin, rout, a0, a0 + len, withA(i % 5 == 0 ? white : col, a * (i % 5 == 0 ? 1 : 3) / 4 / (i % 5 == 0 ? 1 : 1)));
        }
        s.ring(r * 0.82F, r * 0.82F + w * 0.8F, 0, Mth.TWO_PI, withA(col, a * 2 / 3));
        // 3. counter-rotating hexagram
        float hs = -t * 0.3F;
        for (int k = 0; k < 2; k++) {
            float base = hs + k * Mth.PI / 3;
            for (int j = 0; j < 3; j++) {
                float p = base + j * Mth.TWO_PI / 3, q = base + (j + 1) * Mth.TWO_PI / 3;
                s.line(cos(p) * r * 0.8F, sin(p) * r * 0.8F, cos(q) * r * 0.8F, sin(q) * r * 0.8F, w, withA(hot, a * 4 / 5));
            }
        }
        // 4. six orbiting sigils (small rings with a dot), on the hexagram tips' circle
        for (int j = 0; j < 6; j++) {
            float p = hs + j * Mth.TWO_PI / 6;
            float cx = cos(p) * r * 0.8F, cy = sin(p) * r * 0.8F, sr = r * 0.085F;
            s.ringAt(cx, cy, sr, sr + w, withA(white, a));
            s.ringAt(cx, cy, 0, sr * 0.4F, withA(hot, a * 3 / 4));
        }
        // 5. spokes + inner ring, rotating the other way, with tick marks
        float is = t * 0.9F;
        for (int j = 0; j < 12; j++) {
            float p = is + j * Mth.TWO_PI / 12;
            float r0 = r * 0.42F, r1 = r * (j % 2 == 0 ? 0.62F : 0.52F);
            s.line(cos(p) * r0, sin(p) * r0, cos(p) * r1, sin(p) * r1, w * 0.8F, withA(col, a * 2 / 3));
        }
        s.ring(r * 0.4F, r * 0.4F + w * 1.2F, 0, Mth.TWO_PI, withA(hot, a));
        for (int j = 0; j < 24; j++) {
            float p = -is * 0.5F + j * Mth.TWO_PI / 24;
            s.ring(r * 0.31F, r * 0.36F, p, p + 0.08F, withA(col, a * 3 / 4));
        }
        // 6. core: inner square (rotating) + pulsing glow
        float cs = t * 1.4F, cr = r * 0.22F;
        for (int j = 0; j < 4; j++) {
            float p = cs + j * Mth.HALF_PI, q = p + Mth.HALF_PI;
            s.line(cos(p) * cr, sin(p) * cr, cos(q) * cr, sin(q) * cr, w * 0.8F, withA(hot, a * 3 / 4));
        }
        float pulse = 0.6F + 0.4F * Mth.sin(t * 6);
        s.ringAt(0, 0, 0, r * 0.12F, withA(white, (int) (a * 0.5F * pulse)));
        s.ringAt(0, 0, r * 0.12F, r * 0.3F, withA(col, (int) (a * 0.15F * pulse)));
    }

    /** Writes triangles either in the HUD plane or lying flat in the world. */
    private record Sink(BufferBuilder bb, Matrix4f m, boolean world, float ox, float oy, float oz) {
        void v(float u, float w, int argb) {
            if (world) bb.addVertex(m, ox + u, oy, oz + w).setColor(argb);
            else bb.addVertex(m, ox + u, oy + w, 0).setColor(argb);
        }
        void tri(float u0, float w0, float u1, float w1, float u2, float w2, int c) { v(u0, w0, c); v(u1, w1, c); v(u2, w2, c); }
        void ring(float rIn, float rOut, float a0, float a1, int c) { ringAt(0, 0, rIn, rOut, a0, a1, c); }
        void ringAt(float cx, float cy, float rIn, float rOut, int c) { ringAt(cx, cy, rIn, rOut, 0, Mth.TWO_PI, c); }
        void ringAt(float cx, float cy, float rIn, float rOut, float a0, float a1, int c) {
            if ((c >>> 24) == 0 || a1 <= a0 || rOut <= rIn) return;
            int steps = Math.max(2, Math.min(64, (int) Math.ceil((a1 - a0) / 0.1F)));
            for (int i = 0; i < steps; i++) {
                float t0 = a0 + (a1 - a0) * i / steps, t1 = a0 + (a1 - a0) * (i + 1) / steps;
                float c0 = cos(t0), s0 = sin(t0), c1 = cos(t1), s1 = sin(t1);
                tri(cx + c0 * rIn, cy + s0 * rIn, cx + c0 * rOut, cy + s0 * rOut, cx + c1 * rOut, cy + s1 * rOut, c);
                if (rIn > 0) tri(cx + c0 * rIn, cy + s0 * rIn, cx + c1 * rOut, cy + s1 * rOut, cx + c1 * rIn, cy + s1 * rIn, c);
            }
        }
        void line(float x0, float y0, float x1, float y1, float width, int c) {
            float dx = x1 - x0, dy = y1 - y0, len = (float) Math.sqrt(dx * dx + dy * dy);
            if (len < 1e-4F) return;
            float nx = -dy / len * width / 2, ny = dx / len * width / 2;
            tri(x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, c);
            tri(x0 + nx, y0 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny, c);
        }
    }

    private static float cos(float a) { return Mth.cos(a - Mth.HALF_PI); }
    private static float sin(float a) { return Mth.sin(a - Mth.HALF_PI); }
    private static float hash(int i) { int h = i * 0x9E3779B1; h ^= h >>> 15; return (h & 0xFFFF) / 65535F; }
    private static int withA(int c, int a) { return (Mth.clamp(a, 0, 255) << 24) | (c & 0xFFFFFF); }
    private static int lighten(int c, float f) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return 0xFF000000 | ((int) (r + (255 - r) * f) << 16) | ((int) (g + (255 - g) * f) << 8) | (int) (b + (255 - b) * f);
    }

    private MagicCircle() {}
}
