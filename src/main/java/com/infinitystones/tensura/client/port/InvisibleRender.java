package com.infinitystones.tensura.client.port;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.ported.VisualSync;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * Reality Invisibility: a player with the {@link VisualSync#INVISIBLE} flag is not rendered at all, so armour, held
 * items, Curios/Accessories, the name tag, the shadow and every mod layer (gauntlet, radiation...) disappear with the body.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class InvisibleRender {
    public static boolean hidden(Entity e) {
        return e instanceof Player && !VisualSync.CLIENT.isEmpty() && (VisualSync.CLIENT.getOrDefault(e.getId(), 0) & VisualSync.INVISIBLE) != 0;
    }

    @SubscribeEvent
    public static void player(RenderPlayerEvent.Pre e) {
        if (hidden(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void living(RenderLivingEvent.Pre<?, ?> e) {
        if (hidden(e.getEntity())) e.setCanceled(true);
    }

    private InvisibleRender() {}
}
