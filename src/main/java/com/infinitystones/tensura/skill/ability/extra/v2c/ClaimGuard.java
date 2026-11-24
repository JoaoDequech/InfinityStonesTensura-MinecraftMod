package com.infinitystones.tensura.skill.ability.extra.v2c;

import com.infinitystones.tensura.compat.ClaimsCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/** Open Parties and Claims check for permanent block/biome changes: denied only inside a claim somebody else owns. */
final class ClaimGuard {
    static boolean canModify(ServerPlayer p, ServerLevel level, ChunkPos cp) {
        return ClaimsCompat.canModify(p, level, cp.getMiddleBlockPosition(64));
    }

    private ClaimGuard() {}
}
