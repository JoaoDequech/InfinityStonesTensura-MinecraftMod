package com.infinitystones.tensura.skill.ability.extra.space.client;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.space.GatherNet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.player.Input;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client side of the Spiritual Gathering: scenery state and fades, the input lock for gathered players (look only),
 * the caster's HUD message, the outer barrier everybody else sees, portal/hand animations, and the prayer key hook.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class GatherClient {
    private static final String K = "infinity.space.gather.";

    private static int phase;
    private static boolean caster, locked;
    private static Vec3 center = Vec3.ZERO;
    private static float ringR = 5, sceneR = 40;
    private static long seed;
    private static int[] ids = new int[0];
    private static final Set<Integer> ID_SET = new HashSet<>();
    private static float fade, fadePrev;
    private static int fadeTicks = 40;
    private static float waterGrow;
    private static float dissolveStart = -1;
    private static float flash, flashPrev;
    private static boolean wasVisible;

    private record Bar(String dim, Vec3 c, float r) {}
    private static final Map<Integer, Bar> BARS = new HashMap<>();

    // ------------------------------------------------------------------ payloads

    public static void open(GatherNet.Open msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && !isLocked()) mc.setScreen(new GatherScreen(msg));
    }

    public static void accept(GatherNet.State s) {
        Minecraft mc = Minecraft.getInstance();
        int old = phase;
        phase = s.phase(); caster = s.caster(); locked = s.locked() && s.phase() != 0;
        center = new Vec3(s.cx(), s.cy(), s.cz());
        ringR = s.ringR(); sceneR = Math.max(20, s.sceneR()); seed = s.seed(); fadeTicks = Math.max(1, s.fade());
        ids = s.ids();
        ID_SET.clear();
        for (int id : ids) ID_SET.add(id);
        if (phase == 0) { reset(); return; }
        if (phase == 1 && old == 0) { fade = fadePrev = 0; waterGrow = 0; dissolveStart = -1; wasVisible = false; }
        if (phase == 2 && old != 2 && mc.level != null) dissolveStart = ticksNow(0);
    }

    public static void accept(GatherNet.Barrier b) {
        if (!b.on()) { BARS.remove(b.id()); return; }
        BARS.put(b.id(), new Bar(b.dim(), new Vec3(b.cx(), b.cy(), b.cz()), b.radius()));
    }

    public static void accept(GatherNet.Hand h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) GatherHands.add(h, ticksNow(0));
    }

    private static void reset() {
        phase = 0; caster = false; locked = false; fade = fadePrev = 0; waterGrow = 0; dissolveStart = -1; wasVisible = false;
        ids = new int[0]; ID_SET.clear();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) { reset(); BARS.clear(); GatherHands.clear(); }

    @SubscribeEvent
    public static void respawn(ClientPlayerNetworkEvent.Clone e) { reset(); }

    private static float ticksNow(float pt) {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : (mc.level.getGameTime() % 10000000L) + pt;
    }

    static boolean isLocked() { return locked && phase != 0; }

    /** Scenery opacity: fade state, held back while the caster is still rising towards the centre. */
    private static float alpha(float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (phase == 0 || mc.player == null) return 0;
        float f = Mth.lerp(pt, fadePrev, fade);
        float ramp = Mth.clamp((float) (mc.player.getY() - (center.y - 12)) / 8F, 0, 1);
        return f * ramp;
    }

    // ------------------------------------------------------------------ prayer hook (called by the wheel key)

    /** @return true when the wheel key press was consumed (locked, or a prayer was sent). */
    public static boolean onWheelKey(Minecraft mc) {
        if (mc.player == null) return false;
        if (isLocked()) return true;
        for (Stone s : Stone.values()) if (s.isHeldBy(mc.player)) return false;
        PacketDistributor.sendToServer(new GatherNet.Pray());
        return true;
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void preTick(ClientTickEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !isLocked()) return;
        var o = mc.options;
        while (o.keyAttack.consumeClick()) { }
        while (o.keyUse.consumeClick()) { }
        while (o.keyPickItem.consumeClick()) { }
        while (o.keyDrop.consumeClick()) { }
        while (o.keySwapOffhand.consumeClick()) { }
        while (o.keyInventory.consumeClick()) { }
        while (o.keyAdvancements.consumeClick()) { }
        for (int i = 0; i < 9; i++) while (o.keyHotbarSlots[i].consumeClick()) { }
        o.keySprint.setDown(false);
        if (mc.screen != null && !allowedScreen(mc.screen, mc)) mc.setScreen(null);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (mc.isPaused()) return;
        fadePrev = fade;
        flashPrev = flash;
        if (phase == 1) fade = Math.min(1, fade + 1F / 20F);
        else if (phase == 2) fade = Math.max(0, fade - 1F / fadeTicks);
        else fade = 0;
        boolean vis = alpha(0) > 0.02F;
        if (vis && !wasVisible && caster && phase == 1) flash = 1F;
        wasVisible = vis;
        if (vis && phase == 1) waterGrow = Math.min(1, waterGrow + 1F / 45F);
        else if (phase == 2) waterGrow = Math.max(0, waterGrow - 1F / fadeTicks);
        if (flash > 0) flash = Math.max(0, flash - 0.05F);
    }

    // ------------------------------------------------------------------ input lock

    @SubscribeEvent
    public static void move(MovementInputUpdateEvent e) {
        if (!isLocked()) return;
        Input i = e.getInput();
        i.up = i.down = i.left = i.right = false;
        i.jumping = false;
        i.shiftKeyDown = false;
        i.forwardImpulse = 0;
        i.leftImpulse = 0;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interaction(InputEvent.InteractionKeyMappingTriggered e) {
        if (!isLocked()) return;
        e.setCanceled(true);
        e.setSwingHand(false);
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent e) {
        if (isLocked() && Minecraft.getInstance().screen == null) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void screen(ScreenEvent.Opening e) {
        if (!isLocked()) return;
        Screen n = e.getNewScreen();
        if (n != null && !allowedScreen(n, Minecraft.getInstance())) e.setCanceled(true);
    }

    /** Chat, pause (and what opens from it), death screen: everything else is blocked while gathered. */
    private static boolean allowedScreen(Screen s, Minecraft mc) {
        if (s instanceof ChatScreen || s instanceof PauseScreen || s instanceof net.minecraft.client.gui.screens.DeathScreen) return true;
        if (s instanceof AbstractContainerScreen || s instanceof AdvancementsScreen || s instanceof StatsScreen) return false;
        boolean vanilla = s.getClass().getName().startsWith("net.minecraft.client.gui.screens.");
        return vanilla && mc.screen != null;
    }

    // ------------------------------------------------------------------ hide everybody who is not in the gathering

    @SubscribeEvent
    public static void hideOutsiders(RenderLivingEvent.Pre<?, ?> e) {
        if (phase == 0 || alpha(0) < 0.4F) return;
        Entity en = e.getEntity();
        if (ID_SET.contains(en.getId())) return;
        if (en.position().distanceToSqr(center) < (double) sceneR * sceneR * 1.4) e.setCanceled(true);
    }

    // ------------------------------------------------------------------ world rendering

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        RenderLevelStageEvent.Stage st = e.getStage();
        if (st != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS && st != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        boolean scenery = phase != 0 && alpha(0) > 0.01F;
        boolean late = st == RenderLevelStageEvent.Stage.AFTER_PARTICLES;
        if (!scenery && !(late && (GatherHands.any() || !BARS.isEmpty() || dissolveStart >= 0))) return;
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        Camera cam = e.getCamera();
        Vec3 cp = cam.getPosition();
        Vector3f l = cam.getLeftVector(), u = cam.getUpVector();
        Vec3 right = new Vec3(-l.x(), -l.y(), -l.z()), up = new Vec3(u.x(), u.y(), u.z());
        Matrix4f mv = e.getModelViewMatrix();
        float ticks = ticksNow(pt);
        if (scenery) {
            Matrix4f m = new Matrix4f(mv).translate((float) (center.x - cp.x), (float) (center.y - cp.y), (float) (center.z - cp.z));
            List<Vec3> feet = new ArrayList<>();
            for (int i = 1; i < ids.length; i++) {
                Entity en = mc.level.getEntity(ids[i]);
                if (en != null) feet.add(en.getPosition(pt).subtract(center));
            }
            GatherScenery.Ctx ctx = new GatherScenery.Ctx(m, cp.subtract(center), right, up, alpha(pt), ticks, ringR, sceneR, seed, feet, waterGrow);
            if (!late) GatherScenery.backdrop(ctx);
            else GatherScenery.foreground(ctx);
        }
        if (late) {
            if (dissolveStart >= 0) {
                float age = (ticks - dissolveStart) / 20F;
                if (age > 2.4F) dissolveStart = -1;
                else {
                    Matrix4f m = new Matrix4f(mv).translate((float) (center.x - cp.x), (float) (center.y - cp.y), (float) (center.z - cp.z));
                    GatherTex.ensure();
                    GatherScenery.motes(new GatherScenery.Ctx(m, cp.subtract(center), right, up, 1, ticks, ringR, sceneR, seed, List.of(), 0), age);
                }
            }
            if (GatherHands.any()) {
                Matrix4f m = new Matrix4f(mv).translate((float) -cp.x, (float) -cp.y, (float) -cp.z);
                GatherHands.render(m, cp, right, up, ticks);
            }
            if (!BARS.isEmpty()) {
                String dim = mc.level.dimension().location().toString();
                for (Bar b : BARS.values()) {
                    if (!b.dim().equals(dim) || (scenery && alpha(0) > 0.5F)) continue;
                    if (cp.distanceToSqr(b.c()) > (b.r() + 220) * (b.r() + 220)) continue;
                    Matrix4f m = new Matrix4f(mv).translate((float) (b.c().x - cp.x), (float) (b.c().y - cp.y), (float) (b.c().z - cp.z));
                    GatherScenery.barrier(m, cp.subtract(b.c()), right, up, b.r(), ticks);
                }
            }
        }
    }

    // ------------------------------------------------------------------ HUD

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        GuiGraphics g = e.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight();
        float pt = e.getPartialTick().getGameTimeDeltaPartialTick(true);
        float fl = Mth.lerp(pt, flashPrev, flash);
        if (fl > 0.01F) g.fill(0, 0, w, h, ((int) (255 * Mth.clamp(fl, 0, 1)) << 24) | 0xFFF4DC);
        if (phase == 0 || mc.options.hideGui) return;
        float a = alpha(pt);
        if (a > 0.02F) {
            int va = (int) (70 * a);
            g.fillGradient(0, 0, w, 40, (va << 24) | 0xFFC8D8, 0x00FFC8D8);
            g.fillGradient(0, h - 40, w, h, 0x00FFC8D8, (va << 24) | 0xFFC8D8);
        }
        int col = Stone.SPACE.color() & 0xFFFFFF;
        float pulse = 0.5F + 0.5F * Mth.sin(System.currentTimeMillis() / 350F);
        if (caster) {
            Component msg = Component.translatable(phase == 1 ? K + "hud.active" : K + "hud.ending");
            int tw = mc.font.width(msg) + 16;
            int x = w - tw - 6, y = 6;
            for (int i = 3; i >= 1; i--) g.fill(x - i, y - i, x + tw + i, y + 18 + i, ((int) ((8 + 12 * pulse) * (4 - i)) << 24) | col);
            g.fill(x, y, x + tw, y + 18, 0xC0080A16);
            g.drawString(mc.font, msg, x + 8, y + 5, 0xFFFFF0C8, true);
        } else if (isLocked()) {
            Component msg = Component.translatable(K + "hud.member");
            int tw = mc.font.width(msg);
            g.drawString(mc.font, msg, w / 2 - tw / 2, h - 52, (((int) (150 + 90 * pulse)) << 24) | 0xFFF0C8, true);
        }
    }

    private GatherClient() {}
}
