package com.infinitystones.tensura.skill.ability.extra.time.client;

import com.infinitystones.tensura.client.render.GlowFx;
import com.infinitystones.tensura.skill.ability.extra.time.TimeNet;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Drawing of the newer climates (blizzard, ashfall, cherry blossom, supercell, meteor shower, toxic fog, starfall night,
 * heatwave, rainbow). Same passes as {@link ZoneFx}: translucent layers first, then additive glow (untextured) and flares
 * (textured). Everything is bounded by the camera range, so huge zones cost the same as small ones.
 */
final class ZoneFx2 {
    private static final int[] RAINBOW = {0xFF3B3B, 0xFF9A2E, 0xFFE23B, 0x4CE05A, 0x3BB8FF, 0x4B5CFF, 0xA05BFF};

    private static float hash(int a, int b, int s) { return ZoneFx.hash(a, b, s); }

    // ================================================================== translucent

    static void translucent(TDraw d, ClientLevel level, TimeClient.Inst i, TimeClient.ZoneCache c, float pt, float time, int cl, float a) {
        int id = i.f.id();
        float age = ZoneFx.zoneAge(i, pt);
        switch (cl) {
            case TimeNet.C_BLIZZARD -> {
                float g = Math.max(0, TimeNet.gust(id, age));
                double ang = TimeNet.gustAngle(id, age);
                double sx = Math.cos(ang) * (1.2 + 5.5 * g), sz = Math.sin(ang) * (1.2 + 5.5 * g);
                ZoneFx.clouds(d, c, a, time, 0xE9EFF4, 0xA9B4BF, 0.92F, 0.05F, false);
                precip(d, c, id, a, time, 2, 14F + 10F * g, sx, sz, 1.6F + 2.4F * g, 0.03F, 0xFFFFFF, 0.55F, 32, 4500);
                flakes2(d, c, id + 5, a, time, 3, 3.2F, 0.7F, 0.05F, 0xFFFFFF, 0xDCEBFA, 0.9F, 28, sx * 0.25, sz * 0.25, 3500);
                ZoneFx.walls(d, c, a, 0xF4F8FB, 0.5F, 12F, 3);
                ZoneFx.mist(d, c, a, time, 0xF0F4F8, 0.26F);
            }
            case TimeNet.C_ASH -> {
                ZoneFx.clouds(d, c, a, time, 0x4C443F, 0x1F1A17, 0.93F, 0.03F, true);
                flakes2(d, c, id, a, time, 3, 1.6F, 0.9F, 0.06F, 0x8C857F, 0x403A36, 0.85F, 30, 0.35, 0.15, 3500);
                precip(d, c, id + 3, a, time, 1, 8F, 0.2, 0.1, 0.5F, 0.02F, 0xFFA040, 0.35F, 26, 2500);
                ZoneFx.walls(d, c, a, 0x5A4C42, 0.3F, 10F, 2);
                ZoneFx.mist(d, c, a, time, 0x5C4E44, 0.16F);
            }
            case TimeNet.C_CHERRY -> {
                ZoneFx.clouds(d, c, a, time, 0xFFEAF2, 0xE9BDD0, 0.55F, 0.03F, false);
                petals(d, c, id, a, time);
                ZoneFx.mist(d, c, a, time, 0xFFD6E6, 0.07F);
            }
            case TimeNet.C_SUPERCELL -> {
                ZoneFx.clouds(d, c, a, time, 0x2E3138, 0x0B0C0F, 0.96F, 0.14F, true);
                wallCloud(d, c, a, time);
                double ang = time * 0.5;
                precip(d, c, id, a, time, 3, 28F, Math.cos(ang) * 2.2, Math.sin(ang) * 2.2, 1.8F, 0.025F, 0x95A5BA, 0.5F, 34, 5000);
                ZoneFx.mist(d, c, a, time, 0x6C727A, 0.12F);
            }
            case TimeNet.C_TOXIC -> {
                ZoneFx.walls(d, c, a, 0x86E04A, 0.38F, 10F, 3);
                ZoneFx.mist(d, c, a, time, 0x9BE060, 0.24F);
                precip(d, c, id, a, time, 1, 5F, 0.1, 0.05, 0.7F, 0.02F, 0xA4FF5A, 0.3F, 26, 2500);
            }
            case TimeNet.C_HEAT -> ZoneFx.mist(d, c, a, time, 0xFFE6B0, 0.07F);
            case TimeNet.C_RAINBOW -> {
                float rain = Mth.clamp(1 - (age - 300F) / 400F, 0, 1);
                ZoneFx.clouds(d, c, a * (0.25F + 0.75F * rain), time, 0xE9EDF2, 0xAAB2BB, 0.6F, 0.03F, false);
                if (rain > 0.02F) precip(d, c, id, a * rain, time, 1, 16F, 0.6, 0.2, 0.9F, 0.018F, 0xC4D8EC, 0.35F, 30, 3000);
            }
            default -> { }
        }
    }

    /** Falling streaks near the camera (same grid as the rain of the older climates). */
    static void precip(TDraw d, TimeClient.ZoneCache c, int seed, float a, float time, int perCell, float speed, double sx, double sz,
                       float len, float half, int col, float alpha, double range, int cap) {
        Vec3 cam = d.cam;
        int i0 = Math.max(0, (int) ((cam.x - range - (c.cx - c.r)) / c.cell)), i1 = Math.min(c.n - 1, (int) ((cam.x + range - (c.cx - c.r)) / c.cell) + 1);
        int j0 = Math.max(0, (int) ((cam.z - range - (c.cz - c.r)) / c.cell)), j1 = Math.min(c.n - 1, (int) ((cam.z + range - (c.cz - c.r)) / c.cell) + 1);
        int dd = Math.max(1, Math.round(perCell * c.scale * c.scale));
        float top = c.cloudY - 1;
        int count = 0;
        for (int i = i0; i <= i1; i++) for (int j = j0; j <= j1; j++) {
            double x = c.cx - c.r + i * c.cell + (hash(i, j, seed) - 0.5) * c.cell, z = c.cz - c.r + j * c.cell + (hash(j, i, seed + 1) - 0.5) * c.cell;
            double dx = x - c.cx, dz = z - c.cz;
            if (dx * dx + dz * dz > c.r * c.r) continue;
            double cdx = x - cam.x, cdz = z - cam.z, cd2 = cdx * cdx + cdz * cdz;
            if (cd2 > range * range) continue;
            float fall = top - c.h[i * c.n + j];
            if (fall < 2) continue;
            float near = (float) Mth.clamp(Math.sqrt(cd2) / 1.5, 0.15, 1);
            for (int k = 0; k < dd; k++) {
                float ph = (time * speed / fall + hash(i, j, k * 31 + seed)) % 1F;
                double y = top - ph * fall;
                Vec3 lo = new Vec3(x, y, z), hi = new Vec3(x + sx * 0.3, y + len, z + sz * 0.3);
                d.ribbon(lo, hi, half, col, a * alpha * near, col, 0);
                if (++count > cap) return;
            }
        }
    }

    /** Camera-facing flakes / ash / spores drifting down. */
    static void flakes2(TDraw d, TimeClient.ZoneCache c, int seed, float a, float time, int perCell, float speed, float sway, float size,
                        int colA, int colB, float alpha, double range, double driftX, double driftZ, int cap) {
        Vec3 cam = d.cam;
        int i0 = Math.max(0, (int) ((cam.x - range - (c.cx - c.r)) / c.cell)), i1 = Math.min(c.n - 1, (int) ((cam.x + range - (c.cx - c.r)) / c.cell) + 1);
        int j0 = Math.max(0, (int) ((cam.z - range - (c.cz - c.r)) / c.cell)), j1 = Math.min(c.n - 1, (int) ((cam.z + range - (c.cz - c.r)) / c.cell) + 1);
        int dd = Math.max(1, Math.round(perCell * c.scale * c.scale));
        float top = c.cloudY - 1;
        int count = 0;
        for (int i = i0; i <= i1; i++) for (int j = j0; j <= j1; j++) {
            double bx = c.cx - c.r + i * c.cell, bz = c.cz - c.r + j * c.cell;
            double dx = bx - c.cx, dz = bz - c.cz;
            if (dx * dx + dz * dz > c.r * c.r) continue;
            if ((bx - cam.x) * (bx - cam.x) + (bz - cam.z) * (bz - cam.z) > range * range) continue;
            float fall = top - c.h[i * c.n + j];
            if (fall < 2) continue;
            for (int k = 0; k < dd; k++) {
                float hs = hash(i, j, k * 17 + seed);
                float ph = (time * speed / fall + hs) % 1F;
                double y = top - ph * fall;
                double x = bx + (hash(j, i, k + seed) - 0.5) * c.cell + Math.sin(time * 1.3 + hs * 20) * sway + driftX * ph * fall * 0.2;
                double z = bz + (hash(i + 7, j, k + seed) - 0.5) * c.cell + Math.cos(time * 1.1 + hs * 13) * sway + driftZ * ph * fall * 0.2;
                Vec3 p = new Vec3(x, y, z);
                float s = size * (0.8F + hs * 0.8F);
                Vec3 toCam = cam.subtract(p);
                Vec3 side = toCam.cross(new Vec3(0, 1, 0));
                if (side.lengthSqr() < 1e-6) continue;
                side = side.normalize().scale(s);
                Vec3 up = side.cross(toCam).normalize().scale(s);
                d.quad(p.subtract(side).subtract(up), p.add(side).subtract(up), p.add(side).add(up), p.subtract(side).add(up), TDraw.mix(colA, colB, hs), a * alpha);
                if (++count > cap) return;
            }
        }
    }

    /** Petal storm: tumbling quads swirling around the zone centre while they fall. */
    private static void petals(TDraw d, TimeClient.ZoneCache c, int seed, float a, float time) {
        Vec3 cam = d.cam;
        double range = 30;
        int i0 = Math.max(0, (int) ((cam.x - range - (c.cx - c.r)) / c.cell)), i1 = Math.min(c.n - 1, (int) ((cam.x + range - (c.cx - c.r)) / c.cell) + 1);
        int j0 = Math.max(0, (int) ((cam.z - range - (c.cz - c.r)) / c.cell)), j1 = Math.min(c.n - 1, (int) ((cam.z + range - (c.cz - c.r)) / c.cell) + 1);
        int dd = Math.max(1, Math.round(3 * c.scale * c.scale));
        float top = c.cloudY - 2;
        int count = 0;
        for (int i = i0; i <= i1; i++) for (int j = j0; j <= j1; j++) {
            double bx = c.cx - c.r + i * c.cell, bz = c.cz - c.r + j * c.cell;
            double rx = bx - c.cx, rz = bz - c.cz, rr = Math.sqrt(rx * rx + rz * rz);
            if (rr > c.r) continue;
            if ((bx - cam.x) * (bx - cam.x) + (bz - cam.z) * (bz - cam.z) > range * range) continue;
            float fall = top - c.h[i * c.n + j];
            if (fall < 2) continue;
            double swirl = time * 0.28 * (1.2 - rr / c.r);
            double cs = Math.cos(swirl), sn = Math.sin(swirl);
            for (int k = 0; k < dd; k++) {
                float hs = hash(i, j, k * 13 + seed);
                float ph = (time * 1.5F / fall + hs) % 1F;
                double y = top - ph * fall + Math.sin(time * 2 + hs * 30) * 0.15;
                double ox = (hash(j, i, k + seed) - 0.5) * c.cell, oz = (hash(i + 3, j, k + seed) - 0.5) * c.cell;
                double x = c.cx + (rx + ox) * cs - (rz + oz) * sn + Math.sin(time * 1.1 + hs * 17) * 0.8;
                double z = c.cz + (rx + ox) * sn + (rz + oz) * cs + Math.cos(time * 0.9 + hs * 11) * 0.8;
                Vec3 p = new Vec3(x, y, z);
                double a1 = time * 2.1 + hs * 21, a2 = time * 1.3 + hs * 9;
                Vec3 n = new Vec3(Math.sin(a1) * Math.cos(a2), Math.cos(a1), Math.sin(a1) * Math.sin(a2));
                Vec3 u = n.cross(new Vec3(0.3, 1, 0.2));
                if (u.lengthSqr() < 1e-6) continue;
                u = u.normalize().scale(0.1 + hs * 0.05);
                Vec3 v = n.cross(u).scale(0.6);
                int col = TDraw.mix(0xFFC7DA, 0xFF8FB4, hash(k, i, j));
                d.quad(p.subtract(u).subtract(v), p.add(u).subtract(v), p.add(u).add(v), p.subtract(u).add(v), col, a * 0.92F);
                if (++count > 3500) return;
            }
        }
    }

    /** Supercell wall cloud: a low rotating ring of dark puffs under the layer. */
    private static void wallCloud(TDraw d, TimeClient.ZoneCache c, float a, float time) {
        int n = 14;
        float rad = c.r * 0.32F, size = Math.max(3F, c.r * 0.15F);
        for (int k = 0; k < n; k++) {
            double ang = time * 0.35 + k * Math.PI * 2 / n;
            double rr = rad * (1 + 0.12 * Math.sin(time * 0.6 + k * 2));
            Vec3 p = new Vec3(c.cx + Math.cos(ang) * rr, c.cloudY - 3.2 - 0.9 * Math.sin(time * 0.7 + k), c.cz + Math.sin(ang) * rr);
            float s = size * (0.85F + 0.25F * Mth.sin(time + k));
            d.puff(p, s, s * 0.55F, s * 0.9F, 0x1D2025, 0x050506, a * 0.95F);
            d.puff(p.add(0, -s * 0.5, 0), s * 0.6F, s * 0.35F, s * 0.6F, 0x0A0B0D, 0x020202, a * 0.9F);
        }
    }

    // ================================================================== additive glow / textured flares

    static void glow(TDraw d, ClientLevel level, TimeClient.Inst i, TimeClient.ZoneCache c, int cl, float sky, float time, float pt,
                     PoseStack ps, MultiBufferSource buf, boolean textured) {
        int id = i.f.id();
        float age = ZoneFx.zoneAge(i, pt);
        switch (cl) {
            case TimeNet.C_BLIZZARD -> { if (!textured) blizzardGlow(d, c, id, sky, time, age); }
            case TimeNet.C_ASH -> ash(d, c, id, sky, time, ps, buf, textured);
            case TimeNet.C_CHERRY -> cherry(d, c, id, sky, time, ps, buf, textured);
            case TimeNet.C_SUPERCELL -> supercell(d, c, id, sky, time, ps, buf, textured);
            case TimeNet.C_METEOR -> meteor(d, level, c, id, sky, time, ps, buf, textured);
            case TimeNet.C_TOXIC -> { if (!textured) toxic(d, c, id, sky, time); }
            case TimeNet.C_STARFALL -> starfall(d, c, id, sky, time, ps, buf, textured);
            case TimeNet.C_HEAT -> heat(d, level, c, id, sky, time, ps, buf, textured);
            case TimeNet.C_RAINBOW -> rainbow(d, c, id, sky, time, age, ps, buf, textured);
            default -> { }
        }
    }

    private static void blizzardGlow(TDraw d, TimeClient.ZoneCache c, int id, float a, float time, float age) {
        d.ring(new Vec3(c.cx, c.cy + 0.06, c.cz), c.r, 0.6F, 0xCFF4FF, a * 0.3F, 0);
        float g = Math.max(0, TimeNet.gust(id, age));
        if (g <= 0.02F) return;
        double ang = TimeNet.gustAngle(id, age);
        Vec3 dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
        Vec3 cam = d.cam;
        for (int k = 0; k < 22; k++) {
            double ox = (hash(k, id, 1) - 0.5) * 60, oz = (hash(k, id, 2) - 0.5) * 60, oy = hash(k, id, 3) * 14 - 2;
            float ph = (time * (1.4F + hash(k, id, 4)) + hash(k, id, 5)) % 1F;
            Vec3 p = cam.add(ox, oy, oz).add(dir.scale((ph - 0.5) * 50));
            Vec3 q = p.add(dir.scale(7 + 5 * hash(k, id, 6)));
            d.ribbon(p, q, 0.04F, 0xFFFFFF, 0, 0xFFFFFF, a * 0.28F * g * Mth.sin(ph * Mth.PI));
        }
    }

    private static void ash(TDraw d, TimeClient.ZoneCache c, int id, float a, float time, PoseStack ps, MultiBufferSource buf, boolean textured) {
        float flick = 0.75F + 0.25F * Mth.sin(time * 3.1F) * Mth.sin(time * 1.3F + 1);
        if (textured) {
            TimeClient.flare(ps, buf, new Vec3(c.cx, c.cloudY - 1.5, c.cz), c.r * 0.9F, 0xFF5A1A, a * 0.45F * flick, time * 0.05F);
            TimeClient.ground(ps, buf, GlowFx.HALO, new Vec3(c.cx, c.cy + 0.05, c.cz), c.r * 1.1F, 0xFF6A20, a * 0.18F, 0);
            return;
        }
        Vec3 cam = d.cam;
        for (int k = 0; k < 70; k++) {
            float h1 = hash(k, id, 41), h2 = hash(k, id, 42), h3 = hash(k, id, 43);
            float ph = (time * (0.12F + h3 * 0.1F) + h1) % 1F;
            double x = cam.x + (h1 - 0.5) * 50 + Math.sin(time * 0.9 + k) * 0.8, z = cam.z + (h2 - 0.5) * 50 + Math.cos(time * 0.8 + k) * 0.8;
            double dx = x - c.cx, dz = z - c.cz;
            if (dx * dx + dz * dz > c.r * c.r) continue;
            double y = cam.y + 14 - ph * 24;
            float tw = 0.5F + 0.5F * Mth.sin(time * 6 + k * 1.7F);
            d.spark(new Vec3(x, y, z), 0.06F + 0.05F * h3, TDraw.mix(0xFF4A10, 0xFFC060, tw), a * (0.5F + 0.5F * tw));
        }
        d.ring(new Vec3(c.cx, c.cy + 0.07, c.cz), c.r, 0.8F, 0xFF5A20, a * 0.16F * flick, 0);
    }

    private static void cherry(TDraw d, TimeClient.ZoneCache c, int id, float a, float time, PoseStack ps, MultiBufferSource buf, boolean textured) {
        if (textured) {
            TimeClient.ground(ps, buf, GlowFx.HALO, new Vec3(c.cx, c.cy + 0.05, c.cz), c.r * 1.15F, 0xFFB6D0, a * (0.26F + 0.06F * Mth.sin(time * 0.8F)), 0);
            TimeClient.flare(ps, buf, new Vec3(c.cx, c.cloudY + 10, c.cz), c.r * 0.5F, 0xFFD6E6, a * 0.35F, time * 0.1F);
            return;
        }
        d.ring(new Vec3(c.cx, c.cy + 0.06, c.cz), c.r, 0.5F, 0xFFC4DA, a * 0.28F, 0);
        Vec3 cam = d.cam;
        for (int k = 0; k < 40; k++) {
            float h1 = hash(k, id, 51), h2 = hash(k, id, 52), h3 = hash(k, id, 53);
            double x = cam.x + (h1 - 0.5) * 40, z = cam.z + (h2 - 0.5) * 40;
            double dx = x - c.cx, dz = z - c.cz;
            if (dx * dx + dz * dz > c.r * c.r) continue;
            double y = ZoneFx.heightAt(c, x, z) + 0.5 + ((time * 0.15F + h3) % 1F) * 7;
            float tw = Math.max(0, Mth.sin(time * (1.5F + h3 * 2) + k));
            d.spark(new Vec3(x, y, z), 0.07F, 0xFFE4EE, a * tw * 0.8F);
        }
    }

    private static void supercell(TDraw d, TimeClient.ZoneCache c, int id, float a, float time, PoseStack ps, MultiBufferSource buf, boolean textured) {
        ZoneFx.storm(d, c, id, a, time * 1.6F, ps, buf, textured);
        ZoneFx.storm(d, c, id + 99, a, time * 1.3F + 40, ps, buf, textured);
        if (textured) return;
        float flick = Math.max(0, Mth.sin(time * 9F) * Mth.sin(time * 4.3F));
        int n = 14;
        float rad = c.r * 0.32F;
        for (int k = 0; k < n; k++) {
            double a0 = time * 0.35 + k * Math.PI * 2 / n, a1 = time * 0.35 + (k + 1) * Math.PI * 2 / n;
            Vec3 p0 = new Vec3(c.cx + Math.cos(a0) * rad, c.cloudY - 5.5, c.cz + Math.sin(a0) * rad);
            Vec3 p1 = new Vec3(c.cx + Math.cos(a1) * rad, c.cloudY - 5.5, c.cz + Math.sin(a1) * rad);
            d.ribbon(p0, p1, 0.18F, 0x9CB4FF, a * (0.1F + 0.25F * flick));
        }
        d.ring(new Vec3(c.cx, c.cy + 0.07, c.cz), c.r, 0.5F, 0x6A7CA8, a * 0.12F, 0);
    }

    private static void meteor(TDraw d, ClientLevel level, TimeClient.ZoneCache c, int id, float a, float time, PoseStack ps, MultiBufferSource buf, boolean textured) {
        if (textured) {
            TimeClient.ground(ps, buf, GlowFx.HALO, new Vec3(c.cx, c.cy + 0.05, c.cz), c.r * 1.1F, 0x6A8CFF, a * 0.16F, 0);
            return;
        }
        d.ring(new Vec3(c.cx, c.cy + 0.06, c.cz), c.r, 0.5F, 0x9CB8FF, a * 0.22F, 0);
        stars(d, c, id, a, time, 70);
        Vec3 cam = d.cam;
        // shooting stars across the dome
        for (int k = 0; k < 9; k++) {
            float period = 3.2F + hash(k, id, 61) * 3.5F;
            float ph = (time / period + hash(k, id, 62)) % 1F;
            float life = 0.2F;
            if (ph > life) continue;
            float t = ph / life;
            double ang = hash(k, id, 63) * Math.PI * 2 + (int) (time / period + hash(k, id, 62)) * 1.7;
            double rad = 40 + hash(k, id, 64) * 70;
            Vec3 start = new Vec3(cam.x + Math.cos(ang) * rad, cam.y + 45 + hash(k, id, 65) * 40, cam.z + Math.sin(ang) * rad);
            double da = ang + 1.2 + hash(k, id, 66);
            Vec3 dir = new Vec3(Math.cos(da) * 0.8, -0.55, Math.sin(da) * 0.8).normalize();
            Vec3 head = start.add(dir.scale(t * 110));
            Vec3 tail = head.subtract(dir.scale(22 * Math.min(1, t * 4)));
            float al = a * Mth.sin(t * Mth.PI);
            d.fadeLine(tail, head, 0.12F, 0x9CB8FF, 0, al);
            d.fadeLine(head.subtract(dir.scale(9)), head, 0.05F, 0xFFFFFF, 0, al);
            d.spark(head, 0.4F, 0xFFFFFF, al);
        }
        // a rare harmless sparkle lands near the viewer
        float slotLen = 7F;
        int slot = (int) (time / slotLen);
        float local = time - slot * slotLen;
        if (hash(slot, id, 71) < 0.8F && local < 2.4F) {
            double ang = hash(slot, id, 72) * Math.PI * 2, rr = 8 + hash(slot, id, 73) * 22;
            double x = cam.x + Math.cos(ang) * rr, z = cam.z + Math.sin(ang) * rr;
            double dx = x - c.cx, dz = z - c.cz;
            if (dx * dx + dz * dz < c.r * c.r) {
                double gy = ZoneFx.heightAt(c, x, z);
                Vec3 land = new Vec3(x, gy + 0.2, z);
                if (local < 0.7F) {
                    float t = local / 0.7F;
                    Vec3 dir = new Vec3(0.5, -0.8, 0.3).normalize();
                    Vec3 head = land.subtract(dir.scale((1 - t) * 60));
                    d.fadeLine(head.subtract(dir.scale(14)), head, 0.1F, 0xBFD6FF, 0, a);
                    d.spark(head, 0.35F, 0xFFFFFF, a);
                } else {
                    float t = (local - 0.7F) / 1.7F;
                    d.ring(land, 0.3F + t * 3.5F, 0.08F, 0xBFD6FF, a * (1 - t), 0);
                    for (int s = 0; s < 14; s++) {
                        double sa = s * Math.PI * 2 / 14 + hash(s, slot, 74);
                        double sr = t * 2.4 * (0.5 + hash(s, slot, 75));
                        d.spark(land.add(Math.cos(sa) * sr, 0.3 + Math.sin(t * Math.PI) * 1.5 * hash(s, slot, 76), Math.sin(sa) * sr), 0.09F, 0xE8F2FF, a * (1 - t));
                    }
                }
            }
        }
    }

    private static void stars(TDraw d, TimeClient.ZoneCache c, int id, float a, float time, int n) {
        for (int k = 0; k < n; k++) {
            double ang = hash(k, id, 81) * Math.PI * 2, rr = Math.sqrt(hash(k, id, 82)) * c.r;
            Vec3 p = new Vec3(c.cx + Math.cos(ang) * rr, c.cloudY + 14 + hash(k, id, 83) * 30, c.cz + Math.sin(ang) * rr);
            float tw = Math.max(0, Mth.sin(time * (1.2F + hash(k, id, 84) * 3) + k));
            d.spark(p, 0.18F, 0xDDEBFF, a * tw);
        }
    }

    private static void toxic(TDraw d, TimeClient.ZoneCache c, int id, float a, float time) {
        d.ring(new Vec3(c.cx, c.cy + 0.06, c.cz), c.r, 0.7F, 0x8CFF4A, a * 0.25F, 0);
        Vec3 cam = d.cam;
        for (int k = 0; k < 46; k++) {
            float h1 = hash(k, id, 91), h2 = hash(k, id, 92), h3 = hash(k, id, 93);
            double x = cam.x + (h1 - 0.5) * 36, z = cam.z + (h2 - 0.5) * 36;
            double dx = x - c.cx, dz = z - c.cz;
            if (dx * dx + dz * dz > c.r * c.r) continue;
            float life = (time * (0.25F + h3 * 0.3F) + h1) % 1F;
            double y = ZoneFx.heightAt(c, x, z) + 0.2 + life * 5;
            float sz = 0.08F + 0.12F * h3 * (1 - life * 0.5F);
            d.spark(new Vec3(x + Math.sin(time + k) * 0.3, y, z + Math.cos(time * 0.8 + k) * 0.3), sz, TDraw.mix(0x6BFF3A, 0xD8FF8A, h3), a * 0.7F * Mth.sin(life * Mth.PI));
        }
    }

    private static void starfall(TDraw d, TimeClient.ZoneCache c, int id, float a, float time, PoseStack ps, MultiBufferSource buf, boolean textured) {
        if (textured) {
            TimeClient.flare(ps, buf, new Vec3(c.cx + c.r * 0.45, c.cloudY + 40, c.cz + c.r * 0.3), 18F, 0xBFD8FF, a * 0.6F, 0);
            TimeClient.ground(ps, buf, GlowFx.HALO, new Vec3(c.cx, c.cy + 0.05, c.cz), c.r * 1.1F, 0x7A6CFF, a * 0.18F, 0);
            return;
        }
        d.ring(new Vec3(c.cx, c.cy + 0.06, c.cz), c.r, 0.5F, 0xA9B8FF, a * 0.22F, 0);
        ZoneFx.aurora(d, c, id, a * 1.15F, time);
        ZoneFx.aurora(d, c, id + 17, a * 0.8F, time * 0.8F + 50);
        stars(d, c, id, a, time, 90);
        Vec3 cam = d.cam;
        // slow falling stars drifting through the whole zone
        for (int k = 0; k < 80; k++) {
            float h1 = hash(k, id, 101), h2 = hash(k, id, 102), h3 = hash(k, id, 103);
            double x = cam.x + (h1 - 0.5) * 70, z = cam.z + (h2 - 0.5) * 70;
            double dx = x - c.cx, dz = z - c.cz;
            if (dx * dx + dz * dz > c.r * c.r) continue;
            float ph = (time * (0.05F + h3 * 0.05F) + h1) % 1F;
            double y = cam.y + 24 - ph * 40;
            float tw = 0.5F + 0.5F * Mth.sin(time * 4 + k);
            Vec3 p = new Vec3(x + Math.sin(time * 0.4 + k) * 1.2, y, z + Math.cos(time * 0.3 + k) * 1.2);
            int col = TDraw.mix(0xFFE9A0, 0x9CE8FF, h3);
            d.spark(p, 0.2F + 0.15F * h3, col, a * tw);
            d.fadeLine(p.add(0, 1.8, 0), p, 0.04F, col, 0, a * 0.5F * tw);
        }
    }

    private static void heat(TDraw d, ClientLevel level, TimeClient.ZoneCache c, int id, float a, float time, PoseStack ps, MultiBufferSource buf, boolean textured) {
        ZoneFx.sun(d, c, id, a, time, ps, buf, textured);
        if (textured) {
            TimeClient.flare(ps, buf, new Vec3(c.cx, c.cloudY + 14, c.cz), c.r * 1.3F, 0xFFD070, a * 0.35F, -time * 0.1F);
            return;
        }
        Vec3 cam = d.cam;
        BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
        for (int k = 0; k < 64; k++) {
            float h1 = hash(k, id, 111), h2 = hash(k, id, 112), h3 = hash(k, id, 113);
            double x = cam.x + (h1 - 0.5) * 70, z = cam.z + (h2 - 0.5) * 70;
            double dx = x - c.cx, dz = z - c.cz;
            if (dx * dx + dz * dz > c.r * c.r) continue;
            int gy = (int) ZoneFx.heightAt(c, x, z);
            bp.set(Mth.floor(x), gy - 1, Mth.floor(z));
            if (!level.getFluidState(bp).is(FluidTags.WATER)) continue;
            float life = (time * 0.35F + h3) % 1F;
            Vec3 prev = null;
            for (int s = 0; s <= 6; s++) {
                double y = gy + 0.1 + life * 3 + s * 0.45;
                Vec3 p = new Vec3(x + Math.sin(time * 2 + s * 0.9 + k) * 0.35, y, z + Math.cos(time * 1.7 + s * 0.8 + k) * 0.35);
                if (prev != null) d.ribbon(prev, p, 0.18F, 0xFFFFFF, a * 0.16F * (1 - life), 0xFFFFFF, a * 0.16F * (1 - life));
                prev = p;
            }
        }
    }

    private static void rainbow(TDraw d, TimeClient.ZoneCache c, int id, float a, float time, float age, PoseStack ps, MultiBufferSource buf, boolean textured) {
        float shown = Mth.clamp((age - 250F) / 250F, 0, 1);
        if (shown <= 0.01F) return;
        double dirA = id * 2.1 + 0.6;
        Vec3 u = new Vec3(Math.cos(dirA + Math.PI / 2), 0, Math.sin(dirA + Math.PI / 2));
        Vec3 back = new Vec3(Math.cos(dirA), 0, Math.sin(dirA));
        Vec3 base = new Vec3(c.cx, c.cy, c.cz).subtract(back.scale(c.r * 0.35));
        float R = c.r * 0.85F + 8;
        if (textured) {
            TimeClient.flare(ps, buf, new Vec3(c.cx, c.cloudY + 22, c.cz).add(back.scale(c.r * 0.9)), 7F, 0xFFF0C0, a * shown * 0.55F, 0);
            return;
        }
        float bw = Math.max(0.7F, R * 0.032F);
        int segs = 56;
        for (int band = 0; band < 7; band++) {
            double r0 = R + (band - 3) * bw, r1 = r0 + bw * 0.95;
            int col = RAINBOW[band];
            for (int s = 0; s < segs; s++) {
                double t0 = Math.PI * s / segs, t1 = Math.PI * (s + 1) / segs;
                float e0 = Mth.sin((float) t0), e1 = Mth.sin((float) t1);
                float sh0 = 0.85F + 0.15F * Mth.sin(time * 1.5F + s * 0.3F + band), sh1 = 0.85F + 0.15F * Mth.sin(time * 1.5F + (s + 1) * 0.3F + band);
                Vec3 a0 = arc(base, u, r0, t0), b0 = arc(base, u, r1, t0), a1 = arc(base, u, r0, t1), b1 = arc(base, u, r1, t1);
                float k0 = a * shown * 0.32F * Math.min(1, e0 * 3F) * sh0, k1 = a * shown * 0.32F * Math.min(1, e1 * 3F) * sh1;
                d.quad4(a0, b0, b1, a1, col, k0, col, k0, col, k1, col, k1);
            }
        }
        for (int k = 0; k < 36; k++) {
            double t = hash(k, id, 121) * Math.PI;
            int band = (int) (hash(k, id, 122) * 7);
            Vec3 p = arc(base, u, R + (band - 3) * bw, t).add(0, Math.sin(time * 2 + k) * 0.3, 0);
            float tw = Math.max(0, Mth.sin(time * (1.5F + hash(k, id, 123) * 2) + k));
            d.spark(p, 0.2F, 0xFFFFFF, a * shown * tw * 0.8F);
        }
    }

    private static Vec3 arc(Vec3 base, Vec3 u, double r, double t) {
        return base.add(u.scale(Math.cos(t) * r)).add(0, Math.sin(t) * r * 0.8, 0);
    }

    private ZoneFx2() {}
}
