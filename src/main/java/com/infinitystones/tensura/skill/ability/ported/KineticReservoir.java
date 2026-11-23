package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Power Stone "Kinetic Reservoir" — port of KineticReservoirAbility (timed session, 100 ticks guard window). While
 * guarding, incoming damage is reduced by damage_reduction and the absorbed part × energy_conversion is stored (capped
 * at max_reserve). When the guard ends the stored energy stays for reserve_lifetime ticks and is released entirely as
 * bonus damage on the caster's next direct melee hit on a hostile mob.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class KineticReservoir implements Ported {

    private static final class Reservoir {
        final ResourceKey<Level> dimension;
        final float reduction, conversion, maxReserve;
        final int lifetime, color;
        final String sound;
        boolean guarding = true;
        float energy;
        long expiresAt = Long.MAX_VALUE;
        int announcedLevel;
        Reservoir(ResourceKey<Level> dimension, float reduction, float conversion, float maxReserve, int lifetime, int color, String sound) {
            this.dimension = dimension; this.reduction = reduction; this.conversion = conversion; this.maxReserve = maxReserve;
            this.lifetime = lifetime; this.color = color; this.sound = sound;
        }
    }

    /** Caster UUID → reservoir (server thread only). */
    private static final Map<UUID, Reservoir> RESERVOIRS = new HashMap<>();

    @Override
    public void start(ServerPlayer p, StoneTree.Node n) {
        ServerLevel level = p.serverLevel();
        Reservoir r = new Reservoir(level.dimension(),
                Math.max(0F, Math.min(0.95F, PortFx.f(n, "damage_reduction", 0.75F))),
                PortFx.f(n, "energy_conversion", 8F), Math.max(1F, PortFx.f(n, "max_reserve", 240F)),
                PortFx.i(n, "reserve_lifetime", 200), n.stone().color(), PortFx.s(n, "sound", "infinity:power_stone_beam1"));
        RESERVOIRS.put(p.getUUID(), r);
        PortFx.sound(level, new Vec3(p.getX(), p.getEyeY(), p.getZ()), r.sound, 0.55F, 1.35F);
        PortFx.sound(level, p.position(), SoundEvents.BEACON_ACTIVATE, 0.55F, 0.72F);
        renderPulse(level, p, r, 0.9D, 20);
    }

    @Override
    public void tick(ServerPlayer p, StoneTree.Node n, int ticks) {
        ServerLevel level = p.serverLevel();
        Reservoir r = RESERVOIRS.get(p.getUUID());
        if (r == null || !r.dimension.equals(level.dimension())) return;
        r.guarding = true;
        if (p.tickCount % 2 == 0) renderGuard(level, p, r);
    }

    @Override
    public void stop(ServerPlayer p, StoneTree.Node n) {
        Reservoir r = RESERVOIRS.get(p.getUUID());
        if (r == null) return;
        r.guarding = false;
        if (r.energy <= 0.001F || !p.isAlive()) {
            RESERVOIRS.remove(p.getUUID());
            return;
        }
        ServerLevel level = p.serverLevel();
        r.expiresAt = level.getGameTime() + r.lifetime;
        renderPulse(level, p, r, 1.35D, 32);
        PortFx.sound(level, p.position(), SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.6F, 0.86F + 0.24F * r.energy / r.maxReserve);
    }

    // ---------------- hooks (original AbilityEventHandler) ----------------

    /** absorbIncoming — LOW priority so Mental Bastion's reduction applies first, like the original order. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void incomingDamage(LivingIncomingDamageEvent e) {
        if (RESERVOIRS.isEmpty() || !(e.getEntity() instanceof ServerPlayer player)) return;
        Reservoir r = current(player);
        if (r == null || !r.guarding || e.getAmount() <= 0F) return;
        ServerLevel level = player.serverLevel();

        float original = e.getAmount();
        float absorbed = original * r.reduction;
        e.setAmount(Math.max(0F, original - absorbed));

        float previous = r.energy;
        r.energy = Math.min(r.maxReserve, r.energy + absorbed * r.conversion);
        int charge = chargeLevel(r);
        if (charge > r.announcedLevel) {
            r.announcedLevel = charge;
            PortFx.sound(level, player.position(), SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.45F + charge * 0.08F, 0.72F + charge * 0.18F);
            renderPulse(level, player, r, 0.7D + charge * 0.3D, 12 + charge * 6);
        } else if (r.energy > previous) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getEyeY() - 0.2D, player.getZ(),
                    4, 0.36D, 0.5D, 0.36D, 0.08D);
        }
    }

    /** dischargeOnMelee — the victim is a hostile mob, the attacker hit it directly (melee). */
    @SubscribeEvent
    public static void damagePost(LivingDamageEvent.Post e) {
        if (RESERVOIRS.isEmpty()) return;
        LivingEntity target = e.getEntity();
        DamageSource source = e.getSource();
        if (e.getNewDamage() <= 0F || !(target instanceof Enemy)) return;
        if (!(source.getEntity() instanceof ServerPlayer attacker) || source.getDirectEntity() != attacker) return;
        Reservoir r = current(attacker);
        if (r == null || r.energy <= 0.001F || !PortFx.canAffect(attacker, target)) return;

        float discharge = r.energy;
        RESERVOIRS.remove(attacker.getUUID());
        // the triggering hit just set the victim's hurt cooldown; without this the nested hit would only deal
        // (discharge − that hit), which the original intended as a full release
        target.invulnerableTime = 0;
        target.hurt(PortFx.attack(attacker, target), discharge);

        ServerLevel level = attacker.serverLevel();
        Vec3 direction = target.position().subtract(attacker.position());
        direction = direction.lengthSqr() < 1.0E-4 ? Vec3.ZERO : direction.normalize();
        target.push(direction.x * 1.15D, 0.48D, direction.z * 1.15D);
        target.hurtMarked = true;
        renderDischarge(level, attacker, target, r, discharge / r.maxReserve);
        Vec3 at = new Vec3(target.getX(), target.getEyeY(), target.getZ());
        PortFx.sound(level, at, r.sound, 0.7F, 0.62F);
        PortFx.sound(level, at, SoundEvents.GENERIC_EXPLODE, 0.65F, 0.68F);
        PortFx.sound(level, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.35F, 1.45F);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { RESERVOIRS.remove(e.getEntity().getUUID()); }

    @SubscribeEvent
    public static void death(LivingDeathEvent e) {
        if (!RESERVOIRS.isEmpty() && e.getEntity() instanceof ServerPlayer p) RESERVOIRS.remove(p.getUUID());
    }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent e) { RESERVOIRS.clear(); }

    // ---------------- logic / visuals ----------------

    private static @Nullable Reservoir current(ServerPlayer owner) {
        Reservoir r = RESERVOIRS.get(owner.getUUID());
        if (r == null) return null;
        if (!r.dimension.equals(owner.level().dimension()) || (!r.guarding && owner.level().getGameTime() > r.expiresAt)) {
            RESERVOIRS.remove(owner.getUUID());
            return null;
        }
        return r;
    }

    private static int chargeLevel(Reservoir r) {
        float ratio = r.energy / r.maxReserve;
        return ratio >= 0.999F ? 3 : ratio >= 0.66F ? 2 : ratio >= 0.25F ? 1 : 0;
    }

    private static int light(int color, int pct) {
        int red = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        red = red + (255 - red) * pct / 100; g = g + (255 - g) * pct / 100; b = b + (255 - b) * pct / 100;
        return (red << 16) | (g << 8) | b;
    }

    private static void renderGuard(ServerLevel level, ServerPlayer caster, Reservoir r) {
        DustParticleOptions violet = PortFx.dust(r.color, 0.7F), magenta = PortFx.dust(light(r.color, 35), 0.6F),
                white = PortFx.dust(light(r.color, 80), 0.5F);
        int charge = chargeLevel(r);
        double radius = 0.42D + charge * 0.12D;
        Vec3 look = caster.getLookAngle();
        Vec3 side = new Vec3(-look.z, 0D, look.x);
        if (side.lengthSqr() < 0.001D) side = new Vec3(1D, 0D, 0D);
        side = side.normalize();
        Vec3 fist = caster.getEyePosition().add(look.scale(0.62D)).add(side.scale(0.34D)).add(0D, -0.38D, 0D);
        int points = 10 + charge * 4;
        double phase = caster.tickCount * 0.18D;
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2D * i / points + phase;
            Vec3 pt = fist.add(side.scale(Math.cos(angle) * radius)).add(0D, Math.sin(angle) * radius, 0D);
            level.sendParticles(charge == 3 && i % 3 == 0 ? white : i % 2 == 0 ? magenta : violet, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
        }
    }

    private static void renderPulse(ServerLevel level, LivingEntity caster, Reservoir r, double radius, int points) {
        DustParticleOptions violet = PortFx.dust(r.color, 0.8F), magenta = PortFx.dust(light(r.color, 35), 0.7F);
        for (int i = 0; i < points; i++) {
            double angle = Math.PI * 2D * i / points;
            Vec3 pt = caster.position().add(Math.cos(angle) * radius, 1D + Math.sin(angle * 2D) * 0.18D, Math.sin(angle) * radius);
            level.sendParticles(i % 3 == 0 ? magenta : violet, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
        }
    }

    private static void renderDischarge(ServerLevel level, LivingEntity attacker, LivingEntity target, Reservoir r, float ratio) {
        DustParticleOptions violet = PortFx.dust(r.color, 0.7F), magenta = PortFx.dust(light(r.color, 35), 0.6F),
                white = PortFx.dust(light(r.color, 80), 0.5F);
        Vec3 start = attacker.getEyePosition().add(0D, -0.35D, 0D);
        Vec3 delta = target.getEyePosition().subtract(start);
        int segments = Math.max(4, (int) Math.ceil(delta.length() * 5D));
        for (int i = 0; i <= segments; i++) {
            Vec3 pt = start.add(delta.scale(i / (double) segments));
            level.sendParticles(i % 3 == 0 ? white : i % 2 == 0 ? magenta : violet, pt.x, pt.y, pt.z, 2, 0.035D, 0.035D, 0.035D, 0.01D);
        }
        int particles = 28 + Math.round(32F * Math.min(1F, ratio));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getEyeY(), target.getZ(),
                particles, target.getBbWidth() * 0.65D, target.getBbHeight() * 0.38D, target.getBbWidth() * 0.65D, 0.2D);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, target.getX(), target.getEyeY(), target.getZ(),
                particles / 2, 0.55D, 0.75D, 0.55D, 0.08D);
    }
}
