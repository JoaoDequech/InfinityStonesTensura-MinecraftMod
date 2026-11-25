package com.infinitystones.tensura.skill.ability.extra.fear.client;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.extra.fear.FearRealm;
import com.infinitystones.tensura.skill.ability.extra.fear.FearBooth;
import com.infinitystones.tensura.skill.ability.extra.fear.FearNet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import net.neoforged.neoforge.common.util.TriState;
import org.joml.Vector3f;

/** World-space pieces of the nightmare that the client draws itself: the doppelganger and the weather. */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class FearWorld {
    private static RemotePlayer dop;
    private static Vec3 mirror, normal;
    private static int age, total, watched;

    static void clear() { dop = null; age = total = 0; }

    /** A copy of the victim, in their own skin, that mirrors their moves, then stops and turns its head. */
    static void doppel(int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Vec3 n = Vec3.directionFromRotation(0, mc.player.getYRot());
        normal = new Vec3(n.x, 0, n.z).normalize();
        mirror = mc.player.position().add(normal.scale(5.5));
        dop = new RemotePlayer(mc.level, mc.player.getGameProfile());
        age = 0; total = ticks; watched = 0;
        update(mc);
        dop.setOldPosAndRot();
        FearClient.sound(SoundEvents.AMBIENT_CAVE.value(), 0.6F, 0.8F);
    }

    private static void update(Minecraft mc) {
        var p = mc.player;
        Vec3 pp = p.position();
        double d = pp.subtract(mirror).dot(normal);
        Vec3 pos = pp.subtract(normal.scale(2 * d));
        Vec3 f = Vec3.directionFromRotation(0, p.getYRot());
        Vec3 r = f.subtract(normal.scale(2 * f.dot(normal)));
        float yaw = (float) Math.toDegrees(Math.atan2(-r.x, r.z));
        dop.setOldPosAndRot();
        dop.setPos(pos);
        dop.setYRot(yaw); dop.yBodyRot = yaw; dop.yBodyRotO = dop.yBodyRot; dop.yHeadRot = yaw;
        dop.setXRot(p.getXRot());
        dop.setPose(p.getPose());
        dop.calculateEntityAnimation(false);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        if (dop != null) {
            age++;
            if (age < total * 0.5F) update(mc);
            else {
                Vec3 to = mc.player.getEyePosition().subtract(dop.getEyePosition());
                float want = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
                float k = Mth.clamp((age - total * 0.5F) / (total * 0.3F), 0F, 1F);
                dop.yHeadRotO = dop.yHeadRot;
                dop.yHeadRot += Mth.wrapDegrees(want - dop.yHeadRot) * 0.05F * k + (level.random.nextFloat() - 0.5F) * 1.2F * k;
                Vec3 look = mc.player.getLookAngle();
                if (look.dot(dop.getEyePosition().subtract(mc.player.getEyePosition()).normalize()) > 0.93) watched++;
            }
            if (age >= total || watched > 40) {
                for (int i = 0; i < 24; i++) level.addParticle(ParticleTypes.SQUID_INK, dop.getX(), dop.getY() + 1 + level.random.nextFloat(), dop.getZ(), 0, 0.05, 0);
                dop = null;
            }
        }
        if (level.dimension() != FearRealm.DIM || mc.options.particles().get() == net.minecraft.client.ParticleStatus.MINIMAL) return;
        Vec3 c = mc.player.position();
        var r = level.random;
        if (FearState.weather == 1) {
            DustParticleOptions blood = new DustParticleOptions(new Vector3f(0.5F, 0.02F, 0.03F), 1.1F);
            for (int i = 0; i < 8; i++) level.addParticle(blood, c.x + (r.nextFloat() - 0.5F) * 26, c.y + 8 + r.nextFloat() * 4, c.z + (r.nextFloat() - 0.5F) * 26, 0, -0.9, 0);
        } else if (FearState.weather == 2) {
            for (int i = 0; i < 5; i++) level.addParticle(ParticleTypes.ASH, c.x + (r.nextFloat() - 0.5F) * 28, c.y + r.nextFloat() * 8, c.z + (r.nextFloat() - 0.5F) * 28, 0.25, -0.05, 0.1);
        } else if (FearState.weather == 3) {
            for (int i = 0; i < 4; i++) level.addParticle(ParticleTypes.RAIN, c.x + (r.nextFloat() - 0.5F) * 20, c.y + 5 + r.nextFloat() * 4, c.z + (r.nextFloat() - 0.5F) * 20, 0, -0.9, 0);
            if (FearState.victim && r.nextInt(260) == 0) { FearState.lightning.set(9); FearClient.sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.7F, 0.8F); }
        }
    }

    /** The wall of monitors of the physical control room: live readouts and static (no camera feed: see the report). */
    private static void booth(RenderLevelStageEvent e, Minecraft mc, float pt) {
        Vec3 pp = mc.player.position();
        if (Math.abs(pp.x - FearBooth.CX) > 44 || Math.abs(pp.z - FearBooth.CZ) > 34 || mc.level.dimension() != FearRealm.DIM) return;
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(-cam.x, -cam.y, -cam.z);
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.debugQuads());
        Matrix4f m = ps.last().pose();
        var vs = FearState.panel == null ? java.util.List.<FearNet.VInfo>of() : FearState.panel.victims();
        float z = FearBooth.CZ - 11 + 0.03F, y0 = FearBooth.Y + 2, y1 = FearBooth.Y + 7;
        long tk = FearState.ticks;
        for (int i = 0; i < 5; i++) {
            float x0 = FearBooth.CX - 12 + i * 6 - 2, x1 = x0 + 5;
            FearNet.VInfo v = i < vs.size() ? vs.get(i) : null;
            float fear = v == null ? 0F : v.fear() / 100F;
            quad(vc, m, x0, y0, x1, y1, z, 0.02F + fear * 0.22F, 0.025F, 0.035F, 1F);
            for (int k = 0; k < 7; k++) {
                float yy = y0 + FearGfx.hash(k + i * 9, (int) (tk / 2)) * 5F;
                quad(vc, m, x0, yy, x1, yy + 0.05F + 0.1F * FearGfx.hash(k, i), z - 0.005F, 0.7F, 0.75F, 0.8F, 0.1F + (v == null ? 0.18F : 0.04F));
            }
            if (v != null) {
                quad(vc, m, x0 + 0.2F, y0 + 0.2F, x0 + 0.2F + 4.6F * Mth.clamp(v.health() / 20F, 0, 1), y0 + 0.4F, z - 0.01F, 0.8F, 0.15F, 0.15F, 0.95F);
                quad(vc, m, x0 + 0.2F, y0 + 0.5F, x0 + 0.2F + 4.6F * fear, y0 + 0.7F, z - 0.01F, 1F, 0.5F, 0.2F, 0.95F);
            }
        }
        buf.endBatch(RenderType.debugQuads());
        Font font = mc.font;
        for (int i = 0; i < 5; i++) {
            float x0 = FearBooth.CX - 12 + i * 6 - 2;
            FearNet.VInfo v = i < vs.size() ? vs.get(i) : null;
            Component line1 = v == null ? Component.translatable("infinity.fear.booth.nosignal") : Component.literal(v.name());
            if (v == null && (tk / 12) % 3 == 0) continue;
            ps.pushPose();
            ps.translate(x0 + 0.3F, y1 - 0.55F, z - 0.02F);
            ps.scale(0.05F, -0.05F, 0.05F);
            font.drawInBatch(line1, 0, 0, v == null ? 0xFF608070 : 0xFFE8D8C8, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            if (v != null) {
                font.drawInBatch(Component.translatable("infinity.fear.scene." + v.scene()), 0, 11, 0xFF9A8878, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
                font.drawInBatch(Component.literal("♥ " + (int) v.health() + "  ☠ " + v.fear() + "%  ☼ " + v.battery() + "%"), 0, 22, 0xFFC8A070, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            }
            ps.popPose();
        }
        buf.endBatch();
        ps.popPose();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, float r, float g, float b, float a) {
        vc.addVertex(m, x0, y0, z).setColor(r, g, b, a); vc.addVertex(m, x0, y1, z).setColor(r, g, b, a);
        vc.addVertex(m, x1, y1, z).setColor(r, g, b, a); vc.addVertex(m, x1, y0, z).setColor(r, g, b, a);
        vc.addVertex(m, x1, y0, z).setColor(r, g, b, a); vc.addVertex(m, x1, y1, z).setColor(r, g, b, a);
        vc.addVertex(m, x0, y1, z).setColor(r, g, b, a); vc.addVertex(m, x0, y0, z).setColor(r, g, b, a);
    }

    static void render(RenderLevelStageEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) booth(e, mc, e.getPartialTick().getGameTimeDeltaPartialTick(false));
        if (dop == null || mc.level == null) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        double x = Mth.lerp(pt, dop.xo, dop.getX()), y = Mth.lerp(pt, dop.yo, dop.getY()), z = Mth.lerp(pt, dop.zo, dop.getZ());
        mc.getEntityRenderDispatcher().render(dop, x - cam.x, y - cam.y, z - cam.z, Mth.lerp(pt, dop.yRotO, dop.getYRot()), pt, ps, buf, LightTexture.pack(4, 0));
        buf.endBatch();
    }

    @SubscribeEvent
    public static void name(RenderNameTagEvent e) {
        if (e.getEntity() == dop) e.setCanRender(TriState.FALSE);
    }

    private FearWorld() {}
}
