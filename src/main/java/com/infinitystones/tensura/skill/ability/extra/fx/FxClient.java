package com.infinitystones.tensura.skill.ability.extra.fx;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.client.render.GlowFx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Draws {@link FxNet} effects: additive glow geometry in the world + camera shake + screen overlays. */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class FxClient {
    private static final class Inst {
        FxNet.Fx f; int age;
        Inst(FxNet.Fx f) { this.f = f; }
    }

    private static final Map<Integer, Inst> KEYED = new HashMap<>();
    private static final List<Inst> ONE_SHOT = new ArrayList<>();
    private static final int MAX_ONE_SHOT = 600;

    private static float shake, shakeMax; private static int shakeLeft, shakeTotal;
    private static int flashColor; private static float flashAlpha; private static int flashLeft, flashTotal;
    private static int broken, nightmare, avatar;

    static void accept(FxNet.Fx f) {
        switch (f.kind()) {
            case FxNet.KILL -> { KEYED.remove(f.id()); return; }
            case FxNet.SHAKE -> {
                if (f.size() >= shake * (shakeTotal == 0 ? 0 : (float) shakeLeft / shakeTotal)) { shakeMax = f.size(); shake = f.size(); shakeLeft = shakeTotal = Math.max(1, f.life()); }
                return;
            }
            case FxNet.FLASH -> { flashColor = f.color(); flashAlpha = f.size(); flashLeft = flashTotal = Math.max(1, f.life()); return; }
            case FxNet.BROKEN_TINT -> { broken = f.life(); return; }
            case FxNet.NIGHTMARE -> { nightmare = f.life(); return; }
            case FxNet.AVATAR_TINT -> { avatar = f.life(); return; }
            default -> { }
        }
        if (f.id() != 0) {
            Inst old = KEYED.get(f.id());
            Inst n = new Inst(f);
            // refresh keeps the fade-in finished
            if (old != null) n.age = Math.min(old.age, 6);
            KEYED.put(f.id(), n);
        } else if (ONE_SHOT.size() < MAX_ONE_SHOT) ONE_SHOT.add(new Inst(f));
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        KEYED.clear(); ONE_SHOT.clear(); broken = nightmare = avatar = 0; shakeLeft = flashLeft = 0;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (Minecraft.getInstance().isPaused()) return;
        for (Iterator<Inst> it = ONE_SHOT.iterator(); it.hasNext(); ) { Inst i = it.next(); if (++i.age > i.f.life()) it.remove(); }
        KEYED.values().removeIf(i -> ++i.age > i.f.life());
        if (shakeLeft > 0) shakeLeft--;
        if (flashLeft > 0) flashLeft--;
        if (broken > 0) broken--;
        if (nightmare > 0) nightmare--;
        if (avatar > 0) avatar--;
    }

    // ================================================================== world

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || (KEYED.isEmpty() && ONE_SHOT.isEmpty())) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        Camera cam = e.getCamera();
        Vec3 cp = cam.getPosition();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(-cp.x, -cp.y, -cp.z);
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        Matrix4f m = ps.last().pose();
        float time = (mc.level.getGameTime() % 100000L + pt) / 20F;
        VertexConsumer vc = buf.getBuffer(RenderType.lightning());
        Draw d = new Draw(vc, m, cp);
        for (Inst i : ONE_SHOT) shape(d, i, pt, time, ps, buf, false);
        for (Inst i : KEYED.values()) shape(d, i, pt, time, ps, buf, false);
        buf.endBatch(RenderType.lightning());
        // textured passes (rune circles, flares, halos)
        for (Inst i : ONE_SHOT) shape(d, i, pt, time, ps, buf, true);
        for (Inst i : KEYED.values()) shape(d, i, pt, time, ps, buf, true);
        buf.endBatch();
        ps.popPose();
    }

    /** Fade in over 5 ticks, out over the last 30% of life (long effects: last 20 ticks). */
    private static float fade(Inst i, float pt) {
        float age = i.age + pt, life = Math.max(1, i.f.life());
        float in = Mth.clamp(age / 5F, 0, 1);
        float outLen = Math.min(20F, life * 0.35F);
        float out = Mth.clamp((life - age) / outLen, 0, 1);
        return in * out;
    }

    private static Vec3 entityPos(int id, float pt, Vec3 fallback) {
        if (id < 0) return fallback;
        Entity e = Minecraft.getInstance().level.getEntity(id);
        return e == null ? null : e.getPosition(pt);
    }

    private static void shape(Draw d, Inst i, float pt, float time, PoseStack ps, MultiBufferSource buf, boolean textured) {
        FxNet.Fx f = i.f;
        float a = fade(i, pt);
        if (a <= 0.01F) return;
        float age = i.age + pt, prog = Mth.clamp(age / Math.max(1, f.life()), 0, 1);
        int c = f.color();
        d.color(c);
        Vec3 A = f.a(), B = f.b();
        switch (f.kind()) {
            case FxNet.BEAM -> { if (!textured) d.beam(A, B, f.size(), a, time); }
            case FxNet.TETHER -> {
                Entity ea = Minecraft.getInstance().level.getEntity(f.ea());
                if (ea == null) return;
                Vec3 from = ea.getPosition(pt).add(0, ea.getBbHeight() * 0.6, 0);
                Vec3 to = B;
                if (f.eb() >= 0) {
                    Entity eb = Minecraft.getInstance().level.getEntity(f.eb());
                    if (eb == null) return;
                    to = eb.getPosition(pt).add(0, eb.getBbHeight() * 0.55, 0);
                }
                if (!textured) d.beam(from, to, f.size(), a, time);
                else d.flare(ps, buf, to, f.size() * 4, c, a * 0.8F, time * 3);
            }
            case FxNet.CHAIN -> {
                Entity ea = Minecraft.getInstance().level.getEntity(f.ea());
                if (ea == null) return;
                Vec3 from = ea.getPosition(pt).add(0, ea.getBbHeight() * 0.5, 0);
                Vec3 to = B;
                if (f.eb() >= 0) {
                    Entity eb = Minecraft.getInstance().level.getEntity(f.eb());
                    if (eb == null) return;
                    to = eb.getPosition(pt).add(0, eb.getBbHeight() * 0.5, 0);
                }
                if (!textured) d.chain(from, to, a, time);
            }
            case FxNet.SHOCKWAVE -> {
                float ease = 1 - (1 - prog) * (1 - prog) * (1 - prog);
                float r = 0.3F + f.size() * ease;
                float al = a * (1 - prog * 0.85F);
                if (!textured) {
                    d.ring(A.add(0, 0.08, 0), r, 0.35F + 0.5F * (1 - prog), al, 0);
                    d.ring(A.add(0, 0.1, 0), r * 0.82F, 0.12F, al * 0.7F, 0);
                    d.ring(A.add(0, 0.6 * (1 - prog), 0), r * 0.94F, 0.06F, al * 0.5F, 0);
                } else d.ground(ps, buf, GlowFx.HALO, A.add(0, 0.05, 0), r * 1.15F, c, al * 0.6F, 0);
            }
            case FxNet.DOME -> {
                float grow = Mth.clamp(age / 8F, 0, 1);
                float r = f.size() * (1 - (1 - grow) * (1 - grow));
                if (!textured) d.dome(A, r, a, time);
                else d.ground(ps, buf, GlowFx.RUNE_RING, A.add(0, 0.06, 0), r, c, a * 0.8F, time * 0.6F);
            }
            case FxNet.RUNES -> {
                float grow = Mth.clamp(age / 10F, 0, 1);
                float r = f.size() * (1 - (1 - grow) * (1 - grow));
                float spin = time * 0.5F * (f.extra() == 0 ? 1 : f.extra());
                if (textured) {
                    d.ground(ps, buf, GlowFx.RUNE_RING, A.add(0, 0.04, 0), r, c, a, spin);
                    d.ground(ps, buf, GlowFx.RUNE_RING, A.add(0, 0.06, 0), r * 0.62F, c, a * 0.9F, -spin * 1.7F);
                    d.ground(ps, buf, GlowFx.HALO, A.add(0, 0.03, 0), r * 1.3F, c, a * 0.35F, 0);
                } else {
                    d.ring(A.add(0, 0.07, 0), r, 0.07F, a * 0.9F, 0);
                    d.ring(A.add(0, 0.07, 0), r * 1.04F, 0.03F, a * 0.6F, 0);
                    // rising motes along the edge
                    for (int k = 0; k < 10; k++) {
                        double ang = spin * 2 + k * Math.PI * 2 / 10;
                        float h = (float) ((time * 0.8 + k * 0.37) % 1.0);
                        Vec3 p = A.add(Math.cos(ang) * r, 0.1 + h * 1.8, Math.sin(ang) * r);
                        d.spark(p, 0.06F, a * (1 - h));
                    }
                }
            }
            case FxNet.CUBE -> {
                Vec3 cen = entityPos(f.ea(), pt, A);
                if (cen == null) return;
                if (f.ea() >= 0) {
                    Entity e = Minecraft.getInstance().level.getEntity(f.ea());
                    if (e != null) cen = cen.add(0, e.getBbHeight() / 2, 0);
                }
                if (!textured) d.cube(cen, f.size(), a, time);
            }
            case FxNet.FLARE -> {
                if (textured) {
                    float s = f.size() * (0.4F + 0.6F * (float) Math.sin(Math.min(1, prog * 2.2) * Math.PI / 2));
                    d.flare(ps, buf, A, s, c, a * (1 - prog * 0.6F), time);
                    d.flare(ps, buf, A, s * 0.45F, 0xFFFFFF, a * (1 - prog), -time * 2);
                }
            }
            case FxNet.PORTAL -> {
                float grow = Mth.clamp(age / 12F, 0, 1);
                float s = f.size() * (1 - (1 - grow) * (1 - grow));
                if (!textured) d.portal(A, B, s, a, time);
                else d.flare(ps, buf, A, s * 1.6F, c, a * 0.55F, time);
            }
            case FxNet.CROWN -> {
                Entity e = Minecraft.getInstance().level.getEntity(f.ea());
                if (e == null) return;
                Vec3 top = e.getPosition(pt).add(0, e.getBbHeight() + 0.35 + 0.06 * Math.sin(time * 3), 0);
                if (!textured) d.crown(top, f.size(), a, time);
            }
            case FxNet.PILLAR -> {
                if (!textured) d.pillar(A, f.size(), f.extra(), a, time);
                else d.ground(ps, buf, GlowFx.RUNE_RING, A.add(0, 0.05, 0), f.size() * 3.2F, c, a, time * 0.8F);
            }
            case FxNet.TUNNEL -> { if (!textured) d.tunnel(A, B, f.size(), a, prog, time); }
            case FxNet.PANEL -> { if (!textured) d.panel(A, B, f.size(), a, time); }
            case FxNet.CLOCK -> {
                float grow = Mth.clamp(age / 10F, 0, 1);
                float r = f.size() * (1 - (1 - grow) * (1 - grow));
                if (textured) d.ground(ps, buf, GlowFx.RUNE_RING, A.add(0, 0.04, 0), r, c, a * 0.85F, time * 0.2F * Math.signum(f.extra() == 0 ? 1 : f.extra()));
                else d.clock(A.add(0, 0.08, 0), r, a, time * (f.extra() == 0 ? 1 : f.extra()));
            }
            case FxNet.SPIRAL -> {
                Vec3 base = entityPos(f.ea(), pt, A);
                if (base == null || textured) return;
                d.spiral(base, f.size(), f.extra(), a, prog, time);
            }
            case FxNet.ORB -> {
                Vec3 cen = entityPos(f.ea(), pt, A);
                if (cen == null) return;
                if (f.ea() >= 0) cen = cen.add(B);
                float s = f.size() * (0.92F + 0.08F * Mth.sin(time * 9));
                if (!textured) d.orb(cen, s, a, time);
                else {
                    d.flare(ps, buf, cen, s * 3.2F, c, a * 0.9F, time * 2);
                    d.flare(ps, buf, cen, s * 1.2F, 0xFFFFFF, a * 0.7F, -time * 3);
                }
            }
            default -> { }
        }
    }

    // ================================================================== camera + screen

    @SubscribeEvent
    public static void camera(ViewportEvent.ComputeCameraAngles e) {
        if (shakeLeft <= 0 || shakeTotal <= 0) return;
        float k = (float) shakeLeft / shakeTotal;
        float s = shakeMax * k * k;
        double t = (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime()) + e.getPartialTick();
        e.setYaw(e.getYaw() + (float) (Math.sin(t * 2.7) * s * 1.6));
        e.setPitch(e.getPitch() + (float) (Math.cos(t * 3.3) * s * 1.2));
        e.setRoll(e.getRoll() + (float) (Math.sin(t * 1.9) * s * 1.4));
    }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        GuiGraphics g = e.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        float t = (mc.player.tickCount + pt);
        if (avatar > 0) {
            int al = (int) (40 + 18 * Mth.sin(t * 0.15F));
            vignette(g, w, h, 0xF2D02B, al, 60);
        }
        if (broken > 0) {
            // chromatic edges + glitch slices
            int al = (int) (34 + 20 * Mth.sin(t * 0.7F));
            g.fill(0, 0, 3 + (int) (2 * Mth.sin(t * 1.3F)), h, (al << 24) | 0xFF2030);
            g.fill(w - 3 - (int) (2 * Mth.cos(t * 1.1F)), 0, w, h, (al << 24) | 0x20E0FF);
            vignette(g, w, h, 0xE22B3B, 46, 40);
            long seed = (long) (t / 2);
            java.util.Random r = new java.util.Random(seed * 31L);
            if (r.nextInt(4) == 0) {
                for (int k = 0; k < 3; k++) {
                    int y = r.nextInt(h), hh = 1 + r.nextInt(5), off = r.nextInt(10) - 5;
                    g.fill(Math.max(0, off), y, Math.min(w, w + off), y + hh, (40 << 24) | (r.nextBoolean() ? 0xFF2040 : 0x30E0FF));
                }
            }
        }
        if (nightmare > 0) {
            float beat = (float) Math.pow(Math.max(0, Mth.sin(t * 0.55F)), 8);
            vignette(g, w, h, 0x000000, (int) (150 + 80 * beat), 90);
            vignette(g, w, h, 0x400010, (int) (60 * beat), 50);
        }
        if (flashLeft > 0) {
            float k = (flashLeft - pt) / flashTotal;
            int al = (int) (255 * Mth.clamp(flashAlpha * k * k, 0, 1));
            if (al > 2) g.fill(0, 0, w, h, (al << 24) | (flashColor & 0xFFFFFF));
        }
    }

    /** Soft edge darkening/tint built from nested frames. */
    private static void vignette(GuiGraphics g, int w, int h, int rgb, int alpha, int depth) {
        int steps = 10;
        for (int i = 0; i < steps; i++) {
            int inset = depth * i / steps, th = Math.max(1, depth / steps);
            int al = Mth.clamp(alpha * (steps - i) / steps / 2, 0, 255);
            int col = (al << 24) | (rgb & 0xFFFFFF);
            g.fill(inset, inset, w - inset, inset + th, col);
            g.fill(inset, h - inset - th, w - inset, h - inset, col);
            g.fill(inset, inset + th, inset + th, h - inset - th, col);
            g.fill(w - inset - th, inset + th, w - inset, h - inset - th, col);
        }
    }

    private FxClient() {}
}
