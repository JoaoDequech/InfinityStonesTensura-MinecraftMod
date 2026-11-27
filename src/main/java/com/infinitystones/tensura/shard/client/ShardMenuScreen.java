package com.infinitystones.tensura.shard.client;

import com.infinitystones.tensura.registry.ModItems;
import com.infinitystones.tensura.shard.ShardClientState;
import com.infinitystones.tensura.shard.ShardNet;
import com.infinitystones.tensura.shard.ShardRules;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneProgress;
import com.infinitystones.tensura.skill.StoneTier;
import com.infinitystones.tensura.skill.StoneTree;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * "Borrowed Shard" menu. Tab 1: the six stones orbit the gauntlet; pick one and its unlocked powers open as a
 * constellation around it; choose one or more, set the duration (slider or "no limit") and create. Tab 2: the shards
 * I have active, with their remaining time and a delete button.
 */
public final class ShardMenuScreen extends Screen {
    private static final int[] STEPS = {60, 120, 180, 300, 600, 900, 1200, 1800, 2700, 3600, 5400, 7200};
    private static final int PANEL_W = 190;
    private static final long STAGE_MS = 620;

    private final RandomSource rng = RandomSource.create();
    private final long openedAt = System.currentTimeMillis();

    // ---- stars (background)
    private final float[] starX = new float[90], starY = new float[90], starP = new float[90], starS = new float[90];

    // ---- state
    private int tab; private long tabAt = -10_000; private float tabFrom;
    private int selStone = -1;                       // chosen stone ordinal, -1 = orbit view
    private long stageAt = -10_000; private boolean stageForward;
    private float selFromX, selFromY;
    private final Set<String> selected = new LinkedHashSet<>();
    private List<StoneTree.Node> nodes = List.of();
    private float slider = 0.35F;
    private boolean infinite; private float infAnim;
    private boolean dragging;
    private long createdAt = -1;
    private float listScroll;
    private UUID armed; private long armedAt;
    private final Map<UUID, Long> deleting = new HashMap<>();
    private long hubFlash = -10_000; private int hubFlashColor = 0xFFFFFF;
    private String denyReason = "";

    // ---- per-frame layout (filled while drawing, read by input)
    private float cx, cy, stageScale;
    private final float[] orbX = new float[6], orbY = new float[6], orbR = new float[6];
    private float[] nodeX = new float[0], nodeY = new float[0];
    private int hoverStone = -1, hoverNode = -1;
    private float panelX, panelY, panelH;
    private float trackX, trackY, trackW, infX, infY, createX, createY, createW, createH;
    private float backX, backY;
    private float rowTop;

    public ShardMenuScreen() {
        super(Component.translatable("infinity.shard.title"));
        for (int i = 0; i < starX.length; i++) {
            starX[i] = rng.nextFloat(); starY[i] = rng.nextFloat(); starP[i] = rng.nextFloat() * 6.28F; starS[i] = 0.4F + rng.nextFloat();
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override protected void init() {
        PacketDistributor.sendToServer(new ShardNet.Request());
        play(SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 0.35F);
    }

    // ------------------------------------------------------------ helpers

    private void play(SoundEvent s, float pitch, float vol) {
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, vol));
    }

    private static ItemStack stoneItem(Stone s) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("infinity", s.key + "_stone")));
    }

    private static int rgb(Stone s) { return s.color() & 0xFFFFFF; }

    private float stageP(long now) {
        float t = Mth.clamp((now - stageAt) / (float) STAGE_MS, 0, 1);
        return stageForward ? t : 1 - t;
    }

    private static String fmt(long seconds) {
        long h = seconds / 3600, m = seconds % 3600 / 60, s = seconds % 60;
        if (h > 0) return h + " h" + (m > 0 ? " " + m + " min" : "");
        if (m > 0) return m + " min" + (s > 0 ? " " + s + " s" : "");
        return s + " s";
    }

    private int seconds() { return STEPS[Mth.clamp(Math.round(slider * (STEPS.length - 1)), 0, STEPS.length - 1)]; }

    private void item(GuiGraphics g, ItemStack st, float x, float y, float scale, float alpha) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        RenderSystem.setShaderColor(1F, 1F, 1F, Mth.clamp(alpha, 0, 1));
        g.renderItem(st, -8, -8);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        g.pose().popPose();
    }

    private void text(GuiGraphics g, Component c, float x, float y, float scale, int color, boolean centered) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        int w = font.width(c);
        g.drawString(font, c, centered ? -w / 2 : 0, 0, color, true);
        g.pose().popPose();
    }

    private List<StoneTree.Node> powersFor(Stone st) {
        List<StoneTree.Node> out = new ArrayList<>();
        var p = minecraft == null ? null : minecraft.player;
        if (p == null) return out;
        for (StoneTree.Node n : ShardRules.shardableNodes(st))
            if (!StoneTier.hidden(p, n) && StoneProgress.isUnlocked(p, st, n.id())) out.add(n);
        return out;
    }

    private boolean owns(Stone s) { return minecraft != null && minecraft.player != null && s.ownedBy(minecraft.player); }

    private void selectStone(int ordinal, float fromX, float fromY) {
        selStone = ordinal;
        nodes = powersFor(Stone.values()[ordinal]);
        selected.clear();
        stageForward = true;
        stageAt = System.currentTimeMillis();
        selFromX = fromX; selFromY = fromY;
        hubFlash = stageAt; hubFlashColor = rgb(Stone.values()[ordinal]);
        play(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.1F, 0.4F);
        play(SoundEvents.AMETHYST_CLUSTER_BREAK, 1.6F, 0.15F);
    }

    private void back() {
        if (selStone < 0) return;
        stageForward = false;
        stageAt = System.currentTimeMillis();
        play(SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 0.3F);
    }

    // ------------------------------------------------------------ render

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        long now = System.currentTimeMillis();
        float open = ShardGfx.easeOutCubic((now - openedAt) / 350F);
        g.fillGradient(0, 0, width, height, ShardGfx.alpha(0x05060C, 0.93F * open), ShardGfx.alpha(0x120A22, 0.95F * open));
        float t = (now % 600_000) / 1000F;
        Stone tint = selStone >= 0 ? Stone.values()[selStone] : hoverStone >= 0 ? Stone.values()[hoverStone] : null;
        ShardGfx.additive(true);
        ShardGfx.glow(g, width * 0.5F, height * 0.5F, Math.max(width, height) * 0.55F, tint == null ? 0x6A5ACD : rgb(tint), 0.10F * open);
        for (int i = 0; i < starX.length; i++) {
            float tw = 0.5F + 0.5F * Mth.sin(t * starS[i] * 1.6F + starP[i]);
            float x = (starX[i] * width + t * 3F * starS[i]) % width, y = starY[i] * height;
            ShardGfx.glint(g, x, y, 1.5F + 2.2F * tw * starS[i], 0, 0xFFFFFF, 0.18F + 0.35F * tw);
        }
        ShardGfx.additive(false);
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        long now = System.currentTimeMillis();
        float t = (now % 600_000) / 1000F;
        renderBackground(g, mx, my, pt);
        float open = ShardGfx.easeOutCubic((now - openedAt) / 450F);
        float closing = createdAt < 0 ? 0 : Mth.clamp((now - createdAt) / 420F, 0, 1);
        if (createdAt >= 0 && closing >= 1F) { onClose(); return; }
        if (!nodesOk()) return;

        float sp = stageP(now);
        if (selStone >= 0 && !stageForward && sp <= 0F) { selStone = -1; selected.clear(); }
        panelX = width - PANEL_W - 10 + (1 - ShardGfx.easeOutCubic(sp)) * (PANEL_W + 30);
        panelY = 40; panelH = height - 70;
        boolean panelOn = tab == 0 && selStone >= 0 && sp > 0.02F;
        cx = tab == 0 && selStone >= 0 ? (width - (PANEL_W + 20) * ShardGfx.easeInOut(sp)) / 2F : width / 2F;
        cy = height / 2F + 8;
        stageScale = Mth.clamp(Math.min(cx - 12, cy - 34) / 200F, 0.5F, 1F) * (0.92F + 0.08F * open) * (1 + closing * 0.3F);

        drawTabs(g, mx, my, now, open);
        hoverStone = -1; hoverNode = -1;
        List<Component> tip = null;
        if (tab == 0) {
            tip = drawCreateTab(g, mx, my, now, t, sp, open);
            if (panelOn) drawPanel(g, mx, my, now, t, sp);
        } else drawMineTab(g, mx, my, now, t, open);

        if (closing > 0) {
            ShardGfx.additive(true);
            ShardGfx.glow(g, cx, cy, 220 * closing, hubFlashColor, 0.55F * (1 - closing));
            ShardGfx.additive(false);
            g.fill(0, 0, width, height, ShardGfx.alpha(0xFFFFFF, 0.25F * (1 - closing) * (closing < 0.2F ? closing / 0.2F : 1)));
        }
        if (tip != null && !tip.isEmpty()) g.renderComponentTooltip(font, tip, mx, my);
    }

    private boolean nodesOk() { return minecraft != null && minecraft.player != null; }

    // ---- tabs

    private void drawTabs(GuiGraphics g, int mx, int my, long now, float open) {
        Component a = Component.translatable("infinity.shard.tab.create"), b = Component.translatable("infinity.shard.tab.mine");
        float wa = font.width(a) * 1.1F, wb = font.width(b) * 1.1F, gap = 34;
        float total = wa + wb + gap, x0 = width / 2F - total / 2F;
        float ax = x0 + wa / 2F, bx = x0 + wa + gap + wb / 2F;
        text(g, Component.translatable("infinity.shard.title").withStyle(ChatFormatting.BOLD), width / 2F, 8, 1.15F, ShardGfx.alpha(0xFFE9A8, open), true);
        float k = ShardGfx.easeOutBack((now - tabAt) / 380F);
        float from = tabFrom == 0 ? ax : bx, to = tab == 0 ? ax : bx;
        float ux = Mth.lerp(Mth.clamp(k, 0, 1.15F), from, to), uw = Mth.lerp(Mth.clamp(k, 0, 1), tabFrom == 0 ? wa : wb, tab == 0 ? wa : wb);
        boolean ha = my > 20 && my < 36 && Math.abs(mx - ax) < wa / 2 + 6, hb = my > 20 && my < 36 && Math.abs(mx - bx) < wb / 2 + 6;
        text(g, a, ax, 24, 1.1F, tab == 0 ? 0xFFFFFFFF : ha ? 0xFFDDDDDD : 0xFF8A8AA0, true);
        text(g, b, bx, 24, 1.1F, tab == 1 ? 0xFFFFFFFF : hb ? 0xFFDDDDDD : 0xFF8A8AA0, true);
        int col = selStone >= 0 && tab == 0 ? rgb(Stone.values()[selStone]) : 0xFFE9A8;
        ShardGfx.additive(true);
        ShardGfx.gradientH(g, ux - uw / 2 - 8, 35, 8, 2, ShardGfx.alpha(col, 0), ShardGfx.alpha(col, 0.9F));
        g.fill((int) (ux - uw / 2), 35, (int) (ux + uw / 2), 37, ShardGfx.alpha(col, 0.9F));
        ShardGfx.gradientH(g, ux + uw / 2, 35, 8, 2, ShardGfx.alpha(col, 0.9F), ShardGfx.alpha(col, 0));
        ShardGfx.additive(false);
    }

    private void switchTab(int to) {
        if (to == tab) return;
        tabFrom = tab; tab = to; tabAt = System.currentTimeMillis();
        play(SoundEvents.AMETHYST_BLOCK_CHIME, to == 1 ? 1.3F : 1.0F, 0.3F);
        if (to == 1) PacketDistributor.sendToServer(new ShardNet.Request());
    }

    // ---- create tab

    private List<Component> drawCreateTab(GuiGraphics g, int mx, int my, long now, float t, float sp, float open) {
        List<Component> tip = null;
        Stone[] stones = Stone.values();
        float R = 128 * stageScale;
        // hub: the gauntlet
        float hubPulse = 0.5F + 0.5F * Mth.sin(t * 2.2F);
        float flash = Mth.clamp(1 - (now - hubFlash) / 700F, 0, 1);
        ShardGfx.additive(true);
        float hubFade = 1 - sp * 0.85F;
        ShardGfx.glow(g, cx, cy, (46 + 10 * hubPulse + 40 * flash) * stageScale, 0xFFD070, (0.22F + 0.12F * hubPulse + 0.5F * flash) * hubFade * open);
        ShardGfx.circle(g, cx, cy, 34 * stageScale, 1.2F, ShardGfx.alpha(0xFFD070, 0.35F * hubFade));
        for (int i = 0; i < 6; i++) {
            float a = t * 0.9F + i * Mth.TWO_PI / 6;
            boolean owned = owns(stones[i]);
            ShardGfx.disc(g, cx + Mth.cos(a) * 34 * stageScale, cy + Mth.sin(a) * 34 * stageScale, 1.8F, ShardGfx.alpha(rgb(stones[i]), (owned ? 0.95F : 0.2F) * hubFade));
        }
        ShardGfx.additive(false);
        item(g, new ItemStack(com.infinitystones.tensura.skill.GauntletStones.gauntletItem()), cx, cy, 2.4F * stageScale * (1 + 0.04F * flash), hubFade * open);

        // orbit ring
        ShardGfx.additive(true);
        for (int k = 0; k < 2; k++) ShardGfx.circle(g, cx, cy, R * (1 + k * 0.012F), 0.8F, ShardGfx.alpha(0x9C8CFF, (k == 0 ? 0.13F : 0.05F) * (1 - sp) * open));
        ShardGfx.additive(false);

        // orbiting stones, back to front
        Integer[] order = {0, 1, 2, 3, 4, 5};
        float[] depth = new float[6];
        for (int i = 0; i < 6; i++) {
            float a = t * 0.32F + i * Mth.TWO_PI / 6;
            depth[i] = Mth.sin(a);
            float x = cx + Mth.cos(a) * R, y = cy + Mth.sin(a) * R * 0.4F;
            if (sp > 0 && i == selStone) {
                float e = ShardGfx.easeOutBack(sp);
                x = Mth.lerp(e, selFromX, cx); y = Mth.lerp(e, selFromY, cy);
            } else if (sp > 0) { x += (x - cx) * sp * 0.5F; y += (y - cy) * sp * 0.5F; }
            orbX[i] = x; orbY[i] = y;
            orbR[i] = (17 + 6 * depth[i]) * stageScale;
        }
        java.util.Arrays.sort(order, (p, q) -> Float.compare(depth[p], depth[q]));
        boolean orbitInput = selStone < 0 || sp < 0.5F;
        for (int i : order) {
            Stone st = stones[i];
            boolean owned = owns(st);
            boolean chosen = i == selStone;
            float fade = chosen ? 1F : (1 - ShardGfx.easeInOut(sp)) * (0.55F + 0.3F * depth[i] + 0.15F);
            boolean hover = orbitInput && selStone < 0 && Math.hypot(mx - orbX[i], my - orbY[i]) < orbR[i] + 4;
            if (hover) { hoverStone = i; if (!owned) tip = List.of(Component.translatable("infinity.shard.no_stone").withStyle(ChatFormatting.RED)); }
            float sc = (1.6F + 0.45F * depth[i]) * stageScale * (hover ? 1.18F : 1F) * (chosen ? 1F + 1.4F * ShardGfx.easeOutBack(sp) : 1F);
            int col = rgb(st);
            ShardGfx.additive(true);
            float gl = (owned ? 0.34F : 0.08F) + (hover ? 0.28F : 0F) + 0.06F * Mth.sin(t * 3 + i);
            ShardGfx.glow(g, orbX[i], orbY[i], sc * 17F, col, gl * fade * open);
            if (chosen) ShardGfx.glow(g, orbX[i], orbY[i], sc * 22F, col, 0.3F * sp);
            ShardGfx.additive(false);
            if (!owned) RenderSystem.setShaderColor(0.35F, 0.35F, 0.4F, 1F);
            item(g, stoneItem(st), orbX[i], orbY[i], sc, fade * open);
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
            if (selStone < 0 || (sp < 0.3F && !chosen)) {
                Component name = Component.translatable("infinity.skill." + st.key + "_stone");
                float la = (hover ? 1F : 0.55F + 0.25F * depth[i]) * fade * open * (1 - sp * 3F);
                if (la > 0.05F) text(g, name, orbX[i], orbY[i] + sc * 10F + 3, 0.8F, ShardGfx.alpha(owned ? col : 0x888890, la) | 0, true);
            }
        }
        if (selStone < 0) {
            text(g, Component.translatable("infinity.shard.pick_stone"), cx, cy + R * 0.4F + 52 * stageScale, 0.95F, ShardGfx.alpha(0xCFCFE8, 0.7F * open), true);
        }

        // constellation
        if (selStone >= 0 && sp > 0.3F) tip = drawConstellation(g, mx, my, now, t, sp, tip);
        if (selStone >= 0 && sp > 0.3F) {
            backX = 14; backY = 42;
            boolean bh = mx > backX - 4 && mx < backX + 64 && my > backY - 4 && my < backY + 14;
            text(g, Component.translatable("infinity.shard.back"), backX, backY, 1F, bh ? 0xFFFFFFFF : 0xFFA0A0C0, false);
        }
        return tip;
    }

    private void layoutNodes(int n) {
        if (nodeX.length != n) { nodeX = new float[n]; nodeY = new float[n]; }
    }

    private static final int[] RING_CAP = {5, 9, 13, 20};
    private static final float[] RING_R = {66, 108, 146, 184};

    /** Ring index and position within the ring of power #i. */
    private int[] ringOf(int i, int n) {
        int ring = 0, start = 0;
        // fill rings so the outer ones are never nearly empty: the last ring takes what is left
        while (ring < RING_CAP.length - 1 && i >= start + RING_CAP[ring]) { start += RING_CAP[ring]; ring++; }
        int inRing = Math.min(RING_CAP[ring], n - start);
        return new int[]{ring, i - start, inRing, start};
    }

    private List<Component> drawConstellation(GuiGraphics g, int mx, int my, long now, float t, float sp, List<Component> tip) {
        Stone st = Stone.values()[selStone];
        int col = rgb(st);
        int n = nodes.size();
        layoutNodes(n);
        float reveal = Mth.clamp((sp - 0.3F) / 0.7F, 0, 1);
        if (n == 0) {
            text(g, Component.translatable("infinity.shard.no_powers"), cx, cy + 74 * stageScale, 0.95F, ShardGfx.alpha(0xFF8080, reveal), true);
            return tip;
        }
        for (int i = 0; i < n; i++) {
            int[] r = ringOf(i, n);
            float rad = RING_R[r[0]] * stageScale * ShardGfx.easeOutCubic(Mth.clamp(reveal * 1.5F - r[0] * 0.15F, 0, 1)) + 2.5F * Mth.sin(t * 1.1F + i);
            float ang = -Mth.HALF_PI + (r[1] + 0.5F * r[0]) * Mth.TWO_PI / r[2] + Mth.sin(t * 0.25F + i * 0.7F) * 0.025F;
            nodeX[i] = cx + Mth.cos(ang) * rad; nodeY[i] = cy + Mth.sin(ang) * rad;
        }
        ShardGfx.additive(true);
        // constellation lines: along each ring, ring to inner ring, innermost ring to the stone
        for (int i = 0; i < n; i++) {
            int[] r = ringOf(i, n);
            float la = 0.16F * reveal;
            boolean si = selected.contains(nodes.get(i).id());
            int next = r[3] + (r[1] + 1) % r[2];
            if (r[2] > 1 && next != i) {
                boolean both = si && selected.contains(nodes.get(next).id());
                ShardGfx.line(g, nodeX[i], nodeY[i], nodeX[next], nodeY[next], both ? 1.6F : 0.8F, ShardGfx.alpha(col, both ? 0.7F * reveal : la), ShardGfx.alpha(col, both ? 0.7F * reveal : la));
            }
            if (r[0] > 0) {
                int pc = RING_CAP[r[0] - 1], pStart = r[3] - pc, pIn = Math.min(pc, n - pStart);
                int inner = pStart + Math.min(pIn - 1, r[1] * pIn / Math.max(1, r[2]));
                ShardGfx.line(g, nodeX[i], nodeY[i], nodeX[inner], nodeY[inner], 0.7F, ShardGfx.alpha(col, la * 0.7F), ShardGfx.alpha(col, la * 0.7F));
            }
        }
        // spokes with travelling pulses for the chosen powers
        for (int i = 0; i < n; i++) {
            if (!selected.contains(nodes.get(i).id())) continue;
            ShardGfx.line(g, cx, cy, nodeX[i], nodeY[i], 1.3F, ShardGfx.alpha(col, 0.55F * reveal), ShardGfx.alpha(0xFFFFFF, 0.65F * reveal));
            for (int k = 0; k < 2; k++) {
                float u = (t * 0.9F + i * 0.19F + k * 0.5F) % 1F;
                ShardGfx.glow(g, Mth.lerp(u, cx, nodeX[i]), Mth.lerp(u, cy, nodeY[i]), 6, 0xFFFFFF, 0.8F * reveal * (1 - Math.abs(u - 0.5F)));
            }
        }
        ShardGfx.additive(false);
        // the nodes
        for (int i = 0; i < n; i++) {
            StoneTree.Node node = nodes.get(i);
            boolean sel = selected.contains(node.id());
            float na = Mth.clamp(reveal * 1.6F - i * 0.04F, 0, 1);
            boolean hover = na > 0.8F && Math.hypot(mx - nodeX[i], my - nodeY[i]) < 14 * stageScale + 3;
            if (hover) {
                hoverNode = i;
                tip = new ArrayList<>();
                tip.add(node.displayName().withStyle(ChatFormatting.WHITE));
                String desc = node.description();
                if (desc != null && !desc.isBlank()) {
                    tip.add(node.displayDescription().withStyle(ChatFormatting.GRAY));
                }
                tip.add(Component.translatable("infinity.shard.cooldown_line", ShardRules.cooldownSeconds(node.modeId())).withStyle(ChatFormatting.DARK_AQUA));
                tip.add(Component.translatable("infinity.shard.cost_line", String.format("%.1f", ShardRules.magiculeCost(node.modeId()))).withStyle(ChatFormatting.DARK_AQUA));
                tip.add(Component.translatable(sel ? "infinity.shard.click_remove" : "infinity.shard.click_add").withStyle(ChatFormatting.YELLOW));
            }
            float rr = (13 + (hover ? 2 : 0) + (sel ? 1.5F : 0)) * stageScale;
            ShardGfx.additive(true);
            float pulse = 0.5F + 0.5F * Mth.sin(t * 3F + i);
            ShardGfx.glow(g, nodeX[i], nodeY[i], rr * (sel ? 2.8F : 2F), col, (sel ? 0.5F + 0.2F * pulse : hover ? 0.4F : 0.16F) * na);
            ShardGfx.additive(false);
            ShardGfx.disc(g, nodeX[i], nodeY[i], rr, ShardGfx.alpha(sel ? ShardGfx.darken(col, 0.65F) : 0x0B0B14, 0.88F * na));
            ShardGfx.circle(g, nodeX[i], nodeY[i], rr, sel ? 2.2F : 1.2F, ShardGfx.alpha(hover || sel ? ShardGfx.lighten(col, 0.4F) : col, (sel ? 1F : hover ? 0.9F : 0.5F) * na));
            drawIcon(g, node, nodeX[i], nodeY[i], 1F * stageScale + (sel ? 0.12F : 0), na);
            if (sel) {
                ShardGfx.additive(true);
                ShardGfx.glint(g, nodeX[i] + rr * 0.7F, nodeY[i] - rr * 0.7F, 6 + 2 * pulse, t, 0xFFFFFF, 0.9F * na);
                ShardGfx.additive(false);
            }
        }
        // ambient twinkles over the constellation
        ShardGfx.additive(true);
        for (int i = 0; i < n; i++) {
            float tw = Math.max(0, Mth.sin(t * 1.3F + i * 2.1F)) * reveal;
            if (tw > 0.2F) ShardGfx.glint(g, nodeX[i] + 11 * Mth.cos(i * 1.7F), nodeY[i] + 11 * Mth.sin(i * 1.7F), 4 * tw, 0.4F, col, 0.5F * tw);
        }
        ShardGfx.additive(false);
        return tip;
    }

    private void drawIcon(GuiGraphics g, StoneTree.Node node, float x, float y, float scale, float alpha) {
        String icon = node.icon();
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        RenderSystem.setShaderColor(1F, 1F, 1F, Mth.clamp(alpha, 0, 1));
        if (icon != null && icon.contains("textures/")) {
            g.blit(ResourceLocation.parse(icon), -8, -8, 0, 0, 16, 16, 16, 16);
        } else {
            ItemStack stack = icon == null ? stoneItem(node.stone()) : new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(icon)));
            if (stack.isEmpty()) stack = stoneItem(node.stone());
            g.renderItem(stack, -8, -8);
        }
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        g.pose().popPose();
    }

    // ---- side panel

    private boolean canCreate() {
        denyReason = "";
        if (selected.isEmpty()) { denyReason = "infinity.shard.err.none_selected"; return false; }
        if (ShardClientState.list().gems().size() >= ShardRules.MAX_PER_CREATOR) { denyReason = "infinity.shard.err.max"; return false; }
        if (minecraft != null && minecraft.player != null && minecraft.player.getInventory().getFreeSlot() < 0) { denyReason = "infinity.shard.err.full"; return false; }
        if (minecraft != null && minecraft.player != null && !com.infinitystones.tensura.skill.GauntletStones.wearsComplete(minecraft.player)) { denyReason = "infinity.shard.err.gauntlet"; return false; }
        return true;
    }

    private void drawPanel(GuiGraphics g, int mx, int my, long now, float t, float sp) {
        Stone st = Stone.values()[selStone];
        int col = rgb(st);
        float a = Mth.clamp((sp - 0.2F) / 0.8F, 0, 1);
        float x = panelX, y = panelY, w = PANEL_W, h = panelH;
        g.fill((int) x, (int) y, (int) (x + w), (int) (y + h), ShardGfx.alpha(0x090A14, 0.82F * a));
        ShardGfx.gradient(g, x, y, w, 30, ShardGfx.alpha(col, 0.28F * a), ShardGfx.alpha(col, 0));
        g.fill((int) x, (int) y, (int) (x + 2), (int) (y + h), ShardGfx.alpha(col, 0.9F * a));
        float ty = y + 8;
        text(g, Component.translatable("infinity.skill." + st.key + "_stone").withStyle(ChatFormatting.BOLD), x + 10, ty, 1.1F, ShardGfx.alpha(col, a), false);
        ty += 16;
        text(g, Component.translatable("infinity.shard.selected", selected.size()), x + 10, ty, 0.85F, ShardGfx.alpha(0xB8B8D0, a), false);
        ty += 12;
        int shown = 0;
        for (StoneTree.Node n : nodes) {
            if (!selected.contains(n.id())) continue;
            if (shown >= 7) { text(g, Component.literal("+" + (selected.size() - shown)), x + 14, ty, 0.8F, ShardGfx.alpha(0x9090A8, a), false); ty += 9; break; }
            ShardGfx.disc(g, x + 14, ty + 3.5F, 1.8F, ShardGfx.alpha(col, a));
            String nm = font.plainSubstrByWidth(n.displayName().getString(), (int) ((w - 34) / 0.8F));
            text(g, Component.literal(nm), x + 20, ty, 0.8F, ShardGfx.alpha(0xFFFFFF, a * 0.9F), false);
            ty += 9; shown++;
        }
        float sy = y + 126;
        text(g, Component.translatable("infinity.shard.duration"), x + 10, sy, 0.9F, ShardGfx.alpha(0xB8B8D0, a), false);
        infAnim += ((infinite ? 1F : 0F) - infAnim) * 0.25F;
        String readout = infinite ? "\u221E" : fmt(seconds());
        text(g, Component.literal(readout).withStyle(ChatFormatting.BOLD), x + w / 2, sy + 14, 1.5F, ShardGfx.alpha(ShardGfx.mix(0xFFFFFF, col, 0.4F), a), true);
        trackX = x + 14; trackY = sy + 36; trackW = w - 28;
        float k = infinite ? 1F : Math.round(slider * (STEPS.length - 1)) / (float) (STEPS.length - 1);
        float dim = 1 - infAnim * 0.65F;
        g.fill((int) trackX, (int) trackY, (int) (trackX + trackW), (int) (trackY + 4), ShardGfx.alpha(0x2A2A3C, a));
        ShardGfx.additive(true);
        ShardGfx.gradientH(g, trackX, trackY, trackW * k, 4, ShardGfx.alpha(ShardGfx.darken(col, 0.4F), 0.9F * a * dim), ShardGfx.alpha(col, 0.95F * a * dim));
        for (int i = 0; i < STEPS.length; i++) {
            float tx = trackX + trackW * i / (STEPS.length - 1);
            g.fill((int) tx, (int) trackY - 2, (int) tx + 1, (int) trackY, ShardGfx.alpha(0xFFFFFF, 0.25F * a * dim));
        }
        float kx = trackX + trackW * k;
        float kr = dragging ? 7F : 5.5F;
        ShardGfx.glow(g, kx, trackY + 2, 14, col, (dragging ? 0.7F : 0.4F) * a * dim);
        ShardGfx.additive(false);
        ShardGfx.disc(g, kx, trackY + 2, kr, ShardGfx.alpha(0xFFFFFF, a * (0.55F + 0.45F * dim)));
        ShardGfx.disc(g, kx, trackY + 2, kr - 2, ShardGfx.alpha(col, a * dim));
        text(g, Component.literal("1 min"), trackX, trackY + 9, 0.65F, ShardGfx.alpha(0x808098, a), false);
        text(g, Component.literal("2 h"), trackX + trackW, trackY + 9, 0.65F, ShardGfx.alpha(0x808098, a), false);
        // no-limit switch
        infX = x + 12; infY = sy + 62;
        boolean ih = mx > infX - 2 && mx < x + w - 8 && my > infY - 3 && my < infY + 22;
        ShardGfx.additive(infAnim > 0.05F);
        ShardGfx.glow(g, infX + 13, infY + 6, 18, 0xFFFFFF, 0.25F * infAnim * a);
        ShardGfx.additive(false);
        g.fill((int) infX, (int) infY, (int) (infX + 26), (int) (infY + 12), ShardGfx.alpha(ShardGfx.mix(0x2A2A3C, col, infAnim), (ih ? 0.95F : 0.8F) * a));
        ShardGfx.disc(g, infX + 6 + 14 * infAnim, infY + 6, 4.2F, ShardGfx.alpha(0xFFFFFF, a));
        text(g, Component.translatable("infinity.shard.infinite"), infX + 32, infY + 2, 0.78F, ShardGfx.alpha(infinite ? 0xFFFFFF : 0xB0B0C8, a), false);
        // create button
        createW = w - 24; createH = 28; createX = x + 12; createY = y + h - createH - 12;
        boolean ok = canCreate();
        boolean ch = ok && mx > createX && mx < createX + createW && my > createY && my < createY + createH;
        float pulse = 0.5F + 0.5F * Mth.sin(t * 3.2F);
        if (ok) {
            ShardGfx.additive(true);
            ShardGfx.glow(g, createX + createW / 2, createY + createH / 2, createW * 0.7F, col, (0.12F + 0.1F * pulse + (ch ? 0.22F : 0)) * a);
            ShardGfx.additive(false);
        }
        int c0 = ok ? ShardGfx.mix(col, 0xFFFFFF, ch ? 0.35F : 0.1F) : 0x3A3A48, c1 = ok ? ShardGfx.darken(col, 0.45F) : 0x22222C;
        ShardGfx.gradient(g, createX, createY, createW, createH, ShardGfx.alpha(c0, 0.95F * a), ShardGfx.alpha(c1, 0.95F * a));
        g.renderOutline((int) createX, (int) createY, (int) createW, (int) createH, ShardGfx.alpha(ok ? ShardGfx.lighten(col, 0.5F) : 0x55556A, a));
        text(g, Component.translatable("infinity.shard.create").withStyle(ChatFormatting.BOLD), createX + createW / 2, createY + 9, 1.15F, ShardGfx.alpha(ok ? 0xFFFFFF : 0x80808F, a), true);
        if (!ok && !denyReason.isEmpty()) {
            List<FormattedCharSequence> lines = font.split(Component.translatable(denyReason), (int) (w / 0.78F) - 20);
            float ry = createY - 4 - lines.size() * 8;
            for (FormattedCharSequence l : lines) {
                g.pose().pushPose(); g.pose().translate(x + 12, ry, 0); g.pose().scale(0.78F, 0.78F, 1);
                g.drawString(font, l, 0, 0, ShardGfx.alpha(0xFF8A8A, a), true);
                g.pose().popPose();
                ry += 8;
            }
        }
    }

    // ---- "my shards" tab

    private void drawMineTab(GuiGraphics g, int mx, int my, long now, float t, float open) {
        var list = ShardClientState.list();
        long age = ShardClientState.listAge();
        float pw = Math.min(width - 40, 480), px = width / 2F - pw / 2F;
        text(g, Component.translatable("infinity.shard.list.count", list.gems().size(), ShardRules.MAX_PER_CREATOR), width / 2F, 46, 0.95F, ShardGfx.alpha(0xB8B8D0, open), true);
        rowTop = 62;
        float rh = 46, gap = 6;
        float top = rowTop, bottom = height - 14;
        double total = list.gems().size() * (rh + gap);
        listScroll = (float) Mth.clamp(listScroll, 0, Math.max(0, total - (bottom - top)));
        if (list.gems().isEmpty()) {
            float pulse = 0.5F + 0.5F * Mth.sin(t * 2F);
            ShardGfx.additive(true);
            ShardGfx.glow(g, width / 2F, height / 2F, 70, 0x8070FF, 0.12F + 0.08F * pulse);
            ShardGfx.additive(false);
            text(g, Component.translatable("infinity.shard.list.empty"), width / 2F, height / 2F - 4, 1F, ShardGfx.alpha(0x9090B0, open), true);
            return;
        }
        g.enableScissor(0, (int) top, width, (int) bottom);
        for (int i = 0; i < list.gems().size(); i++) {
            ShardNet.Info info = list.gems().get(i);
            Stone st = info.stone() >= 0 && info.stone() < 6 ? Stone.values()[info.stone()] : Stone.MIND;
            int col = rgb(st);
            float enter = ShardGfx.easeOutCubic((age - i * 70L) / 320F);
            Long del = deleting.get(info.id());
            float dying = del == null ? 0F : Mth.clamp((now - del) / 450F, 0, 1);
            float a = enter * (1 - dying) * open;
            if (a <= 0.01F) continue;
            float y = top + i * (rh + gap) - listScroll;
            float x = px + (1 - enter) * 36 + (dying > 0 ? dying * 40 : 0);
            if (y + rh < top || y > bottom) continue;
            boolean rowHover = mx > px && mx < px + pw && my > y && my < y + rh && my > top && my < bottom;
            g.fill((int) x, (int) y, (int) (x + pw), (int) (y + rh), ShardGfx.alpha(0x0B0C18, (rowHover ? 0.92F : 0.8F) * a));
            ShardGfx.gradientH(g, x, y, pw * 0.5F, rh, ShardGfx.alpha(col, 0.14F * a), ShardGfx.alpha(col, 0));
            g.fill((int) x, (int) y, (int) (x + 3), (int) (y + rh), ShardGfx.alpha(col, a));
            ShardGfx.additive(true);
            ShardGfx.glow(g, x + 26, y + rh / 2, 24, col, 0.32F * a);
            ShardGfx.additive(false);
            item(g, stoneItem(st), x + 26, y + rh / 2, 1.5F, a);
            Component head = Component.literal("#" + com.infinitystones.tensura.shard.ShardData.codeOf(info.id())).withStyle(ChatFormatting.BOLD)
                    .append(Component.literal("  ")).append(Component.translatable("infinity.skill." + st.key + "_stone"));
            text(g, head, x + 50, y + 6, 1F, ShardGfx.alpha(col, a), false);
            StringBuilder sb = new StringBuilder();
            for (String id : info.powers()) {
                StoneTree.Node n = StoneTree.of(st).nodes.get(id);
                if (sb.length() > 0) sb.append(", ");
                sb.append(n == null ? id : n.displayName().getString());
            }
            String pl = font.plainSubstrByWidth(sb.toString(), (int) ((pw - 190) / 0.8F));
            text(g, Component.literal(pl), x + 50, y + 18, 0.8F, ShardGfx.alpha(0xD0D0E0, a), false);
            Component bound = info.bound() ? Component.translatable("infinity.shard.list.bound", info.boundName()) : Component.translatable("infinity.shard.list.unbound");
            text(g, bound, x + 50, y + 29, 0.72F, ShardGfx.alpha(info.bound() ? 0x9FE0FF : 0x808098, a), false);
            // time left
            float barX = x + pw - 130, barW = 76;
            String timeTxt;
            float frac;
            if (info.infinite()) {
                timeTxt = "\u221E"; frac = 1F;
            } else {
                long leftTicks = info.expiry() - list.now() - age / 50L;
                long leftS = Math.max(0, leftTicks / 20);
                timeTxt = fmt(leftS);
                frac = Mth.clamp(leftS / 7200F, 0.03F, 1F);
                if (leftS < 60) frac = Mth.clamp(leftS / 60F, 0.03F, 1F) * 0.2F;
            }
            text(g, Component.literal(timeTxt), barX + barW / 2, y + 8, 0.9F, ShardGfx.alpha(info.infinite() ? 0x9FF0FF : frac < 0.1F ? 0xFF7070 : 0xFFFFFF, a), true);
            g.fill((int) barX, (int) y + 22, (int) (barX + barW), (int) y + 25, ShardGfx.alpha(0x26263A, a));
            ShardGfx.additive(true);
            if (info.infinite()) {
                float sh = (t * 0.6F) % 1F;
                ShardGfx.gradientH(g, barX, y + 22, barW, 3, ShardGfx.alpha(0x40D0FF, 0.5F * a), ShardGfx.alpha(0xB080FF, 0.5F * a));
                ShardGfx.glow(g, barX + barW * sh, y + 23.5F, 10, 0xFFFFFF, 0.7F * a);
            } else ShardGfx.gradientH(g, barX, y + 22, barW * frac, 3, ShardGfx.alpha(ShardGfx.darken(col, 0.4F), 0.9F * a), ShardGfx.alpha(frac < 0.1F ? 0xFF4040 : col, 0.95F * a));
            ShardGfx.additive(false);
            // delete
            float bx = x + pw - 50, by = y + rh / 2 - 9, bw = 42, bh = 18;
            boolean isArmed = info.id().equals(armed) && now - armedAt < 2600;
            boolean bhover = mx > bx && mx < bx + bw && my > by && my < by + bh && my > top && my < bottom;
            int bc = isArmed ? 0xFF4A4A : bhover ? 0xFF8A8A : 0xC05A5A;
            g.fill((int) bx, (int) by, (int) (bx + bw), (int) (by + bh), ShardGfx.alpha(isArmed ? 0x7A1010 : 0x2A0E14, (isArmed ? 0.95F : 0.8F) * a));
            g.renderOutline((int) bx, (int) by, (int) bw, (int) bh, ShardGfx.alpha(bc, a));
            text(g, Component.translatable(isArmed ? "infinity.shard.list.confirm" : "infinity.shard.list.delete"), bx + bw / 2, by + 5, isArmed ? 0.66F : 0.8F, ShardGfx.alpha(0xFFFFFF, a), true);
            if (dying > 0) {
                ShardGfx.additive(true);
                for (int k = 0; k < 4; k++) ShardGfx.glint(g, x + 26 + (k - 1.5F) * 22 * dying, y + rh / 2 + Mth.sin(k * 2.1F) * 12 * dying, 8 * (1 - dying), k, col, (1 - dying));
                ShardGfx.additive(false);
            }
        }
        g.disableScissor();
        deleting.entrySet().removeIf(e -> now - e.getValue() > 450 && list.gems().stream().noneMatch(i -> i.id().equals(e.getKey())));
    }

    // ------------------------------------------------------------ input

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (createdAt >= 0) return true;
        long now = System.currentTimeMillis();
        // tabs
        Component a = Component.translatable("infinity.shard.tab.create"), b = Component.translatable("infinity.shard.tab.mine");
        float wa = font.width(a) * 1.1F, wb = font.width(b) * 1.1F, gap = 34;
        float x0 = width / 2F - (wa + wb + gap) / 2F, ax = x0 + wa / 2F, bx = x0 + wa + gap + wb / 2F;
        if (my > 20 && my < 36 && button == 0) {
            if (Math.abs(mx - ax) < wa / 2 + 6) { switchTab(0); return true; }
            if (Math.abs(mx - bx) < wb / 2 + 6) { switchTab(1); return true; }
        }
        if (button != 0) return super.mouseClicked(mx, my, button);
        if (tab == 0) {
            if (selStone < 0) {
                if (hoverStone >= 0) {
                    if (!owns(Stone.values()[hoverStone])) { play(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6F, 0.4F); return true; }
                    selectStone(hoverStone, orbX[hoverStone], orbY[hoverStone]);
                }
                return true;
            }
            if (stageP(now) < 0.6F) return true;
            if (mx > backX - 4 && mx < backX + 64 && my > backY - 4 && my < backY + 14) { back(); return true; }
            if (hoverNode >= 0 && hoverNode < nodes.size()) {
                String id = nodes.get(hoverNode).id();
                boolean added = selected.add(id);
                if (!added) selected.remove(id);
                play(SoundEvents.AMETHYST_BLOCK_CHIME, added ? 1.5F : 0.9F, 0.35F);
                if (added) play(SoundEvents.AMETHYST_CLUSTER_STEP, 1.8F, 0.4F);
                return true;
            }
            if (mx > trackX - 8 && mx < trackX + trackW + 8 && my > trackY - 8 && my < trackY + 14 && !infinite) { dragging = true; setSlider(mx); return true; }
            if (mx > infX - 2 && mx < panelX + PANEL_W - 8 && my > infY - 3 && my < infY + 22) {
                infinite = !infinite;
                play(SoundEvents.UI_BUTTON_CLICK.value(), infinite ? 1.3F : 0.9F, 0.4F);
                return true;
            }
            if (canCreate() && mx > createX && mx < createX + createW && my > createY && my < createY + createH) {
                PacketDistributor.sendToServer(new ShardNet.Create(selStone, new ArrayList<>(selected), seconds(), infinite));
                createdAt = now;
                hubFlash = now; hubFlashColor = rgb(Stone.values()[selStone]);
                play(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.6F, 0.6F);
                return true;
            }
        } else {
            var list = ShardClientState.list();
            float pw = Math.min(width - 40, 480), px = width / 2F - pw / 2F, rh = 46, gap2 = 6;
            for (int i = 0; i < list.gems().size(); i++) {
                float y = rowTop + i * (rh + gap2) - listScroll;
                float bxx = px + pw - 50, by = y + rh / 2 - 9;
                if (mx > bxx && mx < bxx + 42 && my > by && my < by + 18 && my > rowTop) {
                    UUID id = list.gems().get(i).id();
                    if (id.equals(armed) && now - armedAt < 2600) {
                        PacketDistributor.sendToServer(new ShardNet.Delete(id));
                        deleting.put(id, now);
                        armed = null;
                        play(SoundEvents.GLASS_BREAK, 1.4F, 0.5F);
                        play(SoundEvents.AMETHYST_BLOCK_BREAK, 1.2F, 0.6F);
                    } else {
                        armed = id; armedAt = now;
                        play(SoundEvents.NOTE_BLOCK_PLING.value(), 0.7F, 0.4F);
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private void setSlider(double mx) { slider = Mth.clamp((float) ((mx - trackX) / trackW), 0, 1); }

    @Override public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            int before = seconds();
            setSlider(mx);
            if (seconds() != before) play(SoundEvents.NOTE_BLOCK_HAT.value(), 1.2F + slider * 0.8F, 0.25F);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override public boolean mouseReleased(double mx, double my, int button) {
        if (dragging) { dragging = false; slider = Math.round(slider * (STEPS.length - 1)) / (float) (STEPS.length - 1); return true; }
        return super.mouseReleased(mx, my, button);
    }

    @Override public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (tab == 1) { listScroll -= (float) sy * 24; return true; }
        if (tab == 0 && selStone >= 0 && !infinite && mx > panelX) {
            int idx = Mth.clamp(Math.round(slider * (STEPS.length - 1)) + (sy > 0 ? 1 : -1), 0, STEPS.length - 1);
            slider = idx / (float) (STEPS.length - 1);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256 && tab == 0 && selStone >= 0 && createdAt < 0) { back(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
