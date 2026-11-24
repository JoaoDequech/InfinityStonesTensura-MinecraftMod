package com.infinitystones.tensura.skill.ability.extra.v2a;

import com.infinitystones.tensura.skill.ability.extra.Ability;

import java.util.function.BiConsumer;

public final class V2A {
    public static void register(BiConsumer<String, Ability> r) {
        r.accept("infinity:sky_fist", SkyFist.INSTANCE);
        r.accept("infinity:sculptor_hands", SculptorHands.INSTANCE);
        r.accept("infinity:gauntlet_wish", GauntletWish.INSTANCE);
        r.accept("infinity:creator_hand", CreatorHand.INSTANCE);
    }

    private V2A() {}
}
