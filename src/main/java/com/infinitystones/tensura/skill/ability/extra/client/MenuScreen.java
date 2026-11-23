package com.infinitystones.tensura.skill.ability.extra.client;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.MenuNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/** Radial choice menu in the stone's colour: options around a glowing ring, description under the cursor's choice. */
public final class MenuScreen extends Screen {
    private final MenuNet.Open msg;
    private final int color;
    private final ItemStack[] icons;
    private final long opened = System.currentTimeMillis();
    private int hover = -1;
    private final float[] grow;

    MenuScreen(MenuNet.Open msg) {
        super(Component.translatable(msg.title()));
        this.msg = msg;
        this.color = Stone.values()[Mth.clamp(msg.stone(), 0, 5)].color() & 0xFFFFFF;
        this.icons = new ItemStack[msg.options().size()];
        this.grow = new float[msg.options().size()];
        for (int i = 0; i < icons.length; i++) {
            ResourceLocation id = ResourceLocation.tryParse(msg.options().get(i).icon());
            var item = id == null ? Items.BARRIER : BuiltInRegistries.ITEM.get(id);
            icons[i] = new ItemStack(item == Items.AIR ? Items.BARRIER : item);
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    private float anim() { return Mth.clamp((System.currentTimeMillis() - opened) / 180F, 0, 1); }

    private int radius() { return Math.min(width, height) / 4 + 10; }

    private double[] pos(int i, float k) {
        int n = msg.options().size();
        double a = -Math.PI / 2 + Math.PI * 2 * i / n;
        double r = radius() * k;
        return new double[]{width / 2.0 + Math.cos(a) * r, height / 2.0 + Math.sin(a) * r};
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        float k = anim(), ease = 1 - (1 - k) * (1 - k) * (1 - k);
        int cx = width / 2, cy = height / 2;
        g.fill(0, 0, width, height, ((int) (110 * k) << 24));
        float t = (System.currentTimeMillis() % 100000L) / 1000F;
        // glowing rings
        int R = (int) (radius() * ease);
        ring(g, cx, cy, R + 26, 2, (int) (90 * k), t * 0.3F);
        ring(g, cx, cy, R - 26, 1, (int) (70 * k), -t * 0.5F);
        ring(g, cx, cy, 34, 2, (int) (120 * k), t);
        // hover detection: nearest option by angle when the cursor leaves the centre
        hover = -1;
        double dx = mx - cx, dy = my - cy;
        int n = msg.options().size();
        if (dx * dx + dy * dy > 30 * 30 && n > 0) {
            double ang = Math.atan2(dy, dx) + Math.PI / 2;
            if (ang < 0) ang += Math.PI * 2;
            hover = (int) Math.round(ang / (Math.PI * 2) * n) % n;
        }
        for (int i = 0; i < n; i++) {
            grow[i] = Mth.lerp(0.25F, grow[i], i == hover ? 1 : 0);
            double[] p = pos(i, ease);
            int x = (int) p[0], y = (int) p[1];
            int s = (int) (14 + 6 * grow[i]);
            boolean sel = msg.options().get(i).selected();
            int glowA = (int) ((40 + 120 * grow[i] + (sel ? 60 : 0)) * k);
            for (int j = 3; j >= 1; j--) g.fill(x - s - j * 2, y - s - j * 2, x + s + j * 2, y + s + j * 2, ((glowA / (j + 1)) << 24) | color);
            g.fill(x - s, y - s, x + s, y + s, ((int) (200 * k) << 24) | 0x0C0A12);
            int edge = ((int) ((150 + 105 * grow[i]) * k) << 24) | (sel ? 0xFFFFFF : color);
            g.fill(x - s, y - s, x + s, y - s + 1, edge); g.fill(x - s, y + s - 1, x + s, y + s, edge);
            g.fill(x - s, y - s, x - s + 1, y + s, edge); g.fill(x + s - 1, y - s, x + s, y + s, edge);
            g.pose().pushPose();
            float sc = 1 + 0.4F * grow[i];
            g.pose().translate(x, y, 0);
            g.pose().scale(sc, sc, 1);
            g.renderItem(icons[i], -8, -8);
            g.pose().popPose();
            Component label = Component.translatable(msg.options().get(i).label());
            int lw = font.width(label);
            g.drawString(font, label, x - lw / 2, y + s + 4, sel ? 0xFFFFFFFF : (0xFF000000 | lighten(color)), true);
        }
        // centre: title + description of the hovered option
        g.drawCenteredString(font, getTitle(), cx, cy - 10, 0xFF000000 | lighten(color));
        if (hover >= 0) {
            Component d = Component.translatable(msg.options().get(hover).desc());
            var lines = font.split(d, Math.min(260, width - 40));
            int y = cy + radius() + 46;
            for (var l : lines) { g.drawCenteredString(font, l, cx, y, 0xFFEDEDED); y += 10; }
        }
        g.drawCenteredString(font, Component.translatable("infinity.extra.menu.hint"), cx, cy + 4, 0xFF9A9AA8);
    }

    private void ring(GuiGraphics g, int cx, int cy, int r, int th, int alpha, float phase) {
        if (r <= 0 || alpha <= 0) return;
        int segs = 90;
        for (int i = 0; i < segs; i++) {
            if (i % 6 == 5) continue;
            double a = phase + Math.PI * 2 * i / segs;
            int x = cx + (int) (Math.cos(a) * r), y = cy + (int) (Math.sin(a) * r);
            g.fill(x - th, y - th, x + th, y + th, (alpha << 24) | color);
        }
    }

    private static int lighten(int c) {
        int r = (((c >> 16) & 255) + 255) / 2, g = (((c >> 8) & 255) + 255) / 2, b = ((c & 255) + 255) / 2;
        return (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == 1) { onClose(); return true; }
        if (hover >= 0) {
            PacketDistributor.sendToServer(new MenuNet.Pick(msg.stone(), msg.node(), hover));
            Minecraft.getInstance().player.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.4F);
            onClose();
        }
        return true;
    }
}
