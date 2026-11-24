package com.infinitystones.tensura.skill.ability.extra.power;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Displays;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Terrain;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Meteor Gate (conjunction Power + Space). A purple-blue rift opens 26 blocks above the aimed point (visible warning,
 * tearing sound), then 3–9 glowing magma meteors (Alt+scroll) fall one after another within 6 blocks: each digs a
 * crater (radius about 4.5, no drops, claims respected) and hits for 10% of max HP in 5 blocks, fire, shake nearby.
 */
public final class MeteorGate implements Ability {
    public static final MeteorGate INSTANCE = new MeteorGate();
    static final String MODE = "power.meteor_gate";
    static final int SPACE = 0x2F8CFF, WARN = 30, EVERY = 8, FALL = 12;

    @Override public Stone stone() { return Stone.POWER; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        ServerLevel l = p.serverLevel();
        int count = ExtraSettings.get(p, MODE, 5, 3, 9, 1);
        Vec3 g = Ground.below(l, Aim.point(p, 96).add(0, 0.2, 0), 40);
        Vec3 gate = g.add(0, 26, 0);
        int portalId = FxNet.newId(), circleId = PowerNet.newId(), groundId = PowerNet.newId();
        int total = WARN + count * EVERY + FALL + 10;
        FxNet.portal(l, portalId, gate, new Vec3(0, 1, 0), 7, SPACE, total + 10);
        PowerNet.circle(l, circleId, gate.add(0, 0.4, 0), new Vec3(0, 1, 0), 9, PowerUtil.COLOR, SPACE, total + 10, 2.5F, PowerNet.RAYS);
        PowerNet.circle(l, groundId, g.add(0, 0.08, 0), new Vec3(0, 1, 0), 6.5F, SPACE, PowerUtil.COLOR, total, -1.5F, PowerNet.SIMPLE);
        PowerUtil.farSound(l, gate, SoundEvents.END_PORTAL_SPAWN, 0.6F, 0.7F, 140);
        PowerUtil.farSound(l, gate, SoundEvents.ELYTRA_FLYING, 0.5F, 0.5F, 100);
        Fx.bar(p, Stone.POWER, "infinity.extra.power.gate.open", count);
        Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int a) {
                RandomSource rnd = l.random;
                if (a % 4 == 0) {
                    Fx.far(l, ParticleTypes.REVERSE_PORTAL, gate, 20, 4, 0.05, 140);
                    Fx.far(l, Fx.dust(SPACE, 2F), gate, 6, 5, 0, 140);
                }
                int k = (a - WARN) / EVERY;
                if (a >= WARN && (a - WARN) % EVERY == 0 && k < count) {
                    double an = rnd.nextDouble() * Math.PI * 2, rr = rnd.nextDouble() * 6;
                    Vec3 hit = Ground.below(l, g.add(Math.cos(an) * rr, 6, Math.sin(an) * rr), 20);
                    meteor(p, l, gate.add((rnd.nextDouble() - 0.5) * 4, -0.5, (rnd.nextDouble() - 0.5) * 4), hit);
                }
                return a < total;
            }
            @Override public void end() {
                FxNet.kill(l, gate, portalId);
                PowerNet.kill(l, gate, circleId);
                PowerNet.kill(l, g, groundId);
            }
        });
        return true;
    }

    private static void meteor(ServerPlayer p, ServerLevel l, Vec3 from, Vec3 to) {
        float sc = 1.3F + l.random.nextFloat() * 0.6F;
        Display.BlockDisplay d = Displays.block(l, Blocks.MAGMA_BLOCK.defaultBlockState(), from, sc, 0xFF5A1E, 1);
        Fx.sound(l, from, SoundEvents.BLAZE_SHOOT, 0.6F, 0.5F);
        Tasks.add(new Tasks.Task() {
            Vec3 pos = from;
            @Override public boolean tick(int a) {
                double t = Math.min(1, (a + 1) / (double) FALL);
                Vec3 next = from.lerp(to, t * t);
                if (d != null) { d.setPos(next.x, next.y, next.z); if (a % 3 == 0) Displays.pose(d, sc, a * 0.5F, a * 0.3F, 3); }
                Fx.far(l, ParticleTypes.FLAME, next, 6, 0.4, 0.03, 120);
                Fx.far(l, ParticleTypes.LARGE_SMOKE, next.add(0, 0.6, 0), 2, 0.3, 0.01, 120);
                Fx.line(l, pos, next, 0.7, Fx.dust(0xFF7A2A, 2F));
                pos = next;
                if (t >= 1) { impact(p, l, to); return false; }
                return true;
            }
            @Override public void end() { Displays.remove(d); }
        });
    }

    private static void impact(ServerPlayer p, ServerLevel l, Vec3 c) {
        FxNet.shake(l, c, 28, 2.4F, 14);
        FxNet.shockwave(l, c.add(0, 0.15, 0), 5, 0xFF5A1E, 16);
        FxNet.flare(l, c.add(0, 0.8, 0), 5, PowerUtil.COLOR, 10);
        Fx.far(l, ParticleTypes.EXPLOSION, c.add(0, 0.5, 0), 6, 1.2, 0, 120);
        Fx.far(l, ParticleTypes.LAVA, c.add(0, 0.5, 0), 14, 1, 0.3, 120);
        Fx.far(l, ParticleTypes.FLAME, c.add(0, 0.5, 0), 40, 1.5, 0.1, 120);
        PowerUtil.farSound(l, c, SoundEvents.GENERIC_EXPLODE, 0.7F, 0.7F, 80);
        for (int k = 0; k < 5; k++) {
            double an = l.random.nextDouble() * Math.PI * 2;
            PowerUtil.debris(l, c.add(0, 0.6, 0), k % 2 == 0 ? Blocks.MAGMA_BLOCK.defaultBlockState() : PowerUtil.surface(l, c),
                    new Vec3(Math.cos(an) * 0.35, 0.6 + l.random.nextDouble() * 0.4, Math.sin(an) * 0.35), 0.35F, 30, 0);
        }
        Terrain.boom(l, p, c.add(0, 0.3, 0), 4F, 100F);
        for (LivingEntity e : PowerUtil.victims(p, l, c.add(0, 0.5, 0), 5)) {
            Rules.hurtPct(p, e, 10F, 0.10F);
            e.igniteForSeconds(4);
            PowerUtil.push(e, c, 0.6, 0.4);
        }
    }
}
