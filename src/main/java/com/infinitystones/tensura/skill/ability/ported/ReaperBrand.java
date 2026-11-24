package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Port of palladium:reaper_brand — timed session (original palladium:activation, 122 ticks). Brands the aimed
 * hostile (32 blocks) with a six-second soul seal. Every point of health the caster's own hits actually remove from
 * it charges the seal (max 200). After 120 ticks the seal ruptures for 60 + charge damage. Breaks without rupture if
 * the target dies/leaves, the caster gets more than 96 blocks away, or the session ends early.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class ReaperBrand implements Ported {

    private static final Map<UUID, Brand> BRANDS = new HashMap<>();

    public ReaperBrand() {}

    @Override
    public void start(ServerPlayer owner, StoneTree.Node n) {
        ServerLevel level = owner.serverLevel();
        LivingEntity target = aimedTarget(level, owner);
        if (target == null) return;
        int c = n.stone().color();
        Brand brand = new Brand(level, owner, target, n, level.getGameTime() + 120L,
                PortFx.dust(c, 0.85F), PortFx.dust(SpatialExchange.lighten(c, 0.5F), 0.65F));
        BRANDS.put(owner.getUUID(), brand);
        render(brand);
        PortFx.sound(level, target.getEyePosition(), n, "infinity:soul_stone", 0.65F, 0.62F);
        PortFx.sound(level, target.position(), SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.6F, 1.2F);
    }

    @Override
    public void tick(ServerPlayer owner, StoneTree.Node n, int ticks) {
        Brand brand = BRANDS.get(owner.getUUID());
        if (brand == null) return;
        if (!valid(brand)) { BRANDS.remove(owner.getUUID()); return; }
        if (brand.level.getGameTime() >= brand.expiresAt) { rupture(brand); return; }
        int enabled = ticks + 1; // original getEnabledTicks()
        if (enabled % 4 == 0) render(brand);
        if (enabled % 20 == 0) PortFx.sound(brand.level, brand.target.position(), SoundEvents.AMETHYST_BLOCK_RESONATE,
                0.45F, 0.6F + brand.charge / 250F);
    }

    @Override
    public void stop(ServerPlayer owner, StoneTree.Node n) {
        BRANDS.remove(owner.getUUID());
    }

    // ------------------------------------------------------------------ charge hooks (original AbilityEventHandler)

    /** Capture the target's health before the caster's hit lands (after other handlers changed the amount). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void beforeDamage(LivingIncomingDamageEvent e) {
        if (BRANDS.isEmpty() || e.isCanceled() || TemporalEcho.isEcho(e.getSource()) || !(e.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        Brand b = BRANDS.get(attacker.getUUID());
        if (b != null && b.target == e.getEntity() && valid(b)) b.healthBefore = e.getEntity().getHealth();
    }

    /** Charge with the health actually lost. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void afterDamage(LivingDamageEvent.Post e) {
        if (BRANDS.isEmpty() || TemporalEcho.isEcho(e.getSource()) || !(e.getSource().getEntity() instanceof ServerPlayer attacker)) return;
        Brand b = BRANDS.get(attacker.getUUID());
        LivingEntity target = e.getEntity();
        if (b == null || b.target != target || !Float.isFinite(b.healthBefore)) return;
        float lost = Math.max(0F, b.healthBefore - target.getHealth());
        b.healthBefore = Float.NaN;
        if (!valid(b) || lost <= 0F) return;
        b.charge = Math.min(200F, b.charge + lost);
        render(b);
        b.level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getEyeY() + 0.3D, target.getZ(), 4, 0.2D, 0.2D, 0.2D, 0.02D);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { BRANDS.remove(e.getEntity().getUUID()); }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) { BRANDS.clear(); }

    // ------------------------------------------------------------------ logic

    private static boolean valid(Brand b) {
        return b.owner.isAlive() && !b.owner.isRemoved() && b.target.isAlive() && !b.target.isRemoved()
                && !b.owner.hasDisconnected()
                && b.owner.level() == b.level && b.target.level() == b.level
                && b.owner.distanceToSqr(b.target) <= 96D * 96D;
    }

    private static @Nullable LivingEntity aimedTarget(ServerLevel level, ServerPlayer owner) {
        Vec3 eye = owner.getEyePosition(), end = eye.add(owner.getLookAngle().scale(32D));
        HitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        double nearest = wall.getType() == HitResult.Type.MISS ? 32D * 32D : eye.distanceToSqr(wall.getLocation());
        LivingEntity selected = null;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class,
                owner.getBoundingBox().expandTowards(owner.getLookAngle().scale(32D)).inflate(1D),
                e -> e instanceof Enemy && e.isAlive() && !e.isSpectator() && !SpiritLegion.isSpirit(e)
                        && !owner.isAlliedTo(e) && !e.isAlliedTo(owner) && PortFx.canAffect(owner, e))) {
            var hit = candidate.getBoundingBox().inflate(0.3D).clip(eye, end);
            if (hit.isPresent() && eye.distanceToSqr(hit.get()) < nearest && owner.hasLineOfSight(candidate)) {
                nearest = eye.distanceToSqr(hit.get());
                selected = candidate;
            }
        }
        return selected;
    }

    private static void rupture(Brand b) {
        // Remove before hurt: the rupture cannot charge itself or recurse through the mark.
        BRANDS.remove(b.owner.getUUID());
        if (PortFx.canAffect(b.owner, b.target)) b.target.hurt(Rules.source(b.owner, b.target, false), Rules.amount(b.target, 40F, 0.12F, false) + Math.min(b.charge, 0.18F * b.target.getMaxHealth()));
        Vec3 c = b.target.getBoundingBox().getCenter();
        b.level.sendParticles(ParticleTypes.SOUL, c.x, c.y, c.z, 24, 0.55D, 0.65D, 0.55D, 0.08D);
        b.level.sendParticles(b.light, c.x, c.y, c.z, 36, 0.7D, 0.8D, 0.7D, 0D);
        PortFx.sound(b.level, b.target.position(), SoundEvents.RESPAWN_ANCHOR_DEPLETE, 0.7F, 0.7F);
        PortFx.sound(b.level, c, b.node, "infinity:soul_stone", 0.7F, 0.88F);
    }

    private static void render(Brand b) {
        Vec3 c = b.target.position().add(0D, b.target.getBbHeight() + 0.55D, 0D);
        double radius = Math.max(0.55D, Math.min(1.5D, b.target.getBbWidth() * 0.7D));
        int charged = (int) (32 * b.charge / 200F);
        double phase = b.level.getGameTime() * 0.025D;
        for (int i = 0; i < 32; i++) {
            double a = phase + i * Math.PI / 16D;
            b.level.sendParticles(i < charged ? b.light : b.main, c.x + Math.cos(a) * radius,
                    c.y + Math.sin(a) * radius, c.z, 1, 0D, 0D, 0D, 0D);
        }
        b.level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 1, 0D, 0D, 0D, 0D);
    }

    private static final class Brand {
        final ServerLevel level;
        final ServerPlayer owner;
        final LivingEntity target;
        final StoneTree.Node node;
        final long expiresAt;
        final DustParticleOptions main, light;
        float charge = 0F, healthBefore = Float.NaN;

        Brand(ServerLevel level, ServerPlayer owner, LivingEntity target, StoneTree.Node node, long expiresAt,
              DustParticleOptions main, DustParticleOptions light) {
            this.level = level; this.owner = owner; this.target = target; this.node = node;
            this.expiresAt = expiresAt; this.main = main; this.light = light;
        }
    }
}
