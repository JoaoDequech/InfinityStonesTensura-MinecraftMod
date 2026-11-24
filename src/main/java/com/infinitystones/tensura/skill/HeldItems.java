package com.infinitystones.tensura.skill;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/** Searches everything a player carries (inventory, hands, armour, Curios) for a stack matching a test. */
public final class HeldItems {
    public static boolean any(Player player, Predicate<ItemStack> test) {
        for (ItemStack s : player.getInventory().items) if (!s.isEmpty() && test.test(s)) return true;
        for (ItemStack s : player.getInventory().offhand) if (!s.isEmpty() && test.test(s)) return true;
        for (ItemStack s : player.getInventory().armor) if (!s.isEmpty() && test.test(s)) return true;
        return CuriosCompat.has(player, test);
    }

    private HeldItems() {}
}
