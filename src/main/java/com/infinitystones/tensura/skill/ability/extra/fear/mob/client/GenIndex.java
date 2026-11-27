package com.infinitystones.tensura.skill.ability.extra.fear.mob.client;

import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import java.util.HashMap;
import java.util.Map;

/** Generated: layer registration and specs of every creature model. */
final class GenIndex {
    private static final Map<String, Spec> SPECS = new HashMap<>();

    static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(WendigoGen.LAYER, WendigoGen::layer);
        e.registerLayerDefinition(LadyGen.LAYER, LadyGen::layer);
        e.registerLayerDefinition(SurgeonGen.LAYER, SurgeonGen::layer);
        e.registerLayerDefinition(PriestGen.LAYER, PriestGen::layer);
        e.registerLayerDefinition(MascotGen.LAYER, MascotGen::layer);
        e.registerLayerDefinition(DrownedGen.LAYER, DrownedGen::layer);
        e.registerLayerDefinition(GravekeeperGen.LAYER, GravekeeperGen::layer);
        e.registerLayerDefinition(SmilerGen.LAYER, SmilerGen::layer);
        e.registerLayerDefinition(FrostGiantGen.LAYER, FrostGiantGen::layer);
        e.registerLayerDefinition(StalkerGen.LAYER, StalkerGen::layer);
        e.registerLayerDefinition(DoppelgangerGen.LAYER, DoppelgangerGen::layer);
    }

    static Spec spec(String id) {
        return SPECS.computeIfAbsent(id, k -> switch (k) {
            case "wendigo" -> CreatureGen.toSpec(new CreatureGen.CreatureSpec("wendigo", 64, 128, null, WendigoGen.BONES, WendigoGen.CLIPS, WendigoGen.HALOS, WendigoGen.HEAD, WendigoGen.LOOK_YAW, WendigoGen.LOOK_PITCH, WendigoGen.SCALE, WendigoGen.SHADOW));
            case "lady" -> CreatureGen.toSpec(new CreatureGen.CreatureSpec("lady", 64, 64, null, LadyGen.BONES, LadyGen.CLIPS, LadyGen.HALOS, LadyGen.HEAD, LadyGen.LOOK_YAW, LadyGen.LOOK_PITCH, LadyGen.SCALE, LadyGen.SHADOW));
            case "surgeon" -> CreatureGen.toSpec(new CreatureGen.CreatureSpec("surgeon", 64, 64, null, SurgeonGen.BONES, SurgeonGen.CLIPS, SurgeonGen.HALOS, SurgeonGen.HEAD, SurgeonGen.LOOK_YAW, SurgeonGen.LOOK_PITCH, SurgeonGen.SCALE, SurgeonGen.SHADOW));
            case "priest" -> CreatureGen.toSpec(new CreatureGen.CreatureSpec("priest", 64, 64, null, PriestGen.BONES, PriestGen.CLIPS, PriestGen.HALOS, PriestGen.HEAD, PriestGen.LOOK_YAW, PriestGen.LOOK_PITCH, PriestGen.SCALE, PriestGen.SHADOW));
            case "mascot" -> CreatureGen.toSpec(new CreatureGen.CreatureSpec("mascot", 64, 64, null, MascotGen.BONES, MascotGen.CLIPS, MascotGen.HALOS, MascotGen.HEAD, MascotGen.LOOK_YAW, MascotGen.LOOK_PITCH, MascotGen.SCALE, MascotGen.SHADOW));
            case "drowned" -> CreatureGen.toSpec(new CreatureGen.CreatureSpec("drowned", 64, 32, null, DrownedGen.BONES, DrownedGen.CLIPS, DrownedGen.HALOS, DrownedGen.HEAD, DrownedGen.LOOK_YAW, DrownedGen.LOOK_PITCH, DrownedGen.SCALE, DrownedGen.SHADOW));
            case "gravekeeper" -> CreatureGen.toSpec(new CreatureGen.CreatureSpec("gravekeeper", 64, 64, null, GravekeeperGen.BONES, GravekeeperGen.CLIPS, GravekeeperGen.HALOS, GravekeeperGen.HEAD, GravekeeperGen.LOOK_YAW, GravekeeperGen.LOOK_PITCH, GravekeeperGen.SCALE, GravekeeperGen.SHADOW));
            case "smiler" -> CreatureGen.toSpec(new CreatureGen.CreatureSpec("smiler", 64, 64, null, SmilerGen.BONES, SmilerGen.CLIPS, SmilerGen.HALOS, SmilerGen.HEAD, SmilerGen.LOOK_YAW, SmilerGen.LOOK_PITCH, SmilerGen.SCALE, SmilerGen.SHADOW));
            case "frostgiant" -> CreatureGen.toSpec(new CreatureGen.CreatureSpec("frostgiant", 64, 128, null, FrostGiantGen.BONES, FrostGiantGen.CLIPS, FrostGiantGen.HALOS, FrostGiantGen.HEAD, FrostGiantGen.LOOK_YAW, FrostGiantGen.LOOK_PITCH, FrostGiantGen.SCALE, FrostGiantGen.SHADOW));
            case "stalker" -> CreatureGen.toSpec(new CreatureGen.CreatureSpec("stalker", 64, 64, null, StalkerGen.BONES, StalkerGen.CLIPS, StalkerGen.HALOS, StalkerGen.HEAD, StalkerGen.LOOK_YAW, StalkerGen.LOOK_PITCH, StalkerGen.SCALE, StalkerGen.SHADOW));
            case "doppelganger" -> CreatureGen.toSpec(new CreatureGen.CreatureSpec("doppelganger", 64, 64, null, DoppelgangerGen.BONES, DoppelgangerGen.CLIPS, DoppelgangerGen.HALOS, DoppelgangerGen.HEAD, DoppelgangerGen.LOOK_YAW, DoppelgangerGen.LOOK_PITCH, DoppelgangerGen.SCALE, DoppelgangerGen.SHADOW));
            default -> throw new IllegalArgumentException(k);
        });
    }

    private GenIndex() {}
}