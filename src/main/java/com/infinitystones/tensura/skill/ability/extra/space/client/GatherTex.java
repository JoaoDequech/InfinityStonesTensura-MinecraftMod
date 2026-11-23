package com.infinitystones.tensura.skill.ability.extra.space.client;

import com.infinitystones.tensura.InfinityStones;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.Random;

/** Every texture of the Spiritual Gathering scenery, painted once in code (no PNG assets). */
final class GatherTex {
    static ResourceLocation SHEET, PANO_FAR, PANO_MID, PANO_NEAR, GROUND, STONE, WATER, FOG, MAGIC, CHERRY, PINE;
    private static boolean ready;

    // sprite sheet cells (64x64 in a 256x256 sheet)
    static final int GLOW = 0, STRIP = 1, STAR = 2, RING = 3, PETAL = 4, WHITE = 5, SOFT = 6, RAYS = 7;

    /** u0, v0, u1, v1 of a sheet cell (half-pixel inset). */
    static float[] uv(int cell) {
        int cx, cy;
        switch (cell) {
            case GLOW -> { cx = 0; cy = 0; }
            case STRIP -> { cx = 1; cy = 0; }
            case STAR -> { cx = 2; cy = 0; }
            case RING -> { cx = 3; cy = 0; }
            case PETAL -> { cx = 0; cy = 1; }
            case WHITE -> { cx = 1; cy = 1; }
            case SOFT -> { cx = 0; cy = 2; }
            default -> { cx = 3; cy = 1; }
        }
        float e = 1.5F / 256F;
        return new float[]{cx * 0.25F + e, cy * 0.25F + e, cx * 0.25F + 0.25F - e, cy * 0.25F + 0.25F - e};
    }

    static void ensure() {
        if (ready) return;
        ready = true;
        SHEET = reg("sheet", sheet(), true);
        PANO_FAR = reg("pano_far", panoFar(), true);
        PANO_MID = reg("pano_mid", panoMid(), true);
        PANO_NEAR = reg("pano_near", panoNear(), true);
        GROUND = reg("ground", ground(), true);
        STONE = reg("stone", stone(), true);
        WATER = reg("water", water(), true);
        FOG = reg("fog", fog(), true);
        MAGIC = reg("magic", magic(), true);
        CHERRY = reg("cherry", cherryTree(), true);
        PINE = reg("pine", pineTree(), true);
    }

    private static ResourceLocation reg(String name, NativeImage img, boolean blur) {
        ResourceLocation id = InfinityStones.id("gather/" + name);
        DynamicTexture t = new DynamicTexture(img);
        t.setFilter(blur, false);
        Minecraft.getInstance().getTextureManager().register(id, t);
        return id;
    }

    // ------------------------------------------------------------------ pixel helpers

    private static int px(int r, int g, int b, int a) { return ((a & 255) << 24) | ((b & 255) << 16) | ((g & 255) << 8) | (r & 255); }

    /** "Over" compositing; x wraps horizontally. */
    private static void blend(NativeImage im, int x, int y, int r, int g, int b, float a) {
        if (a <= 0.003F || y < 0 || y >= im.getHeight()) return;
        x = Math.floorMod(x, im.getWidth());
        int d = im.getPixelRGBA(x, y);
        float da = ((d >>> 24) & 255) / 255F;
        int dr = d & 255, dg = (d >> 8) & 255, db = (d >> 16) & 255;
        a = Mth.clamp(a, 0, 1);
        float oa = a + da * (1 - a);
        if (oa <= 0) return;
        int or = (int) ((r * a + dr * da * (1 - a)) / oa), og = (int) ((g * a + dg * da * (1 - a)) / oa), ob = (int) ((b * a + db * da * (1 - a)) / oa);
        im.setPixelRGBA(x, y, px(or, og, ob, (int) (oa * 255)));
    }

    private static float h(int x, int y, int s) {
        int n = x * 374761393 + y * 668265263 + s * 1442695041;
        n = (n ^ (n >>> 13)) * 1274126177;
        n ^= n >>> 16;
        return (n & 0xFFFFFF) / (float) 0x1000000;
    }

    /** Smooth value noise periodic in both axes (cells). */
    private static float vn(float x, float y, int px, int py, int s) {
        int x0 = (int) Math.floor(x), y0 = (int) Math.floor(y);
        float fx = x - x0, fy = y - y0;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
        float a = h(Math.floorMod(x0, px), Math.floorMod(y0, py), s), b = h(Math.floorMod(x0 + 1, px), Math.floorMod(y0, py), s);
        float c = h(Math.floorMod(x0, px), Math.floorMod(y0 + 1, py), s), d = h(Math.floorMod(x0 + 1, px), Math.floorMod(y0 + 1, py), s);
        return Mth.lerp(fy, Mth.lerp(fx, a, b), Mth.lerp(fx, c, d));
    }

    private static float fbm(float x, float y, int px, int py, int s, int oct) {
        float sum = 0, amp = 0.5F, tot = 0;
        for (int i = 0; i < oct; i++) {
            sum += vn(x, y, px, py, s + i * 17) * amp; tot += amp;
            x *= 2; y *= 2; px *= 2; py *= 2; amp *= 0.5F;
        }
        return sum / tot;
    }

    private static void ellipse(NativeImage im, float cx, float cy, float rx, float ry, int[] c, float a) {
        for (int y = (int) Math.floor(cy - ry); y <= (int) Math.ceil(cy + ry); y++)
            for (int x = (int) Math.floor(cx - rx); x <= (int) Math.ceil(cx + rx); x++) {
                float dx = (x - cx) / rx, dy = (y - cy) / ry, d = dx * dx + dy * dy;
                if (d < 1) blend(im, x, y, c[0], c[1], c[2], a * Mth.clamp((1 - d) * 5F, 0, 1));
            }
    }

    private static void tri(NativeImage im, float cx, float top, float half, float hgt, int[] c, float a) {
        for (int y = (int) top; y <= (int) (top + hgt); y++) {
            float t = (y - top) / hgt, hw = half * t;
            for (int x = (int) Math.floor(cx - hw); x <= (int) Math.ceil(cx + hw); x++) {
                float edge = Mth.clamp((hw - Math.abs(x - cx)) * 1.2F, 0, 1);
                blend(im, x, y, c[0], c[1], c[2], a * edge);
            }
        }
    }

    private static void rect(NativeImage im, float x0, float y0, float x1, float y1, int[] c, float a) {
        for (int y = (int) y0; y <= (int) y1; y++) for (int x = (int) x0; x <= (int) x1; x++) blend(im, x, y, c[0], c[1], c[2], a);
    }

    private static int[] mix(int[] a, int[] b, float t) {
        return new int[]{(int) Mth.lerp(t, a[0], b[0]), (int) Mth.lerp(t, a[1], b[1]), (int) Mth.lerp(t, a[2], b[2])};
    }

    private static final int[] MIST = {236, 205, 214};

    // ------------------------------------------------------------------ sprite sheet

    private static NativeImage sheet() {
        NativeImage im = new NativeImage(256, 256, true);
        for (int cell = 0; cell < 8; cell++) {
            int ox = (cell % 4) * 64, oy = (cell / 4) * 64;
            // cell index -> sheet position (see uv())
            int[] pos = cellPos(cell);
            ox = pos[0] * 64; oy = pos[1] * 64;
            for (int y = 0; y < 64; y++)
                for (int x = 0; x < 64; x++) {
                    float u = (x + 0.5F) / 32F - 1, v = (y + 0.5F) / 32F - 1;
                    float r = (float) Math.sqrt(u * u + v * v);
                    float a = 0; int cr = 255, cg = 255, cb = 255;
                    switch (cell) {
                        case GLOW -> a = (float) Math.pow(Mth.clamp(1 - r, 0, 1), 2.2);
                        case SOFT -> a = (float) Math.pow(Mth.clamp(1 - r, 0, 1), 1.15);
                        case STRIP -> {
                            float along = (y + 0.5F) / 64F;
                            a = (float) Math.pow(1 - Math.abs(u), 1.5) * Math.min(1, Math.min(along / 0.12F, (1 - along) / 0.12F));
                        }
                        case STAR -> {
                            float core = (float) Math.pow(Mth.clamp(1 - r * 1.6F, 0, 1), 2);
                            float s1 = (float) (Math.exp(-Math.abs(u) * 22) * Math.exp(-Math.abs(v) * 2.4));
                            float s2 = (float) (Math.exp(-Math.abs(v) * 22) * Math.exp(-Math.abs(u) * 2.4));
                            a = Math.min(1, Math.max(s1, s2) + core * 0.8F);
                        }
                        case RING -> {
                            float d = (r - 0.82F) / 0.045F, d2 = (r - 0.82F) / 0.16F;
                            a = Math.min(1, (float) Math.exp(-d * d) + 0.28F * (float) Math.exp(-d2 * d2));
                        }
                        case PETAL -> {
                            float pu = u / 0.5F, pv = (v + 0.05F) / 0.9F, d = pu * pu + pv * pv;
                            if (d < 1) {
                                a = Mth.clamp((1 - d) * 4F, 0, 1);
                                if (v < -0.62F && Math.abs(u) < 0.13F * (v + 0.9F) * 3F) a *= 0.2F;
                                float k = Mth.clamp(1 - d, 0, 1);
                                cr = 255; cg = (int) Mth.lerp(k, 170, 222); cb = (int) Mth.lerp(k, 190, 232);
                            }
                        }
                        case WHITE -> a = 1;
                        default -> { // RAYS
                            double ang = Math.atan2(v, u);
                            float spokes = (float) Math.pow(0.5 + 0.5 * Math.cos(ang * 14), 3);
                            a = (float) Math.pow(Mth.clamp(1 - r, 0, 1), 1.4) * (0.25F + 0.75F * spokes);
                        }
                    }
                    im.setPixelRGBA(ox + x, oy + y, px(cr, cg, cb, (int) (Mth.clamp(a, 0, 1) * 255)));
                }
        }
        return im;
    }

    private static int[] cellPos(int cell) {
        return switch (cell) {
            case GLOW -> new int[]{0, 0};
            case STRIP -> new int[]{1, 0};
            case STAR -> new int[]{2, 0};
            case RING -> new int[]{3, 0};
            case PETAL -> new int[]{0, 1};
            case WHITE -> new int[]{1, 1};
            case SOFT -> new int[]{0, 2};
            default -> new int[]{3, 1};
        };
    }

    // ------------------------------------------------------------------ panoramas (2048 x 256, wrap around)

    private static final int PW = 2048, PH = 256;

    private static NativeImage panoFar() {
        NativeImage im = new NativeImage(PW, PH, true);
        int[][] tops = {{176, 168, 214}, {120, 118, 168}};
        int[][] bots = {{238, 206, 220}, {222, 186, 204}};
        for (int layer = 0; layer < 2; layer++) {
            float base = PH * (layer == 0 ? 0.80F : 0.90F);
            for (int x = 0; x < PW; x++) {
                float u = x / (float) PW;
                float ridge = base - (fbm(x / 190F, layer * 5F, 11, 3, 40 + layer, 4) * 0.30F + 0.10F) * PH * (layer == 0 ? 1.0F : 0.55F);
                if (layer == 0) {
                    float du = Math.abs(u - 0.31F); du = Math.min(du, 1 - du);
                    float cone = (float) Math.pow(Math.max(0, 1 - du / 0.11F), 0.85);
                    ridge -= cone * PH * 0.56F;
                    float du2 = Math.abs(u - 0.78F); du2 = Math.min(du2, 1 - du2);
                    ridge -= (float) Math.pow(Math.max(0, 1 - du2 / 0.07F), 1.1) * PH * 0.26F;
                }
                for (int y = (int) ridge; y < PH; y++) {
                    float k = Mth.clamp((y - ridge) / (PH - ridge), 0, 1);
                    int[] c = mix(tops[layer], bots[layer], (float) Math.pow(k, 0.7));
                    boolean snow = layer == 0 && Math.abs(u - 0.31F) < 0.05F && y - ridge < PH * 0.13F * (1 + 0.6F * vn(x / 9F, y / 9F, 300, 300, 5));
                    if (snow) c = new int[]{252, 238, 244};
                    float a = Mth.clamp(y - ridge + 1, 0, 1);
                    blend(im, x, y, c[0], c[1], c[2], a);
                }
            }
        }
        return im;
    }

    private static NativeImage panoMid() {
        NativeImage im = new NativeImage(PW, PH, true);
        Random rnd = new Random(7);
        int[][] pine = {{72, 98, 104}, {84, 112, 112}};
        int[][] wood = {{96, 70, 80}, {120, 86, 96}};
        int[][] pink = {{248, 176, 198}, {255, 206, 220}, {232, 148, 178}};
        for (int i = 0; i < 150; i++) {
            float x = rnd.nextFloat() * PW, size = 26 + rnd.nextFloat() * 56, base = PH * (0.93F + rnd.nextFloat() * 0.06F);
            float haze = 0.30F + (1 - size / 82F) * 0.25F;
            if (rnd.nextFloat() < 0.68F) {
                int[] c = mix(pine[rnd.nextInt(2)], MIST, haze);
                rect(im, x - size * 0.03F, base - size * 0.25F, x + size * 0.03F, base, mix(wood[0], MIST, haze), 1);
                for (int t = 0; t < 5; t++) {
                    float top = base - size * (1.0F - t * 0.17F), half = size * (0.17F + t * 0.045F);
                    tri(im, x, top, half, size * 0.30F, c, 1);
                }
            } else {
                int[] tr = mix(wood[rnd.nextInt(2)], MIST, haze);
                rect(im, x - size * 0.035F, base - size * 0.55F, x + size * 0.035F, base, tr, 1);
                for (int b = 0; b < 16; b++) {
                    float bx = x + (rnd.nextFloat() - 0.5F) * size * 0.8F, by = base - size * (0.55F + rnd.nextFloat() * 0.38F);
                    ellipse(im, bx, by, size * (0.13F + rnd.nextFloat() * 0.11F), size * (0.10F + rnd.nextFloat() * 0.08F),
                            mix(pink[rnd.nextInt(3)], MIST, haze * 0.7F), 0.95F);
                }
            }
        }
        // pagodas and torii in the haze
        for (int k = 0; k < 2; k++) {
            float x = PW * (0.30F + 0.5F * k), base = PH * 0.97F;
            int[] roof = mix(new int[]{70, 54, 70}, MIST, 0.42F), wall = mix(new int[]{150, 70, 76}, MIST, 0.42F);
            for (int t = 0; t < 5; t++) {
                float w = 34 - t * 5, y = base - 20 - t * 22;
                rect(im, x - w * 0.55F, y - 14, x + w * 0.55F, y, wall, 1);
                tri(im, x, y - 28, w * 1.25F, 17, roof, 1);
                rect(im, x - w * 1.25F, y - 12, x + w * 1.25F, y - 10, roof, 1);
            }
            rect(im, x - 2, base - 140, x + 2, base - 112, roof, 1);
        }
        for (int k = 0; k < 3; k++) {
            float x = PW * (0.10F + 0.31F * k), base = PH * 0.97F;
            int[] red = mix(new int[]{196, 52, 56}, MIST, 0.35F), blk = mix(new int[]{40, 32, 44}, MIST, 0.35F);
            rect(im, x - 26, base - 70, x - 20, base, red, 1);
            rect(im, x + 20, base - 70, x + 26, base, red, 1);
            rect(im, x - 30, base - 58, x + 30, base - 53, red, 1);
            rect(im, x - 36, base - 76, x + 36, base - 69, blk, 1);
        }
        return im;
    }

    private static NativeImage panoNear() {
        NativeImage im = new NativeImage(PW, PH, true);
        Random rnd = new Random(23);
        int[] dark = {38, 30, 40}, bark = {62, 46, 54};
        int[][] pink = {{236, 150, 182}, {248, 184, 206}, {214, 120, 160}};
        for (int i = 0; i < 20; i++) {
            float x = (i + rnd.nextFloat() * 0.8F) * PW / 20F, w = 7 + rnd.nextFloat() * 11;
            boolean cherry = rnd.nextFloat() < 0.5F;
            int[] c = mix(bark, dark, rnd.nextFloat() * 0.6F);
            float lean = (rnd.nextFloat() - 0.5F) * 10;
            for (int y = 0; y < PH; y++) {
                float cx = x + lean * (1 - y / (float) PH);
                rect(im, cx - w / 2, y, cx + w / 2, y, c, 1);
            }
            if (cherry) {
                for (int b = 0; b < 26; b++) {
                    float bx = x + (rnd.nextFloat() - 0.5F) * 120, by = rnd.nextFloat() * 70 - 6;
                    ellipse(im, bx, by, 9 + rnd.nextFloat() * 14, 7 + rnd.nextFloat() * 9, pink[rnd.nextInt(3)], 0.96F);
                }
            } else {
                for (int t = 0; t < 3; t++) tri(im, x + lean, -10 + t * 22, 50 - t * 8, 46, mix(new int[]{34, 62, 64}, dark, 0.3F), 1);
            }
        }
        return im;
    }

    // ------------------------------------------------------------------ ground

    private static NativeImage ground() {
        NativeImage im = new NativeImage(256, 256, true);
        for (int y = 0; y < 256; y++)
            for (int x = 0; x < 256; x++) {
                float n = fbm(x / 20F, y / 20F, 13, 13, 3, 4), n2 = fbm(x / 5F, y / 5F, 52, 52, 9, 2);
                float k = Mth.clamp(n * 1.4F - 0.2F, 0, 1);
                int r = (int) Mth.lerp(k, 40, 92), g = (int) Mth.lerp(k, 78, 132), b = (int) Mth.lerp(k, 44, 70);
                float sp = (n2 - 0.5F) * 34;
                r += sp * 0.5F; g += sp; b += sp * 0.4F;
                if (h(x, y, 77) > 0.994F) { r = 250; g = 190; b = 208; }
                im.setPixelRGBA(x, y, px(r, g, b, 255));
            }
        return im;
    }

    private static NativeImage stone() {
        NativeImage im = new NativeImage(128, 128, true);
        for (int y = 0; y < 128; y++)
            for (int x = 0; x < 128; x++) {
                int row = y / 32;
                int xs = (x + (row % 2) * 16) % 128;
                int slab = xs / 32;
                float tone = h(slab, row, 3) * 0.28F;
                boolean edge = (xs % 32) < 2 || (y % 32) < 2;
                float n = fbm(x / 6F, y / 6F, 21, 21, 4, 3);
                int base = (int) (118 + tone * 60 + (n - 0.5F) * 34);
                int r = base - 4, g = base, b = base + 8;
                if (edge) { r = 62; g = 66; b = 74; if (h(x, y, 5) > 0.55F) { r = 58; g = 92; b = 58; } }
                im.setPixelRGBA(x, y, px(r, g, b, 255));
            }
        return im;
    }

    private static NativeImage water() {
        NativeImage im = new NativeImage(128, 128, true);
        for (int y = 0; y < 128; y++)
            for (int x = 0; x < 128; x++) {
                float n = fbm(x / 8F, y / 28F, 16, 5, 31, 3);
                float cau = (float) Math.abs(Math.sin((x * 0.09F + n * 6.0F) * 1.0F + y * 0.02F));
                float line = (float) Math.pow(1 - cau, 6);
                float a = Mth.clamp(0.50F + (n - 0.5F) * 0.5F + line * 0.5F, 0, 1);
                int r = (int) Mth.lerp(line, 58, 220), g = (int) Mth.lerp(line, 148, 248), b = (int) Mth.lerp(line, 214, 255);
                im.setPixelRGBA(x, y, px(r, g, b, (int) (a * 255)));
            }
        return im;
    }

    private static NativeImage fog() {
        NativeImage im = new NativeImage(256, 256, true);
        for (int y = 0; y < 256; y++)
            for (int x = 0; x < 256; x++) {
                float n = fbm(x / 40F, y / 40F, 6, 6, 61, 4);
                float a = Mth.clamp((n - 0.36F) * 2.0F, 0, 1);
                im.setPixelRGBA(x, y, px(255, 236, 240, (int) (a * 255)));
            }
        return im;
    }

    // ------------------------------------------------------------------ magic circle

    private static NativeImage magic() {
        int N = 256;
        NativeImage im = new NativeImage(N, N, true);
        double[][] oct = new double[8][4], hex = new double[6][4];
        for (int i = 0; i < 8; i++) {
            double a0 = Math.PI * 2 * i / 8, a1 = Math.PI * 2 * ((i + 3) % 8) / 8;
            oct[i] = new double[]{Math.cos(a0) * 0.9, Math.sin(a0) * 0.9, Math.cos(a1) * 0.9, Math.sin(a1) * 0.9};
        }
        for (int i = 0; i < 6; i++) {
            double a0 = Math.PI * 2 * i / 6 + 0.26, a1 = Math.PI * 2 * ((i + 2) % 6) / 6 + 0.26;
            hex[i] = new double[]{Math.cos(a0) * 0.56, Math.sin(a0) * 0.56, Math.cos(a1) * 0.56, Math.sin(a1) * 0.56};
        }
        for (int y = 0; y < N; y++)
            for (int x = 0; x < N; x++) {
                double u = (x + 0.5) / (N / 2.0) - 1, v = (y + 0.5) / (N / 2.0) - 1, r = Math.sqrt(u * u + v * v);
                if (r > 1) continue;
                double ang = Math.atan2(v, u), a = 0;
                for (double rr : new double[]{0.985, 0.93, 0.80, 0.62, 0.40, 0.22}) {
                    double w = rr > 0.97 ? 0.012 : rr == 0.93 || rr == 0.62 ? 0.008 : 0.006;
                    double d = (r - rr) / w;
                    a = Math.max(a, Math.exp(-d * d));
                }
                if (r > 0.93 && r < 0.985) { double t = Math.abs(Math.sin(ang * 36)); if (t > 0.93) a = Math.max(a, 0.85); }
                for (double[] s : oct) a = Math.max(a, segGlow(u, v, s, 0.008));
                for (double[] s : hex) a = Math.max(a, segGlow(u, v, s, 0.007));
                if (r > 0.64 && r < 0.79) {
                    int sectors = 28;
                    double sec = (ang + Math.PI) / (Math.PI * 2) * sectors;
                    int k = (int) Math.floor(sec);
                    double la = sec - k - 0.5, t = (r - 0.64) / 0.15;
                    int bits = (int) (h(k, 3, 91) * 255);
                    double g = 0;
                    if ((bits & 1) != 0 && Math.abs(la) < 0.07 && t > 0.15 && t < 0.85) g = 1;
                    if ((bits & 2) != 0 && Math.abs(t - 0.5) < 0.07 && Math.abs(la) < 0.30) g = 1;
                    if ((bits & 4) != 0 && Math.abs(la - (t - 0.5) * 0.9) < 0.06 && t > 0.1 && t < 0.9) g = 1;
                    if ((bits & 8) != 0 && Math.abs(la + (t - 0.5) * 0.9) < 0.06 && t > 0.1 && t < 0.9) g = 1;
                    if ((bits & 16) != 0 && Math.abs(Math.hypot(la * 1.4, t - 0.5) - 0.28) < 0.05) g = 1;
                    a = Math.max(a, g * 0.9);
                }
                a *= Mth.clamp((1 - r) * 18, 0, 1);
                a += 0.05 * Math.exp(-Math.pow((r - 0.5) / 0.4, 2));
                im.setPixelRGBA(x, y, px(255, 240, 205, (int) (Mth.clamp((float) a, 0, 1) * 255)));
            }
        return im;
    }

    private static double segGlow(double u, double v, double[] s, double w) {
        double dx = s[2] - s[0], dy = s[3] - s[1], l2 = dx * dx + dy * dy;
        double t = Mth.clamp((float) (((u - s[0]) * dx + (v - s[1]) * dy) / l2), 0, 1);
        double d = Math.hypot(u - (s[0] + dx * t), v - (s[1] + dy * t)) / w;
        return Math.exp(-d * d);
    }

    // ------------------------------------------------------------------ billboard trees

    private static NativeImage cherryTree() {
        NativeImage im = new NativeImage(256, 256, true);
        Random rnd = new Random(41);
        int[] bark = {70, 50, 58};
        for (int y = 120; y < 256; y++) rect(im, 124 - (y - 120) * 0.02F, y, 134 + (y - 120) * 0.04F, y, bark, 1);
        for (int b = 0; b < 5; b++) {
            double a = -Math.PI / 2 + (b - 2) * 0.55;
            for (int s = 0; s < 70; s++) {
                float bx = 128 + (float) Math.cos(a) * s * 0.9F, by = 150 + (float) Math.sin(a) * s * 1.0F;
                rect(im, bx - 2, by - 2, bx + 2, by + 2, bark, 1);
            }
        }
        int[][] pink = {{250, 178, 200}, {255, 208, 222}, {236, 146, 178}, {255, 228, 236}};
        for (int i = 0; i < 70; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, rr = Math.sqrt(rnd.nextDouble()) * 92;
            float bx = 128 + (float) Math.cos(a) * (float) rr, by = 92 + (float) Math.sin(a) * (float) rr * 0.72F;
            ellipse(im, bx, by, 14 + rnd.nextFloat() * 20, 11 + rnd.nextFloat() * 14, pink[rnd.nextInt(4)], 0.97F);
        }
        for (int i = 0; i < 260; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, rr = Math.sqrt(rnd.nextDouble()) * 100;
            blend(im, (int) (128 + Math.cos(a) * rr), (int) (92 + Math.sin(a) * rr * 0.72), 255, 244, 248, 0.9F);
        }
        return im;
    }

    private static NativeImage pineTree() {
        NativeImage im = new NativeImage(256, 256, true);
        int[] bark = {70, 50, 54};
        rect(im, 122, 190, 134, 255, bark, 1);
        for (int t = 0; t < 7; t++) {
            float top = 8 + t * 24, half = 22 + t * 11;
            tri(im, 128, top, half, 52, mixC(new int[]{34, 78, 66}, new int[]{70, 128, 96}, t / 7F), 1);
            tri(im, 128, top + 2, half * 0.5F, 30, new int[]{96, 150, 112}, 0.25F);
        }
        return im;
    }

    private static int[] mixC(int[] a, int[] b, float t) { return mix(a, b, t); }

    private GatherTex() {}
}
