package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mind Stone "Mental Bastion" — port of MentalBastionAbility (timed session, 160 ticks): a psychic sanctuary of
 * {@code radius} around the caster. On activation it cleanses harmful effects from the caster and allies (team mates,
 * own tamed pets) inside it; while active allies inside take damage × (1 − damage_reduction), and each hostile mob
 * entering is pushed out once with repel_strength.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class MentalBastion implements Ported {

    private record Bastion(UUID owner, ResourceKey<Level> dimension, long expiresAt, float radius, float damageMultiplier,
                           float repelStrength, int color, Set<UUID> repelled) {}

    /** Caster UUID → active bastion (server thread only). */
    private static final Map<UUID, Bastion> BASTIONS = new HashMap<>();

    @Override
    public void start(ServerPlayer p, StoneTree.Node n) {
        ServerLevel level = p.serverLevel();
        float radius = PortFx.f(n, "radius", 8F);
        int duration = PortFx.i(n, "duration_ticks", 160);
        float reduction = Math.max(0F, Math.min(0.95F, PortFx.f(n, "damage_reduction", 0.75F)));
        float repel = PortFx.f(n, "repel_strength", 2.2F);
        Bastion bastion = new Bastion(p.getUUID(), level.dimension(), level.getGameTime() + duration, radius, 1F - reduction,
                repel, n.stone().color(), new HashSet<>());
        BASTIONS.put(p.getUUID(), bastion);
        cleanseAllies(level, p, bastion);
        renderBurst(level, p, bastion);
        Vec3 eye = new Vec3(p.getX(), p.getEyeY(), p.getZ());
        PortFx.sound(level, eye, n, "infinity:mind_stone_phase", 0.65F, 1.35F);
        PortFx.sound(level, p.position(), SoundEvents.BEACON_ACTIVATE, 0.6F, 1.25F);
    }

    @Override
    public void tick(ServerPlayer p, StoneTree.Node n, int ticks) {
        ServerLevel level = p.serverLevel();
        Bastion bastion = BASTIONS.get(p.getUUID());
        if (bastion == null || level.getGameTime() >= bastion.expiresAt || !level.dimension().equals(bastion.dimension)) {
            BASTIONS.remove(p.getUUID());
            return;
        }
        repelIntruders(level, p, bastion);
        if (level.getGameTime() % 4 == 0) renderRing(level, p, bastion);
    }

    @Override
    public void stop(ServerPlayer p, StoneTree.Node n) {
        Bastion removed = BASTIONS.remove(p.getUUID());
        if (removed != null) {
            ServerLevel level = p.serverLevel();
            PortFx.sound(level, p.position(), SoundEvents.BEACON_DEACTIVATE, 0.5F, 1.35F);
            level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getEyeY(), p.getZ(),
                    18, removed.radius * 0.35D, 0.8D, removed.radius * 0.35D, 0.03D);
        }
    }

    // ---------------- hooks ----------------

    /** Original AbilityEventHandler#livingIncomingDamage → MentalBastionAbility.damageMultiplierFor. */
    @SubscribeEvent
    public static void incomingDamage(LivingIncomingDamageEvent e) {
        if (BASTIONS.isEmpty()) return;
        LivingEntity entity = e.getEntity();
        if (!(entity.level() instanceof ServerLevel level)) return;
        float multiplier = damageMultiplierFor(level, entity);
        if (multiplier < 1F) e.setAmount(e.getAmount() * multiplier);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { BASTIONS.remove(e.getEntity().getUUID()); }

    @SubscribeEvent
    public static void serverStopping(ServerStoppingEvent e) { BASTIONS.clear(); }

    // ---------------- logic ----------------

    private static float damageMultiplierFor(ServerLevel level, LivingEntity entity) {
        float multiplier = 1F;
        for (var entry : List.copyOf(BASTIONS.entrySet())) {
            Bastion bastion = entry.getValue();
            if (level.getGameTime() >= bastion.expiresAt) {
                BASTIONS.remove(entry.getKey());
                continue;
            }
            if (!level.dimension().equals(bastion.dimension)) continue;
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(bastion.owner);
            if (owner == null || !owner.isAlive()) {
                BASTIONS.remove(entry.getKey());
                continue;
            }
            if (owner.level() == level && owner.distanceToSqr(entity) <= bastion.radius * bastion.radius && isAlly(owner, entity)) {
                multiplier = Math.min(multiplier, bastion.damageMultiplier);
            }
        }
        return multiplier;
    }

    private static boolean isAlly(LivingEntity owner, LivingEntity candidate) {
        if (candidate == owner || owner.isAlliedTo(candidate)) return true;
        return candidate instanceof TamableAnimal tame && owner.getUUID().equals(tame.getOwnerUUID());
    }

    private static void cleanseAllies(ServerLevel level, ServerPlayer owner, Bastion bastion) {
        DustParticleOptions pale = PortFx.dust(pale(bastion.color), 0.85F);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(bastion.radius),
                c -> isAlly(owner, c))) {
            for (MobEffectInstance effect : new ArrayList<>(living.getActiveEffects())) {
                if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) living.removeEffect(effect.getEffect());
            }
            level.sendParticles(pale, living.getX(), living.getEyeY(), living.getZ(),
                    10, living.getBbWidth() * 0.35D, living.getBbHeight() * 0.25D, living.getBbWidth() * 0.35D, 0.03D);
        }
    }

    private static void repelIntruders(ServerLevel level, ServerPlayer owner, Bastion bastion) {
        double r2 = bastion.radius * bastion.radius;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(bastion.radius),
                c -> c instanceof Enemy && c.isAlive() && owner.distanceToSqr(c) <= r2 && PortFx.canAffect(owner, c))) {
            if (!bastion.repelled.add(mob.getUUID())) continue;
            Vec3 away = mob.position().subtract(owner.position()).multiply(1D, 0D, 1D);
            if (away.lengthSqr() < 0.01D) away = new Vec3(1D, 0D, 0D);
            away = away.normalize();
            mob.getNavigation().stop();
            mob.push(away.x * bastion.repelStrength, 0.42D, away.z * bastion.repelStrength);
            mob.hurtMarked = true;
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, mob.getX(), mob.getEyeY(), mob.getZ(),
                    20, mob.getBbWidth() * 0.35D, mob.getBbHeight() * 0.25D, mob.getBbWidth() * 0.35D, 0.12D);
            PortFx.sound(level, mob.position(), SoundEvents.SHIELD_BLOCK, 0.5F, 1.4F);
        }
    }

    /** Stone colour blended towards white (the original's pale accent). */
    private static int pale(int color) {
        int r = (color >> 16) & 255, g = (color >> 8) & 255, b = color & 255;
        r = r + (255 - r) * 6 / 10; g = g + (255 - g) * 6 / 10; b = b + (255 - b) * 6 / 10;
        return (r << 16) | (g << 8) | b;
    }

    private static void renderRing(ServerLevel level, ServerPlayer caster, Bastion bastion) {
        DustParticleOptions gold = PortFx.dust(bastion.color, 1.15F);
        DustParticleOptions pale = PortFx.dust(pale(bastion.color), 0.85F);
        float radius = bastion.radius;
        double phase = level.getGameTime() * 0.035D;
        for (int i = 0; i < 28; i++) {
            double angle = Math.PI * 2D * i / 28D + phase;
            double y = caster.getY() + 0.15D + Math.sin(angle * 3D + phase) * 0.16D;
            level.sendParticles(i % 3 == 0 ? pale : gold, caster.getX() + Math.cos(angle) * radius, y,
                    caster.getZ() + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
        }
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2D * i / 8D - phase * 1.8D;
            level.sendParticles(pale, caster.getX() + Math.cos(angle) * radius * 0.52D,
                    caster.getEyeY() + Math.sin(angle * 2D) * 0.35D, caster.getZ() + Math.sin(angle) * radius * 0.52D, 1, 0, 0, 0, 0);
        }
    }

    private static void renderBurst(ServerLevel level, ServerPlayer caster, Bastion bastion) {
        DustParticleOptions gold = PortFx.dust(bastion.color, 1.15F);
        DustParticleOptions pale = PortFx.dust(pale(bastion.color), 0.85F);
        for (int ring = 1; ring <= 4; ring++) {
            double r = bastion.radius * ring / 4D;
            for (int i = 0; i < 24; i++) {
                double angle = Math.PI * 2D * i / 24D;
                level.sendParticles(ring % 2 == 0 ? pale : gold, caster.getX() + Math.cos(angle) * r, caster.getY() + 0.18D,
                        caster.getZ() + Math.sin(angle) * r, 1, 0, 0, 0, 0);
            }
        }
    }
}
