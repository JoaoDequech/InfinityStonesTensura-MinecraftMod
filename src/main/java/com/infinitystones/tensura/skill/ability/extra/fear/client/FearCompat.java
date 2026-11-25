package com.infinitystones.tensura.skill.ability.extra.fear.client;

import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/** Optional-mod checks of the fear realm visuals (never throws, never needs the mods to be present). */
final class FearCompat {
    private static Method inUse;
    private static Object api;
    private static boolean looked, cached;
    private static long cacheTick = -1;

    /** A shader pack (Iris / Oculus) is active: no custom PostChain, additive brightness capped. */
    static boolean shaders() {
        long t = FearState.ticks;
        if (cacheTick == t) return cached;
        cacheTick = t;
        if (!looked) {
            looked = true;
            try {
                if (ModList.get().isLoaded("iris") || ModList.get().isLoaded("oculus")) {
                    Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    api = c.getMethod("getInstance").invoke(null);
                    inUse = c.getMethod("isShaderPackInUse");
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException e) { inUse = null; }
        }
        try { cached = inUse != null && Boolean.TRUE.equals(inUse.invoke(api)); }
        catch (ReflectiveOperationException | RuntimeException e) { cached = false; }
        return cached;
    }

    /** Multiplier for additive glows. */
    static float glow() { return shaders() ? 0.45F : 1F; }

    static boolean veil() { return ModList.get().isLoaded("veil"); }

    private FearCompat() {}
}
