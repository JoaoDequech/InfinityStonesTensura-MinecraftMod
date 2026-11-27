package com.infinitystones.tensura.skill.ability.extra.fear.mob.client;

import com.infinitystones.tensura.skill.ability.extra.fear.mob.FearEntity;
import net.minecraft.client.model.geom.ModelPart;

/** Hand-written touches layered over the generated clips. */
final class Procedurals {
    /** Servo-like creatures update their pose in steps of this many ticks. */
    static float quantum(String id) {
        return switch (id) {
            case "mascot" -> 3F;
            default -> 0F;
        };
    }

    static void apply(Spec spec, FearModel model, FearEntity e, float age, float[] pose) {
        switch (spec.id) {
            case "ap_hanging" -> {
                ModelPart rope = model.part("rope");
                if (rope != null) rope.yScale = Math.max(1, e.variant()) / 16F;
            }
            default -> {}
        }
    }

    private Procedurals() {}
}
