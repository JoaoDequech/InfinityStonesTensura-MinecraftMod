package com.infinitystones.tensura.client.port;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.ported.MobSkins;
import com.infinitystones.tensura.skill.ability.ported.VisualSync;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Client timers behind the original animation timers: Intangibility "vibrate" 0..4, Power "radiaton_timer" 0..1000 and
 * "t-pose" 0..8. Each counts up one per tick while the server's flag is set and down while it is not (Palladium
 * animation timer behaviour). The server only sends flag changes, so the client advances the timers itself.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class PortVisuals {
    public static final int VIBRATE_MAX = 4, RADIATION_MAX = 1000, TPOSE_MAX = 8;

    private static final class Timers { int vibrate, radiation, tpose, pVibrate, pRadiation, pTpose; }
    private static final Map<Integer, Timers> TIMERS = new HashMap<>();

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (Minecraft.getInstance().level == null) { TIMERS.clear(); return; }
        if (TIMERS.isEmpty() && VisualSync.CLIENT.isEmpty()) return;
        Set<Integer> ids = new HashSet<>(TIMERS.keySet());
        ids.addAll(VisualSync.CLIENT.keySet());
        for (int id : ids) {
            int flags = VisualSync.CLIENT.getOrDefault(id, 0);
            Timers t = TIMERS.computeIfAbsent(id, k -> new Timers());
            t.pVibrate = t.vibrate; t.pRadiation = t.radiation; t.pTpose = t.tpose;
            t.vibrate = Mth.clamp(t.vibrate + ((flags & VisualSync.VIBRATE) != 0 ? 1 : -1), 0, VIBRATE_MAX);
            boolean rad = (flags & VisualSync.RADIATION) != 0;
            t.radiation = Mth.clamp(t.radiation + (rad ? 1 : -1), 0, RADIATION_MAX);
            t.tpose = Mth.clamp(t.tpose + (rad ? 1 : -1), 0, TPOSE_MAX);
            if (flags == 0 && t.vibrate == 0 && t.radiation == 0 && t.tpose == 0) TIMERS.remove(id);
        }
    }

    /** Eased 0..1 vibrate progress (original INOUTSINE). */
    public static float vibrate(Entity e, float partial) {
        if (TIMERS.isEmpty()) return 0;
        Timers t = TIMERS.get(e.getId());
        if (t == null || (t.vibrate == 0 && t.pVibrate == 0)) return 0;
        float x = Mth.clamp(Mth.lerp(partial, t.pVibrate, t.vibrate) / VIBRATE_MAX, 0F, 1F);
        return -(Mth.cos((float) Math.PI * x) - 1F) / 2F;
    }

    /** Radiation timer 0..1000 (interpolated), 0 when none. */
    public static float radiation(Entity e, float partial) {
        if (TIMERS.isEmpty()) return 0;
        Timers t = TIMERS.get(e.getId());
        return t == null ? 0 : Mth.lerp(partial, t.pRadiation, t.radiation);
    }

    /** 0..1 T-pose progress. */
    public static float tpose(Entity e, float partial) {
        if (TIMERS.isEmpty()) return 0;
        Timers t = TIMERS.get(e.getId());
        return t == null ? 0 : Mth.clamp(Mth.lerp(partial, t.pTpose, t.tpose) / TPOSE_MAX, 0F, 1F);
    }

    public static boolean radiationHidden(Entity e) {
        return !VisualSync.CLIENT.isEmpty() && (VisualSync.CLIENT.getOrDefault(e.getId(), 0) & VisualSync.HIDE_RADIATION) != 0;
    }

    public static boolean timeScreen() {
        var p = Minecraft.getInstance().player;
        return p != null && (VisualSync.CLIENT.getOrDefault(p.getId(), 0) & VisualSync.TIME_SCREEN) != 0;
    }

    @SubscribeEvent
    public static void left(EntityLeaveLevelEvent e) {
        if (!e.getLevel().isClientSide()) return;
        VisualSync.CLIENT.remove(e.getEntity().getId());
        MobSkins.CLIENT.remove(e.getEntity().getId());
        TIMERS.remove(e.getEntity().getId());
    }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut e) {
        VisualSync.CLIENT.clear();
        MobSkins.CLIENT.clear();
        TIMERS.clear();
    }

    private PortVisuals() {}
}
