package com.infinitystones.tensura.client.will;

import com.infinitystones.tensura.client.wheel.WheelScreen;
import com.infinitystones.tensura.wheel.WheelPayloads;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Construct wheel (middle click on Will), Green Lantern style: "+" at the top creates a new construct, the button at
 * the bottom dismisses every summoned construct (they implode), saved constructs sit around the ring with a spinning
 * 3D preview in hard light. Left = summon / pick, right = dismiss that construct's copies, middle = management menu.
 */
public final class QuickSlotsScreen extends Screen {
    private static final float IN = 38, OUT = 112;
    private static final int RED = 0xFF3346;
    private final long openedAt = System.currentTimeMillis();
    private int hovered = -1; // sector index

    public QuickSlotsScreen() { super(Component.translatable("infinity.construct.quick")); }

    @Override public boolean isPauseScreen() { return false; }

    @Override protected void init() {
        PacketDistributor.sendToServer(new WheelPayloads.ConstructAction(0, -1, ""));
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F, 0.25F));
    }

    /** Sector layout: 0 = "+", half-way = "dismiss all", the rest = saved constructs in order. */
    private int sectors() { int n = WheelPayloads.constructs.names().size() + 2; return n + (n & 1); } // even: "dismiss all" sits exactly at the bottom
    private int dismissSector() { return sectors() / 2; }
    private int constructAt(int sector) {
        if (sector == 0 || sector == dismissSector()) return -1;
        int c = sector < dismissSector() ? sector - 1 : sector - 2;
        return c < WheelPayloads.constructs.names().size() ? c : -1;
    }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        float o = Mth.clamp((System.currentTimeMillis() - openedAt) / 170F, 0, 1);
        int cx = width / 2, cy = height / 2, n = sectors();
        long t = System.currentTimeMillis();
        float fit = Math.min(1F, (Math.min(width, height) / 2F - 12) / (OUT + 16));
        float s = fit * (0.7F + 0.3F * o);
        double dx = mx - cx, dy = my - cy, dist = Math.sqrt(dx * dx + dy * dy) / s;
        float seg = Mth.TWO_PI / n;
        double ang = (Math.atan2(dy, dx) + Math.PI / 2 + seg / 2 + Math.PI * 2) % (Math.PI * 2);
        hovered = dist >= IN - 6 && dist <= OUT + 14 ? Mth.clamp((int) (ang / seg), 0, n - 1) : -1;
        if (hovered > 0 && hovered != dismissSector() && constructAt(hovered) < 0) hovered = -1; // the filler slot is not a button

        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(s, s, 1);
        WheelScreen.disc(g, 0, 0, OUT + 26, (int) (0x55 * o) << 24);
        for (int i = 0; i < n; i++) {
            boolean hot = i == hovered, plus = i == 0, dis = i == dismissSector();
            float a0 = i * seg - seg / 2 + 0.03F, a1 = i * seg + seg / 2 - 0.03F, grow = hot ? 8 : 0;
            boolean blank = !plus && !dis && constructAt(i) < 0;
            int base = blank ? 0x140A0C : plus ? (hot ? 0x2E7A3A : 0x173D1E) : dis ? (hot ? 0x7A2E2E : 0x3D1717) : (hot ? 0x8A1622 : 0x3A0A10);
            WheelScreen.ring(g, 0, 0, IN, OUT + grow, a0, a1, ((int) ((hot ? 235 : 205) * o) << 24) | base);
            WheelScreen.ring(g, 0, 0, OUT + grow, OUT + grow + (hot ? 2.5F : 1.2F), a0, a1, ((int) (255 * o) << 24) | (hot ? 0xFFFFFF : RED));
            float mid = i * seg - Mth.HALF_PI, r = (IN + OUT) / 2 + grow / 2;
            float px = Mth.cos(mid) * r, py = Mth.sin(mid) * r;
            if (plus) g.drawCenteredString(font, "+", (int) px, (int) py - 4, 0xFF9CFFB0);
            else if (dis) g.drawCenteredString(font, "✖", (int) px, (int) py - 4, 0xFFFF9A9A);
            else preview(g, constructAt(i), px, py, (OUT - IN) * 0.62F * (hot ? 1.15F : 1F), t, o);
        }
        WheelScreen.disc(g, 0, 0, IN - 4, ((int) (230 * o) << 24) | 0x0E0608);
        WheelScreen.ring(g, 0, 0, IN - 5, IN - 3, 0, Mth.TWO_PI, ((int) ((170 + 70 * Mth.sin(t / 260F)) * o) << 24) | RED);
        g.pose().popPose();

        Component title;
        int c = hovered < 0 ? -1 : constructAt(hovered);
        if (hovered == 0) title = Component.translatable("infinity.construct.new").withStyle(ChatFormatting.GREEN);
        else if (hovered >= 0 && hovered == dismissSector()) title = Component.translatable("infinity.construct.dismiss_all").withStyle(ChatFormatting.RED);
        else if (c >= 0) title = Component.literal(WheelPayloads.constructs.names().get(c)).withStyle(ChatFormatting.BOLD);
        else title = Component.translatable("infinity.construct.quick").withStyle(ChatFormatting.RED);
        var lines = font.split(title, (int) (IN * 2 * s) + 20);
        int yy = cy - lines.size() * 5;
        for (var l : lines) { g.drawCenteredString(font, l, cx, yy, 0xFFFFFFFF); yy += 10; }
        g.pose().pushPose();
        g.pose().translate(cx, cy + (OUT + 20) * s, 0);
        g.pose().scale(0.75F, 0.75F, 1);
        g.drawCenteredString(font, Component.translatable("infinity.construct.quick_legend"), 0, 0, 0xCCFFD0D4);
        g.pose().popPose();
    }

    /** Spinning 3D miniature of a saved construct, tinted as hard light. */
    private void preview(GuiGraphics g, int idx, float x, float y, float size, long t, float o) {
        var previews = WheelPayloads.constructs.previews();
        if (minecraft == null || idx < 0 || idx >= previews.size()) return;
        List<Integer> flat = previews.get(idx);
        if (flat.size() < 4) return;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i + 3 < flat.size(); i += 4) {
            minX = Math.min(minX, flat.get(i)); maxX = Math.max(maxX, flat.get(i));
            minY = Math.min(minY, flat.get(i + 1)); maxY = Math.max(maxY, flat.get(i + 1));
            minZ = Math.min(minZ, flat.get(i + 2)); maxZ = Math.max(maxZ, flat.get(i + 2));
        }
        float span = Math.max(1, Math.max(maxX - minX + 1, Math.max(maxY - minY + 1, maxZ - minZ + 1)));
        float k = size / (span * 1.15F);
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(x, y, 120);
        ps.scale(k, -k, k);
        ps.mulPose(Axis.XP.rotationDegrees(25));
        ps.mulPose(Axis.YP.rotationDegrees((t % 9000) / 9000F * 360F));
        ps.translate(-(minX + maxX + 1) / 2F, -(minY + maxY + 1) / 2F, -(minZ + maxZ + 1) / 2F);
        Lighting.setupFor3DItems();
        MultiBufferSource.BufferSource src = g.bufferSource();
        MultiBufferSource tinted = type -> new HardLight(src.getBuffer(type));
        var dispatcher = minecraft.getBlockRenderer();
        for (int i = 0; i + 3 < flat.size(); i += 4) {
            BlockState st = Block.stateById(flat.get(i + 3));
            if (st.isAir()) continue;
            ps.pushPose();
            ps.translate(flat.get(i), flat.get(i + 1), flat.get(i + 2));
            dispatcher.renderSingleBlock(st, ps, tinted, 0xF000F0, OverlayTexture.NO_OVERLAY);
            ps.popPose();
        }
        g.flush();
        Lighting.setupFor3DItems();
        ps.popPose();
    }

    /** Vertex wrapper that pulls every colour toward the Reality Stone's red (hard light look). */
    private record HardLight(VertexConsumer d) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) { d.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) {
            d.setColor(Math.min(255, (int) (r * 0.55F + 120)), (int) (g * 0.35F + 20), (int) (b * 0.38F + 28), a); return this;
        }
        @Override public VertexConsumer setUv(float u, float v) { d.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { d.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { d.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { d.setNormal(x, y, z); return this; }
    }

    @Override public boolean mouseClicked(double mx, double my, int button) {
        if (button == 2) { if (minecraft != null) minecraft.setScreen(new ConstructScreen()); return true; }
        if (hovered < 0) return super.mouseClicked(mx, my, button);
        int c = constructAt(hovered);
        if (button == 0) {
            if (hovered == 0) { send(1, -1); onClose(); }                       // new construct → design mode
            else if (hovered == dismissSector()) { send(6, -1); onClose(); }    // dismiss all (implode)
            else if (c >= 0) { send(5, c); onClose(); }                         // summon
        } else if (button == 1 && c >= 0) send(10, c);                          // dismiss that one's copies
        return true;
    }

    private void send(int action, int idx) {
        PacketDistributor.sendToServer(new WheelPayloads.ConstructAction(action, idx, ""));
        if (minecraft != null) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, action == 5 ? 1.5F : 0.9F, 0.3F));
    }

    @SuppressWarnings("unused") private static List<Integer> none() { return new ArrayList<>(); }
    @SuppressWarnings("unused") private static RenderType unused() { return RenderType.solid(); }
}
