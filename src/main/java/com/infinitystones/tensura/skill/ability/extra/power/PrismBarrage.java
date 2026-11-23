package com.infinitystones.tensura.skill.ability.extra.power;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Move;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Prism Barrage. A spinning purple prism opens above your head inside layered circles and fires lances in waves.
 * Left: a flat fan that follows where you look while it fires (sweep it across a crowd), 6 damage each.
 * Right: the lances burst out in every direction, then curve onto the aimed creature (or the point you aim at), 5 each.
 * Alt + scroll: 6–18 lances.
 */
public final class PrismBarrage implements Ability {
    public static final PrismBarrage INSTANCE = new PrismBarrage();
    static final String MODE = "power.prism_barrage";
    private static final AtomicInteger LIVE = new AtomicInteger();
    private static final int CAP = 64, PER_TICK = 3, CHARGE = 8;

    @Override public Stone stone() { return Stone.POWER; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        int count = ExtraSettings.get(p, MODE, 9, 6, 18, 3);
        if (LIVE.get() + count > CAP) return Fx.fail(p, "infinity.extra.power.prism.busy");
        ServerLevel l = p.serverLevel();

        LivingEntity target = alt ? Aim.living(p, 48, e -> Rules.canHarm(p, e)) : null;
        Vec3 point = alt ? Aim.point(p, 48) : Vec3.ZERO;
        Vec3 look0 = p.getLookAngle().normalize();
        Vec3 c0 = prismAt(p, look0);

        PowerNet.circle(l, 0, c0, look0, 2.1F, PowerUtil.COLOR, PowerUtil.ACCENT, 46, 3F, PowerNet.RAYS);
        PowerNet.circle(l, 0, c0.add(look0.scale(0.25)), look0, 1.4F, PowerUtil.ACCENT, PowerUtil.HOT, 46, -4F, PowerNet.GLYPHS_ONLY);
        PowerNet.circle(l, 0, c0.add(look0.scale(0.5)), look0, 0.8F, PowerUtil.HOT, PowerUtil.COLOR, 46, 6F, PowerNet.SIMPLE);
        FxNet.flare(l, c0, 2.2F, PowerUtil.ACCENT, 10);
        Fx.sound(l, c0, SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.8F);
        Fx.sound(l, c0, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, 0.6F);

        LIVE.addAndGet(count);
        Tasks.add(new Tasks.Task() {
            int fired;
            @Override public boolean tick(int age) {
                if (!p.isAlive() || p.hasDisconnected() || p.level() != l) return false;
                Vec3 look = p.getLookAngle().normalize();
                Vec3 c = prismAt(p, look);
                spin(l, c, age);
                if (age >= CHARGE && fired < count) {
                    int n = Math.min(PER_TICK, count - fired);
                    for (int i = 0; i < n; i++, fired++) {
                        Vec3 dir = alt ? burst(l, look) : fan(look, fired, count);
                        lance(p, l, c, dir, alt, target, point);
                    }
                    Fx.sound(l, c, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 0.6F + 1.2F * fired / count);
                    Fx.sound(l, c, SoundEvents.TRIDENT_THROW, 0.35F, 1.6F);
                    FxNet.shake(l, c, 10, 0.35F, 3);
                    Move.set(p, p.getDeltaMovement().add(look.scale(-0.04)));
                }
                if (fired >= count && age >= CHARGE + count / PER_TICK + 3) return false;
                return age < 80;
            }
            @Override public void end() {
                LIVE.addAndGet(-(count - fired));
                Vec3 c = prismAt(p, p.getLookAngle().normalize());
                FxNet.flare(l, c, 2.6F, PowerUtil.ACCENT, 8);
                Fx.at(l, ParticleTypes.END_ROD, c, 20, 0.5, 0.12);
                Fx.at(l, ParticleTypes.WITCH, c, 20, 0.6, 0.1);
            }
        });
        return true;
    }

    private static Vec3 prismAt(ServerPlayer p, Vec3 look) {
        return p.getEyePosition().add(look.scale(1.5)).add(0, 0.9, 0);
    }

    /** Flat fan around the look direction (index k of n). */
    private static Vec3 fan(Vec3 look, int k, int n) {
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 right = look.cross(up);
        right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up2 = right.cross(look).normalize();
        double a = n <= 1 ? 0 : Mth.lerp(k / (double) (n - 1), -0.62, 0.62);
        double b = ((k % 3) - 1) * 0.07;
        return look.add(right.scale(Math.tan(a))).add(up2.scale(b)).normalize();
    }

    /** Random direction in a wide cone around the look direction. */
    private static Vec3 burst(ServerLevel l, Vec3 look) {
        Vec3 v = new Vec3(l.random.nextGaussian(), l.random.nextGaussian() * 0.8, l.random.nextGaussian()).normalize();
        return look.scale(0.8).add(v.scale(1.1)).normalize();
    }

    /** Wireframe octahedron spinning around its vertical axis. */
    private static void spin(ServerLevel l, Vec3 c, int age) {
        double ang = age * 0.35, grow = Math.min(1, (age + 1) / 8.0);
        double rx = 0.55 * grow, ry = 0.95 * grow;
        Vec3 top = c.add(0, ry, 0), bot = c.add(0, -ry, 0);
        Vec3[] eq = new Vec3[4];
        for (int i = 0; i < 4; i++) {
            double a = ang + i * Math.PI / 2;
            eq[i] = c.add(Math.cos(a) * rx, 0, Math.sin(a) * rx);
        }
        var edge = Fx.light(Stone.POWER, 1.1F);
        var core = Fx.dust(Stone.POWER, 1.4F);
        for (int i = 0; i < 4; i++) {
            Fx.line(l, top, eq[i], 0.3, i % 2 == 0 ? edge : core);
            Fx.line(l, bot, eq[i], 0.3, i % 2 == 0 ? core : edge);
            Fx.line(l, eq[i], eq[(i + 1) % 4], 0.3, edge);
        }
        l.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 1, 0.12, 0.2, 0.12, 0.01);
        if (age % 3 == 0) l.sendParticles(Fx.dust(PowerUtil.HOT, 1.6F), c.x, c.y, c.z, 3, 0.25, 0.4, 0.25, 0);
    }

    private static void lance(ServerPlayer p, ServerLevel l, Vec3 from, Vec3 dir, boolean home, LivingEntity target, Vec3 point) {
        final double speed = home ? 1.6 : 2.2;
        final float dmg = home ? 5F : 6F;
        Tasks.add(new Tasks.Task() {
            Vec3 pos = from, vel = dir.scale(home ? 0.9 : speed);
            @Override public boolean tick(int age) {
                if (home && age >= 5) {
                    Vec3 goal = target != null && target.isAlive() ? target.getBoundingBox().getCenter() : point;
                    Vec3 want = goal.subtract(pos);
                    if (want.lengthSqr() > 1e-4) vel = vel.lerp(want.normalize().scale(speed), 0.22);
                } else if (home) {
                    vel = vel.scale(1.12);
                }
                Vec3 next = pos.add(vel);
                var blk = l.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                boolean blocked = blk.getType() != HitResult.Type.MISS;
                Vec3 end = blocked ? blk.getLocation() : next;

                LivingEntity best = null;
                double bd = Double.MAX_VALUE;
                Vec3 hitAt = end;
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(pos, end).inflate(1.5),
                        e -> Rules.isLiving(e) && Rules.canHarm(p, e))) {
                    Optional<Vec3> hit = e.getBoundingBox().inflate(0.3).clip(pos, end);
                    if (hit.isPresent() && hit.get().distanceToSqr(pos) < bd) { bd = hit.get().distanceToSqr(pos); best = e; hitAt = hit.get(); }
                }
                Fx.line(l, pos, hitAt, 0.4, Fx.dust(Stone.POWER, 1.3F));
                l.sendParticles(Fx.light(Stone.POWER, 0.8F), hitAt.x, hitAt.y, hitAt.z, 1, 0.04, 0.04, 0.04, 0);
                l.sendParticles(ParticleTypes.END_ROD, hitAt.x, hitAt.y, hitAt.z, 1, 0, 0, 0, 0);
                if (best != null) {
                    Rules.hurt(p, best, dmg);
                    best.setDeltaMovement(best.getDeltaMovement().add(vel.normalize().scale(0.3)));
                    best.hurtMarked = true;
                    impact(l, hitAt, true);
                    return false;
                }
                if (blocked) { impact(l, hitAt, false); return false; }
                pos = end;
                return age < 48;
            }
            @Override public void end() { LIVE.decrementAndGet(); }
        });
    }

    private static void impact(ServerLevel l, Vec3 at, boolean living) {
        FxNet.flare(l, at, living ? 1.5F : 1.0F, PowerUtil.ACCENT, 6);
        Fx.at(l, Fx.dust(Stone.POWER, 1.8F), at, 10, 0.3, 0.06);
        Fx.at(l, ParticleTypes.END_ROD, at, 4, 0.2, 0.15);
        if (l.random.nextInt(3) == 0) Fx.sound(l, at, SoundEvents.AMETHYST_BLOCK_BREAK, 0.5F, 0.8F + l.random.nextFloat() * 0.8F);
    }
}
