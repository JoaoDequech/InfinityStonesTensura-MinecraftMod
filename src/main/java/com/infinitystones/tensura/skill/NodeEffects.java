package com.infinitystones.tensura.skill;

import com.infinitystones.tensura.InfinityStones;
import io.github.manasmods.manascore.skill.api.SkillAPI;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runs original tree nodes. "palladium:command" nodes replay the original command lists with the original lifecycle:
 * first_tick_commands when the node turns on, commands every tick while on, last_tick_commands when it turns off.
 * Press = one tick, hold = channel (started/stopped from the wheel), toggle = per-node on/off, passive = while unlocked,
 * the stone is carried AND its wheel switch is on (every real passive is a switch, OFF by default — user decision).
 * Other original ability types are ported individually (see NodeActions); their stop() cleans up when switched off.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class NodeEffects {
    /** Per-player switches ("stone.node" → on). Copied to the new player on death/End exit (PlayerEvent.Clone). */
    private static final String TOGGLES = "infinity_toggles";
    /** Passive/toggle nodes currently running per player ("stone.node"). Rebuilt after login/respawn. */
    private static final Map<UUID, Set<String>> RUNNING = new HashMap<>();

    // ---------------- wheel entry points (validated by WheelService) ----------------

    /** @return false when the power fizzled (no target, etc.). */
    public static boolean use(ServerPlayer p, StoneTree.Node node, boolean alt) { return press(p, node, alt || p.isShiftKeyDown()); }
    public static void holdTick(ServerPlayer p, StoneTree.Node node, int ticks) { held(p, node, ticks); }
    public static void releaseHold(ServerPlayer p, StoneTree.Node node) { release(p, node); }

    // ---------------- triggered from StoneSkill ----------------

    static boolean press(ServerPlayer p, StoneTree.Node node, boolean alt) {
        switch (node.activation()) {
            case PRESS -> {
                NodeActions.Result r = NodeActions.press(p, node, alt);
                if (r == NodeActions.Result.NOT_HANDLED) { run(p, node.firstTick()); run(p, node.commands()); run(p, node.lastTick()); }
                return r != NodeActions.Result.FAILED;
            }
            case TOGGLE -> setToggle(p, node, !isToggled(p, node));
            case HOLD -> { if (!NodeActions.holdStart(p, node)) run(p, node.firstTick()); }
            case PASSIVE -> { if (node.isPassiveToggle()) setToggle(p, node, !isToggled(p, node)); }
        }
        return true;
    }

    static boolean held(ServerPlayer p, StoneTree.Node node, int ticks) {
        if (node.activation() != StoneTree.Activation.HOLD) return false;
        if (!NodeActions.holdTick(p, node, ticks)) run(p, node.commands());
        return true;
    }

    static void release(ServerPlayer p, StoneTree.Node node) {
        if (node.activation() == StoneTree.Activation.HOLD && !NodeActions.holdEnd(p, node)) run(p, node.lastTick());
    }

    static void onBought(ServerPlayer p, Stone stone, StoneTree.Node node) {
        if (stone == Stone.SOUL && "immortal".equals(node.id())) {
            // also learned as a Tensura skill (listed in the Skills menu); the switch itself is the wheel toggle, OFF by default
            SkillAPI.getSkillsFrom(p).learnSkill(InfinitySkills.IMMORTALITY.get());
        }
    }

    // ---------------- switches (persisted on the player) ----------------

    public static boolean isToggled(Player p, StoneTree.Node node) {
        if (StoneTree.HIDDEN_IN_WHEEL.contains(node.modeId()) || StoneTier.hidden(p, node)) return false;
        return p.getPersistentData().getCompound(TOGGLES).getBoolean(node.modeId());
    }

    /** Sets a toggle / passive switch and applies it right away (start or stop of the node's lifecycle). */
    public static void setToggle(ServerPlayer p, StoneTree.Node node, boolean on) {
        CompoundTag all = p.getPersistentData().getCompound(TOGGLES);
        all.putBoolean(node.modeId(), on);
        p.getPersistentData().put(TOGGLES, all);
        p.displayClientMessage(node.displayName().append(Component.literal(": "))
                .append(Component.translatable(on ? "infinity.wheel.state.on" : "infinity.wheel.state.off"))
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.GRAY), true);
        update(p);
    }

    // ---------------- passive + toggle upkeep ----------------

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        // nothing running: a switch that was just turned on is picked up within 5 ticks; setToggle() applies it at once
        Set<String> active = RUNNING.get(p.getUUID());
        if ((active == null || active.isEmpty()) && p.tickCount % 5 != 0) return;
        update(p);
    }

    private static void update(ServerPlayer p) {
        Set<String> running = RUNNING.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
        Stone[] stones = Stone.values();
        boolean[] learnedStone = new boolean[stones.length];
        boolean anyLearned = false;
        for (Stone stone : stones) anyLearned |= learnedStone[stone.ordinal()] = StoneProgress.instance(p, stone).isPresent();
        if (!anyLearned && running.isEmpty()) return;
        Set<Stone> held = StoneProgress.heldStones(p);
        for (Stone stone : stones) {
            boolean learned = learnedStone[stone.ordinal()];
            boolean carried = learned && held.contains(stone) && p.isAlive();
            for (StoneTree.Node node : StoneTree.of(stone).nodes.values()) {
                if (node.upper() && StoneTier.hidden(p, node)) clearSwitch(p, node);
                boolean wants = switch (node.activation()) {
                    case PASSIVE -> carried && (!node.isPassiveToggle() || isToggled(p, node))
                            && StoneProgress.isUnlocked(p, stone, node.id(), held);
                    case TOGGLE -> carried && isToggled(p, node) && StoneProgress.isUnlocked(p, stone, node.id(), held);
                    default -> false;
                };
                String key = node.modeId();
                boolean was = running.contains(key);
                if (wants && !was) { running.add(key); if (!NodeActions.start(p, node)) run(p, node.firstTick()); }
                if (wants) { if (!NodeActions.tick(p, node)) run(p, node.commands()); }
                if (!wants && was) { running.remove(key); if (!NodeActions.stop(p, node)) run(p, node.lastTick()); }
            }
        }
    }

    private static void clearSwitch(ServerPlayer p, StoneTree.Node node) {
        CompoundTag all = p.getPersistentData().getCompound(TOGGLES);
        if (!all.getBoolean(node.modeId())) return;
        all.putBoolean(node.modeId(), false);
        p.getPersistentData().put(TOGGLES, all);
    }

    /** Keep switches and ability settings across death / End exit; the new player entity re-runs every start. */
    @SubscribeEvent
    public static void clone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData();
        for (String k : new String[]{TOGGLES, com.infinitystones.tensura.wheel.AbilitySettings.TAG}) {
            if (old.contains(k)) event.getEntity().getPersistentData().put(k, old.getCompound(k).copy());
        }
        RUNNING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) { RUNNING.remove(event.getEntity().getUUID()); }

    // ---------------- volatile switches (see VolatileModes): off on logout, server stop and (defensively) login ----------------

    private static boolean quiet, loginCleanup;

    /** True while a volatile switch-off runs: ports skip their action-bar messages. */
    public static boolean quiet() { return quiet; }

    /** True while the login pass stops switches a crash left ON (their state may exist only in the saved player). */
    public static boolean loginCleanup() { return loginCleanup; }

    /**
     * Turns every volatile switch off with its normal stop(). With {@code leftovers} (login) a switch found stored as ON
     * without running is also stopped, so whatever a crash left behind (attributes, flags, effects) is cleaned up.
     */
    static void switchOffVolatile(ServerPlayer p, boolean leftovers) {
        Set<String> running = RUNNING.get(p.getUUID());
        CompoundTag all = p.getPersistentData().getCompound(TOGGLES);
        boolean before = quiet, beforeLogin = loginCleanup;
        quiet = true;
        loginCleanup = leftovers;
        try {
            for (Stone stone : Stone.values()) {
                for (StoneTree.Node node : StoneTree.of(stone).nodes.values()) {
                    if (!VolatileModes.is(node)) continue;
                    String key = node.modeId();
                    boolean stored = all.getBoolean(key);
                    boolean was = running != null && running.remove(key);
                    if (stored) all.putBoolean(key, false);
                    if (!was && !(leftovers && stored)) continue;
                    try {
                        if (!NodeActions.stop(p, node)) run(p, node.lastTick());
                    } catch (RuntimeException ignored) {
                        // a failing clean-up must never block a logout or the server stop
                    }
                }
            }
            if (!all.isEmpty()) p.getPersistentData().put(TOGGLES, all);
            try {
                com.infinitystones.tensura.skill.ability.ported.CommandPorts.resetLevels(p, leftovers);
            } catch (RuntimeException ignored) {
            }
        } finally {
            quiet = before;
            loginCleanup = beforeLogin;
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            com.infinitystones.tensura.wheel.WheelService.releaseChannel(p);
            switchOffVolatile(p, false);
        }
        RUNNING.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) switchOffVolatile(p, true);
    }

    @SubscribeEvent
    public static void stopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        for (ServerPlayer p : List.copyOf(event.getServer().getPlayerList().getPlayers())) {
            com.infinitystones.tensura.wheel.WheelService.releaseChannel(p);
            switchOffVolatile(p, false);
        }
    }

    // ---------------- command replay ----------------

    static void run(ServerPlayer p, List<String> commands) {
        if (commands.isEmpty() || p.getServer() == null) return;
        CommandSourceStack src = p.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        for (String c : commands) {
            if (c.isBlank()) continue;
            p.getServer().getCommands().performPrefixedCommand(src, c);
        }
    }

    private NodeEffects() {}
}
