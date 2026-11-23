package com.infinitystones.tensura.client.port;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.gauntlet.ArtifactSwap;
import com.infinitystones.tensura.gauntlet.GauntletMenu;
import com.infinitystones.tensura.skill.GauntletStones;
import com.infinitystones.tensura.skill.Stone;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Gauntlet socket screen, drawn procedurally (no texture): dark gold panel, a gauntlet silhouette with six glowing
 * sockets in each stone's colour (empty sockets show a faint ghost of the stone), pulsing halos and the stone name on
 * hover; player inventory below.
 */
public final class GauntletScreen extends AbstractContainerScreen<GauntletMenu> {
    private float time;

    public GauntletScreen(GauntletMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = 196;
        imageHeight = 196;
        inventoryLabelY = 100;
        inventoryLabelX = 18;
        titleLabelX = 12;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        time += pt / 20F;
        super.render(g, mx, my, pt);
        renderTooltip(g, mx, my);
    }

    @Override
    protected void renderBg(GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        // panel: layered border, gradient body
        g.fill(x - 2, y - 2, x + imageWidth + 2, y + imageHeight + 2, 0xFF6B4A12);
        g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, 0xFFE0B040);
        g.fillGradient(x, y, x + imageWidth, y + imageHeight, 0xFF1B1408, 0xFF0B0805);
        // gauntlet silhouette: four fingers, palm, thumb
        int gold = 0xFFB8862A, goldHi = 0xFFE9C25A, dark = 0xFF5A3E10;
        int[][] fingers = {{44, 14, 20, 40}, {72, 6, 20, 46}, {100, 6, 20, 46}, {128, 14, 20, 40}};
        for (int[] f : fingers) {
            g.fill(x + f[0], y + f[1], x + f[0] + f[2], y + f[1] + f[3], dark);
            g.fill(x + f[0] + 1, y + f[1] + 1, x + f[0] + f[2] - 1, y + f[1] + f[3], gold);
            g.fill(x + f[0] + 2, y + f[1] + 2, x + f[0] + 5, y + f[1] + f[3] - 2, goldHi);
            g.fill(x + f[0] + 1, y + f[1] + f[3] / 2, x + f[0] + f[2] - 1, y + f[1] + f[3] / 2 + 1, dark);
        }
        g.fill(x + 42, y + 52, x + 150, y + 96, dark);
        g.fill(x + 43, y + 53, x + 149, y + 96, gold);
        g.fill(x + 45, y + 55, x + 50, y + 94, goldHi);
        g.fill(x + 24, y + 66, x + 46, y + 96, dark); // thumb
        g.fill(x + 25, y + 67, x + 45, y + 96, gold);
        // sockets
        for (int i = 0; i < 6; i++) {
            Slot s = menu.slots.get(i);
            Stone stone = menu.stoneAt(i);
            int c = stone.color() & 0xFFFFFF;
            int sx = x + s.x, sy = y + s.y;
            boolean filled = s.hasItem();
            float pulse = 0.5F + 0.5F * Mth.sin(time * 4F + i);
            int haloA = filled ? (int) (90 + 80 * pulse) : 40;
            for (int r = 6; r >= 1; r--) {
                int a = haloA * (7 - r) / 14;
                g.fill(sx - r, sy - r, sx + 16 + r, sy + 16 + r, (a << 24) | c);
            }
            g.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFF2A1E08);
            g.fill(sx, sy, sx + 16, sy + 16, filled ? 0xFF000000 | darker(c) : 0xFF120D05);
            if (!filled) { // ghost of the missing stone
                g.pose().pushPose();
                g.renderFakeItem(new ItemStack(GauntletStones.gem(stone)), sx, sy);
                g.pose().popPose();
                g.fill(sx, sy, sx + 16, sy + 16, 0xB0120D05);
            }
        }
        // inventory well
        g.fill(x + 16, y + 110, x + 180, y + 188, 0x60000000);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, title, titleLabelX, 4, 0xFFE9C25A, true);
        int n = 0;
        for (int i = 0; i < 6; i++) if (menu.slots.get(i).hasItem()) n++;
        g.drawString(font, n + "/6", imageWidth - 26, 4, n == 6 ? 0xFFFFFFFF : 0xFFB8862A, true);
        for (int i = 0; i < 6; i++) {
            Slot s = menu.slots.get(i);
            if (isHovering(s.x, s.y, 16, 16, mx, my) && !s.hasItem()) {
                Component name = Component.translatableWithFallback("infinity.gauntlet.socket." + menu.stoneAt(i).key,
                        capital(menu.stoneAt(i).key) + " Stone");
                g.drawString(font, name, s.x - font.width(name) / 2 + 8, s.y + 19, menu.stoneAt(i).color(), true);
            }
        }
    }

    private static String capital(String s) { return Character.toUpperCase(s.charAt(0)) + s.substring(1); }

    private static int darker(int c) {
        return (((c >> 16) & 255) / 4 << 16) | (((c >> 8) & 255) / 4 << 8) | ((c & 255) / 4);
    }

    // ---------------- registration + Shift-look-up-hit extraction input ----------------

    @EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void screens(RegisterMenuScreensEvent e) { e.register(GauntletMenu.TYPE, GauntletScreen::new); }
        private Setup() {}
    }

    @EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
    public static final class Input {
        @SubscribeEvent
        public static void leftAir(PlayerInteractEvent.LeftClickEmpty e) {
            if (ArtifactSwap.canExtractByLeftClick(e.getEntity())) PacketDistributor.sendToServer(new ArtifactSwap.Extract());
        }
        @SubscribeEvent
        public static void leftBlock(PlayerInteractEvent.LeftClickBlock e) {
            if (!e.getLevel().isClientSide || e.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) return;
            if (e.getEntity() != Minecraft.getInstance().player || !ArtifactSwap.canExtractByLeftClick(e.getEntity())) return;
            PacketDistributor.sendToServer(new ArtifactSwap.Extract());
            e.setCanceled(true);
        }
        private Input() {}
    }
}
