package com.infinitystones.tensura.client.will;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.wheel.WheelPayloads;
import com.infinitystones.tensura.will.Bodies;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.UUID;

/**
 * Client side of the Will grip: while the mode is on, mouse buttons and the scroll wheel drive the grip instead of
 * attacking / using items; beams of hard light are drawn for every holder in view (multiplayer).
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class WillClient {
    private static final float R = 1F, G = 0.22F, B = 0.3F; // Reality Stone hard light

    private static boolean holdingSomething() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        for (WheelPayloads.Beam b : WheelPayloads.willBeams) if (b.holder() == mc.player.getId()) return true;
        return false;
    }

    // ------------------------------------------------------------ input

    @SubscribeEvent
    public static void interaction(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (!WheelPayloads.willActive || mc.screen != null || mc.player == null) return;
        int action = e.isAttack() ? 0 : e.isUseItem() ? 1 : e.isPickBlock() ? 3 : -1;
        if (action < 0) return;
        e.setCanceled(true);
        e.setSwingHand(action == 1);
        if (action == 0) {                 // hold left = hold the target; releasing the button lets go
            if (gripButton) return;        // key repeat while held
            gripButton = true;
        }
        PacketDistributor.sendToServer(new WheelPayloads.WillInput(action, 0));
    }

    /** Left button is being held for a grip (released → action 6 = let go). */
    private static boolean gripButton;

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (!WheelPayloads.willActive || mc.screen != null || !holdingSomething()) return;
        e.setCanceled(true); // keep the hotbar where it is
        PacketDistributor.sendToServer(new WheelPayloads.WillInput(2, (float) Math.signum(e.getScrollDeltaY())));
    }

    // ------------------------------------------------------------ rotate (Alt + mouse, like the Physics Staff)

    private static double lastMx = Double.NaN, lastMy = Double.NaN;

    private static boolean rotating(Minecraft mc) {
        if (!WheelPayloads.willActive || mc.screen != null || !holdingSomething()) return false;
        long w = mc.getWindow().getWindow();
        return com.mojang.blaze3d.platform.InputConstants.isKeyDown(w, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT)
                || com.mojang.blaze3d.platform.InputConstants.isKeyDown(w, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT);
    }

    /** While rotating, the camera stays still (sensitivity -1/3 cancels the turn formula). */
    @SubscribeEvent
    public static void turn(net.neoforged.neoforge.client.event.CalculatePlayerTurnEvent e) {
        if (rotating(Minecraft.getInstance())) e.setMouseSensitivity(-1.0 / 3.0);
    }

    @SubscribeEvent
    public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (gripButton && !mc.options.keyAttack.isDown()) {
            gripButton = false;
            if (WheelPayloads.willActive) PacketDistributor.sendToServer(new WheelPayloads.WillInput(6, 0));
        }
        double mx = mc.mouseHandler.xpos(), my = mc.mouseHandler.ypos();
        if (rotating(mc) && !Double.isNaN(lastMx)) {
            float dx = (float) (mx - lastMx) * 0.35F, dy = (float) (my - lastMy) * 0.35F;
            if (Math.abs(dx) > 0.05F) PacketDistributor.sendToServer(new WheelPayloads.WillInput(4, Mth.clamp(dx, -10, 10)));
            if (Math.abs(dy) > 0.05F) PacketDistributor.sendToServer(new WheelPayloads.WillInput(5, Mth.clamp(dy, -10, 10)));
        }
        lastMx = mx; lastMy = my;
    }

    // ------------------------------------------------------------ HUD

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (!(WheelPayloads.willActive || ConstructClient.designing()) || mc.options.hideGui || mc.player == null) return;
        GuiGraphics g = e.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        long t = System.currentTimeMillis();
        float pulse = 0.5F + 0.5F * Mth.sin(t / 300F);
        Component title = Component.translatable("infinity.will.hud.title");
        String key = ConstructClient.designing() ? "infinity.will.hud.design" : holdingSomething() ? "infinity.will.hud.holding" : "infinity.will.hud.idle";
        var lines = mc.font.split(Component.translatable(key, ConstructClient.designCount()), 260);
        int tw = mc.font.width(title);
        for (var l : lines) tw = Math.max(tw, (int) (mc.font.width(l) * 0.75F));
        tw += 16;
        int bh = 14 + lines.size() * 8;
        int x = w / 2 - tw / 2, y = h - 50 - bh;
        for (int i = 3; i >= 1; i--) g.fill(x - i, y - i, x + tw + i, y + bh + i, ((int) ((8 + 10 * pulse) * (4 - i)) << 24) | 0xFF3346);
        g.fill(x, y, x + tw, y + bh, 0xC0140406);
        g.drawCenteredString(mc.font, title, w / 2, y + 3, 0xFFFF8A94);
        g.pose().pushPose();
        g.pose().translate(w / 2F, y + 14, 0);
        g.pose().scale(0.75F, 0.75F, 1);
        int ly = 0;
        for (var l : lines) { g.drawCenteredString(mc.font, l, 0, ly, 0xFFFFD0D4); ly += 11; }
        g.pose().popPose();
    }

    // ------------------------------------------------------------ beams

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || WheelPayloads.willBeams.isEmpty()) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        Camera cam = e.getCamera();
        Vec3 cp = cam.getPosition();
        PoseStack ps = e.getPoseStack();
        ps.pushPose();
        ps.translate(-cp.x, -cp.y, -cp.z);
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buf.getBuffer(RenderType.lightning());
        Matrix4f m = ps.last().pose();
        float time = (mc.level.getGameTime() + pt) / 20F;
        for (WheelPayloads.Beam b : WheelPayloads.willBeams) {
            boolean locked = b.holder() < 0;
            Entity holder = mc.level.getEntity(locked ? -b.holder() - 1 : b.holder());
            Vec3 end;
            AABB box = null;
            double size;
            if (b.entity() >= 0) {
                Entity t = mc.level.getEntity(b.entity());
                if (t == null) continue;
                Vec3 off = t.getPosition(pt).subtract(t.position());
                box = t.getBoundingBox().move(off).inflate(0.08);
                end = box.getCenter();
                size = Math.max(0.6, Math.max(box.getXsize(), box.getYsize()) * 0.75);
            } else {
                UUID id;
                try { id = UUID.fromString(b.body()); } catch (Exception ex) { continue; }
                end = Bodies.center(mc.level, id, pt);
                if (end == null) continue;
                AABB wb = Bodies.bounds(mc.level, id);
                size = wb == null ? 1.5 : Math.max(1, Math.min(12, Math.max(wb.getXsize(), Math.max(wb.getYsize(), wb.getZsize())) * 0.62));
            }
            if (end.distanceToSqr(cp) > 160 * 160) continue;
            if (locked) { lockSeal(vc, m, end, size, time); if (box != null) cage(vc, m, box, cp, time); continue; }
            if (!(holder instanceof Player hp)) continue;
            Vec3 start = hand(mc, hp, cam, pt);
            Vec3 dir = end.subtract(start);
            beam(vc, m, start, end, cp, time);
            // a small sigil at the fist and a seal behind the target, both facing along the beam
            HardLightFx.sigil(vc, m, start.add(dir.normalize().scale(0.05)), dir, 0.07, time, 0.75F);
            HardLightFx.seal(vc, m, end.subtract(dir.normalize().scale(size * 0.2)), dir, size * 1.05, time, 0.55F, 1);
            if (box != null) cage(vc, m, box, cp, time);
        }
        buf.endBatch(RenderType.lightning());
        ps.popPose();
    }

    // ------------------------------------------------------------ free 3D turn of held mobs / players (all clients)

    private static final java.util.Map<Integer, org.joml.Quaternionf> SHOWN = new java.util.HashMap<>();
    private static final java.util.Set<Integer> PUSHED = new java.util.HashSet<>();

    private static org.joml.Quaternionf targetTurn(int entityId) {
        for (WheelPayloads.Beam b : WheelPayloads.willBeams) {
            if (b.entity() != entityId || !b.body().startsWith("q:")) continue;
            String[] p = b.body().substring(2).split(",");
            if (p.length != 4) return null;
            try { return new org.joml.Quaternionf(Float.parseFloat(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2]), Float.parseFloat(p[3])).normalize(); }
            catch (NumberFormatException ex) { return null; }
        }
        return null;
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.LOWEST)
    public static void turnPre(net.neoforged.neoforge.client.event.RenderLivingEvent.Pre<?, ?> e) {
        if (SHOWN.isEmpty() && WheelPayloads.willBeams.isEmpty()) return;
        int id = e.getEntity().getId();
        org.joml.Quaternionf target = targetTurn(id);
        org.joml.Quaternionf shown = SHOWN.get(id);
        if (target == null && shown == null) return;
        if (shown == null) shown = new org.joml.Quaternionf();
        // eased toward the server's turn; once let go it rights itself smoothly
        shown.slerp(target == null ? new org.joml.Quaternionf() : target, 0.2F).normalize();
        if (target == null && Math.abs(shown.w) > 0.9995F) { SHOWN.remove(id); return; }
        SHOWN.put(id, shown);
        float h = e.getEntity().getBbHeight() / 2F;
        e.getPoseStack().pushPose();
        e.getPoseStack().translate(0, h, 0);
        e.getPoseStack().mulPose(shown);
        e.getPoseStack().translate(0, -h, 0);
        PUSHED.add(id);
    }

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST, receiveCanceled = true)
    public static void turnPost(net.neoforged.neoforge.client.event.RenderLivingEvent.Post<?, ?> e) {
        if (PUSHED.remove(e.getEntity().getId())) e.getPoseStack().popPose();
    }

    /** Lock: a horizontal seal through the target and two gyroscope seals turning around it (frozen in hard light). */
    private static void lockSeal(VertexConsumer vc, Matrix4f m, Vec3 c, double size, float time) {
        HardLightFx.seal(vc, m, c, new Vec3(0, 1, 0), size * 1.25, time, 0.7F, 1);
        double a = time * 0.7;
        HardLightFx.seal(vc, m, c, new Vec3(Math.cos(a), 0, Math.sin(a)), size * 1.05, time, 0.35F, -1);
        HardLightFx.seal(vc, m, c, new Vec3(-Math.sin(a), 0.35, Math.cos(a)), size * 0.95, time, 0.3F, 1);
    }

    /** Where the beam leaves: the right hand (first person: just below and right of the view). */
    private static Vec3 hand(Minecraft mc, Player p, Camera cam, float pt) {
        if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
            Vec3 look = new Vec3(cam.getLookVector()), left = new Vec3(cam.getLeftVector()), up = new Vec3(cam.getUpVector());
            return cam.getPosition().add(look.scale(0.6)).subtract(left.scale(0.38)).subtract(up.scale(0.32));
        }
        float yaw = Mth.lerp(pt, p.yBodyRotO, p.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 base = p.getPosition(pt).add(0, p.getBbHeight() * 0.62, 0);
        return base.add(-Mth.cos(yaw) * 0.38, 0, -Mth.sin(yaw) * 0.38).add(-Mth.sin(yaw) * 0.25, 0, Mth.cos(yaw) * 0.25);
    }

    /** Camera-facing ribbon: wide soft glow + bright core + two spiralling strands. */
    private static void beam(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 cam, float time) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.05) return;
        Vec3 dir = d.scale(1 / len);
        Vec3 side0 = dir.cross(new Vec3(0, 1, 0));
        if (side0.lengthSqr() < 1e-4) side0 = dir.cross(new Vec3(1, 0, 0));
        side0 = side0.normalize();
        Vec3 up0 = side0.cross(dir).normalize();
        int segs = Math.max(8, (int) (len * 3));
        float flicker = 0.85F + 0.15F * Mth.sin(time * 37F);
        for (int i = 0; i < segs; i++) {
            double t0 = (double) i / segs, t1 = (double) (i + 1) / segs;
            Vec3 p0 = a.add(d.scale(t0)).add(wobble(up0, side0, t0, time, len)), p1 = a.add(d.scale(t1)).add(wobble(up0, side0, t1, time, len));
            // taper near the hand: the beam is born thin at the fist and widens over the first metre or two
            double reach = Math.min(1, (t0 * len) / 1.6);
            float k = (float) (0.22 + 0.78 * reach * reach * (3 - 2 * reach));
            ribbon(vc, m, p0, p1, cam, 0.20F * k, 0.16F * flicker);
            ribbon(vc, m, p0, p1, cam, 0.07F * k, 0.55F * flicker);
            ribbon(vc, m, p0, p1, cam, 0.025F, 0.9F);
            for (int s = 0; s < 2; s++) {
                double ang0 = time * 9 + t0 * len * 2.4 + s * Math.PI, ang1 = time * 9 + t1 * len * 2.4 + s * Math.PI;
                double r0 = 0.13 * Math.sin(Math.PI * t0) + 0.02, r1 = 0.13 * Math.sin(Math.PI * t1) + 0.02;
                Vec3 h0 = p0.add(side0.scale(Math.cos(ang0) * r0)).add(up0.scale(Math.sin(ang0) * r0));
                Vec3 h1 = p1.add(side0.scale(Math.cos(ang1) * r1)).add(up0.scale(Math.sin(ang1) * r1));
                ribbon(vc, m, h0, h1, cam, 0.03F, 0.6F);
            }
        }
    }

    private static Vec3 wobble(Vec3 up, Vec3 side, double t, float time, double len) {
        double env = Math.sin(Math.PI * t) * Math.min(0.12, len * 0.01);
        return up.scale(Math.sin(t * 9 + time * 6) * env).add(side.scale(Math.cos(t * 7 - time * 5) * env));
    }

    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 p0, Vec3 p1, Vec3 cam, float halfWidth, float alpha) {
        Vec3 seg = p1.subtract(p0);
        Vec3 toCam = cam.subtract(p0.add(seg.scale(0.5)));
        Vec3 n = seg.cross(toCam);
        if (n.lengthSqr() < 1e-8) return;
        n = n.normalize().scale(halfWidth);
        quad(vc, m, p0.add(n), p1.add(n), p1.subtract(n), p0.subtract(n), alpha);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 c, Vec3 d, float alpha) {
        vc.addVertex(m, (float) a.x, (float) a.y, (float) a.z).setColor(R, G, B, alpha);
        vc.addVertex(m, (float) b.x, (float) b.y, (float) b.z).setColor(R, G, B, alpha);
        vc.addVertex(m, (float) c.x, (float) c.y, (float) c.z).setColor(R, G, B, alpha);
        vc.addVertex(m, (float) d.x, (float) d.y, (float) d.z).setColor(R, G, B, alpha);
    }

    /** Hard-light cage around a held entity: 12 glowing edges. */
    private static void cage(VertexConsumer vc, Matrix4f m, AABB bb, Vec3 cam, float time) {
        float a = 0.45F + 0.2F * Mth.sin(time * 8);
        double[] xs = {bb.minX, bb.maxX}, ys = {bb.minY, bb.maxY}, zs = {bb.minZ, bb.maxZ};
        for (double y : ys) for (double z : zs) ribbon(vc, m, new Vec3(bb.minX, y, z), new Vec3(bb.maxX, y, z), cam, 0.025F, a);
        for (double x : xs) for (double z : zs) ribbon(vc, m, new Vec3(x, bb.minY, z), new Vec3(x, bb.maxY, z), cam, 0.025F, a);
        for (double x : xs) for (double y : ys) ribbon(vc, m, new Vec3(x, y, bb.minZ), new Vec3(x, y, bb.maxZ), cam, 0.025F, a);
    }

    @SuppressWarnings("unused") private static void unused() { LevelRenderer.class.getName(); }

    private WillClient() {}
}
