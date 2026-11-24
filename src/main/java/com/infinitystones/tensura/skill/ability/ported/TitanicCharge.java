package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.skill.StoneTree;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Power Stone "Titanic Charge" — port of TitanicChargeAbility: the caster dashes horizontally up to {@code distance}
 * blocks in step_size increments, stopping at the first collision (never phases through blocks), and every hostile
 * crossed is struck exactly once for {@code damage} plus impact_knockback.
 */
public final class TitanicCharge implements Ported {

    @Override
    public Boolean press(ServerPlayer p, StoneTree.Node n, boolean alt) {
        ServerLevel level = p.serverLevel();
        float distance = PortFx.f(n, "distance", 16F);
        float damage = PortFx.f(n, "damage", 160F);
        float stepSize = Math.max(0.1F, PortFx.f(n, "step_size", 0.4F));
        float knockback = PortFx.f(n, "impact_knockback", 1.4F);
        int color = n.stone().color();

        Vec3 direction = p.getLookAngle().multiply(1D, 0D, 1D);
        if (direction.lengthSqr() < 0.001D) return false;
        direction = direction.normalize();
        if (p.isPassenger()) p.stopRiding();

        Set<UUID> struck = new HashSet<>();
        double travelled = 0D;
        boolean blocked = false;
        Vec3 lastPosition = p.position();
        PortFx.sound(level, new Vec3(p.getX(), p.getEyeY(), p.getZ()), n, "infinity:power_stone_beam1", 0.7F, 0.72F);
        PortFx.sound(level, p.position(), SoundEvents.RAVAGER_ROAR, 0.5F, 0.62F);

        while (travelled + 0.0001D < distance) {
            double step = Math.min(stepSize, distance - travelled);
            Vec3 delta = direction.scale(step);
            AABB nextBox = p.getBoundingBox().move(delta);
            if (!level.noCollision(p, nextBox)) {
                blocked = true;
                break;
            }
            Vec3 next = p.position().add(delta);
            p.connection.teleport(next.x, next.y, next.z, p.getYRot(), p.getXRot());
            travelled += step;
            strikeAlongStep(level, p, nextBox.inflate(0.55D, 0.3D, 0.55D), direction, struck, damage, knockback, color);
            renderTrail(level, p.position(), direction, travelled, color);
            lastPosition = p.position();
        }

        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
        p.resetFallDistance();
        renderImpact(level, lastPosition, direction, blocked, color);
        if (blocked) PortFx.sound(level, p.position(), SoundEvents.GENERIC_EXPLODE, 0.7F, 0.72F);
        else PortFx.sound(level, p.position(), SoundEvents.RAVAGER_STEP, 0.6F, 0.82F);
        return true;
    }

    private static void strikeAlongStep(ServerLevel level, ServerPlayer caster, AABB area, Vec3 direction, Set<UUID> struck,
                                        float damage, float knockback, int color) {
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                l -> l.isAlive() && l instanceof Enemy && !struck.contains(l.getUUID()) && PortFx.canAffect(caster, l))) {
            struck.add(target.getUUID());
            target.hurt(PortFx.attack(caster, target), Rules.amount(target, damage, 0.30F, false));
            target.push(direction.x * knockback, 0.38D, direction.z * knockback);
            target.hurtMarked = true;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getEyeY(), target.getZ(),
                    24, target.getBbWidth() * 0.45D, target.getBbHeight() * 0.3D, target.getBbWidth() * 0.45D, 0.18D);
            level.sendParticles(PortFx.dust(light(color), 0.8F), target.getX(), target.getEyeY(), target.getZ(),
                    18, target.getBbWidth() * 0.35D, target.getBbHeight() * 0.3D, target.getBbWidth() * 0.35D, 0.08D);
            PortFx.sound(level, target.position(), SoundEvents.GENERIC_EXPLODE, 0.5F, 1.25F);
        }
    }

    /** Lighter tint of the stone colour (the original's magenta accent). */
    private static int light(int color) {
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        r = r + (255 - r) * 45 / 100; g = g + (255 - g) * 25 / 100; b = b + (255 - b) * 45 / 100;
        return (r << 16) | (g << 8) | b;
    }

    private static void renderTrail(ServerLevel level, Vec3 position, Vec3 direction, double travelled, int color) {
        if (((int) Math.round(travelled * 10D)) % 4 != 0) return;
        DustParticleOptions main = PortFx.dust(color, 0.9F);
        DustParticleOptions accent = PortFx.dust(light(color), 0.8F);
        Vec3 side = new Vec3(-direction.z, 0D, direction.x);
        for (int i = -1; i <= 1; i++) {
            Vec3 point = position.add(side.scale(i * 0.55D)).add(0D, 0.25D + Math.abs(i) * 0.35D, 0D);
            level.sendParticles(i == 0 ? accent : main, point.x, point.y, point.z, 2, 0.05D, 0.08D, 0.05D, 0.01D);
        }
    }

    private static void renderImpact(ServerLevel level, Vec3 position, Vec3 direction, boolean blocked, int color) {
        DustParticleOptions main = PortFx.dust(color, 0.9F);
        DustParticleOptions accent = PortFx.dust(light(color), 0.8F);
        Vec3 side = new Vec3(-direction.z, 0D, direction.x);
        int rings = blocked ? 4 : 3;
        for (int ring = 1; ring <= rings; ring++) {
            double radius = ring * 0.62D;
            for (int i = 0; i < 18; i++) {
                double angle = Math.PI * 2D * i / 18D;
                Vec3 radial = side.scale(Math.cos(angle) * radius).add(0D, Math.sin(angle) * radius, 0D);
                Vec3 point = position.add(direction.scale(2.1D)).add(radial);
                level.sendParticles((ring + i) % 3 == 0 ? accent : main, point.x, point.y + 0.9D, point.z, 1, 0, 0, 0, 0);
            }
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, position.x + direction.x * 1.35D, position.y + 0.9D,
                position.z + direction.z * 1.35D, blocked ? 24 : 16, 0.65D, 0.65D, 0.65D, 0.045D);
    }
}
