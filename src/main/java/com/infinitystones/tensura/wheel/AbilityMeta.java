package com.infinitystones.tensura.wheel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.infinitystones.tensura.skill.StoneTier;
import com.infinitystones.tensura.skill.StoneTree;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How the wheel drives each power. Read on BOTH sides (client UI and server validation agree because both read the
 * same tree JSON from the jar, plus whatever was registered in code during common setup).
 *
 * <h3>Aim mode</h3> A power that "needs aim" is not fired from the wheel: choosing it closes the wheel and enters aim
 * mode (free camera, walk/run/jump, HUD hint). Left click confirms (the server then runs the normal use, raycasting
 * from where the player looks at that moment); right click / Esc cancels. Declared by, in priority order:
 * <ol>
 *   <li>node JSON {@code "aim": true|false} (in stone_trees/…/&lt;stone&gt;.json, top level of the node);</li>
 *   <li>{@link #declareAim(String, boolean)} with a mode id ("stone.node") or an original type ("infinity:foo");</li>
 *   <li>the built-in default list {@link #DEFAULT_AIM_TYPES} (powers that raycast for a target/point).</li>
 * </ol>
 *
 * <h3>Settings</h3> An integer per player per power changed with Alt + mouse wheel (while hovering it on the wheel,
 * aiming it or channelling it). Declared by node JSON
 * {@code "settings": {"min":1, "max":4, "default":1, "label":"lang.key", "values":["lang.key.1", …]}} or by
 * {@link #declareSetting}. Read on the server with {@link AbilitySettings#get}.
 */
public final class AbilityMeta {
    /** One integer setting. label = translation key of its name; values = optional translation key per value (min..max). */
    public record SettingSpec(int min, int max, int def, String label, List<String> values, int step, int cap) {
        public SettingSpec(int min, int max, int def, String label, List<String> values, int step) {
            this(min, max, def, label, values, step, max);
        }
        /** The same setting limited to its "cap1" value. */
        public SettingSpec limited() {
            return cap >= max ? this : new SettingSpec(min, cap, Math.min(def, cap), label, values, step, cap);
        }
        public int clamp(int v) { return Math.max(min, Math.min(max, v)); }
        /** Number of positions (for the level bar). */
        public int positions() { return (max - min) / Math.max(1, step) + 1; }
        /** Position index 0..positions-1 of a value. */
        public int index(int v) { return (clamp(v) - min) / Math.max(1, step); }
        /** Translation key for a value, or null to show the number. */
        public @Nullable String valueKey(int v) {
            int i = index(v);
            return i >= 0 && i < values.size() ? values.get(i) : null;
        }
    }

    /** Original/new ability types that pick a target or point where the player looks → aim mode by default. */
    public static final Set<String> DEFAULT_AIM_TYPES = Set.of(
            "palladium:spatial_banishment", "palladium:aim", "palladium:energy_beam", "palladium:projectile",
            "corewithstuff:telekinesis", "palladium:cosmic_arsenal", "palladium:harmless_form", "palladium:orbital_judgement",
            "palladium:psychic_dominion", "palladium:reality_confiscation", "palladium:reaper_brand", "palladium:spatial_exchange",
            "palladium:synaptic_cascade",
            "infinity:age_shift", "infinity:cosmic_arc", "infinity:crusher", "infinity:falling_star", "infinity:impossible_rain",
            "infinity:local_decree", "infinity:mind_read", "infinity:reality_pet", "infinity:return_pact", "infinity:soul_link",
            "infinity:soul_reliquary", "infinity:time_field", "infinity:wormhole_pair", "infinity:mind_puppet");

    private static final Map<String, Boolean> AIM = new ConcurrentHashMap<>();
    private static final Map<String, SettingSpec> SETTINGS = new ConcurrentHashMap<>();

    /** Built-in variable powers owned by the wheel (both sides). Others register via VariableAbility / node JSON. */
    static {
        // Mind flight speed: level 1..10 (vanilla speed is about level 2-3); above half = "paladin" horizontal pose
        SETTINGS.put("mind.flight-dummy", new SettingSpec(1, 10, 3, "infinity.setting.flight_speed", List.of(), 1));
    }

    /** Code registration (call from common setup / a static init that runs on both sides). key = mode id or original type. */
    public static void declareAim(String modeIdOrType, boolean aim) { AIM.put(modeIdOrType, aim); }

    /** Code registration of a setting. key = mode id ("power.power_blast_aim") or original type ("palladium:energy_beam"). */
    public static void declareSetting(String modeIdOrType, int min, int max, int def, String labelKey, String... valueKeys) {
        if (max < min) throw new IllegalArgumentException("max < min for " + modeIdOrType);
        SETTINGS.put(modeIdOrType, new SettingSpec(min, max, Math.max(min, Math.min(max, def)), labelKey, List.of(valueKeys), 1));
    }

    /** Same with a step (value changes by step per scroll notch). */
    public static void declareSetting(String modeIdOrType, int min, int max, int step, int def, String labelKey, String... valueKeys) {
        if (max < min || step < 1) throw new IllegalArgumentException("bad range for " + modeIdOrType);
        SETTINGS.put(modeIdOrType, new SettingSpec(min, max, Math.max(min, Math.min(max, def)), labelKey, List.of(valueKeys), step));
    }

    public static boolean needsAim(StoneTree.Node n) {
        if (n.activation() == StoneTree.Activation.PASSIVE || n.activation() == StoneTree.Activation.TOGGLE) return false;
        JsonObject o = StoneTree.of(n.stone()).json(n.id());
        if (o.has("aim") && o.get("aim").isJsonPrimitive()) return o.get("aim").getAsBoolean();
        Boolean code = AIM.get(n.modeId());
        if (code == null) code = AIM.get(n.originalType());
        if (code != null) return code;
        return DEFAULT_AIM_TYPES.contains(n.originalType());
    }

    public static @Nullable SettingSpec setting(StoneTree.Node n) {
        JsonObject o = StoneTree.of(n.stone()).json(n.id());
        if (o.has("settings") && o.get("settings").isJsonObject()) {
            JsonObject s = o.getAsJsonObject("settings");
            int min = s.has("min") ? s.get("min").getAsInt() : 0, max = s.has("max") ? s.get("max").getAsInt() : min;
            int def = s.has("default") ? s.get("default").getAsInt() : min;
            List<String> values = new ArrayList<>();
            if (s.has("values") && s.get("values").isJsonArray()) {
                JsonArray a = s.getAsJsonArray("values");
                for (JsonElement e : a) values.add(e.getAsString());
            }
            if (max >= min) return new SettingSpec(min, max, Math.max(min, Math.min(max, def)),
                    s.has("label") ? s.get("label").getAsString() : "infinity.wheel.setting", List.copyOf(values),
                    s.has("step") ? Math.max(1, s.get("step").getAsInt()) : 1,
                    s.has("cap1") ? Math.max(min, Math.min(max, s.get("cap1").getAsInt())) : max);
        }
        SettingSpec code = SETTINGS.get(n.modeId());
        return code != null ? code : SETTINGS.get(n.originalType());
    }

    /** The setting as this player may use it. */
    public static @Nullable SettingSpec setting(StoneTree.Node n, net.minecraft.world.entity.player.Player p) {
        SettingSpec s = setting(n);
        return s == null || (p != null && StoneTier.has(p, n.stone())) ? s : s.limited();
    }

    /** Channel = started and stopped from the wheel (no key holding). */
    public static boolean isChannel(StoneTree.Node n) { return n.activation() == StoneTree.Activation.HOLD; }

    /** Switch = toggles and passive switches: the wheel stays open after flipping one. */
    public static boolean isSwitch(StoneTree.Node n) {
        return n.activation() == StoneTree.Activation.TOGGLE || n.isPassiveToggle();
    }

    private AbilityMeta() {}
}
