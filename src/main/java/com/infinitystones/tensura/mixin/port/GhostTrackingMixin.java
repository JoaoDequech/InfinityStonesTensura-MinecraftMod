package com.infinitystones.tensura.mixin.port;

import com.infinitystones.tensura.soul.Possession;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Soul Possession: the possessor's ghost body is never sent to other clients (undetectable). */
@Mixin(ServerPlayer.class)
public abstract class GhostTrackingMixin {
    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void infinity$hideGhost(ServerPlayer viewer, CallbackInfoReturnable<Boolean> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (viewer != self && (Possession.isGhost(self) || com.infinitystones.tensura.skill.ability.ported.Vanish.is(self)))
            cir.setReturnValue(false);
    }
}
