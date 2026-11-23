package com.infinitystones.tensura.skill.ability.extra.reality;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Displays;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.MenuNet;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Local Decree. Left (aim): decree the chosen law in a circle at the aimed point (45 s, radius Alt+scroll 6–16).
 * Right: law menu — Peace (nobody deals damage), Lightness (almost no gravity), Weight (crushing gravity: no jump,
 * elytra fail, projectiles drop), Silence (no sound from inside is heard), Inversion (damage heals, healing hurts).
 * Applies to everybody inside, the caster included. One decree per player.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class LocalDecree implements Ability {
    public static final LocalDecree INSTANCE = new LocalDecree();
    static final String NODE = "local_decree", MODE = "reality.local_decree";
    private static final String K = "infinity.extra.local_decree.";
    static final int PEACE = 0, LIGHTNESS = 1, WEIGHT = 2, SILENCE = 3, INVERSION = 4;
    private static final String[] LAWS = {"peace", "lightness", "weight", "silence", "inversion"};
    private static final String[] ICONS = {"minecraft:white_banner", "minecraft:feather", "minecraft:anvil", "minecraft:white_wool", "minecraft:fermented_spider_eye"};
    private static final ResourceLocation GRAVITY_ID = InfinityStones.id("decree_gravity"), JUMP_ID = InfinityStones.id("decree_jump");

    static final class Zone {
        final UUID owner;
        final ServerLevel level;
        final Vec3 c;
        final double r;
        final int law, life;
        final int runeA = FxNet.newId(), runeB = FxNet.newId(), dome = FxNet.newId(), pillar = FxNet.newId();
        final Set<LivingEntity> affected = Collections.newSetFromMap(new WeakHashMap<>());
        @Nullable Display text;
        @Nullable Tasks.Handle handle;
        Zone(UUID owner, ServerLevel level, Vec3 c, double r, int law, int life) { this.owner = owner; this.level = level; this.c = c; this.r = r; this.law = law; this.life = life; }

        boolean contains(Level l, Vec3 v) {
            if (l != level) return false;
            double dx = v.x - c.x, dz = v.z - c.z;
            return dx * dx + dz * dz <= r * r && v.y >= c.y - 3 && v.y <= c.y + r + 4;
        }

        AABB box() { return new AABB(c.x - r, c.y - 3, c.z - r, c.x + r, c.y + r + 4, c.z + r); }
    }

    private static final List<Zone> ZONES = new CopyOnWriteArrayList<>();
    private static final Map<UUID, Zone> BY_OWNER = new ConcurrentHashMap<>();
    private static boolean inverting;

    @Override public Stone stone() { return Stone.REALITY; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        if (alt) return MenuNet.open(p, Stone.REALITY, NODE, this);
        float mult = Rules.mult(p, "reality");
        double r = Math.min(24, ExtraSettings.get(p, MODE, 10, 6, 16, 1) * mult);
        int law = Math.floorMod(ExtraSettings.choice(p, MODE, PEACE), LAWS.length);
        Vec3 c = Aim.ground(p, 48);
        Zone old = BY_OWNER.remove(p.getUUID());
        if (old != null && old.handle != null) old.handle.cancel();
        Zone z = new Zone(p.getUUID(), p.serverLevel(), c, r, law, (int) (45 * 20 * mult));
        BY_OWNER.put(p.getUUID(), z);
        ZONES.add(z);
        openFx(p, z);
        z.handle = Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) { return zoneTick(z, age); }
            @Override public void end() { close(z); }
        });
        Fx.bar(p, Stone.REALITY, K + "decreed", Component.translatable(K + "law." + LAWS[law]), (int) r);
        return true;
    }

    @Override
    public List<MenuNet.Option> menu(ServerPlayer p) {
        int cur = Math.floorMod(ExtraSettings.choice(p, MODE, PEACE), LAWS.length);
        MenuNet.Option[] o = new MenuNet.Option[LAWS.length];
        for (int i = 0; i < LAWS.length; i++) o[i] = MenuNet.opt(K + "law." + LAWS[i], ICONS[i], i == cur);
        return List.of(o);
    }

    @Override
    public void pick(ServerPlayer p, int index) {
        if (index < 0 || index >= LAWS.length) return;
        ExtraSettings.setChoice(p, MODE, index);
        Fx.bar(p, Stone.REALITY, K + "chosen", Component.translatable(K + "law." + LAWS[index]));
        Fx.sound(p.serverLevel(), p.position(), SoundEvents.BOOK_PAGE_TURN, 0.7F, 0.8F);
    }

    // ------------------------------------------------------------------ zone life

    private static void openFx(ServerPlayer p, Zone z) {
        ServerLevel l = z.level;
        int c = Stone.REALITY.color();
        FxNet.beam(l, p.getEyePosition().add(0, -0.3, 0), z.c.add(0, 0.2, 0), c, 0.25F, 12);
        FxNet.shockwave(l, z.c.add(0, 0.1, 0), (float) z.r * 1.3F, c, 26);
        FxNet.shockwave(l, z.c.add(0, 0.3, 0), (float) z.r * 0.7F, 0xFFFFFF, 18);
        FxNet.flare(l, z.c.add(0, 1.5, 0), 4F, c, 16);
        FxNet.flashAround(l, z.c, z.r + 4, c, 0.35F, 10);
        FxNet.shake(l, z.c, z.r + 16, 0.5F, 12);
        Fx.burstRing(l, z.c.add(0, 0.3, 0), 64, Fx.dust(Stone.REALITY, 1.5F), 0.6);
        Fx.sound(l, z.c, SoundEvents.BELL_BLOCK, 0.7F, 0.5F);
        Fx.sound(l, z.c, SoundEvents.EVOKER_PREPARE_SUMMON, 0.6F, 0.6F);
        Fx.sound(l, z.c, SoundEvents.ANVIL_LAND, 0.4F, 0.5F);
        refreshShapes(z);
        z.text = Displays.text(l, Component.translatable(K + "law." + LAWS[z.law]).withColor(c & 0xFFFFFF)
                        .append(Component.literal("\n")).append(Component.translatable(K + "law." + LAWS[z.law] + ".desc").withColor(0xFFC8CE)),
                z.c.add(0, Math.min(6, 2.5 + z.r * 0.2), 0), 0x60200008, 1.6F);
    }

    private static void refreshShapes(Zone z) {
        ServerLevel l = z.level;
        int c = Stone.REALITY.color();
        FxNet.runes(l, z.runeA, z.c.add(0, 0.06, 0), (float) z.r, c, 60, 0.35F);
        FxNet.runes(l, z.runeB, z.c.add(0, 0.09, 0), (float) z.r * 0.55F, 0xFF8FA0, 60, -0.8F);
        FxNet.dome(l, z.dome, z.c, (float) z.r, c, 60);
        FxNet.pillar(l, z.pillar, z.c, 0.4F, (float) Math.min(10, 3 + z.r * 0.4), c, 60);
    }

    private static boolean zoneTick(Zone z, int age) {
        if (age >= z.life || !BY_OWNER.containsValue(z)) return false;
        ServerLevel l = z.level;
        if (age % 40 == 0 && age > 0) refreshShapes(z);
        if (age % 5 == 0) lawParticles(l, z, age);
        if (age % 2 == 0) applyPhysics(l, z);
        int left = z.life - age;
        if (left == 100 || left == 60 || left == 20) Fx.sound(l, z.c, SoundEvents.NOTE_BLOCK_BELL.value(), 0.5F, 0.6F);
        return true;
    }

    private static void lawParticles(ServerLevel l, Zone z, int age) {
        var rnd = l.random;
        ParticleOptions accent = switch (z.law) {
            case PEACE -> ParticleTypes.END_ROD;
            case LIGHTNESS -> ParticleTypes.CLOUD;
            case WEIGHT -> ParticleTypes.FALLING_OBSIDIAN_TEAR;
            case SILENCE -> ParticleTypes.SMOKE;
            default -> rnd.nextBoolean() ? ParticleTypes.HEART : ParticleTypes.DAMAGE_INDICATOR;
        };
        int n = (int) Math.min(40, z.r * 2.5);
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * z.r;
            Vec3 v = z.c.add(Math.cos(a) * d, 0.2 + rnd.nextDouble() * (z.law == WEIGHT ? 4 : 1.5), Math.sin(a) * d);
            double vy = z.law == LIGHTNESS ? 0.05 : z.law == WEIGHT ? -0.3 : 0.01;
            l.sendParticles(accent, v.x, v.y, v.z, 0, 0, vy, 0, 1);
        }
        // the border: a rotating dotted wall
        double spin = age * 0.05;
        for (int i = 0; i < 24; i++) {
            double a = spin + Math.PI * 2 * i / 24;
            Vec3 v = z.c.add(Math.cos(a) * z.r, 0.2 + (i % 3) * 0.7, Math.sin(a) * z.r);
            Fx.at(l, Fx.dust(Stone.REALITY, 1.1F), v, 1, 0, 0);
        }
    }

    private static void applyPhysics(ServerLevel l, Zone z) {
        if (z.law != LIGHTNESS && z.law != WEIGHT) return;
        Set<LivingEntity> inside = Collections.newSetFromMap(new WeakHashMap<>());
        for (Entity e : l.getEntities((Entity) null, z.box(), e -> z.contains(l, e.position()) && !e.isSpectator())) {
            if (e instanceof LivingEntity le) {
                inside.add(le);
                if (z.law == LIGHTNESS) {
                    modifier(le, Attributes.GRAVITY, GRAVITY_ID, -0.88);
                    le.fallDistance = 0;
                } else {
                    modifier(le, Attributes.GRAVITY, GRAVITY_ID, 2.0);
                    modifier(le, Attributes.JUMP_STRENGTH, JUMP_ID, -0.9);
                    if (le instanceof ServerPlayer sp && sp.isFallFlying()) sp.stopFallFlying();
                    if (le.getDeltaMovement().y > 0.1 && !le.onGround()) le.setDeltaMovement(le.getDeltaMovement().multiply(1, 0.5, 1));
                }
                z.affected.add(le);
            } else if (e instanceof Projectile || e instanceof ItemEntity) {
                Vec3 v = e.getDeltaMovement();
                e.setDeltaMovement(z.law == LIGHTNESS ? new Vec3(v.x * 0.98, v.y * 0.9 + 0.08, v.z * 0.98) : new Vec3(v.x * 0.7, v.y - 0.35, v.z * 0.7));
                e.hurtMarked = true;
            }
        }
        for (LivingEntity le : z.affected.toArray(new LivingEntity[0])) {
            if (!inside.contains(le)) { clear(le); z.affected.remove(le); }
        }
    }

    private static void modifier(LivingEntity e, Holder<Attribute> attr, ResourceLocation id, double amount) {
        AttributeInstance a = e.getAttribute(attr);
        if (a == null || a.hasModifier(id)) return;
        a.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    private static void clear(LivingEntity e) {
        AttributeInstance g = e.getAttribute(Attributes.GRAVITY);
        if (g != null) g.removeModifier(GRAVITY_ID);
        AttributeInstance j = e.getAttribute(Attributes.JUMP_STRENGTH);
        if (j != null) j.removeModifier(JUMP_ID);
    }

    private static void close(Zone z) {
        ZONES.remove(z);
        BY_OWNER.remove(z.owner, z);
        for (LivingEntity le : z.affected.toArray(new LivingEntity[0])) clear(le);
        z.affected.clear();
        ServerLevel l = z.level;
        FxNet.kill(l, z.c, z.runeA); FxNet.kill(l, z.c, z.runeB); FxNet.kill(l, z.c, z.dome); FxNet.kill(l, z.c, z.pillar);
        Displays.remove(z.text);
        FxNet.shockwave(l, z.c.add(0, 0.1, 0), (float) z.r, Stone.REALITY.color(), 14);
        Fx.shell(l, z.c, Math.min(4, z.r * 0.3), 30, Fx.dust(Stone.REALITY, 1F));
        Fx.sound(l, z.c, SoundEvents.BEACON_DEACTIVATE, 0.5F, 0.7F);
    }

    // ------------------------------------------------------------------ the laws

    private static @Nullable Zone at(Level l, Vec3 v, int law) {
        if (ZONES.isEmpty() || l.isClientSide()) return null;
        for (Zone z : ZONES) if (z.law == law && z.contains(l, v)) return z;
        return null;
    }

    @SubscribeEvent
    public static void damage(LivingIncomingDamageEvent e) {
        if (inverting || ZONES.isEmpty()) return;
        LivingEntity victim = e.getEntity();
        Level l = victim.level();
        if (e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        Entity src = e.getSource().getEntity();
        if (src != null && (at(l, victim.position(), PEACE) != null || at(l, src.position(), PEACE) != null)) {
            e.setCanceled(true);
            if (victim.tickCount % 5 == 0 && l instanceof ServerLevel sl)
                Fx.at(sl, ParticleTypes.END_ROD, victim.position().add(0, victim.getBbHeight() / 2, 0), 4, 0.3, 0.02);
            return;
        }
        if (at(l, victim.position(), INVERSION) != null) {
            float amount = e.getAmount();
            e.setCanceled(true);
            inverting = true;
            try { victim.heal(amount); } finally { inverting = false; }
            if (l instanceof ServerLevel sl) Fx.at(sl, ParticleTypes.HEART, victim.position().add(0, victim.getBbHeight() + 0.2, 0), 2, 0.3, 0);
        }
    }

    @SubscribeEvent
    public static void heal(LivingHealEvent e) {
        if (inverting || ZONES.isEmpty()) return;
        LivingEntity le = e.getEntity();
        Zone z = at(le.level(), le.position(), INVERSION);
        if (z == null || e.getAmount() <= 0) return;
        float amount = e.getAmount();
        e.setCanceled(true);
        inverting = true;
        try {
            le.invulnerableTime = 0;
            le.hurt(le.damageSources().magic(), amount);
        } finally { inverting = false; }
        if (le.level() instanceof ServerLevel sl) Fx.at(sl, ParticleTypes.DAMAGE_INDICATOR, le.position().add(0, le.getBbHeight() / 2, 0), 2, 0.3, 0.05);
    }

    @SubscribeEvent
    public static void soundAt(PlayLevelSoundEvent.AtPosition e) {
        if (at(e.getLevel(), e.getPosition(), SILENCE) != null) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void soundEntity(PlayLevelSoundEvent.AtEntity e) {
        if (at(e.getLevel(), e.getEntity().position(), SILENCE) != null) e.setCanceled(true);
    }
}
