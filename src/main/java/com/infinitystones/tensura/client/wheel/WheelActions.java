package com.infinitystones.tensura.client.wheel;

import com.infinitystones.tensura.skill.DualModes;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneTree;
import com.infinitystones.tensura.wheel.AbilityMeta;
import com.infinitystones.tensura.wheel.WheelClientState;
import com.infinitystones.tensura.wheel.WheelPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * What choosing a power does (wheel click, favourite key, quick tap = repeat last). Shared by the wheel, the
 * favourites and the tap so they always behave the same:
 * <ul>
 *   <li>switch (toggle or passive): flipped, the wheel stays open;</li>
 *   <li>targeted power ({@link AbilityMeta#needsAim}): the wheel closes and aim mode starts;</li>
 *   <li>channel: start/stop toggle (aimed first when it needs aim);</li>
 *   <li>anything else: used at once, the wheel closes.</li>
 * </ul>
 */
public final class WheelActions {
    /** Result for the wheel screen. */
    public enum Outcome { STAY_OPEN, CLOSE, NOTHING }

    private static int lastStone = Integer.MIN_VALUE;
    private static String lastNode = "";
    private static boolean lastAlt;

    /** button 0 = left (option 1), 1 = right (option 2 of dual powers). Middle-click menus are handled by the screen. */
    public static Outcome activate(int stone, String nodeId, int button) {
        StoneTree.Node n = node(stone, nodeId);
        if (n == null) return Outcome.NOTHING;
        boolean alt = button == 1 && DualModes.isDual(n.modeId());
        if (AbilityMeta.isSwitch(n)) {
            PacketDistributor.sendToServer(new WheelPayloads.Use(stone, nodeId, 0));
            WheelPayloads.Entry cur = entry(stone, nodeId);
            boolean turningOn = cur == null || !cur.toggled();
            if (turningOn) com.infinitystones.tensura.client.render.GauntletFx.pulse(stone);
            MagicCircle.flashAtFeet(stone, turningOn);
            return Outcome.STAY_OPEN;
        }
        lastStone = stone; lastNode = nodeId; lastAlt = alt;
        if (AbilityMeta.isChannel(n) && n.modeId().equals(WheelClientState.get().holding())) {
            PacketDistributor.sendToServer(new WheelPayloads.Use(stone, nodeId, 1)); // second choice stops it
            return Outcome.CLOSE;
        }
        if (alt && com.infinitystones.tensura.skill.ability.extra.client.ExtraClientApi.hasMenu(n.modeId())) {
            // the right-click variant of menu powers is the menu itself: no aiming, the menu screen replaces the wheel
            PacketDistributor.sendToServer(new WheelPayloads.Use(stone, nodeId, 2));
            return Outcome.STAY_OPEN;
        }
        if (AbilityMeta.needsAim(n)) { AimMode.start(stone, nodeId, alt); return Outcome.CLOSE; }
        PacketDistributor.sendToServer(new WheelPayloads.Use(stone, nodeId, alt ? 2 : 0));
        com.infinitystones.tensura.client.render.GauntletFx.pulse(stone);
        MagicCircle.flashAtFeet(stone, true);
        return Outcome.CLOSE;
    }

    /** Quick tap on the wheel key: stop the channel in progress, else repeat the last power. @return true if something happened. */
    public static boolean tap() {
        if (AimMode.active()) { AimMode.cancel(); return true; }
        if (!WheelClientState.get().holding().isEmpty()) { stopChannel(); return true; }
        if (lastStone == Integer.MIN_VALUE || entry(lastStone, lastNode) == null) return false;
        activate(lastStone, lastNode, lastAlt ? 1 : 0);
        return true;
    }

    public static void stopChannel() {
        StoneTree.Node n = nodeByMode(WheelClientState.get().holding());
        if (n != null) PacketDistributor.sendToServer(new WheelPayloads.Use(n.stone().ordinal(), n.id(), 1));
    }

    // ------------------------------------------------------------ favourites (H + 1..4)

    private static final String[] FAV = new String[4];
    private static boolean favLoaded;

    private static Path favFile() { return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("infinity-wheel-favorites.properties"); }

    /** "stone.node" or "stone.node#alt", or null. */
    public static @Nullable String favorite(int slot) { loadFav(); return slot >= 0 && slot < 4 ? FAV[slot] : null; }

    /** Pins a power to slot 0..3 (the same power moves; pinning it again on its slot clears it). */
    public static void setFavorite(int slot, int stone, String node, boolean alt) {
        loadFav();
        String v = Stone.values()[stone].key + "." + node + (alt ? "#alt" : "");
        boolean clear = v.equals(FAV[slot]);
        for (int i = 0; i < 4; i++) if (v.equals(FAV[i])) FAV[i] = null;
        FAV[slot] = clear ? null : v;
        Properties p = new Properties();
        for (int i = 0; i < 4; i++) if (FAV[i] != null) p.setProperty("slot" + (i + 1), FAV[i]);
        try {
            Files.createDirectories(favFile().getParent());
            try (Writer w = Files.newBufferedWriter(favFile(), StandardCharsets.UTF_8)) { p.store(w, "Infinity wheel favourites (H + 1..4)"); }
        } catch (IOException ignored) { }
    }

    /** Slot (0..3) the power is pinned to, or -1. */
    public static int favoriteSlot(int stone, String node) {
        loadFav();
        String v = Stone.values()[stone].key + "." + node;
        for (int i = 0; i < 4; i++) if (FAV[i] != null && (FAV[i].equals(v) || FAV[i].equals(v + "#alt"))) return i;
        return -1;
    }

    /** Uses favourite slot 0..3. */
    public static Outcome useFavorite(int slot) {
        String f = favorite(slot);
        if (f == null) return Outcome.NOTHING;
        boolean alt = f.endsWith("#alt");
        StoneTree.Node n = nodeByMode(alt ? f.substring(0, f.length() - 4) : f);
        if (n == null || entry(n.stone().ordinal(), n.id()) == null) return Outcome.NOTHING;
        return activate(n.stone().ordinal(), n.id(), alt ? 1 : 0);
    }

    private static void loadFav() {
        if (favLoaded) return;
        favLoaded = true;
        if (!Files.exists(favFile())) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(favFile(), StandardCharsets.UTF_8)) { p.load(r); } catch (IOException ignored) { return; }
        for (int i = 0; i < 4; i++) FAV[i] = p.getProperty("slot" + (i + 1));
    }

    // ------------------------------------------------------------ texts (wheel panel + HUD)

    /** "Intensity: 2/4 (Alt + scroll)" for a power with a setting, else null. */
    public static @Nullable Component settingText(StoneTree.Node n, boolean withKeys) {
        AbilityMeta.SettingSpec spec = AbilityMeta.setting(n, net.minecraft.client.Minecraft.getInstance().player);
        if (spec == null) return null;
        WheelPayloads.Entry e = entry(n.stone().ordinal(), n.id());
        int v = e != null ? spec.clamp(e.setting()) : spec.def();
        String vk = spec.valueKey(v);
        MutableComponent c = Component.translatable(spec.label()).withStyle(ChatFormatting.AQUA).append(Component.literal(": "))
                .append((vk != null ? Component.translatable(vk) : Component.literal(String.valueOf(v))).withStyle(ChatFormatting.WHITE))
                .append(Component.literal(" (" + v + "/" + spec.max() + ")").withStyle(ChatFormatting.DARK_GRAY));
        if (withKeys) c.append(Component.literal("  ")).append(Component.translatableWithFallback("infinity.wheel.hint.alt_scroll", "[Alt + scroll]")
                .withStyle(ChatFormatting.YELLOW));
        return c;
    }

    /** Usage lines for the wheel panel: which buttons do what for this power. */
    public static List<Component> hints(StoneTree.Node n) {
        List<Component> out = new ArrayList<>();
        String mid = n.modeId();
        String custom = "infinity.wheel.hint." + mid;
        if (I18n.exists(custom)) out.add(Component.translatable(custom).withStyle(ChatFormatting.YELLOW));
        else if (AbilityMeta.isSwitch(n)) out.add(Component.translatableWithFallback("infinity.wheel.hint.switch", "[LMB] turn on / off (the wheel stays open)").withStyle(ChatFormatting.YELLOW));
        else if (DualModes.isDual(mid) || DualModes.hasDestination(mid)) out.add(Component.translatable("infinity.wheel.dual." + n.id()).withStyle(ChatFormatting.YELLOW));
        else if (AbilityMeta.isChannel(n)) out.add(Component.translatableWithFallback("infinity.wheel.hint.channel", "[LMB] start / stop channelling (no holding)").withStyle(ChatFormatting.YELLOW));
        else out.add(Component.translatableWithFallback("infinity.wheel.hint.press", "[LMB] use").withStyle(ChatFormatting.YELLOW));
        if (!AbilityMeta.isSwitch(n) && AbilityMeta.needsAim(n)) out.add(Component.translatableWithFallback("infinity.wheel.hint.aim",
                "Then aim freely (you can move): [LMB] confirm · [RMB]/Esc cancel").withStyle(ChatFormatting.GRAY));
        if (AbilityMeta.isChannel(n)) out.add(Component.translatableWithFallback("infinity.wheel.hint.channel_stop",
                "Stop: [RMB], tap %s or choose it again", WheelKeys.WHEEL.getTranslatedKeyMessage()).withStyle(ChatFormatting.GRAY));
        if (DualModes.hasDestination(mid) && DualModes.movesTarget(mid)) out.add(Component.translatableWithFallback("infinity.wheel.hint.destination_aim",
                "[MMB] pick destination, then aim at the target and [LMB]").withStyle(ChatFormatting.GRAY));
        Component s = settingText(n, false);
        if (s != null) { out.add(s); out.add(Component.translatableWithFallback("infinity.wheel.hint.scroll",
                "[Scroll] over it here: change level / mode  (Alt + scroll while aiming)").withStyle(ChatFormatting.YELLOW)); }
        if (!AbilityMeta.isSwitch(n)) out.add(Component.translatableWithFallback("infinity.wheel.hint.favorite", "[1-4] pin as favourite (%s + 1..4)",
                WheelKeys.WHEEL.getTranslatedKeyMessage()).withStyle(ChatFormatting.DARK_GRAY));
        return out;
    }

    // ------------------------------------------------------------ lookup

    static @Nullable StoneTree.Node node(int stone, String node) {
        return stone < 0 || stone >= Stone.values().length ? null : StoneTree.of(Stone.values()[stone]).nodes.get(node);
    }

    public static @Nullable StoneTree.Node nodeByMode(String modeId) {
        int dot = modeId.indexOf('.');
        if (dot <= 0) return null;
        for (Stone s : Stone.values()) if (s.key.equals(modeId.substring(0, dot))) return StoneTree.of(s).nodes.get(modeId.substring(dot + 1));
        return null;
    }

    static @Nullable WheelPayloads.Entry entry(int stone, String node) {
        for (WheelPayloads.Entry e : WheelClientState.get().entries()) if (e.stone() == stone && e.node().equals(node) && e.unlocked()) return e;
        for (com.infinitystones.tensura.shard.ShardNet.Page pg : com.infinitystones.tensura.shard.ShardClientState.pages())
            if (pg.stone() == stone) for (com.infinitystones.tensura.shard.ShardNet.Power w : pg.powers())
                if (w.node().equals(node)) return new WheelPayloads.Entry(stone, node, true, false, w.cdLeft(), Math.max(1, w.cdTotal()), 0);
        return null;
    }

    private WheelActions() {}
}
