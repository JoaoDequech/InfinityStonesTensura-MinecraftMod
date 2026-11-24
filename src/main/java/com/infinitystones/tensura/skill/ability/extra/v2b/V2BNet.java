package com.infinitystones.tensura.skill.ability.extra.v2b;

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

/** One small payload for the visuals of the four v2b powers (the client draws them; the server stays authoritative). */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class V2BNet {
    public static final int STARS = 0, STARS_CLEAR = 1, RIDE = 2, PING = 3, PING_CLEAR = 4, BUBBLE = 5, SCALE = 6, SELECT = 7,
            SELECT_CLEAR = 8, CONFUSE = 9;

    /** kind, key (replaces the previous one with the same key), colour, life in ticks, data, points (x,y,z triples). */
    public record Msg(int kind, int id, int color, int life, int data, double[] pts) implements CustomPacketPayload {
        public static final Type<Msg> TYPE = new Type<>(InfinityStones.id("v2b_fx"));
        public static final StreamCodec<ByteBuf, Msg> CODEC = StreamCodec.of((buf, m) -> {
            buf.writeByte(m.kind); buf.writeInt(m.id); buf.writeInt(m.color); buf.writeInt(m.life); buf.writeInt(m.data);
            int n = Math.min(m.pts.length, 40);
            buf.writeByte(n);
            for (int i = 0; i < n; i++) buf.writeDouble(m.pts[i]);
        }, buf -> {
            int kind = buf.readByte(), id = buf.readInt(), color = buf.readInt(), life = buf.readInt(), data = buf.readInt();
            int n = Math.min(buf.readUnsignedByte(), 40);
            double[] p = new double[n];
            for (int i = 0; i < n; i++) p[i] = buf.readDouble();
            return new Msg(kind, id, color, life, data, p);
        });
        @Override public Type<Msg> type() { return TYPE; }
    }

    public static Msg msg(int kind, int id, int color, int life, int data, double... pts) { return new Msg(kind, id, color, life, data, pts); }

    public static double[] pts(Vec3... v) {
        double[] a = new double[v.length * 3];
        for (int i = 0; i < v.length; i++) { a[i * 3] = v[i].x; a[i * 3 + 1] = v[i].y; a[i * 3 + 2] = v[i].z; }
        return a;
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(Msg.TYPE, Msg.CODEC,
                (msg, ctx) -> ctx.enqueueWork(() -> V2BBridge.accept(msg)));
    }

    public static void to(ServerPlayer p, Msg m) { PacketDistributor.sendToPlayer(p, m); }

    public static void near(ServerLevel l, Vec3 at, double range, Msg m) {
        for (ServerPlayer sp : l.players()) if (sp.position().distanceToSqr(at) <= range * range) PacketDistributor.sendToPlayer(sp, m);
    }

    private V2BNet() {}
}
