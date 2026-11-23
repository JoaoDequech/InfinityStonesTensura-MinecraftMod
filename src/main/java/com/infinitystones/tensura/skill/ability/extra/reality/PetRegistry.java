package com.infinitystones.tensura.skill.ability.extra.reality;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owner → pet UUIDs, saved with the world (overworld data storage) so the 8-pet cap also counts pets left in unloaded
 * chunks, and deaths are recorded even when the owner is offline.
 */
final class PetRegistry extends SavedData {
    private static final String NAME = "infinity_reality_pets";
    private final Map<UUID, Set<UUID>> pets = new HashMap<>();

    static PetRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PetRegistry::new, PetRegistry::load, null), NAME);
    }

    private static PetRegistry load(CompoundTag tag, HolderLookup.Provider lookup) {
        PetRegistry r = new PetRegistry();
        CompoundTag owners = tag.getCompound("owners");
        for (String k : owners.getAllKeys()) {
            try {
                UUID owner = UUID.fromString(k);
                Set<UUID> set = new LinkedHashSet<>();
                for (Tag t : owners.getList(k, Tag.TAG_INT_ARRAY)) set.add(NbtUtils.loadUUID(t));
                if (!set.isEmpty()) r.pets.put(owner, set);
            } catch (IllegalArgumentException ignored) { }
        }
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        CompoundTag owners = new CompoundTag();
        pets.forEach((owner, set) -> {
            ListTag l = new ListTag();
            for (UUID u : set) l.add(NbtUtils.createUUID(u));
            owners.put(owner.toString(), l);
        });
        tag.put("owners", owners);
        return tag;
    }

    java.util.Set<UUID> of(UUID owner) { return pets.getOrDefault(owner, java.util.Set.of()); }

    void add(UUID owner, UUID pet) { pets.computeIfAbsent(owner, k -> new LinkedHashSet<>()).add(pet); setDirty(); }

    void remove(UUID owner, UUID pet) {
        Set<UUID> s = pets.get(owner);
        if (s != null && s.remove(pet)) { if (s.isEmpty()) pets.remove(owner); setDirty(); }
    }

    /** Pets of owner after dropping entries that are loaded but dead / no longer this owner's pet. */
    int count(MinecraftServer server, UUID owner) {
        Set<UUID> s = pets.get(owner);
        if (s == null) return 0;
        boolean changed = s.removeIf(u -> {
            for (ServerLevel l : server.getAllLevels()) {
                Entity e = l.getEntity(u);
                if (e != null) return !e.isAlive() || !owner.equals(RealityPet.ownerOf(e));
            }
            return false;
        });
        if (changed) setDirty();
        return s.size();
    }
}
