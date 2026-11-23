package com.infinitystones.tensura.skill.ability.extra.space;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Move;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import com.infinitystones.tensura.skill.ability.extra.mind.MindKit;
import com.infinitystones.tensura.skill.ability.extra.power.PowerNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Pocket Orbit (toggle). You become a small blue planet: rings of light and three moons circle you, you float down
 * softly, and everything within reach is caught in orbit around you - up to 8 creatures (bosses excepted) and 14
 * items / projectiles. Enemies in orbit cannot fight and take 2 damage a second. Sneak to sling everything outward
 * (6 damage to creatures, they cannot be recaught for 2 s). Alt + scroll: capture radius 4–10.
 */
public final class PocketOrbit implements Ability {
    public static final PocketOrbit INSTANCE = new PocketOrbit();
    static final String MODE = "space.pocket_orbit";
    private static final int BLUE = 0x2F8CFF, LIGHT = 0x9CC8FF;
    private static final int MAX_LIVING = 8, MAX_OTHER = 14;

    private static final class Slot {
        final double phase, dir, radius, tilt;
        Slot(double phase, double dir, double radius, double tilt) { this.phase = phase; this.dir = dir; this.radius = radius; this.tilt = tilt; }
    }

    private static final class State {
        final Map<Integer, Slot> orbiters = new HashMap<>();
        final Map<Integer, Integer> immune = new HashMap<>();
        final int orb = FxNet.newId();
        final int[] rings = {PowerNet.newId(), PowerNet.newId(), PowerNet.newId()};
        int age;
        boolean wasSneak;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    @Override public Stone stone() { return Stone.SPACE; }

    @Override
    public void start(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        STATES.put(p.getUUID(), new State());
        Vec3 o = p.position();
        PowerNet.circle(l, 0, o.add(0, 0.06, 0), new Vec3(0, 1, 0), 4F, BLUE, LIGHT, 40, 4F, PowerNet.RAYS);
        FxNet.shockwave(l, o.add(0, 0.1, 0), 8F, BLUE, 22);
        FxNet.spiral(l, o, 1.0F, 3F, BLUE, 24, p.getId());
        FxNet.flash(p, BLUE, 0.3F, 10);
        Fx.sound(l, o, SoundEvents.BEACON_ACTIVATE, 0.5F, 0.7F);
        Fx.sound(l, o, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 0.5F);
        Fx.bar(p, Stone.SPACE, "infinity.extra.space.orbit.on");
    }

    @Override
    public void tick(ServerPlayer p) {
        State s = STATES.computeIfAbsent(p.getUUID(), k -> new State());
        ServerLevel l = p.serverLevel();
        int r = ExtraSettings.get(p, MODE, 7, 4, 10, 1);
        int age = ++s.age;
        p.fallDistance = 0;
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 8, 0, true, false, false));

        Vec3 center = p.position().add(0, 1.0, 0);
        if (age % 10 == 1) visuals(p, l, s, center);
        moons(l, center, age);

        boolean sneak = p.isShiftKeyDown();
        if (sneak && !s.wasSneak && !s.orbiters.isEmpty()) sling(p, l, s, center);
        s.wasSneak = sneak;

        if (age % 4 == 0) capture(p, l, s, r, center);
        orbit(p, l, s, center, age);
    }

    private void capture(ServerPlayer p, ServerLevel l, State s, int r, Vec3 center) {
        int living = 0, other = 0;
        for (int id : s.orbiters.keySet()) {
            Entity e = l.getEntity(id);
            if (e instanceof LivingEntity) living++; else other++;
        }
        if (living >= MAX_LIVING && other >= MAX_OTHER) return;
        List<Entity> found = new ArrayList<>(l.getEntities(p, p.getBoundingBox().inflate(r), e -> e != p && !s.orbiters.containsKey(e.getId())
                && s.immune.getOrDefault(e.getId(), 0) <= s.age && e.position().distanceToSqr(p.position()) <= (double) r * r && catchable(p, e)));
        found.sort(java.util.Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        for (Entity e : found) {
            boolean isLiving = e instanceof LivingEntity;
            if (isLiving ? living >= MAX_LIVING : other >= MAX_OTHER) continue;
            var rnd = l.random;
            double rad = isLiving ? 2.8 + rnd.nextDouble() * 1.6 : 1.8 + rnd.nextDouble() * 1.4;
            s.orbiters.put(e.getId(), new Slot(rnd.nextDouble() * Math.PI * 2, rnd.nextBoolean() ? 1 : -1, rad, (rnd.nextDouble() - 0.5) * 1.2));
            if (isLiving) living++; else other++;
            Vec3 c = e.getBoundingBox().getCenter();
            FxNet.beam(l, center, c, BLUE, 0.06F, 6);
            Fx.at(l, Fx.light(Stone.SPACE, 1.0F), c, 6, 0.3, 0.02);
            if (isLiving) Fx.sound(l, c, SoundEvents.AMETHYST_BLOCK_CHIME, 0.4F, 0.7F + rnd.nextFloat() * 0.5F);
        }
    }

    private static boolean catchable(ServerPlayer p, Entity e) {
        if (e.isSpectator() || e.isPassenger() || e.isVehicle() || MindKit.isBody(e)) return false;
        if (e instanceof ItemEntity it) return !MindKit.isIllusion(it.getItem());
        if (e instanceof Projectile pr) {
            Entity owner = pr.getOwner();
            return owner != p && (owner == null || !Rules.isAlly(p, owner)) && pr.getDeltaMovement().lengthSqr() > 0.01;
        }
        return e instanceof LivingEntity && Rules.isLiving(e) && Rules.canControl(p, e);
    }

    private void orbit(ServerPlayer p, ServerLevel l, State s, Vec3 center, int age) {
        var it = s.orbiters.entrySet().iterator();
        while (it.hasNext()) {
            var en = it.next();
            Entity e = l.getEntity(en.getKey());
            if (e == null || e.isRemoved() || (e instanceof LivingEntity le && !le.isAlive()) || e.position().distanceToSqr(p.position()) > 20 * 20) { it.remove(); continue; }
            Slot sl = en.getValue();
            double a = age * 0.09 * sl.dir + sl.phase;
            double x = Math.cos(a) * sl.radius, z0 = Math.sin(a) * sl.radius;
            Vec3 target = center.add(x, z0 * Math.sin(sl.tilt), z0 * Math.cos(sl.tilt));
            Move.steer(e, target, 0.5, 1.0);
            if (e instanceof Mob m) { m.setTarget(null); m.getNavigation().stop(); }
            if (e instanceof LivingEntity le) {
                if (age % 20 == 0 && Rules.isEnemy(p, le)) Rules.hurt(p, le, 2);
                if (age % 3 == 0) {
                    Vec3 c = e.getBoundingBox().getCenter();
                    l.sendParticles(Fx.dust(BLUE, 1.0F), c.x, c.y, c.z, 2, 0.2, 0.2, 0.2, 0);
                }
            } else if (age % 2 == 0) {
                l.sendParticles(Fx.light(Stone.SPACE, 0.7F), e.getX(), e.getY() + 0.1, e.getZ(), 1, 0.05, 0.05, 0.05, 0);
            }
        }
    }

    private void sling(ServerPlayer p, ServerLevel l, State s, Vec3 center) {
        for (var en : s.orbiters.entrySet()) {
            Entity e = l.getEntity(en.getKey());
            if (e == null || e.isRemoved()) continue;
            Vec3 out = e.getBoundingBox().getCenter().subtract(center);
            out = out.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : out.normalize();
            Vec3 tan = new Vec3(-out.z, 0, out.x).scale(en.getValue().dir);
            Move.set(e, out.scale(1.2).add(tan.scale(1.0)).add(0, 0.25, 0));
            s.immune.put(en.getKey(), s.age + 40);
            if (e instanceof LivingEntity le && Rules.canHarm(p, le)) Rules.hurt(p, le, 6);
            FxNet.beam(l, e.getBoundingBox().getCenter(), e.getBoundingBox().getCenter().add(out.scale(-4)), LIGHT, 0.1F, 8);
        }
        s.orbiters.clear();
        FxNet.shockwave(l, p.position().add(0, 0.1, 0), 9F, BLUE, 16);
        PowerNet.circle(l, 0, center, new Vec3(0, 1, 0), 5F, BLUE, LIGHT, 18, 8F, PowerNet.SIMPLE);
        FxNet.shake(l, center, 20, 1.1F, 8);
        Fx.sound(l, center, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.7F, 0.6F);
        Fx.sound(l, center, SoundEvents.ENDER_DRAGON_FLAP, 0.5F, 1.2F);
    }

    private void visuals(ServerPlayer p, ServerLevel l, State s, Vec3 center) {
        PowerNet.circle(l, s.rings[0], new Vec3(0, 1.0, 0), new Vec3(0, 1, 0), 3.2F, BLUE, LIGHT, 14, 2F, PowerNet.SIMPLE, p);
        PowerNet.circle(l, s.rings[1], new Vec3(0, 1.0, 0), new Vec3(0.6, 0.8, 0), 2.4F, LIGHT, BLUE, 14, -3F, PowerNet.GLYPHS_ONLY, p);
        PowerNet.circle(l, s.rings[2], new Vec3(0, 1.0, 0), new Vec3(-0.5, 0.75, 0.45), 1.7F, BLUE, LIGHT, 14, 4F, PowerNet.SIMPLE, p);
        FxNet.orb(l, s.orb, p.position(), 0.7F, BLUE, 14, p.getId(), new Vec3(0, 1.0, 0));
    }

    /** Three moons on tilted orbits, each trailing light. */
    private void moons(ServerLevel l, Vec3 c, int age) {
        double[] rad = {1.7, 2.5, 3.2}, tilt = {0.0, 0.6, -0.5}, speed = {0.17, -0.12, 0.09};
        for (int i = 0; i < 3; i++) {
            for (int tr = 0; tr < 3; tr++) {
                double a = (age - tr * 1.6) * speed[i] + i * 2.1;
                double x = Math.cos(a) * rad[i], z0 = Math.sin(a) * rad[i];
                Vec3 v = c.add(x, z0 * Math.sin(tilt[i]), z0 * Math.cos(tilt[i]));
                if (tr == 0) {
                    l.sendParticles(ParticleTypes.END_ROD, v.x, v.y, v.z, 1, 0.02, 0.02, 0.02, 0);
                    l.sendParticles(Fx.light(Stone.SPACE, 1.5F), v.x, v.y, v.z, 1, 0.04, 0.04, 0.04, 0);
                } else {
                    l.sendParticles(Fx.dust(BLUE, 1.0F - tr * 0.25F), v.x, v.y, v.z, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    @Override
    public void stop(ServerPlayer p) {
        State s = STATES.remove(p.getUUID());
        ServerLevel l = p.serverLevel();
        if (s != null) {
            for (int id : s.rings) PowerNet.kill(l, p.position(), id);
            FxNet.kill(l, p.position(), s.orb);
        }
        if (p.isAlive() && !p.hasDisconnected()) {
            FxNet.shockwave(l, p.position().add(0, 0.1, 0), 5F, BLUE, 14);
            Fx.sound(l, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5F, 0.4F);
            Fx.bar(p, Stone.SPACE, "infinity.extra.space.orbit.off");
        }
    }
}
