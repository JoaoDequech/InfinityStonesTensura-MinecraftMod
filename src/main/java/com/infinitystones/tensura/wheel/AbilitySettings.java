package com.infinitystones.tensura.wheel;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side value of each power's integer setting (Alt + mouse wheel). Stored on the player (persistent data,
 * kept on death), synced to the owner with the wheel state and shown on the HUD. See {@link AbilityMeta} for how a
 * power declares its range.
 *
 * <pre>{@code int level = AbilitySettings.get(player, "power.power_blast_aim"); // or get(player, node)}</pre>
 */
public final class AbilitySettings {
    public static final String TAG = "infinity_ability_settings";

    /** Current value (clamped to the declared range); the declared default if never changed; 0 if the power has none. */
    public static int get(Player p, String modeId) {
        StoneTree.Node n = node(modeId);
        return n == null ? 0 : get(p, n);
    }

    public static int get(Player p, StoneTree.Node n) {
        AbilityMeta.SettingSpec spec = AbilityMeta.setting(n, p);
        if (spec == null) return 0;
        CompoundTag all = p.getPersistentData().getCompound(TAG);
        return all.contains(n.modeId()) ? spec.clamp(all.getInt(n.modeId())) : spec.def();
    }

    /** Sets the value (clamped). Returns the stored value. Server only; WheelService syncs it. */
    public static int set(Player p, StoneTree.Node n, int value) {
        AbilityMeta.SettingSpec spec = AbilityMeta.setting(n, p);
        if (spec == null) return 0;
        int v = spec.clamp(value);
        CompoundTag all = p.getPersistentData().getCompound(TAG);
        all.putInt(n.modeId(), v);
        p.getPersistentData().put(TAG, all);
        return v;
    }

    static @Nullable StoneTree.Node node(String modeId) {
        int dot = modeId.indexOf('.');
        if (dot <= 0) return null;
        String key = modeId.substring(0, dot);
        for (Stone s : Stone.values()) if (s.key.equals(key)) return StoneTree.of(s).nodes.get(modeId.substring(dot + 1));
        return null;
    }

    private AbilitySettings() {}
}
