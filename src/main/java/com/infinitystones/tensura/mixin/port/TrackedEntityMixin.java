package com.infinitystones.tensura.mixin.port;

import com.infinitystones.tensura.skill.ability.ported.Vanish;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;

/** Exposes ChunkMap.TrackedEntity#updatePlayers (package-private class) to {@link ChunkMapMixin}. */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityMixin implements Vanish.Updater {
    @Shadow public abstract void updatePlayers(List<ServerPlayer> viewers);

    @Override
    public void infinity$updateAll(List<ServerPlayer> viewers) { updatePlayers(viewers); }
}
