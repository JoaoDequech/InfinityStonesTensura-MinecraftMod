package com.infinitystones.tensura.client.port;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.ported.Intangibility;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderBlockScreenEffectEvent;

/** Client side of the core ports: no "inside a block" overlay while intangible; reset state on disconnect. */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class PortClient {
    @SubscribeEvent
    public static void overlay(RenderBlockScreenEffectEvent e) {
        if (Intangibility.clientSelf && e.getOverlayType() == RenderBlockScreenEffectEvent.OverlayType.BLOCK) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut e) { Intangibility.clientSelf = false; }

    private PortClient() {}
}
