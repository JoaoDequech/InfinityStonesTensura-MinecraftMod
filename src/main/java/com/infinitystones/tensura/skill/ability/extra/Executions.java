package com.infinitystones.tensura.skill.ability.extra;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Executions by hidden powers: a kill that nothing softens (generic kill damage, then every override other mods might
 * have put in the way) that leaves no death message in chat or in the log. Everything runs synchronously on the server
 * thread, so the death message is muted by switching the vanilla "show death messages" rule off for the length of the call.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class Executions {
    private static final Set<UUID> EXECUTING = new HashSet<>();

    /** Runs the action with no death message broadcast (also pets' "your cat died" lines). Safe to nest. */
    public static void silently(MinecraftServer server, Runnable action) {
        if (server == null) { action.run(); return; }
        GameRules.BooleanValue rule = server.getGameRules().getRule(GameRules.RULE_SHOWDEATHMESSAGES);
        boolean was = rule.get();
        if (was) rule.set(false, server);
        try {
            action.run();
        } finally {
            if (was) rule.set(true, server);
        }
    }

    /** Kills for sure and silently. Non-living entities are removed. */
    public static void kill(Entity target) {
        if (!(target instanceof LivingEntity e)) {
            if (!target.isRemoved()) target.discard();
            return;
        }
        if (e.isRemoved()) return;
        silently(e.getServer(), () -> {
            UUID id = e.getUUID();
            EXECUTING.add(id);
            try {
                DamageSource src = e.damageSources().genericKill();
                e.invulnerableTime = 0;
                e.hurt(src, Float.MAX_VALUE);
                if (e.isAlive()) e.setHealth(0F);
                if (!e.isRemoved()) e.die(src);
                if (e.isAlive() && !(e instanceof Player)) e.discard();
            } finally {
                EXECUTING.remove(id);
            }
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void executingDamage(LivingIncomingDamageEvent e) {
        if (EXECUTING.isEmpty() || !EXECUTING.contains(e.getEntity().getUUID())) return;
        e.setCanceled(false);
        e.setAmount(Float.MAX_VALUE);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void executingDeath(LivingDeathEvent e) {
        if (!EXECUTING.isEmpty() && EXECUTING.contains(e.getEntity().getUUID())) e.setCanceled(false);
    }

    private Executions() {}
}
