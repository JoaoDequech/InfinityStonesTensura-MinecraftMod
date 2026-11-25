package com.infinitystones.tensura.skill.ability.extra.fear.client;

import com.infinitystones.tensura.skill.ability.extra.fear.FearRealm;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Real dynamic light of the victim's flashlight through Veil. Only ever loaded when Veil is installed. */
final class FearVeilLights {
    private static LightRenderHandle<PointLightData> flash;

    static void update(float pt) {
        Minecraft mc = Minecraft.getInstance();
        boolean want = mc.level != null && mc.player != null && mc.level.dimension() == FearRealm.DIM
                && (FearState.victim || FearState.watchMode == 1 || FearState.watchMode == 3) && FearHud.lightLevel() > 0.05F && !FearCompat.shaders();
        if (!want) { free(); return; }
        if (flash == null || !flash.isValid()) {
            flash = VeilRenderSystem.renderer().getLightRenderer().addLight(new PointLightData().setRadius(16F).setColor(1F, 0.92F, 0.75F).setBrightness(0.8F));
        }
        var cam = mc.gameRenderer.getMainCamera();
        Vec3 look = Vec3.directionFromRotation(cam.getXRot(), cam.getYRot());
        Vec3 at = cam.getPosition().add(look.scale(3.5));
        float l = FearHud.lightLevel();
        PointLightData d = flash.getLightData();
        d.setPosition(at.x, at.y, at.z);
        d.setBrightness(0.85F * l);
        flash.markDirty();
    }

    static void free() {
        if (flash != null) { if (flash.isValid()) flash.free(); flash = null; }
    }

    private FearVeilLights() {}
}
