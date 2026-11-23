package com.infinitystones.tensura.client.port;

import com.infinitystones.tensura.InfinityStones;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.resources.PlayerSkin;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * Power Stone radiation skin (original render layers power_stone_radiation1..10, geckolib "radiation" model, glow +
 * enchantment glint): a slightly inflated player-shaped shell. As the radiation timer (0..1000, see
 * {@link PortVisuals}) rises the texture steps from radiation10 (faint, 1..99) to radiation1 (strongest, 900+).
 * Switched off by the tag no_radiation_texture.
 */
public final class RadiationLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(InfinityStones.id("radiation"), "main");
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[11];
    static {
        for (int i = 1; i <= 10; i++) TEXTURES[i] = InfinityStones.id("textures/models/radiation" + i + ".png");
    }

    private final Shell shell;

    public RadiationLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, EntityModelSet models) {
        super(parent);
        this.shell = new Shell(models.bakeLayer(LAYER));
    }

    /** radiation1 = strongest: 900..1000 → 1, 100..199 → 9, 1..99 → 10. */
    static int stage(float timer) {
        return timer >= 900 ? 1 : Math.max(1, 10 - (int) (timer / 100));
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                       float limbSwingAmount, float partial, float age, float netHeadYaw, float headPitch) {
        float timer = PortVisuals.radiation(player, partial);
        if (timer < 1F || PortVisuals.radiationHidden(player) || player.isInvisible()) return;
        shell.copy(getParentModel());
        RenderType type = RenderType.entityTranslucentEmissive(TEXTURES[stage(timer)]);
        VertexConsumer vc = VertexMultiConsumer.create(buffers.getBuffer(RenderType.entityGlint()), buffers.getBuffer(type));
        shell.renderToBuffer(pose, vc, 0xF000F0, OverlayTexture.NO_OVERLAY, -1);
    }

    /** The original geo: every body part twice (two UV boxes), inflate 0.1, 64x64 texture. */
    public static LayerDefinition definition() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation d = new CubeDeformation(0.1F);
        root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8, d).texOffs(0, 0).addBox(-4, -8, -4, 8, 8, 8, d), PartPose.ZERO);
        root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 16).addBox(-4, 0, -2, 8, 12, 4, d).texOffs(0, 16).addBox(-4, 0, -2, 8, 12, 4, d), PartPose.ZERO);
        root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(24, 16).addBox(-3, -2, -2, 4, 12, 4, d).texOffs(16, 48).addBox(-3, -2, -2, 4, 12, 4, d), PartPose.offset(-5, 2, 0));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(16, 48).addBox(-1, -2, -2, 4, 12, 4, d).texOffs(32, 48).addBox(-1, -2, -2, 4, 12, 4, d), PartPose.offset(5, 2, 0));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(24, 16).addBox(-2, 0, -2, 4, 12, 4, d).texOffs(16, 48).addBox(-2, 0, -2, 4, 12, 4, d), PartPose.offset(-1.9F, 12, 0));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(16, 48).addBox(-2, 0, -2, 4, 12, 4, d).texOffs(16, 48).addBox(-2, 0, -2, 4, 12, 4, d), PartPose.offset(1.9F, 12, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }

    private static final class Shell extends Model {
        private final ModelPart root, head, body, rightArm, leftArm, rightLeg, leftLeg;

        Shell(ModelPart root) {
            super(RenderType::entityTranslucentEmissive);
            this.root = root;
            head = root.getChild("head"); body = root.getChild("body");
            rightArm = root.getChild("right_arm"); leftArm = root.getChild("left_arm");
            rightLeg = root.getChild("right_leg"); leftLeg = root.getChild("left_leg");
        }

        void copy(PlayerModel<?> m) {
            head.copyFrom(m.head); body.copyFrom(m.body);
            rightArm.copyFrom(m.rightArm); leftArm.copyFrom(m.leftArm);
            rightLeg.copyFrom(m.rightLeg); leftLeg.copyFrom(m.leftLeg);
        }

        @Override
        public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay, int color) {
            root.render(pose, vc, light, overlay, color);
        }
    }

    @EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Registration {
        @SubscribeEvent
        public static void definitions(EntityRenderersEvent.RegisterLayerDefinitions e) {
            e.registerLayerDefinition(LAYER, RadiationLayer::definition);
        }

        @SubscribeEvent
        public static void addLayers(EntityRenderersEvent.AddLayers e) {
            for (PlayerSkin.Model skin : PlayerSkin.Model.values()) {
                if (e.getSkin(skin) instanceof PlayerRenderer r) r.addLayer(new RadiationLayer(r, e.getEntityModels()));
            }
        }
    }
}
