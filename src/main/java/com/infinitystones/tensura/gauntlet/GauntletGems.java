package com.infinitystones.tensura.gauntlet;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.GauntletStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.NonNullList;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Server side of the gauntlet "Joias" screen (opened from the wheel's gauntlet page): put stones into the six sockets
 * of the gauntlet the player carries, or take them out again. The client only names an action; the server picks the
 * gauntlet, checks every socket and moves the items, then answers with the new state.
 */
public final class GauntletGems {
    public static final int REFRESH = 0, REMOVE = 1, INSERT = 2, INSERT_ALL = 3, REMOVE_ALL = 4;

    /** Client → server. socket = stone ordinal for REMOVE / INSERT, ignored otherwise. */
    public record Request(int action, int socket) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(InfinityStones.id("gauntlet_gems_request"));
        public static final StreamCodec<ByteBuf, Request> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Request::action, ByteBufCodecs.VAR_INT, Request::socket, Request::new);
        @Override public Type<Request> type() { return TYPE; }
    }

    /** Server → client. sockets / loose = bit per stone ordinal (stones set in the gauntlet / loose gems carried). */
    public record State(boolean gauntlet, int sockets, int loose, int change) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(InfinityStones.id("gauntlet_gems_state"));
        public static final StreamCodec<ByteBuf, State> CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, State::gauntlet, ByteBufCodecs.VAR_INT, State::sockets, ByteBufCodecs.VAR_INT, State::loose,
                ByteBufCodecs.VAR_INT, State::change, State::new);
        @Override public Type<State> type() { return TYPE; }
    }

    /** Last state received (read by the client screen). change = socket bits that just changed (animation), 0 on a plain refresh. */
    public static volatile State client = new State(false, 0, 0, 0);

    public static void handle(ServerPlayer p, int action, int socket) {
        if (action < REFRESH || action > REMOVE_ALL || !p.isAlive() || p.isSpectator()) return;
        ItemStack g = GauntletStones.primary(p);
        if (g.isEmpty()) { PacketDistributor.sendToPlayer(p, new State(false, 0, looseMask(p), 0)); return; }
        NonNullList<ItemStack> six = GauntletStones.sockets(g);
        int before = GauntletStones.mask(g);
        switch (action) {
            case REMOVE -> { if (socket >= 0 && socket < 6) take(p, six, socket); }
            case REMOVE_ALL -> { for (int i = 0; i < 6; i++) take(p, six, i); }
            case INSERT -> { if (socket >= 0 && socket < 6) put(p, g, six, Stone.values()[socket]); }
            case INSERT_ALL -> { for (Stone s : Stone.values()) put(p, g, six, s); }
            default -> { }
        }
        if (action != REFRESH) {
            GauntletStones.setSockets(g, six);
            p.getInventory().setChanged();
        }
        int after = GauntletStones.mask(g);
        if (after != before) {
            GauntletEvents.socketFx(p, before, after);
            for (Stone s : Stone.values()) {
                int bit = 1 << s.ordinal();
                if ((after & bit) != (before & bit)) ArtifactSwap.circle(p, s, (after & bit) != 0);
            }
        } else if (action != REFRESH) {
            Fx.barGray(p, action == INSERT || action == INSERT_ALL ? "infinity.gauntlet.no_loose" : "infinity.gauntlet.nothing_to_remove");
        }
        PacketDistributor.sendToPlayer(p, new State(true, after, looseMask(p), after ^ before));
    }

    private static void take(ServerPlayer p, NonNullList<ItemStack> six, int socket) {
        ItemStack st = six.get(socket);
        if (st.isEmpty()) return;
        six.set(socket, ItemStack.EMPTY);
        if (!p.getInventory().add(st)) p.drop(st, false);
    }

    private static void put(ServerPlayer p, ItemStack gauntlet, NonNullList<ItemStack> six, Stone s) {
        if (!six.get(s.ordinal()).isEmpty()) return;
        var inv = p.getInventory();
        for (ItemStack it : inv.items) if (insertFrom(it, gauntlet, six, s)) return;
        for (ItemStack it : inv.offhand) if (insertFrom(it, gauntlet, six, s)) return;
    }

    private static boolean insertFrom(ItemStack it, ItemStack gauntlet, NonNullList<ItemStack> six, Stone s) {
        if (it == gauntlet || !GauntletStones.isGem(it, s)) return false;
        six.set(s.ordinal(), it.split(1));
        return true;
    }

    public static int looseMask(ServerPlayer p) {
        int m = 0;
        var inv = p.getInventory();
        for (ItemStack it : inv.items) { Stone s = GauntletStones.gemOf(it); if (s != null) m |= 1 << s.ordinal(); }
        for (ItemStack it : inv.offhand) { Stone s = GauntletStones.gemOf(it); if (s != null) m |= 1 << s.ordinal(); }
        return m;
    }

    @EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            var r = event.registrar("infinity_gauntlet_gems");
            r.playToServer(Request.TYPE, Request.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer sp) handle(sp, msg.action(), msg.socket());
            }));
            r.playToClient(State.TYPE, State.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> client = msg));
        }
        private Network() {}
    }

    private GauntletGems() {}
}
