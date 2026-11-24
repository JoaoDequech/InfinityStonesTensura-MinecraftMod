package com.infinitystones.tensura.mixin.v2a;

import com.infinitystones.tensura.skill.ability.extra.v2a.client.HandClient;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Creator's Hand: the cutscene shot replaces the camera of the player held in the fist. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow protected abstract void setPosition(double x, double y, double z);

    @Shadow protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void infinity$handShot(BlockGetter level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
        double[] shot = HandClient.cameraShot(partialTick);
        if (shot == null) return;
        setPosition(shot[0], shot[1], shot[2]);
        setRotation((float) shot[3], (float) shot[4]);
    }
}
