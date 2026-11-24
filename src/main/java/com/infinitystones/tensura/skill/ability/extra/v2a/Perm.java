package com.infinitystones.tensura.skill.ability.extra.v2a;

import com.infinitystones.tensura.skill.ability.extra.Terrain;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;
import java.util.Map;

/**
 * May this player change blocks here? Direct claim lookup, spawn protection and world border (see {@link Terrain#may}); never
 * Player#mayInteract or BreakEvent, which other mods cancel. Claims work per chunk, so the answer is cached per chunk.
 */
final class Perm {
    private final ServerPlayer p;
    private final ServerLevel l;
    private final Map<Long, Boolean> cache = new HashMap<>();

    Perm(ServerPlayer p, ServerLevel l) { this.p = p; this.l = l; }

    boolean can(BlockPos pos) {
        long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        Boolean c = cache.get(key);
        if (c != null) return c;
        boolean ok = Terrain.may(p, l, pos);
        cache.put(key, ok);
        return ok;
    }
}
