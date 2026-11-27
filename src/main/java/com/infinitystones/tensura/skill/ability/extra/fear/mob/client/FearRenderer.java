package com.infinitystones.tensura.skill.ability.extra.fear.mob.client;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.extra.fear.mob.FearEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Draws a generated creature: body (cutout, or translucent while fading), an emissive layer through the eyes render type
 * (Iris maps it to its emissive entity programs) and soft glow billboards. Glow is capped while a shader pack is active.
 */
final class FearRenderer<T extends FearEntity> extends EntityRenderer<T> {
    static final ResourceLocation HALO = InfinityStones.id("textures/entity/fear/halo.png");
    private final FearModel model;
    private final Spec spec;
    private final ModelPart[][] chains;

    FearRenderer(EntityRendererProvider.Context ctx, Spec spec) {
        super(ctx);
        this.spec = spec;
        this.model = new FearModel(ctx.bakeLayer(spec.layer), spec);
        this.shadowRadius = spec.shadow;
        this.chains = new ModelPart[spec.halos.length][];
        for (int i = 0; i < chains.length; i++) {
            String[] segs = spec.bones[(int) spec.halos[i][0]].split("/");
            chains[i] = new ModelPart[segs.length];
            ModelPart p = model.root;
            for (int j = 0; j < segs.length; j++) { p = p.getChild(segs[j]); chains[i][j] = p; }
        }
    }

    @Override public ResourceLocation getTextureLocation(T e) { return spec.tex; }

    @Override public boolean shouldRender(T e, net.minecraft.client.renderer.culling.Frustum f, double x, double y, double z) { return true; }

    @Override
    public void render(T e, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        float alpha = e.fadeAlpha(partial);
        if (alpha <= .01F) return;
        float age = e.tickCount + partial;
        pose.pushPose();
        float body = Mth.rotLerp(partial, e.yBodyRotO, e.yBodyRot);
        pose.mulPose(Axis.YP.rotationDegrees(180F - body));
        pose.scale(-spec.scale, -spec.scale, spec.scale);
        pose.translate(0F, -1.501F, 0F);
        float limbPos = e.walkAnimation.position(partial);
        float limbSpeed = Math.min(1F, e.walkAnimation.speed(partial));
        float headYaw = Mth.rotLerp(partial, e.yHeadRotO, e.yHeadRot) - body;
        float headPitch = Mth.lerp(partial, e.xRotO, e.getXRot());
        headYaw = Mth.wrapDegrees(headYaw);
        model.setupAnim(e, limbPos, limbSpeed, age, headYaw, headPitch);

        RenderType rt = alpha >= .98F ? RenderType.entityCutoutNoCull(spec.tex) : RenderType.entityTranslucent(spec.tex);
        model.renderToBuffer(pose, buffers.getBuffer(rt), light, OverlayTexture.NO_OVERLAY, FastColor.ARGB32.color((int) (255 * Math.min(1F, alpha)), 255, 255, 255));

        float cap = Shaders.glowCap();
        int g = (int) (255 * alpha * cap);
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(spec.glow)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                FastColor.ARGB32.color(255, g, g, g));
        halos(e, pose, buffers, alpha * cap, age);
        pose.popPose();
        super.render(e, yaw, partial, pose, buffers, light);
    }

    private void halos(T e, PoseStack pose, MultiBufferSource buffers, float alpha, float age) {
        if (spec.halos.length == 0) return;
        VertexConsumer vc = buffers.getBuffer(RenderType.eyes(HALO));
        Camera cam = entityRenderDispatcher.camera;
        Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        Matrix4f id = new Matrix4f();
        for (int i = 0; i < spec.halos.length; i++) {
            float[] h = spec.halos[i];
            pose.pushPose();
            for (ModelPart p : chains[i]) p.translateAndRotate(pose);
            Vector3f c = pose.last().pose().transformPosition(h[1] / 16F, h[2] / 16F, h[3] / 16F, new Vector3f());
            pose.popPose();
            float flick = 1F + h[9] * (Mth.sin(age * 1.7F + i * 2F) * .6F + Mth.sin(age * .43F + i) * .4F);
            float a = Mth.clamp(h[8] * flick * alpha, 0F, 1F);
            float r = h[4] / 16F * spec.scale * (Shaders.active() ? .8F : 1F);
            int ca = (int) (255 * a);
            float lx = left.x * r, ly = left.y * r, lz = left.z * r, ux = up.x * r, uy = up.y * r, uz = up.z * r;
            vert(vc, id, c.x + lx + ux, c.y + ly + uy, c.z + lz + uz, 0, 0, h, ca);
            vert(vc, id, c.x + lx - ux, c.y + ly - uy, c.z + lz - uz, 0, 1, h, ca);
            vert(vc, id, c.x - lx - ux, c.y - ly - uy, c.z - lz - uz, 1, 1, h, ca);
            vert(vc, id, c.x - lx + ux, c.y - ly + uy, c.z - lz + uz, 1, 0, h, ca);
        }
    }

    private static void vert(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v, float[] h, int a) {
        vc.addVertex(m, x, y, z).setColor((int) h[5], (int) h[6], (int) h[7], a).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(0F, 1F, 0F);
    }
}
