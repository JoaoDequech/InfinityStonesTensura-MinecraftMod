package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Port of palladium:orbital_judgement — suspends a hostile cluster in a safe spatial orbit, then crushes it with
 * explicit damage. Timed session (original palladium:activation, 68 ticks): start = capture, tick = orbit,
 * stop = release without damage if the orbit had not finished yet.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class OrbitalJudgement implements Ported {

    private static final Map<UUID, Orbit> ORBITS = new HashMap<>();
    private static final Map<UUID, Captive> CAPTIVES = new HashMap<>();

    public OrbitalJudgement() {}

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void start(ServerPlayer caster, StoneTree.Node n) {
        ServerLevel level = caster.serverLevel();
        Params prm = new Params(n);
        LivingEntity first = aimedTarget(caster, prm.maxDistance);
        if (first == null) return;

        Orbit previous = ORBITS.remove(caster.getUUID());
        if (previous != null) release(previous, false, caster);

        List<LivingEntity> selected = new ArrayList<>();
        selected.add(first);
        level.getEntitiesOfClass(LivingEntity.class, first.getBoundingBox().inflate(prm.clusterRadius),
                        t -> eligible(caster, t) && t != first && !CAPTIVES.containsKey(t.getUUID()))
                .stream().sorted(Comparator.comparingDouble(first::distanceToSqr)).forEach(selected::add);
        if (selected.size() > prm.maxTargets) selected = new ArrayList<>(selected.subList(0, prm.maxTargets));

        Vec3 center = first.position().add(0D, prm.liftHeight, 0D);
        List<Orbiter> orbiters = new ArrayList<>();
        long expiresAt = level.getGameTime() + prm.orbitTicks + 20L;
        for (int i = 0; i < selected.size(); i++) {
            LivingEntity target = selected.get(i);
            boolean movable = !isBossLike(target) && orbitFits(level, target, center, i, selected.size(), prm.orbitRadius);
            orbiters.add(new Orbiter(target.getUUID(), movable, target.isNoGravity(), target.position()));
            if (movable) {
                CAPTIVES.put(target.getUUID(), new Captive(caster.getUUID(), target.isNoGravity(), expiresAt));
                target.setNoGravity(true);
                target.setDeltaMovement(Vec3.ZERO);
                target.fallDistance = 0F;
                if (target instanceof Mob mob) {
                    mob.setTarget(null);
                    mob.getNavigation().stop();
                }
            }
            renderCapture(level, target, i, prm);
        }

        ORBITS.put(caster.getUUID(), new Orbit(level, center, orbiters, prm, n));
        PortFx.sound(level, center, n, "infinity:space_stone_portal", 0.7F, 0.72F);
        PortFx.sound(level, center, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.6F, 0.58F);
    }

    @Override
    public void tick(ServerPlayer caster, StoneTree.Node n, int ticks) {
        ServerLevel level = caster.serverLevel();
        Orbit orbit = ORBITS.get(caster.getUUID());
        if (orbit == null || orbit.level != level || orbit.released) return;
        Params prm = orbit.prm;
        orbit.elapsed++;
        renderOrbit(level, orbit);

        int count = orbit.targets.size();
        for (int i = 0; i < count; i++) {
            Orbiter state = orbit.targets.get(i);
            Entity entity = level.getEntity(state.entityId);
            if (!(entity instanceof LivingEntity target) || !target.isAlive()) {
                CAPTIVES.remove(state.entityId);
                continue;
            }
            if (state.movable) {
                Vec3 point = orbitPoint(orbit.center, i, count, orbit.elapsed, prm.orbitRadius);
                int descentTicks = 10;
                if (orbit.elapsed > prm.orbitTicks - descentTicks) {
                    double progress = (orbit.elapsed - (prm.orbitTicks - descentTicks)) / (double) descentTicks;
                    point = point.lerp(state.origin, Math.min(1D, progress));
                }
                target.teleportTo(point.x, point.y, point.z);
                target.setDeltaMovement(Vec3.ZERO);
                target.fallDistance = 0F;
            }
            renderTether(level, orbit.center, target.getBoundingBox().getCenter(), orbit.elapsed, prm);
        }

        if (orbit.elapsed >= prm.orbitTicks) release(orbit, true, caster);
    }

    @Override
    public void stop(ServerPlayer caster, StoneTree.Node n) {
        Orbit orbit = ORBITS.remove(caster.getUUID());
        if (orbit != null && !orbit.released) release(orbit, false, caster);
    }

    // ------------------------------------------------------------------ failsafes (original PowerEventHandler hook)

    /** Restores captured mobs if the caster is gone or the orbit was interrupted (original tickOrbitingMob). */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post e) {
        if (CAPTIVES.isEmpty() || !(e.getEntity() instanceof LivingEntity mob) || !(mob.level() instanceof ServerLevel level)) return;
        Captive captive = CAPTIVES.get(mob.getUUID());
        if (captive == null) return;
        Orbit orbit = ORBITS.get(captive.owner);
        Entity owner = level.getEntity(captive.owner);
        if (!mob.isAlive() || owner == null || orbit == null || orbit.released || level.getGameTime() >= captive.expiresAt) {
            mob.setNoGravity(captive.originalNoGravity);
            mob.setDeltaMovement(Vec3.ZERO);
            mob.fallDistance = 0F;
            CAPTIVES.remove(mob.getUUID());
        }
    }

    /** A captive unloaded/removed mid-orbit must not be saved with NoGravity. */
    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent e) {
        if (CAPTIVES.isEmpty() || e.getLevel().isClientSide()) return;
        Captive captive = CAPTIVES.remove(e.getEntity().getUUID());
        if (captive != null) e.getEntity().setNoGravity(captive.originalNoGravity);
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        for (Orbit orbit : new ArrayList<>(ORBITS.values())) release(orbit, false, null);
        ORBITS.clear();
        CAPTIVES.clear();
    }

    // ------------------------------------------------------------------ logic

    private static @Nullable LivingEntity aimedTarget(ServerPlayer caster, float maxDistance) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle().normalize();
        AABB searchBox = caster.getBoundingBox().expandTowards(look.scale(maxDistance)).inflate(4D);
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity candidate : caster.level().getEntitiesOfClass(LivingEntity.class, searchBox,
                t -> eligible(caster, t) && !CAPTIVES.containsKey(t.getUUID()))) {
            Vec3 center = candidate.getBoundingBox().getCenter();
            Vec3 delta = center.subtract(eye);
            double projection = delta.dot(look);
            if (projection <= 0D || projection > maxDistance) continue;
            double lateralSq = Math.max(0D, delta.lengthSqr() - projection * projection);
            double tolerance = Math.max(1.25D, candidate.getBbWidth() * 0.75D) + projection * 0.035D;
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

    private static boolean eligible(ServerPlayer caster, LivingEntity target) {
        return target != caster && target.isAlive() && target instanceof Enemy
                && !SpiritLegion.isSpirit(target) && PortFx.canAffect(caster, target);
    }

    private static boolean isBossLike(LivingEntity target) {
        return target instanceof EnderDragon || target instanceof WitherBoss || target instanceof Warden
                || target.getMaxHealth() >= 300F;
    }

    private static boolean orbitFits(ServerLevel level, LivingEntity target, Vec3 center, int index, int count, float orbitRadius) {
        for (int sample = 0; sample < 16; sample++) {
            Vec3 point = orbitPoint(center, index, count, sample * 3, orbitRadius);
            BlockPos feet = BlockPos.containing(point);
            if (!level.hasChunkAt(feet) || level.getFluidState(feet).is(FluidTags.LAVA)) return false;
            Vec3 offset = point.subtract(target.position());
            AABB box = target.getBoundingBox().move(offset).deflate(0.001D);
            if (!level.getWorldBorder().isWithinBounds(box) || level.getBlockCollisions(target, box).iterator().hasNext()) return false;
        }
        return true;
    }

    private static Vec3 orbitPoint(Vec3 center, int index, int count, int elapsed, float orbitRadius) {
        double angle = Math.PI * 2D * index / Math.max(1, count) + elapsed * 0.17D;
        double radius = orbitRadius + (index % 2) * 0.38D;
        return center.add(Math.cos(angle) * radius, Math.sin(angle * 2D + index) * 0.72D, Math.sin(angle) * radius);
    }

    /** Releases every orbiter; with damageTargets the crushing blow lands (caster must be non-null then). */
    private static void release(Orbit orbit, boolean damageTargets, @Nullable ServerPlayer caster) {
        if (orbit.released) return;
        orbit.released = true;
        ServerLevel level = orbit.level;
        Params prm = orbit.prm;
        for (Orbiter state : orbit.targets) {
            Entity entity = level.getEntity(state.entityId);
            Captive captive = CAPTIVES.remove(state.entityId);
            if (!(entity instanceof LivingEntity target)) continue;
            if (state.movable) {
                target.setNoGravity(captive == null ? state.originalNoGravity : captive.originalNoGravity);
                if (isSafePosition(level, target, state.origin)) target.teleportTo(state.origin.x, state.origin.y, state.origin.z);
                target.setDeltaMovement(0D, damageTargets ? -2.15D : 0D, 0D);
                target.fallDistance = 0F;
                target.hurtMarked = true;
            }
            if (damageTargets && caster != null && target.isAlive() && PortFx.canAffect(caster, target)) {
                target.hurt(PortFx.attack(caster, target), prm.damage);
                renderImpact(level, target, prm);
            }
        }
        if (damageTargets) {
            renderCompression(level, orbit.center, prm);
            PortFx.sound(level, orbit.center, orbit.node, "infinity:space_stone_portal", 0.7F, 0.48F);
            PortFx.sound(level, orbit.center, SoundEvents.WARDEN_SONIC_BOOM, 0.6F, 0.66F);
            PortFx.sound(level, orbit.center, SoundEvents.GENERIC_EXPLODE, 0.5F, 0.82F);
        }
    }

    private static boolean isSafePosition(ServerLevel level, LivingEntity target, Vec3 position) {
        BlockPos feet = BlockPos.containing(position);
        if (!level.hasChunkAt(feet) || level.getFluidState(feet).is(FluidTags.LAVA)) return false;
        Vec3 offset = position.subtract(target.position());
        AABB box = target.getBoundingBox().move(offset).deflate(0.001D);
        return level.getWorldBorder().isWithinBounds(box) && !level.getBlockCollisions(target, box).iterator().hasNext();
    }

    // ------------------------------------------------------------------ visuals (stone colour)

    private static void renderCapture(ServerLevel level, LivingEntity target, int index, Params prm) {
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getEyeY(), target.getZ(),
                18, target.getBbWidth() * 0.45D, target.getBbHeight() * 0.34D, target.getBbWidth() * 0.45D, 0.055D);
        level.sendParticles(index % 2 == 0 ? prm.light : prm.deep, target.getX(), target.getEyeY(), target.getZ(), 12,
                target.getBbWidth() * 0.35D, target.getBbHeight() * 0.28D, target.getBbWidth() * 0.35D, 0.025D);
    }

    private static void renderOrbit(ServerLevel level, Orbit orbit) {
        Params prm = orbit.prm;
        int points = 48;
        double pulse = 1D + Math.sin(orbit.elapsed * 0.28D) * 0.08D;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2D * i / points + orbit.elapsed * 0.035D;
            double radius = prm.orbitRadius * pulse;
            Vec3 horizontal = orbit.center.add(Math.cos(angle) * radius, 0D, Math.sin(angle) * radius);
            Vec3 tilted = orbit.center.add(Math.cos(angle) * radius, Math.sin(angle) * radius * 0.42D, Math.sin(angle) * radius * 0.55D);
            level.sendParticles(i % 3 == 0 ? prm.light : prm.deep, horizontal.x, horizontal.y, horizontal.z, 1, 0, 0, 0, 0);
            if (i % 2 == 0) level.sendParticles(prm.core, tilted.x, tilted.y, tilted.z, 1, 0, 0, 0, 0);
        }
        if (orbit.elapsed % 6 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, orbit.center.x, orbit.center.y, orbit.center.z,
                    9, prm.orbitRadius * 0.58D, 0.75D, prm.orbitRadius * 0.58D, 0.075D);
        }
        if (orbit.elapsed % 10 == 0) {
            PortFx.sound(level, orbit.center, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5F, 0.72F + orbit.elapsed * 0.012F);
        }
    }

    private static void renderTether(ServerLevel level, Vec3 from, Vec3 to, int elapsed, Params prm) {
        Vec3 delta = to.subtract(from);
        Vec3 side = delta.cross(new Vec3(0D, 1D, 0D));
        if (side.lengthSqr() < 0.001D) side = new Vec3(1D, 0D, 0D);
        side = side.normalize();
        for (int i = 1; i < 25; i++) {
            Vec3 point = from.add(delta.scale(i / 25D));
            double wave = Math.sin(i * 0.68D + elapsed * 0.31D) * 0.12D;
            Vec3 rimA = point.add(side.scale(0.1D + wave));
            Vec3 rimB = point.add(side.scale(-0.1D + wave));
            level.sendParticles(prm.core, point.x, point.y, point.z, 1, 0, 0, 0, 0);
            level.sendParticles(prm.rim, rimA.x, rimA.y, rimA.z, 1, 0, 0, 0, 0);
            level.sendParticles(prm.light, rimB.x, rimB.y, rimB.z, 1, 0, 0, 0, 0);
            if ((i + elapsed) % 5 == 0) level.sendParticles(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
    }

    private static void renderImpact(ServerLevel level, LivingEntity target, Params prm) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getEyeY(), target.getZ(),
                34, target.getBbWidth() * 0.5D, target.getBbHeight() * 0.4D, target.getBbWidth() * 0.5D, 0.18D);
        level.sendParticles(prm.light, target.getX(), target.getEyeY(), target.getZ(),
                28, target.getBbWidth() * 0.42D, target.getBbHeight() * 0.34D, target.getBbWidth() * 0.42D, 0.085D);
    }

    private static void renderCompression(ServerLevel level, Vec3 center, Params prm) {
        for (int ring = 0; ring < 4; ring++) {
            double radius = prm.orbitRadius * (1D - ring * 0.21D);
            for (int i = 0; i < 36; i++) {
                double angle = Math.PI * 2D * i / 36D + ring * 0.35D;
                Vec3 point = center.add(Math.cos(angle) * radius, (ring - 1.5D) * 0.32D, Math.sin(angle) * radius);
                level.sendParticles(ring % 2 == 0 ? prm.light : prm.deep, point.x, point.y, point.z, 1, 0, 0, 0, 0);
            }
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z, 55, 1.2D, 1D, 1.2D, 0.12D);
        level.sendParticles(prm.core, center.x, center.y, center.z, 36, 0.75D, 0.75D, 0.75D, 0.14D);
    }

    // ------------------------------------------------------------------ state

    /** Original codec parameters (raw node values, codec defaults as fallback) + stone-coloured dusts. */
    private static final class Params {
        final float damage, maxDistance, clusterRadius, liftHeight, orbitRadius;
        final int maxTargets, orbitTicks;
        final DustParticleOptions deep, light, core, rim;

        Params(StoneTree.Node n) {
            damage = Math.max(0F, PortFx.f(n, "damage", 180F));
            maxDistance = clamp(PortFx.f(n, "max_distance", 48F), 1F, 128F);
            clusterRadius = clamp(PortFx.f(n, "cluster_radius", 10F), 1F, 32F);
            maxTargets = Math.max(1, Math.min(12, PortFx.i(n, "max_targets", 5)));
            orbitTicks = Math.max(10, Math.min(200, PortFx.i(n, "orbit_ticks", 60)));
            liftHeight = clamp(PortFx.f(n, "lift_height", 5.5F), 2F, 16F);
            orbitRadius = clamp(PortFx.f(n, "orbit_radius", 3.25F), 1F, 8F);
            int c = n.stone().color();
            deep = PortFx.dust(darken(c, 0.35F), 0.82F);
            light = PortFx.dust(SpatialExchange.lighten(c, 0.45F), 0.55F);
            core = PortFx.dust(SpatialExchange.lighten(c, 0.85F), 0.9F);
            rim = PortFx.dust(c, 0.78F);
        }
        private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
        private static int darken(int rgb, float t) {
            int r = (int) (((rgb >> 16) & 255) * (1 - t)), g = (int) (((rgb >> 8) & 255) * (1 - t)), b = (int) ((rgb & 255) * (1 - t));
            return (r << 16) | (g << 8) | b;
        }
    }

    private static final class Orbit {
        final ServerLevel level;
        final Vec3 center;
        final List<Orbiter> targets;
        final Params prm;
        final StoneTree.Node node;
        int elapsed;
        boolean released;

        Orbit(ServerLevel level, Vec3 center, List<Orbiter> targets, Params prm, StoneTree.Node node) {
            this.level = level; this.center = center; this.targets = targets; this.prm = prm; this.node = node;
        }
    }

    private record Orbiter(UUID entityId, boolean movable, boolean originalNoGravity, Vec3 origin) {}

    private record Captive(UUID owner, boolean originalNoGravity, long expiresAt) {}
}
