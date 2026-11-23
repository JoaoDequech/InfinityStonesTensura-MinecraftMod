package com.infinitystones.tensura.skill.ability.extra.space;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.List;

/**
 * Space Stone payloads: Area Heist state (selection box / placing ghost, owner only), Area Heist in-world input,
 * Abyss Watch holographic panel.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class SpaceNet {
    /**
     * Area Heist mode for its owner. mode 0 = off, 1 = selecting, 2 = placing.
     * Corners (has flags), placing transform, and (only when {@code blocks} is true) the stored blocks as global
     * block-state ids (-1 = empty), index = (y * sz + z) * sx + x.
     */
    public record HeistState(int mode, boolean hasA, int ax, int ay, int az, boolean hasB, int bx, int by, int bz,
                             int rot, boolean mirror, int dist, boolean blocks, int sx, int sy, int sz, int[] states,
                             int index, int count) implements CustomPacketPayload {
        public static final Type<HeistState> TYPE = new Type<>(InfinityStones.id("extra_space_heist_state"));
        public static final StreamCodec<FriendlyByteBuf, HeistState> CODEC = StreamCodec.of((buf, s) -> {
            buf.writeByte(s.mode);
            buf.writeBoolean(s.hasA); buf.writeInt(s.ax); buf.writeInt(s.ay); buf.writeInt(s.az);
            buf.writeBoolean(s.hasB); buf.writeInt(s.bx); buf.writeInt(s.by); buf.writeInt(s.bz);
            buf.writeByte(s.rot); buf.writeBoolean(s.mirror); buf.writeVarInt(s.dist);
            buf.writeBoolean(s.blocks);
            buf.writeVarInt(s.sx); buf.writeVarInt(s.sy); buf.writeVarInt(s.sz);
            if (s.blocks) {
                buf.writeVarInt(s.states.length);
                for (int v : s.states) buf.writeVarInt(v + 1);
            }
            buf.writeVarInt(s.index); buf.writeVarInt(s.count);
        }, buf -> {
            int mode = buf.readByte();
            boolean hasA = buf.readBoolean(); int ax = buf.readInt(), ay = buf.readInt(), az = buf.readInt();
            boolean hasB = buf.readBoolean(); int bx = buf.readInt(), by = buf.readInt(), bz = buf.readInt();
            int rot = buf.readByte(); boolean mirror = buf.readBoolean(); int dist = buf.readVarInt();
            boolean blocks = buf.readBoolean();
            int sx = buf.readVarInt(), sy = buf.readVarInt(), sz = buf.readVarInt();
            int[] states = new int[0];
            if (blocks) {
                int n = Math.min(buf.readVarInt(), 16 * 16 * 16);
                states = new int[n];
                for (int i = 0; i < n; i++) states[i] = buf.readVarInt() - 1;
            }
            int index = buf.readVarInt(), count = buf.readVarInt();
            return new HeistState(mode, hasA, ax, ay, az, hasB, bx, by, bz, rot, mirror, dist, blocks, sx, sy, sz, states, index, count);
        });
        @Override public Type<HeistState> type() { return TYPE; }
    }

    /**
     * Client → server Area Heist input while the mode is on. action: 0 left, 1 right, 2 shift+right, 3 Alt+scroll
     * (value = direction), 4 cancel (middle click). x/y/z = the block the client aims at (selecting) or the ghost
     * anchor (placing); has = false when nothing is aimed.
     */
    public record HeistInput(int action, int value, boolean has, int x, int y, int z) implements CustomPacketPayload {
        public static final Type<HeistInput> TYPE = new Type<>(InfinityStones.id("extra_space_heist_input"));
        public static final StreamCodec<FriendlyByteBuf, HeistInput> CODEC = StreamCodec.of((buf, s) -> {
            buf.writeByte(s.action); buf.writeVarInt(s.value); buf.writeBoolean(s.has);
            buf.writeInt(s.x); buf.writeInt(s.y); buf.writeInt(s.z);
        }, buf -> new HeistInput(buf.readByte(), buf.readVarInt(), buf.readBoolean(), buf.readInt(), buf.readInt(), buf.readInt()));
        @Override public Type<HeistInput> type() { return TYPE; }
    }

    /** Abyss Watch holographic panel: title + lines, shown for {@code ticks}. */
    public record AbyssPanel(Component title, List<Component> lines, int ticks) implements CustomPacketPayload {
        public static final Type<AbyssPanel> TYPE = new Type<>(InfinityStones.id("extra_space_abyss_panel"));
        public static final StreamCodec<RegistryFriendlyByteBuf, AbyssPanel> CODEC = StreamCodec.composite(
                ComponentSerialization.STREAM_CODEC, AbyssPanel::title,
                ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(64)), AbyssPanel::lines,
                ByteBufCodecs.VAR_INT, AbyssPanel::ticks, AbyssPanel::new);
        @Override public Type<AbyssPanel> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1").optional();
        r.playToClient(HeistState.TYPE, HeistState.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> SpaceBridge.heist(msg)));
        r.playToClient(AbyssPanel.TYPE, AbyssPanel.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> SpaceBridge.abyss(msg)));
        r.playToServer(HeistInput.TYPE, HeistInput.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) AreaHeist.input(sp, msg);
        }));
    }

    static void to(ServerPlayer p, CustomPacketPayload msg) { PacketDistributor.sendToPlayer(p, msg); }

    private SpaceNet() {}
}
