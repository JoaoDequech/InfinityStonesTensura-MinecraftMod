package com.infinitystones.tensura.skill.ability.extra.reality;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The server's own creative contents (every creative tab, modded ones included, with the server's feature flags and
 * registries), built once per server. A requested stack keeps its components only when an equal stack is in here.
 * Common classes only: safe on a dedicated server.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class ChestCatalog {
    private static MinecraftServer owner;
    private static Map<Item, List<ItemStack>> byItem;

    private static synchronized Map<Item, List<ItemStack>> get(MinecraftServer server) {
        if (byItem != null && owner == server) return byItem;
        Map<Item, List<ItemStack>> m = new HashMap<>();
        try {
            CreativeModeTabs.tryRebuildTabContents(server.getWorldData().enabledFeatures(), true, server.registryAccess());
            for (CreativeModeTab tab : CreativeModeTabs.allTabs()) {
                add(m, tab.getDisplayItems());
                add(m, tab.getSearchTabDisplayItems());
            }
        } catch (Throwable ex) {
            InfinityStones.LOGGER.warn("[extra] catalog unavailable; plain items only", ex);
        }
        owner = server;
        byItem = m;
        return m;
    }

    private static void add(Map<Item, List<ItemStack>> m, Collection<ItemStack> stacks) {
        for (ItemStack s : stacks) {
            if (s.isEmpty()) continue;
            List<ItemStack> l = m.computeIfAbsent(s.getItem(), k -> new ArrayList<>());
            boolean dup = false;
            for (ItemStack o : l) if (ItemStack.isSameItemSameComponents(o, s)) { dup = true; break; }
            if (!dup) l.add(s.copyWithCount(1));
        }
    }

    /** The stack the server agrees to give (count 1), or EMPTY when the item does not exist / is disabled / is air. */
    static ItemStack resolve(MinecraftServer server, ItemStack wanted) {
        if (wanted.isEmpty()) return ItemStack.EMPTY;
        Item item = wanted.getItem();
        if (item == Items.AIR || !item.isEnabled(server.getWorldData().enabledFeatures())) return ItemStack.EMPTY;
        List<ItemStack> known = get(server).get(item);
        if (known != null) for (ItemStack k : known) if (ItemStack.isSameItemSameComponents(k, wanted)) return k.copyWithCount(1);
        return new ItemStack(item);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent e) {
        synchronized (ChestCatalog.class) { owner = null; byItem = null; }
    }

    private ChestCatalog() {}
}
