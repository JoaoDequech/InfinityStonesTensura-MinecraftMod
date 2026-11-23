package com.infinitystones.tensura.skill.ability.extra;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Legacy engine kept for the Gauntlet "Absolute Stop" (StolenMoment.freeze); not a wheel node any more.
 *
 * Time — Stolen Moment: for 3 s everything within 12 blocks except you (and your allies) stands still — mobs freeze,
 * projectiles hang in the air (yours too, once they leave your hand), enemy players (PvP) can't move or act.
 * Damage you deal to frozen targets is stored and lands all at once when time resumes.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class StolenMoment {
    static final class Frozen {
        final Vec3 pos, vel; final boolean noAi, noGravity;
        Frozen(Entity e) {
            pos = e.position(); vel = e.getDeltaMovement(); noGravity = e.isNoGravity();
            noAi = e instanceof Mob m && m.isNoAi();
        }
    }

    /** Gauntlet "Absolute Stop": the same moment, any radius / length. @return how many things stopped. */
    public static int freeze(ServerPlayer p, double radius, int ticks) {
        ServerLevel l = p.serverLevel();
        Effects.killOwned(Moment.class, p.getUUID());
        Moment m = Effects.add(new Moment(l, p.getUUID(), p.position(), radius, ticks));
        m.capture(p);
        return m.frozen.size();
    }

    public static boolean press(ServerPlayer p) {
        if (Effects.ofOwner(Moment.class, p.getUUID()) != null) return Fx.fail(p, "infinity.extra.moment.active");
        ServerLevel l = p.serverLevel();
        Moment m = Effects.add(new Moment(l, p.getUUID(), p.position(), 12 * 1F, (int) (60 * 1F)));
        m.capture(p);
        Fx.sound(l, p.position(), SoundEvents.BELL_BLOCK, 0.7F, 0.5F);
        Fx.sound(l, p.position(), SoundEvents.BEACON_DEACTIVATE, 0.6F, 0.6F);
        Fx.bar(p, "infinity.extra.moment.on", m.frozen.size());
        return true;
    }

    static final class Moment extends Effects.Zone {
        final Map<Entity, Frozen> frozen = new HashMap<>();
        final Map<LivingEntity, Float> stored = new HashMap<>();
        Moment(ServerLevel l, UUID o, Vec3 c, double r, int dur) { super(l, o, dur, c, r); }

        void capture(ServerPlayer p) {
            for (Entity e : level.getEntities(p, box(), e -> contains(e.position()))) consider(p, e);
        }

        private void consider(ServerPlayer p, Entity e) {
            if (frozen.containsKey(e) || e.isSpectator() || Rules.isBoss(e)) return;
            if (e instanceof LivingEntity) { if (!Rules.canHarm(p, e)) return; }
            else if (e instanceof Projectile pr) { if (pr.getOwner() == p && pr.distanceTo(p) < 1.5) return; }
            Frozen f = new Frozen(e);
            frozen.put(e, f);
            if (e instanceof Mob m) m.setNoAi(true);
            if (!(e instanceof Player)) e.setNoGravity(true);
            e.setDeltaMovement(Vec3.ZERO);
        }

        @Override protected void tick() {
            ServerPlayer o = ownerPlayer();
            if (o != null && age % 2 == 0) for (Projectile pr : level.getEntitiesOfClass(Projectile.class, box(), e -> contains(e.position()))) consider(o, pr);
            if (age % 3 == 0) {
                double r = radius * Math.min(1, (age + 1) / 6.0);
                Fx.ring(level, center.add(0, 0.2, 0), r, (int) (r * 5), Fx.dust(Stone.TIME, 1.2F));
            }
            for (var en : frozen.entrySet()) {
                Entity e = en.getKey();
                if (!e.isAlive()) continue;
                Vec3 at = en.getValue().pos;
                if (e instanceof ServerPlayer sp) { if (sp.position().distanceToSqr(at) > 0.01) sp.connection.teleport(at.x, at.y, at.z, sp.getYRot(), sp.getXRot()); }
                else e.setPos(at);
                e.setDeltaMovement(Vec3.ZERO);
                e.hurtMarked = true;
                if (age % 10 == 0) level.sendParticles(Fx.light(Stone.TIME, 0.6F), e.getX(), e.getY() + e.getBbHeight() + 0.3, e.getZ(), 2, 0.15, 0.05, 0.15, 0);
            }
        }

        @Override protected void onEnd() {
            ServerPlayer o = ownerPlayer();
            for (var en : frozen.entrySet()) {
                Entity e = en.getKey();
                Frozen f = en.getValue();
                if (e instanceof Mob m) m.setNoAi(f.noAi);
                if (!(e instanceof Player)) e.setNoGravity(f.noGravity);
                if (e instanceof Projectile) e.setDeltaMovement(f.vel.lengthSqr() > 0.0001 ? f.vel : e.getDeltaMovement());
                e.hurtMarked = true;
            }
            for (var en : stored.entrySet()) {
                LivingEntity v = en.getKey();
                if (!v.isAlive()) continue;
                v.invulnerableTime = 0;
                releasing = true;
                try { v.hurt(o != null ? o.damageSources().playerAttack(o) : level.damageSources().magic(), en.getValue()); }
                finally { releasing = false; }
                level.sendParticles(ParticleTypes.CRIT, v.getX(), v.getY() + v.getBbHeight() / 2, v.getZ(), 20, 0.3, 0.3, 0.3, 0.2);
            }
            Fx.sound(level, center, SoundEvents.BELL_BLOCK, 0.7F, 1.2F);
            Fx.shell(level, center.add(0, 1, 0), radius * 0.5, 50, Fx.light(Stone.TIME, 1.0F));
        }

        boolean isFrozen(Entity e) { return frozen.containsKey(e); }
    }

    private static boolean releasing;

    private static Moment holding(Entity e) {
        for (Moment m : Effects.all(Moment.class)) if (m.isFrozen(e)) return m;
        return null;
    }

    @SubscribeEvent
    public static void damage(LivingIncomingDamageEvent event) {
        if (releasing || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        Moment m = holding(event.getEntity());
        if (m == null) return;
        Entity attacker = event.getSource().getEntity();
        if (attacker == null || !attacker.getUUID().equals(m.owner)) return;
        m.stored.merge(event.getEntity(), event.getAmount(), Float::sum);
        event.setCanceled(true);
        ServerLevel l = m.level;
        LivingEntity v = event.getEntity();
        l.sendParticles(Fx.dust(Stone.TIME, 1.0F), v.getX(), v.getY() + v.getBbHeight() / 2, v.getZ(), 8, 0.3, 0.3, 0.3, 0);
    }

    @SubscribeEvent
    public static void attack(AttackEntityEvent event) { if (holding(event.getEntity()) != null) event.setCanceled(true); }

    @SubscribeEvent
    public static void useItem(PlayerInteractEvent.RightClickItem event) { if (holding(event.getEntity()) != null) event.setCanceled(true); }

    @SubscribeEvent
    public static void useBlock(PlayerInteractEvent.RightClickBlock event) { if (holding(event.getEntity()) != null) event.setCanceled(true); }

    private StolenMoment() {}
}
