package com.infinitystones.tensura.client.soul;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.soul.Possession;
import com.infinitystones.tensura.soul.SoulNet;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.Set;

/**
 * Client side of Soul Possession: camera on the possessed body, the possessor's input forwarded to the server,
 * ghost bodies hidden, possession HUD; also opens the possession/capture menus the server asks for.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class SoulClient {
    public static final KeyMapping CONTROL = new KeyMapping("key.infinity.soul_control", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.infinity");
    public static final KeyMapping RELEASE = new KeyMapping("key.infinity.soul_release", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_BACKSPACE, "key.categories.infinity");

    @EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void keys(RegisterKeyMappingsEvent e) { e.register(CONTROL); e.register(RELEASE); }
    }

    private static final Set<ResourceLocation> HIDDEN_LAYERS = Set.of(VanillaGuiLayers.HOTBAR, VanillaGuiLayers.PLAYER_HEALTH,
            VanillaGuiLayers.ARMOR_LEVEL, VanillaGuiLayers.FOOD_LEVEL, VanillaGuiLayers.AIR_LEVEL, VanillaGuiLayers.EXPERIENCE_BAR,
            VanillaGuiLayers.EXPERIENCE_LEVEL, VanillaGuiLayers.SELECTED_ITEM_NAME, VanillaGuiLayers.VEHICLE_HEALTH, VanillaGuiLayers.JUMP_METER);

    private static int lastMode = SoulNet.NONE;
    private static @Nullable CameraType savedCamera;
    private static boolean shiftWasDown;
    private static int slot = -1;
    private static long modeSince;

    static int mode() { return SoulNet.state.mode(); }
    static boolean active() { return mode() != SoulNet.NONE; }
    private static boolean drives() { return mode() == SoulNet.CONTROL || mode() == SoulNet.MOB; }

    private static void act(int action, int arg) { PacketDistributor.sendToServer(new SoulNet.Action(action, arg)); }

    private static @Nullable Entity body(Minecraft mc) {
        return mc.level == null || SoulNet.state.entity() < 0 ? null : mc.level.getEntity(SoulNet.state.entity());
    }

    // ------------------------------------------------------------------ ticks

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void preTick(ClientTickEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        // menus asked by the server
        if (SoulNet.pendingPlayers != null) { var pl = SoulNet.pendingPlayers; SoulNet.pendingPlayers = null; mc.setScreen(new PossessScreen(pl)); }
        if (SoulNet.openJar) { SoulNet.openJar = false; mc.setScreen(new SoulJarScreen()); }

        int m = mode();
        if (m != lastMode) onModeChange(mc, lastMode, m);
        if (!active()) return;

        // swallow every vanilla interaction of the possessor's own body
        while (mc.options.keyAttack.consumeClick()) if (drives() && mc.screen == null) act(Possession.ACT_ATTACK, 0);
        while (mc.options.keyUse.consumeClick()) if (drives() && mc.screen == null) act(Possession.ACT_USE, 0);
        while (mc.options.keyPickItem.consumeClick()) if (m == SoulNet.MOB) act(Possession.ACT_ABILITY2, 0);
        while (mc.options.keyInventory.consumeClick()) if (m == SoulNet.CONTROL) act(Possession.ACT_INVENTORY, 0);
        while (mc.options.keyDrop.consumeClick()) if (m == SoulNet.CONTROL) act(Possession.ACT_DROP, Screen.hasControlDown() ? 1 : 0);
        while (mc.options.keySwapOffhand.consumeClick()) { }
        for (int i = 0; i < 9; i++) while (mc.options.keyHotbarSlots[i].consumeClick()) slot = i;
        while (CONTROL.consumeClick()) {
            if (m == SoulNet.MOB) act(Possession.ACT_RELEASE, 0);
            else act(Possession.ACT_CONTROL, 0);
        }
        while (RELEASE.consumeClick()) act(Possession.ACT_RELEASE, 0);
        boolean shift = mc.options.keyShift.isDown();
        if (m == SoulNet.SPECTATE && shift && !shiftWasDown && mc.screen == null && System.currentTimeMillis() - modeSince > 400) act(Possession.ACT_RELEASE, 0);
        shiftWasDown = shift;
    }

    @SubscribeEvent
    public static void postTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !active()) return;
        camera(mc);
        if (!drives()) return;
        boolean free = mc.screen == null;
        float fwd = 0, str = 0;
        if (free) {
            if (mc.options.keyUp.isDown()) fwd++;
            if (mc.options.keyDown.isDown()) fwd--;
            if (mc.options.keyLeft.isDown()) str++;
            if (mc.options.keyRight.isDown()) str--;
        }
        PacketDistributor.sendToServer(new SoulNet.Input(fwd, str, free && mc.options.keyJump.isDown(), free && mc.options.keyShift.isDown(),
                free && mc.options.keySprint.isDown(), mc.player.getYRot(), mc.player.getXRot(),
                free && mc.options.keyAttack.isDown(), free && mc.options.keyUse.isDown(), slot));
    }

    private static void onModeChange(Minecraft mc, int from, int to) {
        lastMode = to;
        modeSince = System.currentTimeMillis();
        shiftWasDown = mc.options.keyShift.isDown();
        if (to == SoulNet.NONE) {
            if (mc.getCameraEntity() != mc.player) mc.setCameraEntity(mc.player);
            if (savedCamera != null) { mc.options.setCameraType(savedCamera); savedCamera = null; }
            slot = -1;
            return;
        }
        if (to == SoulNet.MOB && savedCamera == null) {
            savedCamera = mc.options.getCameraType();
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        } else if (to != SoulNet.MOB && savedCamera != null) {
            mc.options.setCameraType(savedCamera); savedCamera = null;
        }
        Entity b = body(mc);
        if (b != null && (to == SoulNet.CONTROL || to == SoulNet.MOB) && from != SoulNet.CONTROL && from != SoulNet.MOB) {
            // start looking exactly where the body looks
            mc.player.setYRot(b.getYRot()); mc.player.setXRot(b.getXRot());
            mc.player.yRotO = b.getYRot(); mc.player.xRotO = b.getXRot();
        }
        if (to == SoulNet.CONTROL) slot = SoulNet.hud.selected();
    }

    private static void camera(Minecraft mc) {
        Entity b = body(mc);
        if (b == null) return;
        if (mc.getCameraEntity() != b) {
            mc.setCameraEntity(b);
            if (mode() == SoulNet.MOB) mc.gameRenderer.shutdownEffect();
        }
        if (drives()) {
            float y = mc.player.getYRot(), x = mc.player.getXRot();
            b.setYRot(y); b.yRotO = y; b.setXRot(x); b.xRotO = x;
            if (b instanceof LivingEntity le) {
                le.yHeadRot = y; le.yHeadRotO = y;
                if (mode() == SoulNet.MOB) { le.yBodyRot = y; le.yBodyRotO = y; }
            }
        }
    }

    @SubscribeEvent
    public static void frame(RenderFrameEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && active()) camera(mc);
    }

    // ------------------------------------------------------------------ input suppression

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interaction(InputEvent.InteractionKeyMappingTriggered e) {
        if (active()) { e.setSwingHand(false); e.setCanceled(true); }
    }

    @SubscribeEvent
    public static void scroll(InputEvent.MouseScrollingEvent e) {
        if (mode() != SoulNet.CONTROL || Minecraft.getInstance().screen != null) return;
        int cur = slot < 0 ? SoulNet.hud.selected() : slot;
        slot = Math.floorMod(cur - (int) Math.signum(e.getScrollDeltaY()), 9);
        e.setCanceled(true);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        SoulNet.state = new SoulNet.State(SoulNet.NONE, -1, "");
        SoulNet.ghosts = java.util.List.of();
        lastMode = SoulNet.NONE;
        if (savedCamera != null) { Minecraft.getInstance().options.setCameraType(savedCamera); savedCamera = null; }
    }

    // ------------------------------------------------------------------ rendering

    @SubscribeEvent
    public static void hideGhost(RenderLivingEvent.Pre<?, ?> e) {
        if (SoulNet.ghosts.contains(e.getEntity().getId())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void hand(RenderHandEvent e) { if (active()) e.setCanceled(true); }

    @SubscribeEvent
    public static void layers(RenderGuiLayerEvent.Pre e) {
        if (active() && HIDDEN_LAYERS.contains(e.getName())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.options.hideGui) return;
        GuiGraphics g = e.getGuiGraphics();
        Font f = mc.font;
        int w = g.guiWidth(), h = g.guiHeight();
        float t = (System.currentTimeMillis() % 4000) / 4000F;
        int pulse = (int) (90 + 60 * Mth.sin(t * Mth.TWO_PI));
        // ember vignette
        g.fillGradient(0, 0, w, 26, (pulse << 24) | 0x3A1400, 0x00000000);
        g.fillGradient(0, h - 26, w, h, 0x00000000, (pulse << 24) | 0x3A1400);
        int m = mode();
        String modeKey = m == SoulNet.SPECTATE ? "infinity.soul.hud.spectate" : m == SoulNet.CONTROL ? "infinity.soul.hud.control" : "infinity.soul.hud.mob";
        Component title = Component.translatable("infinity.soul.hud.title", SoulNet.state.name());
        g.drawCenteredString(f, title, w / 2, 8, 0xFFF28A1A);
        g.drawCenteredString(f, Component.translatable(modeKey), w / 2, 20, 0xFFFFD9A0);
        Component hint = switch (m) {
            case SoulNet.SPECTATE -> Component.translatable("infinity.soul.hud.hint_spectate", CONTROL.getTranslatedKeyMessage(),
                    mc.options.keyShift.getTranslatedKeyMessage(), RELEASE.getTranslatedKeyMessage());
            case SoulNet.CONTROL -> Component.translatable("infinity.soul.hud.hint_control", CONTROL.getTranslatedKeyMessage(),
                    RELEASE.getTranslatedKeyMessage(), mc.options.keyInventory.getTranslatedKeyMessage());
            default -> Component.translatable("infinity.soul.hud.hint_mob", RELEASE.getTranslatedKeyMessage());
        };
        g.drawCenteredString(f, hint, w / 2, h - 12, 0xFFE8C9A0);
        if (m == SoulNet.SPECTATE || m == SoulNet.CONTROL) {
            var hud = SoulNet.hud;
            int x0 = w / 2 - 91, y0 = h - 44;
            g.fill(x0 - 2, y0 - 2, x0 + 182, y0 + 20, 0x90140800);
            g.renderOutline(x0 - 2, y0 - 2, 184, 22, 0xFFF28A1A);
            int sel = m == SoulNet.CONTROL && slot >= 0 ? slot : hud.selected();
            for (int i = 0; i < 9; i++) {
                int x = x0 + i * 20;
                if (i == sel) { g.fill(x, y0, x + 20, y0 + 18, 0x80F28A1A); g.renderOutline(x, y0, 20, 18, 0xFFFFD9A0); }
                if (i < hud.hotbar().size()) {
                    ItemStack st = hud.hotbar().get(i);
                    g.renderItem(st, x + 2, y0 + 1);
                    g.renderItemDecorations(f, st, x + 2, y0 + 1);
                }
            }
            String hp = String.format("❤ %.0f / %.0f", hud.health(), hud.maxHealth());
            g.drawString(f, hp, x0, y0 - 12, 0xFFFF6B4A);
        } else {
            Entity b = body(mc);
            if (b instanceof LivingEntity le) g.drawString(f, String.format("❤ %.0f / %.0f", le.getHealth(), le.getMaxHealth()), w / 2 - 91, h - 26, 0xFFFF6B4A);
        }
    }

    private SoulClient() {}
}
