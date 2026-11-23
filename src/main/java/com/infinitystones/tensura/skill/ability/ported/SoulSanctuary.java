package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Port of palladium:soul_sanctuary — timed session (original palladium:activation, 200 ticks). Anchors a circle
 * (radius, ±3 blocks tall) where the caster stands. On start and every 20 ticks the caster and its allies inside
 * (team mates, own tamed pets) heal 8 and lose harmful effects; while it lasts all damage to them inside the circle
 * is cancelled (except /kill and the void). Ends if the caster leaves it by more than 32 blocks, dies or logs out.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class SoulSanctuary implements Ported {

    private static final Map<UUID, Sanctuary> SANCTUARIES = new HashMap<>();

    public SoulSanctuary() {}

    @Override
    public void start(ServerPlayer owner, StoneTree.Node n) {
        if (!owner.isAlive()) return;
        ServerLevel level = owner.serverLevel();
        float radius = Math.max(2F, Math.min(16F, PortFx.f(n, "radius", 6F)));
        int c = n.stone().color();
        Sanctuary s = new Sanctuary(level, owner, owner.position(), radius, level.getGameTime() + 200L,
                PortFx.dust(c, 0.9F), PortFx.dust(SpatialExchange.lighten(c, 0.5F), 0.65F));
        SANCTUARIES.put(owner.getUUID(), s);
        healAllies(s);
        render(s);
        PortFx.sound(level, owner.getEyePosition(), n, "infinity:soul_stone", 0.7F, 0.9F);
        PortFx.sound(level, owner.position(), SoundEvents.BEACON_ACTIVATE, 0.6F, 1.3F);
    }

    @Override
    public void tick(ServerPlayer owner, StoneTree.Node n, int ticks) {
        Sanctuary s = SANCTUARIES.get(owner.getUUID());
        if (s == null) return;
        if (!valid(s)) { SANCTUARIES.remove(owner.getUUID()); return; }
        int enabled = ticks + 1; // original getEnabledTicks()
        if (enabled < 200 && enabled % 20 == 0) healAllies(s);
        if (enabled % 5 == 0) render(s);
    }

    @Override
    public void stop(ServerPlayer owner, StoneTree.Node n) {
        SANCTUARIES.remove(owner.getUUID());
    }

    // ------------------------------------------------------------------ protection hook (original AbilityEventHandler)

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onIncomingDamage(LivingIncomingDamageEvent e) {
        if (SANCTUARIES.isEmpty()) return;
        if (e.getSource().is(DamageTypes.GENERIC_KILL) || e.getSource().is(DamageTypes.FELL_OUT_OF_WORLD)) return;
        if (protects(e.getEntity())) e.setCanceled(true);
    }

    public static boolean protects(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) return false;
        for (Sanctuary s : new ArrayList<>(SANCTUARIES.values())) {
            if (!valid(s)) { SANCTUARIES.remove(s.owner.getUUID()); continue; }
            if (s.level == level && within(s, entity) && ally(s.owner, entity)) return true;
        }
        return false;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) { SANCTUARIES.remove(e.getEntity().getUUID()); }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) { SANCTUARIES.clear(); }

    // ------------------------------------------------------------------ logic

    private static boolean valid(Sanctuary s) {
        ServerPlayer owner = s.owner;
        return owner.isAlive() && !owner.isRemoved() && owner.level() == s.level && !owner.hasDisconnected()
                && s.level.getGameTime() < s.expiresAt
                && owner.position().distanceToSqr(s.center) <= 32D * 32D;
    }

    private static boolean ally(ServerPlayer owner, LivingEntity entity) {
        return entity == owner || owner.isAlliedTo(entity)
                || entity instanceof TamableAnimal pet && owner.getUUID().equals(pet.getOwnerUUID());
    }

    private static boolean within(Sanctuary s, LivingEntity entity) {
        Vec3 d = entity.position().subtract(s.center);
        return Math.abs(d.y) <= 3D && d.x * d.x + d.z * d.z <= s.radius * s.radius;
    }

    private static void healAllies(Sanctuary s) {
        Vec3 c = s.center;
        for (LivingEntity entity : s.level.getEntitiesOfClass(LivingEntity.class,
                new AABB(c.x - s.radius, c.y - 3D, c.z - s.radius, c.x + s.radius, c.y + 3D, c.z + s.radius),
                e -> e.isAlive() && within(s, e) && ally(s.owner, e))) {
            entity.heal(8F);
            for (MobEffectInstance effect : new ArrayList<>(entity.getActiveEffects())) {
                if (effect.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) entity.removeEffect(effect.getEffect());
            }
            s.level.sendParticles(ParticleTypes.HEART, entity.getX(), entity.getEyeY() + 0.8D, entity.getZ(), 2, 0.25D, 0.2D, 0.25D, 0D);
        }
        PortFx.sound(s.level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.4F, 1.05F);
    }

    private static void render(Sanctuary s) {
        Vec3 c = s.center;
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI / 32D;
            double r = i % 2 == 0 ? s.radius : s.radius * 0.82D;
            s.level.sendParticles(i % 4 == 0 ? s.light : s.main,
                    c.x + Math.cos(a) * r, c.y + 0.15D, c.z + Math.sin(a) * r, 1, 0D, 0D, 0D, 0D);
            if (i % 8 == 0) s.level.sendParticles(ParticleTypes.END_ROD,
                    c.x + Math.cos(a) * s.radius, c.y + 0.22D, c.z + Math.sin(a) * s.radius, 1, 0D, 0.02D, 0D, 0.005D);
        }
        for (int pillar = 0; pillar < 4; pillar++) {
            double a = pillar * Math.PI / 2D;
            for (int h = 0; h < 5; h++) {
                s.level.sendParticles(h % 2 == 0 ? s.light : s.main,
                        c.x + Math.cos(a) * s.radius, c.y + 0.25D + h * 0.4D, c.z + Math.sin(a) * s.radius, 1, 0D, 0D, 0D, 0D);
            }
            s.level.sendParticles(ParticleTypes.FLAME,
                    c.x + Math.cos(a) * s.radius, c.y + 2.15D, c.z + Math.sin(a) * s.radius, 1, 0.025D, 0.05D, 0.025D, 0.005D);
        }
    }

    private record Sanctuary(ServerLevel level, ServerPlayer owner, Vec3 center, float radius, long expiresAt,
                             DustParticleOptions main, DustParticleOptions light) {}
}
