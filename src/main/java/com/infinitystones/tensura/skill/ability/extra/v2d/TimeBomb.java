package com.infinitystones.tensura.skill.ability.extra.v2d;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Move;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Time Bomb (Power + Time). Left (aim): plants a purple-green bomb with a floating clock that counts down (Alt+scroll
 * 3-10 s) while its ticking speeds up. Right: detonates yours now. The blast is a huge knockback shockwave with light
 * damage and NO block damage. At most 3 bombs per player.
 */
public final class TimeBomb implements Ability {
    public static final TimeBomb INSTANCE = new TimeBomb();
    private static final String MODE = "power.time_bomb";
    private static final int PURPLE = 0xA43CE6, GREEN = 0x2BD56A, MAX = 3;
    private static final double RADIUS = 7;

    private static final class Bomb {
        final UUID owner; final ServerLevel level; final Vec3 pos; final int total; final int id = V2dNet.newId();
        int age; boolean detonate, silent; Tasks.Handle handle; int nextTick;
        Bomb(UUID owner, ServerLevel level, Vec3 pos, int total) { this.owner = owner; this.level = level; this.pos = pos; this.total = total; }
    }

    private static final Map<UUID, List<Bomb>> BOMBS = new HashMap<>();

    @Override public Stone stone() { return Stone.POWER; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        List<Bomb> mine = BOMBS.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
        if (alt) {
            if (mine.isEmpty()) return Fx.fail(p, "infinity.extra.v2d.bomb.none");
            for (Bomb b : new ArrayList<>(mine)) b.detonate = true;
            return true;
        }
        if (mine.size() >= MAX) return Fx.fail(p, "infinity.extra.v2d.bomb.max");
        ServerLevel l = p.serverLevel();
        BlockHitResult hit = Aim.block(p, 40);
        Vec3 at = hit.getType() == HitResult.Type.MISS ? p.getEyePosition().add(p.getLookAngle().scale(6))
                : hit.getLocation().add(hit.getDirection().getStepX() * 0.9, hit.getDirection().getStepY() * 0.9 + 0.4, hit.getDirection().getStepZ() * 0.9);
        int seconds = ExtraSettings.get(p, MODE, 5, 3, 10, 1);
        Bomb b = new Bomb(p.getUUID(), l, at, seconds * 20);
        mine.add(b);
        V2dNet.bomb(l, b.id, at, 1.1F, PURPLE, GREEN, b.total + 5, 0F);
        FxNet.shockwave(l, at, 3F, PURPLE, 14);
        FxNet.flare(l, at, 1.6F, GREEN, 10);
        Fx.sound(l, at, SoundEvents.BEACON_ACTIVATE, 0.5F, 1.8F);
        Fx.sound(l, at, SoundEvents.ENDER_EYE_DEATH, 0.6F, 0.7F);
        Fx.at(l, ParticleTypes.PORTAL, at, 30, 0.6, 0.5);
        b.handle = Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) { return step(b, age); }
            @Override public void end() { finish(b); }
        });
        Fx.bar(p, Stone.POWER, "infinity.extra.v2d.bomb.planted", seconds, mine.size());
        return true;
    }

    private static boolean step(Bomb b, int age) {
        b.age = age;
        ServerLevel l = b.level;
        ServerPlayer owner = l.getServer().getPlayerList().getPlayer(b.owner);
        if (owner == null || owner.isRemoved() || owner.serverLevel() != l) { b.silent = true; return false; }
        if (b.detonate || age >= b.total) return false;
        float prog = age / (float) b.total;
        if (age % 40 == 39) V2dNet.bomb(l, b.id, b.pos, 1.1F, PURPLE, GREEN, b.total - age + 5, prog);
        if (age >= b.nextTick) {
            int interval = Math.max(2, (int) (20 - 18 * prog * prog));
            b.nextTick = age + interval;
            Fx.sound(l, b.pos, SoundEvents.NOTE_BLOCK_HAT, 0.5F, 0.6F + prog * 1.2F);
            if (prog > 0.6F) Fx.sound(l, b.pos, SoundEvents.NOTE_BLOCK_BIT, 0.25F, 0.5F + prog);
            Fx.at(l, Fx.dust(age % (interval * 2) == 0 ? PURPLE : GREEN, 1.1F), b.pos, 6, 0.5, 0.01);
            Fx.at(l, ParticleTypes.ELECTRIC_SPARK, b.pos.add(0, 0.5, 0), 2, 0.4, 0.05);
        }
        return true;
    }

    private static void finish(Bomb b) {
        List<Bomb> mine = BOMBS.get(b.owner);
        if (mine != null) { mine.remove(b); if (mine.isEmpty()) BOMBS.remove(b.owner); }
        ServerLevel l = b.level;
        V2dNet.kill(l, b.pos, b.id);
        if (b.silent || (!b.detonate && b.age < b.total)) {
            Fx.at(l, Fx.dust(PURPLE, 1F), b.pos, 14, 0.5, 0.02);
            return;
        }
        explode(b);
    }

    private static void explode(Bomb b) {
        ServerLevel l = b.level;
        Vec3 c = b.pos;
        ServerPlayer owner = l.getServer().getPlayerList().getPlayer(b.owner);
        AABB box = new AABB(c, c).inflate(RADIUS);
        if (owner != null) {
            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, box, e -> Rules.canHarm(owner, e))) {
                Vec3 mid = e.getBoundingBox().getCenter();
                double d = mid.distanceTo(c);
                if (d > RADIUS) continue;
                double k = 1 - d / RADIUS;
                Vec3 dir = mid.subtract(c);
                dir = dir.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : dir.normalize();
                Rules.hurt(owner, e, (float) (4 + 2 * k));
                Move.set(e, new Vec3(dir.x * (1.1 + 2.4 * k), 0.55 + 0.9 * k, dir.z * (1.1 + 2.4 * k)));
                e.fallDistance = 0;
            }
        }
        FxNet.shockwave(l, c, (float) RADIUS * 1.6F, PURPLE, 26);
        FxNet.shockwave(l, c.add(0, 0.2, 0), (float) RADIUS * 1.25F, GREEN, 22);
        FxNet.shockwave(l, c.add(0, 0.4, 0), (float) RADIUS * 0.8F, 0xFFFFFF, 16);
        FxNet.flare(l, c, 7F, PURPLE, 14);
        FxNet.flare(l, c, 4F, GREEN, 18);
        FxNet.runes(l, 0, c.add(0, -0.2, 0), 5F, GREEN, 30, 4F);
        FxNet.spiral(l, c.add(0, -0.5, 0), 3.5F, -6F, PURPLE, 24, -1);
        FxNet.pillar(l, 0, c, 0.7F, 9F, GREEN, 18);
        FxNet.flashAround(l, c, RADIUS + 12, 0xB66BFF, 0.4F, 10);
        FxNet.shake(l, c, RADIUS + 18, 1.1F, 14);
        Fx.burstRing(l, c, 48, Fx.dust(PURPLE, 2F), 0.7);
        Fx.burstRing(l, c, 36, Fx.dust(GREEN, 1.6F), 0.45);
        Fx.at(l, ParticleTypes.END_ROD, c, 60, 0.2, 0.9);
        Fx.at(l, ParticleTypes.EXPLOSION_EMITTER, c, 1, 0, 0);
        Fx.sound(l, c, SoundEvents.GENERIC_EXPLODE, 0.7F, 0.7F);
        Fx.sound(l, c, SoundEvents.BEACON_DEACTIVATE, 0.7F, 1.8F);
        Fx.sound(l, c, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.7F, 0.6F);
        Fx.sound(l, c, SoundEvents.BELL_RESONATE, 0.6F, 0.7F);
    }

    private TimeBomb() {}
}

