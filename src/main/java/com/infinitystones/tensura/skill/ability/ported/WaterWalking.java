package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Time "water_walking" (hidden helper, original palladium:water_walk, unlocked while Time Freeze is enabled): water
 * and waterlogged blocks get a solid top (collision shape added by BlockStateBaseMixin, same rule as Palladium's
 * BlockBehaviourMixin). The server decides; the client learns its own state through {@link VisualSync#WATER_WALK}.
 */
public final class WaterWalking implements Ported {
    @Override public void start(ServerPlayer p, StoneTree.Node n) { sync(p); }
    @Override public void tick(ServerPlayer p, StoneTree.Node n, int ticks) { sync(p); }
    @Override public void stop(ServerPlayer p, StoneTree.Node n) { VisualSync.set(p, VisualSync.WATER_WALK, false); }

    private static void sync(ServerPlayer p) { VisualSync.set(p, VisualSync.WATER_WALK, CommandPorts.isFreezer(p)); }

    /** Queried by the collision mixin on both sides. */
    public static boolean canWalk(Entity e) {
        if (!(e instanceof Player p)) return false;
        if (p instanceof ServerPlayer sp) return VisualSync.has(sp, VisualSync.WATER_WALK);
        return p.level().isClientSide && (VisualSync.CLIENT.getOrDefault(p.getId(), 0) & VisualSync.WATER_WALK) != 0;
    }
}
