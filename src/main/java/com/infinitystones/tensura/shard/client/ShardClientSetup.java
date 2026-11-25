package com.infinitystones.tensura.shard.client;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.shard.ShardItem;
import com.infinitystones.tensura.shard.ShardRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Item model property "infinity:decay" (0 fresh .. 1 about to vanish): the model gains cracks as the gem runs out. */
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
    }

    static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(ShardRegistries.MINI_JOIA.get(), InfinityStones.id("decay"), (stack, level, entity, seed) -> {
            Level l = level != null ? level : Minecraft.getInstance().level;
            return l == null ? 0F : ShardItem.decay(stack, l.getGameTime());
        }));
    }

    private ShardClientSetup() {}
}
