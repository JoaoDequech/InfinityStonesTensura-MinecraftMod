package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mind Stone "Psychic Dominion" — port of PsychicDominionAbility. On activation (timed session, 240 ticks) the aimed
 * hostile mob and its nearest hostile neighbours (cluster_radius, up to max_targets) are temporarily turned against
 * other hostile mobs within target_radius, without changing ownership or saved AI data. Boss-like mobs (dragon, wither,
 * warden or 300+ max health) are instead given Weakness. Control expires after duration_ticks via the mob's own tick,
 * so it also ends correctly if the caster logs out; it is restored early when the session ends.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class PsychicDominion implements Ported {

    private record Control(UUID owner, @Nullable UUID originalTarget, long expiresAt, float targetRadius, int color) {}

    /** Controlled mob UUID → control (server thread only). */
    private static final Map<UUID, Control> CONTROLS = new HashMap<>();

    @Override
    public void start(ServerPlayer p, StoneTree.Node n) {
        ServerLevel level = p.serverLevel();
        float maxDistance = PortFx.f(n, "max_distance", 32F);
        float clusterRadius = PortFx.f(n, "cluster_radius", 10F);
        float targetRadius = PortFx.f(n, "target_radius", 18F);
        int maxTargets = PortFx.i(n, "max_targets", 3);
        int durationTicks = PortFx.i(n, "duration_ticks", 240);
        int bossAmp = PortFx.i(n, "boss_weakness_amplifier", 3);
        int color = n.stone().color();

        LivingEntity aimed = PortFx.aimed(p, maxDistance, t -> t instanceof Mob m && eligible(p, m));
        if (!(aimed instanceof Mob first)) return;

        List<Mob> candidates = new ArrayList<>();
        candidates.add(first);
        level.getEntitiesOfClass(Mob.class, first.getBoundingBox().inflate(clusterRadius), m -> m != first && eligible(p, m))
                .stream().sorted(Comparator.comparingDouble(first::distanceToSqr)).forEach(candidates::add);

        int affected = 0;
        for (Mob mob : candidates) {
            if (affected >= maxTargets) break;
            if (isBossLike(mob)) {
                mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, durationTicks, bossAmp, false, true, true));
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, mob.getX(), mob.getEyeY(), mob.getZ(),
                        36, mob.getBbWidth() * 0.5, mob.getBbHeight() * 0.4, mob.getBbWidth() * 0.5, 0.12);
                castSound(level, n, mob, 0);
                affected++;
            } else if (!CONTROLS.containsKey(mob.getUUID())) {
                UUID originalTarget = mob.getTarget() == null ? null : mob.getTarget().getUUID();
                Control control = new Control(p.getUUID(), originalTarget, level.getGameTime() + durationTicks, targetRadius, color);
                CONTROLS.put(mob.getUUID(), control);
                updateControlledMob(mob, control);
                renderCrown(level, mob, control.color, true);
                castSound(level, n, mob, affected);
                affected++;
            }
        }
    }

    @Override
    public void tick(ServerPlayer p, StoneTree.Node n, int ticks) {
        ServerLevel level = p.serverLevel();
        if (level.getGameTime() % 5 != 0 || CONTROLS.isEmpty()) return;
        for (var entry : List.copyOf(CONTROLS.entrySet())) {
            if (!entry.getValue().owner.equals(p.getUUID())) continue;
            if (level.getEntity(entry.getKey()) instanceof Mob mob) renderCrown(level, mob, entry.getValue().color, false);
        }
    }

    @Override
    public void stop(ServerPlayer p, StoneTree.Node n) {
        ServerLevel level = p.serverLevel();
        for (var entry : List.copyOf(CONTROLS.entrySet())) {
            if (!entry.getValue().owner.equals(p.getUUID())) continue;
            if (level.getEntity(entry.getKey()) instanceof Mob mob) restore(mob, entry.getValue());
            // mobs in other dimensions: removing the entry is enough, their target simply re-acquires normally
            CONTROLS.remove(entry.getKey());
        }
    }

    // ---------------- hooks ----------------

    /** Original PowerEventHandler#entityTick → tickControlledMob: keeps the redirected target and handles expiry. */
    @SubscribeEvent
    public static void entityTick(EntityTickEvent.Post e) {
        Entity entity = e.getEntity();
        if (entity.level().isClientSide() || CONTROLS.isEmpty() || !(entity instanceof Mob mob)) return;
        Control control = CONTROLS.get(mob.getUUID());
        if (control == null || !(mob.level() instanceof ServerLevel level)) return;
        if (!mob.isAlive() || level.getGameTime() >= control.expiresAt) {
            restore(mob, control);
            CONTROLS.remove(mob.getUUID());
            return;
        }
        updateControlledMob(mob, control);
    }

    /** Drops entries of mobs that were unloaded while controlled (they never tick again to expire themselves). */
    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post e) {
        if (CONTROLS.isEmpty() || e.getServer().getTickCount() % 100 != 0) return;
        long now = e.getServer().overworld().getGameTime();
        CONTROLS.values().removeIf(c -> now >= c.expiresAt + 40);
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent e) { CONTROLS.clear(); }

    // ---------------- logic ----------------

    private static void updateControlledMob(Mob mob, Control control) {
        if (!(mob.level() instanceof ServerLevel level)) return;
        Entity owner = level.getEntity(control.owner);
        LivingEntity target = mob.getTarget();
        if (!isCombatTarget(mob, target, owner, control.targetRadius)) {
            target = level.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(control.targetRadius),
                            c -> isCombatTarget(mob, c, owner, control.targetRadius))
                    .stream().min(Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
            mob.setTarget(target);
            if (target == null) mob.getNavigation().stop();
        }
    }

    private static boolean isCombatTarget(Mob controlled, @Nullable LivingEntity candidate, @Nullable Entity owner, float radius) {
        return candidate != null && candidate != controlled && candidate != owner && candidate.isAlive()
                && candidate instanceof Enemy && !CONTROLS.containsKey(candidate.getUUID())
                && controlled.distanceToSqr(candidate) <= radius * radius && controlled.hasLineOfSight(candidate);
    }

    private static boolean eligible(ServerPlayer caster, Mob target) {
        return target.isAlive() && target instanceof Enemy && !CONTROLS.containsKey(target.getUUID())
                && PortFx.canAffect(caster, target);
    }

    private static boolean isBossLike(Mob mob) {
        return mob instanceof EnderDragon || mob instanceof WitherBoss || mob instanceof Warden || mob.getMaxHealth() >= 300F;
    }

    private static void castSound(ServerLevel level, StoneTree.Node n, Mob mob, int index) {
        Vec3 at = new Vec3(mob.getX(), mob.getEyeY(), mob.getZ());
        PortFx.sound(level, at, n, "infinity:mind_stone_phase", 0.6F, 0.9F + index * 0.08F);
        PortFx.sound(level, at, SoundEvents.EVOKER_CAST_SPELL, 0.5F, 1.15F + index * 0.06F);
    }

    private static void renderCrown(ServerLevel level, Mob mob, int color, boolean burst) {
        DustParticleOptions gold = PortFx.dust(color, 1.05F);
        double y = mob.getY() + mob.getBbHeight() + 0.35;
        int points = burst ? 24 : 10;
        double radius = mob.getBbWidth() * 0.55D + 0.25D;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2D * i / points + level.getGameTime() * 0.08D;
            level.sendParticles(gold, mob.getX() + Math.cos(angle) * radius, y, mob.getZ() + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
        }
        if (burst) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, mob.getX(), mob.getEyeY(), mob.getZ(),
                    22, mob.getBbWidth() * 0.4, mob.getBbHeight() * 0.3, mob.getBbWidth() * 0.4, 0.08);
        }
    }

    private static void restore(Mob mob, Control control) {
        if (!(mob.level() instanceof ServerLevel level)) return;
        LivingEntity original = null;
        if (control.originalTarget != null && level.getEntity(control.originalTarget) instanceof LivingEntity living && living.isAlive()) {
            original = living;
        }
        mob.setTarget(original);
        if (original == null) mob.getNavigation().stop();
    }
}
