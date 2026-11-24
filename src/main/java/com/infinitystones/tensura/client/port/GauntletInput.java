package com.infinitystones.tensura.client.port;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.gauntlet.ArtifactSwap;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Shift + left click (looking up) takes the stone out of an artifact: the client only sees clicks in the air. */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class GauntletInput {
    @SubscribeEvent
    public static void leftAir(PlayerInteractEvent.LeftClickEmpty e) {
        if (ArtifactSwap.canExtractByLeftClick(e.getEntity())) PacketDistributor.sendToServer(new ArtifactSwap.Extract());
    }

    @SubscribeEvent
    public static void leftBlock(PlayerInteractEvent.LeftClickBlock e) {
        if (!e.getLevel().isClientSide || e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
        if (e.getEntity() != Minecraft.getInstance().player || !ArtifactSwap.canExtractByLeftClick(e.getEntity())) return;
        PacketDistributor.sendToServer(new ArtifactSwap.Extract());
        e.setCanceled(true);
    }

    private GauntletInput() {}
}
