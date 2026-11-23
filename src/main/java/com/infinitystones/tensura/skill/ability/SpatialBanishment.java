package com.infinitystones.tensura.skill.ability;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Space Stone "Spatial Banishment" — faithful port of the original Java ability: sends the aimed living target to a
 * checked landing on the central End island (primary) or on top of the Nether bedrock roof (alternate). The wheel's
 * middle click adds a chosen dimension/coordinate (see DestinationService).
 * Works on mobs and on players (players need server PvP, like the original). Server-side only, multiplayer-safe.
 */
public final class SpatialBanishment {
    private static final float MAX_DISTANCE = 48F;

    /** @return true when the target was moved (the cooldown should start). */
    public static boolean cast(ServerPlayer caster, boolean netherRoof) {
        LivingEntity target = aimedTarget(caster);
        if (target == null || target.isPassenger() || target.isVehicle()) return fail(caster);
        ServerLevel destination = caster.server.getLevel(netherRoof ? Level.NETHER : Level.END);
        if (destination == null) return fail(caster);
        Vec3 landing = Relocation.central(destination, target, netherRoof);
        if (landing == null) return fail(caster);
        var fromDim = target.level().dimension();
        Vec3 from = target.position();
        Entity moved = Relocation.move(target, destination, landing, netherRoof ? 0.68F : 1.18F);
        if (moved == null) return fail(caster);
        BanishLog.record(caster, target, fromDim, from, moved);
        caster.displayClientMessage(Component.translatable(netherRoof
                ? "ability.infinity.space_stone.spatial_banishment.nether"
                : "ability.infinity.space_stone.spatial_banishment.end"), true);
        return true;
    }

    /** The living entity under the caster's aim, or null. Riders/vehicles are excluded like in the original. */
    public static @Nullable LivingEntity aimedTarget(ServerPlayer caster) {
        ServerLevel level = caster.serverLevel();
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle().normalize();
        var searchBox = caster.getBoundingBox().expandTowards(look.scale(MAX_DISTANCE)).inflate(4D);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, searchBox, t -> eligible(caster, t, level))) {
            Vec3 center = candidate.getBoundingBox().getCenter();
            Vec3 delta = center.subtract(eye);
            double projection = delta.dot(look);
            if (projection <= 0D || projection > MAX_DISTANCE) continue;
            double lateralSq = Math.max(0D, delta.lengthSqr() - projection * projection);
            double tolerance = Math.max(1.1D, candidate.getBbWidth() * 0.7D) + projection * 0.025D;
            if (lateralSq > tolerance * tolerance) continue;
            HitResult obstruction = level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (obstruction.getType() != HitResult.Type.MISS
                    && obstruction.getLocation().distanceToSqr(eye) + 0.25D < delta.lengthSqr()) continue;
            double score = lateralSq * 8D + delta.lengthSqr() * 0.001D;
            if (score < bestScore) { bestScore = score; best = candidate; }
        }
        return best;
    }

    private static boolean eligible(ServerPlayer caster, LivingEntity target, ServerLevel level) {
        if (target == caster || !target.isAlive() || target instanceof EnderDragon || com.infinitystones.tensura.skill.ability.extra.space.SpiritualGathering.isTrapped(target)) return false;
        if (target instanceof ServerPlayer player) return !player.isCreative() && !player.isSpectator(); // no PvP check (project decision)
        return true;
    }

    static boolean fail(ServerPlayer caster) {
        caster.displayClientMessage(Component.translatable("ability.infinity.space_stone.spatial_banishment.failed"), true);
        return false;
    }

    /** Used by destination moves: true if moved. */
    static boolean relocate(Entity target, ServerLevel destination, Vec3 landing) {
        return Relocation.move(target, destination, landing, 1.0F) != null;
    }

    private SpatialBanishment() {}
}
