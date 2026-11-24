package com.infinitystones.tensura.client.port;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.ported.HarmlessForm;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.animal.Chicken;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Client half of Harmless Form: draws a (scaled, nameless) chicken proxy instead of any player the server marked as transformed
 * (see {@link HarmlessForm#CLIENT_FORMS}). The proxy is never added to the world. No mixin needed: the
 * cancellable {@link RenderLivingEvent.Pre} replaces the mob's own model render.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class HarmlessFormRenderer {
    private static final Map<LivingEntity, Chicken> PROXIES = new WeakHashMap<>();
    private static boolean rendering;

    @SubscribeEvent
    public static void preRender(RenderLivingEvent.Pre<?, ?> e) {
        if (rendering) return;
        LivingEntity entity = e.getEntity();
        if (!(entity instanceof Player) || !HarmlessForm.CLIENT_FORMS.contains(entity.getId())) {
            if (!PROXIES.isEmpty()) PROXIES.remove(entity);
            return;
        }
        Chicken proxy = PROXIES.computeIfAbsent(entity, en -> EntityType.CHICKEN.create(en.level()));
        if (proxy == null) return;
        proxy.setPos(entity.getX(), entity.getY(), entity.getZ());
        proxy.xo = entity.xo; proxy.yo = entity.yo; proxy.zo = entity.zo;
        proxy.tickCount = entity.tickCount;
        proxy.yBodyRot = entity.yBodyRot;
        proxy.yBodyRotO = entity.yBodyRotO;
        proxy.yHeadRot = entity.yHeadRot;
        proxy.yHeadRotO = entity.yHeadRotO;
        proxy.setYRot(entity.getYRot());
        proxy.yRotO = entity.yRotO;
        proxy.setXRot(entity.getXRot());
        proxy.xRotO = entity.xRotO;
        proxy.hurtTime = entity.hurtTime;
        proxy.deathTime = entity.deathTime;
        proxy.setCustomName(null);
        proxy.setCustomNameVisible(false);
        float partial = e.getPartialTick();
        float yaw = Mth.lerp(partial, entity.yRotO, entity.getYRot());
        rendering = true;
        try {
            e.getPoseStack().pushPose();
            e.getPoseStack().scale(2.1F, 2.1F, 2.1F);
            Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(proxy)
                    .render(proxy, yaw, partial, e.getPoseStack(), e.getMultiBufferSource(), e.getPackedLight());
            e.getPoseStack().popPose();
        } finally {
            rendering = false;
        }
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent e) {
        if (e.getLevel().isClientSide()) {
            HarmlessForm.CLIENT_FORMS.remove(e.getEntity().getId());
            if (e.getEntity() instanceof LivingEntity l) PROXIES.remove(l);
        }
    }

    @SubscribeEvent
    public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut e) { clear(); }

    @SubscribeEvent
    public static void respawnOrDimension(ClientPlayerNetworkEvent.Clone e) { clear(); }

    private static void clear() {
        HarmlessForm.CLIENT_FORMS.clear();
        PROXIES.clear();
    }

    private HarmlessFormRenderer() {}
}
