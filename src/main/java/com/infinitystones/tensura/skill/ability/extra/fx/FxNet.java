package com.infinitystones.tensura.skill.ability.extra.fx;

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
 * Server → client "AAA" effects for the new abilities: shapes drawn by {@code FxClient} with additive glow (beams,
 * shockwaves, domes, rune circles, cubes, portals, tunnels, crowns...) plus screen effects (shake, flash, tints).
 * Effects carry an id: sending the same id again replaces the effect (used to keep long effects alive / move them),
 * {@link #kill} removes it. Positions may follow an entity (entity id {@code ea}).
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class FxNet {
    public static final int BEAM = 0, TETHER = 1, SHOCKWAVE = 2, DOME = 3, RUNES = 4, CUBE = 5, FLARE = 6, PORTAL = 7,
            CROWN = 8, PILLAR = 9, TUNNEL = 10, PANEL = 11, CLOCK = 12, KILL = 13, SPIRAL = 14, ORB = 15, CHAIN = 16,
            SHAKE = 20, FLASH = 21, BROKEN_TINT = 22, NIGHTMARE = 23, AVATAR_TINT = 24;

    private static final AtomicInteger IDS = new AtomicInteger(1);
    public static int newId() { return IDS.getAndIncrement(); }

    /**
     * kind, id (0 = one-shot), colour (RGB), life in ticks, size (radius/width/strength), extra (shape-specific),
     * a / b points, ea / eb entity ids (-1 = none).
     */
    public record Fx(int kind, int id, int color, int life, float size, float extra,
                     double ax, double ay, double az, double bx, double by, double bz, int ea, int eb) implements CustomPacketPayload {
        public static final Type<Fx> TYPE = new Type<>(InfinityStones.id("extra_fx"));
        public static final StreamCodec<ByteBuf, Fx> CODEC = StreamCodec.of((buf, f) -> {
            buf.writeByte(f.kind); buf.writeInt(f.id); buf.writeInt(f.color); buf.writeShort(f.life);
            buf.writeFloat(f.size); buf.writeFloat(f.extra);
            buf.writeDouble(f.ax); buf.writeDouble(f.ay); buf.writeDouble(f.az);
            buf.writeDouble(f.bx); buf.writeDouble(f.by); buf.writeDouble(f.bz);
            buf.writeInt(f.ea); buf.writeInt(f.eb);
        }, buf -> new Fx(buf.readByte(), buf.readInt(), buf.readInt(), buf.readShort(), buf.readFloat(), buf.readFloat(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readInt(), buf.readInt()));
        public Vec3 a() { return new Vec3(ax, ay, az); }
        public Vec3 b() { return new Vec3(bx, by, bz); }
        @Override public Type<Fx> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(Fx.TYPE, Fx.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> ClientBridge.fx(msg)));
    }

    // ------------------------------------------------------------------ sending

    /** Range in blocks within which players receive world effects. */
    public static final double RANGE = 128;

    public static void send(ServerLevel l, Vec3 near, Fx fx) {
        for (ServerPlayer sp : l.players())
            if (sp.position().distanceToSqr(near) <= RANGE * RANGE) PacketDistributor.sendToPlayer(sp, fx);
    }

    public static void to(ServerPlayer p, Fx fx) { PacketDistributor.sendToPlayer(p, fx); }

    private static Fx fx(int kind, int id, int color, int life, float size, float extra, Vec3 a, Vec3 b, int ea, int eb) {
        return new Fx(kind, id, color & 0xFFFFFF, life, size, extra, a.x, a.y, a.z, b.x, b.y, b.z, ea, eb);
    }

    public static void beam(ServerLevel l, Vec3 a, Vec3 b, int color, float width, int life) {
        send(l, a, fx(BEAM, 0, color, life, width, 0, a, b, -1, -1));
    }

    /** Beam that follows two entities (eb = -1: to the fixed point b). Keep alive by re-sending with the same id. */
    public static void tether(ServerLevel l, int id, Entity from, Entity to, int color, float width, int life) {
        send(l, from.position(), fx(TETHER, id, color, life, width, 0, from.position(), to.position(), from.getId(), to.getId()));
    }

    public static void tetherPoint(ServerLevel l, int id, Entity from, Vec3 to, int color, float width, int life) {
        send(l, to, fx(TETHER, id, color, life, width, 0, from.position(), to, from.getId(), -1));
    }

    /** Chain of links (soul chain / pact) between an entity and a point or entity. */
    public static void chain(ServerLevel l, int id, Entity from, Vec3 to, Entity toEntity, int color, int life) {
        send(l, to, fx(CHAIN, id, color, life, 0.18F, 0, from.position(), to, from.getId(), toEntity == null ? -1 : toEntity.getId()));
    }

    /** Expanding ring on the ground (or tilted when extra = 1: vertical facing b). */
    public static void shockwave(ServerLevel l, Vec3 c, float radius, int color, int life) {
        send(l, c, fx(SHOCKWAVE, 0, color, life, radius, 0, c, Vec3.ZERO, -1, -1));
    }

    public static void dome(ServerLevel l, int id, Vec3 c, float radius, int color, int life) {
        send(l, c, fx(DOME, id, color, life, radius, 0, c, Vec3.ZERO, -1, -1));
    }

    /** Rotating rune circle lying on the ground. extra = speed multiplier. */
    public static void runes(ServerLevel l, int id, Vec3 c, float radius, int color, int life, float spin) {
        send(l, c, fx(RUNES, id, color, life, radius, spin, c, Vec3.ZERO, -1, -1));
    }

    /** Glowing box; follows entity ea when given. */
    public static void cube(ServerLevel l, int id, Vec3 c, float half, int color, int life, int entity) {
        send(l, c, fx(CUBE, id, color, life, half, 0, c, Vec3.ZERO, entity, -1));
    }

    public static void flare(ServerLevel l, Vec3 c, float size, int color, int life) {
        send(l, c, fx(FLARE, 0, color, life, size, 0, c, Vec3.ZERO, -1, -1));
    }

    /** Oval rift whose face points along normal. */
    public static void portal(ServerLevel l, int id, Vec3 c, Vec3 normal, float size, int color, int life) {
        send(l, c, fx(PORTAL, id, color, life, size, 0, c, normal, -1, -1));
    }

    /** Spinning crown of light above an entity's head. */
    public static void crown(ServerLevel l, int id, Entity e, int color, int life) {
        send(l, e.position(), fx(CROWN, id, color, life, Math.max(0.35F, e.getBbWidth() * 0.6F), 0, e.position(), Vec3.ZERO, e.getId(), -1));
    }

    public static void pillar(ServerLevel l, int id, Vec3 base, float radius, float height, int color, int life) {
        send(l, base, fx(PILLAR, id, color, life, radius, height, base, Vec3.ZERO, -1, -1));
    }

    /** Rings marching from a to b (fold / portals). */
    public static void tunnel(ServerLevel l, Vec3 a, Vec3 b, float radius, int color, int life) {
        send(l, a, fx(TUNNEL, 0, color, life, radius, 0, a, b, -1, -1));
        if (a.distanceToSqr(b) > 40 * 40) send(l, b, fx(TUNNEL, 0, color, life, radius, 0, a, b, -1, -1));
    }

    /** Flat rippling panel (mirror). b = normal; size = half width. */
    public static void panel(ServerLevel l, int id, Vec3 c, Vec3 normal, float half, int color, int life) {
        send(l, c, fx(PANEL, id, color, life, half, 0, c, normal, -1, -1));
    }

    /** Clock dial on the ground with ticking hands. extra = hand speed (negative = backwards). */
    public static void clock(ServerLevel l, int id, Vec3 c, float radius, int color, int life, float speed) {
        send(l, c, fx(CLOCK, id, color, life, radius, speed, c, Vec3.ZERO, -1, -1));
    }

    /** Spiral of light rising (or falling when extra < 0) around a point / entity. */
    public static void spiral(ServerLevel l, Vec3 c, float radius, float height, int color, int life, int entity) {
        send(l, c, fx(SPIRAL, 0, color, life, radius, height, c, Vec3.ZERO, entity, -1));
    }

    /** Pulsing energy orb; follows entity ea (offset b) when given. */
    public static void orb(ServerLevel l, int id, Vec3 c, float radius, int color, int life, int entity, Vec3 offset) {
        send(l, c, fx(ORB, id, color, life, radius, 0, c, offset, entity, -1));
    }

    public static void kill(ServerLevel l, Vec3 near, int id) {
        if (id != 0) send(l, near, fx(KILL, id, 0, 1, 0, 0, near, Vec3.ZERO, -1, -1));
    }

    // ------------------------------------------------------------------ screen effects

    /** Camera shake for players within radius of c, fading with distance. */
    public static void shake(ServerLevel l, Vec3 c, double radius, float strength, int ticks) {
        for (ServerPlayer sp : l.players()) {
            double d = sp.position().distanceTo(c);
            if (d <= radius) to(sp, fx(SHAKE, 0, 0, ticks, (float) (strength * (1 - d / radius * 0.7)), 0, c, Vec3.ZERO, -1, -1));
        }
    }

    public static void flash(ServerPlayer p, int color, float alpha, int ticks) {
        to(p, fx(FLASH, 0, color, ticks, alpha, 0, p.position(), Vec3.ZERO, -1, -1));
    }

    public static void flashAround(ServerLevel l, Vec3 c, double radius, int color, float alpha, int ticks) {
        for (ServerPlayer sp : l.players()) if (sp.position().distanceToSqr(c) <= radius * radius) flash(sp, color, alpha, ticks);
    }

    /** Full-screen persistent tint (kind BROKEN_TINT / NIGHTMARE / AVATAR_TINT); life 0 = off. */
    public static void screen(ServerPlayer p, int kind, int life) {
        to(p, fx(kind, -kind, 0, life, 1, 0, p.position(), Vec3.ZERO, -1, -1));
    }

    private FxNet() {}
}
