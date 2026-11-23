package com.infinitystones.tensura.client.render;

import com.infinitystones.tensura.skill.Stone;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Item renderer for the six stones and their carriers. Stones: faceted emissive gem model (item/gem/KEY) drawn
 * full-bright, plus an additive halo and a slowly turning star flare that breathe. Carriers: their original geometry
 * (item/base/NAME) plus a soft glow at the stone's location.
 */
public final class GemItemRenderer extends BlockEntityWithoutLevelRenderer {
    public GemItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (path.endsWith("_stone")) renderStone(path.substring(0, path.length() - "_stone".length()), ctx, pose, buf, overlay);
        else renderCarrier(path, stack, ctx, pose, buf, light, overlay);
    }

    private static Stone stone(String key) {
        try { return Stone.valueOf(key.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
    }

    private void renderStone(String key, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buf, int overlay) {
        Stone s = stone(key);
        int color = s == null ? 0xFFFFFFFF : s.color();
        float phase = s == null ? 0 : s.ordinal() * 1.3F;
        float pulse = GlowFx.pulse(2.2F, phase);
        float time = GlowFx.seconds();
        boolean world = GlowFx.inWorld(ctx);

        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        if (ctx == ItemDisplayContext.GUI) pose.mulPose(Axis.YP.rotationDegrees(10F * (float) Math.sin(time * 0.9F + phase)));
        else if (ctx == ItemDisplayContext.FIXED || ctx == ItemDisplayContext.HEAD) pose.mulPose(Axis.YP.rotationDegrees(time * 25F));
        pose.translate(-0.5F, -0.5F, -0.5F);
        GlowFx.renderModel(GlowFx.baked("item/gem/" + key), pose, buf.getBuffer(Sheets.translucentItemSheet()), GlowFx.FULL, overlay, 1F, 1F, 1F, 1F);
        pose.popPose();

        // glow: inner halo (tight, bright) + outer halo (wide, faint) + rotating flare
        float base = ctx == ItemDisplayContext.GUI ? 0.75F : 1F;
        GlowFx.billboard(pose, buf, GlowFx.HALO, world, 0.5F, 0.5F, 0.5F, 0.34F + 0.04F * pulse, color, (0.55F + 0.3F * pulse) * base, 0);
        GlowFx.billboard(pose, buf, GlowFx.HALO, world, 0.5F, 0.5F, 0.5F, 0.62F + 0.08F * pulse, color, (0.18F + 0.14F * pulse) * base, 0);
        if (ctx != ItemDisplayContext.GUI || pulse > 0.55F)
            GlowFx.billboard(pose, buf, GlowFx.FLARE, world, 0.5F, 0.5F, 0.5F, 0.42F + 0.1F * pulse, color, 0.35F * pulse * base, time * 0.35F + phase);
    }

    private void renderCarrier(String path, ItemStack stack, ItemDisplayContext ctx, PoseStack pose, MultiBufferSource buf, int light, int overlay) {
        boolean eye = path.equals("eye_of_agamotto");
        boolean selfLit = path.equals("tesseract");
        GlowFx.renderModel(GlowFx.baked("item/base/" + path), pose, buf.getBuffer(Sheets.translucentItemSheet()),
                selfLit ? GlowFx.FULL : light, overlay, 1F, 1F, 1F, 1F);

        int color = GlowFx.relicColor(stack);
        if (color == 0) return;
        float[] anchor = eye ? new float[]{0.5F, 0.5F, 0.6F, 0.16F} : VisualLayout.CARRIERS.get(path);
        if (anchor == null) return;
        float pulse = GlowFx.pulse(2.0F, path.length());
        boolean world = GlowFx.inWorld(ctx);
        float k = ctx == ItemDisplayContext.GUI ? 0.6F : 1F;
        GlowFx.billboard(pose, buf, GlowFx.HALO, world, anchor[0], anchor[1], anchor[2], anchor[3] * (1.5F + 0.25F * pulse), color, (0.35F + 0.25F * pulse) * k, 0);
        if (eye) GlowFx.billboard(pose, buf, GlowFx.FLARE, world, anchor[0], anchor[1], anchor[2], 0.3F, color, 0.3F * pulse * k, GlowFx.seconds() * 0.3F);
    }
}
