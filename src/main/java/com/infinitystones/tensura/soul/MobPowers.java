package com.infinitystones.tensura.soul;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Native attacks of a possessed mob, fired in the direction the possessor looks. */
final class MobPowers {
    static boolean flies(Mob m) {
        return m instanceof FlyingMob || m instanceof FlyingAnimal || m instanceof EnderDragon || m instanceof WitherBoss
                || m instanceof Blaze || m instanceof Vex || m instanceof Phantom || m instanceof net.minecraft.world.entity.ambient.Bat
                || m.isNoGravity();
    }

    /** Left click: the mob's melee hit on whatever it looks at. */
    static void melee(@Nullable ServerPlayer p, Mob m) {
        m.swing(InteractionHand.MAIN_HAND, true);
        double reach = m.getBbWidth() * 0.5 + 3.0;
        HitResult hr = Possession.pick(m, reach, reach);
        if (!(hr instanceof EntityHitResult eh) || eh.getEntity() == p) return;
        Entity t = eh.getEntity();
        if (m.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)) m.doHurtTarget(t);
        else t.hurt(m.damageSources().mobAttack(m), 2F + m.getBbWidth() * 2F);
        if (m instanceof Creeper c) c.ignite();
    }

    /** Right click (secondary=false) / middle click (secondary=true): the mob's special power. */
    static void ability(@Nullable ServerPlayer p, Mob m, boolean secondary) {
        if (!(m.level() instanceof ServerLevel l)) return;
        Vec3 look = m.getViewVector(1F).normalize();
        Vec3 eye = m.getEyePosition();
        LivingEntity aimed = aimed(m, 48);
        Vec3 aimPoint = aimed != null ? aimed.getBoundingBox().getCenter() : eye.add(look.scale(24));
        m.swing(InteractionHand.MAIN_HAND, true);

        if (m instanceof Creeper c) {
            if (secondary) { c.setSwellDir(-1); return; }
            c.ignite();
            return;
        }
        if (m instanceof Ghast g) {
            LargeFireball fb = new LargeFireball(l, g, look, secondary ? 3 : g.getExplosionPower());
            fb.setPos(eye.add(look.scale(2.5)));
            l.addFreshEntity(fb);
            l.playSound(null, g.blockPosition(), SoundEvents.GHAST_SHOOT, g.getSoundSource(), 3F, 1F);
            return;
        }
        if (m instanceof Blaze) {
            for (int i = 0; i < (secondary ? 8 : 3); i++) {
                Vec3 dir = look.add(l.random.triangle(0, 0.08), l.random.triangle(0, 0.08), l.random.triangle(0, 0.08));
                SmallFireball fb = new SmallFireball(l, m, dir.normalize());
                fb.setPos(eye.add(look.scale(1.2)));
                l.addFreshEntity(fb);
            }
            l.playSound(null, m.blockPosition(), SoundEvents.BLAZE_SHOOT, m.getSoundSource(), 1.5F, 1F);
            return;
        }
        if (m instanceof EnderDragon d) {
            if (secondary) { roar(l, m, 12, 2.5); l.playSound(null, d.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, d.getSoundSource(), 5F, 1F); return; }
            DragonFireball fb = new DragonFireball(l, d, look);
            fb.setPos(eye.add(look.scale(6)));
            l.addFreshEntity(fb);
            l.playSound(null, d.blockPosition(), SoundEvents.ENDER_DRAGON_SHOOT, d.getSoundSource(), 4F, 1F);
            return;
        }
        if (m instanceof WitherBoss w) {
            WitherSkull sk = new WitherSkull(l, w, look);
            sk.setPos(eye.add(look.scale(2)));
            if (secondary) sk.setDangerous(true);
            l.addFreshEntity(sk);
            l.playSound(null, w.blockPosition(), SoundEvents.WITHER_SHOOT, w.getSoundSource(), 2F, 1F);
            return;
        }
        if (m instanceof Warden) {
            if (secondary) { roar(l, m, 8, 1.8); l.playSound(null, m.blockPosition(), SoundEvents.WARDEN_ROAR, m.getSoundSource(), 3F, 1F); return; }
            for (int i = 1; i < 20; i++) l.sendParticles(ParticleTypes.SONIC_BOOM, eye.x + look.x * i, eye.y + look.y * i, eye.z + look.z * i, 1, 0, 0, 0, 0);
            for (LivingEntity t : l.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().expandTowards(look.scale(20)).inflate(1.5),
                    t -> t != m && t != p && !Possession.isGhost(t) && onRay(eye, look, t, 20)))
                t.hurt(l.damageSources().sonicBoom(m), com.infinitystones.tensura.skill.ability.extra.Rules.amount(t, 10F, 0.15F, false));
            l.playSound(null, m.blockPosition(), SoundEvents.WARDEN_SONIC_BOOM, m.getSoundSource(), 3F, 1F);
            return;
        }
        if (m instanceof Guardian) {
            if (aimed != null) {
                SoulFx.line(l, eye, aimed.getEyePosition(), 0.3, ParticleTypes.BUBBLE, null);
                aimed.hurt(l.damageSources().indirectMagic(m, m), com.infinitystones.tensura.skill.ability.extra.Rules.amount(aimed, m instanceof ElderGuardian ? 8F : 6F, m instanceof ElderGuardian ? 0.05F : 0.04F, false));
            }
            l.playSound(null, m.blockPosition(), SoundEvents.GUARDIAN_ATTACK, m.getSoundSource(), 1.5F, 1F);
            return;
        }
        if (m instanceof Shulker) {
            if (aimed != null) l.addFreshEntity(new ShulkerBullet(l, m, aimed, Direction.Axis.Y));
            l.playSound(null, m.blockPosition(), SoundEvents.SHULKER_SHOOT, m.getSoundSource(), 2F, 1F);
            return;
        }
        if (m instanceof Evoker) {
            if (secondary) {
                for (int i = 0; i < 3; i++) {
                    Vex v = EntityType.VEX.create(l);
                    if (v == null) continue;
                    v.moveTo(m.getX() + l.random.triangle(0, 2), m.getY() + 1, m.getZ() + l.random.triangle(0, 2), 0, 0);
                    v.setOwner(m);
                    l.addFreshEntity(v);
                }
                l.playSound(null, m.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, m.getSoundSource(), 2F, 1F);
                return;
            }
            float yaw = (float) Mth.atan2(look.z, look.x);
            for (int i = 1; i <= 16; i++) {
                Vec3 at = m.position().add(look.x * i * 1.25, 0, look.z * i * 1.25);
                l.addFreshEntity(new EvokerFangs(l, at.x, m.getY(), at.z, yaw, i, m));
            }
            l.playSound(null, m.blockPosition(), SoundEvents.EVOKER_CAST_SPELL, m.getSoundSource(), 2F, 1F);
            return;
        }
        if (m instanceof EnderMan) {
            Vec3 to = Possession.pick(m, 48, 0).getLocation().subtract(look);
            m.teleportTo(to.x, to.y, to.z);
            l.playSound(null, m.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, m.getSoundSource(), 1.5F, 1F);
            return;
        }
        if (m instanceof Ravager) { roar(l, m, 6, 1.5); l.playSound(null, m.blockPosition(), SoundEvents.RAVAGER_ROAR, m.getSoundSource(), 2F, 1F); return; }
        if (m instanceof Slime s) { m.setDeltaMovement(look.x * 0.8, 0.6 + s.getSize() * 0.1, look.z * 0.8); return; }
        if (!secondary && m instanceof RangedAttackMob r) {
            LivingEntity target = aimed;
            if (target == null) { ArmorStand dummy = new ArmorStand(l, aimPoint.x, aimPoint.y - 1, aimPoint.z); target = dummy; }
            try { r.performRangedAttack(target, 1F); } catch (RuntimeException ignored) { }
            return;
        }
        if (secondary) {
            m.playAmbientSound();
            roar(l, m, 5, 1.2);
            return;
        }
        // generic: pounce forward and strike what is ahead
        m.setDeltaMovement(look.x * 1.1, Math.max(0.35, look.y * 0.6 + 0.3), look.z * 1.1);
        m.hasImpulse = true;
        melee(p, m);
    }

    private static @Nullable LivingEntity aimed(Mob m, double range) {
        HitResult hr = Possession.pick(m, range, range);
        return hr instanceof EntityHitResult eh && eh.getEntity() instanceof LivingEntity le ? le : null;
    }

    private static boolean onRay(Vec3 eye, Vec3 dir, Entity t, double range) {
        Vec3 d = t.getBoundingBox().getCenter().subtract(eye);
        double proj = d.dot(dir);
        return proj > 0 && proj < range && d.lengthSqr() - proj * proj < 2.5 * 2.5;
    }

    /** Shockwave around the mob in soul colours. */
    private static void roar(ServerLevel l, Mob m, double r, double force) {
        for (LivingEntity t : l.getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(r), t -> t != m && !Possession.isGhost(t))) {
            Vec3 d = t.position().subtract(m.position()).normalize();
            t.push(d.x * force, 0.5, d.z * force);
            t.hurtMarked = true;
        }
        SoulFx.ring(l, m.position().add(0, 0.2, 0), r * 0.5, 30, 0, SoulFx.D_ORANGE, null);
        SoulFx.ring(l, m.position().add(0, 0.2, 0), r, 50, 0.5, SoulFx.D_EMBER, null);
    }

    private MobPowers() {}
}
