package com.infinitystones.tensura.mixin.compat;

import com.infinitystones.tensura.skill.ability.ported.Vanish;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Open Parties and Claims shares each member's dimension and position with the party (and mutual allies) through this
 * object, which Xaero's maps draw. While the player is vanished the object is switched to its "inactive" form, which is
 * what OPAC sends to remove a member from the maps (the packet then carries no position), and the real position is
 * no longer copied into it. Revealing flips it back and forces a resend. Does nothing when OPAC is not installed.
 */
@Pseudo
@Mixin(targets = "xaero.pac.common.parties.party.PartyMemberDynamicInfoSyncable", remap = false)
public abstract class OpacDynamicInfoMixin {
    @Shadow(remap = false) @Final private UUID playerId;
    @Shadow(remap = false) private boolean dirty;
    @Shadow(remap = false) @Final @Mutable private boolean active;

    @Unique private boolean infinity$hidden;

    @Inject(method = "update(Lnet/minecraft/resources/ResourceLocation;DDD)V", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void infinity$update(ResourceLocation dimension, double x, double y, double z, CallbackInfo ci) {
        boolean vanished = Vanish.isId(playerId);
        if (vanished != infinity$hidden) {
            infinity$hidden = vanished;
            active = !vanished;
            dirty = true;
        }
        if (vanished) ci.cancel();
    }
}
