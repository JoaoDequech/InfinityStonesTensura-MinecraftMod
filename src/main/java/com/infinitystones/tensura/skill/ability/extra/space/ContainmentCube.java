package com.infinitystones.tensura.skill.ability.extra.space;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Cubo de Contenção / Containment Cube (replaces the Event Horizon projectile node). Left: a translucent blue cube
 * with glowing edges closes around the aimed living target (no bosses) for 10 s — the prisoner is kept inside,
 * projectiles ricochet off the walls, no fall damage. Left again releases. Right: drags the cube (and the prisoner)
 * to where you look (≤ 24 blocks). A teleport out of the cube (&gt; 10 blocks) breaks it.
 */
public final class ContainmentCube implements Ability {
    public static final ContainmentCube INSTANCE = new ContainmentCube();
    private static final String K = "infinity.extra.containment_cube.";
    private static final int LIFE = 200;

    private static final class Cube {
        LivingEntity target; Vec3 centre; double half, hy;
        Vec3 dragFrom, dragTo; int dragAge = -1;
        boolean released;
        final int cubeId = FxNet.newId(), runeId = FxNet.newId(), chainId = FxNet.newId();
    }

    private static final Map<UUID, Cube> CUBES = new HashMap<>();
    private static final Set<UUID> CAGED = new HashSet<>();

    @Override public Stone stone() { return Stone.SPACE; }

    private static int col() { return Stone.SPACE.color() & 0xFFFFFF; }

    /** Is this entity held in somebody's cube? */
    public static boolean isCaged(Entity e) { return CAGED.contains(e.getUUID()); }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        Cube c = CUBES.get(p.getUUID());
        if (alt) {
            if (c == null) return Fx.fail(p, K + "none");
            Vec3 to = Aim.point(p, 24);
            Vec3 feet = Ground.below(p.serverLevel(), to.add(0, 0.2, 0), 4);
            if (!Ground.fits(p.serverLevel(), c.target, feet)) {
                Vec3 s = Ground.safeAlong(p.serverLevel(), c.target, c.centre.subtract(0, c.hy, 0), feet);
                if (s == null) return Fx.fail(p, K + "blocked");
                feet = s;
            }
            c.dragFrom = c.centre;
            c.dragTo = feet.add(0, c.hy, 0);
            c.dragAge = 0;
            ServerLevel l = p.serverLevel();
            FxNet.beam(l, p.position().add(0, 1.2, 0), c.centre, col(), 0.12F, 12);
            Fx.sound(l, c.centre, SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.5F);
            return true;
        }
        if (c != null) { c.released = true; return true; }
        LivingEntity t = Aim.living(p, 32, e -> Rules.canControl(p, e) && !isCaged(e));
        if (t == null) return Fx.fail(p, K + "no_target");
        trap(p, t);
        return true;
    }

    private static void trap(ServerPlayer p, LivingEntity t) {
        ServerLevel l = p.serverLevel();
        Cube c = new Cube();
        c.target = t;
        c.hy = t.getBbHeight() / 2;
        c.half = Math.max(1.0, Math.max(t.getBbWidth(), t.getBbHeight()) / 2 + 0.3);
        c.centre = t.position().add(0, c.hy, 0);
        CUBES.put(p.getUUID(), c);
        CAGED.add(t.getUUID());
        UUID owner = p.getUUID();
        if (t.isPassenger()) t.stopRiding();
        t.ejectPassengers();

        FxNet.flare(l, c.centre, 3.5F, col(), 14);
        FxNet.shockwave(l, c.centre.subtract(0, c.hy - 0.05, 0), (float) c.half * 3, col(), 16);
        FxNet.flashAround(l, c.centre, 8, col(), 0.25F, 6);
        FxNet.shake(l, c.centre, 10, 0.5F, 6);
        Fx.shell(l, c.centre, c.half * 1.7, 50, Fx.dust(Stone.SPACE, 1.3F));
        Fx.sound(l, c.centre, SoundEvents.BEACON_ACTIVATE, 0.6F, 1.6F);
        Fx.sound(l, c.centre, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, 0.6F);
        if (t instanceof ServerPlayer sp) { FxNet.flash(sp, col(), 0.3F, 10); Fx.barGray(sp, K + "trapped_you"); }

        Tasks.add(new Tasks.Task() {
            @Override
            public boolean tick(int age) {
                if (c.released || age > LIFE || !t.isAlive() || t.isRemoved() || t.level() != l || p.hasDisconnected() || CUBES.get(owner) != c) return false;
                if (t.position().add(0, c.hy, 0).distanceTo(c.centre) > 10) {          // teleported out
                    if (!p.hasDisconnected()) Fx.barGray(p, K + "escaped");
                    return false;
                }
                // drag
                if (c.dragAge >= 0) {
                    float k = Math.min(1F, ++c.dragAge / 12F), e = 1 - (1 - k) * (1 - k);
                    c.centre = c.dragFrom.lerp(c.dragTo, e);
                    FxNet.cube(l, c.cubeId, c.centre, (float) c.half, col(), 30, -1);
                    if (age % 2 == 0) Fx.at(l, ParticleTypes.REVERSE_PORTAL, c.centre, 6, c.half * 0.6, 0.02);
                    if (k >= 1) { c.dragAge = -1; FxNet.shockwave(l, c.centre.subtract(0, c.hy - 0.05, 0), (float) c.half * 2.5F, col(), 12); }
                }
                hold(c);
                ricochet(l, c, p);
                if (age % 20 == 0 && c.dragAge < 0) {
                    FxNet.cube(l, c.cubeId, c.centre, (float) c.half, col(), 30, -1);
                    FxNet.runes(l, c.runeId, c.centre.subtract(0, c.hy - 0.03, 0), (float) c.half * 1.6F, col(), 30, 2F);
                    FxNet.chain(l, c.chainId, p, c.centre, null, col(), 30);
                }
                if (age % 4 == 0) edges(l, c);
                if (age == LIFE - 40 && !p.hasDisconnected()) Fx.barGray(p, K + "fading");
                return true;
            }

            @Override
            public void end() {
                CUBES.remove(owner, c);
                CAGED.remove(t.getUUID());
                FxNet.kill(l, c.centre, c.cubeId); FxNet.kill(l, c.centre, c.runeId); FxNet.kill(l, c.centre, c.chainId);
                FxNet.flare(l, c.centre, 2.6F, col(), 12);
                Fx.at(l, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState()), c.centre, 60, c.half, 0.1);
                Fx.sound(l, c.centre, SoundEvents.GLASS_BREAK, 0.7F, 0.7F);
                Fx.sound(l, c.centre, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.6F, 0.9F);
            }
        });
    }

    /** Keeps the prisoner's box inside the cube; no momentum, no fall damage. */
    private static void hold(Cube c) {
        LivingEntity t = c.target;
        double hw = t.getBbWidth() / 2, h = t.getBbHeight();
        double minX = c.centre.x - c.half + hw, maxX = c.centre.x + c.half - hw;
        double minZ = c.centre.z - c.half + hw, maxZ = c.centre.z + c.half - hw;
        double minY = c.centre.y - c.half, maxY = c.centre.y + c.half - h;
        Vec3 pos = t.position();
        double x = clamp(pos.x, minX, maxX), y = clamp(pos.y, minY, Math.max(minY, maxY)), z = clamp(pos.z, minZ, maxZ);
        if (Math.abs(x - pos.x) + Math.abs(y - pos.y) + Math.abs(z - pos.z) > 0.02) {
            t.teleportTo(x, y, z);
            t.setDeltaMovement(Vec3.ZERO);
            t.hurtMarked = true;
        }
        t.resetFallDistance();
    }

    private static double clamp(double v, double lo, double hi) { return lo > hi ? (lo + hi) / 2 : Math.max(lo, Math.min(hi, v)); }

    /** Projectiles crossing a wall (in or out) bounce back. */
    private static void ricochet(ServerLevel l, Cube c, ServerPlayer owner) {
        AABB box = new AABB(c.centre.subtract(c.half, c.half, c.half), c.centre.add(c.half, c.half, c.half));
        for (Projectile pr : l.getEntitiesOfClass(Projectile.class, box.inflate(3))) {
            Vec3 v = pr.getDeltaMovement();
            if (v.lengthSqr() < 0.01) continue;
            Vec3 a = pr.position(), b = a.add(v);
            boolean in0 = box.contains(a), in1 = box.contains(b);
            if (in0 == in1) continue;
            double vx = v.x, vy = v.y, vz = v.z;
            if ((a.x < box.minX) != (b.x < box.minX) || (a.x > box.maxX) != (b.x > box.maxX)) vx = -vx;
            if ((a.y < box.minY) != (b.y < box.minY) || (a.y > box.maxY) != (b.y > box.maxY)) vy = -vy;
            if ((a.z < box.minZ) != (b.z < box.minZ) || (a.z > box.maxZ) != (b.z > box.maxZ)) vz = -vz;
            pr.setDeltaMovement(new Vec3(vx, vy, vz).scale(0.85));
            pr.hasImpulse = true;
            pr.hurtMarked = true;
            Vec3 hit = a.lerp(b, 0.5);
            FxNet.flare(l, hit, 1.2F, col(), 8);
            Fx.at(l, ParticleTypes.ELECTRIC_SPARK, hit, 8, 0.15, 0.2);
            Fx.sound(l, hit, SoundEvents.AMETHYST_BLOCK_HIT, 0.7F, 1.6F);
        }
    }

    private static void edges(ServerLevel l, Cube c) {
        double h = c.half;
        var dust = Fx.light(Stone.SPACE, 0.7F);
        for (int i = 0; i < 6; i++) {
            double sx = l.random.nextBoolean() ? h : -h, sz = l.random.nextBoolean() ? h : -h, t = l.random.nextDouble() * 2 - 1;
            switch (l.random.nextInt(3)) {
                case 0 -> l.sendParticles(dust, c.centre.x + t * h, c.centre.y + sx, c.centre.z + sz, 1, 0, 0, 0, 0);
                case 1 -> l.sendParticles(dust, c.centre.x + sx, c.centre.y + t * h, c.centre.z + sz, 1, 0, 0, 0, 0);
                default -> l.sendParticles(dust, c.centre.x + sx, c.centre.y + sz, c.centre.z + t * h, 1, 0, 0, 0, 0);
            }
        }
    }
}
