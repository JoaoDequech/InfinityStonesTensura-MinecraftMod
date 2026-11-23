package com.infinitystones.tensura.mixin.port;

import com.infinitystones.tensura.client.port.PortVisuals;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Power Stone T-pose (original KubeJS animation "tposeactivation", driven by the 0..8 t-pose timer that runs while an
 * explosion ability is on): arms out and forward, legs apart, eased with easeOutBack. Third person only.
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void infinity$tpose(LivingEntity entity, float limbSwing, float limbSwingAmount, float age, float yaw, float pitch, CallbackInfo ci) {
        if (!(entity instanceof Player)) return;
        Minecraft mc = Minecraft.getInstance();
        if (entity == mc.player && mc.options.getCameraType().isFirstPerson()) return;
        float t = PortVisuals.tpose(entity, mc.getTimer().getGameTimeDeltaPartialTick(false));
        if (t <= 0F) return;
        float c1 = 1.70158F, c3 = c1 + 1F, x = t - 1F;
        float a = 1F + c3 * x * x * x + c1 * x * x;
        HumanoidModel<?> m = (HumanoidModel<?>) (Object) this;
        pose(m.rightArm, a, -45F, 0F, 50F, -4F, 3F, 1F);
        pose(m.leftArm, a, -45F, 0F, -50F, 4F, 3F, 1F);
        pose(m.leftLeg, a, -10F, -45F, 0F, m.leftLeg.x, m.leftLeg.y, m.leftLeg.z);
        pose(m.rightLeg, a, -10F, 45F, 0F, m.rightLeg.x, m.rightLeg.y, m.rightLeg.z);
    }

    private static void pose(ModelPart p, float a, float xDeg, float yDeg, float zDeg, float px, float py, float pz) {
        p.xRot = Mth.lerp(a, p.xRot, xDeg * Mth.DEG_TO_RAD);
        p.yRot = Mth.lerp(a, p.yRot, yDeg * Mth.DEG_TO_RAD);
        p.zRot = Mth.lerp(a, p.zRot, zDeg * Mth.DEG_TO_RAD);
        p.x = Mth.lerp(a, p.x, px);
        p.y = Mth.lerp(a, p.y, py);
        p.z = Mth.lerp(a, p.z, pz);
    }
}
