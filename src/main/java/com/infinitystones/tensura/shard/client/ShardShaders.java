package com.infinitystones.tensura.shard.client;

import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/**
 * Shader-pack awareness (Iris / Oculus) for the mini-gem visuals. Optional: without the mod, or if the API is not what we
 * expect, everything stays at full strength and nothing fails. With a pack active the additive brightness, alpha and glow
 * radius are capped so the pack's bloom does not blow the effects out.
 */
public final class ShardShaders {
    private static Method inUse;
    private static Object api;
    private static boolean broken, probed;
    private static boolean cached;
    private static long checkedAt;

    /** True while a shader pack is on (re-asked at most every 100 ms). */
    public static boolean active() {
        long now = System.currentTimeMillis();
        if (now - checkedAt < 100) return cached;
        checkedAt = now;
        cached = ask();
        return cached;
    }

    private static boolean ask() {
        if (broken) return false;
        try {
            if (!probed) {
                probed = true;
                ModList list = ModList.get();
                if (list == null || !(list.isLoaded("iris") || list.isLoaded("oculus"))) { broken = true; return false; }
                Class<?> c = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = c.getMethod("getInstance").invoke(null);
                inUse = c.getMethod("isShaderPackInUse");
            }
            return (Boolean) inUse.invoke(api);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
            broken = true;
            return false;
        }
    }

    /** Multiplier for additive alpha / brightness of world effects (1 without a shader pack). */
    public static float glow() { return active() ? 0.58F : 1F; }

    /** Multiplier for the size of additive glow sprites. */
    public static float radius() { return active() ? 0.75F : 1F; }

    /** Multiplier for the glow drawn in menus (milder: GUIs are not shaded, but some packs bloom the whole frame). */
    public static float gui() { return active() ? 0.8F : 1F; }

    private ShardShaders() {}
}
