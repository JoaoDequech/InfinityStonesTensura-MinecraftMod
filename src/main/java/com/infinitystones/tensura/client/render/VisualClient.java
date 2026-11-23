package com.infinitystones.tensura.client.render;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.registry.ModItems;
import com.infinitystones.tensura.skill.Stone;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import java.util.function.Supplier;

/** Mod-bus wiring for the relic visuals: standalone models, custom item renderers and the worn-gauntlet layer. */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VisualClient {
    @SubscribeEvent
    public static void models(ModelEvent.RegisterAdditional event) {
        for (Stone s : Stone.values()) {
            event.register(GlowFx.model("item/gem/" + s.key));
            event.register(GlowFx.model("item/gauntlet/gem_" + s.key));
        }
        event.register(GlowFx.model("item/gauntlet/base"));
        event.register(GlowFx.model("item/base/eye_of_agamotto"));
        for (String carrier : VisualLayout.CARRIERS.keySet()) event.register(GlowFx.model("item/base/" + carrier));
    }

    @SubscribeEvent
    public static void itemRenderers(RegisterClientExtensionsEvent event) {
        event.registerItem(extension(GemItemRenderer::new),
                ModItems.MIND_STONE.get(), ModItems.POWER_STONE.get(), ModItems.SPACE_STONE.get(),
                ModItems.REALITY_STONE.get(), ModItems.SOUL_STONE.get(), ModItems.TIME_STONE.get(),
                ModItems.SCEPTER.get(), ModItems.ORB.get(), ModItems.TESSERACT.get(), ModItems.AETHER.get(), ModItems.EYE_OF_AGAMOTTO.get(),
                ModItems.COSMI_ROD_MIND.get(), ModItems.COSMI_ROD_POWER.get(), ModItems.COSMI_ROD_SPACE.get(),
                ModItems.COSMI_ROD_REALITY.get(), ModItems.COSMI_ROD_SOUL.get(), ModItems.COSMI_ROD_TIME.get());
        event.registerItem(extension(GauntletRenderer::new), ModItems.INFINITY_GAUNTLET.get());
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            EntityRenderer<? extends Player> r = event.getSkin(skin);
            if (r instanceof PlayerRenderer pr) pr.addLayer(new GauntletLayer(pr));
        }
    }

    /** The renderer is created lazily: Minecraft's dispatchers don't exist yet while extensions are registered. */
    private static IClientItemExtensions extension(Supplier<BlockEntityWithoutLevelRenderer> factory) {
        return new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = factory.get();
                return renderer;
            }
        };
    }

    private VisualClient() {}
}
