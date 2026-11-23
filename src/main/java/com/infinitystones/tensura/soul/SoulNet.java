package com.infinitystones.tensura.soul;

import com.infinitystones.tensura.InfinityStones;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.List;

/**
 * Network messages of the Soul Stone's Possession and Soul Capture. Client-bound messages only store data in the
 * volatile fields below; the client classes (client/soul) read them, so nothing here touches client code.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class SoulNet {
    public static final int NONE = 0, SPECTATE = 1, CONTROL = 2, MOB = 3;

    /** Server → possessor: possession mode and the entity the camera follows. */
    public record State(int mode, int entity, String name) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(InfinityStones.id("soul_state"));
        public static final StreamCodec<ByteBuf, State> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, State::mode, ByteBufCodecs.VAR_INT, State::entity, ByteBufCodecs.STRING_UTF8, State::name, State::new);
        @Override public Type<State> type() { return TYPE; }
    }

    /** Server → possessor: the possessed player's hotbar and health (drawn on the possession HUD). */
    public record Hud(List<ItemStack> hotbar, int selected, float health, float maxHealth) implements CustomPacketPayload {
        public static final Type<Hud> TYPE = new Type<>(InfinityStones.id("soul_hud"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Hud> CODEC = StreamCodec.composite(
                ItemStack.OPTIONAL_LIST_STREAM_CODEC, Hud::hotbar, ByteBufCodecs.VAR_INT, Hud::selected,
                ByteBufCodecs.FLOAT, Hud::health, ByteBufCodecs.FLOAT, Hud::maxHealth, Hud::new);
        @Override public Type<Hud> type() { return TYPE; }
    }

    /** Server → everyone: entity ids of possessors' bodies (never rendered). */
    public record Ghosts(List<Integer> ids) implements CustomPacketPayload {
        public static final Type<Ghosts> TYPE = new Type<>(InfinityStones.id("soul_ghosts"));
        public static final StreamCodec<ByteBuf, Ghosts> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), Ghosts::ids, Ghosts::new);
        @Override public Type<Ghosts> type() { return TYPE; }
    }

    /** Server → caster: online players that can be possessed (opens the player menu). */
    public record Players(List<String> names, List<String> uuids) implements CustomPacketPayload {
        public static final Type<Players> TYPE = new Type<>(InfinityStones.id("soul_players"));
        public static final StreamCodec<ByteBuf, Players> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Players::names,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Players::uuids, Players::new);
        @Override public Type<Players> type() { return TYPE; }
    }

    /** Server → caster: captured souls ({nbt, name}); open = open the menu (false = refresh it if open). */
    public record Jar(List<CompoundTag> entries, boolean open) implements CustomPacketPayload {
        public static final Type<Jar> TYPE = new Type<>(InfinityStones.id("soul_jar"));
        public static final StreamCodec<ByteBuf, Jar> CODEC = StreamCodec.composite(
                ByteBufCodecs.COMPOUND_TAG.apply(ByteBufCodecs.list()), Jar::entries, ByteBufCodecs.BOOL, Jar::open, Jar::new);
        @Override public Type<Jar> type() { return TYPE; }
    }

    /** Client → server: possessor's movement input, every tick while controlling a body. */
    public record Input(float forward, float strafe, boolean jump, boolean sneak, boolean sprint, float yaw, float pitch,
                        boolean attack, boolean use, int slot) implements CustomPacketPayload {
        public static final Type<Input> TYPE = new Type<>(InfinityStones.id("soul_input"));
        public static final StreamCodec<FriendlyByteBuf, Input> CODEC = StreamCodec.of((b, i) -> {
            b.writeFloat(i.forward); b.writeFloat(i.strafe); b.writeFloat(i.yaw); b.writeFloat(i.pitch);
            b.writeByte((i.jump ? 1 : 0) | (i.sneak ? 2 : 0) | (i.sprint ? 4 : 0) | (i.attack ? 8 : 0) | (i.use ? 16 : 0));
            b.writeByte(i.slot);
        }, b -> {
            float f = b.readFloat(), s = b.readFloat(), y = b.readFloat(), p = b.readFloat();
            int bits = b.readByte();
            return new Input(f, s, (bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0, y, p, (bits & 8) != 0, (bits & 16) != 0, b.readByte());
        });
        @Override public Type<Input> type() { return TYPE; }
    }

    /** Client → server: possession action (see Possession.ACT_*). */
    public record Action(int action, int arg) implements CustomPacketPayload {
        public static final Type<Action> TYPE = new Type<>(InfinityStones.id("soul_action"));
        public static final StreamCodec<ByteBuf, Action> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Action::action, ByteBufCodecs.VAR_INT, Action::arg, Action::new);
        @Override public Type<Action> type() { return TYPE; }
    }

    /** Client → server: player picked in the possession menu. */
    public record Pick(String uuid) implements CustomPacketPayload {
        public static final Type<Pick> TYPE = new Type<>(InfinityStones.id("soul_pick"));
        public static final StreamCodec<ByteBuf, Pick> CODEC = StreamCodec.composite(ByteBufCodecs.stringUtf8(64), Pick::uuid, Pick::new);
        @Override public Type<Pick> type() { return TYPE; }
    }

    /** Client → server: captured-soul menu action (see SoulJar.action). */
    public record JarAction(int action, int index) implements CustomPacketPayload {
        public static final Type<JarAction> TYPE = new Type<>(InfinityStones.id("soul_jar_action"));
        public static final StreamCodec<ByteBuf, JarAction> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, JarAction::action, ByteBufCodecs.VAR_INT, JarAction::index, JarAction::new);
        @Override public Type<JarAction> type() { return TYPE; }
    }

    // ---- client-side copies (read by client/soul) ----
    public static volatile State state = new State(NONE, -1, "");
    public static volatile Hud hud = new Hud(List.of(), 0, 0, 0);
    public static volatile List<Integer> ghosts = List.of();
    public static volatile Players pendingPlayers;
    public static volatile Jar jar = new Jar(List.of(), false);
    public static volatile boolean openJar;

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToClient(State.TYPE, State.CODEC, (m, c) -> c.enqueueWork(() -> state = m));
        r.playToClient(Hud.TYPE, Hud.CODEC, (m, c) -> c.enqueueWork(() -> hud = m));
        r.playToClient(Ghosts.TYPE, Ghosts.CODEC, (m, c) -> c.enqueueWork(() -> ghosts = m.ids()));
        r.playToClient(Players.TYPE, Players.CODEC, (m, c) -> c.enqueueWork(() -> pendingPlayers = m));
        r.playToClient(Jar.TYPE, Jar.CODEC, (m, c) -> c.enqueueWork(() -> { jar = m; if (m.open()) openJar = true; }));
        r.playToServer(Input.TYPE, Input.CODEC, (m, c) -> c.enqueueWork(() -> {
            if (c.player() instanceof ServerPlayer sp) Possession.input(sp, m);
        }));
        r.playToServer(Action.TYPE, Action.CODEC, (m, c) -> c.enqueueWork(() -> {
            if (c.player() instanceof ServerPlayer sp) Possession.action(sp, m.action(), m.arg());
        }));
        r.playToServer(Pick.TYPE, Pick.CODEC, (m, c) -> c.enqueueWork(() -> {
            if (c.player() instanceof ServerPlayer sp) Possession.pick(sp, m.uuid());
        }));
        r.playToServer(JarAction.TYPE, JarAction.CODEC, (m, c) -> c.enqueueWork(() -> {
            if (c.player() instanceof ServerPlayer sp) SoulJar.action(sp, m.action(), m.index());
        }));
    }

    private SoulNet() {}
}
