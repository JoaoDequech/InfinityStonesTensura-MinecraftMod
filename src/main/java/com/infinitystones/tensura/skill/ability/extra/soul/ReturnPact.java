package com.infinitystones.tensura.skill.ability.extra.soul;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Return Pact (Soul). Left: sign a pact with your soul at the spot where you stand — a rune circle and a pillar of light
 * mark it. For 10 minutes, if you would die you do not: your soul is pulled back to the pact spot with your whole
 * inventory, experience and effects-free health, once (the pact is spent). Right: tear the pact up. The pact is stored on
 * the player (it survives relogging) and uses the server clock, so it works the same in multiplayer, without OP.
 * Admin kills (/kill) are not blocked.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class ReturnPact implements Ability {
    public static final ReturnPact INSTANCE = new ReturnPact();
    private static final String K = "infinity.extra.pact.";
    private static final String DATA = "infinity_return_pact";
    private static final long DURATION = 12000;

    private static final Map<UUID, Tasks.Handle> BEACONS = new HashMap<>();

    @Override public Stone stone() { return Stone.SOUL; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        if (alt) {
            if (!active(p)) return Fx.fail(p, K + "none");
            ServerLevel l = level(p);
            Vec3 at = spot(p);
            if (l != null) {
                FxNet.shockwave(l, at.add(0, 0.1, 0), 6F, SoulKit.EMBER, 18);
                SoulKit.soulBurst(l, at.add(0, 1, 0), 1.5F);
                Fx.sound(l, at, SoundEvents.AMETHYST_BLOCK_BREAK, 0.6F, 0.6F);
            }
            clear(p);
            Fx.bar(p, Stone.SOUL, K + "broken");
            return true;
        }
        ServerLevel l = p.serverLevel();
        boolean replace = active(p);
        if (replace) { ServerLevel old = level(p); if (old != null) FxNet.shockwave(old, spot(p).add(0, 0.1, 0), 3F, SoulKit.EMBER, 12); }
        CompoundTag t = new CompoundTag();
        t.putString("dim", l.dimension().location().toString());
        t.putDouble("x", p.getX());
        t.putDouble("y", p.getY());
        t.putDouble("z", p.getZ());
        t.putFloat("yaw", p.getYRot());
        t.putFloat("pitch", p.getXRot());
        t.putLong("until", p.server.overworld().getGameTime() + DURATION);
        Rules.data(p).put(DATA, t);
        beacon(p);

        Vec3 c = p.position();
        SoulKit.circle(l, c, 5F, SoulKit.ORANGE, 90);
        FxNet.pillar(l, 0, c, 1.2F, 9F, SoulKit.LIGHT, 40);
        FxNet.spiral(l, c, 1.6F, 5F, SoulKit.PALE, 36, p.getId());
        FxNet.spiral(l, c, 2.6F, -4F, SoulKit.SPIRIT, 28, -1);
        FxNet.dome(l, 0, c, 2.5F, SoulKit.ORANGE, 16);
        FxNet.flash(p, SoulKit.LIGHT, 0.2F, 8);
        FxNet.shake(l, c, 16, 0.25F, 8);
        Fx.burstRing(l, c.add(0, 0.2, 0), 40, ParticleTypes.SOUL, 0.3);
        Fx.sound(l, c, SoundEvents.BELL_RESONATE, 0.6F, 0.5F);
        Fx.sound(l, c, SoundEvents.SOUL_ESCAPE.value(), 0.6F, 0.5F);
        Fx.sound(l, c, SoundEvents.BEACON_ACTIVATE, 0.4F, 1.4F);
        Fx.bar(p, Stone.SOUL, K + "signed");
        return true;
    }

    // ------------------------------------------------------------------ state

    private static boolean active(ServerPlayer p) {
        CompoundTag t = Rules.data(p).getCompound(DATA);
        if (t.isEmpty()) return false;
        if (p.server.overworld().getGameTime() > t.getLong("until")) { clear(p); return false; }
        return true;
    }

    private static void clear(ServerPlayer p) {
        Rules.data(p).remove(DATA);
        Tasks.Handle h = BEACONS.remove(p.getUUID());
        if (h != null) h.cancel();
    }

    private static ServerLevel level(ServerPlayer p) {
        CompoundTag t = Rules.data(p).getCompound(DATA);
        ResourceLocation id = ResourceLocation.tryParse(t.getString("dim"));
        return id == null ? null : p.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    private static Vec3 spot(ServerPlayer p) {
        CompoundTag t = Rules.data(p).getCompound(DATA);
        return new Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z"));
    }

    /** Keeps the marker alive for everyone nearby while the pact stands and its owner is online. */
    private static void beacon(ServerPlayer p) {
        Tasks.Handle old = BEACONS.remove(p.getUUID());
        if (old != null) old.cancel();
        UUID id = p.getUUID();
        int fxId = FxNet.newId();
        Tasks.Handle[] self = new Tasks.Handle[1];
        self[0] = Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) {
                ServerPlayer o = p.server.getPlayerList().getPlayer(id);
                if (o == null || BEACONS.get(id) != self[0]) return false;
                if (age % 20 != 0) return true;
                if (!active(o)) return false;
                ServerLevel l = level(o);
                if (l == null) return false;
                Vec3 at = spot(o);
                if (!l.isLoaded(net.minecraft.core.BlockPos.containing(at))) return true;
                FxNet.runes(l, fxId, at.add(0, 0.06, 0), 3.4F, SoulKit.ORANGE, 40, 1F);
                FxNet.runes(l, fxId + 1, at.add(0, 0.08, 0), 2F, SoulKit.PALE, 40, -1.6F);
                FxNet.pillar(l, fxId + 2, at, 0.5F, 6F, SoulKit.LIGHT, 40);
                Fx.at(l, ParticleTypes.SOUL, at.add(0, 0.4, 0), 2, 0.5, 0.03);
                return true;
            }
        });
        BEACONS.put(id, self[0]);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && active(p)) beacon(p);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        Tasks.Handle h = BEACONS.remove(e.getEntity().getUUID());
        if (h != null) h.cancel();
    }

    // ------------------------------------------------------------------ the return

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void death(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.level().isClientSide || !active(p)) return;
        if (e.getSource().is(DamageTypes.GENERIC_KILL)) return;
        ServerLevel dest = level(p);
        if (dest == null) { clear(p); return; }
        Vec3 spot = spot(p);
        CompoundTag t = Rules.data(p).getCompound(DATA);
        float yaw = t.getFloat("yaw"), pitch = t.getFloat("pitch");
        clear(p);

        e.setCanceled(true);
        p.setHealth(p.getMaxHealth());
        p.removeAllEffects();
        p.clearFire();
        p.fallDistance = 0;
        p.setDeltaMovement(Vec3.ZERO);
        p.setAirSupply(p.getMaxAirSupply());
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 4));
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 2));
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 200, 0));
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 1));
        p.invulnerableTime = 40;

        ServerLevel from = p.serverLevel();
        Vec3 here = p.position();
        SoulKit.soulBurst(from, here.add(0, 1, 0), 2F);
        FxNet.shockwave(from, here.add(0, 0.1, 0), 7F, SoulKit.ORANGE, 18);
        FxNet.pillar(from, 0, here, 1F, 12F, SoulKit.PALE, 14);
        Fx.sound(from, here, SoundEvents.TOTEM_USE, 0.5F, 0.8F);

        // teleport one tick later, outside the damage handling
        Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) {
                if (age < 1) return true;
                if (!p.isAlive() || p.hasDisconnected()) return false;
                Vec3 at = safe(dest, p, spot);
                p.teleportTo(dest, at.x, at.y, at.z, yaw, pitch);
                p.fallDistance = 0;
                arrive(p, dest, at);
                return false;
            }
        });
    }

    /** The pact spot, or the nearest free spot above it when something has been built there since. */
    private static Vec3 safe(ServerLevel l, ServerPlayer p, Vec3 spot) {
        if (Ground.fits(l, p, spot)) return spot;
        for (int up = 1; up <= 6; up++) {
            Vec3 v = spot.add(0, up, 0);
            if (Ground.fits(l, p, v)) return v;
        }
        return spot.add(0, 1, 0);
    }

    private static void arrive(ServerPlayer p, ServerLevel l, Vec3 at) {
        SoulKit.circle(l, at, 6.5F, SoulKit.ORANGE, 70);
        FxNet.pillar(l, 0, at, 1.5F, 14F, SoulKit.LIGHT, 28);
        FxNet.spiral(l, at, 2.2F, 6F, SoulKit.PALE, 32, p.getId());
        FxNet.spiral(l, at, 3F, -5F, SoulKit.SPIRIT, 26, -1);
        FxNet.dome(l, 0, at, 3F, SoulKit.ORANGE, 14);
        FxNet.shake(l, at, 28, 0.5F, 14);
        FxNet.flash(p, SoulKit.PALE, 0.7F, 24);
        Fx.burstRing(l, at.add(0, 0.3, 0), 56, ParticleTypes.SOUL, 0.5);
        Fx.at(l, ParticleTypes.SOUL_FIRE_FLAME, at.add(0, 1, 0), 40, 0.5, 0.15);
        Fx.sound(l, at, SoundEvents.TOTEM_USE, 0.6F, 1.2F);
        Fx.sound(l, at, SoundEvents.BELL_RESONATE, 0.6F, 0.7F);
        Fx.sound(l, at, SoundEvents.SOUL_ESCAPE.value(), 0.7F, 0.6F);
        Fx.bar(p, Stone.SOUL, K + "returned");
    }
}
