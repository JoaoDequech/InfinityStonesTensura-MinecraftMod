package com.infinitystones.tensura.shard;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registrations of the mini gem (item + data component), attached from the mod constructor. */
public final class ShardRegistries {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(InfinityStones.MOD_ID);
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, InfinityStones.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ShardData>> SHARD =
            COMPONENTS.register("shard", () -> DataComponentType.<ShardData>builder()
                    .persistent(ShardData.CODEC)
                    .networkSynchronized(ShardData.STREAM_CODEC)
                    .cacheEncoding()
                    .build());

    public static final DeferredItem<ShardItem> MINI_JOIA = ITEMS.register("mini_joia",
            () -> new ShardItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public static void init(IEventBus bus) {
        ITEMS.register(bus);
        COMPONENTS.register(bus);
    }

    private ShardRegistries() {}
}
