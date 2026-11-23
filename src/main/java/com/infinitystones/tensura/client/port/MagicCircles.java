package com.infinitystones.tensura.client.port;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.ported.FxNet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Layered, rotating magic circles (rings, rune bands, star polygons, tick marks, rays, stacked tiers) drawn with
 * additive glow in a stone's colours. Used by every ported power through {@link FxNet} (server → all nearby players)
 * and directly by the beam renderer. Pure geometry: no textures, a few thousand quads at most per circle.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class MagicCircles {
    private static final List<Live> LIVE = new ArrayList<>();
    private static final int MAX_LIVE = 96;

    private record Live(FxNet.Circle c, long born) {}

    /** Called by the FxNet client handler. */
    public static void add(FxNet.Circle c) {
        long now = FxNet.clientTicks();
        // a repeated (follow, seed) pair is a refresh of a long-lived circle: keep it fully faded in
        for (int i = 0; i < LIVE.size(); i++) {
            FxNet.Circle o = LIVE.get(i).c();
            if (o.seed() == c.seed() && o.follow() == c.follow() && o.style() == c.style() && o.color() == c.color()) {
                long age = now - LIVE.get(i).born();
                LIVE.set(i, new Live(c, now - Math.min(age, 6)));
                return;
            }
        }
        if (LIVE.size() >= MAX_LIVE) LIVE.remove(0);
        LIVE.add(new Live(c, now));
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        for (FxNet.Circle c; (c = FxNet.INBOX.poll()) != null; ) add(c);
        if (LIVE.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { LIVE.clear(); return; }
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 cp = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(-cp.x, -cp.y, -cp.z);
        Matrix4f m = ps.last().pose();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(BeamRenderType.BEAM);
        long now = FxNet.clientTicks();
        for (Iterator<Live> it = LIVE.iterator(); it.hasNext(); ) {
            Live l = it.next();
            FxNet.Circle c = l.c();
            float age = now - l.born() + pt;
            if (age > c.duration()) { it.remove(); continue; }
            Vec3 center = new Vec3(c.x(), c.y(), c.z());
            if (c.follow() >= 0) {
                Entity en = mc.level.getEntity(c.follow());
                if (en == null) { it.remove(); continue; }
                center = en.getPosition(pt).add(c.x(), c.y(), c.z()); // offset when following
            }
            if (center.distanceToSqr(cp) > 256 * 256) continue;
            float in = Mth.clamp(age / 6F, 0, 1), out = Mth.clamp((c.duration() - age) / 8F, 0, 1);
            float appear = 1 - (1 - in) * (1 - in) * (1 - in);
            float alpha = Math.min(in, out);
            draw(vc, m, center, new Vec3(c.nx(), c.ny(), c.nz()), c.radius() * (0.35F + 0.65F * appear), c.color(), c.color2(),
                    alpha, (now + pt) / 20F, c.seed(), c.style());
        }
        buf.endBatch(BeamRenderType.BEAM);
        ps.popPose();
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut e) { LIVE.clear(); }

    // ---------------- geometry ----------------

    /**
     * One magic circle. style bits: 0-1 = extra stacked tiers (0..3), 4 = outward rays, 8 = heptagram instead of
     * hexagram, 16 = spin fast.
     */
    public static void draw(VertexConsumer vc, Matrix4f m, Vec3 center, Vec3 normal, float radius, int rgb, int rgb2,
                            float alpha, float time, int seed, int style) {
        if (alpha <= 0.01F || radius <= 0.01F) return;
        Vec3 n = normal.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : normal.normalize();
        Vec3 ref = Math.abs(n.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = n.cross(ref).normalize(), v = n.cross(u).normalize();
        float spin = (style & 16) != 0 ? 2.2F : 1F;
        int tiers = style & 3;
        disc(vc, m, center, u, v, radius * 1.02F, rgb, alpha * 0.10F);
        layer(vc, m, center, u, v, radius, rgb, rgb2, alpha, time * spin, seed, style, true);
        for (int t = 1; t <= tiers; t++) {
            float r = radius * (1F - 0.22F * t);
            Vec3 c = center.add(n.scale(radius * 0.32F * t));
            layer(vc, m, c, u, v, r, t % 2 == 0 ? rgb : rgb2, rgb, alpha * (1F - 0.15F * t), -time * spin * (1 + 0.3F * t), seed + t * 101, style, false);
        }
    }

    private static void layer(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 u, Vec3 v, float R, int rgb, int rgb2, float a,
                              float time, int seed, int style, boolean main) {
        float rot = time * 0.6F, rotB = -time * 0.9F, rotC = time * 1.4F;
        float lw = Math.max(0.03F, R * 0.012F);
        // outer double ring
        ring(vc, m, c, u, v, R, lw * 1.6F, rgb, a);
        ring(vc, m, c, u, v, R * 0.94F, lw, rgb2, a * 0.85F);
        // rune band: blocky glyph segments between two rings (pseudo-random per seed)
        RandomSource r = RandomSource.create(seed);
        int runes = 36;
        for (int k = 0; k < runes; k++) {
            float a0 = rot + (float) (Math.PI * 2 * k / runes);
            float span = (float) (Math.PI * 2 / runes) * (0.35F + r.nextFloat() * 0.5F);
            float rIn = R * (0.80F + r.nextFloat() * 0.04F), rOut = R * (0.86F + r.nextFloat() * 0.05F);
            arc(vc, m, c, u, v, rIn, rOut, a0, a0 + span, rgb, a * (0.6F + 0.4F * r.nextFloat()));
            if (r.nextInt(3) == 0) { // a little stroke inside the glyph
                float mid = a0 + span * 0.5F;
                line(vc, m, c, u, v, polar(c, u, v, R * 0.78F, mid), polar(c, u, v, R * 0.70F, mid), lw, rgb2, a * 0.8F);
            }
        }
        ring(vc, m, c, u, v, R * 0.76F, lw, rgb, a * 0.9F);
        // star polygon (hexagram or heptagram) rotating the other way
        boolean hepta = (style & 8) != 0;
        int pts = hepta ? 7 : 6, step = hepta ? 3 : 2;
        for (int k = 0; k < pts; k++) {
            Vec3 p0 = polar(c, u, v, R * 0.74F, rotB + (float) (Math.PI * 2 * k / pts));
            Vec3 p1 = polar(c, u, v, R * 0.74F, rotB + (float) (Math.PI * 2 * ((k + step) % pts) / pts));
            line(vc, m, c, u, v, p0, p1, lw * 1.3F, rgb2, a);
        }
        // inner ring with tick marks
        ring(vc, m, c, u, v, R * 0.46F, lw, rgb, a);
        for (int k = 0; k < 24; k++) {
            float ang = rotC + (float) (Math.PI * 2 * k / 24);
            float len = k % 3 == 0 ? 0.10F : 0.05F;
            line(vc, m, c, u, v, polar(c, u, v, R * 0.46F, ang), polar(c, u, v, R * (0.46F + len), ang), lw * 0.8F, rgb2, a * 0.9F);
        }
        // core: small square glyph + ring
        ring(vc, m, c, u, v, R * 0.2F, lw, rgb2, a);
        for (int k = 0; k < 4; k++) {
            Vec3 p0 = polar(c, u, v, R * 0.3F, -rotC + (float) (Math.PI / 2 * k));
            Vec3 p1 = polar(c, u, v, R * 0.3F, -rotC + (float) (Math.PI / 2 * (k + 1)));
            line(vc, m, c, u, v, p0, p1, lw, rgb, a);
        }
        disc(vc, m, c, u, v, R * 0.2F, rgb2, a * 0.35F);
        // outward rays (the "sun" spikes of the reference)
        if (main && (style & 4) != 0) {
            for (int k = 0; k < 12; k++) {
                float ang = rot * 0.5F + (float) (Math.PI * 2 * k / 12);
                float len = 1.25F + 0.35F * Mth.sin(time * 3 + k);
                for (int s = 0; s < 4; s++) { // blocky dashed spike
                    float f0 = 1.02F + (len - 1.02F) * s / 4F, f1 = f0 + (len - 1.02F) / 6F;
                    line(vc, m, c, u, v, polar(c, u, v, R * f0, ang), polar(c, u, v, R * f1, ang), lw * (2.2F - s * 0.4F), s % 2 == 0 ? rgb : rgb2, a * (1 - s * 0.2F));
                }
            }
        }
    }

    private static Vec3 polar(Vec3 c, Vec3 u, Vec3 v, float r, float ang) {
        return c.add(u.scale(Math.cos(ang) * r)).add(v.scale(Math.sin(ang) * r));
    }

    private static void ring(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 u, Vec3 v, float r, float w, int rgb, float a) {
        arc(vc, m, c, u, v, r - w, r + w, 0, (float) (Math.PI * 2), rgb, a);
    }

    private static void arc(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 u, Vec3 v, float rIn, float rOut, float a0, float a1, int rgb, float a) {
        int n = Math.max(2, (int) ((a1 - a0) / (Math.PI * 2) * 64));
        int R = (rgb >> 16) & 255, G = (rgb >> 8) & 255, B = rgb & 255, A = (int) (Mth.clamp(a, 0, 1) * 255);
        for (int k = 0; k < n; k++) {
            float t0 = a0 + (a1 - a0) * k / n, t1 = a0 + (a1 - a0) * (k + 1) / n;
            vtx(vc, m, polar(c, u, v, rIn, t0), R, G, B, A);
            vtx(vc, m, polar(c, u, v, rOut, t0), R, G, B, A);
            vtx(vc, m, polar(c, u, v, rOut, t1), R, G, B, A);
            vtx(vc, m, polar(c, u, v, rIn, t1), R, G, B, A);
        }
    }

    private static void line(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 u, Vec3 v, Vec3 p0, Vec3 p1, float w, int rgb, float a) {
        Vec3 d = p1.subtract(p0);
        Vec3 side = u.cross(v).cross(d);
        if (side.lengthSqr() < 1e-8) return;
        side = side.normalize().scale(w);
        int R = (rgb >> 16) & 255, G = (rgb >> 8) & 255, B = rgb & 255, A = (int) (Mth.clamp(a, 0, 1) * 255);
        vtx(vc, m, p0.add(side), R, G, B, A); vtx(vc, m, p0.subtract(side), R, G, B, A);
        vtx(vc, m, p1.subtract(side), R, G, B, A); vtx(vc, m, p1.add(side), R, G, B, A);
    }

    private static void disc(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3 u, Vec3 v, float r, int rgb, float a) {
        int R = (rgb >> 16) & 255, G = (rgb >> 8) & 255, B = rgb & 255, A = (int) (Mth.clamp(a, 0, 1) * 255);
        int n = 32;
        for (int k = 0; k < n; k++) {
            float t0 = (float) (Math.PI * 2 * k / n), t1 = (float) (Math.PI * 2 * (k + 1) / n);
            vtx(vc, m, c, R, G, B, A); vtx(vc, m, c, R, G, B, A);
            vtx(vc, m, polar(c, u, v, r, t1), R, G, B, 0); vtx(vc, m, polar(c, u, v, r, t0), R, G, B, 0);
        }
    }

    private static void vtx(VertexConsumer vc, Matrix4f m, Vec3 p, int r, int g, int b, int a) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
    }

    private MagicCircles() {}
}
