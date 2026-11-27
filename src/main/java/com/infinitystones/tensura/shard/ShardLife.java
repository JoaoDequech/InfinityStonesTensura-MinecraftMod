package com.infinitystones.tensura.shard;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.CuriosCompat;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ChunkWatchEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Lazy invalidation and binding: every place a mini gem shows up checks it against the registry. */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class ShardLife {

    /** One stack held by a player: dies when dead, binds to the first other player, is thrown out of a third player's hands. */
    static void tickStack(ServerPlayer p, ItemStack st) {
        ShardData d = st.get(ShardRegistries.SHARD.get());
        ShardRegistry.Shard s = d == null ? null : ShardRegistry.live(d.id());
        if (s == null) {
            ShardSweep.scrubPlayer(p, false);
            return;
        }
        ShardRegistry reg = ShardRegistry.get();
        if (reg == null) return;
        var uuid = p.getUUID();
        boolean creator = uuid.equals(s.creator);
        if (s.boundTo == null && !creator) {
            reg.bind(s, uuid, p.getGameProfile().getName());
            p.displayClientMessage(Component.translatable("infinity.shard.bound", s.creatorName), true);
        } else if (s.boundTo != null && !creator && !uuid.equals(s.boundTo)) {
            ItemStack copy = st.copy();
            st.setCount(0);
            ItemEntity out = p.drop(copy, true);
            if (out != null) out.setPickUpDelay(200);
            p.displayClientMessage(Component.translatable("infinity.shard.not_yours"), true);
            return;
        }
        ShardData fresh = s.data();
        if (!fresh.equals(d)) st.set(ShardRegistries.SHARD.get(), fresh);
    }

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post event) {
        if (!ShardRegistry.everCreated || !(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 10 != 3) return;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (ShardSweep.isShard(s)) tickStack(p, s);
        }
        CuriosCompat.forEach(p, s -> { if (ShardSweep.isShard(s)) tickStack(p, s); });
        if (p.tickCount % 200 == 3) ShardSweep.scrubPlayer(p, true); // nested stacks (backpacks, bundles) and the ender chest
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (ShardRegistry.everCreated && event.getEntity() instanceof ServerPlayer p) ShardSweep.scrubPlayer(p, true);
    }

    @SubscribeEvent
    public static void open(PlayerContainerEvent.Open event) {
        if (!ShardRegistry.everCreated || !(event.getEntity() instanceof ServerPlayer p)) return;
        ShardSweep.scrubMenu(event.getContainer(), new ShardSweep.Ctx(p.serverLevel(), p.position().add(0, 1, 0), true));
        event.getContainer().broadcastChanges();
    }

    @SubscribeEvent
    public static void watch(ChunkWatchEvent.Watch event) {
        if (ShardRegistry.everCreated) ShardSweep.scrubChunk(event.getChunk());
    }

    /** Bound mini gems can only be picked up by the bound player or the creator; dead ones vanish on contact. */
    @SubscribeEvent
    public static void pickup(ItemEntityPickupEvent.Pre event) {
        ItemEntity ie = event.getItemEntity();
        ItemStack st = ie.getItem();
        if (!ShardSweep.isShard(st)) return;
        ShardData d = st.get(ShardRegistries.SHARD.get());
        ShardRegistry.Shard s = d == null ? null : ShardRegistry.live(d.id());
        if (s == null) {
            ShardSweep.scrubEntity(ie, false);
            event.setCanPickup(TriState.FALSE);
            return;
        }
        var uuid = event.getPlayer().getUUID();
        if (s.boundTo != null && !uuid.equals(s.boundTo) && !uuid.equals(s.creator)) event.setCanPickup(TriState.FALSE);
    }

    private ShardLife() {}
}
