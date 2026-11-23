package com.infinitystones.tensura.skill.ability.extra.time;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import com.infinitystones.tensura.skill.ability.extra.soul.Ghosts;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Time Clone (Time). Left: two copies of you step out of a green rift from 3 s in the future, wearing your armour and
 * holding your weapon, and fight at your side for 20 s. Whenever YOU hit something they blink next to it in a flash of
 * time and echo the blow (60% of your damage each) a moment later. Right: send them back. Clones are green-glowing allies
 * (never hurt you or your allies, drop nothing, give no experience); a new cast replaces the old pair.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class TimeClones implements Ability {
    public static final TimeClones INSTANCE = new TimeClones();
    private static final String K = "infinity.extra.clones.";
    private static final int GREEN = Stone.TIME.color() & 0xFFFFFF, KIND = 2, LIFE = 400, ARRIVE = 22;
    private static final EquipmentSlot[] GEAR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    private static final Map<UUID, List<Ghosts.Ghost>> CLONES = new HashMap<>();
    private static boolean echoing;

    @Override public Stone stone() { return Stone.TIME; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        List<Ghosts.Ghost> mine = alive(p.getUUID());
        if (alt) {
            if (mine.isEmpty()) return Fx.fail(p, K + "none");
            for (Ghosts.Ghost g : mine) g.expireNow();
            CLONES.remove(p.getUUID());
            Fx.bar(p, Stone.TIME, K + "gone");
            return true;
        }
        for (Ghosts.Ghost g : mine) g.expireNow();
        CLONES.remove(p.getUUID());
        ServerLevel l = p.serverLevel();
        float yaw = p.getYRot();
        double side = Math.toRadians(yaw);
        Vec3 right = new Vec3(-Math.cos(side), 0, -Math.sin(side));
        boolean ranged = p.getMainHandItem().getItem() instanceof BowItem || p.getMainHandItem().getItem() instanceof CrossbowItem;
        float dmg = (float) Math.max(3, Math.min(14, p.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.6));
        float hp = Math.max(20F, Math.min(40F, p.getMaxHealth()));
        int placed = 0;
        for (int s = -1; s <= 1; s += 2) {
            Vec3 want = p.position().add(right.scale(2.2 * s)).add(0, 1.5, 0);
            Vec3 at = Ground.below(l, want, 5);
            if (!Ground.fits(l, p, at)) at = Ground.below(l, p.position().add(0, 1.5, 0), 3);
            final Vec3 spawn = at;
            placed++;
            FxNet.portal(l, 0, spawn.add(0, 1.1, 0), right.scale(s), 1.6F, GREEN, ARRIVE + 6);
            FxNet.clock(l, 0, spawn.add(0, 0.05, 0), 1.8F, GREEN, ARRIVE + 10, -6F);
            FxNet.spiral(l, spawn, 0.9F, -3F, GREEN, ARRIVE, -1);
            TimeNet.ghost(l, spawn, p.getBbWidth(), p.getBbHeight(), 0, ARRIVE, yaw);
            Fx.sound(l, spawn, SoundEvents.BEACON_DEACTIVATE, 0.5F, 1.6F);
            Tasks.add(new Tasks.Task() {
                @Override public boolean tick(int age) {
                    if (!p.isAlive() || p.hasDisconnected() || p.level() != l) return false;
                    Fx.at(l, ParticleTypes.REVERSE_PORTAL, spawn.add(0, 1, 0), 3, 0.3, 0.1);
                    return age < ARRIVE;
                }
                @Override public void end() { if (p.isAlive() && !p.hasDisconnected() && p.level() == l) arrive(p, l, spawn, yaw, ranged, hp, dmg); }
            });
        }
        if (placed == 0) return false;
        Fx.bar(p, Stone.TIME, K + "on");
        return true;
    }

    private static void arrive(ServerPlayer p, ServerLevel l, Vec3 at, float yaw, boolean ranged, float hp, float dmg) {
        EntityType<?> type = ranged ? EntityType.SKELETON : EntityType.HUSK;
        Ghosts.Ghost g = Ghosts.spawn(p, type, at, yaw, KIND, LIFE, hp, dmg, GREEN, null, 0);
        if (g == null) return;
        Mob m = g.mob();
        for (EquipmentSlot s : GEAR) {
            ItemStack st = p.getItemBySlot(s);
            m.setItemSlot(s, st.copy());
            m.setDropChance(s, 0F);
        }
        m.setCustomName(net.minecraft.network.chat.Component.translatable(K + "name", p.getDisplayName()).withColor(GREEN));
        CLONES.computeIfAbsent(p.getUUID(), k -> new ArrayList<>()).add(g);
        Vec3 c = at.add(0, m.getBbHeight() / 2, 0);
        FxNet.shockwave(l, at.add(0, 0.1, 0), 3.5F, GREEN, 14);
        FxNet.flare(l, c, 2.4F, GREEN, 10);
        Fx.at(l, ParticleTypes.END_ROD, c, 18, 0.4, 0.1);
        Fx.sound(l, at, SoundEvents.ENDERMAN_TELEPORT, 0.5F, 0.7F);
        Fx.sound(l, at, SoundEvents.BELL_RESONATE, 0.45F, 1.5F);
    }

    private static List<Ghosts.Ghost> alive(UUID owner) {
        List<Ghosts.Ghost> l = CLONES.get(owner);
        if (l == null) return new ArrayList<>();
        l.removeIf(g -> !g.mob().isAlive());
        if (l.isEmpty()) CLONES.remove(owner);
        return new ArrayList<>(l);
    }

    // ------------------------------------------------------------------ echoes

    @SubscribeEvent
    public static void hit(LivingDamageEvent.Post e) {
        if (echoing || CLONES.isEmpty()) return;
        DamageSource src = e.getSource();
        if (!(src.getEntity() instanceof ServerPlayer p) || !CLONES.containsKey(p.getUUID())) return;
        boolean mine = src.is(DamageTypes.PLAYER_ATTACK) || src.getDirectEntity() instanceof Projectile;
        LivingEntity victim = e.getEntity();
        if (!mine || victim == p || !(victim.level() instanceof ServerLevel l) || !Rules.canHarm(p, victim)) return;
        float dmg = Math.max(1F, e.getNewDamage() * 0.6F);
        int i = 0;
        for (Ghosts.Ghost g : alive(p.getUUID())) {
            Mob m = g.mob();
            if (m.level() != l) continue;
            final int delay = 3 + 3 * i++;
            double a = l.random.nextDouble() * Math.PI * 2;
            Vec3 spot = Ground.below(l, victim.position().add(Math.cos(a) * 1.6, 1.2, Math.sin(a) * 1.6), 4);
            if (!Ground.fits(l, m, spot)) spot = victim.position();
            final Vec3 dest = spot;
            Vec3 old = m.position();
            FxNet.flare(l, old.add(0, m.getBbHeight() / 2, 0), 1.6F, GREEN, 8);
            FxNet.beam(l, old.add(0, m.getBbHeight() / 2, 0), dest.add(0, m.getBbHeight() / 2, 0), GREEN, 0.12F, 6);
            Tasks.add(new Tasks.Task() {
                @Override public boolean tick(int age) {
                    if (!m.isAlive() || !victim.isAlive() || victim.level() != l) return false;
                    if (age == 0) {
                        m.moveTo(dest.x, dest.y, dest.z, m.getYRot(), m.getXRot());
                        m.getNavigation().stop();
                        m.fallDistance = 0;
                        m.getLookControl().setLookAt(victim, 60, 60);
                        FxNet.flare(l, dest.add(0, m.getBbHeight() / 2, 0), 1.6F, GREEN, 8);
                        Fx.sound(l, dest, SoundEvents.ENDERMAN_TELEPORT, 0.3F, 1.5F);
                    }
                    if (age < delay) return true;
                    echoing = true;
                    try {
                        m.swing(InteractionHand.MAIN_HAND);
                        victim.setLastHurtByPlayer(p);
                        victim.invulnerableTime = 0;
                        if (victim.hurt(m.damageSources().mobAttack(m), dmg))
                            victim.knockback(0.4, m.getX() - victim.getX(), m.getZ() - victim.getZ());
                    } finally { echoing = false; }
                    Vec3 hit = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
                    Fx.at(l, Fx.dust(GREEN, 1.3F), hit, 8, 0.25, 0);
                    Fx.at(l, ParticleTypes.CRIT, hit, 5, 0.2, 0.25);
                    Fx.at(l, ParticleTypes.REVERSE_PORTAL, hit, 5, 0.2, 0.1);
                    FxNet.beam(l, m.position().add(0, m.getBbHeight() * 0.7, 0), hit, GREEN, 0.1F, 5);
                    Fx.sound(l, hit, SoundEvents.AMETHYST_BLOCK_CHIME, 0.4F, 1.6F);
                    return false;
                }
            });
        }
    }
}
