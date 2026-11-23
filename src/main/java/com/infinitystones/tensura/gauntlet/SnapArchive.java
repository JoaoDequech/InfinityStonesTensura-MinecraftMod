package com.infinitystones.tensura.gauntlet;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Where snapped creatures go (the original sent them to the Soulworld). Each one is kept as its full save data with
 * where it was and who snapped it, in the world save (survives restarts) — no hundreds of live mobs crammed in one
 * chunk, no lag. "Bring back" rebuilds them from here. Capped (oldest first) so a save can never grow without bound.
 */
public final class SnapArchive extends SavedData {
    private static final String NAME = "infinity_snap_archive";
    private static final int CAP = 6000;

    public record Entry(UUID by, String dim, double x, double y, double z, CompoundTag nbt) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putUUID("by", by); t.putString("dim", dim);
            t.putDouble("x", x); t.putDouble("y", y); t.putDouble("z", z);
            t.put("nbt", nbt);
            return t;
        }
        static Entry load(CompoundTag t) {
            return new Entry(t.getUUID("by"), t.getString("dim"), t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getCompound("nbt"));
        }
    }

    private final List<Entry> entries = new ArrayList<>();

    public static SnapArchive get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(SnapArchive::new, SnapArchive::load), NAME);
    }

    private static SnapArchive load(CompoundTag tag, HolderLookup.Provider registries) {
        SnapArchive a = new SnapArchive();
        ListTag list = tag.getList("entries", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) a.entries.add(Entry.load(list.getCompound(i)));
        return a;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Entry e : entries) list.add(e.save());
        tag.put("entries", list);
        return tag;
    }

    public void add(Entry e) {
        entries.add(e);
        while (entries.size() > CAP) entries.remove(0);
        setDirty();
    }

    public int count(UUID by) { int n = 0; for (Entry e : entries) if (e.by().equals(by)) n++; return n; }

    /** Removes and returns the entries matching the filter. */
    public List<Entry> take(Predicate<Entry> filter) {
        List<Entry> out = new ArrayList<>();
        for (Iterator<Entry> it = entries.iterator(); it.hasNext(); ) {
            Entry e = it.next();
            if (filter.test(e)) { out.add(e); it.remove(); }
        }
        if (!out.isEmpty()) setDirty();
        return out;
    }

    public void putBack(List<Entry> list) { if (!list.isEmpty()) { entries.addAll(list); setDirty(); } }
}
