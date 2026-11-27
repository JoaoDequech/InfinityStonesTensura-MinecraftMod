package com.infinitystones.tensura.skill.ability.extra.fear.mob.client;

import java.lang.reflect.Method;

/** Optional Iris/Oculus detection by reflection (no hard dependency); the answer is cached for a short while. */
public final class Shaders {
    private static Method instance, inUse;
    private static boolean failed;
    private static boolean value;
    private static long last;

    public static boolean active() {
        long now = System.nanoTime();
        if (now - last < 250_000_000L) return value;
        last = now;
        if (failed) return false;
        try {
            if (instance == null) {
                Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                instance = c.getMethod("getInstance");
                inUse = c.getMethod("isShaderPackInUse");
            }
            value = (Boolean) inUse.invoke(instance.invoke(null));
        } catch (Throwable t) {
            failed = true;
            value = false;
        }
        return value;
    }

    /** Multiplier for additive glow so bloom-heavy shader packs do not blow it out. */
    public static float glowCap() { return active() ? .55F : 1F; }

    private Shaders() {}
}
