package com.infinitystones.tensura.skill.ability.extra.fear.mob;

import com.infinitystones.tensura.skill.ability.extra.Executions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A themed creature. SCARE mode never deals damage: it lurks at the edge of view, stalks, freezes when watched, screams, vanishes
 * and comes back. HUNT mode chases, grabs, takes small chunks and retreats until the final grab, which executes the victim.
 */
public final class FearMob extends FearEntity {
    private enum Phase { APPEAR, EMERGE, LURK, STALK, TOY, SCREAM, VANISH, HIDDEN, CHASE, WINDUP, KILL, RETREAT }

    public final Kind kind;
    private int mode = com.infinitystones.tensura.skill.ability.extra.fear.FearApi.SCARE;
    private Phase phase = Phase.APPEAR;
    private int ph, age, observedTicks, stareHold, hits, killHits = 6, huntTicks, stuck, mimicTimer = 200, ambientTimer = 120;
    private int pendingSteps, stepTimer, sinceRetreat;
    private boolean obs, retreatVanish;
    private Vec3 lastPos = Vec3.ZERO, strideFrom = Vec3.ZERO;
    private double circle;

    public FearMob(EntityType<? extends FearMob> type, Level level) {
        super(type, level);
        this.kind = FearMobs.kindOf(type);
        this.circle = random.nextDouble() * Math.PI * 2;
        this.mimicTimer = 160 + random.nextInt(300);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        Kind k = FearMobs.kindOf(getType());
        return k != null && k.climbs ? new WallClimberNavigation(this, level) : new GroundPathNavigation(this, level);
    }

    public void begin(int mode) {
        this.mode = mode;
        this.killHits = 5 + random.nextInt(3);
        setPhase(kind.emerges ? Phase.EMERGE : Phase.APPEAR, kind.emerges ? 70 : 18);
    }

    public void setMode(int m) {
        if (m == mode) return;
        mode = m;
        hits = 0; huntTicks = 0; killHits = 5 + random.nextInt(3);
        if (phase == Phase.HIDDEN || phase == Phase.VANISH || phase == Phase.APPEAR || phase == Phase.EMERGE) return;
        if (m == com.infinitystones.tensura.skill.ability.extra.fear.FearApi.HUNT) setPhase(Phase.CHASE, 0);
        else setPhase(Phase.LURK, 40 + random.nextInt(60));
    }

    private boolean hunting() { return mode == com.infinitystones.tensura.skill.ability.extra.fear.FearApi.HUNT; }

    private void setPhase(Phase p, int ticks) {
        phase = p; ph = ticks;
        switch (p) {
            case APPEAR -> { act(A_APPEAR); setShown(true); setFast(false); }
            case EMERGE -> { act(A_EMERGE); setShown(true); setFast(false); }
            case LURK, STALK, TOY, CHASE -> { act(A_IDLE); setShown(true); }
            case SCREAM -> act(A_SCREAM);
            case VANISH -> act(A_VANISH);
            case WINDUP -> act(A_ATTACK);
            case KILL -> act(A_KILL);
            case RETREAT, HIDDEN -> act(A_IDLE);
        }
    }

    // ------------------------------------------------------------------ helpers
    private void face(ServerPlayer v) {
        double dx = v.getX() - getX(), dz = v.getZ() - getZ();
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
        setYRot(yaw); yBodyRot = yaw; yHeadRot = yaw; yRotO = yaw; yBodyRotO = yaw;
    }

    private void stand() { getNavigation().stop(); setFast(false); }

    private void speed(double s) {
        getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(s);
        setFast(s >= .25);
    }

    private boolean light(Vec3 p) { return level().getMaxLocalRawBrightness(BlockPos.containing(p)) <= 5; }

    private Vec3 findSpot(ServerPlayer v, double min, double max, boolean behind, boolean dark) {
        Vec3 best = null; double bestScore = -1e9;
        double base = Math.toRadians(v.getYRot() + (behind ? 180 : 0));
        for (int i = 0; i < 30; i++) {
            double ang = behind ? base + (random.nextDouble() - .5) * Math.PI * 1.3 : random.nextDouble() * Math.PI * 2;
            double r = min + random.nextDouble() * (max - min);
            double x = v.getX() - Math.sin(ang) * r, z = v.getZ() + Math.cos(ang) * r;
            Vec3 g = ground(x, v.getY(), z);
            if (g == null) continue;
            double score = random.nextDouble() * 1.5;
            int bl = level().getMaxLocalRawBrightness(BlockPos.containing(g));
            if (dark) score += (15 - bl) * .35;
            if (kind.darkOnly && bl > 5) score -= 6;
            if (!v.hasLineOfSight(this)) score += 0;
            if (score > bestScore) { bestScore = score; best = g; }
        }
        return best;
    }

    private Vec3 ground(double x, double y0, double z) {
        return FearMobs.ground((ServerLevel) level(), this, x, y0, z, kind.width, (float) Math.ceil(kind.height));
    }

    private void blink(Vec3 to, ServerPlayer v) {
        Fx.fog((ServerLevel) level(), position(), kind.width * .5, kind.height, 12);
        getNavigation().stop();
        moveTo(to.x, to.y, to.z, getYRot(), 0);
        setDeltaMovement(Vec3.ZERO);
        Fx.fog((ServerLevel) level(), position(), kind.width * .5, kind.height, 12);
        face(v);
    }

    private void blinkCloser(ServerPlayer v) {
        Vec3 dir = v.position().subtract(position()).multiply(1, 0, 1);
        double d = dir.length();
        if (d < 4) return;
        dir = dir.normalize();
        double step = Math.min(d - 3, 3 + random.nextDouble() * 3);
        Vec3 g = ground(getX() + dir.x * step, getY(), getZ() + dir.z * step);
        if (g == null) return;
        Fx.play((ServerLevel) level(), position(), FearSounds.CLICK, .8F, 1.4F);
        blink(g, v);
    }

    // ------------------------------------------------------------------ brain
    @Override
    protected void serverTick(ServerPlayer v) {
        age++;
        ServerLevel sl = (ServerLevel) level();
        if (age % 2 == 0) obs = observedBy(v);
        double d = distanceTo(v);
        if (ph > 0) ph--;
        if (kind.climbs) setClimbing(horizontalCollision && getNavigation().isInProgress());
        if (phase != Phase.HIDDEN) {
            getLookControl().setLookAt(v, 90F, 90F);
            footsteps(sl);
            ambience(sl, v);
            stuckCheck(v);
        }
        if (kind == Kind.DOPPELGANGER) mimicPlayer(v);

        switch (phase) {
            case APPEAR -> {
                if (ph == 17) { Fx.fog(sl, position(), kind.width * .6, kind.height, 24); Fx.stinger(sl, position(), .5F); }
                if (ph == 0) endAppear();
            }
            case EMERGE -> {
                stand(); face(v);
                if (ph % 10 == 0) { Fx.play(sl, position(), SoundEvents.PLAYER_SPLASH, .8F, .6F); sl.sendParticles(ParticleTypes.SPLASH, getX(), getY() + .4, getZ(), 14, .5, .2, .5, .1); }
                if (ph == 0) endAppear();
            }
            case LURK -> {
                stand();
                observedTicks = obs ? observedTicks + 1 : 0;
                act(obs ? (kind.freezeObserved ? A_FROZEN : A_STARE) : (kind == Kind.GRAVEKEEPER || kind == Kind.MASCOT ? A_TOY : A_IDLE));
                if (obs && observedTicks == 1) face(v);
                if (kind.darkOnly && !light(position()) && obs && observedTicks > 8) { setPhase(Phase.VANISH, 26); break; }
                if (observedTicks > 70) { observedTicks = 0; setPhase(d < 26 ? Phase.SCREAM : Phase.VANISH, d < 26 ? 44 : 26); break; }
                if (ph <= 0) scareNext(v, d);
            }
            case STALK -> stalk(v, d);
            case TOY -> toy(v, d);
            case SCREAM -> {
                stand(); face(v);
                if (ph == 38) { Fx.scream(sl, position(), kind); Fx.blood(sl, position().add(0, kind.height * .8, 0), 8); }
                if (ph <= 0) setPhase(Phase.VANISH, 26);
            }
            case VANISH -> {
                stand();
                if (ph == 14) { setShown(false); Fx.burst(sl, position(), false); Fx.play(sl, position(), FearSounds.WHISPER, .6F, .7F); }
                if (ph <= 0) {
                    setPhase(Phase.HIDDEN, hunting() ? 20 + random.nextInt(50) : 70 + random.nextInt(110));
                    noPhysics = true; setNoGravity(true); setDeltaMovement(Vec3.ZERO);
                }
            }
            case HIDDEN -> {
                setShown(false);
                if (ph <= 0) reappear(v);
            }
            case CHASE -> chase(v, d);
            case WINDUP -> {
                stand(); face(v);
                if (ph == 18) strike(v);
                if (ph <= 0) retreat(v);
            }
            case KILL -> kill(v);
            case RETREAT -> {
                sinceRetreat++;
                if (ph <= 0 || position().distanceTo(v.position()) > 14) { setPhase(Phase.CHASE, 0); }
            }
        }
        lastPos = position();
    }

    private void endAppear() {
        if (hunting()) setPhase(Phase.CHASE, 0);
        else setPhase(Phase.LURK, 60 + random.nextInt(80));
    }

    private void reappear(ServerPlayer v) {
        Vec3 s = hunting() ? findSpot(v, 8, 13, false, kind.darkOnly) : findSpot(v, 12, 22, true, true);
        if (s == null) { ph = 20; return; }
        noPhysics = false; setNoGravity(false);
        moveTo(s.x, s.y, s.z, getYRot(), 0);
        getNavigation().stop();
        face(v);
        setPhase(Phase.APPEAR, 18);
    }

    private void scareNext(ServerPlayer v, double d) {
        float r = random.nextFloat();
        if (r < .5) setPhase(Phase.STALK, 180 + random.nextInt(160));
        else if (r < .75) { circle = random.nextDouble() * 6.28; setPhase(Phase.TOY, 150); }
        else setPhase(Phase.VANISH, 26);
    }

    private void stalk(ServerPlayer v, double d) {
        if (kind.freezeObserved && obs) { stand(); act(A_FROZEN); }
        else if (stareHold > 0) { stareHold--; stand(); act(A_STARE); }
        else {
            act(A_IDLE);
            speed(kind.walk * (kind.freezeObserved ? 1.8 : 1));
            getNavigation().moveTo(v, 1.0);
            if (obs && d < 30 && random.nextInt(30) == 0) stareHold = 14 + random.nextInt(26);
            if (kind.freezeObserved && !obs && d > 7 && random.nextInt(25) == 0) blinkCloser(v);
        }
        if (kind.darkOnly && obs && !light(position())) { setPhase(Phase.VANISH, 26); return; }
        if (d < kind.scareDist) setPhase(Phase.SCREAM, 44);
        else if (ph <= 0) setPhase(Phase.VANISH, 26);
    }

    private void toy(ServerPlayer v, double d) {
        circle += .035;
        double r = 4.0 + Math.sin(age * .05);
        Vec3 g = ground(v.getX() + Math.cos(circle) * r, v.getY(), v.getZ() + Math.sin(circle) * r);
        if (g != null && position().distanceTo(g) > 1.2) { act(A_IDLE); speed(kind.walk * 1.2); getNavigation().moveTo(g.x, g.y, g.z, 1.0); }
        else { stand(); act(A_TOY); face(v); }
        if (ph <= 0) setPhase(random.nextBoolean() ? Phase.SCREAM : Phase.VANISH, random.nextBoolean() ? 44 : 26);
    }

    private void chase(ServerPlayer v, double d) {
        huntTicks++;
        if (kind.freezeObserved && obs && d > 3) { stand(); act(A_FROZEN); return; }
        act(A_IDLE);
        speed(kind.chase * (kind.freezeObserved ? 1.5 : 1));
        getNavigation().moveTo(v, 1.0);
        if (kind.freezeObserved && !obs && d > 6 && random.nextInt(30) == 0) blinkCloser(v);
        if (kind.darkOnly && obs && !light(position())) { setPhase(Phase.VANISH, 26); return; }
        if (d <= kind.reach + .4 && hasLineOfSight(v)) {
            face(v);
            boolean fin = hits >= killHits || huntTicks > 4800;
            if (fin) setPhase(Phase.KILL, 56); else setPhase(Phase.WINDUP, 28);
        }
    }

    private void strike(ServerPlayer v) {
        ServerLevel sl = (ServerLevel) level();
        if (distanceTo(v) > kind.reach + 1.6) { Fx.play(sl, position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1F, .5F); return; }
        hits++;
        float dmg = Math.min(kind.bite, Math.max(0F, v.getHealth() - 1F));
        if (dmg > 0F) { v.invulnerableTime = 0; v.hurt(damageSources().mobAttack(this), dmg); }
        v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1, false, false, false));
        Fx.gore(sl, v.position().add(0, v.getBbHeight() * .6, 0));
        Fx.play(sl, position(), FearSounds.SCREAM_B, .6F, .6F);
    }

    private void retreat(ServerPlayer v) {
        retreatVanish = random.nextBoolean();
        if (retreatVanish) { setPhase(Phase.VANISH, 26); return; }
        Vec3 s = findSpot(v, 11, 15, false, kind.darkOnly);
        setPhase(Phase.RETREAT, 60 + random.nextInt(60));
        speed(kind.walk);
        if (s != null) getNavigation().moveTo(s.x, s.y, s.z, 1.0);
    }

    private void kill(ServerPlayer v) {
        ServerLevel sl = (ServerLevel) level();
        stand(); face(v);
        v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 8, 6, false, false, false));
        if (ph > 20) {
            Vec3 hand = position().add(Vec3.directionFromRotation(0, getYRot()).scale(kind.reach * .55)).add(0, kind.height * .35, 0);
            Vec3 pull = hand.subtract(v.position().add(0, v.getBbHeight() * .5, 0));
            v.setDeltaMovement(pull.scale(.22).add(0, .04, 0));
            v.hurtMarked = true;
        }
        if (ph == 26) {
            Fx.gore(sl, v.position().add(0, v.getBbHeight() * .6, 0));
            Fx.scream(sl, position(), kind);
            Executions.kill(v);
        }
        if (ph <= 0) setPhase(Phase.VANISH, 26);
    }

    // ------------------------------------------------------------------ ambience
    private void footsteps(ServerLevel sl) {
        if (!onGround() && !climbing()) return;
        Vec3 p = position();
        if (strideFrom == Vec3.ZERO) strideFrom = p;
        double stride = kind == Kind.FROSTGIANT ? 3.4 : fast() ? 2.4 : 1.5;
        if (p.distanceToSqr(strideFrom) > stride * stride) {
            strideFrom = p;
            Fx.step(sl, p, kind);
            if (kind == Kind.FROSTGIANT) sl.sendParticles(ParticleTypes.SNOWFLAKE, p.x, p.y + .2, p.z, 24, 1, .1, 1, .08);
            if (kind == Kind.DROWNED) sl.sendParticles(ParticleTypes.FALLING_WATER, p.x, p.y + .5, p.z, 6, .3, .1, .3, 0);
        }
        if (pendingSteps > 0 && --stepTimer <= 0) { pendingSteps--; stepTimer = 7; Fx.step(sl, p, Kind.DOPPELGANGER); }
    }

    private void ambience(ServerLevel sl, ServerPlayer v) {
        if (phase == Phase.APPEAR || phase == Phase.VANISH) return;
        if (--ambientTimer <= 0) { ambientTimer = 100 + random.nextInt(220); Fx.ambient(sl, position(), kind); }
        Vec3 p = position();
        switch (kind) {
            case GRAVEKEEPER -> { if (age % 3 == 0) { Vec3 l = p.add(Vec3.directionFromRotation(0, yBodyRot + 30).scale(.7)); sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, l.x, p.y + 1.2, l.z, 1, .05, .05, .05, .002); } }
            case DROWNED -> { if (age % 6 == 0) sl.sendParticles(ParticleTypes.FALLING_WATER, p.x, p.y + .8, p.z, 2, .4, .1, .4, 0); }
            case MASCOT -> { if (age % 25 == 0) sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y + 2, p.z, 3, .3, .3, .3, .1); }
            case FROSTGIANT -> { if (age % 4 == 0) sl.sendParticles(ParticleTypes.SNOWFLAKE, p.x, p.y + 2.5, p.z, 3, 1.2, 1.5, 1.2, .01); }
            case PRIEST -> { if (age % 10 == 0) sl.sendParticles(ParticleTypes.SOUL, p.x, p.y + 1.3, p.z, 1, .4, .4, .4, .01); }
            default -> {}
        }
        if (kind == Kind.WENDIGO && --mimicTimer <= 0) {
            mimicTimer = 200 + random.nextInt(360);
            switch (random.nextInt(3)) {
                case 0 -> { pendingSteps = 4 + random.nextInt(3); stepTimer = 0; }
                case 1 -> Fx.play(sl, p, SoundEvents.PLAYER_HURT, 1F, .55F + random.nextFloat() * .1F);
                default -> { Fx.play(sl, p, SoundEvents.PLAYER_BREATH, 1F, .6F); Fx.play(sl, p, FearSounds.BREATH, .6F, .9F); }
            }
        }
    }

    private void mimicPlayer(ServerPlayer v) {
        setShiftKeyDown(v.isShiftKeyDown());
        if (v.swinging && !swinging && random.nextInt(3) == 0) swing(InteractionHand.MAIN_HAND);
    }

    private void stuckCheck(ServerPlayer v) {
        if (phase != Phase.CHASE && phase != Phase.STALK) { stuck = 0; return; }
        if (age % 20 != 0) return;
        if (position().distanceToSqr(lastStuck()) < .25 && !(kind.freezeObserved && obs) && stareHold == 0) stuck++; else stuck = 0;
        stuckPos = position();
        if (stuck >= 2) { stuck = 0; blinkCloser(v); }
    }

    private Vec3 stuckPos = Vec3.ZERO;
    private Vec3 lastStuck() { return stuckPos; }
}
