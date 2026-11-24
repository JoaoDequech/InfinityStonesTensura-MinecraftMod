package com.infinitystones.tensura.skill.ability.extra.v2d.client;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.extra.v2d.LucidDream;
import com.infinitystones.tensura.skill.ability.extra.v2d.LucidNet;
import com.infinitystones.tensura.skill.ability.extra.v2d.LucidShapes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Lucid Dream, client side (owner only): the golden ghost of the current brush that follows the aimed surface, the clicks
 * (left place, Sneak + left undo, right shape menu, middle stop) and the scroll (size) sent to the server, and a small hint bar.
 * The ghost uses the same {@link LucidShapes} as the server, so what is previewed is what appears.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class LucidClient {
    private static final int GOLD = 0xF2D02B, RED = 0xE22B3B;
    private static boolean active, hasAnchor;
    private static int brush, size = 1, count;
    private static BlockPos anchor = BlockPos.ZERO;
    private static boolean attackHeld;
    private static int lastAction = -1, lastTick;

    public static void accept(LucidNet.State s) {
        active = s.active();
        brush = Mth.clamp(s.brush(), 0, LucidShapes.NAMES.length - 1);
        size = Math.max(1, s.size());
        hasAnchor = s.hasAnchor();
        anchor = new BlockPos(s.ax(), s.ay(), s.az());
        count = s.count();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { active = false; }

    // ------------------------------------------------------------------ input

    @SubscribeEvent
    public static void interaction(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || mc.screen != null || mc.player == null) return;
        int action;
        if (e.isAttack()) action = mc.player.isShiftKeyDown() ? 2 : 0;
        else if (e.isUseItem()) action = 1;
        else if (e.isPickBlock()) action = 4;
        else return;
        e.setCanceled(true);
        e.setSwingHand(false);
        if (e.isAttack()) { if (attackHeld) return; attackHeld = true; }
        if (action == lastAction && mc.player.tickCount - lastTick < 3) return;
        lastAction = action; lastTick = mc.player.tickCount;
        PacketDistributor.sendToServer(new LucidNet.Input(action, 0));
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || mc.screen != null || mc.player == null || e.getScrollDeltaY() == 0) return;
        e.setCanceled(true);
        PacketDistributor.sendToServer(new LucidNet.Input(3, e.getScrollDeltaY() > 0 ? 1 : -1));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (attackHeld && !mc.options.keyAttack.isDown()) attackHeld = false;
    }

    // ------------------------------------------------------------------ ghost

    private static BlockPos target(Minecraft mc, float pt) {
        Vec3 eye = mc.player.getEyePosition(pt), look = mc.player.getViewVector(pt);
        BlockHitResult h = mc.level.clip(new ClipContext(eye, eye.add(look.scale(LucidDream.REACH)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        if (h.getType() == HitResult.Type.MISS) return BlockPos.containing(eye.add(look.scale(LucidDream.FAR_POINT)));
        return h.getBlockPos().relative(h.getDirection());
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !active) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 cp = e.getCamera().getPosition();
        BlockPos t = target(mc, pt);
        List<BlockPos> cells = LucidShapes.shape(brush, size, t, mc.player.getDirection(), hasAnchor ? anchor : null, mc.player.blockPosition());
        Set<BlockPos> shown = new HashSet<>();
        for (BlockPos c : cells)
            if (c.distSqr(mc.player.blockPosition()) <= LucidDream.BUILD_RADIUS * LucidDream.BUILD_RADIUS && mc.level.getBlockState(c).isAir()) shown.add(c);
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(-cp.x, -cp.y, -cp.z);
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        float time = (mc.level.getGameTime() % 100000L + pt) / 20F;
        V2dDraw d = new V2dDraw(buf.getBuffer(RenderType.lightning()), ps.last().pose(), cp);
        boolean edges = shown.size() <= 160;
        int i = 0;
        for (BlockPos c : shown) {
            float pulse = 0.5F + 0.5F * Mth.sin(time * 4 + (c.getX() + c.getY() * 2 + c.getZ()) * 0.5F);
            d.color(((c.getX() + c.getY() + c.getZ()) & 1) == 0 ? GOLD : 0xF2A02B);
            for (Direction dir : Direction.values()) {
                if (shown.contains(c.relative(dir))) continue;
                face(d, c, dir, 0.13F + 0.08F * pulse, edges);
            }
            i++;
        }
        d.color(GOLD);
        Vec3 tc = Vec3.atCenterOf(t);
        d.spark(tc, 0.3F + 0.08F * Mth.sin(time * 6), 0.9F);
        if (hasAnchor) {
            Vec3 ac = Vec3.atCenterOf(anchor);
            d.color(RED);
            d.spark(ac, 0.22F, 0.7F);
            if (brush == 1) { d.color(GOLD); d.line(ac, tc, 0.03F, 0.5F); }
        }
        buf.endBatch(RenderType.lightning());
        ps.popPose();
    }

    private static void face(V2dDraw d, BlockPos c, Direction dir, float al, boolean edges) {
        double x0 = c.getX() + 0.02, y0 = c.getY() + 0.02, z0 = c.getZ() + 0.02, x1 = c.getX() + 0.98, y1 = c.getY() + 0.98, z1 = c.getZ() + 0.98;
        Vec3 a, b, cc, dd;
        switch (dir) {
            case DOWN -> { a = new Vec3(x0, y0, z0); b = new Vec3(x1, y0, z0); cc = new Vec3(x1, y0, z1); dd = new Vec3(x0, y0, z1); }
            case UP -> { a = new Vec3(x0, y1, z0); b = new Vec3(x0, y1, z1); cc = new Vec3(x1, y1, z1); dd = new Vec3(x1, y1, z0); }
            case NORTH -> { a = new Vec3(x0, y0, z0); b = new Vec3(x0, y1, z0); cc = new Vec3(x1, y1, z0); dd = new Vec3(x1, y0, z0); }
            case SOUTH -> { a = new Vec3(x0, y0, z1); b = new Vec3(x1, y0, z1); cc = new Vec3(x1, y1, z1); dd = new Vec3(x0, y1, z1); }
            case WEST -> { a = new Vec3(x0, y0, z0); b = new Vec3(x0, y0, z1); cc = new Vec3(x0, y1, z1); dd = new Vec3(x0, y1, z0); }
            default -> { a = new Vec3(x1, y0, z0); b = new Vec3(x1, y1, z0); cc = new Vec3(x1, y1, z1); dd = new Vec3(x1, y0, z1); }
        }
        d.quad(a, b, cc, dd, al);
        if (edges) {
            d.ribbon(a, b, 0.012F, al * 3F, true); d.ribbon(b, cc, 0.012F, al * 3F, true);
            d.ribbon(cc, dd, 0.012F, al * 3F, true); d.ribbon(dd, a, 0.012F, al * 3F, true);
        }
    }

    // ------------------------------------------------------------------ HUD

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (!active || mc.options.hideGui || mc.player == null) return;
        GuiGraphics g = e.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        Component name = Component.translatable("infinity.extra.v2d.lucid.brush." + LucidShapes.NAMES[brush]);
        Component line1 = Component.translatable("infinity.extra.v2d.lucid.hud", name, size, count);
        Component line2 = Component.translatable("infinity.extra.v2d.lucid.hud_keys");
        int tw = Math.max(mc.font.width(line1), (int) (mc.font.width(line2) * 0.8F)) + 14;
        int x = w / 2 - tw / 2, y = h - 74;
        g.fill(x, y, x + tw, y + 25, 0xA0120A02);
        g.fill(x, y, x + tw, y + 1, 0xFF000000 | GOLD);
        g.drawCenteredString(mc.font, line1, w / 2, y + 4, 0xFFFFE9A0);
        g.pose().pushPose();
        g.pose().translate(w / 2F, y + 15, 0);
        g.pose().scale(0.8F, 0.8F, 1);
        g.drawCenteredString(mc.font, line2, 0, 0, 0xFFE8D8B0);
        g.pose().popPose();
    }

    private LucidClient() {}
}
