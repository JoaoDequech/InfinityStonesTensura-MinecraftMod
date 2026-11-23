package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Timed channels: original abilities enabled by {@code palladium:activation} (press once → active for N ticks →
 * cooldown). Runs start/tick/stop on the server independent of the wheel key. Stops early if the caster dies,
 * logs out, changes dimension or stops carrying the stone.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class Sessions {
    private static final class Session {
        final StoneTree.Node node; final Ported ability; final int duration; int age;
        Session(StoneTree.Node node, Ported ability, int duration) { this.node = node; this.ability = ability; this.duration = duration; }
    }
    private static final Map<UUID, List<Session>> RUNNING = new HashMap<>();

    public static boolean isRunning(ServerPlayer p, StoneTree.Node n) {
        for (Session s : RUNNING.getOrDefault(p.getUUID(), List.of())) if (s.node.modeId().equals(n.modeId())) return true;
        return false;
    }

    /** Starts (or ignores if already running) a timed session. */
    public static void begin(ServerPlayer p, StoneTree.Node n, Ported a, int duration) {
        if (isRunning(p, n)) return;
        RUNNING.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(new Session(n, a, duration));
        a.start(p, n);
    }

    /** Ends a running session early (stop() runs). */
    public static void end(ServerPlayer p, StoneTree.Node n) {
        List<Session> list = RUNNING.get(p.getUUID());
        if (list == null) return;
        for (Iterator<Session> it = list.iterator(); it.hasNext(); ) {
            Session s = it.next();
            if (s.node.modeId().equals(n.modeId())) { it.remove(); s.ability.stop(p, s.node); }
        }
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        List<Session> list = RUNNING.get(p.getUUID());
        if (list == null || list.isEmpty()) return;
        for (Session s : List.copyOf(list)) {
            boolean alive = p.isAlive() && s.node.stone().isHeldBy(p);
            if (alive && s.age < s.duration) {
                s.ability.tick(p, s.node, s.age);
                s.age++;
            }
            if (!alive || s.age >= s.duration) {
                list.remove(s);
                s.ability.stop(p, s.node);
            }
        }
    }

    private static void stopAll(ServerPlayer p) {
        List<Session> list = RUNNING.remove(p.getUUID());
        if (list != null) for (Session s : list) s.ability.stop(p, s.node);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if (e.getEntity() instanceof ServerPlayer p) stopAll(p); }

    @SubscribeEvent
    public static void changeDim(PlayerEvent.PlayerChangedDimensionEvent e) { if (e.getEntity() instanceof ServerPlayer p) stopAll(p); }

    private Sessions() {}
}
