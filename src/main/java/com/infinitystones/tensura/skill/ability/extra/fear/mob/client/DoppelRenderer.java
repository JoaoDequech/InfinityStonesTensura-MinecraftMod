package com.infinitystones.tensura.skill.ability.extra.fear.mob.client;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.extra.fear.mob.FearEntity;
import com.infinitystones.tensura.skill.ability.extra.fear.mob.FearMob;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** The doppelganger: the victim's own skin, a few degrees too tall and wrong, with a stitched grin and blank glowing eyes. */
final class DoppelRenderer extends EntityRenderer<FearMob> {
    private static final ResourceLocation FACE = InfinityStones.id("textures/entity/fear/doppel_face.png");
    private static final ResourceLocation FACE_GLOW = InfinityStones.id("textures/entity/fear/doppel_face_glow.png");
    private static final ResourceLocation FACE_SCREAM = InfinityStones.id("textures/entity/fear/doppel_scream.png");
    private static final ResourceLocation FACE_SCREAM_GLOW = InfinityStones.id("textures/entity/fear/doppel_scream_glow.png");
    private final PlayerModel<FearEntity> wide, slim;

    DoppelRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        wide = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false);
        slim = new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        shadowRadius = .5F;
    }

    @Override public ResourceLocation getTextureLocation(FearMob e) { return DefaultPlayerSkin.getDefaultTexture(); }

    @Override public boolean shouldRender(FearMob e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) { return true; }

    @Override
    public void render(FearMob e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float alpha = e.fadeAlpha(partial);
        if (alpha <= .01F) return;
        float age = e.tickCount + partial;
        ResourceLocation skin = DefaultPlayerSkin.getDefaultTexture();
        boolean thin = false;
        Player p = e.clientVictim();
        if (p instanceof AbstractClientPlayer acp) {
            PlayerSkin s = acp.getSkin();
            skin = s.texture();
            thin = s.model() == PlayerSkin.Model.SLIM;
        }
        PlayerModel<FearEntity> m = thin ? slim : wide;
        pose.pushPose();
        float body = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
        pose.mulPose(Axis.YP.rotationDegrees(180F - body));
        int act = e.action();
        float stretch = 1.08F + (act == FearEntity.A_SCREAM ? .06F * Mth.sin(age * 2.2F) : 0F);
        pose.scale(-.9375F * .97F, -.9375F * stretch, .9375F * .97F);
        pose.translate(0F, -1.501F, 0F);
        float limbPos = e.walkAnimation.position(partial);
        float limbSpeed = Math.min(1F, e.walkAnimation.speed(partial));
        float headYaw = Mth.wrapDegrees(Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) - body);
        m.setAllVisible(true);
        m.setupAnim(e, limbPos, limbSpeed, age, headYaw, Mth.lerp(partial, e.xRotO, e.getXRot()));
        // wrongness: stiff arms, a permanent tilt, and snaps
        float snap = (float) Math.floor(age / 40F) * 7.31F;
        float twitch = (age % 40F) < 2F ? Mth.sin(snap) * .5F : 0F;
        m.head.zRot += .2F + twitch;
        m.head.yRot += (age % 40F) < 2F ? Mth.cos(snap) * .35F : 0F;
        m.rightArm.xRot *= .35F; m.leftArm.xRot *= .35F;
        m.rightArm.zRot += .08F; m.leftArm.zRot -= .08F;
        if (act == FearEntity.A_ATTACK || act == FearEntity.A_KILL || act == FearEntity.A_SCREAM) {
            m.rightArm.xRot = -1.5F; m.leftArm.xRot = -1.5F; m.rightArm.zRot = .3F; m.leftArm.zRot = -.3F;
            if (act == FearEntity.A_SCREAM) m.head.xRot = -.7F;
        }
        m.hat.copyFrom(m.head); m.jacket.copyFrom(m.body);
        m.leftSleeve.copyFrom(m.leftArm); m.rightSleeve.copyFrom(m.rightArm);
        m.leftPants.copyFrom(m.leftLeg); m.rightPants.copyFrom(m.rightLeg);

        int c = FastColor.ARGB32.color((int) (255 * Math.min(1F, alpha)), 255, 255, 255);
        RenderType rt = alpha >= .98F ? RenderType.entityCutoutNoCull(skin) : RenderType.entityTranslucent(skin);
        m.renderToBuffer(pose, buffers.getBuffer(rt), light, OverlayTexture.NO_OVERLAY, c);
        boolean scream = act == FearEntity.A_SCREAM;
        m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(scream ? FACE_SCREAM : FACE)), light, OverlayTexture.NO_OVERLAY, c);
        int g = (int) (255 * alpha * Shaders.glowCap());
        m.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(scream ? FACE_SCREAM_GLOW : FACE_GLOW)), LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, FastColor.ARGB32.color(255, g, g, g));
        pose.popPose();
        super.render(e, yaw, partial, pose, buffers, light);
    }
}
