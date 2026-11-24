package com.infinitystones.tensura.skill.ability.extra.v2c.client;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.client.render.GlowFx;
import com.infinitystones.tensura.skill.ability.extra.v2c.V2CNet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

/**
 * Client drawing of the Genesis red wave: a translucent crimson wall and a glowing ground band that follow the terrain
 * exactly at the rewrite front, plus dense dust, sparks and rune glyphs. The server sends one start message; the front is
 * animated here with the same curve the server uses to rewrite the land.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class GenesisClient {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final int FADE = 22, MAX_SEG = 240;
    private static final int RED = 0xFF2418, EMBER = 0xFF7A30, DEEP = 0x8A0A12;

    private static final class Wave {
        double cx, cy, cz;
        int r, total, age;
        Object level;
        int n;
        final float[] hOut = new float[MAX_SEG + 1], hIn = new float[MAX_SEG + 1];
    }

    private static final Map<Integer, Wave> WAVES = new HashMap<>();

    public static void accept(V2CNet.Msg m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (m.kind() == V2CNet.GENESIS_END) { WAVES.remove(m.id()); return; }
        Wave w = new Wave();
        w.cx = m.x(); w.cy = m.y(); w.cz = m.z();
        w.r = Math.max(4, m.a());
        w.total = Math.max(10, m.b());
        w.level = mc.level;
        WAVES.put(m.id(), w);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { WAVES.clear(); }

    private static double radius(Wave w, float age) {
        float prog = Math.min(1F, (age + 1F) / w.total);
        return w.r * (1 - (1 - prog) * (1 - prog));
    }

    private static float density(Minecraft mc) {
        ParticleStatus s = mc.options.particles().get();
        return s == ParticleStatus.MINIMAL ? 0.3F : s == ParticleStatus.DECREASED ? 0.6F : 1F;
    }

    private static float ground(ClientLevel l, double x, double z, float fallback) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        if (!l.hasChunk(bx >> 4, bz >> 4)) return fallback;
        int h = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
        return h <= l.getMinBuildHeight() + 1 ? fallback : h;
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.level == null || mc.player == null) return;
        if (WAVES.isEmpty()) return;
        ClientLevel l = mc.level;
        float dens = density(mc);
        RandomSource r = l.random;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        WAVES.values().removeIf(w -> {
            if (w.level != l) return true;
            w.age++;
            if (w.age > w.total + FADE) return true;
            double rad = radius(w, w.age);
            if (rad < 1) return false;
            w.n = Mth.clamp((int) (Math.PI * 2 * rad / 1.5), 24, MAX_SEG);
            float last = (float) w.cy;
            for (int i = 0; i <= w.n; i++) {
                double a = Math.PI * 2 * i / w.n;
                double cos = Math.cos(a), sin = Math.sin(a);
                last = ground(l, w.cx + cos * rad, w.cz + sin * rad, last);
                w.hOut[i] = last;
                w.hIn[i] = ground(l, w.cx + cos * Math.max(0, rad - 6), w.cz + sin * Math.max(0, rad - 6), last);
            }
            float fade = w.age > w.total ? 1F - (w.age - w.total) / (float) FADE : 1F;
            spawn(l, r, w, rad, cam, dens, fade);
            return false;
        });
    }

    private static void spawn(ClientLevel l, RandomSource r, Wave w, double rad, Vec3 cam, float dens, float fade) {
        int samples = (int) ((26 + Math.min(rad, 64) * 0.9) * dens * fade);
        double toCam = Math.atan2(cam.z - w.cz, cam.x - w.cx);
        DustParticleOptions red = new DustParticleOptions(new Vector3f(1F, 0.1F, 0.07F), 2.8F);
        DustParticleOptions bright = new DustParticleOptions(new Vector3f(1F, 0.35F, 0.12F), 2.0F);
        DustColorTransitionOptions fadeDust = new DustColorTransitionOptions(new Vector3f(1F, 0.15F, 0.1F), new Vector3f(0.35F, 0.0F, 0.05F), 2.6F);
        for (int k = 0; k < samples; k++) {
            double a = r.nextFloat() < 0.6F ? toCam + r.nextGaussian() * 0.55 : r.nextDouble() * Math.PI * 2;
            double rr = rad + (r.nextDouble() - 0.35) * 1.8;
            double x = w.cx + Math.cos(a) * rr, z = w.cz + Math.sin(a) * rr;
            double dx = x - cam.x, dz = z - cam.z;
            if (dx * dx + dz * dz > 110 * 110) continue;
            double y = ground(l, x, z, (float) w.cy);
            double py = y + r.nextDouble() * 3.2;
            double ux = Math.cos(a) * 0.04, uz = Math.sin(a) * 0.04;
            l.addAlwaysVisibleParticle(red, x, py, z, ux, 0.02, uz);
            l.addAlwaysVisibleParticle(fadeDust, x + (r.nextDouble() - 0.5) * 1.4, y + 0.2 + r.nextDouble() * 5.5, z + (r.nextDouble() - 0.5) * 1.4, ux, 0.05, uz);
            float f = r.nextFloat();
            if (f < 0.42F) l.addAlwaysVisibleParticle(bright, x, y + 0.3 + r.nextDouble() * 1.4, z, 0, 0.03, 0);
            if (f < 0.3F) l.addAlwaysVisibleParticle(ParticleTypes.FLAME, x, y + 0.2, z, ux * 1.5, 0.12 + r.nextDouble() * 0.12, uz * 1.5);
            else if (f < 0.55F) l.addAlwaysVisibleParticle(ParticleTypes.CRIMSON_SPORE, x, y + 1 + r.nextDouble() * 3, z, ux, 0.02, uz);
            else if (f < 0.8F) l.addAlwaysVisibleParticle(ParticleTypes.ENCHANT, x, y + 1.5 + r.nextDouble() * 2.5, z,
                    (r.nextDouble() - 0.5) * 2, r.nextDouble() * -0.5 - 0.2, (r.nextDouble() - 0.5) * 2);
        }
    }

    // ------------------------------------------------------------------ render

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || WAVES.isEmpty()) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 cp = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(-cp.x, -cp.y, -cp.z);
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        float time = (mc.level.getGameTime() % 100000L + pt) / 20F;
        for (Wave w : WAVES.values()) if (w.level == mc.level && w.n >= 8) walls(ps, buf, w, pt, time);
        buf.endBatch(RenderType.eyes(WHITE));
        for (Wave w : WAVES.values()) if (w.level == mc.level && w.n >= 8) flares(ps, buf, w, pt, time, cp);
        buf.endBatch();
        ps.popPose();
    }

    private static float fade(Wave w, float pt) {
        float age = w.age + pt;
        float in = Mth.clamp(age / 6F, 0, 1);
        float out = age > w.total ? 1F - Mth.clamp((age - w.total) / FADE, 0, 1) : 1F;
        return in * out;
    }

    private static void walls(PoseStack ps, MultiBufferSource buf, Wave w, float pt, float time) {
        float fade = fade(w, pt);
        if (fade <= 0.01F) return;
        double rad = radius(w, w.age + pt);
        if (rad < 1) return;
        Matrix4f m = ps.last().pose();
        VertexConsumer vc = buf.getBuffer(RenderType.eyes(WHITE));
        int n = w.n;
        for (int i = 0; i < n; i++) {
            double a0 = Math.PI * 2 * i / n, a1 = Math.PI * 2 * (i + 1) / n;
            float ripple0 = 0.75F + 0.25F * Mth.sin(time * 3.2F + i * 0.55F), ripple1 = 0.75F + 0.25F * Mth.sin(time * 3.2F + (i + 1) * 0.55F);
            // outer crimson wall, tall and soft
            wall(vc, m, w, rad, a0, a1, w.hOut[i], w.hOut[i + 1], 8F * ripple0, 8F * ripple1, 0.55F, RED, fade);
            // thin bright ember wall right on the front
            wall(vc, m, w, rad - 0.6, a0, a1, w.hOut[i], w.hOut[i + 1], 3.2F * ripple1, 3.2F * ripple0, 0.95F, EMBER, fade);
            // dark inner shimmer a few blocks behind
            wall(vc, m, w, rad - 2.4, a0, a1, w.hOut[i], w.hOut[i + 1], 4.5F * ripple0, 4.5F * ripple1, 0.35F, DEEP, fade);
            // ground band, bright at the front and fading inwards
            double rin = Math.max(0, rad - 6);
            float ix0 = (float) (w.cx + Math.cos(a0) * rin), iz0 = (float) (w.cz + Math.sin(a0) * rin);
            float ix1 = (float) (w.cx + Math.cos(a1) * rin), iz1 = (float) (w.cz + Math.sin(a1) * rin);
            float ox0 = (float) (w.cx + Math.cos(a0) * (rad + 0.6)), oz0 = (float) (w.cz + Math.sin(a0) * (rad + 0.6));
            float ox1 = (float) (w.cx + Math.cos(a1) * (rad + 0.6)), oz1 = (float) (w.cz + Math.sin(a1) * (rad + 0.6));
            float pulse = 0.8F + 0.2F * Mth.sin(time * 5F - i * 0.3F);
            quad2(vc, m, ix0, w.hIn[i] + 0.12F, iz0, ix1, w.hIn[i + 1] + 0.12F, iz1, ox1, w.hOut[i + 1] + 0.12F, oz1, ox0, w.hOut[i] + 0.12F, oz0,
                    0F, 0F, 0.85F * pulse * fade, 0.85F * pulse * fade, RED);
        }
    }

    private static void wall(VertexConsumer vc, Matrix4f m, Wave w, double rad, double a0, double a1, float h0, float h1,
                             float height0, float height1, float strength, int rgb, float fade) {
        float x0 = (float) (w.cx + Math.cos(a0) * rad), z0 = (float) (w.cz + Math.sin(a0) * rad);
        float x1 = (float) (w.cx + Math.cos(a1) * rad), z1 = (float) (w.cz + Math.sin(a1) * rad);
        float s = strength * fade;
        float yb0 = h0 - 1.2F, yb1 = h1 - 1.2F;
        float ym0 = h0 + height0 * 0.3F, ym1 = h1 + height1 * 0.3F;
        float yt0 = h0 + height0, yt1 = h1 + height1;
        quad2(vc, m, x0, yb0, z0, x1, yb1, z1, x1, ym1, z1, x0, ym0, z0, s, s, s * 0.55F, s * 0.55F, rgb);
        quad2(vc, m, x0, ym0, z0, x1, ym1, z1, x1, yt1, z1, x0, yt0, z0, s * 0.55F, s * 0.55F, 0F, 0F, rgb);
    }

    /** Double-sided quad with per-edge intensity (a: first and second vertex, b: third and fourth). */
    private static void quad2(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                              float x2, float y2, float z2, float x3, float y3, float z3,
                              float ia, float ib, float ic, float id, int rgb) {
        vert(vc, m, x0, y0, z0, ia, rgb); vert(vc, m, x1, y1, z1, ib, rgb); vert(vc, m, x2, y2, z2, ic, rgb); vert(vc, m, x3, y3, z3, id, rgb);
        vert(vc, m, x3, y3, z3, id, rgb); vert(vc, m, x2, y2, z2, ic, rgb); vert(vc, m, x1, y1, z1, ib, rgb); vert(vc, m, x0, y0, z0, ia, rgb);
    }

    private static void vert(VertexConsumer vc, Matrix4f m, float x, float y, float z, float intensity, int rgb) {
        int r = c(((rgb >> 16) & 255) * intensity), g = c(((rgb >> 8) & 255) * intensity), b = c((rgb & 255) * intensity);
        vc.addVertex(m, x, y, z).setColor(r, g, b, 255).setUv(0.5F, 0.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(GlowFx.FULL).setNormal(0, 1, 0);
    }

    private static int c(float v) { return Mth.clamp((int) v, 0, 255); }

    private static void flares(PoseStack ps, MultiBufferSource buf, Wave w, float pt, float time, Vec3 cam) {
        float fade = fade(w, pt);
        if (fade <= 0.01F) return;
        double rad = radius(w, w.age + pt);
        int n = w.n;
        int step = n > 120 ? 3 : 2;
        for (int i = 0; i < n; i += step) {
            double a = Math.PI * 2 * i / n;
            double x = w.cx + Math.cos(a) * rad, z = w.cz + Math.sin(a) * rad;
            double dx = x - cam.x, dz = z - cam.z;
            if (dx * dx + dz * dz > 120 * 120) continue;
            float y = w.hOut[i] + 0.6F;
            float p = 0.75F + 0.25F * Mth.sin(time * 6F + i * 0.8F);
            GlowFx.billboard(ps, buf, GlowFx.FLARE, true, (float) x, y + 0.5F * p, (float) z, 1.5F * p, EMBER, 0.85F * fade, time + i);
            if (i % (step * 2) == 0) GlowFx.billboard(ps, buf, GlowFx.HALO, true, (float) x, y + 1.2F, (float) z, 4.2F, RED, 0.35F * fade * p, 0);
        }
    }

    private GenesisClient() {}
}
