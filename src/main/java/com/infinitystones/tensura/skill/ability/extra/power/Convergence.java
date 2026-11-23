package com.infinitystones.tensura.skill.ability.extra.power;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Convergence (conjunction: Power + at least one other carried stone). Every carried stone becomes an orb of its
 * colour orbiting you; they converge above your head and fire a braided multi-colour beam at the aim (≤96).
 * Damage scales with stones carried: 2→20, 3→35, 4→55, 5→80, 6→lethal. Pierces everything on the line.
 */
public final class Convergence implements Ability {
    public static final Convergence INSTANCE = new Convergence();
    static final int ORBIT = 16, MERGE = 12, RANGE = 96;
    static final float[] DAMAGE = {0, 10, 20, 35, 55, 80, 100000};

    @Override public Stone stone() { return Stone.POWER; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        int mask = 0, n = 0;
        for (Stone s : Stone.values()) if (s == Stone.POWER || s.isHeldBy(p)) { mask |= 1 << s.ordinal(); n++; }
        if (n < 2) return Fx.fail(p, "infinity.extra.power.convergence.need");
        ServerLevel l = p.serverLevel();
        LivingEntity target = Aim.living(p, RANGE, e -> Rules.canHarm(p, e));
        Vec3 aimPt = target != null ? null : Aim.point(p, RANGE);
        final int fmask = mask, count = n;
        int[] orbIds = new int[6];
        for (int i = 0; i < 6; i++) orbIds[i] = FxNet.newId();
        int ringId = PowerNet.newId();
        PowerNet.circle(l, ringId, new Vec3(0, 0.06, 0), new Vec3(0, 1, 0), 3.2F, 0xFFFFFF, 0xFFFFFF, ORBIT + MERGE + 20, 3F, PowerNet.RAINBOW, p);
        Fx.sound(l, p.position(), SoundEvents.BEACON_ACTIVATE, 0.7F, 1.2F);
        Fx.sound(l, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, 0.6F);
        Fx.bar(p, Stone.POWER, "infinity.extra.power.convergence.charge", count);
        Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int a) {
                if (!p.isAlive() || p.isRemoved() || p.level() != l) return false;
                Vec3 head = p.position().add(0, p.getBbHeight() + 1.6, 0);
                int k = 0;
                for (Stone s : Stone.values()) {
                    if ((fmask & (1 << s.ordinal())) == 0) continue;
                    double ang = a * 0.35 + Math.PI * 2 * k / count;
                    double rr = a < ORBIT ? 1.8 : 1.8 * (1 - (a - ORBIT) / (double) MERGE);
                    double y = a < ORBIT ? 1.1 + 0.3 * Math.sin(a * 0.4 + k) : 1.1 + (p.getBbHeight() + 0.5) * (a - ORBIT) / (double) MERGE;
                    Vec3 off = new Vec3(Math.cos(ang) * Math.max(0, rr), y, Math.sin(ang) * Math.max(0, rr));
                    if (a % 2 == 0) FxNet.orb(l, orbIds[s.ordinal()], p.position().add(off), 0.28F, PowerNet.STONE_COLORS[s.ordinal()], 6, p.getId(), off);
                    Vec3 w = p.position().add(off);
                    l.sendParticles(Fx.dust(PowerNet.STONE_COLORS[s.ordinal()], 1.2F), w.x, w.y, w.z, 2, 0.05, 0.05, 0.05, 0);
                    k++;
                }
                if (a % 5 == 0) Fx.sound(l, head, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 0.6F + a * 0.04F);
                if (a == ORBIT + MERGE) { fire(head); return false; }
                return true;
            }

            void fire(Vec3 head) {
                for (int id : orbIds) FxNet.kill(l, head, id);
                Vec3 end = target != null && target.isAlive() ? target.getBoundingBox().getCenter() : aimPt != null ? aimPt : Aim.point(p, RANGE);
                float w = 0.25F + count * 0.08F;
                PowerNet.braid(l, 0, head, end, w, fmask, 26);
                PowerNet.circle(l, 0, head, end.subtract(head), 1.6F + count * 0.2F, 0xFFFFFF, 0xFFFFFF, 24, 6F, PowerNet.RAINBOW | PowerNet.SIMPLE);
                FxNet.flare(l, head, 3, 0xFFFFFF, 10);
                FxNet.flare(l, end, 4 + count, 0xFFFFFF, 14);
                FxNet.shockwave(l, end, 3 + count, 0xFFFFFF, 16);
                FxNet.shake(l, end, 24 + count * 4, 1 + count * 0.5F, 14);
                FxNet.flash(p, 0xFFFFFF, 0.25F, 6);
                Fx.sound(l, head, SoundEvents.WARDEN_SONIC_BOOM, 0.7F, 1.3F);
                Fx.sound(l, end, SoundEvents.GENERIC_EXPLODE, 0.6F, 1.2F);
                Fx.far(l, ParticleTypes.END_ROD, end, 40, 0.6, 0.35, 120);
                for (int i = 0; i < 6; i++) if ((fmask & (1 << i)) != 0) Fx.far(l, Fx.dust(PowerNet.STONE_COLORS[i], 2.5F), end, 20, 1.2, 0.1, 120);
                // pierce: everything near the line, queried in short segments
                float dmg = DAMAGE[Math.min(6, count)];
                Set<Integer> done = new HashSet<>();
                double len = head.distanceTo(end);
                Vec3 dir = end.subtract(head).normalize();
                for (double s = 0; s < len + 4; s += 8) {
                    Vec3 c = head.add(dir.scale(Math.min(len, s + 4)));
                    for (LivingEntity e : PowerUtil.victims(p, l, c, 7)) {
                        Vec3 ec = e.getBoundingBox().getCenter();
                        double proj = ec.subtract(head).dot(dir);
                        if (proj < -1 || proj > len + 1.5) continue;
                        double lat = ec.subtract(head.add(dir.scale(proj))).length();
                        if (lat > 1.3 + e.getBbWidth() * 0.5 + w || !done.add(e.getId())) continue;
                        Rules.hurt(p, e, dmg);
                        e.setDeltaMovement(e.getDeltaMovement().add(dir.x * 0.6, 0.25, dir.z * 0.6));
                        e.hurtMarked = true;
                    }
                }
            }

            @Override public void end() {
                PowerNet.kill(l, p.position(), ringId);
                for (int id : orbIds) FxNet.kill(l, p.position(), id);
            }
        });
        return true;
    }
}
