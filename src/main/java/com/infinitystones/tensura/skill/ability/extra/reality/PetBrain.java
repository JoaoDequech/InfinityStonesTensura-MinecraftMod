package com.infinitystones.tensura.skill.ability.extra.reality;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The AI of a Reality pet: two goals with top priority (one holds MOVE/JUMP, one holds TARGET) so the mob's own
 * wandering, panic and targeting goals are suspended while it is a pet. Installed again whenever the mob loads.
 * Mobs without an attack-damage attribute (cows, sheep...) get a hand-made melee hit.
 */
final class PetBrain {
    static final int PRIORITY = -50;
    static final int FOLLOW = 0, SIT = 1, GUARD = 2;

    /** Runtime state of a loaded pet. */
    static final class State {
        final Mob mob;
        final MoveGoal move;
        final TargetGoal target;
        final int crownId = FxNet.newId();
        @Nullable LivingEntity attack;
        int attackCd, navCd, scanCd, age;
        State(Mob mob) { this.mob = mob; this.move = new MoveGoal(this); this.target = new TargetGoal(this); }
    }

    private static final Map<Mob, State> STATES = new WeakHashMap<>();

    static @Nullable State state(Mob m) { return STATES.get(m); }

    static Iterable<State> all() { return STATES.values(); }

    static void install(Mob m) {
        if (STATES.containsKey(m)) return;
        State s = new State(m);
        STATES.put(m, s);
        m.goalSelector.addGoal(PRIORITY, s.move);
        m.targetSelector.addGoal(PRIORITY, s.target);
    }

    static void uninstall(Mob m) {
        State s = STATES.remove(m);
        if (s == null) return;
        m.goalSelector.removeGoal(s.move);
        m.targetSelector.removeGoal(s.target);
        m.setTarget(null);
        m.getNavigation().stop();
        if (m.level() instanceof ServerLevel l) FxNet.kill(l, m.position(), s.crownId);
    }

    static int order(Mob m) { return m.getPersistentData().getInt(RealityPet.TAG_ORDER); }

    static @Nullable ServerPlayer owner(Mob m) {
        UUID u = RealityPet.ownerOf(m);
        if (u == null || m.getServer() == null) return null;
        ServerPlayer p = m.getServer().getPlayerList().getPlayer(u);
        return p != null && p.level() == m.level() ? p : null;
    }

    static Vec3 guardPos(Mob m) {
        var t = m.getPersistentData();
        return t.contains(RealityPet.TAG_GUARD + "X") ? new Vec3(t.getDouble(RealityPet.TAG_GUARD + "X"), t.getDouble(RealityPet.TAG_GUARD + "Y"), t.getDouble(RealityPet.TAG_GUARD + "Z")) : m.position();
    }

    static boolean validTarget(State s, @Nullable LivingEntity t) {
        if (t == null || !t.isAlive() || t.isRemoved() || t.level() != s.mob.level() || t == s.mob) return false;
        if (t.distanceToSqr(s.mob) > 64 * 64) return false;
        UUID owner = RealityPet.ownerOf(s.mob);
        return owner != null && !RealityPet.isFriend(owner, t, s.mob.level());
    }

    /** Movement, attacks, guard scan, crown visual. */
    static final class MoveGoal extends Goal {
        private final State s;
        MoveGoal(State s) { this.s = s; setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP)); }

        @Override public boolean canUse() { return RealityPet.ownerOf(s.mob) != null; }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean isInterruptable() { return false; }
        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            Mob m = s.mob;
            if (!(m.level() instanceof ServerLevel l)) return;
            s.age++;
            if (s.attackCd > 0) s.attackCd--;
            if (s.navCd > 0) s.navCd--;
            ServerPlayer owner = owner(m);
            int order = order(m);

            if (s.age % 40 == 1) {
                FxNet.crown(l, s.crownId, m, Stone.REALITY.color(), 60);
            }
            if (s.age % 8 == 0) Fx.at(l, Fx.dust(Stone.REALITY, 0.7F), m.position().add(0, m.getBbHeight() + 0.25, 0), 1, 0.2, 0);

            if (!validTarget(s, s.attack)) s.attack = null;
            if (s.attack == null && order == GUARD && --s.scanCd <= 0) {
                s.scanCd = 10;
                s.attack = findGuardTarget(l, m, guardPos(m));
            }
            // brain-driven mobs (villagers, piglins, goats...): keep their brain from walking away
            if (m.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) m.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);

            if (s.attack != null) { attackTick(l, m, s.attack, owner); return; }
            switch (order) {
                case SIT -> m.getNavigation().stop();
                case GUARD -> {
                    Vec3 g = guardPos(m);
                    if (m.position().distanceToSqr(g) > 9) moveTo(m, g, 1.1);
                    else if (m.getNavigation().isInProgress() && m.position().distanceToSqr(g) < 2) m.getNavigation().stop();
                }
                default -> {
                    if (owner == null) { m.getNavigation().stop(); return; }
                    double d2 = m.distanceToSqr(owner);
                    if (d2 > 20 * 20) teleportNear(l, m, owner);
                    else if (d2 > 16) { moveTo(m, owner.position(), d2 > 100 ? 1.5 : 1.2); m.getLookControl().setLookAt(owner, 10, m.getMaxHeadXRot()); }
                    else if (d2 < 6) m.getNavigation().stop();
                }
            }
        }

        private void moveTo(Mob m, Vec3 v, double speed) {
            if (s.navCd > 0 && m.getNavigation().isInProgress()) return;
            s.navCd = 8;
            if (!m.getNavigation().moveTo(v.x, v.y, v.z, speed)) m.getMoveControl().setWantedPosition(v.x, v.y, v.z, speed);
        }

        private void attackTick(ServerLevel l, Mob m, LivingEntity t, @Nullable ServerPlayer owner) {
            if (m.getTarget() != t) m.setTarget(t);
            m.getLookControl().setLookAt(t, 30, 30);
            double d2 = m.distanceToSqr(t);
            if (m instanceof RangedAttackMob ranged && d2 < 15 * 15 && m.getSensing().hasLineOfSight(t)) {
                m.getNavigation().stop();
                if (s.attackCd <= 0) {
                    s.attackCd = 30;
                    ranged.performRangedAttack(t, 1F);
                }
                return;
            }
            boolean inReach = m.isWithinMeleeAttackRange(t);
            if (!inReach) {
                if (s.navCd <= 0 || !m.getNavigation().isInProgress()) {
                    s.navCd = 5;
                    if (!m.getNavigation().moveTo(t, 1.35)) m.getMoveControl().setWantedPosition(t.getX(), t.getY(), t.getZ(), 1.35);
                }
                return;
            }
            m.getNavigation().stop();
            if (s.attackCd > 0) return;
            s.attackCd = 20;
            m.swing(InteractionHand.MAIN_HAND);
            AttributeInstance a = m.getAttribute(Attributes.ATTACK_DAMAGE);
            if (a != null && a.getValue() > 0) {
                m.doHurtTarget(t);
            } else {
                // a cow can bite too: the Reality says so
                float dmg = Mth.clamp(m.getMaxHealth() * 0.25F, 2F, 8F) * (owner != null ? Rules.mult(owner, "reality") : 1F);
                if (t.hurt(m.damageSources().mobAttack(m), dmg)) {
                    t.knockback(0.4, m.getX() - t.getX(), m.getZ() - t.getZ());
                }
            }
            Vec3 hit = t.position().add(0, t.getBbHeight() * 0.6, 0);
            Fx.at(l, Fx.dust(Stone.REALITY, 1.2F), hit, 8, 0.25, 0);
            Fx.at(l, ParticleTypes.CRIT, hit, 4, 0.2, 0.2);
        }
    }

    /** Holds the TARGET flag so the mob's own targeting (players, revenge on the owner...) never runs. */
    static final class TargetGoal extends Goal {
        private final State s;
        TargetGoal(State s) { this.s = s; setFlags(EnumSet.of(Flag.TARGET)); }

        @Override public boolean canUse() { return RealityPet.ownerOf(s.mob) != null; }
        @Override public boolean canContinueToUse() { return canUse(); }
        @Override public boolean isInterruptable() { return false; }

        @Override
        public void tick() {
            Mob m = s.mob;
            LivingEntity t = m.getTarget();
            if (t != null && (t != s.attack || !validTarget(s, t))) m.setTarget(s.attack != null && validTarget(s, s.attack) ? s.attack : null);
            var brain = m.getBrain();
            if (brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
                LivingEntity bt = brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
                if (bt != null && bt != s.attack) brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
            }
            if (brain.hasMemoryValue(MemoryModuleType.ANGRY_AT)) brain.eraseMemory(MemoryModuleType.ANGRY_AT);
        }
    }

    static @Nullable LivingEntity findGuardTarget(ServerLevel l, Mob m, Vec3 g) {
        UUID owner = RealityPet.ownerOf(m);
        if (owner == null) return null;
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(g, g).inflate(8, 4, 8), e -> e.isAlive() && e != m)) {
            if (RealityPet.isFriend(owner, e, l)) continue;
            boolean hostile = e instanceof Enemy;
            if (!hostile && e instanceof Mob em && em.getTarget() != null && RealityPet.isFriend(owner, em.getTarget(), l)) hostile = true;
            if (!hostile) continue;
            double d = e.distanceToSqr(g);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    static void teleportNear(ServerLevel l, Mob m, Entity owner) {
        Vec3 from = m.position();
        for (int i = 0; i < 12; i++) {
            double a = l.random.nextDouble() * Math.PI * 2, r = 1.5 + l.random.nextDouble() * 1.5;
            Vec3 v = Ground.below(l, owner.position().add(Math.cos(a) * r, 1.5, Math.sin(a) * r), 4);
            if (Ground.fits(l, m, v) && Math.abs(v.y - owner.getY()) < 3) {
                m.moveTo(v.x, v.y, v.z, m.getYRot(), m.getXRot());
                m.getNavigation().stop();
                m.fallDistance = 0;
                FxNet.flare(l, from.add(0, m.getBbHeight() / 2, 0), 1.2F, Stone.REALITY.color(), 12);
                FxNet.flare(l, v.add(0, m.getBbHeight() / 2, 0), 1.2F, Stone.REALITY.color(), 12);
                Fx.at(l, Fx.dust(Stone.REALITY, 1F), v.add(0, m.getBbHeight() / 2, 0), 14, 0.4, 0);
                Fx.sound(l, v, SoundEvents.ENDERMAN_TELEPORT, 0.35F, 1.4F);
                return;
            }
        }
    }

    private PetBrain() {}
}
