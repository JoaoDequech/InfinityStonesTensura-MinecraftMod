package com.infinitystones.tensura.skill.ability.extra.fear.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** A short-lived scripted scare: appears at the edge of view, rushes or looms, and bursts into smoke. */
public final class Apparition extends FearEntity {
    public enum Type {
        RUSHER(.8F, 2.1F, false), CRAWLER(1.0F, .8F, true), SCREAMER(1.0F, 1.6F, false), HANGING(.7F, 1.9F, false),
        SHADOW(.8F, 2.2F, true), BRIDE(.8F, 2.0F, false), CHILD(.6F, 1.3F, true);

        public final float width, height;
        public final boolean grounded;

        Type(float w, float h, boolean g) { width = w; height = h; grounded = g; }

        public String id() { return name().toLowerCase(Locale.ROOT); }

        public static Type byId(String id) {
            for (Type t : values()) if (t.id().equals(id)) return t;
            return null;
        }
    }

    public final Type type;
    private int t, trigger = -1, obsTicks, blinks;
    private Vec3 anchor = Vec3.ZERO;

    public Apparition(EntityType<? extends Apparition> et, Level level) {
        super(et, level);
        this.type = FearMobs.appTypeOf(et);
        if (type != null && !type.grounded) { noPhysics = true; setNoGravity(true); }
    }

    @Override
    protected PathNavigation createNavigation(Level level) { return new GroundPathNavigation(this, level); }

    /** Places the apparition for the victim; returns false when no place was found. */
    public boolean begin(ServerPlayer v) {
        ServerLevel sl = (ServerLevel) level();
        Vec3 look = v.getViewVector(1F).multiply(1, 0, 1);
        Vec3 ln = look.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : look.normalize();
        Vec3 p = null;
        for (int i = 0; i < 24 && p == null; i++) {
            double off, r;
            switch (type) {
                case RUSHER -> { off = Math.toRadians(100 + random.nextInt(80)) * (random.nextBoolean() ? 1 : -1); r = 17 + random.nextInt(6); }
                case CRAWLER -> { off = Math.toRadians(80 + random.nextInt(100)) * (random.nextBoolean() ? 1 : -1); r = 14 + random.nextInt(6); }
                case HANGING -> { off = Math.toRadians(random.nextInt(60) - 30); r = 6 + random.nextInt(4); }
                case SHADOW -> { off = Math.toRadians(55 + random.nextInt(30)) * (random.nextBoolean() ? 1 : -1); r = 11 + random.nextInt(5); }
                case BRIDE -> { off = Math.toRadians(random.nextInt(50) - 25); r = 15 + random.nextInt(4); }
                case CHILD -> { off = Math.toRadians(100 + random.nextInt(70)) * (random.nextBoolean() ? 1 : -1); r = 10 + random.nextInt(5); }
                default -> { off = 0; r = 5; }
            }
            double dx = ln.x * Math.cos(off) - ln.z * Math.sin(off), dz = ln.x * Math.sin(off) + ln.z * Math.cos(off);
            double x = v.getX() + dx * r, z = v.getZ() + dz * r;
            if (type == Type.SCREAMER) { x = v.getX() + ln.x * 5; z = v.getZ() + ln.z * 5; }
            p = FearMobs.ground(sl, this, x, v.getY(), z, type.width, type.height);
            if (p != null && type == Type.SCREAMER) p = new Vec3(x, v.getEyeY() - 1.0, z);
        }
        if (p == null) return false;
        if (type == Type.BRIDE) p = p.add(0, .6, 0);
        if (type == Type.HANGING) {
            p = p.add(0, 1.2, 0);
            int up = 0;
            BlockPos bp = BlockPos.containing(p).above(2);
            while (up < 18 && sl.getBlockState(bp.above(up)).getCollisionShape(sl, bp.above(up)).isEmpty()) up++;
            setVariant(Math.min(up + 2, 16) * 16);
        }
        moveTo(p.x, p.y, p.z, 0, 0);
        anchor = p;
        faceVictim(v);
        setShown(true);
        act(A_APPEAR);
        Fx.stinger(sl, p, 1F);
        if (type == Type.CHILD) { faceAway(v); }
        return true;
    }

    private void faceVictim(ServerPlayer v) {
        float yaw = (float) (Mth.atan2(v.getZ() - getZ(), v.getX() - getX()) * Mth.RAD_TO_DEG) - 90F;
        setYRot(yaw); yBodyRot = yaw; yHeadRot = yaw; yRotO = yaw; yBodyRotO = yaw;
    }

    private void faceAway(ServerPlayer v) {
        float yaw = (float) (Mth.atan2(v.getZ() - getZ(), v.getX() - getX()) * Mth.RAD_TO_DEG) + 90F;
        setYRot(yaw); yBodyRot = yaw; yHeadRot = yaw; yRotO = yaw; yBodyRotO = yaw;
    }

    private void end(ServerLevel sl, boolean gore, Vec3 p) {
        Fx.burst(sl, p, gore);
        discard();
    }

    @Override
    protected void serverTick(ServerPlayer v) {
        ServerLevel sl = (ServerLevel) level();
        t++;
        if (t > 260) { end(sl, false, position()); return; }
        Vec3 pos = position();
        Vec3 eye = v.getEyePosition();
        double d = pos.distanceTo(v.position());
        boolean obs = observedBy(v);
        getLookControl().setLookAt(v, 90F, 90F);
        switch (type) {
            case RUSHER -> {
                if (t < 10) { act(A_APPEAR); faceVictim(v); break; }
                act(A_IDLE); setFast(true); faceVictim(v);
                if (t % 4 == 0) Fx.play(sl, pos, FearSounds.DRONE, .9F, 1.4F);
                Vec3 to = new Vec3(v.getX(), v.getY() + .2, v.getZ()).subtract(pos);
                if (to.length() < 1.7) {
                    Fx.play(sl, pos, FearSounds.SCREAM_A, 1.8F, 1.25F);
                    Fx.play(sl, pos, SoundEvents.WARDEN_ROAR, .8F, 1.5F);
                    end(sl, true, pos); return;
                }
                setPos(pos.add(to.normalize().scale(1.05)));
            }
            case CRAWLER -> {
                if (t < 8) { act(A_APPEAR); faceVictim(v); break; }
                act(A_IDLE); speed(.42);
                getNavigation().moveTo(v, 1.0);
                if (t % 7 == 0) Fx.play(sl, pos, FearSounds.CLICK, .8F, .7F + random.nextFloat() * .5F);
                if (t % 22 == 0) Fx.play(sl, pos, FearSounds.BREATH, .8F, .8F);
                if (d < 2.2) { Fx.play(sl, pos, FearSounds.SCREAM_B, 1.6F, 1.1F); end(sl, true, pos.add(0, .3, 0)); }
            }
            case SCREAMER -> {
                faceVictim(v);
                Vec3 dir = eye.subtract(pos.add(0, 1.0, 0));
                if (t < 6) { act(A_APPEAR); break; }
                if (t < 15) {
                    float f = Mth.clamp((t - 6) / 8F, 0F, 1F);
                    Vec3 target = eye.add(dir.normalize().scale(-1.3));
                    Vec3 start = anchor.add(0, 1.0, 0);
                    Vec3 cur = start.lerp(target, f * f);
                    setPos(cur.x, cur.y - 1.0, cur.z);
                    act(A_SCREAM);
                    if (t == 8) { Fx.scream(sl, pos, Kind.WENDIGO); Fx.play(sl, pos, FearSounds.SCREAM_B, 1.8F, 1.6F); }
                }
                if (t >= 16) end(sl, true, pos.add(0, 1, 0));
            }
            case HANGING -> {
                act(trigger < 0 ? A_IDLE : A_SCREAM);
                faceVictim(v);
                if (t % 30 == 0) Fx.play(sl, pos, SoundEvents.CHAIN_STEP, .7F, .5F);
                if (t % 50 == 0) Fx.play(sl, pos, FearSounds.BREATH, .5F, .6F);
                obsTicks = obs ? obsTicks + 1 : 0;
                if (trigger < 0 && t > 40 && (obsTicks > 12 || t > 80)) {
                    trigger = t;
                    Fx.play(sl, pos, FearSounds.SCREAM_LOW, 1.7F, 1.1F);
                    Fx.play(sl, pos, SoundEvents.ROOTED_DIRT_BREAK, 1F, .5F);
                }
                if (trigger >= 0 && t - trigger > 22) end(sl, true, pos.add(0, 1, 0));
            }
            case SHADOW -> {
                faceVictim(v);
                if (t < 10) { act(A_APPEAR); break; }
                act(A_IDLE);
                obsTicks = obs ? obsTicks + 1 : 0;
                if (blinks == 0 && obsTicks > 10) { blinks = 1; trigger = t; }
                if (blinks > 0 && blinks < 4 && (t - trigger) % 3 == 0) {
                    double f = blinks == 1 ? .55 : blinks == 2 ? .3 : .1;
                    Vec3 dir = v.position().subtract(anchor);
                    Vec3 n = FearMobs.ground(sl, this, anchor.x + dir.x * (1 - f), v.getY(), anchor.z + dir.z * (1 - f), type.width, type.height);
                    if (n != null) { setPos(n); faceVictim(v); }
                    Fx.play(sl, position(), FearSounds.CLICK, 1F, .5F);
                    blinks++;
                }
                if (blinks == 4) {
                    act(A_SCREAM);
                    Fx.scream(sl, position(), Kind.SMILER);
                    end(sl, true, position().add(0, 1, 0)); return;
                }
                if (blinks == 0 && t > 120) { setShown(false); if (t > 132) end(sl, false, pos); }
            }
            case BRIDE -> {
                faceVictim(v);
                if (t < 10) { act(A_APPEAR); break; }
                if (trigger < 0) {
                    act(A_IDLE);
                    Vec3 to = new Vec3(v.getX(), pos.y, v.getZ()).subtract(pos);
                    setPos(pos.add(to.normalize().scale(.12)).add(0, Math.sin(t * .15) * .01, 0));
                    if (t % 24 == 0) Fx.play(sl, pos, FearSounds.WHISPER, .9F, .8F);
                    if (t % 40 == 0) Fx.play(sl, pos, SoundEvents.GHAST_AMBIENT, .8F, .5F);
                    if (d < 3.2) {
                        trigger = t;
                        Fx.play(sl, pos, FearSounds.SCREAM_B, 1.8F, 1.3F);
                        Fx.play(sl, pos, SoundEvents.GHAST_SCREAM, 1F, .6F);
                    }
                } else {
                    act(A_SCREAM);
                    setPos(pos.add(0, .25, 0).add(eye.subtract(pos).normalize().scale(.1)));
                    if (t - trigger > 12) end(sl, false, pos.add(0, 1, 0));
                }
            }
            case CHILD -> {
                if (t < 8) { act(A_APPEAR); break; }
                if (t < 40) {
                    act(A_IDLE);
                    if (t % 14 == 0) { Fx.play(sl, pos, SoundEvents.VEX_AMBIENT, .7F, 1.9F); Fx.play(sl, pos, FearSounds.WHISPER, .5F, 1.6F); }
                } else if (t < 58) {
                    act(A_STARE); faceVictim(v);
                    if (t == 40) Fx.play(sl, pos, FearSounds.CLICK, 1F, .5F);
                } else {
                    act(A_IDLE); speed(.5);
                    getNavigation().moveTo(v, 1.0);
                    if (t % 5 == 0) Fx.play(sl, pos, FearSounds.CLICK, .7F, 1.2F);
                    if (d < 1.9) { Fx.play(sl, pos, FearSounds.SCREAM_B, 1.7F, 1.45F); end(sl, true, pos.add(0, .6, 0)); }
                }
            }
        }
        if (t % 3 == 0 && shown()) sl.sendParticles(ParticleTypes.SQUID_INK, getX(), getY() + type.height * .5, getZ(), 1, .2, .4, .2, .0);
    }

    private void speed(double s) {
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(s);
        setFast(true);
    }
}
