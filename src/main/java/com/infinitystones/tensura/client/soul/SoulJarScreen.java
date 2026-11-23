package com.infinitystones.tensura.client.soul;

import com.infinitystones.tensura.soul.SoulJar;
import com.infinitystones.tensura.soul.SoulNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Captured souls: grid of 3D previews; pick one, then release / execute / soul world / release possessed. */
final class SoulJarScreen extends Screen {
    private static final int COLS = 5, CW = 64, CH = 76, GAP = 6;
    private final Map<Integer, LivingEntity> previews = new HashMap<>();
    private List<CompoundTag> seen;
    private int selected = -1, scroll, hovered = -1;
    private final Button[] buttons = new Button[4];
    private final long opened = System.currentTimeMillis();

    SoulJarScreen() { super(Component.translatable("infinity.soul.menu.jar")); }

    @Override public boolean isPauseScreen() { return false; }

    private List<CompoundTag> entries() { return SoulNet.jar.entries(); }
    private int rows() { return Math.max(1, Math.min(3, (height - 120) / (CH + GAP))); }
    private int gridW() { return COLS * CW + (COLS - 1) * GAP; }
    private int x0() { return width / 2 - gridW() / 2; }
    private int y0() { return height / 2 - (rows() * (CH + GAP)) / 2 - 10; }

    @Override protected void init() {
        String[] keys = {"infinity.soul.menu.release", "infinity.soul.menu.execute", "infinity.soul.menu.soulworld", "infinity.soul.menu.possess"};
        int[] acts = {SoulJar.RELEASE, SoulJar.EXECUTE, SoulJar.SOUL_WORLD, SoulJar.POSSESS};
        int bw = (gridW() - 3 * 4) / 4, by = y0() + rows() * (CH + GAP) + 6;
        for (int i = 0; i < 4; i++) {
            int act = acts[i];
            buttons[i] = addRenderableWidget(Button.builder(Component.translatable(keys[i]), b -> send(act))
                    .bounds(x0() + i * (bw + 4), by, bw, 20).build());
        }
        updateButtons();
    }

    private void send(int act) {
        if (selected < 0 || selected >= entries().size()) return;
        PacketDistributor.sendToServer(new SoulNet.JarAction(act, selected));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                act == SoulJar.EXECUTE ? SoundEvents.WITHER_SKELETON_HURT : SoundEvents.SOUL_ESCAPE.value(), 1F, act == SoulJar.EXECUTE ? 0.6F : 1F));
        onClose(); // the action happens in the world: show it
    }

    private void updateButtons() {
        boolean ok = selected >= 0 && selected < entries().size();
        for (Button b : buttons) if (b != null) b.active = ok;
    }

    private LivingEntity preview(int i) {
        return previews.computeIfAbsent(i, k -> {
            try {
                CompoundTag nbt = entries().get(k).getCompound("nbt").copy();
                var e = EntityType.create(nbt, Minecraft.getInstance().level).orElse(null);
                return e instanceof LivingEntity le ? le : null;
            } catch (RuntimeException ex) { return null; }
        });
    }

    private Component name(int i) {
        try {
            Component c = Component.Serializer.fromJson(entries().get(i).getString("name"), Minecraft.getInstance().level.registryAccess());
            if (c != null) return c;
        } catch (RuntimeException ignored) { }
        return Component.literal("?");
    }

    @Override public void tick() {
        if (seen != entries()) { seen = entries(); previews.clear(); if (selected >= entries().size()) selected = -1; updateButtons(); }
    }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, 0x70100500, 0xB0200A00);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        float in = Mth.clamp((System.currentTimeMillis() - opened) / 250F, 0, 1);
        float t = (System.currentTimeMillis() % 3000) / 3000F;
        int x0 = x0(), y0 = y0(), rows = rows();
        g.drawCenteredString(font, title, width / 2, y0 - 30, 0xFFF28A1A);
        g.drawCenteredString(font, Component.translatable("infinity.soul.menu.jar_count", entries().size()), width / 2, y0 - 18, 0xFF9E8A78);
        hovered = -1;
        if (entries().isEmpty()) {
            g.drawCenteredString(font, Component.translatable("infinity.soul.menu.jar_empty"), width / 2, y0 + 30, 0xFFFFD9A0);
            return;
        }
        int perPage = rows * COLS;
        for (int s = 0; s < perPage; s++) {
            int i = s + scroll * COLS;
            if (i >= entries().size()) break;
            int cx = x0 + (s % COLS) * (CW + GAP), cy = y0 + (s / COLS) * (CH + GAP);
            boolean hov = mx >= cx && mx < cx + CW && my >= cy && my < cy + CH;
            if (hov) hovered = i;
            boolean sel = i == selected;
            int bg = sel ? 0xC0602808 : hov ? 0xA0402010 : (int) (0x90 * in) << 24 | 0x1A0C04;
            g.fill(cx, cy, cx + CW, cy + CH, bg);
            int glow = sel ? 0xFFFFD9A0 : (0xFF000000 | (hov ? 0xF28A1A : 0x7A4410));
            g.renderOutline(cx, cy, CW, CH, glow);
            if (sel) { int p = (int) (Mth.sin(t * Mth.TWO_PI) * 2); g.renderOutline(cx - 2 - p, cy - 2 - p, CW + 4 + 2 * p, CH + 4 + 2 * p, 0x80F28A1A); }
            LivingEntity le = preview(i);
            if (le != null) {
                float size = Math.max(le.getBbHeight(), le.getBbWidth() * 0.9F);
                int sc = (int) Mth.clamp(30 / Math.max(0.3F, size), 4, 40);
                g.enableScissor(cx + 1, cy + 1, cx + CW - 1, cy + CH - 12);
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, cx + 2, cy + 2, cx + CW - 2, cy + CH - 12, sc, 0.0625F, mx, my, le);
                g.disableScissor();
            } else g.drawCenteredString(font, "?", cx + CW / 2, cy + CH / 2 - 10, 0xFFF28A1A);
            Component n = name(i);
            String str = font.plainSubstrByWidth(n.getString(), CW - 4);
            g.drawCenteredString(font, str, cx + CW / 2, cy + CH - 10, sel ? 0xFFFFFFFF : 0xFFFFD9A0);
        }
        int pages = (entries().size() + COLS - 1) / COLS - rows + 1;
        if (pages > 1) g.drawCenteredString(font, "▲▼ " + (scroll + 1) + "/" + pages, width / 2, y0 + rows * (CH + GAP) + 30, 0xFF9E8A78);
        if (selected < 0) g.drawCenteredString(font, Component.translatable("infinity.soul.menu.jar_pick"), width / 2, y0 + rows * (CH + GAP) + 30 + (pages > 1 ? 10 : 0), 0xFF9E8A78);
    }

    @Override public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        int maxScroll = Math.max(0, (entries().size() + COLS - 1) / COLS - rows());
        scroll = Mth.clamp(scroll - (int) Math.signum(dy), 0, maxScroll);
        return true;
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (button == 0 && hovered >= 0) {
            selected = hovered;
            updateButtons();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4F, 0.4F));
            return true;
        }
        if (button == 1) { onClose(); return true; }
        return false;
    }
}
