package com.infinitystones.tensura.skill.ability.extra.fear.mob.client;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.extra.fear.mob.Apparition;
import com.infinitystones.tensura.skill.ability.extra.fear.mob.FearMobs;
import com.infinitystones.tensura.skill.ability.extra.fear.mob.Kind;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only registration of the creature models and renderers (never loaded on a dedicated server). */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FearMobsClient {
    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        GenIndex.registerLayers(e);
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers e) {
        for (Kind k : Kind.values()) {
            if (k == Kind.DOPPELGANGER) e.registerEntityRenderer(FearMobs.MOBS.get(k).get(), DoppelRenderer::new);
            else e.registerEntityRenderer(FearMobs.MOBS.get(k).get(), ctx -> new FearRenderer<>(ctx, GenIndex.spec(k.id())));
        }
        for (Apparition.Type t : Apparition.Type.values())
            e.registerEntityRenderer(FearMobs.APPS.get(t).get(), ctx -> new FearRenderer<>(ctx, GenIndex.spec("ap_" + t.id())));
    }

    private FearMobsClient() {}
}
