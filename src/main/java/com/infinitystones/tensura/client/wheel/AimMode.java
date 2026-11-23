package com.infinitystones.tensura.client.wheel;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneTree;
import com.infinitystones.tensura.wheel.AbilityMeta;
import com.infinitystones.tensura.wheel.WheelClientState;
import com.infinitystones.tensura.wheel.WheelPayloads;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Aim mode: entered after choosing a targeted power on the wheel. No screen is open, so the player walks, runs,
 * jumps and looks around freely. LEFT click confirms (sends the use; the server raycasts from the current look),
 * RIGHT click or Esc cancels. Alt + mouse wheel changes the power's setting. Also draws the channel HUD
 * (a channel is stopped with RIGHT click or a tap on the wheel key) — nothing here ever blocks movement.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class AimMode {
    /** What confirming does. */
    private record Pending(int stone, String node, boolean alt, @Nullable WheelPayloads.Destination destination) {}

    private static @Nullable Pending pending;
    private static long startedAt;
    private static boolean swallowAttack, swallowUse;
    private static @Nullable Entity target;
    private static double targetDist;

    public static boolean active() { return pending != null; }

    /** Enter aim mode for a power (left/right variant already chosen on the wheel). */
    public static void start(int stone, String node, boolean alt) { begin(new Pending(stone, node, alt, null)); }

    /** Enter aim mode for a destination use (Banishment after the destination screen). */
    public static void startDestination(WheelPayloads.Destination d) { begin(new Pending(d.stone(), d.node(), false, d)); }

    private static void begin(Pending p) {
        pending = p;
        startedAt = System.currentTimeMillis();
        swallowAttack = swallowUse = false;
        ui(SoundEvents.AMETHYST_BLOCK_CHIME, 1.8F, 0.25F);
    }

    public static void cancel() {
        if (pending == null) return;
        pending = null;
        target = null;
        MagicCircle.aimAt = null;
        ui(SoundEvents.AMETHYST_BLOCK_STEP, 0.7F, 0.35F);
    }

    private static void confirm() {
        Pending p = pending;
        if (p == null) return;
        pending = null;
        target = null;
        MagicCircle.aimAt = null;
        com.infinitystones.tensura.client.render.GauntletFx.pulse(p.stone());
        MagicCircle.flashAtFeet(p.stone(), true);
        if (p.destination() != null) PacketDistributor.sendToServer(p.destination());
        else PacketDistributor.sendToServer(new WheelPayloads.Use(p.stone(), p.node(), p.alt() ? 2 : 0));
        ui(SoundEvents.AMETHYST_BLOCK_RESONATE, p.alt() ? 1.1F : 1.4F, 0.35F);
    }

    // ------------------------------------------------------------ input

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void interaction(InputEvent.InteractionKeyMappingTriggered e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.player == null) return;
        if (pending != null) {
            // ignore the click that closed the wheel / the destination screen
            if (System.currentTimeMillis() - startedAt < 120) { e.setCanceled(true); e.setSwingHand(false); return; }
            if (e.isAttack()) { e.setCanceled(true); e.setSwingHand(true); swallowAttack = true; confirm(); }
            else if (e.isUseItem()) { e.setCanceled(true); e.setSwingHand(false); swallowUse = true; cancel(); }
            else if (e.isPickBlock()) { e.setCanceled(true); e.setSwingHand(false); }
            return;
        }
        String holding = WheelClientState.get().holding();
        if (!holding.isEmpty() && e.isUseItem()) { // right click stops the channel
            e.setCanceled(true);
            e.setSwingHand(false);
            swallowUse = true;
            WheelActions.stopChannel();
        }
    }

    /** Esc cancels aim mode instead of pausing. */
    @SubscribeEvent
    public static void opening(ScreenEvent.Opening e) {
        if (pending == null) return;
        if (e.getNewScreen() instanceof PauseScreen) { e.setCanceled(true); cancel(); }
        else if (e.getNewScreen() != null) cancel(); // inventory, chat… : leave aim mode, let the screen open
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void scroll(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        // plain scroll (or Alt + scroll) while aiming / channelling changes the power's level, same as scrolling it in the wheel
        if (mc.screen != null || mc.player == null) return;
        StoneTree.Node n = currentNode();
        if (n == null || AbilityMeta.setting(n, mc.player) == null || e.getScrollDeltaY() == 0) return;
        e.setCanceled(true);
        PacketDistributor.sendToServer(new WheelPayloads.Setting(n.stone().ordinal(), n.id(), e.getScrollDeltaY() > 0 ? 1 : -1));
        ui(SoundEvents.UI_BUTTON_CLICK.value(), e.getScrollDeltaY() > 0 ? 1.6F : 1.3F, 0.15F);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Pre e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { pending = null; MagicCircle.aimAt = null; return; }
        // the confirming/cancelling click must not keep mining or using an item while the button stays down
        if (swallowAttack) { if (mc.options.keyAttack.isDown()) mc.options.keyAttack.setDown(false); else swallowAttack = false; }
        if (swallowUse) { if (mc.options.keyUse.isDown()) mc.options.keyUse.setDown(false); else swallowUse = false; }
        if (pending == null) return;
        if (!mc.player.isAlive() || entry(pending.stone(), pending.node()) == null) { cancel(); return; }
        // what the server will most likely hit (client preview only; the server decides)
        target = null;
        Vec3 eye = mc.player.getEyePosition(), look = mc.player.getLookAngle();
        double range = 64;
        BlockHitResult block = mc.level.clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        double max = block.getType() == HitResult.Type.MISS ? range : block.getLocation().distanceTo(eye);
        Vec3 end = eye.add(look.scale(max));
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(mc.level, mc.player, eye, end,
                new AABB(eye, end).inflate(1.5), x -> x instanceof LivingEntity && x.isPickable() && !x.isSpectator(), 0.3F);
        if (hit != null) { target = hit.getEntity(); targetDist = hit.getLocation().distanceTo(eye); }
        else targetDist = max;
        // world circle: under the target, else on the aimed block / point
        StoneTree.Node an = node(pending.stone(), pending.node());
        MagicCircle.aimColor = an == null ? 0xFFFFFFFF : an.stone().color();
        MagicCircle.aimLocked = target != null;
        if (target != null) { MagicCircle.aimAt = target.position(); MagicCircle.aimRadius = Math.max(0.9F, target.getBbWidth() * 1.4F); }
        else { MagicCircle.aimAt = end; MagicCircle.aimRadius = 1.3F; }
    }

    // ------------------------------------------------------------ HUD

    @SubscribeEvent
    public static void hud(RenderGuiEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.screen != null) return;
        GuiGraphics g = e.getGuiGraphics();
        int cx = g.guiWidth() / 2, cy = g.guiHeight() / 2;
        long t = System.currentTimeMillis();
        if (pending != null) {
            StoneTree.Node n = node(pending.stone(), pending.node());
            if (n == null) return;
            int col = n.stone().color();
            float in = Mth.clamp((t - startedAt) / 180F, 0, 1);
            float r = 13 + 6 * (1 - in) + Mth.sin(t / 160F) * 1.2F;
            float spin = (t % 4000) / 4000F * Mth.TWO_PI;
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            MagicCircle.hud(g, r * 1.35F, col, in, target != null);
            g.pose().popPose();

            MutableComponent title = n.displayName().copy().withStyle(s -> s.withColor(col & 0xFFFFFF).withBold(true));
            if (pending.alt()) title.append(Component.literal(" ②").withStyle(ChatFormatting.AQUA));
            if (pending.destination() != null) title.append(Component.literal(" → ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(shortDim(pending.destination().dimension())).withStyle(ChatFormatting.AQUA));
            int y = cy + (int) r + 10;
            centered(g, title, cx, y, 1F);
            y += 11;
            Component aimed = target != null
                    ? Component.translatableWithFallback("infinity.aim.target", "Target: %s (%s m)", target.getDisplayName(), String.valueOf((int) targetDist)).withStyle(ChatFormatting.WHITE)
                    : Component.translatableWithFallback("infinity.aim.point", "Point: %s m ahead", String.valueOf((int) targetDist)).withStyle(ChatFormatting.GRAY);
            centered(g, aimed, cx, y, 0.8F);
            y += 9;
            centered(g, Component.translatableWithFallback("infinity.aim.keys", "[LMB] confirm  ·  [RMB] / Esc cancel").withStyle(ChatFormatting.YELLOW), cx, y, 0.8F);
            y += 9;
            settingLine(g, n, cx, y);
            return;
        }
        String holding = WheelClientState.get().holding();
        if (!holding.isEmpty()) {
            StoneTree.Node n = WheelActions.nodeByMode(holding);
            if (n == null) return;
            int col = n.stone().color();
            int y = cy + 22;
            MutableComponent title = Component.literal("⟳ ").withStyle(s -> s.withColor(col & 0xFFFFFF))
                    .append(n.displayName().copy().withStyle(s -> s.withColor(col & 0xFFFFFF).withBold(true)));
            float k = 0.75F + 0.25F * Mth.sin(t / 200F);
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            WheelScreen.ring(g, 0, 0, 11, 12.4F, 0, Mth.TWO_PI * ((t % 1200) / 1200F), withAlpha(col, (int) (200 * k)));
            g.pose().popPose();
            centered(g, title, cx, y, 0.9F);
            centered(g, Component.translatableWithFallback("infinity.aim.channel_keys", "Channelling · [RMB] or tap %s: stop",
                    WheelKeys.WHEEL.getTranslatedKeyMessage()).withStyle(ChatFormatting.YELLOW), cx, y + 10, 0.75F);
            settingLine(g, n, cx, y + 19);
        }
    }

    private static void settingLine(GuiGraphics g, StoneTree.Node n, int cx, int y) {
        Component s = WheelActions.settingText(n, true);
        if (s != null) centered(g, s, cx, y, 0.8F);
    }

    private static void centered(GuiGraphics g, Component c, int cx, int y, float scale) {
        var font = Minecraft.getInstance().font;
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1);
        int w = font.width(c);
        g.fill(-w / 2 - 3, -2, w / 2 + 3, 9, 0x88000000);
        g.drawString(font, c, -w / 2, 0, 0xFFFFFFFF, true);
        g.pose().popPose();
    }

    // ------------------------------------------------------------ helpers

    /** Power the HUD/Alt+scroll refers to: the one being aimed, else the channel in progress. */
    static @Nullable StoneTree.Node currentNode() {
        if (pending != null) return node(pending.stone(), pending.node());
        String holding = WheelClientState.get().holding();
        return holding.isEmpty() ? null : WheelActions.nodeByMode(holding);
    }

    private static @Nullable StoneTree.Node node(int stone, String node) {
        return stone < 0 || stone >= Stone.values().length ? null : StoneTree.of(Stone.values()[stone]).nodes.get(node);
    }

    private static @Nullable WheelPayloads.Entry entry(int stone, String node) {
        for (WheelPayloads.Entry e : WheelClientState.get().entries()) if (e.stone() == stone && e.node().equals(node) && e.unlocked()) return e;
        return null;
    }

    private static String shortDim(String id) { int i = id.indexOf(':'); return i < 0 ? id : id.substring(i + 1); }
    private static int withAlpha(int c, int a) { return (Mth.clamp(a, 0, 255) << 24) | (c & 0xFFFFFF); }

    private static void ui(net.minecraft.sounds.SoundEvent s, float pitch, float vol) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, vol));
    }

    private AimMode() {}
}
