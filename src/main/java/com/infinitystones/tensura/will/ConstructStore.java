package com.infinitystones.tensura.will;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Saved hard-light constructs, per player, kept with the world (survives restarts; works on dedicated servers).
 * A blueprint is {name, blocks:[{x,y,z,s:blockstate,e?:block entity}]} with positions relative to its minimum corner.
 * The first 8 of a player's list are the quick slots (middle click on Will in the wheel).
 */
public final class ConstructStore extends SavedData {
    public static final int QUICK_SLOTS = 8;
    private static final String ID = "infinity_constructs";

    public record Blueprint(String name, ListTag blocks) {
        public int count() { return blocks.size(); }
        CompoundTag save() { CompoundTag t = new CompoundTag(); t.putString("name", name); t.put("blocks", blocks); return t; }
        static Blueprint load(CompoundTag t) { return new Blueprint(t.getString("name"), t.getList("blocks", Tag.TAG_COMPOUND)); }
    }

    private final Map<UUID, List<Blueprint>> byPlayer = new HashMap<>();

    public static ConstructStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ConstructStore::new, ConstructStore::load, null), ID);
    }

    public List<Blueprint> of(UUID player) { return byPlayer.computeIfAbsent(player, k -> new ArrayList<>()); }

    public void changed() { setDirty(); }

    private static ConstructStore load(CompoundTag tag, HolderLookup.Provider registries) {
        ConstructStore s = new ConstructStore();
        CompoundTag players = tag.getCompound("players");
        for (String key : players.getAllKeys()) {
            List<Blueprint> list = new ArrayList<>();
            for (Tag t : players.getList(key, Tag.TAG_COMPOUND)) list.add(Blueprint.load((CompoundTag) t));
            try { s.byPlayer.put(UUID.fromString(key), list); } catch (IllegalArgumentException ignored) { }
        }
        return s;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag players = new CompoundTag();
        byPlayer.forEach((id, list) -> {
            ListTag l = new ListTag();
            for (Blueprint b : list) l.add(b.save());
            players.put(id.toString(), l);
        });
        tag.put("players", players);
        return tag;
    }
}
