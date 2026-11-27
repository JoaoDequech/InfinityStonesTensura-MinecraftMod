package com.infinitystones.tensura.shard;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Network of the mini gems: menu requests, the list of my gems, the wheel pages of carried gems and the creation effect. */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class ShardNet {

    // ---------------------------------------------------------------- client -> server

    /** Create a gem: stone ordinal, power ids, duration in seconds (ignored when infinite). */
    public record Create(int stone, List<String> powers, int seconds, boolean infinite) implements CustomPacketPayload {
        public static final Type<Create> TYPE = new Type<>(InfinityStones.id("shard_create"));
        public static final StreamCodec<FriendlyByteBuf, Create> CODEC = StreamCodec.of((b, m) -> {
            b.writeVarInt(m.stone); b.writeVarInt(m.powers.size());
            for (String p : m.powers) b.writeUtf(p, 64);
            b.writeVarInt(m.seconds); b.writeBoolean(m.infinite);
        }, b -> {
            int stone = b.readVarInt(), n = Math.min(b.readVarInt(), 64);
            List<String> powers = new ArrayList<>(n);
            for (int i = 0; i < n; i++) powers.add(b.readUtf(64));
            return new Create(stone, powers, b.readVarInt(), b.readBoolean());
        });
        @Override public Type<Create> type() { return TYPE; }
    }

    /** Delete one of my gems. */
    public record Delete(UUID id) implements CustomPacketPayload {
        public static final Type<Delete> TYPE = new Type<>(InfinityStones.id("shard_delete"));
        public static final StreamCodec<FriendlyByteBuf, Delete> CODEC = StreamCodec.of((b, m) -> b.writeUUID(m.id), b -> new Delete(b.readUUID()));
        @Override public Type<Delete> type() { return TYPE; }
    }

    /** Ask for the list of my gems. */
    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(InfinityStones.id("shard_request"));
        public static final StreamCodec<FriendlyByteBuf, Request> CODEC = StreamCodec.of((b, m) -> { }, b -> new Request());
        @Override public Type<Request> type() { return TYPE; }
    }

    // ---------------------------------------------------------------- server -> client

    public record Info(UUID id, int stone, List<String> powers, long expiry, boolean infinite, boolean bound, String boundName) {}

    /** My active gems; now = the overworld game time the expiry values refer to. */
    public record ListMsg(long now, List<Info> gems) implements CustomPacketPayload {
        public static final Type<ListMsg> TYPE = new Type<>(InfinityStones.id("shard_list"));
        public static final StreamCodec<FriendlyByteBuf, ListMsg> CODEC = StreamCodec.of((b, m) -> {
            b.writeLong(m.now); b.writeVarInt(m.gems.size());
            for (Info i : m.gems) {
                b.writeUUID(i.id); b.writeVarInt(i.stone); b.writeVarInt(i.powers.size());
                for (String p : i.powers) b.writeUtf(p, 64);
                b.writeLong(i.expiry); b.writeBoolean(i.infinite); b.writeBoolean(i.bound); b.writeUtf(i.boundName, 64);
            }
        }, b -> {
            long now = b.readLong();
            int n = b.readVarInt();
            List<Info> gems = new ArrayList<>(n);
            for (int k = 0; k < n; k++) {
                UUID id = b.readUUID();
                int stone = b.readVarInt(), pc = b.readVarInt();
                List<String> powers = new ArrayList<>(pc);
                for (int j = 0; j < pc; j++) powers.add(b.readUtf(64));
                gems.add(new Info(id, stone, powers, b.readLong(), b.readBoolean(), b.readBoolean(), b.readUtf(64)));
            }
            return new ListMsg(now, gems);
        });
        @Override public Type<ListMsg> type() { return TYPE; }
    }

    public record Power(String node, int cdLeft, int cdTotal) {}
    public record Page(UUID id, int stone, long expiry, boolean infinite, List<Power> powers) {}

    /** Gems carried and usable by the player: one wheel page each. */
    public record WheelMsg(List<Page> pages) implements CustomPacketPayload {
        public static final Type<WheelMsg> TYPE = new Type<>(InfinityStones.id("shard_wheel"));
        public static final StreamCodec<FriendlyByteBuf, WheelMsg> CODEC = StreamCodec.of((b, m) -> {
            b.writeVarInt(m.pages.size());
            for (Page p : m.pages) {
                b.writeUUID(p.id); b.writeVarInt(p.stone); b.writeLong(p.expiry); b.writeBoolean(p.infinite);
                b.writeVarInt(p.powers.size());
                for (Power w : p.powers) { b.writeUtf(w.node, 64); b.writeVarInt(w.cdLeft); b.writeVarInt(w.cdTotal); }
            }
        }, b -> {
            int n = b.readVarInt();
            List<Page> pages = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                UUID id = b.readUUID();
                int stone = b.readVarInt();
                long expiry = b.readLong();
                boolean inf = b.readBoolean();
                int pc = b.readVarInt();
                List<Power> powers = new ArrayList<>(pc);
                for (int j = 0; j < pc; j++) powers.add(new Power(b.readUtf(64), b.readVarInt(), b.readVarInt()));
                pages.add(new Page(id, stone, expiry, inf, powers));
            }
            return new WheelMsg(pages);
        });
        @Override public Type<WheelMsg> type() { return TYPE; }
    }

    public static final int FX_START = 0, FX_CANCEL = 1;

    /** Creation animation: start / cancel on the character with this entity id. */
    public record FxMsg(int kind, int entity, int stone) implements CustomPacketPayload {
        public static final Type<FxMsg> TYPE = new Type<>(InfinityStones.id("shard_fx"));
        public static final StreamCodec<FriendlyByteBuf, FxMsg> CODEC = StreamCodec.of((b, m) -> {
            b.writeVarInt(m.kind); b.writeVarInt(m.entity); b.writeVarInt(m.stone);
        }, b -> new FxMsg(b.readVarInt(), b.readVarInt(), b.readVarInt()));
        @Override public Type<FxMsg> type() { return TYPE; }
    }

    public record BeltEntry(int stone, long expiry, boolean infinite) {}

    /** The mini gems a player carries, for the hologram at his belt (stones + expiry; empty = none). */
    public record BeltMsg(int entity, List<BeltEntry> gems) implements CustomPacketPayload {
        public static final Type<BeltMsg> TYPE = new Type<>(InfinityStones.id("shard_belt"));
        public static final StreamCodec<FriendlyByteBuf, BeltMsg> CODEC = StreamCodec.of((b, m) -> {
            b.writeVarInt(m.entity); b.writeVarInt(m.gems.size());
            for (BeltEntry e : m.gems) { b.writeVarInt(e.stone); b.writeLong(e.expiry); b.writeBoolean(e.infinite); }
        }, b -> {
            int entity = b.readVarInt(), n = Math.min(b.readVarInt(), 8);
            List<BeltEntry> gems = new ArrayList<>(n);
            for (int i = 0; i < n; i++) gems.add(new BeltEntry(b.readVarInt(), b.readLong(), b.readBoolean()));
            return new BeltMsg(entity, gems);
        });
        @Override public Type<BeltMsg> type() { return TYPE; }
    }

    /** A mini gem shattered at this point (expired or deleted). */
    public record BurstMsg(int stone, double x, double y, double z) implements CustomPacketPayload {
        public static final Type<BurstMsg> TYPE = new Type<>(InfinityStones.id("shard_burst"));
        public static final StreamCodec<FriendlyByteBuf, BurstMsg> CODEC = StreamCodec.of((b, m) -> {
            b.writeVarInt(m.stone); b.writeDouble(m.x); b.writeDouble(m.y); b.writeDouble(m.z);
        }, b -> new BurstMsg(b.readVarInt(), b.readDouble(), b.readDouble(), b.readDouble()));
        @Override public Type<BurstMsg> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1").optional();
        r.playToServer(Create.TYPE, Create.CODEC, (m, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) ShardCreate.begin(sp, m);
        }));
        r.playToServer(Delete.TYPE, Delete.CODEC, (m, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) delete(sp, m.id());
        }));
        r.playToServer(Request.TYPE, Request.CODEC, (m, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) sendList(sp);
        }));
        r.playToClient(ListMsg.TYPE, ListMsg.CODEC, (m, ctx) -> ctx.enqueueWork(() -> ShardClientBridge.list(m)));
        r.playToClient(WheelMsg.TYPE, WheelMsg.CODEC, (m, ctx) -> ctx.enqueueWork(() -> ShardClientBridge.wheel(m)));
        r.playToClient(FxMsg.TYPE, FxMsg.CODEC, (m, ctx) -> ctx.enqueueWork(() -> ShardClientBridge.fx(m)));
        r.playToClient(BeltMsg.TYPE, BeltMsg.CODEC, (m, ctx) -> ctx.enqueueWork(() -> ShardClientBridge.belt(m)));
        r.playToClient(BurstMsg.TYPE, BurstMsg.CODEC, (m, ctx) -> ctx.enqueueWork(() -> ShardClientBridge.burst(m)));
    }

    // ---------------------------------------------------------------- server actions

    /** Only the creator can delete; the gem dies everywhere it is (online and loaded) at once. */
    static void delete(ServerPlayer p, UUID id) {
        ShardRegistry reg = ShardRegistry.get();
        ShardRegistry.Shard s = ShardRegistry.live(id);
        if (reg != null && s != null && s.creator.equals(p.getUUID())) {
            reg.revoke(id);
            if (p.getServer() != null) ShardSweep.sweepAll(p.getServer());
        }
        sendList(p);
    }

    static void sendList(ServerPlayer p) {
        List<Info> out = new ArrayList<>();
        ShardRegistry reg = ShardRegistry.get();
        if (reg != null) {
            for (ShardRegistry.Shard s : reg.byCreator(p.getUUID()))
                out.add(new Info(s.id, s.stone, s.powers, s.expiry, s.infinite, s.boundTo != null, s.boundName));
        }
        PacketDistributor.sendToPlayer(p, new ListMsg(ShardRegistry.now(), out));
    }

    // ---------------------------------------------------------------- wheel pages of carried gems

    private static final Map<UUID, Integer> LAST = new HashMap<>();

    public static void syncWheel(ServerPlayer p, boolean force) {
        List<Page> pages = new ArrayList<>();
        for (ShardGrants.Held h : ShardGrants.carried(p)) {
            List<Power> powers = new ArrayList<>();
            for (String id : h.shard().powers) {
                String mode = h.stone().key + "." + id;
                powers.add(new Power(id, ShardGrants.cooldownLeft(p, mode), ShardRules.cooldownSeconds(mode) * 20));
            }
            pages.add(new Page(h.shard().id, h.stone().ordinal(), h.shard().expiry, h.shard().infinite, powers));
        }
        int hash = 0;
        for (Page pg : pages) {
            hash = hash * 31 + pg.id.hashCode();
            for (Power w : pg.powers) hash = hash * 31 + w.node.hashCode() + (w.cdLeft + 19) / 20;
        }
        Integer prev = LAST.get(p.getUUID());
        if (!force && prev != null && prev == hash) return;
        if (prev == null && pages.isEmpty()) { LAST.put(p.getUUID(), hash); return; }
        LAST.put(p.getUUID(), hash);
        PacketDistributor.sendToPlayer(p, new WheelMsg(pages));
    }

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p.tickCount % 10 != 5) return;
        if (!ShardRegistry.everCreated && !LAST.containsKey(p.getUUID())) return;
        syncWheel(p, false);
        ShardBelt.sync(p);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { LAST.remove(event.getEntity().getUUID()); }

    static String stoneName(int ordinal) { return ordinal >= 0 && ordinal < Stone.values().length ? Stone.values()[ordinal].key : "?"; }

    private ShardNet() {}
}
