package com.infinitystones.tensura.skill.ability.extra.v2a.client;

import com.infinitystones.tensura.client.render.GlowFx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

/**
 * The giant hand + forearm model, built from cuboids so every finger joint can be posed at run time.
 * Model units, origin = centre of the knuckle line, +Y towards the arm, +Z = palm side (fingers curl towards +Z), +X = thumb.
 * The hand is about 2 wide, 2.2 tall (palm) and fingers add 2.7; the caller scales it.
 */
final class Hands {
    private static ResourceLocation tex(String name) { return ResourceLocation.withDefaultNamespace("textures/block/" + name + ".png"); }

    record Style(ResourceLocation body, ResourceLocation trim, ResourceLocation gem,
                 float br, float bg, float bb, boolean gauntlet) {}

    /** Stone hand with purple light veins (Sky Fist, Sculptor's Hands). */
    static final Style RUNE_STONE = new Style(tex("stone_bricks"), tex("crying_obsidian"), tex("white_concrete"), 0.82F, 0.78F, 0.92F, false);
    /** The golden gauntlet of the Creator. */
    static final Style GOLD = new Style(tex("gold_block"), tex("raw_gold_block"), tex("white_concrete"), 1F, 0.97F, 0.85F, true);

    /** Gem positions on the back of the hand (stone order mind, power, space, reality, soul, time). */
    static final float[][] GEMS = {
            {-0.75F, 0.55F, -0.7F, 0.34F}, {-0.25F, 0.55F, -0.7F, 0.34F}, {0.25F, 0.55F, -0.7F, 0.34F}, {0.75F, 0.55F, -0.7F, 0.34F},
            {0F, 1.5F, -0.72F, 0.5F}, {1.12F, 1.0F, -0.35F, 0.38F}};
    static final int[] GEM_COLORS = {0xF2D02B, 0xA43CE6, 0x2F8CFF, 0xE22B3B, 0xF28A1A, 0x2BD56A};

    private static final float[] FLEN = {0.95F, 1F, 0.92F, 0.76F};

    /**
     * Solid pass.
     * @param curl 0 open .. 1 fist; @param spread 0..1 fingers fanned; @param armLen forearm length above the wrist;
     * @param palmDown wrist bent so the palm faces down (open-palm slam), bottom of the palm at y = 0;
     * @param litMask gauntlet stones that glow; @param pulse 0..1 light pulse of the runes.
     */
    static void solid(PoseStack ps, MultiBufferSource buf, Style st, int light, float curl, float spread, float armLen,
                      boolean palmDown, int litMask, float pulse) {
        VertexConsumer body = buf.getBuffer(RenderType.entityCutoutNoCull(st.body));
        VertexConsumer trim = buf.getBuffer(RenderType.entityCutoutNoCull(st.trim));
        float br = st.br, bg = st.bg, bb = st.bb;
        int trimLight = st.gauntlet ? light : Geo.FULL;
        float glow = st.gauntlet ? 1F : 0.8F + 0.2F * pulse;
        if (palmDown) ps.translate(0, -1.6F, 0);

        if (palmDown) {
            ps.pushPose();
            ps.translate(0, 2.2F, 0);
            ps.mulPose(Axis.XP.rotationDegrees(90));
            ps.translate(0, -2.2F, 0);
        }
        // palm
        Geo.box(ps, body, -1F, 0, -0.6F, 1F, 2.2F, 0.6F, light, br, bg, bb, 1F);
        if (st.gauntlet) {
            Geo.box(ps, trim, -1.08F, 0.1F, -0.78F, 1.08F, 2.25F, -0.58F, light, br, bg, bb, 1F);     // back plate
            Geo.box(ps, body, -1.1F, -0.12F, -0.72F, 1.1F, 0.42F, 0.66F, light, br * 0.9F, bg * 0.9F, bb * 0.9F, 1F); // knuckle guard
            Geo.box(ps, trim, -0.9F, 0.95F, -0.86F, 0.9F, 2.0F, -0.76F, light, br, bg, bb, 1F);
        } else {
            for (int i = -1; i <= 1; i++) Geo.box(ps, trim, i * 0.62F - 0.08F, 0.4F, -0.66F, i * 0.62F + 0.08F, 2.0F, -0.58F, trimLight, glow, glow, glow, 1F);
            Geo.box(ps, trim, -0.9F, 1.05F, -0.64F, 0.9F, 1.17F, -0.58F, trimLight, glow, glow, glow, 1F);
        }
        // fingers
        for (int i = 0; i < 4; i++) {
            float x = -0.75F + i * 0.5F, w = 0.4F, d = 0.5F, m = FLEN[i];
            float c1 = curl * 90F, c2 = curl * 98F, c3 = curl * 82F;
            ps.pushPose();
            ps.translate(x, 0, 0.05F);
            ps.mulPose(Axis.ZP.rotationDegrees((i - 1.5F) * spread * 7F));
            ps.mulPose(Axis.XP.rotationDegrees(-c1));
            Geo.box(ps, body, -w / 2, -1.1F * m, -d / 2, w / 2, 0, d / 2, light, br, bg, bb, 1F);
            if (st.gauntlet) Geo.box(ps, trim, -w / 2 - 0.03F, -0.5F * m, -d / 2 - 0.03F, w / 2 + 0.03F, -0.1F * m, d / 2 + 0.03F, light, br, bg, bb, 1F);
            ps.translate(0, -1.1F * m, 0);
            ps.mulPose(Axis.XP.rotationDegrees(-c2));
            Geo.box(ps, body, -w / 2 + 0.02F, -0.9F * m, -d / 2 + 0.02F, w / 2 - 0.02F, 0, d / 2 - 0.02F, light, br * 0.95F, bg * 0.95F, bb * 0.95F, 1F);
            ps.translate(0, -0.9F * m, 0);
            ps.mulPose(Axis.XP.rotationDegrees(-c3));
            Geo.box(ps, body, -w / 2 + 0.04F, -0.7F * m, -d / 2 + 0.04F, w / 2 - 0.04F, 0, d / 2 - 0.04F, light, br * 0.9F, bg * 0.9F, bb * 0.9F, 1F);
            if (!st.gauntlet) Geo.box(ps, trim, -w / 2 + 0.1F, -0.72F * m, -d / 2 + 0.1F, w / 2 - 0.1F, -0.55F * m, d / 2 - 0.1F, trimLight, glow, glow, glow, 1F);
            ps.popPose();
        }
        // thumb
        ps.pushPose();
        ps.translate(1.0F, 0.95F, 0.15F);
        ps.mulPose(Axis.ZP.rotationDegrees(28F + spread * 14F - curl * 10F));
        ps.mulPose(Axis.XP.rotationDegrees(-curl * 62F));
        Geo.box(ps, body, -0.28F, -1.05F, -0.28F, 0.28F, 0, 0.28F, light, br, bg, bb, 1F);
        ps.translate(0, -1.05F, 0);
        ps.mulPose(Axis.XP.rotationDegrees(-curl * 70F));
        ps.mulPose(Axis.ZP.rotationDegrees(-curl * 25F));
        Geo.box(ps, body, -0.24F, -0.85F, -0.24F, 0.24F, 0, 0.24F, light, br * 0.92F, bg * 0.92F, bb * 0.92F, 1F);
        ps.popPose();
        // gems (gauntlet)
        if (st.gauntlet) {
            VertexConsumer gem = buf.getBuffer(RenderType.entityCutoutNoCull(st.gem));
            for (int i = 0; i < 6; i++) {
                float[] g = GEMS[i];
                boolean lit = (litMask & (1 << i)) != 0;
                int c = GEM_COLORS[i];
                float k = lit ? 1F : 0.16F;
                float rr = ((c >> 16) & 255) / 255F * k, gg = ((c >> 8) & 255) / 255F * k, b2 = (c & 255) / 255F * k;
                float h = g[3];
                Geo.box(ps, gem, g[0] - h / 2, g[1] - h / 2, g[2] - h, g[0] + h / 2, g[1] + h / 2, g[2] + h * 0.1F, lit ? Geo.FULL : light, rr, gg, b2, 1F);
                Geo.box(ps, trim, g[0] - h * 0.7F, g[1] - h * 0.7F, g[2] - h * 0.3F, g[0] + h * 0.7F, g[1] + h * 0.7F, g[2] + h * 0.1F, light, br, bg, bb, 1F);
            }
        }
        if (palmDown) ps.popPose();

        // wrist and forearm (always upright)
        Geo.box(ps, trim, -1.22F, 2.2F, -0.9F, 1.22F, 2.55F, 0.9F, trimLight, st.gauntlet ? br : glow, st.gauntlet ? bg : glow, st.gauntlet ? bb : glow, 1F);
        Geo.box(ps, body, -1.12F, 2.55F, -0.86F, 1.12F, 2.9F, 0.86F, light, br, bg, bb, 1F);
        if (armLen > 0.01F) {
            float top = 2.9F + armLen;
            Geo.box(ps, body, -1F, 2.9F, -0.8F, 1F, top, 0.8F, light, br * 0.95F, bg * 0.95F, bb * 0.95F, 1F);
            for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2)
                Geo.box(ps, trim, sx * 1F - 0.08F + sx * 0.02F, 2.9F, sz * 0.8F - 0.08F + sz * 0.02F, sx * 1F + 0.08F + sx * 0.02F, top, sz * 0.8F + 0.08F + sz * 0.02F,
                        trimLight, st.gauntlet ? br : glow, st.gauntlet ? bg : glow, st.gauntlet ? bb : glow, 1F);
            for (float y = 4.4F; y < top - 0.4F; y += 5F)
                Geo.box(ps, body, -1.2F, y, -0.98F, 1.2F, y + 0.4F, 0.98F, light, br * 0.85F, bg * 0.85F, bb * 0.85F, 1F);
            for (float y = 4.8F; y < top - 0.3F; y += 5F)
                Geo.box(ps, trim, -1.24F, y, -1.02F, 1.24F, y + 0.12F, 1.02F, trimLight, st.gauntlet ? br : glow, st.gauntlet ? bg : glow, st.gauntlet ? bb : glow, 1F);
        }
    }

    /** Additive glow pass (flares at the lit stones / rune veins). Same pose as {@link #solid}. */
    static void glow(PoseStack ps, MultiBufferSource buf, Style st, int litMask, float pulse, float flash, boolean palmDown, float armLen) {
        if (palmDown) return;
        if (st.gauntlet) {
            for (int i = 0; i < 6; i++) {
                if ((litMask & (1 << i)) == 0) continue;
                float[] g = GEMS[i];
                float k = 0.75F + 0.25F * pulse + flash;
                GlowFx.billboard(ps, buf, GlowFx.FLARE, true, g[0], g[1], g[2] - 0.2F, 1.6F * g[3] * 3.4F, GEM_COLORS[i], Math.min(1.4F, k), pulse * 2F);
                GlowFx.billboard(ps, buf, GlowFx.HALO, true, g[0], g[1], g[2] - 0.2F, 1.1F * g[3] * 3.4F, 0xFFFFFF, Math.min(1F, 0.4F * k), 0);
            }
        } else {
            float k = 0.6F + 0.4F * pulse;
            GlowFx.billboard(ps, buf, GlowFx.FLARE, true, 0, 1.1F, -0.7F, 2.2F, 0xC77DFF, k, pulse);
            for (float y = 4.6F; y < 2.9F + armLen; y += 6F) GlowFx.billboard(ps, buf, GlowFx.FLARE, true, 0, y, 0, 2.4F, 0xB05CFF, 0.45F * k, -pulse);
        }
    }

    private Hands() {}
}
