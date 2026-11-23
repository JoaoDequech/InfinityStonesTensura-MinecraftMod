package com.infinitystones.tensura.skill.ability.extra.space.client;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.space.SpaceNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Abyss Watch holographic panel (caster only): a glowing blue hologram on the right side of the screen that unfolds
 * line by line, with a scanline sweep, corner brackets and a flicker; fades out at the end.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class AbyssClient {
    private static Component title = Component.empty();
    private static List<Component> lines = List.of();
    private static long shownAt, until;

    public static void accept(SpaceNet.AbyssPanel p) {
        title = p.title();
        lines = p.lines();
        shownAt = System.currentTimeMillis();
        until = shownAt + p.ticks() * 50L;
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { until = 0; }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (now > until || mc.options.hideGui || mc.player == null) return;
        GuiGraphics g = e.getGuiGraphics();
        int sw = g.guiWidth(), sh = g.guiHeight();
        int col = Stone.SPACE.color() & 0xFFFFFF;
        float open = Mth.clamp((now - shownAt) / 350F, 0, 1), ease = 1 - (1 - open) * (1 - open);
        float close = Mth.clamp((until - now) / 500F, 0, 1);
        float alpha = Math.min(ease, close) * (0.92F + 0.08F * Mth.sin(now / 47F));
        int pw = Math.min(250, sw / 2 - 10);
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component c : lines) wrapped.addAll(mc.font.split(c, (int) ((pw - 14) / 0.8F)));
        int ph = (int) ((22 + wrapped.size() * 9) * ease);
        int x = sw - pw - 10, y = Math.max(8, sh / 2 - (22 + wrapped.size() * 9) / 2);
        int a = (int) (255 * alpha);
        if (a < 6) return;
        for (int i = 4; i >= 1; i--) g.fill(x - i, y - i, x + pw + i, y + ph + i, ((a / (i * 5)) << 24) | col);
        g.fill(x, y, x + pw, y + ph, ((int) (a * 0.7F) << 24) | 0x020814);
        // grid lines
        for (int gy = y + 4; gy < y + ph; gy += 6) g.fill(x + 1, gy, x + pw - 1, gy + 1, ((a / 14) << 24) | col);
        // scanline sweep
        int sy = y + (int) (((now - shownAt) / 12) % Math.max(1, ph));
        g.fill(x, sy, x + pw, sy + 2, ((a / 3) << 24) | col);
        // brackets
        int br = 8, edge = (a << 24) | lighten(col);
        g.fill(x + pw - br, y - 1, x + pw + 1, y, edge); g.fill(x + pw, y - 1, x + pw + 1, y + br, edge);
        g.fill(x - 1, y + ph, x + br, y + ph + 1, edge); g.fill(x - 1, y + ph - br, x, y + ph + 1, edge);
        g.fill(x + pw - br, y + ph, x + pw + 1, y + ph + 1, edge); g.fill(x + pw, y + ph - br, x + pw + 1, y + ph + 1, edge);
        g.fill(x - 1, y - 1, x + br, y, edge); g.fill(x - 1, y - 1, x, y + br, edge);
        if (ease < 1) return;
        g.drawString(mc.font, title, x + 7, y + 6, (a << 24) | lighten(col), true);
        g.fill(x + 6, y + 16, x + pw - 6, y + 17, ((a / 2) << 24) | col);
        int shown = (int) Math.min(wrapped.size(), (now - shownAt - 350) / 40);
        g.pose().pushPose();
        g.pose().translate(x + 7, y + 20, 0);
        g.pose().scale(0.8F, 0.8F, 1);
        for (int i = 0; i < shown; i++) g.drawString(mc.font, wrapped.get(i), 0, (int) (i * 9 / 0.8F), (a << 24) | 0xD6E8FF, false);
        g.pose().popPose();
    }

    private static int lighten(int c) {
        int r = (((c >> 16) & 255) + 255) / 2, gg = (((c >> 8) & 255) + 255) / 2, b = ((c & 255) + 255) / 2;
        return (r << 16) | (gg << 8) | b;
    }

    private AbyssClient() {}
}
