package com.infinitystones.tensura.skill.ability.extra.time.client;

import com.infinitystones.tensura.client.render.GlowFx;
import com.infinitystones.tensura.skill.ability.extra.time.TimeNet;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Climate zone drawing: clean animated outline + per-climate sky/precipitation/mist layers, only over the area. */
final class ZoneFx {
    private static final int GREEN = TimeClient.GREEN;

    private static float hash(int a, int b, int s) {
        int h = a * 73856093 ^ b * 19349663 ^ s * 83492791;
        h ^= h >>> 13; h *= 0x5bd1e995; h ^= h >>> 15;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float zoneAge(TimeClient.Inst i, float pt) { return (float) i.f.bx() + i.age + pt; }

    private static float heightAt(TimeClient.ZoneCache c, double x, double z) {
        int i = Mth.clamp((int) Math.round((x - (c.cx - c.r)) / c.cell), 0, c.n - 1), j = Mth.clamp((int) Math.round((z - (c.cz - c.r)) / c.cell), 0, c.n - 1);
        return c.h[i * c.n + j];
    }

    // ================================================================== translucent (clouds, rain, snow, mist, sand)

    static void translucent(TDraw d, ClientLevel level, TimeClient.Inst i, TimeClient.ZoneCache c, float pt, float time) {
        TimeNet.T f = i.f;
        int cl = (int) f.extra();
        if (cl == TimeNet.C_NONE) return;
        Vec3 cam = d.cam;
        double dist = Math.sqrt((cam.x - c.cx) * (cam.x - c.cx) + (cam.z - c.cz) * (cam.z - c.cz));
        if (dist > c.r + 220) return;
        float a = TimeClient.fade(i, pt) * Mth.clamp(zoneAge(i, pt) / 30F, 0, 1);
        if (a <= 0.01F) return;
        switch (cl) {
            case TimeNet.C_STORM -> {
                clouds(d, c, a, time, 0x3C4049, 0x111216, 0.93F, 0.06F, true);
                streaks(d, c, f.id(), a, time, 3, 26F, 1.5F, 0x8C9CB0, 0.5F, 0.35);
            }
            case TimeNet.C_RAIN -> {
                clouds(d, c, a, time, 0xA9AFB7, 0x5E646C, 0.8F, 0.03F, false);
                streaks(d, c, f.id(), a, time, 2, 20F, 1.0F, 0xA6BAD2, 0.42F, 0.15);
            }
            case TimeNet.C_SNOW -> {
                clouds(d, c, a, time, 0xF2F5F8, 0xBFC6CE, 0.82F, 0.02F, false);
                flakes(d, c, f.id(), a, time);
            }
            case TimeNet.C_FOG -> {
                walls(d, c, a, 0xD4D8DC, 0.34F, 9F, 3);
                mist(d, c, a, time, 0xDDE1E5, 0.16F);
            }
            case TimeNet.C_SAND -> {
                walls(d, c, a, 0xC9A15E, 0.32F, 8F, 2);
                sand(d, c, f.id(), a, time);
                mist(d, c, a, time, 0xC09A5A, 0.12F);
            }
            default -> { }
        }
    }

    /** Stylised cloud layer: shaded low-poly puffs rotating slowly over the area, bobbing. */
    private static void clouds(TDraw d, TimeClient.ZoneCache c, float a, float time, int top, int bottom, float alpha, float spin, boolean storm) {
        double rot = time * spin;
        double cs = Math.cos(rot), sn = Math.sin(rot);
        for (int k = 0; k < c.px.length; k++) {
            double x = c.px[k] * cs - c.pz[k] * sn, z = c.px[k] * sn + c.pz[k] * cs;
            double dd = Math.sqrt(x * x + z * z);
            float edge = (float) Mth.clamp((c.r - dd) / 3.5 + 0.35, 0.15, 1);
            float r = c.pr[k] * (1 + 0.07F * Mth.sin(time * 0.9F + c.ph[k])) * (0.55F + 0.45F * edge);
            double y = c.cloudY + Math.sin(time * 0.55 + c.ph[k]) * 0.35 + (storm ? Math.sin(c.ph[k] * 3) * 0.6 : 0);
            Vec3 p = new Vec3(c.cx + x, y, c.cz + z);
            d.puff(p, r, r * 0.52, r * 0.9, top, bottom, a * alpha * (0.7F + 0.3F * edge));
            if (storm && (k & 1) == 0) // dark hanging under-layer (mammatus)
                d.puff(p.add(0.8, -r * 0.45, -0.6), r * 0.6, r * 0.35, r * 0.6, bottom, 0x08090B, a * alpha);
        }
    }

    /** Rain streaks falling from the cloud to the terrain, only near the camera. */
    private static void streaks(TDraw d, TimeClient.ZoneCache c, int seed, float a, float time, int drops, float speed, float len, int col, float alpha, double slant) {
        Vec3 cam = d.cam;
        double range = 34;
        int i0 = Math.max(0, (int) ((cam.x - range - (c.cx - c.r)) / c.cell)), i1 = Math.min(c.n - 1, (int) ((cam.x + range - (c.cx - c.r)) / c.cell) + 1);
        int j0 = Math.max(0, (int) ((cam.z - range - (c.cz - c.r)) / c.cell)), j1 = Math.min(c.n - 1, (int) ((cam.z + range - (c.cz - c.r)) / c.cell) + 1);
        int count = 0;
        float top = c.cloudY - 1;
        for (int i = i0; i <= i1; i++) for (int j = j0; j <= j1; j++) {
            double x = c.cx - c.r + i * c.cell + (hash(i, j, seed) - 0.5) * c.cell, z = c.cz - c.r + j * c.cell + (hash(j, i, seed + 1) - 0.5) * c.cell;
            double dx = x - c.cx, dz = z - c.cz;
            if (dx * dx + dz * dz > c.r * c.r) continue;
            double cdx = x - cam.x, cdz = z - cam.z;
            double cd2 = cdx * cdx + cdz * cdz;
            if (cd2 > range * range) continue;
            float gy = c.h[i * c.n + j];
            float fall = top - gy;
            if (fall < 2) continue;
            float near = (float) Mth.clamp(Math.sqrt(cd2) / 1.5, 0.15, 1);
            for (int k = 0; k < drops; k++) {
                float ph = (time * speed / fall + hash(i, j, k * 31 + seed)) % 1F;
                double y = top - ph * fall;
                Vec3 lo = new Vec3(x, y, z), hi = new Vec3(x + slant, y + len, z + slant * 0.4);
                d.ribbon(lo, hi, 0.022F, col, a * alpha * near, col, 0);
                if (++count > 5000) return;
            }
        }
    }

    private static void flakes(TDraw d, TimeClient.ZoneCache c, int seed, float a, float time) {
        Vec3 cam = d.cam;
        double range = 30;
        int i0 = Math.max(0, (int) ((cam.x - range - (c.cx - c.r)) / c.cell)), i1 = Math.min(c.n - 1, (int) ((cam.x + range - (c.cx - c.r)) / c.cell) + 1);
        int j0 = Math.max(0, (int) ((cam.z - range - (c.cz - c.r)) / c.cell)), j1 = Math.min(c.n - 1, (int) ((cam.z + range - (c.cz - c.r)) / c.cell) + 1);
        float top = c.cloudY - 1;
        int count = 0;
        for (int i = i0; i <= i1; i++) for (int j = j0; j <= j1; j++) {
            double bx = c.cx - c.r + i * c.cell, bz = c.cz - c.r + j * c.cell;
            double dx = bx - c.cx, dz = bz - c.cz;
            if (dx * dx + dz * dz > c.r * c.r) continue;
            if ((bx - cam.x) * (bx - cam.x) + (bz - cam.z) * (bz - cam.z) > range * range) continue;
            float gy = c.h[i * c.n + j];
            float fall = top - gy;
            if (fall < 2) continue;
            for (int k = 0; k < 3; k++) {
                float hs = hash(i, j, k * 17 + seed);
                float ph = (time * 2.2F / fall + hs) % 1F;
                double y = top - ph * fall;
                double x = bx + (hash(j, i, k + seed) - 0.5) * c.cell + Math.sin(time * 1.3 + hs * 20) * 0.45;
                double z = bz + (hash(i + 7, j, k + seed) - 0.5) * c.cell + Math.cos(time * 1.1 + hs * 13) * 0.45;
                Vec3 p = new Vec3(x, y, z);
                float s = 0.045F + hs * 0.04F;
                Vec3 toCam = cam.subtract(p);
                Vec3 side = toCam.cross(new Vec3(0, 1, 0));
                if (side.lengthSqr() < 1e-6) continue;
                side = side.normalize().scale(s);
                Vec3 up = side.cross(toCam).normalize().scale(s);
                d.quad(p.subtract(side).subtract(up), p.add(side).subtract(up), p.add(side).add(up), p.subtract(side).add(up), 0xFFFFFF, a * 0.9F);
                if (++count > 4000) return;
            }
        }
    }

    /** Soft cylinder curtains following the outline (mist / dust wall seen from outside). */
    private static void walls(TDraw d, TimeClient.ZoneCache c, float a, int col, float alpha, float height, int layers) {
        int segs = 64;
        for (int l = 0; l < layers; l++) {
            float r = c.r * (1 - l * 0.24F);
            float hgt = height * (1 - l * 0.15F);
            for (int k = 0; k < segs; k++) {
                double a0 = Math.PI * 2 * k / segs, a1 = Math.PI * 2 * (k + 1) / segs;
                float y0 = c.ring[k * c.ring.length / segs] - 0.5F, y1 = c.ring[((k + 1) % segs) * c.ring.length / segs] - 0.5F;
                Vec3 b0 = new Vec3(c.cx + Math.cos(a0) * r, y0, c.cz + Math.sin(a0) * r), b1 = new Vec3(c.cx + Math.cos(a1) * r, y1, c.cz + Math.sin(a1) * r);
                d.quad4(b0, b1, b1.add(0, hgt, 0), b0.add(0, hgt, 0), col, a * alpha, col, a * alpha, col, 0, col, 0);
            }
        }
    }

    private static void mist(TDraw d, TimeClient.ZoneCache c, float a, float time, int col, float alpha) {
        for (int k = 0; k < c.px.length; k++) {
            double ang = time * 0.05 + c.ph[k];
            double x = c.cx + c.px[k] + Math.cos(ang) * 1.2, z = c.cz + c.pz[k] + Math.sin(ang) * 1.2;
            float gy = heightAt(c, x, z);
            float r = c.pr[k] * 1.5F;
            d.puff(new Vec3(x, gy + 0.8 + Math.sin(time * 0.4 + c.ph[k]) * 0.3, z), r, r * 0.4, r, col, col, a * alpha);
        }
    }

    /** Sand streaks swirling around the centre. */
    private static void sand(TDraw d, TimeClient.ZoneCache c, int seed, float a, float time) {
        int n = (int) Mth.clamp(c.r * 22, 120, 520);
        for (int k = 0; k < n; k++) {
            float h1 = hash(k, seed, 1), h2 = hash(k, seed, 2), h3 = hash(k, seed, 3), h4 = hash(k, seed, 4);
            double r = Math.sqrt(h3) * c.r * 0.98;
            double ang = h1 * Math.PI * 2 + time * (0.7 + h2 * 0.9) * (6 / Math.max(3, r * 0.5 + 3));
            double x = c.cx + Math.cos(ang) * r, z = c.cz + Math.sin(ang) * r;
            if ((x - d.cam.x) * (x - d.cam.x) + (z - d.cam.z) * (z - d.cam.z) > 70 * 70) continue;
            float gy = heightAt(c, x, z);
            double y = gy + 0.3 + h4 * 7 + Math.sin(time * 2 + k) * 0.2;
            double ang2 = ang + Math.min(0.5, 1.6 / Math.max(1, r));
            Vec3 p0 = new Vec3(x, y, z), p1 = new Vec3(c.cx + Math.cos(ang2) * r, y + 0.1, c.cz + Math.sin(ang2) * r);
            int col = TDraw.mix(0xD8B878, 0x9A7440, h2);
            d.ribbon(p0, p1, 0.035F + h4 * 0.03F, col, 0, col, a * 0.55F);
        }
    }

    // ================================================================== additive glow (outline + sky effects)

    static void glow(TDraw d, ClientLevel level, TimeClient.Inst i, TimeClient.ZoneCache c, float a, float pt, float time,
                     PoseStack ps, MultiBufferSource buf, boolean textured) {
        TimeNet.T f = i.f;
        int cl = (int) f.extra();
        float age = zoneAge(i, pt);
        float intro = Mth.clamp(age / 22F, 0, 1);
        float grow = 1 - (1 - intro) * (1 - intro) * (1 - intro);
        if (!textured) outline(d, c, a, grow, time, age);
        float sky = a * Mth.clamp(age / 30F, 0, 1);
        switch (cl) {
            case TimeNet.C_STORM -> storm(d, c, f.id(), sky, time, ps, buf, textured);
            case TimeNet.C_SUN -> sun(d, c, f.id(), sky, time, ps, buf, textured);
            case TimeNet.C_AURORA -> { if (!textured) aurora(d, c, f.id(), sky, time); }
            case TimeNet.C_SNOW -> { if (!textured) d.ring(new Vec3(c.cx, c.cy + 0.06, c.cz), c.r, 0.5F, 0xCFF4FF, sky * 0.25F, 0); }
            default -> { }
        }
        if (textured && intro < 1) TimeClient.ground(ps, buf, GlowFx.HALO, new Vec3(c.cx, c.cy + 0.05, c.cz), c.r * 1.2F * grow, GREEN, a * (1 - intro) * 0.8F, 0);
    }

    private static Vec3 ringPoint(TimeClient.ZoneCache c, int k, float r, float lift) {
        int n = c.ring.length;
        double ang = Math.PI * 2 * k / n;
        return new Vec3(c.cx + Math.cos(ang) * r, c.ring[((k % n) + n) % n] + lift, c.cz + Math.sin(ang) * r);
    }

    /** Clean animated outline: terrain-following ring + inner band, thin rising cylinder lines, sweep rings, rotating markers. */
    private static void outline(TDraw d, TimeClient.ZoneCache c, float a, float grow, float time, float age) {
        int n = c.ring.length;
        float r = c.r * grow;
        int light = TDraw.lighten(GREEN, 0.45F);
        float pulse = 0.8F + 0.2F * Mth.sin(time * 2.2F);
        for (int k = 0; k < n; k++) {
            Vec3 p0 = ringPoint(c, k, r, 0.08F), p1 = ringPoint(c, k + 1, r, 0.08F);
            Vec3 q0 = ringPoint(c, k, r - 0.35F, 0.07F), q1 = ringPoint(c, k + 1, r - 0.35F, 0.07F);
            d.quad4(q0, p0, p1, q1, GREEN, 0, GREEN, a * 0.45F * pulse, GREEN, a * 0.45F * pulse, GREEN, 0);
            d.ribbon(p0, p1, 0.05F, light, a * pulse);
            d.ribbon(p0, p1, 0.16F, GREEN, a * 0.22F);
            // inner thin ring
            Vec3 i0 = ringPoint(c, k, r * 0.94F, 0.06F), i1 = ringPoint(c, k + 1, r * 0.94F, 0.06F);
            if ((k / 3) % 2 == 0) d.ribbon(i0, i1, 0.025F, GREEN, a * 0.6F);
        }
        // fine vertical lines rising and fading (cylinder)
        float hgt = 7F + 1.5F * Mth.sin(time * 0.8F);
        for (int k = 0; k < n; k += 3) {
            Vec3 b = ringPoint(c, k, r, 0.1F);
            float w = 0.55F + 0.45F * Mth.sin(time * 3 + k * 0.7F);
            d.fadeLine(b, b.add(0, hgt * (0.6F + 0.4F * w), 0), 0.035F, GREEN, a * 0.55F * w, 0);
        }
        // sweep rings climbing the cylinder
        for (int s = 0; s < 3; s++) {
            float h = ((time * 0.22F + s / 3F) % 1F);
            float al = a * (1 - h) * 0.7F;
            float y = h * hgt;
            for (int k = 0; k < n; k++) {
                Vec3 p0 = ringPoint(c, k, r, 0.1F + y), p1 = ringPoint(c, k + 1, r, 0.1F + y);
                d.ribbon(p0, p1, 0.03F, light, al);
            }
        }
        // rotating markers (vertical diamonds) on the edge, two directions
        for (int m = 0; m < 8; m++) {
            boolean ccw = m % 2 == 0;
            double ang = (ccw ? time * 0.35 : -time * 0.25) + Math.PI * 2 * (m / 2) / 4 + (ccw ? 0 : Math.PI / 4);
            int k = (int) Math.round(ang / (Math.PI * 2) * n);
            Vec3 base = new Vec3(c.cx + Math.cos(ang) * r, c.ring[((k % n) + n) % n], c.cz + Math.sin(ang) * r);
            Vec3 p = base.add(0, 1.3 + 0.25 * Math.sin(time * 2 + m), 0);
            Vec3 toCam = d.cam.subtract(p);
            Vec3 side = new Vec3(-toCam.z, 0, toCam.x);
            if (side.lengthSqr() < 1e-6) continue;
            side = side.normalize().scale(0.22);
            Vec3 top = p.add(0, 0.42, 0), bot = p.add(0, -0.42, 0), l = p.subtract(side), rr = p.add(side);
            d.line(top, rr, 0.03F, GREEN, a); d.line(rr, bot, 0.03F, GREEN, a); d.line(bot, l, 0.03F, GREEN, a); d.line(l, top, 0.03F, GREEN, a);
            d.spark(p, 0.09F, GREEN, a);
            d.fadeLine(base.add(0, 0.1, 0), bot, 0.02F, GREEN, a * 0.2F, a * 0.7F);
        }
        // placement flash: bright ring sweeping out
        if (age < 26) {
            float k2 = age / 26F;
            d.ring(new Vec3(c.cx, c.cy + 0.12, c.cz), Math.max(0.2F, c.r * k2), 0.5F * (1 - k2) + 0.05F, light, a * (1 - k2), 0);
        }
    }

    private static void storm(TDraw d, TimeClient.ZoneCache c, int seed, float a, float time, PoseStack ps, MultiBufferSource buf, boolean textured) {
        if (c.px.length == 0) return;
        int slot = (int) (time * 2.5F);
        float h = hash(slot, seed, 5);
        if (h > 0.35F) return;
        float local = time * 2.5F - slot;
        float flick = local < 0.15F ? 1 : local < 0.3F ? 0.3F : local < 0.45F ? 0.8F : Math.max(0, 1 - (local - 0.45F) * 2.5F);
        int k = (int) (hash(slot, seed, 6) * c.px.length) % c.px.length;
        int k2 = (int) (hash(slot, seed, 7) * c.px.length) % c.px.length;
        double rot = time * 0.06, cs = Math.cos(rot), sn = Math.sin(rot);
        Vec3 p = new Vec3(c.cx + c.px[k] * cs - c.pz[k] * sn, c.cloudY - 0.6, c.cz + c.px[k] * sn + c.pz[k] * cs);
        Vec3 q = new Vec3(c.cx + c.px[k2] * cs - c.pz[k2] * sn, c.cloudY - 0.9, c.cz + c.px[k2] * sn + c.pz[k2] * cs);
        if (textured) {
            TimeClient.flare(ps, buf, p, 9F, 0xB8D4FF, a * flick * 0.9F, 0);
            return;
        }
        // cloud-to-cloud crawler
        Vec3 prev = p;
        int segs = 10;
        for (int s = 1; s <= segs; s++) {
            Vec3 nx = p.lerp(q, s / (double) segs);
            if (s < segs) nx = nx.add((hash(slot, s, 8) - 0.5) * 1.6, (hash(slot, s, 9) - 0.5) * 0.8, (hash(slot, s, 10) - 0.5) * 1.6);
            d.line(prev, nx, 0.05F, 0xCFE0FF, a * flick);
            prev = nx;
        }
    }

    private static void sun(TDraw d, TimeClient.ZoneCache c, int seed, float a, float time, PoseStack ps, MultiBufferSource buf, boolean textured) {
        Vec3 sun = new Vec3(c.cx, c.cloudY + 14, c.cz);
        if (textured) {
            TimeClient.flare(ps, buf, sun, c.r * 0.9F, 0xFF9A2A, a * 0.85F, time * 0.2F);
            TimeClient.flare(ps, buf, sun, c.r * 0.4F, 0xFFF2C0, a, -time * 0.4F);
            TimeClient.ground(ps, buf, GlowFx.HALO, new Vec3(c.cx, c.cy + 0.05, c.cz), c.r * 1.1F, 0xFF8A20, a * 0.35F, 0);
            return;
        }
        // god rays
        for (int k = 0; k < 12; k++) {
            double ang = time * 0.06 + k * Math.PI * 2 / 12;
            double rr = c.r * (0.35 + 0.55 * hash(k, seed, 3));
            Vec3 g = new Vec3(c.cx + Math.cos(ang) * rr, 0, c.cz + Math.sin(ang) * rr);
            g = new Vec3(g.x, heightAt(c, g.x, g.z) + 0.2, g.z);
            float w = 0.5F + 0.5F * Mth.sin(time * 0.9F + k);
            d.ribbon(sun, g, 0.6F + 0.4F * w, 0xFFC060, a * 0.10F * w, 0xFFC060, a * 0.02F);
        }
        // heat shimmer: wavy strips rising from the ground
        int n = (int) Mth.clamp(c.r * 4, 24, 90);
        for (int k = 0; k < n; k++) {
            float h1 = hash(k, seed, 11), h2 = hash(k, seed, 12), h3 = hash(k, seed, 13);
            double rr = Math.sqrt(h1) * c.r * 0.95, ang = h2 * Math.PI * 2;
            double x = c.cx + Math.cos(ang) * rr, z = c.cz + Math.sin(ang) * rr;
            float gy = heightAt(c, x, z);
            float life = (time * 0.45F + h3) % 1F;
            Vec3 prev = null;
            for (int s = 0; s <= 5; s++) {
                double y = gy + 0.2 + (life * 2.5 + s * 0.35);
                Vec3 p = new Vec3(x + Math.sin(time * 5 + s * 1.3 + k) * 0.12, y, z + Math.cos(time * 4.3 + s * 1.1 + k) * 0.12);
                if (prev != null) d.ribbon(prev, p, 0.12F, 0xFFE2B0, a * 0.07F * (1 - life));
                prev = p;
            }
        }
        d.ring(new Vec3(c.cx, c.cy + 0.07, c.cz), c.r, 0.8F, 0xFF8A2A, a * 0.18F, 0);
    }

    private static void aurora(TDraw d, TimeClient.ZoneCache c, int seed, float a, float time) {
        int bands = 3;
        for (int b = 0; b < bands; b++) {
            double dirA = 0.4 + b * 1.05 + Math.sin(time * 0.03 + b) * 0.2;
            Vec3 u = new Vec3(Math.cos(dirA), 0, Math.sin(dirA)), w = new Vec3(-u.z, 0, u.x);
            double base = c.cloudY + 4 + b * 3.2;
            int segs = 44;
            Vec3 prevB = null, prevT = null; float prevA = 0;
            for (int s = 0; s <= segs; s++) {
                double t = -1 + 2.0 * s / segs;
                double off = Math.sin(t * 3.1 + time * 0.45 + b * 2) * c.r * 0.22 + Math.sin(t * 7 + time * 0.9) * c.r * 0.05 + (b - 1) * c.r * 0.28;
                Vec3 p = new Vec3(c.cx, base, c.cz).add(u.scale(t * c.r * 0.95)).add(w.scale(off));
                double dx = p.x - c.cx, dz = p.z - c.cz;
                float edge = (float) Mth.clamp((c.r - Math.sqrt(dx * dx + dz * dz)) / 4, 0, 1);
                float hgt = (float) (9 + 4 * Math.sin(t * 5 + time * 1.2 + b));
                Vec3 top = p.add(0, hgt, 0);
                float al = a * edge * (0.75F + 0.25F * Mth.sin((float) (t * 12 + time * 3)));
                if (prevB != null) {
                    int lo = b == 1 ? 0x30FFB0 : 0x3BFF7A, hi = b == 2 ? 0x9A5BFF : 0x4A7BFF;
                    d.quad4(prevB, p, top, prevT, lo, prevA * 0.42F, lo, al * 0.42F, hi, 0, hi, 0);
                    d.ribbon(prevB, p, 0.08F, 0xB0FFD8, al * 0.5F, 0xB0FFD8, al * 0.5F);
                }
                prevB = p; prevT = top; prevA = al;
            }
        }
        for (int k = 0; k < 40; k++) {
            double ang = hash(k, seed, 21) * Math.PI * 2, rr = Math.sqrt(hash(k, seed, 22)) * c.r;
            Vec3 p = new Vec3(c.cx + Math.cos(ang) * rr, c.cloudY + 18 + hash(k, seed, 23) * 6, c.cz + Math.sin(ang) * rr);
            float tw = Math.max(0, Mth.sin(time * (1.5F + hash(k, seed, 24) * 3) + k));
            d.spark(p, 0.12F, 0xDDF6FF, a * tw);
        }
    }

    private ZoneFx() {}
}
