package com.infinitystones.tensura.wheel;

import com.infinitystones.tensura.InfinityStones;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

/** Network messages for the Infinity wheel. */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class WheelPayloads {

    /** Client → server: use a node (phase 0) or stop channelling it (phase 1). stone = -1 for immortality. */
    public record Use(int stone, String node, int phase) implements CustomPacketPayload {
        public static final Type<Use> TYPE = new Type<>(InfinityStones.id("wheel_use"));
        public static final StreamCodec<ByteBuf, Use> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Use::stone, ByteBufCodecs.STRING_UTF8, Use::node, ByteBufCodecs.VAR_INT, Use::phase, Use::new);
        @Override public Type<Use> type() { return TYPE; }
    }

    /** setting = current Alt+scroll value of the power (0 if it has none). */
    public record Entry(int stone, String node, boolean unlocked, boolean toggled, int cooldownLeft, int cooldownTotal, int setting) {
        public static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.of((buf, e) -> {
            ByteBufCodecs.VAR_INT.encode(buf, e.stone); ByteBufCodecs.STRING_UTF8.encode(buf, e.node);
            buf.writeBoolean(e.unlocked); buf.writeBoolean(e.toggled);
            ByteBufCodecs.VAR_INT.encode(buf, e.cooldownLeft); ByteBufCodecs.VAR_INT.encode(buf, e.cooldownTotal);
            ByteBufCodecs.INT.encode(buf, e.setting);
        }, buf -> new Entry(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), buf.readBoolean(), buf.readBoolean(),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.INT.decode(buf)));
    }

    /** Server → flyer + trackers: entity is in the "paladin" horizontal flight pose. */
    public record FlyPose(int entity, boolean on) implements CustomPacketPayload {
        public static final Type<FlyPose> TYPE = new Type<>(InfinityStones.id("fly_pose"));
        public static final StreamCodec<ByteBuf, FlyPose> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, FlyPose::entity, ByteBufCodecs.BOOL, FlyPose::on, FlyPose::new);
        @Override public Type<FlyPose> type() { return TYPE; }
    }
    /** Client copy: entity ids currently in the paladin pose. */
    public static final java.util.Set<Integer> paladinFlyers = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Client → server: Alt + mouse wheel on a power with a setting. delta = +1 / -1. */
    public record Setting(int stone, String node, int delta) implements CustomPacketPayload {
        public static final Type<Setting> TYPE = new Type<>(InfinityStones.id("wheel_setting"));
        public static final StreamCodec<ByteBuf, Setting> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Setting::stone, ByteBufCodecs.stringUtf8(64), Setting::node, ByteBufCodecs.VAR_INT, Setting::delta, Setting::new);
        @Override public Type<Setting> type() { return TYPE; }
    }

    /** Server → client: what the wheel shows. holding = mode id of the channel in progress ("" if none). */
    public record State(List<Entry> entries, String holding, int tiers) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(InfinityStones.id("wheel_state"));
        public static final StreamCodec<ByteBuf, State> CODEC = StreamCodec.composite(
                Entry.CODEC.apply(ByteBufCodecs.list()), State::entries, ByteBufCodecs.STRING_UTF8, State::holding,
                ByteBufCodecs.VAR_INT, State::tiers, State::new);
        @Override public Type<State> type() { return TYPE; }
    }

    /**
     * Client → server: destination-menu use (middle click). dimension = level id; useDefault = the dimension's default
     * point, otherwise x/y/z (autoY = find the safe floor at x/z).
     */
    public record Destination(int stone, String node, String dimension, boolean useDefault, double x, double y, double z, boolean autoY)
            implements CustomPacketPayload {
        public static final Type<Destination> TYPE = new Type<>(InfinityStones.id("wheel_destination"));
        public static final StreamCodec<FriendlyByteBuf, Destination> CODEC = StreamCodec.of((buf, d) -> {
            buf.writeVarInt(d.stone); buf.writeUtf(d.node, 64); buf.writeUtf(d.dimension, 256); buf.writeBoolean(d.useDefault);
            buf.writeDouble(d.x); buf.writeDouble(d.y); buf.writeDouble(d.z); buf.writeBoolean(d.autoY);
        }, buf -> new Destination(buf.readVarInt(), buf.readUtf(64), buf.readUtf(256), buf.readBoolean(),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean()));
        @Override public Type<Destination> type() { return TYPE; }
    }

    /** Client → server: "bring it back" menu. action 0 = list, 1 = bring back entry #index. */
    public record BanishAction(int action, int index) implements CustomPacketPayload {
        public static final Type<BanishAction> TYPE = new Type<>(InfinityStones.id("banish_action"));
        public static final StreamCodec<ByteBuf, BanishAction> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, BanishAction::action, ByteBufCodecs.VAR_INT, BanishAction::index, BanishAction::new);
        @Override public Type<BanishAction> type() { return TYPE; }
    }

    public record Banished(String name, String inDim, String fromDim) {
        public static final StreamCodec<ByteBuf, Banished> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Banished::name, ByteBufCodecs.STRING_UTF8, Banished::inDim, ByteBufCodecs.STRING_UTF8, Banished::fromDim,
                Banished::new);
    }

    /** Server → client: the caster's recent banishments, most recent first. */
    public record BanishedList(List<Banished> entries) implements CustomPacketPayload {
        public static final Type<BanishedList> TYPE = new Type<>(InfinityStones.id("banished_list"));
        public static final StreamCodec<ByteBuf, BanishedList> CODEC = StreamCodec.composite(
                Banished.CODEC.apply(ByteBufCodecs.list()), BanishedList::entries, BanishedList::new);
        @Override public Type<BanishedList> type() { return TYPE; }
    }

    /** Client → server: buy a tree node (server re-validates and pays). */
    public record TreeBuy(int stone, String node) implements CustomPacketPayload {
        public static final Type<TreeBuy> TYPE = new Type<>(InfinityStones.id("tree_buy"));
        public static final StreamCodec<ByteBuf, TreeBuy> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, TreeBuy::stone, ByteBufCodecs.STRING_UTF8, TreeBuy::node, TreeBuy::new);
        @Override public Type<TreeBuy> type() { return TYPE; }
    }

    // ---------------- Will (Reality) grip mode ----------------

    /** Server → owner: grip mode on/off. */
    public record WillState(boolean active) implements CustomPacketPayload {
        public static final Type<WillState> TYPE = new Type<>(InfinityStones.id("will_state"));
        public static final StreamCodec<ByteBuf, WillState> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, WillState::active, WillState::new);
        @Override public Type<WillState> type() { return TYPE; }
    }

    /** Client → server: grip input. action 0 = left (grab/pin), 1 = right (hurl), 2 = scroll (value), 3 = middle (release). */
    public record WillInput(int action, float value) implements CustomPacketPayload {
        public static final Type<WillInput> TYPE = new Type<>(InfinityStones.id("will_input"));
        public static final StreamCodec<ByteBuf, WillInput> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, WillInput::action, ByteBufCodecs.FLOAT, WillInput::value, WillInput::new);
        @Override public Type<WillInput> type() { return TYPE; }
    }

    /** One hard-light beam: holder entity id → target entity id, or a Sable body uuid ("" when it is an entity). */
    public record Beam(int holder, int entity, String body) {
        public static final StreamCodec<ByteBuf, Beam> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Beam::holder, ByteBufCodecs.VAR_INT, Beam::entity, ByteBufCodecs.STRING_UTF8, Beam::body, Beam::new);
    }

    /** Server → everyone: all active beams (drawn for every player, so the grip is visible in multiplayer). */
    public record WillBeams(List<Beam> beams) implements CustomPacketPayload {
        public static final Type<WillBeams> TYPE = new Type<>(InfinityStones.id("will_beams"));
        public static final StreamCodec<ByteBuf, WillBeams> CODEC = StreamCodec.composite(
                Beam.CODEC.apply(ByteBufCodecs.list()), WillBeams::beams, WillBeams::new);
        @Override public Type<WillBeams> type() { return TYPE; }
    }

    // ---------------- Will constructs ----------------

    /** Client → server: construct menu action (see Constructs.action). */
    public record ConstructAction(int action, int index, String name) implements CustomPacketPayload {
        public static final Type<ConstructAction> TYPE = new Type<>(InfinityStones.id("construct_action"));
        public static final StreamCodec<ByteBuf, ConstructAction> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, ConstructAction::action, ByteBufCodecs.VAR_INT, ConstructAction::index,
                ByteBufCodecs.stringUtf8(64), ConstructAction::name, ConstructAction::new);
        @Override public Type<ConstructAction> type() { return TYPE; }
    }

    /** Server → owner: saved constructs (first 8 = quick slots), design state, how many are summoned. */
    /** previews: per construct a flat list x,y,z,blockStateId,... (capped) for the 3D preview in the construct wheel. */
    public record ConstructList(List<String> names, List<Integer> counts, boolean designing, int designCount, int active,
                                List<List<Integer>> previews) implements CustomPacketPayload {
        public static final Type<ConstructList> TYPE = new Type<>(InfinityStones.id("construct_list"));
        public static final StreamCodec<ByteBuf, ConstructList> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ConstructList::names,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), ConstructList::counts,
                ByteBufCodecs.BOOL, ConstructList::designing, ByteBufCodecs.VAR_INT, ConstructList::designCount,
                ByteBufCodecs.VAR_INT, ConstructList::active,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).apply(ByteBufCodecs.list()), ConstructList::previews,
                ConstructList::new);
        @Override public Type<ConstructList> type() { return TYPE; }
    }

    /** Server → owner: blocks of the construct being designed (drawn as glowing hard light). */
    public record DesignBlocks(List<net.minecraft.core.BlockPos> blocks) implements CustomPacketPayload {
        public static final Type<DesignBlocks> TYPE = new Type<>(InfinityStones.id("design_blocks"));
        public static final StreamCodec<ByteBuf, DesignBlocks> CODEC = StreamCodec.composite(
                net.minecraft.core.BlockPos.STREAM_CODEC.apply(ByteBufCodecs.list()), DesignBlocks::blocks, DesignBlocks::new);
        @Override public Type<DesignBlocks> type() { return TYPE; }
    }

    /** Server → everyone: ids of every summoned construct body (drawn with the hard-light glow for all players). */
    public record ConstructBodies(List<String> ids) implements CustomPacketPayload {
        public static final Type<ConstructBodies> TYPE = new Type<>(InfinityStones.id("construct_bodies"));
        public static final StreamCodec<ByteBuf, ConstructBodies> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), ConstructBodies::ids, ConstructBodies::new);
        @Override public Type<ConstructBodies> type() { return TYPE; }
    }
    public static volatile List<String> constructBodies = List.of();

    public static volatile ConstructList constructs = new ConstructList(List.of(), List.of(), false, 0, 0, List.of());
    public static volatile List<net.minecraft.core.BlockPos> designBlocks = List.of();

    /** Client-side copies (read by the client renderer / input handler). */
    public static volatile boolean willActive;
    public static volatile List<Beam> willBeams = List.of();

    /** Last list received (read by the destination screen). */
    public static volatile List<Banished> lastBanished = List.of();

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(WillInput.TYPE, WillInput.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp && Float.isFinite(msg.value()))
                com.infinitystones.tensura.will.WillMode.input(sp, msg.action(), Math.max(-10, Math.min(10, msg.value())));
        }));
        r.playToServer(ConstructAction.TYPE, ConstructAction.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) com.infinitystones.tensura.will.Constructs.action(sp, msg.action(), msg.index(), msg.name());
        }));
        r.playToClient(ConstructBodies.TYPE, ConstructBodies.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> constructBodies = msg.ids()));
        r.playToClient(ConstructList.TYPE, ConstructList.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> constructs = msg));
        r.playToClient(DesignBlocks.TYPE, DesignBlocks.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> designBlocks = msg.blocks()));
        r.playToClient(WillState.TYPE, WillState.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> willActive = msg.active()));
        r.playToClient(WillBeams.TYPE, WillBeams.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> willBeams = msg.beams()));
        r.playToServer(TreeBuy.TYPE, TreeBuy.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) WheelService.handleBuy(sp, msg.stone(), msg.node());
        }));
        r.playToServer(BanishAction.TYPE, BanishAction.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) WheelService.handleBanished(sp, msg.action(), msg.index());
        }));
        r.playToClient(BanishedList.TYPE, BanishedList.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> lastBanished = msg.entries()));
        r.playToServer(Destination.TYPE, Destination.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) WheelService.handleDestination(sp, msg);
        }));
        r.playToClient(FlyPose.TYPE, FlyPose.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            if (msg.on()) paladinFlyers.add(msg.entity()); else paladinFlyers.remove(msg.entity());
        }));
        r.playToServer(Setting.TYPE, Setting.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) WheelService.handleSetting(sp, msg.stone(), msg.node(), msg.delta());
        }));
        r.playToServer(Use.TYPE, Use.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) WheelService.handleUse(sp, msg.stone(), msg.node(), msg.phase());
        }));
        r.playToClient(State.TYPE, State.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> WheelClientState.accept(msg)));
    }

    private WheelPayloads() {}
}
