package com.infinitystones.tensura.mixin.v2b;

import com.infinitystones.tensura.skill.ability.extra.v2b.BorrowedEyes;
import com.mojang.authlib.GameProfile;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Borrowed Eyes: every player but the caster keeps seeing the caster's real game mode in the tab list. */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class TabModeMixin {
    @Shadow protected abstract GameProfile playerProfile();

    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;)V", at = @At("HEAD"), argsOnly = true)
    private Packet<?> infinity$tabMode(Packet<?> packet) {
        if (packet instanceof ClientboundPlayerInfoUpdatePacket info) {
            GameProfile me = playerProfile();
            return BorrowedEyes.tabMask(info, me == null ? null : me.getId());
        }
        return packet;
    }
}
