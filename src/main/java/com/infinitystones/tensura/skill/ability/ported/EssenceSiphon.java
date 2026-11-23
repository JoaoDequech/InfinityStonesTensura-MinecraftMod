package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;

/**
 * Port of palladium:essence_siphon — timed session (original palladium:activation, 100 ticks). A pulse on start and
 * every 20 ticks deals 20 damage to up to 5 nearest visible hostiles within the radius; the caster heals 25% of the
 * health actually lost (max 12 per pulse). Soul threads link the victims to the caster every 4 ticks.
 */
public final class EssenceSiphon implements Ported {

    public EssenceSiphon() {}

    @Override
    public void start(ServerPlayer caster, StoneTree.Node n) {
        if (!caster.isAlive()) return;
        ServerLevel level = caster.serverLevel();
        PortFx.sound(level, caster.getEyePosition(), n, "infinity:soul_stone", 0.65F, 0.72F);
        pulse(level, caster, n);
    }

    @Override
    public void tick(ServerPlayer caster, StoneTree.Node n, int ticks) {
        if (!caster.isAlive()) return;
        ServerLevel level = caster.serverLevel();
        int enabled = ticks + 1; // original getEnabledTicks() is already 1 on the first tick
        if (enabled < 100 && enabled % 20 == 0) pulse(level, caster, n);
        if (enabled % 4 == 0) {
            DustParticleOptions[] c = colours(n);
            for (LivingEntity target : targets(level, caster, n)) renderThread(level, target, caster, c);
        }
    }

    private static List<LivingEntity> targets(ServerLevel level, ServerPlayer caster, StoneTree.Node n) {
        float radius = Math.max(2F, Math.min(24F, PortFx.f(n, "radius", 10F)));
        return level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(radius),
                        t -> t != caster && t instanceof Enemy && t.isAlive() && !t.isSpectator()
                                && !caster.isAlliedTo(t) && !t.isAlliedTo(caster) && !SpiritLegion.isSpirit(t)
                                && PortFx.canAffect(caster, t)
                                && caster.distanceToSqr(t) <= radius * radius && caster.hasLineOfSight(t))
                .stream().sorted(Comparator.comparingDouble(caster::distanceToSqr)).limit(5).toList();
    }

    private static void pulse(ServerLevel level, ServerPlayer caster, StoneTree.Node n) {
        DustParticleOptions[] c = colours(n);
        float drained = 0F;
        for (LivingEntity target : targets(level, caster, n)) {
            float before = target.getHealth();
            if (target.hurt(PortFx.attack(caster, target), 20F)) drained += Math.max(0F, before - target.getHealth());
            renderThread(level, target, caster, c);
        }
        if (drained > 0F) {
            caster.heal(Math.min(12F, drained * 0.25F));
            level.sendParticles(ParticleTypes.HEART, caster.getX(), caster.getEyeY(), caster.getZ(), 3, 0.4D, 0.25D, 0.4D, 0D);
            PortFx.sound(level, caster.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5F, 0.65F);
        }
        Vec3 center = caster.position().add(0D, 0.15D, 0D);
        for (int i = 0; i < 32; i++) {
            double angle = i * Math.PI / 16D;
            level.sendParticles(i % 4 == 0 ? c[1] : c[0],
                    center.x + Math.cos(angle) * 1.4D, center.y, center.z + Math.sin(angle) * 1.4D, 1, 0D, 0D, 0D, 0D);
        }
    }

    private static void renderThread(ServerLevel level, LivingEntity target, LivingEntity caster, DustParticleOptions[] c) {
        Vec3 start = target.getBoundingBox().getCenter(), end = caster.getBoundingBox().getCenter();
        Vec3 direction = end.subtract(start);
        Vec3 side = new Vec3(-direction.z, 0D, direction.x);
        side = side.lengthSqr() < 1.0E-6D ? Vec3.ZERO : side.normalize().scale(0.35D);
        double phase = level.getGameTime() * 0.15D;
        for (int i = 0; i <= 20; i++) {
            double t = i / 20D;
            Vec3 point = start.lerp(end, t)
                    .add(side.scale(Math.sin(t * Math.PI) * Math.sin(phase + t * 5D)))
                    .add(0D, Math.sin(t * Math.PI) * 0.6D, 0D);
            level.sendParticles(i % 4 == 0 ? c[1] : c[0], point.x, point.y, point.z, 1, 0D, 0D, 0D, 0D);
        }
        double t = (level.getGameTime() % 20L) / 20D;
        Vec3 mote = start.lerp(end, t).add(0D, Math.sin(t * Math.PI) * 0.6D, 0D);
        level.sendParticles(ParticleTypes.SOUL, mote.x, mote.y, mote.z, 1, 0D, 0D, 0D, 0D);
    }

    /** [0] = stone colour, [1] = light accent. */
    private static DustParticleOptions[] colours(StoneTree.Node n) {
        int c = n.stone().color();
        return new DustParticleOptions[]{PortFx.dust(c, 0.9F), PortFx.dust(SpatialExchange.lighten(c, 0.5F), 0.65F)};
    }
}
