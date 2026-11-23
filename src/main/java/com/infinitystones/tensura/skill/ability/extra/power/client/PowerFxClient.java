package com.infinitystones.tensura.skill.ability.extra.power.client;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.client.render.GlowFx;
import com.infinitystones.tensura.skill.ability.extra.power.PowerNet;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Draws {@link PowerNet} effects. Client only (reached from the payload handler and client events). */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class PowerFxClient {
    private static final class Inst {
        final PowerNet.Msg f; int age;
        Inst(PowerNet.Msg f) { this.f = f; }
    }

    private static final Map<Integer, Inst> KEYED = new HashMap<>();
    private static final List<Inst> ONE_SHOT = new ArrayList<>();

    public static void accept(PowerNet.Msg f) {
        if (f.kind() == PowerNet.KILL) { KEYED.remove(f.id()); return; }
        if (f.id() != 0) {
            Inst old = KEYED.get(f.id());
            Inst n = new Inst(f);
            // NUKE keeps its own timeline; refreshed circles keep their fade-in finished
            if (old != null) n.age = f.kind() == PowerNet.NUKE ? old.age : Math.min(old.age, 8);
            KEYED.put(f.id(), n);
        } else if (ONE_SHOT.size() < 256) ONE_SHOT.add(new Inst(f));
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { KEYED.clear(); ONE_SHOT.clear(); }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (Minecraft.getInstance().isPaused()) return;
        for (Iterator<Inst> it = ONE_SHOT.iterator(); it.hasNext(); ) { Inst i = it.next(); if (++i.age > i.f.life()) it.remove(); }
        KEYED.values().removeIf(i -> ++i.age > i.f.life());
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || (KEYED.isEmpty() && ONE_SHOT.isEmpty())) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 cp = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        PDraw d = new PDraw(buf.getBuffer(PowerRT.GLOW), ps.last().pose(), cp);
        for (Inst i : ONE_SHOT) draw(d, i, pt, null, null);
        for (Inst i : KEYED.values()) draw(d, i, pt, null, null);
        buf.endBatch(PowerRT.GLOW);
        for (Inst i : ONE_SHOT) draw(null, i, pt, ps, buf);
        for (Inst i : KEYED.values()) draw(null, i, pt, ps, buf);
        buf.endBatch();
    }

    private static float fade(Inst i, float pt) {
        float age = i.age + pt, life = Math.max(1, i.f.life());
        return Mth.clamp(age / 6F, 0, 1) * Mth.clamp((life - age) / Math.min(20F, life * 0.35F), 0, 1);
    }

    static float easeOut(float t) { t = Mth.clamp(t, 0, 1); return 1 - (1 - t) * (1 - t) * (1 - t); }

    /** d != null: geometry pass; otherwise textured flare pass (ps/buf). */
    private static void draw(PDraw d, Inst i, float pt, PoseStack ps, MultiBufferSource buf) {
        PowerNet.Msg f = i.f;
        switch (f.kind()) {
            case PowerNet.NUKE -> nuke(d, i, pt, ps, buf);
            case PowerNet.CIRCLE -> {
                float a = fade(i, pt);
                if (a <= 0.01F) return;
                Vec3 c = f.a();
                if (f.entity() >= 0) {
                    Entity e = Minecraft.getInstance().level.getEntity(f.entity());
                    if (e == null) return;
                    c = e.getPosition(pt).add(c);
                }
                float age = i.age + pt;
                float grow = easeOut(age / 12F);
                double ang = age * 0.05 * (f.extra() == 0 ? 1 : f.extra());
                if (d != null) d.magic(new PDraw.P(c, f.b()), f.size() * grow, a, ang, f.id() * 31 + 7, f.style(), f.color(), f.color2());
                else flare(ps, buf, c, f.size() * 0.5F * grow, f.color(), a * 0.5F, ang);
            }
            case PowerNet.BRAID -> {
                float a = fade(i, pt);
                float age = i.age + pt;
                float reveal = easeOut(age / 6F);
                float time = age / 20F;
                if (d != null) d.braid(f.a(), f.b(), f.size(), f.style(), a, time, reveal);
                else {
                    flare(ps, buf, f.a(), f.size() * 3, 0xFFFFFF, a, time);
                    if (reveal > 0.95F) flare(ps, buf, f.b(), f.size() * 5, PowerNet.STONE_COLORS[(int) (time * 4) % 6], a, -time);
                }
            }
            default -> { }
        }
    }

    private static void flare(PoseStack ps, MultiBufferSource buf, Vec3 c, float size, int color, float al, double spin) {
        Vec3 cp = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        float x = (float) (c.x - cp.x), y = (float) (c.y - cp.y), z = (float) (c.z - cp.z);
        GlowFx.billboard(ps, buf, GlowFx.FLARE, true, x, y, z, size, color, al, (float) spin);
        GlowFx.billboard(ps, buf, GlowFx.HALO, true, x, y, z, size * 0.7F, color, al * 0.6F, 0);
    }

    // ================================================================== Celestial Nuke

    /** Accumulated spin (ticks): accelerates during the charge (speed 1 → 8), then keeps the top speed. */
    private static double phase(float t, float fire) {
        if (t < fire) { float c = t / fire; return t + 2 * fire * Math.pow(c, 3.5); }
        return 3 * fire + 8 * (t - fire);
    }

    private static void nuke(PDraw d, Inst i, float pt, PoseStack ps, MultiBufferSource buf) {
        PowerNet.Msg f = i.f;
        float t = i.age + pt, F = Math.max(20, f.extra()), L = Math.max(F + 10, f.life());
        Vec3 A = f.a(), B = f.b(), C = f.c();
        float R = f.size();
        int c1 = f.color(), c2 = f.color2();
        float charge = Mth.clamp(t / F, 0, 1);
        float post = t > F ? Mth.clamp((t - F) / (L - F), 0, 1) : 0;
        float alive = (t > F ? 1 - post * post : 1) * Mth.clamp(t / 4F, 0, 1);
        float grow = easeOut(t / 24F);
        double ang = phase(t, F) * 0.035;
        double Rm = R * grow * (1 + post * 0.8);
        float al = alive * (0.7F + 0.3F * charge);
        double H = Math.max(8, A.y - B.y);
        float time = t / 20F;

        if (d == null) { // textured glow pass
            float core = 2 + charge * R * 0.45F;
            if (t < F) flare(ps, buf, A, core * (0.9F + 0.1F * Mth.sin(t * 0.9F)), c1, al, time * (1 + charge * 4));
            if (t < F) flare(ps, buf, A, core * 0.4F, 0xFFFFFF, al * charge, -time * 3);
            if (t >= F && t < F + 30) {
                float k = 1 - (t - F) / 30F;
                flare(ps, buf, B.add(0, 1, 0), R * 1.6F * k + 3, 0xFFFFFF, k, time);
                flare(ps, buf, B.add(0, 1, 0), R * 2.4F * k + 4, c1, k * 0.8F, -time);
            }
            if (t < 40) flare(ps, buf, C.add(0, 1, 0), 2.5F, c1, (1 - t / 40F), time * 2);
            return;
        }

        // 1) pillar from the caster to the sky
        if (t < 44) {
            float k = easeOut(t / 12F);
            Vec3 top = new Vec3(C.x, C.y + (A.y + 10 - C.y) * k, C.z);
            float pa = t < 30 ? 1 : (44 - t) / 14F;
            d.color(c1);
            d.beam(C.add(0, 0.2, 0), top, 0.55, pa, time);
            d.magic(new PDraw.P(C.add(0, 0.06, 0), PDraw.UP), 2.6 * easeOut(t / 8F), pa * 0.9F, ang * 2, 3, PowerNet.SIMPLE, c1, c2);
        }

        PDraw.P main = new PDraw.P(A, PDraw.UP);
        // 2) the layered circles in the sky
        d.magic(main, Rm, al, ang, 11, PowerNet.RAYS, c1, c2);
        // crown above: wide rim with long ladder rays flaring outward/upward
        PDraw.P crown = main.shift(2.5 + R * 0.1);
        d.magic(crown, Rm * 1.15, al * 0.55F, -ang * 0.7, 23, PowerNet.SIMPLE, c2, c1);
        d.color(c1);
        d.rays(crown, Rm * 1.16, Rm * 2.0, 24, ang * 0.25, 0.55, R * 0.012 + 0.06, al * 0.8F, 5);
        // small high circle
        d.magic(main.shift(6 + R * 0.3), Rm * 0.42, al * 0.85F, ang * 1.8, 31, 0, c1, c2);
        // descending stack of rings (each appears in turn, alternating spin)
        int N = 6;
        double drop = Math.min(H * 0.78, 8 + R * 1.5);
        double lowR = Rm;
        for (int k = 1; k <= N; k++) {
            float ap = easeOut((t - (12 + k * 6)) / 10F);
            if (ap <= 0) continue;
            double y = -drop * k / N - post * 4 * k;
            double rk = Rm * (0.9 - k * 0.1) * ap;
            lowR = rk;
            d.magic(main.shift(y), rk, al * ap * (0.95F - k * 0.07F), ang * (k % 2 == 0 ? 1.35 : -1.55) * (1 + k * 0.12),
                    40 + k, k >= 4 ? PowerNet.SIMPLE : 0, k % 2 == 0 ? c1 : c2, k % 2 == 0 ? c2 : c1);
        }
        // cage struts linking the main circle to the lowest ring
        float cageA = easeOut((t - 30) / 15F) * al;
        if (cageA > 0.01F) {
            d.color(c2);
            for (int s = 0; s < 8; s++) {
                double a = -ang * 0.9 + Math.PI * 2 * s / 8;
                Vec3 top = main.pt(Rm * 0.82, a), low = main.shift(-drop - post * 4 * N).pt(lowR * 0.82, a + 0.4);
                d.line(top, low, 0.04 + R * 0.002, cageA * 0.5F);
            }
        }
        // ground target circle (warning)
        float ga = easeOut((t - 24) / 14F);
        if (ga > 0) d.magic(new PDraw.P(B.add(0, 0.12, 0), PDraw.UP), R * ga * (1 + post * 0.5), al * 0.75F * ga, -ang * 0.6, 77, PowerNet.SIMPLE, c1, c2);
        // 3) charge: sparks sucked into the centre along spirals
        if (t < F + 2) {
            float ca = charge * charge;
            for (int s = 0; s < 64; s++) {
                double ph = (t * 0.018 * (1 + charge * 3) + s * 0.6180339) % 1.0;
                double rad = Rm * 1.7 * (1 - ph);
                double a = s * 2.399 + ph * 5 + ang;
                Vec3 q = main.pt(rad, a).add(0, Math.sin(s * 1.7) * 6 * (1 - ph) - 2 * (1 - ph), 0);
                d.color(s % 3 == 0 ? 0xFFFFFF : c1);
                d.spark(q, 0.12 + R * 0.01, ca * (float) ph * alive);
            }
            // energy threads from ground to the circle
            d.color(c1);
            for (int s = 0; s < 10; s++) {
                double a = s * 0.628 + time * 0.7;
                Vec3 g0 = B.add(Math.cos(a) * R * 0.9, 0.2, Math.sin(a) * R * 0.9);
                float flick = 0.5F + 0.5F * Mth.sin(t * 1.3F + s * 2);
                d.ribbon(g0, main.pt(Rm * 0.3, a), 0.05 + R * 0.004, ca * 0.35F * flick, 0.5F);
            }
            // core orb rings
            d.color(c2);
            for (int k = 0; k < 3; k++) {
                Vec3 nrm = new Vec3(Math.sin(time * (1.3 + k) + k), Math.cos(time * (0.9 + k * 0.5)), Math.sin(time * 0.7 + k * 2));
                d.ring(new PDraw.P(A, nrm), 1.2 + charge * R * 0.12, 0.05, al * 0.8F);
            }
        }
        // 4) the colossal beam
        if (t >= F && t < F + 34) {
            float bt = t - F;
            double w = (R * 0.42 + 1) * Math.min(1, bt / 3) * (1 - Math.pow(bt / 34, 2));
            d.colossal(A.add(0, 6, 0), B, w, 1, time, c1, 0xFFFFFF);
            // ground blast discs
            PDraw.P gp = new PDraw.P(B.add(0, 0.3, 0), PDraw.UP);
            float k = bt / 34F;
            d.color(0xFFFFFF);
            d.ring(gp, R * (0.4 + k * 2.2), 0.3 + R * 0.02, (1 - k));
            d.color(c1);
            d.ring(gp, R * (0.2 + k * 1.5), 0.2 + R * 0.015, (1 - k) * 0.8F);
            d.disk(gp, R * (0.3 + k), (1 - k) * 0.25F);
        }
    }

    // ================================================================== screen: sky darkens near a charging nuke

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || KEYED.isEmpty()) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        float dark = 0;
        for (Inst i : KEYED.values()) {
            if (i.f.kind() != PowerNet.NUKE) continue;
            double dist = mc.player.position().distanceTo(i.f.b());
            if (dist > 110) continue;
            float t = i.age + pt, F = Math.max(20, i.f.extra());
            float k = t < F ? Mth.clamp((t - F * 0.35F) / (F * 0.65F), 0, 1) : Mth.clamp(1 - (t - F) / 8F, 0, 1);
            dark = Math.max(dark, k * (float) (1 - dist / 110 * 0.6));
        }
        if (dark <= 0.01F) return;
        GuiGraphics g = e.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        g.fill(0, 0, w, h, ((int) (dark * 95) << 24) | 0x12041E);
        int steps = 8;
        for (int s = 0; s < steps; s++) {
            int in = 50 * s / steps, th = Math.max(1, 50 / steps);
            int col = ((int) (dark * 120 * (steps - s) / steps) << 24) | 0x1A0630;
            g.fill(in, in, w - in, in + th, col);
            g.fill(in, h - in - th, w - in, h - in, col);
            g.fill(in, in + th, in + th, h - in - th, col);
            g.fill(w - in - th, in + th, w - in, h - in - th, col);
        }
    }

    private PowerFxClient() {}
}
