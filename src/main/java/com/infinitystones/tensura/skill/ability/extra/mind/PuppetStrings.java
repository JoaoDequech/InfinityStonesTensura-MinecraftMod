package com.infinitystones.tensura.skill.ability.extra.mind;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneProgress;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Move;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import com.infinitystones.tensura.skill.ability.extra.power.PowerNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Puppet Strings. Left: golden strings tie up to 3 creatures in front of you (bosses are immune); they float where you
 * look, keeping the distance they had, until you let go (left again), the strings snap after 20 s or you drop the stone.
 * Right: hurl every puppet along your look direction - on impact each one smashes (6) and the shock hurts whoever is
 * around it (4).
 */
public final class PuppetStrings implements Ability {
    public static final PuppetStrings INSTANCE = new PuppetStrings();
    private static final int MAX = 3, TICKS = 400;
    private static final int FLING = 0xFFE680;

    private static final class Puppet {
        final LivingEntity e; final double dist, side;
        final int tether = FxNet.newId(), crown = FxNet.newId();
        Puppet(LivingEntity e, double dist, double side) { this.e = e; this.dist = dist; this.side = side; }
    }

    private static final class Session {
        final List<Puppet> puppets = new ArrayList<>();
        Tasks.Handle handle;
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    @Override public Stone stone() { return Stone.MIND; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        Session cur = SESSIONS.get(p.getUUID());
        if (alt) {
            if (cur == null) return Fx.fail(p, "infinity.extra.mind.puppet.none");
            fling(p, cur);
            return true;
        }
        if (cur != null) {
            SESSIONS.remove(p.getUUID());
            if (cur.handle != null) cur.handle.cancel();
            Fx.bar(p, Stone.MIND, "infinity.extra.mind.puppet.release");
            Fx.sound(p.serverLevel(), p.position(), SoundEvents.AMETHYST_BLOCK_BREAK, 0.5F, 1.4F);
            return true;
        }
        return grab(p);
    }

    private boolean grab(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Vec3 eye = p.getEyePosition(), look = p.getLookAngle().normalize();
        List<LivingEntity> cand = new ArrayList<>(l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24),
                e -> Rules.isLiving(e) && Rules.canControl(p, e) && !MindKit.isBody(e) && cone(eye, look, e) > 0.8));
        if (cand.isEmpty()) return Fx.fail(p, "infinity.extra.mind.no_target");
        cand.sort(Comparator.comparingDouble(e -> -cone(eye, look, e) * 4 + e.distanceTo(p) * 0.02));
        if (cand.size() > MAX) cand = cand.subList(0, MAX);

        Session s = new Session();
        int n = cand.size();
        for (int i = 0; i < n; i++) {
            LivingEntity e = cand.get(i);
            double dist = Math.max(4, Math.min(14, e.distanceTo(p)));
            s.puppets.add(new Puppet(e, dist, (i - (n - 1) / 2.0) * 2.4));
            Vec3 c = e.getBoundingBox().getCenter();
            FxNet.beam(l, eye.add(0, -0.4, 0), c, MindKit.GOLD, 0.08F, 12);
            FxNet.runes(l, 0, e.position().add(0, 0.05, 0), Math.max(1.4F, e.getBbWidth() * 2), MindKit.GOLD, 40, 2F);
            FxNet.flare(l, c, 1.2F, MindKit.PALE, 10);
            MindKit.spark(l, c, 10);
        }
        SESSIONS.put(p.getUUID(), s);
        Fx.sound(l, eye, SoundEvents.NOTE_BLOCK_HARP, 0.6F, 1.5F);
        Fx.sound(l, eye, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 0.7F);
        Fx.bar(p, Stone.MIND, "infinity.extra.mind.puppet.grab", n);
        s.handle = Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) { return hold(p, s, age); }
            @Override public void end() { cleanup(p, s); }
        });
        return true;
    }

    private static double cone(Vec3 eye, Vec3 look, LivingEntity e) {
        Vec3 d = e.getBoundingBox().getCenter().subtract(eye);
        double len = d.length();
        return len < 1e-3 ? 1 : d.dot(look) / len;
    }

    private boolean hold(ServerPlayer p, Session s, int age) {
        if (SESSIONS.get(p.getUUID()) != s) return false;
        if (!p.isAlive() || p.hasDisconnected() || age >= TICKS || !StoneProgress.heldStones(p).contains(Stone.MIND)) {
            SESSIONS.remove(p.getUUID());
            if (p.isAlive() && !p.hasDisconnected()) Fx.bar(p, Stone.MIND, "infinity.extra.mind.puppet.release");
            return false;
        }
        ServerLevel l = p.serverLevel();
        s.puppets.removeIf(pp -> !pp.e.isAlive() || pp.e.level() != l);
        if (s.puppets.isEmpty()) { SESSIONS.remove(p.getUUID()); return false; }

        Vec3 eye = p.getEyePosition(), look = p.getLookAngle().normalize();
        Vec3 right = new Vec3(-look.z, 0, look.x);
        right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 hand = eye.add(0, -0.5, 0).add(look.scale(0.4));
        for (Puppet pp : s.puppets) {
            LivingEntity e = pp.e;
            Vec3 target = eye.add(look.scale(pp.dist)).add(right.scale(pp.side));
            var hit = l.clip(new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
            if (hit.getType() != HitResult.Type.MISS) target = hit.getLocation().subtract(look.scale(0.6));
            Move.steer(e, target, 0.3, 1.1);
            if (e instanceof Mob m) { m.setTarget(null); m.getNavigation().stop(); }
            Vec3 c = e.getBoundingBox().getCenter();
            if (age % 4 == 0) {
                FxNet.tether(l, pp.tether, p, e, MindKit.GOLD, 0.05F, 7);
                FxNet.crown(l, pp.crown, e, MindKit.GOLD, 9);
            }
            if (age % 2 == 0) {
                int n = Math.max(2, Math.min(18, (int) (hand.distanceTo(c) / 0.8)));
                for (int i = 1; i < n; i++) {
                    Vec3 v = hand.lerp(c, i / (double) n);
                    l.sendParticles(i % 2 == 0 ? Fx.dust(MindKit.GOLD, 0.8F) : ParticleTypes.END_ROD, v.x, v.y, v.z, 1, 0, 0, 0, 0);
                }
            }
        }
        if (age % 40 == 0) Fx.sound(l, hand, SoundEvents.NOTE_BLOCK_CHIME, 0.3F, 0.7F + l.random.nextFloat() * 0.8F);
        return true;
    }

    private void cleanup(ServerPlayer p, Session s) {
        ServerLevel l = p.serverLevel();
        for (Puppet pp : s.puppets) {
            FxNet.kill(l, pp.e.position(), pp.tether);
            FxNet.kill(l, pp.e.position(), pp.crown);
            if (pp.e.isAlive()) {
                Vec3 c = pp.e.getBoundingBox().getCenter();
                MindKit.spark(l, c, 8);
                pp.e.fallDistance = 0;
            }
        }
    }

    private void fling(ServerPlayer p, Session s) {
        SESSIONS.remove(p.getUUID());
        if (s.handle != null) s.handle.cancel();
        ServerLevel l = p.serverLevel();
        Vec3 look = p.getLookAngle().normalize();
        Vec3 eye = p.getEyePosition();
        FxNet.shake(l, eye, 24, 1.2F, 10);
        PowerNet.circle(l, 0, eye.add(look.scale(1.2)), look, 1.6F, MindKit.GOLD, MindKit.PALE, 14, 4F, PowerNet.SIMPLE);
        Fx.sound(l, eye, SoundEvents.NOTE_BLOCK_BASS, 0.6F, 0.5F);
        Fx.sound(l, eye, SoundEvents.ENDER_DRAGON_FLAP, 0.5F, 1.4F);
        Fx.bar(p, Stone.MIND, "infinity.extra.mind.puppet.fling");
        for (Puppet pp : s.puppets) {
            LivingEntity e = pp.e;
            if (!e.isAlive()) continue;
            Rules.hurt(p, e, 4);
            Move.set(e, look.scale(2.3).add(0, 0.25, 0));
            FxNet.beam(l, e.getBoundingBox().getCenter(), e.getBoundingBox().getCenter().add(look.scale(-6)), FLING, 0.12F, 8);
            Tasks.add(age -> {
                if (!e.isAlive() || e.level() != l) return false;
                Vec3 c = e.getBoundingBox().getCenter();
                l.sendParticles(Fx.dust(MindKit.GOLD, 1.4F), c.x, c.y, c.z, 4, 0.2, 0.2, 0.2, 0);
                l.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 1, 0.1, 0.1, 0.1, 0.02);
                boolean bump = age >= 2 && (e.horizontalCollision || e.verticalCollision || e.onGround()
                        || !l.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(0.6), o -> o != e && o != p && Rules.canHarm(p, o)).isEmpty());
                if (bump) { impact(p, l, e); return false; }
                return age < 30;
            });
        }
    }

    private void impact(ServerPlayer p, ServerLevel l, LivingEntity e) {
        Vec3 c = e.getBoundingBox().getCenter();
        Rules.hurt(p, e, 6);
        for (LivingEntity o : l.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(3),
                o -> o != e && Rules.isLiving(o) && Rules.canHarm(p, o))) {
            Rules.hurt(p, o, 4);
            Vec3 d = o.position().subtract(e.position()).multiply(1, 0, 1);
            if (d.lengthSqr() > 1e-4) Move.set(o, o.getDeltaMovement().add(d.normalize().scale(0.8)).add(0, 0.4, 0));
        }
        FxNet.shockwave(l, e.position().add(0, 0.1, 0), 3.5F, MindKit.GOLD, 16);
        FxNet.flare(l, c, 2.2F, MindKit.PALE, 8);
        FxNet.shake(l, c, 16, 1.4F, 10);
        MindKit.spark(l, c, 28);
        Fx.sound(l, c, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.7F, 0.6F);
        Fx.sound(l, c, SoundEvents.ANVIL_LAND, 0.4F, 1.2F);
    }
}
