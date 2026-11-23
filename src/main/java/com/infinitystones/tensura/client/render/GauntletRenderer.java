package com.infinitystones.tensura.client.render;

import com.infinitystones.tensura.skill.Stone;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The Infinity Gauntlet: gold articulated model (item/gauntlet/base) with six sockets. A socket lights up (full-bright
 * gem + glow) when the wearer carries that stone; otherwise it shows dark, unlit glass. With all six, a golden aura
 * breathes around the hand. Third person is drawn on the arm by {@link GauntletLayer}.
 */
public final class GauntletRenderer extends BlockEntityWithoutLevelRenderer {
    public GauntletRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        if (ctx == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || ctx == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) return; // on the arm (GauntletLayer)
        boolean dormant = ctx == ItemDisplayContext.GROUND || ctx == ItemDisplayContext.FIXED || ctx == ItemDisplayContext.HEAD;
        pose.pushPose();
        if (ctx.firstPerson()) { // a small "kick" when a power fires (GauntletFx.pulse)
            float k = GauntletFx.strongest();
            if (k > 0) {
                pose.translate(0.5F, 0.4F, 0.5F);
                pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-9F * k));
                pose.scale(1 + 0.07F * k, 1 + 0.07F * k, 1 + 0.07F * k);
                pose.translate(-0.5F, -0.4F, -0.5F);
            }
        }
        render(pose, buf, light, overlay, dormant ? null : Minecraft.getInstance().player, ctx);
        pose.popPose();
    }

    /** Draws the gauntlet in model space (0..1 block, fingers +Y, back of the hand +Z). */
    public static void render(PoseStack pose, MultiBufferSource buf, int light, int overlay, @Nullable Player holder, ItemDisplayContext ctx) {
        VertexConsumer vc = buf.getBuffer(Sheets.translucentItemSheet());
        GlowFx.renderModel(GlowFx.baked("item/gauntlet/base"), pose, vc, light, overlay, 1F, 1F, 1F, 1F);
        int mask = holder == null ? 0 : mask(holder);
        boolean world = GlowFx.inWorld(ctx);
        float time = GlowFx.seconds();
        float gui = ctx == ItemDisplayContext.GUI ? 0.7F : 1F;
        boolean live = holder != null && holder == Minecraft.getInstance().player;
        for (Stone s : Stone.values()) {
            float boost = live ? GauntletFx.strength(s.ordinal()) : 0F;
            boolean lit = (mask & (1 << s.ordinal())) != 0 || boost > 0;
            if (lit) GlowFx.renderModel(GlowFx.baked("item/gauntlet/gem_" + s.key), pose, vc, GlowFx.FULL, overlay, 1F, 1F, 1F, 1F);
            else GlowFx.renderModel(GlowFx.baked("item/gauntlet/gem_" + s.key), pose, vc, light, overlay, 0.24F, 0.22F, 0.26F, 1F);
            float[] c = VisualLayout.GAUNTLET_SOCKETS.get(s.key);
            if (lit && c != null) {
                float p = GlowFx.pulse(2.6F, s.ordinal() * 1.1F);
                float size = s == Stone.MIND ? 0.13F : 0.085F;
                GlowFx.billboard(pose, buf, GlowFx.HALO, world, c[0], c[1], c[2] + 0.03F, size * (1.6F + 0.4F * p + 3.2F * boost), s.color(), (0.55F + 0.35F * p + 0.9F * boost) * gui, 0);
                float flare = Math.max(p > 0.6F ? (p - 0.6F) * 1.2F : 0F, boost * 1.3F);
                if (flare > 0) GlowFx.billboard(pose, buf, GlowFx.FLARE, world, c[0], c[1], c[2] + 0.04F, size * (1.8F + 4F * boost), s.color(), flare * gui, time * 0.5F + s.ordinal() + boost * 2F);
                if (boost > 0.05F) // expanding shock ring
                    GlowFx.billboard(pose, buf, GlowFx.RUNE_RING, world, c[0], c[1], c[2] + 0.05F, size * (2F + 7F * (1 - boost)), s.color(), boost * 0.8F * gui, time);
            }
        }
        if (mask == 0b111111) {
            float p = GlowFx.pulse(1.4F, 0);
            GlowFx.billboard(pose, buf, GlowFx.HALO, world, 0.5F, 0.55F, 0.6F, 0.7F + 0.08F * p, GlowFx.GOLD, (0.16F + 0.14F * p) * gui, 0);
        }
    }

    // --------------------------------------------------------------- which stones the wearer carries (cached per tick)

    private static UUID cachedFor;
    private static long cachedTick = Long.MIN_VALUE;
    private static int cachedMask;

    public static int mask(Player p) {
        long tick = p.level().getGameTime();
        if (p.getUUID().equals(cachedFor) && tick == cachedTick) return cachedMask;
        int m = 0;
        for (Stone s : Stone.values()) if (s.isHeldBy(p)) m |= 1 << s.ordinal();
        cachedFor = p.getUUID(); cachedTick = tick; cachedMask = m;
        return m;
    }
}
