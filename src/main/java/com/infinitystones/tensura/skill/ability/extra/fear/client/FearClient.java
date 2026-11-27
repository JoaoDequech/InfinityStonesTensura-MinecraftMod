package com.infinitystones.tensura.skill.ability.extra.fear.client;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneTier;
import com.infinitystones.tensura.skill.ability.extra.fear.FearNet;
import com.infinitystones.tensura.skill.ability.extra.fear.FearRealm;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Client side of the fear realm: receives the server's events and renders the nightmare (HUD, camera, fog, sky, post effects). */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class FearClient {
    public static final KeyMapping PANEL_KEY = new KeyMapping("key.infinity.fear_panel", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, "key.categories.infinity");
    private static boolean waitingOpen, waitingMini;
    private static long lastBeat = -100;

    @EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e) { e.register(PANEL_KEY); }
        @SubscribeEvent public static void effects(RegisterDimensionSpecialEffectsEvent e) { e.register(InfinityStones.id("fear_realm"), new FearSky()); }
    }

    // ------------------------------------------------------------------ entry points (called from the network handlers and the Nightmare hook)

    /** Secret tier of Mind (client copy of the attunement mask). */
    public static boolean available() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && StoneTier.has(mc.player, Stone.MIND);
    }

    /** Nightmare menu hook (middle click on Nightmare in the wheel): opens the control room, or the portable panel inside. */
    public static void open() {
        Minecraft mc = Minecraft.getInstance();
        if (!available() || mc.player == null) return;
        if (FearState.self && FearState.victim) { waitingMini = true; PacketDistributor.sendToServer(new FearNet.Request(true)); return; }
        if (FearState.victim) return;
        waitingOpen = true;
        PacketDistributor.sendToServer(new FearNet.Request(true));
    }

    public static void panel(FearNet.Panel p) {
        FearState.panel = p;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ControlRoomScreen s) s.update(p);
        else if (waitingMini) { waitingMini = false; mc.setScreen(new MiniPanelScreen()); }
        else if (waitingOpen) { waitingOpen = false; mc.setScreen(new ControlRoomScreen(p)); }
        else if (!FearState.self) PacketDistributor.sendToServer(new FearNet.Request(false));
    }

    public static void presets(FearNet.Presets p) {
        FearState.presets.clear();
        FearState.presets.addAll(p.list());
    }

    public static void invite(FearNet.Invite i) { Minecraft.getInstance().setScreen(new InviteScreen(i)); }

    public static void env(FearNet.Env e) {
        FearState.fog = e.fog(); FearState.dark = e.dark(); FearState.sky = e.sky(); FearState.weather = e.weather();
        FearState.fear = e.fear(); FearState.flags = e.flags(); FearState.battery = e.battery();
        FearState.candles = e.candles(); FearState.candlesTotal = e.candlesTotal();
        if (e.battery() <= 0) FearState.flashOn = false;
    }

    public static void watch(FearNet.Watch w) {
        Minecraft mc = Minecraft.getInstance();
        FearState.watchMode = w.mode();
        FearState.watchEntity = w.entityId();
        FearState.watchName = w.name();
        FearState.orbitPos = null;
        if (w.mode() != 2) { FearState.dummy = null; if (mc.player != null && FearState.replay == null) mc.setCameraEntity(mc.player); }
        if (w.mode() == 0) { FearState.replay = null; if (mc.screen instanceof ControlRoomScreen) mc.setScreen(null); }
    }

    public static void replay(FearNet.Replay r) { FearState.replay = r.data(); FearState.replayTick = 0; }

    public static void event(FearNet.Event e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        FearState.seed = e.seed();
        var rnd = mc.level.random;
        switch (e.kind()) {
            case FearNet.ENTER -> {
                FearState.victim = true; FearState.self = e.a() == 1; FearState.flashOn = true; FearState.exit.clear();
                FearState.enter.set(96);
                if (mc.screen instanceof ControlRoomScreen || mc.screen instanceof MiniPanelScreen) mc.setScreen(null);
                sound(SoundEvents.ENDERMAN_SCREAM, 0.45F, 1F); sound(SoundEvents.GHAST_SCREAM, 0.5F, 0.8F);
            }
            case FearNet.SWAP -> { FearState.enter.set(56); sound(SoundEvents.WARDEN_SONIC_CHARGE, 0.6F, 0.9F); sound(SoundEvents.ENDERMAN_SCREAM, 0.6F, 0.7F); }
            case FearNet.LAND -> { FearState.enter.clear(); FearState.wake.set(130); sound(SoundEvents.PLAYER_BREATH, 0.55F, 1F); }
            case FearNet.EXIT -> {
                FearState.victim = false; FearState.self = false;
                FearState.enter.clear(); FearState.wake.clear(); FearState.lights.clear(); FearState.lava.clear(); FearState.voidFall.clear();
                FearState.jump.clear(); FearState.eyes.clear(); FearState.speak.clear(); FearState.flip.clear(); FearState.items.clear(); FearState.inv.clear();
                FearState.exit.set(40);
                FearState.fakeDeath = FearState.fakeLost = false;
                if (mc.screen instanceof FakeDeathScreen || mc.screen instanceof FakeLostScreen || mc.screen instanceof MiniPanelScreen) mc.setScreen(null);
                sound(SoundEvents.PLAYER_BREATH, 0.8F, 1F); sound(SoundEvents.ENDERMAN_TELEPORT, 0.5F, 1F);
            }
            case FearNet.JUMPSCARE -> {
                FearState.jumpVariant = e.a(); FearState.jump.set(52);
                FearState.shake(5F, 30);
                sound(SoundEvents.ENDERMAN_SCREAM, 0.45F, 1F); sound(SoundEvents.GHAST_SCREAM, 0.5F, 1F); sound(SoundEvents.WARDEN_ROAR, 0.6F, 1F);
                sound(SoundEvents.ENDER_DRAGON_GROWL, 0.55F, 1F); sound(SoundEvents.LIGHTNING_BOLT_THUNDER, 0.7F, 1F);
            }
            case FearNet.LOUD -> loud(e.a());
            case FearNet.FAKE_DEATH -> {
                FearState.fakeDeath = true; sound(SoundEvents.PLAYER_HURT, 1F, 0.9F);
                mc.setScreen(new FakeDeathScreen());
            }
            case FearNet.FAKE_LAVA -> { FearState.lava.set(e.a()); FearState.shake(2.5F, 20); sound(SoundEvents.PLAYER_HURT_ON_FIRE, 1F, 1F); sound(SoundEvents.LAVA_AMBIENT, 1F, 0.8F); }
            case FearNet.VOID_FALL -> { FearState.voidFall.set(e.a()); sound(SoundEvents.ELYTRA_FLYING, 0.8F, 0.6F); }
            case FearNet.LIGHTS_OUT, FearNet.BLACKOUT -> { FearState.lights.set(e.a()); sound(SoundEvents.REDSTONE_TORCH_BURNOUT, 0.9F, 0.6F); }
            case FearNet.FLASH_OFF -> {
                FearState.flashOff.set(e.a()); sound(SoundEvents.LEVER_CLICK, 0.9F, 1.4F);
                if (e.b() == 1) FearState.flashOn = false;
            }
            case FearNet.FLASH_FLICKER -> { FearState.flick.set(e.a()); sound(SoundEvents.LEVER_CLICK, 0.6F, 1.7F); }
            case FearNet.WHISPER -> { FearState.whisperName = e.s(); FearState.whisper.set(70); }
            case FearNet.SPEAK -> { FearState.speakText = e.s(); FearState.speak.set(Math.max(80, 40 + e.s().length() * 6)); FearState.shake(1.2F, 20); }
            case FearNet.FAKE_CHAT -> mc.gui.getChat().addMessage(Component.translatable("chat.type.text", Component.literal(e.s()), Component.translatable("infinity.fear.chat." + e.a())));
            case FearNet.INV_EMPTY -> {
                FearState.inv.set(e.a());
                FearState.items.clear();
                for (int i = 0; i < 9; i++) {
                    ItemStack st = mc.player.getInventory().getItem(i);
                    if (st.isEmpty()) continue;
                    FearState.Falling f = new FearState.Falling();
                    f.stack = st.copy(); f.x = -90 + 20 * i + 90 - 90; f.x = (i - 4) * 20; f.y = 0; f.vx = (rnd.nextFloat() - 0.5F) * 6; f.vy = -8 - rnd.nextFloat() * 6; f.vr = (rnd.nextFloat() - 0.5F) * 30;
                    FearState.items.add(f);
                    sound(SoundEvents.ITEM_PICKUP, 0.7F, 0.6F + rnd.nextFloat() * 0.5F);
                }
            }
            case FearNet.DOPPEL -> FearWorld.doppel(e.a());
            case FearNet.FLIP -> { FearState.flip.set(e.a()); sound(SoundEvents.ENDERMAN_TELEPORT, 0.8F, 0.6F); }
            case FearNet.PULSE -> FearState.pulse.set(e.a());
            case FearNet.LOST -> { FearState.fakeLost = true; mc.setScreen(new FakeLostScreen(e.a())); }
            case FearNet.WAKE_FLASH -> { FearState.wakeFlash.set(30); FearState.shake(2F, 14); sound(SoundEvents.PLAYER_BREATH, 0.8F, 0.9F); }
            case FearNet.LIGHTNING -> { FearState.lightning.set(10); FearState.shake(1.5F, 10); }
            case FearNet.EYES -> {
                FearState.eyes.set(e.a());
                FearState.eyePairs.clear();
                float yaw = mc.player.getYRot();
                for (int i = 0; i < 5; i++) FearState.eyePairs.add(new float[]{yaw + (rnd.nextFloat() * 2 - 1) * 70F, (rnd.nextFloat() - 0.5F) * 30F, 2.5F + rnd.nextFloat() * 3F, rnd.nextFloat()});
            }
            case FearNet.SCREEN_TEAR -> { FearState.tear.set(e.a()); FearState.shake(1.5F, 20); sound(SoundEvents.GLASS_BREAK, 0.5F, 0.6F); }
            case FearNet.HEARTBEAT -> FearState.heart.set(e.a());
            case FearNet.SHOCK -> FearState.shake(4F, 14);
            case FearNet.OPEN -> waitingOpen = true;
            default -> { }
        }
    }

    private static void loud(int type) {
        FearState.loudFlash.set(6); FearState.shake(3F, 16);
        switch (type) {
            case 0 -> { sound(SoundEvents.ANVIL_LAND, 0.5F, 1F); sound(SoundEvents.GENERIC_EXPLODE.value(), 0.5F, 0.8F); sound(SoundEvents.IRON_DOOR_CLOSE, 0.5F, 1F); }
            case 1 -> { sound(SoundEvents.BELL_BLOCK, 0.5F, 1F); sound(SoundEvents.BELL_BLOCK, 0.6F, 1F); sound(SoundEvents.BELL_BLOCK, 0.4F, 1F); }
            case 2 -> { sound(SoundEvents.WARDEN_ROAR, 0.8F, 1F); sound(SoundEvents.WARDEN_SONIC_BOOM, 0.6F, 1F); }
            case 3 -> { sound(SoundEvents.ENDER_DRAGON_GROWL, 0.6F, 1F); sound(SoundEvents.GHAST_SCREAM, 0.5F, 1F); }
            case 4 -> { for (int i = 0; i < 4; i++) sound(SoundEvents.GLASS_BREAK, 0.5F + i * 0.3F, 0.9F); }
            default -> { sound(SoundEvents.ZOMBIE_ATTACK_IRON_DOOR, 0.5F, 1F); sound(SoundEvents.ENDERMAN_SCREAM, 0.5F, 1F); }
        }
    }

    static void sound(SoundEvent s, float pitch, float vol) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, vol));
    }

    static void cmd(int action, java.util.UUID target, String arg, int num) { PacketDistributor.sendToServer(new FearNet.Cmd(action, target, arg, num)); }

    // ------------------------------------------------------------------ ticking

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        FearState.tick();
        if (FearState.replay != null) {
            FearState.replayTick++;
            if (FearState.replayTick / 2 >= FearState.replay.length / 5 - 1) { FearState.replay = null; if (FearState.watchMode != 2) mc.setCameraEntity(mc.player); }
        }
        while (PANEL_KEY.consumeClick()) if (mc.screen == null) open();
        if (!FearState.victim && FearState.watchMode == 0) return;
        FearState.Timer en = FearState.enter;
        if (en.on()) {
            int t = en.total - en.left;
            if (en.total >= 90) {
                if (t == 22) { sound(SoundEvents.WARDEN_EMERGE, 0.8F, 1F); sound(SoundEvents.SCULK_BLOCK_SPREAD, 0.7F, 1F); FearState.shake(3F, 30); }
                if (t == 34) { sound(SoundEvents.GLASS_BREAK, 0.5F, 1F); sound(SoundEvents.GLASS_BREAK, 0.8F, 0.9F); FearState.shake(4F, 20); }
                if (t == 46) sound(SoundEvents.PORTAL_TRAVEL, 0.45F, 0.35F);
            }
        }
        if (FearState.wake.on() && (FearState.wake.total - FearState.wake.left) % 42 == 4) {
            sound(SoundEvents.PLAYER_BREATH, 0.55F, 0.95F);
        }
        if (FearState.victim && mc.level.dimension() == FearRealm.DIM && FearState.fear > 15 && FearState.enter.left == 0) {
            float bpm = 62 + FearState.fear * 0.9F;
            if (FearState.ticks - lastBeat > 1200F / bpm) { lastBeat = FearState.ticks; sound(SoundEvents.WARDEN_HEARTBEAT, 0.4F + FearState.fear / 250F, 1F); }
        }
        if (FearState.victim && FearState.battery <= 0) FearState.flashOn = false;
    }

    @SubscribeEvent
    public static void attack(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (!e.isAttack() || !FearState.victim || mc.level == null || mc.level.dimension() != FearRealm.DIM || mc.screen != null) return;
        if (mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS) return;
        if (FearState.battery <= 0 || FearState.flashOff.on() || FearState.enter.on() || FearState.wake.on()) { sound(SoundEvents.LEVER_CLICK, 0.4F, 0.5F); return; }
        FearState.flashOn = !FearState.flashOn;
        sound(SoundEvents.LEVER_CLICK, 0.7F, FearState.flashOn ? 1.8F : 1.4F);
        PacketDistributor.sendToServer(new FearNet.Flash(FearState.flashOn));
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { FearState.clear(); FearFace.reset(); FearPost.reset(); }

    @SubscribeEvent
    public static void move(MovementInputUpdateEvent e) {
        if (FearState.watchMode == 2 || FearState.watchMode == 3 || FearState.replay != null) {
            e.getInput().leftImpulse = 0; e.getInput().forwardImpulse = 0; e.getInput().jumping = false;
        }
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent e) {
        if (FearState.watchMode == 2 && Minecraft.getInstance().screen == null) {
            FearState.orbitDist = Mth.clamp(FearState.orbitDist - (float) e.getScrollDeltaY() * 0.9F, 2F, 18F);
            e.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ camera

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(false);
        if (FearState.replay != null) {
            float[] d = FearState.replay;
            int n = d.length / 5;
            float f = Mth.clamp((FearState.replayTick + pt) / 2F, 0, n - 1.001F);
            int i = (int) f; float k = f - i;
            float x = Mth.lerp(k, d[i * 5], d[i * 5 + 5]), y = Mth.lerp(k, d[i * 5 + 1], d[i * 5 + 6]), z = Mth.lerp(k, d[i * 5 + 2], d[i * 5 + 7]);
            float yaw = d[i * 5 + 3] + Mth.wrapDegrees(d[i * 5 + 8] - d[i * 5 + 3]) * k, pitch = Mth.lerp(k, d[i * 5 + 4], d[i * 5 + 9]);
            place(mc, new Vec3(x, y, z), yaw, pitch);
        } else if (FearState.watchMode == 2) {
            Entity target = mc.level.getEntity(FearState.watchEntity);
            if (target == null) return;
            Vec3 tp = target.getPosition(pt).add(0, 1.3, 0);
            float yaw = mc.player.getViewYRot(pt), pitch = Mth.clamp(mc.player.getViewXRot(pt), -85, 85);
            Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
            Vec3 want = tp.subtract(dir.scale(FearState.orbitDist));
            if (FearState.dummy == null) FearState.dummy = new ArmorStand(EntityType.ARMOR_STAND, mc.level);
            var hit = mc.level.clip(new ClipContext(tp, want, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, FearState.dummy));
            Vec3 goal = hit.getType() == HitResult.Type.MISS ? want : hit.getLocation().add(dir.scale(0.35));
            FearState.orbitPos = FearState.orbitPos == null ? goal : FearState.orbitPos.lerp(goal, 0.3);
            place(mc, FearState.orbitPos, yaw, pitch);
        }
    }

    private static void place(Minecraft mc, Vec3 eye, float yaw, float pitch) {
        ClientLevel l = mc.level;
        if (FearState.dummy == null || FearState.dummy.level() != l) FearState.dummy = new ArmorStand(EntityType.ARMOR_STAND, l);
        Entity d = FearState.dummy;
        double y = eye.y - d.getEyeHeight();
        d.setPos(eye.x, y, eye.z);
        d.xo = d.xOld = eye.x; d.yo = d.yOld = y; d.zo = d.zOld = eye.z;
        d.setYRot(yaw); d.setXRot(pitch); d.yRotO = yaw; d.xRotO = pitch;
        if (mc.getCameraEntity() != d) mc.setCameraEntity(d);
    }

    @SubscribeEvent
    public static void angles(ViewportEvent.ComputeCameraAngles e) {
        if (!FearState.victim && FearState.watchMode != 1) return;
        float pt = (float) e.getPartialTick();
        float t = FearState.ticks + pt;
        float roll = 0, pitch = 0, yaw = 0;
        if (FearState.flip.on()) {
            float p = FearState.flip.p(pt);
            roll += 180F * (p < 0.2F ? FearGfx.easeInOut(p / 0.2F) : p > 0.85F ? FearGfx.easeInOut((1 - p) / 0.15F) : 1F);
        }
        if (FearState.wake.on()) {
            float p = FearState.wake.p(pt);
            float k = 1F - FearGfx.easeOutCubic(Mth.clamp((p - 0.15F) / 0.75F, 0, 1));
            roll += 74F * k; pitch += 48F * k + Mth.sin(t * 0.17F) * 2F * k;
        }
        if (FearState.voidFall.on()) roll += Mth.sin(t * 0.21F) * 9F;
        if (FearState.pulse.on()) pitch += Mth.sin(t * 0.3F) * 0.35F;
        float s = FearState.shakeNow(pt);
        if (s > 0F) {
            yaw += (Mth.sin(t * 2.7F) + Mth.sin(t * 5.3F) * 0.5F) * s * 0.35F;
            pitch += (Mth.cos(t * 3.1F) + Mth.sin(t * 6.1F) * 0.5F) * s * 0.35F;
            roll += Mth.sin(t * 4.1F) * s * 0.4F;
        }
        e.setRoll(e.getRoll() + roll); e.setPitch(e.getPitch() + pitch); e.setYaw(e.getYaw() + yaw);
    }

    @SubscribeEvent
    public static void fov(ViewportEvent.ComputeFov e) {
        if (!FearState.victim && FearState.watchMode != 1) return;
        float pt = (float) e.getPartialTick();
        double d = 0;
        if (FearState.pulse.on()) d += Mth.sin((FearState.ticks + pt) * 0.3F) * 4.5F;
        if (FearState.voidFall.on()) d += 28F * Math.min(1F, FearState.voidFall.p(pt) * 8F);
        if (FearState.jump.on() && FearState.jump.p(pt) < 0.2F) d -= 18F * (1F - FearState.jump.p(pt) / 0.2F);
        if (FearState.wake.on()) d -= 14F * FearState.wake.rem(pt);
        if (d != 0) e.setFOV(e.getFOV() + d);
    }

    @SubscribeEvent
    public static void fog(ViewportEvent.RenderFog e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != FearRealm.DIM) return;
        float k = FearState.fog / 100F;
        float far = Mth.lerp(k, 150F, 16F);
        if (FearState.lights.on()) far = Math.min(far, 7F);
        e.setNearPlaneDistance(0F);
        e.setFarPlaneDistance(far);
        e.setFogShape(com.mojang.blaze3d.shaders.FogShape.SPHERE);
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void fogColor(ViewportEvent.ComputeFogColor e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.level.dimension() != FearRealm.DIM) return;
        switch (FearState.sky) {
            case 1 -> { e.setRed(0.01F); e.setGreen(0.01F); e.setBlue(0.012F); }
            case 2 -> { e.setRed(0.05F); e.setGreen(0.015F); e.setBlue(0.045F); }
            case 3 -> { e.setRed(0.2F); e.setGreen(0.015F); e.setBlue(0.02F); }
            default -> { e.setRed(0.07F); e.setGreen(0.008F); e.setBlue(0.012F); }
        }
    }

    // ------------------------------------------------------------------ drawing

    @SubscribeEvent
    public static void gui(RenderGuiEvent.Post e) { FearHud.render(e.getGuiGraphics(), e.getPartialTick().getGameTimeDeltaPartialTick(false)); }

    @SubscribeEvent
    public static void layer(RenderGuiLayerEvent.Pre e) {
        if (e.getName().equals(VanillaGuiLayers.HOTBAR) && FearState.inv.on()) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void level(RenderLevelStageEvent e) {
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            FearWorld.render(e);
            FearVeilBridge.update(e.getPartialTick().getGameTimeDeltaPartialTick(false));
        }
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) FearPost.apply(e.getPartialTick().getGameTimeDeltaPartialTick(false));
    }

    private FearClient() {}
}
