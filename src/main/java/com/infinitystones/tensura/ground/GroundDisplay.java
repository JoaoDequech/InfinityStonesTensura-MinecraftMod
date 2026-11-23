package com.infinitystones.tensura.ground;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Stones/containers lying on the ground are invisible armor stands (original datapack design). The datapack now
 * stores the displayed item in the stand's chest slot; this class guarantees players can never take it out.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class GroundDisplay {
    public static final TagKey<Item> DISPLAYABLE = TagKey.create(Registries.ITEM, InfinityStones.id("ground_display"));

    public static boolean isDisplay(ArmorStand stand) {
        ItemStack chest = stand.getItemBySlot(EquipmentSlot.CHEST);
        return stand.isInvisible() && !chest.isEmpty() && chest.is(DISPLAYABLE);
    }

    @SubscribeEvent
    public static void noTaking(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getTarget() instanceof ArmorStand stand && isDisplay(stand)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private GroundDisplay() {}
}
