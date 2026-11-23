package com.infinitystones.tensura.skill.ability.extra.power;

import com.infinitystones.tensura.InfinityStones;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Power-stone client effects (S2C), drawn by {@code power.client.PowerFxClient}: the Celestial Nuke sky sequence
 * (layered magic circles, charge, colossal beam), generic layered magic circles (any plane, may follow an entity) and
 * the braided multi-colour Convergence beam. Same id = replace, {@link #kill} removes.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class PowerNet {
    public static final int NUKE = 1, CIRCLE = 2, BRAID = 3, KILL = 5;
    /** Circle style bits. */
    public static final int RAINBOW = 1, RAYS = 2, SIMPLE = 4, GLYPHS_ONLY = 8;
    /** Stone colours in wheel order (mind, power, space, reality, soul, time). */
    public static final int[] STONE_COLORS = {0xF2D02B, 0xA43CE6, 0x2F8CFF, 0xE22B3B, 0xF28A1A, 0x2BD56A};

    private static final AtomicInteger IDS = new AtomicInteger(1_000_000);
    public static int newId() { return IDS.getAndIncrement(); }

    /**
     * kind, id (0 = one-shot), style bits, colours, life (ticks), size, extra, points a/b/c, entity (-1 = none).
     * NUKE: a = sky centre, b = ground target, c = caster, size = circle radius, extra = fire tick.
     * CIRCLE: a = centre (offset when following entity), b = plane normal, size = radius, extra = spin speed.
     * BRAID: a → b, size = width, style = stone mask (bit = Stone.ordinal()).
     */
    public record Msg(int kind, int id, int style, int color, int color2, int life, float size, float extra,
                      double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz,
                      int entity) implements CustomPacketPayload {
        public static final Type<Msg> TYPE = new Type<>(InfinityStones.id("extra_power_fx"));
        public static final StreamCodec<ByteBuf, Msg> CODEC = StreamCodec.of((buf, f) -> {
            buf.writeByte(f.kind); buf.writeInt(f.id); buf.writeInt(f.style); buf.writeInt(f.color); buf.writeInt(f.color2);
            buf.writeShort(f.life); buf.writeFloat(f.size); buf.writeFloat(f.extra);
            buf.writeDouble(f.ax); buf.writeDouble(f.ay); buf.writeDouble(f.az);
            buf.writeDouble(f.bx); buf.writeDouble(f.by); buf.writeDouble(f.bz);
            buf.writeDouble(f.cx); buf.writeDouble(f.cy); buf.writeDouble(f.cz);
            buf.writeInt(f.entity);
        }, buf -> new Msg(buf.readByte(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readShort(),
                buf.readFloat(), buf.readFloat(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readInt()));
        public Vec3 a() { return new Vec3(ax, ay, az); }
        public Vec3 b() { return new Vec3(bx, by, bz); }
        public Vec3 c() { return new Vec3(cx, cy, cz); }
        @Override public Type<Msg> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(Msg.TYPE, Msg.CODEC,
                (msg, ctx) -> ctx.enqueueWork(() -> com.infinitystones.tensura.skill.ability.extra.power.client.PowerFxClient.accept(msg)));
    }

    public static void send(ServerLevel l, Vec3 near, double range, Msg m) {
        for (ServerPlayer sp : l.players())
            if (sp.position().distanceToSqr(near) <= range * range) PacketDistributor.sendToPlayer(sp, m);
    }

    private static Msg msg(int kind, int id, int style, int c1, int c2, int life, float size, float extra, Vec3 a, Vec3 b, Vec3 c, int e) {
        return new Msg(kind, id, style, c1 & 0xFFFFFF, c2 & 0xFFFFFF, Math.min(32000, life), size, extra, a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z, e);
    }

    /** The full Celestial Nuke sky sequence; impact at fireTick. Sent far (sky effect visible from 224 blocks). */
    public static void nuke(ServerLevel l, int id, Vec3 sky, Vec3 ground, Vec3 caster, float radius, int fireTick, int life, int color, int accent) {
        send(l, ground, 224, msg(NUKE, id, 0, color, accent, life, radius, fireTick, sky, ground, caster, -1));
    }

    /** Layered magic circle in the plane of normal; follows entity (centre = entity pos + a) when given. */
    public static void circle(ServerLevel l, int id, Vec3 c, Vec3 normal, float radius, int color, int accent, int life, float spin, int style, Entity follow) {
        Vec3 near = follow == null ? c : follow.position();
        send(l, near, 160, msg(CIRCLE, id, style, color, accent, life, radius, spin, c, normal, Vec3.ZERO, follow == null ? -1 : follow.getId()));
    }

    public static void circle(ServerLevel l, int id, Vec3 c, Vec3 normal, float radius, int color, int accent, int life, float spin, int style) {
        circle(l, id, c, normal, radius, color, accent, life, spin, style, null);
    }

    /** Braided beam of the given stone colours (mask bits = Stone ordinals). */
    public static void braid(ServerLevel l, int id, Vec3 a, Vec3 b, float width, int mask, int life) {
        if (id == 0) id = newId();
        send(l, a, 160, msg(BRAID, id, mask, 0xFFFFFF, 0xFFFFFF, life, width, 0, a, b, Vec3.ZERO, -1));
        if (a.distanceToSqr(b) > 80 * 80) send(l, b, 96, msg(BRAID, id, mask, 0xFFFFFF, 0xFFFFFF, life, width, 0, a, b, Vec3.ZERO, -1));
    }

    public static void kill(ServerLevel l, Vec3 near, int id) {
        if (id != 0) send(l, near, 224, msg(KILL, id, 0, 0, 0, 1, 0, 0, near, Vec3.ZERO, Vec3.ZERO, -1));
    }

    /** Lighter tint (towards white) of an RGB colour. */
    public static int tint(int rgb, float k) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        r = (int) (r + (255 - r) * k); g = (int) (g + (255 - g) * k); b = (int) (b + (255 - b) * k);
        return (r << 16) | (g << 8) | b;
    }

    private PowerNet() {}
}
