package com.infinitystones.tensura.skill.ability.extra.v2a.client;

import com.infinitystones.tensura.skill.ability.extra.v2a.V2aNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * Death list of the Hand of the Creator: every player of the dimension in a black and blood-red panel; the click picks
 * the victim (the server then does exactly what grabbing by aim does).
 */
final class HandTargetScreen extends Screen {
    private static final int ROW = 26, PANEL_W = 290;
    private final List<V2aNet.Target> list;
    private final long opened = System.currentTimeMillis();
    private int scroll, hover = -1;

    HandTargetScreen(List<V2aNet.Target> list) {
        super(Component.translatable("infinity.v2a.creator.pick.title"));
        this.list = list;
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.WARDEN_HEARTBEAT, 0.7F, 0.6F));
    }

    private int rows() { return Math.max(1, Math.min(list.size(), (height - 120) / ROW)); }

    private int top() { return (height - rows() * ROW) / 2 + 6; }

    private int left() { return (width - PANEL_W) / 2; }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        long now = System.currentTimeMillis();
        float k = Mth.clamp((now - opened) / 260F, 0, 1);
        float t = (now % 100000L) / 1000F;
        float flick = 0.75F + 0.25F * Mth.sin(t * 5F) * Mth.sin(t * 2.3F + 1F);
        g.fill(0, 0, width, height, ((int) (190 * k) << 24) | 0x050000);
        g.fillGradient(0, height / 2, width, height, 0x00000000, ((int) (150 * k) << 24) | 0x3A0000);
        g.fillGradient(0, 0, width, height / 4, ((int) (120 * k) << 24) | 0x1A0000, 0x00000000);
        // embers rising from the bottom
        for (int i = 0; i < 46; i++) {
            float sp = 0.05F + (i % 7) * 0.012F;
            float y = height - ((t * sp * height + i * 53) % (height + 20));
            float x = (i * 97 % width) + Mth.sin(t * 1.3F + i) * 8F;
            int a = (int) (170 * k * (y / height));
            g.fill((int) x, (int) y, (int) x + 2, (int) y + 2, (a << 24) | 0xD02010);
        }

        int rows = rows(), px = left(), pyTop = top();
        int ph = rows * ROW + 12;
        int x0 = px - 8, y0 = pyTop - 46, x1 = px + PANEL_W + 8, y1 = pyTop + ph + 8;
        g.fill(x0, y0, x1, y1, ((int) (225 * k) << 24) | 0x0C0000);
        int edge = ((int) (255 * k * flick) << 24) | 0x8A0A0A;
        g.renderOutline(x0, y0, x1 - x0, y1 - y0, edge);
        g.renderOutline(x0 + 3, y0 + 3, x1 - x0 - 6, y1 - y0 - 6, ((int) (110 * k) << 24) | 0x600000);

        // title between two skulls
        Component title = getTitle();
        int tw = font.width(title);
        int cx = width / 2;
        g.drawString(font, title, cx - tw / 2, y0 + 12, ((int) (255 * k) << 24) | 0xC81818, true);
        ItemStack skull = new ItemStack(Items.WITHER_SKELETON_SKULL);
        g.renderItem(skull, cx - tw / 2 - 26, y0 + 7);
        g.renderItem(skull, cx + tw / 2 + 10, y0 + 7);
        Component hint = Component.translatable("infinity.v2a.creator.pick.hint");
        g.drawString(font, hint, cx - font.width(hint) / 2, y0 + 28, ((int) (200 * k) << 24) | 0x8A5050, false);

        int shown = Math.min(rows, list.size());
        int maxScroll = Math.max(0, list.size() - shown);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        hover = -1;
        for (int r = 0; r < shown; r++) {
            int idx = r + scroll;
            V2aNet.Target tg = list.get(idx);
            int ry = pyTop + r * ROW + 6;
            boolean over = mx >= px && mx < px + PANEL_W && my >= ry && my < ry + ROW - 2;
            if (over) hover = idx;
            int bg = over ? 0xB0420808 : 0x90140404;
            g.fill(px, ry, px + PANEL_W, ry + ROW - 2, bg);
            g.fill(px, ry, px + 2, ry + ROW - 2, over ? 0xFFE02020 : 0xFF5A0808);
            g.renderItem(new ItemStack(over ? Items.WITHER_SKELETON_SKULL : Items.SKELETON_SKULL), px + 6, ry + 3);
            PlayerInfo info = minecraft != null && minecraft.getConnection() != null ? minecraft.getConnection().getPlayerInfo(tg.id()) : null;
            if (info != null) PlayerFaceRenderer.draw(g, info.getSkin(), px + 28, ry + 4, 16);
            g.drawString(font, tg.name(), px + 50, ry + 8, over ? 0xFFFFE0E0 : 0xFFC09090, true);
            String d = Component.translatable("infinity.v2a.creator.pick.dist", tg.dist()).getString();
            g.drawString(font, d, px + PANEL_W - 8 - font.width(d), ry + 8, over ? 0xFFFF6060 : 0xFF904040, false);
            if (!over) g.fill(px + 28, ry + 4, px + 44, ry + 20, 0x50300000);
        }
        if (maxScroll > 0) {
            int trackH = rows * ROW;
            int barH = Math.max(14, trackH * shown / list.size());
            int by = pyTop + 6 + (trackH - barH) * scroll / maxScroll;
            g.fill(px + PANEL_W + 2, by, px + PANEL_W + 5, by + barH, 0xFF8A1010);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll -= (int) Math.signum(sy);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && hover >= 0 && hover < list.size()) {
            PacketDistributor.sendToServer(new V2aNet.Pick(list.get(hover).id()));
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.WITHER_SPAWN, 0.5F, 1.6F));
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
}
