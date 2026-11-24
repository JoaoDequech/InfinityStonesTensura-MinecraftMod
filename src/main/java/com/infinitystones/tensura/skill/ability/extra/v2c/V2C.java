package com.infinitystones.tensura.skill.ability.extra.v2c;

import com.infinitystones.tensura.skill.ability.extra.Ability;

import java.util.function.BiConsumer;

public final class V2C {
    public static void register(BiConsumer<String, Ability> r) {
        r.accept("infinity:painted_sky", PaintedSky.INSTANCE);
        r.accept("infinity:genesis", Genesis.INSTANCE);
        r.accept("infinity:wisp_guide", WispGuide.INSTANCE);
        r.accept("infinity:essence_harvest", EssenceHarvest.INSTANCE);
    }

    private V2C() {}
}
