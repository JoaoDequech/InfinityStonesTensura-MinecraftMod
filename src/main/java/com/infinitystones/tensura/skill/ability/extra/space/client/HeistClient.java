package com.infinitystones.tensura.skill.ability.extra.space.client;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.space.SpaceNet;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
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
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Area Heist, client side (owner only): mouse input while selecting/placing, the animated selection box (glowing edges,
 * travelling sparks, pulsing corners, size label) and the translucent blue ghost of the stored blocks that follows
 * the aim (obstructed cells glow red).
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class HeistClient {
    private static final double RANGE = 64;
    private static final int MAX_SIDE = 16;
    private static final String K = "infinity.extra.area_heist.";

    private static int mode;
    private static @Nullable BlockPos a, b;
    private static int rot, dist = 10, stored, count;
    private static boolean mirror;
    private static int sx, sy, sz;
    private static int[] states = new int[0];

    private record Ghost(BlockPos off, BlockState state, int faces) {}
    private static @Nullable List<Ghost> ghosts;
    private static int gw, gh, gd;

    public static void accept(SpaceNet.HeistState s) {
        mode = s.mode();
        a = s.hasA() ? new BlockPos(s.ax(), s.ay(), s.az()) : null;
        b = s.hasB() ? new BlockPos(s.bx(), s.by(), s.bz()) : null;
        if (s.rot() != rot || s.mirror() != mirror) ghosts = null;
        rot = s.rot(); mirror = s.mirror(); dist = s.dist(); stored = s.index(); count = s.count();
        if (s.blocks()) { sx = s.sx(); sy = s.sy(); sz = s.sz(); states = s.states(); ghosts = null; }
        if (mode == 0) { states = new int[0]; ghosts = null; }
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { mode = 0; a = b = null; states = new int[0]; ghosts = null; }

    // ------------------------------------------------------------------ input

    private static boolean attackHeld;
    private static int lastAction = -1, lastTick;

    @SubscribeEvent
    public static void interaction(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (mode == 0 || mc.screen != null || mc.player == null) return;
        int action;
        if (e.isAttack()) action = 0;
        else if (e.isUseItem()) action = mc.player.isShiftKeyDown() ? 2 : 1;
        else if (e.isPickBlock()) action = 4;
        else return;
        e.setCanceled(true);
        e.setSwingHand(false);
        if (action == 0) { if (attackHeld) return; attackHeld = true; }
        if (action == lastAction && mc.player.tickCount - lastTick < 4) return;
        lastAction = action; lastTick = mc.player.tickCount;
        send(action, 0);
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mode != 2 || mc.screen != null || e.getScrollDeltaY() == 0 || !altDown(mc)) return;
        e.setCanceled(true);
        int d = e.getScrollDeltaY() > 0 ? 1 : -1;
        dist = Mth.clamp(dist + d, 3, 48);
        send(3, d);
    }

    private static boolean altDown(Minecraft mc) {
        long w = mc.getWindow().getWindow();
        return InputConstants.isKeyDown(w, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) || InputConstants.isKeyDown(w, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (attackHeld && !mc.options.keyAttack.isDown()) attackHeld = false;
    }

    private static void send(int action, int value) {
        BlockPos at = mode == 2 ? anchor(1F) : aimed(1F);
        PacketDistributor.sendToServer(new SpaceNet.HeistInput(action, value, at != null,
                at == null ? 0 : at.getX(), at == null ? 0 : at.getY(), at == null ? 0 : at.getZ()));
    }

    private static @Nullable BlockHitResult ray(float pt, double range) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        Vec3 eye = mc.player.getEyePosition(pt), look = mc.player.getViewVector(pt);
        return mc.level.clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
    }

    /** Block under the crosshair (selecting). */
    private static @Nullable BlockPos aimed(float pt) {
        BlockHitResult h = ray(pt, RANGE);
        return h == null || h.getType() != HitResult.Type.BLOCK ? null : h.getBlockPos();
    }

    /** Ghost anchor (placing): the free cell on the face hit within the distance, else the point at the distance. */
    private static @Nullable BlockPos anchor(float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        BlockHitResult h = ray(pt, dist);
        if (h != null && h.getType() == HitResult.Type.BLOCK) return h.getBlockPos().relative(h.getDirection());
        return BlockPos.containing(mc.player.getEyePosition(pt).add(mc.player.getViewVector(pt).scale(dist)));
    }

    // ------------------------------------------------------------------ ghost cache

    private static int[] dims() { return (rot & 1) == 0 ? new int[]{sx, sy, sz} : new int[]{sz, sy, sx}; }

    private static BlockPos transform(int x, int y, int z) {
        if (mirror) x = sx - 1 - x;
        int w = sx, d = sz;
        for (int r = 0; r < (rot & 3); r++) {
            int nx = d - 1 - z, nz = x;
            x = nx; z = nz;
            int t = w; w = d; d = t;
        }
        return new BlockPos(x, y, z);
    }

    private static BlockState transform(BlockState s) {
        if (mirror) s = s.mirror(net.minecraft.world.level.block.Mirror.FRONT_BACK);
        return switch (rot & 3) {
            case 1 -> s.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_90);
            case 2 -> s.rotate(net.minecraft.world.level.block.Rotation.CLOCKWISE_180);
            case 3 -> s.rotate(net.minecraft.world.level.block.Rotation.COUNTERCLOCKWISE_90);
            default -> s;
        };
    }

    private static List<Ghost> ghosts() {
        if (ghosts != null) return ghosts;
        int[] dm = dims();
        gw = dm[0]; gh = dm[1]; gd = dm[2];
        boolean[] filled = new boolean[Math.max(1, gw * gh * gd)];
        List<BlockPos> offs = new ArrayList<>();
        List<BlockState> sts = new ArrayList<>();
        if (states.length == sx * sy * sz) {
            for (int y = 0; y < sy; y++)
                for (int z = 0; z < sz; z++)
                    for (int x = 0; x < sx; x++) {
                        int id = states[(y * sz + z) * sx + x];
                        if (id < 0) continue;
                        BlockState st = Block.stateById(id);
                        if (st.isAir()) continue;
                        BlockPos o = transform(x, y, z);
                        filled[(o.getY() * gd + o.getZ()) * gw + o.getX()] = true;
                        offs.add(o);
                        sts.add(transform(st));
                    }
        }
        List<Ghost> out = new ArrayList<>(offs.size());
        for (int i = 0; i < offs.size(); i++) {
            BlockPos o = offs.get(i);
            int faces = 0;
            for (Direction d : Direction.values()) {
                int nx = o.getX() + d.getStepX(), ny = o.getY() + d.getStepY(), nz = o.getZ() + d.getStepZ();
                boolean inside = nx >= 0 && ny >= 0 && nz >= 0 && nx < gw && ny < gh && nz < gd && filled[(ny * gd + nz) * gw + nx];
                if (!inside || !sts.get(i).canOcclude()) faces |= 1 << d.ordinal();
            }
            out.add(new Ghost(o, sts.get(i), faces));
        }
        ghosts = out;
        return out;
    }

    // ------------------------------------------------------------------ world render

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || mode == 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        Camera cam = e.getCamera();
        Vec3 cp = cam.getPosition();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(-cp.x, -cp.y, -cp.z);
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        float time = (mc.level.getGameTime() % 100000L + pt) / 20F;
        int col = Stone.SPACE.color() & 0xFFFFFF;
        if (mode == 1) renderSelection(mc, ps, buf, cp, pt, time, col);
        else renderGhost(mc, ps, buf, cp, pt, time, col);
        ps.popPose();
    }

    private static void renderSelection(Minecraft mc, PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cp, float pt, float time, int col) {
        BlockPos aim = aimed(pt);
        BlockPos p1 = a != null ? a : aim, p2 = b != null ? b : aim;
        Matrix4f m = ps.last().pose();
        Glow g = new Glow(buf.getBuffer(RenderType.lightning()), m, cp).color(col);
        if (p1 == null) { buf.endBatch(RenderType.lightning()); return; }
        if (p2 == null) p2 = p1;
        Vec3 lo = new Vec3(Math.min(p1.getX(), p2.getX()), Math.min(p1.getY(), p2.getY()), Math.min(p1.getZ(), p2.getZ())).subtract(0.01, 0.01, 0.01);
        Vec3 hi = new Vec3(Math.max(p1.getX(), p2.getX()) + 1, Math.max(p1.getY(), p2.getY()) + 1, Math.max(p1.getZ(), p2.getZ()) + 1).add(0.01, 0.01, 0.01);
        int dx = Math.abs(p1.getX() - p2.getX()) + 1, dy = Math.abs(p1.getY() - p2.getY()) + 1, dz = Math.abs(p1.getZ() - p2.getZ()) + 1;
        boolean tooBig = dx > MAX_SIDE || dy > MAX_SIDE || dz > MAX_SIDE;
        if (tooBig) g.color(0xFF3040);
        boolean done = a != null && b != null;
        float pulse = 0.75F + 0.25F * Mth.sin(time * 6);
        g.box(lo, hi, done ? 0.05F : 0.035F, (done ? 1F : 0.75F) * pulse, time);
        g.faces(lo, hi, done ? 0.06F + 0.03F * Mth.sin(time * 3) : 0.03F);
        // scan plane sweeping up while the box is complete
        if (done) {
            double y = lo.y + (hi.y - lo.y) * ((time * 0.5) % 1.0);
            g.quad(new Vec3(lo.x, y, lo.z), new Vec3(hi.x, y, lo.z), new Vec3(hi.x, y, hi.z), new Vec3(lo.x, y, hi.z), 0.12F);
            g.line(new Vec3(lo.x, y, lo.z), new Vec3(hi.x, y, lo.z), 0.03F, 0.6F);
            g.line(new Vec3(lo.x, y, hi.z), new Vec3(hi.x, y, hi.z), 0.03F, 0.6F);
        }
        float cs = 0.18F + 0.06F * Mth.sin(time * 8);
        if (a != null) g.corner(Vec3.atCenterOf(a), cs, 1F);
        if (b != null) g.corner(Vec3.atCenterOf(b), cs, 1F);
        else if (aim != null) g.corner(Vec3.atCenterOf(aim), cs * 0.8F, 0.6F);
        buf.endBatch(RenderType.lightning());
        label(mc, ps, buf, new Vec3((lo.x + hi.x) / 2, hi.y + 0.6, (lo.z + hi.z) / 2),
                Component.literal(dx + " × " + dy + " × " + dz), tooBig ? 0xFFFF5060 : 0xFF000000 | lighten(col));
    }

    private static final RandomSource RAND = RandomSource.create();

    private static void renderGhost(Minecraft mc, PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 cp, float pt, float time, int col) {
        BlockPos anchor = anchor(pt);
        if (anchor == null || states.length == 0) return;
        List<Ghost> list = ghosts();
        BlockPos origin = anchor.offset(-gw / 2, 0, -gd / 2);
        float breathe = 0.38F + 0.1F * Mth.sin(time * 4);
        List<BlockPos> blocked = new ArrayList<>();
        // textured ghost on the block translucent layer; the plain wrapper keeps the vanilla vertex path under Sodium
        VertexConsumer vc = new Plain(buf.getBuffer(RenderType.translucent()));
        int quads = 0;
        for (Ghost g : list) {
            BlockPos w = origin.offset(g.off);
            BlockState cur = mc.level.getBlockState(w);
            if (!(cur.isAir() || cur.canBeReplaced()) || !mc.level.isInWorldBounds(w)) { blocked.add(w); continue; }
            if (g.state.getRenderShape() != RenderShape.MODEL || quads > 60000) continue;
            BakedModel model = mc.getBlockRenderer().getBlockModel(g.state);
            ps.pushPose();
            ps.translate(w.getX(), w.getY(), w.getZ());
            PoseStack.Pose pose = ps.last();
            for (Direction d : Direction.values()) {
                if ((g.faces & (1 << d.ordinal())) == 0) continue;
                RAND.setSeed(42L);
                for (BakedQuad q : model.getQuads(g.state, d, RAND)) { vc.putBulkData(pose, q, 0.55F, 0.78F, 1F, breathe, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY); quads++; }
            }
            RAND.setSeed(42L);
            for (BakedQuad q : model.getQuads(g.state, null, RAND)) { vc.putBulkData(pose, q, 0.55F, 0.78F, 1F, breathe, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY); quads++; }
            ps.popPose();
        }
        buf.endBatch(RenderType.translucent());
        Glow gl = new Glow(buf.getBuffer(RenderType.lightning()), ps.last().pose(), cp).color(col);
        // hologram silhouette of every exposed face (always visible, even when shader packs drop the textured layer)
        float holo = 0.10F + 0.05F * Mth.sin(time * 4);
        int faceCount = 0;
        for (Ghost g : list) {
            if (faceCount > 12000) break;
            BlockPos w = origin.offset(g.off);
            if (blocked.contains(w)) continue;
            double x0 = w.getX() + 0.01, y0 = w.getY() + 0.01, z0 = w.getZ() + 0.01, x1 = w.getX() + 0.99, y1 = w.getY() + 0.99, z1 = w.getZ() + 0.99;
            for (Direction d : Direction.values()) {
                if ((g.faces & (1 << d.ordinal())) == 0) continue;
                faceCount++;
                switch (d) {
                    case DOWN -> gl.quad(new Vec3(x0, y0, z0), new Vec3(x1, y0, z0), new Vec3(x1, y0, z1), new Vec3(x0, y0, z1), holo);
                    case UP -> gl.quad(new Vec3(x0, y1, z0), new Vec3(x0, y1, z1), new Vec3(x1, y1, z1), new Vec3(x1, y1, z0), holo);
                    case NORTH -> gl.quad(new Vec3(x0, y0, z0), new Vec3(x0, y1, z0), new Vec3(x1, y1, z0), new Vec3(x1, y0, z0), holo);
                    case SOUTH -> gl.quad(new Vec3(x0, y0, z1), new Vec3(x1, y0, z1), new Vec3(x1, y1, z1), new Vec3(x0, y1, z1), holo);
                    case WEST -> gl.quad(new Vec3(x0, y0, z0), new Vec3(x0, y0, z1), new Vec3(x0, y1, z1), new Vec3(x0, y1, z0), holo);
                    case EAST -> gl.quad(new Vec3(x1, y0, z0), new Vec3(x1, y1, z0), new Vec3(x1, y1, z1), new Vec3(x1, y0, z1), holo);
                }
            }
        }
        Vec3 lo = Vec3.atLowerCornerOf(origin).subtract(0.02, 0.02, 0.02);
        Vec3 hi = Vec3.atLowerCornerOf(origin.offset(gw, gh, gd)).add(0.02, 0.02, 0.02);
        gl.box(lo, hi, 0.04F, 0.85F, time);
        gl.faces(lo, hi, 0.035F);
        // rune-like rings under the ghost
        Vec3 base = new Vec3((lo.x + hi.x) / 2, lo.y + 0.03, (lo.z + hi.z) / 2);
        float rad = Math.max(gw, gd) * 0.72F + 0.5F;
        gl.ring(base, rad, 0.06F, 0.8F, time * 0.6F);
        gl.ring(base, rad * 0.8F, 0.04F, 0.6F, -time);
        gl.ring(base, rad * 1.12F, 0.03F, 0.4F, time * 0.3F);
        // forward arrow (rotation readout)
        gl.color(0xFF3040);
        for (int i = 0; i < Math.min(blocked.size(), 400); i++) {
            BlockPos w = blocked.get(i);
            gl.box(Vec3.atLowerCornerOf(w).add(0.05, 0.05, 0.05), Vec3.atLowerCornerOf(w).add(0.95, 0.95, 0.95), 0.02F, 0.8F, time);
        }
        buf.endBatch(RenderType.lightning());
        Component text = Component.translatable(K + "ghost_label", gw, gh, gd, rot * 90, mirror ? "⇆" : "", count);
        label(mc, ps, buf, new Vec3(base.x, hi.y + 0.6, base.z), text, blocked.isEmpty() ? 0xFF000000 | lighten(col) : 0xFFFF5060);
    }

    private static void label(Minecraft mc, PoseStack ps, MultiBufferSource.BufferSource buf, Vec3 at, Component text, int argb) {
        Font font = mc.font;
        ps.pushPose();
        ps.translate(at.x, at.y, at.z);
        ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        float s = 0.03F * (float) Math.max(1, Math.min(3, mc.gameRenderer.getMainCamera().getPosition().distanceTo(at) / 10));
        ps.scale(s, -s, s);
        float w = font.width(text) / -2F;
        font.drawInBatch(text, w, 0, argb, false, ps.last().pose(), buf, Font.DisplayMode.SEE_THROUGH, 0x60000000, LightTexture.FULL_BRIGHT);
        buf.endBatch();
        ps.popPose();
    }

    private static int lighten(int c) {
        int r = (((c >> 16) & 255) + 255) / 2, g = (((c >> 8) & 255) + 255) / 2, bb = ((c & 255) + 255) / 2;
        return (r << 16) | (g << 8) | bb;
    }

    // ------------------------------------------------------------------ HUD

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mode == 0 || mc.options.hideGui || mc.player == null) return;
        GuiGraphics g = e.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        int col = Stone.SPACE.color() & 0xFFFFFF;
        float pulse = 0.5F + 0.5F * Mth.sin(System.currentTimeMillis() / 300F);
        Component title = Component.translatable(mode == 1 ? K + "hud.select" : K + "hud.place");
        String key = mode == 1 ? (a == null ? K + "hud.select_a" : b == null ? K + "hud.select_b" : K + "hud.select_go") : K + "hud.place_keys";
        var lines = mc.font.split(Component.translatable(key, dist), 300);
        int tw = mc.font.width(title);
        for (var l : lines) tw = Math.max(tw, (int) (mc.font.width(l) * 0.75F));
        tw += 16;
        int bh = 14 + lines.size() * 8;
        int x = w / 2 - tw / 2, y = 6; // top centre: clear of the action bar and of the ghost in front of the player
        for (int i = 3; i >= 1; i--) g.fill(x - i, y - i, x + tw + i, y + bh + i, ((int) ((8 + 10 * pulse) * (4 - i)) << 24) | col);
        g.fill(x, y, x + tw, y + bh, 0xC0040A18);
        g.drawCenteredString(mc.font, title, w / 2, y + 3, 0xFF000000 | lighten(col));
        g.pose().pushPose();
        g.pose().translate(w / 2F, y + 14, 0);
        g.pose().scale(0.75F, 0.75F, 1);
        int ly = 0;
        for (var l : lines) { g.drawCenteredString(mc.font, l, 0, ly, 0xFFD6E8FF); ly += 11; }
        g.pose().popPose();
    }

    private record Plain(VertexConsumer d) implements VertexConsumer {
        @Override public VertexConsumer addVertex(float x, float y, float z) { d.addVertex(x, y, z); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { d.setColor(r, g, b, a); return this; }
        @Override public VertexConsumer setUv(float u, float v) { d.setUv(u, v); return this; }
        @Override public VertexConsumer setUv1(int u, int v) { d.setUv1(u, v); return this; }
        @Override public VertexConsumer setUv2(int u, int v) { d.setUv2(u, v); return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { d.setNormal(x, y, z); return this; }
    }

    private HeistClient() {}
}
