package com.infinitystones.tensura.client.wheel;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.wheel.WheelPayloads;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * "Paladin" flight pose: a fast Mind flyer is drawn lying horizontal along the look direction (super-hero style),
 * blending in/out over ~0.25 s, with the head raised to look ahead. Driven by the server flag
 * (WheelPayloads.FlyPose), so every player sees the same pose.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class FlightPoseClient {
    private static final Map<Integer, Float> BLEND = new HashMap<>();
    private static final Map<Integer, Long> LAST = new HashMap<>();
    private static float savedXRot, savedXRotO;
    private static boolean pushed;

    @SubscribeEvent
    public static void pre(RenderPlayerEvent.Pre e) {
        Player p = e.getEntity();
        int id = p.getId();
        boolean on = WheelPayloads.paladinFlyers.contains(id); // server flag already requires flying (remote abilities are not synced)
        if (!on && BLEND.isEmpty()) return;
        long now = System.currentTimeMillis();
        float dt = Math.min(0.1F, (now - LAST.getOrDefault(id, now - 16)) / 1000F);
        LAST.put(id, now);
        float b = Mth.clamp(BLEND.getOrDefault(id, 0F) + (on ? dt : -dt) * 4F, 0F, 1F);
        if (b <= 0) { BLEND.remove(id); LAST.remove(id); return; }
        BLEND.put(id, b);
        float pt = e.getPartialTick();
        float yaw = Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot);
        float pitch = Mth.lerp(pt, p.xRotO, p.getXRot());
        float k = b * b * (3 - 2 * b); // smoothstep
        float angle = Mth.clamp(90F + pitch, 25F, 150F) * k;
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        pushed = true;
        ps.translate(0, 0.9, 0);
        ps.mulPose(Axis.YP.rotationDegrees(-yaw));
        ps.mulPose(Axis.XP.rotationDegrees(angle));
        ps.mulPose(Axis.YP.rotationDegrees(yaw));
        ps.translate(0, -0.9, 0);
        // the head looks ahead instead of at the ground while lying down
        savedXRot = p.getXRot(); savedXRotO = p.xRotO;
        float head = Mth.lerp(k, pitch, -55F);
        p.setXRot(head); p.xRotO = head;
    }

    @SubscribeEvent
    public static void post(RenderPlayerEvent.Post e) {
        if (!pushed) return;
        pushed = false;
        e.getPoseStack().popPose();
        e.getEntity().setXRot(savedXRot);
        e.getEntity().xRotO = savedXRotO;
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut e) { WheelPayloads.paladinFlyers.clear(); BLEND.clear(); LAST.clear(); }

    @SubscribeEvent
    public static void respawn(ClientPlayerNetworkEvent.Clone e) { WheelPayloads.paladinFlyers.clear(); }

    private FlightPoseClient() {}
}
