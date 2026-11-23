package com.infinitystones.tensura.skill;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Stones set into an Infinity Gauntlet (original function of the mod: insert / remove each stone). The six sockets
 * live on the gauntlet stack itself, in the vanilla CONTAINER component (slot = {@link Stone#ordinal()}), so they are
 * saved with the item, travel with it (drop, chest, trade) and are synced to every client for rendering.
 * A stone in a gauntlet the player carries counts as carried ({@link Stone#isHeldBy}).
 * Gauntlet powers need a COMPLETE gauntlet (all six) worn: main hand, off hand or a Curios slot.
 */
public final class GauntletStones {
    public static final ResourceLocation GAUNTLET_ID = ResourceLocation.parse("infinity:infinity_gauntlet");
    private static Item gauntlet;
    private static final Item[] GEMS = new Item[Stone.values().length];

    public static Item gauntletItem() {
        if (gauntlet == null) gauntlet = BuiltInRegistries.ITEM.get(GAUNTLET_ID);
        return gauntlet;
    }

    public static boolean isGauntlet(ItemStack s) { return !s.isEmpty() && s.is(gauntletItem()); }

    /** The bare gem item of a stone (the only thing a socket accepts). */
    public static Item gem(Stone stone) {
        Item g = GEMS[stone.ordinal()];
        if (g == null) GEMS[stone.ordinal()] = g = BuiltInRegistries.ITEM.get(ResourceLocation.parse(stone.originalPower.equals("infinity:power_stone_passive") ? "infinity:power_stone" : stone.originalPower));
        return g;
    }

    public static boolean isGem(ItemStack s, Stone stone) { return !s.isEmpty() && s.is(gem(stone)); }

    public static @Nullable Stone gemOf(ItemStack s) {
        for (Stone st : Stone.values()) if (isGem(s, st)) return st;
        return null;
    }

    public static NonNullList<ItemStack> sockets(ItemStack gauntlet) {
        NonNullList<ItemStack> out = NonNullList.withSize(6, ItemStack.EMPTY);
        ItemContainerContents c = gauntlet.get(DataComponents.CONTAINER);
        if (c != null) c.copyInto(out);
        return out;
    }

    public static void setSockets(ItemStack gauntlet, List<ItemStack> six) {
        List<ItemStack> copy = new ArrayList<>(6);
        for (int i = 0; i < 6; i++) copy.add(i < six.size() ? six.get(i).copy() : ItemStack.EMPTY);
        gauntlet.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(copy));
    }

    public static boolean has(ItemStack stack, Stone stone) {
        if (!isGauntlet(stack)) return false;
        ItemContainerContents c = stack.get(DataComponents.CONTAINER);
        return c != null && c.getSlots() > stone.ordinal() && isGem(c.getStackInSlot(stone.ordinal()), stone);
    }

    public static int mask(ItemStack stack) {
        if (!isGauntlet(stack)) return 0;
        ItemContainerContents c = stack.get(DataComponents.CONTAINER);
        if (c == null) return 0;
        int m = 0;
        int slots = c.getSlots();
        for (Stone s : Stone.values()) if (slots > s.ordinal() && isGem(c.getStackInSlot(s.ordinal()), s)) m |= 1 << s.ordinal();
        return m;
    }

    public static boolean isComplete(ItemStack stack) { return mask(stack) == 0b111111; }

    /** A complete gauntlet in a hand or a Curios slot. */
    public static boolean wearsComplete(Player p) {
        if (isComplete(p.getMainHandItem()) || isComplete(p.getOffhandItem())) return true;
        return ModList.get().isLoaded("curios") && CuriosCompat.has(p, GauntletStones::isComplete);
    }

    /** The gauntlet in a hand (main first), or EMPTY. */
    public static ItemStack inHand(Player p) {
        if (isGauntlet(p.getMainHandItem())) return p.getMainHandItem();
        if (isGauntlet(p.getOffhandItem())) return p.getOffhandItem();
        return ItemStack.EMPTY;
    }

    private GauntletStones() {}
}
