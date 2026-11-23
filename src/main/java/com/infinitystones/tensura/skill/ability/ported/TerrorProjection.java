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

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mind Stone "Terror Projection" — port of TerrorProjectionAbility: a cone of psychic terror (range, half_angle) that
 * damages up to max_targets hostile mobs in sight and makes ordinary ones flee from the caster for fear_ticks
 * (boss-like mobs get Weakness instead). Fear ends early if the caster leaves/dies, and is driven by the mob's tick.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class TerrorProjection implements Ported {

    private record Fear(UUID source, @Nullable UUID originalTarget, long expiresAt, float speed, int color) {}

    /** Frightened mob UUID → fear (server thread only). */
    private static final Map<UUID, Fear> FEARS = new HashMap<>();

    @Override
    public Boolean press(ServerPlayer p, StoneTree.Node n, boolean alt) {
        ServerLevel level = p.serverLevel();
        float damage = PortFx.f(n, "damage", 60F);
        float range = PortFx.f(n, "range", 20F);
        float halfAngle = PortFx.f(n, "half_angle", 50F);
        int maxTargets = PortFx.i(n, "max_targets", 8);
        int fearTicks = PortFx.i(n, "fear_ticks", 100);
        float fleeSpeed = PortFx.f(n, "flee_speed", 1.35F);
        int bossAmp = PortFx.i(n, "boss_weakness_amplifier", 4);
        int color = n.stone().color();

        Vec3 origin = p.getEyePosition();
        Vec3 look = p.getLookAngle().normalize();
        double minimumDot = Math.cos(Math.toRadians(halfAngle));
        List<Mob> targets = level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(range),
                        mob -> inCone(p, mob, origin, look, minimumDot, range))
                .stream().sorted(Comparator.comparingDouble(p::distanceToSqr)).limit(maxTargets).toList();

        renderProjection(level, p, look, range, halfAngle, color);
        Vec3 eye = new Vec3(p.getX(), p.getEyeY(), p.getZ());
        PortFx.sound(level, eye, n, "infinity:mind_stone_phase", 0.7F, 0.72F);
        PortFx.sound(level, eye, SoundEvents.WARDEN_SONIC_CHARGE, 0.55F, 0.65F);

        for (int index = 0; index < targets.size(); index++) {
            Mob mob = targets.get(index);
            mob.hurt(PortFx.attack(p, mob), damage);
            if (!mob.isAlive()) continue;
            if (isBossLike(mob)) {
                mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, fearTicks, bossAmp, false, true, true));
            } else {
                UUID originalTarget = mob.getTarget() == null ? null : mob.getTarget().getUUID();
                FEARS.put(mob.getUUID(), new Fear(p.getUUID(), originalTarget, level.getGameTime() + fearTicks, fleeSpeed, color));
                mob.setTarget(null);
                flee(mob, p, fleeSpeed);
            }
            renderImpact(level, mob, index, color);
        }
        return true;
    }

    // ---------------- hooks ----------------

    /** Original PowerEventHandler#entityTick → tickFrightenedMob. */
    @SubscribeEvent
    public static void entityTick(EntityTickEvent.Post e) {
        Entity entity = e.getEntity();
        if (entity.level().isClientSide() || FEARS.isEmpty() || !(entity instanceof Mob mob)) return;
        Fear fear = FEARS.get(mob.getUUID());
        if (fear == null || !(mob.level() instanceof ServerLevel level)) return;
        if (!mob.isAlive() || level.getGameTime() >= fear.expiresAt) {
            restore(mob, fear);
            FEARS.remove(mob.getUUID());
            return;
        }
        Entity source = level.getEntity(fear.source);
        if (!(source instanceof LivingEntity living) || !living.isAlive()) {
            restore(mob, fear);
            FEARS.remove(mob.getUUID());
            return;
        }
        mob.setTarget(null);
        if (level.getGameTime() % 10 == 0 || mob.getNavigation().isDone()) flee(mob, living, fear.speed);
        if (level.getGameTime() % 5 == 0) renderFear(level, mob, fear.color);
    }

    /** Drops entries of mobs that were unloaded while frightened. */
    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post e) {
        if (FEARS.isEmpty() || e.getServer().getTickCount() % 100 != 0) return;
        long now = e.getServer().overworld().getGameTime();
        FEARS.values().removeIf(f -> now >= f.expiresAt + 40);
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent e) { FEARS.clear(); }

    // ---------------- logic ----------------

    private static boolean inCone(ServerPlayer caster, Mob mob, Vec3 origin, Vec3 look, double minimumDot, float range) {
        if (!mob.isAlive() || !(mob instanceof Enemy) || FEARS.containsKey(mob.getUUID()) || !PortFx.canAffect(caster, mob)) return false;
        Vec3 delta = mob.getBoundingBox().getCenter().subtract(origin);
        return delta.lengthSqr() <= range * range && delta.normalize().dot(look) >= minimumDot && caster.hasLineOfSight(mob);
    }

    private static boolean isBossLike(Mob mob) {
        return mob instanceof EnderDragon || mob instanceof WitherBoss || mob instanceof Warden || mob.getMaxHealth() >= 300F;
    }

    private static void flee(Mob mob, LivingEntity source, float speed) {
        Vec3 away = mob.position().subtract(source.position()).multiply(1D, 0D, 1D);
        if (away.lengthSqr() < 0.01D) away = new Vec3(1D, 0D, 0D);
        Vec3 destination = mob.position().add(away.normalize().scale(14D));
        mob.getNavigation().moveTo(destination.x, destination.y, destination.z, speed);
    }

    private static void restore(Mob mob, Fear fear) {
        if (!(mob.level() instanceof ServerLevel level)) return;
        LivingEntity original = null;
        if (fear.originalTarget != null && level.getEntity(fear.originalTarget) instanceof LivingEntity living && living.isAlive()) {
            original = living;
        }
        mob.getNavigation().stop();
        mob.setTarget(original);
    }

    /** Darker shade of the stone colour (the original's violet accent). */
    private static int dark(int color) {
        int r = (int) (((color >> 16) & 255) * 0.42F), g = (int) (((color >> 8) & 255) * 0.3F), b = (int) ((color & 255) * 0.3F);
        return (r << 16) | (g << 8) | b;
    }

    private static void renderProjection(ServerLevel level, ServerPlayer caster, Vec3 look, float range, float halfAngle, int color) {
        DustParticleOptions gold = PortFx.dust(color, 1.25F);
        DustParticleOptions shade = PortFx.dust(dark(color), 1.05F);
        Vec3 horizontal = new Vec3(look.x, 0D, look.z);
        if (horizontal.lengthSqr() < 0.01D) horizontal = new Vec3(0D, 0D, 1D);
        horizontal = horizontal.normalize();
        double baseAngle = Math.atan2(horizontal.z, horizontal.x);
        for (int ring = 1; ring <= 5; ring++) {
            double distance = range * ring / 5D;
            for (int step = -5; step <= 5; step++) {
                double angle = baseAngle + Math.toRadians(halfAngle * step / 5D);
                double x = caster.getX() + Math.cos(angle) * distance;
                double z = caster.getZ() + Math.sin(angle) * distance;
                double y = caster.getEyeY() + Math.sin(ring * 1.3D + step) * 0.18D;
                level.sendParticles(((ring + step) & 1) == 0 ? gold : shade, x, y, z, 1, 0, 0, 0, 0);
            }
        }
    }

    private static void renderImpact(ServerLevel level, Mob mob, int index, int color) {
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, mob.getX(), mob.getEyeY(), mob.getZ(),
                18, mob.getBbWidth() * 0.35, mob.getBbHeight() * 0.3, mob.getBbWidth() * 0.35, 0.08);
        level.sendParticles(PortFx.dust(color, 1.25F), mob.getX(), mob.getY() + mob.getBbHeight() + 0.2, mob.getZ(),
                12, mob.getBbWidth() * 0.4, 0.12, mob.getBbWidth() * 0.4, 0.02);
        PortFx.sound(level, mob.position(), SoundEvents.ILLUSIONER_CAST_SPELL, 0.45F, 0.72F + index * 0.045F);
    }

    private static void renderFear(ServerLevel level, Mob mob, int color) {
        DustParticleOptions gold = PortFx.dust(color, 1.1F);
        DustParticleOptions shade = PortFx.dust(dark(color), 1.0F);
        double y = mob.getY() + mob.getBbHeight() + 0.25D;
        double phase = level.getGameTime() * 0.22D;
        for (int i = 0; i < 4; i++) {
            double angle = phase + Math.PI * 0.5D * i;
            level.sendParticles(i % 2 == 0 ? gold : shade, mob.getX() + Math.cos(angle) * 0.45D, y + Math.sin(phase) * 0.08D,
                    mob.getZ() + Math.sin(angle) * 0.45D, 1, 0, 0, 0, 0);
        }
    }
}
