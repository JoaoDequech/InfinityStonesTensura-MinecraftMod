package com.infinitystones.tensura.skill.ability.extra.v2e;

import com.infinitystones.tensura.skill.ability.extra.Ability;

import java.util.function.BiConsumer;

public final class V2E {
    public static void register(BiConsumer<String, Ability> r) {
        r.accept("infinity:enchanter", Enchanter.INSTANCE);
    }

    private V2E() {}
}
