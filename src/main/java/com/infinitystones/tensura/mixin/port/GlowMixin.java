package com.infinitystones.tensura.mixin.port;

import com.infinitystones.tensura.client.port.GlowView;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Soul Glow: the local player sees every living entity around outlined, nobody else does. Client only. */
@Mixin(Entity.class)
public abstract class GlowMixin {
    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void infinity$glow(CallbackInfoReturnable<Boolean> cir) {
        Entity self = (Entity) (Object) this;
        if (self.level().isClientSide && GlowView.outlined(self)) cir.setReturnValue(true);
    }
}
