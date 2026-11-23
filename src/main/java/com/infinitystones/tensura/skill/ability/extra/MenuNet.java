package com.infinitystones.tensura.skill.ability.extra;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneProgress;
import com.infinitystones.tensura.skill.StoneTree;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Per-power choice menus (Decree laws, pet orders, species...). The server sends the options; the client shows a radial
 * menu ({@code extra.client.MenuScreen}); the pick comes back and is validated again.
 * Wheel hook for the middle click: {@code extra.client.ExtraClientApi.hasMenu(modeId)} / {@code requestMenu(stone, node)}.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class MenuNet {
    /** label/description are translation keys; icon = item id; selected = current choice. */
    public record Option(String label, String desc, String icon, boolean selected) {
        public static final StreamCodec<ByteBuf, Option> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Option::label, ByteBufCodecs.STRING_UTF8, Option::desc,
                ByteBufCodecs.STRING_UTF8, Option::icon, ByteBufCodecs.BOOL, Option::selected, Option::new);
    }

    public record Open(int stone, String node, String title, List<Option> options) implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(InfinityStones.id("extra_menu_open"));
        public static final StreamCodec<ByteBuf, Open> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Open::stone, ByteBufCodecs.STRING_UTF8, Open::node, ByteBufCodecs.STRING_UTF8, Open::title,
                Option.CODEC.apply(ByteBufCodecs.list(32)), Open::options, Open::new);
        @Override public Type<Open> type() { return TYPE; }
    }

    /** index -1 = just asking the server to open the menu (middle click). */
    public record Pick(int stone, String node, int index) implements CustomPacketPayload {
        public static final Type<Pick> TYPE = new Type<>(InfinityStones.id("extra_menu_pick"));
        public static final StreamCodec<ByteBuf, Pick> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Pick::stone, ByteBufCodecs.STRING_UTF8, Pick::node, ByteBufCodecs.VAR_INT, Pick::index, Pick::new);
        @Override public Type<Pick> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1");
        r.playToClient(Open.TYPE, Open.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> com.infinitystones.tensura.skill.ability.extra.client.ExtraClientApi.open(msg)));
        r.playToServer(Pick.TYPE, Pick.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) handle(sp, msg);
        }));
    }

    /** Sends the ability's menu to the player (call from press() for "menu on right click" powers). */
    public static boolean open(ServerPlayer p, Stone stone, String nodeId, Ability a) {
        List<Option> opts = a.menu(p);
        if (opts == null || opts.isEmpty()) return false;
        PacketDistributor.sendToPlayer(p, new Open(stone.ordinal(), nodeId, title(stone, nodeId), opts));
        return true;
    }

    /** The node's title key (replacement nodes keep the old id but have their own title). */
    private static String title(Stone stone, String nodeId) {
        StoneTree.Node n = StoneTree.of(stone).nodes.get(nodeId);
        String t = n == null ? "" : n.title();
        return t.startsWith("@") ? t.substring(1) : "ability.infinity." + stone.key + "_stone." + nodeId + ".title";
    }

    private static void handle(ServerPlayer p, Pick msg) {
        if (msg.stone() < 0 || msg.stone() >= Stone.values().length || msg.node().length() > 64) return;
        Stone stone = Stone.values()[msg.stone()];
        StoneTree.Node node = StoneTree.of(stone).nodes.get(msg.node());
        if (node == null) return;
        Ability a = Abilities.get(node.originalType());
        if (a == null || !stone.isHeldBy(p) || !StoneProgress.isUnlocked(p, stone, node.id())) return;
        if (msg.index() < 0) { open(p, stone, node.id(), a); return; }
        List<Option> opts = a.menu(p);
        if (opts == null || msg.index() >= opts.size()) return;
        a.pick(p, msg.index());
    }

    public static @Nullable Ability abilityFor(String modeId) {
        int dot = modeId.indexOf('.');
        if (dot < 0) return null;
        for (Stone s : Stone.values()) if (s.key.equals(modeId.substring(0, dot))) {
            StoneTree.Node n = StoneTree.of(s).nodes.get(modeId.substring(dot + 1));
            return n == null ? null : Abilities.get(n.originalType());
        }
        return null;
    }

    public static Option opt(String key, String icon, boolean selected) {
        return new Option(key, key + ".desc", icon, selected);
    }

    private MenuNet() {}
}
