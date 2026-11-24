package com.infinitystones.tensura.skill.ability.extra.v2b;

import com.infinitystones.tensura.skill.ability.extra.Ability;

import java.util.function.BiConsumer;

public final class V2B {
    public static void register(BiConsumer<String, Ability> r) {
        r.accept("infinity:star_rails", StarRails.INSTANCE);
        r.accept("infinity:cosmic_scale", CosmicScale.INSTANCE);
        r.accept("infinity:telepathic_link", TelepathicLink.INSTANCE);
        r.accept("infinity:borrowed_eyes", BorrowedEyes.INSTANCE);
    }

    private V2B() {}
}
