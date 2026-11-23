package com.infinitystones.tensura.skill.ability.extra.time.client;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.client.render.GlowFx;
import com.infinitystones.tensura.skill.ability.extra.time.TimeNet;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Client renderer of the Time stone effects ({@link TimeNet}). Three passes per frame: translucent (storm clouds,
 * rain/snow/sand streaks, mist), additive glow (outlines, dials, aurora, waves, ghosts) and textured glow (rune rings,
 * flares). Climate zones also drive the fog, HUD tints, local splash particles and ambience for players inside.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class TimeClient {
    static final int GREEN = 0x2BD56A, RED = 0xE22B3B;

    static final class Inst {
        TimeNet.T f; int age;
        Inst(TimeNet.T f) { this.f = f; }
    }

    private static final Map<Integer, Inst> KEYED = new HashMap<>();
    private static final List<Inst> ONE_SHOT = new ArrayList<>();
    private static final Map<Integer, ZoneCache> CACHE = new HashMap<>();
    private static final int MAX_ONE_SHOT = 400;

    /** Fog / tint blend towards the climate the camera is in (0..1), and that climate. */
    private static float blend, blendPrev;
    private static int fogClimate;

    public static void accept(TimeNet.T f) {
        if (f.kind() == TimeNet.KILL) { KEYED.remove(f.id()); CACHE.remove(f.id()); return; }
        if (f.id() != 0) {
            Inst old = KEYED.get(f.id());
            Inst n = new Inst(f);
            if (old != null) n.age = Math.min(old.age, 6);
            KEYED.put(f.id(), n);
        } else if (ONE_SHOT.size() < MAX_ONE_SHOT) ONE_SHOT.add(new Inst(f));
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { KEYED.clear(); ONE_SHOT.clear(); CACHE.clear(); blend = blendPrev = 0; }

    // ================================================================== zones

    /** Per-zone client data: terrain heights of the precipitation grid, ring heights, cloud puffs. */
    static final class ZoneCache {
        double cx, cy, cz; float r; long built = -9999; int climate = -1;
        float cell; int n; float[] h; float maxH; float[] ring = new float[96];
        float[] px, pz, pr, ph; float cloudY;
    }

    private static boolean inZone(TimeNet.T z, Vec3 p, double margin) {
        double dx = p.x - z.ax(), dz = p.z - z.az();
        return dx * dx + dz * dz <= (z.size() + margin) * (z.size() + margin) && p.y > z.ay() - 8 && p.y < z.ay() + 48;
    }

    /** Climate of the zone containing p (C_NONE if none). */
    public static int climateAt(Vec3 p) {
        int best = TimeNet.C_NONE;
        for (Inst i : KEYED.values()) if (i.f.kind() == TimeNet.ZONE && (int) i.f.extra() != TimeNet.C_NONE && inZone(i.f, p, 0)) best = (int) i.f.extra();
        return best;
    }

    private static ZoneCache cache(ClientLevel level, Inst z) {
        TimeNet.T f = z.f;
        ZoneCache c = CACHE.computeIfAbsent(f.id(), k -> new ZoneCache());
        int climate = (int) f.extra();
        long now = level.getGameTime();
        boolean moved = c.cx != f.ax() || c.cz != f.az() || c.r != f.size() || c.climate != climate;
        if (!moved && now - c.built < 40) return c;
        c.cx = f.ax(); c.cy = f.ay(); c.cz = f.az(); c.r = f.size(); c.climate = climate; c.built = now;
        c.cell = climate == TimeNet.C_STORM ? 1.25F : climate == TimeNet.C_SNOW ? 1.6F : 1.45F;
        c.n = (int) Math.ceil(c.r * 2 / c.cell) + 1;
        c.h = new float[c.n * c.n];
        c.maxH = (float) c.cy;
        for (int i = 0; i < c.n; i++) for (int j = 0; j < c.n; j++) {
            double x = c.cx - c.r + i * c.cell, zz = c.cz - c.r + j * c.cell;
            float hh = level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(zz));
            c.h[i * c.n + j] = hh;
            double dx = x - c.cx, dz = zz - c.cz;
            if (dx * dx + dz * dz <= c.r * c.r) c.maxH = Math.max(c.maxH, hh);
        }
        for (int k = 0; k < c.ring.length; k++) {
            double a = Math.PI * 2 * k / c.ring.length;
            float hh = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(c.cx + Math.cos(a) * c.r), Mth.floor(c.cz + Math.sin(a) * c.r));
            c.ring[k] = (float) Mth.clamp(hh, c.cy - 6, c.cy + 6);
        }
        c.cloudY = (float) Math.min(c.cy + 32, Math.max(c.cy + 16, c.maxH + 7));
        // cloud puffs (deterministic per zone)
        RandomSource rnd = RandomSource.create(f.id() * 7919L + (long) (c.r * 10));
        float spacing = climate == TimeNet.C_STORM ? 3.0F : 4.2F;
        List<float[]> puffs = new ArrayList<>();
        for (float x = -c.r; x <= c.r; x += spacing) for (float zz = -c.r; zz <= c.r; zz += spacing) {
            float jx = x + (rnd.nextFloat() - 0.5F) * spacing * 0.8F, jz = zz + (rnd.nextFloat() - 0.5F) * spacing * 0.8F;
            if (jx * jx + jz * jz > c.r * c.r * 1.02F) continue;
            puffs.add(new float[]{jx, jz, 2.2F + rnd.nextFloat() * 2.0F, rnd.nextFloat() * 6.28F});
        }
        c.px = new float[puffs.size()]; c.pz = new float[puffs.size()]; c.pr = new float[puffs.size()]; c.ph = new float[puffs.size()];
        for (int k = 0; k < puffs.size(); k++) { float[] p = puffs.get(k); c.px[k] = p[0]; c.pz[k] = p[1]; c.pr[k] = p[2]; c.ph[k] = p[3]; }
        return c;
    }

    private static float heightAt(ZoneCache c, double x, double z) {
        int i = Mth.clamp((int) Math.round((x - (c.cx - c.r)) / c.cell), 0, c.n - 1), j = Mth.clamp((int) Math.round((z - (c.cz - c.r)) / c.cell), 0, c.n - 1);
        return c.h[i * c.n + j];
    }

    private static float hash(int a, int b, int s) {
        int h = a * 73856093 ^ b * 19349663 ^ s * 83492791;
        h ^= h >>> 13; h *= 0x5bd1e995; h ^= h >>> 15;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    // ================================================================== tick: particles, sounds, fog blend

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused() || mc.level == null || mc.player == null) return;
        for (Iterator<Inst> it = ONE_SHOT.iterator(); it.hasNext(); ) { Inst i = it.next(); if (++i.age > i.f.life()) it.remove(); }
        KEYED.values().removeIf(i -> {
            boolean dead = ++i.age > i.f.life();
            if (dead) CACHE.remove(i.f.id());
            return dead;
        });
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        int cl = climateAt(cam);
        blendPrev = blend;
        if (cl != TimeNet.C_NONE) { if (cl != fogClimate && blend > 0.05F) blend = Math.max(0, blend - 0.1F); else { fogClimate = cl; blend = Math.min(1, blend + 0.05F); } }
        else blend = Math.max(0, blend - 0.05F);
        ambience(mc, cl);
    }

    private static void ambience(Minecraft mc, int cl) {
        ClientLevel level = mc.level;
        RandomSource r = level.random;
        Vec3 pp = mc.player.position();
        for (Inst z : KEYED.values()) {
            if (z.f.kind() != TimeNet.ZONE) continue;
            int c = (int) z.f.extra();
            if (c == TimeNet.C_NONE || !inZone(z.f, pp, 16)) continue;
            ZoneCache zc = cache(level, z);
            int n = switch (c) { case TimeNet.C_STORM -> 14; case TimeNet.C_RAIN -> 8; case TimeNet.C_SNOW -> 4; case TimeNet.C_SAND -> 10; case TimeNet.C_SUN -> 3; case TimeNet.C_AURORA -> 2; default -> 3; };
            for (int k = 0; k < n; k++) {
                double x = pp.x + (r.nextDouble() - 0.5) * 24, zz = pp.z + (r.nextDouble() - 0.5) * 24;
                double dx = x - z.f.ax(), dz = zz - z.f.az();
                if (dx * dx + dz * dz > z.f.size() * z.f.size()) continue;
                float gy = heightAt(zc, x, zz);
                switch (c) {
                    case TimeNet.C_STORM, TimeNet.C_RAIN -> level.addParticle(ParticleTypes.RAIN, x, gy + 0.05, zz, 0, 0, 0);
                    case TimeNet.C_SNOW -> level.addParticle(ParticleTypes.SNOWFLAKE, x, pp.y + 2 + r.nextDouble() * 5, zz, (r.nextDouble() - 0.5) * 0.03, -0.05, (r.nextDouble() - 0.5) * 0.03);
                    case TimeNet.C_SAND -> {
                        Vec3 t = new Vec3(-dz, 0, dx).normalize().scale(0.6);
                        level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SAND.defaultBlockState()), x, gy + r.nextDouble() * 4, zz, t.x, 0, t.z);
                        if (k % 3 == 0) level.addParticle(ParticleTypes.WHITE_ASH, x, gy + 1 + r.nextDouble() * 3, zz, t.x * 0.4, 0.02, t.z * 0.4);
                    }
                    case TimeNet.C_SUN -> level.addParticle(ParticleTypes.WHITE_ASH, x, gy + 0.3 + r.nextDouble(), zz, 0, 0.04, 0);
                    case TimeNet.C_AURORA -> level.addParticle(ParticleTypes.END_ROD, x, gy + 2 + r.nextDouble() * 6, zz, 0, 0.01, 0);
                    case TimeNet.C_FOG -> level.addParticle(ParticleTypes.CLOUD, x, gy + 0.5 + r.nextDouble() * 2, zz, 0.01, 0, 0.01);
                    default -> { }
                }
            }
        }
        if (cl == TimeNet.C_NONE) return;
        long t = level.getGameTime();
        if ((cl == TimeNet.C_STORM || cl == TimeNet.C_RAIN) && t % 14 == 0)
            level.playLocalSound(pp.x, pp.y + 1, pp.z, SoundEvents.WEATHER_RAIN, SoundSource.WEATHER, cl == TimeNet.C_STORM ? 0.45F : 0.28F, cl == TimeNet.C_STORM ? 0.75F : 1F, false);
        if (cl == TimeNet.C_SAND && t % 18 == 0)
            level.playLocalSound(pp.x, pp.y + 1, pp.z, SoundEvents.WEATHER_RAIN, SoundSource.WEATHER, 0.25F, 0.45F, false);
        if (cl == TimeNet.C_SUN && t % 60 == 0)
            level.playLocalSound(pp.x, pp.y + 1, pp.z, SoundEvents.FIRE_AMBIENT, SoundSource.WEATHER, 0.2F, 0.6F, false);
        if (cl == TimeNet.C_AURORA && t % 50 == 0)
            level.playLocalSound(pp.x, pp.y + 1, pp.z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.WEATHER, 0.25F, 0.6F + level.random.nextFloat() * 0.4F, false);
    }

    // ================================================================== fog

    private static float fogBlend(double pt) { return Mth.lerp((float) pt, blendPrev, blend); }

    @SubscribeEvent
    public static void fog(ViewportEvent.RenderFog e) {
        float b = fogBlend(e.getPartialTick());
        if (b <= 0.001F) return;
        float far = switch (fogClimate) {
            case TimeNet.C_FOG -> 7; case TimeNet.C_SAND -> 14; case TimeNet.C_STORM -> 42; case TimeNet.C_SNOW -> 40; case TimeNet.C_RAIN -> 70;
            case TimeNet.C_SUN -> 120; default -> -1; };
        if (far < 0 || far >= e.getFarPlaneDistance()) return;
        float near = fogClimate == TimeNet.C_FOG || fogClimate == TimeNet.C_SAND ? 0 : far * 0.25F;
        e.setFarPlaneDistance(Mth.lerp(b, e.getFarPlaneDistance(), far));
        e.setNearPlaneDistance(Mth.lerp(b, e.getNearPlaneDistance(), near));
        e.setFogShape(FogShape.SPHERE);
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void fogColor(ViewportEvent.ComputeFogColor e) {
        float b = fogBlend(e.getPartialTick());
        if (b <= 0.001F) return;
        int c = switch (fogClimate) {
            case TimeNet.C_FOG -> 0xC4C9CE; case TimeNet.C_SAND -> 0xC9A66B; case TimeNet.C_STORM -> 0x34373D; case TimeNet.C_SNOW -> 0xDDE4EA;
            case TimeNet.C_RAIN -> 0x7C8590; case TimeNet.C_SUN -> 0xF2C27A; case TimeNet.C_AURORA -> 0x10324A; default -> -1; };
        if (c < 0) return;
        float k = fogClimate == TimeNet.C_AURORA ? b * 0.5F : b;
        e.setRed(Mth.lerp(k, e.getRed(), ((c >> 16) & 255) / 255F));
        e.setGreen(Mth.lerp(k, e.getGreen(), ((c >> 8) & 255) / 255F));
        e.setBlue(Mth.lerp(k, e.getBlue(), (c & 255) / 255F));
    }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float b = fogBlend(e.getPartialTick().getGameTimeDeltaPartialTick(true));
        if (b <= 0.01F) return;
        GuiGraphics g = e.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        float t = mc.player.tickCount + e.getPartialTick().getGameTimeDeltaPartialTick(true);
        switch (fogClimate) {
            case TimeNet.C_STORM -> vignette(g, w, h, 0x05070C, (int) (110 * b), 80);
            case TimeNet.C_SUN -> {
                vignette(g, w, h, 0xFF7A1A, (int) ((55 + 25 * Mth.sin(t * 0.08F)) * b), 70);
                g.fill(0, 0, w, h, ((int) (18 * b) << 24) | 0xFFB050);
            }
            case TimeNet.C_SAND -> { g.fill(0, 0, w, h, ((int) (46 * b) << 24) | 0xC9A15E); vignette(g, w, h, 0x7A5A2A, (int) (90 * b), 70); }
            case TimeNet.C_SNOW -> vignette(g, w, h, 0xEAF4FF, (int) (70 * b), 50);
            case TimeNet.C_FOG -> g.fill(0, 0, w, h, ((int) (40 * b) << 24) | 0xD0D4D8);
            case TimeNet.C_AURORA -> vignette(g, w, h, 0x20FF9A, (int) ((30 + 15 * Mth.sin(t * 0.05F)) * b), 60);
            default -> { }
        }
    }

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

    // ================================================================== world render

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

        // 1) translucent: clouds, precipitation, mist
        TDraw tr = new TDraw(buf.getBuffer(RenderType.debugQuads()), m, cp, false);
        for (Inst i : KEYED.values()) if (i.f.kind() == TimeNet.ZONE) ZoneFx.translucent(tr, mc.level, i, cache(mc.level, i), pt, time);
        buf.endBatch(RenderType.debugQuads());

        // 2) additive glow
        TDraw d = new TDraw(buf.getBuffer(RenderType.lightning()), m, cp, true);
        for (Inst i : KEYED.values()) shape(d, mc.level, i, pt, time, ps, buf, false);
        for (Inst i : ONE_SHOT) shape(d, mc.level, i, pt, time, ps, buf, false);
        buf.endBatch(RenderType.lightning());

        // 3) textured glow
        for (Inst i : KEYED.values()) shape(d, mc.level, i, pt, time, ps, buf, true);
        for (Inst i : ONE_SHOT) shape(d, mc.level, i, pt, time, ps, buf, true);
        buf.endBatch();
        ps.popPose();
    }

    static float fade(Inst i, float pt) {
        float age = i.age + pt, life = Math.max(1, i.f.life());
        float in = Mth.clamp(age / 6F, 0, 1);
        float out = Mth.clamp((life - age) / Math.min(20F, life * 0.35F), 0, 1);
        return in * out;
    }

    static void ground(PoseStack ps, MultiBufferSource buf, net.minecraft.resources.ResourceLocation tex, Vec3 c, float r, int color, float al, float spin) {
        ps.pushPose();
        ps.translate(c.x, c.y, c.z);
        GlowFx.groundQuad(ps, buf, tex, r, color, al, spin);
        ps.popPose();
    }

    static void flare(PoseStack ps, MultiBufferSource buf, Vec3 c, float size, int color, float al, float spin) {
        GlowFx.billboard(ps, buf, GlowFx.FLARE, true, (float) c.x, (float) c.y, (float) c.z, size, color, al, spin);
        GlowFx.billboard(ps, buf, GlowFx.HALO, true, (float) c.x, (float) c.y, (float) c.z, size * 0.7F, color, al * 0.6F, 0);
    }

    private static Vec3 entityPos(ClientLevel level, int id, float pt) {
        Entity e = id < 0 ? null : level.getEntity(id);
        return e == null ? null : e.getPosition(pt);
    }

    private static void shape(TDraw d, ClientLevel level, Inst i, float pt, float time, PoseStack ps, MultiBufferSource buf, boolean textured) {
        TimeNet.T f = i.f;
        if (f.a().distanceToSqr(d.cam) > 260 * 260 && f.ea() < 0) return;
        float a = fade(i, pt);
        if (a <= 0.01F) return;
        float age = i.age + pt, prog = Mth.clamp(age / Math.max(1, f.life()), 0, 1);
        switch (f.kind()) {
            case TimeNet.ZONE -> ZoneFx.glow(d, level, i, cache(level, i), a, pt, time, ps, buf, textured);
            case TimeNet.STASIS -> TimeShapes.stasis(d, f, a, age, time, ps, buf, textured);
            case TimeNet.FROZEN -> {
                Vec3 p = entityPos(level, f.ea(), pt);
                if (p != null && !textured) TimeShapes.frozen(d, p, f.size(), f.extra(), a, time);
            }
            case TimeNet.HOURGLASS -> {
                Vec3 p = entityPos(level, f.ea(), pt);
                Entity e = level.getEntity(f.ea());
                if (p != null && e != null && !textured) {
                    float progress = Mth.clamp(f.size() + (1 - f.size()) * prog, 0, 1);
                    TimeShapes.hourglass(d, p.add(0, e.getBbHeight() + 0.9, 0), progress, f.extra() > 0, a, time);
                }
            }
            case TimeNet.LAPSE -> TimeShapes.lapse(d, f, a, age, time, ps, buf, textured);
            case TimeNet.ERA -> TimeShapes.era(d, f, a, prog, time, ps, buf, textured);
            case TimeNet.GHOST -> {
                float delay = (float) f.bx();
                if (age < delay || textured) return;
                float k = Mth.clamp((age - delay) / Math.max(1, f.life() - delay), 0, 1);
                TimeShapes.ghost(d, f.a(), f.size(), f.extra(), (float) f.by(), (1 - k) * Mth.clamp((age - delay) / 2F, 0, 1), time);
            }
            case TimeNet.RESTORE -> TimeShapes.restore(d, f, a, prog, time, ps, buf, textured);
            case TimeNet.MARK_RING -> {
                if (textured) return;
                float ease = 1 - (1 - prog) * (1 - prog) * (1 - prog);
                float r = 0.3F + f.size() * ease;
                float al = a * (1 - prog * 0.8F);
                d.ring(f.a().add(0, 0.1, 0), r, 0.3F * (1 - prog) + 0.05F, f.color(), al, 0);
                for (int k = 0; k < 24; k++) {
                    double an = Math.PI * 2 * k / 24;
                    Vec3 b = f.a().add(Math.cos(an) * r, 0.1, Math.sin(an) * r);
                    d.fadeLine(b, b.add(0, 2.5 * (1 - prog), 0), 0.05F, f.color(), al, 0);
                }
            }
            default -> { }
        }
    }

    private TimeClient() {}
}
