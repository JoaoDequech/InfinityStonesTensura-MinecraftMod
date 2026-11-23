package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Original "radiaton_timer" and "t-pose" helpers of the Power tree: both ran while either explosion ability
 * (explosion = bare Power Stone punishment, explosion2 = Cosmic Explosion) was enabled. The port keeps the cause set
 * on the server and tells clients through {@link VisualSync#RADIATION}; the client runs the timers (radiation 0..1000,
 * T-pose 0..8) and draws the layers.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class Radiation {
    private static final Map<UUID, Set<String>> CAUSES = new HashMap<>();

    public static void cause(ServerPlayer p, String cause, boolean on) {
        Set<String> set = CAUSES.computeIfAbsent(p.getUUID(), k -> new HashSet<>());
        if (on) set.add(cause); else set.remove(cause);
        VisualSync.set(p, VisualSync.RADIATION, !set.isEmpty());
        if (set.isEmpty()) CAUSES.remove(p.getUUID());
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { CAUSES.remove(e.getEntity().getUUID()); }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent e) { CAUSES.remove(e.getEntity().getUUID()); }

    private Radiation() {}
}
