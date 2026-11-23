package com.infinitystones.tensura.client.wheel;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneProgress;
import com.infinitystones.tensura.skill.StoneTree;
import com.infinitystones.tensura.wheel.WheelPayloads;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import io.github.manasmods.manascore.skill.api.ManasSkillInstance;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The Infinity tree: one tab per carried stone, the original node layout (gui_position) drawn as a constellation of
 * energy links. Unlock state is computed client-side from the synced skill data; buying goes to the server, which
 * validates and pays (no permission needed). Drag to pan, scroll to zoom, click a node to inspect, button to unlock.
 */
public final class StoneTreeScreen extends Screen {
    private static final float UNIT = 52F, NODE_R = 10.5F;
    private static final int PANEL_W = 132, HEADER_H = 34;
    private static int lastStone = -1;

    private enum State { OWNED, AVAILABLE, LOCKED }

    private final long openedAt = System.currentTimeMillis();
    private final RandomSource rng = RandomSource.create();
    private final List<Stone> tabs = new ArrayList<>();
    private Stone stone;
    private String selected;
    private float zoom = 1F, panX, panY;
    private boolean dragging;
    private double dragStartX, dragStartY;
    private float dragPanX, dragPanY;
    private long lastFrame = -1;
    private final Map<String, State> prevStates = new HashMap<>();
    private final List<float[]> parts = new ArrayList<>(); // x,y,vx,vy,life,max,size,color
    private final List<float[]> waves = new ArrayList<>(); // x,y,start,color
    private final List<float[]> stars = new ArrayList<>();
    private long buyPendingAt = -1;
    private boolean needFit = true;
    private float fitZoom = 1F;
    private long viewOpenedAt = System.currentTimeMillis();
    /** Generated 32x32 icons (textures/icon/tree/STONE/NODE.png) for nodes whose original icon was a placeholder. */
    private static final Map<String, ResourceLocation> TREE_ICONS = new HashMap<>();
    private static final ResourceLocation NO_ICON = ResourceLocation.fromNamespaceAndPath("infinity", "none");

    public StoneTreeScreen(Stone initial) {
        super(Component.translatable("infinity.tree.title"));
        this.stone = initial;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override protected void init() {
        tabs.clear();
        LocalPlayer p = minecraft == null ? null : minecraft.player;
        if (p != null) for (Stone s : Stone.values()) if (s.isHeldBy(p) && StoneProgress.instance(p, s).isPresent()) tabs.add(s);
        if (stone == null || !tabs.contains(stone)) {
            Stone remembered = lastStone >= 0 ? Stone.values()[lastStone] : null;
            stone = remembered != null && tabs.contains(remembered) ? remembered : tabs.isEmpty() ? null : tabs.get(0);
        }
        if (stars.isEmpty()) {
            RandomSource r = RandomSource.create(7);
            for (int i = 0; i < 140; i++) stars.add(new float[]{r.nextFloat(), r.nextFloat(), 0.4F + r.nextFloat() * 1.1F, r.nextFloat() * 6.28F});
        }
        resetView();
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 0.9F, 0.3F));
    }

    private void resetView() {
        zoom = 1F; panX = 0; panY = 0; prevStates.clear(); selected = null; needFit = true; viewOpenedAt = System.currentTimeMillis();
        if (stone != null) lastStone = stone.ordinal();
    }

    // ------------------------------------------------------------ model

    private List<StoneTree.Node> visibleNodes() {
        List<StoneTree.Node> out = new ArrayList<>();
        if (stone == null) return out;
        var me = net.minecraft.client.Minecraft.getInstance().player;
        for (StoneTree.Node n : StoneTree.of(stone).nodes.values())
            if (n.inTree() && !n.title().isBlank() && n.hasPos() && !(me != null && com.infinitystones.tensura.skill.StoneTier.hidden(me, n))) out.add(n);
        return out;
    }

    private State state(LocalPlayer p, StoneTree.Node n, Set<Stone> held) {
        if (com.infinitystones.tensura.skill.StoneTier.hidden(p, n)) return State.LOCKED;
        if (StoneProgress.isUnlocked(p, stone, n.id(), held)) return State.OWNED;
        if (n.cost() == null) return State.LOCKED;
        Optional<ManasSkillInstance> inst = StoneProgress.instance(p, stone);
        if (inst.isEmpty() || StoneProgress.bought(inst.get()).contains(n.id())) return State.LOCKED;
        for (String parent : n.parents()) if (!StoneProgress.parentOk(p, stone, parent, held)) return State.LOCKED;
        for (String power : n.requiresPowers()) { Stone o = Stone.byOriginalPower(power); if (o != null && !held.contains(o)) return State.LOCKED; }
        return State.AVAILABLE;
    }

    private int have(LocalPlayer p, StoneTree.Cost c) {
        if ("xp_levels".equals(c.type())) return p.experienceLevel;
        if (c.item() == null) return 0;
        Item item = BuiltInRegistries.ITEM.get(c.item());
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        return n;
    }

    private boolean affordable(LocalPlayer p, StoneTree.Cost c) { return p.getAbilities().instabuild || have(p, c) >= c.amount(); }

    // geometry
    private float midX, midY;
    private void computeMid(List<StoneTree.Node> nodes) {
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (StoneTree.Node n : nodes) { minX = Math.min(minX, n.guiX()); maxX = Math.max(maxX, n.guiX()); minY = Math.min(minY, n.guiY()); maxY = Math.max(maxY, n.guiY()); }
        midX = nodes.isEmpty() ? 0 : (minX + maxX) / 2; midY = nodes.isEmpty() ? 0 : (minY + maxY) / 2;
        if (needFit && !nodes.isEmpty()) {
            // fit the whole tree in the canvas (with a margin for the halos), never larger than 1.3x
            float cw = width - PANEL_W - 40, ch = height - HEADER_H - 22 - 40;
            float spanX = (maxX - minX) * UNIT + NODE_R * 2, spanY = (maxY - minY) * UNIT + NODE_R * 2;
            fitZoom = Mth.clamp(Math.min(cw / Math.max(1, spanX), ch / Math.max(1, spanY)), 0.35F, 1.3F);
            zoom = fitZoom;
            needFit = false;
        }
    }
    private float canvasCx() { return (width - PANEL_W) / 2F + panX; }
    private float canvasCy() { return HEADER_H + (height - HEADER_H - 22) / 2F + panY; }
    private float sx(StoneTree.Node n) { return canvasCx() + (n.guiX() - midX) * UNIT * zoom; }
    private float sy(StoneTree.Node n) { return canvasCy() + (n.guiY() - midY) * UNIT * zoom; }

    // ------------------------------------------------------------ render

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long time = System.currentTimeMillis();
        float dt = lastFrame < 0 ? 0 : Math.min(0.05F, (time - lastFrame) / 1000F);
        lastFrame = time;
        LocalPlayer p = minecraft == null ? null : minecraft.player;
        float open = Mth.clamp((time - openedAt) / 260F, 0, 1);
        int col = stone == null ? 0xFF3FD56A : stone.color();

        // backdrop: deep space, faint grid, nebula in the stone colour, twinkling stars
        g.fill(0, 0, width, height, a(0xF2, 0x05060B, open));
        additive(true);
        for (int i = 0; i < 3; i++) {
            float nx = width * (0.25F + 0.25F * i) + Mth.sin(time / 9000F + i * 2) * 30, ny = height * (0.45F + 0.12F * Mth.cos(time / 7000F + i));
            float rad = 120 + 30 * i;
            for (int k = 8; k >= 1; k--) disc(g, nx, ny, rad * k / 8F, a(0x04, col, open)); // stacked = soft radial falloff
        }
        additive(false);
        int grid = (int) (24 * zoom);
        if (grid >= 10) {
            int ox = (int) (canvasCx() % grid), oy = (int) (canvasCy() % grid);
            for (int x = ox; x < width - PANEL_W; x += grid) g.fill(x, HEADER_H, x + 1, height, a(0x0C, col, open));
            for (int y = HEADER_H + (oy - HEADER_H % grid + grid) % grid; y < height; y += grid) g.fill(0, y, width - PANEL_W, y + 1, a(0x0C, col, open));
        }
        for (float[] s : stars) {
            float tw = 0.55F + 0.45F * Mth.sin(time / 600F + s[3]);
            int alpha = (int) (180 * tw * open);
            g.fill((int) (s[0] * width), (int) (s[1] * height), (int) (s[0] * width) + (s[2] > 1.2F ? 2 : 1), (int) (s[1] * height) + (s[2] > 1.2F ? 2 : 1),
                    (Mth.clamp(alpha, 0, 255) << 24) | 0xDDE6FF);
        }

        if (p == null || stone == null) {
            g.drawCenteredString(font, Component.translatable("infinity.wheel.empty"), width / 2, height / 2, 0xFFBBBBBB);
            return;
        }

        Set<Stone> held = StoneProgress.heldStones(p);
        List<StoneTree.Node> nodes = visibleNodes();
        computeMid(nodes);
        Map<String, StoneTree.Node> byId = new HashMap<>();
        Map<String, State> states = new HashMap<>();
        for (StoneTree.Node n : nodes) { byId.put(n.id(), n); states.put(n.id(), state(p, n, held)); }

        // celebrate nodes that just became owned (reacts to the server's confirmation)
        for (StoneTree.Node n : nodes) {
            State was = prevStates.get(n.id()), now = states.get(n.id());
            if (was != null && was != State.OWNED && now == State.OWNED) celebrate(sx(n), sy(n), col);
            prevStates.put(n.id(), now);
        }

        // energy links parent → child
        g.enableScissor(0, HEADER_H, width - PANEL_W, height);
        for (StoneTree.Node n : nodes) {
            for (String pid : n.parents()) {
                StoneTree.Node par = byId.get(pid);
                if (par == null) continue;
                boolean lit = states.get(pid) == State.OWNED && states.get(n.id()) == State.OWNED;
                boolean next = states.get(pid) == State.OWNED && states.get(n.id()) == State.AVAILABLE;
                float x0 = sx(par), y0 = sy(par), x1 = sx(n), y1 = sy(n);
                if (lit) {
                    additive(true);
                    line(g, x0, y0, x1, y1, 6 * zoom, a(0x30, col, open));
                    line(g, x0, y0, x1, y1, 2.2F * zoom, a(0xE0, lighten(col, 0.25F), open));
                    float len = Mth.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0));
                    for (int k = 0; k < Math.max(1, (int) (len / 40)); k++) {
                        float t = ((time / 900F) + k * 40F / Math.max(1, len)) % 1F;
                        disc(g, Mth.lerp(t, x0, x1), Mth.lerp(t, y0, y1), 2.6F * zoom, a(0xD0, lighten(col, 0.6F), open));
                    }
                    additive(false);
                } else if (next) {
                    float pulse = 0.5F + 0.5F * Mth.sin(time / 300F);
                    dashed(g, x0, y0, x1, y1, 1.6F * zoom, a((int) (0x70 + 0x50 * pulse), col, open), time);
                } else {
                    line(g, x0, y0, x1, y1, 1.4F * zoom, a(0x40, 0x8088A0, open));
                }
            }
        }

        // nodes
        StoneTree.Node hovered = null;
        for (StoneTree.Node n : nodes) {
            float x = sx(n), y = sy(n), r = NODE_R * zoom;
            boolean hov = mouseX < width - PANEL_W && (mouseX - x) * (mouseX - x) + (mouseY - y) * (mouseY - y) <= r * r * 1.3F;
            if (hov) hovered = n;
            State st = states.get(n.id());
            boolean sel = n.id().equals(selected);
            float appear = appear(n, time);
            if (appear <= 0.01F) continue;
            float grow = (hov ? 1.12F : 1F) * (sel ? 1.08F : 1F) * appear;
            float rr = r * grow;
            if (appear < 1F) { // reveal flash, rippling out from the centre of the tree
                additive(true);
                disc(g, x, y, r * (1.2F + 1.4F * appear), a((int) (0x50 * (1 - appear)), col, open));
                additive(false);
            }
            if (hov) {
                additive(true);
                disc(g, x, y, rr * 2.1F, a(0x16, col, open));
                disc(g, x, y, rr * 1.45F, a(0x22, lighten(col, 0.3F), open));
                additive(false);
            }
            switch (st) {
                case OWNED -> {
                    additive(true);
                    disc(g, x, y, rr * 1.6F, a((int) (0x1C + 0x10 * Mth.sin(time / 400F + n.guiX())), col, open));
                    additive(false);
                    disc(g, x, y, rr, a(0xFF, darken(col, 0.45F), open));
                    ring(g, x, y, rr - 2 * zoom, rr + 0.5F, a(0xFF, lighten(col, 0.35F), open));
                }
                case AVAILABLE -> {
                    float pulse = 0.5F + 0.5F * Mth.sin(time / 260F);
                    disc(g, x, y, rr, a(0xF0, 0x0C0E16, open));
                    boolean can = affordable(p, n.cost());
                    ring(g, x, y, rr - 1.5F * zoom, rr + 0.5F, a((int) (0x90 + 0x60 * pulse), can ? col : 0x8A7050, open));
                    float spin = (time % 6000) / 6000F * Mth.TWO_PI;
                    for (int d = 0; d < 8; d++) arc(g, x, y, rr + 3 * zoom, rr + 4.5F * zoom, spin + d * Mth.TWO_PI / 8, spin + d * Mth.TWO_PI / 8 + 0.35F,
                            a((int) (0x60 + 0x60 * pulse), col, open));
                }
                case LOCKED -> {
                    disc(g, x, y, rr, a(0xE8, 0x0A0A10, open));
                    ring(g, x, y, rr - 1.2F * zoom, rr, a(0x90, 0x3A3E4C, open));
                }
            }
            if (sel) {
                ring(g, x, y, rr + 6 * zoom, rr + 7.2F * zoom, a(0xFF, 0xFFFFFF, open));
                float spin = (time % 4000) / 4000F * Mth.TWO_PI;
                additive(true);
                for (int d = 0; d < 3; d++) arc(g, x, y, rr + 8 * zoom, rr + 9.4F * zoom, -spin + d * Mth.TWO_PI / 3, -spin + d * Mth.TWO_PI / 3 + 0.9F, a(0xB0, lighten(col, 0.5F), open));
                additive(false);
            }
            icon(g, n, x, y, zoom * grow, st != State.LOCKED);
            if (st == State.AVAILABLE && affordable(p, n.cost())) {
                disc(g, x + rr * 0.72F, y - rr * 0.72F, 4.2F * zoom, a(0xFF, lighten(col, 0.2F), open));
                g.drawCenteredString(font, "+", (int) (x + rr * 0.72F), (int) (y - rr * 0.72F - 4), 0xFFFFFFFF);
            }
        }

        // particles / waves
        stepFx(g, dt, time);
        g.disableScissor();

        // header: title + stone tabs
        g.fill(0, 0, width, HEADER_H, a(0xE8, 0x07080E, open));
        g.fill(0, HEADER_H - 1, width, HEADER_H, a(0xFF, col, open));
        additive(true);
        g.fill(0, HEADER_H, width, HEADER_H + 3, a(0x40, col, open));
        // a sheen sweeping along the header rule
        float sweep = ((time - openedAt) % 5200) / 5200F * (width + 240) - 120;
        for (int k = 0; k < 6; k++) {
            int half = 60 - k * 10;
            g.fill((int) sweep - half, HEADER_H - 2, (int) sweep + half, HEADER_H + 1, a(0x18 + k * 0x10, lighten(col, 0.5F), open));
        }
        additive(false);
        g.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), 10, 13, 0xFFFFFFFF, true);
        int tx = 10 + font.width(title) + 24;
        for (Stone s : tabs) {
            boolean act = s == stone, th = inside(mouseX, mouseY, tx - 2, 5, 24, 24);
            int sc = s.color();
            if (act) { additive(true); disc(g, tx + 10, 17, 15, a(0x50, sc, open)); additive(false); }
            disc(g, tx + 10, 17, 11, a(0xFF, act ? darken(sc, 0.55F) : th ? darken(sc, 0.35F) : 0x15161E, open));
            ring(g, tx + 10, 17, 10, 11.5F, a(0xFF, act ? lighten(sc, 0.4F) : darken(sc, 0.8F), open));
            g.renderItem(stoneStack(s), tx + 2, 9);
            tx += 30;
        }
        Component stoneName = Component.translatable("infinity.skill." + stone.key + "_stone").withStyle(st -> st.withColor(lighten(col, 0.3F) & 0xFFFFFF));
        g.drawString(font, stoneName, tx + 8, 13, 0xFFFFFFFF, true);

        // footer: currencies + hints
        int aspects = 0;
        Item asp = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("infinity", "aspects_of_existance"));
        for (ItemStack s : p.getInventory().items) if (s.is(asp)) aspects += s.getCount();
        g.fill(0, height - 20, width - PANEL_W, height, a(0xD0, 0x07080E, open));
        g.renderItem(new ItemStack(asp), 6, height - 18);
        g.drawString(font, String.valueOf(aspects), 24, height - 14, 0xFFFFFFFF, true);
        g.drawString(font, Component.translatable("infinity.tree.xp", p.experienceLevel), 60, height - 14, 0xFF9CE07A, true);
        Component hint = Component.translatable("infinity.tree.hint");
        g.drawString(font, hint, width - PANEL_W - 8 - font.width(hint), height - 14, 0xFF6E7486, false);

        // side panel
        renderPanel(g, p, byId.get(selected != null ? selected : hovered != null ? hovered.id() : ""), states, mouseX, mouseY, col, open, time);

        // hover tooltip (short) when something else is selected
        if (hovered != null && !hovered.id().equals(selected)) {
            g.renderTooltip(font, List.of(hovered.displayName().getVisualOrderText(),
                    Component.translatable("infinity.tree.state." + states.get(hovered.id()).name().toLowerCase()).withStyle(stateColor(states.get(hovered.id()))).getVisualOrderText()),
                    mouseX, mouseY);
        }
    }

    private int buyX, buyY, buyW = PANEL_W - 20, buyH = 20;

    private void renderPanel(GuiGraphics g, LocalPlayer p, StoneTree.Node n, Map<String, State> states, int mx, int my, int col, float open, long time) {
        int x0 = width - PANEL_W;
        g.fill(x0, HEADER_H, width, height, a(0xEE, 0x090A12, open));
        g.fill(x0, HEADER_H, x0 + 1, height, a(0xFF, darken(col, 0.8F), open));
        buyY = -100;
        if (n == null) {
            int y = HEADER_H + 14;
            for (FormattedCharSequence l : font.split(Component.translatable("infinity.tree.pick"), PANEL_W - 20)) { g.drawString(font, l, x0 + 10, y, 0xFF8A90A0, false); y += 10; }
            return;
        }
        State st = states.get(n.id());
        int y = HEADER_H + 10;
        // big icon with halo
        float ix = x0 + PANEL_W / 2F, iy = y + 22;
        if (st == State.OWNED) { additive(true); disc(g, ix, iy, 30, a(0x30, col, open)); additive(false); }
        disc(g, ix, iy, 20, a(0xFF, st == State.OWNED ? darken(col, 0.45F) : 0x10121A, open));
        ring(g, ix, iy, 18.5F, 20.5F, a(0xFF, st == State.LOCKED ? 0x3A3E4C : lighten(col, 0.3F), open));
        icon(g, n, ix, iy, 1.6F, st != State.LOCKED);
        y += 50;
        for (FormattedCharSequence l : font.split(n.displayName().withStyle(ChatFormatting.BOLD), PANEL_W - 20)) { g.drawCenteredString(font, l, (int) ix, y, 0xFFFFFFFF); y += 10; }
        g.drawCenteredString(font, Component.translatable("infinity.tree.state." + st.name().toLowerCase()).withStyle(stateColor(st)), (int) ix, y + 1, 0xFFFFFFFF);
        y += 13;
        g.drawCenteredString(font, Component.translatable("infinity.tree.kind." + n.activation().name().toLowerCase()), (int) ix, y, 0xFF8FB8FF);
        y += 14;
        g.fill(x0 + 10, y, width - 10, y + 1, a(0x60, col, open));
        y += 6;
        int descLines = 0;
        for (FormattedCharSequence l : font.split(n.displayDescription(), PANEL_W - 20)) {
            if (y > height - 70) break;
            g.drawString(font, l, x0 + 10, y, 0xFFB8BED0, false); y += 10; descLines++;
        }
        // cost + unlock button
        if (st != State.OWNED && n.cost() != null) {
            int cy = height - 58;
            boolean xp = "xp_levels".equals(n.cost().type());
            int have = have(p, n.cost());
            boolean can = affordable(p, n.cost());
            if (!xp && n.cost().item() != null) g.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(n.cost().item())), x0 + 10, cy - 4);
            Component cost = xp ? Component.translatable("infinity.tree.cost_xp", n.cost().amount())
                    : Component.translatable("infinity.tree.cost_item", n.cost().amount());
            g.drawString(font, cost, x0 + 30, cy, 0xFFFFFFFF, true);
            g.drawString(font, Component.translatable("infinity.tree.have", have), x0 + 30, cy + 10, can ? 0xFF7FE08A : 0xFFE07A7A, false);
            buyX = x0 + 10; buyY = height - 30;
            boolean hb = inside(mx, my, buyX, buyY, buyW, buyH);
            boolean enabled = st == State.AVAILABLE && can;
            float pulse = 0.5F + 0.5F * Mth.sin(time / 300F);
            if (enabled) { additive(true); g.fill(buyX - 2, buyY - 2, buyX + buyW + 2, buyY + buyH + 2, a((int) (0x30 + 0x30 * pulse), col, open)); additive(false); }
            g.fill(buyX, buyY, buyX + buyW, buyY + buyH, a(0xFF, enabled ? (hb ? lighten(col, 0.15F) : darken(col, 0.7F)) : 0x22232C, open));
            Component label = Component.translatable(st == State.LOCKED ? "infinity.tree.need_parent" : can ? "infinity.tree.unlock" : "infinity.tree.cant_afford")
                    .withStyle(ChatFormatting.BOLD);
            g.drawCenteredString(font, label, buyX + buyW / 2, buyY + 6, enabled ? 0xFFFFFFFF : 0xFF7A7E8C);
            if (buyPendingAt > 0 && time - buyPendingAt < 1500) g.fill(buyX, buyY + buyH - 2, buyX + (int) (buyW * ((time - buyPendingAt) % 500) / 500F), buyY + buyH, 0xFFFFFFFF);
        }
    }

    private static ChatFormatting stateColor(State s) {
        return switch (s) { case OWNED -> ChatFormatting.GREEN; case AVAILABLE -> ChatFormatting.YELLOW; case LOCKED -> ChatFormatting.DARK_GRAY; };
    }

    // ------------------------------------------------------------ input

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return super.mouseClicked(mx, my, button);
        // tabs
        int tx = 10 + font.width(title) + 24;
        for (Stone s : tabs) {
            if (inside(mx, my, tx - 2, 5, 24, 24)) {
                if (s != stone) { stone = s; resetView(); click(1.4F); }
                return true;
            }
            tx += 30;
        }
        // unlock button
        if (selected != null && inside(mx, my, buyX, buyY, buyW, buyH)) { tryBuy(); return true; }
        if (mx >= width - PANEL_W || my < HEADER_H) return true;
        // nodes
        for (StoneTree.Node n : visibleNodes()) {
            float x = sx(n), y = sy(n), r = NODE_R * zoom;
            if ((mx - x) * (mx - x) + (my - y) * (my - y) <= r * r * 1.3F) {
                selected = n.id(); click(1.7F);
                for (int i = 0; i < 8; i++) spawn(x, y, (rng.nextFloat() - 0.5F) * 80, (rng.nextFloat() - 0.5F) * 80, 0.4F, 1.2F, stone.color());
                return true;
            }
        }
        dragging = true; dragStartX = mx; dragStartY = my; dragPanX = panX; dragPanY = panY;
        return true;
    }

    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) { panX = dragPanX + (float) (mx - dragStartX); panY = dragPanY + (float) (my - dragStartY); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (dragging && Math.abs(mx - dragStartX) + Math.abs(my - dragStartY) < 3) selected = null; // click on empty space
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx >= width - PANEL_W) return true;
        float old = zoom;
        zoom = Mth.clamp(zoom * (sy > 0 ? 1.12F : 1 / 1.12F), 0.3F, 2.6F);
        // zoom around the cursor
        float k = zoom / old;
        float cx = (width - PANEL_W) / 2F, cy = HEADER_H + (height - HEADER_H - 22) / 2F;
        panX = (float) (mx - cx - (mx - cx - panX) * k);
        panY = (float) (my - cy - (my - cy - panY) * k);
        return true;
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode >= 49 && keyCode < 49 + tabs.size()) { stone = tabs.get(keyCode - 49); resetView(); click(1.4F); return true; } // 1..6
        if (keyCode == 82) { zoom = fitZoom; panX = panY = 0; return true; } // R = recentre
        if ((keyCode == 257 || keyCode == 335) && selected != null) { tryBuy(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void tryBuy() {
        if (minecraft == null || minecraft.player == null || selected == null) return;
        StoneTree.Node n = StoneTree.of(stone).nodes.get(selected);
        if (n == null || n.cost() == null) return;
        State st = state(minecraft.player, n, StoneProgress.heldStones(minecraft.player));
        if (st != State.AVAILABLE || !affordable(minecraft.player, n.cost())) { click(0.6F); return; }
        buyPendingAt = System.currentTimeMillis();
        PacketDistributor.sendToServer(new WheelPayloads.TreeBuy(stone.ordinal(), n.id()));
        click(1.2F);
    }

    // ------------------------------------------------------------ effects

    private void celebrate(float x, float y, int col) {
        buyPendingAt = -1;
        float now = System.currentTimeMillis() - openedAt; // relative ms: a float can't hold epoch millis
        waves.add(new float[]{x, y, now, Float.intBitsToFloat(col)});
        waves.add(new float[]{x, y, now + 90, Float.intBitsToFloat(lighten(col, 0.6F) | 0xFF000000)});
        for (int i = 0; i < 60; i++) {
            float an = rng.nextFloat() * Mth.TWO_PI, sp = 60 + rng.nextFloat() * 200;
            int c = switch (rng.nextInt(4)) { case 0 -> 0xFFFFFFFF; case 1 -> lighten(col, 0.5F) | 0xFF000000; default -> col; };
            spawn(x, y, Mth.cos(an) * sp, Mth.sin(an) * sp, 0.5F + rng.nextFloat() * 0.6F, 1F + rng.nextFloat() * 1.8F, c);
        }
        if (minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 0.4F));
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.6F, 0.15F));
        }
    }

    private void spawn(float x, float y, float vx, float vy, float life, float size, int color) {
        if (parts.size() < 300) parts.add(new float[]{x, y, vx, vy, life, life, size, Float.intBitsToFloat(color)});
    }

    private void stepFx(GuiGraphics g, float dt, long time) {
        float drag = (float) Math.pow(0.08, dt);
        additive(true);
        for (Iterator<float[]> it = parts.iterator(); it.hasNext(); ) {
            float[] q = it.next();
            q[4] -= dt; if (q[4] <= 0) { it.remove(); continue; }
            q[0] += q[2] * dt; q[1] += q[3] * dt; q[2] *= drag; q[3] *= drag;
            float k = q[4] / q[5];
            int c = Float.floatToRawIntBits(q[7]);
            disc(g, q[0], q[1], q[6] * 3, a((int) (50 * k), c, 1));
            disc(g, q[0], q[1], q[6] * (0.5F + 0.5F * k), a((int) (240 * k), c, 1));
        }
        for (Iterator<float[]> it = waves.iterator(); it.hasNext(); ) {
            float[] w = it.next();
            float t = ((time - openedAt) - w[2]) / 600F;
            if (t > 1) { it.remove(); continue; }
            if (t < 0) continue;
            float e = 1 - (1 - t) * (1 - t) * (1 - t);
            float r = 10 + 70 * e * zoom;
            ring(g, w[0], w[1], r - (2 + 6 * (1 - t)), r, a((int) (170 * (1 - t)), Float.floatToRawIntBits(w[3]), 1));
        }
        additive(false);
    }

    // ------------------------------------------------------------ helpers

    private void icon(GuiGraphics g, StoneTree.Node n, float x, float y, float scale, boolean lit) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        if (!lit) RenderSystem.setShaderColor(0.4F, 0.4F, 0.46F, 1F);
        String ic = n.icon();
        ResourceLocation generated = treeIcon(n);
        if (generated != null) g.blit(generated, -8, -8, 16, 16, 0, 0, 32, 32, 32, 32);
        else if (ic != null && ic.contains("textures/")) g.blit(ResourceLocation.parse(ic), -8, -8, 0, 0, 16, 16, 16, 16);
        else if (ic != null) g.renderItem(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(ic))), -8, -8);
        else g.renderItem(stoneStack(stone), -8, -8);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        g.pose().popPose();
    }

    private ResourceLocation treeIcon(StoneTree.Node n) {
        if (stone == null || minecraft == null) return null;
        String file = n.id().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        ResourceLocation rl = TREE_ICONS.computeIfAbsent(stone.key + "/" + file, k -> {
            ResourceLocation id = ResourceLocation.tryBuild("infinity", "textures/icon/tree/" + k + ".png");
            return id != null && minecraft.getResourceManager().getResource(id).isPresent() ? id : NO_ICON;
        });
        return rl == NO_ICON ? null : rl;
    }

    /** 0..1 reveal of a node after (re)opening a tab: a wave travelling outwards from the middle of the tree. */
    private float appear(StoneTree.Node n, long time) {
        float dist = Math.abs(n.guiX() - midX) + Math.abs(n.guiY() - midY);
        float t = Mth.clamp((time - viewOpenedAt - dist * 70F) / 260F, 0F, 1F);
        float c = 1.70158F, u = t - 1; // ease-out-back: a small overshoot
        return t >= 1 ? 1 : 1 + (c + 1) * u * u * u + c * u * u;
    }

    private static ItemStack stoneStack(Stone s) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("infinity", s.key + "_stone")));
    }

    private void click(float pitch) {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.2F));
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    private static int a(int alpha, int rgb, float open) { return (Mth.clamp((int) (alpha * open), 0, 255) << 24) | (rgb & 0xFFFFFF); }
    private static int darken(int c, float f) { return ((int) (((c >> 16) & 255) * f) << 16) | ((int) (((c >> 8) & 255) * f) << 8) | (int) ((c & 255) * f); }
    private static int lighten(int c, float f) {
        int r = (c >> 16) & 255, gg = (c >> 8) & 255, b = c & 255;
        return ((int) (r + (255 - r) * f) << 16) | ((int) (gg + (255 - gg) * f) << 8) | (int) (b + (255 - b) * f);
    }

    // primitives (same style as the wheel)
    private static boolean additiveBlend;
    private static void additive(boolean on) { additiveBlend = on; }

    private static void disc(GuiGraphics g, float x, float y, float r, int argb) { arc(g, x, y, 0, r, 0, Mth.TWO_PI, argb); }
    private static void ring(GuiGraphics g, float x, float y, float rIn, float rOut, int argb) { arc(g, x, y, rIn, rOut, 0, Mth.TWO_PI, argb); }

    private static void arc(GuiGraphics g, float cx, float cy, float rIn, float rOut, float a0, float a1, int argb) {
        if ((argb >>> 24) == 0 || rOut <= rIn) return;
        Matrix4f m = g.pose().last().pose();
        int steps = Math.max(3, Math.min(64, (int) Math.ceil((a1 - a0) * Math.max(4, rOut) / 6F)));
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < steps; i++) {
            float t0 = a0 + (a1 - a0) * i / steps, t1 = a0 + (a1 - a0) * (i + 1) / steps;
            float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
            bb.addVertex(m, cx + c0 * rIn, cy + s0 * rIn, 0).setColor(argb);
            bb.addVertex(m, cx + c0 * rOut, cy + s0 * rOut, 0).setColor(argb);
            bb.addVertex(m, cx + c1 * rOut, cy + s1 * rOut, 0).setColor(argb);
            bb.addVertex(m, cx + c0 * rIn, cy + s0 * rIn, 0).setColor(argb);
            bb.addVertex(m, cx + c1 * rOut, cy + s1 * rOut, 0).setColor(argb);
            bb.addVertex(m, cx + c1 * rIn, cy + s1 * rIn, 0).setColor(argb);
        }
        draw(bb);
    }

    private static void line(GuiGraphics g, float x0, float y0, float x1, float y1, float w, int argb) {
        float dx = x1 - x0, dy = y1 - y0, len = Mth.sqrt(dx * dx + dy * dy);
        if (len < 0.01F || (argb >>> 24) == 0) return;
        float nx = -dy / len * w / 2, ny = dx / len * w / 2;
        Matrix4f m = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        bb.addVertex(m, x0 + nx, y0 + ny, 0).setColor(argb);
        bb.addVertex(m, x1 + nx, y1 + ny, 0).setColor(argb);
        bb.addVertex(m, x1 - nx, y1 - ny, 0).setColor(argb);
        bb.addVertex(m, x0 + nx, y0 + ny, 0).setColor(argb);
        bb.addVertex(m, x1 - nx, y1 - ny, 0).setColor(argb);
        bb.addVertex(m, x0 - nx, y0 - ny, 0).setColor(argb);
        draw(bb);
    }

    private static void dashed(GuiGraphics g, float x0, float y0, float x1, float y1, float w, int argb, long time) {
        float len = Mth.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0));
        if (len < 1) return;
        float dash = 6, gap = 5, off = (time / 40F) % (dash + gap);
        for (float d = -off; d < len; d += dash + gap) {
            float a0 = Math.max(0, d), a1 = Math.min(len, d + dash);
            if (a1 <= a0) continue;
            line(g, Mth.lerp(a0 / len, x0, x1), Mth.lerp(a0 / len, y0, y1), Mth.lerp(a1 / len, x0, x1), Mth.lerp(a1 / len, y0, y1), w, argb);
        }
    }

    private static void draw(BufferBuilder bb) {
        RenderSystem.enableBlend();
        if (additiveBlend) RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        else RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(bb.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }
}
