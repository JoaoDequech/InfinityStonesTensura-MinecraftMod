package com.infinitystones.tensura.skill.ability.extra.v2d;

import com.infinitystones.tensura.skill.ability.extra.Ability;

import java.util.function.BiConsumer;

public final class V2D {
    public static void register(BiConsumer<String, Ability> r) {
        r.accept("infinity:time_race", TimeRace.INSTANCE);
        r.accept("infinity:temporal_lag", TemporalLag.INSTANCE);
        r.accept("infinity:lucid_dream", LucidDream.INSTANCE);
        r.accept("infinity:time_bomb", TimeBomb.INSTANCE);
        r.accept("infinity:reincarnate_pet", ReincarnatePet.INSTANCE);
        r.accept("infinity:deja_vu", DejaVu.INSTANCE);
    }

    private V2D() {}
}
