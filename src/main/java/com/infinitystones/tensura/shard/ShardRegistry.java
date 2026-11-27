package com.infinitystones.tensura.shard;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The server's list of active mini gems (created / expiry / binding). A stack whose id is not in here, or is expired, is
 * dead and removes itself wherever it is found. Revoking = removing the entry, then sweeping everything that is loaded.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class ShardRegistry extends SavedData {
    public static final class Shard {
        public final UUID id, creator;
        public final String creatorName;
        public final int stone;
        public final List<String> powers;
        public final long expiry, total;
        public final boolean infinite;
        public UUID boundTo;
        public String boundName = "";

        Shard(UUID id, UUID creator, String creatorName, int stone, List<String> powers, long expiry, long total, boolean infinite) {
            this.total = total;
            this.id = id; this.creator = creator; this.creatorName = creatorName; this.stone = stone;
            this.powers = List.copyOf(powers); this.expiry = expiry; this.infinite = infinite;
        }

        public ShardData data() {
            return new ShardData(id, creator, creatorName, stone, powers, expiry, total, infinite, boundTo, boundName);
        }
    }

    private static final String NAME = "infinity_shards";
    private static MinecraftServer server;
    private static ShardRegistry instance;
    /** Cheap "is there anything to check at all" flag for hot paths (false until the first mini gem ever exists). */
    public static volatile boolean everCreated;
    public static volatile int activeCount;

    private final Map<UUID, Shard> shards = new LinkedHashMap<>();
    private boolean ever;

    // ---------------------------------------------------------------- access

    public static @Nullable ShardRegistry get() {
        MinecraftServer s = server;
        if (s == null) return null;
        if (instance == null) {
            instance = s.overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(ShardRegistry::new, ShardRegistry::load, null), NAME);
            everCreated = instance.ever;
            activeCount = instance.shards.size();
        }
        return instance;
    }

    public static long now() {
        MinecraftServer s = server;
        return s == null ? 0L : s.overworld().getGameTime();
    }

    public static @Nullable MinecraftServer server() { return server; }

    /** The live entry of an id: present and not expired, else null. */
    public static @Nullable Shard live(UUID id) {
        if (!everCreated) return null;
        ShardRegistry r = get();
        if (r == null) return null;
        Shard s = r.shards.get(id);
        if (s == null) return null;
        return s.infinite || now() < s.expiry ? s : null;
    }

    public List<Shard> byCreator(UUID creator) {
        List<Shard> out = new ArrayList<>();
        long now = now();
        for (Shard s : shards.values()) if (s.creator.equals(creator) && (s.infinite || now < s.expiry)) out.add(s);
        return out;
    }

    public Shard create(UUID creator, String creatorName, int stone, List<String> powers, long durationTicks, boolean infinite) {
        Shard s = new Shard(UUID.randomUUID(), creator, creatorName, stone, powers, infinite ? -1L : now() + durationTicks, infinite ? 0L : durationTicks, infinite);
        shards.put(s.id, s);
        ever = true;
        everCreated = true;
        activeCount = shards.size();
        setDirty();
        ShardGrants.invalidate();
        return s;
    }

    public void bind(Shard s, UUID player, String name) {
        s.boundTo = player;
        s.boundName = name;
        setDirty();
        ShardGrants.invalidate();
    }

    /** Removes the entry (all copies of the stack die with it) and sweeps the loaded world. */
    public boolean revoke(UUID id) {
        if (shards.remove(id) == null) return false;
        activeCount = shards.size();
        setDirty();
        ShardGrants.invalidate();
        return true;
    }

    // ---------------------------------------------------------------- persistence

    private static ShardRegistry load(CompoundTag tag, HolderLookup.Provider regs) {
        ShardRegistry r = new ShardRegistry();
        r.ever = tag.getBoolean("ever");
        for (Tag t : tag.getList("shards", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            List<String> powers = new ArrayList<>();
            for (Tag p : c.getList("powers", Tag.TAG_STRING)) powers.add(p.getAsString());
            Shard s = new Shard(NbtUtils.loadUUID(c.get("id")), NbtUtils.loadUUID(c.get("creator")), c.getString("creator_name"),
                    c.getInt("stone"), powers, c.getLong("expiry"), c.getLong("total"), c.getBoolean("infinite"));
            if (c.contains("bound")) { s.boundTo = NbtUtils.loadUUID(c.get("bound")); s.boundName = c.getString("bound_name"); }
            r.shards.put(s.id, s);
        }
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider regs) {
        tag.putBoolean("ever", ever);
        ListTag list = new ListTag();
        for (Shard s : shards.values()) {
            CompoundTag c = new CompoundTag();
            c.put("id", NbtUtils.createUUID(s.id));
            c.put("creator", NbtUtils.createUUID(s.creator));
            c.putString("creator_name", s.creatorName);
            c.putInt("stone", s.stone);
            ListTag p = new ListTag();
            for (String n : s.powers) p.add(StringTag.valueOf(n));
            c.put("powers", p);
            c.putLong("expiry", s.expiry);
            c.putLong("total", s.total);
            c.putBoolean("infinite", s.infinite);
            if (s.boundTo != null) { c.put("bound", NbtUtils.createUUID(s.boundTo)); c.putString("bound_name", s.boundName); }
            list.add(c);
        }
        tag.put("shards", list);
        return tag;
    }

    // ---------------------------------------------------------------- lifecycle

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        server = event.getServer();
        instance = null;
        get();
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        server = null;
        instance = null;
        everCreated = false;
        activeCount = 0;
    }

    /** Once a second: expired mini gems are removed from the list and from the whole loaded world. */
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer s = event.getServer();
        if (s != server || !everCreated || activeCount == 0) return;
        ShardRegistry r = get();
        if (r == null || r.shards.isEmpty()) return;
        long now = now();
        List<UUID> dead = new ArrayList<>();
        for (Shard sh : r.shards.values()) if (!sh.infinite && now >= sh.expiry) dead.add(sh.id);
        if (dead.isEmpty()) return;
        for (UUID id : dead) r.revoke(id);
        ShardSweep.sweepAll(s);
    }
}
