package com.infinitystones.tensura.skill.ability.extra.mind;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Displays;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Priv;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Phantasm. Left: a double of you (your head, armour and weapon) runs the way you look; mobs within 24 blocks switch
 * to it and you turn invisible (armour hidden too) for 8 s. Whoever hits the double makes it shatter into golden
 * shards that blind and nauseate the attacker. Right: detonate the double (blindness + nausea in 5 blocks).
 * The double is an illusion: its items are marked copies that can never drop.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class Phantasm implements Ability {
    public static final Phantasm INSTANCE = new Phantasm();
    private static final int LIFE = 200, HIDE = 160;
    private static final double LURE = 24;

    @Override public Stone stone() { return Stone.MIND; }

    private static final class Decoy {
        final UUID owner;
        final ArmorStand stand;
        Vec3 dir;
        Tasks.Handle task;
        final int glowId = FxNet.newId();
        Decoy(UUID owner, ArmorStand stand, Vec3 dir) { this.owner = owner; this.stand = stand; this.dir = dir; }
    }

    private static final Map<UUID, Decoy> BY_OWNER = new HashMap<>();
    private static final Map<Entity, Decoy> BY_STAND = new HashMap<>();

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        Decoy cur = BY_OWNER.get(p.getUUID());
        if (alt) {
            if (cur == null || cur.stand.isRemoved()) return Fx.fail(p, "infinity.extra.mind.phantasm.none");
            shatter(cur, null, true);
            return true;
        }
        if (cur != null) end(cur, false);
        ServerLevel l = p.serverLevel();
        Vec3 dir = p.getLookAngle().multiply(1, 0, 1);
        if (dir.lengthSqr() < 1.0E-4) dir = new Vec3(0, 0, 1);
        dir = dir.normalize();
        float yaw = p.getYRot();
        ArmorStand s = MindKit.body(p, p.position(), yaw, false, true);
        if (s == null) return Fx.fail(p, "infinity.extra.mind.phantasm.none");
        Decoy d = new Decoy(p.getUUID(), s, dir);
        BY_OWNER.put(p.getUUID(), d);
        BY_STAND.put(s, d);

        // the caster vanishes
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, HIDE, 0, false, false, true));
        MindKit.hideEquipment(p, HIDE);
        lure(p, d);

        // split FX: afterimage burst at the swap point
        Vec3 c = p.position().add(0, 1, 0);
        FxNet.flash(p, MindKit.GOLD, 0.3F, 8);
        FxNet.shockwave(l, p.position().add(0, 0.05, 0), 3.5F, MindKit.GOLD, 16);
        FxNet.spiral(l, p.position(), 0.8F, 2.4F, MindKit.PALE, 18, -1);
        FxNet.flare(l, c, 1.6F, MindKit.PALE, 10);
        Fx.at(l, net.minecraft.core.particles.ParticleTypes.POOF, c, 12, 0.3, 0.02);
        MindKit.spark(l, c, 20);
        Fx.sound(l, c, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.7F, 1.1F);
        Fx.sound(l, c, SoundEvents.ILLUSIONER_PREPARE_MIRROR, 0.5F, 1.4F);

        d.task = Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) {
                if (s.isRemoved() || !s.isAlive()) return false;
                ServerPlayer owner = l.getServer().getPlayerList().getPlayer(d.owner);
                if (owner == null || owner.level() != l) return false;
                run(d, age);
                if (age % 10 == 0 && age < HIDE) lure(owner, d);
                if (age % 20 == 0) Priv.glow(owner, s, true); // only the caster sees which one is fake
                mobHits(owner, d);
                return age < LIFE;
            }
            @Override public void end() { if (BY_OWNER.get(d.owner) == d) Phantasm.end(d, true); }
        });
        Fx.bar(p, Stone.MIND, "infinity.extra.mind.phantasm.out");
        return true;
    }

    private static void run(Decoy d, int age) {
        ArmorStand s = d.stand;
        ServerLevel l = (ServerLevel) s.level();
        if (s.horizontalCollision) {
            if (s.onGround()) s.setDeltaMovement(s.getDeltaMovement().x, 0.45, s.getDeltaMovement().z);
            if (age % 20 == 10) d.dir = d.dir.yRot((float) (l.random.nextBoolean() ? Math.PI / 3 : -Math.PI / 3));
        }
        Vec3 v = d.dir.scale(0.26);
        s.setDeltaMovement(v.x, s.getDeltaMovement().y, v.z);
        float yaw = (float) (Math.atan2(d.dir.z, d.dir.x) * 180 / Math.PI) - 90F;
        s.setYRot(yaw);
        s.setYBodyRot(yaw);
        s.setYHeadRot(yaw);
        if (age % 2 == 0) MindKit.runPose(s, age);
        if (age % 3 == 0) Fx.at(l, Fx.light(MindKit.MIND, 0.4F), s.position().add(0, 0.2, 0), 1, 0.15, 0); // faint, easy to miss
    }

    /** Mobs near the caster/double switch to the double. */
    private static void lure(ServerPlayer p, Decoy d) {
        ServerLevel l = p.serverLevel();
        for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(LURE), m -> m.getTarget() == p || brainTarget(m) == p)) {
            m.setTarget(d.stand);
            if (m.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)) m.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, d.stand);
        }
    }

    private static @Nullable LivingEntity brainTarget(Mob m) {
        return m.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)
                ? m.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) : null;
    }

    /** Mob melee does not hurt armor stands: detect the swing in reach instead. */
    private static void mobHits(ServerPlayer owner, Decoy d) {
        ServerLevel l = (ServerLevel) d.stand.level();
        for (Mob m : l.getEntitiesOfClass(Mob.class, d.stand.getBoundingBox().inflate(2.5), m -> m.getTarget() == d.stand && m.swinging)) {
            shatter(d, m, false);
            return;
        }
    }

    /** The double bursts into blinding golden shards. attacker = who hit it; area = right-click detonation. */
    private static void shatter(Decoy d, @Nullable LivingEntity attacker, boolean area) {
        ArmorStand s = d.stand;
        if (s.isRemoved()) return;
        ServerLevel l = (ServerLevel) s.level();
        ServerPlayer owner = l.getServer().getPlayerList().getPlayer(d.owner);
        Vec3 c = s.position().add(0, 1, 0);
        if (owner != null) {
            if (area) {
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, s.getBoundingBox().inflate(5), e -> Rules.canHarm(owner, e))) blind(e);
            } else if (attacker != null && Rules.canHarm(owner, attacker)) blind(attacker);
        }
        float r = area ? 5F : 2.5F;
        FxNet.flare(l, c, area ? 4F : 2.5F, 0xFFFFFF, 12);
        FxNet.shockwave(l, s.position().add(0, 0.05, 0), r, MindKit.GOLD, 18);
        FxNet.shockwave(l, s.position().add(0, 1, 0), r * 0.7F, MindKit.PALE, 12);
        FxNet.flashAround(l, c, r + 1, MindKit.PALE, 0.6F, 14);
        if (area) FxNet.shake(l, c, 10, 1.2F, 10);
        Fx.burstRing(l, c, 32, Fx.dust(MindKit.MIND, 1.3F), 0.35);
        Fx.at(l, net.minecraft.core.particles.ParticleTypes.END_ROD, c, 20, 0.4, 0.15);
        Fx.at(l, net.minecraft.core.particles.ParticleTypes.FLASH, c, 1, 0, 0);
        Fx.sound(l, c, SoundEvents.GLASS_BREAK, 0.7F, 0.8F);
        Fx.sound(l, c, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.7F, 1.2F);
        shards(l, c, area ? 14 : 9);
        end(d, false);
    }

    private static void blind(LivingEntity e) {
        e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
        e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0));
        if (e instanceof Mob m) { m.setTarget(null); m.getNavigation().stop(); }
    }

    /** Flying golden glass shards (visual block displays, removed after a moment). */
    private static void shards(ServerLevel l, Vec3 c, int n) {
        for (int i = 0; i < n; i++) {
            Display.BlockDisplay b = Displays.block(l, (i % 2 == 0 ? Blocks.YELLOW_STAINED_GLASS : Blocks.GLASS).defaultBlockState(), c, 0.22F, MindKit.GOLD, 10);
            if (b == null) continue;
            Vec3 v = new Vec3(l.random.nextGaussian(), 0.4 + l.random.nextDouble() * 0.6, l.random.nextGaussian()).normalize().scale(2.2 + l.random.nextDouble() * 1.6);
            float yaw = l.random.nextFloat() * 6.28F, pitch = l.random.nextFloat() * 6.28F;
            Tasks.add(new Tasks.Task() {
                @Override public boolean tick(int age) {
                    if (age == 1) { b.teleportTo(c.x + v.x, c.y + v.y, c.z + v.z); Displays.pose(b, 0.2F, yaw, pitch, 10); }
                    if (age == 12) Displays.pose(b, 0.01F, yaw * 2, pitch * 2, 8);
                    return age < 20;
                }
                @Override public void end() { Displays.remove(b); }
            });
        }
    }

    private static void end(Decoy d, boolean fade) {
        BY_OWNER.remove(d.owner, d);
        BY_STAND.remove(d.stand);
        if (d.task != null) d.task.cancel();
        if (!d.stand.isRemoved() && fade) {
            ServerLevel l = (ServerLevel) d.stand.level();
            Vec3 c = d.stand.position().add(0, 1, 0);
            Fx.at(l, Fx.light(MindKit.MIND, 0.9F), c, 18, 0.35, 0.04);
            Fx.at(l, net.minecraft.core.particles.ParticleTypes.POOF, c, 6, 0.3, 0.02);
            Fx.sound(l, c, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.4F, 1.6F);
        }
        MindKit.discard(d.stand);
    }

    @SubscribeEvent
    public static void attack(AttackEntityEvent e) {
        Decoy d = BY_STAND.get(e.getTarget());
        if (d == null) return;
        e.setCanceled(true);
        if (e.getEntity().getUUID().equals(d.owner)) return; // the caster swinging at their own double does nothing
        shatter(d, e.getEntity(), false);
    }

    @SubscribeEvent
    public static void projectile(ProjectileImpactEvent e) {
        if (!(e.getRayTraceResult() instanceof EntityHitResult hit)) return;
        Decoy d = BY_STAND.get(hit.getEntity());
        if (d == null) return;
        e.setCanceled(true);
        Entity owner = e.getProjectile().getOwner();
        e.getProjectile().discard();
        shatter(d, owner instanceof LivingEntity le && !le.getUUID().equals(d.owner) ? le : null, false);
    }

    /** While hidden, mobs that would pick the caster pick the double instead. */
    @SubscribeEvent
    public static void target(LivingChangeTargetEvent e) {
        LivingEntity nt = e.getNewAboutToBeSetTarget();
        if (nt == null) return;
        Decoy d = BY_OWNER.get(nt.getUUID());
        if (d != null && !d.stand.isRemoved() && e.getEntity().distanceToSqr(d.stand) < 48 * 48) e.setNewAboutToBeSetTarget(d.stand);
    }
}
