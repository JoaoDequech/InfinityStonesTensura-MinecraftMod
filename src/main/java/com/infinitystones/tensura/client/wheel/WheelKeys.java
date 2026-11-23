package com.infinitystones.tensura.client.wheel;

import com.infinitystones.tensura.InfinityStones;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * The single Infinity key (default H — the only free letter in the OFA Holy Packs keymap).
 * Press = open the wheel (it stays open after the key is released; choosing a power closes it).
 * Quick tap = stop the channel in progress / leave aim mode / repeat the last power. H then 1..4 = favourites.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class WheelKeys {
    public static final KeyMapping WHEEL = new KeyMapping("key.infinity.wheel", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, "key.categories.infinity");

    @EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent event) { event.register(WHEEL); }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        while (WHEEL.consumeClick()) {
            if (mc.screen == null) {
                if (com.infinitystones.tensura.skill.ability.extra.space.client.GatherClient.onWheelKey(mc)) continue;
                mc.setScreen(new WheelScreen());
            }
        }
    }

    /** True while the wheel key is physically down (keyboard or mouse button). */
    static boolean isPhysicallyDown(Minecraft mc) {
        InputConstants.Key key = WHEEL.getKey();
        long w = mc.getWindow().getWindow();
        if (key.getType() == InputConstants.Type.KEYSYM) return InputConstants.isKeyDown(w, key.getValue());
        if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(w, key.getValue()) == GLFW.GLFW_PRESS;
        return false;
    }

    private WheelKeys() {}
}
