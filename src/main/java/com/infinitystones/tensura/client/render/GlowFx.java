package com.infinitystones.tensura.client.render;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.registry.ModItems;
import com.infinitystones.tensura.skill.Stone;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Shared drawing helpers for the glowing relics: baked-model passes with custom light/tint and additive billboards. */
public final class GlowFx {
    public static final ResourceLocation HALO = InfinityStones.id("textures/render/halo.png");
    public static final ResourceLocation FLARE = InfinityStones.id("textures/render/flare.png");
    public static final ResourceLocation RUNE_RING = InfinityStones.id("textures/render/rune_ring.png");
    public static final int FULL = LightTexture.FULL_BRIGHT;
    /** Colour used for the gauntlet / gold relics. */
    public static final int GOLD = 0xFFFFC64A;

    private static final RandomSource RAND = RandomSource.create();

    public static ModelResourceLocation model(String path) { return ModelResourceLocation.standalone(InfinityStones.id(path)); }

    public static BakedModel baked(String path) { return Minecraft.getInstance().getModelManager().getModel(model(path)); }

    /** Draws every quad of a baked model with an explicit light value and RGBA multiplier. */
    public static void renderModel(BakedModel model, PoseStack pose, VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) {
        PoseStack.Pose last = pose.last();
        for (Direction d : Direction.values()) {
            RAND.setSeed(42L);
            for (BakedQuad q : model.getQuads(null, d, RAND)) vc.putBulkData(last, q, r, g, b, a, light, overlay, true);
        }
        RAND.setSeed(42L);
        for (BakedQuad q : model.getQuads(null, null, RAND)) vc.putBulkData(last, q, r, g, b, a, light, overlay, true);
    }

    /** True for display contexts rendered inside the level (billboards must then face the camera). */
    public static boolean inWorld(ItemDisplayContext ctx) {
        return ctx != ItemDisplayContext.GUI && !ctx.firstPerson();
    }

    /**
     * Additive camera-facing quad centred on (x, y, z) of the current pose (model units). The pose's rotation is
     * dropped (only translation + average scale are kept) so the glow is always a disc, whatever the item transform.
     */
    public static void billboard(PoseStack pose, MultiBufferSource buf, ResourceLocation tex, boolean faceCamera,
                                 float x, float y, float z, float radius, int rgb, float intensity, float spin) {
        if (intensity <= 0.01F || radius <= 0) return;
        Matrix4f m = new Matrix4f(pose.last().pose()).translate(x, y, z);
        Vector3f t = m.getTranslation(new Vector3f());
        Vector3f s = m.getScale(new Vector3f());
        float scale = (s.x + s.y + s.z) / 3F * radius;
        if (scale < 1.0E-5F) return;
        Matrix4f bm = new Matrix4f().translation(t);
        if (faceCamera) bm.rotate(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        bm.rotateZ(spin).scale(scale);
        quad(buf.getBuffer(RenderType.eyes(tex)), bm, rgb, intensity, true);
    }

    /** Flat additive quad on the XZ plane of the pose (used for the ground rune circle). */
    public static void groundQuad(PoseStack pose, MultiBufferSource buf, ResourceLocation tex, float radius, int rgb, float intensity, float spin) {
        if (intensity <= 0.01F) return;
        Matrix4f m = new Matrix4f(pose.last().pose()).rotateX((float) (Math.PI / 2)).rotateZ(spin).scale(radius);
        quad(buf.getBuffer(RenderType.eyes(tex)), m, rgb, intensity, true);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, int rgb, float intensity, boolean bothSides) {
        int r = clamp((int) (((rgb >> 16) & 255) * intensity)), g = clamp((int) (((rgb >> 8) & 255) * intensity)), b = clamp((int) ((rgb & 255) * intensity));
        vert(vc, m, -1, -1, 0, 1, r, g, b);
        vert(vc, m, 1, -1, 1, 1, r, g, b);
        vert(vc, m, 1, 1, 1, 0, r, g, b);
        vert(vc, m, -1, 1, 0, 0, r, g, b);
        if (bothSides) {
            vert(vc, m, -1, 1, 0, 0, r, g, b);
            vert(vc, m, 1, 1, 1, 0, r, g, b);
            vert(vc, m, 1, -1, 1, 1, r, g, b);
            vert(vc, m, -1, -1, 0, 1, r, g, b);
        }
    }

    private static void vert(VertexConsumer vc, Matrix4f m, float x, float y, float u, float v, int r, int g, int b) {
        vc.addVertex(m, x, y, 0).setColor(r, g, b, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL).setNormal(0, 0, 1);
    }

    private static int clamp(int v) { return Math.max(0, Math.min(255, v)); }

    /** Soft breathing value 0..1 (seconds-based, independent of the game tick so it also animates in menus). */
    public static float pulse(float speed, float phase) {
        return 0.5F + 0.5F * (float) Math.sin(seconds() * speed + phase);
    }

    public static float seconds() { return (net.minecraft.Util.getMillis() % 3_600_000L) / 1000F; }

    /** Signature colour of the stone carried by this item (ARGB), the gauntlet's gold, or 0 when it is not a relic. */
    public static int relicColor(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (stack.is(ModItems.INFINITY_GAUNTLET.get())) return GOLD;
        Stone s = Stone.carrierOf(stack);
        return s == null ? 0 : s.color();
    }

    private GlowFx() {}
}
