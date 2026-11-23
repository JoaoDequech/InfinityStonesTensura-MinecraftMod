package com.infinitystones.tensura.skill.ability.extra;

import com.infinitystones.tensura.InfinityStones;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Timed server effects (zones, projectiles, animations). A task returns false from tick() when finished; end() runs
 * once afterwards (also on server stop, so nothing is left behind). Safe to add tasks from inside a tick.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class Tasks {
    public interface Task {
        /** @param age ticks since start (0 on the first call). @return false when done. */
        boolean tick(int age);
        default void end() {}
    }

    private static final class Run { final Task t; int age; boolean dead; Run(Task t) { this.t = t; } }

    private static final List<Run> RUNS = new ArrayList<>();
    private static final List<Run> ADD = new ArrayList<>();

    /** Starts a task; returns a handle that can cancel it. */
    public static Handle add(Task t) {
        Run r = new Run(t);
        ADD.add(r);
        return new Handle(r);
    }

    public static final class Handle {
        private final Run r;
        private Handle(Run r) { this.r = r; }
        public void cancel() { r.dead = true; }
        public boolean alive() { return !r.dead; }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        if (!ADD.isEmpty()) { RUNS.addAll(ADD); ADD.clear(); }
        for (int i = 0; i < RUNS.size(); i++) {
            Run r = RUNS.get(i);
            boolean alive = !r.dead;
            if (alive) {
                try { alive = r.t.tick(r.age++); }
                catch (Exception ex) { InfinityStones.LOGGER.error("[extra] task failed", ex); alive = false; }
            }
            if (!alive) {
                r.dead = true;
                try { r.t.end(); } catch (Exception ex) { InfinityStones.LOGGER.error("[extra] task end failed", ex); }
            }
        }
        RUNS.removeIf(r -> r.dead);
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) {
        RUNS.addAll(ADD); ADD.clear();
        for (Run r : RUNS) if (!r.dead) { r.dead = true; try { r.t.end(); } catch (Exception ignored) { } }
        RUNS.clear();
    }

    private Tasks() {}
}
