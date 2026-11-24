package com.infinitystones.tensura.skill.ability.extra.v2a;

import com.infinitystones.tensura.InfinityStones;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server → client effects of the v2a abilities, drawn by {@code v2a.client.V2aClient}: the giant arm / hands / gauntlet
 * models and the sky shows of the Wish. Same id = update (state change), {@link #KILL} removes.
 * y0/y1/dur describe the current vertical move (the client interpolates from the moment the message arrives).
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class V2aNet {
    public static final int ARM = 1, HANDS = 2, GAUNTLET = 3, RAINBOW = 4, STARS = 5, KILL = 9;

    private static final AtomicInteger IDS = new AtomicInteger(3_000_000);
    public static int newId() { return IDS.getAndIncrement(); }

    public record Msg(int kind, int id, int state, int life, float scale, double x, double y, double z, float yaw,
                      double y0, double y1, int dur, float extra) implements CustomPacketPayload {
        public static final Type<Msg> TYPE = new Type<>(InfinityStones.id("v2a_fx"));
        public static final StreamCodec<ByteBuf, Msg> CODEC = StreamCodec.of((buf, m) -> {
            buf.writeByte(m.kind); buf.writeInt(m.id); buf.writeByte(m.state); buf.writeShort(m.life); buf.writeFloat(m.scale);
            buf.writeDouble(m.x); buf.writeDouble(m.y); buf.writeDouble(m.z); buf.writeFloat(m.yaw);
            buf.writeDouble(m.y0); buf.writeDouble(m.y1); buf.writeShort(m.dur); buf.writeFloat(m.extra);
        }, buf -> new Msg(buf.readByte(), buf.readInt(), buf.readByte(), buf.readShort(), buf.readFloat(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(),
                buf.readDouble(), buf.readDouble(), buf.readShort(), buf.readFloat()));
        @Override public Type<Msg> type() { return TYPE; }
    }

    /**
     * Creator's Hand cutscene lock for one player. mode 0 = free, 1 = held in the fist (camera shot + every input locked),
     * 2 = the caster (rooted, can still look and use the wheel). shotYaw = direction the shot looks along (degrees),
     * dist = camera distance from the fist. The server repeats it every second; the client frees itself when it stops.
     */
    public record Lock(int mode, int id, float shotYaw, float dist, float scale, int force, int ready) implements CustomPacketPayload {
        public static final Type<Lock> TYPE = new Type<>(InfinityStones.id("v2a_hand_lock"));
        public static final StreamCodec<ByteBuf, Lock> CODEC = StreamCodec.of((buf, m) -> {
            ByteBufCodecs.VAR_INT.encode(buf, m.mode); ByteBufCodecs.VAR_INT.encode(buf, m.id);
            buf.writeFloat(m.shotYaw); buf.writeFloat(m.dist); buf.writeFloat(m.scale);
            ByteBufCodecs.VAR_INT.encode(buf, m.force); ByteBufCodecs.VAR_INT.encode(buf, m.ready);
        }, buf -> new Lock(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
                buf.readFloat(), buf.readFloat(), buf.readFloat(),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));
        @Override public Type<Lock> type() { return TYPE; }
    }

    /** Caster controls while holding (client → server): 1 = squeeze (repeated while the button stays down), 2 = release. */
    public record Input(int action) implements CustomPacketPayload {
     public static final Type<Input> TYPE = new Type<>(InfinityStones.id("v2a_hand_input"));
     public static final StreamCodec<ByteBuf, Input> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Input::action, Input::new);
     @Override public Type<Input> type() { return TYPE; }
    }
    /** Gore burst of an executed body (entity id = the client entity whose texture the pieces use). */
    public record Gore(int entity, double x, double y, double z, float w, float h) implements CustomPacketPayload {
        public static final Type<Gore> TYPE = new Type<>(InfinityStones.id("v2a_hand_gore"));
        public static final StreamCodec<ByteBuf, Gore> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Gore::entity, ByteBufCodecs.DOUBLE, Gore::x, ByteBufCodecs.DOUBLE, Gore::y,
                ByteBufCodecs.DOUBLE, Gore::z, ByteBufCodecs.FLOAT, Gore::w, ByteBufCodecs.FLOAT, Gore::h, Gore::new);
        @Override public Type<Gore> type() { return TYPE; }
    }

    public record Target(UUID id, String name, int dist) {
        public static final StreamCodec<ByteBuf, Target> CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Target::id, ByteBufCodecs.STRING_UTF8, Target::name, ByteBufCodecs.VAR_INT, Target::dist, Target::new);
    }

    /** The player list of the Hand's menu (server → caster). */
    public record Targets(List<Target> list) implements CustomPacketPayload {
        public static final Type<Targets> TYPE = new Type<>(InfinityStones.id("v2a_hand_targets"));
        public static final StreamCodec<ByteBuf, Targets> CODEC = StreamCodec.composite(
                Target.CODEC.apply(ByteBufCodecs.list(64)), Targets::list, Targets::new);
        @Override public Type<Targets> type() { return TYPE; }
    }

    /** The caster picked a player in the menu (client → server, validated again there). */
    public record Pick(UUID id) implements CustomPacketPayload {
        public static final Type<Pick> TYPE = new Type<>(InfinityStones.id("v2a_hand_pick"));
        public static final StreamCodec<ByteBuf, Pick> CODEC = StreamCodec.composite(UUIDUtil.STREAM_CODEC, Pick::id, Pick::new);
        @Override public Type<Pick> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1").optional();
        r.playToClient(Msg.TYPE, Msg.CODEC,
                (msg, ctx) -> ctx.enqueueWork(() -> com.infinitystones.tensura.skill.ability.extra.v2a.client.V2aClient.accept(msg)));
        r.playToClient(Lock.TYPE, Lock.CODEC,
                (msg, ctx) -> ctx.enqueueWork(() -> com.infinitystones.tensura.skill.ability.extra.v2a.client.HandClient.lock(msg)));
        r.playToClient(Gore.TYPE, Gore.CODEC,
                (msg, ctx) -> ctx.enqueueWork(() -> com.infinitystones.tensura.skill.ability.extra.v2a.client.HandClient.gore(msg)));
        r.playToClient(Targets.TYPE, Targets.CODEC,
                (msg, ctx) -> ctx.enqueueWork(() -> com.infinitystones.tensura.skill.ability.extra.v2a.client.HandClient.targets(msg)));
        r.playToServer(Input.TYPE, Input.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
         if (ctx.player() instanceof ServerPlayer sp) CreatorHand.input(sp, msg.action());
        }));
        r.playToServer(Pick.TYPE, Pick.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) CreatorHand.pick(sp, msg.id());
        }));
    }

    public static Msg msg(int kind, int id, int state, int life, float scale, Vec3 at, float yaw, double y0, double y1, int dur, float extra) {
        return new Msg(kind, id, state, Math.min(32000, Math.max(1, life)), scale, at.x, at.y, at.z, yaw, y0, y1, Math.min(32000, Math.max(0, dur)), extra);
    }

    public static void send(ServerLevel l, Vec3 near, double range, Msg m) {
        for (ServerPlayer sp : l.players())
            if (sp.position().distanceToSqr(near) <= range * range) PacketDistributor.sendToPlayer(sp, m);
    }

    public static void kill(ServerLevel l, Vec3 near, int id) {
        send(l, near, 400, msg(KILL, id, 0, 1, 1, near, 0, 0, 0, 0, 0));
    }

    private V2aNet() {}
}
