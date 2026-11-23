package com.infinitystones.tensura.wheel;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.NodeEffects;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneProgress;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mind flight extras driven by the wheel: flying speed = the flight switch's level (scroll over it on the wheel), and
 * above half of the bar the "paladin" pose (lying horizontal like a super-hero) while flying fast. The pose flag is
 * sent to the flyer and everyone tracking them, so it is the same for all players. No OP needed.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class FlightControl {
    private static final float VANILLA = 0.05F;
    private static final Set<UUID> SPEED_SET = new HashSet<>();
    private static final Set<UUID> PALADIN = new HashSet<>();
    private static final Map<UUID, Integer> FAST_TICKS = new HashMap<>();
    private static final Map<UUID, net.minecraft.world.phys.Vec3> LAST_POS = new HashMap<>();

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        StoneTree.Node n = StoneTree.of(Stone.MIND).nodes.get("flight-dummy");
        if (n == null) return;
        UUID id = p.getUUID();
        boolean active = p.isAlive() && p.getAbilities().mayfly && NodeEffects.isToggled(p, n) && Stone.MIND.isHeldBy(p)
                && StoneProgress.isUnlocked(p, Stone.MIND, n.id());
        if (!active && !SPEED_SET.contains(id) && !PALADIN.contains(id) && FAST_TICKS.getOrDefault(id, 0) == 0) {
            LAST_POS.remove(id);
            return;
        }
        int level = active ? AbilitySettings.get(p, n) : 0;
        if (active) {
            float speed = 0.02F * level;
            if (Math.abs(p.getAbilities().getFlyingSpeed() - speed) > 1e-4) { p.getAbilities().setFlyingSpeed(speed); p.onUpdateAbilities(); }
            SPEED_SET.add(id);
        } else if (SPEED_SET.remove(id)) {
            p.getAbilities().setFlyingSpeed(VANILLA);
            p.onUpdateAbilities();
        }
        // paladin pose: flying, level above half, actually moving fast (with a short hysteresis so it does not flicker)
        AbilityMeta.SettingSpec spec = AbilityMeta.setting(n);
        // own last-position map: on the server the client-driven movement lands between ticks (xo == x inside the tick)
        net.minecraft.world.phys.Vec3 last = LAST_POS.put(id, p.position());
        double moved = last == null ? 0 : p.position().subtract(last).multiply(1, 0.7, 1).length();
        boolean fastNow = active && spec != null && level > (spec.min() + spec.max()) / 2 && p.getAbilities().flying && moved > 0.35;
        int ticks = FAST_TICKS.getOrDefault(id, 0);
        ticks = fastNow ? Math.min(ticks + 1, 10) : Math.max(ticks - 1, 0);
        if (ticks == 0) FAST_TICKS.remove(id); else FAST_TICKS.put(id, ticks);
        boolean was = PALADIN.contains(id);
        boolean now = was ? ticks > 0 && active && p.getAbilities().flying : ticks >= 4;
        if (now != was) {
            if (now) PALADIN.add(id); else PALADIN.remove(id);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new WheelPayloads.FlyPose(p.getId(), now));
        }
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer flyer && PALADIN.contains(flyer.getUUID()) && e.getEntity() instanceof ServerPlayer viewer)
            PacketDistributor.sendToPlayer(viewer, new WheelPayloads.FlyPose(flyer.getId(), true));
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        SPEED_SET.remove(id); FAST_TICKS.remove(id); LAST_POS.remove(id);
        if (PALADIN.remove(id) && e.getEntity() instanceof ServerPlayer p)
            PacketDistributor.sendToPlayersTrackingEntity(p, new WheelPayloads.FlyPose(p.getId(), false));
    }

    private FlightControl() {}
}
