package com.infinitystones.tensura.shard;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.CuriosCompat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Belt hologram + last-seconds countdown, server side. Per player, the stones of the (up to 3) live mini gems he
 * carries are kept as a small list; it is sent to everyone tracking him (and himself) only when it changes. The same list
 * drives the digital tick heard in the last 10 seconds before a gem runs out.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class ShardBelt {
    /** Ticks of the final countdown. */
    public static final int FINAL_TICKS = 200;
    public static final int MAX_SHOWN = 3;

    private static final Map<UUID, List<ShardNet.BeltEntry>> STATE = new HashMap<>();
    private static final Map<UUID, Integer> HASH = new HashMap<>();

    /** Live mini gems the player carries (hands, inventory, armour, accessory slots), at most MAX_SHOWN, stable order. */
    private static List<ShardNet.BeltEntry> scan(ServerPlayer p) {
        List<ShardRegistry.Shard> found = new ArrayList<>();
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) add(found, inv.getItem(i));
        CuriosCompat.forEach(p, s -> add(found, s));
        found.sort(Comparator.comparing(s -> s.id));
        List<ShardNet.BeltEntry> out = new ArrayList<>();
        for (ShardRegistry.Shard s : found) {
            if (out.size() >= MAX_SHOWN) break;
            out.add(new ShardNet.BeltEntry(s.stone, s.expiry, s.infinite));
        }
        return out;
    }

    private static void add(List<ShardRegistry.Shard> out, ItemStack st) {
        if (!ShardSweep.isShard(st)) return;
        ShardData d = st.get(ShardRegistries.SHARD.get());
        ShardRegistry.Shard s = d == null ? null : ShardRegistry.live(d.id());
        if (s != null && !out.contains(s)) out.add(s);
    }

    /** Called every 10 ticks per player: recompute and broadcast on change. */
    static void sync(ServerPlayer p) {
        List<ShardNet.BeltEntry> now = ShardRegistry.everCreated ? scan(p) : List.of();
        Integer prev = HASH.get(p.getUUID());
        int hash = now.hashCode() * 31 + p.getId();   // the entity id changes on respawn: resend then
        if (prev == null && now.isEmpty()) return;
        if (prev != null && prev == hash) return;
        HASH.put(p.getUUID(), hash);
        if (now.isEmpty()) { STATE.remove(p.getUUID()); HASH.remove(p.getUUID()); } else STATE.put(p.getUUID(), now);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new ShardNet.BeltMsg(p.getId(), now));
    }

    /** A player that starts tracking another one gets his current hologram. */
    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking event) {
        if (!(event.getTarget() instanceof ServerPlayer target) || !(event.getEntity() instanceof ServerPlayer viewer)) return;
        List<ShardNet.BeltEntry> list = STATE.get(target.getUUID());
        if (list != null && !list.isEmpty()) PacketDistributor.sendToPlayer(viewer, new ShardNet.BeltMsg(target.getId(), list));
    }

    /** Digital tick once a second in the last 10 seconds, higher each time. */
    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (STATE.isEmpty() || !(event.getEntity() instanceof ServerPlayer p)) return;
        List<ShardNet.BeltEntry> list = STATE.get(p.getUUID());
        if (list == null) return;
        long now = ShardRegistry.now();
        int sec = -1;
        for (ShardNet.BeltEntry e : list) {
            if (e.infinite()) continue;
            long left = e.expiry() - now;
            if (left > 0 && left <= FINAL_TICKS && left % 20 == 0) sec = (int) (left / 20);
        }
        if (sec < 0) return;
        ServerLevel l = p.serverLevel();
        float pitch = 0.8F + (10 - sec) * 0.13F;
        double x = p.getX(), y = p.getY() + 0.9, z = p.getZ();
        l.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.5F, pitch);
        if (sec <= 3) l.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.4F, 1.6F + (3 - sec) * 0.2F);
        if (sec <= 1) l.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.PLAYERS, 0.35F, pitch * 1.5F);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATE.remove(event.getEntity().getUUID());
        HASH.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        STATE.clear();
        HASH.clear();
    }

    private ShardBelt() {}
}
