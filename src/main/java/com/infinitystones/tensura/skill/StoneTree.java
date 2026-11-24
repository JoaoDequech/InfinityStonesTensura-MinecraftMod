package com.infinitystones.tensura.skill;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.infinitystones.tensura.InfinityStones;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The original per-stone ability trees, extracted from the Palladium powers (tools/gen-trees.ps1) into
 * data/infinity/stone_trees/<stone>.json. Read from the mod jar on both sides, so client UI and server rules agree.
 */
public final class StoneTree {
    public enum Activation { PASSIVE, PRESS, HOLD, TOGGLE }

    public record Cost(String type, @Nullable ResourceLocation item, int amount) {
        public boolean isItem() { return "item".equals(type); }
    }

    /** Short texts shown by the wheel's Shift panel; null fields are unknown. Never present on tier-2 nodes. */
    public record Stats(@Nullable String damage, @Nullable String range, @Nullable String duration, @Nullable String area,
                        @Nullable Boolean players) {}

    public record Node(Stone stone, String id, String originalType, String title, String description, @Nullable String icon,
                       @Nullable Cost cost, List<String> parents, List<String> requiresPowers, Activation activation,
                       int cooldownTicks, boolean inTree, boolean inBar, List<String> commands, List<String> firstTick,
                       List<String> lastTick, JsonObject raw, float guiX, float guiY, boolean hasPos,
                       int page, @Nullable Stats stats) {
        public MutableComponent displayName() {
            // original nodes carry a literal English title: translate it through ability.infinity.<stone>_stone.<id>.title
            if (title != null && !title.isBlank() && !title.startsWith("@"))
                return Component.translatableWithFallback("ability.infinity." + stone.key + "_stone." + id + ".title", title);
            return text(title);
        }
        public MutableComponent displayDescription() { return text(description); }
        public boolean isActive() { return activation != Activation.PASSIVE; }
        /** Flagged "tier": 2 (see {@link StoneTier}). */
        public boolean upper() { return StoneTree.of(stone).upper.contains(id); }
        public String modeId() { return stone.key + "." + id; }
        /**
         * A passive the player switches on/off on the wheel (OFF by default): every real passive of the tree —
         * not layout dummies, hidden helpers (not in the tree) or one-shot grants.
         */
        public boolean isPassiveToggle() {
            return activation == Activation.PASSIVE && inTree && !"palladium:dummy".equals(originalType)
                    && !ALWAYS_ON.contains(modeId());
        }

        private static MutableComponent text(String s) {
            if (s == null || s.isEmpty()) return Component.empty();
            return s.startsWith("@") ? Component.translatable(s.substring(1)) : Component.literal(s);
        }
    }

    private static final Map<Stone, StoneTree> TREES = new EnumMap<>(Stone.class);

    public final Stone stone;
    public final Map<String, Node> nodes;
    /** Nodes that can be triggered from a Tensura slot (press/hold/toggle), in original list order. */
    public final List<Node> activeNodes;
    /** What the Infinity wheel lists: usable powers (minus superseded ones) followed by the passive switches. */
    public final List<Node> wheelNodes;
    /** Top-level JSON of each node (wheel metadata such as "aim" and "settings"; see notes/wheel-ux-api.md). */
    public final Map<String, JsonObject> json;
    /** Ids flagged "tier": 2 in any definition. */
    public final java.util.Set<String> upper;

    /** Superseded on the wheel: Mind flight is switched by its own passive (flight-dummy) now. Kept off. */
    public static final java.util.Set<String> HIDDEN_IN_WHEEL = java.util.Set.of("mind.flight_toggle");
    /** Passives that stay always-on (one-shot grants that cannot be undone). */
    static final java.util.Set<String> ALWAYS_ON = java.util.Set.of("mind.recipe");

    private StoneTree(Stone stone, Map<String, Node> nodes, Map<String, JsonObject> json, java.util.Set<String> upper) {
        this.stone = stone;
        this.upper = java.util.Set.copyOf(upper);
        this.nodes = Collections.unmodifiableMap(nodes);
        this.json = Collections.unmodifiableMap(json);
        List<Node> active = new ArrayList<>();
        for (Node n : nodes.values()) if (n.isActive() && n.inBar()) active.add(n);
        this.activeNodes = List.copyOf(active);
        List<Node> wheel = new ArrayList<>();
        for (Node n : active) if (!HIDDEN_IN_WHEEL.contains(n.modeId())) wheel.add(n);
        for (Node n : nodes.values()) if (n.isPassiveToggle()) wheel.add(n);
        this.wheelNodes = List.copyOf(wheel);
    }

    /** Top-level JSON of a node (empty object if unknown). */
    public JsonObject json(String nodeId) { JsonObject o = json.get(nodeId); return o == null ? new JsonObject() : o; }

    public static StoneTree of(Stone stone) {
        return TREES.computeIfAbsent(stone, StoneTree::load);
    }

    private static StoneTree load(Stone stone) {
        String path = "/data/infinity/stone_trees/" + stone.key + ".json";
        try (var in = StoneTree.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("missing " + path);
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, Node> nodes = new LinkedHashMap<>();
            Map<String, JsonObject> json = new LinkedHashMap<>();
            List<JsonElement> all = new ArrayList<>();
            root.getAsJsonArray("nodes").forEach(all::add);
            // new powers (not in the original mod) live in extras/<stone>.json so regenerating the original trees never drops them
            // extras/port/<stone>.json = ported/new abilities (port-originals); same id as an original node = override
            for (String extra : new String[]{"extras/", "extras/port/", "extras/v2a/", "extras/v2b/", "extras/v2c/", "extras/v2d/"}) {
                try (var ex = StoneTree.class.getResourceAsStream("/data/infinity/stone_trees/" + extra + stone.key + ".json")) {
                    if (ex != null) JsonParser.parseReader(new InputStreamReader(ex, StandardCharsets.UTF_8)).getAsJsonObject()
                            .getAsJsonArray("nodes").forEach(all::add);
                }
            }
            java.util.Set<String> upper = new java.util.HashSet<>();
            for (JsonElement e : all) {
                JsonObject o = e.getAsJsonObject();
                if (o.has("tier") && o.get("tier").getAsInt() >= 2) upper.add(o.get("id").getAsString());
                Cost cost = null;
                if (o.has("cost") && o.get("cost").isJsonObject()) {
                    JsonObject c = o.getAsJsonObject("cost");
                    cost = new Cost(c.get("type").getAsString(),
                            c.has("item") ? ResourceLocation.parse(c.get("item").getAsString()) : null, c.get("amount").getAsInt());
                }
                Node n = new Node(stone, o.get("id").getAsString(), str(o, "original_type"), str(o, "title"), str(o, "description"),
                        o.has("icon") && !o.get("icon").isJsonNull() ? o.get("icon").getAsString() : null,
                        cost, strings(o, "parents"), strings(o, "requires_stones"),
                        Activation.valueOf(str(o, "activation").toUpperCase()), o.has("cooldown_ticks") ? o.get("cooldown_ticks").getAsInt() : 0,
                        bool(o, "in_tree"), bool(o, "in_bar"), strings(o, "commands"), strings(o, "first_tick_commands"),
                        strings(o, "last_tick_commands"), o.has("raw") ? o.getAsJsonObject("raw") : new JsonObject(),
                        pos(o, 0), pos(o, 1), o.has("gui_position") && o.get("gui_position").isJsonArray()
                                && o.getAsJsonArray("gui_position").size() >= 2,
                        o.has("page") ? Math.max(1, o.get("page").getAsInt()) : 1, stats(o));
                nodes.put(n.id(), n);
                json.put(n.id(), o);
            }
            InfinityStones.LOGGER.info("Loaded {} tree: {} nodes", stone.key, nodes.size());
            return new StoneTree(stone, nodes, json, upper);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not read stone tree " + path, ex);
        }
    }

    private static @Nullable Stats stats(JsonObject o) {
        if (!o.has("stats") || !o.get("stats").isJsonObject() || (o.has("tier") && o.get("tier").getAsInt() >= 2)) return null;
        JsonObject s = o.getAsJsonObject("stats");
        return new Stats(opt(s, "damage"), opt(s, "range"), opt(s, "duration"), opt(s, "area"),
                s.has("players") && !s.get("players").isJsonNull() ? s.get("players").getAsBoolean() : null);
    }
    private static @Nullable String opt(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null; }
    private static String str(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; }
    private static float pos(JsonObject o, int i) {
        if (!o.has("gui_position") || !o.get("gui_position").isJsonArray()) return 0;
        JsonArray a = o.getAsJsonArray("gui_position");
        return a.size() > i ? a.get(i).getAsFloat() : 0;
    }
    private static boolean bool(JsonObject o, String k) { return o.has(k) && !o.get(k).isJsonNull() && o.get(k).getAsBoolean(); }
    private static List<String> strings(JsonObject o, String k) {
        if (!o.has(k) || !o.get(k).isJsonArray()) return List.of();
        JsonArray a = o.getAsJsonArray(k);
        List<String> out = new ArrayList<>(a.size());
        for (JsonElement e : a) if (!e.isJsonNull() && !e.getAsString().isBlank()) out.add(e.getAsString());
        return List.copyOf(out);
    }
}
