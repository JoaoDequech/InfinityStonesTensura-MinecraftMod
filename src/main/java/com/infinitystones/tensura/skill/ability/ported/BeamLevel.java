package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;

/**
 * Energy-beam intensity (0 = weak, 1 = strong, 2 = lethal, 3 = explosive / infinite).
 *
 * <p>The only place the beam reads its level. Stored through {@link Levels} — the same per-player compound as the
 * wheel-ux AbilitySettings, key = mode id, range declared as "settings" 0..3 in the tree JSON — so the wheel's
 * scroll-over-the-power control drives it with no code change after the merge. The temporary Alt + scroll in
 * client/port/BeamClient (only while the local beam fires) turns itself off when the wheel settings class exists.</p>
 */
public final class BeamLevel {
    public static final int MAX = 3;
    private static final String[] LEVEL_NAMES = {"Weak", "Strong", "Lethal", "Explosive ∞"};

    /** Current level 0..3 of this beam node for this player. */
    public static int get(ServerPlayer p, StoneTree.Node n) {
        return Levels.get(p, n.modeId(), 0, cap(p, n), 0);
    }

    public static void set(ServerPlayer p, StoneTree.Node n, int level) {
        Levels.set(p, n.modeId(), 0, cap(p, n), level);
    }

    /** Highest level for this player. */
    private static int cap(ServerPlayer p, StoneTree.Node n) {
        var spec = com.infinitystones.tensura.wheel.AbilityMeta.setting(n, p);
        return spec == null ? MAX : Math.min(MAX, spec.max());
    }

    /** Alt + scroll: +1 / -1 (clamped). Feedback on the action bar + a click like the original DamageCycle scroll. */
    public static void step(ServerPlayer p, StoneTree.Node n, int delta) {
        int before = get(p, n);
        int after = Math.clamp(before + Integer.signum(delta), 0, cap(p, n));
        if (after == before) return;
        set(p, n, after);
        feedback(p, after);
    }

    public static void feedback(ServerPlayer p, int level) {
        ChatFormatting c = switch (level) { case 0 -> ChatFormatting.AQUA; case 1 -> ChatFormatting.LIGHT_PURPLE; case 2 -> ChatFormatting.RED; default -> ChatFormatting.GOLD; };
        p.displayClientMessage(Component.translatableWithFallback("ability.infinity.port.beam.level", "Beam: %s",
                Component.translatableWithFallback("ability.infinity.port.beam.level." + level, LEVEL_NAMES[level]).withStyle(c, ChatFormatting.BOLD)), true);
        p.serverLevel().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.COMPARATOR_CLICK,
                net.minecraft.sounds.SoundSource.PLAYERS, 0.5F, 0.8F + level * 0.25F);
    }

    private BeamLevel() {}
}
