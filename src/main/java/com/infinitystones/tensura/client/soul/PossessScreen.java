package com.infinitystones.tensura.client.soul;

import com.infinitystones.tensura.soul.SoulNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.UUID;

/** Clean list of online players to possess (Soul Stone colours). */
final class PossessScreen extends Screen {
    private static final int ROW = 26, W = 220;
    private final SoulNet.Players players;
    private int scroll, hovered = -1;
    private final long opened = System.currentTimeMillis();

    PossessScreen(SoulNet.Players players) {
        super(Component.translatable("infinity.soul.menu.players"));
        this.players = players;
    }

    @Override public boolean isPauseScreen() { return false; }

    private int visible() { return Math.min(players.names().size(), Math.max(3, (height - 90) / ROW)); }
    private int top() { return height / 2 - visible() * ROW / 2; }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        float in = Mth.clamp((System.currentTimeMillis() - opened) / 220F, 0, 1);
        int x0 = width / 2 - W / 2, y0 = top(), n = visible();
        int a = (int) (0xD0 * in);
        g.fill(x0 - 10, y0 - 34, x0 + W + 10, y0 + n * ROW + 10, (a << 24) | 0x120802);
        g.renderOutline(x0 - 10, y0 - 34, W + 20, n * ROW + 44, 0xFFF28A1A);
        g.fill(x0 - 10, y0 - 34, x0 + W + 10, y0 - 33, 0xFFFFD9A0);
        g.drawCenteredString(font, title, width / 2, y0 - 26, 0xFFF28A1A);
        g.drawCenteredString(font, Component.translatable("infinity.soul.menu.players_hint"), width / 2, y0 - 14, 0xFF9E8A78);
        hovered = -1;
        for (int i = 0; i < n; i++) {
            int idx = i + scroll;
            if (idx >= players.names().size()) break;
            int y = y0 + i * ROW;
            boolean hov = mx >= x0 && mx < x0 + W && my >= y && my < y + ROW - 2;
            if (hov) hovered = idx;
            g.fill(x0, y, x0 + W, y + ROW - 2, hov ? 0xA0F28A1A : 0x50301808);
            if (hov) g.renderOutline(x0, y, W, ROW - 2, 0xFFFFD9A0);
            PlayerInfo info = null;
            try { info = Minecraft.getInstance().getConnection() == null ? null
                    : Minecraft.getInstance().getConnection().getPlayerInfo(UUID.fromString(players.uuids().get(idx))); } catch (IllegalArgumentException ignored) { }
            if (info != null) PlayerFaceRenderer.draw(g, info.getSkin(), x0 + 4, y + 3, 18);
            g.drawString(font, players.names().get(idx), x0 + 28, y + 8, hov ? 0xFFFFFFFF : 0xFFFFD9A0);
            if (hov) g.drawString(font, "▶", x0 + W - 14, y + 8, 0xFFFFFFFF);
        }
        if (players.names().size() > n)
            g.drawCenteredString(font, (scroll + 1) + "-" + Math.min(players.names().size(), scroll + n) + " / " + players.names().size(),
                    width / 2, y0 + n * ROW + 1, 0xFF9E8A78);
    }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, 0x60100500, 0xA0200A00);
    }

    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll = Mth.clamp(scroll - (int) Math.signum(dy), 0, Math.max(0, players.names().size() - visible()));
        return true;
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button == 1) { onClose(); return true; }
        if (button == 0 && hovered >= 0) {
            PacketDistributor.sendToServer(new SoulNet.Pick(players.uuids().get(hovered)));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SOUL_ESCAPE.value(), 0.8F, 1F));
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
}
