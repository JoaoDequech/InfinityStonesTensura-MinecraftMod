package com.infinitystones.tensura.client.port;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.ported.MobSkins;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * Original mob powers (thanos_classic, trasferimento_zombie, pugmeowla_zombie, dark_zombie) were skins: zombies from
 * Invocar Zumbi are drawn as a player-shaped Thanos (textures/models/thanos_mcu.png); the three named ones add
 * thanos_classic and their own skin on top, with the zombie body hidden. Skinned zombies are known by
 * {@link MobSkins#CLIENT}; their render is replaced here (no mixin).
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class SkinZombieRenderer extends HumanoidMobRenderer<Zombie, SkinZombieRenderer.Model> {
    private static final ResourceLocation THANOS_MCU = InfinityStones.id("textures/models/thanos_mcu.png");
    private static final ResourceLocation THANOS_CLASSIC = InfinityStones.id("textures/models/thanos_classic.png");
    private static final ResourceLocation[] OWN = {
            null, InfinityStones.id("textures/models/trasferimento.png"),
            InfinityStones.id("textures/models/pugmeowla.png"), InfinityStones.id("textures/models/dark.png")};

    private static SkinZombieRenderer instance;
    private static boolean rendering;

    private SkinZombieRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model(ctx.bakeLayer(ModelLayers.PLAYER)), 0.5F);
        addLayer(new Overlays(this));
    }

    @Override
    public ResourceLocation getTextureLocation(Zombie zombie) { return THANOS_MCU; }

    /** A player model whose arms are raised like a zombie's. */
    static final class Model extends PlayerModel<Zombie> {
        Model(net.minecraft.client.model.geom.ModelPart root) { super(root, false); }

        @Override
        public void setupAnim(Zombie z, float limbSwing, float limbSwingAmount, float age, float netHeadYaw, float headPitch) {
            super.setupAnim(z, limbSwing, limbSwingAmount, age, netHeadYaw, headPitch);
            AnimationUtils.animateZombieArms(leftArm, rightArm, z.isAggressive(), attackTime, age);
            leftSleeve.copyFrom(leftArm);
            rightSleeve.copyFrom(rightArm);
        }
    }

    private static final class Overlays extends RenderLayer<Zombie, Model> {
        Overlays(RenderLayerParent<Zombie, Model> parent) { super(parent); }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, Zombie z, float limbSwing, float limbSwingAmount,
                           float partial, float age, float netHeadYaw, float headPitch) {
            Integer kind = MobSkins.CLIENT.get(z.getId());
            if (kind == null || kind == MobSkins.PLAIN || z.isInvisible()) return;
            int overlay = LivingEntityRenderer.getOverlayCoords(z, 0F);
            for (ResourceLocation tex : new ResourceLocation[]{THANOS_CLASSIC, OWN[Mth.clamp(kind, 1, 3)]})
                getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)), light, overlay, -1);
        }
    }

    @SubscribeEvent
    public static void pre(RenderLivingEvent.Pre<?, ?> e) {
        if (rendering || !(e.getEntity() instanceof Zombie z) || !MobSkins.CLIENT.containsKey(z.getId())) return;
        if (instance == null) {
            Minecraft mc = Minecraft.getInstance();
            var dispatcher = mc.getEntityRenderDispatcher();
            instance = new SkinZombieRenderer(new EntityRendererProvider.Context(dispatcher, mc.getItemRenderer(),
                    mc.getBlockRenderer(), dispatcher.getItemInHandRenderer(), mc.getResourceManager(), mc.getEntityModels(), mc.font));
        }
        rendering = true;
        try {
            instance.render(z, Mth.lerp(e.getPartialTick(), z.yRotO, z.getYRot()), e.getPartialTick(), e.getPoseStack(),
                    e.getMultiBufferSource(), e.getPackedLight());
        } finally {
            rendering = false;
        }
        e.setCanceled(true);
    }
}
