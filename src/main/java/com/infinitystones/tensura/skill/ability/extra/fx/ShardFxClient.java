package com.infinitystones.tensura.skill.ability.extra.fx;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.client.render.GlowFx;
import com.infinitystones.tensura.shard.ShardCreate;
import com.infinitystones.tensura.shard.ShardNet;
import com.infinitystones.tensura.shard.client.ShardShaders;
import com.infinitystones.tensura.skill.Stone;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The creation sequence of a mini gem, drawn around the character (everyone nearby sees it): energy threads leave the
 * gauntlet arm, the chosen stone's light splits off and gathers in front of the chest, a bipyramid crystal forms,
 * spins up with crackling arcs and rune rings, condenses with a flash, and flies into the caster. Timeline numbers are
 * shared with {@link ShardCreate} (the server hands over the item when it ends).
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class ShardFxClient {
    private static final int FORM = ShardCreate.FORM, CONDENSE = ShardCreate.CONDENSE, FLY = ShardCreate.FLY, TOTAL = ShardCreate.TOTAL;
    private static final int GOLD = 0xFFD866;
    private static final RandomSource RNG = RandomSource.create();

    private static final class Anim {
        final int entity, rgb;
        int age, cancelAt = -1;
        final List<Vec3> trail = new ArrayList<>();
        Anim(int entity, int rgb) { this.entity = entity; this.rgb = rgb; }
    }

    private static final List<Anim> ANIMS = new ArrayList<>();

    /** A mini gem shattering: holographic shards fly out, glitch and fade. */
    private static final class Burst {
        final Vec3 pos; final int rgb, seed; int age;
        Burst(Vec3 pos, int rgb, int seed) { this.pos = pos; this.rgb = rgb; this.seed = seed; }
    }

    private static final List<Burst> BURSTS = new ArrayList<>();
    private static final int BURST_LIFE = 28, FRAGMENTS = 18;

    public static void burst(ShardNet.BurstMsg m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int st = Mth.clamp(m.stone(), 0, Stone.values().length - 1);
        int rgb = Stone.values()[st].color() & 0xFFFFFF;
        if (BURSTS.size() > 24) BURSTS.remove(0);
        BURSTS.add(new Burst(new Vec3(m.x(), m.y(), m.z()), rgb, RNG.nextInt()));
        var col = new Vector3f((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F);
        for (int i = 0; i < 26; i++) {
            Vec3 d = new Vec3(RNG.nextGaussian(), RNG.nextGaussian(), RNG.nextGaussian()).normalize().scale(0.05 + RNG.nextDouble() * 0.2);
            mc.level.addParticle(i % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : i % 3 == 1 ? ParticleTypes.END_ROD : new DustParticleOptions(col, 0.9F),
                    m.x(), m.y(), m.z(), d.x, d.y, d.z);
        }
    }

    private static float hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535F;
    }

    public static void accept(ShardNet.FxMsg m) {
        if (m.kind() == ShardNet.FX_CANCEL) {
            for (Anim a : ANIMS) if (a.entity == m.entity() && a.cancelAt < 0) a.cancelAt = a.age;
            return;
        }
        ANIMS.removeIf(a -> a.entity == m.entity());
        int st = Mth.clamp(m.stone(), 0, Stone.values().length - 1);
        ANIMS.add(new Anim(m.entity(), Stone.values()[st].color() & 0xFFFFFF));
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { ANIMS.clear(); BURSTS.clear(); }

    // ================================================================== geometry shared by tick + render

    private static Vec3 forward(Entity e, float pt) {
        double yaw = Math.toRadians(Mth.rotLerp(pt, e instanceof LivingEntity l ? l.yBodyRotO : e.yRotO, e instanceof LivingEntity l ? l.yBodyRot : e.getYRot()));
        return new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
    }

    /** Gauntlet hand: the arm opposite the main arm, raised towards the chest as the sequence builds. */
    private static Vec3 hand(Entity e, float pt, float t) {
        Vec3 f = forward(e, pt), right = new Vec3(-f.z, 0, f.x);
        double side = e instanceof LivingEntity l && l.getMainArm() == HumanoidArm.LEFT ? 1 : -1;
        float raise = Mth.clamp(t / 14F, 0, 1);
        raise = raise * raise * (3 - 2 * raise);
        Vec3 base = e.getPosition(pt);
        return base.add(right.scale(0.36 * side * (1 - raise * 0.25))).add(f.scale(0.18 + 0.3 * raise)).add(0, 0.85 + 0.35 * raise, 0);
    }

    private static Vec3 crystal(Entity e, float pt) {
        return e.getPosition(pt).add(forward(e, pt).scale(0.85)).add(0, 1.3, 0);
    }

    private static Vec3 chest(Entity e, float pt) { return e.getPosition(pt).add(0, 1.1, 0); }

    private static int lerpRgb(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int r = (int) Mth.lerp(t, a >> 16 & 255, b >> 16 & 255), g = (int) Mth.lerp(t, a >> 8 & 255, b >> 8 & 255), bl = (int) Mth.lerp(t, a & 255, b & 255);
        return r << 16 | g << 8 | bl;
    }

    private static float ease(float t) { t = Mth.clamp(t, 0, 1); return t * t * (3 - 2 * t); }

    // ================================================================== tick: particles

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        for (Iterator<Burst> it = BURSTS.iterator(); it.hasNext(); ) if (++it.next().age > BURST_LIFE) it.remove();
        if (ANIMS.isEmpty()) return;
        for (Iterator<Anim> it = ANIMS.iterator(); it.hasNext(); ) {
            Anim a = it.next();
            Entity e = mc.level.getEntity(a.entity);
            a.age++;
            if (e == null || a.age > TOTAL + 8 || (a.cancelAt >= 0 && a.age - a.cancelAt > 8)) { it.remove(); continue; }
            Vec3 h = hand(e, 1F, a.age), c = crystal(e, 1F);
            var col = new Vector3f((a.rgb >> 16 & 255) / 255F, (a.rgb >> 8 & 255) / 255F, (a.rgb & 255) / 255F);
            if (a.cancelAt >= 0) {
                if (a.age == a.cancelAt + 1) for (int i = 0; i < 8; i++) mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, RNG.nextGaussian() * 0.06, RNG.nextGaussian() * 0.06, RNG.nextGaussian() * 0.06);
                continue;
            }
            if (a.age < CONDENSE) {
                // motes peel off the gauntlet and stream towards the forming crystal
                Vec3 v = c.subtract(h).scale(0.07);
                for (int i = 0; i < (a.age < FORM ? 3 : 1); i++) {
                    Vec3 p = h.add(RNG.nextGaussian() * 0.04, RNG.nextGaussian() * 0.04, RNG.nextGaussian() * 0.04);
                    boolean gold = a.age < FORM * 0.6 || RNG.nextInt(3) == 0;
                    mc.level.addParticle(gold ? new DustParticleOptions(new Vector3f(1F, 0.85F, 0.4F), 0.8F) : new DustParticleOptions(col, 0.8F),
                            p.x, p.y, p.z, v.x, v.y + 0.01, v.z);
                }
            }
            if (a.age >= FORM && a.age < CONDENSE) {
                float rr = 0.45F + RNG.nextFloat() * 0.3F;
                double ang = RNG.nextDouble() * Math.PI * 2;
                mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, c.x + Math.cos(ang) * rr, c.y + (RNG.nextFloat() - 0.5F) * 0.5F, c.z + Math.sin(ang) * rr,
                        -Math.cos(ang) * 0.03, 0, -Math.sin(ang) * 0.03);
                if (a.age % 3 == 0) mc.level.addParticle(new DustParticleOptions(col, 0.7F), c.x + RNG.nextGaussian() * 0.12, c.y + RNG.nextGaussian() * 0.12, c.z + RNG.nextGaussian() * 0.12, 0, 0.02, 0);
            }
            if (a.age == CONDENSE) {
                for (int i = 0; i < 44; i++) {
                    Vec3 d = new Vec3(RNG.nextGaussian(), RNG.nextGaussian(), RNG.nextGaussian()).normalize().scale(0.1 + RNG.nextDouble() * 0.28);
                    mc.level.addParticle(i % 3 == 0 ? ParticleTypes.END_ROD : i % 3 == 1 ? ParticleTypes.ELECTRIC_SPARK : new DustParticleOptions(col, 1.1F),
                            c.x, c.y, c.z, d.x, d.y, d.z);
                }
            }
            if (a.age >= FLY) {
                Vec3 p = flyPos(a, c, chest(e, 1F), 1F);
                a.trail.add(p);
                if (a.trail.size() > 8) a.trail.remove(0);
                mc.level.addParticle(new DustParticleOptions(col, 0.9F), p.x, p.y, p.z, 0, 0, 0);
                if (a.age % 2 == 0) mc.level.addParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, 0, 0.01, 0);
            }
            if (a.age == TOTAL) {
                Vec3 t = chest(e, 1F);
                for (int i = 0; i < 16; i++) mc.level.addParticle(i % 2 == 0 ? ParticleTypes.END_ROD : new DustParticleOptions(col, 1F), t.x, t.y, t.z,
                        RNG.nextGaussian() * 0.07, RNG.nextGaussian() * 0.07 + 0.03, RNG.nextGaussian() * 0.07);
            }
        }
    }

    private static Vec3 flyPos(Anim a, Vec3 from, Vec3 to, float pt) {
        float u = ease(((a.age + pt - 1F) - FLY) / (float) (TOTAL - FLY));
        u = u * u;
        Vec3 mid = from.add(to).scale(0.5).add(0, 0.55, 0);
        Vec3 p0 = from.lerp(mid, u), p1 = mid.lerp(to, u);
        return p0.lerp(p1, u);
    }

    // ================================================================== render

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || (ANIMS.isEmpty() && BURSTS.isEmpty())) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
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
        for (Anim a : ANIMS) draw(d, a, mc, pt, time, ps, buf, false);
        for (Burst b : BURSTS) drawBurst(d, b, pt, ps, buf, false);
        buf.endBatch(RenderType.lightning());
        for (Anim a : ANIMS) draw(d, a, mc, pt, time, ps, buf, true);
        for (Burst b : BURSTS) drawBurst(d, b, pt, ps, buf, true);
        buf.endBatch();
        ps.popPose();
    }

    private static void draw(Draw d, Anim a, Minecraft mc, float pt, float time, PoseStack ps, MultiBufferSource buf, boolean textured) {
        Entity e = mc.level.getEntity(a.entity);
        if (e == null) return;
        float t = a.age + pt;
        float fade = Mth.clamp(t / 3F, 0, 1);
        if (a.cancelAt >= 0) fade *= Mth.clamp(1 - (t - a.cancelAt) / 8F, 0, 1);
        if (t > TOTAL) fade *= Mth.clamp(1 - (t - TOTAL) / 6F, 0, 1);
        fade *= ShardShaders.glow();   // shader packs: cap the additive strength
        if (fade <= 0.01F) return;
        Vec3 h = hand(e, pt, t), c0 = crystal(e, pt), chest = chest(e, pt);
        int stone = a.rgb;

        // crystal position and size over time
        float growT = ease((t - 4F) / (FORM + 6F));
        float size = 0.05F + 0.17F * growT;                       // half height of the bipyramid
        if (t >= FORM) size = 0.22F + 0.06F * Mth.sin((t - FORM) * 0.35F);
        float cond = (t - CONDENSE) / 6F;                          // 0..1 over the condense flash
        if (t >= CONDENSE) size = Mth.lerp(ease(cond), 0.28F, 0.14F) + (cond < 0.3F ? 0.12F * (1 - cond / 0.3F) : 0);
        Vec3 c = c0.add(0, Mth.sin(t * 0.22F) * 0.025, 0);
        if (t >= FLY) {
            c = flyPos(a, c0, chest, pt);
            size *= 1 - 0.7F * ease((t - FLY) / (TOTAL - FLY));
        }
        float spin = (t * t * 0.0045F + t * 0.12F);               // spins up

        // ---- energy threads: gauntlet -> crystal
        float reveal = ease(t / 14F);
        float threadAl = fade * (t < CONDENSE - 4 ? 1F : Mth.clamp((CONDENSE - t) / 4F + 0.0F, 0, 1)) * (t > FORM ? 0.55F : 1F);
        if (!textured && t < CONDENSE && threadAl > 0.02F) {
            int tcol = lerpRgb(GOLD, stone, (t - 6F) / 14F);
            for (int k = 0; k < 7; k++) {
                float ph = k * 0.9F;
                Vec3 side = Draw.perp(c0.subtract(h));
                Vec3 up = side.cross(c0.subtract(h)).normalize();
                float wob = 0.18F * (1 - growT * 0.4F);
                Vec3 p1 = h.add(0, 0.28, 0).add(side.scale(Math.sin(time * 2.2F + ph) * wob)).add(up.scale(Math.cos(time * 1.7F + ph) * wob));
                Vec3 p2 = c0.lerp(h, 0.35).add(side.scale(Math.cos(time * 2.6F + ph) * wob * 1.2F)).add(up.scale(Math.sin(time * 2.0F + ph) * wob));
                d.color(k % 3 == 0 ? 0xFFFFFF : tcol);
                Vec3 prev = h;
                int segs = 14;
                for (int s = 1; s <= segs; s++) {
                    float u = s / (float) segs;
                    if (u > reveal) break;
                    Vec3 p = bez(h, p1, p2, c0, u);
                    float tip = 1 - Math.abs(0.5F - u) * 0.6F;
                    d.line(prev, p, 0.012F + 0.01F * tip, 0.75F * threadAl * tip);
                    prev = p;
                }
                float pu = (t * 0.07F + k / 7F) % 1F;
                if (pu <= reveal) d.spark(bez(h, p1, p2, c0, pu), 0.05F + 0.03F * Mth.sin(time * 9 + k), 0.9F * threadAl);
            }
        }
        if (textured && t < CONDENSE + 4) {
            float al = fade * Mth.clamp(t / 6F, 0, 1);
            d.flare(ps, buf, h, 0.45F + 0.25F * Mth.sin(time * 11), lerpRgb(GOLD, stone, t / 30F), 0.85F * al * Mth.clamp((CONDENSE - t) / 10F + 0.2F, 0, 1), time * 2);
        }

        // ---- the crystal
        if (!textured) {
            float s = Math.max(0.01F, size) * fade;
            crystal(d, c, s, spin, stone, fade, t >= CONDENSE && t < CONDENSE + 3);
            // crackling arcs: a new set of jagged bolts several times a second
            if (t >= FORM - 4 && t < CONDENSE) {
                long seed = (long) (t * 0.7F);
                java.util.Random r = new java.util.Random(seed * 7919L + a.entity);
                int n = 5;
                for (int k = 0; k < n; k++) {
                    Vec3 dir = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.8, r.nextGaussian()).normalize();
                    double len = 0.35 + r.nextDouble() * 0.45;
                    Vec3 prev = c;
                    d.color(k % 2 == 0 ? 0xFFFFFF : stone);
                    for (int sg = 1; sg <= 4; sg++) {
                        Vec3 p = c.add(dir.scale(len * sg / 4.0)).add(r.nextGaussian() * 0.05, r.nextGaussian() * 0.05, r.nextGaussian() * 0.05);
                        d.line(prev, p, 0.008F, 0.8F * fade * (0.5F + 0.5F * (float) r.nextDouble()));
                        prev = p;
                    }
                }
            }
            // rune ring edges (thin glowing rings around the crystal, shrinking away at the condense)
            if (t >= FORM - 2 && t < CONDENSE + 4) {
                float k = t < CONDENSE ? ease((t - FORM + 2) / 8F) : 1 - ease((t - CONDENSE) / 4F);
                d.color(stone);
                d.ringN(c, new Vec3(0, 1, 0), 0.62F * k, 0.012F, 0.9F * fade, spin * 0.6F);
                Vec3 tilt = new Vec3(Math.sin(spin * 0.2F) * 0.6, 1, Math.cos(spin * 0.2F) * 0.6).normalize();
                d.ringN(c, tilt, 0.46F * k, 0.01F, 0.8F * fade, -spin);
                d.color(0xFFFFFF);
                d.ringN(c, new Vec3(0, 1, 0), 0.7F * k, 0.006F, 0.45F * fade, -spin * 0.3F);
            }
            // condense shockwave
            if (t >= CONDENSE && t < CONDENSE + 9) {
                float k = (t - CONDENSE) / 9F;
                d.color(stone);
                d.ringN(c0, new Vec3(0, 1, 0), 0.2F + 1.6F * ease(k), 0.03F, 0.9F * (1 - k) * fade, 0);
                d.color(0xFFFFFF);
                d.ringN(c0, new Vec3(0.2, 1, 0.1).normalize(), 0.15F + 1.1F * ease(k), 0.015F, 0.8F * (1 - k) * fade, 1);
            }
            // flight trail
            if (t >= FLY) {
                d.color(stone);
                Vec3 prev = a.trail.isEmpty() ? c : a.trail.get(0);
                int tn = a.trail.size();
                for (int i = 1; i <= tn; i++) {
                    Vec3 cur = i == tn ? c : a.trail.get(i);
                    d.line(prev, cur, 0.022F * i / tn, 0.85F * i / tn * fade);
                    prev = cur;
                }
            }
        } else {
            float k = t < CONDENSE ? 1F : 1 - ease((t - CONDENSE) / 4F);
            // soft glow of the crystal, rune textures, condense flash
            d.flare(ps, buf, c, 0.35F + size * 2.4F, stone, 0.9F * fade, time * 2);
            if (t >= FORM - 2 && t < CONDENSE + 4) {
                float rk = t < CONDENSE ? ease((t - FORM + 2) / 8F) : k;
                ps.pushPose();
                ps.translate(c.x, c.y, c.z);
                GlowFx.groundQuad(ps, buf, GlowFx.RUNE_RING, 0.62F * rk, stone, 0.95F * fade, spin * 0.6F);
                ps.pushPose();
                ps.mulPose(Axis.XP.rotationDegrees(55F));
                ps.mulPose(Axis.YP.rotationDegrees(spin * 25F));
                GlowFx.groundQuad(ps, buf, GlowFx.RUNE_RING, 0.46F * rk, lerpRgb(stone, 0xFFFFFF, 0.5F), 0.8F * fade, -spin);
                ps.popPose();
                ps.popPose();
            }
            if (t >= CONDENSE && t < CONDENSE + 10) {
                float f = 1 - (t - CONDENSE) / 10F;
                d.flare(ps, buf, c0, (2.4F * f + 0.4F) * ShardShaders.radius(), 0xFFFFFF, f * fade, -time * 3);
                d.flare(ps, buf, c0, (3.4F * f + 0.4F) * ShardShaders.radius(), stone, 0.8F * f * fade, time);
            }
            if (t >= TOTAL - 2 && t < TOTAL + 5) {
                float f = 1 - (t - TOTAL + 2) / 7F;
                d.flare(ps, buf, chest, 1.1F * f + 0.2F, stone, f * fade, time * 2);
            }
        }
    }

    private static void drawBurst(Draw d, Burst b, float pt, PoseStack ps, MultiBufferSource buf, boolean textured) {
        float t = b.age + pt;
        float life = Mth.clamp(t / BURST_LIFE, 0, 1);
        float cap = ShardShaders.glow();
        float al = (float) Math.pow(1 - life, 1.2) * cap;
        if (al <= 0.01F) return;
        int rgb = b.rgb;
        if (textured) {
            float f = Mth.clamp(1 - t / 9F, 0, 1);
            if (f > 0) {
                d.flare(ps, buf, b.pos, (0.5F + 1.9F * f) * ShardShaders.radius(), 0xFFFFFF, f * cap, t);
                d.flare(ps, buf, b.pos, (0.8F + 2.6F * f) * ShardShaders.radius(), rgb, 0.8F * f * cap, -t * 0.5F);
            }
            return;
        }
        // digital glitch at the origin: horizontal tear bars that jump around for the first ticks
        if (t < 9) {
            d.color(0xFFFFFF);
            for (int i = 0; i < 4; i++) {
                float jump = hash(b.seed + i, (int) (t * 1.5F));
                double dy = (jump - 0.5) * 0.7, len = 0.12 + hash(b.seed, i + (int) t) * 0.4;
                Vec3 o = b.pos.add(0, dy, 0);
                Vec3 axis = i % 2 == 0 ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
                d.line(o.subtract(axis.scale(len)), o.add(axis.scale(len)), 0.006F, 0.8F * (1 - t / 9F) * cap);
            }
        }
        d.color(rgb);
        d.ringN(b.pos, new Vec3(0, 1, 0), 0.1F + 1.3F * easeOut(life * 1.6F), 0.03F, 0.7F * (1 - life) * cap, 0);
        for (int i = 0; i < FRAGMENTS; i++) {
            if (hash(b.seed + i, b.age / 2) < 0.16F + life * 0.2F) continue;                       // glitch: flickers out for a moment
            Vec3 dir = new Vec3(hash(b.seed, i * 3) - 0.5, hash(b.seed, i * 3 + 1) - 0.4, hash(b.seed, i * 3 + 2) - 0.5).normalize();
            double speed = 0.05 + hash(b.seed, i + 77) * 0.16;
            double dist = speed * t * (1 - t / (2.0 * BURST_LIFE));
            Vec3 p = b.pos.add(dir.scale(dist)).add(0, -0.0042 * t * t, 0);
            if (hash(b.seed + i, b.age / 3 + 9) < 0.28F)                                           // glitch: snaps to a coarse grid
                p = new Vec3(Math.round(p.x * 20) / 20.0, Math.round(p.y * 20) / 20.0, Math.round(p.z * 20) / 20.0);
            Vec3 axis = new Vec3(hash(b.seed, i + 200) - 0.5, hash(b.seed, i + 300) - 0.5, hash(b.seed, i + 400) - 0.5).normalize();
            Vec3 u0 = Draw.perp(axis);
            double ang = t * (0.3 + hash(b.seed, i + 500) * 0.5);
            Vec3 u = u0.scale(Math.cos(ang)).add(axis.cross(u0).scale(Math.sin(ang)));
            Vec3 v = axis.cross(u);
            float size = 0.045F + hash(b.seed, i + 600) * 0.07F;
            Vec3 a1 = p.add(u.scale(size)), a2 = p.add(u.scale(-size * 0.5).add(v.scale(size * 0.87))), a3 = p.add(u.scale(-size * 0.5).subtract(v.scale(size * 0.87)));
            boolean white = hash(b.seed + i, b.age) < 0.3F;
            d.color(white ? 0xFFFFFF : rgb);
            d.quad(a1, a2, a3, a3, al * 0.55F);
            d.color(0xFFFFFF);
            d.line(a1, a2, 0.004F, al * 0.9F);
            d.line(a2, a3, 0.004F, al * 0.7F);
            d.line(a3, a1, 0.004F, al * 0.9F);
        }
    }

    private static float easeOut(float t) { t = Mth.clamp(t, 0, 1); return 1 - (1 - t) * (1 - t) * (1 - t); }

    /** Hexagonal bipyramid with translucent faces, bright edges and a white core. */
    private static void crystal(Draw d, Vec3 c, float h, float spin, int rgb, float fade, boolean white) {
        float r = h * 0.62F;
        Vec3 top = c.add(0, h, 0), bot = c.subtract(0, h, 0);
        Vec3[] ring = new Vec3[6];
        for (int i = 0; i < 6; i++) {
            double ang = spin + i * Math.PI / 3;
            ring[i] = c.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
        }
        d.color(white ? lerpRgb(rgb, 0xFFFFFF, 0.8F) : rgb);
        for (int i = 0; i < 6; i++) {
            Vec3 a = ring[i], b = ring[(i + 1) % 6];
            float shade = 0.28F + 0.22F * (float) Math.sin(spin * 1.3 + i * 1.7);
            d.quad(top, a, b, b, shade * fade);
            d.quad(bot, b, a, a, shade * fade * 0.85F);
        }
        d.color(0xFFFFFF);
        for (int i = 0; i < 6; i++) {
            Vec3 a = ring[i], b = ring[(i + 1) % 6];
            d.line(a, b, 0.006F, 0.9F * fade);
            d.line(top, a, 0.005F, 0.7F * fade);
            d.line(bot, a, 0.005F, 0.6F * fade);
        }
        d.color(rgb);
        d.spark(c, h * 0.7F, 0.95F * fade);
    }

    private static Vec3 bez(Vec3 a, Vec3 b, Vec3 c, Vec3 dd, float t) {
        float u = 1 - t;
        return a.scale(u * u * u).add(b.scale(3 * u * u * t)).add(c.scale(3 * u * t * t)).add(dd.scale(t * t * t));
    }

    private ShardFxClient() {}
}
