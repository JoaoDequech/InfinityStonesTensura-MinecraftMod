package com.infinitystones.tensura.client.wheel;

import com.infinitystones.tensura.gauntlet.GauntletGems;
import com.infinitystones.tensura.skill.GauntletStones;
import com.infinitystones.tensura.skill.Stone;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * "Joias": the gauntlet drawn big with its six sockets. Click a lit socket to take that stone out, click an empty socket
 * (or a stone of the list below) to set it in; two buttons do all six at once. Procedural art, no texture. Everything is
 * decided by the server ({@link GauntletGems}); this screen only shows the state it sends back.
 */
public final class GauntletGemsScreen extends Screen {
    /** Socket order on screen: four knuckles, palm, thumb. */
    private static final Stone[] ORDER = {Stone.SPACE, Stone.REALITY, Stone.POWER, Stone.MIND, Stone.TIME, Stone.SOUL};
    private static final int[][] POS = {{50, 30}, {78, 22}, {106, 22}, {134, 30}, {92, 64}, {40, 78}};
    private static final float S = 1.5F;
    private static final int W = 270, H = 236, OX = 24, OY = 22;
    private static final int GOLD = 0xFFE9C25A, GOLD_DARK = 0xFFB8862A, EDGE = 0xFF6B4A12;

    private final long openedAt = System.currentTimeMillis();
    private GauntletGems.State seen;
    private long changedAt = -1;
    private int changeBits;
    private int left, top;

    public GauntletGemsScreen() { super(Component.translatable("infinity.gauntlet.title")); }

    @Override public boolean isPauseScreen() { return false; }

    @Override protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        addRenderableWidget(Button.builder(Component.translatable("infinity.gauntlet.gems.insert_all"), b -> request(GauntletGems.INSERT_ALL, 0))
                .bounds(left + 14, top + H - 28, 118, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("infinity.gauntlet.gems.remove_all"), b -> request(GauntletGems.REMOVE_ALL, 0))
                .bounds(left + W - 14 - 118, top + H - 28, 118, 18).build());
        if (seen == null && minecraft != null && minecraft.player != null) { // best guess until the server answers
            int mask = GauntletStones.carriedMask(minecraft.player);
            GauntletGems.client = new GauntletGems.State(mask >= 0, Math.max(0, mask), 0, 0);
            seen = GauntletGems.client;
        }
        request(GauntletGems.REFRESH, 0);
    }

    private void request(int action, int socket) {
        PacketDistributor.sendToServer(new GauntletGems.Request(action, socket));
        if (action != GauntletGems.REFRESH && minecraft != null)
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4F, 0.4F));
    }

    private int sx(int i) { return left + OX + (int) (POS[i][0] * S); }
    private int sy(int i) { return top + OY + (int) (POS[i][1] * S); }
    private static final int SOCKET = (int) (18 * S);

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        long now = System.currentTimeMillis();
        GauntletGems.State st = GauntletGems.client;
        if (st != seen) {
            if (seen != null && st.change() != 0) { changedAt = now; changeBits = st.change(); }
            seen = st;
        }
        float open = Mth.clamp((now - openedAt) / 160F, 0F, 1F);
        float ease = 1 - (1 - open) * (1 - open) * (1 - open);
        g.fill(0, 0, width, height, (int) (120 * ease) << 24);
        g.pose().pushPose();
        g.pose().translate(width / 2F, height / 2F, 0);
        float sc = 0.92F + 0.08F * ease;
        g.pose().scale(sc, sc, 1);
        g.pose().translate(-width / 2F, -height / 2F, 0);

        g.fill(left - 2, top - 2, left + W + 2, top + H + 2, EDGE);
        g.fill(left - 1, top - 1, left + W + 1, top + H + 1, 0xFFE0B040);
        g.fillGradient(left, top, left + W, top + H, 0xFF1B1408, 0xFF0B0805);

        drawGauntlet(g);
        int count = Integer.bitCount(st.sockets());
        g.drawString(font, title, left + 12, top + 7, GOLD, true);
        String n = count + "/6";
        g.drawString(font, n, left + W - 12 - font.width(n), top + 7, count == 6 ? 0xFFFFFFFF : GOLD_DARK, true);

        int hoverSocket = -1;
        for (int i = 0; i < 6; i++) {
            Stone stone = ORDER[i];
            int x = sx(i), y = sy(i);
            boolean filled = (st.sockets() >> stone.ordinal() & 1) != 0;
            boolean hot = mx >= x && mx < x + SOCKET && my >= y && my < y + SOCKET;
            if (hot) hoverSocket = i;
            drawSocket(g, stone, x, y, filled, hot, now, i);
        }

        // loose stones carried (click to set)
        int ly = top + 160;
        g.drawString(font, Component.translatable("infinity.gauntlet.gems.loose"), left + 14, ly - 11, GOLD_DARK, true);
        int hoverLoose = -1;
        for (int i = 0; i < 6; i++) {
            Stone stone = ORDER[i];
            int x = left + 14 + i * 42, y = ly;
            boolean have = (st.loose() >> stone.ordinal() & 1) != 0;
            boolean free = (st.sockets() >> stone.ordinal() & 1) == 0;
            boolean hot = mx >= x && mx < x + 34 && my >= y && my < y + 26;
            if (hot) hoverLoose = i;
            int c = stone.color() & 0xFFFFFF;
            g.fill(x - 1, y - 1, x + 35, y + 27, have && free ? (hot ? 0xFFFFE9A0 : 0xFF000000 | c) : 0xFF3A2A0C);
            g.fill(x, y, x + 34, y + 26, 0xFF120D05);
            if (have && free) g.fill(x, y, x + 34, y + 26, (hot ? 0x50 : 0x28) << 24 | c);
            g.pose().pushPose();
            g.pose().translate(x + 9, y + 5, 0);
            g.renderFakeItem(new ItemStack(GauntletStones.gem(stone)), 0, 0);
            g.pose().popPose();
            if (!(have && free)) g.fill(x, y, x + 34, y + 26, 0xB0120D05);
        }

        if (st.gauntlet() && count == 0) {
            float pulse = 0.6F + 0.4F * Mth.sin(now / 260F);
            g.drawCenteredString(font, Component.translatable("infinity.gauntlet.gems.insert_hint"), left + W / 2, top + OY + 4,
                    ((int) (255 * pulse) << 24) | 0xFFE9C25A & 0xFFFFFF);
        }
        if (!st.gauntlet()) {
            g.fill(left + 10, top + 70, left + W - 10, top + 100, 0xE0000000);
            g.drawCenteredString(font, Component.translatable("infinity.gauntlet.gems.none"), left + W / 2, top + 81, 0xFFFF8080);
        }
        super.render(g, mx, my, pt);
        if (hoverSocket >= 0) {
            Stone stone = ORDER[hoverSocket];
            boolean filled = (st.sockets() >> stone.ordinal() & 1) != 0;
            g.renderTooltip(font, Component.translatable("infinity.gauntlet.socket." + stone.key).withStyle(s -> s.withColor(stone.color() & 0xFFFFFF))
                    .append(Component.literal("  ")).append(Component.translatable(filled ? "infinity.gauntlet.gems.click_remove" : "infinity.gauntlet.gems.click_insert")
                            .withStyle(net.minecraft.ChatFormatting.GRAY)), mx, my);
        } else if (hoverLoose >= 0) {
            g.renderTooltip(font, Component.translatable("infinity.gauntlet.socket." + ORDER[hoverLoose].key), mx, my);
        }
        g.pose().popPose();
    }

    private void drawGauntlet(GuiGraphics g) {
        int gold = GOLD_DARK, hi = 0xFFE9C25A, dark = 0xFF5A3E10;
        int[][] fingers = {{44, 14, 20, 40}, {72, 6, 20, 46}, {100, 6, 20, 46}, {128, 14, 20, 40}};
        g.pose().pushPose();
        g.pose().translate(left + OX, top + OY, 0);
        g.pose().scale(S, S, 1);
        for (int[] f : fingers) {
            g.fill(f[0], f[1], f[0] + f[2], f[1] + f[3], dark);
            g.fill(f[0] + 1, f[1] + 1, f[0] + f[2] - 1, f[1] + f[3], gold);
            g.fill(f[0] + 2, f[1] + 2, f[0] + 5, f[1] + f[3] - 2, hi);
            g.fill(f[0] + 1, f[1] + f[3] / 2, f[0] + f[2] - 1, f[1] + f[3] / 2 + 1, dark);
        }
        g.fill(42, 52, 150, 96, dark);
        g.fill(43, 53, 149, 96, gold);
        g.fill(45, 55, 50, 94, hi);
        g.fill(24, 66, 46, 96, dark);
        g.fill(25, 67, 45, 96, gold);
        g.pose().popPose();
    }

    private void drawSocket(GuiGraphics g, Stone stone, int x, int y, boolean filled, boolean hot, long now, int i) {
        int c = stone.color() & 0xFFFFFF;
        float pulse = 0.5F + 0.5F * Mth.sin(now / 250F + i);
        float flash = 0;
        if (changedAt >= 0 && (changeBits >> stone.ordinal() & 1) != 0) flash = Mth.clamp(1 - (now - changedAt) / 600F, 0F, 1F);
        int haloA = (int) ((filled ? 90 + 80 * pulse : 36) + 120 * flash + (hot ? 40 : 0));
        int reach = 7 + (int) (6 * flash);
        for (int r = reach; r >= 1; r--) {
            int a = Mth.clamp(haloA * (reach + 1 - r) / (reach * 2 + 2), 0, 255);
            g.fill(x - r, y - r, x + SOCKET + r, y + SOCKET + r, (a << 24) | c);
        }
        g.fill(x - 1, y - 1, x + SOCKET + 1, y + SOCKET + 1, hot ? 0xFFFFE9A0 : 0xFF2A1E08);
        g.fill(x, y, x + SOCKET, y + SOCKET, filled ? 0xFF000000 | ((c >> 18 & 0x3F) << 16 | (c >> 10 & 0x3F) << 8 | (c >> 2 & 0x3F)) : 0xFF120D05);
        float bob = filled ? Mth.sin(now / 400F + i) * 0.8F : 0;
        g.pose().pushPose();
        g.pose().translate(x + (SOCKET - 16 * 1.2F) / 2F, y + (SOCKET - 16 * 1.2F) / 2F + bob, 0);
        g.pose().scale(1.2F, 1.2F, 1);
        g.renderFakeItem(new ItemStack(GauntletStones.gem(stone)), 0, 0);
        g.pose().popPose();
        if (!filled) g.fill(x, y, x + SOCKET, y + SOCKET, 0xB0120D05);
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button != 0) return false;
        GauntletGems.State st = GauntletGems.client;
        for (int i = 0; i < 6; i++) {
            Stone stone = ORDER[i];
            int x = sx(i), y = sy(i);
            if (mx >= x && mx < x + SOCKET && my >= y && my < y + SOCKET) {
                boolean filled = (st.sockets() >> stone.ordinal() & 1) != 0;
                request(filled ? GauntletGems.REMOVE : GauntletGems.INSERT, stone.ordinal());
                return true;
            }
            int lx = left + 14 + i * 42, ly = top + 160;
            if (mx >= lx && mx < lx + 34 && my >= ly && my < ly + 26) {
                request(GauntletGems.INSERT, stone.ordinal());
                return true;
            }
        }
        return false;
    }
}
