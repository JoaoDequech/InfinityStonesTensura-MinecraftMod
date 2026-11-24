package com.infinitystones.tensura.skill.ability.extra.v2c;

import com.infinitystones.tensura.InfinityStones;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** One small generic payload shared by the v2c visuals (painted sky, wisp, soul orb). */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class V2CNet {
    public static final int SKY = 0, SKY_END = 1, WISP = 2, WISP_END = 3, ORB = 4;

    private static final AtomicInteger IDS = new AtomicInteger(1);
    public static int newId() { return IDS.getAndIncrement(); }

    public record Msg(int kind, int id, int a, int b, float f, double x, double y, double z, double w) implements CustomPacketPayload {
        public static final Type<Msg> TYPE = new Type<>(InfinityStones.id("extra_v2c_fx"));
        public static final StreamCodec<ByteBuf, Msg> CODEC = StreamCodec.of((buf, m) -> {
            buf.writeByte(m.kind); buf.writeInt(m.id); buf.writeInt(m.a); buf.writeInt(m.b); buf.writeFloat(m.f);
            buf.writeDouble(m.x); buf.writeDouble(m.y); buf.writeDouble(m.z); buf.writeDouble(m.w);
        }, buf -> new Msg(buf.readByte(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readFloat(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble()));
        @Override public Type<Msg> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(Msg.TYPE, Msg.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> V2CClientBridge.accept(msg)));
    }

    public static Msg msg(int kind, int id, int a, int b, float f, Vec3 pos, double w) {
        return new Msg(kind, id, a, b, f, pos.x, pos.y, pos.z, w);
    }

    public static void to(ServerPlayer p, Msg m) { PacketDistributor.sendToPlayer(p, m); }

    /** Sends to every player of the level whose horizontal distance to c is at most range. */
    public static void near(ServerLevel l, Vec3 c, double range, Msg m) {
        for (ServerPlayer sp : l.players()) {
            double dx = sp.getX() - c.x, dz = sp.getZ() - c.z;
            if (dx * dx + dz * dz <= range * range) PacketDistributor.sendToPlayer(sp, m);
        }
    }

    public static @Nullable ServerPlayer player(MinecraftServer server, UUID id) { return server.getPlayerList().getPlayer(id); }

    private V2CNet() {}
}
