package com.infinitystones.tensura.shard;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.CuriosCompat;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * What the mini gems a player carries let him do. Server side only: a valid gem (live in the registry, bound to this
 * player or unbound and his own) lends its stone and exactly its powers to {@code Stone.isHeldBy},
 * {@code StoneProgress.isUnlocked} and {@code StoneTier.hidden}. Everything else about the powers is unchanged, so the
 * existing abilities run as they are. Per-power cooldowns for borrowed use live here too.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class ShardGrants {
    public record Held(ShardRegistry.Shard shard, Stone stone, Set<String> powers) {}

    private record Cache(long tick, List<Held> held) {}

    private static final Map<UUID, Cache> CACHE = new HashMap<>();
    private static final Map<UUID, Map<String, Long>> COOLDOWNS = new HashMap<>();
    private static final ThreadLocal<int[]> BYPASS = ThreadLocal.withInitial(() -> new int[1]);

    // ---------------------------------------------------------------- bypass (ask "what does he really own?")

    public static <T> T without(Supplier<T> action) {
        int[] depth = BYPASS.get();
        depth[0]++;
        try {
            return action.get();
        } finally {
            depth[0]--;
        }
    }

    public static void without(Runnable action) { without(() -> { action.run(); return null; }); }

    // ---------------------------------------------------------------- carried gems

    /** Valid gems the player carries (hands, inventory, armour, accessory slots) that he is allowed to use. */
    public static List<Held> carried(Player p) {
        if (!ShardRegistry.everCreated || !(p instanceof ServerPlayer sp) || BYPASS.get()[0] > 0) return List.of();
        long tick = sp.serverLevel().getGameTime();
        Cache c = CACHE.get(sp.getUUID());
        if (c != null && c.tick == tick) return c.held;
        List<Held> out = new ArrayList<>();
        Inventory inv = sp.getInventory();
        for (ItemStack s : inv.items) add(out, sp, s);
        for (ItemStack s : inv.offhand) add(out, sp, s);
        for (ItemStack s : inv.armor) add(out, sp, s);
        CuriosCompat.forEach(sp, s -> add(out, sp, s));
        List<Held> res = out.isEmpty() ? List.of() : List.copyOf(out);
        CACHE.put(sp.getUUID(), new Cache(tick, res));
        return res;
    }

    private static void add(List<Held> out, ServerPlayer p, ItemStack s) {
        if (!ShardSweep.isShard(s)) return;
        ShardData d = s.get(ShardRegistries.SHARD.get());
        if (d == null) return;
        ShardRegistry.Shard sh = ShardRegistry.live(d.id());
        if (sh == null || !usable(sh, p.getUUID())) return;
        for (Held h : out) if (h.shard == sh) return;
        if (sh.stone < 0 || sh.stone >= Stone.values().length) return;
        out.add(new Held(sh, Stone.values()[sh.stone], Set.copyOf(sh.powers)));
    }

    /** Bound to this player, or unbound and his own (the creator may carry it unbound). */
    public static boolean usable(ShardRegistry.Shard s, UUID player) {
        return s.boundTo == null ? s.creator.equals(player) : s.boundTo.equals(player);
    }

    public static boolean grants(Player p, Stone stone, String nodeId) {
        for (Held h : carried(p)) if (h.stone == stone && h.powers.contains(nodeId)) return true;
        return false;
    }

    public static boolean holdsStone(Player p, Stone stone) {
        for (Held h : carried(p)) if (h.stone == stone) return true;
        return false;
    }

    /** True when the power is usable only because of a gem: the player does not own it the normal way. */
    public static boolean isBorrowed(ServerPlayer p, Stone stone, StoneTree.Node node) {
        if (!grants(p, stone, node.id())) return false;
        boolean legit = without(() -> stone.isHeldBy(p) && com.infinitystones.tensura.skill.StoneProgress.isUnlocked(p, stone, node.id())
                && !com.infinitystones.tensura.skill.StoneTier.hidden(p, node));
        return !legit;
    }

    public static void invalidate() { CACHE.clear(); }

    // ---------------------------------------------------------------- cooldowns of borrowed use

    public static int cooldownLeft(ServerPlayer p, String modeId) {
        Map<String, Long> m = COOLDOWNS.get(p.getUUID());
        Long until = m == null ? null : m.get(modeId);
        if (until == null) return 0;
        long left = until - p.serverLevel().getGameTime();
        if (left <= 0) { m.remove(modeId); return 0; }
        return (int) left;
    }

    public static void startCooldown(ServerPlayer p, String modeId) {
        COOLDOWNS.computeIfAbsent(p.getUUID(), k -> new HashMap<>())
                .put(modeId, p.serverLevel().getGameTime() + ShardRules.cooldownSeconds(modeId) * 20L);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        CACHE.remove(id);
        COOLDOWNS.remove(id);
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        CACHE.clear();
        COOLDOWNS.clear();
    }

    private ShardGrants() {}
}
