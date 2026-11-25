package com.infinitystones.tensura.skill.ability.extra.fear.client;

/** Loads the Veil light code only when Veil is present; any failure disables it silently (the overlay flashlight remains). */
final class FearVeilBridge {
    private static boolean dead;

    static void update(float pt) {
        if (dead || !FearCompat.veil()) return;
        try { FearVeilLights.update(pt); }
        catch (Throwable t) { dead = true; try { FearVeilLights.free(); } catch (Throwable ignored) { } }
    }

    private FearVeilBridge() {}
}
