package com.infinitystones.tensura.skill.ability.extra.fear;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent part of the fear realm: who is in somebody's nightmare and where they came from (so a restart or a relog can
 * always send them back to the exact spot), which arena slot belongs to which victim, and where watching casters left from.
 */
public final class FearData extends SavedData {
    private static final String NAME = "infinity_fear_realm";

    /** Exact origin of someone taken to / watching the realm. */
    public record Origin(ResourceLocation dim, double x, double y, double z, float yaw, float pitch, int gameType) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("d", dim.toString());
            t.putDouble("x", x); t.putDouble("y", y); t.putDouble("z", z);
            t.putFloat("yw", yaw); t.putFloat("pt", pitch); t.putInt("g", gameType);
            return t;
        }

        static Origin load(CompoundTag t) {
            ResourceLocation d = ResourceLocation.tryParse(t.getString("d"));
            return new Origin(d == null ? ResourceLocation.withDefaultNamespace("overworld") : d, t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getFloat("yw"), t.getFloat("pt"), t.getInt("g"));
        }
    }

    public record Victim(UUID caster, Origin origin) {}

    public final Map<UUID, Victim> victims = new HashMap<>();
    public final Map<UUID, Integer> slots = new HashMap<>();
    /** Casters currently watching: caster -> where they stood before. */
    public final Map<UUID, Origin> watchers = new HashMap<>();
    /** Self-experience presets per caster. */
    public final Map<UUID, java.util.List<CompoundTag>> presets = new HashMap<>();

    public static FearData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(FearData::new, FearData::load, null), NAME);
    }

    public int slotFor(UUID victim) {
        Integer s = slots.get(victim);
        if (s != null) return s;
        Set<Integer> used = new HashSet<>(slots.values());
        int start = Math.floorMod(victim.hashCode(), FearRealm.SLOTS);
        for (int i = 0; i < FearRealm.SLOTS; i++) {
            int c = (start + i) % FearRealm.SLOTS;
            if (c != 2080 && !used.contains(c)) { slots.put(victim, c); setDirty(); return c; }
        }
        return start;
    }

    private static FearData load(CompoundTag tag, HolderLookup.Provider provider) {
        FearData d = new FearData();
        for (Tag e : tag.getList("victims", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            d.victims.put(t.getUUID("v"), new Victim(t.getUUID("c"), Origin.load(t.getCompound("o"))));
        }
        for (Tag e : tag.getList("slots", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            d.slots.put(t.getUUID("v"), t.getInt("s"));
        }
        for (Tag e : tag.getList("watch", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            d.watchers.put(t.getUUID("c"), Origin.load(t.getCompound("o")));
        }
        for (Tag e : tag.getList("presets", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            java.util.List<CompoundTag> l = new java.util.ArrayList<>();
            for (Tag x : t.getList("l", Tag.TAG_COMPOUND)) l.add((CompoundTag) x);
            d.presets.put(t.getUUID("c"), l);
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag v = new ListTag(), s = new ListTag(), w = new ListTag();
        victims.forEach((id, vic) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("v", id); t.putUUID("c", vic.caster()); t.put("o", vic.origin().save());
            v.add(t);
        });
        slots.forEach((id, slot) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("v", id); t.putInt("s", slot);
            s.add(t);
        });
        watchers.forEach((id, o) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("c", id); t.put("o", o.save());
            w.add(t);
        });
        ListTag pr = new ListTag();
        presets.forEach((id, l) -> {
            CompoundTag t = new CompoundTag();
            t.putUUID("c", id);
            ListTag ll = new ListTag();
            ll.addAll(l);
            t.put("l", ll);
            pr.add(t);
        });
        tag.put("victims", v); tag.put("slots", s); tag.put("watch", w); tag.put("presets", pr);
        return tag;
    }
}
