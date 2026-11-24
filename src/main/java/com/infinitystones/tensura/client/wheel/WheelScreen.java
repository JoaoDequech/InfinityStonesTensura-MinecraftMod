package com.infinitystones.tensura.client.wheel;

import com.infinitystones.tensura.skill.DualModes;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneTree;
import com.infinitystones.tensura.wheel.WheelClientState;
import com.infinitystones.tensura.wheel.WheelPayloads;
import com.infinitystones.tensura.wheel.WheelService;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The Infinity wheel. Hold the wheel key: the inner ring lists carried stones, the outer ring the focused stone's
 * powers. Left click uses the hovered power, right click its second variant (dual powers); releasing the key closes it.
 * All effects (pulses, shockwaves, glowing particles) are drawn in the wheel's own space, in the stone's colour.
 */
public final class WheelScreen extends Screen {
    private static final float INNER_IN = 44, INNER_OUT = 84, OUTER_IN = 94, OUTER_OUT = 148;
    private static final long CLOSE_MS = 170;
    private static int focusedStone = -1;

    private final long openedAt = System.currentTimeMillis();
    private final RandomSource rng = RandomSource.create();
    private int hoveredStone = -1;
    private int hoveredEntry = -1;
    private boolean hubHover;
    private int lastHoverSig = Integer.MIN_VALUE;
    private boolean usedSomething;
    private boolean introDone;
    private long closingAt = -1;
    private long lastFrame = -1;
    private long lastAmbient;
    private float lastScale = 1F;

    // ---- effects state (wheel-local coordinates, unscaled) ----
    private static final class Particle {
        float x, y, vx, vy, life, max, size, spin; int color; boolean star;
    }
    private record Pulse(long start, int stone, int page, int entry, boolean denied) {}
    private record Wave(long start, int color, float from, float to, long ms) {}
    private final List<Particle> particles = new ArrayList<>();
    private final List<Pulse> pulses = new ArrayList<>();
    private final List<Wave> waves = new ArrayList<>();
    private long hubFlash = -1;
    private int hubFlashColor;

    public WheelScreen() { super(Component.translatable("infinity.wheel.title")); }

    @Override public boolean isPauseScreen() { return false; }

    @Override protected void init() {
        if (introDone) return; // init() runs again on window resize
        introDone = true;
        playUi(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F, 0.25F);
        List<Slice> s = slices();
        int col = s.isEmpty() ? 0xFFFFFFFF : color(focusedStone >= 0 && s.stream().anyMatch(x -> x.stone() == focusedStone) ? focusedStone : s.get(0).stone());
        waves.add(new Wave(openedAt, col, INNER_IN * 0.5F, OUTER_OUT + 26, 420));
        for (int i = 0; i < 28; i++) {
            float a = rng.nextFloat() * Mth.TWO_PI, sp = 70 + rng.nextFloat() * 120;
            spawn(Mth.cos(a) * 12, Mth.sin(a) * 12, Mth.cos(a) * sp, Mth.sin(a) * sp, 0.35F + rng.nextFloat() * 0.35F,
                    1F + rng.nextFloat() * 1.3F, rng.nextInt(4) == 0 ? lighten(col, 0.7F) : col, rng.nextInt(6) == 0);
        }
    }

    // ------------------------------------------------------------ data

    /** entries = what the focused page shows (all hover / click logic indexes into it); pages = both pages (0 = main, 1 = page 2). */
    private record Slice(int stone, List<WheelPayloads.Entry> entries, List<List<WheelPayloads.Entry>> pages) {
        boolean hasPage2() { return !pages.get(1).isEmpty(); }
    }

    private List<Slice> slices() {
        List<Slice> out = new ArrayList<>();
        WheelPayloads.State st = WheelClientState.get();
        for (Stone s : Stone.values()) {
            List<WheelPayloads.Entry> list = new ArrayList<>();
            for (WheelPayloads.Entry e : st.entries()) if (e.stone() == s.ordinal()) list.add(e);
            if (s == Stone.SOUL) for (WheelPayloads.Entry e : st.entries()) if (e.stone() == -1) list.add(e);
            if (list.isEmpty()) continue;
            List<WheelPayloads.Entry> p1 = new ArrayList<>(), p2 = new ArrayList<>();
            for (WheelPayloads.Entry e : list) (pageOf(e) == 1 ? p2 : p1).add(e);
            if (p1.isEmpty()) { p1 = list; p2 = new ArrayList<>(); }
            boolean second = s.ordinal() == focusedStone && page == 1 && !p2.isEmpty();
            out.add(new Slice(s.ordinal(), second ? p2 : p1, List.of(p1, p2)));
        }
        return out;
    }

    /** 0 = main page, 1 = page 2 (nodes flagged "page": 2). */
    private static int pageOf(WheelPayloads.Entry e) {
        StoneTree.Node n = node(e);
        return n != null && n.page() >= 2 ? 1 : 0;
    }

    // ---- page state: page = target, pagePos animates between pages (0..1)
    private static final long PAGE_MS = 200;
    private static final float PAGE_SLIDE = 150F;
    private int page;
    private float pageFrom;
    private long pageAt;

    private boolean pageSettled(long now) { return now - pageAt >= PAGE_MS; }

    private float pagePos(long now) {
        float t = Mth.clamp((now - pageAt) / (float) PAGE_MS, 0F, 1F);
        float e = 1 - (1 - t) * (1 - t) * (1 - t);
        return pageFrom + (page - pageFrom) * e;
    }

    private void setPage(int target, long now, boolean animate) {
        pageFrom = animate ? pagePos(now) : target;
        page = target;
        pageAt = animate ? now : 0;
    }

    // ------------------------------------------------------------ render

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float a = openProgress();
        int cx = width / 2, cy = height / 2;
        // soft radial vignette instead of the vanilla blur, so the world stays readable
        for (int i = 6; i >= 1; i--) {
            float r = OUTER_OUT + 40 + i * 26;
            disc(g, cx, cy, r, argb((int) (a * 16 * i / 6F), 0, 0, 0));
        }
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long time = System.currentTimeMillis();
        float dt = lastFrame < 0 ? 0 : Math.min(0.05F, (time - lastFrame) / 1000F);
        lastFrame = time;
        if (closingAt >= 0 && time - closingAt >= CLOSE_MS) { onClose(); return; }

        renderBackground(g, mouseX, mouseY, partialTick);
        List<Slice> slices = slices();
        int cx = width / 2, cy = height / 2;
        float open = openProgress();
        float fit = Math.min(1F, (Math.min(width, height) / 2F - 14F) / (OUTER_OUT + 30F));
        float scale = fit * (closingAt >= 0 ? 0.85F + 0.15F * open : 0.6F + 0.4F * easeOutBack(open));
        lastScale = scale;

        if (slices.isEmpty()) {
            g.drawCenteredString(font, Component.translatable("infinity.wheel.empty"), cx, cy - 4, 0xFFBBBBBB);
            return;
        }
        if (focusedStone < 0 || slices.stream().noneMatch(s -> s.stone() == focusedStone)) focusedStone = slices.get(0).stone();

        // hover resolution (frozen while closing)
        double dx = mouseX - cx, dy = mouseY - cy, dist = Math.sqrt(dx * dx + dy * dy) / scale;
        double ang = (Math.atan2(dy, dx) + Math.PI / 2 + Math.PI * 2) % (Math.PI * 2);
        if (closingAt < 0) {
            hoveredStone = -1; hoveredEntry = -1;
            if (dist >= INNER_IN - 6 && dist <= INNER_OUT + 4) {
                int i = (int) (ang / (Math.PI * 2 / slices.size()));
                hoveredStone = slices.get(Mth.clamp(i, 0, slices.size() - 1)).stone();
                if (hoveredStone != focusedStone) {
                    focusedStone = hoveredStone;
                    setPage(0, time, false);
                    slices = slices();
                    waves.add(new Wave(time, color(focusedStone), OUTER_IN - 6, OUTER_OUT + 8, 260));
                }
            }
        }
        hubHover = closingAt < 0 && dist < INNER_IN - 4;
        Slice focus = slices.stream().filter(s -> s.stone() == focusedStone).findFirst().orElse(slices.get(0));
        if (page == 1 && !focus.hasPage2()) setPage(0, time, false); // page 2 went away (e.g. stone dropped)
        boolean settled = pageSettled(time);
        float pos = pagePos(time);
        if (closingAt < 0 && settled && dist >= OUTER_IN - 4 && dist <= OUTER_OUT + 18) {
            int i = (int) (ang / (Math.PI * 2 / focus.entries().size()));
            hoveredEntry = Mth.clamp(i, 0, focus.entries().size() - 1);
        }
        if (hoveredEntry >= focus.entries().size()) hoveredEntry = -1;
        int sig = hoveredStone * 1000 + hoveredEntry;
        if (settled && sig != lastHoverSig) {
            if (lastHoverSig != Integer.MIN_VALUE) {
                playUi(SoundEvents.UI_BUTTON_CLICK.value(), 1.9F, 0.12F);
                if (hoveredEntry >= 0) hoverSparks(focus, hoveredEntry);
            }
            lastHoverSig = sig;
        }

        int col = color(focus.stone());
        ambient(time, col);

        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1);

        // ambient: rotating dashed halo + orbiting motes (additive glow)
        float spin = (time % 20000) / 20000F * Mth.TWO_PI;
        additive(true);
        for (int d = 0; d < 48; d++) {
            float a0 = spin + d * Mth.TWO_PI / 48, a1 = a0 + Mth.TWO_PI / 48 * 0.55F;
            ring(g, 0, 0, OUTER_OUT + 10, OUTER_OUT + 12, a0, a1, withAlpha(col, (int) (110 * open)));
        }
        for (int m = 0; m < 10; m++) {
            float a0 = -spin * 1.7F + m * Mth.TWO_PI / 10;
            float r = OUTER_OUT + 20 + 4 * Mth.sin(time / 300F + m);
            float px = Mth.cos(a0 - Mth.HALF_PI) * r, py = Mth.sin(a0 - Mth.HALF_PI) * r;
            disc(g, px, py, 4.5F, withAlpha(col, (int) (40 * open)));
            disc(g, px, py, 1.6F, withAlpha(lighten(col, 0.4F), (int) (220 * open)));
        }
        additive(false);

        // inner ring: stones
        float seg = Mth.TWO_PI / slices.size();
        for (int i = 0; i < slices.size(); i++) {
            Slice s = slices.get(i);
            boolean hot = s.stone() == focusedStone;
            float grow = hot ? 5 + Mth.sin(time / 180F) * 1.2F : 0;
            float a0 = i * seg + 0.035F, a1 = (i + 1) * seg - 0.035F;
            ring(g, 0, 0, INNER_IN, INNER_OUT + grow, a0, a1, withAlpha(darken(color(s.stone()), hot ? 0.95F : 0.45F), (int) (225 * open)));
            ring(g, 0, 0, INNER_OUT + grow, INNER_OUT + grow + 2, a0, a1, withAlpha(color(s.stone()), (int) (255 * open)));
            if (hot) {
                additive(true);
                ring(g, 0, 0, INNER_OUT + grow + 2, INNER_OUT + grow + 7, a0, a1, withAlpha(color(s.stone()), (int) (60 * open)));
                additive(false);
            }
            float mid = (a0 + a1) / 2 - Mth.HALF_PI, r = (INNER_IN + INNER_OUT) / 2 + grow / 2;
            item(g, stoneItem(s.stone()), Mth.cos(mid) * r - 8, Mth.sin(mid) * r - 8);
        }

        // outer ring: powers of the focused stone; page changes slide the ring sideways while it fades
        for (int pg = 0; pg < focus.pages().size(); pg++) {
            float d = pg - pos;
            List<WheelPayloads.Entry> list = focus.pages().get(pg);
            if (Math.abs(d) >= 1F || list.isEmpty()) continue;
            g.pose().pushPose();
            g.pose().translate(d * PAGE_SLIDE, 0, 0);
            drawPowerRing(g, focus.stone(), list, pg, time, open * (1 - Math.abs(d)), col, settled && pg == page ? hoveredEntry : -1);
            g.pose().popPose();
        }

        // shockwaves + particles (additive, over the rings)
        drawWaves(g, time);
        stepAndDrawParticles(g, dt, open);

        // centre hub
        disc(g, 0, 0, INNER_IN - 4, withAlpha(0xFF0B0B12, (int) (230 * open)));
        ring(g, 0, 0, INNER_IN - 5, INNER_IN - 3, 0, Mth.TWO_PI, withAlpha(col, (int) ((170 + 70 * Mth.sin(time / 260F)) * open)));
        if (hubFlash >= 0 && time - hubFlash < 380) {
            float t = (time - hubFlash) / 380F, k = 1 - t * t;
            additive(true);
            disc(g, 0, 0, (INNER_IN - 4) * (0.6F + 0.5F * t), withAlpha(hubFlashColor, (int) (120 * k)));
            additive(false);
        }
        g.pose().popPose();

        if (closingAt < 0) {
            drawCentre(g, cx, cy, focus);
            if (focus.hasPage2()) drawPageDots(g, cx, cy, focus, scale, open, pos);
            drawInfoPanel(g, cx, cy, focus, scale, open, mouseX, mouseY);
            drawLevelBar(g, cx, cy, focus, scale, open);
        }
    }

    /** One page of the outer ring (the focused stone's powers) in wheel-local space; hov = hovered index on this page or -1. */
    private void drawPowerRing(GuiGraphics g, int stone, List<WheelPayloads.Entry> entries, int pg, long time, float open, int col, int hov) {
        float oseg = Mth.TWO_PI / entries.size();
        // outer ring: powers of the focused stone (dark backing keeps it readable over any scenery)
        ring(g, 0, 0, OUTER_IN - 3, OUTER_OUT + 3, 0, Mth.TWO_PI, withAlpha(0xFF07070C, (int) (170 * open)));
        for (int i = 0; i < entries.size(); i++) {
            WheelPayloads.Entry e = entries.get(i);
            boolean hot = i == hov;
            float punch = pulseAmount(time, stone, pg, i);      // 0..1 elastic punch from a recent click
            float shake = denyShake(time, stone, pg, i);         // small wobble when the power is unavailable
            float grow = (hot ? 8 : 0) + punch * 10;
            float a0 = i * oseg + 0.02F + shake, a1 = (i + 1) * oseg - 0.02F + shake;
            int base = e.unlocked() ? darken(col, hot ? 0.85F : 0.4F) : 0xFF2A2A30;
            if (punch > 0) base = mix(base, lighten(col, 0.75F), punch);
            ring(g, 0, 0, OUTER_IN, OUTER_OUT + grow, a0, a1, withAlpha(base, (int) (215 * open)));
            if (e.cooldownLeft() > 0) {
                float frac = Mth.clamp(e.cooldownLeft() / (float) e.cooldownTotal(), 0, 1);
                ring(g, 0, 0, OUTER_IN, OUTER_OUT + grow, a0, a0 + (a1 - a0) * frac, withAlpha(0xFF000000, (int) (150 * open)));
            }
            int edge = e.toggled() ? lighten(col, 0.6F) : hot ? 0xFFFFFFFF : withAlpha(col, 140);
            float ew = e.toggled() ? 3 : hot ? 2 : 1;
            ring(g, 0, 0, OUTER_OUT + grow, OUTER_OUT + grow + ew, a0, a1, withAlpha(edge, (int) (255 * open)));
            additive(true);
            if (hot && e.unlocked()) ring(g, 0, 0, OUTER_OUT + grow + ew, OUTER_OUT + grow + ew + 6, a0, a1, withAlpha(col, (int) (70 * open)));
            if (e.toggled()) {
                ring(g, 0, 0, OUTER_IN - 3, OUTER_IN - 1, a0, a1, withAlpha(lighten(col, 0.6F), (int) ((180 + 60 * Mth.sin(time / 150F)) * open)));
                ring(g, 0, 0, OUTER_OUT + grow + ew, OUTER_OUT + grow + ew + 5, a0, a1, withAlpha(col, (int) ((50 + 30 * Mth.sin(time / 150F)) * open)));
            }
            if (punch > 0) ring(g, 0, 0, OUTER_IN, OUTER_OUT + grow + 4, a0, a1, withAlpha(lighten(col, 0.5F), (int) (110 * punch)));
            additive(false);
            float mid = (a0 + a1) / 2 - Mth.HALF_PI, r = (OUTER_IN + OUTER_OUT) / 2 + grow / 2;
            float ix = Mth.cos(mid) * r - 8, iy = Mth.sin(mid) * r - 8;
            icon(g, e, ix, iy, e.unlocked());
            if (!e.unlocked()) g.drawString(font, "✖", (int) ix + 11, (int) iy + 9, 0xFFB0B0B0, true);
            if (isDual(e)) dualMarker(g, mid, OUTER_OUT + grow - 7, e.unlocked() ? lighten(col, 0.5F) : 0xFF6A6A70, open);
            StoneTree.Node sn = node(e);
            if (sn != null && sn.isPassiveToggle()) switchMarker(g, mid, OUTER_IN + 7, e.toggled(), col, open);
            int fav = e.stone() < 0 ? -1 : WheelActions.favoriteSlot(e.stone(), e.node());
            if (fav >= 0) {
                float fx = Mth.cos(mid) * (OUTER_OUT + grow - 9), fy = Mth.sin(mid) * (OUTER_OUT + grow - 9);
                disc(g, fx, fy, 5F, withAlpha(0xFF000000, (int) (190 * open)));
                g.drawCenteredString(font, String.valueOf(fav + 1), (int) fx, (int) fy - 4, withAlpha(0xFFFFD45A, (int) (255 * open)));
            }
        }
    }

    /**
     * Side panel for the hovered power: name, state, wrapped description and the usage lines with the right buttons.
     * Right of the wheel when there is room, else left, else a tooltip at the cursor.
     */
    private void drawInfoPanel(GuiGraphics g, int cx, int cy, Slice focus, float scale, float open, int mouseX, int mouseY) {
        if (hoveredEntry < 0 || hoveredEntry >= focus.entries().size()) return;
        WheelPayloads.Entry e = focus.entries().get(hoveredEntry);
        StoneTree.Node n = node(e);
        if (n == null) return;
        int col = color(focus.stone());
        final int W = 190;
        List<FormattedCharSequence> lines = new ArrayList<>();
        List<Integer> gaps = new ArrayList<>(); // line indexes that get a little space above
        lines.addAll(font.split(n.displayName().copy().withStyle(s -> s.withColor(col & 0xFFFFFF).withBold(true)), W));
        lines.addAll(font.split(kind(e), W));
        Component desc = n.displayDescription();
        if (!desc.getString().isBlank()) {
            gaps.add(lines.size());
            lines.addAll(font.split(desc.copy().withStyle(ChatFormatting.GRAY), W));
        }
        if (!e.unlocked()) {
            gaps.add(lines.size());
            lines.addAll(font.split(Component.translatable("infinity.wheel.state.locked").withStyle(ChatFormatting.DARK_GRAY), W));
        } else {
            gaps.add(lines.size());
            for (Component h : WheelActions.hints(n)) lines.addAll(font.split(h, W));
            if (statsOf(n) != null) lines.addAll(font.split(Component.translatableWithFallback("infinity.wheel.hint.stats", "[Shift] stats")
                    .withStyle(ChatFormatting.DARK_GRAY), W));
        }
        List<FormattedCharSequence> stats = hasShiftDown() && statsOf(n) != null ? statLines(n, W) : List.of();
        int h = lines.size() * 10 + gaps.size() * 4 + 8;
        float wheelR = (OUTER_OUT + 34) * scale;
        int x;
        if (cx + wheelR + 10 + W + 12 <= width) x = (int) (cx + wheelR + 10);
        else if (cx - wheelR - 10 - W - 12 >= 0) x = (int) (cx - wheelR - 10 - W - 12);
        else {
            List<FormattedCharSequence> all = new ArrayList<>(lines);
            all.addAll(stats);
            g.renderTooltip(font, all, mouseX, mouseY);
            return;
        }
        int y = Mth.clamp(cy - h / 2, 4, Math.max(4, height - h - 4));
        int a = (int) (230 * open);
        g.fill(x, y, x + W + 12, y + h, withAlpha(0xFF0A0A12, a));
        g.fill(x, y, x + 2, y + h, withAlpha(col, a));
        g.fill(x, y, x + W + 12, y + 1, withAlpha(col, a / 2));
        g.fill(x, y + h - 1, x + W + 12, y + h, withAlpha(col, a / 2));
        int ly = y + 5;
        for (int i = 0; i < lines.size(); i++) {
            if (gaps.contains(i)) ly += 4;
            g.drawString(font, lines.get(i), x + 7, ly, 0xFFFFFFFF, true);
            ly += 10;
        }
        if (!stats.isEmpty()) {
            // Shift: stats panel in the same style, stacked under the info panel (above it when the screen ends)
            int sh = stats.size() * 10 + 8;
            int sy = y + h + 4 + sh <= height - 4 ? y + h + 4 : Math.max(4, y - sh - 4);
            g.fill(x, sy, x + W + 12, sy + sh, withAlpha(0xFF0A0A12, a));
            g.fill(x, sy, x + 2, sy + sh, withAlpha(col, a));
            g.fill(x, sy, x + W + 12, sy + 1, withAlpha(col, a / 2));
            g.fill(x, sy + sh - 1, x + W + 12, sy + sh, withAlpha(col, a / 2));
            int ty = sy + 5;
            for (FormattedCharSequence l : stats) {
                g.drawString(font, l, x + 7, ty, 0xFFFFFFFF, true);
                ty += 10;
            }
        }
    }

    /** Stats of a public power (tier-2 nodes never carry any). */
    private static StoneTree.Stats statsOf(StoneTree.Node n) {
        return n.upper() ? null : n.stats();
    }

    private List<FormattedCharSequence> statLines(StoneTree.Node n, int width) {
        StoneTree.Stats s = statsOf(n);
        List<FormattedCharSequence> out = new ArrayList<>();
        if (s == null) return out;
        int col = color(n.stone().ordinal());
        out.addAll(font.split(Component.translatableWithFallback("infinity.wheel.stats.title", "Stats").withStyle(st -> st.withColor(col & 0xFFFFFF).withBold(true)), width));
        out.addAll(font.split(statRow("infinity.wheel.stats.damage", "Damage", statText(s.damage())), width));
        out.addAll(font.split(statRow("infinity.wheel.stats.range", "Range", statText(s.range())), width));
        out.addAll(font.split(statRow("infinity.wheel.stats.duration", "Duration", statText(s.duration())), width));
        out.addAll(font.split(statRow("infinity.wheel.stats.area", "Area", statText(s.area())), width));
        Component players = s.players() == null ? Component.literal("\u2014").withStyle(ChatFormatting.WHITE)
                : s.players() ? Component.translatableWithFallback("infinity.wheel.stats.yes", "Yes").withStyle(ChatFormatting.RED)
                : Component.translatableWithFallback("infinity.wheel.stats.no", "No").withStyle(ChatFormatting.GREEN);
        out.addAll(font.split(statRow("infinity.wheel.stats.players", "Hits players", players), width));
        out.addAll(font.split(Component.translatableWithFallback("infinity.wheel.stats.cooldown", "Cooldown: none").withStyle(ChatFormatting.DARK_GRAY), width));
        out.addAll(font.split(Component.translatableWithFallback("infinity.wheel.stats.cost", "Magicule cost: 1 per use").withStyle(ChatFormatting.DARK_GRAY), width));
        return out;
    }

    private static Component statRow(String key, String fallback, Component value) {
        return Component.translatableWithFallback(key, fallback).withStyle(ChatFormatting.GRAY)
                .append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY)).append(value);
    }

    /** Stat values are short literal texts; "@lang.key" translates. Missing = em dash. */
    private static Component statText(String v) {
        if (v == null || v.isBlank()) return Component.literal("\u2014").withStyle(ChatFormatting.WHITE);
        return (v.startsWith("@") ? Component.translatable(v.substring(1)) : Component.literal(v)).withStyle(ChatFormatting.WHITE);
    }

    private void drawCentre(GuiGraphics g, int cx, int cy, Slice focus) {
        Component title; Component line2; int color = color(focus.stone());
        if (hoveredEntry >= 0) {
            WheelPayloads.Entry e = focus.entries().get(hoveredEntry);
            title = name(e).copy().withStyle(e.unlocked() ? ChatFormatting.WHITE : ChatFormatting.GRAY);
            if (!e.unlocked()) line2 = Component.translatable("infinity.wheel.state.locked").withStyle(ChatFormatting.DARK_GRAY);
            else line2 = kind(e); // full description + buttons are in the side panel
        } else {
            title = Component.translatable("infinity.skill." + Stone.values()[focus.stone()].key + "_stone").withStyle(s -> s.withColor(color & 0xFFFFFF));
            line2 = hubHover && focus.hasPage2()
                    ? Component.translatableWithFallback("infinity.wheel.page.hint", "Scroll: switch page").withStyle(ChatFormatting.GRAY)
                    : Component.translatable("infinity.wheel.state.pick").withStyle(ChatFormatting.GRAY);
        }
        List<FormattedCharSequence> lines = font.split(title, 70);
        int y = cy - 6 - (lines.size() - 1) * 5;
        for (FormattedCharSequence l : lines) { g.drawCenteredString(font, l, cx, y, 0xFFFFFFFF); y += 10; }
        List<FormattedCharSequence> sub = font.split(line2, 100);
        g.pose().pushPose();
        g.pose().translate(cx, y + 1, 0);
        g.pose().scale(0.75F, 0.75F, 1);
        int sy = 0;
        for (FormattedCharSequence l : sub) { g.drawCenteredString(font, l, 0, sy, 0xFFFFFFFF); sy += 10; }
        g.pose().popPose();
    }

    /** "1 • 2" under the hub: the number of the page on show glows in the stone's colour (follows the slide). */
    private void drawPageDots(GuiGraphics g, int cx, int cy, Slice focus, float scale, float open, float pos) {
        int col = lighten(color(focus.stone()), 0.5F), dim = 0xFF6A6A72;
        int a = (int) (255 * open);
        int w1 = font.width("1 "), w2 = font.width("1 \u2022 "), total = font.width("1 \u2022 2");
        g.pose().pushPose();
        g.pose().translate(cx, cy + 29 * scale, 0);
        g.pose().scale(0.8F, 0.8F, 1);
        float x = -total / 2F;
        g.drawString(font, "1", (int) x, 0, withAlpha(mix(dim, col, 1 - pos), a), true);
        g.drawString(font, "\u2022", (int) x + w1, 0, withAlpha(dim, a), true);
        g.drawString(font, "2", (int) x + w2, 0, withAlpha(mix(dim, col, pos), a), true);
        g.pose().popPose();
    }

    private Component kind(WheelPayloads.Entry e) {
        if (e.stone() < 0) return Component.translatable(e.toggled() ? "infinity.wheel.state.on" : "infinity.wheel.state.off")
                .withStyle(e.toggled() ? ChatFormatting.GOLD : ChatFormatting.GRAY);
        StoneTree.Node n = node(e);
        if (n == null) return Component.empty();
        if (n.isPassiveToggle()) return Component.translatableWithFallback("infinity.wheel.kind.passive", "Passive")
                .withStyle(ChatFormatting.DARK_AQUA).append(Component.literal(" · ")).append(Component.translatable(
                        e.toggled() ? "infinity.wheel.state.on" : "infinity.wheel.state.off").withStyle(e.toggled() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
        boolean aim = com.infinitystones.tensura.wheel.AbilityMeta.needsAim(n);
        return switch (n.activation()) {
            case TOGGLE -> Component.translatable(e.toggled() ? "infinity.wheel.state.on" : "infinity.wheel.state.off")
                    .withStyle(e.toggled() ? ChatFormatting.GREEN : ChatFormatting.GRAY);
            case HOLD -> n.modeId().equals(WheelClientState.get().holding())
                    ? Component.translatableWithFallback("infinity.wheel.kind.channelling", "Channelling — click to stop").withStyle(ChatFormatting.GREEN)
                    : Component.translatableWithFallback("infinity.wheel.kind.channel", "Channel (on/off)").withStyle(ChatFormatting.AQUA);
            default -> aim ? Component.translatableWithFallback("infinity.wheel.kind.aim", "Aimed power").withStyle(ChatFormatting.YELLOW)
                    : Component.translatable("infinity.wheel.kind.press").withStyle(ChatFormatting.YELLOW);
        };
    }

    // ------------------------------------------------------------ input

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (closingAt >= 0) return true;
        if (button < 0 || button > 2) return super.mouseClicked(mouseX, mouseY, button);
        List<Slice> slices = slices();
        Slice focus = slices.stream().filter(s -> s.stone() == focusedStone).findFirst().orElse(null);
        double hdx = mouseX - width / 2D, hdy = mouseY - height / 2D;
        if (focus != null && button == 0 && Math.sqrt(hdx * hdx + hdy * hdy) < (INNER_IN - 4) * lastScale) {
            // centre hub → the stone's tree
            usedSomething = true;
            playUi(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.3F);
            if (minecraft != null) minecraft.setScreen(new StoneTreeScreen(Stone.values()[focus.stone()]));
            return true;
        }
        if (focus == null || hoveredEntry < 0 || hoveredEntry >= focus.entries().size()) return true;
        WheelPayloads.Entry e = focus.entries().get(hoveredEntry);
        long now = System.currentTimeMillis();
        String mid = e.stone() < 0 ? "" : Stone.values()[e.stone()].key + "." + e.node();
        boolean menuOk = e.unlocked() && button == 2 && DualModes.hasDestination(mid); // the menu also holds "bring it back"
        if (!menuOk && (!e.unlocked() || e.cooldownLeft() > 0)) {
            pulses.add(new Pulse(now, focus.stone(), page, hoveredEntry, true));
            burst(focus, hoveredEntry, 0xFF8A8A92, false, 10, 0.6F);
            playUi(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, 0.2F);
            return true;
        }
        int col = color(focus.stone());
        String modeId = e.stone() < 0 ? "" : Stone.values()[e.stone()].key + "." + e.node();
        if ("reality.will_construct".equals(modeId) && button != 0) {
            // Will: right = construct menu, middle = quick construct slots (left falls through: grip mode on/off)
            usedSomething = true;
            playUi(SoundEvents.AMETHYST_BLOCK_RESONATE, button == 1 ? 1.1F : 1.5F, 0.3F);
            if (minecraft != null) minecraft.setScreen(button == 1 ? new com.infinitystones.tensura.client.will.ConstructScreen()
                    : new com.infinitystones.tensura.client.will.QuickSlotsScreen());
            return true;
        }
        if (DualModes.hasDestination(modeId) && (button == 2 || DualModes.menuOnLeft(modeId))) {
            // middle click (scroll-wheel button): choose dimension + coordinates
            usedSomething = true;
            playUi(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9F, 0.3F);
            if (minecraft != null) minecraft.setScreen(new DestinationScreen(e.stone(), e.node(), DualModes.movesTarget(modeId), col));
            return true;
        }
        if (button == 2 && com.infinitystones.tensura.skill.ability.extra.client.ExtraClientApi.hasMenu(modeId)) {
            // middle click on menu powers (orders, laws, species, climates, saved heists): open the choice menu
            usedSomething = true;
            playUi(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.9F, 0.3F);
            com.infinitystones.tensura.skill.ability.extra.client.ExtraClientApi.requestMenu(e.stone(), e.node());
            return true;
        }
        if (button == 2) return true;
        boolean alt = button == 1 && isDual(e);
        usedSomething = true;
        WheelActions.Outcome out = WheelActions.activate(e.stone(), e.node(), button);
        playChoiceFx(focus, hoveredEntry, alt, now);
        if (out == WheelActions.Outcome.CLOSE) beginClose(); // aim mode / channel / one-shot: back to the world at once
        return true;
    }

    private void playChoiceFx(Slice focus, int entry, boolean alt, long now) {
        int col = color(focus.stone());
        int fx = alt ? lighten(col, 0.35F) : col;
        pulses.add(new Pulse(now, focus.stone(), page, entry, false));
        waves.add(new Wave(now, fx, OUTER_IN, OUTER_OUT + 70, 520));
        waves.add(new Wave(now + 70, lighten(fx, 0.6F), INNER_IN, OUTER_OUT + 30, 420));
        hubFlash = now; hubFlashColor = fx;
        burst(focus, entry, fx, alt, 54, 1F);
        playUi(SoundEvents.AMETHYST_BLOCK_RESONATE, alt ? 1.1F : 1.4F, 0.3F);
        playUi(SoundEvents.AMETHYST_CLUSTER_BREAK, alt ? 1.3F : 1.7F, 0.12F);
    }

    private void beginClose() { if (closingAt < 0) closingAt = System.currentTimeMillis() - CLOSE_MS / 2; }

    /** Key still held since the press that opened the wheel (ignore its key-repeat). */
    private boolean openKeyHeld = true;

    @Override public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (WheelKeys.WHEEL.matches(keyCode, scanCode)) { releasedOpenKey(); return true; }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    /** Releasing the key no longer closes the wheel; a quick tap repeats the last power / stops the channel. */
    private void releasedOpenKey() {
        if (!openKeyHeld) return;
        openKeyHeld = false;
        boolean tap = System.currentTimeMillis() - openedAt < 200;
        if (tap && !usedSomething && WheelActions.tap()) onClose();
    }

    @Override public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (WheelKeys.WHEEL.matches(keyCode, scanCode)) {
            if (!openKeyHeld) beginClose(); // second press closes the wheel
            return true;
        }
        if (keyCode >= org.lwjgl.glfw.GLFW.GLFW_KEY_1 && keyCode <= org.lwjgl.glfw.GLFW.GLFW_KEY_4 && closingAt < 0) {
            int slot = keyCode - org.lwjgl.glfw.GLFW.GLFW_KEY_1;
            Slice focus = slices().stream().filter(s -> s.stone() == focusedStone).findFirst().orElse(null);
            if (focus != null && hoveredEntry >= 0 && hoveredEntry < focus.entries().size()) {
                WheelPayloads.Entry e = focus.entries().get(hoveredEntry);
                StoneTree.Node n = node(e);
                if (n != null && !com.infinitystones.tensura.wheel.AbilityMeta.isSwitch(n)) {
                    WheelActions.setFavorite(slot, e.stone(), e.node(), hasShiftDown() && DualModes.isDual(n.modeId()));
                    playUi(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.5F + slot * 0.1F, 0.25F);
                    hubFlash = System.currentTimeMillis(); hubFlashColor = color(focus.stone());
                }
                return true;
            }
            usedSomething = true;
            if (WheelActions.useFavorite(slot) == WheelActions.Outcome.NOTHING) playUi(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, 0.2F);
            else onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // the wheel key may be rebound to a mouse button
        if (WheelKeys.WHEEL.matchesMouse(button)) { releasedOpenKey(); return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** Mouse wheel on a hovered variable power changes its level / mode (no modifier needed in the wheel). */
    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY == 0 || closingAt >= 0) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        Slice focus = slices().stream().filter(s -> s.stone() == focusedStone).findFirst().orElse(null);
        if (focus != null && Math.hypot(mouseX - width / 2D, mouseY - height / 2D) < (INNER_IN - 4) * lastScale) {
            // scroll over the hub: slide to the other page of this stone
            long now = System.currentTimeMillis();
            if (!focus.hasPage2() || !pageSettled(now)) return true;
            setPage(1 - page, now, true);
            int col = color(focus.stone());
            waves.add(new Wave(now, lighten(col, 0.4F), INNER_IN, OUTER_OUT + 12, 320));
            hubFlash = now; hubFlashColor = col;
            playUi(SoundEvents.AMETHYST_BLOCK_CHIME, 1.3F + 0.5F * page, 0.25F);
            return true;
        }
        if (focus == null || hoveredEntry < 0 || hoveredEntry >= focus.entries().size()) return true;
        WheelPayloads.Entry e = focus.entries().get(hoveredEntry);
        StoneTree.Node n = node(e);
        var spec = n == null ? null : com.infinitystones.tensura.wheel.AbilityMeta.setting(n, net.minecraft.client.Minecraft.getInstance().player);
        if (spec == null || !e.unlocked()) return true;
        int dir = scrollY > 0 ? 1 : -1;
        int cur = shownSetting(n, e, spec);
        int next = spec.clamp(cur + dir * Math.max(1, spec.step()));
        if (next == cur) { // at the end of the bar
            barBump = System.currentTimeMillis();
            playUi(SoundEvents.NOTE_BLOCK_BASS.value(), dir > 0 ? 0.9F : 0.6F, 0.2F);
            return true;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new WheelPayloads.Setting(e.stone(), e.node(), dir));
        predictedNode = n.modeId(); predictedValue = next; predictedAt = System.currentTimeMillis();
        barChanged(focus, spec, next, dir);
        hoverSparks(focus, hoveredEntry);
        return true;
    }

    // ------------------------------------------------------------ level bar (above the wheel)

    private String predictedNode = "";
    private int predictedValue;
    private long predictedAt, barFlash = -1, barBump = -1;
    private int barDir;
    private float barShown = -1; // animated fill (positions)
    private String barFor = "";
    private float barX, barY, barW;

    private int shownSetting(StoneTree.Node n, WheelPayloads.Entry e, com.infinitystones.tensura.wheel.AbilityMeta.SettingSpec spec) {
        if (n.modeId().equals(predictedNode) && System.currentTimeMillis() - predictedAt < 600 && e.setting() != predictedValue) return predictedValue;
        return spec.clamp(e.setting());
    }

    private void barChanged(Slice focus, com.infinitystones.tensura.wheel.AbilityMeta.SettingSpec spec, int value, int dir) {
        long now = System.currentTimeMillis();
        barFlash = now; barDir = dir;
        float frac = spec.positions() <= 1 ? 1 : spec.index(value) / (float) (spec.positions() - 1);
        int col = color(focus.stone());
        playUi(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F + 0.9F * frac, 0.35F);
        playUi(SoundEvents.BEACON_POWER_SELECT, 1.2F + 0.6F * frac, 0.12F);
        if (frac >= 1F && dir > 0) playUi(SoundEvents.PLAYER_LEVELUP, 1.6F, 0.12F);
        // sparks bursting from the new tip of the bar (wheel-local coordinates)
        if (barW > 0 && lastScale > 0) {
            float tipX = (barX + barW * frac - width / 2F) / lastScale, tipY = (barY + 3 - height / 2F) / lastScale;
            for (int i = 0; i < 26; i++) {
                float a = rng.nextFloat() * Mth.TWO_PI, sp = 40 + rng.nextFloat() * 140;
                spawn(tipX, tipY, Mth.cos(a) * sp + dir * 60, Mth.sin(a) * sp - 30, 0.35F + rng.nextFloat() * 0.4F,
                        0.8F + rng.nextFloat() * 1.2F, rng.nextInt(3) == 0 ? 0xFFFFFFFF : lighten(col, 0.4F), rng.nextInt(4) == 0);
            }
        }
        waves.add(new Wave(now, lighten(col, 0.5F), OUTER_OUT, OUTER_OUT + 40, 360));
    }

    /** Level / mode bar of the hovered variable power, drawn above the wheel, animated. */
    private void drawLevelBar(GuiGraphics g, int cx, int cy, Slice focus, float scale, float open) {
        if (hoveredEntry < 0 || hoveredEntry >= focus.entries().size()) { barFor = ""; return; }
        WheelPayloads.Entry e = focus.entries().get(hoveredEntry);
        StoneTree.Node n = node(e);
        var spec = n == null ? null : com.infinitystones.tensura.wheel.AbilityMeta.setting(n, net.minecraft.client.Minecraft.getInstance().player);
        if (spec == null) { barFor = ""; return; }
        long now = System.currentTimeMillis();
        int col = color(focus.stone());
        int value = shownSetting(n, e, spec), positions = spec.positions(), idx = spec.index(value);
        if (!barFor.equals(n.modeId())) { barFor = n.modeId(); barShown = idx; }
        barShown += (idx - barShown) * 0.25F;
        final int W = 200, H = 7;
        float bump = barBump >= 0 && now - barBump < 250 ? Mth.sin((now - barBump) / 250F * Mth.PI * 4) * 3 * (1 - (now - barBump) / 250F) : 0;
        int x = cx - W / 2 + (int) bump;
        int y = Math.max(16, (int) (cy - (OUTER_OUT + 34) * scale) - 16);
        barX = x; barY = y; barW = W;
        int a = (int) (235 * open);
        float flash = barFlash >= 0 && now - barFlash < 450 ? 1 - (now - barFlash) / 450F : 0;
        // label: "Intensity · Lethal (3/4)"
        String vk = spec.valueKey(value);
        Component label = Component.translatable(spec.label()).withStyle(s -> s.withColor(lighten(col, 0.5F) & 0xFFFFFF))
                .append(Component.literal("  ·  ").withStyle(ChatFormatting.DARK_GRAY))
                .append((vk != null ? Component.translatable(vk) : Component.literal(String.valueOf(value))).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal("  " + (idx + 1) + "/" + positions).withStyle(ChatFormatting.GRAY));
        g.pose().pushPose();
        float ls = 1F + 0.18F * flash;
        g.pose().translate(cx, y - 7, 0);
        g.pose().scale(ls, ls, 1);
        g.drawCenteredString(font, label, 0, -4, withAlpha(0xFFFFFFFF, a));
        g.pose().popPose();
        // frame
        g.fill(x - 3, y - 2, x + W + 3, y + H + 2, withAlpha(0xFF07070C, a));
        g.fill(x - 3, y - 2, x + W + 3, y - 1, withAlpha(col, a / 2));
        g.fill(x - 3, y + H + 1, x + W + 3, y + H + 2, withAlpha(col, a / 2));
        additive(true);
        if (positions <= 16) {
            float seg = W / (float) positions;
            for (int i = 0; i < positions; i++) {
                int x0 = (int) (x + i * seg + 1), x1 = (int) (x + (i + 1) * seg - 1);
                boolean lit = i <= Math.round(barShown);
                boolean tip = i == idx;
                int c = lit ? mix(darken(col, 0.7F), lighten(col, 0.5F), positions <= 1 ? 1 : i / (float) (positions - 1)) : 0xFF22222A;
                int grow = tip ? (int) (2 * flash) + 1 : 0;
                g.fill(x0, y - grow, x1, y + H + grow, withAlpha(c, lit ? a : a / 2));
                if (tip) g.fill(x0, y - grow, x1, y + H + grow, withAlpha(0xFFFFFFFF, (int) (160 * flash + 40 * (0.5F + 0.5F * Mth.sin(now / 150F)))));
            }
        } else {
            float fill = positions <= 1 ? 1 : barShown / (positions - 1);
            g.fill(x, y, x + (int) (W * fill), y + H, withAlpha(lighten(col, 0.3F), a));
            g.fill(x + (int) (W * fill) - 2, y - 1, x + (int) (W * fill) + 1, y + H + 1, withAlpha(0xFFFFFFFF, a));
        }
        // sweeping shine after a change
        if (flash > 0) {
            int sx = (int) (x + W * (barDir > 0 ? 1 - flash : flash));
            g.fill(sx - 10, y - 1, sx + 10, y + H + 1, withAlpha(lighten(col, 0.8F), (int) (120 * flash)));
        }
        additive(false);
        // scroll hint arrows
        g.drawString(font, "▼", x - 12, y - 1, withAlpha(idx > 0 ? lighten(col, 0.4F) : 0xFF444450, a), false);
        g.drawString(font, "▲", x + W + 5, y - 1, withAlpha(idx < positions - 1 ? lighten(col, 0.4F) : 0xFF444450, a), false);
    }

    /** The wheel never freezes the player: movement keys keep working while it is open. */
    @Override public void tick() {
        if (minecraft == null) return;
        long w = minecraft.getWindow().getWindow();
        var o = minecraft.options;
        for (net.minecraft.client.KeyMapping k : new net.minecraft.client.KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keySprint, o.keyShift}) {
            com.mojang.blaze3d.platform.InputConstants.Key key = k.getKey();
            if (key.getType() == com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM && !WheelKeys.WHEEL.getKey().equals(key))
                k.setDown(com.mojang.blaze3d.platform.InputConstants.isKeyDown(w, key.getValue()));
        }
        if (openKeyHeld && !WheelKeys.isPhysicallyDown(minecraft)) releasedOpenKey(); // release missed (focus change)
    }

    @Override public void removed() {
        if (minecraft != null) for (net.minecraft.client.KeyMapping k : new net.minecraft.client.KeyMapping[]{
                minecraft.options.keyUp, minecraft.options.keyDown, minecraft.options.keyLeft, minecraft.options.keyRight,
                minecraft.options.keyJump, minecraft.options.keySprint, minecraft.options.keyShift}) k.setDown(false);
        super.removed();
    }

    // ------------------------------------------------------------ effects

    private float pulseAmount(long time, int stone, int pg, int entry) {
        float best = 0;
        for (Pulse p : pulses) {
            if (p.denied() || p.stone() != stone || p.page() != pg || p.entry() != entry) continue;
            float t = (time - p.start()) / 420F;
            if (t < 0 || t > 1) continue;
            // fast punch out, elastic settle
            float v = t < 0.18F ? t / 0.18F : (float) (Math.exp(-5.5 * (t - 0.18)) * Math.cos((t - 0.18) * 11));
            best = Math.max(best, Math.max(0, v));
        }
        return best;
    }

    private float denyShake(long time, int stone, int pg, int entry) {
        for (Pulse p : pulses) {
            if (!p.denied() || p.stone() != stone || p.page() != pg || p.entry() != entry) continue;
            float t = (time - p.start()) / 260F;
            if (t >= 0 && t <= 1) return 0.035F * (1 - t) * Mth.sin(t * 40);
        }
        return 0;
    }

    private void burst(Slice focus, int entry, int col, boolean alt, int count, float power) {
        float oseg = Mth.TWO_PI / focus.entries().size();
        float a0 = entry * oseg, a1 = (entry + 1) * oseg;
        float dir = alt ? -1 : 1;                              // right click swirls the other way
        for (int i = 0; i < count; i++) {
            float a = a0 + rng.nextFloat() * (a1 - a0) - Mth.HALF_PI;
            float r = OUTER_IN + rng.nextFloat() * (OUTER_OUT - OUTER_IN);
            float x = Mth.cos(a) * r, y = Mth.sin(a) * r;
            boolean inward = i % 7 == 0;                       // a few motes rush into the hub: energy drawn in
            float radial = (inward ? -(90 + rng.nextFloat() * 70) : 70 + rng.nextFloat() * 190) * power;
            float tangential = dir * (rng.nextFloat() * 90 - 20) * power;
            float vx = Mth.cos(a) * radial - Mth.sin(a) * tangential, vy = Mth.sin(a) * radial + Mth.cos(a) * tangential;
            int c = switch (rng.nextInt(5)) { case 0 -> 0xFFFFFFFF; case 1, 2 -> lighten(col, 0.45F); default -> col; };
            spawn(x, y, vx, vy, 0.45F + rng.nextFloat() * 0.5F, 1F + rng.nextFloat() * 1.7F, c, rng.nextInt(5) == 0);
        }
    }

    private void hoverSparks(Slice focus, int entry) {
        float oseg = Mth.TWO_PI / focus.entries().size();
        int col = color(focus.stone());
        for (int i = 0; i < 6; i++) {
            float a = entry * oseg + rng.nextFloat() * oseg - Mth.HALF_PI, r = OUTER_OUT + 6;
            float sp = 25 + rng.nextFloat() * 45;
            spawn(Mth.cos(a) * r, Mth.sin(a) * r, Mth.cos(a) * sp, Mth.sin(a) * sp, 0.3F + rng.nextFloat() * 0.25F,
                    0.8F + rng.nextFloat() * 0.8F, lighten(col, 0.4F), false);
        }
    }

    /** Slow embers drifting off the outer ring so the wheel feels alive. */
    private void ambient(long time, int col) {
        if (closingAt >= 0 || time - lastAmbient < 70) return;
        lastAmbient = time;
        float a = rng.nextFloat() * Mth.TWO_PI, r = OUTER_OUT + 4 + rng.nextFloat() * 8;
        float sp = 8 + rng.nextFloat() * 16;
        spawn(Mth.cos(a) * r, Mth.sin(a) * r, Mth.cos(a) * sp, Mth.sin(a) * sp - 6, 0.9F + rng.nextFloat() * 0.8F,
                0.7F + rng.nextFloat() * 0.7F, rng.nextBoolean() ? col : lighten(col, 0.5F), false);
    }

    private void spawn(float x, float y, float vx, float vy, float life, float size, int color, boolean star) {
        if (particles.size() > 260) return;
        Particle p = new Particle();
        p.x = x; p.y = y; p.vx = vx; p.vy = vy; p.life = p.max = life; p.size = size; p.color = color; p.star = star;
        p.spin = rng.nextFloat() * Mth.TWO_PI;
        particles.add(p);
    }

    private void stepAndDrawParticles(GuiGraphics g, float dt, float open) {
        float drag = (float) Math.pow(0.09, dt);
        float alpha = closingAt >= 0 ? 1 : open;
        additive(true);
        for (Iterator<Particle> it = particles.iterator(); it.hasNext(); ) {
            Particle p = it.next();
            p.life -= dt;
            if (p.life <= 0) { it.remove(); continue; }
            p.x += p.vx * dt; p.y += p.vy * dt;
            p.vx *= drag; p.vy *= drag;
            p.spin += dt * 6;
            float k = p.life / p.max;                       // 1 → 0
            float fade = Mth.clamp((k > 0.75F ? (1 - k) / 0.25F : k / 0.75F) * 1.6F, 0, 1) * alpha;
            float s = p.size * (0.55F + 0.45F * k);
            disc(g, p.x, p.y, s * 3.2F, withAlpha(p.color, (int) (45 * fade)));
            disc(g, p.x, p.y, s, withAlpha(p.color, (int) (235 * fade)));
            if (p.star) {
                float len = s * 4.5F;
                bar(g, p.x, p.y, len, 0.7F, p.spin, withAlpha(p.color, (int) (200 * fade)));
                bar(g, p.x, p.y, len, 0.7F, p.spin + Mth.HALF_PI, withAlpha(p.color, (int) (200 * fade)));
            }
        }
        additive(false);
    }

    private void drawWaves(GuiGraphics g, long time) {
        additive(true);
        for (Iterator<Wave> it = waves.iterator(); it.hasNext(); ) {
            Wave w = it.next();
            float t = (time - w.start()) / (float) w.ms();
            if (t > 1) { it.remove(); continue; }
            if (t < 0) continue;
            float e = 1 - (1 - t) * (1 - t) * (1 - t);   // ease-out cubic
            float r = w.from() + (w.to() - w.from()) * e;
            float thick = 1.5F + 7F * (1 - t);
            ring(g, 0, 0, r - thick, r, 0, Mth.TWO_PI, withAlpha(w.color(), (int) (150 * (1 - t))));
            ring(g, 0, 0, r - thick * 2.5F, r - thick, 0, Mth.TWO_PI, withAlpha(w.color(), (int) (45 * (1 - t))));
        }
        pulses.removeIf(p -> time - p.start() > 600);
        additive(false);
    }

    /** Passive switch marker near the inner edge: hollow ring = off, lit dot with glow = on. */
    private static void switchMarker(GuiGraphics g, float mid, float r, boolean on, int col, float open) {
        float px = Mth.cos(mid) * r, py = Mth.sin(mid) * r;
        disc(g, px, py, 4.2F, withAlpha(0xFF000000, (int) (170 * open)));
        ring(g, px, py, 2.6F, 3.6F, 0, Mth.TWO_PI, withAlpha(on ? lighten(col, 0.6F) : 0xFF8A8A92, (int) (255 * open)));
        if (on) {
            disc(g, px, py, 1.9F, withAlpha(lighten(col, 0.7F), (int) (255 * open)));
            additive(true);
            disc(g, px, py, 6F, withAlpha(col, (int) (70 * open)));
            additive(false);
        }
    }

    /** Two small dots on the outer edge: this power has a left-click and a right-click variant. */
    private static void dualMarker(GuiGraphics g, float mid, float r, int col, float open) {
        float px = Mth.cos(mid) * r, py = Mth.sin(mid) * r;
        float tx = -Mth.sin(mid) * 5F, ty = Mth.cos(mid) * 5F;
        disc(g, px - tx, py - ty, 3.4F, withAlpha(0xFF000000, (int) (160 * open)));
        disc(g, px + tx, py + ty, 3.4F, withAlpha(0xFF000000, (int) (160 * open)));
        disc(g, px - tx, py - ty, 2.5F, withAlpha(col, (int) (255 * open)));
        disc(g, px + tx, py + ty, 2.5F, withAlpha(0xFFFFFFFF, (int) (230 * open)));
    }

    // ------------------------------------------------------------ helpers

    /** Has extra inputs (right click and/or middle click) — shows the two-dot marker and the input hint. */
    private static boolean isDual(WheelPayloads.Entry e) {
        if (e.stone() < 0) return false;
        String id = Stone.values()[e.stone()].key + "." + e.node();
        return DualModes.isDual(id) || DualModes.hasDestination(id);
    }

    private static StoneTree.Node node(WheelPayloads.Entry e) {
        return e.stone() < 0 ? null : StoneTree.of(Stone.values()[e.stone()]).nodes.get(e.node());
    }

    private static Component name(WheelPayloads.Entry e) {
        if (e.stone() < 0) return Component.translatable("infinity.skill." + WheelService.IMMORTALITY);
        StoneTree.Node n = node(e);
        return n == null ? Component.literal(e.node()) : n.displayName();
    }

    private void icon(GuiGraphics g, WheelPayloads.Entry e, float x, float y, boolean lit) {
        String icon = e.stone() < 0 ? "infinity:soul_stone" : node(e) == null ? null : node(e).icon();
        if (!lit) RenderSystem.setShaderColor(0.45F, 0.45F, 0.5F, 1F);
        if (icon != null && icon.contains("textures/")) {
            g.blit(ResourceLocation.parse(icon), (int) x, (int) y, 0, 0, 16, 16, 16, 16);
        } else {
            ItemStack stack = icon == null ? stoneItem(e.stone() < 0 ? Stone.SOUL.ordinal() : e.stone())
                    : new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(icon)));
            item(g, stack, x, y);
        }
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    private static void item(GuiGraphics g, ItemStack stack, float x, float y) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    private static ItemStack stoneItem(int stone) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("infinity", Stone.values()[stone].key + "_stone")));
    }

    static int color(int stone) { return Stone.values()[stone].color(); }

    private float openProgress() {
        long now = System.currentTimeMillis();
        if (closingAt >= 0) return Mth.clamp(1 - (now - closingAt) / (float) CLOSE_MS, 0F, 1F);
        return Mth.clamp((now - openedAt) / 180F, 0F, 1F);
    }
    private static float easeOutBack(float t) { float c1 = 1.70158F, c3 = c1 + 1; return 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2); }
    private static int argb(int a, int r, int g, int b) { return (Mth.clamp(a, 0, 255) << 24) | (r << 16) | (g << 8) | b; }
    private static int withAlpha(int c, int a) { return (Mth.clamp(a, 0, 255) << 24) | (c & 0xFFFFFF); }
    private static int darken(int c, float f) { return 0xFF000000 | ((int) (((c >> 16) & 255) * f) << 16) | ((int) (((c >> 8) & 255) * f) << 8) | (int) ((c & 255) * f); }
    private static int lighten(int c, float f) {
        int r = (c >> 16) & 255, gg = (c >> 8) & 255, b = c & 255;
        return 0xFF000000 | ((int) (r + (255 - r) * f) << 16) | ((int) (gg + (255 - gg) * f) << 8) | (int) (b + (255 - b) * f);
    }
    private static int mix(int a, int b, float t) {
        t = Mth.clamp(t, 0, 1);
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255), gg = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255), bl = (int) Mth.lerp(t, a & 255, b & 255);
        return 0xFF000000 | (r << 16) | (gg << 8) | bl;
    }

    private void playUi(net.minecraft.sounds.SoundEvent sound, float pitch, float volume) {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    // ------------------------------------------------------------ primitives

    private static boolean additiveBlend;

    /** Additive blending makes overlapping effects glow instead of darkening. */
    private static void additive(boolean on) { additiveBlend = on; }

    /** Filled annulus sector from a0 to a1 (radians, 0 = up, clockwise). */
    public static void ring(GuiGraphics g, float cx, float cy, float rIn, float rOut, float a0, float a1, int argb) {
        if ((argb >>> 24) == 0 || a1 <= a0 || rOut <= rIn) return;
        Matrix4f m = g.pose().last().pose();
        int steps = Math.max(2, (int) Math.ceil((a1 - a0) / 0.06F));
        if (rOut < 12) steps = Math.min(steps, 20);
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < steps; i++) {
            float t0 = a0 + (a1 - a0) * i / steps - Mth.HALF_PI, t1 = a0 + (a1 - a0) * (i + 1) / steps - Mth.HALF_PI;
            float c0 = Mth.cos(t0), s0 = Mth.sin(t0), c1 = Mth.cos(t1), s1 = Mth.sin(t1);
            bb.addVertex(m, cx + c0 * rIn, cy + s0 * rIn, 0).setColor(argb);
            bb.addVertex(m, cx + c0 * rOut, cy + s0 * rOut, 0).setColor(argb);
            bb.addVertex(m, cx + c1 * rOut, cy + s1 * rOut, 0).setColor(argb);
            bb.addVertex(m, cx + c0 * rIn, cy + s0 * rIn, 0).setColor(argb);
            bb.addVertex(m, cx + c1 * rOut, cy + s1 * rOut, 0).setColor(argb);
            bb.addVertex(m, cx + c1 * rIn, cy + s1 * rIn, 0).setColor(argb);
        }
        draw(bb);
    }

    /** Thin rotated bar centred on (cx, cy) with transparent tips — used for the star glints. */
    private static void bar(GuiGraphics g, float cx, float cy, float len, float thick, float angle, int argb) {
        Matrix4f m = g.pose().last().pose();
        float ux = Mth.cos(angle) * len, uy = Mth.sin(angle) * len, vx = -Mth.sin(angle) * thick, vy = Mth.cos(angle) * thick;
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        int tip = argb & 0x00FFFFFF;
        bb.addVertex(m, cx - ux, cy - uy, 0).setColor(tip);
        bb.addVertex(m, cx + vx, cy + vy, 0).setColor(argb);
        bb.addVertex(m, cx - vx, cy - vy, 0).setColor(argb);
        bb.addVertex(m, cx + ux, cy + uy, 0).setColor(tip);
        bb.addVertex(m, cx - vx, cy - vy, 0).setColor(argb);
        bb.addVertex(m, cx + vx, cy + vy, 0).setColor(argb);
        draw(bb);
    }

    private static void draw(BufferBuilder bb) {
        RenderSystem.enableBlend();
        if (additiveBlend) RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        else RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(bb.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
    }

    public static void disc(GuiGraphics g, float cx, float cy, float r, int argb) { ring(g, cx, cy, 0, r, 0, Mth.TWO_PI, argb); }
}
