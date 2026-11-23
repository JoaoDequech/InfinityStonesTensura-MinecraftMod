package com.infinitystones.tensura.skill.ability.extra.reality;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Creation Chest payloads: open the picker (server → owner), the choice (client → server, count 0 = cancelled) and the
 * hologram script of the chest (server → everyone nearby).
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class ChestNet {
    public static final int SPAWN = 0, OPEN = 1, ITEM = 2, CLOSE = 3, FOLD = 4;
    public static final double RANGE = 96;

    /** The picker opens; last = what the player chose the previous time (empty = nothing). */
    public record Open(ItemStack last, int lastCount) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(InfinityStones.id("extra_chest_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC, Open::last, ByteBufCodecs.VAR_INT, Open::lastCount, Open::new);
        @Override public Type<Open> type() { return TYPE; }
    }

    /** The chosen item (components as the client knows them: the server re-checks them) and how many. count 0 = cancel. */
    public record Request(ItemStack stack, int count) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(InfinityStones.id("extra_chest_request"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_STREAM_CODEC, Request::stack, ByteBufCodecs.VAR_INT, Request::count, Request::new);
        @Override public Type<Request> type() { return TYPE; }
    }

    /**
     * One step of the hologram chest's script. op: SPAWN / OPEN (lid) / ITEM (stack x count falls into the chest over
     * ticks) / CLOSE (lid) / FOLD (despawns). Position and yaw (block-facing degrees) only matter for SPAWN.
     */
    public record Holo(int id, int op, double x, double y, double z, float yaw, ItemStack stack, int count, int ticks) implements CustomPacketPayload {
        public static final Type<Holo> TYPE = new Type<>(InfinityStones.id("extra_chest_holo"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Holo> CODEC = StreamCodec.of((buf, h) -> {
            buf.writeVarInt(h.id); buf.writeByte(h.op);
            buf.writeDouble(h.x); buf.writeDouble(h.y); buf.writeDouble(h.z); buf.writeFloat(h.yaw);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, h.stack);
            buf.writeVarInt(h.count); buf.writeVarInt(h.ticks);
        }, buf -> new Holo(buf.readVarInt(), buf.readByte(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(),
                ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt()));
        @Override public Type<Holo> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1").optional();
        r.playToClient(Open.TYPE, Open.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> ChestBridge.open(msg)));
        r.playToClient(Holo.TYPE, Holo.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> ChestBridge.holo(msg)));
        r.playToServer(Request.TYPE, Request.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) CreationChest.request(sp, msg);
        }));
    }

    static void open(ServerPlayer p, ItemStack last, int count) {
        PacketDistributor.sendToPlayer(p, new Open(last, count));
    }

    static void holo(ServerLevel l, Vec3 at, Holo h) {
        for (ServerPlayer sp : l.players()) if (sp.position().distanceToSqr(at) <= RANGE * RANGE) PacketDistributor.sendToPlayer(sp, h);
    }

    private ChestNet() {}
}
