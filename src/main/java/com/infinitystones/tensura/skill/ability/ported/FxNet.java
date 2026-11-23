package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Server → client visual effects for ported powers (drawn by client/port/MagicCircles). One small packet per effect,
 * sent to every player near it, so everybody sees the same thing. Lag-safe: geometry is generated on the client.
 */
public final class FxNet {
    /** style bits: 0-1 stacked tiers (0..3), 4 rays, 8 heptagram, 16 fast spin. */
    public static final int TIERS_1 = 1, TIERS_2 = 2, TIERS_3 = 3, RAYS = 4, HEPTA = 8, FAST = 16;

    /** A magic circle. follow = entity id (centre = entity position + x/y/z offset) or -1 (absolute x/y/z). */
    public record Circle(double x, double y, double z, float nx, float ny, float nz, float radius, int color, int color2,
                         int duration, int style, int follow, int seed) implements CustomPacketPayload {
        public static final Type<Circle> TYPE = new Type<>(InfinityStones.id("port_fx_circle"));
        public static final StreamCodec<ByteBuf, Circle> CODEC = new StreamCodec<>() {
            @Override public Circle decode(ByteBuf b) {
                return new Circle(b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readFloat(), b.readFloat(),
                        b.readFloat(), b.readInt(), b.readInt(), b.readShort(), b.readByte(), b.readInt(), b.readInt());
            }
            @Override public void encode(ByteBuf b, Circle c) {
                b.writeDouble(c.x); b.writeDouble(c.y); b.writeDouble(c.z); b.writeFloat(c.nx); b.writeFloat(c.ny); b.writeFloat(c.nz);
                b.writeFloat(c.radius); b.writeInt(c.color); b.writeInt(c.color2); b.writeShort(Math.min(c.duration, 32000));
                b.writeByte(c.style); b.writeInt(c.follow); b.writeInt(c.seed);
            }
        };
        @Override public Type<Circle> type() { return TYPE; }
    }

    /** Client side inbox, drained by MagicCircles (keeps client classes out of this common class). */
    public static final ConcurrentLinkedQueue<Circle> INBOX = new ConcurrentLinkedQueue<>();

    public static long clientTicks() { return BeamNet.clientTicks; }

    // ---------------- server API ----------------

    public static void circle(ServerLevel level, Vec3 at, Vec3 normal, float radius, int color, int color2, int duration, int style) {
        Circle c = new Circle(at.x, at.y, at.z, (float) normal.x, (float) normal.y, (float) normal.z, radius, color & 0xFFFFFF,
                color2 & 0xFFFFFF, duration, style, -1, level.random.nextInt());
        PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, 192 + radius * 2, c);
    }

    /** Flat circle on the ground under/around an entity that follows it. */
    public static void circleOn(Entity e, double yOffset, float radius, int color, int color2, int duration, int style) {
        Circle c = new Circle(0, yOffset, 0, 0, 1, 0, radius, color & 0xFFFFFF, color2 & 0xFFFFFF, duration, style, e.getId(),
                e.level().random.nextInt());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, c);
    }

    /**
     * Keyed circle following an entity (offset + any normal). Sending the same key for the same entity again keeps the
     * circle alive without replaying its fade-in, so long toggles can refresh a short-lived circle every few ticks.
     */
    public static void circleKeyed(Entity e, int key, Vec3 offset, Vec3 normal, float radius, int color, int color2, int duration, int style) {
        Circle c = new Circle(offset.x, offset.y, offset.z, (float) normal.x, (float) normal.y, (float) normal.z, radius,
                color & 0xFFFFFF, color2 & 0xFFFFFF, duration, style, e.getId(), key == 0 ? 1 : key);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, c);
    }

    /** Keyed circle at a fixed point (see {@link #circleKeyed(Entity, int, Vec3, Vec3, float, int, int, int, int)}). */
    public static void circleKeyed(ServerLevel level, int key, Vec3 at, Vec3 normal, float radius, int color, int color2, int duration, int style) {
        Circle c = new Circle(at.x, at.y, at.z, (float) normal.x, (float) normal.y, (float) normal.z, radius, color & 0xFFFFFF,
                color2 & 0xFFFFFF, duration, style, -1, key == 0 ? 1 : key);
        PacketDistributor.sendToPlayersNear(level, null, at.x, at.y, at.z, 192 + radius * 2, c);
    }

    /** Stone colour + a brighter companion colour. */
    public static int light(Stone s) {
        int c = s.color();
        int r = Math.min(255, ((c >> 16) & 255) + 110), g = Math.min(255, ((c >> 8) & 255) + 110), b = Math.min(255, (c & 255) + 110);
        return (r << 16) | (g << 8) | b;
    }
    public static void circle(ServerLevel level, Vec3 at, Vec3 normal, float radius, Stone s, int duration, int style) {
        circle(level, at, normal, radius, s.color(), light(s), duration, style);
    }
    public static void circleOn(Entity e, double yOffset, float radius, Stone s, int duration, int style) {
        circleOn(e, yOffset, radius, s.color(), light(s), duration, style);
    }

    @EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("infinity_port_fx").playToClient(Circle.TYPE, Circle.CODEC, (msg, ctx) -> INBOX.add(msg));
        }
        private Network() {}
    }

    private FxNet() {}
}
