package com.infinitystones.tensura.registry;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The six sockets of an Infinity Gauntlet (slot = stone ordinal), stored as the mod's own item data component so the
 * stones survive every ItemEntity / container / network copy of the stack. Always exactly six entries.
 */
public record GauntletContents(List<ItemStack> sockets) {
    public static final int SIZE = 6;
    public static final GauntletContents EMPTY = new GauntletContents(List.of());

    public static final Codec<GauntletContents> CODEC = ItemStack.OPTIONAL_CODEC.sizeLimitedListOf(SIZE)
            .xmap(GauntletContents::new, GauntletContents::sockets);
    public static final StreamCodec<RegistryFriendlyByteBuf, GauntletContents> STREAM_CODEC =
            ItemStack.OPTIONAL_LIST_STREAM_CODEC.map(GauntletContents::new, GauntletContents::sockets);

    public GauntletContents {
        List<ItemStack> six = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) six.add(i < sockets.size() ? sockets.get(i).copy() : ItemStack.EMPTY);
        sockets = java.util.Collections.unmodifiableList(six);
    }

    public ItemStack get(int socket) {
        return socket >= 0 && socket < SIZE ? sockets.get(socket) : ItemStack.EMPTY;
    }

    @Override public boolean equals(Object o) {
        return this == o || o instanceof GauntletContents other && ItemStack.listMatches(sockets, other.sockets);
    }

    @Override public int hashCode() { return ItemStack.hashStackList(sockets); }
}
