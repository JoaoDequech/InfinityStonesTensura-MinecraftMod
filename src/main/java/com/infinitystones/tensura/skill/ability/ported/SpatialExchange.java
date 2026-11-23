package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Port of palladium:spatial_exchange — atomically swaps the caster with an aimed hostile after validating both
 * destinations (loaded chunk, no lava, no block collision, inside the world border). The target is disoriented.
 */
public final class SpatialExchange implements Ported {

    public SpatialExchange() {}

    @Override
    public Boolean press(ServerPlayer caster, StoneTree.Node n, boolean alt) {
        ServerLevel level = caster.serverLevel();
        float maxDistance = PortFx.f(n, "max_distance", 32F);
        int disorientation = PortFx.i(n, "disorientation_ticks", 60);

        LivingEntity target = aimedTarget(caster, maxDistance);
        if (target == null || caster.isPassenger() || caster.isVehicle()
                || target.isPassenger() || target.isVehicle()
                || com.infinitystones.tensura.skill.ability.extra.space.SpiritualGathering.isTrapped(target)) return false;

        Vec3 casterOrigin = caster.position();
        Vec3 targetOrigin = target.position();
        if (!isSafeDestination(level, caster, targetOrigin) || !isSafeDestination(level, target, casterOrigin)) return false;

        int color = n.stone().color();
        DustParticleOptions outer = PortFx.dust(color, 1.15F);
        DustParticleOptions inner = PortFx.dust(lighten(color, 0.55F), 0.72F);

        renderPortal(level, casterOrigin, caster.getBbHeight(), targetOrigin.subtract(casterOrigin), outer, inner);
        renderPortal(level, targetOrigin, target.getBbHeight(), casterOrigin.subtract(targetOrigin), outer, inner);
        playExchangeSound(level, n, casterOrigin, 0.9F);
        playExchangeSound(level, n, targetOrigin, 1.14F);

        target.teleportTo(casterOrigin.x, casterOrigin.y, casterOrigin.z);
        caster.connection.teleport(targetOrigin.x, targetOrigin.y, targetOrigin.z, caster.getYRot(), caster.getXRot());
        target.setDeltaMovement(Vec3.ZERO);
        caster.setDeltaMovement(Vec3.ZERO);
        target.fallDistance = 0F;
        caster.fallDistance = 0F;

        if (disorientation > 0) {
            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, disorientation, 0, false, true, true));
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, disorientation, 3, false, true, true));
        }
        if (target instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }

        level.sendParticles(inner, caster.getX(), caster.getEyeY(), caster.getZ(), 18, 0.28D, 0.55D, 0.28D, 0.04D);
        level.sendParticles(inner, target.getX(), target.getEyeY(), target.getZ(), 18, 0.28D, 0.55D, 0.28D, 0.04D);
        return true;
    }

    /** Original cone search (hostiles only, unobstructed), plus the PvP/ally-safe canAffect check. */
    private static @Nullable LivingEntity aimedTarget(ServerPlayer caster, float maxDistance) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle().normalize();
        AABB searchBox = caster.getBoundingBox().expandTowards(look.scale(maxDistance)).inflate(3D);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity candidate : caster.level().getEntitiesOfClass(LivingEntity.class, searchBox,
                living -> living != caster && living.isAlive() && living instanceof Enemy
                        && !SpiritLegion.isSpirit(living) && PortFx.canAffect(caster, living))) {
            Vec3 center = candidate.getBoundingBox().getCenter();
            Vec3 delta = center.subtract(eye);
            double projection = delta.dot(look);
            if (projection <= 0D || projection > maxDistance) continue;
            double lateralSq = Math.max(0D, delta.lengthSqr() - projection * projection);
            double tolerance = Math.max(1.1D, candidate.getBbWidth() * 0.7D) + projection * 0.025D;
            if (lateralSq > tolerance * tolerance) continue;
            HitResult obstruction = caster.level().clip(new ClipContext(eye, center,
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (obstruction.getType() != HitResult.Type.MISS
                    && obstruction.getLocation().distanceToSqr(eye) + 0.25D < delta.lengthSqr()) continue;
            double score = lateralSq * 8D + delta.lengthSqr() * 0.001D;
            if (score < bestScore) { bestScore = score; best = candidate; }
        }
        return best;
    }

    private static boolean isSafeDestination(ServerLevel level, LivingEntity entity, Vec3 destination) {
        BlockPos feet = BlockPos.containing(destination);
        BlockPos head = BlockPos.containing(destination.add(0D, Math.max(0.2D, entity.getBbHeight() - 0.1D), 0D));
        if (!level.hasChunkAt(feet) || level.getFluidState(feet).is(FluidTags.LAVA)
                || level.getFluidState(head).is(FluidTags.LAVA)) return false;
        Vec3 offset = destination.subtract(entity.position());
        AABB box = entity.getBoundingBox().move(offset).deflate(0.001D);
        return level.getWorldBorder().isWithinBounds(box) && !level.getBlockCollisions(entity, box).iterator().hasNext();
    }

    private static void renderPortal(ServerLevel level, Vec3 base, double height, Vec3 toward,
                                     DustParticleOptions outer, DustParticleOptions inner) {
        Vec3 normal = new Vec3(toward.x, 0D, toward.z);
        if (normal.lengthSqr() < 0.001D) normal = new Vec3(0D, 0D, 1D);
        normal = normal.normalize();
        Vec3 right = new Vec3(normal.z, 0D, -normal.x);
        Vec3 center = base.add(0D, Math.max(0.9D, height * 0.52D), 0D);
        double radius = Math.max(0.8D, Math.min(1.35D, height * 0.48D));
        for (int i = 0; i < 72; i++) {
            double angle = Math.PI * 2D * i / 72D;
            Vec3 point = center.add(right.scale(Math.cos(angle) * radius)).add(0D, Math.sin(angle) * radius, 0D);
            level.sendParticles(i % 3 == 0 ? inner : outer, point.x, point.y, point.z, 1, 0, 0, 0, 0);
            if (i % 2 == 0) {
                Vec3 depth = normal.scale(i % 4 == 0 ? 0.11D : -0.11D);
                level.sendParticles(inner, point.x + depth.x, point.y, point.z + depth.z, 1, 0, 0, 0, 0);
            }
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z,
                40, radius * 0.5D, radius * 0.65D, radius * 0.18D, 0.08D);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z,
                14, radius * 0.45D, radius * 0.55D, radius * 0.15D, 0.09D);
        level.sendParticles(ParticleTypes.SCULK_SOUL, center.x, center.y, center.z,
                10, radius * 0.35D, radius * 0.45D, radius * 0.1D, 0.02D);
    }

    private static void playExchangeSound(ServerLevel level, StoneTree.Node n, Vec3 at, float pitch) {
        PortFx.sound(level, at, n, "infinity:space_stone_portal", 0.7F, pitch);
        PortFx.sound(level, at, SoundEvents.ENDERMAN_TELEPORT, 0.6F, pitch + 0.12F);
    }

    static int lighten(int rgb, float t) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        r = (int) (r + (255 - r) * t); g = (int) (g + (255 - g) * t); b = (int) (b + (255 - b) * t);
        return (r << 16) | (g << 8) | b;
    }
}
