package com.infinitystones.tensura.wheel;

import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Generic "variable ability" API: a power with levels or named modes, changed on the wheel by hovering it and
 * scrolling (and with Alt + scroll while aiming / channelling). Shown as the level bar above the wheel.
 *
 * <pre>{@code
 * // common setup (runs on BOTH sides) — key = mode id "stone.node" or an original_type
 * VariableAbility.range("power.power_blast_aim", 1, 4, 1, 1, "infinity.setting.beam");          // levels 1..4
 * VariableAbility.modes("time.reset_time_speed", 0, "infinity.setting.tick",                     // named modes
 *         "infinity.setting.tick.slow2", "infinity.setting.tick.slow1", "infinity.setting.tick.normal", ...);
 * VariableAbility.onChange("time.reset_time_speed", (player, node, value) -> applyTickRate(player, value));
 * // server, inside the ability
 * int level = VariableAbility.get(player, "power.power_blast_aim");
 * }</pre>
 * JSON alternative on the node: {@code "settings": {"min":1,"max":4,"step":1,"default":1,"label":"…","values":[…]}}.
 * Range/values are validated and clamped on the server (no OP, multiplayer-safe).
 */
public final class VariableAbility {
    @FunctionalInterface
    public interface Listener { void changed(ServerPlayer player, StoneTree.Node node, int value); }

    private static final Map<String, List<Listener>> LISTENERS = new ConcurrentHashMap<>();

    /** Numeric levels min..max moving by step per scroll notch. */
    public static void range(String key, int min, int max, int step, int def, String labelKey) {
        AbilityMeta.declareSetting(key, min, max, step, def, labelKey);
    }

    /** Named modes: value 0..n-1, each shown by its translation key. */
    public static void modes(String key, int def, String labelKey, String... modeKeys) {
        if (modeKeys.length == 0) throw new IllegalArgumentException("no modes for " + key);
        AbilityMeta.declareSetting(key, 0, modeKeys.length - 1, 1, def, labelKey, modeKeys);
    }

    /** Server callback when the player changes the value (e.g. apply a server-wide tick rate at once). */
    public static void onChange(String key, Listener l) { LISTENERS.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(l); }

    public static int get(ServerPlayer p, String modeId) { return AbilitySettings.get(p, modeId); }
    public static int get(ServerPlayer p, StoneTree.Node n) { return AbilitySettings.get(p, n); }

    static void fire(ServerPlayer p, StoneTree.Node n, int value) {
        for (String k : new String[]{n.modeId(), n.originalType()}) {
            List<Listener> ls = LISTENERS.get(k);
            if (ls != null) for (Listener l : ls) l.changed(p, n, value);
        }
    }

    private VariableAbility() {}
}
