package com.infinitystones.tensura.skill.ability.extra.v2a;

import com.infinitystones.tensura.skill.ability.extra.Displays;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.Priv;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Small helpers shared by the v2a abilities. */
final class Bits {
    static final int PURPLE = 0xA43CE6, ACCENT = 0xF0C4FF, DEEP = 0x5B17A0, HOT = 0xFF6BD8, GOLD = 0xFFC64A;

    /** Harmable living entities within r of c. */
    static List<LivingEntity> victims(ServerPlayer p, ServerLevel l, Vec3 c, double r) {
        return l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                e -> Rules.isLiving(e) && Rules.canHarm(p, e) && e.getBoundingBox().getCenter().distanceToSqr(c) <= r * r);
    }

    /** A sound everyone within range hears (played near each listener, in the direction of the source). */
    static void farSound(ServerLevel l, Vec3 at, Holder<SoundEvent> s, float vol, float pitch, double range) {
        for (ServerPlayer sp : l.players()) {
            Vec3 ear = sp.getEyePosition();
            double d = ear.distanceTo(at);
            if (d > range) continue;
            Vec3 pos = d < 14 ? at : ear.add(at.subtract(ear).normalize().scale(10));
            float v = (float) Math.min(0.7, vol * (d < 14 ? 1 : 1 - d / range * 0.6));
            Priv.sound(sp, s, pos, v, pitch);
        }
    }

    static void farSound(ServerLevel l, Vec3 at, SoundEvent s, float vol, float pitch, double range) {
        farSound(l, at, Holder.direct(s), vol, pitch, range);
    }

    /** Look of the ground at v (a solid, rendered block), dirt when nothing fits. */
    static BlockState surface(ServerLevel l, Vec3 v) {
        BlockPos fp = Ground.floorBlock(l, v.add(0, 3, 0), 8);
        if (fp == null) return Blocks.DIRT.defaultBlockState();
        BlockState s = l.getBlockState(fp);
        if (s.getRenderShape() != RenderShape.MODEL || s.hasBlockEntity() || !s.getFluidState().isEmpty()) return Blocks.DIRT.defaultBlockState();
        return s;
    }

    /** Visual debris (block display) thrown with velocity vel, falling under gravity, spinning; removed after life ticks. */
    static void debris(ServerLevel l, Vec3 from, BlockState st, Vec3 vel, float scale, int life) {
        Display.BlockDisplay d = Displays.block(l, st, from, scale, 0, 2);
        if (d == null) return;
        final float spinY = (l.random.nextFloat() - 0.5F) * 0.9F, spinX = (l.random.nextFloat() - 0.5F) * 0.9F;
        Tasks.add(new Tasks.Task() {
            Vec3 pos = from, v = vel;
            boolean landed; int landAge;
            @Override public boolean tick(int age) {
                if (d.isRemoved()) return false;
                if (!landed) {
                    v = v.add(0, -0.08, 0).scale(0.98);
                    Vec3 next = pos.add(v);
                    BlockPos bp = BlockPos.containing(next);
                    if (v.y < 0 && !l.getBlockState(bp).getCollisionShape(l, bp).isEmpty()) {
                        landed = true; landAge = age;
                        l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), pos.x, pos.y, pos.z, 6, 0.3, 0.2, 0.3, 0.1);
                    } else {
                        pos = next;
                        d.setPos(pos.x, pos.y, pos.z);
                    }
                    if (age % 4 == 0) Displays.pose(d, scale, spinY * age, spinX * age, 4);
                }
                if (landed && age % 4 == 0) Displays.pose(d, scale * Math.max(0.05F, 1 - (age - landAge) / 24F), spinY * landAge, spinX * landAge, 4);
                if (landed && age - landAge > 24) return false;
                return age < life;
            }
            @Override public void end() { Displays.remove(d); }
        });
    }

    /** Push e away from c horizontally (h) and up; synced to players. */
    static void push(LivingEntity e, Vec3 c, double h, double up) {
        Vec3 d = e.position().subtract(c);
        Vec3 dir = new Vec3(d.x, 0, d.z);
        dir = dir.lengthSqr() < 1e-4 ? Vec3.ZERO : dir.normalize();
        double kb = 1 - e.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE) * 0.7;
        e.setDeltaMovement(e.getDeltaMovement().add(dir.scale(h * kb)).add(0, up * kb, 0));
        e.hurtMarked = true;
    }

    /** Puts any entity (players included) at a position without leaving it moving. */
    static void place(Entity e, Vec3 to) {
        if (e instanceof ServerPlayer sp) sp.connection.teleport(to.x, to.y, to.z, sp.getYRot(), sp.getXRot());
        else {
            e.moveTo(to.x, to.y, to.z, e.getYRot(), e.getXRot());
            if (e instanceof Mob m) m.getNavigation().stop();
        }
        e.setDeltaMovement(Vec3.ZERO);
        e.hurtMarked = true;
        e.fallDistance = 0;
    }

    private Bits() {}
}
