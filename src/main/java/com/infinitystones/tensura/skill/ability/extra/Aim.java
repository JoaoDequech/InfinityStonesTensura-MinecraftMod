package com.infinitystones.tensura.skill.ability.extra;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Server-side aiming read at activation (the wheel-ux "aim mode" confirms with a left click; the look direction at
 * that moment is the aim). Forgiving cone so moving targets are easy to pick.
 */
public final class Aim {
    public static @Nullable LivingEntity living(ServerPlayer p, double range, Predicate<LivingEntity> filter) {
        return entity(p, range, LivingEntity.class, e -> e.isAlive() && Rules.isLiving(e) && filter.test(e));
    }

    public static <T extends Entity> @Nullable T entity(ServerPlayer p, double range, Class<T> type, Predicate<T> filter) {
        ServerLevel level = p.serverLevel();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle().normalize();
        var box = p.getBoundingBox().expandTowards(look.scale(range)).inflate(3D);
        T best = null;
        double bestScore = Double.MAX_VALUE;
        for (T c : level.getEntitiesOfClass(type, box, t -> t != p && !t.isSpectator() && filter.test(t))) {
            Vec3 center = c.getBoundingBox().getCenter();
            Vec3 d = center.subtract(eye);
            double proj = d.dot(look);
            if (proj <= 0 || proj > range) continue;
            double lat = Math.max(0, d.lengthSqr() - proj * proj);
            double tol = Math.max(1.0, c.getBbWidth() * 0.7) + proj * 0.025;
            if (lat > tol * tol) continue;
            HitResult hit = level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (hit.getType() != HitResult.Type.MISS && hit.getLocation().distanceToSqr(eye) + 0.25 < d.lengthSqr()) continue;
            double score = lat * 8 + d.lengthSqr() * 0.001;
            if (score < bestScore) { bestScore = score; best = c; }
        }
        return best;
    }

    public static BlockHitResult block(ServerPlayer p, double range) {
        Vec3 eye = p.getEyePosition();
        return p.serverLevel().clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(range)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
    }

    /** Where the crosshair lands (block face, or the end of the ray in open air). */
    public static Vec3 point(ServerPlayer p, double range) {
        BlockHitResult b = block(p, range);
        return b.getType() == HitResult.Type.MISS ? p.getEyePosition().add(p.getLookAngle().scale(range)) : b.getLocation();
    }

    /** Aimed point dropped to the ground below it (for floor effects), max 12 blocks down. */
    public static Vec3 ground(ServerPlayer p, double range) {
        return Ground.below(p.serverLevel(), point(p, range).add(0, 0.1, 0), 12);
    }

    private Aim() {}
}
