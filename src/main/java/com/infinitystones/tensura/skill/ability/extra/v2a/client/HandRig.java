package com.infinitystones.tensura.skill.ability.extra.v2a.client;

import com.infinitystones.tensura.InfinityStones;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A baked giant-hand model (Blockbench "Modded Entity" export, see tools/blockbench) with its finger chains, and the
 * procedural pose that curls them. Bone names: hand (palm, fingers, thumb, gems), wrist, arm (one tile of forearm,
 * repeated). Model px are 1/16 of a pose unit; {@code scale(-1,-1,1)} turns the model's Java frame into the pose frame
 * (origin = knuckle line, +Y towards the arm, +Z palm side, +X thumb), so the callers' positions and timings are unchanged.
 */
final class HandRig {
    private static final Logger LOG = LoggerFactory.getLogger("InfinityStones");
    static final float PX = 1F / 16F;
    static final float WRIST_Y = 35F * PX, ARM_Y = 46F * PX, TILE = 40F * PX;
    static final String[] GEMS = {"mind", "power", "space", "reality", "soul", "time"};

    static final ModelLayerLocation STONE = layer("v2_stone_hand");
    static final ModelLayerLocation GAUNTLET = layer("v2_gauntlet");

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(InfinityStones.MOD_ID, name), "main");
    }

    private static final Map<ModelLayerLocation, HandRig> CACHE = new HashMap<>();
    private static final Set<ModelLayerLocation> FAILED = new HashSet<>();

    /** The baked rig for a layer, or null (once logged) if it could not be built, so callers draw the fallback geometry. */
    static HandRig get(ModelLayerLocation loc) {
        HandRig rig = CACHE.get(loc);
        if (rig != null) return rig;
        if (FAILED.contains(loc)) return null;
        try {
            rig = new HandRig(Minecraft.getInstance().getEntityModels().bakeLayer(loc));
            CACHE.put(loc, rig);
            return rig;
        } catch (RuntimeException ex) {
            FAILED.add(loc);
            LOG.warn("Giant hand model layer unavailable, using the built-in geometry");
            return null;
        }
    }

    @EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent
        public static void definitions(EntityRenderersEvent.RegisterLayerDefinitions e) {
            e.registerLayerDefinition(STONE, HandLayers::stone);
            e.registerLayerDefinition(GAUNTLET, HandLayers::gauntlet);
        }
    }

    private final ModelPart hand, wrist, arm, thumb, thumbTip;
    private final ModelPart[][] fingers = new ModelPart[4][3];
    private final ModelPart[] gems = new ModelPart[6];
    private final float thumbRestZ;

    private HandRig(ModelPart root) {
        hand = root.getChild("hand");
        wrist = root.getChild("wrist");
        arm = root.getChild("arm");
        for (int i = 0; i < 4; i++) {
            ModelPart p = hand.getChild("finger" + i);
            ModelPart m = p.getChild("finger" + i + "_mid");
            fingers[i] = new ModelPart[]{p, m, m.getChild("finger" + i + "_tip")};
        }
        thumb = hand.getChild("thumb");
        thumbTip = thumb.getChild("thumb_tip");
        thumbRestZ = thumb.zRot;
        for (int i = 0; i < 6; i++) {
            if (hand.hasChild("gem_" + GEMS[i])) {
                gems[i] = hand.getChild("gem_" + GEMS[i]);
                gems[i].visible = false;
            }
        }
    }

    boolean hasGems() { return gems[0] != null; }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0F, 1F);
        return x * x * (3F - 2F * x);
    }

    /**
     * @param curl 0 open .. 1 fist (the index finger leads, the little finger follows); @param spread 0..1 fingers fanned;
     * @param t ticks, drives the idle sway of an open hand; @param squeeze 0..1 extra clench on top of a closed fist
     */
    void pose(float curl, float spread, float t, float squeeze) {
        float idle = 1F - Mth.clamp(curl * 1.4F, 0F, 1F);
        float sq = squeeze * 0.09F;
        for (int i = 0; i < 4; i++) {
            float lag = 0.12F - 0.04F * i;
            if (i == 3) lag = 0F;
            float c = smooth((curl - lag) / (1F - lag));
            float sway = idle * 0.05F * Mth.sin(t * 0.11F + i * 1.7F);
            fingers[i][0].xRot = c * 1.5708F + sway * 0.6F + sq;
            fingers[i][1].xRot = c * 1.7104F + sway + sq;
            fingers[i][2].xRot = c * 1.4312F + sway * 1.5F + sq;
            fingers[i][0].zRot = (i - 1.5F) * spread * 0.1222F;
        }
        float th = smooth(curl);
        thumb.xRot = th * 1.0821F + idle * 0.04F * Mth.sin(t * 0.09F);
        thumb.zRot = thumbRestZ + spread * 0.2443F - th * 0.1745F;
        thumbTip.xRot = th * 1.2217F + sq;
        thumbTip.zRot = -th * 0.4363F;
    }

    private static void enter(PoseStack ps) {
        ps.scale(-1F, -1F, 1F);
        ps.scale(PX, PX, PX);
    }

    private static void bendWrist(PoseStack ps, boolean palmDown) {
        if (!palmDown) return;
        ps.translate(0, WRIST_Y, 0);
        ps.mulPose(Axis.XP.rotationDegrees(90));
        ps.translate(0, -WRIST_Y, 0);
    }

    /** Palm, fingers, wrist and forearm (gems are drawn by {@link #renderGem}). Pose frame in, armLen in pose units above the wrist. */
    void renderBody(PoseStack ps, VertexConsumer vc, int light, int color, float armLen, boolean palmDown) {
        int ov = OverlayTexture.NO_OVERLAY;
        ps.pushPose();
        bendWrist(ps, palmDown);
        enter(ps);
        hand.render(ps, vc, light, ov, color);
        ps.popPose();

        ps.pushPose();
        enter(ps);
        wrist.render(ps, vc, light, ov, color);
        ps.popPose();

        float len = Math.max(0F, armLen + (2.9F - ARM_Y));
        int n = Mth.clamp((int) Math.ceil(len / TILE), 0, 60);
        for (int k = 0; k < n; k++) {
            float frac = k == n - 1 ? Mth.clamp((len - k * TILE) / TILE, 0.04F, 1F) : 1F;
            ps.pushPose();
            ps.translate(0, k * TILE, 0);
            enter(ps);
            arm.yScale = frac;
            arm.render(ps, vc, light, ov, color);
            arm.yScale = 1F;
            ps.popPose();
        }
    }

    void renderGem(PoseStack ps, VertexConsumer vc, int idx, int light, int color, boolean palmDown) {
        ModelPart g = gems[idx];
        if (g == null) return;
        ps.pushPose();
        bendWrist(ps, palmDown);
        enter(ps);
        hand.translateAndRotate(ps);
        g.visible = true;
        g.render(ps, vc, light, OverlayTexture.NO_OVERLAY, color);
        g.visible = false;
        ps.popPose();
    }

    static int rgb(float r, float g, float b) {
        return 0xFF000000 | (Mth.clamp((int) (r * 255F), 0, 255) << 16) | (Mth.clamp((int) (g * 255F), 0, 255) << 8) | Mth.clamp((int) (b * 255F), 0, 255);
    }
}
