package com.infinitystones.tensura.skill.ability.extra.v2e.client;

import com.infinitystones.tensura.skill.ability.extra.v2e.V2eNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The Enchanter's table: every enchantment of the game (modded ones too) with search and mod filter, a level picker up to
 * 255 and the slot to enchant (hands and armour). Only asks the server; the server decides.
 */
final class EnchanterScreen extends Screen {
    private static final int RED = 0xE02848, PURPLE = 0xA050FF, ROW = 18;
    private static final String[] SLOT_KEYS = {"mainhand", "offhand", "head", "chest", "legs", "feet"};

    private record Row(Holder.Reference<Enchantment> holder, ResourceLocation id, String name, String ns, int max) {}

    private final long opened = System.currentTimeMillis();
    private final List<Row> all = new ArrayList<>();
    private final List<Row> view = new ArrayList<>();
    private final List<String> mods = new ArrayList<>();
    private final Map<String, String> modNames = new TreeMap<>();
    private int modIndex, scroll, slot, level = 1;
    private Row sel;
    private EditBox search, levelBox;
    private String searchText = "";
    private boolean syncing;

    private int px, py, pw, ph, listX, listY, listW, listRows, rightX, rightW;

    EnchanterScreen() {
        super(Component.translatable("infinity.v2e.title"));
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        if (all.isEmpty() && mc.level != null) {
            var reg = mc.level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            TreeSet<String> ns = new TreeSet<>();
            reg.holders().forEach(h -> {
                Enchantment e = h.value();
                ResourceLocation id = h.key().location();
                all.add(new Row(h, id, e.description().getString(), id.getNamespace(), e.getMaxLevel()));
                ns.add(id.getNamespace());
            });
            all.sort(Comparator.comparing((Row r) -> r.name().toLowerCase(Locale.ROOT)).thenComparing(r -> r.id().toString()));
            mods.add(null);
            mods.addAll(ns);
            for (String n : ns) modNames.put(n, ModList.get().getModContainerById(n).map(c -> c.getModInfo().getDisplayName()).orElse(n));
            for (int s = 0; s < V2eNet.SLOTS.length; s++)
                if (!stackOf(s).isEmpty()) { slot = s; break; }
        }

        pw = Math.min(width - 24, 560);
        ph = Math.min(height - 16, 318);
        px = (width - pw) / 2;
        py = (height - ph) / 2;
        rightW = 190;
        rightX = px + pw - rightW - 10;
        listX = px + 10;
        listW = rightX - listX - 10;
        listY = py + 74;
        listRows = Math.max(3, (py + ph - 30 - listY) / ROW);

        search = new EditBox(font, listX + 24, py + 51, listW - 156, 12, Component.translatable("infinity.v2e.search"));
        search.setBordered(false);
        search.setMaxLength(48);
        search.setTextColor(0xFFF0E0FF);
        search.setHint(Component.translatable("infinity.v2e.search").withStyle(s -> s.withColor(0x8A6AA0)));
        search.setValue(searchText);
        search.setResponder(s -> { searchText = s; refresh(); });
        addWidget(search);

        levelBox = new EditBox(font, rightX + 52, py + 190, 86, 12, Component.translatable("infinity.v2e.level"));
        levelBox.setBordered(false);
        levelBox.setMaxLength(3);
        levelBox.setTextColor(0xFFFFE9A0);
        levelBox.setFilter(s -> s.isEmpty() || s.matches("\\d{1,3}"));
        levelBox.setValue(Integer.toString(level));
        levelBox.setResponder(s -> {
            if (syncing || s.isEmpty()) return;
            level = Mth.clamp(Integer.parseInt(s), 1, V2eNet.MAX_LEVEL);
        });
        addWidget(levelBox);
        setInitialFocus(search);
        refresh();
    }

    private ItemStack stackOf(int s) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? ItemStack.EMPTY : mc.player.getItemBySlot(V2eNet.SLOTS[s]);
    }

    private void refresh() {
        view.clear();
        String q = searchText.trim().toLowerCase(Locale.ROOT);
        String mod = mods.isEmpty() ? null : mods.get(modIndex);
        for (Row r : all) {
            if (mod != null && !r.ns().equals(mod)) continue;
            if (!q.isEmpty() && !r.name().toLowerCase(Locale.ROOT).contains(q) && !r.id().toString().contains(q)) continue;
            view.add(r);
        }
        scroll = Mth.clamp(scroll, 0, Math.max(0, view.size() - listRows));
    }

    private void setLevel(int v) {
        level = Mth.clamp(v, 1, V2eNet.MAX_LEVEL);
        syncing = true;
        levelBox.setValue(Integer.toString(level));
        syncing = false;
    }

    private void cycleMod(int dir) {
        if (mods.isEmpty()) return;
        modIndex = Math.floorMod(modIndex + dir, mods.size());
        scroll = 0;
        refresh();
        click(1.4F);
    }

    private void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.5F));
    }

    private ItemEnchantments enchantsOf(ItemStack s) {
        return s.getOrDefault(s.is(Items.ENCHANTED_BOOK) ? DataComponents.STORED_ENCHANTMENTS : DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
    }

    private int currentLevel(Row r) {
        ItemStack s = stackOf(slot);
        return s.isEmpty() || r == null ? 0 : enchantsOf(s).getLevel(r.holder());
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    private void send(int lvl) {
        if (sel == null || stackOf(slot).isEmpty()) return;
        PacketDistributor.sendToServer(new V2eNet.Apply(slot, sel.id(), lvl));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ENCHANTMENT_TABLE_USE, 1.0F, 0.6F));
        onClose();
    }

    // ------------------------------------------------------------------ drawing

    private void button(GuiGraphics g, int x, int y, int w, int h, Component label, boolean over, boolean enabled, int accent, float t) {
        int a = enabled ? (over ? 255 : 200) : 90;
        g.fill(x, y, x + w, y + h, (a << 24) | (over && enabled ? 0x2A1030 : 0x14081A));
        int edge = enabled ? accent : 0x504060;
        g.renderOutline(x, y, w, h, 0xFF000000 | edge);
        if (enabled && over) g.fill(x + 1, y + 1, x + w - 1, y + 3, 0x40000000 | (accent & 0xFFFFFF));
        int col = enabled ? (over ? 0xFFFFFFFF : 0xFFE6D8F5) : 0xFF807090;
        g.drawCenteredString(font, label, x + w / 2, y + (h - 8) / 2, col);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        long now = System.currentTimeMillis();
        float k = Mth.clamp((now - opened) / 240F, 0, 1);
        float t = (now % 100000L) / 1000F;
        float pulse = 0.75F + 0.25F * Mth.sin(t * 3F);
        int al = (int) (255 * k);

        g.fill(0, 0, width, height, ((int) (170 * k) << 24) | 0x07020C);
        g.fillGradient(0, height / 2, width, height, 0x00000000, ((int) (120 * k) << 24) | 0x40081A);
        for (int i = 0; i < 40; i++) {
            float sp = 0.03F + (i % 7) * 0.01F;
            float y = height - ((t * sp * height + i * 61) % (height + 20));
            float x = (i * 89 % width) + Mth.sin(t * 1.1F + i) * 10F;
            g.fill((int) x, (int) y, (int) x + 2, (int) y + 2, (((int) (150 * k * (y / height))) << 24) | (i % 2 == 0 ? 0xC02050 : 0x8040E0));
        }

        int y0 = py + (int) ((1 - k) * 10);
        g.fill(px, y0, px + pw, y0 + ph, ((int) (235 * k) << 24) | 0x0C0612);
        g.renderOutline(px, y0, pw, ph, ((int) (255 * k * pulse) << 24) | 0x9A2040);
        g.renderOutline(px + 3, y0 + 3, pw - 6, ph - 6, ((int) (130 * k) << 24) | 0x6038B0);
        g.fillGradient(px + 4, y0 + 4, px + pw - 4, y0 + 44, ((int) (90 * k) << 24) | 0x601030, 0x00000000);

        // title
        Component title = getTitle();
        int cx = px + pw / 2;
        g.drawString(font, title, cx - font.width(title) / 2, y0 + 12, (al << 24) | 0xF0A0C0, true);
        ItemStack table = new ItemStack(Items.ENCHANTING_TABLE), book = new ItemStack(Items.ENCHANTED_BOOK);
        g.renderItem(table, cx - font.width(title) / 2 - 24, y0 + 7);
        g.renderItem(book, cx + font.width(title) / 2 + 8, y0 + 7);
        g.drawCenteredString(font, Component.translatable("infinity.v2e.count", view.size(), all.size()), cx, y0 + 26, (al << 24) | 0x9A80B8);
        g.fill(px + 14, y0 + 38, px + pw - 14, y0 + 39, ((int) (160 * k) << 24) | 0x7A2848);

        renderLeft(g, mx, my, y0 - py, k);
        renderRight(g, mx, my, y0 - py, k, t);
        search.render(g, mx, my, pt);
        levelBox.render(g, mx, my, pt);
        tooltips(g, mx, my);
    }

    private void renderLeft(GuiGraphics g, int mx, int my, int dy, float k) {
        // search + mod filter
        int sy = py + 46 + dy, sw = listW - 128;
        g.fill(listX, sy, listX + sw, sy + 20, 0xC0140A1C);
        g.renderOutline(listX, sy, sw, 20, 0xFF5A3070);
        g.renderItem(new ItemStack(Items.SPYGLASS), listX + 2, sy + 2);
        search.setY(sy + 5);
        int mxw = 124, mxx = listX + listW - mxw;
        boolean overMod = in(mx, my, mxx, sy, mxw, 20);
        String mod = mods.isEmpty() ? null : mods.get(modIndex);
        Component modLabel = mod == null ? Component.translatable("infinity.v2e.mod.all") : Component.literal(modNames.getOrDefault(mod, mod));
        String shown = font.plainSubstrByWidth(modLabel.getString(), mxw - 18);
        g.fill(mxx, sy, mxx + mxw, sy + 20, overMod ? 0xE0301848 : 0xC0140A1C);
        g.renderOutline(mxx, sy, mxw, 20, overMod ? 0xFFC03058 : 0xFF5A3070);
        g.drawString(font, shown, mxx + 6, sy + 6, mod == null ? 0xFFC8B0E0 : 0xFFFFD890, false);
        g.drawString(font, "\u25BE", mxx + mxw - 11, sy + 6, 0xFFC03058, false);

        // list
        int ly = listY + dy;
        int lh = listRows * ROW;
        g.fill(listX, ly - 2, listX + listW, ly + lh + 2, 0xB0100818);
        g.renderOutline(listX, ly - 2, listW, lh + 4, 0xFF3A2050);
        g.enableScissor(listX, ly, listX + listW - 6, ly + lh);
        int shown2 = Math.min(listRows, view.size() - scroll);
        for (int r = 0; r < shown2; r++) {
            Row row = view.get(r + scroll);
            int ry = ly + r * ROW;
            boolean over = in(mx, my, listX, ry, listW - 6, ROW) && my < ly + lh;
            boolean picked = row == sel;
            if (picked) {
                g.fill(listX, ry, listX + listW - 6, ry + ROW - 1, 0xA0501038);
                g.fill(listX, ry, listX + 2, ry + ROW - 1, 0xFFE02848);
            } else if (over) {
                g.fill(listX, ry, listX + listW - 6, ry + ROW - 1, 0x80301848);
                g.fill(listX, ry, listX + 2, ry + ROW - 1, 0xFFA050FF);
            }
            int cur = currentLevel(row);
            String badge = row.ns();
            int bw = font.width(badge);
            int right = listX + listW - 12;
            g.drawString(font, badge, right - bw, ry + 5, 0xFF7A6890, false);
            right -= bw + 8;
            if (cur > 0) {
                String lv = Component.translatable("infinity.v2e.row.level", cur).getString();
                right -= font.width(lv);
                g.drawString(font, lv, right, ry + 5, 0xFFFFD060, false);
                right -= 8;
            }
            String name = font.plainSubstrByWidth(row.name(), Math.max(20, right - listX - 12));
            g.drawString(font, name, listX + 8, ry + 5, picked ? 0xFFFFFFFF : over ? 0xFFF4E4FF : 0xFFC9B6DE, false);
        }
        g.disableScissor();
        if (view.isEmpty()) g.drawCenteredString(font, Component.translatable("infinity.v2e.empty"), listX + listW / 2, ly + lh / 2 - 4, 0xFF8A7098);
        int maxScroll = Math.max(0, view.size() - listRows);
        if (maxScroll > 0) {
            int barH = Math.max(14, lh * listRows / view.size());
            int by = ly + (lh - barH) * scroll / maxScroll;
            g.fill(listX + listW - 5, ly, listX + listW - 2, ly + lh, 0x40000000);
            g.fill(listX + listW - 5, by, listX + listW - 2, by + barH, 0xFFA03050);
        }
    }

    private void renderRight(GuiGraphics g, int mx, int my, int dy, float k, float t) {
        int x = rightX, w = rightW, y = py + 46 + dy;
        g.fill(x - 4, y - 4, x + w + 4, py + ph - 8 + dy, 0x90100818);
        g.renderOutline(x - 4, y - 4, w + 8, py + ph - 8 + dy - y + 4, 0xFF3A2050);

        // slot tabs
        for (int s = 0; s < 6; s++) {
            int sx = x + s * 31 + 2, sy = y;
            ItemStack st = stackOf(s);
            boolean over = in(mx, my, sx, sy, 28, 28);
            boolean on = s == slot;
            g.fill(sx, sy, sx + 28, sy + 28, on ? 0xC0501038 : over ? 0xA0301848 : 0x90140A1C);
            g.renderOutline(sx, sy, 28, 28, on ? 0xFFE02848 : over ? 0xFFA050FF : 0xFF3A2050);
            if (!st.isEmpty()) g.renderItem(st, sx + 6, sy + 6);
            else g.drawCenteredString(font, "-", sx + 14, sy + 10, 0xFF504060);
        }

        // target preview
        ItemStack target = stackOf(slot);
        int py2 = y + 36;
        if (target.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("infinity.v2e.noitem"), x + w / 2, py2 + 24, 0xFF8A7098);
        } else {
            float bob = Mth.sin(t * 2F) * 1.5F;
            g.pose().pushPose();
            g.pose().translate(x + 14, py2 + 4 + bob, 0);
            g.pose().scale(2.5F, 2.5F, 1F);
            g.renderItem(target, 0, 0);
            g.pose().popPose();
            Component name = target.getHoverName();
            g.drawString(font, font.plainSubstrByWidth(name.getString(), w - 70), x + 62, py2 + 4, 0xFFF4E4FF, false);
            ItemEnchantments ie = enchantsOf(target);
            int line = 0;
            if (ie.isEmpty()) g.drawString(font, Component.translatable("infinity.v2e.on_item.none"), x + 62, py2 + 16, 0xFF7A6890, false);
            else {
                for (var en : ie.entrySet()) {
                    if (line >= 3) break;
                    String s = font.plainSubstrByWidth(Enchantment.getFullname(en.getKey(), en.getIntValue()).getString(), w - 66);
                    g.drawString(font, s, x + 62, py2 + 16 + line * 10, 0xFFB090E0, false);
                    line++;
                }
                if (ie.size() > 3) g.drawString(font, Component.translatable("infinity.v2e.on_item.more", ie.size() - 3), x + 62, py2 + 16 + 30, 0xFF7A6890, false);
            }
        }

        // selected enchantment
        int sy = py + 142 + dy;
        g.fill(x, sy, x + w, sy + 34, 0xA0140A1C);
        g.renderOutline(x, sy, w, 34, sel == null ? 0xFF3A2050 : 0xFF9A2040);
        if (sel == null) g.drawCenteredString(font, Component.translatable("infinity.v2e.none"), x + w / 2, sy + 13, 0xFF8A7098);
        else {
            g.drawString(font, font.plainSubstrByWidth(sel.name(), w - 12), x + 6, sy + 5, 0xFFFFE9A0, false);
            g.drawString(font, font.plainSubstrByWidth(sel.id().toString(), w - 12), x + 6, sy + 17, 0xFF7A6890, false);
        }

        // level picker
        int ly = py + 176 + dy;
        g.drawString(font, Component.translatable("infinity.v2e.level"), x + 2, ly + 6, 0xFFC8B0E0, false);
        int by = py + 186 + dy;
        int[] deltas = {-10, -1, 1, 10};
        int[] bxs = {x, x + 26, x + w - 52, x + w - 26};
        String[] labels = {"-10", "-1", "+1", "+10"};
        for (int i = 0; i < 4; i++) {
            boolean over = in(mx, my, bxs[i], by, 24, 20);
            button(g, bxs[i], by, 24, 20, Component.literal(labels[i]), over, true, PURPLE, t);
        }
        levelBox.setY(by + 6);
        g.fill(x + 50, by, x + w - 54, by + 20, 0xC0140A1C);
        g.renderOutline(x + 50, by, w - 104, 20, 0xFF9A7020);
        levelBox.setX(x + 54);
        levelBox.setWidth(w - 112);

        int qy = by + 24, qw = (w - 8) / 3;
        int vmax = sel == null ? 1 : sel.max();
        Component[] ql = {Component.translatable("infinity.v2e.level.one"), Component.translatable("infinity.v2e.level.vanilla", vmax), Component.translatable("infinity.v2e.level.max")};
        for (int i = 0; i < 3; i++) {
            int qx = x + i * (qw + 4);
            button(g, qx, qy, qw, 18, ql[i], in(mx, my, qx, qy, qw, 18), true, 0xC07A20, t);
        }

        // apply / remove
        int ay = py + ph - 8 + dy - 50;
        boolean can = sel != null && !target.isEmpty();
        boolean hasIt = can && currentLevel(sel) > 0;
        int bw = (w - 6) / 2;
        float glow = 0.6F + 0.4F * Mth.sin(t * 4F);
        if (can) g.fill(x - 1, ay - 1, x + bw + 1, ay + 33, ((int) (70 * glow) << 24) | 0xE02848);
        button(g, x, ay, bw, 32, Component.translatable("infinity.v2e.apply").withStyle(s -> s.withBold(true)), in(mx, my, x, ay, bw, 32), can, RED, t);
        button(g, x + bw + 6, ay, bw, 32, Component.translatable("infinity.v2e.remove"), in(mx, my, x + bw + 6, ay, bw, 32), hasIt, 0x8A8AA0, t);
    }

    private void tooltips(GuiGraphics g, int mx, int my) {
        int y = py + 46;
        for (int s = 0; s < 6; s++) {
            int sx = rightX + s * 31 + 2;
            if (in(mx, my, sx, y, 28, 28)) {
                ItemStack st = stackOf(s);
                List<Component> tip = new ArrayList<>();
                tip.add(Component.translatable("infinity.v2e.slot." + SLOT_KEYS[s]));
                tip.add(st.isEmpty() ? Component.translatable("infinity.v2e.slot.empty") : st.getHoverName());
                g.renderComponentTooltip(font, tip, mx, my);
                return;
            }
        }
        int mxw = 124, mxx = listX + listW - mxw;
        if (in(mx, my, mxx, y, mxw, 20)) {
            g.renderComponentTooltip(font, List.of(Component.translatable("infinity.v2e.mod.tip")), mx, my);
            return;
        }
        int lh = listRows * ROW;
        if (in(mx, my, listX, listY, listW - 6, lh)) {
            int idx = (my - listY) / ROW + scroll;
            if (idx >= 0 && idx < view.size()) {
                Row r = view.get(idx);
                g.renderComponentTooltip(font, List.of(Component.literal(r.name()), Component.literal(r.id().toString()).withStyle(s -> s.withColor(0x9A80B8)),
                        Component.translatable("infinity.v2e.row.tip", r.max()).withStyle(s -> s.withColor(0xC0A060))), mx, my);
            }
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int sign = (int) -Math.signum(sy);
        if (in(mx, my, listX, listY, listW, listRows * ROW)) {
            scroll = Mth.clamp(scroll + sign * 3, 0, Math.max(0, view.size() - listRows));
            return true;
        }
        if (in(mx, my, listX, py + 46, listW, 20)) { cycleMod(sign); return true; }
        if (mx >= rightX - 4) { setLevel(level - sign * (hasShiftDown() ? 10 : 1)); return true; }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 && button != 1) return super.mouseClicked(mx, my, button);
        int sy = py + 46;
        // slot tabs
        for (int s = 0; s < 6; s++) {
            if (in(mx, my, rightX + s * 31 + 2, sy, 28, 28)) { slot = s; click(1.2F); return true; }
        }
        // mod filter
        int mxw = 124, mxx = listX + listW - mxw;
        if (in(mx, my, mxx, sy, mxw, 20)) { cycleMod(button == 1 ? -1 : 1); return true; }
        // list
        if (in(mx, my, listX, listY, listW - 6, listRows * ROW)) {
            int idx = ((int) my - listY) / ROW + scroll;
            if (idx >= 0 && idx < view.size()) {
                sel = view.get(idx);
                int cur = currentLevel(sel);
                if (cur > 0) setLevel(cur);
                setFocused(null);
                click(1.0F);
                return true;
            }
        }
        // level
        int by = py + 186, x = rightX, w = rightW;
        int[] deltas = {-10, -1, 1, 10};
        int[] bxs = {x, x + 26, x + w - 52, x + w - 26};
        for (int i = 0; i < 4; i++) if (in(mx, my, bxs[i], by, 24, 20)) { setLevel(level + deltas[i]); click(1.3F); return true; }
        int qy = by + 24, qw = (w - 8) / 3;
        for (int i = 0; i < 3; i++) {
            if (in(mx, my, x + i * (qw + 4), qy, qw, 18)) {
                setLevel(i == 0 ? 1 : i == 1 ? (sel == null ? 1 : sel.max()) : V2eNet.MAX_LEVEL);
                click(1.5F);
                return true;
            }
        }
        // apply / remove
        int ay = py + ph - 8 - 50, bw = (w - 6) / 2;
        boolean can = sel != null && !stackOf(slot).isEmpty();
        if (in(mx, my, x, ay, bw, 32) && can) { send(Mth.clamp(level, 1, V2eNet.MAX_LEVEL)); return true; }
        if (in(mx, my, x + bw + 6, ay, bw, 32) && can && currentLevel(sel) > 0) { send(0); return true; }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if ((key == 257 || key == 335) && levelBox.isFocused() && sel != null && !stackOf(slot).isEmpty()) {
            send(Mth.clamp(level, 1, V2eNet.MAX_LEVEL));
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
