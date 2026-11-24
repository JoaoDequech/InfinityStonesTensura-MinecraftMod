package com.infinitystones.tensura.skill.ability.extra.v2d;

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

/**
 * Clockwork world effects of the v2d abilities (race gates, floating clocks, big digits, bombs). Same id = replace,
 * {@link #KILL} removes. Positions are world coordinates; the client animates on its own from {@code life}.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class V2dNet {
    public static final int GATE = 0, CLOCK = 1, DIGIT = 2, BOMB = 3, KILL = 9;
    public static final double RANGE = 128;
    private static final java.util.concurrent.atomic.AtomicInteger IDS = new java.util.concurrent.atomic.AtomicInteger(1);
    public static int newId() { return IDS.getAndIncrement(); }

    /**
     * kind, id (0 = one-shot), colour, accent colour, life (ticks), size, extra (shape-specific), a = position,
     * b = normal (zero = face the camera).
     */
    public record Pkt(int kind, int id, int color, int color2, int life, float size, float extra,
                      double ax, double ay, double az, double bx, double by, double bz) implements CustomPacketPayload {
        public static final Type<Pkt> TYPE = new Type<>(InfinityStones.id("extra_v2d_fx"));
        public static final StreamCodec<ByteBuf, Pkt> CODEC = StreamCodec.of((buf, f) -> {
            buf.writeByte(f.kind); buf.writeInt(f.id); buf.writeInt(f.color); buf.writeInt(f.color2); buf.writeShort(f.life);
            buf.writeFloat(f.size); buf.writeFloat(f.extra);
            buf.writeDouble(f.ax); buf.writeDouble(f.ay); buf.writeDouble(f.az);
            buf.writeDouble(f.bx); buf.writeDouble(f.by); buf.writeDouble(f.bz);
        }, buf -> new Pkt(buf.readByte(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readShort(), buf.readFloat(), buf.readFloat(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble()));
        public Vec3 a() { return new Vec3(ax, ay, az); }
        public Vec3 b() { return new Vec3(bx, by, bz); }
        @Override public Type<Pkt> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(Pkt.TYPE, Pkt.CODEC,
                (msg, ctx) -> ctx.enqueueWork(() -> com.infinitystones.tensura.skill.ability.extra.v2d.client.V2dClient.accept(msg)));
    }

    private static Pkt pkt(int kind, int id, int color, int color2, int life, float size, float extra, Vec3 a, Vec3 b) {
        return new Pkt(kind, id, color & 0xFFFFFF, color2 & 0xFFFFFF, life, size, extra, a.x, a.y, a.z, b.x, b.y, b.z);
    }

    public static void send(ServerLevel l, Vec3 near, Pkt f) {
        for (ServerPlayer sp : l.players())
            if (sp.position().distanceToSqr(near) <= RANGE * RANGE) PacketDistributor.sendToPlayer(sp, f);
    }

    /** Vertical ring gate with a giant clock face. number 1..99 is shown above it; start = checkered start/finish band. */
    public static void gate(ServerLevel l, int id, Vec3 center, Vec3 normal, float radius, int color, int number, boolean start, int life) {
        send(l, center, pkt(GATE, id, color, start ? 1 : 0, life, radius, number, center, normal));
    }

    /** Floating clock face. progress (0..1) is where the hands are now; they reach 1 when life ends. normal zero = faces the viewer. */
    public static void clock(ServerLevel l, int id, Vec3 center, Vec3 normal, float radius, int color, int accent, int life, float progress) {
        send(l, center, pkt(CLOCK, id, color, accent, life, radius, progress, center, normal));
    }

    /** The purple-green bomb clock; same progress rules as {@link #clock}, the pulse speeds up towards the end. */
    public static void bomb(ServerLevel l, int id, Vec3 center, float radius, int color, int accent, int life, float progress) {
        send(l, center, pkt(BOMB, id, color, accent, life, radius, progress, center, Vec3.ZERO));
    }

    /** Big glowing number facing the viewer (countdown). */
    public static void digit(ServerLevel l, Vec3 center, int value, float height, int color, int life) {
        send(l, center, pkt(DIGIT, 0, color, color, life, height, value, center, Vec3.ZERO));
    }

    public static void kill(ServerLevel l, Vec3 near, int id) {
        if (id != 0) send(l, near, pkt(KILL, id, 0, 0, 1, 0, 0, near, Vec3.ZERO));
    }

    private V2dNet() {}
}

