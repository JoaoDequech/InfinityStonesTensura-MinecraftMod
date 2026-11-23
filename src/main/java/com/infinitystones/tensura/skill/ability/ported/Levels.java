package com.infinitystones.tensura.skill.ability.ported;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * Per-power integer setting (beam level, punch damage, tick rate, size…). Shares the storage of the wheel's
 * AbilitySettings (wheel-ux branch: player persistent compound "infinity_ability_settings", key = mode id), so the
 * wheel's scroll-over-the-power control changes these values directly; the ranges are declared as "settings" in the
 * stone_trees JSON. Ported code only reads/writes through here, so nothing changes when the branches are merged.
 */
public final class Levels {
    public static final String TAG = "infinity_ability_settings";

    public static int get(Player p, String modeId, int min, int max, int def) {
        CompoundTag all = p.getPersistentData().getCompound(TAG);
        return Math.clamp(all.contains(modeId) ? all.getInt(modeId) : def, min, max);
    }

    public static int set(Player p, String modeId, int min, int max, int value) {
        int v = Math.clamp(value, min, max);
        CompoundTag all = p.getPersistentData().getCompound(TAG);
        all.putInt(modeId, v);
        p.getPersistentData().put(TAG, all);
        return v;
    }

    /** True when the wheel-ux settings control (scroll on the wheel) is present, so our own temporary input stays off. */
    public static final boolean WHEEL_SETTINGS = classExists("com.infinitystones.tensura.wheel.AbilitySettings");

    private static boolean classExists(String name) {
        try { Class.forName(name, false, Levels.class.getClassLoader()); return true; } catch (Throwable t) { return false; }
    }

    private Levels() {}
}
