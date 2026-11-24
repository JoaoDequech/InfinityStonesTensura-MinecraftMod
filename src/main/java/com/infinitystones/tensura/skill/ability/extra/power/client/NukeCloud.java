package com.infinitystones.tensura.skill.ability.extra.power.client;

import com.infinitystones.tensura.skill.ability.extra.power.PowerNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The aftermath of a Celestial Annihilation, drawn only on the client of everyone who can see it: a blinding white-out,
 * a rising fireball that becomes a mushroom cloud (stem + rolling toroidal cap, grows for about 10 s and thins out
 * over the next 20 s), a condensation ring (Wilson cloud) and a dust wall racing out along the ground, ash falling for
 * the rest of the half minute, and sounds that reach each listener after a delay that grows with distance.
 * Everything honours the player's particle setting (Minimal: only the flash, glow and sounds).
 */
public final class NukeCloud {
    /** Ticks the cloud lives after the impact: ~10 s of growth, ~20 s of dissipation. */
    public static final int RISE = 200, TOTAL = 640;
    /** Stylised speed of sound in blocks per tick (a listener 240 blocks away hears the blast 3 s later). */
    private static final double SOUND_SPEED = 4.0;

    private static final class Pending {
        final long at; final SoundEvent sound; final float vol, pitch; final Vec3 pos;
        Pending(long at, SoundEvent s, float vol, float pitch, Vec3 pos) { this.at = at; this.sound = s; this.vol = vol; this.pitch = pitch; this.pos = pos; }
    }
    private static final List<Pending> SOUNDS = new ArrayList<>();
    private static long clock;

    /** Called once per client tick for every live nuke; tt = ticks since the impact (negative while charging). */
    public static void tick(Minecraft mc, PowerNet.Msg f, int tt) {
        if (mc.level == null || mc.player == null) return;
        Vec3 B = f.b();
        float R = f.size();
        double dist = mc.player.position().distanceTo(B);
        if (tt == 0) scheduleSounds(mc, B, R, dist);
        if (tt < 0 || tt > TOTAL) return;
        ParticleStatus ps = mc.options.particles().get();
        if (ps == ParticleStatus.MINIMAL || dist > 420) return;
        // fewer particles the further away and the lower the setting; the shape reads fine from afar with a third of them
        double density = (ps == ParticleStatus.ALL ? 1.0 : 0.4) * (dist > 200 ? 0.6 : 1.0);
        RandomSource rnd = mc.level.random;
        float rise = ease(tt / (float) RISE);
        double hmax = R * 3.0;
        double h = hmax * rise;                                  // height of the cap centre above the ground
        float fade = tt < RISE + 120 ? 1F : Mth.clamp(1F - (tt - RISE - 120) / (float) (TOTAL - RISE - 120), 0F, 1F);
        double capR = R * 0.95 * ease(tt / (float) (RISE * 0.9F)) * (1 + Math.max(0, tt - RISE) * 0.0012);
        double stemR = R * (0.13 + 0.05 * rise);

        // 1) fireball: hot core that lifts off in the first seconds
        if (tt < 90) {
            double fr = R * 0.5 * ease(tt / 30F) * (1 - tt / 120F);
            Vec3 fc = B.add(0, Math.min(h * 0.6, tt * 0.9) + fr * 0.4, 0);
            int n = n(14, density, rnd);
            for (int k = 0; k < n; k++) {
                Vec3 p = fc.add(sphere(rnd).scale(fr));
                add(mc, ParticleTypes.FLAME, p, jitter(rnd, 0.02), 0.06);
                if (k % 3 == 0) add(mc, ParticleTypes.LARGE_SMOKE, p, jitter(rnd, 0.02), 0.04);
            }
            if (tt < 12) add(mc, ParticleTypes.EXPLOSION_EMITTER, fc, Vec3.ZERO, 0);
        }

        // 2) stem: a rising column of smoke, hot and glowing at the bottom
        if (tt < RISE + 160) {
            int n = n(20, density, rnd);
            for (int k = 0; k < n; k++) {
                double y = rnd.nextDouble() * h;
                double a = rnd.nextDouble() * Math.PI * 2;
                double r = stemR * (0.55 + rnd.nextDouble() * 0.6) * (1 + 0.5 * Math.max(0, 1 - y / (R * 0.8)));
                Vec3 p = B.add(Math.cos(a) * r, y, Math.sin(a) * r);
                Vec3 v = new Vec3(-Math.sin(a) * 0.05, 0.12 * fade, Math.cos(a) * 0.05); // slow swirl, rising
                add(mc, y < R * 0.6 && tt < 160 && rnd.nextInt(3) == 0 ? ParticleTypes.FLAME : ParticleTypes.LARGE_SMOKE, p, v, 1);
            }
        }

        // 3) cap: a rolling torus (up through the middle, out over the top, down the outside) plus a dome on top
        if (tt > 10 && tt < RISE + 260) {
            double ringR = capR * 0.68, tube = capR * 0.34 * (0.7 + 0.3 * rise);
            double cy = B.y + h;
            int n = n(52, density, rnd) + (int) (4 * Math.min(1, fade));
            for (int k = 0; k < n; k++) {
                double phi = rnd.nextDouble() * Math.PI * 2, th = rnd.nextDouble() * Math.PI * 2;
                double rr = ringR + Math.cos(th) * tube * (0.85 + rnd.nextDouble() * 0.3);
                double yy = Math.sin(th) * tube * 0.75;
                Vec3 p = new Vec3(B.x + Math.cos(phi) * rr, cy + yy, B.z + Math.sin(phi) * rr);
                // rolling: velocity tangent to the tube circle (radial component cos, vertical sin rotated by 90 degrees)
                double roll = 0.16 * fade;
                double vr = -Math.sin(th) * roll, vy = Math.cos(th) * roll * 0.8;
                Vec3 v = new Vec3(Math.cos(phi) * vr, vy, Math.sin(phi) * vr);
                add(mc, k % 9 == 0 && tt < 170 ? ParticleTypes.FLAME : ParticleTypes.LARGE_SMOKE, p, v, 1);
            }
            // underside glows orange while the cap is hot
            if (tt < 170 && rnd.nextInt(2) == 0) {
                double a = rnd.nextDouble() * Math.PI * 2, r = rnd.nextDouble() * ringR;
                add(mc, ParticleTypes.FLAME, new Vec3(B.x + Math.cos(a) * r, cy - tube * 0.55, B.z + Math.sin(a) * r), new Vec3(0, 0.02, 0), 1);
            }
            // dome over the top of the cap
            int d = n(10, density, rnd);
            for (int k = 0; k < d; k++) {
                double a = rnd.nextDouble() * Math.PI * 2, r = Math.sqrt(rnd.nextDouble()) * capR * 0.9;
                double y = cy + tube * 0.5 + Math.sqrt(Math.max(0, 1 - (r * r) / (capR * capR))) * tube * 0.9;
                add(mc, ParticleTypes.LARGE_SMOKE, new Vec3(B.x + Math.cos(a) * r, y, B.z + Math.sin(a) * r), new Vec3(Math.cos(a) * 0.05, 0.05, Math.sin(a) * 0.05), 1);
            }
        }

        // 4) condensation ring: a white halo of cloud that rides the shockwave front at about half the cap's height
        if (tt < 60) {
            double rr = R * 0.4 + tt * R * 0.085;
            double y = B.y + Math.min(R * 0.6, 3 + tt * 0.5);
            int n = n(36, density, rnd);
            for (int k = 0; k < n; k++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                Vec3 p = new Vec3(B.x + Math.cos(a) * rr, y + rnd.nextDouble() * 2.5, B.z + Math.sin(a) * rr);
                add(mc, ParticleTypes.CLOUD, p, new Vec3(Math.cos(a) * 0.35, 0.02, Math.sin(a) * 0.35), 1);
            }
        }

        // 5) dust wall rolling outward along the ground
        if (tt < 110) {
            double rr = R * 0.3 + tt * R * 0.075;
            int n = n(40, density, rnd);
            BlockParticleOption dirt = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState());
            for (int k = 0; k < n; k++) {
                double a = rnd.nextDouble() * Math.PI * 2;
                Vec3 p = new Vec3(B.x + Math.cos(a) * rr, B.y + 0.6 + rnd.nextDouble() * 3.5, B.z + Math.sin(a) * rr);
                Vec3 out = new Vec3(Math.cos(a) * 0.45, 0.05 + rnd.nextDouble() * 0.12, Math.sin(a) * 0.45);
                add(mc, k % 4 == 0 ? dirt : k % 4 == 1 ? ParticleTypes.POOF : ParticleTypes.CAMPFIRE_COSY_SMOKE, p, out, 1);
            }
        }

        // 6) ash falling over the whole area for the rest of the half minute
        if (tt > 100 && tt < TOTAL - 40) {
            int n = n(26, density, rnd) + 2;
            for (int k = 0; k < n; k++) {
                double a = rnd.nextDouble() * Math.PI * 2, r = Math.sqrt(rnd.nextDouble()) * R * 2.6;
                Vec3 p = new Vec3(B.x + Math.cos(a) * r, B.y + 6 + rnd.nextDouble() * 34, B.z + Math.sin(a) * r);
                add(mc, ParticleTypes.ASH, p, new Vec3(0.02, -0.06, 0.02), 1);
                if (k % 5 == 0) add(mc, ParticleTypes.WHITE_ASH, p, new Vec3(0.02, -0.05, 0.02), 1);
            }
        }
    }

    // ---------------------------------------------------------------- sound: delayed by distance

    private static void scheduleSounds(Minecraft mc, Vec3 B, float R, double dist) {
        long delay = (long) Math.max(0, (dist - 12) / SOUND_SPEED);
        float vol = (float) Mth.clamp(2.0 + dist / 14.0, 2.0, 40.0); // volume > 1 widens the audible range (16 blocks per 1.0)
        float far = (float) Mth.clamp(1.0 - dist / 520.0, 0.25, 1.0);
        SOUNDS.add(new Pending(clock + delay, SoundEvents.GENERIC_EXPLODE.value(), vol * 0.7F * far + 1F, 0.45F, B));
        SOUNDS.add(new Pending(clock + delay + 2, SoundEvents.WARDEN_SONIC_BOOM, vol * 0.55F * far + 1F, 0.5F, B));
        SOUNDS.add(new Pending(clock + delay + 6, SoundEvents.LIGHTNING_BOLT_THUNDER, vol * 0.7F * far + 1F, 0.55F, B));
        // long rumble: low explosions and thunder rolling in behind the first shock
        for (int k = 1; k <= 8; k++) {
            float v = (1F - k / 9F) * far;
            SOUNDS.add(new Pending(clock + delay + 14L * k + (long) (k * k * 1.6), k % 2 == 0 ? SoundEvents.LIGHTNING_BOLT_THUNDER : SoundEvents.GENERIC_EXPLODE.value(),
                    vol * 0.45F * v + 0.6F, 0.35F + 0.03F * (k % 3), B));
        }
        // the Wilson ring hisses past a moment later
        SOUNDS.add(new Pending(clock + delay + 30, SoundEvents.FIRE_EXTINGUISH, vol * 0.4F * far + 1F, 0.5F, B));
    }

    /** Plays the sounds whose time has come (client tick). */
    public static void pumpSounds(Minecraft mc) {
        clock++;
        if (SOUNDS.isEmpty() || mc.level == null) { if (mc.level == null) SOUNDS.clear(); return; }
        for (Iterator<Pending> it = SOUNDS.iterator(); it.hasNext(); ) {
            Pending p = it.next();
            if (p.at > clock) continue;
            it.remove();
            mc.level.playLocalSound(p.pos.x, p.pos.y, p.pos.z, p.sound, SoundSource.AMBIENT, p.vol, p.pitch, false);
        }
    }

    public static void clear() { SOUNDS.clear(); }

    // ---------------------------------------------------------------- screen: white-out

    /** 0..1 strength of the blinding flash for the local player (looking towards the blast hurts more than looking away). */
    public static float whiteout(Minecraft mc, PowerNet.Msg f, float tt) {
        if (mc.player == null || tt < 0 || tt > 70) return 0;
        Vec3 B = f.b();
        Vec3 to = B.subtract(mc.player.getEyePosition());
        double dist = to.length();
        if (dist > 520) return 0;
        double facing = dist < 1 ? 1 : mc.player.getViewVector(1F).dot(to.scale(1 / dist));
        float inView = (float) Mth.clamp((facing + 0.15) / 0.75, 0.0, 1.0);
        float reach = (float) Mth.clamp(1.15 - dist / (f.size() * 9.0 + 120.0), 0.0, 1.0);
        float decay = tt < 6 ? 1F : 1F - (tt - 6) / 64F;
        return Mth.clamp(reach * (0.18F + 0.82F * inView) * decay * decay, 0F, 1F);
    }

    // ---------------------------------------------------------------- helpers

    private static void add(Minecraft mc, ParticleOptions o, Vec3 p, Vec3 v, double speedUnused) {
        mc.level.addAlwaysVisibleParticle(o, true, p.x, p.y, p.z, v.x, v.y, v.z);
    }

    private static int n(int base, double density, RandomSource rnd) {
        double x = base * density;
        return (int) x + (rnd.nextDouble() < x - (int) x ? 1 : 0);
    }

    private static Vec3 sphere(RandomSource r) {
        double x, y, z;
        do { x = r.nextDouble() * 2 - 1; y = r.nextDouble() * 2 - 1; z = r.nextDouble() * 2 - 1; } while (x * x + y * y + z * z > 1);
        return new Vec3(x, y, z);
    }

    private static Vec3 jitter(RandomSource r, double s) { return new Vec3((r.nextDouble() - 0.5) * s, 0.04 + r.nextDouble() * s, (r.nextDouble() - 0.5) * s); }

    private static float ease(float t) { t = Mth.clamp(t, 0, 1); return 1 - (1 - t) * (1 - t) * (1 - t); }

    private NukeCloud() {}
}
