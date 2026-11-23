package com.infinitystones.tensura.client.wheel;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import io.github.manasmods.tensura.client.screen.AbilityCategoriesScreen;
import io.github.manasmods.tensura.client.screen.templates.SimpleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Hidden "Infinity Stones" tab on Tensura's Skills screen: drawn beside Tensura's panel only while the player carries
 * a stone. Nothing is injected into Tensura's slots or widgets — it is our own overlay that opens our tree screen.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class TensuraTab {
    private static final int W = 24, H = 64;
    private static Field guiRightF, guiTopF, guiLeftF, typeF, scaleF, txF, tyF;
    private static boolean logged;
    private static boolean reflectTried;
    private static long hoverSince = -1;

    private static List<Stone> carried() {
        List<Stone> out = new ArrayList<>();
        var p = Minecraft.getInstance().player;
        if (p != null) for (Stone s : Stone.values()) if (s.isHeldBy(p)) out.add(s);
        return out;
    }

    private static boolean applies(Screen screen) {
        if (!(screen instanceof AbilityCategoriesScreen)) return false;
        reflect();
        if (typeF != null) {
            try { return "SKILL".equals(String.valueOf(typeF.get(screen))); } catch (Exception ignored) { }
        }
        return true;
    }

    private static void reflect() {
        if (reflectTried) return;
        reflectTried = true;
        try {
            guiRightF = SimpleScreen.class.getDeclaredField("guiRight"); guiRightF.setAccessible(true);
            guiTopF = SimpleScreen.class.getDeclaredField("guiTop"); guiTopF.setAccessible(true);
            guiLeftF = SimpleScreen.class.getDeclaredField("guiLeft"); guiLeftF.setAccessible(true);
            scaleF = SimpleScreen.class.getDeclaredField("scale"); scaleF.setAccessible(true);
            txF = SimpleScreen.class.getDeclaredField("translateX"); txF.setAccessible(true);
            tyF = SimpleScreen.class.getDeclaredField("translateY"); tyF.setAccessible(true);
        } catch (Exception e) { guiRightF = guiTopF = null; }
        try { typeF = AbilityCategoriesScreen.class.getDeclaredField("abilityType"); typeF.setAccessible(true); } catch (Exception e) { typeF = null; }
    }

    /** Tab rectangle (x, y) in screen space: glued to the right edge of Tensura's panel. */
    private static int[] pos(Screen s) {
        int x = s.width / 2 - 254 / 2 - W - 3, y = s.height / 2 - 163 / 2 + 30;
        try {
            if (guiLeftF != null && guiTopF != null && scaleF != null && txF != null && tyF != null) {
                // Tensura draws its panel as translate(translateX, translateY) · scale(scale) in its own space
                float sc = scaleF.getFloat(s), tx = txF.getFloat(s), ty = tyF.getFloat(s);
                int l = guiLeftF.getInt(null), t = guiTopF.getInt(null);
                if (!logged) { logged = true; InfinityStones.LOGGER.info("[tab] w={} h={} scale={} tx={} ty={} guiLeft={} guiTop={}", s.width, s.height, sc, tx, ty, l, t); }
                if (sc > 0.1F) {
                    // left edge of Tensura's panel (its right side already has Tensura's own side tabs)
                    int lx = Math.round(tx + sc * l), ty2 = Math.round(ty + sc * t);
                    if (lx - W - 3 > 0) { x = lx - W - 3; y = ty2 + Math.round(30 * sc); }
                }
            }
        } catch (Exception ignored) { }
        x = Mth.clamp(x, 2, s.width - W - 2);
        return new int[]{x, y};
    }

    @SubscribeEvent
    public static void render(ScreenEvent.Render.Post e) {
        Screen s = e.getScreen();
        if (!applies(s)) return;
        List<Stone> stones = carried();
        if (stones.isEmpty()) return;
        GuiGraphics g = e.getGuiGraphics();
        int[] p = pos(s);
        int x = p[0], y = p[1];
        boolean hov = e.getMouseX() >= x && e.getMouseX() < x + W && e.getMouseY() >= y && e.getMouseY() < y + H;
        long t = System.currentTimeMillis();
        if (hov && hoverSince < 0) hoverSince = t;
        if (!hov) hoverSince = -1;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        // glow in the colour of the carried stones, cycling
        Stone cur = stones.get((int) ((t / 1600) % stones.size()));
        int col = cur.color() & 0xFFFFFF;
        float pulse = 0.5F + 0.5F * Mth.sin(t / 350F);
        for (int i = 4; i >= 1; i--) g.fill(x - i, y - i, x + W + i, y + H + i, ((int) ((10 + 18 * pulse + (hov ? 20 : 0)) * (5 - i)) << 24) | col);
        g.fill(x, y, x + W, y + H, 0xF00A0B12);
        // six-stone gauntlet strip: one dot per stone, lit when carried
        int shimmer = (int) ((t / 110) % 14); // a glint running down the lit stones
        for (int i = 0; i < 6; i++) {
            Stone st = Stone.values()[i];
            boolean has = stones.contains(st);
            int y0 = y + 26 + i * 6;
            if (has) {
                int c = st.color() & 0xFFFFFF;
                float sp = 0.5F + 0.5F * Mth.sin(t / 300F + i);
                g.fill(x + 2, y0 - 1, x + W - 2, y0 + 5, ((int) (0x30 + 0x30 * sp) << 24) | c);   // glow
                g.fill(x + 3, y0, x + W - 3, y0 + 4, 0xFF000000 | c);                             // gem
                g.fill(x + 4, y0, x + W - 4, y0 + 1, 0xC0FFFFFF & (0xFF000000 | lighten(c)));      // top facet
                g.fill(x + 3, y0 + 3, x + W - 3, y0 + 4, 0x60000000);                             // lower facet shade
                if (shimmer == i) g.fill(x + 5, y0 + 1, x + 8, y0 + 3, 0xE0FFFFFF);
            } else {
                g.fill(x + 3, y0, x + W - 3, y0 + 4, 0xFF1A1B22);
                g.fill(x + 3, y0, x + W - 3, y0 + 1, 0xFF2A2C36);
            }
        }
        g.fill(x, y, x + 1, y + H, 0xFF000000 | col);
        ItemStack icon = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("infinity", cur.key + "_stone")));
        float bob = Mth.sin(t / 400F) * 1.5F;
        g.pose().pushPose();
        g.pose().translate(x + 4, y + 4 + bob, 0);
        g.renderItem(icon, 0, 0);
        g.pose().popPose();
        if (hov) g.renderTooltip(Minecraft.getInstance().font, List.of(
                Component.translatable("infinity.tree.title").getVisualOrderText(),
                Component.translatable("infinity.tree.tab_hint").getVisualOrderText()), e.getMouseX(), e.getMouseY());
        g.pose().popPose();
    }

    @SubscribeEvent
    public static void click(ScreenEvent.MouseButtonPressed.Pre e) {
        Screen s = e.getScreen();
        if (e.getButton() != 0 || !applies(s)) return;
        List<Stone> stones = carried();
        if (stones.isEmpty()) return;
        int[] p = pos(s);
        if (e.getMouseX() >= p[0] && e.getMouseX() < p[0] + W && e.getMouseY() >= p[1] && e.getMouseY() < p[1] + H) {
            Minecraft mc = Minecraft.getInstance();
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.1F, 0.3F));
            mc.setScreen(new StoneTreeScreen(stones.get(0)));
            e.setCanceled(true);
        }
    }

    private static int lighten(int c) {
        int r = (c >> 16) & 255, gg = (c >> 8) & 255, b = c & 255;
        return ((r + (255 - r) * 2 / 3) << 16) | ((gg + (255 - gg) * 2 / 3) << 8) | (b + (255 - b) * 2 / 3);
    }

    private TensuraTab() {}
}
