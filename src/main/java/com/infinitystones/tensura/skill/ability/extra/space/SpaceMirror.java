package com.infinitystones.tensura.skill.ability.extra.space;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Espelho Espacial / Space Mirror: a 4×4 rippling panel of space at the aimed point, facing you, for 20 s (one per
 * player). Left: enemy projectiles crossing it fly back to whoever shot them at twice the speed. Right (return
 * mirror): anything that enters (mob, item, arrow, player — no bosses) comes out 2 blocks behind you, facing forward.
 */
public final class SpaceMirror implements Ability {
    public static final SpaceMirror INSTANCE = new SpaceMirror();
    private static final String K = "infinity.extra.space_mirror.";
    private static final int LIFE = 400;
    private static final double HALF = 2.0;

    private static final class Mirror {
        Vec3 c, n, u; boolean ret; boolean dead;
        final int panelId = FxNet.newId(), portalId = FxNet.newId(), runeId = FxNet.newId();
        final Map<Integer, Integer> cool = new HashMap<>();
    }

    private static final Map<UUID, Mirror> MIRRORS = new HashMap<>();

    @Override public Stone stone() { return Stone.SPACE; }

    private static int col(boolean ret) { return ret ? 0x7FC8FF : Stone.SPACE.color() & 0xFFFFFF; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        ServerLevel l = p.serverLevel();
        Mirror old = MIRRORS.remove(p.getUUID());
        if (old != null) old.dead = true;
        Vec3 look = p.getLookAngle();
        Vec3 pt = Aim.point(p, 32).subtract(look.scale(0.6));
        if (pt.distanceToSqr(p.getEyePosition()) < 9) pt = p.getEyePosition().add(look.scale(3));
        Vec3 floor = Ground.below(l, pt, 3);
        double y = Math.max(pt.y, floor.y + HALF + 0.1);
        Mirror m = new Mirror();
        m.c = new Vec3(pt.x, y, pt.z);
        Vec3 n = new Vec3(p.getX() - m.c.x, 0, p.getZ() - m.c.z);
        if (n.lengthSqr() < 1e-4) n = new Vec3(-look.x, 0, -look.z);
        if (n.lengthSqr() < 1e-4) n = new Vec3(0, 0, 1);
        m.n = n.normalize();
        m.u = new Vec3(-m.n.z, 0, m.n.x);
        m.ret = alt;
        MIRRORS.put(p.getUUID(), m);
        int col = col(alt);
        UUID owner = p.getUUID();

        FxNet.panel(l, m.panelId, m.c, m.n, (float) HALF, col, 40);
        FxNet.flare(l, m.c, 4F, col, 14);
        FxNet.shockwave(l, new Vec3(m.c.x, floor.y + 0.05, m.c.z), 4F, col, 16);
        FxNet.beam(l, p.getEyePosition().subtract(0, 0.3, 0), m.c, col, 0.1F, 8);
        Fx.sound(l, m.c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, 1.2F);
        Fx.sound(l, m.c, SoundEvents.BEACON_ACTIVATE, 0.5F, 1.8F);
        Fx.bar(p, Stone.SPACE, alt ? K + "return" : K + "reflect");

        Tasks.add(new Tasks.Task() {
            @Override
            public boolean tick(int age) {
                if (m.dead || age > LIFE || p.hasDisconnected() || MIRRORS.get(owner) != m) return false;
                if (age % 30 == 0) {
                    FxNet.panel(l, m.panelId, m.c, m.n, (float) HALF, col, 40);
                    if (m.ret) FxNet.portal(l, m.portalId, m.c, m.n, 1.6F, col, 40);
                    FxNet.runes(l, m.runeId, new Vec3(m.c.x, floor.y + 0.03, m.c.z), 2.6F, col, 40, m.ret ? -1.5F : 1.5F);
                }
                if (age % 3 == 0) {
                    double a = l.random.nextDouble() * 2 - 1, b = l.random.nextDouble() * 2 - 1;
                    Vec3 s = m.c.add(m.u.scale(a * HALF)).add(0, b * HALF, 0);
                    l.sendParticles(Fx.light(Stone.SPACE, 0.7F), s.x, s.y, s.z, 1, 0, 0, 0, 0);
                }
                m.cool.replaceAll((k, v) -> v - 1);
                m.cool.values().removeIf(v -> v <= 0);
                scan(l, p, m);
                return true;
            }

            @Override
            public void end() {
                MIRRORS.remove(owner, m);
                FxNet.kill(l, m.c, m.panelId); FxNet.kill(l, m.c, m.portalId); FxNet.kill(l, m.c, m.runeId);
                FxNet.flare(l, m.c, 2.5F, col, 10);
                Fx.at(l, ParticleTypes.END_ROD, m.c, 20, 1.0, 0.05);
                Fx.sound(l, m.c, SoundEvents.GLASS_BREAK, 0.5F, 1.4F);
            }
        });
        return true;
    }

    /** Did e cross the panel between its last and next position? Returns the crossing point or null. */
    private static Vec3 crossing(Mirror m, Vec3 a, Vec3 b) {
        double d0 = a.subtract(m.c).dot(m.n), d1 = b.subtract(m.c).dot(m.n);
        if (Math.signum(d0) == Math.signum(d1) && Math.abs(d1) > 0.25) return null;
        double t = Math.abs(d0 - d1) < 1e-6 ? 1 : d0 / (d0 - d1);
        Vec3 x = a.lerp(b, Math.max(0, Math.min(1, t)));
        Vec3 rel = x.subtract(m.c);
        if (Math.abs(rel.dot(m.u)) > HALF || Math.abs(rel.y) > HALF) return null;
        return x;
    }

    private static void scan(ServerLevel l, ServerPlayer p, Mirror m) {
        AABB box = new AABB(m.c.subtract(HALF + 4, HALF + 4, HALF + 4), m.c.add(HALF + 4, HALF + 4, HALF + 4));
        int col = col(m.ret);
        for (Entity e : l.getEntities(p, box, e -> e.isAlive() && !e.isSpectator())) {
            if (m.cool.containsKey(e.getId())) continue;
            boolean proj = e instanceof Projectile;
            Vec3 now = proj ? e.position() : e.getBoundingBox().getCenter();
            Vec3 prev = new Vec3(e.xo, e.yo, e.zo).add(now.subtract(e.position()));
            Vec3 x = crossing(m, prev, proj ? now.add(e.getDeltaMovement()) : now);
            if (x == null) continue;
            if (!m.ret) {
                if (!(e instanceof Projectile pr)) continue;
                Entity shooter = pr.getOwner();
                if (shooter != null && Rules.isAlly(p, shooter)) continue;
                Vec3 v = pr.getDeltaMovement();
                double speed = Math.min(4.0, Math.max(0.6, v.length() * 2));
                Vec3 dir;
                if (shooter != null && shooter.isAlive() && shooter.level() == l) dir = shooter.getEyePosition().subtract(pr.position()).normalize();
                else dir = v.subtract(m.n.scale(2 * v.dot(m.n))).normalize();
                pr.setDeltaMovement(dir.scale(speed));
                pr.setOwner(p);
                pr.hasImpulse = true;
                pr.hurtMarked = true;
                m.cool.put(e.getId(), 10);
                FxNet.flare(l, x, 2F, col, 10);
                FxNet.shockwave(l, x, 1.2F, col, 8);
                Fx.at(l, ParticleTypes.ELECTRIC_SPARK, x, 12, 0.2, 0.25);
                Fx.sound(l, x, SoundEvents.AMETHYST_BLOCK_HIT, 0.7F, 1.8F);
                Fx.sound(l, x, SoundEvents.SHIELD_BLOCK, 0.5F, 1.4F);
            } else {
                if (e == p || (e instanceof LivingEntity && Rules.isBoss(e)) || p.level() != l || com.infinitystones.tensura.skill.ability.extra.space.SpiritualGathering.isTrapped(e)) continue;
                if (!(proj || e instanceof ItemEntity || e instanceof LivingEntity)) continue;
                Vec3 back = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
                if (back.lengthSqr() < 1e-4) back = new Vec3(0, 0, 1);
                back = back.normalize();
                Vec3 out = p.position().subtract(back.scale(2));
                if (proj || e instanceof ItemEntity) out = out.add(0, 1.2, 0);
                else if (!Ground.fits(l, e, out)) { Vec3 s = Ground.safeAlong(l, e, p.position(), out); if (s == null) continue; out = s; }
                FxNet.flare(l, x, 1.8F, col, 10);
                Fx.at(l, ParticleTypes.REVERSE_PORTAL, x, 20, 0.3, 0.1);
                if (e.isPassenger()) e.stopRiding();
                e.teleportTo(out.x, out.y, out.z);
                e.setYRot(p.getYRot()); e.setYHeadRot(p.getYRot());
                if (proj) e.setDeltaMovement(back.scale(Math.max(0.5, e.getDeltaMovement().length())));
                else e.setDeltaMovement(Vec3.ZERO);
                e.resetFallDistance();
                e.hurtMarked = true;
                m.cool.put(e.getId(), 30);
                FxNet.portal(l, 0, out.add(0, e.getBbHeight() / 2, 0), back, 1.1F, col, 14);
                Fx.sound(l, out, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.6F, 1.3F);
            }
        }
    }
}
