package com.infinitystones.tensura.skill.ability.extra.fear.mob.client;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import java.util.HashMap;
import java.util.Map;

/** Generated: layer registration and specs of every creature model. */
final class GenIndex {
    private static final Map<String, Spec> SPECS = new HashMap<>();

    static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(WendigoGen.LAYER, WendigoGen::layer);
    }

    static Spec spec(String id) {
        return SPECS.computeIfAbsent(id, k -> switch (k) {
            case "wendigo" -> new Spec("wendigo", WendigoGen.LAYER, WendigoGen.BONES, WendigoGen.CLIPS, WendigoGen.HALOS, WendigoGen.HEAD, WendigoGen.LOOK_YAW, WendigoGen.LOOK_PITCH, WendigoGen.SCALE, WendigoGen.SHADOW);
            default -> throw new IllegalArgumentException(k);
        });
    }

    private GenIndex() {}
}
