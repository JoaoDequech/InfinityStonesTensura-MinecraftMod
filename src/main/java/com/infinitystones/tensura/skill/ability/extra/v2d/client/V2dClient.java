package com.infinitystones.tensura.skill.ability.extra.v2d.client;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.extra.v2d.V2dNet;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Draws {@link V2dNet} effects: additive clock geometry in the world. */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class V2dClient {
    private static final class Inst {
        final V2dNet.Pkt f; int age;
        Inst(V2dNet.Pkt f) { this.f = f; }
    }

    private static final Map<Integer, Inst> KEYED = new HashMap<>();
    private static final List<Inst> ONE_SHOT = new ArrayList<>();
    private static final int MAX_ONE_SHOT = 200;

    public static void accept(V2dNet.Pkt f) {
        if (f.kind() == V2dNet.KILL) { KEYED.remove(f.id()); return; }
        if (f.id() != 0) KEYED.put(f.id(), new Inst(f));
        else if (ONE_SHOT.size() < MAX_ONE_SHOT) ONE_SHOT.add(new Inst(f));
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
        ps.pushPose();
        ps.translate(-cp.x, -cp.y, -cp.z);
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        float time = (mc.level.getGameTime() % 100000L + pt) / 20F;
        float quality = mc.options.particles().get() == ParticleStatus.MINIMAL ? 0.6F : 1F;
        V2dDraw d = new V2dDraw(buf.getBuffer(RenderType.lightning()), ps.last().pose(), cp);
        for (Inst i : ONE_SHOT) shape(d, i, pt, time, quality);
        for (Inst i : KEYED.values()) shape(d, i, pt, time, quality);
        buf.endBatch(RenderType.lightning());
        ps.popPose();
    }

    private static float fade(Inst i, float pt) {
        float age = i.age + pt, life = Math.max(1, i.f.life());
        return Mth.clamp(age / 5F, 0, 1) * Mth.clamp((life - age) / Math.min(15F, life * 0.35F), 0, 1);
    }

    private static void shape(V2dDraw d, Inst i, float pt, float time, float quality) {
        V2dNet.Pkt f = i.f;
        float al = fade(i, pt) * quality;
        if (al <= 0.01F) return;
        Vec3 c = f.a();
        d.color(f.color());
        switch (f.kind()) {
            case V2dNet.GATE -> gate(d, f, c, al, time);
            case V2dNet.CLOCK -> {
                Vec3 n = facing(d, c, f.b());
                Vec3 u = n.cross(new Vec3(0, 1, 0)).normalize(), w = new Vec3(0, 1, 0);
                float prog = progress(i, pt);
                d.face(c, u, w, f.size(), al, prog * Mth.TWO_PI * 3, prog * Mth.TWO_PI * 0.25F, time * 0.6F);
            }
            case V2dNet.BOMB -> bomb(d, i, f, c, al, time, pt);
            case V2dNet.DIGIT -> {
                Vec3 n = facing(d, c, Vec3.ZERO);
                Vec3 u = n.cross(new Vec3(0, 1, 0)).normalize(), w = new Vec3(0, 1, 0);
                float k = 1F + 0.35F * Math.max(0, 1 - (i.age + pt) / 8F);
                d.number(c, u, w, (int) f.extra(), f.size() * k, al);
                d.ring(c, u, w, f.size() * 0.9F * k, f.size() * 0.03F, al * 0.7F, time * 2, 0);
            }
            default -> { }
        }
    }

    private static float progress(Inst i, float pt) {
        float p0 = i.f.extra(), life = Math.max(1, i.f.life());
        return Mth.clamp(p0 + (1 - p0) * Math.min(1, (i.age + pt) / life), 0, 1);
    }

    /** Horizontal unit vector from the effect towards the viewer, or the given normal when it is not zero. */
    private static Vec3 facing(V2dDraw d, Vec3 c, Vec3 normal) {
        Vec3 n = normal.lengthSqr() > 1e-6 ? normal : new Vec3(d.cam.x - c.x, 0, d.cam.z - c.z);
        n = new Vec3(n.x, 0, n.z);
        return n.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : n.normalize();
    }

    private static void gate(V2dDraw d, V2dNet.Pkt f, Vec3 c, float al, float time) {
        float rad = f.size();
        Vec3 n = facing(d, c, f.b());
        Vec3 u = n.cross(new Vec3(0, 1, 0)).normalize(), w = new Vec3(0, 1, 0);
        float minute = time * 1.6F, hour = time * 0.13F;
        d.face(c, u, w, rad, al, minute, hour, time * 0.5F);
        d.ring(c, u, w, rad * 1.3F, rad * 0.02F, al * 0.5F, -time * 0.4F, 18);
        Vec3 top = c.add(0, rad * 1.5F, 0);
        d.number(top, u, w, (int) f.extra(), rad * 0.55F, al);
        // pillars, ground glow and a sweeping light between them
        Vec3 l = c.add(u.scale(-rad * 1.12)), r = c.add(u.scale(rad * 1.12));
        Vec3 groundL = l.add(0, -rad, 0), groundR = r.add(0, -rad, 0);
        d.line(groundL, l, rad * 0.03F, al * 0.8F);
        d.line(groundR, r, rad * 0.03F, al * 0.8F);
        d.ring(c.add(0, -rad + 0.05, 0), u, n, rad * 1.2F, rad * 0.03F, al * 0.5F, time, 0);
        double sweep = (time * 0.7) % 1.0;
        d.spark(c.add(u.scale((sweep * 2 - 1) * rad * 1.1)).add(0, Math.sin(time * 3) * rad * 0.2, 0), rad * 0.08F, al);
        if (f.color2() == 1) {
            float s = rad * 0.22F;
            Vec3 base = c.add(0, rad * 1.12F, 0);
            for (int k = -4; k < 4; k++) for (int row = 0; row < 2; row++) {
                if (((k + row) & 1) != 0) continue;
                Vec3 p = base.add(u.scale(k * s * 0.5)).add(0, row * s * 0.5, 0);
                d.quad(p, p.add(u.scale(s * 0.5)), p.add(u.scale(s * 0.5)).add(0, s * 0.5, 0), p.add(0, s * 0.5, 0), al * 0.7F);
            }
        }
    }

    private static void bomb(V2dDraw d, Inst i, V2dNet.Pkt f, Vec3 c, float al, float time, float pt) {
        float prog = progress(i, pt);
        float freq = 4F + prog * prog * 28F;
        float pulse = 1F + 0.07F * Mth.sin(time * freq) * (0.4F + prog);
        float rad = f.size() * pulse;
        Vec3 n = facing(d, c, Vec3.ZERO);
        Vec3 u = n.cross(new Vec3(0, 1, 0)).normalize(), w = new Vec3(0, 1, 0);
        d.face(c, u, w, rad, al, prog * Mth.TWO_PI * 2, prog * Mth.TWO_PI * 0.2F, time * 0.8F);
        d.color(f.color2());
        d.ring(c, u, w, rad * 1.35F, rad * 0.03F, al * 0.8F, -time * 1.4F, 16);
        d.ring(c, n, u, rad * 1.1F, rad * 0.02F, al * 0.5F, time * 1.1F, 12);
        d.ring(c, n, w, rad * 1.2F, rad * 0.02F, al * 0.5F, time * 0.9F, 12);
        for (int k = 0; k < 6; k++) {
            double a = time * (1.5 + prog * 3) + k * Math.PI / 3;
            d.spark(c.add(u.scale(Math.cos(a) * rad * 1.35)).add(0, Math.sin(a) * rad * 1.35, 0), rad * 0.07F, al);
        }
        d.color(f.color());
        Vec3 fuse = c.add(0, rad * 1.45F, 0);
        d.line(c.add(0, rad, 0), fuse, rad * 0.03F, al * 0.8F);
        d.spark(fuse.add(Math.sin(time * 40) * 0.02, 0, 0), rad * (0.12F + 0.1F * prog), al);
    }
}
