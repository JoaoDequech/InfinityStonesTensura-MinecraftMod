package com.infinitystones.tensura.skill.ability.extra.fear.mob;

import java.util.Locale;

/** Themed creatures of the fear realm: hitbox, speeds and behaviour flags. Speeds are movement-attribute values. */
public enum Kind {
    //            scene       w    h    walk  chase  scare reach bite  freeze dark  climb emerge toys
    WENDIGO("claims", 1.0F, 3.6F, .20, .34, 7, 2.4, 3.0F, false, false, true, false),
    LADY("mansion", .7F, 2.1F, .20, .34, 3.5, 1.9, 2.5F, true, false, false, false),
    SURGEON("hospital", .9F, 2.5F, .19, .27, 5, 2.3, 3.0F, false, false, false, false),
    PRIEST("church", .9F, 2.4F, .15, .22, 5, 2.1, 2.5F, false, false, false, false),
    MASCOT("fair", 1.3F, 2.5F, .17, .30, 6, 2.2, 3.0F, false, false, false, false),
    DROWNED("well", .9F, .9F, .17, .30, 5, 1.8, 2.5F, false, false, false, true),
    GRAVEKEEPER("graveyard", .9F, 2.3F, .17, .25, 5, 2.4, 3.5F, false, false, false, false),
    SMILER("backrooms", .9F, 2.4F, .18, .33, 5, 2.0, 2.5F, false, true, false, false),
    FROSTGIANT("mountain", 2.0F, 5.0F, .13, .21, 9, 3.6, 4.0F, false, false, false, false),
    STALKER("forest", 1.0F, 3.4F, .20, .36, 4, 2.2, 3.0F, true, false, false, false),
    DOPPELGANGER("home", .6F, 1.8F, .22, .31, 5, 1.8, 2.0F, false, false, false, false);

    public final String scene;
    public final float width, height;
    public final double walk, chase, scareDist, reach;
    public final float bite;
    public final boolean freezeObserved, darkOnly, climbs, emerges;

    Kind(String scene, float w, float h, double walk, double chase, double scareDist, double reach, float bite,
         boolean freeze, boolean dark, boolean climbs, boolean emerges) {
        this.scene = scene; this.width = w; this.height = h; this.walk = walk; this.chase = chase;
        this.scareDist = scareDist; this.reach = reach; this.bite = bite;
        this.freezeObserved = freeze; this.darkOnly = dark; this.climbs = climbs; this.emerges = emerges;
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }

    public static Kind byId(String id) {
        if (id != null) for (Kind k : values()) if (k.id().equals(id)) return k;
        return WENDIGO;
    }

    public static Kind forScene(String scene) {
        for (Kind k : values()) if (k.scene.equals(scene)) return k;
        return WENDIGO;
    }
}
