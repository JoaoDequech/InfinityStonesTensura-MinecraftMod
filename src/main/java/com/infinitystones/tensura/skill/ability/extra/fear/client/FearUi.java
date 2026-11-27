package com.infinitystones.tensura.skill.ability.extra.fear.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import static com.infinitystones.tensura.skill.ability.extra.fear.client.FearGfx.argb;

/** Immediate-mode widgets for the control room and the portable panel: buttons, sliders, ripples, toasts, scan-lines. */
abstract class FearUi extends Screen {
    static final class Btn {
        int x, y, w, h; Component label; Runnable act; boolean on, danger, slider; int min, max, value; IntConsumer set; float hover;
        Btn(int x, int y, int w, int h, Component label) { this.x = x; this.y = y; this.w = w; this.h = h; this.label = label; }
        boolean in(double mx, double my) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    }

    private static final class Ripple { float x, y; int age; }

    protected final List<Btn> btns = new ArrayList<>();
    private final List<Ripple> ripples = new ArrayList<>();
    protected Component toast = Component.empty();
    protected int toastLeft, openAge;
    protected Btn drag;
    protected final int accent;

    protected FearUi(Component title, int accent) { super(title); this.accent = accent; }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void tick() { openAge++; if (toastLeft > 0) toastLeft--; ripples.removeIf(r -> ++r.age > 14); }

    protected abstract void layout();

    @Override
    protected void init() { rebuild(); }

    protected void rebuild() { btns.clear(); layout(); }

    protected Btn add(int x, int y, int w, int h, Component label, boolean on, Runnable act) {
        Btn b = new Btn(x, y, w, h, label); b.on = on; b.act = act; btns.add(b); return b;
    }

    protected Btn slider(int x, int y, int w, Component label, int min, int max, int value, IntConsumer set) {
        Btn b = new Btn(x, y, w, 14, label); b.slider = true; b.min = min; b.max = max; b.value = value; b.set = set; btns.add(b); return b;
    }

    protected void say(Component c) { toast = c; toastLeft = 50; }

    protected void click(Btn b, double mx, double my) {
        FearClient.sound(SoundEvents.UI_BUTTON_CLICK.value(), 1.25F, 0.6F);
        FearClient.sound(SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F + (b.x % 7) * 0.08F, 0.35F);
        Ripple r = new Ripple(); r.x = (float) mx; r.y = (float) my; ripples.add(r);
        say(Component.literal("› ").append(b.label));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = btns.size() - 1; i >= 0; i--) {
            Btn b = btns.get(i);
            if (!b.in(mx, my)) continue;
            if (b.slider) { drag = b; slide(b, mx); return true; }
            click(b, mx, my);
            if (b.act != null) b.act.run();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void slide(Btn b, double mx) {
        int v = b.min + Math.round((float) Mth.clamp((mx - b.x) / b.w, 0, 1) * (b.max - b.min));
        if (v != b.value) { b.value = v; if (b.set != null) b.set.accept(v); }
    }

    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) { if (drag != null) { slide(drag, mx); return true; } return super.mouseDragged(mx, my, button, dx, dy); }

    @Override public boolean mouseReleased(double mx, double my, int button) { drag = null; return super.mouseReleased(mx, my, button); }

    protected void drawBtns(GuiGraphics g, int mx, int my, float pt) {
        for (Btn b : btns) {
            boolean hov = b.in(mx, my);
            b.hover += ((hov ? 1F : 0F) - b.hover) * 0.35F;
            int base = b.danger ? 0xC02828 : accent;
            float a = 0.28F + b.hover * 0.3F + (b.on ? 0.25F : 0F);
            g.fill(b.x, b.y, b.x + b.w, b.y + b.h, argb(0.65F, 0x080406));
            g.fill(b.x, b.y, b.x + b.w, b.y + b.h, argb(a * 0.6F, base));
            g.fill(b.x, b.y, b.x + 2, b.y + b.h, argb(0.6F + b.hover * 0.4F, base));
            if (b.slider) {
                int fw = (int) (b.w * (b.value - b.min) / (float) Math.max(1, b.max - b.min));
                g.fill(b.x, b.y + b.h - 3, b.x + fw, b.y + b.h, argb(0.9F, base));
                g.drawString(font, b.label.copy().append(": " + b.value), b.x + 6, b.y + 3, 0xFFE6DCD0, false);
            } else {
                g.drawString(font, b.label, b.x + 6, b.y + (b.h - 8) / 2, b.on ? 0xFFFFF0D8 : 0xFFD0C4B8, false);
                if (b.on) g.fill(b.x + b.w - 6, b.y + 3, b.x + b.w - 3, b.y + b.h - 3, argb(0.9F, 0x70FF90));
            }
            if (b.hover > 0.1F) g.fill(b.x, b.y + b.h - 1, b.x + (int) (b.w * b.hover), b.y + b.h, argb(0.9F, base));
        }
        for (Ripple r : ripples) {
            float k = r.age / 14F;
            FearGfx.disc(g, r.x, r.y, 6 + k * 40, 6 + k * 40, argb(0F, accent), argb((1F - k) * 0.5F, accent), 18, true);
        }
    }

    protected void scanlines(GuiGraphics g, int x0, int y0, int x1, int y1, float a) {
        for (int y = y0; y < y1; y += 3) g.fill(x0, y, x1, y + 1, argb(a, 0));
        int noise = (int) ((x1 - x0) * (y1 - y0) / 4500F);
        for (int i = 0; i < noise; i++) {
            int x = x0 + (int) (FearGfx.hash(i, openAge + (int) (FearState.ticks)) * (x1 - x0)), y = y0 + (int) (FearGfx.hash(i + 99, openAge + (int) (FearState.ticks)) * (y1 - y0));
            g.fill(x, y, x + 1, y + 1, argb(0.12F, 0xFFFFFF));
        }
    }

    protected void drawToast(GuiGraphics g, int x, int y) {
        if (toastLeft <= 0) return;
        float a = Math.min(1F, toastLeft / 15F);
        g.drawString(font, toast, x, y, argb(a, 0xE8D8C0), true);
    }
}
