package com.infinitystones.tensura.skill.ability.extra.v2b.client;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.client.render.GlowFx;
import com.infinitystones.tensura.skill.ability.extra.v2b.V2BNet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Client side of the v2b powers: star rails, ride trails, ping beams, thought bubbles, scale waves, confusion. */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class V2BClient {
    private static final ResourceLocation BUBBLE_TEX = InfinityStones.id("textures/render/v2b_bubble.png");
    private static final ItemStack[] BUBBLE_ICONS = {new ItemStack(Items.FIRE_CHARGE), new ItemStack(Items.ENDER_PEARL), new ItemStack(Items.DIAMOND),
            new ItemStack(Items.BELL), new ItemStack(Items.CLOCK), new ItemStack(Items.GOLDEN_APPLE)};
    private static final int[] BUBBLE_GLOW = {0xFF4A3A, 0xB36BFF, 0x6BE0FF, 0xFFD34A, 0xE8E8E8, 0x6BE07A};

    private static final class StarSet { List<Vec3> pts = new ArrayList<>(); int[] born = new int[0]; int expire, color; boolean riding; }
    private static final class Ride { int[] ids; int expire, color; }
    private static final class Ping { Vec3 pos; int color, type, born, life; }
    private static final class Bubble { int type, born, life; }
    private static final class Scale { int color, born, life, data; double[] old, now; }
    private static final class Sel { int color, born, life; double[] box; }

    private static final Map<Integer, StarSet> STARS = new HashMap<>();
    private static final Map<Integer, Ride> RIDES = new HashMap<>();
    private static final Map<Integer, ArrayDeque<Vec3>> TRAILS = new HashMap<>();
    private static final Map<Integer, Ping> PINGS = new HashMap<>();
    private static final Map<Integer, Bubble> BUBBLES = new HashMap<>();
    private static final Map<Integer, Scale> SCALES = new HashMap<>();
    private static final Map<Integer, Sel> SELS = new HashMap<>();
    private static int now, confuseUntil;

    public static void accept(V2BNet.Msg m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        double[] p = m.pts();
        switch (m.kind()) {
            case V2BNet.STARS -> {
                StarSet old = STARS.get(m.id());
                StarSet s = new StarSet();
                int n = p.length / 3;
                s.born = new int[n];
                for (int i = 0; i < n; i++) {
                    Vec3 v = new Vec3(p[i * 3], p[i * 3 + 1], p[i * 3 + 2]);
                    s.pts.add(v);
                    s.born[i] = now;
                    if (old != null) for (int j = 0; j < old.pts.size(); j++)
                        if (old.pts.get(j).distanceToSqr(v) < 1e-4) { s.born[i] = old.born[j]; break; }
                }
                s.expire = now + m.life();
                s.color = m.color();
                s.riding = (m.data() & 1) != 0;
                STARS.put(m.id(), s);
            }
            case V2BNet.STARS_CLEAR -> STARS.remove(m.id());
            case V2BNet.RIDE -> {
                if (m.life() <= 0) { RIDES.clear(); return; }
                Ride r = new Ride();
                r.ids = new int[p.length];
                for (int i = 0; i < p.length; i++) r.ids[i] = (int) p[i];
                r.expire = now + m.life();
                r.color = m.color();
                RIDES.put(m.id(), r);
            }
            case V2BNet.PING -> {
                if (p.length < 3) return;
                Ping g = new Ping();
                g.pos = new Vec3(p[0], p[1], p[2]);
                g.color = m.color(); g.type = m.data(); g.born = now; g.life = m.life();
                PINGS.put(m.id(), g);
            }
            case V2BNet.PING_CLEAR -> PINGS.remove(m.id());
            case V2BNet.BUBBLE -> {
                Bubble b = new Bubble();
                b.type = Mth.clamp(m.data(), 0, BUBBLE_ICONS.length - 1); b.born = now; b.life = m.life();
                BUBBLES.put(m.id(), b);
            }
            case V2BNet.SCALE -> {
                if (p.length < 12) return;
                Scale s = new Scale();
                s.color = m.color(); s.born = now; s.life = m.life(); s.data = m.data();
                s.old = java.util.Arrays.copyOfRange(p, 0, 6);
                s.now = java.util.Arrays.copyOfRange(p, 6, 12);
                SCALES.put(m.id(), s);
            }
            case V2BNet.SELECT -> {
                if (p.length < 6) return;
                Sel s = new Sel();
                s.color = m.color(); s.born = now; s.life = m.life();
                s.box = java.util.Arrays.copyOf(p, 6);
                SELS.put(m.id(), s);
            }
            case V2BNet.SELECT_CLEAR -> SELS.remove(m.id());
            case V2BNet.CONFUSE -> confuseUntil = m.life() <= 0 ? 0 : now + m.life();
            default -> { }
        }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        STARS.clear(); RIDES.clear(); TRAILS.clear(); PINGS.clear(); BUBBLES.clear(); SCALES.clear(); SELS.clear(); confuseUntil = 0;
    }

    private static boolean localRiding(LocalPlayer p) {
        for (Ride r : RIDES.values()) if (r.expire > now) for (int id : r.ids) if (id == p.getId()) return true;
        return false;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        now++;
        STARS.values().removeIf(s -> s.expire < now);
        RIDES.values().removeIf(r -> r.expire < now);
        PINGS.values().removeIf(g -> now - g.born > g.life);
        BUBBLES.values().removeIf(b -> now - b.born > b.life);
        SCALES.values().removeIf(s -> now - s.born > s.life);
        SELS.values().removeIf(s -> now - s.born > s.life);

        java.util.Set<Integer> live = new java.util.HashSet<>();
        for (Ride r : RIDES.values()) for (int id : r.ids) {
            live.add(id);
            Entity en = mc.level.getEntity(id);
            if (en == null) continue;
            ArrayDeque<Vec3> t = TRAILS.computeIfAbsent(id, k -> new ArrayDeque<>());
            t.addLast(en.position().add(0, en.getBbHeight() * 0.5, 0));
            while (t.size() > 28) t.removeFirst();
        }
        TRAILS.keySet().removeIf(id -> !live.contains(id));

        LocalPlayer lp = mc.player;
        if (lp != null && confuseUntil > now && mc.screen == null) {
            float t = now;
            float drift = Mth.sin(t * 0.11F) * 0.9F + Mth.sin(t * 0.037F + 1.3F) * 0.7F;
            lp.turn(drift / 0.15F, 0);
        }
    }

    /** Riders pass through blocks and cannot steer; the confused have their movement keys swapped. */
    @SubscribeEvent
    public static void input(MovementInputUpdateEvent e) {
        if (!(e.getEntity() instanceof LocalPlayer lp)) return;
        var in = e.getInput();
        if (localRiding(lp)) {
            lp.noPhysics = true;
            in.forwardImpulse = 0; in.leftImpulse = 0;
            in.up = false; in.down = false; in.left = false; in.right = false; in.jumping = false;
            return;
        }
        if (confuseUntil > now) {
            in.forwardImpulse = -in.forwardImpulse;
            in.leftImpulse = -in.leftImpulse;
            boolean u = in.up; in.up = in.down; in.down = u;
            boolean l = in.left; in.left = in.right; in.right = l;
        }
    }

    // ================================================================== rendering

    private static float ease(float t) { t = Mth.clamp(t, 0, 1); return t * t * (3 - 2 * t); }

    /** Pop-in with a little overshoot. */
    private static float pop(float age) {
        float t = Mth.clamp(age / 8F, 0, 1);
        float c = 1.9F;
        return 1 + (c + 1) * (float) Math.pow(t - 1, 3) + c * (float) Math.pow(t - 1, 2);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (STARS.isEmpty() && RIDES.isEmpty() && PINGS.isEmpty() && BUBBLES.isEmpty() && SCALES.isEmpty() && SELS.isEmpty()) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        Camera cam = e.getCamera();
        Vec3 cp = cam.getPosition();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(-cp.x, -cp.y, -cp.z);
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        Matrix4f m = ps.last().pose();
        float time = (mc.level.getGameTime() % 100000L + pt) / 20F;

        V2BDraw d = new V2BDraw(buf.getBuffer(RenderType.lightning()), m, cp);
        for (StarSet s : STARS.values()) stars(d, s, pt, time, cp);
        for (Ride r : RIDES.values()) ride(d, r, pt, mc);
        for (Ping g : PINGS.values()) ping(d, g, pt, time, 1F);
        for (Scale s : SCALES.values()) scale(d, s, pt, time);
        for (Sel s : SELS.values()) select(d, s, pt, time);
        buf.endBatch(RenderType.lightning());

        if (!PINGS.isEmpty()) {
            V2BDraw see = new V2BDraw(buf.getBuffer(RenderType.guiOverlay()), m, cp);
            see.scale(0.4F);
            for (Ping g : PINGS.values()) ping(see, g, pt, time, 0.4F);
            buf.endBatch(RenderType.guiOverlay());
        }

        for (StarSet s : STARS.values()) starFlares(ps, buf, s, pt, time, cp);
        for (Ping g : PINGS.values()) {
            float a = fade(now + pt - g.born, g.life);
            GlowFx.billboard(ps, buf, GlowFx.FLARE, true, (float) g.pos.x, (float) g.pos.y + 1.1F, (float) g.pos.z, 1.5F, g.color, a, time);
            GlowFx.billboard(ps, buf, GlowFx.HALO, true, (float) g.pos.x, (float) g.pos.y + 1.1F, (float) g.pos.z, 2.6F, g.color, a * 0.5F, 0);
        }
        for (Scale s : SCALES.values()) scaleFlares(ps, buf, s, pt, time);
        for (Map.Entry<Integer, Bubble> en : BUBBLES.entrySet()) bubble(ps, buf, mc, en.getKey(), en.getValue(), pt, time);
        buf.endBatch();
        ps.popPose();
    }

    private static float fade(float age, int life) {
        return Mth.clamp(age / 6F, 0, 1) * Mth.clamp((life - age) / 20F, 0, 1);
    }

    // ------------------------------------------------------------------ star rails

    private static void stars(V2BDraw d, StarSet s, float pt, float time, Vec3 cp) {
        int n = s.pts.size();
        float outA = Mth.clamp((s.expire - now - pt) / 20F, 0, 1);
        for (int i = 0; i < n; i++) {
            Vec3 p = s.pts.get(i);
            if (p.distanceToSqr(cp) > 220 * 220) continue;
            float age = now + pt - s.born[i];
            float sc = Math.max(0, pop(age));
            float tw = 0.8F + 0.2F * Mth.sin(time * 6F + i * 1.7F);
            d.color(s.color);
            d.spark(p, 1.15F * sc * tw, 0.95F * outA);
            for (int k = 0; k < 3; k++) {
                double a = time * 2.2 + k * 2.094 + i;
                Vec3 q = p.add(Math.cos(a) * 0.75, Math.sin(a * 1.3) * 0.28, Math.sin(a) * 0.75);
                d.spark(q, 0.15F * sc, 0.85F * outA);
            }
            if (i > 0) {
                Vec3 a = s.pts.get(i - 1);
                float g = ease(age / 14F);
                Vec3 end = a.lerp(p, g);
                d.color(s.color);
                d.line(a, end, s.riding ? 0.075F : 0.05F, 0.85F * outA);
                for (int k = 0; k < 3; k++) {
                    float t = ((time * (s.riding ? 1.1F : 0.45F) + k / 3F + i * 0.2F) % 1F) * g;
                    d.color(0xFFFFFF);
                    d.spark(a.lerp(p, t), 0.2F, 0.95F * outA);
                }
            }
        }
    }

    private static void starFlares(PoseStack ps, MultiBufferSource buf, StarSet s, float pt, float time, Vec3 cp) {
        float outA = Mth.clamp((s.expire - now - pt) / 20F, 0, 1);
        for (int i = 0; i < s.pts.size(); i++) {
            Vec3 p = s.pts.get(i);
            if (p.distanceToSqr(cp) > 220 * 220) continue;
            float sc = Math.max(0, pop(now + pt - s.born[i]));
            float tw = 0.85F + 0.15F * Mth.sin(time * 5F + i * 2.1F);
            GlowFx.billboard(ps, buf, GlowFx.FLARE, true, (float) p.x, (float) p.y, (float) p.z, 1.5F * sc * tw, s.color, 0.9F * outA, time * 0.4F);
            GlowFx.billboard(ps, buf, GlowFx.HALO, true, (float) p.x, (float) p.y, (float) p.z, 2.4F * sc, s.color, 0.45F * outA, 0);
        }
    }

    private static void ride(V2BDraw d, Ride r, float pt, Minecraft mc) {
        for (int id : r.ids) {
            ArrayDeque<Vec3> trail = TRAILS.get(id);
            Entity en = mc.level.getEntity(id);
            if (trail == null || trail.size() < 2) continue;
            List<Vec3> pts = new ArrayList<>(trail);
            if (en != null) pts.add(en.getPosition(pt).add(0, en.getBbHeight() * 0.5, 0));
            int n = pts.size();
            for (int j = 1; j < n; j++) {
                float t = (float) j / (n - 1);
                Vec3 a = pts.get(j - 1), b = pts.get(j);
                d.color(r.color);
                d.ribbon(a, b, 0.42F * t, 0.5F * t, false);
                d.color(0xFFFFFF);
                d.ribbon(a, b, 0.11F * t, 0.9F * t, true);
                if (j % 2 == 0) {
                    double h = id * 12.9898 + j * 78.233;
                    Vec3 off = new Vec3(Math.sin(h) * 0.55, Math.sin(h * 1.7) * 0.45, Math.cos(h * 1.3) * 0.55).scale(1 - t * 0.5);
                    d.color(r.color);
                    d.spark(a.add(off), 0.16F * (0.3F + t), 0.85F * t);
                }
            }
        }
    }

    // ------------------------------------------------------------------ pings

    private static void ping(V2BDraw d, Ping g, float pt, float time, float k) {
        float age = now + pt - g.born;
        float a = fade(age, g.life);
        if (a <= 0.01F) return;
        Vec3 pos = g.pos;
        d.color(g.color);
        float grow = ease(age / 10F);
        d.line(pos, pos.add(0, 70 * grow, 0), 0.07F, a * 0.9F);
        d.ribbon(pos, pos.add(0, 70 * grow, 0), 0.5F, a * 0.08F, false);
        d.diamond(pos.add(0, 1.1 + 0.15 * Mth.sin(time * 2.5F), 0), 0.38F, 0.22F, time * 2.0, a);
        if (k >= 1F) {
            d.ring(pos.add(0, 0.05, 0), 0.9 + 0.2 * Mth.sin(time * 3F) + (age < 10 ? (1 - age / 10F) * 3 : 0), 0.045F, a, time);
            float f = (time * 1.1F) % 1F;
            d.ring(pos.add(0, 0.06, 0), 0.4 + f * 2.8, 0.035F, a * (1 - f), -time);
            d.color(0xFFFFFF);
            d.spark(pos.add(0, 1.1 + 0.15 * Mth.sin(time * 2.5F), 0), 0.5F, a * 0.9F);
        }
    }

    // ------------------------------------------------------------------ thought bubbles

    private static void bubble(PoseStack ps, MultiBufferSource buf, Minecraft mc, int entityId, Bubble b, float pt, float time) {
        Entity en = mc.level.getEntity(entityId);
        if (en == null) return;
        if (en == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson()) return;
        float age = now + pt - b.born;
        float sc = Math.max(0, pop(age)) * Mth.clamp((b.life - age) / 8F, 0, 1);
        if (sc <= 0.01F) return;
        Vec3 pos = en.getPosition(pt).add(0, en.getBbHeight() + 0.8 + Mth.sin(time * 3F) * 0.04, 0);
        int glow = BUBBLE_GLOW[b.type];
        GlowFx.billboard(ps, buf, GlowFx.HALO, true, (float) pos.x, (float) pos.y + 0.1F, (float) pos.z, 0.75F * sc, glow, 0.55F, 0);
        ps.pushPose();
        ps.translate(pos.x, pos.y, pos.z);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        float s = 0.42F * sc;
        ps.scale(s, s, s);
        Matrix4f mm = ps.last().pose();
        VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(BUBBLE_TEX));
        vert(vc, mm, -1, -1, 0, 1); vert(vc, mm, 1, -1, 1, 1); vert(vc, mm, 1, 1, 1, 0); vert(vc, mm, -1, 1, 0, 0);
        ps.translate(0, 0.22, 0.04);
        ps.scale(0.78F, 0.78F, 0.02F);
        mc.getItemRenderer().renderStatic(BUBBLE_ICONS[b.type], ItemDisplayContext.GUI, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ps, buf, mc.level, 0);
        ps.popPose();
    }

    private static void vert(VertexConsumer vc, Matrix4f m, float x, float y, float u, float v) {
        vc.addVertex(m, x, y, 0).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0, 0, 1);
    }

    // ------------------------------------------------------------------ cosmic scale

    private static double[] curBox(Scale s, float ease) {
        double[] c = new double[6];
        for (int i = 0; i < 6; i++) c[i] = Mth.lerp(ease, s.old[i], s.now[i]);
        return c;
    }

    private static void scale(V2BDraw d, Scale s, float pt, float time) {
        float age = now + pt - s.born;
        float a = Mth.clamp(age / 6F, 0, 1) * Mth.clamp((s.life - age) / 20F, 0, 1);
        if (a <= 0.01F) return;
        float pr = Mth.clamp((age - 6F) / Math.max(1F, s.life - 36F), 0, 1);
        float ez = ease(pr);
        double[] c = curBox(s, ez);
        double x0 = c[0], y0 = c[1], z0 = c[2], x1 = c[3], y1 = c[4], z1 = c[5];
        d.color(s.color);
        d.box(x0, y0, z0, x1, y1, z1, 0.06F, a * 0.9F);
        double ys = Mth.lerp(pr, Math.min(s.old[1], s.now[1]), Math.max(s.old[4], s.now[4]));
        double ysc = Mth.clamp(ys, y0, y1);
        float pulse = 0.75F + 0.25F * Mth.sin(time * 9F);
        d.color(0xFFFFFF);
        Vec3 q0 = new Vec3(x0, ysc, z0), q1 = new Vec3(x1, ysc, z0), q2 = new Vec3(x1, ysc, z1), q3 = new Vec3(x0, ysc, z1);
        d.line(q0, q1, 0.09F, a * pulse); d.line(q1, q2, 0.09F, a * pulse); d.line(q2, q3, 0.09F, a * pulse); d.line(q3, q0, 0.09F, a * pulse);
        d.color(s.color);
        d.quad(q0, q1, q2, q3, a * 0.2F, false);
        // faint volume
        d.quad(new Vec3(x0, y0, z0), new Vec3(x1, y0, z0), new Vec3(x1, y1, z0), new Vec3(x0, y1, z0), a * 0.06F, false);
        d.quad(new Vec3(x0, y0, z1), new Vec3(x1, y0, z1), new Vec3(x1, y1, z1), new Vec3(x0, y1, z1), a * 0.06F, false);
        d.quad(new Vec3(x0, y0, z0), new Vec3(x0, y0, z1), new Vec3(x0, y1, z1), new Vec3(x0, y1, z0), a * 0.06F, false);
        d.quad(new Vec3(x1, y0, z0), new Vec3(x1, y0, z1), new Vec3(x1, y1, z1), new Vec3(x1, y1, z0), a * 0.06F, false);
        warp(d, x0, z0, x1, z0, 0, -1, y0, y1, ysc, time, a);
        warp(d, x1, z0, x1, z1, 1, 0, y0, y1, ysc, time, a);
        warp(d, x1, z1, x0, z1, 0, 1, y0, y1, ysc, time, a);
        warp(d, x0, z1, x0, z0, -1, 0, y0, y1, ysc, time, a);
        double cx = (x0 + x1) / 2, cz = (z0 + z1) / 2, rad = Math.max(x1 - x0, z1 - z0) * 0.72;
        d.color(s.color);
        d.ring(new Vec3(cx, ysc, cz), rad * (1 + 0.04 * Mth.sin(time * 6F)), 0.07F, a * 0.8F, time);
        float f = (time * 1.4F) % 1F;
        d.ring(new Vec3(cx, ysc, cz), rad * (0.8 + f * 0.9), 0.05F, a * (1 - f) * 0.7F, -time);
        for (int i = 0; i < 4; i++) {
            double[] cc = {i == 0 || i == 3 ? x0 : x1, i < 2 ? z0 : z1};
            d.color(s.color);
            d.line(new Vec3(cc[0], y0, cc[1]), new Vec3(cc[0], y1 + 5 + 3 * Mth.sin(time * 4F + i), cc[1]), 0.06F, a * 0.7F);
        }
    }

    /** One face of the box as vertical lines bent by a travelling wave: space being stretched. */
    private static void warp(V2BDraw d, double ax, double az, double bx, double bz, double nx, double nz, double y0, double y1, double ys, float time, float a) {
        double len = Math.hypot(bx - ax, bz - az);
        if (len < 0.5 || y1 - y0 < 0.5) return;
        int lines = Mth.clamp((int) (len / 1.5), 2, 14);
        int segs = Mth.clamp((int) ((y1 - y0) / 1.2), 2, 28);
        for (int i = 0; i <= lines; i++) {
            double t = (double) i / lines;
            double px = Mth.lerp(t, ax, bx), pz = Mth.lerp(t, az, bz);
            Vec3 prev = null;
            for (int j = 0; j <= segs; j++) {
                double y = Mth.lerp((double) j / segs, y0, y1);
                double near = Math.exp(-Math.pow((y - ys) / 3.0, 2));
                double w = Math.sin(y * 0.8 - time * 6 + i * 1.3) * (0.1 + 0.45 * near);
                Vec3 v = new Vec3(px + nx * w, y, pz + nz * w);
                if (prev != null) d.line(prev, v, 0.022F, a * (0.25F + 0.55F * (float) near));
                prev = v;
            }
        }
    }

    private static void scaleFlares(PoseStack ps, MultiBufferSource buf, Scale s, float pt, float time) {
        float age = now + pt - s.born;
        float a = Mth.clamp(age / 6F, 0, 1) * Mth.clamp((s.life - age) / 20F, 0, 1);
        if (a <= 0.01F) return;
        float pr = Mth.clamp((age - 6F) / Math.max(1F, s.life - 36F), 0, 1);
        double[] c = curBox(s, ease(pr));
        double ys = Mth.clamp(Mth.lerp(pr, Math.min(s.old[1], s.now[1]), Math.max(s.old[4], s.now[4])), c[1], c[4]);
        for (int i = 0; i < 4; i++) {
            float x = (float) (i == 0 || i == 3 ? c[0] : c[3]), z = (float) (i < 2 ? c[2] : c[5]);
            GlowFx.billboard(ps, buf, GlowFx.FLARE, true, x, (float) ys, z, 2.6F, s.color, a * 0.85F, time * 0.6F + i);
            GlowFx.billboard(ps, buf, GlowFx.HALO, true, x, (float) c[1], z, 1.8F, s.color, a * 0.5F, 0);
        }
        float h = (float) ((c[3] - c[0] + c[5] - c[2]) * 0.25);
        ps.pushPose();
        ps.translate((c[0] + c[3]) / 2, c[1] + 0.05, (c[2] + c[5]) / 2);
        GlowFx.groundQuad(ps, buf, GlowFx.RUNE_RING, h * 1.6F, s.color, a * 0.8F, time * 0.5F);
        ps.popPose();
    }

    private static void select(V2BDraw d, Sel s, float pt, float time) {
        float age = now + pt - s.born;
        float a = Mth.clamp(age / 5F, 0, 1) * Mth.clamp((s.life - age) / 20F, 0, 1);
        if (a <= 0.01F) return;
        float pulse = 0.6F + 0.4F * Mth.sin(time * 6F);
        double[] b = s.box;
        d.color(s.color);
        d.box(b[0] - 0.02, b[1] - 0.02, b[2] - 0.02, b[3] + 0.02, b[4] + 0.02, b[5] + 0.02, 0.035F, a * pulse);
        d.spark(new Vec3((b[0] + b[3]) / 2, b[4] + 0.5, (b[2] + b[5]) / 2), 0.4F, a * pulse);
    }

    private V2BClient() {}
}
