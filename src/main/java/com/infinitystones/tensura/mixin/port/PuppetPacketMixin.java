package com.infinitystones.tensura.mixin.port;

import com.infinitystones.tensura.soul.Possession;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Soul Possession: while a player is taken over, the server ignores every gameplay packet of their own client
 * (the possessor's input drives them instead). The possessor's ghost body sends no gameplay either.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PuppetPacketMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = {"handleMovePlayer", "handlePlayerAction", "handleUseItemOn", "handleUseItem", "handleInteract",
            "handleSetCarriedItem", "handlePlayerCommand", "handleAnimate", "handlePlayerInput", "handlePickItem",
            "handleSetCreativeModeSlot", "handlePlaceRecipe"}, at = @At("HEAD"), cancellable = true)
    private void infinity$blockPossessed(CallbackInfo ci) {
        if (player != null && (Possession.isPuppet(player) || Possession.isGhost(player))) ci.cancel();
    }

    @Inject(method = {"handleContainerClick", "handleContainerButtonClick"}, at = @At("HEAD"), cancellable = true)
    private void infinity$blockPuppetMenus(CallbackInfo ci) {
        if (player != null && Possession.isPuppet(player)) ci.cancel();
    }
}
