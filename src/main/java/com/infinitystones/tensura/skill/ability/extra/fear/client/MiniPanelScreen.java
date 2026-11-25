package com.infinitystones.tensura.skill.ability.extra.fear.client;

import com.infinitystones.tensura.skill.ability.extra.fear.FearApi;
import com.infinitystones.tensura.skill.ability.extra.fear.FearNet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

import static com.infinitystones.tensura.skill.ability.extra.fear.client.FearGfx.argb;

/** The portable panel used from inside: a battered handheld radio with a flickering CRT. Pause the director, trigger a few events, change scene or creature mode, set intensity, leave. */
final class MiniPanelScreen extends FearUi {
    private static final int[] EVENTS = {FearNet.FOOTSTEPS, FearNet.WHISPER, FearNet.FLASH_FLICKER, FearNet.LOUD, FearNet.HEARTBEAT, FearNet.EYES, FearNet.CREEPER};
    private static boolean paused, killer;
    private static int intensity = 60, eventIdx, sceneIdx;

    MiniPanelScreen() { super(Component.translatable("infinity.fear.mini.title"), 0xC8A040); }

    private UUID me() { return Minecraft.getInstance().player.getUUID(); }

    private void cmd(int a, String arg, int num) { FearClient.cmd(a, me(), arg, num); }

    private int bx() { return width / 2 - 100; }

    private int by() { return height / 2 - 125; }

    @Override
    protected void layout() {
        int x = bx() + 14, y = by() + 78, w = 172;
        FearNet.Panel pn = FearState.panel;
        add(x, y, w, 16, Component.translatable(paused ? "infinity.fear.mini.resume" : "infinity.fear.mini.pause"), !paused, () -> { paused = !paused; cmd(FearNet.AUTO, "", paused ? 0 : 1); rebuild(); });
        add(x, y + 20, w, 16, Component.translatable("infinity.fear.mini.event", Component.translatable("infinity.fear.fx." + EVENTS[eventIdx])), false, () -> cmd(FearNet.FX, "", EVENTS[eventIdx]));
        add(x, y + 40, 84, 16, Component.translatable("infinity.fear.mini.prev"), false, () -> { eventIdx = (eventIdx + EVENTS.length - 1) % EVENTS.length; rebuild(); });
        add(x + 88, y + 40, 84, 16, Component.translatable("infinity.fear.mini.next"), false, () -> { eventIdx = (eventIdx + 1) % EVENTS.length; rebuild(); });
        if (pn != null && !pn.scenes().isEmpty()) add(x, y + 60, w, 16, Component.translatable("infinity.fear.mini.scene"), false, () -> {
            sceneIdx = (sceneIdx + 1) % pn.scenes().size();
            cmd(FearNet.SCENE, pn.scenes().get(sceneIdx), 0);
        });
        add(x, y + 80, w, 16, Component.translatable(killer ? "infinity.fear.mini.killer" : "infinity.fear.mini.scare"), killer, () -> {
            killer = !killer; cmd(FearNet.MODE, "", killer ? FearApi.HUNT : FearApi.SCARE); rebuild();
        }).danger = killer;
        slider(x, y + 100, w, Component.translatable("infinity.fear.ui.intensity"), 0, 100, intensity, v -> { intensity = v; cmd(FearNet.ENV, "intensity", v); });
        add(x, y + 122, w, 18, Component.translatable("infinity.fear.mini.exit"), false, () -> { cmd(FearNet.PULL, "", 0); onClose(); }).danger = true;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        int x = bx(), y = by();
        float t = FearState.ticks + pt;
        float open = Mth.clamp((openAge + pt) / 8F, 0F, 1F);
        g.fill(x - 3, y - 3, x + 203, y + 253, argb(0.96F, 0x0A0806));
        g.fill(x, y, x + 200, y + 250, argb(1F, 0x2A2420));
        g.fill(x + 2, y + 2, x + 198, y + 248, argb(1F, 0x1A1614));
        for (int i = 0; i < 9; i++) g.fill(x + 170 + (i % 3) * 8, y + 8 + (i / 3) * 6, x + 175 + (i % 3) * 8, y + 11 + (i / 3) * 6, argb(0.6F, 0x070605));
        int sx = x + 10, sy = y + 24, sw = 180, sh = 44;
        g.fill(sx - 2, sy - 2, sx + sw + 2, sy + sh + 2, argb(1F, 0x050505));
        g.fill(sx, sy, sx + sw, sy + sh, argb(1F, 0x061006));
        boolean glitch = openAge < 10 || FearGfx.hash((int) (t * 0.3F), 5) < 0.05F;
        if (!glitch) {
            g.drawString(font, Component.translatable("infinity.fear.mini.status", FearState.fear), sx + 6, sy + 6, argb(0.9F, 0x80FF90), false);
            g.drawString(font, Component.translatable(paused ? "infinity.fear.mini.paused" : "infinity.fear.mini.running"), sx + 6, sy + 18, argb(0.8F, 0xA0E0A0), false);
            g.drawString(font, Component.translatable("infinity.fear.mini.battery", FearState.battery), sx + 6, sy + 30, argb(0.8F, 0xA0E0A0), false);
        }
        scanlines(g, sx, sy, sx + sw, sy + sh, glitch ? 0.5F : 0.22F);
        if (glitch) for (int i = 0; i < 6; i++) g.fill(sx, sy + (int) (FearGfx.hash(i, (int) t) * sh), sx + sw, sy + 2 + (int) (FearGfx.hash(i, (int) t) * sh), argb(0.4F, 0x80FF90));
        FearGfx.vgrad(g, sx, sy, sx + sw, sy + sh, argb(0.1F, 0xFFFFFF), argb(0F, 0xFFFFFF));
        g.drawString(font, Component.translatable("infinity.fear.mini.title"), x + 10, y + 10, 0xFFA09070, false);
        drawBtns(g, mx, my, pt);
        drawToast(g, x + 14, y + 236);
        if (open < 1F) g.fill(x - 3, y - 3 + (int) (256 * open), x + 203, y + 253, argb(1F, 0));
        FearGfx.rings(g, width / 2F, height / 2F, new float[]{0F, height * 0.5F, height * 1.1F}, new int[]{argb(0F, 0), argb(0.25F, 0), argb(0.6F, 0)}, 1.4F, 24);
    }

    @Override public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ESCAPE || FearClient.PANEL_KEY.matches(key, scan)) { onClose(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override public void onClose() { FearClient.sound(SoundEvents.LEVER_CLICK, 0.8F, 0.6F); Minecraft.getInstance().setScreen(null); }
}
