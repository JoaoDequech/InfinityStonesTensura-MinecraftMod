package com.infinitystones.tensura.shard.client;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.shard.ShardItem;
import com.infinitystones.tensura.shard.ShardRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;

/**
 * Item model properties "infinity:decay" (0 fresh .. 1 about to vanish) and "infinity:final" (0 until the last 10 seconds,
 * then up to 1): the model gains cracks as the gem runs out and they spread live during the countdown. Also the slot
 * decoration: pulsing red flash and the seconds left, in the hotbar and in every inventory screen.
 */
public final class ShardClientSetup {
    @EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
    public static final class Game {
        @SubscribeEvent
        public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { com.infinitystones.tensura.shard.ShardClientState.clear(); }
    }

    @EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Mod {
        @SubscribeEvent
        public static void setup(FMLClientSetupEvent event) { ShardClientSetup.setup(event); }

        @SubscribeEvent
        public static void decorations(RegisterItemDecorationsEvent event) {
            event.register(ShardRegistries.MINI_JOIA.get(), ShardClientSetup::decorate);
        }
    }

    static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(ShardRegistries.MINI_JOIA.get(), InfinityStones.id("decay"), (stack, level, entity, seed) -> {
                Level l = level != null ? level : Minecraft.getInstance().level;
                return l == null ? 0F : ShardItem.decay(stack, l.getGameTime());
            });
            ItemProperties.register(ShardRegistries.MINI_JOIA.get(), InfinityStones.id("final"), (stack, level, entity, seed) -> {
                Level l = level != null ? level : Minecraft.getInstance().level;
                return l == null ? 0F : ShardItem.finalFrac(stack, l.getGameTime());
            });
        });
    }

    /** Last 10 seconds: red pulse over the slot (faster as it nears 0) and the seconds left, ticking bigger each second. */
    private static boolean decorate(GuiGraphics g, Font font, ItemStack stack, int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        long time = mc.level.getGameTime();
        int sec = ShardItem.finalSeconds(stack, time);
        if (sec <= 0) return false;
        float f = ShardItem.finalFrac(stack, time);
        com.infinitystones.tensura.shard.ShardData d = stack.get(ShardRegistries.SHARD.get());
        long left = d == null ? 0 : d.expiry() - time;
        float hz = 2.5F + f * 9F;
        float flash = 0.5F + 0.5F * Mth.sin(System.currentTimeMillis() / 1000F * Mth.TWO_PI * hz);
        g.pose().pushPose();
        g.pose().translate(0, 0, 190);
        g.fill(x, y, x + 16, y + 16, ShardGfx.alpha(0xFF2A2A, (0.08F + 0.34F * flash * (0.5F + f * 0.5F)) * ShardShaders.gui()));
        float pulse = (left % 20) / 20F;
        float scale = 0.85F + 0.55F * pulse * pulse * pulse;
        float shake = f * 0.9F;
        float ox = Mth.sin(System.nanoTime() / 2.0E7F) * shake, oy = Mth.cos(System.nanoTime() / 1.7E7F) * shake;
        String s = String.valueOf(sec);
        int col = ShardGfx.mix(0xFFFFFF, 0xFF3030, f) | 0xFF000000;
        g.pose().translate(x + 8 + ox, y + 3 + oy, 0);
        g.pose().scale(scale, scale, 1);
        int w = font.width(s);
        g.drawString(font, s, -w / 2, -3, col, true);
        g.pose().popPose();
        return false;
    }

    private ShardClientSetup() {}
}
