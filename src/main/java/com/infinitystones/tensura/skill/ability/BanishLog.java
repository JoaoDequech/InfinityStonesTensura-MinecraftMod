package com.infinitystones.tensura.skill.ability;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Bring it back": every banishment is remembered per caster (most recent first, up to 8) with where the target came
 * from, so the caster can undo it from the destination menu. Only the caster who banished can bring the target back.
 * Kept in memory for the session (a server restart forgets the list, the banished stay where they are).
 */
public final class BanishLog {
    public record Entry(UUID target, String name, ResourceKey<Level> fromDim, Vec3 from, ResourceKey<Level> toDim, Vec3 to) {}

    private static final int MAX = 8;
    private static final Map<UUID, Deque<Entry>> LOG = new HashMap<>();

    public static void record(ServerPlayer caster, Entity target, ResourceKey<Level> fromDim, Vec3 from, Entity moved) {
        if (target == caster) return;
        Deque<Entry> d = LOG.computeIfAbsent(caster.getUUID(), k -> new ArrayDeque<>());
        d.removeIf(e -> e.target().equals(moved.getUUID()));
        d.addFirst(new Entry(moved.getUUID(), target.getDisplayName().getString(), fromDim, from, moved.level().dimension(), moved.position()));
        while (d.size() > MAX) d.removeLast();
    }

    public static List<Entry> list(ServerPlayer caster) { return new ArrayList<>(LOG.getOrDefault(caster.getUUID(), new ArrayDeque<>())); }

    public enum Result { OK, NOT_LOADED, GONE, NO_SPOT }

    /** Brings entry #index back to where it was banished from. */
    public static Result bringBack(ServerPlayer caster, int index) {
        Deque<Entry> d = LOG.get(caster.getUUID());
        if (d == null || index < 0 || index >= d.size()) return Result.GONE;
        Entry e = new ArrayList<>(d).get(index);
        MinecraftServer server = caster.server;
        Entity target = find(server, e);
        if (target == null) {
            ServerLevel to = server.getLevel(e.toDim());
            if (to == null) { d.remove(e); return Result.GONE; }
            // load the landing chunk for ~15 s so its entities stream in; the next click finds the target
            to.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(net.minecraft.core.BlockPos.containing(e.to())), 2,
                    net.minecraft.core.BlockPos.containing(e.to()));
            return Result.NOT_LOADED;
        }
        if (!target.isAlive()) { d.remove(e); return Result.GONE; }
        ServerLevel home = server.getLevel(e.fromDim());
        if (home == null) { d.remove(e); return Result.GONE; }
        Vec3 spot = Relocation.at(home, target, e.from().x, e.from().y, e.from().z);
        if (spot == null) return Result.NO_SPOT;
        if (Relocation.move(target, home, spot, 1.3F) == null) return Result.NO_SPOT;
        d.remove(e);
        return Result.OK;
    }

    private static @Nullable Entity find(MinecraftServer server, Entry e) {
        ServerPlayer p = server.getPlayerList().getPlayer(e.target());
        if (p != null) return p;
        ServerLevel to = server.getLevel(e.toDim());
        if (to != null) { Entity x = to.getEntity(e.target()); if (x != null) return x; }
        for (ServerLevel l : server.getAllLevels()) { Entity x = l.getEntity(e.target()); if (x != null) return x; }
        return null;
    }

    public static net.minecraft.network.chat.MutableComponent describe(Result r) {
        return Component.translatable("infinity.dest.back." + r.name().toLowerCase(java.util.Locale.ROOT));
    }

    public static void forget(UUID caster) { LOG.remove(caster); }

    private BanishLog() {}
}
