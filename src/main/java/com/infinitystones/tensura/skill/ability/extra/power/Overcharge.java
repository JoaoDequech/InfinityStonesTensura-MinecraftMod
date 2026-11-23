package com.infinitystones.tensura.skill.ability.extra.power;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Overcharge. Left: empower the aimed ally (or yourself when nothing is aimed): +30% size, Strength II, knockback
 * immunity and a purple aura for 20 s. Right: the aimed enemy becomes an unstable bomb — pulses faster and faster and
 * explodes after 3 s (or at once when it dies): 14 damage in 4 blocks, knockback, no block damage.
 */
public final class Overcharge implements Ability {
    public static final Overcharge INSTANCE = new Overcharge();
    static final ResourceLocation SCALE_ID = InfinityStones.id("overcharge_scale"), KB_ID = InfinityStones.id("overcharge_kb");
    static final int BUFF = 400, FUSE = 60;
    private static final Set<UUID> BOMBS = new HashSet<>();

    @Override public Stone stone() { return Stone.POWER; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        ServerLevel l = p.serverLevel();
        if (alt) {
            LivingEntity t = Aim.living(p, 32, e -> Rules.canHarm(p, e));
            if (t == null) return Fx.fail(p, "infinity.extra.power.overcharge.no_target");
            if (BOMBS.contains(t.getUUID())) return Fx.fail(p, "infinity.extra.power.overcharge.already");
            bomb(p, l, t);
            return true;
        }
        LivingEntity t = Aim.living(p, 32, e -> Rules.isAlly(p, e));
        if (t == null) t = p;
        empower(p, l, t);
        return true;
    }

    private static void mod(LivingEntity e, Holder<Attribute> a, ResourceLocation id, double v, AttributeModifier.Operation op) {
        AttributeInstance inst = e.getAttribute(a);
        if (inst == null) return;
        inst.removeModifier(id);
        if (v != 0) inst.addTransientModifier(new AttributeModifier(id, v, op));
    }

    private static void empower(ServerPlayer p, ServerLevel l, LivingEntity t) {
        mod(t, Attributes.SCALE, SCALE_ID, 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        mod(t, Attributes.KNOCKBACK_RESISTANCE, KB_ID, 1.0, AttributeModifier.Operation.ADD_VALUE);
        t.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, BUFF, 1), p);
        if (t != p) FxNet.tether(l, 0, p, t, PowerUtil.COLOR, 0.12F, 14);
        FxNet.spiral(l, t.position(), t.getBbWidth() + 0.6F, t.getBbHeight() * 1.3F + 0.5F, PowerUtil.COLOR, 30, t.getId());
        FxNet.flare(l, t.getBoundingBox().getCenter(), 3F, PowerUtil.COLOR, 12);
        Fx.sound(l, t.position(), SoundEvents.BEACON_POWER_SELECT, 0.7F, 0.7F);
        Fx.sound(l, t.position(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, 0.5F, 0.6F);
        Fx.bar(p, Stone.POWER, "infinity.extra.power.overcharge.empowered", t.getDisplayName());
        int circleId = PowerNet.newId(), crownId = FxNet.newId();
        Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int a) {
                if (!t.isAlive() || t.isRemoved()) return false;
                if (a % 30 == 0) {
                    PowerNet.circle(l, circleId, new Vec3(0, 0.06, 0), new Vec3(0, 1, 0), Math.max(1.2F, t.getBbWidth() * 1.6F), PowerUtil.COLOR, PowerUtil.ACCENT, 45, 2F, PowerNet.SIMPLE, t);
                    FxNet.crown(l, crownId, t, PowerUtil.COLOR, 45);
                }
                if (a % 3 == 0) {
                    Vec3 c = t.position();
                    l.sendParticles(Fx.dust(Stone.POWER, 1.3F), c.x, c.y + t.getBbHeight() * 0.5, c.z, 3, t.getBbWidth() * 0.6, t.getBbHeight() * 0.4, t.getBbWidth() * 0.6, 0);
                }
                return a < BUFF;
            }
            @Override public void end() {
                mod(t, Attributes.SCALE, SCALE_ID, 0, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
                mod(t, Attributes.KNOCKBACK_RESISTANCE, KB_ID, 0, AttributeModifier.Operation.ADD_VALUE);
                PowerNet.kill(l, t.position(), circleId);
                FxNet.kill(l, t.position(), crownId);
            }
        });
    }

    private static void bomb(ServerPlayer p, ServerLevel l, LivingEntity t) {
        BOMBS.add(t.getUUID());
        int orbId = FxNet.newId(), circleId = PowerNet.newId();
        FxNet.tether(l, 0, p, t, PowerUtil.HOT, 0.1F, 10);
        Fx.sound(l, t.position(), SoundEvents.CREEPER_PRIMED, 0.7F, 0.6F);
        Fx.bar(p, Stone.POWER, "infinity.extra.power.overcharge.unstable", t.getDisplayName());
        Tasks.add(new Tasks.Task() {
            Vec3 last = t.position();
            int next = 0;
            boolean boomed;
            @Override public boolean tick(int a) {
                if (t.isAlive() && !t.isRemoved()) last = t.getBoundingBox().getCenter();
                else { boom(); return false; }
                float k = a / (float) FUSE;
                if (a >= next) {
                    next = a + Math.max(2, (int) (14 * (1 - k)));
                    Fx.sound(l, last, SoundEvents.NOTE_BLOCK_PLING.value(), 0.6F, 0.6F + k * 1.4F);
                    FxNet.orb(l, orbId, last, t.getBbWidth() * (0.8F + k), PowerUtil.HOT, 16, t.getId(), new Vec3(0, t.getBbHeight() * 0.5, 0));
                    PowerNet.circle(l, circleId, new Vec3(0, 0.06, 0), new Vec3(0, 1, 0), 1.2F + 2.8F * k, PowerUtil.HOT, PowerUtil.COLOR, 20, 3 + k * 8, PowerNet.SIMPLE, t);
                    l.sendParticles(Fx.dust(PowerUtil.HOT, 1.6F), last.x, last.y, last.z, 10, 0.4, 0.5, 0.4, 0.05);
                }
                if (a >= FUSE) { boom(); return false; }
                return true;
            }
            void boom() {
                if (boomed) return;
                boomed = true;
                Vec3 c = last;
                FxNet.kill(l, c, orbId);
                PowerNet.kill(l, c, circleId);
                FxNet.flashAround(l, c, 14, 0xF0C4FF, 0.5F, 8);
                FxNet.shake(l, c, 24, 2.5F, 16);
                FxNet.shockwave(l, c.add(0, -0.5, 0), 6, PowerUtil.HOT, 18);
                FxNet.flare(l, c, 6, PowerUtil.COLOR, 10);
                PowerNet.circle(l, 0, c.subtract(0, t.getBbHeight() * 0.5 - 0.1, 0), new Vec3(0, 1, 0), 4.5F, PowerUtil.COLOR, PowerUtil.HOT, 26, -4, 0);
                Fx.at(l, ParticleTypes.EXPLOSION_EMITTER, c, 1, 0, 0);
                Fx.at(l, Fx.dust(Stone.POWER, 3F), c, 80, 2, 0.3);
                Fx.at(l, ParticleTypes.END_ROD, c, 40, 0.5, 0.4);
                Fx.sound(l, c, SoundEvents.GENERIC_EXPLODE, 0.7F, 0.8F);
                Fx.sound(l, c, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 0.7F, 0.6F);
                for (LivingEntity e : PowerUtil.victims(p, l, c, 4)) {
                    Rules.hurt(p, e, 14);
                    PowerUtil.push(e, c, 1.2, 0.5);
                }
                if (t.isAlive() && Rules.canHarm(p, t) && t.getBoundingBox().getCenter().distanceTo(c) > 4) Rules.hurt(p, t, 14);
            }
            @Override public void end() { BOMBS.remove(t.getUUID()); }
        });
    }
}
