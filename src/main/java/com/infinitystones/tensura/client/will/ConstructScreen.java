package com.infinitystones.tensura.client.will;

import com.infinitystones.tensura.wheel.WheelPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Hard-light construct menu (right click on Will in the wheel). Build a new construct, copy the Aeronautics contraption
 * you are looking at, and manage saved ones. In the list: left click = summon, right click = dismiss its copies,
 * middle click = move up (the first 8 are the quick slots of the middle click on Will).
 */
public final class ConstructScreen extends Screen {
    private static final int W = 300, H = 214, ROW = 18, COL = 0xFF3346;
    private final long openedAt = System.currentTimeMillis();
    private final RandomSource rng = RandomSource.create();
    private final List<float[]> motes = new ArrayList<>();
    private EditBox name;
    private int scroll, selected = -1, renaming = -1;
    private Component tip;

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if ((key == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || key == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) && name.isFocused()) {
            if (renaming >= 0 && !name.getValue().isBlank()) { send(8, renaming); renaming = -1; name.setValue(""); click(1.5F); }
            else if (WheelPayloads.constructs.designing()) { send(3, -1); name.setValue(""); click(1.6F); }
            name.setFocused(false); setFocused(null);
            return true;
        }
        if (name.isFocused() && key != org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) return name.keyPressed(key, scan, mods) || true; // typing never closes the menu
        return super.keyPressed(key, scan, mods);
    }
    private long lastFrame = -1;

    public ConstructScreen() { super(Component.translatable("infinity.construct.title")); }

    @Override public boolean isPauseScreen() { return false; }
    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    @Override protected void init() {
        name = new EditBox(font, left() + 10, top() + 32, 150, 14, Component.translatable("infinity.construct.name"));
        name.setMaxLength(32);
        name.setHint(Component.translatable("infinity.construct.name").withStyle(ChatFormatting.DARK_GRAY));
        name.setBordered(false);
        name.setTextColor(0xFFFFFFFF);
        addRenderableWidget(name);
        send(0, -1);
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.1F, 0.3F));
    }

    private void send(int action, int index) { PacketDistributor.sendToServer(new WheelPayloads.ConstructAction(action, index, name == null ? "" : name.getValue())); }

    private void click(float pitch) { if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.25F)); }

    // ------------------------------------------------------------ render

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) { g.fill(0, 0, width, height, (int) (0x70 * open()) << 24); }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        long time = System.currentTimeMillis();
        float dt = lastFrame < 0 ? 0 : Math.min(0.05F, (time - lastFrame) / 1000F);
        lastFrame = time;
        renderBackground(g, mx, my, pt);
        tip = null;
        WheelPayloads.ConstructList st = WheelPayloads.constructs;
        float o = open(), s = 0.88F + 0.12F * o, pulse = 0.5F + 0.5F * Mth.sin(time / 380F);
        int x0 = left(), y0 = top();
        g.pose().pushPose();
        g.pose().translate(width / 2F, height / 2F, 0); g.pose().scale(s, s, 1); g.pose().translate(-width / 2F, -height / 2F, 0);
        for (int i = 4; i >= 1; i--) g.fill(x0 - i, y0 - i, x0 + W + i, y0 + H + i, a((int) ((10 + 16 * pulse) * (5 - i)), COL, o));
        g.fill(x0, y0, x0 + W, y0 + H, a(0xE6, 0x0E0608, o));
        g.fill(x0, y0, x0 + W, y0 + 24, a(0x80, 0x3A0A10, o));
        g.fill(x0, y0 + 24, x0 + W, y0 + 25, a(0xFF, COL, o));
        g.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), x0 + 10, y0 + 8, 0xFFFF9AA2, true);
        g.drawString(font, Component.translatable("infinity.construct.active", st.active()), x0 + W - 10 - font.width(Component.translatable("infinity.construct.active", st.active())), y0 + 8, 0xFFB08A8E, false);

        // name box frame
        g.fill(x0 + 8, y0 + 29, x0 + 164, y0 + 45, a(0xFF, name.isFocused() ? 0xFF6672 : 0x5A1C24, o));
        g.fill(x0 + 9, y0 + 30, x0 + 163, y0 + 44, a(0xFF, 0x070304, o));
        name.setX(x0 + 12); name.setY(y0 + 33);

        if (st.designing()) {
            button(g, x0 + 170, y0 + 28, 58, Component.translatable("infinity.construct.save"), mx, my, true, o);
            button(g, x0 + 232, y0 + 28, 58, Component.translatable("infinity.construct.cancel"), mx, my, false, o);
            g.drawCenteredString(font, Component.translatable("infinity.construct.designing", WheelPayloads.designBlocks.size()).withStyle(ChatFormatting.BOLD), x0 + W / 2, y0 + 70, 0xFFFF8A94);
            int yy = y0 + 88;
            for (var l : font.split(Component.translatable("infinity.construct.design_help"), W - 30)) { g.drawCenteredString(font, l, x0 + W / 2, yy, 0xFFC8A6AA); yy += 10; }
        } else {
            button(g, x0 + 170, y0 + 28, 120, Component.translatable("infinity.construct.new"), mx, my, true, o);
            button(g, x0 + 10, y0 + 50, 138, Component.translatable("infinity.construct.copy"), mx, my, false, o);
            button(g, x0 + 152, y0 + 50, 138, Component.translatable("infinity.construct.dismiss_all"), mx, my, false, o);
            // list
            int ly = y0 + 76, rows = visibleRows();
            g.fill(x0 + 8, ly, x0 + W - 8, ly + rows * ROW, a(0x90, 0x000000, o));
            if (st.names().isEmpty()) {
                int yy = ly + 20;
                for (var l : font.split(Component.translatable("infinity.construct.empty"), W - 40)) { g.drawCenteredString(font, l, x0 + W / 2, yy, 0xFF8A7276); yy += 10; }
            }
            for (int i = 0; i < rows && i + scroll < st.names().size(); i++) {
                int idx = i + scroll, ry = ly + i * ROW;
                boolean hov = inside(mx, my, x0 + 8, ry, W - 16, ROW);
                if (idx == selected) g.fill(x0 + 8, ry, x0 + W - 8, ry + ROW, a(0xB0, 0x5A1620, o));
                else if (hov) g.fill(x0 + 8, ry, x0 + W - 8, ry + ROW, a(0x50, COL, o));
                if (idx < 8) { g.fill(x0 + 12, ry + 3, x0 + 24, ry + 15, a(0xFF, COL, o)); g.drawCenteredString(font, String.valueOf(idx + 1), x0 + 18, ry + 5, 0xFFFFFFFF); }
                g.drawString(font, font.plainSubstrByWidth(st.names().get(idx), 132), x0 + 30, ry + 5, 0xFFFFFFFF, false);
                g.drawString(font, Component.translatable("infinity.construct.blocks", st.counts().get(idx)), x0 + 168, ry + 5, 0xFFB08A8E, false);
                // rename / delete icons
                boolean he = inside(mx, my, x0 + W - 66, ry + 2, 16, 14), hr = inside(mx, my, x0 + W - 46, ry + 2, 16, 14), hd = inside(mx, my, x0 + W - 26, ry + 2, 16, 14);
                g.drawString(font, "⚒", x0 + W - 62, ry + 5, he ? 0xFFFFFFFF : 0xFFB08A8E, false);
                g.drawString(font, "✎", x0 + W - 42, ry + 5, hr || renaming == idx ? 0xFFFFFFFF : 0xFFB08A8E, false);
                if (he) tip = Component.translatable("infinity.construct.edit_blocks");
                else if (hr) tip = Component.translatable(renaming == idx ? "infinity.construct.rename_confirm" : "infinity.construct.rename");
                else if (hd) tip = Component.translatable("infinity.construct.delete");
                g.drawString(font, "✖", x0 + W - 22, ry + 5, hd ? 0xFFFF6A6A : 0xFFB08A8E, false);
            }
            g.pose().pushPose();
            g.pose().translate(x0 + W / 2F, y0 + H - 13, 0);
            g.pose().scale(0.75F, 0.75F, 1);
            g.drawCenteredString(font, Component.translatable("infinity.construct.legend"), 0, 0, 0xFF8A7276);
            g.pose().popPose();
        }
        // embers
        if (rng.nextInt(3) == 0 && motes.size() < 50) motes.add(new float[]{x0 + rng.nextFloat() * W, rng.nextBoolean() ? y0 : y0 + H, (rng.nextFloat() - 0.5F) * 10, -10 - rng.nextFloat() * 14, 1.3F, 1.3F});
        for (Iterator<float[]> it = motes.iterator(); it.hasNext(); ) {
            float[] q = it.next();
            q[4] -= dt; if (q[4] <= 0) { it.remove(); continue; }
            q[0] += q[2] * dt; q[1] += q[3] * dt;
            int alpha = (int) (210 * Math.min(1, q[4] / q[5] * 2) * o);
            g.fill((int) q[0], (int) q[1], (int) q[0] + 1, (int) q[1] + 1, (alpha << 24) | 0xFF8A94);
        }
        super.render(g, mx, my, pt);
        g.pose().popPose();
        if (tip != null) g.renderTooltip(font, tip, mx, my);
    }

    private int visibleRows() { return (H - 76 - 22) / ROW; }

    private void button(GuiGraphics g, int x, int y, int w, Component label, int mx, int my, boolean primary, float o) {
        boolean hov = inside(mx, my, x, y, w, 16);
        int c = primary ? (hov ? 0xFF5A66 : 0xB01E2C) : (hov ? 0x5A1620 : 0x26101A);
        if (primary) for (int i = 2; i >= 1; i--) g.fill(x - i, y - i, x + w + i, y + 16 + i, a(0x30 * (3 - i), COL, o));
        g.fill(x, y, x + w, y + 16, a(0xFF, c, o));
        g.drawCenteredString(font, label, x + w / 2, y + 4, 0xFFFFFFFF);
    }

    // ------------------------------------------------------------ input

    @Override public boolean mouseClicked(double mx, double my, int button) {
        WheelPayloads.ConstructList st = WheelPayloads.constructs;
        int x0 = left(), y0 = top();
        if (button == 0 && st.designing()) {
            if (inside(mx, my, x0 + 170, y0 + 28, 58, 16)) { send(3, -1); name.setValue(""); click(1.6F); return true; }
            if (inside(mx, my, x0 + 232, y0 + 28, 58, 16)) { send(2, -1); click(0.9F); return true; }
            return super.mouseClicked(mx, my, button);
        }
        if (button == 0 && inside(mx, my, x0 + 170, y0 + 28, 120, 16)) { send(1, -1); click(1.6F); onClose(); return true; }
        if (button == 0 && inside(mx, my, x0 + 10, y0 + 50, 138, 16)) { send(4, -1); name.setValue(""); click(1.4F); return true; }
        if (button == 0 && inside(mx, my, x0 + 152, y0 + 50, 138, 16)) { send(6, -1); click(1.0F); return true; }
        int ly = y0 + 76;
        if (inside(mx, my, x0 + 8, ly, W - 16, visibleRows() * ROW)) {
            int idx = scroll + (int) ((my - ly) / ROW);
            if (idx >= st.names().size()) return true;
            int ry = ly + (idx - scroll) * ROW;
            if (button == 0 && inside(mx, my, x0 + W - 66, ry + 2, 16, 14)) { // EDIT blocks: it appears in front of you
                send(11, idx); click(1.7F); onClose(); return true;
            }
            if (button == 0 && inside(mx, my, x0 + W - 46, ry + 2, 16, 14)) { // rename: name box gets the name; ✎ / Enter confirms
                if (renaming == idx && !name.getValue().isBlank()) { send(8, idx); name.setValue(""); renaming = -1; click(1.5F); }
                else { renaming = idx; selected = idx; name.setValue(st.names().get(idx)); name.setFocused(true); setFocused(name); name.moveCursorToEnd(false); click(1.2F); }
                return true;
            }
            if (button == 0 && inside(mx, my, x0 + W - 26, ry + 2, 16, 14)) { send(7, idx); click(0.7F); return true; }
            selected = idx;
            switch (button) {
                case 0 -> { send(5, idx); click(1.8F); onClose(); }      // summon
                case 1 -> { send(10, idx); click(1.0F); }                 // dismiss its copies
                case 2 -> { send(9, idx); click(1.3F); }                  // move up (towards the quick slots)
                default -> { }
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        scroll = Mth.clamp(scroll - (int) Math.signum(sy), 0, Math.max(0, WheelPayloads.constructs.names().size() - visibleRows()));
        return true;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    private float open() { return Mth.clamp((System.currentTimeMillis() - openedAt) / 200F, 0, 1); }
    private static int a(int alpha, int rgb, float o) { return (Mth.clamp((int) (alpha * o), 0, 255) << 24) | (rgb & 0xFFFFFF); }
}
