package com.infinitystones.tensura.skill.ability.extra.v2a.client;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.client.render.GlowFx;
import com.infinitystones.tensura.skill.ability.extra.v2a.V2aAnim;
import com.infinitystones.tensura.skill.ability.extra.v2a.V2aNet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Draws the v2a effects: the giant forearm and fist of the Sky Fist, the pair of stone hands of the Sculptor, the golden
 * gauntlet of the Creator (models built in code, posed every frame) and the sky shows of the Wish (rainbow, starry
 * night). Every instance is keyed by the server's id and driven by its messages; the animation clocks are local ticks.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class V2aClient {
    private static final class Inst {
        V2aNet.Msg m;
        int age, sAge, end;
        double yTop;
        Vector3f[] stars; float[] starSize, starPhase;
        Inst(V2aNet.Msg m) { this.m = m; }
    }

    private static final Map<Integer, Inst> ALL = new HashMap<>();

    public static void accept(V2aNet.Msg m) {
        if (m.kind() == V2aNet.KILL) { ALL.remove(m.id()); return; }
        Inst i = ALL.get(m.id());
        if (i == null) { i = new Inst(m); ALL.put(m.id(), i); i.yTop = m.y0(); }
        i.m = m;
        i.sAge = i.age;
        i.end = i.age + m.life();
        if (m.kind() == V2aNet.GAUNTLET && m.state() == V2aAnim.G_DESC) i.yTop = m.y0();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { ALL.clear(); }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (Minecraft.getInstance().isPaused()) return;
        ALL.values().removeIf(i -> ++i.age > i.end);
    }

    // ================================================================== world

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ALL.isEmpty()) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        Camera cam = e.getCamera();
        Vec3 cp = cam.getPosition();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(-cp.x, -cp.y, -cp.z);
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();

        for (Inst i : ALL.values()) {
            switch (i.m.kind()) {
                case V2aNet.ARM -> arm(ps, buf, i, pt, 0, null);
                case V2aNet.HANDS -> hands(ps, buf, i, pt, 0, null);
                case V2aNet.GAUNTLET -> gauntlet(ps, buf, i, pt, 0, null);
                default -> { }
            }
        }
        buf.endBatch();
        for (Inst i : ALL.values()) {
            switch (i.m.kind()) {
                case V2aNet.ARM -> arm(ps, buf, i, pt, 1, null);
                case V2aNet.GAUNTLET -> gauntlet(ps, buf, i, pt, 1, null);
                default -> { }
            }
        }
        buf.endBatch();

        Glow gl = new Glow(buf.getBuffer(RenderType.lightning()), ps.last().pose(), cp);
        for (Inst i : ALL.values()) {
            switch (i.m.kind()) {
                case V2aNet.ARM -> arm(ps, buf, i, pt, 2, gl);
                case V2aNet.HANDS -> hands(ps, buf, i, pt, 2, gl);
                case V2aNet.GAUNTLET -> gauntlet(ps, buf, i, pt, 2, gl);
                case V2aNet.RAINBOW -> rainbow(ps, buf, i, pt, gl);
                case V2aNet.STARS -> stars(i, pt, gl, cam, cp);
                default -> { }
            }
        }
        buf.endBatch(RenderType.lightning());
        buf.endBatch();
        ps.popPose();
    }

    private static int light(Minecraft mc, double x, double y, double z) {
        int real = LevelRenderer.getLightColor(mc.level, BlockPos.containing(x, y, z));
        return LightTexture.pack(Math.max(LightTexture.block(real), 6), Math.max(LightTexture.sky(real), 10));
    }

    private static float pulse(float t, float speed) { return 0.5F + 0.5F * Mth.sin(t * speed); }

    // ------------------------------------------------------------------ Sky Fist

    /** pass 0 = solid model, 1 = dark patch of sky, 2 = glow. */
    private static void arm(PoseStack ps, MultiBufferSource buf, Inst i, float pt, int pass, Glow gl) {
        V2aNet.Msg m = i.m;
        Minecraft mc = Minecraft.getInstance();
        float t = i.age + pt, s = m.scale();
        double yC = m.y0(), gY = m.y1();
        boolean palm = m.state() == 1;
        float dark = Mth.clamp(t / 12F, 0, 1) * Mth.clamp((V2aAnim.FIST_LIFE - t) / 12F, 0, 1);
        double yb;
        if (t < V2aAnim.FIST_START) yb = yC;
        else if (t < V2aAnim.FIST_IMPACT) yb = V2aAnim.lerp(yC, gY, V2aAnim.easeIn((t - V2aAnim.FIST_START) / (V2aAnim.FIST_IMPACT - V2aAnim.FIST_START)));
        else if (t < V2aAnim.FIST_STAY) yb = gY;
        else yb = V2aAnim.lerp(gY, yC, V2aAnim.ease((t - V2aAnim.FIST_STAY) / (V2aAnim.FIST_END - V2aAnim.FIST_STAY)));
        double originY = palm ? yb : yb + 0.25 * s;
        double wristY = palm ? 1.3 : 2.9;
        float armLen = (float) Math.max(0, (yC - (originY + wristY * s)) / s);
        boolean show = t >= V2aAnim.FIST_START && originY < yC - 0.05;

        if (pass == 1) {
            VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(Hands.RUNE_STONE.gem()));
            float a = 0.3F * dark;
            float r = 4.2F * s;
            Geo.discDown(ps, vc, m.x(), yC - 0.4, m.z(), r, a);
            Geo.discDown(ps, vc, m.x(), yC - 0.42, m.z(), r * 0.78F, a);
            Geo.discDown(ps, vc, m.x(), yC - 0.44, m.z(), r * 0.52F, a);
            return;
        }
        if (pass == 0) {
            if (!show) return;
            int light = light(mc, m.x(), originY + 3, m.z());
            float curl = palm ? 0F : V2aAnim.ease((t - V2aAnim.FIST_CURL) / (float) (V2aAnim.FIST_IMPACT - V2aAnim.FIST_CURL));
            ps.pushPose();
            ps.translate(m.x(), originY, m.z());
            ps.mulPose(Axis.YP.rotation(m.yaw()));
            ps.scale(s, s, s);
            Hands.solid(ps, buf, Hands.SKY, light, curl, palm ? 0.6F : 0F, armLen, palm, 0, pulse(t, 0.4F), t, 0F);
            ps.popPose();
            return;
        }
        // glow
        if (t >= V2aAnim.FIST_START - 4 && t < V2aAnim.FIST_END) {
            if (t < V2aAnim.FIST_IMPACT && originY < yC - 0.05) {
                gl.color(0xA43CE6);
                Vec3 top = new Vec3(m.x(), yC, m.z()), bot = new Vec3(m.x(), originY + 0.5 * s, m.z());
                float al = Mth.clamp((t - V2aAnim.FIST_START) / 6F, 0, 1);
                gl.ribbon(top, bot, 0.9F * s, 0.1F * al, 0);
                gl.ribbon(top, bot, 0.5F * s, 0.2F * al, 0.2F);
                gl.ribbon(top, bot, 0.18F * s, 0.45F * al, 0.7F);
            }
            if (show) {
                int litFlash = t >= V2aAnim.FIST_IMPACT && t < V2aAnim.FIST_IMPACT + 12 ? 1 : 0;
                float flash = litFlash == 0 ? 0 : 1 - (t - V2aAnim.FIST_IMPACT) / 12F;
                ps.pushPose();
                ps.translate(m.x(), originY, m.z());
                ps.mulPose(Axis.YP.rotation(m.yaw()));
                ps.scale(s, s, s);
                Hands.glow(ps, buf, Hands.SKY, 0, pulse(t, 0.4F), flash, palm, armLen,
                        palm ? 0F : V2aAnim.ease((t - V2aAnim.FIST_CURL) / (float) (V2aAnim.FIST_IMPACT - V2aAnim.FIST_CURL)), palm ? 0.6F : 0F, t);
                ps.popPose();
            }
        }
        if (t >= V2aAnim.FIST_IMPACT && t < V2aAnim.FIST_IMPACT + 14) {
            float k = (t - V2aAnim.FIST_IMPACT) / 14F;
            GlowFx.billboard(ps, buf, GlowFx.FLARE, true, (float) m.x(), (float) (gY + 0.8), (float) m.z(), 5.5F * s * (0.5F + k), 0xE6B8FF, 1F - k, t);
            GlowFx.billboard(ps, buf, GlowFx.HALO, true, (float) m.x(), (float) (gY + 0.3), (float) m.z(), 8F * s * (0.4F + k), 0xA43CE6, 0.8F * (1 - k), 0);
        }
    }

    // ------------------------------------------------------------------ Sculptor's Hands

    private static void hands(PoseStack ps, MultiBufferSource buf, Inst i, float pt, int pass, Glow gl) {
        if (pass == 1) return;
        V2aNet.Msg m = i.m;
        Minecraft mc = Minecraft.getInstance();
        float t = i.age + pt, s = m.scale(), R = m.extra();
        boolean dig = m.state() == 1;
        float rise = V2aAnim.easeOut(t / V2aAnim.H_RISE);
        float sink = V2aAnim.ease((i.end - t) / (float) V2aAnim.H_SINK);
        float vis = Math.min(rise, sink);
        if (vis <= 0.01F) return;
        float b = Mth.clamp((t - V2aAnim.H_RISE) / Math.max(1F, m.dur()), 0, 1);
        double delta = m.y1();
        float dist = dig ? R * (0.3F + 0.5F * b) : R * (0.62F - 0.25F * b);
        float curl = dig ? 0.12F : 0.3F + 0.15F * Mth.sin(t * 0.3F);
        double originY = m.y() + (-5.5 + 7.2 * vis) * s + (dig ? delta * b * 0.8 : delta * b * 0.7);
        float armLen = 8F;
        float ax = Mth.cos(m.yaw()), az = Mth.sin(m.yaw());
        for (int side = -1; side <= 1; side += 2) {
            double hx = m.x() + ax * dist * side, hz = m.z() + az * dist * side;
            // palm towards the centre
            double ux = -ax * side, uz = -az * side;
            float a = (float) Math.atan2(-ux, -uz);
            ps.pushPose();
            ps.translate(hx, originY, hz);
            ps.mulPose(Axis.YP.rotation(a));
            ps.mulPose(Axis.XP.rotation((float) Math.PI));
            ps.scale(s, s, s);
            if (pass == 0) {
                int light = light(mc, hx, originY + 2 * s, hz);
                Hands.solid(ps, buf, Hands.SCULPTOR, light, curl, 0.5F, armLen, false, 0, pulse(t, 0.35F + 0.05F * side), t + side * 20F, 0F);
            } else {
                Hands.glow(ps, buf, Hands.SCULPTOR, 0, pulse(t, 0.35F + 0.05F * side), 0, false, armLen, curl, 0.5F, t + side * 20F);
            }
            ps.popPose();
        }
        if (pass == 2 && t > V2aAnim.H_RISE && b < 1) {
            Vec3 c = new Vec3(m.x(), m.y() + (dig ? 0.5 : delta * b + 0.5), m.z());
            gl.color(0xC77DFF);
            for (int side = -1; side <= 1; side += 2) {
                Vec3 h = new Vec3(m.x() + ax * dist * side, originY - 1.2 * s, m.z() + az * dist * side);
                gl.ribbon(h, c, 0.35F, 0.12F * vis, 0.3F);
            }
        }
    }

    // ------------------------------------------------------------------ Creator's gauntlet

    private static void gauntlet(PoseStack ps, MultiBufferSource buf, Inst i, float pt, int pass, Glow gl) {
        V2aNet.Msg m = i.m;
        Minecraft mc = Minecraft.getInstance();
        float t = i.age + pt, s = m.scale();
        float tt = t - i.sAge;
        int st = m.state();
        double yb = V2aAnim.lerp(m.y0(), m.y1(), V2aAnim.ease(tt / Math.max(1F, m.dur())));
        double yC = i.yTop;
        float dark = Mth.clamp(t / 14F, 0, 1) * Mth.clamp((i.end - t) / 20F, 0, 1);
        if (pass == 1) {
            VertexConsumer vc = buf.getBuffer(RenderType.entityTranslucent(Hands.RUNE_STONE.gem()));
            float a = 0.3F * dark, r = 5.2F * s;
            Geo.discDown(ps, vc, m.x(), yC - 0.4, m.z(), r, a);
            Geo.discDown(ps, vc, m.x(), yC - 0.42, m.z(), r * 0.78F, a);
            Geo.discDown(ps, vc, m.x(), yC - 0.44, m.z(), r * 0.52F, a);
            return;
        }
        if (yb >= yC - 0.05) return;
        float curl = switch (st) {
            case V2aAnim.G_CLOSE -> V2aAnim.ease(tt / V2aAnim.G_CLOSE_TICKS);
            case V2aAnim.G_RISE, V2aAnim.G_HOLD, V2aAnim.G_SQUEEZE -> 1F;
            case V2aAnim.G_RELEASE -> 1F - V2aAnim.ease(tt / 16F);
            default -> 0F;
        };
        int lit = st == V2aAnim.G_DESC ? 0 : st == V2aAnim.G_OPEN ? Math.min(6, (int) (tt / 3F)) : 6;
        int mask = (1 << lit) - 1;
        float flash = st == V2aAnim.G_SQUEEZE ? Math.max(0, 1 - (tt % 10) / 6F) : 0;
        double sx = 0, sz = 0;
        if (st == V2aAnim.G_SQUEEZE) {
            sx = Math.sin(t * 2.9) * 0.05 * s;
            sz = Math.cos(t * 3.7) * 0.05 * s;
        }
        float armLen = (float) Math.max(0, (yC - (yb + 2.9 * s)) / s);
        float pulse = pulse(t, 0.45F);
        ps.pushPose();
        ps.translate(m.x() + sx, yb, m.z() + sz);
        ps.mulPose(Axis.YP.rotation(m.yaw()));
        ps.scale(s, s, s);
        if (pass == 0) {
            int light = light(mc, m.x(), yb + 2, m.z());
            Hands.solid(ps, buf, Hands.GOLD, light, curl, st == V2aAnim.G_OPEN ? 0.7F : 0.2F, armLen, false, mask, pulse, t, flash);
        } else {
            Hands.glow(ps, buf, Hands.GOLD, mask, pulse, flash, false, armLen, curl, st == V2aAnim.G_OPEN ? 0.7F : 0.2F, t);
        }
        ps.popPose();
        if (pass == 2 && st == V2aAnim.G_DESC) {
            gl.color(0xFFC64A);
            Vec3 top = new Vec3(m.x(), yC, m.z()), bot = new Vec3(m.x(), yb, m.z());
            gl.ribbon(top, bot, 1.2F * s, 0.07F, 0);
            gl.ribbon(top, bot, 0.5F * s, 0.16F, 0.4F);
        }
    }

    // ------------------------------------------------------------------ Wish: rainbow

    private static final int[] RAINBOW = {0xFF2A2A, 0xFF8A1A, 0xFFE82A, 0x3CE04A, 0x2A9BFF, 0x4A36D8, 0xA43CE6};

    private static void rainbow(PoseStack ps, MultiBufferSource buf, Inst i, float pt, Glow gl) {
        V2aNet.Msg m = i.m;
        float t = i.age + pt;
        float fade = Mth.clamp(t / 20F, 0, 1) * Mth.clamp((i.end - t) / 40F, 0, 1);
        if (fade <= 0.01F) return;
        float reveal = V2aAnim.easeOut(t / 70F) * (float) Math.PI;
        Vec3 c = new Vec3(m.x(), m.y() - 4, m.z());
        Vec3 side = new Vec3(Mth.cos(m.yaw()), 0, -Mth.sin(m.yaw()));
        double base = 46, w = 2.6;
        int seg = 56;
        for (int b = 0; b < 7; b++) {
            gl.color(RAINBOW[b]);
            double r0 = base + b * w, r1 = r0 + w;
            for (int j = 0; j < seg; j++) {
                double a0 = reveal * j / seg, a1 = reveal * (j + 1) / seg;
                gl.quad(pt(c, side, r0, a0), pt(c, side, r1, a0), pt(c, side, r1, a1), pt(c, side, r0, a1), 0.42F * fade);
            }
        }
        gl.color(0xFFFFFF);
        double rm = base + 3.5 * w, wide = 11;
        for (int j = 0; j < seg; j++) {
            double a0 = reveal * j / seg, a1 = reveal * (j + 1) / seg;
            gl.quad(pt(c, side, rm - wide, a0), pt(c, side, rm + wide, a0), pt(c, side, rm + wide, a1), pt(c, side, rm - wide, a1), 0.05F * fade);
        }
        for (int k = 0; k < 14; k++) {
            double a = ((t * 0.006 + k / 14.0) % 1.0) * reveal;
            double r = base + (k % 7 + 0.5) * w;
            Vec3 p = pt(c, side, r, a);
            GlowFx.billboard(ps, buf, GlowFx.FLARE, true, (float) p.x, (float) p.y, (float) p.z, 4F + (k % 3), 0xFFFFFF, 0.55F * fade * (0.6F + 0.4F * Mth.sin(t * 0.3F + k)), t * 0.1F + k);
        }
    }

    private static Vec3 pt(Vec3 c, Vec3 side, double r, double a) {
        return new Vec3(c.x + side.x * Math.cos(a) * r, c.y + Math.sin(a) * r, c.z + side.z * Math.cos(a) * r);
    }

    // ------------------------------------------------------------------ Wish: starry night

    private static void stars(Inst i, float pt, Glow gl, Camera cam, Vec3 cp) {
        V2aNet.Msg m = i.m;
        float t = i.age + pt;
        double dist = cp.distanceTo(new Vec3(m.x(), m.y(), m.z()));
        float fade = Mth.clamp(t / 40F, 0, 1) * Mth.clamp((i.end - t) / 50F, 0, 1) * Mth.clamp((240F - (float) dist) / 60F, 0, 1);
        if (fade <= 0.01F) return;
        if (i.stars == null) {
            Random r = new Random(i.m.id() * 7919L);
            int n = 520;
            i.stars = new Vector3f[n]; i.starSize = new float[n]; i.starPhase = new float[n];
            for (int k = 0; k < n; k++) {
                double yaw = r.nextDouble() * Math.PI * 2, el = Math.asin(r.nextDouble() * 1.02 - 0.02);
                i.stars[k] = new Vector3f((float) (Math.cos(el) * Math.cos(yaw)), (float) Math.sin(el), (float) (Math.cos(el) * Math.sin(yaw)));
                i.starSize[k] = 0.25F + r.nextFloat() * r.nextFloat() * 0.9F;
                i.starPhase[k] = r.nextFloat() * 6.28F;
            }
        }
        Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        double R = 64;
        for (int k = 0; k < i.stars.length; k++) {
            Vector3f d = i.stars[k];
            float tw = 0.55F + 0.45F * Mth.sin(t * 0.17F + i.starPhase[k]);
            int col = k % 11 == 0 ? 0xBFD8FF : k % 7 == 0 ? 0xFFE6B0 : 0xFFFFFF;
            gl.color(col);
            Vec3 p = new Vec3(cp.x + d.x * R, cp.y + d.y * R, cp.z + d.z * R);
            float sz = i.starSize[k] * 1.5F;
            gl.starQuad(p, left, up, sz, fade * tw);
        }
        // shooting stars: a new one about every second, crossing a quarter of the sky in a bit more than a second
        int first = Math.max(0, (int) ((t - 30) / 14));
        int last = (int) (t / 14);
        for (int k = first; k <= last; k++) {
            Random r = new Random(i.m.id() * 31L + k * 1013L);
            float ts = k * 14 + r.nextInt(10);
            float u = (t - ts) / 30F;
            if (u < 0 || u > 1) continue;
            double yaw = r.nextDouble() * Math.PI * 2, el = Math.toRadians(25 + r.nextDouble() * 45);
            double dir = r.nextDouble() * Math.PI * 2;
            Vec3 o = new Vec3(Math.cos(el) * Math.cos(yaw), Math.sin(el), Math.cos(el) * Math.sin(yaw));
            Vec3 tan = o.cross(new Vec3(0, 1, 0));
            if (tan.lengthSqr() < 1e-4) tan = new Vec3(1, 0, 0);
            tan = tan.normalize();
            Vec3 tan2 = o.cross(tan);
            Vec3 dv = tan.scale(Math.cos(dir)).add(tan2.scale(Math.sin(dir)));
            gl.color(0xFFFFFF);
            Vec3 prev = null;
            for (int q = 0; q <= 9; q++) {
                double a = (u - q * 0.018) * 0.7;
                if (a < 0) break;
                Vec3 dd = o.scale(Math.cos(a)).add(dv.scale(Math.sin(a))).normalize();
                Vec3 p = cp.add(dd.scale(R * 0.96));
                if (prev != null) {
                    float al = fade * (1 - q / 10F) * Mth.clamp((1 - u) * 3, 0, 1);
                    gl.ribbon(prev, p, 0.55F * (1 - q / 11F), al, 0.8F);
                }
                prev = p;
            }
        }
    }

    // ================================================================== screen

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || ALL.isEmpty()) return;
        GuiGraphics g = e.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        Vec3 pos = mc.player.position();
        float night = 0, shade = 0;
        for (Inst i : ALL.values()) {
            V2aNet.Msg m = i.m;
            float t = i.age;
            double d = Math.hypot(pos.x - m.x(), pos.z - m.z());
            switch (m.kind()) {
                case V2aNet.ARM -> {
                    float k = Mth.clamp(t / 12F, 0, 1) * Mth.clamp((V2aAnim.FIST_LIFE - t) / 12F, 0, 1);
                    shade = Math.max(shade, k * Mth.clamp(1F - (float) d / (4.5F * m.scale() + 16F), 0, 1));
                }
                case V2aNet.GAUNTLET -> {
                    float k = Mth.clamp(t / 14F, 0, 1) * Mth.clamp((i.end - t) / 20F, 0, 1);
                    shade = Math.max(shade, k * Mth.clamp(1F - (float) d / (5.5F * m.scale() + 24F), 0, 1));
                }
                case V2aNet.STARS -> {
                    float k = Mth.clamp(t / 40F, 0, 1) * Mth.clamp((i.end - t) / 50F, 0, 1) * Mth.clamp((240F - (float) d) / 60F, 0, 1);
                    night = Math.max(night, k);
                }
                default -> { }
            }
        }
        if (shade > 0.01F) g.fill(0, 0, w, h, ((int) (95 * shade) << 24));
        if (night > 0.01F) g.fill(0, 0, w, h, ((int) (70 * night) << 24) | 0x040A2C);
    }

    // ================================================================== additive primitives

    private static final class Glow {
        final VertexConsumer vc; final Matrix4f m; final Vec3 cam;
        float r, g, b;
        Glow(VertexConsumer vc, Matrix4f m, Vec3 cam) { this.vc = vc; this.m = m; this.cam = cam; }

        void color(int c) { r = ((c >> 16) & 255) / 255F; g = ((c >> 8) & 255) / 255F; b = (c & 255) / 255F; }

        private void v(Vec3 p, float al, float wk) {
            vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r + (1 - r) * wk, g + (1 - g) * wk, b + (1 - b) * wk, Mth.clamp(al, 0, 1));
        }

        void quad(Vec3 a, Vec3 b2, Vec3 c, Vec3 d, float al) {
            if (al <= 0.003F) return;
            v(a, al, 0); v(b2, al, 0); v(c, al, 0); v(d, al, 0);
            v(d, al, 0); v(c, al, 0); v(b2, al, 0); v(a, al, 0);
        }

        void ribbon(Vec3 p0, Vec3 p1, float half, float al, float wk) {
            if (al <= 0.003F) return;
            Vec3 seg = p1.subtract(p0);
            Vec3 toCam = cam.subtract(p0.add(seg.scale(0.5)));
            Vec3 n = seg.cross(toCam);
            if (n.lengthSqr() < 1e-10) return;
            n = n.normalize().scale(half);
            Vec3 a = p0.add(n), b2 = p1.add(n), c = p1.subtract(n), d = p0.subtract(n);
            v(a, al, wk); v(b2, al, wk); v(c, al, wk); v(d, al, wk);
            v(d, al, wk); v(c, al, wk); v(b2, al, wk); v(a, al, wk);
        }

        void starQuad(Vec3 p, Vector3f left, Vector3f up, float s, float al) {
            if (al <= 0.01F) return;
            Vec3 l = new Vec3(left.x * s, left.y * s, left.z * s), u = new Vec3(up.x * s, up.y * s, up.z * s);
            v(p.add(l).add(u), al, 0.6F); v(p.subtract(l).add(u), al, 0.6F); v(p.subtract(l).subtract(u), al, 0.6F); v(p.add(l).subtract(u), al, 0.6F);
        }
    }

    private V2aClient() {}
}
