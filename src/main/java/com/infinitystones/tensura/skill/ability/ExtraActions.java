package com.infinitystones.tensura.skill.ability;

import com.infinitystones.tensura.gauntlet.*;
import com.infinitystones.tensura.skill.StoneTree;
import com.infinitystones.tensura.skill.ability.extra.Abilities;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Integration point for the NEW abilities (v2, notes/new-abilities.md, code in skill/ability/extra). Dispatch on
 * {@code n.originalType()} = "infinity:&lt;name&gt;" through {@link Abilities}.
 * press â†’ null = not ours, TRUE = OK, FALSE = fizzled. Lifecycle â†’ true = handled. Server thread, validated by the wheel.
 * No hold nodes: channelled powers are press-to-start / press-again (aim mode friendly).
 */
public final class ExtraActions {
    public static @Nullable Boolean press(ServerPlayer p, StoneTree.Node n, boolean alt) {
        Boolean g = gauntlet(p, n, alt);
        if (g != null) return g;
        Ability a = Abilities.get(n.originalType());
        return a == null ? null : a.press(p, alt);
    }
    public static boolean holdStart(ServerPlayer p, StoneTree.Node n) { return false; }
    public static boolean holdTick(ServerPlayer p, StoneTree.Node n, int ticks) { return false; }
    public static boolean holdEnd(ServerPlayer p, StoneTree.Node n) { return false; }
    /** Infinity Gauntlet powers (complete gauntlet worn) â€” owned by the gauntlet work stream. */
    private static @Nullable Boolean gauntlet(ServerPlayer p, StoneTree.Node n, boolean alt) {
        return switch (n.originalType()) {
            case "infinity:gauntlet_snap" -> Snap.snap(p);
            case "infinity:gauntlet_return" -> Snap.bringBack(p, alt);
            case "infinity:gauntlet_purge" -> Snap.purge(p);
            case "infinity:gauntlet_execution" -> GauntletAbilities.execute(p, alt);
            case "infinity:gauntlet_convergence" -> GauntletAbilities.convergence(p);
            case "infinity:gauntlet_dominion" -> GauntletAbilities.dominion(p, alt);
            case "infinity:gauntlet_eye" -> GauntletAbilities.eye(p);
            case "infinity:gauntlet_stop" -> GauntletAbilities.absoluteStop(p);
            case "infinity:gauntlet_transmute" -> GauntletAbilities.transmute(p, alt);
            case "infinity:gauntlet_undo_death" -> UndoDeath.press(p);
            case "infinity:soul_possession" -> alt ? com.infinitystones.tensura.soul.Possession.openPlayerMenu(p) : com.infinitystones.tensura.soul.Possession.possessAimedMob(p);
            case "infinity:soul_capture" -> com.infinitystones.tensura.soul.SoulJar.press(p, alt);
            default -> null;
        };
    }

    public static boolean start(ServerPlayer p, StoneTree.Node n) {
        if ("infinity:gauntlet_destroy".equals(n.originalType())) { DestroyStones.start(p); return true; } Ability a = Abilities.get(n.originalType()); if (a == null) return false; a.start(p); return true; }
    public static boolean tick(ServerPlayer p, StoneTree.Node n) {
        if ("infinity:gauntlet_destroy".equals(n.originalType())) { DestroyStones.tick(p); return true; } Ability a = Abilities.get(n.originalType()); if (a == null) return false; a.tick(p); return true; }
    public static boolean stop(ServerPlayer p, StoneTree.Node n) {
        if ("infinity:gauntlet_destroy".equals(n.originalType())) { DestroyStones.stop(p); return true; } Ability a = Abilities.get(n.originalType()); if (a == null) return false; a.stop(p); return true; }
    private ExtraActions() {}
}
