package com.infinitystones.tensura.skill.ability.extra.soul;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared bits of the Soul powers: palette, layered "soul circle" effect, wisps that fly to a creature, healing with
 * a golden shield, and the log of recently dead creatures (their souls can be raised again by the Spectral Army).
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class SoulKit {
    public static final int ORANGE = Stone.SOUL.color() & 0xFFFFFF, LIGHT = 0xFFC07A, PALE = 0xFFE9CC, EMBER = 0xB8470C, SPIRIT = 0x9FF3FF;

    /** A creature that died recently. */
    public record Corpse(EntityType<?> type, Vec3 pos, long time, float maxHealth, float yaw) {}

    private static final Map<ResourceKey<Level>, ArrayDeque<Corpse>> CORPSES = new HashMap<>();
    private static final int LOG_CAP = 64;

    @SubscribeEvent
    public static void died(LivingDeathEvent e) {
        LivingEntity le = e.getEntity();
        if (!(le.level() instanceof ServerLevel l) || !(le instanceof Mob m) || Ghosts.isGhost(m)) return;
        if (com.infinitystones.tensura.skill.ability.extra.Rules.isBoss(m)) return;
        record(l, m.getType(), m.position(), m.getMaxHealth(), m.getYRot());
    }

    public static void record(ServerLevel l, EntityType<?> type, Vec3 pos, float maxHealth, float yaw) {
        ArrayDeque<Corpse> q = CORPSES.computeIfAbsent(l.dimension(), k -> new ArrayDeque<>());
        q.addLast(new Corpse(type, pos, l.getGameTime(), maxHealth, yaw));
        while (q.size() > LOG_CAP) q.removeFirst();
    }

    /** Souls of creatures that died within maxAge ticks and r blocks of c, nearest first. They are consumed. */
    public static List<Corpse> take(ServerLevel l, Vec3 c, double r, int maxAge, int max) {
        ArrayDeque<Corpse> q = CORPSES.get(l.dimension());
        List<Corpse> out = new ArrayList<>();
        if (q == null) return out;
        long now = l.getGameTime();
        q.removeIf(k -> now - k.time() > 1200);
        List<Corpse> near = new ArrayList<>();
        for (Corpse k : q) if (now - k.time() <= maxAge && k.pos().distanceToSqr(c) <= r * r) near.add(k);
        near.sort((a, b) -> Double.compare(a.pos().distanceToSqr(c), b.pos().distanceToSqr(c)));
        for (Corpse k : near) { if (out.size() >= max) break; out.add(k); q.remove(k); }
        return out;
    }

    /** Layered magic circle on the ground: counter-rotating rune rings, outward waves and a flare. */
    public static void circle(ServerLevel l, Vec3 c, float r, int color, int life) {
        Vec3 g = c.add(0, 0.06, 0);
        FxNet.runes(l, 0, g, r, color, life, 1.0F);
        FxNet.runes(l, 0, g.add(0, 0.02, 0), r * 0.64F, LIGHT, life, -1.6F);
        FxNet.runes(l, 0, g.add(0, 0.04, 0), r * 1.3F, EMBER, life, 0.5F);
        FxNet.runes(l, 0, g.add(0, 0.06, 0), r * 0.3F, PALE, life, 2.4F);
        FxNet.shockwave(l, g, r * 1.4F, color, 18);
        FxNet.shockwave(l, g, r * 0.8F, LIGHT, 12);
        FxNet.flare(l, g.add(0, 0.5, 0), r * 0.9F, color, 10);
    }

    /** A soul that flies from a point into a creature, leaving a glowing trail, then runs arrive. */
    public static void wisp(ServerLevel l, Vec3 from, LivingEntity to, @Nullable Runnable arrive) {
        final Vec3[] pos = {from};
        final double swirl = l.random.nextDouble() * Math.PI * 2;
        Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) {
                if (!to.isAlive() || to.level() != l) return false;
                Vec3 target = to.position().add(0, to.getBbHeight() * 0.6, 0);
                Vec3 d = target.subtract(pos[0]);
                double dist = d.length();
                if (dist < 0.7 || age > 50) { if (arrive != null) arrive.run(); return false; }
                double step = Math.min(dist, 0.55 + age * 0.03);
                Vec3 side = new Vec3(-d.z, 0, d.x).normalize().scale(Math.sin(age * 0.5 + swirl) * 0.25);
                pos[0] = pos[0].add(d.normalize().scale(step)).add(side.scale(0.4));
                Fx.at(l, ParticleTypes.SOUL, pos[0], 1, 0.03, 0.01);
                Fx.at(l, Fx.dust(age % 2 == 0 ? ORANGE : LIGHT, 1.0F), pos[0], 2, 0.05, 0);
                if (age % 3 == 0) Fx.at(l, ParticleTypes.SOUL_FIRE_FLAME, pos[0], 1, 0.04, 0.0);
                return true;
            }
        });
    }

    /** Health plus a golden shield (absorption, capped). */
    public static void gain(ServerPlayer p, float heal, float absorbCap) {
        if (heal <= 0) return;
        float room = p.getMaxHealth() - p.getHealth();
        p.heal(heal);
        float extra = heal - room;
        if (extra > 0 && absorbCap > 0) p.setAbsorptionAmount(Math.min(absorbCap, p.getAbsorptionAmount() + extra));
    }

    /** Burst for a soul leaving a body. */
    public static void soulBurst(ServerLevel l, Vec3 at, float scale) {
        Fx.at(l, ParticleTypes.SOUL, at, (int) (10 * scale), 0.3 * scale, 0.08);
        Fx.at(l, ParticleTypes.SOUL_FIRE_FLAME, at, (int) (6 * scale), 0.25 * scale, 0.05);
        Fx.at(l, Fx.dust(ORANGE, 1.4F), at, (int) (14 * scale), 0.35 * scale, 0.02);
        Fx.at(l, Fx.light(Stone.SOUL, 1.0F), at, (int) (8 * scale), 0.25 * scale, 0.02);
        FxNet.flare(l, at, 1.2F * scale, ORANGE, 8);
        Fx.sound(l, at, SoundEvents.SOUL_ESCAPE.value(), 0.5F, 0.8F + l.random.nextFloat() * 0.5F);
    }

    /** Purely cosmetic glowing outline on a creature for a while. */
    public static void glow(LivingEntity e, int ticks) {
        e.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, false, false));
    }

    public static float clamp(float v, float lo, float hi) { return Mth.clamp(v, lo, hi); }

    private SoulKit() {}
}
