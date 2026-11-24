package com.infinitystones.tensura.skill.ability.extra.v2c.client;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.client.render.GlowFx;
import com.infinitystones.tensura.skill.ability.extra.v2c.V2CNet;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Client drawing of the Will-o'-Wisp guide and of the Essence Harvest soul orb (additive billboards + particles). */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class SoulClient {
    private static final int ORANGE = 0xFF8A20, AMBER = 0xFFC058, BLUE = 0x3FA8FF, ICE = 0xBFE8FF, SOUL = 0xFFD9A0;

    private static final class Wisp {
        Vec3 prev, cur, target;
        int state, sinceMsg, endTicks, age;
        boolean ending, arrived;
        final ArrayDeque<Vec3> trail = new ArrayDeque<>();
        Object level;
    }

    private static final class Orb { int life; float size; boolean charging; int age; }

    private static final Map<Integer, Wisp> WISPS = new HashMap<>();
    private static final Map<Integer, Orb> ORBS = new HashMap<>();

    public static void accept(V2CNet.Msg m) {
        Minecraft mc = Minecraft.getInstance();
        switch (m.kind()) {
            case V2CNet.WISP -> {
                Wisp w = WISPS.computeIfAbsent(m.id(), k -> new Wisp());
                if (w.ending) return;
                Vec3 p = new Vec3(m.x(), m.y(), m.z());
                if (w.cur == null) { w.cur = p; w.prev = p; w.level = mc.level; }
                w.target = p;
                w.state = m.a();
                w.sinceMsg = 0;
            }
            case V2CNet.WISP_END -> {
                Wisp w = WISPS.get(m.id());
                if (w == null) return;
                w.ending = true;
                w.arrived = m.a() == 1;
                if (w.arrived) burst(mc.level, w.cur);
            }
            case V2CNet.ORB -> {
                Orb o = ORBS.computeIfAbsent(m.id(), k -> new Orb());
                o.life = m.b();
                o.size = m.f();
                o.charging = m.a() == 1;
            }
            default -> { }
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { WISPS.clear(); ORBS.clear(); }

    private static float density(Minecraft mc) {
        ParticleStatus s = mc.options.particles().get();
        return s == ParticleStatus.MINIMAL ? 0F : s == ParticleStatus.DECREASED ? 0.5F : 1F;
    }

    private static void burst(ClientLevel level, Vec3 at) {
        if (level == null || at == null) return;
        float dens = density(Minecraft.getInstance());
        if (dens <= 0) return;
        RandomSource r = level.random;
        int n = (int) (36 * dens);
        for (int i = 0; i < n; i++) {
            double a = i * 2.4, up = 0.05 + r.nextDouble() * 0.22;
            level.addParticle(i % 3 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME, at.x, at.y, at.z,
                    Math.cos(a) * 0.18, up, Math.sin(a) * 0.18);
        }
        level.addParticle(ParticleTypes.FLASH, at.x, at.y, at.z, 0, 0, 0);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.level == null) return;
        float dens = density(mc);
        RandomSource r = mc.level.random;
        WISPS.values().removeIf(w -> {
            if (w.cur == null) return true;
            w.sinceMsg++;
            w.age++;
            if (w.ending) w.endTicks++;
            if (w.level != mc.level || w.sinceMsg > 40 || w.endTicks > 18) return true;
            w.prev = w.cur;
            w.cur = w.cur.lerp(w.target, 0.55);
            w.trail.addFirst(w.cur);
            while (w.trail.size() > 26) w.trail.removeLast();
            if (dens > 0 && !w.ending) {
                if (r.nextFloat() < 0.8F * dens)
                    mc.level.addParticle(w.age % 3 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SMALL_FLAME,
                            w.cur.x + (r.nextDouble() - 0.5) * 0.25, w.cur.y + 0.1 + r.nextDouble() * 0.2, w.cur.z + (r.nextDouble() - 0.5) * 0.25,
                            (r.nextDouble() - 0.5) * 0.01, 0.02 + r.nextDouble() * 0.02, (r.nextDouble() - 0.5) * 0.01);
                if (w.state == 1 && w.age % 3 == 0)
                    mc.level.addParticle(ParticleTypes.END_ROD, w.cur.x, w.cur.y + 0.2, w.cur.z, 0, 0.03, 0);
            }
            return false;
        });
        ORBS.values().removeIf(o -> { o.age++; return --o.life < 0; });
        if (dens > 0) for (Map.Entry<Integer, Orb> en : ORBS.entrySet()) {
            Entity ent = mc.level.getEntity(en.getKey());
            Orb o = en.getValue();
            if (ent == null || (!o.charging && o.age % 5 != 0) || r.nextFloat() > dens) continue;
            Vec3 at = orbPos(ent, 1F, o.age / 20F);
            mc.level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, at.x + (r.nextDouble() - 0.5) * 0.3, at.y + (r.nextDouble() - 0.5) * 0.3, at.z + (r.nextDouble() - 0.5) * 0.3, 0, 0.01, 0);
        }
    }

    private static Vec3 orbPos(Entity ent, float pt, float time) {
        float yaw = Mth.rotLerp(pt, ent.yRotO, ent.getYRot()) * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        return ent.getPosition(pt).add(right.scale(0.95)).add(0, ent.getBbHeight() + 0.35 + Math.sin(time * 2.4) * 0.12, 0);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || (WISPS.isEmpty() && ORBS.isEmpty())) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 cp = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(-cp.x, -cp.y, -cp.z);
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        float time = (mc.level.getGameTime() % 100000L + pt) / 20F;
        for (Wisp w : WISPS.values()) if (w.cur != null) wisp(ps, buf, w, pt, time);
        for (Map.Entry<Integer, Orb> en : ORBS.entrySet()) {
            Entity ent = mc.level.getEntity(en.getKey());
            if (ent != null) orb(ps, buf, en.getValue(), orbPos(ent, pt, time), time, pt);
        }
        buf.endBatch();
        ps.popPose();
    }

    private static void flare(PoseStack ps, MultiBufferSource buf, Vec3 c, float size, int rgb, float al, float spin) {
        GlowFx.billboard(ps, buf, GlowFx.FLARE, true, (float) c.x, (float) c.y, (float) c.z, size, rgb, al, spin);
    }

    private static void halo(PoseStack ps, MultiBufferSource buf, Vec3 c, float size, int rgb, float al) {
        GlowFx.billboard(ps, buf, GlowFx.HALO, true, (float) c.x, (float) c.y, (float) c.z, size, rgb, al, 0);
    }

    private static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255), g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255), bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    // ------------------------------------------------------------------ wisp

    private static void wisp(PoseStack ps, MultiBufferSource buf, Wisp w, float pt, float time) {
        float fade = w.ending ? 1 - Mth.clamp((w.endTicks + pt) / 18F, 0, 1) : Mth.clamp((w.age + pt) / 8F, 0, 1);
        if (fade <= 0.01F) return;
        Vec3 base = w.prev.lerp(w.cur, pt);
        Vec3 c = base.add(0, Math.sin(time * 3.1) * 0.12, 0);
        float flick = 0.9F + 0.1F * Mth.sin(time * 23F) + 0.05F * Mth.sin(time * 37F);
        boolean waiting = w.state == 1;
        float wait = waiting ? 0.75F + 0.25F * Mth.sin(time * 7F) : 1F;

        // trail of cooling embers
        int i = 0, n = w.trail.size();
        for (Vec3 t : w.trail) {
            float f = i / (float) Math.max(1, n);
            Vec3 tp = t.add(Math.sin(time * 3.1 - i * 0.2) * 0.02, 0.05 + i * 0.012, Math.cos(time * 2.7 - i * 0.2) * 0.02);
            flare(ps, buf, tp, 0.2F * (1 - f) + 0.02F, mix(AMBER, BLUE, f), (1 - f) * 0.55F * fade, 0);
            i++;
        }

        halo(ps, buf, c, 1.15F * flick * wait, ORANGE, 0.42F * fade);
        halo(ps, buf, c.add(0, -0.04, 0), 0.75F, BLUE, 0.34F * fade);
        // flame body: stacked flares that lean and lick upwards
        float[] size = {0.36F, 0.31F, 0.24F, 0.16F, 0.085F};
        for (int k = 0; k < size.length; k++) {
            double lean = Math.sin(time * 9.0 + k * 1.3) * 0.035 * k;
            Vec3 bp = c.add(lean, -0.04 + k * 0.11 * flick, Math.cos(time * 8.0 + k) * 0.03 * k);
            flare(ps, buf, bp, size[k] * flick, mix(0xFF6A18, 0xFFE070, k / 4F), 0.9F * fade, 0);
        }
        flare(ps, buf, c.add(Math.sin(time * 13) * 0.05, 0.62 * flick, 0), 0.07F + 0.03F * Mth.sin(time * 17), AMBER, 0.8F * fade, 0);
        // blue-white heart
        flare(ps, buf, c.add(0, -0.03, 0), 0.19F, ICE, fade, time);
        flare(ps, buf, c.add(0, -0.03, 0), 0.09F, 0xFFFFFF, fade, -time * 2);
        // orbiting sparks
        int sparks = waiting ? 5 : 3;
        for (int k = 0; k < sparks; k++) {
            double a = time * (waiting ? 4.0 : 2.6) + k * Math.PI * 2 / sparks;
            double rad = waiting ? 0.55 : 0.42;
            Vec3 sp = c.add(Math.cos(a) * rad, 0.1 + Math.sin(a * 1.7) * 0.15, Math.sin(a) * rad);
            flare(ps, buf, sp, 0.05F, k % 2 == 0 ? AMBER : ICE, 0.9F * fade, 0);
        }
        if (w.ending && w.arrived) {
            float e = (w.endTicks + pt) / 18F;
            halo(ps, buf, c, 0.8F + e * 4F, AMBER, (1 - e) * 0.7F);
        }
    }

    // ------------------------------------------------------------------ soul orb

    private static void orb(PoseStack ps, MultiBufferSource buf, Orb o, Vec3 c, float time, float pt) {
        float fadeIn = Mth.clamp((o.age + pt) / 10F, 0, 1);
        float fadeOut = Mth.clamp((o.life + 1) / 10F, 0, 1);
        float f = fadeIn * fadeOut;
        float s = 0.14F + 0.16F * Mth.clamp(o.size, 0, 1);
        float pulse = 1F + (o.charging ? 0.2F : 0.07F) * Mth.sin(time * (o.charging ? 14F : 4F));
        halo(ps, buf, c, (0.5F + 0.5F * o.size) * pulse, ORANGE, 0.55F * f);
        halo(ps, buf, c, 0.3F * pulse, AMBER, 0.6F * f);
        flare(ps, buf, c, s * 1.5F * pulse, SOUL, f, time * 0.8F);
        flare(ps, buf, c, s * 0.8F, 0xFFFFFF, f, -time);
        // two soul flames swirling inside the sphere
        for (int k = 0; k < 2; k++) {
            double a = time * 3.2 + k * Math.PI;
            Vec3 p = c.add(Math.cos(a) * s * 0.7, Math.sin(a * 1.3) * s * 0.7, Math.sin(a) * s * 0.7);
            flare(ps, buf, p, 0.08F, ICE, 0.85F * f, 0);
        }
        int sparks = o.charging ? 9 : 4;
        for (int k = 0; k < sparks; k++) {
            double a = time * (o.charging ? 3.4 : 1.8) + k * Math.PI * 2 / sparks, rad = s + 0.28 + 0.06 * (k % 3);
            Vec3 p = c.add(Math.cos(a) * rad, Math.sin(a * 1.4 + k) * 0.22, Math.sin(a) * rad);
            flare(ps, buf, p, 0.045F, k % 2 == 0 ? AMBER : SOUL, 0.85F * f, 0);
        }
    }

    private SoulClient() {}
}
