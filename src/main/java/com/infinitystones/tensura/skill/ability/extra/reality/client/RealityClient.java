package com.infinitystones.tensura.skill.ability.extra.reality.client;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.extra.reality.RealityNet;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Client side of Rewrite Loyalty's "Attack target": while armed, a left click on a creature (up to 48 blocks) picks it. */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class RealityClient {
    private static final double RANGE = 48;
    private static boolean armed;
    private static long armedAt;

    public static void arm(boolean on) {
        armed = on;
        armedAt = System.currentTimeMillis();
    }

    @SubscribeEvent
    public static void click(InputEvent.InteractionKeyMappingTriggered e) {
        if (!armed || !e.isAttack()) return;
        if (System.currentTimeMillis() - armedAt > 20_000) { armed = false; return; }
        Minecraft mc = Minecraft.getInstance();
        Entity cam = mc.getCameraEntity();
        if (cam == null || mc.level == null || mc.screen != null) return;
        float pt = mc.getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 eye = cam.getEyePosition(pt);
        Vec3 look = cam.getViewVector(pt);
        Vec3 end = eye.add(look.scale(RANGE));
        HitResult block = mc.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, cam));
        double max = block.getType() == HitResult.Type.MISS ? RANGE * RANGE : block.getLocation().distanceToSqr(eye);
        AABB box = cam.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.5);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(cam, eye, end, box,
                t -> t instanceof LivingEntity && !t.isSpectator() && t.isPickable() && t != mc.player, max);
        if (hit == null) {
            // forgiving: slightly wider ray
            hit = ProjectileUtil.getEntityHitResult(cam, eye, end, box.inflate(1.0),
                    t -> t instanceof LivingEntity && !t.isSpectator() && t != mc.player
                            && t.getBoundingBox().inflate(0.6).clip(eye, end).isPresent(), max);
        }
        if (hit == null) return;
        PacketDistributor.sendToServer(new RealityNet.PetPick(hit.getEntity().getId()));
        armed = false;
        e.setCanceled(true);
        e.setSwingHand(true);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { armed = false; }

    private RealityClient() {}
}
