package com.infinitystones.tensura.skill.ability.extra.reality.client;

import com.infinitystones.tensura.skill.ability.extra.reality.ChestNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Creation Chest picker: a creative-style grid of every item of the game (tabs, search, amount) in a dark panel with
 * a red glow. It does not pause the game and leaves the holographic chest visible in the world.
 */
public final class CreationChestScreen extends Screen {
    private static final int RED = 0xE22B3B, MAX_STACKS = 27, SLOT = 18;
    private static final String K = "infinity.extra.chest.";

    private record Btn(int x, int y, int w, int h, Component label, Runnable action) {
        boolean in(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    private final ItemStack lastStack;
    private final int lastCount;
    private final long opened = System.currentTimeMillis();
    private int ticks;

    private List<ChestItems.Tab> tabs = List.of();
    private int tabIdx, tabScroll;
    private List<ChestItems.Entry> shown = List.of();
    private int scroll, lastTipVersion;
    private ItemStack selected = ItemStack.EMPTY;
    private boolean sent, dragging;

    private EditBox search, qtyBox;
    private final List<Btn> buttons = new ArrayList<>();
    private int px, py, pw, ph, gx, gy, cols, rows, visTabs, bottom;

    CreationChestScreen(ItemStack lastStack, int lastCount) {
        super(Component.translatable(K + "title"));
        this.lastStack = lastStack;
        this.lastCount = lastCount;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) { }

    // ------------------------------------------------------------------ layout

    @Override
    protected void init() {
        ChestItems.ensure();
        tabs = ChestItems.tabs == null ? List.of() : ChestItems.tabs;
        pw = Mth.clamp((int) (width * 0.52), 250, 340);
        ph = Mth.clamp(height - 24, 210, 290);
        px = Math.max(4, width - pw - 10);
        py = Math.max(2, (height - ph) / 2);
        bottom = py + ph - 80;
        gx = px + 8;
        gy = py + 68;
        cols = Math.max(1, (pw - 16 - 8) / SLOT);
        rows = Math.max(1, (bottom - 4 - gy) / SLOT);
        visTabs = Math.max(1, (pw - 16 - 24) / 22);

        String old = search == null ? "" : search.getValue();
        search = new EditBox(font, px + 12, py + 50, pw - 24, 10, Component.translatable(K + "search"));
        search.setBordered(false);
        search.setMaxLength(48);
        search.setHint(Component.translatable(K + "search").withStyle(s -> s.withColor(0x8A8090)));
        search.setValue(old);
        search.setResponder(s -> refilter(true));
        addRenderableWidget(search);

        String oq = qtyBox == null ? "1" : qtyBox.getValue();
        qtyBox = new EditBox(font, px + 8 + 20 + 4, bottom + 53, 34, 10, Component.translatable(K + "quantity"));
        qtyBox.setBordered(false);
        qtyBox.setMaxLength(4);
        qtyBox.setFilter(s -> s.matches("\\d{0,4}"));
        qtyBox.setValue(oq);
        addRenderableWidget(qtyBox);
        setInitialFocus(search);

        buttons.clear();
        int bx = px + 8, by = bottom + 32, bw = (pw - 16 - 4 * 3) / 5;
        buttons.add(new Btn(bx, by, bw, 14, Component.literal("1"), () -> setQty(1)));
        buttons.add(new Btn(bx + (bw + 3), by, bw, 14, Component.literal("16"), () -> setQty(16)));
        buttons.add(new Btn(bx + (bw + 3) * 2, by, bw, 14, Component.literal("64"), () -> setQty(64)));
        buttons.add(new Btn(bx + (bw + 3) * 3, by, bw, 14, Component.translatable(K + "stack"), () -> setQty(stackSize())));
        buttons.add(new Btn(bx + (bw + 3) * 4, by, bw, 14, Component.translatable(K + "max"), () -> setQty(stackSize() * MAX_STACKS)));
        buttons.add(new Btn(px + 8, bottom + 50, 16, 16, Component.literal("-"), () -> setQty(qty() - step())));
        buttons.add(new Btn(px + 8 + 20 + 34 + 8, bottom + 50, 16, 16, Component.literal("+"), () -> setQty(qty() + step())));
        int cw = 78;
        buttons.add(new Btn(px + pw - 8 - cw, bottom + 50, cw, 18, Component.translatable(K + "create"), this::create));

        if (selected.isEmpty() && !lastStack.isEmpty()) {
            selected = lastStack.copyWithCount(1);
            qtyBox.setValue(String.valueOf(Mth.clamp(lastCount, 1, maxQty())));
        }
        refilter(false);
    }

    private int stackSize() { return selected.isEmpty() ? 64 : Math.max(1, selected.getMaxStackSize()); }
    private int maxQty() { return stackSize() * MAX_STACKS; }

    private int qty() {
        String t = qtyBox.getValue();
        int v = 1;
        try { if (!t.isEmpty()) v = Integer.parseInt(t); } catch (NumberFormatException ignored) { }
        return Mth.clamp(v, 1, maxQty());
    }

    private int step() { return hasShiftDown() ? 16 : (hasControlDown() ? stackSize() : 1); }

    private void setQty(int v) { qtyBox.setValue(String.valueOf(Mth.clamp(v, 1, maxQty()))); }

    // ------------------------------------------------------------------ filtering

    private void refilter(boolean resetScroll) {
        if (tabs.isEmpty()) { shown = List.of(); return; }
        List<ChestItems.Entry> src = tabs.get(Mth.clamp(tabIdx, 0, tabs.size() - 1)).entries;
        String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) shown = src;
        else {
            List<ChestItems.Entry> out = new ArrayList<>();
            boolean mod = q.startsWith("@");
            String[] tokens = (mod ? q.substring(1) : q).split("\\s+");
            for (ChestItems.Entry e : src) {
                boolean ok = true;
                for (String t : tokens) {
                    if (t.isEmpty()) continue;
                    boolean hit = mod ? e.mod.contains(t)
                            : e.name.contains(t) || e.path.contains(t) || e.mod.equals(t) || (e.tip != null && t.length() >= 2 && e.tip.contains(t));
                    if (!hit) { ok = false; break; }
                }
                if (ok) out.add(e);
            }
            shown = out;
        }
        lastTipVersion = ChestItems.tipVersion;
        if (resetScroll) scroll = 0;
        clampScroll();
    }

    private int maxScroll() { return Math.max(0, (shown.size() + cols - 1) / cols - rows); }
    private void clampScroll() { scroll = Mth.clamp(scroll, 0, maxScroll()); }

    @Override
    public void tick() {
        ticks++;
        if (ticks > 30 && !ChestHolo.any()) { onClose(); return; }
        ChestItems.fillTips(3_000_000L);
        if (ticks % 20 == 0 && lastTipVersion != ChestItems.tipVersion && !search.getValue().isBlank() && !search.getValue().startsWith("@")) refilter(false);
    }

    // ------------------------------------------------------------------ actions

    private void create() {
        if (selected.isEmpty() || sent) return;
        sent = true;
        PacketDistributor.sendToServer(new ChestNet.Request(selected.copyWithCount(1), qty()));
        click(1.5F);
        onClose();
    }

    @Override
    public void removed() {
        if (!sent) {
            sent = true;
            PacketDistributor.sendToServer(new ChestNet.Request(ItemStack.EMPTY, 0));
        }
        super.removed();
    }

    private void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, pitch, 0.5F));
    }

    // ------------------------------------------------------------------ input

    private int tabX(int i) { return px + 8 + 12 + (i - tabScroll) * 22; }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            for (Btn b : buttons) if (b.in(mx, my)) { b.action.run(); click(1.2F); return true; }
            // tabs
            int ty = py + 22;
            if (my >= ty && my < ty + 22) {
                if (mx >= px + 8 && mx < px + 20) { tabScroll = Math.max(0, tabScroll - visTabs / 2 - 1); return true; }
                if (mx >= px + pw - 20 && mx < px + pw - 8) { tabScroll = Math.min(Math.max(0, tabs.size() - visTabs), tabScroll + visTabs / 2 + 1); return true; }
                for (int i = tabScroll; i < Math.min(tabs.size(), tabScroll + visTabs); i++) {
                    if (mx >= tabX(i) && mx < tabX(i) + 20) {
                        tabIdx = i;
                        refilter(true);
                        click(1.0F);
                        return true;
                    }
                }
            }
            // grid
            int idx = slotAt(mx, my);
            if (idx >= 0) {
                selected = shown.get(idx).stack.copyWithCount(1);
                if (hasShiftDown()) setQty(stackSize()); else setQty(qty());
                click(1.6F);
                return true;
            }
            // scrollbar
            int sbx = gx + cols * SLOT + 2;
            if (mx >= sbx && mx < sbx + 6 && my >= gy && my < gy + rows * SLOT) { dragging = true; dragTo(my); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void dragTo(double my) {
        int ms = maxScroll();
        if (ms <= 0) return;
        scroll = Mth.clamp((int) Math.round((my - gy) / (rows * SLOT) * (ms + rows) - rows / 2.0), 0, ms);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) { dragTo(my); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (my >= py + 22 && my < py + 44 && mx >= px && mx < px + pw) {
            tabScroll = Mth.clamp(tabScroll - (int) Math.signum(sy), 0, Math.max(0, tabs.size() - visTabs));
            return true;
        }
        scroll -= (int) Math.signum(sy) * (hasShiftDown() ? rows : 1);
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == 257 || key == 335) { create(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    private int slotAt(double mx, double my) {
        if (mx < gx || my < gy || mx >= gx + cols * SLOT || my >= gy + rows * SLOT) return -1;
        int idx = (scroll + (int) ((my - gy) / SLOT)) * cols + (int) ((mx - gx) / SLOT);
        return idx >= 0 && idx < shown.size() ? idx : -1;
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        float k = Mth.clamp((System.currentTimeMillis() - opened) / 220F, 0, 1);
        k = 1 - (1 - k) * (1 - k);
        float t = (System.currentTimeMillis() % 100000L) / 1000F;
        int cx = px + pw / 2, cy = py + ph / 2;

        ring(g, cx, cy, ph / 2 + 22, 2, (int) (70 * k), t * 0.25F, 120);
        ring(g, cx, cy, ph / 2 + 36, 1, (int) (45 * k), -t * 0.4F, 70);
        ring(g, cx, cy, pw / 2 + 14, 1, (int) (40 * k), t * 0.15F, 150);

        for (int i = 4; i >= 1; i--) g.fill(px - i, py - i, px + pw + i, py + ph + i, ((int) (70 * k / i) << 24) | RED);
        g.fill(px, py, px + pw, py + ph, ((int) (205 * k) << 24) | 0x0B0609);
        g.fillGradient(px, py, px + pw, py + 40, ((int) (95 * k) << 24) | RED, RED & 0xFFFFFF);
        for (int ly = py + 3; ly < py + ph; ly += 6) g.fill(px + 1, ly, px + pw - 1, ly + 1, ((int) (9 * k) << 24) | RED);
        int sy = py + (int) ((System.currentTimeMillis() / 14) % ph);
        g.fill(px, sy, px + pw, sy + 1, ((int) (60 * k) << 24) | RED);
        int edge = ((int) (255 * k) << 24) | 0xFF6A78;
        box(g, px, py, pw, ph, edge);
        int br = 9;
        int ec = ((int) (255 * k) << 24) | 0xFFD0D4;
        g.fill(px - 2, py - 2, px + br, py - 1, ec); g.fill(px - 2, py - 2, px - 1, py + br, ec);
        g.fill(px + pw - br, py - 2, px + pw + 2, py - 1, ec); g.fill(px + pw + 1, py - 2, px + pw + 2, py + br, ec);
        g.fill(px - 2, py + ph + 1, px + br, py + ph + 2, ec); g.fill(px - 2, py + ph - br, px - 1, py + ph + 2, ec);
        g.fill(px + pw - br, py + ph + 1, px + pw + 2, py + ph + 2, ec); g.fill(px + pw + 1, py + ph - br, px + pw + 2, py + ph + 2, ec);

        g.drawString(font, getTitle(), px + 9, py + 8, 0xFFFFD0D4, true);
        Component res = Component.translatable(K + "results", shown.size());
        g.drawString(font, res, px + pw - 9 - font.width(res), py + 8, 0xFF9A8E94, false);
        g.fill(px + 8, py + 19, px + pw - 8, py + 20, 0x66E22B3B);

        ChestItems.Tab hoverTab = renderTabs(g, mx, my, k);

        // search box frame
        int sx = px + 8, sw = pw - 16;
        g.fill(sx, py + 46, sx + sw, py + 62, 0xCC000000);
        box(g, sx, py + 46, sw, 16, search.isFocused() ? 0xFFFF6A78 : 0x88E22B3B);
        super.render(g, mx, my, pt);

        ItemStack hover = renderGrid(g, mx, my, k, t);
        renderBottom(g, mx, my, k, t);

        if (hoverTab != null) g.renderTooltip(font, hoverTab.name, mx, my);
        else if (!hover.isEmpty()) {
            try { g.renderTooltip(font, hover, mx, my); }
            catch (Throwable ex) { g.renderTooltip(font, hover.getHoverName(), mx, my); }
        }
    }

    private ChestItems.Tab renderTabs(GuiGraphics g, int mx, int my, float k) {
        ChestItems.Tab hoverTab = null;
        int ty = py + 23;
        arrow(g, px + 8, ty + 4, true, tabScroll > 0);
        arrow(g, px + pw - 20, ty + 4, false, tabScroll + visTabs < tabs.size());
        for (int i = tabScroll; i < Math.min(tabs.size(), tabScroll + visTabs); i++) {
            int x = tabX(i);
            boolean sel = i == tabIdx, hov = mx >= x && mx < x + 20 && my >= ty && my < ty + 20;
            g.fill(x, ty, x + 20, ty + 20, sel ? 0xCC5A0F18 : (hov ? 0xAA2A1014 : 0x99140A0D));
            box(g, x, ty, 20, 20, sel ? 0xFFFF6A78 : (hov ? 0xAAE22B3B : 0x55E22B3B));
            if (sel) g.fill(x + 2, ty + 19, x + 18, ty + 20, 0xFFFFD0D4);
            g.renderItem(tabs.get(i).icon, x + 2, ty + 2);
            if (hov) hoverTab = tabs.get(i);
        }
        return hoverTab;
    }

    private void arrow(GuiGraphics g, int x, int y, boolean left, boolean on) {
        int c = on ? 0xFFFF6A78 : 0x44E22B3B;
        for (int i = 0; i < 5; i++) {
            int o = left ? i : 4 - i;
            g.fill(x + 2 + o, y + i, x + 3 + o, y + 10 - i, c);
        }
    }

    private ItemStack renderGrid(GuiGraphics g, int mx, int my, float k, float t) {
        ItemStack hover = ItemStack.EMPTY;
        int hi = slotAt(mx, my);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                int idx = (scroll + r) * cols + c;
                int x = gx + c * SLOT, y = gy + r * SLOT;
                g.fill(x, y, x + SLOT - 1, y + SLOT - 1, 0x99120A0D);
                if (idx >= shown.size()) continue;
                ItemStack st = shown.get(idx).stack;
                boolean sel = !selected.isEmpty() && ItemStack.isSameItemSameComponents(st, selected);
                if (idx == hi) { g.fill(x, y, x + SLOT - 1, y + SLOT - 1, 0x55FF6A78); hover = st; }
                if (sel) {
                    int a = (int) (150 + 100 * Mth.sin(t * 5));
                    box(g, x, y, SLOT - 1, SLOT - 1, (a << 24) | 0xFF6A78);
                    box(g, x - 1, y - 1, SLOT + 1, SLOT + 1, 0x55E22B3B);
                } else box(g, x, y, SLOT - 1, SLOT - 1, idx == hi ? 0xAAFF6A78 : 0x33E22B3B);
                g.renderItem(st, x + 1, y + 1);
            }
        }
        if (shown.isEmpty()) g.drawCenteredString(font, Component.translatable(K + "no_results"), gx + cols * SLOT / 2, gy + rows * SLOT / 2 - 4, 0xFF9A8E94);
        // scrollbar
        int sbx = gx + cols * SLOT + 2, sh = rows * SLOT;
        g.fill(sbx, gy, sbx + 4, gy + sh, 0x66000000);
        int ms = maxScroll();
        int th = ms <= 0 ? sh : Math.max(12, sh * rows / (ms + rows));
        int ty = ms <= 0 ? gy : gy + (sh - th) * scroll / ms;
        g.fill(sbx, ty, sbx + 4, ty + th, dragging ? 0xFFFF6A78 : 0xCCE22B3B);
        return hover;
    }

    private void renderBottom(GuiGraphics g, int mx, int my, float k, float t) {
        int by = bottom;
        g.fill(px + 8, by - 2, px + pw - 8, by - 1, 0x66E22B3B);
        // preview
        g.fill(px + 8, by + 2, px + 34, by + 28, 0xAA000000);
        box(g, px + 8, by + 2, 26, 26, selected.isEmpty() ? 0x44E22B3B : 0xFFFF6A78);
        if (!selected.isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(px + 10, by + 4, 0);
            g.pose().scale(1.5F, 1.5F, 1);
            g.renderItem(selected, 0, 0);
            g.pose().popPose();
            int tw = pw - 48 - 8;
            String name = font.plainSubstrByWidth(selected.getHoverName().getString(), tw);
            g.drawString(font, name, px + 40, by + 4, 0xFFFFD0D4, true);
            var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(selected.getItem());
            g.drawString(font, font.plainSubstrByWidth(key.toString(), tw), px + 40, by + 15, 0xFF9A8E94, false);
            int q = qty(), st = stackSize();
            g.drawString(font, Component.translatable(K + "stacks", (q + st - 1) / st), px + 40, by + 24 - 1, 0xFF7E7480, false);
        } else {
            g.drawString(font, Component.translatable(K + "none_selected"), px + 40, by + 11, 0xFF9A8E94, false);
        }
        for (Btn b : buttons) drawBtn(g, b, mx, my, t);
        // quantity box frame
        g.fill(qtyBox.getX() - 3, by + 50, qtyBox.getX() + qtyBox.getWidth() + 3, by + 66, 0xCC000000);
        box(g, qtyBox.getX() - 3, by + 50, qtyBox.getWidth() + 6, 16, qtyBox.isFocused() ? 0xFFFF6A78 : 0x88E22B3B);
        qtyBox.setY(by + 54);
        g.pose().pushPose();
        g.pose().translate(px + 8, by + 70, 0);
        g.pose().scale(0.7F, 0.7F, 1);
        g.drawString(font, Component.translatable(K + "hint"), 0, 0, 0xFF7E7480, false);
        g.pose().popPose();
    }

    private void drawBtn(GuiGraphics g, Btn b, int mx, int my, float t) {
        boolean create = b.label == buttons.get(buttons.size() - 1).label;
        boolean hov = b.in(mx, my);
        boolean off = create && selected.isEmpty();
        int bg = off ? 0x88140A0D : create ? (hov ? 0xFFB01E2E : 0xFF7A1420) : (hov ? 0xCC4A1018 : 0xAA1E0C10);
        g.fill(b.x, b.y, b.x + b.w, b.y + b.h, bg);
        box(g, b.x, b.y, b.w, b.h, off ? 0x44E22B3B : hov ? 0xFFFF6A78 : (create ? 0xFFE22B3B : 0x77E22B3B));
        if (create && !off) {
            int a = (int) (60 + 40 * Mth.sin(t * 4));
            box(g, b.x - 1, b.y - 1, b.w + 2, b.h + 2, (a << 24) | 0xFF6A78);
        }
        g.drawCenteredString(font, b.label, b.x + b.w / 2, b.y + (b.h - 8) / 2, off ? 0xFF6A6068 : 0xFFFFE8EA);
    }

    private static void box(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c); g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c); g.fill(x + w - 1, y, x + w, y + h, c);
    }

    private void ring(GuiGraphics g, int cx, int cy, int r, int th, int alpha, float phase, int segs) {
        if (r <= 0 || alpha <= 0) return;
        for (int i = 0; i < segs; i++) {
            if (i % 7 > 4) continue;
            double a = phase + Math.PI * 2 * i / segs;
            int x = cx + (int) (Math.cos(a) * r), y = cy + (int) (Math.sin(a) * r);
            g.fill(x - th, y - th, x + th, y + th, (alpha << 24) | RED);
        }
    }
}
