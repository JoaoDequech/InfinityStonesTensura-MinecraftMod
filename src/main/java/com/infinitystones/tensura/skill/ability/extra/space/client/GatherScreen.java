package com.infinitystones.tensura.skill.ability.extra.space.client;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.space.GatherNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The caster's player picker: checkboxes, "take everyone", "only me" and "confirm". Same look as the other stone menus. */
final class GatherScreen extends Screen {
    private static final String K = "infinity.space.gather.";
    private final List<GatherNet.Entry> players = new ArrayList<>();
    private final Set<String> picked = new HashSet<>();
    private final int color = Stone.SPACE.color() & 0xFFFFFF;
    private final long opened = System.currentTimeMillis();
    private int scroll;

    GatherScreen(GatherNet.Open msg) {
        super(Component.translatable(K + "title"));
        players.addAll(msg.players());
        players.sort(Comparator.comparing(GatherNet.Entry::name, String.CASE_INSENSITIVE_ORDER));
    }

    @Override public boolean isPauseScreen() { return false; }

    private int panelW() { return Math.min(300, width - 20); }
    private int panelH() { return Math.min(height - 20, 60 + Math.min(players.size(), 9) * 20 + 50); }
    private int px() { return (width - panelW()) / 2; }
    private int py() { return (height - panelH()) / 2; }
    private int rows() { return Math.max(1, (panelH() - 110) / 20); }
    private int listTop() { return py() + 44; }
    private int btnY() { return py() + panelH() - 30; }

    private int[] button(int i) {
        int w = (panelW() - 24) / 3;
        return new int[]{px() + 8 + i * (w + 4), btnY(), w, 22};
    }

    private float anim() { return Mth.clamp((System.currentTimeMillis() - opened) / 200F, 0, 1); }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        float k = anim();
        g.fill(0, 0, width, height, ((int) (120 * k) << 24));
        int x = px(), y = py(), w = panelW(), h = panelH();
        float pulse = 0.5F + 0.5F * Mth.sin(System.currentTimeMillis() / 400F);
        for (int i = 4; i >= 1; i--) g.fill(x - i * 2, y - i * 2, x + w + i * 2, y + h + i * 2, ((int) ((10 + 14 * pulse) * (5 - i) * k) << 24) | color);
        g.fill(x, y, x + w, y + h, 0xEA0C0A12);
        g.renderOutline(x, y, w, h, ((int) (200 * k) << 24) | color);
        g.drawCenteredString(font, getTitle(), width / 2, y + 8, 0xFF000000 | lighten(color));
        g.drawCenteredString(font, Component.translatable(K + "menu.hint"), width / 2, y + 22, 0xFF9A9AA8);
        // rows
        int top = listTop(), rowsShown = rows();
        scroll = Mth.clamp(scroll, 0, Math.max(0, players.size() - rowsShown));
        if (players.isEmpty()) g.drawCenteredString(font, Component.translatable(K + "menu.empty"), width / 2, top + 14, 0xFFB0B0C0);
        for (int i = 0; i < rowsShown && i + scroll < players.size(); i++) {
            GatherNet.Entry e = players.get(i + scroll);
            int ry = top + i * 20;
            boolean hover = mx >= x + 8 && mx < x + w - 8 && my >= ry && my < ry + 18;
            boolean on = picked.contains(e.uuid());
            g.fill(x + 8, ry, x + w - 8, ry + 18, hover ? 0x40FFFFFF : 0x18FFFFFF);
            g.renderOutline(x + 12, ry + 3, 12, 12, 0xFF000000 | (on ? lighten(color) : 0x707080));
            if (on) {
                g.fill(x + 14, ry + 5, x + 22, ry + 13, 0xFF000000 | lighten(color));
            }
            g.drawString(font, e.name(), x + 30, ry + 5, on ? 0xFFFFFFFF : 0xFFD6D6E0, true);
            String dim = shortDim(e.dim());
            g.drawString(font, dim, x + w - 12 - font.width(dim), ry + 5, 0xFF808094, false);
        }
        if (players.size() > rowsShown) {
            int sh = (int) ((rowsShown * 20) * (rowsShown / (float) players.size()));
            int sy = top + (int) ((rowsShown * 20 - sh) * (scroll / (float) (players.size() - rowsShown)));
            g.fill(x + w - 6, sy, x + w - 4, sy + sh, 0x80000000 | color);
        }
        // buttons
        String[] labels = {K + "all", K + "me", K + "confirm"};
        for (int i = 0; i < 3; i++) {
            int[] b = button(i);
            boolean hover = mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
            int base = i == 2 ? 0xFF000000 | color : 0xFF2A2638;
            g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], hover ? lightenA(base) : base);
            g.renderOutline(b[0], b[1], b[2], b[3], 0xFF000000 | lighten(color));
            Component label = Component.translatable(labels[i]);
            if (i == 2 && !picked.isEmpty()) label = Component.translatable(K + "confirm_n", picked.size());
            g.drawCenteredString(font, label, b[0] + b[2] / 2, b[1] + 7, 0xFFFFFFFF);
        }
    }

    private static String shortDim(String dim) {
        int c = dim.indexOf(':');
        String p = c >= 0 ? dim.substring(c + 1) : dim;
        return switch (p) {
            case "overworld" -> "Overworld";
            case "the_nether" -> "Nether";
            case "the_end" -> "End";
            default -> p;
        };
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll -= (int) Math.signum(dy);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 1) { onClose(); return true; }
        int x = px(), w = panelW(), top = listTop();
        for (int i = 0; i < rows() && i + scroll < players.size(); i++) {
            int ry = top + i * 20;
            if (mx >= x + 8 && mx < x + w - 8 && my >= ry && my < ry + 18) {
                String id = players.get(i + scroll).uuid();
                if (!picked.remove(id)) picked.add(id);
                Minecraft.getInstance().player.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.5F, picked.contains(id) ? 1.5F : 1.0F);
                return true;
            }
        }
        for (int i = 0; i < 3; i++) {
            int[] b = button(i);
            if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) {
                if (i == 0) send(true, List.of());
                else if (i == 1) send(false, List.of());
                else send(false, new ArrayList<>(picked));
                return true;
            }
        }
        return true;
    }

    private void send(boolean all, List<String> ids) {
        PacketDistributor.sendToServer(new GatherNet.Confirm(all, ids));
        Minecraft.getInstance().player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.3F);
        onClose();
    }

    private static int lighten(int c) {
        int r = (((c >> 16) & 255) + 255) / 2, g = (((c >> 8) & 255) + 255) / 2, b = ((c & 255) + 255) / 2;
        return (r << 16) | (g << 8) | b;
    }

    private static int lightenA(int argb) {
        int a = argb >>> 24, r = Math.min(255, ((argb >> 16) & 255) + 40), g = Math.min(255, ((argb >> 8) & 255) + 40), b = Math.min(255, (argb & 255) + 40);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
