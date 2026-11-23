package com.infinitystones.tensura.client;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.client.render.GlowFx;
import com.infinitystones.tensura.ground.GroundDisplay;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * Draws ground relics (stones/containers held by invisible armor stands): the relic floats above a slowly turning
 * rune circle, bobs, spins and breathes light; {@code RelicParticles} adds orbiting motes in the stone's colour.
 * Uses the render event rather than a renderer layer so it still works when another mod replaces the armor stand renderer.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class GroundDisplayLayer {
    public static final float BASE_HOVER = 0.55F;

    /** Height of the relic above the stand's feet (also used to place particles). */
    public static double hover(ArmorStand stand, float partialTick) {
        float t = stand.tickCount + partialTick;
        return BASE_HOVER + Mth.sin(t * 0.06F) * 0.07F;
    }

    @SubscribeEvent
    public static void afterLiving(RenderLivingEvent.Post<?, ?> event) {
        if (!(event.getEntity() instanceof ArmorStand stand) || !GroundDisplay.isDisplay(stand)) return;
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buf = event.getMultiBufferSource();
        ItemStack relic = stand.getItemBySlot(EquipmentSlot.CHEST);
        float t = stand.tickCount + event.getPartialTick();
        int color = GlowFx.relicColor(relic);
        if (color == 0) color = 0xFFFFFFFF;
        float pulse = 0.5F + 0.5F * Mth.sin(t * 0.11F);

        // floating relic
        pose.pushPose();
        pose.translate(0D, hover(stand, event.getPartialTick()), 0D);
        pose.mulPose(Axis.YP.rotationDegrees(t * 1.6F));
        pose.scale(0.9F, 0.9F, 0.9F);
        Minecraft.getInstance().getItemRenderer().renderStatic(relic, ItemDisplayContext.GROUND,
                GlowFx.FULL, OverlayTexture.NO_OVERLAY, pose, buf, stand.level(), stand.getId());
        pose.popPose();

        // light pooled on the ground + slowly turning rune circle
        pose.pushPose();
        pose.translate(0D, 0.03D, 0D);
        GlowFx.groundQuad(pose, buf, GlowFx.HALO, 0.85F + 0.06F * pulse, color, 0.22F + 0.12F * pulse, 0);
        GlowFx.groundQuad(pose, buf, GlowFx.RUNE_RING, 0.62F, color, 0.35F + 0.25F * pulse, t * 0.012F);
        pose.popPose();
    }

    private GroundDisplayLayer() {}
}
