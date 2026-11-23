package com.infinitystones.tensura.skill.ability.extra.reality.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Every item the picker can offer, built at runtime: all creative tabs (modded ones and their variants: potions,
 * enchanted books...) plus every registered item that no tab shows. Built once per world.
 */
@EventBusSubscriber(modid = com.infinitystones.tensura.InfinityStones.MOD_ID, value = Dist.CLIENT)
final class ChestItems {
    static final class Entry {
        final ItemStack stack;
        final String name, mod, path;
        volatile String tip;

        Entry(ItemStack stack) {
            this.stack = stack;
            String n;
            try { n = stack.getHoverName().getString(); } catch (Throwable t) { n = ""; }
            var key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            this.name = n.toLowerCase(Locale.ROOT);
            this.mod = key.getNamespace().toLowerCase(Locale.ROOT);
            this.path = key.getPath().toLowerCase(Locale.ROOT);
        }
    }

    static final class Tab {
        final Component name;
        final ItemStack icon;
        final List<Entry> entries = new ArrayList<>();

        Tab(Component name, ItemStack icon) { this.name = name; this.icon = icon; }
    }

    /** tabs.get(0) = everything, the last one = items that have no creative tab. */
    static List<Tab> tabs;
    private static Level builtFor;
    private static int tipPos;
    static volatile int tipVersion;

    static void ensure() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (tabs != null && builtFor == mc.level) return;
        Level lvl = mc.level;
        List<Tab> out = new ArrayList<>();
        Tab all = new Tab(Component.translatable("infinity.extra.chest.all"), new ItemStack(Items.CHEST));
        Tab others = new Tab(Component.translatable("infinity.extra.chest.others"), new ItemStack(Items.BARRIER));
        out.add(all);
        java.util.Map<ItemStack, Entry> seen = new it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap<>(ItemStackLinkedSet.TYPE_AND_TAG);
        Set<Item> present = new java.util.HashSet<>();
        try {
            CreativeModeTabs.tryRebuildTabContents(lvl.enabledFeatures(), true, lvl.registryAccess());
            for (CreativeModeTab t : CreativeModeTabs.allTabs()) {
                if (t.getType() != CreativeModeTab.Type.CATEGORY) continue;
                Tab tab = null;
                for (ItemStack s : new ArrayList<>(t.getDisplayItems())) {
                    if (s.isEmpty()) continue;
                    if (tab == null) tab = new Tab(t.getDisplayName(), t.getIconItem());
                    ItemStack one = s.copyWithCount(1);
                    Entry e = seen.get(one);
                    if (e == null) { e = new Entry(one); seen.put(one, e); all.entries.add(e); }
                    tab.entries.add(e);
                    present.add(s.getItem());
                }
                if (tab != null) out.add(tab);
            }
        } catch (Throwable ex) {
            com.infinitystones.tensura.InfinityStones.LOGGER.warn("[extra] tabs unavailable; listing the registry only", ex);
        }
        for (Item it : BuiltInRegistries.ITEM) {
            if (it == Items.AIR || present.contains(it) || !it.isEnabled(lvl.enabledFeatures())) continue;
            ItemStack one = new ItemStack(it);
            Entry e = seen.get(one);
            if (e == null) { e = new Entry(one); seen.put(one, e); all.entries.add(e); }
            others.entries.add(e);
        }
        if (!others.entries.isEmpty()) out.add(others);
        tabs = out;
        builtFor = lvl;
        tipPos = 0;
        tipVersion++;
    }

    /** Fills tooltip texts (for the search) a little each call, so the screen never stalls. */
    static void fillTips(long budgetNanos) {
        if (tabs == null || tipPos >= tabs.get(0).entries.size()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        List<Entry> list = tabs.get(0).entries;
        long end = System.nanoTime() + budgetNanos;
        Item.TooltipContext ctx = Item.TooltipContext.of(mc.level);
        boolean any = false;
        while (tipPos < list.size() && System.nanoTime() < end) {
            Entry e = list.get(tipPos++);
            StringBuilder sb = new StringBuilder();
            try {
                for (Component c : e.stack.getTooltipLines(ctx, mc.player, TooltipFlag.NORMAL)) sb.append(c.getString()).append('\n');
            } catch (Throwable ignored) { }
            e.tip = sb.toString().toLowerCase(Locale.ROOT);
            any = true;
        }
        if (any) tipVersion++;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { tabs = null; builtFor = null; }

    private ChestItems() {}
}
