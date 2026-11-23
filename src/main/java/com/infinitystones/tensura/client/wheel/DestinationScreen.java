package com.infinitystones.tensura.client.wheel;

import com.infinitystones.tensura.wheel.WheelPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Destination menu (middle click on a destination power in the wheel): pick any dimension the server has — vanilla
 * and modded — then either its default point or exact coordinates. The server validates everything again.
 */
public final class DestinationScreen extends Screen {
    private static final int W = 320, ROW = 18, LIST_W = 132;
    private final int H;
    private boolean backView;
    private static boolean lastUseDefault = true;
    private static ResourceLocation lastDim;

    private final int stone;
    private final String node;
    private final boolean movesTarget;
    private final int col;
    private final long openedAt = System.currentTimeMillis();
    private final RandomSource rng = RandomSource.create();
    private final List<ResourceLocation> dims = new ArrayList<>();
    private int selected;
    private int scroll;
    private boolean useDefault = lastUseDefault;
    private boolean autoY = true;
    private EditBox bx, by, bz;
    private long confirmFlash = -1;
    private long lastFrame = -1;
    private final List<float[]> motes = new ArrayList<>(); // x, y, vx, vy, life, max

    public DestinationScreen(int stone, String node, boolean movesTarget, int color) {
        super(Component.translatable("infinity.dest.title"));
        this.stone = stone; this.node = node; this.movesTarget = movesTarget; this.col = color;
        this.H = movesTarget ? 232 : 204;
    }

    @Override public boolean isPauseScreen() { return false; }

    private int left() { return (width - W) / 2; }
    private int top() { return (height - H) / 2; }

    @Override protected void init() {
        dims.clear();
        if (minecraft != null && minecraft.getConnection() != null) {
            for (ResourceKey<Level> k : minecraft.getConnection().levels()) dims.add(k.location());
        }
        dims.sort(Comparator.comparingInt(DestinationScreen::rank).thenComparing(ResourceLocation::toString));
        ResourceLocation want = lastDim != null ? lastDim : Level.END.location();
        selected = Math.max(0, dims.indexOf(want));
        if (selected >= visibleRows()) scroll = selected - visibleRows() + 1;

        int x = left() + LIST_W + 22, y = top() + 92;
        var pos = minecraft != null && minecraft.player != null ? minecraft.player.blockPosition() : net.minecraft.core.BlockPos.ZERO;
        bx = box(x + 12, y, String.valueOf(pos.getX()));
        by = box(x + 66, y, String.valueOf(pos.getY()));
        bz = box(x + 120, y, String.valueOf(pos.getZ()));
        updateBoxes();
        if (movesTarget) PacketDistributor.sendToServer(new WheelPayloads.BanishAction(0, 0)); // fetch "bring back" list
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.2F, 0.25F));
    }

    private EditBox box(int x, int y, String value) {
        EditBox b = new EditBox(font, x, y, 40, 14, Component.empty());
        b.setBordered(false);
        b.setMaxLength(10);
        b.setFilter(s -> s.matches("-?[0-9]*\\.?[0-9]*"));
        b.setValue(value);
        b.setTextColor(0xFFFFFFFF);
        addRenderableWidget(b);
        return b;
    }

    private void updateBoxes() {
        bx.visible = by.visible = bz.visible = !useDefault && !backView;
        by.setEditable(!autoY);
        by.setTextColor(autoY ? 0xFF707078 : 0xFFFFFFFF);
    }

    private static int rank(ResourceLocation id) {
        if (id.equals(Level.OVERWORLD.location())) return 0;
        if (id.equals(Level.NETHER.location())) return 1;
        if (id.equals(Level.END.location())) return 2;
        return id.getNamespace().equals("infinity") ? 3 : 4;
    }

    private int visibleRows() { return (204 - 50) / ROW; }

    // ------------------------------------------------------------ render

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, (int) (0x70 * open()) << 24);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long time = System.currentTimeMillis();
        float dt = lastFrame < 0 ? 0 : Math.min(0.05F, (time - lastFrame) / 1000F);
        lastFrame = time;
        renderBackground(g, mouseX, mouseY, partialTick);
        float open = open();
        float s = 0.85F + 0.15F * easeOutBack(open);
        int x0 = left(), y0 = top();

        g.pose().pushPose();
        g.pose().translate(width / 2F, height / 2F, 0);
        g.pose().scale(s, s, 1);
        g.pose().translate(-width / 2F, -height / 2F, 0);

        // panel: dark glass + glowing frame in the stone colour
        float pulse = 0.5F + 0.5F * Mth.sin(time / 380F);
        glowFrame(g, x0, y0, W, H, col, open, pulse);
        g.fill(x0, y0, x0 + W, y0 + H, a(0xE0, 0x0A0A12, open));
        g.fill(x0, y0, x0 + W, y0 + 24, a(0x70, darken(col, 0.35F), open));
        g.fill(x0, y0 + 24, x0 + W, y0 + 25, a(0xFF, col, open));
        g.drawString(font, title.copy().withStyle(st -> st.withColor(lighten(col, 0.35F) & 0xFFFFFF)), x0 + 10, y0 + 8, 0xFFFFFFFF, true);
        Component who = Component.translatable(movesTarget ? "infinity.dest.who_target" : "infinity.dest.who_self");
        g.drawString(font, who, x0 + W - 10 - font.width(who), y0 + 8, 0xFF9AA0B0, false);

        // dimension list
        int lx = x0 + 8, ly = y0 + 32;
        g.fill(lx, ly, lx + LIST_W, ly + visibleRows() * ROW, a(0x90, 0x000000, open));
        for (int i = 0; i < visibleRows() && i + scroll < dims.size(); i++) {
            int idx = i + scroll, ry = ly + i * ROW;
            ResourceLocation id = dims.get(idx);
            boolean sel = idx == selected, hov = inside(mouseX, mouseY, lx, ry, LIST_W, ROW);
            if (sel) {
                g.fill(lx, ry, lx + LIST_W, ry + ROW, a(0xB0, darken(col, 0.55F), open));
                g.fill(lx, ry, lx + 2, ry + ROW, a(0xFF, lighten(col, 0.4F), open));
            } else if (hov) g.fill(lx, ry, lx + LIST_W, ry + ROW, a(0x40, col, open));
            int dot = dimColor(id);
            g.fill(lx + 7, ry + 6, lx + 12, ry + 11, a(0xFF, dot, open));
            String name = font.plainSubstrByWidth(dimName(id), LIST_W - 22);
            g.drawString(font, name, lx + 17, ry + 5, sel ? 0xFFFFFFFF : 0xFFC8CCD8, false);
        }
        if (dims.size() > visibleRows()) {
            int track = visibleRows() * ROW, thumb = Math.max(12, track * visibleRows() / dims.size());
            int ty = ly + (track - thumb) * scroll / Math.max(1, dims.size() - visibleRows());
            g.fill(lx + LIST_W - 2, ty, lx + LIST_W, ty + thumb, a(0xC0, col, open));
        }

        // right side
        int rx = x0 + LIST_W + 22, ry0 = y0 + 34;
        ResourceLocation dim = dims.isEmpty() ? null : dims.get(selected);
        if (movesTarget) {
            // bottom-left: "bring it back" toggle
            int bbx = x0 + 8, bby = y0 + H - 30;
            boolean hb = inside(mouseX, mouseY, bbx, bby, LIST_W, 20);
            glowFrame(g, bbx, bby, LIST_W, 20, col, open, backView ? 1 : hb ? 0.7F : 0.2F);
            g.fill(bbx, bby, bbx + LIST_W, bby + 20, a(0xFF, backView ? col : hb ? darken(col, 0.6F) : 0x1C1C26, open));
            int n = WheelPayloads.lastBanished.size();
            g.drawCenteredString(font, Component.translatable("infinity.dest.back.button", n), bbx + LIST_W / 2, bby + 6, 0xFFFFFFFF);
        }
        if (backView) {
            renderBackList(g, rx, ry0, mouseX, mouseY, open);
            int kx = x0 + W - 172, ky = y0 + H - 30;
            g.drawCenteredString(font, Component.translatable("infinity.dest.cancel"), kx + 27, ky + 6,
                    inside(mouseX, mouseY, kx, ky, 54, 20) ? 0xFFFFFFFF : 0xFF8A90A0);
            super.render(g, mouseX, mouseY, partialTick);
            g.pose().popPose();
            return;
        }
        if (dim != null) {
            g.drawString(font, Component.literal(dimName(dim)).withStyle(ChatFormatting.BOLD), rx, ry0, 0xFFFFFFFF, true);
            g.drawString(font, dim.toString(), rx, ry0 + 11, 0xFF6E7486, false);
        }
        pill(g, rx, y0 + 62, 76, Component.translatable("infinity.dest.mode_default"), useDefault, mouseX, mouseY, open);
        pill(g, rx + 82, y0 + 62, 76, Component.translatable("infinity.dest.mode_coords"), !useDefault, mouseX, mouseY, open);

        if (useDefault) {
            Component desc = dim == null ? Component.empty() : Component.translatable(defaultKey(dim));
            int yy = y0 + 92;
            for (FormattedCharSequence l : font.split(desc, 156)) { g.drawString(font, l, rx, yy, 0xFFB8BED0, false); yy += 10; }
        } else {
            int by0 = y0 + 92;
            field(g, rx, by0, "X", bx, open);
            field(g, rx + 54, by0, "Y", by, open);
            field(g, rx + 108, by0, "Z", bz, open);
            pill(g, rx, by0 + 22, 76, Component.translatable("infinity.dest.auto_y"), autoY, mouseX, mouseY, open);
            pill(g, rx + 82, by0 + 22, 76, Component.translatable("infinity.dest.here"), false, mouseX, mouseY, open);
            Component tip = Component.translatable(autoY ? "infinity.dest.tip_auto" : "infinity.dest.tip_exact");
            int yy = by0 + 44;
            for (FormattedCharSequence l : font.split(tip, 156)) { g.drawString(font, l, rx, yy, 0xFF7E8496, false); yy += 10; }
        }

        // confirm / cancel
        int cbx = x0 + W - 112, cby = y0 + H - 30;
        boolean hc = inside(mouseX, mouseY, cbx, cby, 102, 20);
        float flash = confirmFlash < 0 ? 0 : Math.max(0, 1 - (time - confirmFlash) / 300F);
        int cfill = hc ? lighten(col, 0.15F) : darken(col, 0.75F);
        glowFrame(g, cbx, cby, 102, 20, col, open, hc ? 1 : pulse * 0.6F);
        g.fill(cbx, cby, cbx + 102, cby + 20, a(0xFF, flash > 0 ? lighten(cfill, flash) : cfill, open));
        Component go = Component.translatable(movesTarget ? "infinity.dest.go_target" : "infinity.dest.go_self").withStyle(ChatFormatting.BOLD);
        g.drawCenteredString(font, go, cbx + 51, cby + 6, 0xFFFFFFFF);
        int kx = x0 + W - 172, hk = inside(mouseX, mouseY, kx, cby, 54, 20) ? 0xFFFFFFFF : 0xFF8A90A0;
        g.drawCenteredString(font, Component.translatable("infinity.dest.cancel"), kx + 27, cby + 6, hk);

        // motes rising off the frame
        if (rng.nextInt(3) == 0 && motes.size() < 60) {
            float mx = x0 + rng.nextFloat() * W, my = rng.nextBoolean() ? y0 : y0 + H;
            motes.add(new float[]{mx, my, (rng.nextFloat() - 0.5F) * 10, -8 - rng.nextFloat() * 16, 1.2F, 1.2F});
        }
        for (Iterator<float[]> it = motes.iterator(); it.hasNext(); ) {
            float[] m = it.next();
            m[4] -= dt; if (m[4] <= 0) { it.remove(); continue; }
            m[0] += m[2] * dt; m[1] += m[3] * dt;
            float k = m[4] / m[5];
            int alpha = (int) (200 * Math.min(1, k * 2) * open);
            g.fill((int) m[0], (int) m[1], (int) m[0] + 1, (int) m[1] + 1, (alpha << 24) | (lighten(col, 0.4F) & 0xFFFFFF));
        }

        super.render(g, mouseX, mouseY, partialTick); // edit boxes
        g.pose().popPose();
    }

    private static final int BACK_ROW = 22;

    private void renderBackList(GuiGraphics g, int rx, int ry0, int mouseX, int mouseY, float open) {
        g.drawString(font, Component.translatable("infinity.dest.back.title").withStyle(ChatFormatting.BOLD), rx, ry0, 0xFFFFFFFF, true);
        g.drawString(font, font.plainSubstrByWidth(I18n.get("infinity.dest.back.sub"), 160), rx, ry0 + 11, 0xFF6E7486, false);
        List<WheelPayloads.Banished> list = WheelPayloads.lastBanished;
        if (list.isEmpty()) {
            int yy = ry0 + 32;
            for (FormattedCharSequence l : font.split(Component.translatable("infinity.dest.back.empty"), 158)) { g.drawString(font, l, rx, yy, 0xFF8A90A0, false); yy += 10; }
            return;
        }
        for (int i = 0; i < Math.min(7, list.size()); i++) {
            WheelPayloads.Banished b = list.get(i);
            int y = ry0 + 28 + i * BACK_ROW;
            boolean hov = inside(mouseX, mouseY, rx, y, 158, BACK_ROW - 3);
            g.fill(rx, y, rx + 158, y + BACK_ROW - 3, a(hov ? 0xC0 : 0x80, hov ? darken(col, 0.6F) : 0x12121A, open));
            g.fill(rx, y, rx + 2, y + BACK_ROW - 3, a(0xFF, dimColor(ResourceLocation.parse(b.inDim())), open));
            g.drawString(font, font.plainSubstrByWidth(b.name(), 100), rx + 6, y + 2, 0xFFFFFFFF, false);
            g.pose().pushPose();
            g.pose().translate(rx + 6, y + 11, 0);
            g.pose().scale(0.7F, 0.7F, 1);
            g.drawString(font, Component.translatable("infinity.dest.back.in", dimName(ResourceLocation.parse(b.inDim()))), 0, 0, 0xFF9AA0B0, false);
            g.pose().popPose();
            Component bring = Component.translatable("infinity.dest.back.do");
            int bw = font.width(bring) + 8, bxx = rx + 158 - bw - 2;
            g.fill(bxx, y + 3, bxx + bw, y + BACK_ROW - 6, a(0xFF, hov ? col : darken(col, 0.55F), open));
            g.drawString(font, bring, bxx + 4, y + 5, 0xFFFFFFFF, false);
        }
    }

    private void field(GuiGraphics g, int x, int y, String label, EditBox box, float open) {
        g.drawString(font, label, x, y + 3, 0xFF9AA0B0, false);
        boolean focus = box.isFocused();
        int c = box == by && autoY ? 0x404048 : focus ? lighten(col, 0.3F) : darken(col, 0.8F);
        g.fill(x + 9, y - 2, x + 52, y + 12, a(0xFF, c, open));
        g.fill(x + 10, y - 1, x + 51, y + 11, a(0xFF, 0x05050A, open));
        box.setX(x + 12); box.setY(y + 1);
    }

    private void pill(GuiGraphics g, int x, int y, int w, Component text, boolean on, int mx, int my, float open) {
        boolean hov = inside(mx, my, x, y, w, 16);
        g.fill(x, y, x + w, y + 16, a(0xFF, on ? col : hov ? darken(col, 0.6F) : 0x1C1C26, open));
        if (on) g.fill(x, y + 15, x + w, y + 16, a(0xFF, lighten(col, 0.6F), open));
        g.drawCenteredString(font, text, x + w / 2, y + 4, on ? 0xFFFFFFFF : 0xFFAAB0C0);
    }

    private static void glowFrame(GuiGraphics g, int x, int y, int w, int h, int col, float open, float pulse) {
        for (int i = 4; i >= 1; i--) {
            int alpha = (int) ((10 + 16 * pulse) * (5 - i) * open);
            g.fill(x - i, y - i, x + w + i, y + h + i, (Mth.clamp(alpha, 0, 255) << 24) | (col & 0xFFFFFF));
        }
    }

    // ------------------------------------------------------------ input

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x0 = left(), y0 = top();
        // the panel is drawn scaled around the centre while opening; after 200 ms it is 1:1 and clicks are exact
        int lx = x0 + 8, ly = y0 + 32;
        if (inside(mouseX, mouseY, lx, ly, LIST_W, visibleRows() * ROW)) {
            int idx = scroll + (int) ((mouseY - ly) / ROW);
            if (idx < dims.size()) { selected = idx; click(1.5F); }
            return true;
        }
        int rx = x0 + LIST_W + 22;
        if (movesTarget && inside(mouseX, mouseY, x0 + 8, y0 + H - 30, LIST_W, 20)) {
            backView = !backView; updateBoxes(); click(1.2F);
            if (backView) PacketDistributor.sendToServer(new WheelPayloads.BanishAction(0, 0));
            return true;
        }
        if (backView) {
            List<WheelPayloads.Banished> list = WheelPayloads.lastBanished;
            for (int i = 0; i < Math.min(7, list.size()); i++) {
                if (inside(mouseX, mouseY, rx, y0 + 34 + 28 + i * BACK_ROW, 158, BACK_ROW - 3)) {
                    PacketDistributor.sendToServer(new WheelPayloads.BanishAction(1, i));
                    if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.4F, 0.35F));
                    confirmFlash = System.currentTimeMillis();
                    return true;
                }
            }
            if (inside(mouseX, mouseY, x0 + W - 172, y0 + H - 30, 54, 20)) { onClose(); return true; }
            return true;
        }
        if (inside(mouseX, mouseY, rx, y0 + 62, 76, 16)) { useDefault = true; updateBoxes(); click(1.3F); return true; }
        if (inside(mouseX, mouseY, rx + 82, y0 + 62, 76, 16)) { useDefault = false; updateBoxes(); click(1.3F); return true; }
        if (!useDefault) {
            int by0 = y0 + 92;
            if (inside(mouseX, mouseY, rx, by0 + 22, 76, 16)) { autoY = !autoY; updateBoxes(); click(1.6F); return true; }
            if (inside(mouseX, mouseY, rx + 82, by0 + 22, 76, 16) && minecraft != null && minecraft.player != null) {
                var p = minecraft.player.blockPosition();
                bx.setValue(String.valueOf(p.getX())); by.setValue(String.valueOf(p.getY())); bz.setValue(String.valueOf(p.getZ()));
                click(1.8F); return true;
            }
        }
        int cby = y0 + H - 30;
        if (inside(mouseX, mouseY, x0 + W - 112, cby, 102, 20)) { confirm(); return true; }
        if (inside(mouseX, mouseY, x0 + W - 172, cby, 54, 20)) { onClose(); return true; }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double sx, double sy) {
        scroll = Mth.clamp(scroll - (int) Math.signum(sy), 0, Math.max(0, dims.size() - visibleRows()));
        return true;
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335) && !backView) { confirm(); return true; } // Enter / keypad Enter
        if (keyCode == 265 && selected > 0) { selected--; if (selected < scroll) scroll = selected; return true; }
        if (keyCode == 264 && selected < dims.size() - 1) { selected++; if (selected >= scroll + visibleRows()) scroll++; return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void confirm() {
        if (dims.isEmpty()) return;
        double x = 0, y = 0, z = 0;
        if (!useDefault) {
            try {
                x = Double.parseDouble(bx.getValue());
                z = Double.parseDouble(bz.getValue());
                if (!autoY) y = Double.parseDouble(by.getValue());
            } catch (NumberFormatException e) {
                click(0.6F);
                return;
            }
        }
        lastDim = dims.get(selected);
        lastUseDefault = useDefault;
        confirmFlash = System.currentTimeMillis();
        WheelPayloads.Destination dest = new WheelPayloads.Destination(stone, node, lastDim.toString(), useDefault, x, y, z, autoY || useDefault);
        // moving a target (Banishment): destination chosen here, then aim at the target in the world and confirm with left click
        if (movesTarget) AimMode.startDestination(dest); else PacketDistributor.sendToServer(dest);
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.35F));
        onClose();
    }

    // ------------------------------------------------------------ helpers

    private void click(float pitch) {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.2F));
    }

    static String dimName(ResourceLocation id) {
        String key = "dimension." + id.getNamespace() + "." + id.getPath();
        if (I18n.exists(key)) return I18n.get(key);
        if (id.getNamespace().equals("minecraft")) {
            String k2 = "infinity.dest.dim." + id.getPath();
            if (I18n.exists(k2)) return I18n.get(k2);
        }
        String path = id.getPath().replace('/', ' ').replace('_', ' ');
        StringBuilder b = new StringBuilder();
        for (String w : path.split(" ")) if (!w.isEmpty()) b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return b.toString().trim();
    }

    private static String defaultKey(ResourceLocation id) {
        if (id.equals(Level.END.location())) return "infinity.dest.default.end";
        if (id.equals(Level.NETHER.location())) return "infinity.dest.default.nether";
        if (id.equals(Level.OVERWORLD.location())) return "infinity.dest.default.overworld";
        return "infinity.dest.default.other";
    }

    private int dimColor(ResourceLocation id) {
        if (id.equals(Level.OVERWORLD.location())) return 0x3FBF5A;
        if (id.equals(Level.NETHER.location())) return 0xD8402A;
        if (id.equals(Level.END.location())) return 0xB58CFF;
        return (id.toString().hashCode() & 0x7F7F7F) | 0x404040;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) { return mx >= x && my >= y && mx < x + w && my < y + h; }
    private float open() { return Mth.clamp((System.currentTimeMillis() - openedAt) / 200F, 0, 1); }
    private static float easeOutBack(float t) { float c1 = 1.70158F, c3 = c1 + 1; return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2); }
    private static int a(int alpha, int rgb, float open) { return (Mth.clamp((int) (alpha * open), 0, 255) << 24) | (rgb & 0xFFFFFF); }
    private static int darken(int c, float f) { return ((int) (((c >> 16) & 255) * f) << 16) | ((int) (((c >> 8) & 255) * f) << 8) | (int) ((c & 255) * f); }
    private static int lighten(int c, float f) {
        int r = (c >> 16) & 255, gg = (c >> 8) & 255, b = c & 255;
        return ((int) (r + (255 - r) * f) << 16) | ((int) (gg + (255 - gg) * f) << 8) | (int) (b + (255 - b) * f);
    }

    @SuppressWarnings("unused") private static String fmt(double v) { return String.format(Locale.ROOT, "%.0f", v); }
}
