package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Port of palladium:spatial_reflection — for the session (original palladium:activation, 100 ticks) arrows and
 * thrown potions fired by hostile mobs that are flying toward the caster get bounced back at their shooter
 * (ownership moves to the caster, so the reflected hit counts as the caster's). Never reflects friendly fire, never
 * reflects the same projectile twice, at most max_reflections per cast.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class SpatialReflection implements Ported {

    private static final String REFLECTED_TAG = "infinity_spatial_reflected";
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public SpatialReflection() {}

    @Override
    public void start(ServerPlayer caster, StoneTree.Node n) {
        ServerLevel level = caster.serverLevel();
        Session s = new Session(level, level.getGameTime() + 120L, n);
        SESSIONS.put(caster.getUUID(), s);
        PortFx.sound(level, caster.getEyePosition(), n, "infinity:space_stone_portal", 0.65F, 1.45F);
        PortFx.sound(level, caster.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 0.92F);
        renderLens(level, caster.getBoundingBox().getCenter(), 1.35D, 24, s);
    }

    @Override
    public void tick(ServerPlayer caster, StoneTree.Node n, int ticks) {
        ServerLevel level = caster.serverLevel();
        Session session = SESSIONS.get(caster.getUUID());
        if (session == null || session.level != level || !caster.isAlive()) return;
        if (level.getGameTime() >= session.expiresAt) {
            SESSIONS.remove(caster.getUUID());
            return;
        }
        if (level.getGameTime() % 4L == 0L) renderLens(level, caster.getBoundingBox().getCenter(), 1.25D, 16, session);
        if (session.reflections >= session.maxReflections) return;

        Vec3 center = caster.getBoundingBox().getCenter();
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class,
                caster.getBoundingBox().inflate(session.radius), SpatialReflection::supported)) {
            if (session.reflections >= session.maxReflections) break;
            Entity origin = projectile.getOwner();
            if (!(origin instanceof LivingEntity livingOrigin) || !(origin instanceof Enemy)
                    || !livingOrigin.isAlive() || origin.level() != level || origin == caster
                    || !PortFx.canAffect(caster, livingOrigin)) continue;
            Vec3 incoming = projectile.getDeltaMovement();
            Vec3 towardCaster = center.subtract(projectile.position());
            if (incoming.lengthSqr() < 0.01D || incoming.dot(towardCaster) <= 0D) continue;
            Vec3 towardOrigin = livingOrigin.getBoundingBox().getCenter().subtract(projectile.position());
            if (towardOrigin.lengthSqr() < 0.01D) continue;

            // Mark before ownership changes so a second caster cannot bounce the same entity indefinitely.
            projectile.addTag(REFLECTED_TAG);
            projectile.setOwner(caster);
            double speed = Math.min(session.maxSpeed, Math.max(1.2D, incoming.length()));
            projectile.setDeltaMovement(towardOrigin.normalize().scale(speed));
            projectile.hasImpulse = true;
            projectile.hurtMarked = true;
            session.reflections++;
            renderLens(level, projectile.position(), 0.85D, 28, session);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, projectile.getX(), projectile.getY(), projectile.getZ(),
                    12, 0.4D, 0.4D, 0.4D, 0.08D);
            PortFx.sound(level, projectile.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5F, 1.5F);
        }
    }

    @Override
    public void stop(ServerPlayer caster, StoneTree.Node n) {
        Session session = SESSIONS.remove(caster.getUUID());
        if (session != null) {
            caster.serverLevel().sendParticles(ParticleTypes.REVERSE_PORTAL, caster.getX(), caster.getEyeY(), caster.getZ(),
                    16, 0.65D, 0.8D, 0.65D, 0.03D);
        }
    }

    private static boolean supported(Projectile projectile) {
        return projectile.isAlive() && !projectile.getTags().contains(REFLECTED_TAG)
                && (projectile instanceof AbstractArrow || projectile instanceof ThrownPotion);
    }

    private static void renderLens(ServerLevel level, Vec3 center, double radius, int samples, Session s) {
        for (int i = 0; i < samples; i++) {
            double angle = Math.PI * 2D * i / samples;
            level.sendParticles(i % 3 == 0 ? s.light : s.main,
                    center.x + Math.cos(angle) * radius, center.y + Math.sin(angle) * radius, center.z, 1, 0D, 0D, 0D, 0D);
        }
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, center.x, center.y, center.z,
                Math.max(3, samples / 3), radius * 0.4D, radius * 0.4D, radius * 0.4D, 0.025D);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { SESSIONS.remove(e.getEntity().getUUID()); }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) { SESSIONS.clear(); }

    private static final class Session {
        final ServerLevel level;
        final long expiresAt;
        final float radius, maxSpeed;
        final int maxReflections;
        final DustParticleOptions main, light;
        int reflections;

        Session(ServerLevel level, long expiresAt, StoneTree.Node n) {
            this.level = level;
            this.expiresAt = expiresAt;
            this.radius = Math.max(2F, Math.min(24F, PortFx.f(n, "radius", 8F)));
            this.maxSpeed = Math.max(0.5F, Math.min(4F, PortFx.f(n, "max_speed", 2.2F)));
            this.maxReflections = Math.max(1, Math.min(128, PortFx.i(n, "max_reflections", 32)));
            int c = n.stone().color();
            this.main = PortFx.dust(c, 0.9F);
            this.light = PortFx.dust(SpatialExchange.lighten(c, 0.5F), 0.65F);
        }
    }
}
