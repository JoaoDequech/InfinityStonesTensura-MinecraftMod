package com.infinitystones.tensura.client.port;

import com.infinitystones.tensura.InfinityStones;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * Time Stone green screen (original palladium:gui_overlay "gui": textures/gui/time.png stretched over the whole
 * screen while a Time power runs). The server sets the flag and honours the tag no_time_screen.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class TimeScreen {
    private static final ResourceLocation TEXTURE = InfinityStones.id("textures/gui/time.png");

    @SubscribeEvent
    public static void layers(RegisterGuiLayersEvent e) {
        e.registerAboveAll(InfinityStones.id("time_screen"), (graphics, delta) -> {
            if (!PortVisuals.timeScreen()) return;
            RenderSystem.enableBlend();
            graphics.blit(TEXTURE, 0, 0, graphics.guiWidth(), graphics.guiHeight(), 0, 0, 16, 16, 16, 16);
            RenderSystem.disableBlend();
        });
    }

    private TimeScreen() {}
}
