package com.infinitystones.tensura.skill.ability.extra.v2a;

import com.infinitystones.tensura.InfinityStones;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

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

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(Msg.TYPE, Msg.CODEC,
                (msg, ctx) -> ctx.enqueueWork(() -> com.infinitystones.tensura.skill.ability.extra.v2a.client.V2aClient.accept(msg)));
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
