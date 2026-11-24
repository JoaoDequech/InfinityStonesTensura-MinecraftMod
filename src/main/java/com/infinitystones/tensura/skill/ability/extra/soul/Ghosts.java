package com.infinitystones.tensura.skill.ability.extra.soul;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Spectral allies: a real mob of the wanted type, glowing, with its own AI removed and replaced by a small brain run
 * from a task (follow the owner, fight the owner's enemies, optionally replay a recorded path first).
 * They never drop anything, never hurt the owner or allies, vanish when the time is up, the owner dies / logs out or
 * the server restarts. Kills they make count as the owner's (loot and experience).
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class Ghosts {
    public static final int ARMY = 0, ECHO = 1;
    public static final int CAP_ARMY = 8, CAP_ECHO = 2;
    private static final String TAG_OWNER = "infinity_ghost_owner";

    /** One recorded position of a creature (Past Echo replays these). */
    public record Sample(double x, double y, double z, float yaw, float pitch) {}

    public static final class Ghost {
        final Mob mob;
        final UUID owner;
        final int kind, life, color;
        final float strike;
        final int crown = FxNet.newId();
        int age, attackCd, navCd, scanCd;
        @Nullable LivingEntity target;
        @Nullable List<Sample> path;
        float pathPos, pathSpeed;
        Ghost(Mob mob, UUID owner, int kind, int life, float strike, int color) {
            this.mob = mob; this.owner = owner; this.kind = kind; this.life = life; this.strike = strike; this.color = color;
        }
        public Mob mob() { return mob; }
        public void expireNow() { age = life; }
    }

    private static final Map<UUID, List<Ghost>> BY_OWNER = new HashMap<>();
    private static final Set<UUID> TRACKED = new HashSet<>();

    public static boolean isGhost(@Nullable Entity e) { return e != null && e.getPersistentData().hasUUID(TAG_OWNER); }

    public static @Nullable UUID ownerOf(@Nullable Entity e) {
        return e != null && e.getPersistentData().hasUUID(TAG_OWNER) ? e.getPersistentData().getUUID(TAG_OWNER) : null;
    }

    public static int count(UUID owner, int kind) {
        int n = 0;
        for (Ghost g : BY_OWNER.getOrDefault(owner, List.of())) if (g.kind == kind && g.age < g.life) n++;
        return n;
    }

    /** Types that would grief, split, teleport or need a brain of their own: a husk stands in. */
    private static boolean usable(EntityType<?> t) {
        if (t == EntityType.CREEPER || t == EntityType.ENDERMAN || t == EntityType.GHAST || t == EntityType.PHANTOM
                || t == EntityType.SLIME || t == EntityType.MAGMA_CUBE || t == EntityType.SHULKER || t == EntityType.GUARDIAN
                || t == EntityType.ELDER_GUARDIAN || t == EntityType.ENDERMITE || t == EntityType.SILVERFISH
                || t == EntityType.EVOKER || t == EntityType.RAVAGER || t == EntityType.PLAYER || t == EntityType.ARMOR_STAND
                || t == EntityType.WARDEN || t == EntityType.ENDER_DRAGON || t == EntityType.WITHER) return false;
        return t.canSummon();
    }

    /**
     * Raises a ghost. hp/strike are final values. path (optional) is replayed first at pathSpeed samples per tick.
     * Returns null when nothing could be spawned.
     */
    public static @Nullable Ghost spawn(ServerPlayer owner, EntityType<?> wanted, Vec3 pos, float yaw, int kind, int life,
                                        float hp, float strike, int color, @Nullable List<Sample> path, float pathSpeed) {
        ServerLevel l = owner.serverLevel();
        List<Ghost> mine = BY_OWNER.computeIfAbsent(owner.getUUID(), k -> new ArrayList<>());
        int cap = kind == ECHO ? CAP_ECHO : CAP_ARMY;
        // each pass retires one live ghost of this kind; stop when none is left to retire (the loop must never spin)
        for (int guard = 0; guard < cap + 8 && count(owner.getUUID(), kind) >= cap; guard++) {
            boolean retired = false;
            for (Ghost g : mine) if (g.kind == kind && g.age < g.life) { g.expireNow(); retired = true; break; }
            if (!retired) break;
        }
        Mob m = create(l, wanted);
        boolean stand = m.getType() == EntityType.HUSK;
        m.moveTo(pos.x, pos.y, pos.z, yaw, 0);
        m.setYHeadRot(yaw);
        m.getPersistentData().putUUID(TAG_OWNER, owner.getUUID());
        m.goalSelector.removeAllGoals(g -> true);
        m.targetSelector.removeAllGoals(g -> true);
        m.setTarget(null);
        m.setPersistenceRequired();
        for (EquipmentSlot s : EquipmentSlot.values()) m.setDropChance(s, 0F);
        AttributeInstance maxHp = m.getAttribute(Attributes.MAX_HEALTH);
        if (maxHp != null) maxHp.setBaseValue(hp);
        m.setHealth(hp);
        AttributeInstance speed = m.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(Math.max(speed.getBaseValue() * 1.25, 0.26));
        if (stand) m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        m.addEffect(new MobEffectInstance(MobEffects.GLOWING, life + 40, 0, false, false));
        m.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, life + 40, 0, false, false));
        m.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, life + 40, 0, false, false));
        m.setCustomName(Component.translatable("infinity.extra.soul.ghost", m.getType().getDescription()).withColor(color));
        m.setCustomNameVisible(false);
        TRACKED.add(m.getUUID());
        if (!l.addFreshEntity(m)) { TRACKED.remove(m.getUUID()); return null; }
        Ghost g = new Ghost(m, owner.getUUID(), kind, life, strike, color);
        if (path != null && path.size() > 2) { g.path = path; g.pathSpeed = pathSpeed; }
        mine.add(g);
        FxNet.flare(l, pos.add(0, m.getBbHeight() / 2, 0), 1.6F + m.getBbWidth(), color, 12);
        Fx.at(l, ParticleTypes.SOUL, pos.add(0, m.getBbHeight() / 2, 0), 12, 0.3, 0.08);
        Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) { return Ghosts.tick(g); }
            @Override public void end() { Ghosts.finish(g); }
        });
        return g;
    }

    private static Mob create(ServerLevel l, EntityType<?> wanted) {
        if (usable(wanted)) {
            Entity e = wanted.create(l);
            if (e instanceof Mob m && !Rules.isBoss(m) && m.getBrain().getActiveActivities().isEmpty()) return m;
            if (e != null) e.discard();
        }
        return (Mob) EntityType.HUSK.create(l);
    }

    // ------------------------------------------------------------------ per tick

    private static boolean tick(Ghost g) {
        Mob m = g.mob;
        if (!m.isAlive() || m.isRemoved() || !(m.level() instanceof ServerLevel l) || m.getServer() == null) return false;
        ServerPlayer owner = m.getServer().getPlayerList().getPlayer(g.owner);
        if (owner == null || !owner.isAlive() || owner.level() != l) return false;
        if (++g.age > g.life) return false;
        m.setRemainingFireTicks(0);
        m.setAirSupply(m.getMaxAirSupply());
        if (g.age % 3 == 0) {
            Vec3 body = m.position().add(0, m.getBbHeight() * 0.5, 0);
            Fx.at(l, Fx.dust(g.color, 1.0F), body, 2, m.getBbWidth() * 0.4, 0);
            if (g.age % 6 == 0) Fx.at(l, ParticleTypes.SOUL, body, 1, m.getBbWidth() * 0.3, 0.02);
        }
        if (g.age % 40 == 1) FxNet.crown(l, g.crown, m, g.color, 60);
        if (g.life - g.age == 40) Fx.at(l, ParticleTypes.SOUL_FIRE_FLAME, m.position().add(0, m.getBbHeight() * 0.5, 0), 10, 0.3, 0.03);

        if (g.path != null) { replay(g, l, owner); return true; }
        if (g.attackCd > 0) g.attackCd--;
        if (g.navCd > 0) g.navCd--;
        if (--g.scanCd <= 0 || !valid(g, owner, g.target)) {
            g.scanCd = 8;
            g.target = valid(g, owner, g.target) ? g.target : pick(g, owner, l);
        }
        if (g.target != null) fight(g, l, owner, g.target, g.strike);
        else follow(g, l, owner);
        return true;
    }

    private static void finish(Ghost g) {
        Mob m = g.mob;
        List<Ghost> mine = BY_OWNER.get(g.owner);
        if (mine != null) { mine.remove(g); if (mine.isEmpty()) BY_OWNER.remove(g.owner); }
        TRACKED.remove(m.getUUID());
        if (m.level() instanceof ServerLevel l) {
            Vec3 c = m.position().add(0, m.getBbHeight() / 2, 0);
            FxNet.kill(l, m.position(), g.crown);
            SoulKit.soulBurst(l, c, 0.8F);
            FxNet.shockwave(l, m.position().add(0, 0.1, 0), 2.2F, g.color, 12);
        }
        if (!m.isRemoved()) m.discard();
    }

    private static boolean valid(Ghost g, ServerPlayer owner, @Nullable LivingEntity t) {
        if (t == null || !t.isAlive() || t.isRemoved() || t.level() != g.mob.level() || t.distanceToSqr(g.mob) > 40 * 40) return false;
        return Rules.canHarm(owner, t);
    }

    private static @Nullable LivingEntity pick(Ghost g, ServerPlayer owner, ServerLevel l) {
        LivingEntity a = owner.getLastHurtMob();
        if (a != null && owner.tickCount - owner.getLastHurtMobTimestamp() < 160 && valid(g, owner, a)) return a;
        LivingEntity b = owner.getLastHurtByMob();
        if (b != null && owner.tickCount - owner.getLastHurtByMobTimestamp() < 160 && valid(g, owner, b)) return b;
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, g.mob.getBoundingBox().inflate(16, 6, 16),
                e -> e.isAlive() && e != g.mob && !(e instanceof ServerPlayer) && Rules.canHarm(owner, e))) {
            boolean hostile = e instanceof Enemy || e instanceof Mob em && em.getTarget() != null && Rules.isAlly(owner, em.getTarget());
            if (!hostile) continue;
            double d = e.distanceToSqr(g.mob);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    private static void follow(Ghost g, ServerLevel l, ServerPlayer owner) {
        Mob m = g.mob;
        double d2 = m.distanceToSqr(owner);
        if (d2 > 24 * 24) { teleportNear(g, l, owner); return; }
        if (d2 > 25) {
            if (g.navCd <= 0) {
                g.navCd = 8;
                if (!m.getNavigation().moveTo(owner, d2 > 100 ? 1.5 : 1.2)) m.getMoveControl().setWantedPosition(owner.getX(), owner.getY(), owner.getZ(), 1.3);
            }
            m.getLookControl().setLookAt(owner, 10, m.getMaxHeadXRot());
        } else if (d2 < 9) m.getNavigation().stop();
    }

    private static void teleportNear(Ghost g, ServerLevel l, ServerPlayer owner) {
        Mob m = g.mob;
        for (int i = 0; i < 12; i++) {
            double a = l.random.nextDouble() * Math.PI * 2, r = 2 + l.random.nextDouble() * 2;
            Vec3 v = Ground.below(l, owner.position().add(Math.cos(a) * r, 1.5, Math.sin(a) * r), 4);
            if (Ground.fits(l, m, v) && Math.abs(v.y - owner.getY()) < 3) {
                FxNet.flare(l, m.position().add(0, m.getBbHeight() / 2, 0), 1.4F, g.color, 10);
                m.moveTo(v.x, v.y, v.z, m.getYRot(), m.getXRot());
                m.getNavigation().stop();
                m.fallDistance = 0;
                FxNet.flare(l, v.add(0, m.getBbHeight() / 2, 0), 1.4F, g.color, 10);
                Fx.sound(l, v, SoundEvents.SOUL_ESCAPE.value(), 0.3F, 1.3F);
                return;
            }
        }
    }

    private static void fight(Ghost g, ServerLevel l, ServerPlayer owner, LivingEntity t, float dmg) {
        Mob m = g.mob;
        if (m.getTarget() != t) m.setTarget(t);
        m.getLookControl().setLookAt(t, 30, 30);
        double d2 = m.distanceToSqr(t);
        if (m instanceof RangedAttackMob ranged && d2 < 15 * 15 && m.getSensing().hasLineOfSight(t)) {
            m.getNavigation().stop();
            if (g.attackCd <= 0) { g.attackCd = 30; ranged.performRangedAttack(t, 1F); }
            return;
        }
        if (!m.isWithinMeleeAttackRange(t)) {
            if (g.navCd <= 0 || !m.getNavigation().isInProgress()) {
                g.navCd = 5;
                if (!m.getNavigation().moveTo(t, 1.4)) m.getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), 1.4);
            }
            return;
        }
        m.getNavigation().stop();
        if (g.attackCd > 0) return;
        g.attackCd = 16;
        strike(g, l, owner, t, dmg);
    }

    private static void strike(Ghost g, ServerLevel l, ServerPlayer owner, LivingEntity t, float dmg) {
        Mob m = g.mob;
        m.swing(InteractionHand.MAIN_HAND);
        t.setLastHurtByPlayer(owner);
        DamageSource src = m.damageSources().mobAttack(m);
        if (t.hurt(src, dmg)) t.knockback(0.5, m.getX() - t.getX(), m.getZ() - t.getZ());
        Vec3 hit = t.position().add(0, t.getBbHeight() * 0.6, 0);
        Fx.at(l, Fx.dust(g.color, 1.3F), hit, 8, 0.25, 0);
        Fx.at(l, ParticleTypes.SOUL_FIRE_FLAME, hit, 3, 0.2, 0.04);
        Fx.at(l, ParticleTypes.CRIT, hit, 4, 0.2, 0.2);
    }

    /** The recorded path is walked at pathSpeed samples per tick; the ghost slashes whatever it passes. */
    private static void replay(Ghost g, ServerLevel l, ServerPlayer owner) {
        Mob m = g.mob;
        List<Sample> path = g.path;
        g.pathPos += g.pathSpeed;
        int i = (int) g.pathPos;
        if (path == null || i >= path.size() - 1) {
            g.path = null;
            Vec3 c = m.position().add(0, m.getBbHeight() / 2, 0);
            FxNet.shockwave(l, m.position().add(0, 0.1, 0), 3.5F, g.color, 14);
            FxNet.flare(l, c, 2.2F, g.color, 10);
            Fx.sound(l, c, SoundEvents.BELL_RESONATE, 0.4F, 1.5F);
            return;
        }
        Sample a = path.get(i), b = path.get(i + 1);
        float f = g.pathPos - i;
        double x = Mth.lerp(f, a.x(), b.x()), y = Mth.lerp(f, a.y(), b.y()), z = Mth.lerp(f, a.z(), b.z());
        float yaw = Mth.rotLerp(f, a.yaw(), b.yaw());
        m.moveTo(x, y, z, yaw, Mth.lerp(f, a.pitch(), b.pitch()));
        m.setYHeadRot(yaw);
        m.setDeltaMovement(Vec3.ZERO);
        m.fallDistance = 0;
        if (g.attackCd > 0) g.attackCd--;
        if (g.attackCd <= 0) {
            LivingEntity t = null;
            double bd = 3.6 * 3.6;
            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(3.6), e -> e != m && Rules.canHarm(owner, e) && !(e instanceof ServerPlayer ps && ps == owner))) {
                double d = e.distanceToSqr(m);
                if (d < bd) { bd = d; t = e; }
            }
            if (t != null) {
                g.attackCd = 5;
                strike(g, l, owner, t, g.strike * 1.5F);
                FxNet.beam(l, m.position().add(0, m.getBbHeight() * 0.7, 0), t.position().add(0, t.getBbHeight() * 0.5, 0), g.color, 0.1F, 6);
            }
        }
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void join(EntityJoinLevelEvent e) {
        Entity en = e.getEntity();
        if (e.getLevel().isClientSide() || !isGhost(en)) return;
        if (!TRACKED.contains(en.getUUID())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void drops(LivingDropsEvent e) { if (isGhost(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent
    public static void xp(LivingExperienceDropEvent e) { if (isGhost(e.getEntity())) e.setCanceled(true); }

    /** Ghosts take no damage from their owner or the owner's allies. */
    @SubscribeEvent
    public static void damage(LivingIncomingDamageEvent e) {
        UUID id = ownerOf(e.getEntity());
        if (id == null) return;
        Entity src = e.getSource().getEntity();
        if (src == null) return;
        if (src.getUUID().equals(id) || src instanceof LivingEntity && id.equals(ownerOf(src))) { e.setCanceled(true); return; }
        if (e.getEntity().getServer() != null) {
            ServerPlayer owner = e.getEntity().getServer().getPlayerList().getPlayer(id);
            if (owner != null && Rules.isAlly(owner, src)) e.setCanceled(true);
        }
    }

    private Ghosts() {}
}
