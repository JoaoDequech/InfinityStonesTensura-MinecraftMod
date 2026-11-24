package com.infinitystones.tensura.skill.ability.extra.v2d;

import com.infinitystones.tensura.InfinityStones;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Lucid Dream networking: the owner's mode (brush, size, last point) goes to the client so it can draw the ghost preview, and the
 * owner's clicks / scroll come back. The server recomputes the aimed cell itself, so the client only says what was pressed.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class LucidNet {
    /** active = the dream is on; anchor = the last placed point (line start), count = blocks standing now. */
    public record State(boolean active, int brush, int size, boolean hasAnchor, int ax, int ay, int az, int count) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(InfinityStones.id("extra_lucid_state"));
        public static final StreamCodec<ByteBuf, State> CODEC = StreamCodec.of((buf, s) -> {
            buf.writeBoolean(s.active); buf.writeByte(s.brush); buf.writeByte(s.size); buf.writeBoolean(s.hasAnchor);
            buf.writeInt(s.ax); buf.writeInt(s.ay); buf.writeInt(s.az); buf.writeShort(s.count);
        }, buf -> new State(buf.readBoolean(), buf.readByte(), buf.readByte(), buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readShort()));
        @Override public Type<State> type() { return TYPE; }
    }

    /** action: 0 place, 1 shape menu, 2 undo (Sneak + left), 3 size (value = direction), 4 stop. */
    public record Input(int action, int value) implements CustomPacketPayload {
        public static final Type<Input> TYPE = new Type<>(InfinityStones.id("extra_lucid_input"));
        public static final StreamCodec<ByteBuf, Input> CODEC = StreamCodec.of((buf, s) -> { buf.writeByte(s.action); buf.writeByte(s.value); },
                buf -> new Input(buf.readByte(), buf.readByte()));
        @Override public Type<Input> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1").optional();
        r.playToClient(State.TYPE, State.CODEC, (msg, ctx) -> ctx.enqueueWork(() ->
                com.infinitystones.tensura.skill.ability.extra.v2d.client.LucidClient.accept(msg)));
        r.playToServer(Input.TYPE, Input.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) LucidDream.input(sp, msg);
        }));
    }

    public static void to(ServerPlayer p, State s) { PacketDistributor.sendToPlayer(p, s); }

    private LucidNet() {}
}
