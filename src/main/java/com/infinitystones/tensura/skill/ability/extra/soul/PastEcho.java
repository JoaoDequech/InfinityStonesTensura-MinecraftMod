package com.infinitystones.tensura.skill.ability.extra.soul;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import com.infinitystones.tensura.skill.ability.extra.time.TimeNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Echo of the Past (conjunction Time + Soul). While you carry both stones the world around you is remembered: every
 * 5 ticks the position of the creatures within 24 blocks is written into a 10 s ring. Left (aim): the aimed creature's
 * last 10 s are summoned as a ghost — it appears where that creature stood 10 s ago and re-lives the whole path in 3 s,
 * slashing everything it passes (1.5× damage), then stays 20 s as a spectral fighter on your side. Right: your own echo.
 * Up to two echoes at a time. A creature must have been in range for at least 2 s.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class PastEcho implements Ability {
    public static final PastEcho INSTANCE = new PastEcho();
    private static final String K = "infinity.extra.echo.";
    private static final int GREEN = 0x2BD56A, SAMPLES = 40, EVERY = 5, MIN = 8, REPLAY_TICKS = 60, STAY = 400;

    private static final class Track {
        final ArrayDeque<Ghosts.Sample> samples = new ArrayDeque<>();
        final EntityType<?> type; final float maxHealth, attack, w, h;
        long seen;
        Track(LivingEntity e) {
            type = e.getType(); maxHealth = e.getMaxHealth(); w = e.getBbWidth(); h = e.getBbHeight();
            AttributeInstance a = e.getAttribute(Attributes.ATTACK_DAMAGE);
            attack = a == null ? 0 : (float) a.getValue();
        }
    }

    private static final Map<UUID, Map<UUID, Track>> MEMORY = new HashMap<>();

    @Override public Stone stone() { return Stone.TIME; }

    // ------------------------------------------------------------------ memory

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.tickCount % EVERY != 0) return;
        if (!Stone.TIME.isHeldBy(p) || !Stone.SOUL.isHeldBy(p)) { MEMORY.remove(p.getUUID()); return; }
        Map<UUID, Track> mem = MEMORY.computeIfAbsent(p.getUUID(), k -> new HashMap<>());
        long now = p.tickCount;
        int n = 0;
        for (LivingEntity le : p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(24, 12, 24),
                x -> x.isAlive() && !(x instanceof ArmorStand) && !Ghosts.isGhost(x))) {
            if (++n > 32) break;
            Track t = mem.computeIfAbsent(le.getUUID(), k -> new Track(le));
            if (now - t.seen > EVERY * 3 && !t.samples.isEmpty()) t.samples.clear();
            t.seen = now;
            t.samples.addLast(new Ghosts.Sample(le.getX(), le.getY(), le.getZ(), le.getYRot(), le.getXRot()));
            while (t.samples.size() > SAMPLES) t.samples.removeFirst();
        }
        mem.values().removeIf(t -> now - t.seen > 300);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { MEMORY.remove(e.getEntity().getUUID()); }

    // ------------------------------------------------------------------ cast

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        LivingEntity t = alt ? p : Aim.living(p, 40, e -> true);
        if (t == null) t = p;
        Map<UUID, Track> mem = MEMORY.get(p.getUUID());
        Track tr = mem == null ? null : mem.get(t.getUUID());
        if (tr == null || tr.samples.size() < MIN) return Fx.fail(p, K + "forgotten", t.getDisplayName());
        ServerLevel l = p.serverLevel();
        List<Ghosts.Sample> path = new ArrayList<>(tr.samples);
        Ghosts.Sample first = path.get(0);
        Vec3 start = new Vec3(first.x(), first.y(), first.z());

        boolean person = t instanceof ServerPlayer || tr.type == EntityType.PLAYER;
        EntityType<?> type = person ? EntityType.HUSK : tr.type;
        float hp = Mth.clamp(tr.maxHealth, 20F, 80F);
        float strike = person ? 12F : Mth.clamp(tr.attack * 1.5F, 8F, 20F);
        float speed = (path.size() - 1) / (float) REPLAY_TICKS;
        Ghosts.Ghost g = Ghosts.spawn(p, type, start, first.yaw(), Ghosts.ECHO, REPLAY_TICKS + STAY, hp, strike, GREEN, path, speed);
        if (g == null) return Fx.fail(p, K + "forgotten", t.getDisplayName());
        fx(p, l, t, tr, path, start);
        Fx.bar(p, Stone.TIME, K + "cast", t.getDisplayName());
        return true;
    }

    private static void fx(ServerPlayer p, ServerLevel l, LivingEntity t, Track tr, List<Ghosts.Sample> path, Vec3 start) {
        Vec3 now = t.position();
        // the creature "now": clock dial turning backwards under it, soul runes over it, time falling around it
        FxNet.clock(l, 0, now.add(0, 0.06, 0), Math.max(2.2F, tr.w * 2.5F), GREEN, 50, -9F);
        FxNet.runes(l, 0, now.add(0, 0.09, 0), Math.max(1.6F, tr.w * 2F), SoulKit.ORANGE, 50, 2F);
        FxNet.spiral(l, now, tr.w + 0.5F, tr.h + 2F, GREEN, 26, t.getId());
        FxNet.spiral(l, now, tr.w + 1F, -(tr.h + 2F), SoulKit.ORANGE, 26, -1);
        FxNet.shockwave(l, now.add(0, 0.1, 0), 5F, GREEN, 18);
        FxNet.beam(l, p.getEyePosition().add(0, -0.3, 0), now.add(0, tr.h * 0.6, 0), SoulKit.LIGHT, 0.1F, 12);

        // the past: a rift opens where it all began, soul + time circles around it
        SoulKit.circle(l, start, 4.5F, GREEN, 70);
        FxNet.clock(l, 0, start.add(0, 0.1, 0), 3F, SoulKit.ORANGE, 60, 7F);
        Vec3 toNow = now.subtract(start);
        Vec3 normal = toNow.lengthSqr() < 0.01 ? new Vec3(0, 0, 1) : toNow.normalize();
        FxNet.portal(l, 0, start.add(0, tr.h * 0.6 + 0.4, 0), normal, 1.6F, GREEN, 50);
        FxNet.pillar(l, 0, start, 0.9F, 8F, SoulKit.ORANGE, 30);
        Fx.at(l, ParticleTypes.REVERSE_PORTAL, start.add(0, tr.h * 0.5, 0), 40, 0.5, 0.15);
        if (start.distanceToSqr(now) > 4) FxNet.tunnel(l, start.add(0, 0.8, 0), now.add(0, 0.8, 0), 1.0F, GREEN, 30);

        // afterimages walking the old path + a line of ember along it
        int n = path.size();
        for (int g = 0; g < 14; g++) {
            int idx = Math.round(g * (n - 1) / 13F);
            Ghosts.Sample s = path.get(idx);
            TimeNet.ghost(l, new Vec3(s.x(), s.y(), s.z()), tr.w, tr.h, g * 4, 16, s.yaw());
        }
        for (int i = 0; i < n - 1; i++) {
            Ghosts.Sample a = path.get(i), b = path.get(i + 1);
            Fx.line(l, new Vec3(a.x(), a.y() + 0.1, a.z()), new Vec3(b.x(), b.y() + 0.1, b.z()), 0.5, Fx.dust(i % 2 == 0 ? GREEN : SoulKit.ORANGE, 1.1F));
        }
        FxNet.shake(l, start, 16, 0.3F, 10);
        FxNet.flash(p, GREEN, 0.15F, 8);
        Fx.sound(l, now, SoundEvents.BELL_BLOCK, 0.6F, 0.6F);
        Fx.sound(l, start, SoundEvents.SOUL_ESCAPE.value(), 0.7F, 0.5F);
        Fx.sound(l, start, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 0.5F);
        Fx.sound(l, start, SoundEvents.BEACON_ACTIVATE, 0.35F, 1.7F);
    }
}
