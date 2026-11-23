package com.infinitystones.tensura.skill.ability.extra.reality.client;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.extra.reality.ChestNet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The holographic Creation Chest, client side: a translucent red chest model with glowing edges that unfolds, opens its
 * lid, receives the falling item(s) from the beam above and folds away, over a layered rune circle. Driven by the
 * server's {@code ChestNet.Holo} script; everyone nearby sees it.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class ChestHolo {
    private static final int RED = 0xE22B3B, HOT = 0xFF6A78, WHITE = 0xFFE8EA;
    private static final float HINGE_Y = 9 / 16F, HINGE_Z = 1 / 16F;
    // built lazily: touching Sheets during mod loading breaks other mods' registry-based materials (signs, chests, beds)
    private static Material chestTex;

    private static Material chestTex() {
        if (chestTex == null) chestTex = new Material(Sheets.CHEST_SHEET, ResourceLocation.withDefaultNamespace("entity/chest/normal"));
        return chestTex;
    }

    private record Drop(ItemStack stack, int count, float t0, float dur) {}

    private static final class H {
        int id;
        Vec3 pos;
        float yaw, born;
        Level level;
        float lidFrom, lidTo, lidT0, lidDur;
        float foldT0 = -1, foldDur = 12;
        final List<Drop> drops = new ArrayList<>();
    }

    private static final Map<Integer, H> HOLOS = new HashMap<>();
    private static EntityModelSet bakedFor;
    private static ModelPart bottom, lid, lock;

    public static boolean any() { return !HOLOS.isEmpty(); }

    public static void accept(ChestNet.Holo m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float now = mc.level.getGameTime();
        if (m.op() == ChestNet.SPAWN) {
            H h = new H();
            h.id = m.id();
            h.pos = new Vec3(m.x(), m.y(), m.z());
            h.yaw = m.yaw();
            h.born = now;
            h.level = mc.level;
            HOLOS.put(h.id, h);
            return;
        }
        H h = HOLOS.get(m.id());
        if (h == null) return;
        switch (m.op()) {
            case ChestNet.OPEN -> setLid(h, now, 1F, Math.max(1, m.ticks()));
            case ChestNet.CLOSE -> setLid(h, now, 0F, Math.max(1, m.ticks()));
            case ChestNet.ITEM -> h.drops.add(new Drop(m.stack(), Math.max(1, m.count()), now, Math.max(8, m.ticks())));
            case ChestNet.FOLD -> { h.foldT0 = now; h.foldDur = Math.max(2, m.ticks()); }
            default -> { }
        }
    }

    private static void setLid(H h, float now, float to, float dur) {
        h.lidFrom = lid(h, now);
        h.lidTo = to;
        h.lidT0 = now;
        h.lidDur = dur;
    }

    private static float lid(H h, float now) {
        if (h.lidDur <= 0) return h.lidTo;
        float u = Mth.clamp((now - h.lidT0) / h.lidDur, 0, 1);
        float e;
        if (h.lidTo >= h.lidFrom) { float c = 1.70158F, x = u - 1; e = 1 + (c + 1) * x * x * x + c * x * x; }
        else e = u * u;
        return Mth.lerp(e, h.lidFrom, h.lidTo);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { HOLOS.clear(); }

    // ------------------------------------------------------------------ render

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || HOLOS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        float now = mc.level.getGameTime() + pt;
        Vec3 cp = e.getCamera().getPosition();
        for (Iterator<H> it = HOLOS.values().iterator(); it.hasNext(); ) {
            H h = it.next();
            if (h.level != mc.level || now - h.born > 2400 || (h.foldT0 >= 0 && now - h.foldT0 > h.foldDur + 2)) { it.remove(); continue; }
            if (h.pos.distanceToSqr(cp) > 160 * 160) continue;
            PoseStack ps = e.getPoseStack();
            ps.pushPose();
            ps.translate(-cp.x, -cp.y, -cp.z);
            try { draw(mc, h, ps, cp, now); }
            catch (RuntimeException ex) { it.remove(); InfinityStones.LOGGER.warn("[extra] render failed", ex); }
            ps.popPose();
        }
    }

    private static float ease(float u) { u = Mth.clamp(u, 0, 1); return 1 - (1 - u) * (1 - u) * (1 - u); }

    private static void draw(Minecraft mc, H h, PoseStack ps, Vec3 cp, float now) {
        float appear = ease((now - h.born) / 12F);
        float fold = h.foldT0 < 0 ? 0 : Mth.clamp((now - h.foldT0) / h.foldDur, 0, 1);
        float overshoot = appear < 1 ? 1 + 0.12F * Mth.sin(appear * Mth.PI) : 1;
        float sy = Mth.lerp(appear, 0.05F, 1F) * overshoot * (1 - 0.95F * ease(fold * 2));
        float sxz = Mth.lerp(appear, 0.35F, 1F) * (1 - ease(fold * 2 - 1));
        float alpha = appear * (1 - fold * fold) * (0.9F + 0.1F * Mth.sin(now * 1.7F + h.id));
        if ((int) (now * 0.7F + h.id) % 23 == 0) alpha *= 0.65F;
        if (alpha < 0.01F || sxz < 0.01F) return;
        float bob = 0.04F * Mth.sin(now * 0.12F) * appear;
        float open = lid(h, now);
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();

        // textured translucent model
        if (bakedFor != mc.getEntityModels()) {
            ModelPart root = mc.getEntityModels().bakeLayer(ModelLayers.CHEST);
            bottom = root.getChild("bottom"); lid = root.getChild("lid"); lock = root.getChild("lock");
            bakedFor = mc.getEntityModels();
        }
        ps.pushPose();
        ps.translate(h.pos.x, h.pos.y + bob, h.pos.z);
        ps.scale(sxz, sy, sxz);
        ps.mulPose(Axis.YP.rotationDegrees(-h.yaw));
        ps.translate(-0.5, 0, -0.5);
        lid.xRot = -open * Mth.HALF_PI;
        lock.xRot = lid.xRot;
        var vc = chestTex().buffer(buf, RenderType::entityTranslucent);
        int col = FastColor.ARGB32.color((int) (255 * 0.6F * alpha), 255, 150, 160);
        int light = LightTexture.FULL_BRIGHT;
        bottom.render(ps, vc, light, OverlayTexture.NO_OVERLAY, col);
        lid.render(ps, vc, light, OverlayTexture.NO_OVERLAY, col);
        lock.render(ps, vc, light, OverlayTexture.NO_OVERLAY, col);
        ps.popPose();
        buf.endBatch(RenderType.entityTranslucent(chestTex().atlasLocation()));

        // additive glow: edges, faint faces, light column, rune circle
        Frame f = new Frame(h.pos.add(0, bob, 0), h.yaw, sxz, sy);
        HoloGlow gl = new HoloGlow(buf.getBuffer(RenderType.lightning()), ps.last().pose(), cp).color(HOT);
        float t = now / 20F;
        float lidAng = -open * Mth.HALF_PI;
        double[] bLo = {1 / 16D, 0, 1 / 16D}, bHi = {15 / 16D, 10 / 16D, 15 / 16D};
        double[] lLo = {1 / 16D, 9 / 16D, 1 / 16D}, lHi = {15 / 16D, 14 / 16D, 15 / 16D};
        edges(gl, f, bLo, bHi, 0, false, 0.012F, 0.85F * alpha, t);
        edges(gl, f, lLo, lHi, lidAng, true, 0.012F, 0.85F * alpha, t + 0.5F);
        gl.color(RED);
        faces(gl, f, bLo, bHi, 0, false, 0.10F * alpha);
        faces(gl, f, lLo, lHi, lidAng, true, 0.10F * alpha);
        if (open > 0.05F) {
            float o = Mth.clamp(open, 0, 1);
            gl.color(WHITE);
            gl.quad(f.w(1 / 16D, 0.62, 1 / 16D, 0, false), f.w(15 / 16D, 0.62, 1 / 16D, 0, false),
                    f.w(15 / 16D, 0.62, 15 / 16D, 0, false), f.w(1 / 16D, 0.62, 15 / 16D, 0, false), 0.5F * o * alpha, 0.5F * o * alpha);
            gl.color(HOT);
            float flick = 0.85F + 0.15F * Mth.sin(now * 0.9F);
            for (int p = 0; p < 2; p++) {
                double ax = p == 0 ? 1 : 0, az = p == 0 ? 0 : 1;
                gl.quad(f.w(0.5 - 0.2 * ax, 0.62, 0.5 - 0.2 * az, 0, false), f.w(0.5 + 0.2 * ax, 0.62, 0.5 + 0.2 * az, 0, false),
                        f.w(0.5 + 0.1 * ax, 2.1, 0.5 + 0.1 * az, 0, false), f.w(0.5 - 0.1 * ax, 2.1, 0.5 - 0.1 * az, 0, false),
                        0.5F * o * alpha * flick, 0F);
            }
            for (int s = 0; s < 6; s++) {
                float u = (t * 0.8F + s / 6F) % 1F;
                double a = s * 2.1 + t * 2;
                gl.spark(f.w(0.5 + Math.cos(a) * 0.18 * (1 - u), 0.65 + u * 1.3, 0.5 + Math.sin(a) * 0.18 * (1 - u), 0, false), 0.05F, 0.9F * o * alpha * (1 - u));
            }
        }
        runeCircle(gl, h.pos.add(0, -0.52, 0), (float) Math.max(0.01, appear * (1 - ease(fold))), alpha, t);
        buf.endBatch(RenderType.lightning());

        // falling items
        boolean any = false;
        for (Drop d : h.drops) any |= drop(mc, h, d, ps, buf, cp, now, alpha);
        if (any) buf.endBatch();
        h.drops.removeIf(d -> now > d.t0 + d.dur + 2);
    }

    private static boolean drop(Minecraft mc, H h, Drop d, PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cp, float now, float alpha) {
        int stacks = (d.count + Math.max(1, d.stack.getMaxStackSize()) - 1) / Math.max(1, d.stack.getMaxStackSize());
        int n = Math.min(6, Math.max(1, stacks));
        float dur = d.dur * 0.78F;
        boolean drew = false;
        HoloGlow gl = new HoloGlow(buf.getBuffer(RenderType.lightning()), ps.last().pose(), cp).color(HOT);
        for (int i = 0; i < n; i++) {
            float delay = n > 1 ? (d.dur - dur) * i / (n - 1) : 0;
            float u = (now - d.t0 - delay) / dur;
            if (u < 0 || u > 1) continue;
            float yTop = 8F, yEnd = 0.5F;
            float e = u * u * 0.7F + u * 0.3F;
            double y = h.pos.y + Mth.lerp(e, yTop, yEnd);
            double rad = n > 1 ? 0.2 * (1 - u) : 0;
            double ang = i * Math.PI * 2 / n + u * 8;
            Vec3 p = new Vec3(h.pos.x + Math.cos(ang) * rad, y, h.pos.z + Math.sin(ang) * rad);
            float grow = ease(u * 4);
            float dive = (float) Mth.clamp((y - (h.pos.y + 0.3)) / 0.7, 0, 1);
            float s = grow * dive;
            // halo, trail and sparks
            gl.color(HOT);
            gl.spark(p, 0.5F * s, 0.8F * alpha);
            gl.line(p, p.add(0, 1.6 * s, 0), 0.04F, 0.8F * alpha);
            if (s <= 0.02F) continue;
            ps.pushPose();
            ps.translate(p.x, p.y, p.z);
            ps.scale(0.85F * s, 0.85F * s, 0.85F * s);
            ps.mulPose(Axis.YP.rotationDegrees(now * 14F + i * 60F));
            mc.getItemRenderer().renderStatic(d.stack, ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ps, buf, mc.level, i);
            ps.popPose();
            drew = true;
        }
        buf.endBatch(RenderType.lightning());
        return drew;
    }

    private static void runeCircle(HoloGlow gl, Vec3 c, float k, float alpha, float t) {
        if (k <= 0.01F) return;
        float a = alpha * Mth.clamp(k * 1.4F, 0, 1);
        gl.color(RED);
        gl.circle(c, 1.55 * k, 72, t * 0.5, 0.022F, 0.9F * a, false);
        gl.circle(c, 1.3 * k, 48, -t * 0.8, 0.016F, 0.8F * a, true);
        gl.circle(c, 0.75 * k, 36, t * 1.1, 0.014F, 0.7F * a, false);
        gl.color(HOT);
        gl.ticks(c, 1.32 * k, 1.52 * k, 36, t * 0.5, 0.012F, a);
        gl.star(c, 1.28 * k, 6, 2, -t * 0.8, 0.014F, 0.8F * a);
        gl.star(c, 1.28 * k, 6, 1, -t * 0.8, 0.012F, 0.5F * a);
        gl.color(WHITE);
        for (int i = 0; i < 6; i++) {
            double an = -t * 0.8 + Math.PI * 2 * i / 6;
            gl.spark(c.add(Math.cos(an) * 1.28 * k, 0.02, Math.sin(an) * 1.28 * k), 0.07F * k, 0.8F * a);
        }
    }

    // ------------------------------------------------------------------ chest-local geometry

    /** Block-space chest point (0..1 cube, lid rotating about its hinge) → camera-relative world point. */
    private record Frame(Vec3 pos, float yaw, float sxz, float sy) {
        Vec3 w(double lx, double ly, double lz, float lidAng, boolean isLid) {
            if (isLid) {
                double y = ly - HINGE_Y, z = lz - HINGE_Z;
                double c = Math.cos(lidAng), s = Math.sin(lidAng);
                ly = HINGE_Y + y * c - z * s;
                lz = HINGE_Z + y * s + z * c;
            }
            double x = (lx - 0.5) * sxz, z2 = (lz - 0.5) * sxz;
            double phi = Math.toRadians(-yaw), cp = Math.cos(phi), sp = Math.sin(phi);
            return pos.add(x * cp + z2 * sp, ly * sy, -x * sp + z2 * cp);
        }
    }

    private static Vec3[] corners(Frame f, double[] lo, double[] hi, float ang, boolean isLid) {
        Vec3[] c = new Vec3[8];
        for (int i = 0; i < 8; i++)
            c[i] = f.w((i & 1) == 0 ? lo[0] : hi[0], (i & 4) == 0 ? lo[1] : hi[1], (i & 2) == 0 ? lo[2] : hi[2], ang, isLid);
        return c;
    }

    private static void edges(HoloGlow gl, Frame f, double[] lo, double[] hi, float ang, boolean isLid, float width, float al, float time) {
        Vec3[] c = corners(f, lo, hi, ang, isLid);
        int[][] e = {{0, 1}, {1, 3}, {3, 2}, {2, 0}, {4, 5}, {5, 7}, {7, 6}, {6, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int i = 0; i < e.length; i++) {
            Vec3 a = c[e[i][0]], b = c[e[i][1]];
            gl.line(a, b, width, al);
            float u = (time * 0.7F + i * 0.137F) % 1F;
            gl.spark(a.lerp(b, u), width * 3.2F, al * 0.8F);
        }
    }

    private static void faces(HoloGlow gl, Frame f, double[] lo, double[] hi, float ang, boolean isLid, float al) {
        Vec3[] c = corners(f, lo, hi, ang, isLid);
        gl.quad(c[0], c[1], c[3], c[2], al, al); gl.quad(c[4], c[5], c[7], c[6], al, al);
        gl.quad(c[0], c[1], c[5], c[4], al, al); gl.quad(c[2], c[3], c[7], c[6], al, al);
        gl.quad(c[0], c[2], c[6], c[4], al, al); gl.quad(c[1], c[3], c[7], c[5], al, al);
    }

    private ChestHolo() {}
}
