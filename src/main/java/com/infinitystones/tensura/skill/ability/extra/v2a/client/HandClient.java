package com.infinitystones.tensura.skill.ability.extra.v2a.client;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.client.wheel.AimMode;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.wheel.WheelPayloads;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import com.infinitystones.tensura.skill.ability.extra.v2a.V2aAnim;
import com.infinitystones.tensura.skill.ability.extra.v2a.V2aNet;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.minecraft.client.player.Input;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client side of the Creator's Hand: the cutscene of whoever is held in the fist (camera shot placed behind the caster's
 * back, every input locked, letterbox), the rooting of the caster, and the gore burst of an execution (pieces that
 * carry the victim's own texture, fly with physics and poof when they touch the ground).
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class HandClient {
    // ------------------------------------------------------------------ lock state

    private static int mode, ttl, held, force = 5, ready, holdTicks;
    private static double sx, sy, sz;
    private static boolean sInit;
    private static long lastMs;
    private static float shotYaw, dist, bars, barsPrev;
    private static CameraType saved;

    public static void lock(V2aNet.Lock m) {
        Minecraft mc = Minecraft.getInstance();
        if (m.mode() == 0) { release(mc); return; }
        boolean fresh = mode != m.mode();
        mode = m.mode();
        ttl = 55;
        shotYaw = m.shotYaw();
        dist = m.dist();
        force = m.force();
        ready = m.ready();
        if (fresh) {
            held = 0;
            sInit = false;
            holdTicks = 0;
            if (mode == 1) {
                if (saved == null) saved = mc.options.getCameraType();
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                if (mc.screen != null && !allowedScreen(mc.screen)) mc.setScreen(null);
            }
        }
    }

    private static void release(Minecraft mc) {
        mode = 0;
        sInit = false;
        holdTicks = 0;
        if (saved != null) {
            if (mc.options != null) mc.options.setCameraType(saved);
            saved = null;
        }
    }

    public static void targets(V2aNet.Targets m) {
        Minecraft.getInstance().setScreen(new HandTargetScreen(m.list()));
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        release(Minecraft.getInstance());
        PIECES.clear();
        HIDDEN.clear();
        bars = barsPrev = 0;
    }

    @SubscribeEvent
    public static void tickPre(ClientTickEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (mode != 0) release(mc);
            return;
        }
        if (mc.isPaused()) return;
        if (mode != 0 && --ttl <= 0) release(mc);
        if (mode == 0) return;
        held++;
        if (mode == 2) { casterTick(mc); return; }
        if (mode != 1) return;
        if (mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        for (KeyMapping k : mc.options.keyMappings) {
            if (k == mc.options.keyChat || k == mc.options.keyCommand) continue;
            while (k.consumeClick()) { }
            k.setDown(false);
        }
        if (mc.screen != null && !allowedScreen(mc.screen)) mc.setScreen(null);
        face(mc.player);
    }

    @SubscribeEvent
    public static void tickPost(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        barsPrev = bars;
        bars = Mth.clamp(bars + (mode == 1 ? 0.1F : -0.1F), 0, 1);
        HIDDEN.replaceAll((k, v) -> v - 1);
        HIDDEN.values().removeIf(v -> v <= 0);
        physics(mc.level);
    }

    private static Minecraft mc0() { return Minecraft.getInstance(); }
    private static void send(int action) { PacketDistributor.sendToServer(new V2aNet.Input(action)); }
    private static boolean physDown(KeyMapping k) {
        long win = Minecraft.getInstance().getWindow().getWindow();
        InputConstants.Key key = k.getKey();
        if (key.getType() == InputConstants.Type.MOUSE) return GLFW.glfwGetMouseButton(win, key.getValue()) == GLFW.GLFW_PRESS;
        return InputConstants.isKeyDown(win, key.getValue());
    }
    /** The caster while the fist holds: left/right are swallowed (squeeze / release), the held button keeps squeezing. */
    private static void casterTick(Minecraft mc) {
        if (mc.screen != null || AimMode.active() || ready == 0) { holdTicks = 0; return; }
        boolean left = physDown(mc.options.keyAttack);
        for (KeyMapping k : new KeyMapping[]{mc.options.keyAttack, mc.options.keyUse}) {
            while (k.consumeClick()) { }
            k.setDown(false);
        }
        if (left && (ready & 1) != 0) {
            holdTicks++;
            if (holdTicks >= 8 && holdTicks % 3 == 0) send(1);
        } else holdTicks = 0;
    }
    /** The held player faces the camera and cannot turn. */
    private static void face(LocalPlayer p) {
        float yaw = shotYaw + 180F;
        p.setYRot(yaw); p.yRotO = yaw;
        p.setXRot(0F); p.xRotO = 0F;
        p.yHeadRot = yaw; p.yHeadRotO = yaw;
        p.yBodyRot = yaw; p.yBodyRotO = yaw;
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (mode == 1 && mc.player != null) face(mc.player);
    }

    @SubscribeEvent
    public static void move(MovementInputUpdateEvent e) {
        if (mode == 0) return;
        Input i = e.getInput();
        i.up = i.down = i.left = i.right = false;
        i.jumping = false;
        i.shiftKeyDown = false;
        i.forwardImpulse = 0;
        i.leftImpulse = 0;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interaction(InputEvent.InteractionKeyMappingTriggered e) {
        if (mode == 2 && ready != 0 && mc0().screen == null && !AimMode.active()) {
         if (e.isAttack()) {
          if ((ready & 1) != 0) send(1);
          e.setCanceled(true); e.setSwingHand(false);
         } else if (e.isUseItem()) {
          send(2);
          e.setCanceled(true); e.setSwingHand(false);
         }
         return;
        }
        if (mode != 1) return;
        e.setCanceled(true);
        e.setSwingHand(false);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void scroll(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        if (mode == 1) { e.setCanceled(true); return; }
        if (mode == 2 && (ready & 1) != 0 && !AimMode.active() && e.getScrollDeltaY() != 0) {
         e.setCanceled(true);
         int dir = e.getScrollDeltaY() > 0 ? 1 : -1;
         force = Mth.clamp(force + dir, 1, 10);
         PacketDistributor.sendToServer(new WheelPayloads.Setting(Stone.POWER.ordinal(), "creator_hand", dir));
         mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 0.8F + 0.08F * force, 0.4F));
        }
    }

    @SubscribeEvent
    public static void screen(ScreenEvent.Opening e) {
        if (mode != 1) return;
        Screen n = e.getNewScreen();
        if (n != null && !allowedScreen(n)) e.setCanceled(true);
    }

    /** Chat and the pause menu (and what opens from it) stay available. */
    private static boolean allowedScreen(Screen s) {
        if (s instanceof ChatScreen || s instanceof PauseScreen || s instanceof net.minecraft.client.gui.screens.DeathScreen) return true;
        if (s instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen) return false;
        return s.getClass().getName().startsWith("net.minecraft.client.gui.screens.") && Minecraft.getInstance().screen != null;
    }

    @SubscribeEvent
    public static void hud(RenderGuiLayerEvent.Pre e) {
        if (mode == 1 && !VanillaGuiLayers.CHAT.equals(e.getName())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void bars(RenderGuiEvent.Post e) {
        if (mode == 2 && ready != 0) hint(e.getGuiGraphics());
        float k = Mth.lerp(e.getPartialTick().getGameTimeDeltaPartialTick(true), barsPrev, bars);
        if (k <= 0.01F) return;
        GuiGraphics g = e.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        int bar = (int) (h * 0.11F * V2aAnim.ease(k));
        g.fillGradient(0, bar, w, bar + h / 6, ((int) (70 * k) << 24) | 0x2A0000, 0x00000000);
        g.fillGradient(0, h - bar - h / 6, w, h - bar, 0x00000000, ((int) (70 * k) << 24) | 0x2A0000);
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
    }

    private static void hint(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        int w = g.guiWidth(), h = g.guiHeight(), y = h - 74;
        var font = mc.font;
        Component line = (ready & 1) != 0 ? Component.translatable("infinity.v2a.hand.hint", force)
                : Component.translatable("infinity.v2a.hand.hint.wait");
        int tw = font.width(line);
        g.fill(w / 2 - tw / 2 - 6, y - 4, w / 2 + tw / 2 + 6, y + 12, 0x88000000);
        g.drawString(font, line, w / 2 - tw / 2, y, 0xFFE9A8, true);
        if ((ready & 1) != 0) {
            int bx = w / 2 - 50;
            for (int i = 0; i < 10; i++) {
                int c = i < force ? (i >= 9 ? 0xFFE23B3B : 0xFFF2C14A) : 0x66333333;
                g.fill(bx + i * 10, y + 15, bx + i * 10 + 8, y + 20, c);
            }
        }
    }
    /** Camera of the held player: over the caster's shoulder, a few blocks back and up, looking at the fist. */
    public static double[] cameraShot(float pt) {
        if (mode != 1) return null;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return null;
        double tx = Mth.lerp(pt, p.xo, p.getX()), ty = Mth.lerp(pt, p.yo, p.getY()) + p.getBbHeight() * 0.55, tz = Mth.lerp(pt, p.zo, p.getZ());
        long now = net.minecraft.Util.getMillis();
        if (!sInit) { sx = tx; sy = ty; sz = tz; sInit = true; lastMs = now; }
        double dt = Mth.clamp((now - lastMs) / 1000.0, 0, 0.1);
        lastMs = now;
        double f = 1 - Math.exp(-dt * 4.5);
        sx += (tx - sx) * f; sy += (ty - sy) * f; sz += (tz - sz) * f;
        double fx = sx, fy = sy, fz = sz;
        float tt = held + pt;
        double d = dist * (1.14 - 0.14 * V2aAnim.ease(tt / 70F));
        double sy = Math.toRadians(shotYaw) + Math.sin(tt * 0.02) * 0.05;
        double lx = -Math.sin(sy), lz = Math.cos(sy), rx = -Math.cos(sy), rz = -Math.sin(sy);
        Vec3 focus = new Vec3(fx, fy, fz);
        Vec3 cam = new Vec3(fx - lx * d + rx * d * 0.1, fy + 1.5 + 0.2 * d, fz - lz * d + rz * d * 0.1);
        BlockHitResult hit = mc.level.clip(new ClipContext(focus, cam, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, p));
        if (hit.getType() != HitResult.Type.MISS) cam = hit.getLocation().subtract(cam.subtract(focus).normalize().scale(0.35));
        Vec3 look = focus.subtract(cam);
        float yaw = (float) (Math.atan2(-look.x, look.z) * 180.0 / Math.PI);
        float pitch = (float) -(Math.atan2(look.y, Math.hypot(look.x, look.z)) * 180.0 / Math.PI);
        return new double[]{cam.x, cam.y, cam.z, yaw, pitch};
    }

    // ------------------------------------------------------------------ gore

    private static final class Piece {
        double x, y, z, px, py, pz, vx, vy, vz;
        float half, hx, hy, hz, rx, ry, srx, sry, u0, v0, u1, v1, cr, cg, cb;
        int age, life;
        boolean trail;
        ResourceLocation tex;
    }

    private static final List<Piece> PIECES = new ArrayList<>();
    private static final Map<Integer, Integer> HIDDEN = new HashMap<>();

    private static final ResourceLocation[] FLESH = {
            ResourceLocation.withDefaultNamespace("textures/block/red_concrete.png"),
            ResourceLocation.withDefaultNamespace("textures/block/nether_wart_block.png"),
            ResourceLocation.withDefaultNamespace("textures/block/red_nether_bricks.png")};
    /** Opaque parts of a player skin (px of 64x64): face, chest, arm, leg. */
    private static final int[][] SKIN = {{8, 8}, {20, 20}, {44, 20}, {4, 20}, {20, 24}, {44, 24}};

    @SuppressWarnings("unchecked")
    private static ResourceLocation textureOf(Entity e) {
        EntityRenderer<? super Entity> r = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(e);
        return ((EntityRenderer<Entity>) r).getTextureLocation(e);
    }

    public static void gore(V2aNet.Gore g) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel lv = mc.level;
        if (lv == null) return;
        Entity ent = lv.getEntity(g.entity());
        ResourceLocation tex = null;
        if (ent != null) {
            try { tex = textureOf(ent); } catch (RuntimeException | LinkageError ignored) { tex = null; }
        }
        boolean skin = ent instanceof AbstractClientPlayer;
        HIDDEN.put(g.entity(), 240);
        if (ent != null && ent != mc.player) lv.removeEntity(ent.getId(), Entity.RemovalReason.KILLED);

        RandomSource rnd = lv.random;
        float size = Math.max(g.w(), g.h());
        int n = Mth.clamp((int) (24 + g.w() * g.w() * g.h() * 6), 24, 90);
        bodyParts(lv, g, ent, tex, skin);
        float base = Mth.clamp(size * 0.1F, 0.07F, 0.5F);
        for (int i = 0; i < n && PIECES.size() < 700; i++) {
            Piece p = new Piece();
            p.x = p.px = g.x() + (rnd.nextDouble() - 0.5) * g.w() * 0.8;
            p.y = p.py = g.y() + (rnd.nextDouble() - 0.5) * g.h() * 0.8;
            p.z = p.pz = g.z() + (rnd.nextDouble() - 0.5) * g.w() * 0.8;
            Vec3 dir = new Vec3(rnd.nextGaussian(), rnd.nextGaussian() * 0.6 + 0.35, rnd.nextGaussian());
            dir = dir.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : dir.normalize();
            double sp = (0.22 + rnd.nextDouble() * 0.55) * (0.7 + Math.min(1.2, size * 0.12));
            p.vx = dir.x * sp + (p.x - g.x()) * 0.06;
            p.vy = dir.y * sp + 0.12;
            p.vz = dir.z * sp + (p.z - g.z()) * 0.06;
            p.half = base * (0.5F + rnd.nextFloat()) * 0.5F;
            p.hx = p.hy = p.hz = p.half;
            p.rx = rnd.nextFloat() * 360F; p.ry = rnd.nextFloat() * 360F;
            p.srx = (rnd.nextFloat() - 0.5F) * 40F; p.sry = (rnd.nextFloat() - 0.5F) * 40F;
            p.life = 70 + rnd.nextInt(40);
            p.trail = rnd.nextFloat() < 0.3F;
            float roll = rnd.nextFloat();
            if (tex != null && roll < 0.5F) {
                p.tex = tex;
                if (skin) {
                    int[] uv = SKIN[rnd.nextInt(SKIN.length)];
                    p.u0 = uv[0] / 64F; p.v0 = uv[1] / 64F; p.u1 = p.u0 + 4 / 64F; p.v1 = p.v0 + 4 / 64F;
                } else {
                    p.u0 = rnd.nextFloat() * 0.9F; p.v0 = rnd.nextFloat() * 0.9F; p.u1 = p.u0 + 0.0625F; p.v1 = p.v0 + 0.0625F;
                }
                float t = 0.75F + rnd.nextFloat() * 0.25F;
                p.cr = t; p.cg = t * 0.78F; p.cb = t * 0.78F;
            } else {
                p.tex = FLESH[rnd.nextInt(FLESH.length)];
                p.u0 = rnd.nextBoolean() ? 0F : 0.5F; p.v0 = rnd.nextBoolean() ? 0F : 0.5F; p.u1 = p.u0 + 0.5F; p.v1 = p.v0 + 0.5F;
                float t = roll < 0.85F ? 0.8F + rnd.nextFloat() * 0.2F : 0.35F;
                p.cr = t; p.cg = t * 0.8F; p.cb = t * 0.8F;
            }
            PIECES.add(p);
        }

        DustParticleOptions blood = new DustParticleOptions(new Vector3f(0.55F, 0.02F, 0.02F), 1.8F);
        DustParticleOptions dark = new DustParticleOptions(new Vector3f(0.16F, 0F, 0F), 1.4F);
        for (int i = 0; i < 90; i++) {
            Vec3 d = new Vec3(rnd.nextGaussian(), rnd.nextGaussian() * 0.7, rnd.nextGaussian()).scale(0.22);
            lv.addParticle(i % 3 == 0 ? dark : blood, g.x() + d.x * 2, g.y() + d.y * 2, g.z() + d.z * 2, d.x, d.y + 0.05, d.z);
        }
        lv.addParticle(ParticleTypes.EXPLOSION, g.x(), g.y(), g.z(), 0, 0, 0);
        DustParticleOptions gold = new DustParticleOptions(new Vector3f(1F, 0.78F, 0.2F), 2.2F);
        for (int i = 0; i < 60; i++) {
         Vec3 d = new Vec3(rnd.nextGaussian(), rnd.nextGaussian() * 0.8, rnd.nextGaussian()).scale(0.3);
         lv.addParticle(gold, g.x(), g.y(), g.z(), d.x, d.y, d.z);
        }
        for (int i = 0; i < 12; i++) lv.addParticle(ParticleTypes.END_ROD, g.x(), g.y(), g.z(), rnd.nextGaussian() * 0.25, rnd.nextGaussian() * 0.25 + 0.1, rnd.nextGaussian() * 0.25);
        lv.addParticle(ParticleTypes.FLASH, g.x(), g.y(), g.z(), 0, 0, 0);
        for (int i = 0; i < 14; i++)
            lv.addParticle(ParticleTypes.LARGE_SMOKE, g.x() + (rnd.nextDouble() - 0.5) * g.w(), g.y() + (rnd.nextDouble() - 0.5) * g.h(),
                    g.z() + (rnd.nextDouble() - 0.5) * g.w(), 0, 0.03, 0);
        lv.playLocalSound(g.x(), g.y(), g.z(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.5F, 0.5F, false);
    }

    private static void part(RandomSource rnd, V2aNet.Gore g, ResourceLocation tex, double ox, double oy, double oz,
            float hx, float hy, float hz, float u0, float v0, float u1, float v1, float yaw, float power) {
        Piece p = new Piece();
        p.x = p.px = g.x() + ox; p.y = p.py = g.y() + oy; p.z = p.pz = g.z() + oz;
        Vec3 dir = new Vec3(ox, oy * 0.5, oz).scale(1.5).add(rnd.nextGaussian() * 0.45, 0.45 + rnd.nextDouble() * 0.55, rnd.nextGaussian() * 0.45);
        dir = dir.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : dir.normalize();
        double sp = (0.32 + rnd.nextDouble() * 0.3) * power;
        p.vx = dir.x * sp; p.vy = dir.y * sp + 0.12; p.vz = dir.z * sp;
        p.hx = hx; p.hy = hy; p.hz = hz;
        p.half = Math.max(hx, Math.max(hy, hz));
        p.ry = yaw;
        p.srx = (rnd.nextFloat() - 0.5F) * 34F; p.sry = (rnd.nextFloat() - 0.5F) * 34F;
        p.life = 90 + rnd.nextInt(30);
        p.trail = true;
        p.tex = tex;
        p.u0 = u0; p.v0 = v0; p.u1 = u1; p.v1 = v1;
        p.cr = 1F; p.cg = 0.86F; p.cb = 0.86F;
        PIECES.add(p);
    }
    /** The body itself bursts in chunks: head, torso, arms and legs of a player (cut out of the skin), big pieces of its texture otherwise. */
    private static void bodyParts(ClientLevel lv, V2aNet.Gore g, Entity ent, ResourceLocation tex, boolean skin) {
        RandomSource rnd = lv.random;
        float yaw = ent == null ? rnd.nextFloat() * 360F : -ent.getYRot();
        float power = 0.8F + Math.min(1F, Math.max(g.w(), g.h()) * 0.15F);
        if (skin && tex != null) {
            float k = g.h() / 1.8F;
            float s = 1F / 64F;
            // head, torso, two arms, two legs: {ox, oy, hx, hy, hz, u, v, w, h}
            float[][] parts = {
                {0, 0.735F, 0.235F, 0.235F, 0.235F, 8, 8, 8, 8},
                {0, 0.15F, 0.235F, 0.35F, 0.12F, 20, 20, 8, 12},
                {0.34F, 0.15F, 0.1F, 0.35F, 0.1F, 44, 20, 4, 12},
                {-0.34F, 0.15F, 0.1F, 0.35F, 0.1F, 44, 20, 4, 12},
                {0.12F, -0.55F, 0.1F, 0.35F, 0.1F, 4, 20, 4, 12},
                {-0.12F, -0.55F, 0.1F, 0.35F, 0.1F, 4, 20, 4, 12}};
            for (float[] q : parts)
                part(rnd, g, tex, q[0] * k, q[1] * k, 0, q[2] * k, q[3] * k, q[4] * k, q[5] * s, q[6] * s, (q[5] + q[7]) * s, (q[6] + q[8]) * s, yaw, power);
            return;
        }
        int n = 7;
        for (int i = 0; i < n; i++) {
            ResourceLocation t = tex != null && i % 4 != 3 ? tex : FLESH[rnd.nextInt(FLESH.length)];
            float u = tex != null && t == tex ? rnd.nextFloat() * 0.7F : 0F, v = tex != null && t == tex ? rnd.nextFloat() * 0.7F : 0F;
            float w = tex != null && t == tex ? 0.25F : 0.5F;
            float hx = g.w() * (0.14F + rnd.nextFloat() * 0.14F), hy = g.h() * (0.1F + rnd.nextFloat() * 0.14F);
            part(rnd, g, t, (rnd.nextDouble() - 0.5) * g.w() * 0.6, (rnd.nextDouble() - 0.5) * g.h() * 0.7, (rnd.nextDouble() - 0.5) * g.w() * 0.6,
                hx, hy, hx, u, v, u + w, v + w, yaw, power);
        }
    }
    private static boolean solid(ClientLevel lv, double x, double y, double z) {
        BlockPos bp = BlockPos.containing(x, y, z);
        return !lv.getBlockState(bp).getCollisionShape(lv, bp).isEmpty();
    }

    private static void physics(ClientLevel lv) {
        if (PIECES.isEmpty()) return;
        DustParticleOptions trail = new DustParticleOptions(new Vector3f(0.5F, 0.02F, 0.02F), 0.8F);
        DustParticleOptions blood = new DustParticleOptions(new Vector3f(0.55F, 0.02F, 0.02F), 1.2F);
        RandomSource rnd = lv.random;
        for (int i = PIECES.size() - 1; i >= 0; i--) {
            Piece p = PIECES.get(i);
            p.px = p.x; p.py = p.y; p.pz = p.z;
            p.age++;
            p.vy -= 0.05;
            p.vx *= 0.985; p.vy *= 0.985; p.vz *= 0.985;
            double ny = p.y + p.vy;
            boolean poof = p.age > p.life;
            if (!poof && p.vy < 0 && solid(lv, p.x, ny - p.half, p.z)) poof = true;
            else if (p.vy > 0 && solid(lv, p.x, ny + p.half, p.z)) p.vy = -p.vy * 0.3;
            if (!poof) {
                if (solid(lv, p.x + p.vx, p.y, p.z)) p.vx *= -0.35;
                if (solid(lv, p.x, p.y, p.z + p.vz)) p.vz *= -0.35;
                p.x += p.vx; p.y += p.vy; p.z += p.vz;
                p.rx += p.srx; p.ry += p.sry;
                if (p.trail && (p.age & 1) == 0) lv.addParticle(trail, p.x, p.y, p.z, 0, 0, 0);
            } else {
                int puffs = 1 + (int) (p.half * 10);
                for (int q = 0; q < puffs; q++) {
                 lv.addParticle(ParticleTypes.POOF, p.x + rnd.nextGaussian() * p.half, p.y + rnd.nextGaussian() * p.half * 0.5, p.z + rnd.nextGaussian() * p.half, 0, 0.03, 0);
                 if (q % 2 == 0) lv.addParticle(ParticleTypes.LARGE_SMOKE, p.x, p.y, p.z, rnd.nextGaussian() * 0.02, 0.04, rnd.nextGaussian() * 0.02);
                }
                if (rnd.nextInt(3) == 0) lv.addParticle(blood, p.x, p.y, p.z, 0, 0.02, 0);
                if (rnd.nextInt(5) == 0) lv.playLocalSound(p.x, p.y, p.z, SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.25F, 0.5F + rnd.nextFloat() * 0.4F, false);
                PIECES.remove(i);
            }
        }
    }

    @SubscribeEvent
    public static void hideDead(RenderLivingEvent.Pre<?, ?> e) {
        if (!HIDDEN.isEmpty() && HIDDEN.containsKey(e.getEntity().getId())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || PIECES.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        Vec3 cp = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        Map<ResourceLocation, List<Piece>> by = new HashMap<>();
        for (Piece p : PIECES) by.computeIfAbsent(p.tex, k -> new ArrayList<>()).add(p);
        ps.pushPose();
        ps.translate(-cp.x, -cp.y, -cp.z);
        for (Map.Entry<ResourceLocation, List<Piece>> en : by.entrySet()) {
            VertexConsumer vc = buf.getBuffer(RenderType.entityCutoutNoCull(en.getKey()));
            for (Piece p : en.getValue()) {
                double ix = Mth.lerp(pt, p.px, p.x), iy = Mth.lerp(pt, p.py, p.y), iz = Mth.lerp(pt, p.pz, p.z);
                int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(ix, iy, iz));
                ps.pushPose();
                ps.translate(ix, iy, iz);
                ps.mulPose(Axis.YP.rotationDegrees(p.ry + p.sry * pt));
                ps.mulPose(Axis.XP.rotationDegrees(p.rx + p.srx * pt));
                Geo.uvBox(ps, vc, p.hx, p.hy, p.hz, p.u0, p.v0, p.u1, p.v1, light, p.cr, p.cg, p.cb);
                ps.popPose();
            }
            buf.endBatch(RenderType.entityCutoutNoCull(en.getKey()));
        }
        ps.popPose();
    }

    private HandClient() {}
}
