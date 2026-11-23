package com.infinitystones.tensura.skill.ability.extra.space.client;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The inside of a Spiritual Gathering: a painted Japanese dojo forest that fully covers the real world. Static parts
 * (dome, floor, panoramas, trees, torii, lanterns) are baked once into meshes; animated parts (sun, fog, petals, water
 * ring, magic circles, lantern lights) are emitted per frame. Everything is relative to the scene centre; the floor is y = 0.
 */
final class GatherScenery {
    /** Per-frame context. m already contains the translation to the scene centre. */
    record Ctx(Matrix4f m, Vec3 cam, Vec3 right, Vec3 up, float alpha, float ticks, float ringR, float sceneR, long seed,
               List<Vec3> feet, float waterGrow) {}

    private static boolean built;
    private static long bSeed; private static float bRing, bScene;
    private static GatherQ.Mesh sky, ground, platform, far, mid, near, mist, props, cherries, pines;
    private static final List<Vec3> LANTERNS = new ArrayList<>();
    private static double pathAng, sunAng;

    private static final float[] WHITE_C = {1, 1, 1, 1};

    // ================================================================== build

    private static void build(float ringR, float sceneR, long seed) {
        if (built && seed == bSeed && ringR == bRing && sceneR == bScene) return;
        built = true; bSeed = seed; bRing = ringR; bScene = sceneR;
        GatherTex.ensure();
        Random rnd = new Random(seed);
        float S = sceneR, D = S * 1.1F;
        pathAng = rnd.nextDouble() * Math.PI * 2;
        sunAng = pathAng + Math.PI * (0.75 + rnd.nextDouble() * 0.5);
        LANTERNS.clear();

        sky = new GatherQ.Mesh(); buildSky(new GatherQ(sky), D);
        ground = new GatherQ.Mesh(); buildGround(new GatherQ(ground), D);
        platform = new GatherQ.Mesh(); buildPlatform(new GatherQ(platform), ringR, S);
        far = new GatherQ.Mesh(); cylinder(new GatherQ(far), S * 0.98F, S * 0.98F * 0.46F, 96, 1, 1, 1, 1);
        mid = new GatherQ.Mesh(); cylinder(new GatherQ(mid), S * 0.86F, S * 0.86F * 0.30F, 96, 2, 1, 1, 1);
        near = new GatherQ.Mesh(); cylinder(new GatherQ(near), S * 0.72F, S * 0.72F * 0.40F, 96, 3, 0.92F, 0.86F, 0.9F);
        mist = new GatherQ.Mesh(); buildMist(new GatherQ(mist), S);
        props = new GatherQ.Mesh(); buildProps(new GatherQ(props), rnd, ringR, S);
        cherries = new GatherQ.Mesh(); pines = new GatherQ.Mesh();
        buildTrees(new GatherQ(cherries), new GatherQ(pines), rnd, ringR, S);
    }

    private static float[] c(float r, float g, float b, float a) { return new float[]{r, g, b, a}; }

    private static float[] skyColor(double elev) {
        double[][] k = {{-0.15, 1.00, 0.86, 0.72}, {0.0, 1.00, 0.87, 0.74}, {0.18, 1.00, 0.72, 0.69}, {0.55, 0.84, 0.60, 0.72},
                {0.95, 0.47, 0.44, 0.70}, {1.58, 0.24, 0.26, 0.54}};
        for (int i = 0; i < k.length - 1; i++) {
            if (elev <= k[i + 1][0]) {
                float t = (float) ((elev - k[i][0]) / (k[i + 1][0] - k[i][0]));
                return c((float) Mth.lerp(t, k[i][1], k[i + 1][1]), (float) Mth.lerp(t, k[i][2], k[i + 1][2]), (float) Mth.lerp(t, k[i][3], k[i + 1][3]), 1);
            }
        }
        return c(0.24F, 0.26F, 0.54F, 1);
    }

    private static void buildSky(GatherQ q, float D) {
        float[] uv = GatherTex.uv(GatherTex.WHITE);
        float[] w = {(uv[0] + uv[2]) / 2, (uv[1] + uv[3]) / 2};
        int lon = 48, lat = 18;
        double e0 = -0.15, e1 = Math.PI / 2;
        for (int i = 0; i < lat; i++) {
            double ea = Mth.lerp((double) i / lat, e0, e1), eb = Mth.lerp((double) (i + 1) / lat, e0, e1);
            // finer steps near the horizon
            ea = e0 + (e1 - e0) * Math.pow((double) i / lat, 1.6); eb = e0 + (e1 - e0) * Math.pow((double) (i + 1) / lat, 1.6);
            for (int j = 0; j < lon; j++) {
                double aa = Math.PI * 2 * j / lon, ab = Math.PI * 2 * (j + 1) / lon;
                q.quadC(sph(D, ea, aa), sph(D, ea, ab), sph(D, eb, ab), sph(D, eb, aa), w, w, w, w, skyColor(ea), skyColor(ea), skyColor(eb), skyColor(eb));
            }
        }
    }

    private static Vec3 sph(double r, double elev, double az) {
        return new Vec3(Math.cos(elev) * Math.cos(az) * r, Math.sin(elev) * r, Math.cos(elev) * Math.sin(az) * r);
    }

    private static void buildGround(GatherQ q, float D) {
        float[] uv = GatherTex.uv(GatherTex.WHITE);
        int rings = 14, seg = 56;
        for (int i = 0; i < rings; i++) {
            double r0 = D * Math.pow((double) i / rings, 1.4), r1 = D * Math.pow((double) (i + 1) / rings, 1.4);
            for (int j = 0; j < seg; j++) {
                double a0 = Math.PI * 2 * j / seg, a1 = Math.PI * 2 * (j + 1) / seg;
                Vec3 p00 = new Vec3(Math.cos(a0) * r0, -0.05, Math.sin(a0) * r0), p01 = new Vec3(Math.cos(a1) * r0, -0.05, Math.sin(a1) * r0);
                Vec3 p11 = new Vec3(Math.cos(a1) * r1, -0.05, Math.sin(a1) * r1), p10 = new Vec3(Math.cos(a0) * r1, -0.05, Math.sin(a0) * r1);
                float[] c0 = groundColor(r0 / D), c1 = groundColor(r1 / D);
                q.quadC(p00, p01, p11, p10, uvOf(p00, 6), uvOf(p01, 6), uvOf(p11, 6), uvOf(p10, 6), c0, c0, c1, c1);
            }
        }
    }

    private static float[] groundColor(double k) {
        float f = (float) Math.pow(Mth.clamp((float) k, 0, 1), 1.9);
        return c(Mth.lerp(f, 1.0F, 0.98F), Mth.lerp(f, 1.0F, 0.84F), Mth.lerp(f, 1.0F, 0.86F), 1);
    }

    private static float[] uvOf(Vec3 p, float tile) { return new float[]{(float) p.x / tile, (float) p.z / tile}; }

    private static void buildPlatform(GatherQ q, float ringR, float S) {
        double R = ringR + 3.2;
        int seg = 80;
        for (int j = 0; j < seg; j++) {
            double a0 = Math.PI * 2 * j / seg, a1 = Math.PI * 2 * (j + 1) / seg;
            Vec3 c0 = new Vec3(0, -0.01, 0), p0 = new Vec3(Math.cos(a0) * R, -0.01, Math.sin(a0) * R), p1 = new Vec3(Math.cos(a1) * R, -0.01, Math.sin(a1) * R);
            q.quadC(c0, p0, p1, p1, uvOf(c0, 4), uvOf(p0, 4), uvOf(p1, 4), uvOf(p1, 4), c(1, 1, 1, 1), c(0.96F, 0.96F, 1F, 1), c(0.96F, 0.96F, 1F, 1), c(0.96F, 0.96F, 1F, 1));
            // border step
            double R2 = R + 0.45;
            Vec3 o0 = new Vec3(Math.cos(a0) * R2, -0.01, Math.sin(a0) * R2), o1 = new Vec3(Math.cos(a1) * R2, -0.01, Math.sin(a1) * R2);
            q.quadC(p0, o0, o1, p1, uvOf(p0, 2), uvOf(o0, 2), uvOf(o1, 2), uvOf(p1, 2), c(0.62F, 0.62F, 0.68F, 1), c(0.62F, 0.62F, 0.68F, 1), c(0.62F, 0.62F, 0.68F, 1), c(0.62F, 0.62F, 0.68F, 1));
        }
        // stone path to the great torii
        Vec3 f = new Vec3(Math.cos(pathAng), 0, Math.sin(pathAng)), s = new Vec3(-f.z, 0, f.x);
        double from = R + 0.4, to = S * 0.74, hw = 1.7;
        for (double d = from; d < to; d += 3.4) {
            double d1 = Math.min(to, d + 3.4);
            Vec3 a = f.scale(d).add(s.scale(-hw)).add(0, -0.005, 0), b = f.scale(d).add(s.scale(hw)).add(0, -0.005, 0);
            Vec3 cc = f.scale(d1).add(s.scale(hw)).add(0, -0.005, 0), dd = f.scale(d1).add(s.scale(-hw)).add(0, -0.005, 0);
            float haze = (float) Mth.clamp((float) (d / to), 0, 1);
            float[] col = c(1 - haze * 0.15F, 1 - haze * 0.25F, 1 - haze * 0.22F, 1);
            q.quadC(a, b, cc, dd, new float[]{0, (float) (d / 3.4)}, new float[]{1, (float) (d / 3.4)}, new float[]{1, (float) (d1 / 3.4)}, new float[]{0, (float) (d1 / 3.4)}, col, col, col, col);
        }
    }

    private static void cylinder(GatherQ q, float r, float hgt, int seg, int tiles, float cr, float cg, float cb) {
        float[] none = c(cr, cg, cb, 1);
        for (int j = 0; j < seg; j++) {
            double a0 = Math.PI * 2 * j / seg, a1 = Math.PI * 2 * (j + 1) / seg;
            float u0 = tiles * (float) j / seg, u1 = tiles * (float) (j + 1) / seg;
            Vec3 b0 = new Vec3(Math.cos(a0) * r, -0.02, Math.sin(a0) * r), b1 = new Vec3(Math.cos(a1) * r, -0.02, Math.sin(a1) * r);
            Vec3 t1 = b1.add(0, hgt, 0), t0 = b0.add(0, hgt, 0);
            q.quadC(b0, b1, t1, t0, new float[]{u0, 1}, new float[]{u1, 1}, new float[]{u1, 0}, new float[]{u0, 0}, none, none, none, none);
        }
    }

    private static void buildMist(GatherQ q, float S) {
        float[] uv = GatherTex.uv(GatherTex.WHITE);
        float[] w = {(uv[0] + uv[2]) / 2, (uv[1] + uv[3]) / 2};
        double[][] bands = {{0.995, 0.26, 0.92}, {0.93, 0.18, 0.80}, {0.80, 0.12, 0.66}, {0.64, 0.07, 0.50}};
        int seg = 72;
        for (double[] bd : bands) {
            double r = S * bd[0], hgt = S * bd[1];
            float[] lo = c(1, 0.90F, 0.92F, (float) bd[2]), hi = c(1, 0.92F, 0.94F, 0);
            for (int j = 0; j < seg; j++) {
                double a0 = Math.PI * 2 * j / seg, a1 = Math.PI * 2 * (j + 1) / seg;
                Vec3 b0 = new Vec3(Math.cos(a0) * r, 0.0, Math.sin(a0) * r), b1 = new Vec3(Math.cos(a1) * r, 0.0, Math.sin(a1) * r);
                q.quadC(b0, b1, b1.add(0, hgt, 0), b0.add(0, hgt, 0), w, w, w, w, lo, lo, hi, hi);
            }
        }
    }

    // ------------------------------------------------------------------ props: torii, lanterns, trees

    private static void obox(GatherQ q, Vec3 c, Vec3 f, Vec3 s, double hf, double h, double hs, float r, float g, float b) {
        float[] uv = GatherTex.uv(GatherTex.WHITE);
        Vec3[] bot = {c.add(f.scale(-hf)).add(s.scale(-hs)), c.add(f.scale(hf)).add(s.scale(-hs)), c.add(f.scale(hf)).add(s.scale(hs)), c.add(f.scale(-hf)).add(s.scale(hs))};
        Vec3[] top = new Vec3[4];
        for (int i = 0; i < 4; i++) top[i] = bot[i].add(0, h, 0);
        for (int i = 0; i < 4; i++) {
            int k = (i + 1) % 4;
            float sh = i % 2 == 0 ? 0.84F : 0.7F;
            q.quad(bot[i], bot[k], top[k], top[i], uv, r * sh, g * sh, b * sh, 1);
        }
        q.quad(top[0], top[1], top[2], top[3], uv, r, g, b, 1);
    }

    private static void torii(GatherQ q, Vec3 base, Vec3 f, double width, double height) {
        Vec3 s = new Vec3(-f.z, 0, f.x);
        float hz = (float) Math.min(1, base.length() / 200);
        float red = 0.72F, rg = 0.16F, rb = 0.17F;
        for (int k = -1; k <= 1; k += 2) {
            Vec3 pc = base.add(s.scale(k * width / 2));
            obox(q, pc, f, s, 0.62, 0.35, 0.62, 0.20F, 0.19F, 0.21F);
            obox(q, pc.add(0, 0.35, 0), f, s, 0.42, height - 0.35, 0.42, red, rg, rb);
        }
        obox(q, base.add(0, height * 0.72, 0), f, s, 0.26, 0.45, width / 2 + 0.2, red, rg, rb);
        obox(q, base.add(0, height - 0.2, 0), f, s, 0.36, 0.5, width / 2 + 1.5, 0.12F, 0.10F, 0.13F);
        obox(q, base.add(0, height + 0.3, 0), f, s, 0.7, 0.28, width / 2 + 2.2, 0.10F, 0.09F, 0.12F);
        obox(q, base.add(0, height - 0.55, 0), f, s, 0.3, 0.35, width / 2 + 0.7, red, rg, rb);
    }

    private static void lantern(GatherQ q, Vec3 p, double sc) {
        Vec3 f = new Vec3(1, 0, 0), s = new Vec3(0, 0, 1);
        obox(q, p, f, s, 0.45 * sc, 0.22 * sc, 0.45 * sc, 0.55F, 0.55F, 0.6F);
        obox(q, p.add(0, 0.22 * sc, 0), f, s, 0.17 * sc, 0.8 * sc, 0.17 * sc, 0.62F, 0.62F, 0.66F);
        obox(q, p.add(0, 1.02 * sc, 0), f, s, 0.4 * sc, 0.14 * sc, 0.4 * sc, 0.58F, 0.58F, 0.62F);
        obox(q, p.add(0, 1.16 * sc, 0), f, s, 0.3 * sc, 0.46 * sc, 0.3 * sc, 1.0F, 0.80F, 0.46F);
        obox(q, p.add(0, 1.62 * sc, 0), f, s, 0.62 * sc, 0.2 * sc, 0.62 * sc, 0.52F, 0.52F, 0.57F);
        obox(q, p.add(0, 1.82 * sc, 0), f, s, 0.26 * sc, 0.2 * sc, 0.26 * sc, 0.56F, 0.56F, 0.6F);
        LANTERNS.add(p.add(0, 1.4 * sc, 0));
    }

    private static void buildProps(GatherQ q, Random rnd, float ringR, float S) {
        Vec3 f = new Vec3(Math.cos(pathAng), 0, Math.sin(pathAng)), s = new Vec3(-f.z, 0, f.x);
        torii(q, f.scale(S * 0.72), f, 9.0, 11.5);
        torii(q, f.scale(S * 0.54), f, 6.2, 8.0);
        torii(q, f.scale(S * 0.40), f, 4.6, 6.0);
        // lanterns beside the path and around the platform
        for (double d = ringR + 6; d < S * 0.62; d += 7.5)
            for (int k = -1; k <= 1; k += 2) lantern(q, f.scale(d).add(s.scale(k * 2.7)), 1.3);
        int n = 14;
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + pathAng + Math.PI / n;
            lantern(q, new Vec3(Math.cos(a) * (ringR + 4.4), 0, Math.sin(a) * (ringR + 4.4)), 1.4);
        }
    }

    private static void buildTrees(GatherQ qc, GatherQ qp, Random rnd, float ringR, float S) {
        int n = 70;
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = ringR + 9 + rnd.nextDouble() * (S * 0.6 - ringR - 9);
            double da = Math.abs(Mth.wrapDegrees((float) Math.toDegrees(a - pathAng)));
            if (da < 5 + 90 / Math.max(r, 10) * 4) continue;
            boolean cherry = rnd.nextFloat() < 0.58F;
            float hgt = 9 + rnd.nextFloat() * 8, wid = hgt * (cherry ? 1.1F : 0.8F);
            float haze = Mth.clamp((float) (r / S) * 0.5F, 0, 0.45F);
            float tr = Mth.lerp(haze, 0.95F, 1F), tg = Mth.lerp(haze, 0.95F, 0.86F), tb = Mth.lerp(haze, 0.95F, 0.9F);
            Vec3 base = new Vec3(Math.cos(a) * r, -0.3, Math.sin(a) * r);
            double rot = rnd.nextDouble() * Math.PI;
            GatherQ q = cherry ? qc : qp;
            float[] uv = {0, 0, 1, 1};
            for (int k = 0; k < 2; k++) {
                double ang = rot + k * Math.PI / 2;
                Vec3 side = new Vec3(Math.cos(ang) * wid / 2, 0, Math.sin(ang) * wid / 2);
                q.quad(base.subtract(side), base.add(side), base.add(side).add(0, hgt, 0), base.subtract(side).add(0, hgt, 0), uv, tr, tg, tb, 1);
            }
        }
    }

    // ================================================================== backdrop (before entities, covers the real world)

    static void backdrop(Ctx c) {
        build(c.ringR, c.sceneR, c.seed);
        GatherQ q = new GatherQ(c.m, c.cam);
        float a = c.alpha, S = c.sceneR, t = c.ticks / 20F;
        q.drawMesh(sky, GatherTex.SHEET, GatherQ.NORMAL, true, true, a, 0, 0);
        q.drawMesh(ground, GatherTex.GROUND, GatherQ.NORMAL, false, true, a, 0, 0);
        q.drawMesh(platform, GatherTex.STONE, GatherQ.NORMAL, false, true, a, 0, 0);
        // sun
        q.begin();
        Vec3 sun = sph(S * 1.02, 0.20, sunAng);
        q.billboard(sun, S * 0.38F, S * 0.38F, t * 0.03F, GatherTex.RAYS, 1F, 0.84F, 0.6F, 0.30F * a, c.right, c.up);
        q.billboard(sun, S * 0.30F, S * 0.30F, 0, GatherTex.SOFT, 1F, 0.76F, 0.55F, 0.55F * a, c.right, c.up);
        q.billboard(sun, S * 0.09F, S * 0.09F, 0, GatherTex.GLOW, 1F, 0.96F, 0.84F, 1F * a, c.right, c.up);
        q.billboard(sun, S * 0.17F, S * 0.17F, 0.4F, GatherTex.STAR, 1F, 0.92F, 0.76F, 0.28F * a, c.right, c.up);
        q.flush(GatherTex.SHEET, GatherQ.ADD, false, false);
        q.drawMesh(far, GatherTex.PANO_FAR, GatherQ.NORMAL, false, false, a, 0, 0);
        q.drawMesh(mid, GatherTex.PANO_MID, GatherQ.NORMAL, false, false, a, 0, 0);
        q.drawMesh(mist, GatherTex.SHEET, GatherQ.NORMAL, false, false, a, 0, 0);
        q.drawMesh(near, GatherTex.PANO_NEAR, GatherQ.NORMAL, false, false, a, 0, 0);
        q.drawMesh(pines, GatherTex.PINE, GatherQ.NORMAL, false, true, a, 0, 0);
        q.drawMesh(cherries, GatherTex.CHERRY, GatherQ.NORMAL, false, true, a, 0, 0);
        q.drawMesh(props, GatherTex.SHEET, GatherQ.NORMAL, false, true, a, 0, 0);
        magicCircles(c, a, t);
        lights(c, a, t);
    }

    private static void magicCircles(Ctx c, float a, float t) {
        GatherQ q = new GatherQ(c.m, c.cam);
        float[] uv = {0, 0, 1, 1};
        Vec3 X = new Vec3(1, 0, 0), Z = new Vec3(0, 0, 1);
        q.begin();
        float waterR = Math.max(2.5F, c.ringR - 2.2F);
        q.oriented(new Vec3(0, 0.03, 0), X, Z, waterR + 1.2F, waterR + 1.2F, t * 0.25F, uv, 1F, 0.93F, 0.66F, 0.85F * a);
        q.oriented(new Vec3(0, 0.025, 0), X, Z, c.ringR + 3.0F, c.ringR + 3.0F, -t * 0.12F, uv, 1F, 0.80F, 0.78F, 0.55F * a);
        q.oriented(new Vec3(0, 0.035, 0), X, Z, Math.min(2.6F, waterR * 0.8F), Math.min(2.6F, waterR * 0.8F), -t * 0.5F, uv, 0.8F, 0.92F, 1F, 0.7F * a);
        for (Vec3 f : c.feet) {
            float spin = (float) (f.x * 0.37 + f.z * 0.21);
            q.oriented(new Vec3(f.x, 0.04, f.z), X, Z, 1.55F, 1.55F, t * 0.6F + spin, uv, 1F, 0.92F, 0.66F, 0.8F * a);
            q.oriented(new Vec3(f.x, 0.045, f.z), X, Z, 1.0F, 1.0F, -t * 0.9F + spin, uv, 0.9F, 0.96F, 1F, 0.6F * a);
        }
        q.flush(GatherTex.MAGIC, GatherQ.ADD, false, false);
        q.begin();
        for (Vec3 f : c.feet) {
            float flick = 0.8F + 0.2F * Mth.sin(t * 3 + (float) f.x);
            q.beam(new Vec3(f.x, 0.05, f.z), new Vec3(f.x, 9, f.z), 0.45F, 1F, 0.93F, 0.7F, 0.28F * a * flick);
        }
        q.flush(GatherTex.SHEET, GatherQ.ADD, false, false);
    }

    private static void lights(Ctx c, float a, float t) {
        GatherQ q = new GatherQ(c.m, c.cam);
        q.begin();
        for (int i = 0; i < LANTERNS.size(); i++) {
            Vec3 p = LANTERNS.get(i);
            float fl = 0.78F + 0.22F * Mth.sin(t * 7 + i * 1.7F) * Mth.sin(t * 3.1F + i);
            q.billboard(p, 0.95F, 0.95F, 0, GatherTex.GLOW, 1F, 0.76F, 0.40F, 0.85F * a * fl, c.right, c.up);
            q.billboard(p, 2.6F, 2.6F, 0, GatherTex.SOFT, 1F, 0.66F, 0.34F, 0.20F * a * fl, c.right, c.up);
        }
        // sky lanterns drifting up
        float S = c.sceneR;
        for (int i = 0; i < 34; i++) {
            float ang = r(i, 1) * Mth.TWO_PI, rad = (0.22F + 0.62F * r(i, 2)) * S;
            float y = ((r(i, 3) * 50 + t * (0.7F + r(i, 4) * 0.8F)) % 52) + 2;
            float x = Mth.cos(ang) * rad + Mth.sin(t * 0.3F + i) * 2.2F, z = Mth.sin(ang) * rad + Mth.cos(t * 0.27F + i) * 2.2F;
            float dim = 1 - Mth.clamp(rad / (S * 1.2F), 0, 1) * 0.45F;
            float fl = 0.8F + 0.2F * Mth.sin(t * 5 + i);
            float size = 0.55F + 0.45F * r(i, 5);
            Vec3 p = new Vec3(x, y, z);
            q.billboard(p, size, size, 0, GatherTex.GLOW, 1F, 0.68F, 0.32F, 0.9F * a * fl * dim, c.right, c.up);
            q.billboard(p, size * 0.28F, size * 0.28F, 0, GatherTex.GLOW, 1F, 0.95F, 0.8F, 0.9F * a * dim, c.right, c.up);
            q.billboard(p, size * 2.6F, size * 2.6F, 0, GatherTex.SOFT, 1F, 0.6F, 0.3F, 0.12F * a * dim, c.right, c.up);
        }
        q.flush(GatherTex.SHEET, GatherQ.ADD, false, false);
    }

    // ================================================================== foreground (after particles)

    static void foreground(Ctx c) {
        float a = c.alpha, t = c.ticks / 20F, S = c.sceneR;
        // ground fog layers
        GatherQ q = new GatherQ(c.m, c.cam);
        double[] heights = {0.25, 0.85, 1.7, 3.2};
        for (int L = 0; L < heights.length; L++) {
            q.begin();
            double R = S * (0.86 - 0.08 * L);
            int seg = 40, rings = 6;
            float sc = 22 + L * 7;
            float dir = L % 2 == 0 ? 1 : -1;
            for (int i = 0; i < rings; i++) {
                double r0 = R * i / rings, r1 = R * (i + 1) / rings;
                float a0 = fogA(i / (float) rings) * 0.30F * a, a1 = fogA((i + 1) / (float) rings) * 0.30F * a;
                for (int j = 0; j < seg; j++) {
                    double g0 = Math.PI * 2 * j / seg, g1 = Math.PI * 2 * (j + 1) / seg;
                    Vec3 p00 = new Vec3(Math.cos(g0) * r0, heights[L], Math.sin(g0) * r0), p01 = new Vec3(Math.cos(g1) * r0, heights[L], Math.sin(g1) * r0);
                    Vec3 p11 = new Vec3(Math.cos(g1) * r1, heights[L], Math.sin(g1) * r1), p10 = new Vec3(Math.cos(g0) * r1, heights[L], Math.sin(g0) * r1);
                    float ox = t * 0.35F * dir * (1 + L * 0.3F), oz = t * 0.2F * (L + 1);
                    q.quadC(p00, p01, p11, p10, fuv(p00, sc, ox, oz), fuv(p01, sc, ox, oz), fuv(p11, sc, ox, oz), fuv(p10, sc, ox, oz),
                            c(1, 0.92F, 0.94F, a0), c(1, 0.92F, 0.94F, a0), c(1, 0.92F, 0.94F, a1), c(1, 0.92F, 0.94F, a1));
                }
            }
            q.flush(GatherTex.FOG, GatherQ.NORMAL, false, false);
        }
        petals(c, a, t);
    }

    private static float fogA(float k) { return (float) Math.sin(Math.PI * Math.min(1, k * 1.15F)) * (1 - k * 0.3F); }

    private static float[] fuv(Vec3 p, float sc, float ox, float oz) { return new float[]{(float) p.x / sc + ox / sc * 6, (float) p.z / sc + oz / sc * 6}; }

    private static float r(int i, int k) {
        int n = i * 374761393 + k * 668265263;
        n = (n ^ (n >>> 13)) * 1274126177;
        n ^= n >>> 16;
        return (n & 0xFFFFFF) / (float) 0x1000000;
    }

    // ------------------------------------------------------------------ petals

    private static void petals(Ctx c, float a, float t) {
        GatherQ q = new GatherQ(c.m, c.cam);
        q.begin();
        float S = c.sceneR;
        for (int i = 0; i < 260; i++) {
            boolean near = i < 170;
            float area = near ? c.ringR + 14 : S * 0.62F;
            float ang = r(i, 11) * Mth.TWO_PI, rad = (float) Math.sqrt(r(i, 12)) * area;
            float fall = 0.7F + r(i, 13) * 0.9F, top = 28;
            float y = top - ((t * fall + r(i, 14) * top) % top);
            float x = Mth.cos(ang) * rad + Mth.sin(t * 0.8F + i) * 1.5F + t * 0.45F * 0, z = Mth.sin(ang) * rad + Mth.cos(t * 0.7F + i * 1.3F) * 1.5F;
            float size = 0.10F + r(i, 15) * 0.07F;
            float flip = Math.max(0.25F, Math.abs(Mth.cos(t * (1.2F + r(i, 16)) + i)));
            float fade = Mth.clamp(y / 2F, 0, 1) * Mth.clamp((top - y) / 2F, 0, 1);
            q.billboard(new Vec3(x, y, z), size, size * flip, t * (0.6F + r(i, 17)) + i, GatherTex.PETAL, 1F, 0.86F + 0.1F * r(i, 18), 0.9F, 0.95F * a * fade, c.right, c.up);
        }
        q.flush(GatherTex.SHEET, GatherQ.NORMAL, false, false);
    }

    /** Dissolve: petals and sparks flying away. age in seconds since the dissolve started. */
    static void motes(Ctx c, float age) {
        if (age < 0 || age > 2.2F) return;
        GatherQ q = new GatherQ(c.m, c.cam);
        float S = c.sceneR;
        float life = Mth.clamp(1 - age / 2.2F, 0, 1);
        q.begin();
        for (int i = 0; i < 300; i += 2) mote(q, c, i, age, S, life, true);
        q.flush(GatherTex.SHEET, GatherQ.NORMAL, false, false);
        q.begin();
        for (int i = 1; i < 300; i += 2) mote(q, c, i, age, S, life, false);
        q.flush(GatherTex.SHEET, GatherQ.ADD, false, false);
    }

    private static void mote(GatherQ q, Ctx c, int i, float age, float S, float life, boolean petal) {
        float u = r(i, 31) * 2 - 1, th = r(i, 32) * Mth.TWO_PI, s = (float) Math.sqrt(1 - u * u);
        Vec3 dir = new Vec3(s * Mth.cos(th), Math.abs(u) * 0.6F + 0.15F, s * Mth.sin(th));
        float d0 = r(i, 33) * (c.ringR + 10);
        float speed = 5 + r(i, 34) * 20, e = 1 - (float) Math.exp(-age * 1.6F);
        Vec3 p = dir.scale(d0 + speed * e * 1.4F).add(0, 1.5 + r(i, 35) * 3 + age * 1.5, 0);
        float size = petal ? 0.18F + r(i, 36) * 0.12F : 0.22F + r(i, 36) * 0.3F;
        if (petal) q.billboard(p, size, size * Math.max(0.3F, Math.abs(Mth.cos(age * 3 + i))), age * 2 + i, GatherTex.PETAL, 1, 0.84F, 0.9F, 0.95F * life, c.right, c.up);
        else q.billboard(p, size, size, 0, GatherTex.GLOW, 1F, 0.9F, 0.6F, 0.9F * life, c.right, c.up);
    }

    // ================================================================== outer barrier (what outsiders see)

    static void barrier(Matrix4f m, Vec3 cam, Vec3 right, Vec3 up, float radius, float ticks) {
        GatherTex.ensure();
        GatherQ q = new GatherQ(m, cam);
        float t = ticks / 20F;
        int lon = 28, lat = 14;
        q.begin();
        float[] w = GatherTex.uv(GatherTex.WHITE);
        float[] uv = {(w[0] + w[2]) / 2, (w[1] + w[3]) / 2};
        for (int i = 0; i < lat; i++) {
            double ea = -Math.PI / 2 + Math.PI * i / lat, eb = -Math.PI / 2 + Math.PI * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                double aa = Math.PI * 2 * j / lon, ab = Math.PI * 2 * (j + 1) / lon;
                Vec3 p0 = sph(radius, ea, aa), p1 = sph(radius, ea, ab), p2 = sph(radius, eb, ab), p3 = sph(radius, eb, aa);
                float shimmer = 0.55F + 0.45F * Mth.sin(t * 1.6F + j * 0.5F + i * 0.8F);
                q.quadC(p0, p1, p2, p3, uv, uv, uv, uv, fres(p0, cam, shimmer), fres(p1, cam, shimmer), fres(p2, cam, shimmer), fres(p3, cam, shimmer));
            }
        }
        q.flush(GatherTex.SHEET, GatherQ.ADD, false, false);
        q.begin();
        for (int j = 0; j < lon; j += 2) {
            double a = Math.PI * 2 * j / lon + t * 0.1;
            for (int i = 0; i < 24; i++) {
                double e0 = -Math.PI / 2 + Math.PI * i / 24, e1 = -Math.PI / 2 + Math.PI * (i + 1) / 24;
                q.ribbon(sph(radius, e0, a), sph(radius, e1, a), 0.06F, 1F, 0.9F, 0.62F, 0.30F);
            }
        }
        for (int i = 1; i < lat; i += 2) {
            double e = -Math.PI / 2 + Math.PI * i / lat;
            for (int j = 0; j < 48; j++) {
                double a0 = Math.PI * 2 * j / 48 + t * 0.15, a1 = Math.PI * 2 * (j + 1) / 48 + t * 0.15;
                q.ribbon(sph(radius, e, a0), sph(radius, e, a1), 0.06F, 1F, 0.9F, 0.62F, 0.30F);
            }
        }
        for (int k = 0; k < 40; k++) {
            double e = Math.asin(r(k, 41) * 2 - 1), a = r(k, 42) * Math.PI * 2;
            float tw = Mth.sin(t * 3 + k) * 0.5F + 0.5F;
            q.billboard(sph(radius, e, a), 0.5F * tw, 0.5F * tw, t, GatherTex.STAR, 1F, 0.95F, 0.8F, 0.85F, right, up);
        }
        q.flush(GatherTex.SHEET, GatherQ.ADD, false, false);
    }

    private static float[] fres(Vec3 p, Vec3 cam, float shimmer) {
        Vec3 view = p.subtract(cam).normalize(), n = p.normalize();
        float f = 1 - (float) Math.abs(view.dot(n));
        return c(1F, 0.92F, 0.72F, (0.035F + 0.30F * f * f) * shimmer);
    }

    private GatherScenery() {}
}
