package com.infinitystones.tensura.client.render;

import com.infinitystones.tensura.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Third person: the gauntlet is worn on the arm (fingers down along the forearm, sockets facing out) instead of being
 * held like an item, matching the original addon where the in-hand item model was hidden.
 */
public final class GauntletLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public GauntletLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buf, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) return;
        // Shown on the character only while worn in an accessory slot (Curios / Accessories); held stacks stay hidden.
        ItemStack[] worn = new ItemStack[1];
        if (!com.infinitystones.tensura.skill.CuriosCompat.has(player, s -> {
            if (!s.is(ModItems.INFINITY_GAUNTLET.get())) return false;
            worn[0] = s;
            return true;
        })) return;
        {
            HumanoidArm arm = player.getMainArm().getOpposite();
            boolean left = arm == HumanoidArm.LEFT;
            pose.pushPose();
            getParentModel().translateToHand(arm, pose);
            // arm space (px/16): +Y runs down the arm, -Z is the front. Fingertips just past the fist.
            pose.translate((left ? 1F : -1F) / 16F, 12.2F / 16F, 0F);
            pose.mulPose(Axis.YP.rotationDegrees(left ? 90F : 180F));
            pose.scale(0.6F, 0.52F, 0.95F);
            pose.translate(-0.5F, -17.3F / 16F, -8.2F / 16F);
            GauntletRenderer.render(pose, buf, light, OverlayTexture.NO_OVERLAY, player,
                    left ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
            pose.popPose();
        }
    }
}
