package com.infinitystones.tensura.skill.ability.extra.v2c.client;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.extra.v2c.V2CNet;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Painted Sky renderer: a camera-centred dome drawn right after the vanilla sky (own geometry, no textures) that hides
 * the vanilla sun, moon and stars while the camera is inside a region, plus the matching horizon fog colour.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class SkyClient {
    private static final float R = 80F;
    private static final double HALF_PI = Math.PI / 2;
    public static final int AURORA = 0, GALAXY = 1, SUNSET = 2, BLOOD = 3, COTTON = 4;

    // horizon, middle, zenith
    private static final int[][] SKY = {
            {0x14465A, 0x0B2142, 0x040716},
            {0x34115C, 0x17093A, 0x05020F},
            {0xFFA23C, 0xD9558F, 0x2A1A5E},
            {0x82120F, 0x3F0709, 0x0B0204},
            {0xFFF0FA, 0xFFB5E4, 0x8FD0FF}};

    private static final class Region {
        int id, theme, life, sinceMsg, endTicks;
        float age;
        double x, z;
        float r;
        boolean ending;
        Object level;
    }

    private static final Map<Integer, Region> REGIONS = new HashMap<>();
    private static Region cur;
    private static float blend, reveal;

    public static void accept(V2CNet.Msg m) {
        Minecraft mc = Minecraft.getInstance();
        if (m.kind() == V2CNet.SKY_END) {
            Region g = REGIONS.get(m.id());
            if (g != null) g.ending = true;
            return;
        }
        Region g = REGIONS.computeIfAbsent(m.id(), k -> new Region());
        if (g.ending) return;
        g.id = m.id();
        g.theme = Mth.clamp(m.a(), 0, SKY.length - 1);
        g.life = m.b();
        g.sinceMsg = 0;
        g.age = m.f();
        g.x = m.x();
        g.z = m.z();
        g.r = (float) m.w();
        g.level = mc.level;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { REGIONS.clear(); cur = null; }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (REGIONS.isEmpty() || Minecraft.getInstance().isPaused()) return;
        REGIONS.values().removeIf(g -> {
            g.sinceMsg++;
            if (g.ending) g.endTicks++;
            return g.sinceMsg > g.life + 5 || g.endTicks > 40;
        });
    }

    private static boolean state(float pt) {
        cur = null;
        blend = 0;
        Minecraft mc = Minecraft.getInstance();
        if (REGIONS.isEmpty() || mc.level == null || !mc.level.dimensionType().hasSkyLight()) return false;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        for (Region g : REGIONS.values()) {
            if (g.level != mc.level) continue;
            double d = Math.hypot(cam.x - g.x, cam.z - g.z);
            float b = Mth.clamp((float) ((g.r + 10 - d) / 22.0), 0, 1);
            if (g.ending) b *= 1 - Mth.clamp((g.endTicks + pt) / 40F, 0, 1);
            b = b * b * (3 - 2 * b);
            if (b > blend) { blend = b; cur = g; }
        }
        if (cur == null || blend < 0.01F) { cur = null; return false; }
        reveal = Mth.clamp((cur.age + cur.sinceMsg + pt) / 90F, 0, 1);
        return true;
    }

    @SubscribeEvent
    public static void fogColor(ViewportEvent.ComputeFogColor e) {
        if (!state((float) e.getPartialTick())) return;
        int c = SKY[cur.theme][0];
        float k = blend * Mth.clamp((reveal - 0.55F) * 3F, 0, 1);
        e.setRed(Mth.lerp(k, e.getRed(), ((c >> 16) & 255) / 255F));
        e.setGreen(Mth.lerp(k, e.getGreen(), ((c >> 8) & 255) / 255F));
        e.setBlue(Mth.lerp(k, e.getBlue(), (c & 255) / 255F));
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        if (!state(pt)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer.getMainCamera().getFluidInCamera() != FogType.NONE) return;
        Matrix4f m = e.getModelViewMatrix();
        float time = (mc.level.getGameTime() % 1_000_000L + pt) / 20F;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        for (int pass = 0; pass < 2; pass++) {
            if (pass == 0) RenderSystem.defaultBlendFunc();
            else RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            Dome d = new Dome(bb, m, blend, reveal, time, pass == 1);
            paint(d, cur.theme);
            MeshData md = bb.build();
            if (md != null) BufferUploader.drawWithShader(md);
        }
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

    // ================================================================== dome helpers

    private static final class Dome {
        final BufferBuilder bb;
        final Matrix4f m;
        final float blend, reveal, time;
        final boolean add;

        Dome(BufferBuilder bb, Matrix4f m, float blend, float reveal, float time, boolean add) {
            this.bb = bb; this.m = m; this.blend = blend; this.reveal = reveal; this.time = time; this.add = add;
        }

        /** Paint progress: the colour pours from the zenith down to the horizon with a ragged edge. */
        float vis(double az, double el) {
            if (reveal >= 1F) return 1F;
            double t = el > 0 ? 1 - Math.min(1, el / HALF_PI) : 1;
            double wobble = 0.07 * Math.sin(az * 5 + 1) + 0.04 * Math.sin(az * 13);
            return Mth.clamp((float) ((reveal * 1.35 - t + wobble) * 4), 0, 1);
        }

        void v(double az, double el, int rgb, float a) {
            float al = Mth.clamp(a * blend * vis(az, el), 0, 1);
            double ce = Math.cos(el);
            bb.addVertex(m, (float) (ce * Math.sin(az) * R), (float) (Math.sin(el) * R), (float) (-ce * Math.cos(az) * R))
                    .setColor(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F, al);
        }

        void band(double e0, double e1, int c0, int c1, int segs) {
            for (int j = 0; j < segs; j++) {
                double a0 = Math.PI * 2 * j / segs, a1 = Math.PI * 2 * (j + 1) / segs;
                v(a0, e0, c0, 1); v(a1, e0, c0, 1); v(a1, e1, c1, 1); v(a0, e1, c1, 1);
            }
        }

        void disc(double az, double el, double rAz, double rEl, int ci, float ai, int co, float ao, int segs) {
            double ce = Math.max(0.15, Math.cos(el));
            for (int i = 0; i < segs; i++) {
                double t0 = Math.PI * 2 * i / segs, t1 = Math.PI * 2 * (i + 1) / segs;
                double a0 = az + Math.cos(t0) * rAz / ce, e0 = clampEl(el + Math.sin(t0) * rEl);
                double a1 = az + Math.cos(t1) * rAz / ce, e1 = clampEl(el + Math.sin(t1) * rEl);
                v(az, el, ci, ai); v(a0, e0, co, ao); v(a1, e1, co, ao); v(az, el, ci, ai);
            }
        }

        void star(double az, double el, double s, int rgb, float a) {
            double ce = Math.max(0.15, Math.cos(el));
            v(az - s / ce, el, rgb, a); v(az, el + s, rgb, a); v(az + s / ce, el, rgb, a); v(az, el - s, rgb, a);
        }

        void strip(double a0, double e0, double a1, double e1, double w, int rgb, float al0, float al1) {
            double dx = a1 - a0, dy = e1 - e0, len = Math.max(1e-6, Math.hypot(dx, dy));
            double px = -dy / len * w, py = dx / len * w;
            v(a0 - px, e0 - py, rgb, al0); v(a0 + px, e0 + py, rgb, al0); v(a1 + px, e1 + py, rgb, al1); v(a1 - px, e1 - py, rgb, al1);
        }
    }

    private static double clampEl(double e) { return Mth.clamp(e, -0.5, HALF_PI - 0.001); }

    private static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255), g = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255), bl = (int) Mth.lerp(t, a & 255, b & 255);
        return (r << 16) | (g << 8) | bl;
    }

    private static int grad(int theme, double el) {
        int[] s = SKY[theme];
        double e = Math.max(0, el);
        if (e < 0.6) return mix(s[0], s[1], (float) Math.pow(e / 0.6, 0.75));
        return mix(s[1], s[2], (float) ((e - 0.6) / (HALF_PI - 0.6)));
    }

    // ================================================================== static scatter data

    private static final int NS = 1800, NB = 900;
    private static final float[] SA = new float[NS], SE = new float[NS], SZ = new float[NS];
    private static final int[] SC = new int[NS];
    private static final float[] BA = new float[NB], BE = new float[NB], BZ = new float[NB];
    private static final int[] STAR_COLORS = {0xFFFFFF, 0xDDE8FF, 0xFFF1C8, 0xBBD0FF, 0xFFD8E8};
    /** az, el, rAz, rEl, colour index */
    private static final float[][] NEBULA = new float[18][];
    private static final float[][] STREAKS = new float[18][];
    private static final float[][] WISPS = new float[10][];
    private static final float[][] PUFFS = new float[22 * 6][];
    private static final int[] NEB_COLORS = {0xC040FF, 0x4060FF, 0xFF4090, 0x40D0FF};
    private static final int[] SUNSET_CLOUDS = {0xFF7FA8, 0xFFA060, 0xB060C0, 0xFFC090};
    private static final int[] PUFF_COLORS = {0xFFFFFF, 0xFFC4EC, 0xBDE4FF, 0xE6CCFF};

    private static double bandEl(double az) { return 0.62 * Math.sin(az + 0.7) + 0.35; }

    static {
        Random r = new Random(1337);
        for (int i = 0; i < NS; i++) {
            SA[i] = (float) (r.nextDouble() * Math.PI * 2);
            SE[i] = (float) Math.asin(r.nextDouble() * 1.12 - 0.12);
            SZ[i] = (float) (0.0028 + Math.pow(r.nextDouble(), 3) * 0.008);
            SC[i] = STAR_COLORS[r.nextInt(STAR_COLORS.length)];
        }
        for (int i = 0; i < NB; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            BA[i] = (float) a;
            BE[i] = (float) Mth.clamp(bandEl(a) + r.nextGaussian() * 0.11, -0.05, 1.5);
            BZ[i] = (float) (0.0024 + Math.pow(r.nextDouble(), 3) * 0.006);
        }
        for (int i = 0; i < NEBULA.length; i++) {
            double a = i * Math.PI * 2 / NEBULA.length + r.nextDouble() * 0.25;
            NEBULA[i] = new float[]{(float) a, (float) (bandEl(a) + (r.nextDouble() - 0.5) * 0.18), 0.30F + r.nextFloat() * 0.25F, 0.12F + r.nextFloat() * 0.1F, r.nextInt(NEB_COLORS.length)};
        }
        for (int i = 0; i < STREAKS.length; i++)
            STREAKS[i] = new float[]{r.nextFloat() * 6.283F, 0.06F + r.nextFloat() * 0.5F, 0.3F + r.nextFloat() * 0.35F, 0.018F + r.nextFloat() * 0.03F, r.nextInt(SUNSET_CLOUDS.length)};
        for (int i = 0; i < WISPS.length; i++)
            WISPS[i] = new float[]{r.nextFloat() * 6.283F, 0.15F + r.nextFloat() * 0.6F, 0.35F + r.nextFloat() * 0.3F, 0.02F + r.nextFloat() * 0.03F, 0};
        for (int c = 0; c < 22; c++) {
            double a = r.nextDouble() * Math.PI * 2, e = 0.12 + r.nextDouble() * 0.85;
            int col = r.nextInt(PUFF_COLORS.length);
            for (int k = 0; k < 6; k++)
                PUFFS[c * 6 + k] = new float[]{(float) (a + (r.nextDouble() - 0.5) * 0.34), (float) (e + r.nextDouble() * 0.05),
                        0.09F + r.nextFloat() * 0.07F, 0.04F + r.nextFloat() * 0.035F, col};
        }
    }

    // ================================================================== themes

    private static void paint(Dome d, int theme) {
        if (!d.add) {
            double[] rows = {-0.35, -0.1, 0.0, 0.08, 0.2, 0.38, 0.6, 0.85, 1.1, 1.35, HALF_PI};
            for (int i = 0; i < rows.length - 1; i++) d.band(rows[i], rows[i + 1], grad(theme, rows[i]), grad(theme, rows[i + 1]), 40);
        }
        switch (theme) {
            case AURORA -> {
                stars(d, 700, 0.8F, -1, 0.002F);
                aurora(d);
                shooting(d);
            }
            case GALAXY -> {
                float spin = d.time * 0.004F;
                stars(d, NS, 1F, -1, 0.004F);
                bandStars(d, spin);
                nebula(d, spin);
                shooting(d);
            }
            case SUNSET -> sunset(d);
            case BLOOD -> blood(d);
            default -> cotton(d);
        }
    }

    private static void stars(Dome d, int count, float scale, int tint, float spin) {
        if (!d.add) return;
        float sp = d.time * spin;
        for (int i = 0; i < count; i++) {
            float al = scale * (0.55F + 0.45F * Mth.sin(d.time * (1.2F + (i % 7) * 0.3F) + i));
            d.star(SA[i] + sp, SE[i], SZ[i], tint < 0 ? SC[i] : mix(SC[i], tint, 0.6F), al);
        }
    }

    private static void bandStars(Dome d, float spin) {
        if (!d.add) return;
        for (int i = 0; i < NB; i++) {
            float al = 0.7F + 0.3F * Mth.sin(d.time * 1.7F + i * 1.3F);
            d.star(BA[i] + spin, BE[i], BZ[i], STAR_COLORS[i % STAR_COLORS.length], al);
        }
    }

    private static void nebula(Dome d, float spin) {
        if (!d.add) return;
        for (float[] n : NEBULA) {
            int c = NEB_COLORS[(int) n[4]];
            float pulse = 0.85F + 0.15F * Mth.sin(d.time * 0.4F + n[0] * 3);
            d.disc(n[0] + spin, n[1], n[2], n[3], c, 0.2F * pulse, c, 0F, 14);
            d.disc(n[0] + spin + 0.04, n[1] + 0.01, n[2] * 0.5, n[3] * 0.5, mix(c, 0xFFFFFF, 0.35F), 0.2F * pulse, c, 0F, 12);
        }
    }

    private static void shooting(Dome d) {
        if (!d.add) return;
        float period = 7F, slot = (float) Math.floor(d.time / period), p = (d.time - slot * period) / 1.3F;
        if (p >= 1F) return;
        Random r = new Random((long) slot * 7919L + 13);
        double a = r.nextDouble() * Math.PI * 2, e = 0.35 + r.nextDouble() * 0.7;
        double dir = r.nextDouble() * Math.PI * 2;
        double hx = a + Math.cos(dir) * p * 0.8, hy = e + Math.sin(dir) * p * 0.5;
        double tx = hx - Math.cos(dir) * 0.3, ty = hy - Math.sin(dir) * 0.19;
        float fade = Mth.sin(p * Mth.PI);
        d.strip(tx, ty, hx, hy, 0.006, 0xCFE8FF, 0F, fade);
        d.star(hx, hy, 0.012, 0xFFFFFF, fade);
    }

    private static void aurora(Dome d) {
        if (!d.add) return;
        int[] arcs = {0, 2, 4};
        for (int ri = 0; ri < arcs.length; ri++) {
            double centre = ri * 2.1 + d.time * 0.012, span = 2.0;
            int n = 48, stack = 6;
            for (int i = 0; i < n; i++) {
                double u0 = i / (double) n, u1 = (i + 1) / (double) n;
                double sway0 = Math.sin(d.time * 0.5 + i * 0.35 + ri * 2) * 0.05, sway1 = Math.sin(d.time * 0.5 + (i + 1) * 0.35 + ri * 2) * 0.05;
                double a0 = centre + (u0 - 0.5) * span + sway0, a1 = centre + (u1 - 0.5) * span + sway1;
                double b0 = 0.2 + ri * 0.05 + 0.07 * Math.sin(i * 0.3 + d.time * 0.6 + ri), b1 = 0.2 + ri * 0.05 + 0.07 * Math.sin((i + 1) * 0.3 + d.time * 0.6 + ri);
                double h0 = 0.45 + 0.18 * Math.sin(i * 0.5 + d.time * 0.9 + ri * 3), h1 = 0.45 + 0.18 * Math.sin((i + 1) * 0.5 + d.time * 0.9 + ri * 3);
                float env0 = Mth.sin((float) (u0 * Math.PI)), env1 = Mth.sin((float) (u1 * Math.PI));
                float ray0 = 0.55F + 0.45F * Mth.sin(i * 1.7F + d.time * 1.3F + ri), ray1 = 0.55F + 0.45F * Mth.sin((i + 1) * 1.7F + d.time * 1.3F + ri);
                for (int k = 0; k < stack; k++) {
                    float f0 = k / (float) stack, f1 = (k + 1) / (float) stack;
                    float al0 = (float) Math.pow(1 - f0, 1.4) * 0.55F, al1 = (float) Math.pow(1 - f1, 1.4) * 0.55F;
                    int c0 = mix(0x38FFA0, 0x9060FF, f0 * f0), c1 = mix(0x38FFA0, 0x9060FF, f1 * f1);
                    d.v(a0, b0 + (h0) * f0, c0, al0 * env0 * ray0);
                    d.v(a1, b1 + (h1) * f0, c0, al0 * env1 * ray1);
                    d.v(a1, b1 + (h1) * f1, c1, al1 * env1 * ray1);
                    d.v(a0, b0 + (h0) * f1, c1, al1 * env0 * ray0);
                }
            }
        }
    }

    private static void sunset(Dome d) {
        double sunAz = 2.3, sunEl = 0.06;
        if (d.add) {
            d.disc(sunAz, sunEl, 1.0, 0.5, 0xFF7A30, 0.5F, 0xFF4020, 0F, 20);
            d.disc(sunAz, sunEl, 0.38, 0.22, 0xFFB060, 0.75F, 0xFF8040, 0F, 20);
            d.disc(sunAz, sunEl, 0.14, 0.14, 0xFFE8B0, 0.5F, 0xFFC070, 0F, 16);
            return;
        }
        for (float[] s : STREAKS) {
            int c = SUNSET_CLOUDS[(int) s[4]];
            double az = s[0] + d.time * 0.004;
            d.disc(az, s[1], s[2], s[3], c, 0.65F, mix(c, 0x6A3A8A, 0.5F), 0F, 12);
        }
        d.disc(sunAz, sunEl, 0.075, 0.075, 0xFFF3CC, 1F, 0xFFE2A0, 1F, 20);
    }

    private static final double[][] CRATERS = {{-0.04, 0.03, 0.035}, {0.05, -0.02, 0.025}, {0.01, 0.07, 0.02}, {-0.07, -0.05, 0.03}, {0.08, 0.06, 0.018}, {-0.01, -0.08, 0.022}};

    private static void blood(Dome d) {
        double az = 3.6, el = 0.7;
        if (d.add) {
            stars(d, 420, 0.55F, 0xFF6060, 0.001F);
            d.disc(az, el, 0.5, 0.5, 0xFF2020, 0.5F, 0xFF1010, 0F, 24);
            d.disc(az, el, 1.0, 1.0, 0xFF3010, 0.2F, 0xA00808, 0F, 24);
            d.disc(az, el, 0.2, 0.2, 0xFF5030, 0.18F, 0xFF3020, 0F, 20);
            return;
        }
        for (float[] w : WISPS) {
            double a = w[0] + d.time * 0.003;
            d.disc(a, w[1], w[2], w[3], 0x1E0303, 0.5F, 0x2A0505, 0F, 10);
        }
        d.disc(az, el, 0.17, 0.17, 0xC21818, 1F, 0x9A0E0E, 1F, 28);
        for (double[] c : CRATERS) d.disc(az + c[0], el + c[1], c[2], c[2], 0x5E0707, 0.6F, 0x7A0A0A, 0.25F, 10);
    }

    private static void cotton(Dome d) {
        if (d.add) {
            stars(d, 380, 0.7F, 0xFFD0F0, 0.003F);
            d.disc(1.2, 1.0, 0.7, 0.5, 0xFFF4C0, 0.35F, 0xFFD0F0, 0F, 20);
            return;
        }
        d.disc(1.2, 1.0, 0.1, 0.1, 0xFFFAD6, 1F, 0xFFF1B8, 1F, 20);
        double drift = d.time * 0.006;
        for (float[] p : PUFFS) {
            int c = PUFF_COLORS[(int) p[4]];
            d.disc(p[0] + drift, p[1], p[2], p[3], c, 0.95F, mix(c, 0xFFFFFF, 0.4F), 0.3F, 12);
        }
    }

    private SkyClient() {}
}
