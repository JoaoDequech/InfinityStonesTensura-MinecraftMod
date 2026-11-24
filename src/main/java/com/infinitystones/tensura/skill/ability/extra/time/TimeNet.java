package com.infinitystones.tensura.skill.ability.extra.time;

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
 * Time stone client effects (own payload, drawn by {@code time.client.TimeClient}): climate zones (outline, clouds,
 * precipitation, fog, aurora...), stasis bubbles, hourglasses, lapse fields, era waves, rewind ghosts, restoration waves.
 * Same id = replace (keep alive by re-sending), {@link #KILL} removes.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class TimeNet {
    public static final int ZONE = 0, STASIS = 1, FROZEN = 2, HOURGLASS = 3, LAPSE = 4, ERA = 5, GHOST = 6, RESTORE = 7,
            MARK_RING = 8, KILL = 9;

    /** Climates (zone extra). */
    public static final int C_NONE = 0, C_STORM = 1, C_RAIN = 2, C_SNOW = 3, C_FOG = 4, C_SUN = 5, C_AURORA = 6, C_SAND = 7,
            C_BLIZZARD = 8, C_ASH = 9, C_CHERRY = 10, C_SUPERCELL = 11, C_METEOR = 12, C_TOXIC = 13, C_STARFALL = 14, C_HEAT = 15, C_RAINBOW = 16;

    /** Climates whose sky is forced to night for everybody inside the zone. */
    public static boolean night(int climate) { return climate == C_METEOR || climate == C_STARFALL; }

    /** Wind gust phase shared by server pushes and client visuals: 0..1 while a gust blows, -1 otherwise (zone age in ticks). */
    public static float gust(int zoneId, float ageTicks) {
        float cyc = ageTicks % 90F;
        return cyc < 36F ? (float) Math.sin(cyc / 36F * Math.PI) : -1F;
    }

    /** Direction (radians) of the gust that blows at this zone age. */
    public static double gustAngle(int zoneId, float ageTicks) { return zoneId * 1.713 + Math.floor(ageTicks / 90F) * 2.399; }

    private static final AtomicInteger IDS = new AtomicInteger(1);
    /** Ids live in their own range so they never collide with FxNet ids on the client (separate store anyway). */
    public static int newId() { return IDS.getAndIncrement(); }

    public record T(int kind, int id, int color, int life, float size, float extra,
                    double ax, double ay, double az, double bx, double by, double bz, int ea) implements CustomPacketPayload {
        public static final Type<T> TYPE = new Type<>(InfinityStones.id("extra_time_fx"));
        public static final StreamCodec<ByteBuf, T> CODEC = StreamCodec.of((buf, f) -> {
            buf.writeByte(f.kind); buf.writeInt(f.id); buf.writeInt(f.color); buf.writeInt(f.life);
            buf.writeFloat(f.size); buf.writeFloat(f.extra);
            buf.writeDouble(f.ax); buf.writeDouble(f.ay); buf.writeDouble(f.az);
            buf.writeDouble(f.bx); buf.writeDouble(f.by); buf.writeDouble(f.bz);
            buf.writeInt(f.ea);
        }, buf -> new T(buf.readByte(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readFloat(), buf.readFloat(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt()));
        public Vec3 a() { return new Vec3(ax, ay, az); }
        public Vec3 b() { return new Vec3(bx, by, bz); }
        @Override public Type<T> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(T.TYPE, T.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> TimeClientBridge.accept(msg)));
    }

    /** Range within which players receive time effects (zones are big and visible from afar). */
    public static final double RANGE = 160;

    public static void send(ServerLevel l, Vec3 near, T t) {
        for (ServerPlayer sp : l.players())
            if (sp.position().distanceToSqr(near) <= RANGE * RANGE) PacketDistributor.sendToPlayer(sp, t);
    }

    public static T of(int kind, int id, int color, int life, float size, float extra, Vec3 a, Vec3 b, int ea) {
        return new T(kind, id, color & 0xFFFFFF, life, size, extra, a.x, a.y, a.z, b.x, b.y, b.z, ea);
    }

    /** Zones refresh every 2 s (life 5 s): one small packet per player in range. */
    public static final int ZONE_LIFE = 100, ZONE_REFRESH = 40;

    public static void zone(ServerLevel l, int id, Vec3 c, float radius, int climate, int life, int ageTicks) {
        send(l, c, of(ZONE, id, 0x2BD56A, Math.max(life, ZONE_LIFE), radius, climate, c, new Vec3(ageTicks, 0, 0), -1));
    }

    public static void stasis(ServerLevel l, int id, Vec3 c, float radius, int life) {
        send(l, c, of(STASIS, id, 0x2BD56A, life, radius, 0, c, Vec3.ZERO, -1));
    }

    public static void frozen(ServerLevel l, int id, Entity e, int life) {
        send(l, e.position(), of(FROZEN, id, 0x2BD56A, life, Math.max(0.5F, e.getBbWidth()), e.getBbHeight(), e.position(), Vec3.ZERO, e.getId()));
    }

    /** extra: 0 = sand falling (marked), 1 = rewinding (sand rising). */
    public static void hourglass(ServerLevel l, int id, Entity e, int life, float progress, boolean rewinding) {
        send(l, e.position(), of(HOURGLASS, id, 0x2BD56A, life, progress, rewinding ? 1 : 0, e.position(), Vec3.ZERO, e.getId()));
    }

    /** speed > 0 fast field, < 0 slow field. */
    public static void lapse(ServerLevel l, int id, Vec3 c, float radius, float speed, int life) {
        send(l, c, of(LAPSE, id, speed > 0 ? 0x2BD56A : 0x1E9C8C, life, radius, speed, c, Vec3.ZERO, -1));
    }

    public static void era(ServerLevel l, Vec3 c, float radius, boolean aging, int life) {
        send(l, c, of(ERA, 0, aging ? 0x2BD56A : 0x9CFFC4, life, radius, aging ? 1 : -1, c, Vec3.ZERO, -1));
    }

    public static void ghost(ServerLevel l, Vec3 pos, float width, float height, int delay, int life, float yaw) {
        send(l, pos, of(GHOST, 0, 0x2BD56A, life + delay, width, height, pos, new Vec3(delay, yaw, 0), -1));
    }

    public static void restore(ServerLevel l, Vec3 c, float radius, int life) {
        send(l, c, of(RESTORE, 0, 0x2BD56A, life, radius, 0, c, Vec3.ZERO, -1));
    }

    /** Expanding marker ring (placing/moving a zone). */
    public static void markRing(ServerLevel l, Vec3 c, float radius, int color, int life) {
        send(l, c, of(MARK_RING, 0, color, life, radius, 0, c, Vec3.ZERO, -1));
    }

    public static void kill(ServerLevel l, Vec3 near, int id) {
        if (id != 0) send(l, near, of(KILL, id, 0, 1, 0, 0, near, Vec3.ZERO, -1));
    }

    private TimeNet() {}
}
