package com.infinitystones.tensura.mixin.port;

import com.infinitystones.tensura.skill.ability.ported.Vanish;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Vanish: lets the server re-run the tracking check for one player's entity the moment he vanishes / reappears. */
@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin implements Vanish.Refresh {
    @Shadow @Final private Int2ObjectMap<?> entityMap;
    @Shadow @Final ServerLevel level;

    @Override
    public void infinity$refresh(ServerPlayer player) {
        Object tracked = entityMap.get(player.getId());
        if (tracked instanceof Vanish.Updater u) u.infinity$updateAll(level.players());
    }
}
