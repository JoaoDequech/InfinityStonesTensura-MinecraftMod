package com.infinitystones.tensura.skill.ability.extra.time.client;

import com.infinitystones.tensura.client.render.GlowFx;
import com.infinitystones.tensura.skill.ability.extra.time.TimeNet;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Layered clockwork geometry of the Time powers (bubble, frozen marks, hourglass, lapse dials, era/restore waves, ghosts). */
final class TimeShapes {
    private static final Vec3 UP = new Vec3(0, 1, 0);

    // ------------------------------------------------------------------ stasis bubble

    static void stasis(TDraw d, TimeNet.T f, float a, float age, float time, PoseStack ps, MultiBufferSource buf, boolean textured) {
        Vec3 c = f.a();
        float grow = Mth.clamp(age / 8F, 0, 1);
        float r = f.size() * (1 - (1 - grow) * (1 - grow) * (1 - grow));
        int col = f.color(), light = TDraw.lighten(col, 0.5F);
        if (textured) {
            TimeClient.ground(ps, buf, GlowFx.RUNE_RING, c.add(0, 0.05, 0), r, col, a * 0.9F, time * 0.08F);
            TimeClient.ground(ps, buf, GlowFx.RUNE_RING, c.add(0, 0.07, 0), r * 0.55F, col, a * 0.8F, -time * 0.15F);
            TimeClient.ground(ps, buf, GlowFx.HALO, c.add(0, 0.04, 0), r * 1.4F, col, a * 0.4F, 0);
            TimeClient.flare(ps, buf, c.add(0, r, 0), 1.6F, col, a * 0.7F, time * 0.3F);
            return;
        }
        // stuck hands: time stopped (tiny jitter)
        double jit = Math.sin(time * 40) * 0.03;
        // meridians
        int mer = 16;
        for (int k = 0; k < mer; k++) {
            double phi = Math.PI * 2 * k / mer + time * 0.05;
            Vec3 prev = null;
            for (int j = 0; j <= 12; j++) {
                double th = Math.PI / 2 * j / 12;
                Vec3 p = c.add(Math.sin(th) * Math.cos(phi) * r, Math.cos(th) * r, Math.sin(th) * Math.sin(phi) * r);
                if (prev != null) d.ribbon(prev, p, 0.025F, col, a * 0.35F);
                prev = p;
            }
        }
        // parallels
        for (int j = 1; j < 5; j++) {
            double th = Math.PI / 2 * j / 5;
            d.ring(c.add(0, Math.cos(th) * r, 0), (float) (Math.sin(th) * r), 0.03F, col, a * 0.35F, 0);
        }
        // shell
        int lat = 6, lon = 24;
        for (int j = 0; j < lat; j++) {
            double t0 = Math.PI / 2 * j / lat, t1 = Math.PI / 2 * (j + 1) / lat;
            for (int k = 0; k < lon; k++) {
                double p0 = Math.PI * 2 * k / lon, p1 = Math.PI * 2 * (k + 1) / lon;
                d.quad(sph(c, r, t0, p0), sph(c, r, t0, p1), sph(c, r, t1, p1), sph(c, r, t1, p0), col, a * 0.045F);
            }
        }
        // big ground dial (frozen at XII)
        d.dial(c.add(0, 0.1, 0), UP, r * 0.98F, col, a, -Math.PI / 2 + jit, -Math.PI / 2 + Math.PI / 6 + jit);
        // gear rings at the base: toothed, counter-rotating (slow: time barely moves)
        gear(d, c.add(0, 0.25, 0), r * 1.02F, 40, col, a * 0.8F, time * 0.04F);
        gear(d, c.add(0, 0.6, 0), r * 0.9F, 32, col, a * 0.6F, -time * 0.06F);
        // floating dials on the dome surface, facing outward
        for (int k = 0; k < 6; k++) {
            double phi = Math.PI * 2 * k / 6 + time * 0.03;
            double th = Math.toRadians(52 + 10 * Math.sin(k));
            Vec3 n = new Vec3(Math.sin(th) * Math.cos(phi), Math.cos(th), Math.sin(th) * Math.sin(phi));
            Vec3 p = c.add(n.scale(r * 1.02));
            d.dial(p, n, Math.max(0.5F, r * 0.18F), col, a * 0.9F, k * 1.1 + jit, k * 0.4 + jit);
        }
        // vertical crossing rings
        d.ringN(c.add(0, 0.01, 0), new Vec3(1, 0, 0), r, 0.05F, col, a * 0.5F, 0);
        d.ringN(c.add(0, 0.01, 0), new Vec3(0, 0, 1), r, 0.05F, col, a * 0.5F, 0);
        // suspended motes (do not move: time is stopped) twinkling
        for (int k = 0; k < 40; k++) {
            double u = h(k, 1), v = h(k, 2), w = h(k, 3);
            double rr = Math.cbrt(u) * r * 0.9, phi = v * Math.PI * 2, th = Math.acos(w);
            Vec3 p = c.add(Math.sin(th) * Math.cos(phi) * rr, Math.abs(Math.cos(th)) * rr, Math.sin(th) * Math.sin(phi) * rr);
            float tw = 0.4F + 0.6F * Math.max(0, Mth.sin(time * 2 + k));
            d.spark(p, 0.06F, light, a * tw);
        }
        // pulse sweeping across the dome from the top
        float s = (time * 0.35F) % 1F;
        double th = s * Math.PI / 2;
        d.ring(c.add(0, Math.cos(th) * r, 0), (float) (Math.sin(th) * r), 0.12F, light, a * 0.7F * (1 - s), 0);
    }

    private static void gear(TDraw d, Vec3 c, float r, int teeth, int col, float a, float phase) {
        d.ring(c, r, 0.08F, col, a, phase);
        for (int i = 0; i < teeth; i++) {
            double a0 = phase + Math.PI * 2 * i / teeth, a1 = a0 + Math.PI / teeth;
            Vec3 b0 = c.add(Math.cos(a0) * r, 0, Math.sin(a0) * r), b1 = c.add(Math.cos(a1) * r, 0, Math.sin(a1) * r);
            Vec3 o0 = c.add(Math.cos(a0) * (r + 0.25), 0, Math.sin(a0) * (r + 0.25)), o1 = c.add(Math.cos(a1) * (r + 0.25), 0, Math.sin(a1) * (r + 0.25));
            d.quad(b0, o0, o1, b1, col, a * 0.5F);
        }
    }

    private static Vec3 sph(Vec3 c, double r, double th, double ph) {
        return c.add(Math.sin(th) * Math.cos(ph) * r, Math.cos(th) * r, Math.sin(th) * Math.sin(ph) * r);
    }

    private static double h(int a, int b) {
        int x = a * 73856093 ^ b * 19349663;
        x ^= x >>> 13; x *= 0x5bd1e995; x ^= x >>> 15;
        return (x & 0xFFFFFF) / (double) 0x1000000;
    }

    // ------------------------------------------------------------------ frozen entity

    static void frozen(TDraw d, Vec3 feet, float w, float hgt, float a, float time) {
        int col = 0x7FE8A8;
        float r = w * 0.85F + 0.25F;
        Vec3 mid = feet.add(0, hgt * 0.5, 0);
        d.ringN(mid, new Vec3(0.35, 1, 0.2), r, 0.04F, col, a * 0.8F, 0);
        d.ringN(mid, new Vec3(-0.3, 1, -0.25), r * 1.08F, 0.03F, col, a * 0.6F, 0);
        d.dial(feet.add(0, hgt + 0.55, 0), new Vec3(0, 0.25, 1).normalize(), 0.32F, col, a, -Math.PI / 2, -Math.PI / 2 + 0.6);
        d.ring(feet.add(0, 0.05, 0), r, 0.06F, col, a * 0.6F, 0);
        // ice-like vertical bars (cage of stopped time)
        for (int k = 0; k < 6; k++) {
            double an = Math.PI * 2 * k / 6;
            Vec3 b = feet.add(Math.cos(an) * r, 0.05, Math.sin(an) * r);
            d.fadeLine(b, b.add(0, hgt + 0.2, 0), 0.025F, col, a * 0.6F, a * 0.05F);
        }
    }

    // ------------------------------------------------------------------ hourglass

    static void hourglass(TDraw d, Vec3 c, float progress, boolean rewinding, float a, float time) {
        int col = TimeClient.GREEN, light = TDraw.lighten(col, 0.55F), sand = 0xFFE07A;
        float hh = 0.45F, rr = 0.28F;
        float spin = time * (rewinding ? 6 : 0.8F);
        Vec3 top = c.add(0, hh, 0), bot = c.add(0, -hh, 0);
        d.ring(top, rr, 0.04F, col, a, spin); d.ring(bot, rr, 0.04F, col, a, spin);
        d.ring(top.add(0, 0.05, 0), rr + 0.08F, 0.05F, col, a, -spin); d.ring(bot.add(0, -0.05, 0), rr + 0.08F, 0.05F, col, a, -spin);
        for (int k = 0; k < 6; k++) {
            double an = spin + Math.PI * 2 * k / 6;
            Vec3 t = top.add(Math.cos(an) * rr, 0, Math.sin(an) * rr), b = bot.add(Math.cos(an) * rr, 0, Math.sin(an) * rr);
            d.ribbon(t, c, 0.018F, light, a * 0.8F);
            d.ribbon(c, b, 0.018F, light, a * 0.8F);
        }
        for (int k = 0; k < 3; k++) {
            double an = spin * 0.5 + Math.PI * 2 * k / 3;
            Vec3 t = top.add(Math.cos(an) * (rr + 0.08), 0.05, Math.sin(an) * (rr + 0.08));
            d.line(t, t.add(0, -2 * hh - 0.1, 0), 0.02F, col, a);
        }
        // sand: upper cone fill shrinks as progress grows (inverted when rewinding)
        float upper = rewinding ? progress : 1 - progress;
        float lower = 1 - upper;
        if (upper > 0.02F) {
            float y = (float) (hh * upper);
            float rad = rr * upper;
            d.quad(c.add(-rad * 0.7, y, -rad * 0.7), c.add(rad * 0.7, y, -rad * 0.7), c.add(rad * 0.7, y, rad * 0.7), c.add(-rad * 0.7, y, rad * 0.7), sand, a * 0.5F);
            d.ribbon(c, c.add(0, y, 0), rad * 0.55F, sand, a * 0.45F);
        }
        if (lower > 0.02F) d.ribbon(bot, bot.add(0, hh * lower, 0), rr * 0.6F * (0.4F + 0.6F * lower), sand, a * 0.5F);
        // falling (or rising) grains through the neck
        for (int k = 0; k < 4; k++) {
            float ph = (time * 2.5F + k / 4F) % 1F;
            double y = rewinding ? -hh + ph * hh : -ph * hh;
            d.spark(c.add(0, y, 0), 0.03F, sand, a);
        }
        if (rewinding) for (int k = 0; k < 8; k++) {
            double an = -time * 9 + Math.PI * 2 * k / 8;
            d.spark(c.add(Math.cos(an) * 0.7, Math.sin(time * 4 + k) * 0.3, Math.sin(an) * 0.7), 0.06F, light, a);
        }
    }

    // ------------------------------------------------------------------ lapse field

    static void lapse(TDraw d, TimeNet.T f, float a, float age, float time, PoseStack ps, MultiBufferSource buf, boolean textured) {
        Vec3 c = f.a().add(0, 0.08, 0);
        float grow = Mth.clamp(age / 10F, 0, 1);
        float r = f.size() * (1 - (1 - grow) * (1 - grow));
        float sp = f.extra();
        boolean fast = sp > 0;
        float k = Math.abs(sp);
        float rate = fast ? 0.6F * k : 0.6F / k;
        int col = f.color(), light = TDraw.lighten(col, 0.5F);
        if (textured) {
            TimeClient.ground(ps, buf, GlowFx.RUNE_RING, c, r, col, a * 0.85F, time * 0.15F * (fast ? k : 1 / k));
            TimeClient.ground(ps, buf, GlowFx.HALO, c, r * 1.3F, col, a * 0.35F, 0);
            return;
        }
        double t = time * rate;
        d.dial(c, UP, r, col, a, t * 6, t * 0.5);
        d.dial(c.add(0, 0.02, 0), UP, r * 0.62F, col, a * 0.9F, -t * 9, -t * 0.75);
        d.dial(c.add(0, 0.04, 0), UP, r * 0.3F, col, a * 0.9F, t * 14, t * 1.2);
        d.dashed(c.add(0, 0.03, 0), r * 1.06F, 0.05F, light, a * 0.7F, (float) (t * 0.6), 24, 0.55F);
        d.dashed(c.add(0, 0.03, 0), r * 0.8F, 0.035F, col, a * 0.6F, (float) (-t * 0.9), 16, 0.4F);
        // spokes
        for (int i = 0; i < 12; i++) {
            double an = t * 0.2 + Math.PI * 2 * i / 12;
            d.ribbon(c.add(Math.cos(an) * r * 0.32, 0.02, Math.sin(an) * r * 0.32), c.add(Math.cos(an) * r * 0.6, 0.02, Math.sin(an) * r * 0.6), 0.02F, col, a * 0.5F);
        }
        // ripples: outward when fast, inward when slow
        for (int i = 0; i < 3; i++) {
            float ph = (float) ((time * (fast ? 0.5 + k * 0.05 : 0.15) + i / 3.0) % 1.0);
            float rr = fast ? ph * r : (1 - ph) * r;
            d.ring(c.add(0, 0.05, 0), Math.max(0.2F, rr), 0.1F, light, a * 0.5F * (fast ? 1 - ph : ph), 0);
        }
        // motes rising fast / sinking slowly
        for (int i = 0; i < 28; i++) {
            double an = h(i, 7) * Math.PI * 2, rr = Math.sqrt(h(i, 8)) * r;
            float ph = (float) ((time * (fast ? 0.25 * k : 0.05) + h(i, 9)) % 1.0);
            double y = fast ? ph * 4 : (1 - ph) * 4;
            d.spark(c.add(Math.cos(an) * rr, y, Math.sin(an) * rr), 0.07F, light, a * (fast ? 1 - ph : ph));
        }
        // edge curtain
        for (int i = 0; i < 36; i++) {
            double an = Math.PI * 2 * i / 36;
            Vec3 b = c.add(Math.cos(an) * r, 0, Math.sin(an) * r);
            d.fadeLine(b, b.add(0, 1.6 + 0.6 * Math.sin(time * (fast ? 4 : 1) + i), 0), 0.03F, col, a * 0.5F, 0);
        }
    }

    // ------------------------------------------------------------------ eras wave

    static void era(TDraw d, TimeNet.T f, float a, float prog, float time, PoseStack ps, MultiBufferSource buf, boolean textured) {
        Vec3 c = f.a();
        boolean aging = f.extra() > 0;
        float wave = Mth.clamp(prog / 0.7F, 0, 1);
        float ease = 1 - (1 - wave) * (1 - wave);
        float r = 0.3F + f.size() * ease;
        int col = aging ? TDraw.mix(f.color(), 0xC8A060, 0.35F) : f.color();
        float al = a * (1 - prog * 0.7F);
        if (textured) {
            TimeClient.ground(ps, buf, GlowFx.RUNE_RING, c.add(0, 0.05, 0), r, col, al, (aging ? 1 : -1) * time * 0.8F);
            TimeClient.flare(ps, buf, c.add(0, 1.2, 0), 2.5F * (1 - prog), col, al, time);
            return;
        }
        // big central dial with hands racing forwards (aging) or backwards
        double t = (aging ? 1 : -1) * time * 8;
        d.dial(c.add(0, 0.12, 0), UP, Math.max(0.6F, f.size() * 0.45F), col, al, t, t / 12);
        // expanding shell: ground ring + 3 meridian rings
        d.ring(c.add(0, 0.1, 0), r, 0.35F * (1 - wave) + 0.06F, col, al, 0);
        for (int k = 0; k < 3; k++) {
            double an = Math.PI * k / 3 + time * 0.5;
            d.ringN(c, new Vec3(Math.cos(an), 0, Math.sin(an)), r, 0.04F, col, al * 0.6F, 0);
        }
        d.ring(c.add(0, r * 0.5, 0), (float) (r * 0.866), 0.04F, col, al * 0.5F, 0);
        // numeral ticks around the wave front
        for (int k = 0; k < 12; k++) {
            double an = Math.PI * 2 * k / 12 + (aging ? time : -time) * 0.6;
            Vec3 p = c.add(Math.cos(an) * r, 0.15, Math.sin(an) * r);
            d.fadeLine(p, p.add(0, k % 3 == 0 ? 1.4 : 0.7, 0), 0.05F, col, al, 0);
        }
    }

    // ------------------------------------------------------------------ rewind ghost

    static void ghost(TDraw d, Vec3 feet, float w, float hgt, float yaw, float a, float time) {
        if (a <= 0.01F) return;
        int col = TimeClient.GREEN, light = TDraw.lighten(col, 0.5F);
        float hw = Math.max(0.2F, w * 0.5F);
        double yr = Math.toRadians(-yaw);
        Vec3 fwd = new Vec3(Math.sin(yr), 0, Math.cos(yr)), side = new Vec3(fwd.z, 0, -fwd.x);
        // body silhouette: legs, torso, arms, head
        Vec3 hip = feet.add(0, hgt * 0.45, 0), neck = feet.add(0, hgt * 0.8, 0), head = feet.add(0, hgt * 0.9, 0);
        d.line(feet.add(side.scale(hw * 0.5)), hip, 0.06F, col, a * 0.8F);
        d.line(feet.subtract(side.scale(hw * 0.5)), hip, 0.06F, col, a * 0.8F);
        d.line(hip, neck, 0.09F, col, a * 0.8F);
        d.line(neck, hip.add(side.scale(hw * 1.1)), 0.05F, col, a * 0.7F);
        d.line(neck, hip.subtract(side.scale(hw * 1.1)), 0.05F, col, a * 0.7F);
        d.ringN(head, fwd, hgt * 0.1F, 0.04F, light, a, 0);
        d.ribbon(feet, head, hw * 1.2F, col, a * 0.12F);
        d.ring(feet.add(0, 0.05, 0), hw * 1.4F, 0.05F, col, a * 0.6F, time);
    }

    // ------------------------------------------------------------------ restoration wave

    static void restore(TDraw d, TimeNet.T f, float a, float prog, float time, PoseStack ps, MultiBufferSource buf, boolean textured) {
        Vec3 c = f.a();
        float ease = 1 - (1 - prog) * (1 - prog);
        float r = 0.5F + f.size() * ease;
        float al = a * (1 - prog * 0.8F);
        if (textured) {
            TimeClient.ground(ps, buf, GlowFx.RUNE_RING, c.add(0, 0.06, 0), r, TimeClient.GREEN, al, -time * 0.5F);
            TimeClient.ground(ps, buf, GlowFx.RUNE_RING, c.add(0, 0.08, 0), r * 0.9F, TimeClient.RED, al * 0.8F, time * 0.7F);
            return;
        }
        d.ring(c.add(0, 0.1, 0), r, 0.5F * (1 - prog) + 0.08F, TimeClient.GREEN, al, 0);
        d.ring(c.add(0, 0.12, 0), r * 0.93F, 0.25F * (1 - prog) + 0.05F, TimeClient.RED, al * 0.8F, 0);
        for (int k = 0; k < 32; k++) {
            double an = Math.PI * 2 * k / 32 - time * 0.4;
            Vec3 p = c.add(Math.cos(an) * r, 0.1, Math.sin(an) * r);
            d.fadeLine(p, p.add(0, 3.5 * (1 - prog) * (0.5 + 0.5 * h(k, 4)), 0), 0.05F, k % 2 == 0 ? TimeClient.GREEN : TimeClient.RED, al, 0);
        }
        d.dial(c.add(0, 0.15, 0), UP, 2.5F, TimeClient.GREEN, al, -time * 6, -time * 0.5);
    }

    private TimeShapes() {}
}
