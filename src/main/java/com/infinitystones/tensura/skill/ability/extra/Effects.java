package com.infinitystones.tensura.skill.ability.extra;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Timed world effects of the new abilities (zones, portals, orbs, beacons). Ticked once per server tick, in memory
 * only (a restart clears them — they are temporary by design). Each effect checks only its own small box.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class Effects {
    public abstract static class Effect {
        public final ServerLevel level;
        public final UUID owner;
        public int age;
        public int duration;
        boolean done;

        protected Effect(ServerLevel level, UUID owner, int duration) {
            this.level = level; this.owner = owner; this.duration = duration;
        }

        /** Called every tick while alive (age starts at 0). */
        protected abstract void tick();
        protected void onEnd() {}
        public void kill() { done = true; }
        public boolean alive() { return !done; }

        public @Nullable ServerPlayer ownerPlayer() { return level.getServer().getPlayerList().getPlayer(owner); }
    }

    /** A sphere zone helper. */
    public abstract static class Zone extends Effect {
        public Vec3 center;
        public double radius;

        protected Zone(ServerLevel level, UUID owner, int duration, Vec3 center, double radius) {
            super(level, owner, duration); this.center = center; this.radius = radius;
        }

        public boolean contains(Vec3 v) { return v.distanceToSqr(center) <= radius * radius; }
        public AABB box() { return new AABB(center, center).inflate(radius); }
    }

    private static final List<Effect> ALL = new ArrayList<>();

    public static <T extends Effect> T add(T e) { ALL.add(e); return e; }

    public static <T extends Effect> List<T> all(Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Effect e : ALL) if (!e.done && type.isInstance(e)) out.add(type.cast(e));
        return out;
    }

    public static <T extends Effect> @Nullable T ofOwner(Class<T> type, UUID owner) {
        for (Effect e : ALL) if (!e.done && type.isInstance(e) && e.owner.equals(owner)) return type.cast(e);
        return null;
    }

    public static void killOwned(Class<? extends Effect> type, UUID owner) {
        for (Effect e : ALL) if (type.isInstance(e) && e.owner.equals(owner)) e.done = true;
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        if (ALL.isEmpty()) return;
        List<Effect> snapshot = new ArrayList<>(ALL);
        for (Effect e : snapshot) {
            if (!e.done) {
                try { e.tick(); }
                catch (Exception ex) { InfinityStones.LOGGER.error("[extra] effect failed", ex); e.done = true; }
                e.age++;
                if (e.duration > 0 && e.age >= e.duration) e.done = true;
            }
            if (e.done) {
                ALL.remove(e);
                try { e.onEnd(); } catch (Exception ex) { InfinityStones.LOGGER.error("[extra] effect end failed", ex); }
            }
        }
    }

    /** Undo lasting side effects (frozen mobs, fake blocks…) before the world is saved. */
    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        for (Effect e : new ArrayList<>(ALL)) { try { e.onEnd(); } catch (Exception ex) { InfinityStones.LOGGER.error("[extra] effect end failed", ex); } }
        ALL.clear();
    }

    private Effects() {}
}
