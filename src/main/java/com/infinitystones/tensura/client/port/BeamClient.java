package com.infinitystones.tensura.client.port;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.ported.BeamNet;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * Client half of the energy beam: every client draws every synced beam (multiplayer), traced from the caster's own
 * interpolated eye/rotation so it follows the aim smoothly. Look = the original Palladium beams (glow + core colour,
 * rotation, "palladium:lightning" bolts for the gauntlet beam, lava at the hit) made much richer: layered additive
 * core/glow/haze, travelling pulses, a rotating double helix, lightning bolts, shock rings on lethal levels, muzzle
 * and impact flares, sparks/embers/smoke particles, a looping hum and a little camera shake on the explosive level.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class BeamClient {
    private static final Map<Integer, Long> BORN = new HashMap<>();
    private static final Map<Integer, BeamHum> HUMS = new HashMap<>();
    private static final RandomSource RNG = RandomSource.create();

    /** Geometry of one beam this frame. */
    private record Shape(Vec3 from, Vec3 to, Vec3 eye, Vec3 dir, boolean hit, boolean hitBlock) {}

    // ---------------- tick: expiry, particles, sound ----------------

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        BeamNet.clientTicks++;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) { clear(); return; }
        long now = BeamNet.clientTicks;
        for (Integer id : java.util.List.copyOf(BeamNet.CLIENT.keySet())) {
            Long seen = BeamNet.CLIENT_SEEN.get(id);
            Entity en = level.getEntity(id);
            if (seen == null || now - seen > 30 || !(en instanceof LivingEntity) || !en.isAlive()) {
                BeamNet.CLIENT.remove(id); BeamNet.CLIENT_SEEN.remove(id);
            }
        }
        BORN.keySet().removeIf(id -> !BeamNet.CLIENT.containsKey(id));
        HUMS.entrySet().removeIf(h -> { if (!BeamNet.CLIENT.containsKey(h.getKey())) { h.getValue().finish(); return true; } return false; });

        for (Map.Entry<Integer, BeamNet.Visual> entry : BeamNet.CLIENT.entrySet()) {
            int id = entry.getKey();
            BeamNet.Visual v = entry.getValue();
            if (!(level.getEntity(id) instanceof LivingEntity caster)) continue;
            BORN.putIfAbsent(id, now);
            BeamHum hum = HUMS.get(id);
            if (hum == null || hum.isStopped()) {
                hum = new BeamHum(caster);
                HUMS.put(id, hum);
                mc.getSoundManager().play(hum);
            }
            hum.level = v.level();
            particles(level, caster, v, shape(caster, v, 1F, mc), now - BORN.get(id));
        }
    }

    private static void particles(ClientLevel level, LivingEntity caster, BeamNet.Visual v, Shape s, long age) {
        int lvl = v.level();
        int glow = v.rainbow() ? rainbow(BeamNet.clientTicks / 20F, 0) : v.glow();
        DustParticleOptions dust = new DustParticleOptions(rgb(glow), 0.8F + lvl * 0.35F);
        DustParticleOptions white = new DustParticleOptions(rgb(lerp(v.core(), 0xFFFFFF, 0.5F)), 0.6F + lvl * 0.2F);
        double len = s.from.distanceTo(s.to);
        Vec3 dir = s.dir;
        // energy shedding off the beam
        int along = Math.min(14, 2 + lvl * 3 + (int) (len / 20));
        for (int k = 0; k < along; k++) {
            Vec3 pt = s.from.add(dir.scale(RNG.nextDouble() * len));
            Vec3 jitter = randomPerp(dir).scale(v.width() * (1.2 + RNG.nextDouble()));
            level.addParticle(RNG.nextInt(3) == 0 ? white : dust, pt.x + jitter.x, pt.y + jitter.y, pt.z + jitter.z,
                    jitter.x * 0.08, jitter.y * 0.08 + 0.01, jitter.z * 0.08);
        }
        if (RNG.nextInt(3) == 0) level.addParticle(ParticleTypes.END_ROD, s.from.x, s.from.y, s.from.z,
                RNG.nextGaussian() * 0.02, RNG.nextGaussian() * 0.02, RNG.nextGaussian() * 0.02);
        if (!s.hit) return;
        Vec3 at = s.to;
        Vec3 back = dir.scale(-0.08);
        // impact: sparks, embers, smoke
        for (int k = 0; k < 3 + lvl * 3; k++)
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z,
                    back.x * 3 + RNG.nextGaussian() * 0.25, Math.abs(RNG.nextGaussian()) * 0.25, back.z * 3 + RNG.nextGaussian() * 0.25);
        for (int k = 0; k < 2 + lvl * 2; k++)
            level.addParticle(dust, at.x + RNG.nextGaussian() * 0.15, at.y + RNG.nextGaussian() * 0.15, at.z + RNG.nextGaussian() * 0.15,
                    RNG.nextGaussian() * 0.1, RNG.nextDouble() * 0.12, RNG.nextGaussian() * 0.1);
        if (s.hitBlock) {
            if (RNG.nextInt(2) == 0) level.addParticle(ParticleTypes.SMALL_FLAME, at.x, at.y, at.z, RNG.nextGaussian() * 0.04, 0.05, RNG.nextGaussian() * 0.04);
            if (RNG.nextInt(3) == 0) level.addParticle(ParticleTypes.SMOKE, at.x, at.y + 0.1, at.z, 0, 0.06, 0);
            if (lvl >= 1 && RNG.nextInt(5) == 0) level.addParticle(ParticleTypes.LAVA, at.x, at.y, at.z, 0, 0, 0);
            if (lvl >= 2 && RNG.nextInt(4) == 0) level.addParticle(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.2, at.z, 0, 0.05, 0);
        }
        if (lvl == 3 && age % 6 == 0) level.addParticle(ParticleTypes.FLASH, at.x, at.y, at.z, 0, 0, 0);
        if (lvl == 3 && RNG.nextInt(3) == 0) level.addParticle(ParticleTypes.SONIC_BOOM, s.from.x + dir.x * 2, s.from.y + dir.y * 2, s.from.z + dir.z * 2, 0, 0, 0);
    }

    // ---------------- render ----------------

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || BeamNet.CLIENT.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        Camera cam = e.getCamera();
        Vec3 cp = cam.getPosition();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(-cp.x, -cp.y, -cp.z);
        Matrix4f m = ps.last().pose();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(BeamRenderType.BEAM);
        float time = (BeamNet.clientTicks + pt) / 20F;
        Vector3f camLeft = cam.getLeftVector(), camUp = cam.getUpVector();
        for (Map.Entry<Integer, BeamNet.Visual> entry : BeamNet.CLIENT.entrySet()) {
            if (!(mc.level.getEntity(entry.getKey()) instanceof LivingEntity caster)) continue;
            BeamNet.Visual v = entry.getValue();
            long born = BORN.getOrDefault(entry.getKey(), BeamNet.clientTicks);
            float age = BeamNet.clientTicks - born + pt;
            drawBeam(vc, m, cp, camLeft, camUp, shape(caster, v, pt, mc), v, time, age, entry.getKey());
        }
        buf.endBatch(BeamRenderType.BEAM);
        ps.popPose();
    }

    private static void drawBeam(VertexConsumer vc, Matrix4f m, Vec3 cp, Vector3f camLeft, Vector3f camUp, Shape s, BeamNet.Visual v,
                                 float time, float age, int seed) {
        int lvl = v.level();
        float grow = Mth.clamp(age / 4F, 0F, 1F); // the beam shoots out over 4 ticks
        float ease = 1 - (1 - grow) * (1 - grow) * (1 - grow);
        Vec3 a = s.from;
        Vec3 full = s.to.subtract(a);
        double len = full.length() * ease;
        if (len < 0.05) return;
        Vec3 dir = full.normalize();
        Vec3 b = a.add(dir.scale(len));
        int glow = v.rainbow() ? rainbow(time, 0) : v.glow();
        int core = v.core();
        float flicker = 1F + 0.07F * Mth.sin(time * v.rotationSpeed() * 1.7F) + 0.04F * Mth.sin(time * 53F);
        float w = v.width() * flicker * (0.6F + 0.4F * ease);

        // body: haze, glow, inner, core — tapered at the muzzle, travelling pulses
        int segs = Mth.clamp((int) (len * 2.5), 8, 260);
        float[][] layers = { {3.4F, 0.07F}, {2.0F, 0.22F}, {1.15F, 0.45F}, {0.42F, 0.95F} };
        for (int li = 0; li < layers.length; li++) {
            int col = li == 3 ? lerp(core, 0xFFFFFF, 0.55F) : li == 2 ? lerp(glow, core, 0.45F) : glow;
            for (int k = 0; k < segs; k++) {
                double d0 = len * k / segs, d1 = len * (k + 1) / segs;
                Vec3 p0 = a.add(dir.scale(d0)), p1 = a.add(dir.scale(d1));
                if (v.rainbow() && li < 3) col = rainbow(time, (float) (d0 * 0.04));
                float w0 = w * layers[li][0] * profile(d0, len, time, lvl), w1 = w * layers[li][0] * profile(d1, len, time, lvl);
                ribbon(vc, m, cp, p0, p1, w0, w1, col, layers[li][1], layers[li][1]);
            }
        }

        // rotating double helix (original rotation_speed)
        double helixLen = Math.min(len, 64);
        Vec3[] basis = basis(dir);
        for (int strand = 0; strand < 2; strand++) {
            Vec3 prev = null;
            for (double d = 0.15; d <= helixLen; d += 0.3) {
                double ang = d * 1.3 - time * v.rotationSpeed() * 0.35 + strand * Math.PI;
                double r = w * (1.5 + 0.25 * Math.sin(d * 0.7 + time * 4));
                Vec3 pt = a.add(dir.scale(d)).add(basis[0].scale(Math.cos(ang) * r)).add(basis[1].scale(Math.sin(ang) * r));
                if (prev != null) {
                    float fade = (float) (1 - d / helixLen);
                    ribbon(vc, m, cp, prev, pt, w * 0.16F, w * 0.16F, lerp(glow, 0xFFFFFF, 0.25F), 0.55F * fade, 0.55F * fade);
                }
                prev = pt;
            }
        }

        // lightning bolts (palladium:lightning: segments 10, spread 2) — re-rolled every 2 ticks
        if (v.lightning()) {
            int bolts = 2 + lvl;
            long frame = (long) (time * 10);
            for (int bi = 0; bi < bolts; bi++) {
                RandomSource r = RandomSource.create(seed * 31L + frame * 977L + bi * 7919L);
                int n = Math.max(10, (int) (len / 2.5));
                float spread = w * (1.6F + lvl * 0.5F);
                Vec3 prev = a;
                for (int k = 1; k <= n; k++) {
                    double d = len * k / n;
                    Vec3 off = k == n ? Vec3.ZERO : basis[0].scale((r.nextFloat() * 2 - 1) * spread).add(basis[1].scale((r.nextFloat() * 2 - 1) * spread));
                    Vec3 pt = a.add(dir.scale(d)).add(off);
                    ribbon(vc, m, cp, prev, pt, w * 0.28F, w * 0.28F, glow, 0.5F, 0.5F);
                    ribbon(vc, m, cp, prev, pt, w * 0.09F, w * 0.09F, 0xFFFFFF, 0.9F, 0.9F);
                    prev = pt;
                }
            }
        }

        // shock rings racing along the beam (lethal / explosive)
        if (lvl >= 2) {
            float speed = 45F;
            for (int ri = 0; ri < 6; ri++) {
                double d = ((time * speed) + ri * (len / 6.0)) % len;
                float fade = (float) Math.sin(Math.PI * d / len);
                ring(vc, m, a.add(dir.scale(d)), basis, w * (2.4F + lvl * 0.4F), w * 0.22F, dir, glow, 0.45F * fade);
            }
        }

        // magic circles at the muzzle (stone colours): one on strong, two on lethal, a stacked cannon with rays on explosive
        if (lvl >= 1) {
            int c2 = lerp(glow, 0xFFFFFF, 0.5F);
            float cr = w * (6F + lvl * 2.5F) * (0.4F + 0.6F * ease);
            int style = lvl == 3 ? (3 | 4 | 16) : lvl == 2 ? 1 : 0;
            MagicCircles.draw(vc, m, a.add(dir.scale(0.9 + lvl * 0.3)), dir, cr, glow, c2, 0.85F, time, seed, style);
            if (lvl == 3) MagicCircles.draw(vc, m, a.add(dir.scale(Math.min(len * 0.5, 14))), dir, cr * 1.9F, glow, c2, 0.5F, -time * 0.7F, seed + 7, 1 | 8);
            if (lvl >= 2 && grow >= 1F && s.hit) MagicCircles.draw(vc, m, b.subtract(dir.scale(0.05)), dir.scale(-1), cr * 0.9F, glow, c2, 0.6F, time * 1.3F, seed + 3, 8);
        }

        // muzzle flare and impact flare (billboards)
        float pulse = 1F + 0.18F * Mth.sin(time * 22F);
        flare(vc, m, a, camLeft, camUp, w * 2.6F * pulse, lerp(glow, 0xFFFFFF, 0.3F), 0.75F, time * 2F);
        if (grow >= 1F && (s.hit || v.pierce())) {
            float size = w * (5.5F + lvl * 1.5F) * pulse;
            flare(vc, m, b, camLeft, camUp, size, glow, 0.6F, -time * 3F);
            flare(vc, m, b, camLeft, camUp, size * 0.45F, 0xFFFFFF, 0.9F, time * 5F);
            if (s.hitBlock || v.pierce()) {
                float phase = (time * 3F) % 1F; // expanding shock ring on the surface
                ring(vc, m, b, basis, size * (0.5F + phase * 1.5F), w * 0.3F, dir, glow, 0.5F * (1 - phase));
            }
        }
    }

    /** Width profile along the beam: grows out of the hand, travelling pulses, fat on lethal levels. */
    private static float profile(double d, double len, float time, int lvl) {
        float taper = (float) Mth.clamp(d / 0.7, 0.15, 1.0);
        float pulse = 1F + (0.10F + lvl * 0.05F) * Mth.sin((float) (d * 1.6 - time * 26F));
        float end = (float) Mth.clamp((len - d) / 0.4 + 0.4, 0.4, 1.0);
        return taper * pulse * end;
    }

    // ---------------- geometry helpers ----------------

    /** Camera-facing quad strip from p0 (half width w0) to p1 (half width w1). */
    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 cam, Vec3 p0, Vec3 p1, float w0, float w1, int rgb, float a0, float a1) {
        Vec3 seg = p1.subtract(p0);
        Vec3 toCam = cam.subtract(p0.add(p1).scale(0.5));
        Vec3 side = seg.cross(toCam);
        double l = side.length();
        if (l < 1e-6) return;
        side = side.scale(1 / l);
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, bl = rgb & 255;
        int ia0 = (int) (Mth.clamp(a0, 0, 1) * 255), ia1 = (int) (Mth.clamp(a1, 0, 1) * 255);
        vtx(vc, m, p0.add(side.scale(w0)), r, g, bl, ia0);
        vtx(vc, m, p0.subtract(side.scale(w0)), r, g, bl, ia0);
        vtx(vc, m, p1.subtract(side.scale(w1)), r, g, bl, ia1);
        vtx(vc, m, p1.add(side.scale(w1)), r, g, bl, ia1);
    }

    private static void vtx(VertexConsumer vc, Matrix4f m, Vec3 p, int r, int g, int b, int a) {
        vc.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(r, g, b, a);
    }

    /** Soft round billboard (bright centre, transparent rim) plus a 4-point star. */
    private static void flare(VertexConsumer vc, Matrix4f m, Vec3 c, Vector3f left, Vector3f up, float radius, int rgb, float alpha, float spin) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        int ac = (int) (alpha * 255);
        int n = 20;
        for (int k = 0; k < n; k++) {
            double t0 = Math.PI * 2 * k / n, t1 = Math.PI * 2 * (k + 1) / n;
            Vec3 e0 = c.add(dir2(left, up, t0).scale(radius)), e1 = c.add(dir2(left, up, t1).scale(radius));
            vtx(vc, m, c, r, g, b, ac); vtx(vc, m, c, r, g, b, ac);
            vtx(vc, m, e1, r, g, b, 0); vtx(vc, m, e0, r, g, b, 0);
        }
        for (int k = 0; k < 4; k++) { // star streaks
            double t = spin + Math.PI / 2 * k;
            Vec3 tip = c.add(dir2(left, up, t).scale(radius * 2.2));
            Vec3 side = dir2(left, up, t + Math.PI / 2).scale(radius * 0.09);
            vtx(vc, m, c.add(side), r, g, b, ac); vtx(vc, m, c.subtract(side), r, g, b, ac);
            vtx(vc, m, tip, 255, 255, 255, 0); vtx(vc, m, tip, 255, 255, 255, 0);
        }
    }

    /** Ring around the beam axis at c (band of thickness th along the axis). */
    private static void ring(VertexConsumer vc, Matrix4f m, Vec3 c, Vec3[] basis, float radius, float th, Vec3 axis, int rgb, float alpha) {
        if (alpha <= 0.01F) return;
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255, a = (int) (Mth.clamp(alpha, 0, 1) * 255);
        int n = 24;
        Vec3 half = axis.scale(th);
        for (int k = 0; k < n; k++) {
            double t0 = Math.PI * 2 * k / n, t1 = Math.PI * 2 * (k + 1) / n;
            Vec3 p0 = c.add(basis[0].scale(Math.cos(t0) * radius)).add(basis[1].scale(Math.sin(t0) * radius));
            Vec3 p1 = c.add(basis[0].scale(Math.cos(t1) * radius)).add(basis[1].scale(Math.sin(t1) * radius));
            vtx(vc, m, p0.add(half), r, g, b, a); vtx(vc, m, p0.subtract(half), r, g, b, a);
            vtx(vc, m, p1.subtract(half), r, g, b, a); vtx(vc, m, p1.add(half), r, g, b, a);
            // soft outer fringe
            Vec3 q0 = c.add(basis[0].scale(Math.cos(t0) * radius * 1.25)).add(basis[1].scale(Math.sin(t0) * radius * 1.25));
            Vec3 q1 = c.add(basis[0].scale(Math.cos(t1) * radius * 1.25)).add(basis[1].scale(Math.sin(t1) * radius * 1.25));
            vtx(vc, m, p0, r, g, b, a / 2); vtx(vc, m, p1, r, g, b, a / 2);
            vtx(vc, m, q1, r, g, b, 0); vtx(vc, m, q0, r, g, b, 0);
        }
    }

    private static Vec3 dir2(Vector3f left, Vector3f up, double t) {
        return new Vec3(left.x() * Math.cos(t) + up.x() * Math.sin(t), left.y() * Math.cos(t) + up.y() * Math.sin(t), left.z() * Math.cos(t) + up.z() * Math.sin(t));
    }

    private static Vec3[] basis(Vec3 dir) {
        Vec3 ref = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 u = dir.cross(ref).normalize();
        Vec3 v = dir.cross(u).normalize();
        return new Vec3[]{u, v};
    }

    private static Vec3 randomPerp(Vec3 dir) {
        Vec3[] b = basis(dir);
        double t = RNG.nextDouble() * Math.PI * 2;
        return b[0].scale(Math.cos(t)).add(b[1].scale(Math.sin(t)));
    }

    /** Where the beam starts (hand / head, like the original body_part) and where it stops (same trace as the server). */
    private static Shape shape(LivingEntity caster, BeamNet.Visual v, float pt, Minecraft mc) {
        Vec3 eye = caster.getEyePosition(pt);
        Vec3 look = caster.getViewVector(pt);
        Vec3 end = eye.add(look.scale(v.maxDistance()));
        boolean hit = false, hitBlock = false;
        if (!v.pierce()) {
            BlockHitResult block = caster.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (block.getType() != HitResult.Type.MISS) { end = block.getLocation(); hit = true; hitBlock = true; }
            EntityHitResult ent = ProjectileUtil.getEntityHitResult(caster.level(), caster, eye, end, new AABB(eye, end).inflate(1D),
                    en -> en.isPickable() && !en.isSpectator() && en != caster, 0.3F + 0.15F * v.level());
            if (ent != null) { end = ent.getLocation(); hit = true; hitBlock = false; }
        }
        boolean firstPerson = caster == mc.player && mc.options.getCameraType().isFirstPerson();
        HumanoidArm main = caster instanceof Player pl ? pl.getMainArm() : HumanoidArm.RIGHT;
        int mainSide = main == HumanoidArm.RIGHT ? 1 : -1;
        int side = v.origin() == BeamNet.ORIGIN_OFF_ARM ? -mainSide : mainSide;
        Vec3 from;
        if (v.origin() == BeamNet.ORIGIN_HEAD) {
            from = eye.add(look.scale(firstPerson ? 0.5 : 0.3)).add(0, firstPerson ? -0.08 : 0, 0);
        } else if (firstPerson) {
            Vec3 right = look.cross(new Vec3(0, 1, 0));
            right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
            Vec3 up = right.cross(look).normalize();
            from = eye.add(look.scale(0.6)).add(right.scale(side * 0.3)).add(up.scale(-0.26));
        } else {
            float scale = caster.getScale();
            float yaw = Mth.lerp(pt, caster.yBodyRotO, caster.yBodyRot) * Mth.DEG_TO_RAD;
            Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
            Vec3 shoulder = caster.getPosition(pt).add(0, caster.getBbHeight() * 0.77, 0).add(right.scale(side * 0.36 * scale));
            from = shoulder.add(look.scale(0.72 * scale));
        }
        return new Shape(from, end, eye, end.subtract(from).normalize(), hit, hitBlock);
    }

    // ---------------- Alt + scroll (temporary, only while the local beam is firing) ----------------

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (com.infinitystones.tensura.skill.ability.ported.Levels.WHEEL_SETTINGS || mc.player == null || mc.screen != null || !BeamNet.CLIENT.containsKey(mc.player.getId())) return;
        long win = mc.getWindow().getWindow();
        if (!InputConstants.isKeyDown(win, GLFW.GLFW_KEY_LEFT_ALT) && !InputConstants.isKeyDown(win, GLFW.GLFW_KEY_RIGHT_ALT)) return;
        double d = e.getScrollDeltaY();
        if (d == 0) return;
        PacketDistributor.sendToServer(new BeamNet.Step(d > 0 ? 1 : -1));
        e.setCanceled(true);
    }

    // ---------------- camera shake on the explosive level ----------------

    @SubscribeEvent
    public static void shake(ViewportEvent.ComputeCameraAngles e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || BeamNet.CLIENT.isEmpty()) return;
        float strength = 0;
        for (Map.Entry<Integer, BeamNet.Visual> entry : BeamNet.CLIENT.entrySet()) {
            if (entry.getValue().level() < 3) continue;
            Entity en = mc.level == null ? null : mc.level.getEntity(entry.getKey());
            if (en == null) continue;
            double dist = en.distanceTo(mc.player);
            strength = Math.max(strength, en == mc.player ? 0.35F : (float) Math.max(0, 0.3 - dist / 80));
        }
        if (strength <= 0) return;
        float t = (float) ((BeamNet.clientTicks + e.getPartialTick()) * 1.7);
        e.setYaw(e.getYaw() + Mth.sin(t * 3.1F) * strength);
        e.setPitch(e.getPitch() + Mth.cos(t * 2.3F) * strength);
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut e) { clear(); }

    private static void clear() {
        BeamNet.CLIENT.clear(); BeamNet.CLIENT_SEEN.clear(); BORN.clear();
        HUMS.values().forEach(BeamHum::finish); HUMS.clear();
    }

    // ---------------- colour helpers ----------------

    private static Vector3f rgb(int c) { return new Vector3f(((c >> 16) & 255) / 255F, ((c >> 8) & 255) / 255F, (c & 255) / 255F); }

    private static int lerp(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255), g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255), bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    private static int rainbow(float time, float offset) {
        return java.awt.Color.HSBtoRGB((time * 0.6F + offset) % 1F, 0.8F, 1F) & 0xFFFFFF;
    }

    /** Looping hum that follows the caster while the beam fires; louder and higher on stronger levels. */
    private static final class BeamHum extends AbstractTickableSoundInstance {
        private final LivingEntity caster;
        int level;
        BeamHum(LivingEntity caster) {
            super(SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, RandomSource.create());
            this.caster = caster;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.01F;
            this.pitch = 1.3F;
            this.x = caster.getX(); this.y = caster.getEyeY(); this.z = caster.getZ();
        }
        @Override public void tick() {
            if (caster.isRemoved()) { stop(); return; }
            x = caster.getX(); y = caster.getEyeY(); z = caster.getZ();
            float target = 0.35F + level * 0.08F;
            volume = Mth.lerp(0.3F, volume, target);
            pitch = 1.25F + level * 0.18F;
        }
        void finish() { stop(); }
        @Override public boolean canStartSilent() { return true; }
    }

    private BeamClient() {}
}
