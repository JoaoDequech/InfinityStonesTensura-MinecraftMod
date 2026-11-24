package com.infinitystones.tensura.skill.ability.extra.v2a;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server side of the Creator's Hand lock: a player held in the fist can do nothing (no attacks, no interaction, no
 * blocks, no drops, no containers); the client side (camera shot, input) is driven by {@link V2aNet.Lock}.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
final class HandLock {
    static void send(ServerPlayer sp, int mode, int id, float shotYaw, float dist, float scale) {
        PacketDistributor.sendToPlayer(sp, new V2aNet.Lock(mode, id, shotYaw, dist, scale));
    }

    static void free(ServerPlayer sp) {
        if (sp.hasDisconnected()) return;
        send(sp, 0, 0, 0, 0, 1);
    }

    /** Every tick for each held player. */
    static void enforce(ServerPlayer sp) {
        if (sp.containerMenu != sp.inventoryMenu) sp.closeContainer();
        if (sp.isUsingItem()) sp.stopUsingItem();
        sp.setSprinting(false);
    }

    private static boolean held(net.minecraft.world.entity.Entity e) { return e instanceof ServerPlayer && CreatorHand.isHeld(e); }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) free(sp);
    }

    /** A new body or a new world never inherits the lock screen (the client resets only on its own logout). */
    @SubscribeEvent
    public static void respawned(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && !held(sp)) free(sp);
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && !held(sp)) free(sp);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attacks(LivingIncomingDamageEvent e) {
        if (held(e.getSource().getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(AttackEntityEvent e) { if (held(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void useItem(PlayerInteractEvent.RightClickItem e) { if (held(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void useBlock(PlayerInteractEvent.RightClickBlock e) { if (held(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hitBlock(PlayerInteractEvent.LeftClickBlock e) { if (held(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void useEntity(PlayerInteractEvent.EntityInteract e) { if (held(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void useEntityAt(PlayerInteractEvent.EntityInteractSpecific e) { if (held(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void breakBlock(BlockEvent.BreakEvent e) { if (held(e.getPlayer())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void placeBlock(BlockEvent.EntityPlaceEvent e) { if (held(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void toss(ItemTossEvent e) { if (held(e.getPlayer())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void startUse(LivingEntityUseItemEvent.Start e) { if (held(e.getEntity())) e.setCanceled(true); }

    private HandLock() {}
}
