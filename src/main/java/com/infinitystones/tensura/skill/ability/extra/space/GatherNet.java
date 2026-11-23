package com.infinitystones.tensura.skill.ability.extra.space;

import com.infinitystones.tensura.InfinityStones;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.List;

/** Spiritual Gathering payloads: caster menu, confirm, per-participant state, outer barrier, angelic hand, prayer. */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class GatherNet {
    /** One online player offered in the menu. */
    public record Entry(String uuid, String name, String dim) {
        public static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Entry::uuid, ByteBufCodecs.STRING_UTF8, Entry::name,
                ByteBufCodecs.STRING_UTF8, Entry::dim, Entry::new);
    }

    public record Open(List<Entry> players) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(InfinityStones.id("extra_gather_open"));
        public static final StreamCodec<ByteBuf, Open> CODEC = Entry.CODEC.apply(ByteBufCodecs.list(64)).map(Open::new, Open::players);
        @Override public Type<Open> type() { return TYPE; }
    }

    /** all = "take everyone"; otherwise only the listed uuids (empty = only the caster). */
    public record Confirm(boolean all, List<String> ids) implements CustomPacketPayload {
        public static final Type<Confirm> TYPE = new Type<>(InfinityStones.id("extra_gather_confirm"));
        public static final StreamCodec<ByteBuf, Confirm> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Confirm::all,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)), Confirm::ids, Confirm::new);
        @Override public Type<Confirm> type() { return TYPE; }
    }

    /**
     * What a participant sees. phase 0 = nothing, 1 = scenery on, 2 = scenery dissolving over {@code fade} ticks.
     * locked = may only look around (gathered players); ids = entity ids of every participant (everyone else is hidden).
     */
    public record State(int phase, boolean caster, boolean locked, double cx, double cy, double cz, float ringR, float sceneR,
                        long seed, int fade, int[] ids) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(InfinityStones.id("extra_gather_state"));
        public static final StreamCodec<FriendlyByteBuf, State> CODEC = StreamCodec.of((buf, s) -> {
            buf.writeByte(s.phase); buf.writeBoolean(s.caster); buf.writeBoolean(s.locked);
            buf.writeDouble(s.cx); buf.writeDouble(s.cy); buf.writeDouble(s.cz);
            buf.writeFloat(s.ringR); buf.writeFloat(s.sceneR); buf.writeLong(s.seed); buf.writeVarInt(s.fade);
            buf.writeVarInt(s.ids.length);
            for (int id : s.ids) buf.writeVarInt(id);
        }, buf -> {
            int phase = buf.readByte(); boolean caster = buf.readBoolean(), locked = buf.readBoolean();
            double cx = buf.readDouble(), cy = buf.readDouble(), cz = buf.readDouble();
            float ringR = buf.readFloat(), sceneR = buf.readFloat(); long seed = buf.readLong(); int fade = buf.readVarInt();
            int n = Math.min(buf.readVarInt(), 128);
            int[] ids = new int[n];
            for (int i = 0; i < n; i++) ids[i] = buf.readVarInt();
            return new State(phase, caster, locked, cx, cy, cz, ringR, sceneR, seed, fade, ids);
        });
        @Override public Type<State> type() { return TYPE; }
    }

    /** The glowing sphere outsiders see around a running gathering. */
    public record Barrier(boolean on, int id, String dim, double cx, double cy, double cz, float radius) implements CustomPacketPayload {
        public static final Type<Barrier> TYPE = new Type<>(InfinityStones.id("extra_gather_barrier"));
        public static final StreamCodec<FriendlyByteBuf, Barrier> CODEC = StreamCodec.of((buf, s) -> {
            buf.writeBoolean(s.on); buf.writeVarInt(s.id); buf.writeUtf(s.dim);
            buf.writeDouble(s.cx); buf.writeDouble(s.cy); buf.writeDouble(s.cz); buf.writeFloat(s.radius);
        }, buf -> new Barrier(buf.readBoolean(), buf.readVarInt(), buf.readUtf(128), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat()));
        @Override public Type<Barrier> type() { return TYPE; }
    }

    /** Portal + angelic hand animation (purely visual): portal centre, direction towards the target, grab point. */
    public record Hand(int target, double px, double py, double pz, double nx, double ny, double nz,
                       double tx, double ty, double tz, int life, boolean back) implements CustomPacketPayload {
        public static final Type<Hand> TYPE = new Type<>(InfinityStones.id("extra_gather_hand"));
        public static final StreamCodec<FriendlyByteBuf, Hand> CODEC = StreamCodec.of((buf, s) -> {
            buf.writeVarInt(s.target);
            buf.writeDouble(s.px); buf.writeDouble(s.py); buf.writeDouble(s.pz);
            buf.writeDouble(s.nx); buf.writeDouble(s.ny); buf.writeDouble(s.nz);
            buf.writeDouble(s.tx); buf.writeDouble(s.ty); buf.writeDouble(s.tz);
            buf.writeVarInt(s.life); buf.writeBoolean(s.back);
        }, buf -> new Hand(buf.readVarInt(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readVarInt(), buf.readBoolean()));
        @Override public Type<Hand> type() { return TYPE; }
    }

    /** A player without any stone pressed the wheel key. */
    public record Pray() implements CustomPacketPayload {
        public static final Type<Pray> TYPE = new Type<>(InfinityStones.id("extra_gather_pray"));
        public static final StreamCodec<ByteBuf, Pray> CODEC = StreamCodec.unit(new Pray());
        @Override public Type<Pray> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1").optional();
        r.playToClient(Open.TYPE, Open.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> GatherBridge.open(msg)));
        r.playToClient(State.TYPE, State.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> GatherBridge.state(msg)));
        r.playToClient(Barrier.TYPE, Barrier.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> GatherBridge.barrier(msg)));
        r.playToClient(Hand.TYPE, Hand.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> GatherBridge.hand(msg)));
        r.playToServer(Confirm.TYPE, Confirm.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) SpiritualGathering.confirm(sp, msg);
        }));
        r.playToServer(Pray.TYPE, Pray.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) SpiritualGathering.pray(sp);
        }));
    }

    private GatherNet() {}
}
