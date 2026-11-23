package com.infinitystones.tensura.skill.ability.ported;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import com.infinitystones.tensura.InfinityStones;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side vanish used by Reality Invisibility: while a player is in the set, no other client is ever told he
 * exists (ChunkMap tracking is refused by GhostTrackingMixin), so rendering, radar/minimap entity lists and every
 * client-side sensing skill have nothing to show. The mixins also read it to silence his sounds and to stop Open Parties
 * and Claims from sharing his position with the party.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class Vanish {
    private static final Set<UUID> VANISHED = ConcurrentHashMap.newKeySet();

    /** Implemented on ChunkMap by a mixin. */
    public interface Refresh { void infinity$refresh(ServerPlayer player); }

    /** Implemented on ChunkMap$TrackedEntity by a mixin. */
    public interface Updater { void infinity$updateAll(List<ServerPlayer> viewers); }

    public static boolean is(Entity e) { return e instanceof Player && VANISHED.contains(e.getUUID()); }

    public static boolean isId(UUID id) { return id != null && VANISHED.contains(id); }

    /** Vanishes / reveals the player and re-pairs him with every viewer right away. */
    public static void set(ServerPlayer p, boolean on) {
        boolean changed = on ? VANISHED.add(p.getUUID()) : VANISHED.remove(p.getUUID());
        if (changed && p.serverLevel().getChunkSource().chunkMap instanceof Refresh r) r.infinity$refresh(p);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { VANISHED.remove(e.getEntity().getUUID()); }

    private Vanish() {}
}
