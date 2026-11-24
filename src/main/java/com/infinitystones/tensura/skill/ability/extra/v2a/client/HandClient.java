package com.infinitystones.tensura.skill.ability.extra.v2a.client;

import com.infinitystones.tensura.InfinityStones;
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

    private static int mode, ttl, held;
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
        if (fresh) {
            held = 0;
            if (mode == 1) {
                if (saved == null) saved = mc.options.getCameraType();
                mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                if (mc.screen != null && !allowedScreen(mc.screen)) mc.setScreen(null);
            }
        }
    }

    private static void release(Minecraft mc) {
        mode = 0;
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
        if (mode != 1) return;
        e.setCanceled(true);
        e.setSwingHand(false);
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent e) {
        if (mode == 1 && Minecraft.getInstance().screen == null) e.setCanceled(true);
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

    /** Camera of the held player: over the caster's shoulder, a few blocks back and up, looking at the fist. */
    public static double[] cameraShot(float pt) {
        if (mode != 1) return null;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null) return null;
        double fx = Mth.lerp(pt, p.xo, p.getX()), fy = Mth.lerp(pt, p.yo, p.getY()) + p.getBbHeight() * 0.55, fz = Mth.lerp(pt, p.zo, p.getZ());
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
        float half, rx, ry, srx, sry, u0, v0, u1, v1, cr, cg, cb;
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
        int n = Mth.clamp((int) (36 + g.w() * g.w() * g.h() * 10), 36, 150);
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
        lv.addParticle(ParticleTypes.FLASH, g.x(), g.y(), g.z(), 0, 0, 0);
        for (int i = 0; i < 14; i++)
            lv.addParticle(ParticleTypes.LARGE_SMOKE, g.x() + (rnd.nextDouble() - 0.5) * g.w(), g.y() + (rnd.nextDouble() - 0.5) * g.h(),
                    g.z() + (rnd.nextDouble() - 0.5) * g.w(), 0, 0.03, 0);
        lv.playLocalSound(g.x(), g.y(), g.z(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.5F, 0.5F, false);
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
                lv.addParticle(ParticleTypes.POOF, p.x, p.y, p.z, 0, 0.02, 0);
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
                Geo.uvCube(ps, vc, p.half, p.u0, p.v0, p.u1, p.v1, light, p.cr, p.cg, p.cb);
                ps.popPose();
            }
            buf.endBatch(RenderType.entityCutoutNoCull(en.getKey()));
        }
        ps.popPose();
    }

    private HandClient() {}
}
