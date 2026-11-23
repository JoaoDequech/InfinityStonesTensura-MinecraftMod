package com.infinitystones.tensura.client.port;

import com.infinitystones.tensura.InfinityStones;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * Intangibility "vibrate" (original palladium:vibrate, fed by ability_enabled intangibility): ten faint copies of the
 * player jitter around the body, more the longer it has been on (timer 0..4, eased). Copies are the player's own
 * model drawn translucent (alpha 0.3) at a random sideways offset of up to 1/15 block; layers are left out.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class VibrateGhosts {
    private static boolean rendering;

    @SubscribeEvent
    public static void pre(RenderPlayerEvent.Pre e) {
        if (rendering || !(e.getEntity() instanceof AbstractClientPlayer player)) return;
        float partial = e.getPartialTick();
        float v = PortVisuals.vibrate(player, partial);
        if (v <= 0F || player.isInvisible()) return;
        MultiBufferSource ghost = new Ghost(e.getMultiBufferSource(), e.getRenderer().getTextureLocation(player));
        RandomSource rand = RandomSource.create();
        PoseStack pose = e.getPoseStack();
        float yaw = Mth.lerp(partial, player.yRotO, player.getYRot());
        rendering = true;
        try {
            for (int i = 0; i < 10; i++) {
                pose.pushPose();
                pose.translate((rand.nextFloat() - 0.5F) / 15F * v, 0, (rand.nextFloat() - 0.5F) / 15F * v);
                e.getRenderer().render(player, yaw, partial, pose, ghost, e.getPackedLight());
                pose.popPose();
            }
        } finally {
            rendering = false;
        }
    }

    /** Only the body is kept (re-typed translucent, alpha 0.3); name tags, items and armour of the copies are dropped. */
    private record Ghost(MultiBufferSource inner, net.minecraft.resources.ResourceLocation skin) implements MultiBufferSource {
        @Override
        public VertexConsumer getBuffer(RenderType type) {
            if (type == RenderType.entityCutoutNoCull(skin) || type == RenderType.entityTranslucent(skin))
                return new Faded(inner.getBuffer(RenderType.entityTranslucent(skin)), 0.3F);
            return Discard.INSTANCE;
        }
    }

    private record Faded(VertexConsumer out, float alpha) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) { out.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { out.setColor(r, g, b, (int) (a * alpha)); return this; }
        @Override public VertexConsumer setUv(float u, float v) { out.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { out.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { out.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { out.setNormal(x, y, z); return this; }
    }

    private enum Discard implements VertexConsumer {
        INSTANCE;
        @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer setUv(float u, float v) { return this; }
        @Override public VertexConsumer setUv1(int u, int v) { return this; }
        @Override public VertexConsumer setUv2(int u, int v) { return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
    }

    private VibrateGhosts() {}
}
