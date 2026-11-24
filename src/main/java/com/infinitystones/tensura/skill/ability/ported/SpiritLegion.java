package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Port of palladium:spirit_legion — timed session (original palladium:activation, 300 ticks). Summons three soul
 * sentinels around the caster. The original Palladium SoulSentinel entity is replaced by a vanilla Vex with no AI,
 * no gravity, invulnerable and silent, driven entirely from here exactly like the original entity tick:
 * <ul>
 *   <li>picks the 3 nearest hostiles within 16 blocks of the caster that the caster can see (plus, first, whoever
 *       recently hit the caster or was hit by the caster, if the caster may affect them);</li>
 *   <li>each spirit circles its target (formation % targets) or orbits the caster at 2 blocks, max 0.45 b/tick;</li>
 *   <li>attacks every 20 ticks (first after 10 + 5·formation) within 2.5 blocks for 30 damage credited to the caster;</li>
 *   <li>vanishes when the session ends, after 300 ticks, if the caster dies/logs out or is more than 48 blocks away.</li>
 * </ul>
 * Spirits are tagged; untracked tagged vexes (server restart, chunk reload) are discarded and they never drop loot/xp.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class SpiritLegion implements Ported {

    public static final String TAG = "infinity_spirit_legion";
    private static final Map<UUID, Legion> LEGIONS = new HashMap<>();
    /** Every spirit currently owned by a live legion. */
    private static final Set<UUID> LIVE = new HashSet<>();

    public SpiritLegion() {}

    /** True for our summoned spirits (other abilities skip them as targets). */
    public static boolean isSpirit(Entity e) {
        return e instanceof Vex && e.getTags().contains(TAG);
    }

    @Override
    public void start(ServerPlayer caster, StoneTree.Node n) {
        ServerLevel level = caster.serverLevel();
        dismiss(caster.getUUID());
        Legion legion = new Legion(level, level.getGameTime() + 300L, n);
        for (int i = 0; i < 3; i++) {
            Vex spirit = EntityType.VEX.create(level);
            if (spirit == null) continue;
            double angle = i * Math.PI * 2D / 3D;
            spirit.moveTo(caster.getX() + Math.cos(angle) * 1.5D, caster.getY() + 1.5D,
                    caster.getZ() + Math.sin(angle) * 1.5D, caster.getYRot(), 0F);
            if (!level.hasChunkAt(spirit.blockPosition())) continue;
            spirit.addTag(TAG);
            spirit.setNoAi(true);
            spirit.setNoGravity(true);
            spirit.setCanPickUpLoot(false);
            spirit.setSilent(true);
            spirit.setInvulnerable(true);
            spirit.setPersistenceRequired(); // we own its lifetime; never random-despawn mid-fight
            LIVE.add(spirit.getUUID());
            if (level.addFreshEntity(spirit)) legion.spirits.add(new Spirit(spirit.getUUID(), i));
            else LIVE.remove(spirit.getUUID());
        }
        LEGIONS.put(caster.getUUID(), legion);
        PortFx.sound(level, caster.getEyePosition(), n, "infinity:soul_stone", 0.7F, 1.15F);
        PortFx.sound(level, caster.position(), SoundEvents.EVOKER_PREPARE_SUMMON, 0.6F, 1.3F);
    }

    @Override
    public void tick(ServerPlayer caster, StoneTree.Node n, int ticks) {
        Legion legion = LEGIONS.get(caster.getUUID());
        if (legion == null) return;
        ServerLevel level = caster.serverLevel();
        if (legion.level != level || !caster.isAlive() || caster.hasDisconnected() || level.getGameTime() >= legion.expiresAt) {
            dismiss(caster.getUUID());
            return;
        }
        List<LivingEntity> targets = targets(level, caster);
        for (Spirit s : new ArrayList<>(legion.spirits)) {
            if (!(level.getEntity(s.id) instanceof Vex spirit) || !spirit.isAlive()) {
                legion.spirits.remove(s);
                LIVE.remove(s.id);
                continue;
            }
            if (spirit.distanceToSqr(caster) > 48D * 48D) {
                discard(level, spirit, legion);
                legion.spirits.remove(s);
                continue;
            }
            drive(level, caster, spirit, s, targets, legion);
        }
    }

    @Override
    public void stop(ServerPlayer caster, StoneTree.Node n) {
        dismiss(caster.getUUID());
    }

    // ------------------------------------------------------------------ behaviour (original SoulSentinel#tick)

    private static List<LivingEntity> targets(ServerLevel level, ServerPlayer caster) {
        List<LivingEntity> list = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(16D),
                        e -> e instanceof Enemy && validTarget(caster, e))
                .stream().sorted(Comparator.comparingDouble(caster::distanceToSqr)).limit(3).toList());
        // the caster's current fight comes first (attacker, then the last thing the caster hit)
        LivingEntity hitBy = caster.getLastHurtByMob();
        LivingEntity hit = caster.tickCount - caster.getLastHurtMobTimestamp() < 100 ? caster.getLastHurtMob() : null;
        for (LivingEntity prio : new LivingEntity[]{hit, hitBy}) {
            if (prio != null && prio.level() == level && validTarget(caster, prio)) {
                list.remove(prio);
                list.add(0, prio);
            }
        }
        return list.size() > 3 ? new ArrayList<>(list.subList(0, 3)) : list;
    }

    private static boolean validTarget(ServerPlayer caster, LivingEntity e) {
        return !isSpirit(e) && e.isAlive() && !e.isSpectator()
                && !caster.isAlliedTo(e) && !e.isAlliedTo(caster) && PortFx.canAffect(caster, e)
                && caster.distanceToSqr(e) <= 16D * 16D && caster.hasLineOfSight(e);
    }

    private static void drive(ServerLevel level, ServerPlayer caster, Vex spirit, Spirit s, List<LivingEntity> targets, Legion legion) {
        LivingEntity target = targets.isEmpty() ? null : targets.get(s.formation % targets.size());
        double angle = level.getGameTime() * 0.04D + s.formation * Math.PI * 2D / 3D;
        Vec3 destination = target == null
                ? caster.position().add(Math.cos(angle) * 2D, 1.6D + Math.sin(angle * 2D) * 0.25D, Math.sin(angle) * 2D)
                : target.getBoundingBox().getCenter().add(
                        Math.cos(angle) * (target.getBbWidth() * 0.5D + 0.8D), 0.3D,
                        Math.sin(angle) * (target.getBbWidth() * 0.5D + 0.8D));
        Vec3 movement = destination.subtract(spirit.position());
        if (movement.lengthSqr() > 0.45D * 0.45D) movement = movement.normalize().scale(0.45D);
        if (level.hasChunkAt(spirit.blockPosition().offset((int) Math.floor(movement.x),
                (int) Math.floor(movement.y), (int) Math.floor(movement.z)))) {
            spirit.move(MoverType.SELF, movement);
        }
        spirit.setDeltaMovement(Vec3.ZERO);
        spirit.setIsCharging(target != null);
        if (target != null) {
            Vec3 look = target.position().subtract(spirit.position());
            float yaw = (float) (Math.atan2(look.z, look.x) * 180D / Math.PI) - 90F;
            spirit.setYRot(yaw);
            spirit.yBodyRot = yaw;
            spirit.setYHeadRot(yaw);
        }
        if (s.attackDelay > 0) s.attackDelay--;
        if (target != null && spirit.distanceToSqr(target) <= 6.25D && spirit.hasLineOfSight(target) && s.attackDelay == 0) {
            s.attackDelay = 20;
            if (target.hurt(Rules.source(caster, target, false), Rules.amount(target, 30F, 0.04F, false))) {
                level.sendParticles(legion.dust, target.getX(), target.getEyeY(), target.getZ(), 12, 0.4D, 0.4D, 0.4D, 0D);
                PortFx.sound(level, spirit.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.45F, 1.4F);
            }
        }
        if (spirit.tickCount % 4 == 0) {
            level.sendParticles(legion.trail, spirit.getX(), spirit.getY() + 0.3D, spirit.getZ(), 2, 0.12D, 0.2D, 0.12D, 0D);
        }
    }

    // ------------------------------------------------------------------ cleanup

    private static void dismiss(UUID caster) {
        Legion legion = LEGIONS.remove(caster);
        if (legion == null) return;
        for (Spirit s : legion.spirits) {
            Entity e = legion.level.getEntity(s.id);
            if (e != null) discard(legion.level, e, legion);
            LIVE.remove(s.id);
        }
        legion.spirits.clear();
    }

    private static void discard(ServerLevel level, Entity spirit, @Nullable Legion legion) {
        LIVE.remove(spirit.getUUID());
        if (legion != null) level.sendParticles(ParticleTypes.SOUL, spirit.getX(), spirit.getY() + 0.3D, spirit.getZ(), 4, 0.15D, 0.2D, 0.15D, 0.02D);
        spirit.discard();
    }

    /** Untracked spirits (saved before a restart, or whose legion ended while unloaded) never come back. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() && isSpirit(e.getEntity()) && !LIVE.contains(e.getEntity().getUUID())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre e) {
        Entity en = e.getEntity();
        if (en instanceof Vex && !en.level().isClientSide() && isSpirit(en) && !LIVE.contains(en.getUUID())) en.discard();
    }

    @SubscribeEvent
    public static void onDrops(LivingDropsEvent e) { if (isSpirit(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent
    public static void onXp(LivingExperienceDropEvent e) { if (isSpirit(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { dismiss(e.getEntity().getUUID()); }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        for (UUID id : new ArrayList<>(LEGIONS.keySet())) dismiss(id);
        LIVE.clear();
    }

    // ------------------------------------------------------------------ state

    private static final class Spirit {
        final UUID id;
        final int formation;
        int attackDelay;
        Spirit(UUID id, int formation) { this.id = id; this.formation = formation; this.attackDelay = 10 + formation * 5; }
    }

    private static final class Legion {
        final ServerLevel level;
        final long expiresAt;
        final List<Spirit> spirits = new ArrayList<>();
        final DustParticleOptions dust, trail;
        Legion(ServerLevel level, long expiresAt, StoneTree.Node n) {
            this.level = level;
            this.expiresAt = expiresAt;
            int c = n.stone().color();
            this.dust = PortFx.dust(c, 0.7F);
            this.trail = PortFx.dust(SpatialExchange.lighten(c, 0.25F), 0.7F);
        }
    }
}
