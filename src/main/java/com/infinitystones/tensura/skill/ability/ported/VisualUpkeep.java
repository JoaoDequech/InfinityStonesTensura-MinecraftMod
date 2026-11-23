package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.NodeEffects;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Keeps the flags the original overlays read: the radiation skin hide tag (no_radiation_texture) and the green Time
 * screen (original gui overlay while fast_forward / projectile_stop / reset_time_speed / time_freeze was enabled,
 * unless the tag no_time_screen was set).
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class VisualUpkeep {
    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.tickCount % 4 != 3) return;
        VisualSync.set(p, VisualSync.HIDE_RADIATION, p.getTags().contains("no_radiation_texture"));
        VisualSync.set(p, VisualSync.TIME_SCREEN, timeScreen(p));
    }

    private static boolean timeScreen(ServerPlayer p) {
        if (p.getTags().contains("no_time_screen") || !p.isAlive()) return false;
        if (CommandPorts.isFreezer(p) || CommandPorts.timeSpeedOn(p) || CommandPorts.isFastForward(p)) return true;
        StoneTree.Node stop = StoneTree.of(Stone.TIME).nodes.get("projectile_stop");
        return stop != null && NodeEffects.isToggled(p, stop) && Stone.TIME.isHeldBy(p);
    }

    private VisualUpkeep() {}
}
