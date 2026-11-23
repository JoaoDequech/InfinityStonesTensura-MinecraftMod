package com.infinitystones.tensura.mixin.port;

import com.infinitystones.tensura.skill.ability.ported.Vanish;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanish: the server does not broadcast the vanished player's own sounds (footsteps, splashes, eating...). */
@Mixin(Player.class)
public abstract class PlayerSoundMixin {
    @Inject(method = "playSound(Lnet/minecraft/sounds/SoundEvent;FF)V", at = @At("HEAD"), cancellable = true)
    private void infinity$silent(SoundEvent sound, float volume, float pitch, CallbackInfo ci) {
        Player self = (Player) (Object) this;
        if (!self.level().isClientSide && Vanish.is(self)) ci.cancel();
    }
}
