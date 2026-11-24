package com.infinitystones.tensura.skill;

import com.infinitystones.tensura.registry.GauntletContents;
import com.infinitystones.tensura.registry.ModComponents;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Stones set into an Infinity Gauntlet (original function of the mod: insert / remove each stone). The six sockets
 * live on the gauntlet stack itself, in the mod's own {@link GauntletContents} data component (slot =
 * {@link Stone#ordinal()}), so they are saved with the item, travel with it (drop, chest, trade) and are synced to
 * every client for rendering. Gauntlets written by older builds kept the stones in the vanilla CONTAINER component;
 * they are read from there and moved to the new component the first time they are written.
 * A stone in a gauntlet the player carries counts as carried ({@link Stone#isHeldBy}).
 * Gauntlet powers need a COMPLETE gauntlet (all six) carried or worn: hands, inventory, armour or an accessory slot.
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

    /** Sockets of a gauntlet stack, new component first, then the pre-component CONTAINER layout. */
    private static List<ItemStack> read(ItemStack gauntlet) {
        GauntletContents c = gauntlet.get(ModComponents.GAUNTLET_CONTENTS);
        if (c != null) return c.sockets();
        ItemContainerContents legacy = gauntlet.get(DataComponents.CONTAINER);
        if (legacy == null) return List.of();
        NonNullList<ItemStack> out = NonNullList.withSize(Math.max(GauntletContents.SIZE, legacy.getSlots()), ItemStack.EMPTY);
        legacy.copyInto(out);
        return out;
    }

    public static NonNullList<ItemStack> sockets(ItemStack gauntlet) {
        NonNullList<ItemStack> out = NonNullList.withSize(GauntletContents.SIZE, ItemStack.EMPTY);
        List<ItemStack> stored = read(gauntlet);
        for (int i = 0; i < GauntletContents.SIZE && i < stored.size(); i++) out.set(i, stored.get(i).copy());
        return out;
    }

    public static void setSockets(ItemStack gauntlet, List<ItemStack> six) {
        List<ItemStack> copy = new ArrayList<>(GauntletContents.SIZE);
        for (int i = 0; i < GauntletContents.SIZE; i++) copy.add(i < six.size() ? six.get(i).copy() : ItemStack.EMPTY);
        gauntlet.set(ModComponents.GAUNTLET_CONTENTS, new GauntletContents(copy));
        gauntlet.remove(DataComponents.CONTAINER);
    }

    public static boolean has(ItemStack stack, Stone stone) {
        return isGauntlet(stack) && (mask(stack) >> stone.ordinal() & 1) != 0;
    }

    public static int mask(ItemStack stack) {
        if (!isGauntlet(stack)) return 0;
        List<ItemStack> stored = read(stack);
        int m = 0;
        for (Stone s : Stone.values()) if (stored.size() > s.ordinal() && isGem(stored.get(s.ordinal()), s)) m |= 1 << s.ordinal();
        return m;
    }

    public static final int ALL = 0b111111;

    public static boolean isComplete(ItemStack stack) { return mask(stack) == ALL; }

    /**
     * First gauntlet the player carries that passes the test. Order: main hand, off hand, accessory slots, then the
     * rest of the inventory and armour, so the equipped / held one is the one that is used.
     */
    public static ItemStack find(Player p, Predicate<ItemStack> test) {
        ItemStack main = p.getMainHandItem();
        if (isGauntlet(main) && test.test(main)) return main;
        ItemStack off = p.getOffhandItem();
        if (isGauntlet(off) && test.test(off)) return off;
        ItemStack[] found = {ItemStack.EMPTY};
        CuriosCompat.forEach(p, s -> {
            if (found[0].isEmpty() && isGauntlet(s) && test.test(s)) found[0] = s;
        });
        if (!found[0].isEmpty()) return found[0];
        for (ItemStack s : p.getInventory().items) if (isGauntlet(s) && test.test(s)) return s;
        for (ItemStack s : p.getInventory().armor) if (isGauntlet(s) && test.test(s)) return s;
        return ItemStack.EMPTY;
    }

    /** The gauntlet the player manages: a complete one if carried, else the first carried one, else EMPTY. */
    public static ItemStack primary(Player p) {
        ItemStack complete = find(p, GauntletStones::isComplete);
        return complete.isEmpty() ? find(p, s -> true) : complete;
    }

    /** A complete gauntlet anywhere the player carries it (hands, inventory, armour, accessory slots). */
    public static ItemStack completeGauntlet(Player p) { return find(p, GauntletStones::isComplete); }

    public static boolean wearsComplete(Player p) { return !completeGauntlet(p).isEmpty(); }

    /** Stones set in the managed gauntlet (bit per ordinal), or -1 when the player carries no gauntlet at all. */
    public static int carriedMask(Player p) {
        ItemStack g = primary(p);
        return g.isEmpty() ? -1 : mask(g);
    }

    /** The gauntlet in a hand (main first), or EMPTY. */
    public static ItemStack inHand(Player p) {
        if (isGauntlet(p.getMainHandItem())) return p.getMainHandItem();
        if (isGauntlet(p.getOffhandItem())) return p.getOffhandItem();
        return ItemStack.EMPTY;
    }

    /** A usable power that the original only granted with all six stones: needs a complete gauntlet. */
    public static boolean isGauntletPower(StoneTree.Node node) {
        return node.isActive() && node.requiresPowers().size() >= 5;
    }

    private GauntletStones() {}
}
