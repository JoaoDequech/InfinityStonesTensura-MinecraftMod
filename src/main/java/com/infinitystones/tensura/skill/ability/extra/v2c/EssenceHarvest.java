package com.infinitystones.tensura.skill.ability.extra.v2c;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.compat.ClaimsCompat;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Priv;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import com.infinitystones.tensura.skill.ability.extra.soul.SoulKit;
import io.github.manasmods.tensura.registry.attribute.TensuraAttributes;
import io.github.manasmods.tensura.storage.TensuraStorages;
import io.github.manasmods.tensura.storage.ep.IExistence;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Soul, hidden: channels a target's XP levels and magicules into a soul orb kept by the caster (persisted on the
 * player), and gives the orb's contents to an ally or to the caster. Nothing is told to the victim.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class EssenceHarvest implements Ability {
    public static final EssenceHarvest INSTANCE = new EssenceHarvest();
    static final String MODE = "soul.essence_harvest", K = "infinity.extra.v2c.harvest.", ORB = "infinity_essence_orb", TAG = "infinity_essence_taken";
    private static final int CHANNEL = 60, GIVE = 30, ORANGE = SoulKit.ORANGE;
    private static final double DRAIN = 0.35;

    private static final Set<UUID> BUSY = new HashSet<>();

    @Override public Stone stone() { return Stone.SOUL; }

    // ------------------------------------------------------------------ orb storage

    private static int levels(Player p) { return Rules.data(p).getCompound(ORB).getInt("levels"); }
    private static double magicule(Player p) { return Rules.data(p).getCompound(ORB).getDouble("mag"); }

    private static void store(Player p, int levels, double mag) {
        CompoundTag t = new CompoundTag();
        t.putInt("levels", Math.max(0, levels));
        t.putDouble("mag", Math.max(0, mag));
        Rules.data(p).put(ORB, t);
    }

    private static float orbSize(Player p) {
        double m = magicule(p);
        return (float) Mth.clamp(levels(p) / 40.0 + (m > 0 ? Math.min(0.5, Math.log10(m + 1) / 12) : 0), 0.05, 1);
    }

    // ------------------------------------------------------------------ press

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        if (BUSY.contains(p.getUUID())) return Fx.fail(p, K + "busy");
        return alt ? give(p) : harvest(p);
    }

    private boolean harvest(ServerPlayer p) {
        LivingEntity t = Aim.living(p, 24, e -> Rules.canHarm(p, e) && Rules.foreignOwner(p, e) == null);
        if (t == null) return Fx.fail(p, K + "no_target");
        int want = ExtraSettings.get(p, MODE, 5, 1, 30, 1);
        UUID owner = p.getUUID(), target = t.getUUID();
        ServerLevel l = p.serverLevel();
        BUSY.add(owner);
        Priv.glow(p, t, true);
        Fx.sound(l, p.position(), SoundEvents.SOUL_ESCAPE.value(), 0.5F, 0.6F);
        Fx.sound(l, t.position(), SoundEvents.SOUL_ESCAPE.value(), 0.4F, 0.5F);
        Fx.bar(p, Stone.SOUL, K + "channel");
        final boolean[] ok = {false};
        Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) {
                ServerPlayer pl = V2CNet.player(l.getServer(), owner);
                Entity te = l.getEntity(target);
                if (pl == null || !pl.isAlive() || pl.serverLevel() != l || !(te instanceof LivingEntity victim) || !victim.isAlive()
                        || victim.distanceToSqr(pl) > 32 * 32) return false;
                strands(l, victim.position().add(0, victim.getBbHeight() * 0.6, 0), anchor(pl, age), age, false);
                ring(l, victim, age);
                if (age % 5 == 0) V2CNet.near(l, pl.position(), 64, V2CNet.msg(V2CNet.ORB, pl.getId(), 1, 12, orbSize(pl) + age / 120F, pl.position(), 0));
                if (age % 10 == 0) Fx.sound(l, victim.position(), SoundEvents.SOUL_ESCAPE.value(), 0.3F, 0.7F + age / 100F);
                if (age + 1 >= CHANNEL) { ok[0] = true; return false; }
                return true;
            }
            @Override public void end() {
                BUSY.remove(owner);
                ServerPlayer pl = V2CNet.player(l.getServer(), owner);
                Entity te = l.getEntity(target);
                if (pl != null && te != null) Priv.glow(pl, te, false);
                if (!ok[0] || pl == null || !(te instanceof LivingEntity victim)) {
                    if (pl != null) Fx.barGray(pl, K + "broken");
                    return;
                }
                drain(pl, victim, want);
            }
        });
        return true;
    }

    private static void drain(ServerPlayer p, LivingEntity victim, int want) {
        ServerLevel l = p.serverLevel();
        int gotLevels = 0;
        if (victim instanceof Player vp) {
            gotLevels = Math.min(want, vp.experienceLevel);
            if (gotLevels > 0) vp.giveExperienceLevels(-gotLevels);
        } else if (!victim.getPersistentData().getBoolean(TAG)) {
            gotLevels = Mth.clamp((int) Math.ceil(victim.getMaxHealth() / 10F), 1, want);
            victim.getPersistentData().putBoolean(TAG, true);
        }
        double gotMag = 0;
        try {
            IExistence ex = TensuraStorages.getExistenceFrom(victim);
            if (ex != null) {
                gotMag = Math.max(0, ex.getMagicule()) * DRAIN;
                ex.setMagicule(ex.getMagicule() - gotMag);
            }
        } catch (RuntimeException | LinkageError ignored) { gotMag = 0; }
        store(p, levels(p) + gotLevels, magicule(p) + gotMag);
        Vec3 orb = anchor(p, 0);
        Fx.at(l, ParticleTypes.SOUL_FIRE_FLAME, orb, 14, 0.3, 0.05);
        Fx.at(l, Fx.light(Stone.SOUL, 1.2F), orb, 10, 0.3, 0.04);
        FxNet.flare(l, orb, 1.4F, ORANGE, 8);
        SoulKit.soulBurst(l, victim.position().add(0, victim.getBbHeight() * 0.5, 0), 0.8F);
        Fx.sound(l, orb, SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, 0.8F);
        Fx.sound(l, orb, SoundEvents.SOUL_ESCAPE.value(), 0.5F, 1.4F);
        V2CNet.near(l, p.position(), 64, V2CNet.msg(V2CNet.ORB, p.getId(), 0, 400, orbSize(p), p.position(), 0));
        Fx.bar(p, Stone.SOUL, K + "done", gotLevels, (int) gotMag, levels(p), (int) magicule(p));
    }

    private boolean give(ServerPlayer p) {
        int lv = levels(p);
        double mag = magicule(p);
        if (lv <= 0 && mag <= 0) return Fx.fail(p, K + "empty");
        LivingEntity t = Aim.living(p, 24, e -> e instanceof Player pl && !pl.isSpectator() || Rules.isAlly(p, e));
        LivingEntity to = t != null ? t : p;
        UUID owner = p.getUUID(), target = to.getUUID();
        ServerLevel l = p.serverLevel();
        BUSY.add(owner);
        Fx.sound(l, p.position(), SoundEvents.SOUL_ESCAPE.value(), 0.5F, 1.1F);
        final boolean[] ok = {false};
        Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) {
                ServerPlayer pl = V2CNet.player(l.getServer(), owner);
                Entity te = target.equals(owner) ? pl : l.getEntity(target);
                if (pl == null || !pl.isAlive() || pl.serverLevel() != l || !(te instanceof LivingEntity r) || !r.isAlive() || r.distanceToSqr(pl) > 32 * 32) return false;
                strands(l, anchor(pl, age), r.position().add(0, r.getBbHeight() * 0.6, 0), age * 2, true);
                if (age % 5 == 0) V2CNet.near(l, pl.position(), 64, V2CNet.msg(V2CNet.ORB, pl.getId(), 1, 12, orbSize(pl), pl.position(), 0));
                if (age + 1 >= GIVE) { ok[0] = true; return false; }
                return true;
            }
            @Override public void end() {
                BUSY.remove(owner);
                ServerPlayer pl = V2CNet.player(l.getServer(), owner);
                Entity te = target.equals(owner) ? pl : l.getEntity(target);
                if (!ok[0] || pl == null || !(te instanceof LivingEntity r)) return;
                deliver(pl, r);
            }
        });
        return true;
    }

    private static void deliver(ServerPlayer p, LivingEntity to) {
        ServerLevel l = p.serverLevel();
        int lv = levels(p);
        double mag = magicule(p);
        int giveLv = 0;
        if (to instanceof Player tp && lv > 0) { tp.giveExperienceLevels(lv); giveLv = lv; }
        double giveMag = 0;
        if (mag > 0) {
            try {
                IExistence ex = TensuraStorages.getExistenceFrom(to);
                if (ex != null) {
                    double cap = Double.MAX_VALUE;
                    var inst = to.getAttribute(TensuraAttributes.MAX_MAGICULE);
                    if (inst != null) cap = inst.getValue();
                    double room = Math.max(0, cap - ex.getMagicule());
                    giveMag = Math.min(mag, room);
                    ex.setMagicule(ex.getMagicule() + giveMag);
                }
            } catch (RuntimeException | LinkageError ignored) { giveMag = 0; }
        }
        store(p, lv - giveLv, mag - giveMag);
        Vec3 at = to.position().add(0, to.getBbHeight() * 0.5, 0);
        Fx.at(l, ParticleTypes.SOUL_FIRE_FLAME, at, 18, 0.4, 0.06);
        Fx.at(l, Fx.light(Stone.SOUL, 1.2F), at, 14, 0.4, 0.05);
        FxNet.flare(l, at, 1.8F, ORANGE, 10);
        FxNet.pillar(l, 0, to.position(), 0.5F, 4, ORANGE, 14);
        Fx.sound(l, at, SoundEvents.PLAYER_LEVELUP, 0.4F, 1.2F);
        Fx.sound(l, at, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.0F);
        Fx.bar(p, Stone.SOUL, K + "given", giveLv, (int) giveMag, levels(p), (int) magicule(p));
    }

    // ------------------------------------------------------------------ visuals

    /** Same anchor the client uses for the floating orb (right shoulder, bobbing). */
    private static Vec3 anchor(ServerPlayer p, int age) {
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        return p.position().add(right.scale(0.95)).add(0, p.getBbHeight() + 0.35 + Math.sin(age / 20.0 * 2.4) * 0.12, 0);
    }

    /** Orange soul strands travelling along swirling curves from a to b. */
    private static void strands(ServerLevel l, Vec3 a, Vec3 b, int age, boolean fast) {
        Vec3 mid = a.add(b).scale(0.5);
        double dist = a.distanceTo(b);
        for (int s = 0; s < 5; s++) {
            double phase = s * Math.PI * 2 / 5;
            Vec3 ctrl = mid.add(Math.cos(phase + age * 0.16) * dist * 0.28, 0.4 + Math.sin(phase * 2 + age * 0.1) * dist * 0.2, Math.sin(phase + age * 0.16) * dist * 0.28);
            for (int k = 0; k < 4; k++) {
                double t = (age * (fast ? 0.07 : 0.05) + s * 0.19 + k * 0.1) % 1.0;
                double u = 1 - t;
                Vec3 v = a.scale(u * u).add(ctrl.scale(2 * u * t)).add(b.scale(t * t));
                Fx.at(l, Fx.dust((s + k) % 3 == 0 ? SoulKit.LIGHT : ORANGE, 1.0F), v, 1, 0.03, 0);
                if (k == 0 && (age + s) % 4 == 0) Fx.at(l, ParticleTypes.FLAME, v, 1, 0.02, 0.0);
            }
        }
    }

    private static void ring(ServerLevel l, LivingEntity v, int age) {
        double rad = 1.1 - (age % 20) / 20.0 * 0.9;
        for (int i = 0; i < 4; i++) {
            double a = age * 0.4 + i * Math.PI / 2;
            Fx.at(l, Fx.dust(ORANGE, 1.1F), v.position().add(Math.cos(a) * rad, v.getBbHeight() * 0.5 + Math.sin(age * 0.2 + i) * 0.4, Math.sin(a) * rad), 1, 0, 0);
        }
    }

    // ------------------------------------------------------------------ orb upkeep

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        if (e.getServer().getTickCount() % 10 != 0) return;
        for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            if (!p.isAlive() || p.isSpectator() || BUSY.contains(p.getUUID())) continue;
            if (levels(p) <= 0 && magicule(p) <= 0) continue;
            if (!Stone.SOUL.isHeldBy(p)) continue;
            V2CNet.near(p.serverLevel(), p.position(), 64, V2CNet.msg(V2CNet.ORB, p.getId(), 0, 24, orbSize(p), p.position(), 0));
        }
    }

    private EssenceHarvest() {}
}
