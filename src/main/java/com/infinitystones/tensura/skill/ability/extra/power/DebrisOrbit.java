package com.infinitystones.tensura.skill.ability.extra.power;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Displays;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Debris Orbit. Left: tear up to 8 light-copies of the ground around you (the ground is NOT changed); they orbit you
 * for 30 s and block incoming projectiles (one piece per projectile). Left again: fire one at the aimed target;
 * right: fire them all. Each hit: 6 damage, knockback, dust of the block.
 */
public final class DebrisOrbit implements Ability {
    public static final DebrisOrbit INSTANCE = new DebrisOrbit();
    static final int COUNT = 8, LIFE = 600;
    private static final Map<UUID, Orbit> ORBITS = new HashMap<>();

    @Override public Stone stone() { return Stone.POWER; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        Orbit o = ORBITS.get(p.getUUID());
        if (o != null && !o.pieces.isEmpty()) {
            if (alt) { o.fireAll(); return true; }
            o.fireOne(o.aim());
            return true;
        }
        if (alt) return Fx.fail(p, "infinity.extra.power.orbit.none");
        if (o != null) o.handle.cancel();
        Orbit n = new Orbit(p);
        if (n.pieces.isEmpty()) return Fx.fail(p, "infinity.extra.power.orbit.nothing");
        ORBITS.put(p.getUUID(), n);
        n.handle = Tasks.add(n);
        Fx.bar(p, Stone.POWER, "infinity.extra.power.orbit.start", n.pieces.size());
        return true;
    }

    private static final class Piece {
        Display.BlockDisplay d; BlockState st; Vec3 pos; int rise;
        Piece(Display.BlockDisplay d, BlockState st, Vec3 pos) { this.d = d; this.st = st; this.pos = pos; }
    }

    private static final class Orbit implements Tasks.Task {
        final ServerPlayer p; final ServerLevel l;
        final List<Piece> pieces = new ArrayList<>();
        final int circleId = PowerNet.newId();
        Tasks.Handle handle;
        int age, salvo;

        Orbit(ServerPlayer p) {
            this.p = p; this.l = p.serverLevel();
            for (int k = 0; k < COUNT; k++) {
                double a = Math.PI * 2 * k / COUNT + l.random.nextDouble() * 0.5, r = 1.5 + l.random.nextDouble() * 2.5;
                Vec3 at = p.position().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                BlockPos fp = com.infinitystones.tensura.skill.ability.extra.Ground.floorBlock(l, at.add(0, 2, 0), 5);
                if (fp == null) continue;
                BlockState st = PowerUtil.surface(l, Vec3.atCenterOf(fp).add(0, 0.6, 0));
                Vec3 start = Vec3.atCenterOf(fp).add(0, 0.6, 0);
                Display.BlockDisplay d = Displays.block(l, st, start, 0.7F, PowerUtil.COLOR, 1);
                if (d == null) continue;
                pieces.add(new Piece(d, st, start));
                l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), start.x, start.y, start.z, 12, 0.3, 0.2, 0.3, 0.1);
            }
            Fx.sound(l, p.position(), SoundEvents.ROOTED_DIRT_BREAK, 0.7F, 0.5F);
            Fx.sound(l, p.position(), SoundEvents.BEACON_ACTIVATE, 0.5F, 1.4F);
            PowerNet.circle(l, 0, p.position().add(0, 0.05, 0), new Vec3(0, 1, 0), 4.2F, PowerUtil.COLOR, PowerUtil.ACCENT, 30, 2F, PowerNet.SIMPLE);
            FxNet.shockwave(l, p.position().add(0, 0.1, 0), 5, PowerUtil.COLOR, 16);
        }

        Vec3 slot(int i, int n) {
            double a = age * 0.09 + Math.PI * 2 * i / Math.max(1, n);
            double r = 2.3 + 0.2 * Math.sin(age * 0.05 + i);
            return p.position().add(Math.cos(a) * r, 1.1 + 0.45 * Math.sin(age * 0.12 + i * 1.3), Math.sin(a) * r);
        }

        /** Target: aimed enemy (homing) or aimed point. */
        Object aim() {
            LivingEntity e = Aim.living(p, 48, x -> Rules.canHarm(p, x));
            return e != null ? e : Aim.point(p, 48);
        }

        void fireAll() {
            Object t = aim();
            salvo = 0;
            int n = pieces.size();
            for (int k = 0; k < n; k++) {
                int delay = k * 2;
                Tasks.add(a -> { if (a == delay) fireOne(t); return a < delay; });
            }
            Fx.bar(p, Stone.POWER, "infinity.extra.power.orbit.salvo", n);
        }

        void fireOne(Object target) {
            if (pieces.isEmpty()) return;
            Piece pc = pieces.remove(0);
            Fx.sound(l, pc.pos, SoundEvents.BREEZE_SHOOT, 0.6F, 0.6F);
            Tasks.add(new Tasks.Task() {
                Vec3 pos = pc.pos, vel = null;
                @Override public boolean tick(int a) {
                    if (pc.d.isRemoved()) return false;
                    Vec3 aimPt = target instanceof LivingEntity le ? (le.isAlive() ? le.getBoundingBox().getCenter() : null) : (Vec3) target;
                    if (aimPt == null) aimPt = pos.add(vel == null ? p.getLookAngle() : vel);
                    Vec3 dir = aimPt.subtract(pos).normalize();
                    vel = vel == null ? dir.scale(1.6) : vel.scale(0.6).add(dir.scale(1.6 * 0.4)).normalize().scale(1.7);
                    Vec3 next = pos.add(vel);
                    Fx.line(l, pos, next, 0.6, Fx.dust(Stone.POWER, 1.4F));
                    pos = next;
                    pc.d.setPos(pos.x, pos.y, pos.z);
                    if (a % 3 == 0) Displays.pose(pc.d, 0.7F, a * 0.8F, a * 0.5F, 3);
                    // hit
                    for (LivingEntity e : PowerUtil.victims(p, l, pos, 1.6)) {
                        Rules.hurt(p, e, 6);
                        Vec3 kb = vel.normalize();
                        e.setDeltaMovement(e.getDeltaMovement().add(kb.x * 0.8, 0.35, kb.z * 0.8));
                        e.hurtMarked = true;
                        burst(pos);
                        return false;
                    }
                    BlockPos bp = BlockPos.containing(pos);
                    if (!l.getBlockState(bp).getCollisionShape(l, bp).isEmpty() || a > 40) { burst(pos); return false; }
                    if (target instanceof Vec3 v && pos.distanceToSqr(v) < 1.2) { burst(pos); return false; }
                    return true;
                }
                void burst(Vec3 at) {
                    l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, pc.st), at.x, at.y, at.z, 30, 0.4, 0.4, 0.4, 0.2);
                    l.sendParticles(Fx.dust(Stone.POWER, 2F), at.x, at.y, at.z, 12, 0.4, 0.4, 0.4, 0.05);
                    Fx.sound(l, at, SoundEvents.GENERIC_EXPLODE, 0.35F, 1.4F);
                    FxNet.shockwave(l, at, 2.2F, PowerUtil.COLOR, 10);
                }
                @Override public void end() { Displays.remove(pc.d); }
            });
        }

        @Override
        public boolean tick(int a) {
            age = a;
            if (!p.isAlive() || p.isRemoved() || p.level() != l || a > LIFE || pieces.isEmpty()) return false;
            int n = pieces.size();
            for (int i = 0; i < n; i++) {
                Piece pc = pieces.get(i);
                Vec3 s = slot(i, n);
                if (pc.rise < 10) { pc.rise++; s = pc.pos.lerp(s, pc.rise / 10.0); }
                pc.pos = s;
                pc.d.setPos(s.x, s.y, s.z);
                if (a % 4 == 0) Displays.pose(pc.d, 0.7F, a * 0.15F + i, (float) Math.sin(a * 0.1 + i) * 0.6F, 4);
            }
            if (a % 3 == 0) for (Piece pc : pieces) l.sendParticles(Fx.dust(Stone.POWER, 1.1F), pc.pos.x, pc.pos.y, pc.pos.z, 1, 0.15, 0.15, 0.15, 0);
            if (a % 30 == 0) PowerNet.circle(l, circleId, new Vec3(0, 0.05, 0), new Vec3(0, 1, 0), 2.6F, PowerUtil.COLOR, PowerUtil.ACCENT, 45, 1.2F, PowerNet.SIMPLE, p);
            // projectile shield
            if (a % 2 == 0) {
                for (Projectile pr : l.getEntitiesOfClass(Projectile.class, p.getBoundingBox().inflate(3.2))) {
                    Entity owner = pr.getOwner();
                    if (owner != null && Rules.isAlly(p, owner)) continue;
                    if (pieces.isEmpty()) break;
                    Vec3 at = pr.position();
                    Piece pc = pieces.remove(pieces.size() - 1);
                    l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, pc.st), at.x, at.y, at.z, 20, 0.3, 0.3, 0.3, 0.15);
                    Fx.sound(l, at, SoundEvents.SHIELD_BLOCK, 0.6F, 0.7F);
                    if (pr instanceof net.minecraft.world.entity.projectile.ThrownTrident) { pr.setDeltaMovement(pr.getDeltaMovement().scale(-0.2)); pr.hurtMarked = true; }
                    else pr.discard();
                    Displays.remove(pc.d);
                }
            }
            if (a == LIFE - 40) Fx.barGray(p, "infinity.extra.power.orbit.ending");
            return true;
        }

        @Override
        public void end() {
            ORBITS.remove(p.getUUID(), this);
            for (Piece pc : pieces) {
                l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, pc.st), pc.pos.x, pc.pos.y, pc.pos.z, 8, 0.2, 0.2, 0.2, 0.1);
                Displays.remove(pc.d);
            }
            pieces.clear();
            PowerNet.kill(l, p.position(), circleId);
        }
    }
}
