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
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Port of palladium:aether_transmutation (Reality). For the activation window (120 ticks), hostile projectiles
 * (arrows, potions, fireballs, shulker bullets fired by monsters) flying toward the caster inside the radius are
 * turned into harmless fragments (discarded + petals); at most {@code max_transmutations} per cast. Never creates items.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class AetherTransmutation implements Ported {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private static final class Session {
        final ServerLevel level; final long expiresAt; int transmutations;
        Session(ServerLevel level, long expiresAt) { this.level = level; this.expiresAt = expiresAt; }
    }

    public AetherTransmutation() {}

    private static DustParticleOptions ruby(StoneTree.Node n) { return PortFx.dust(n.stone(), 0.8F); }
    private static DustParticleOptions rose() { return PortFx.dust(0xFF9ECC, 0.7F); }

    @Override
    public void start(ServerPlayer p, StoneTree.Node n) {
        ServerLevel level = p.serverLevel();
        float radius = PortFx.f(n, "radius", 8F);
        SESSIONS.put(p.getUUID(), new Session(level, level.getGameTime() + 120L));
        PortFx.sound(level, p.getEyePosition(), n, "infinity:reality_stone", 0.7F, 1.25F);
        PortFx.sound(level, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 0.8F);
        halo(level, n, p.getBoundingBox().getCenter(), radius, 32);
    }

    @Override
    public void tick(ServerPlayer p, StoneTree.Node n, int ticks) {
        ServerLevel level = p.serverLevel();
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.level != level || !p.isAlive()) return;
        if (level.getGameTime() >= s.expiresAt) { SESSIONS.remove(p.getUUID()); return; }
        float radius = PortFx.f(n, "radius", 8F);
        int max = PortFx.i(n, "max_transmutations", 32);
        if (level.getGameTime() % 5L == 0L) halo(level, n, p.getBoundingBox().getCenter(), radius, 20);
        if (s.transmutations >= max) return;

        Vec3 center = p.getBoundingBox().getCenter();
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(radius),
                AetherTransmutation::supported)) {
            if (s.transmutations >= max) break;
            Entity origin = projectile.getOwner();
            if (!(origin instanceof LivingEntity living) || !(origin instanceof Enemy) || !living.isAlive()
                    || origin.level() != level || origin == p) continue;
            Vec3 incoming = projectile.getDeltaMovement();
            Vec3 toward = center.subtract(projectile.position());
            if (incoming.lengthSqr() < 0.01D || incoming.dot(toward) <= 0D) continue;

            Vec3 at = projectile.position();
            projectile.discard();
            s.transmutations++;
            level.sendParticles(ParticleTypes.CHERRY_LEAVES, at.x, at.y, at.z, 18, 0.35D, 0.35D, 0.35D, 0.04D);
            level.sendParticles(ParticleTypes.BUBBLE_POP, at.x, at.y, at.z, 12, 0.25D, 0.25D, 0.25D, 0.02D);
            level.sendParticles(ruby(n), at.x, at.y, at.z, 12, 0.35D, 0.35D, 0.35D, 0.03D);
            PortFx.sound(level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 0.55F, 1.55F);
        }
    }

    @Override
    public void stop(ServerPlayer p, StoneTree.Node n) { SESSIONS.remove(p.getUUID()); }

    private static boolean supported(Projectile projectile) {
        return projectile.isAlive() && (projectile instanceof AbstractArrow || projectile instanceof ThrownPotion
                || projectile instanceof AbstractHurtingProjectile || projectile instanceof ShulkerBullet);
    }

    private static void halo(ServerLevel level, StoneTree.Node n, Vec3 c, double radius, int samples) {
        DustParticleOptions ruby = ruby(n), rose = rose();
        for (int i = 0; i < samples; i++) {
            double a = Math.PI * 2D * i / samples;
            level.sendParticles(i % 4 == 0 ? rose : ruby, c.x + Math.cos(a) * radius, c.y + 0.5D * Math.sin(a * 3D),
                    c.z + Math.sin(a) * radius, 1, 0D, 0D, 0D, 0D);
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { SESSIONS.remove(e.getEntity().getUUID()); }
}
