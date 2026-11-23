package com.infinitystones.tensura.skill.ability.extra.power;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Seismic Wave. Left: a crest of earth (visual copies of the ground) races along the look direction at 1 block/tick
 * and launches whatever stands on it (5 damage + fall). Right: a ring around you pushing everything outward.
 * Alt+scroll: reach 10–30. The real ground is never changed.
 */
public final class SeismicWave implements Ability {
    public static final SeismicWave INSTANCE = new SeismicWave();
    static final String MODE = "power.seismic_wave";

    @Override public Stone stone() { return Stone.POWER; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        ServerLevel l = p.serverLevel();
        int range = ExtraSettings.get(p, MODE, 20, 10, 30, 5);
        Vec3 o = p.position();
        PowerNet.circle(l, 0, o.add(0, 0.06, 0), new Vec3(0, 1, 0), 2.8F, PowerUtil.COLOR, PowerUtil.ACCENT, 26, 4F, PowerNet.SIMPLE);
        FxNet.shake(l, o, 20 + range, 1.6F, 18);
        Fx.sound(l, o, SoundEvents.WARDEN_ATTACK_IMPACT, 0.7F, 0.5F);
        Fx.sound(l, o, SoundEvents.DRIPSTONE_BLOCK_BREAK, 0.7F, 0.5F);
        Set<Integer> hit = new HashSet<>();
        if (!alt) {
            Vec3 look = p.getLookAngle();
            Vec3 dir = new Vec3(look.x, 0, look.z);
            if (dir.lengthSqr() < 1e-4) dir = Vec3.directionFromRotation(0, p.getYRot());
            Vec3 d = dir.normalize(), side = new Vec3(-d.z, 0, d.x);
            Tasks.add(a -> {
                double dist = 1.5 + a;
                Vec3 c = Ground.below(l, o.add(d.scale(dist)).add(0, 3, 0), 8);
                for (int s = -1; s <= 1; s++) crest(l, c.add(side.scale(s)), s == 0 ? 1.0F : 0.75F, 0.55 + (s == 0 ? 0.15 : 0));
                l.sendParticles(Fx.dust(Stone.POWER, 2F), c.x, c.y + 0.3, c.z, 6, 1, 0.2, 1, 0);
                if (a % 4 == 0) Fx.sound(l, c, SoundEvents.ROOTED_DIRT_BREAK, 0.6F, 0.5F + l.random.nextFloat() * 0.3F);
                for (LivingEntity e : PowerUtil.victims(p, l, c.add(0, 0.8, 0), 2.0)) {
                    if (!hit.add(e.getId())) continue;
                    Rules.hurt(p, e, 5);
                    e.setDeltaMovement(e.getDeltaMovement().add(d.x * 0.3, 1.05, d.z * 0.3));
                    e.hurtMarked = true;
                }
                return dist < range;
            });
        } else {
            FxNet.shockwave(l, o.add(0, 0.1, 0), range, PowerUtil.COLOR, range + 6);
            Tasks.add(a -> {
                double rr = 1.5 + a;
                int n = Math.min(24, (int) (rr * 2.2));
                for (int k = 0; k < n; k++) {
                    double an = Math.PI * 2 * k / n + a * 0.37;
                    Vec3 c = Ground.below(l, o.add(Math.cos(an) * rr, 3, Math.sin(an) * rr), 8);
                    if (k % 2 == 0) crest(l, c, 0.8F, 0.5);
                    else l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, PowerUtil.surface(l, c)), c.x, c.y + 0.2, c.z, 4, 0.3, 0.1, 0.3, 0.15);
                }
                if (a % 4 == 0) Fx.sound(l, o, SoundEvents.ROOTED_DIRT_BREAK, 0.6F, 0.5F);
                for (LivingEntity e : PowerUtil.victims(p, l, o, rr + 1)) {
                    if (!hit.add(e.getId())) continue;
                    if (e.position().distanceTo(o) < rr - 1.5) { hit.remove(e.getId()); continue; }
                    Rules.hurt(p, e, 5);
                    PowerUtil.push(e, o, 1.3, 0.55);
                }
                return rr < range;
            });
        }
        return true;
    }

    /** A block copy of the ground jumps up and falls back (visual), real block dust, grass and leaves pop. */
    private static void crest(ServerLevel l, Vec3 at, float scale, double up) {
        BlockState st = PowerUtil.surface(l, at);
        PowerUtil.debris(l, at.add(0, 0.2, 0), st, new Vec3((l.random.nextDouble() - 0.5) * 0.05, up, (l.random.nextDouble() - 0.5) * 0.05), scale, 16, 0);
        l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, st), at.x, at.y + 0.2, at.z, 6, 0.4, 0.1, 0.4, 0.15);
        if (l.random.nextInt(3) == 0) l.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.3, at.z, 1, 0.3, 0.1, 0.3, 0.01);
    }
}
