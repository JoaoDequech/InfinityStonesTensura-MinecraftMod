package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Mind Stone "Synaptic Cascade" — port of SynapticCascadeAbility: a one-shot psychic strike on the aimed hostile that
 * chains to the nearest visible hostile within jump_distance, up to max_targets. Each hit deals damage, applies
 * Slowness VII for interrupt_ticks and clears the mob's target/navigation.
 */
public final class SynapticCascade implements Ported {

    @Override
    public Boolean press(ServerPlayer p, StoneTree.Node n, boolean alt) {
        ServerLevel level = p.serverLevel();
        float damage = PortFx.f(n, "damage", 80F);
        float maxDistance = PortFx.f(n, "max_distance", 48F);
        float jumpDistance = PortFx.f(n, "jump_distance", 8F);
        int maxTargets = PortFx.i(n, "max_targets", 6);
        int interruptTicks = PortFx.i(n, "interrupt_ticks", 20);

        LivingEntity first = PortFx.aimed(p, maxDistance, t -> valid(p, t));
        if (first == null) return false;

        DustParticleOptions arc = PortFx.dust(n.stone(), 1.15F);
        List<LivingEntity> struck = new ArrayList<>();
        LivingEntity current = first;
        Vec3 start = p.getEyePosition();
        while (current != null && struck.size() < maxTargets) {
            Vec3 center = current.getBoundingBox().getCenter();
            renderArc(level, start, center, arc);
            strike(p, n, level, current, struck.size(), damage, interruptTicks);
            struck.add(current);
            start = center;
            LivingEntity origin = current;
            current = level.getEntitiesOfClass(LivingEntity.class, origin.getBoundingBox().inflate(jumpDistance),
                            c -> valid(p, c) && !struck.contains(c) && origin.hasLineOfSight(c))
                    .stream().min(Comparator.comparingDouble(origin::distanceToSqr)).orElse(null);
        }
        return true;
    }

    private static boolean valid(ServerPlayer caster, LivingEntity target) {
        return target != caster && target.isAlive() && target instanceof Enemy && PortFx.canAffect(caster, target);
    }

    private static void strike(ServerPlayer caster, StoneTree.Node n, ServerLevel level, LivingEntity target, int index,
                               float damage, int interruptTicks) {
        target.hurt(PortFx.attack(caster, target), damage);
        if (interruptTicks > 0) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, interruptTicks, 6, false, true, true));
            if (target instanceof Mob mob) {
                mob.setTarget(null);
                mob.getNavigation().stop();
            }
        }
        PortFx.sound(level, new Vec3(target.getX(), target.getEyeY(), target.getZ()), n, "infinity:mind_stone_phase",
                0.6F, 1.15F + index * 0.08F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getEyeY(), target.getZ(),
                14, target.getBbWidth() * 0.35, target.getBbHeight() * 0.25, target.getBbWidth() * 0.35, 0.08);
        level.sendParticles(PortFx.dust(n.stone(), 0.9F), target.getX(), target.getEyeY(), target.getZ(),
                8, target.getBbWidth() * 0.3, target.getBbHeight() * 0.25, target.getBbWidth() * 0.3, 0.02);
    }

    private static void renderArc(ServerLevel level, Vec3 from, Vec3 to, DustParticleOptions dust) {
        int steps = Math.max(4, (int) Math.ceil(from.distanceTo(to) * 3D));
        for (int i = 0; i <= steps; i++) {
            Vec3 point = from.lerp(to, i / (double) steps);
            double wave = Math.sin(i * 2.4D) * 0.08D;
            level.sendParticles(dust, point.x + wave, point.y, point.z - wave, 1, 0, 0, 0, 0);
        }
    }
}
