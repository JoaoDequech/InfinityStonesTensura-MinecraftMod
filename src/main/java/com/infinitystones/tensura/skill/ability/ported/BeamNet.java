package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Network side of the energy beam. The server decides everything (damage, world effects, level); clients only get
 * "entity X fires a beam of this look" and trace the visual from that entity's own synced eye/rotation, so every
 * player around sees the same beam. A C2S packet carries the temporary Alt + scroll level step.
 */
public final class BeamNet {
    /** Origin of the beam on the body (original energy_beams/*.json "body_part"). */
    public static final int ORIGIN_OFF_ARM = 0, ORIGIN_MAIN_ARM = 1, ORIGIN_HEAD = 2;

    /** What clients draw. glow/core = 0xRRGGBB; flags: 1 = lightning bolts (palladium:lightning), 2 = rainbow, 4 = pierces blocks. */
    public record Visual(int origin, int glow, int core, int level, float width, float maxDistance, int flags, float rotationSpeed) {
        public boolean lightning() { return (flags & 1) != 0; }
        public boolean rainbow() { return (flags & 2) != 0; }
        public boolean pierce() { return (flags & 4) != 0; }
    }

    /** Client-side copy: entity id → visual + the client tick it was last refreshed (filled by the S2C handler). */
    public static final Map<Integer, Visual> CLIENT = new ConcurrentHashMap<>();
    public static final Map<Integer, Long> CLIENT_SEEN = new ConcurrentHashMap<>();
    /** Local client tick counter used for expiry (set by the client tick). */
    public static volatile long clientTicks;

    public static void send(Entity caster, Visual v) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new Sync(caster.getId(), true, v));
    }

    public static void stop(Entity caster) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(caster, new Sync(caster.getId(), false, new Visual(0, 0, 0, 0, 0, 0, 0, 0)));
    }

    private static final StreamCodec<ByteBuf, Visual> VISUAL = new StreamCodec<>() {
        @Override public Visual decode(ByteBuf b) {
            return new Visual(b.readByte(), b.readInt(), b.readInt(), b.readByte(), b.readFloat(), b.readFloat(), b.readByte(), b.readFloat());
        }
        @Override public void encode(ByteBuf b, Visual v) {
            b.writeByte(v.origin()); b.writeInt(v.glow()); b.writeInt(v.core()); b.writeByte(v.level());
            b.writeFloat(v.width()); b.writeFloat(v.maxDistance()); b.writeByte(v.flags()); b.writeFloat(v.rotationSpeed());
        }
    };

    /** Server → client: entity {@code entityId} starts/refreshes ({@code on}) or stops its beam. */
    public record Sync(int entityId, boolean on, Visual visual) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(InfinityStones.id("port_beam"));
        public static final StreamCodec<ByteBuf, Sync> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Sync::entityId, ByteBufCodecs.BOOL, Sync::on, VISUAL, Sync::visual, Sync::new);
        @Override public Type<Sync> type() { return TYPE; }
    }

    /** Client → server: Alt + scroll while firing (delta +1 / -1). */
    public record Step(int delta) implements CustomPacketPayload {
        public static final Type<Step> TYPE = new Type<>(InfinityStones.id("port_beam_level"));
        public static final StreamCodec<ByteBuf, Step> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, Step::delta, Step::new);
        @Override public Type<Step> type() { return TYPE; }
    }

    @EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            PayloadRegistrar r = event.registrar("infinity_port_beam");
            r.playToClient(Sync.TYPE, Sync.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
                if (msg.on()) { CLIENT.put(msg.entityId(), msg.visual()); CLIENT_SEEN.put(msg.entityId(), clientTicks); }
                else { CLIENT.remove(msg.entityId()); CLIENT_SEEN.remove(msg.entityId()); }
            }));
            r.playToServer(Step.TYPE, Step.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer p) EnergyBeam.stepLevel(p, msg.delta());
            }));
        }
        private Network() {}
    }

    private BeamNet() {}
}
