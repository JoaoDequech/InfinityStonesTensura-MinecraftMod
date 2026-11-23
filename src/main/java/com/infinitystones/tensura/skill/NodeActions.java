package com.infinitystones.tensura.skill;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.infinitystones.tensura.skill.ability.ExtraActions;
import com.infinitystones.tensura.skill.ability.PortedActions;
import com.infinitystones.tensura.skill.ability.SpatialBanishment;

import java.util.Set;

/**
 * Hand-ported behaviour for original nodes that are not plain command replays (beams, aim, projectiles, the Java
 * extras, and the few command nodes that called Palladium-only commands). Each method returns true when it handled
 * the node; false falls back to replaying the node's original commands.
 */
final class NodeActions {
    /** Original types that only make sense with a hand port; until ported they report it instead of silently failing. */
    private static final Set<String> COMMAND_LIKE = Set.of("palladium:command", "palladium:dummy");

    enum Result { NOT_HANDLED, OK, FAILED }

    /** One-shot use. alt = the second variant (right click on the wheel, or Shift like the original). */
    static Result press(ServerPlayer p, StoneTree.Node n, boolean alt) {
        Boolean ported = PortedActions.press(p, n, alt);
        if (ported == null) ported = ExtraActions.press(p, n, alt);
        if (ported != null) return ported ? Result.OK : Result.FAILED;
        return switch (n.originalType()) {
            case "palladium:spatial_banishment" -> SpatialBanishment.cast(p, alt) ? Result.OK : Result.FAILED;
            // Will: left = grip mode on/off; right (alt) = construct menu, opened client-side — nothing to do here
            case "infinity:will" -> { if (!alt) com.infinitystones.tensura.will.WillMode.toggle(p); yield Result.OK; }
            default -> pending(p, n) ? Result.FAILED : Result.NOT_HANDLED;
        };
    }
    static boolean holdStart(ServerPlayer p, StoneTree.Node n) { return PortedActions.holdStart(p, n) || ExtraActions.holdStart(p, n) || pending(p, n); }
    static boolean holdTick(ServerPlayer p, StoneTree.Node n, int ticks) { return PortedActions.holdTick(p, n, ticks) || ExtraActions.holdTick(p, n, ticks) || !COMMAND_LIKE.contains(n.originalType()); }
    static boolean holdEnd(ServerPlayer p, StoneTree.Node n) { return PortedActions.holdEnd(p, n) || ExtraActions.holdEnd(p, n) || !COMMAND_LIKE.contains(n.originalType()); }
    static boolean start(ServerPlayer p, StoneTree.Node n) { return PortedActions.start(p, n) || ExtraActions.start(p, n) || !COMMAND_LIKE.contains(n.originalType()); }
    static boolean tick(ServerPlayer p, StoneTree.Node n) { return PortedActions.tick(p, n) || ExtraActions.tick(p, n) || !COMMAND_LIKE.contains(n.originalType()); }
    static boolean stop(ServerPlayer p, StoneTree.Node n) { return PortedActions.stop(p, n) || ExtraActions.stop(p, n) || !COMMAND_LIKE.contains(n.originalType()); }

    private static boolean pending(ServerPlayer p, StoneTree.Node n) {
        if (COMMAND_LIKE.contains(n.originalType())) return false;
        p.displayClientMessage(n.displayName().append(Component.literal(" — port in progress (" + n.originalType() + ")")
                .withStyle(ChatFormatting.DARK_GRAY)), true);
        return true;
    }

    private NodeActions() {}
}
