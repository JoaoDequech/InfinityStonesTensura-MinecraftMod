package com.infinitystones.tensura.shard.client;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.client.port.InvisibleRender;
import com.infinitystones.tensura.client.render.GlowFx;
import com.infinitystones.tensura.shard.ShardBelt;
import com.infinitystones.tensura.shard.ShardClientState;
import com.infinitystones.tensura.shard.ShardNet;
import com.infinitystones.tensura.skill.Stone;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Belt hologram: everyone who carries mini gems shows a small holographic copy of the stone spinning at the right hip
 * (up to three, orbiting slightly). Follows the body pose, bobs, glows in the stone's colour, scanline + flicker. In
 * the last 10 seconds of a gem it strobes faster and cracks spread across it. Drawn with the entity translucent-emissive
 * render type (white texture, vertex colours) and the shared eyes-type glow sprites, which shader packs understand;
 * brightness is capped while a shader pack is on.
 */
public final class ShardBeltLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    private static final ResourceLocation WHITE = InfinityStones.id("textures/render/white.png");
    private static final float H = 0.07F, R = 0.042F;

    public ShardBeltLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) { super(parent); }

    @Override
    public void render(PoseStack pose, MultiBufferSource buf, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
                       float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible() || player.isSpectator() || InvisibleRender.hidden(player)) return;
        List<ShardNet.BeltEntry> gems = ShardClientState.belt(player.getId());
        if (gems.isEmpty()) return;
        long gt = player.level().getGameTime();
        float time = (gt % 1_000_000L + partialTick) / 20F;
        float cap = ShardShaders.glow();
        pose.pushPose();
        getParentModel().body.translateAndRotate(pose);
        int n = gems.size();
        for (int k = 0; k < n; k++) {
            ShardNet.BeltEntry e = gems.get(k);
            Stone st = Stone.values()[Mth.clamp(e.stone(), 0, Stone.values().length - 1)];
            int rgb = st.color() & 0xFFFFFF;
            float left = e.infinite() ? 1e9F : e.expiry() - (gt + partialTick);
            float fin = left <= ShardBelt.FINAL_TICKS ? Mth.clamp(1F - left / ShardBelt.FINAL_TICKS, 0F, 1F) : -1F;
            float strobe = 1F;
            if (fin >= 0) strobe = Mth.sin(time * (4F + fin * 12F) * Mth.TWO_PI) > 0 ? 1F : 0.25F;
            float dip = Mth.sin(time * 9.1F + k * 2.3F) > 0.93F ? 0.4F : 1F;                     // scanline flicker
            float flick = (0.84F + 0.16F * Mth.sin(time * 37F + k * 3F)) * dip * strobe;
            float a = 0.62F * flick * cap;

            float x = -0.36F, y = 0.74F + Mth.sin(time * 1.8F + k * 2F) * 0.02F, z = 0.02F;
            if (n > 1) {
                float ang = time * 0.9F + k * Mth.TWO_PI / n;
                x += Mth.cos(ang) * 0.075F; z += Mth.sin(ang) * 0.075F;
            }
            if (fin >= 0) { x += Mth.sin(time * 91F + k) * 0.008F * fin; y += Mth.cos(time * 77F + k) * 0.008F * fin; }

            pose.pushPose();
            pose.translate(x, y, z);
            // soft glow sprites (eyes render type, camera facing)
            GlowFx.billboard(pose, buf, GlowFx.HALO, true, 0, 0, 0, 0.19F * ShardShaders.radius(), rgb, 0.5F * flick * cap, 0);
            GlowFx.billboard(pose, buf, GlowFx.FLARE, true, 0, 0, 0, 0.075F * ShardShaders.radius(), rgb, 0.55F * flick * cap, time * 1.5F + k);
            pose.mulPose(Axis.YP.rotationDegrees(time * 140F + k * 60F));
            crystal(pose, buf, time, k, rgb, a, fin, strobe * cap);
            pose.popPose();
        }
        pose.popPose();
    }

    private static void v(VertexConsumer vc, PoseStack.Pose p, float x, float y, float z, float r, float g, float b, float a, float nx, float ny, float nz) {
        vc.addVertex(p.pose(), x, y, z).setColor(r, g, b, Mth.clamp(a, 0F, 1F)).setUv(0.5F, 0.5F)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p, nx, ny, nz);
    }

    private static void tri(VertexConsumer vc, PoseStack.Pose p, float[] a, float[] b, float[] c, float r, float g, float bl, float al) {
        float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], wx = c[0] - a[0], wy = c[1] - a[1], wz = c[2] - a[2];
        float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx;
        float l = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (l > 1e-6F) { nx /= l; ny /= l; nz /= l; }
        v(vc, p, a[0], a[1], a[2], r, g, bl, al, nx, ny, nz);
        v(vc, p, b[0], b[1], b[2], r, g, bl, al, nx, ny, nz);
        v(vc, p, c[0], c[1], c[2], r, g, bl, al, nx, ny, nz);
        v(vc, p, c[0], c[1], c[2], r, g, bl, al, nx, ny, nz);
    }

    private static float hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFF) / 65535F;
    }

    /** Hexagonal bipyramid (translucent faces), moving scanline band, bright core, edges, and the cracks of the final seconds. */
    private static void crystal(PoseStack pose, MultiBufferSource buf, float time, int k, int rgb, float a, float fin, float lineA) {
        PoseStack.Pose p = pose.last();
        // a buffer is only valid until another render type is requested: fetch right before writing
        VertexConsumer faces = buf.getBuffer(RenderType.entityTranslucentEmissive(WHITE));
        float cr = (rgb >> 16 & 255) / 255F, cg = (rgb >> 8 & 255) / 255F, cb = (rgb & 255) / 255F;
        float[] top = {0, -H, 0}, bot = {0, H, 0};
        float[][] ring = new float[6][];
        for (int i = 0; i < 6; i++) { float an = i * Mth.PI / 3F; ring[i] = new float[]{Mth.cos(an) * R, 0, Mth.sin(an) * R}; }
        int tick = (int) (time * 12F);
        for (int i = 0; i < 6; i++) {
            if (fin > 0.55F && hash(i + k * 7, tick) < (fin - 0.45F) * 0.7F) continue;      // glitch: a face drops out
            float[] s0 = ring[i], s1 = ring[(i + 1) % 6];
            float sh = 0.5F + 0.5F * Mth.sin(i * 1.3F + time * 3.2F);
            float w = 0.15F + 0.5F * sh;
            tri(faces, p, top, s0, s1, mixc(cr, w), mixc(cg, w), mixc(cb, w), a * (0.45F + 0.55F * sh));
            tri(faces, p, bot, s1, s0, mixc(cr, w * 0.6F), mixc(cg, w * 0.6F), mixc(cb, w * 0.6F), a * (0.35F + 0.4F * sh));
        }
        // scanline band sweeping the crystal
        float sy = -H + 2 * H * ((time * 0.9F + k * 0.31F) % 1F);
        float rr = R * (1F - Math.abs(sy) / H);
        float t0 = 0.004F;
        for (int i = 0; i < 6; i++) {
            float a0 = i * Mth.PI / 3F, a1 = (i + 1) * Mth.PI / 3F;
            float[] p0 = {Mth.cos(a0) * rr * 1.02F, sy - t0, Mth.sin(a0) * rr * 1.02F}, p1 = {Mth.cos(a1) * rr * 1.02F, sy - t0, Mth.sin(a1) * rr * 1.02F};
            float[] p2 = {p1[0], sy + t0, p1[2]}, p3 = {p0[0], sy + t0, p0[2]};
            tri(faces, p, p0, p1, p2, 1F, 1F, 1F, Math.min(1F, a * 1.35F));
            tri(faces, p, p0, p2, p3, 1F, 1F, 1F, Math.min(1F, a * 1.35F));
        }
        // core
        float cs = 0.38F;
        float[] ct = {0, -H * cs, 0}, cbm = {0, H * cs, 0};
        for (int i = 0; i < 6; i++) {
            float[] s0 = {ring[i][0] * cs, 0, ring[i][2] * cs}, s1 = {ring[(i + 1) % 6][0] * cs, 0, ring[(i + 1) % 6][2] * cs};
            tri(faces, p, ct, s0, s1, 1F, 1F, 1F, Math.min(1F, a * 1.1F));
            tri(faces, p, cbm, s1, s0, 1F, 1F, 1F, Math.min(1F, a * 0.9F));
        }
        // edges + cracks (lines)
        float la = 0.85F * lineA;
        if (la <= 0.02F) return;
        VertexConsumer lines = buf.getBuffer(RenderType.lines());
        for (int i = 0; i < 6; i++) {
            float[] s0 = ring[i], s1 = ring[(i + 1) % 6];
            line(lines, p, s0, s1, 1F, 1F, 1F, la);
            line(lines, p, top, s0, cr, cg, cb, la * 0.8F);
            line(lines, p, bot, s0, cr, cg, cb, la * 0.7F);
        }
        if (fin >= 0) {
            int count = 1 + (int) (fin * 7F);
            int segs = Math.min(4, 1 + (int) (fin * 5F));
            for (int c = 0; c < count; c++) {
                float an = hash(c, k) * Mth.TWO_PI, y = (hash(c + 11, k) - 0.5F) * H * 1.3F;
                float[] prev = surface(an, y);
                for (int sg = 0; sg < segs; sg++) {
                    an += (hash(c * 5 + sg, k + 3) - 0.5F) * 0.7F;
                    y += (hash(c * 7 + sg, k + 5) - 0.35F) * 0.03F;
                    y = Mth.clamp(y, -H * 0.85F, H * 0.85F);
                    float[] next = surface(an, y);
                    line(lines, p, prev, next, 1F, 1F, 1F, Math.min(1F, la * 1.2F));
                    prev = next;
                }
            }
        }
    }

    /** A point on the crystal's side at this angle and height. */
    private static float[] surface(float an, float y) {
        float rad = R * (1F - Math.abs(y) / H) * 1.03F;
        return new float[]{Mth.cos(an) * rad, y, Mth.sin(an) * rad};
    }

    private static void line(VertexConsumer vc, PoseStack.Pose p, float[] a, float[] b, float r, float g, float bl, float al) {
        float nx = b[0] - a[0], ny = b[1] - a[1], nz = b[2] - a[2];
        float l = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (l < 1e-6F) return;
        nx /= l; ny /= l; nz /= l;
        vc.addVertex(p.pose(), a[0], a[1], a[2]).setColor(r, g, bl, Mth.clamp(al, 0F, 1F)).setNormal(p, nx, ny, nz);
        vc.addVertex(p.pose(), b[0], b[1], b[2]).setColor(r, g, bl, Mth.clamp(al, 0F, 1F)).setNormal(p, nx, ny, nz);
    }

    private static float mixc(float c, float w) { return c + (1F - c) * w; }

    // ================================================================== particles

    @EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
    public static final class Fx {
        private static final RandomSource RNG = RandomSource.create();
        private static final Map<Integer, Integer> MISSING = new java.util.HashMap<>();

        @SubscribeEvent
        public static void tick(ClientTickEvent.Post e) {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            Map<Integer, List<ShardNet.BeltEntry>> belts = ShardClientState.belts();
            if (level == null || mc.isPaused() || belts.isEmpty()) return;
            long gt = level.getGameTime();
            for (Iterator<Map.Entry<Integer, List<ShardNet.BeltEntry>>> it = belts.entrySet().iterator(); it.hasNext(); ) {
                var en = it.next();
                Entity ent = level.getEntity(en.getKey());
                if (!(ent instanceof Player p)) {
                    // not loaded (yet): forget it only after a while, the message can arrive before the entity does
                    if (MISSING.merge(en.getKey(), 1, Integer::sum) > 200) { it.remove(); MISSING.remove(en.getKey()); }
                    continue;
                }
                MISSING.remove(en.getKey());
                if (p.isInvisible() || p.isSpectator() || InvisibleRender.hidden(p) || p.distanceToSqr(mc.player) > 28 * 28) continue;
                double yaw = Math.toRadians(p.yBodyRot);
                double hx = p.getX() - Math.cos(yaw) * 0.36, hy = p.getY() + (p.isShiftKeyDown() ? 0.72 : 0.9), hz = p.getZ() - Math.sin(yaw) * 0.36;
                for (ShardNet.BeltEntry g : en.getValue()) {
                    int rgb = Stone.values()[Mth.clamp(g.stone(), 0, Stone.values().length - 1)].color() & 0xFFFFFF;
                    float fin = !g.infinite() && g.expiry() - gt <= ShardBelt.FINAL_TICKS ? Mth.clamp(1F - (g.expiry() - gt) / (float) ShardBelt.FINAL_TICKS, 0, 1) : -1F;
                    var col = new Vector3f((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F);
                    if ((gt + en.getKey()) % 7 == 0)
                        level.addParticle(new DustParticleOptions(col, 0.4F), hx + RNG.nextGaussian() * 0.06, hy + RNG.nextGaussian() * 0.05, hz + RNG.nextGaussian() * 0.06, 0, 0.012, 0);
                    if ((gt + en.getKey()) % 19 == 0)
                        level.addParticle(ParticleTypes.END_ROD, hx + RNG.nextGaussian() * 0.05, hy + 0.05, hz + RNG.nextGaussian() * 0.05, 0, 0.01, 0);
                    if (fin >= 0 && RNG.nextFloat() < 0.25F + fin * 0.5F)
                        level.addParticle(ParticleTypes.ELECTRIC_SPARK, hx + RNG.nextGaussian() * 0.05, hy + RNG.nextGaussian() * 0.05, hz + RNG.nextGaussian() * 0.05,
                                RNG.nextGaussian() * 0.03, -0.01, RNG.nextGaussian() * 0.03);
                }
            }
        }
    }
}
