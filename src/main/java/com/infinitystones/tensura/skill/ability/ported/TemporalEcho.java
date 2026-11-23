package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Port of palladium:temporal_echo (Time, 122-tick activation). During the 120-tick window the caster's first three
 * confirmed hits on hostile monsters (within 64 blocks) are echoed twice: 0.5 s and 1 s later the target takes half of
 * the health it actually lost again (capped at 80 per echo), with the {@code infinity:temporal_echo} damage type and
 * the caster as indirect attacker. A hit near the end may still echo after the window, but never after losing the
 * Stone. Echo damage never re-echoes.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class TemporalEcho implements Ported {
    private static final ResourceKey<DamageType> ECHO_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, InfinityStones.id("temporal_echo"));
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static boolean echoing;

    private record Echo(UUID target, float damage, long at, int number) {}
    private static final class Session {
        final ServerLevel level; final ServerPlayer owner; final long windowEnd; final int rgb;
        final List<Echo> jobs = new ArrayList<>();
        boolean active = true; int hits; UUID captured; float healthBefore = Float.NaN;
        Session(ServerLevel level, ServerPlayer owner, long windowEnd, int rgb) {
            this.level = level; this.owner = owner; this.windowEnd = windowEnd; this.rgb = rgb;
        }
    }

    public TemporalEcho() {}

    @Override
    public void start(ServerPlayer p, StoneTree.Node n) {
        ServerLevel level = p.serverLevel();
        SESSIONS.put(p.getUUID(), new Session(level, p, level.getGameTime() + 120L, n.stone().color() & 0xFFFFFF));
        PortFx.sound(level, p.getEyePosition(), n, "infinity:time_stone", 0.7F, 1.5F);
    }

    @Override
    public void tick(ServerPlayer p, StoneTree.Node n, int ticks) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || !valid(s)) return;
        if (ticks % 5 == 0 && s.hits < 3) {
            for (int i = s.hits; i < 3; i++) {
                double a = p.tickCount * 0.07D + i * Math.PI * 2D / 3D;
                s.level.sendParticles(ParticleTypes.END_ROD, p.getX() + Math.cos(a) * 0.8D, p.getEyeY() + 0.45D,
                        p.getZ() + Math.sin(a) * 0.8D, 1, 0D, 0D, 0D, 0D);
                s.level.sendParticles(PortFx.dust(s.rgb, 0.6F), p.getX() + Math.cos(a) * 0.8D, p.getEyeY() + 0.45D,
                        p.getZ() + Math.sin(a) * 0.8D, 1, 0D, 0D, 0D, 0D);
            }
        }
    }

    @Override
    public void stop(ServerPlayer p, StoneTree.Node n) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null) return;
        s.active = false;
        // A final hit may echo after the natural activation ends, but never after losing the Stone.
        if (s.level.getGameTime() < s.windowEnd || !valid(s)) SESSIONS.remove(p.getUUID());
    }

    private static boolean valid(Session s) {
        return s.owner.isAlive() && !s.owner.isRemoved() && s.owner.level() == s.level && !s.owner.hasDisconnected()
                && Stone.TIME.isHeldBy(s.owner) && s.level.getGameTime() <= s.windowEnd + 25L;
    }

    private static boolean targetAllowed(Session s, LivingEntity target) {
        return target instanceof Enemy && !target.isSpectator() && target.level() == s.level
                && !s.owner.isAlliedTo(target) && !target.isAlliedTo(s.owner)
                && s.owner.distanceToSqr(target) <= 64D * 64D && PortFx.canAffect(s.owner, target);
    }

    public static boolean isEcho(DamageSource source) { return echoing || source.is(ECHO_DAMAGE); }

    // ---------------- hooks ----------------

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void beforeDamage(LivingIncomingDamageEvent e) {
        DamageSource source = e.getSource();
        if (isEcho(source) || !(source.getEntity() instanceof ServerPlayer attacker)) return;
        Session s = SESSIONS.get(attacker.getUUID());
        LivingEntity target = e.getEntity();
        if (s == null || !s.active || !valid(s) || s.hits >= 3 || s.level.getGameTime() >= s.windowEnd
                || !targetAllowed(s, target) || !target.isAlive()) return;
        s.captured = target.getUUID();
        s.healthBefore = target.getHealth();
    }

    @SubscribeEvent
    public static void afterDamage(LivingDamageEvent.Post e) {
        DamageSource source = e.getSource();
        if (isEcho(source) || !(source.getEntity() instanceof ServerPlayer attacker)) return;
        Session s = SESSIONS.get(attacker.getUUID());
        LivingEntity target = e.getEntity();
        if (s == null || !target.getUUID().equals(s.captured) || !Float.isFinite(s.healthBefore)) return;
        float lost = Math.max(0F, s.healthBefore - target.getHealth());
        s.captured = null;
        s.healthBefore = Float.NaN;
        if (!valid(s) || s.hits >= 3 || lost <= 0F || !targetAllowed(s, target)) return;
        s.hits++;
        if (target.isAlive()) {
            float dmg = Math.min(80F, lost * 0.5F);
            long now = s.level.getGameTime();
            s.jobs.add(new Echo(target.getUUID(), dmg, now + 10L, 1));
            s.jobs.add(new Echo(target.getUUID(), dmg, now + 20L, 2));
            render(s, target, 0);
        }
    }

    @SubscribeEvent
    public static void ownerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer owner)) return;
        Session s = SESSIONS.get(owner.getUUID());
        if (s == null) return;
        if (!valid(s) || s.owner != owner) { SESSIONS.remove(owner.getUUID()); return; }
        long now = s.level.getGameTime();
        for (Echo echo : new ArrayList<>(s.jobs)) {
            if (echo.at > now) continue;
            s.jobs.remove(echo);
            Entity entity = s.level.getEntity(echo.target);
            if (!(entity instanceof LivingEntity target) || !target.isAlive() || !targetAllowed(s, target)) continue;
            echoing = true;
            try {
                // Indirect source (no direct entity) so it never counts as a melee strike.
                target.hurt(new DamageSource(damageType(s.level), null, owner), echo.damage);
            } finally {
                echoing = false;
            }
            render(s, target, echo.number);
            PortFx.sound(s.level, target.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.65F, echo.number == 1 ? 1.3F : 1.7F);
        }
        if (now >= s.windowEnd && s.jobs.isEmpty()) SESSIONS.remove(owner.getUUID());
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { SESSIONS.remove(e.getEntity().getUUID()); }

    // ---------------- helpers ----------------

    private static Holder<DamageType> damageType(ServerLevel level) {
        var reg = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        return reg.getHolder(ECHO_DAMAGE).<Holder<DamageType>>map(h -> h).orElseGet(() -> reg.getHolderOrThrow(DamageTypes.MAGIC));
    }

    private static void render(Session s, LivingEntity target, int echo) {
        DustParticleOptions green = PortFx.dust(s.rgb, 0.7F), pale = PortFx.dust(0xB3FFCC, 0.45F);
        Vec3 c = target.getBoundingBox().getCenter();
        double r = Math.max(0.7D, Math.min(2D, target.getBbWidth() * 0.7D));
        for (int i = 0; i < 28; i++) {
            double a = i * Math.PI * 2D / 28D;
            s.level.sendParticles(i % 3 == 0 ? pale : green, c.x + Math.cos(a) * r, c.y + Math.sin(a) * r,
                    c.z + echo * 0.12D, 1, 0D, 0D, 0D, 0D);
        }
        if (echo > 0) s.level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 8, 0.35D, 0.45D, 0.35D, 0.04D);
    }
}
