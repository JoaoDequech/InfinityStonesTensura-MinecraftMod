package com.infinitystones.tensura.mixin.xaero;

import com.infinitystones.tensura.client.wheel.XaeroPick;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Xaero's World Map (optional): when the map was opened from the destination menu, a right click picks the clicked block
 * as the destination instead of opening Xaero's own menu (whose Teleport entry sends a /tp command that needs OP).
 */
@Pseudo
@Mixin(targets = "xaero.map.gui.GuiMap", remap = false)
public abstract class XaeroMapMixin {
    @Shadow private int rightClickX;
    @Shadow private int rightClickZ;
    @Shadow private ResourceKey<Level> rightClickDim;

    @Inject(method = "handleRightClick", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void infinity$pick(CallbackInfo ci) {
        if (!XaeroPick.active()) return;
        XaeroPick.picked(rightClickX, rightClickZ, rightClickDim);
        ci.cancel();
    }
}
