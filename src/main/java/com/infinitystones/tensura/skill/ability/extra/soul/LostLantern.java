package com.infinitystones.tensura.skill.ability.extra.soul;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Displays;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lantern of the Lost (Soul). The first use lights a floating soul lantern at your shoulder that gathers the souls of every
 * creature that dies within 32 blocks of it (up to 24; it also takes the ones that died in the last 30 s around you). Left
 * (aim): releases the whole lantern as a homing swarm — every soul hits for 3.5 and seeks the next enemy if its prey dies.
 * Right: the souls rush into you and your allies as healing and a golden shield; with an empty lantern, right puts it out.
 * No blocks are touched; the lantern vanishes if you die, log out or the server stops.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class LostLantern implements Ability {
    public static final LostLantern INSTANCE = new LostLantern();
    private static final String K = "infinity.extra.lantern.";
    private static final int ORANGE = Stone.SOUL.color() & 0xFFFFFF, PALE = 0xFFD9A0, MAX_SOULS = 24;
    private static final float HIT = 3.5F, HEAL = 4F;

    private static final class Lantern {
        final UUID owner; ServerLevel level; @Nullable Display.BlockDisplay disp; int souls, pending, age, id = FxNet.newId();
        Vec3 pos = Vec3.ZERO; boolean dead;
        Lantern(UUID owner, ServerLevel level) { this.owner = owner; this.level = level; }
    }

    private static final Map<UUID, Lantern> LANTERNS = new HashMap<>();

    @Override public Stone stone() { return Stone.SOUL; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        Lantern lt = LANTERNS.get(p.getUUID());
        if (lt == null) { summon(p); return true; }
        if (alt) {
            if (lt.souls == 0) { lt.dead = true; Fx.bar(p, Stone.SOUL, K + "off"); return true; }
            return bless(p, lt);
        }
        return swarm(p, lt);
    }

    // ------------------------------------------------------------------ lantern

    private static void summon(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Lantern lt = new Lantern(p.getUUID(), l);
        LANTERNS.put(p.getUUID(), lt);
        lt.pos = anchor(p, 0);
        lt.disp = Displays.block(l, Blocks.SOUL_LANTERN.defaultBlockState(), lt.pos, 0.9F, ORANGE, 3);
        SoulKit.circle(l, p.position(), 3.2F, ORANGE, 40);
        FxNet.pillar(l, 0, p.position(), 0.5F, 8, ORANGE, 30);
        FxNet.flare(l, lt.pos, 2F, ORANGE, 10);
        Fx.sound(l, lt.pos, SoundEvents.SOUL_ESCAPE.value(), 0.6F, 0.8F);
        Fx.sound(l, lt.pos, SoundEvents.LANTERN_PLACE, 0.7F, 0.6F);
        Fx.sound(l, lt.pos, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 0.8F);
        for (SoulKit.Corpse c : SoulKit.take(l, p.position(), 24, 600, 8)) fly(lt, c.pos().add(0, 0.6, 0));
        Fx.bar(p, Stone.SOUL, K + "on");
        Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) { return LostLantern.tick(lt, p); }
            @Override public void end() { LostLantern.remove(lt); }
        });
    }

    private static Vec3 anchor(ServerPlayer p, int age) {
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
        return p.position().add(right.scale(0.95)).add(0, p.getBbHeight() + 0.35 + Math.sin(age * 0.12) * 0.12, 0);
    }

    private static boolean tick(Lantern lt, ServerPlayer p) {
        if (lt.dead || LANTERNS.get(lt.owner) != lt || !p.isAlive() || p.hasDisconnected()) return false;
        ServerLevel l = p.serverLevel();
        lt.age++;
        if (l != lt.level) {
            Displays.remove(lt.disp);
            lt.level = l;
            lt.disp = Displays.block(l, Blocks.SOUL_LANTERN.defaultBlockState(), anchor(p, lt.age), 0.9F, ORANGE, 3);
        }
        lt.pos = anchor(p, lt.age);
        if (lt.disp != null && !lt.disp.isRemoved()) lt.disp.setPos(lt.pos.x, lt.pos.y, lt.pos.z);
        int shown = Math.min(lt.souls, 12);
        if (lt.age % 3 == 0) {
            for (int i = 0; i < shown; i += 2) {
                double a = lt.age * 0.17 + i * Math.PI * 2 / Math.max(1, shown), r = 0.5 + 0.06 * (i % 3);
                Vec3 v = lt.pos.add(Math.cos(a) * r, Math.sin(a * 1.7) * 0.35, Math.sin(a) * r);
                Fx.at(l, i % 4 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : Fx.dust(i % 3 == 0 ? PALE : ORANGE, 0.9F), v, 1, 0, 0);
            }
        }
        if (lt.age % 6 == 0) FxNet.orb(l, lt.id, lt.pos, 0.35F + lt.souls * 0.03F, ORANGE, 10, -1, Vec3.ZERO);
        if (lt.age % 40 == 0 && lt.disp != null) Displays.pose(lt.disp, 0.9F, lt.age * 0.08F, 0F, 40);
        return true;
    }

    private static void remove(Lantern lt) {
        LANTERNS.remove(lt.owner, lt);
        Displays.remove(lt.disp);
        lt.disp = null;
        if (lt.level != null) {
            FxNet.kill(lt.level, lt.pos, lt.id);
            SoulKit.soulBurst(lt.level, lt.pos, 1F + lt.souls * 0.1F);
            FxNet.flare(lt.level, lt.pos, 1.6F, ORANGE, 8);
        }
    }

    // ------------------------------------------------------------------ souls in

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void died(LivingDeathEvent e) {
        LivingEntity v = e.getEntity();
        if (LANTERNS.isEmpty() || !(v.level() instanceof ServerLevel l) || Ghosts.isGhost(v) || v instanceof ArmorStand) return;
        Vec3 at = v.position().add(0, v.getBbHeight() * 0.5, 0);
        for (Lantern lt : LANTERNS.values()) {
            if (lt.level != l || lt.dead || v.getUUID().equals(lt.owner) || lt.souls + lt.pending >= MAX_SOULS) continue;
            if (lt.pos.distanceToSqr(at) > 32 * 32) continue;
            fly(lt, at);
        }
    }

    /** A soul streaming from a point into the lantern. */
    private static void fly(Lantern lt, Vec3 from) {
        lt.pending++;
        final Vec3[] pos = {from};
        final double swirl = lt.level.random.nextDouble() * Math.PI * 2;
        Fx.sound(lt.level, from, SoundEvents.SOUL_ESCAPE.value(), 0.4F, 1F + lt.level.random.nextFloat() * 0.5F);
        SoulKit.soulBurst(lt.level, from, 0.6F);
        Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) {
                if (lt.dead || lt.disp == null) return false;
                ServerLevel l = lt.level;
                Vec3 d = lt.pos.subtract(pos[0]);
                double dist = d.length();
                if (dist < 0.6 || age > 120) return false;
                double step = Math.min(dist, 0.35 + age * 0.04);
                Vec3 side = new Vec3(-d.z, 0, d.x).normalize().scale(Math.sin(age * 0.45 + swirl) * 0.3);
                pos[0] = pos[0].add(d.normalize().scale(step)).add(side.scale(0.4));
                Fx.at(l, ParticleTypes.SOUL, pos[0], 1, 0.03, 0.01);
                Fx.at(l, Fx.dust(age % 2 == 0 ? ORANGE : PALE, 1F), pos[0], 2, 0.05, 0);
                return true;
            }
            @Override public void end() {
                lt.pending = Math.max(0, lt.pending - 1);
                if (lt.dead || lt.disp == null) return;
                lt.souls = Math.min(MAX_SOULS, lt.souls + 1);
                Fx.at(lt.level, Fx.light(Stone.SOUL, 1F), lt.pos, 5, 0.25, 0.03);
                Fx.sound(lt.level, lt.pos, SoundEvents.AMETHYST_BLOCK_CHIME, 0.35F, 0.7F + lt.souls * 0.04F);
            }
        });
    }

    // ------------------------------------------------------------------ souls out

    private static boolean swarm(ServerPlayer p, Lantern lt) {
        if (lt.souls <= 0) return Fx.fail(p, K + "empty");
        ServerLevel l = lt.level;
        LivingEntity prey = Aim.living(p, 48, e -> Rules.canHarm(p, e));
        Vec3 focus = prey != null ? prey.position() : Aim.point(p, 30);
        if (prey == null && nearest(p, l, focus, 24, null) == null) return Fx.fail(p, K + "no_enemy");
        int n = lt.souls;
        lt.souls = 0;
        class Wisp { Vec3 pos, vel; @Nullable LivingEntity target; boolean dead; int born; }
        List<Wisp> wisps = new ArrayList<>();
        Fx.sound(l, lt.pos, SoundEvents.SOUL_ESCAPE.value(), 0.7F, 0.6F);
        Fx.sound(l, lt.pos, SoundEvents.BELL_RESONATE, 0.5F, 1.8F);
        FxNet.flare(l, lt.pos, 2.4F, ORANGE, 8);
        FxNet.shockwave(l, p.position().add(0, 0.1, 0), 5F, ORANGE, 16);
        Fx.bar(p, Stone.SOUL, K + "swarm", n);
        final int[] launched = {0};
        Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) {
                if (launched[0] < n) {
                    Wisp w = new Wisp();
                    double a = launched[0] * 2.4;
                    w.pos = lt.pos;
                    w.vel = new Vec3(Math.cos(a) * 0.5, 0.45 + 0.1 * (launched[0] % 3), Math.sin(a) * 0.5);
                    w.target = prey != null && prey.isAlive() ? prey : nearest(p, l, focus, 24, null);
                    w.born = age;
                    wisps.add(w);
                    launched[0]++;
                }
                boolean any = launched[0] < n;
                for (Wisp w : wisps) {
                    if (w.dead) continue;
                    any = true;
                    int life = age - w.born;
                    if (w.target == null || !w.target.isAlive() || w.target.level() != l || !Rules.canHarm(p, w.target)) w.target = nearest(p, l, w.pos, 24, null);
                    if (w.target == null || life > 140) { w.dead = true; end(l, w.pos, false); continue; }
                    Vec3 aim = w.target.position().add(0, w.target.getBbHeight() * 0.6, 0);
                    Vec3 dir = aim.subtract(w.pos);
                    double dist = dir.length();
                    if (dist < 1.0) {
                        w.dead = true;
                        Rules.hurt(p, w.target, HIT);
                        end(l, aim, true);
                        continue;
                    }
                    double speed = Math.min(1.5, 0.45 + life * 0.05);
                    w.vel = w.vel.scale(0.78).add(dir.normalize().scale(speed * 0.4));
                    if (w.vel.length() > speed) w.vel = w.vel.normalize().scale(speed);
                    Vec3 next = w.pos.add(w.vel);
                    Fx.line(l, w.pos, next, 0.35, Fx.dust(life % 2 == 0 ? ORANGE : PALE, 1.1F));
                    if (life % 2 == 0) Fx.at(l, ParticleTypes.SOUL, next, 1, 0.04, 0.01);
                    w.pos = next;
                }
                return any;
            }
            private void end(ServerLevel lv, Vec3 at, boolean hit) {
                if (hit) {
                    SoulKit.soulBurst(lv, at, 0.8F);
                    Fx.at(lv, ParticleTypes.CRIT, at, 6, 0.25, 0.25);
                } else Fx.at(lv, ParticleTypes.SOUL, at, 6, 0.2, 0.05);
            }
        });
        return true;
    }

    private static boolean bless(ServerPlayer p, Lantern lt) {
        ServerLevel l = lt.level;
        List<LivingEntity> who = new ArrayList<>();
        who.add(p);
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24), e -> e != p && e.isAlive() && Rules.isLiving(e) && Rules.isAlly(p, e))) who.add(e);
        int n = lt.souls;
        lt.souls = 0;
        Fx.bar(p, Stone.SOUL, K + "bless", n);
        Fx.sound(l, lt.pos, SoundEvents.SOUL_ESCAPE.value(), 0.7F, 1.2F);
        Fx.sound(l, lt.pos, SoundEvents.BEACON_POWER_SELECT, 0.5F, 1.6F);
        FxNet.flare(l, lt.pos, 2.2F, PALE, 8);
        for (int i = 0; i < n; i++) {
            LivingEntity to = who.get(i % who.size());
            final int delay = i;
            Tasks.add(new Tasks.Task() {
                Vec3 pos = lt.pos;
                @Override public boolean tick(int age) {
                    if (age < delay) return true;
                    if (!to.isAlive() || to.level() != l) return false;
                    Vec3 aim = to.position().add(0, to.getBbHeight() * 0.6, 0);
                    Vec3 d = aim.subtract(pos);
                    double dist = d.length();
                    if (dist < 0.8 || age > delay + 80) {
                        if (to instanceof ServerPlayer sp) SoulKit.gain(sp, HEAL, 8F);
                        else to.heal(HEAL);
                        SoulKit.soulBurst(l, aim, 0.5F);
                        FxNet.flare(l, aim, 1.2F, PALE, 6);
                        return false;
                    }
                    Vec3 next = pos.add(d.normalize().scale(Math.min(dist, 0.6 + (age - delay) * 0.05)));
                    Fx.line(l, pos, next, 0.35, Fx.dust(PALE, 1.1F));
                    Fx.at(l, ParticleTypes.SOUL, next, 1, 0.04, 0.01);
                    pos = next;
                    return true;
                }
            });
        }
        return true;
    }

    private static @Nullable LivingEntity nearest(ServerPlayer p, ServerLevel l, Vec3 c, double r, @Nullable LivingEntity not) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(c, c).inflate(r), e -> e != not && Rules.isEnemy(p, e))) {
            double d = e.distanceToSqr(c);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent e) {
        Lantern lt = LANTERNS.get(e.getEntity().getUUID());
        if (lt != null) lt.dead = true;
    }
}
