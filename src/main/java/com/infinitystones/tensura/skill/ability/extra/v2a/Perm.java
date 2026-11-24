package com.infinitystones.tensura.skill.ability.extra.v2a;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * May this player change blocks here? Spawn protection / claims that hook mayInteract, and block-break protection
 * (Open Parties and Claims cancels BreakEvent). Claims work per chunk, so the answer is cached per chunk.
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
        boolean ok = l.getWorldBorder().isWithinBounds(pos) && l.mayInteract(p, pos);
        if (ok) {
            BlockState st = l.getBlockState(pos);
            if (st.isAir()) st = Blocks.STONE.defaultBlockState();
            ok = !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(l, pos, st, p)).isCanceled();
        }
        cache.put(key, ok);
        return ok;
    }
}
