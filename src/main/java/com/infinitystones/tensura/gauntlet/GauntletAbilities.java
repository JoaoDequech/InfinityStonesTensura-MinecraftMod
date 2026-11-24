package com.infinitystones.tensura.gauntlet;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.RealityPets;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.StolenMoment;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** The one-shot gauntlet powers (see notes/new-abilities.md, "Manopla"). All need a complete gauntlet worn. */
public final class GauntletAbilities {

    // ------------------------------------------------------------ Execution (original "Kill Other Players")

    private static final java.util.Map<java.util.UUID, Long> EXEC_MENU = new java.util.HashMap<>();
    private static final int MENU_MOBS = 40;
    private static final double MENU_RANGE = 64;

    /** Left: execute whatever you aim at, player or any mob. Right: pick the victim from a list (players, then mobs nearby). */
    public static boolean execute(ServerPlayer p, boolean menu) {
        if (!GauntletPowers.ready(p, true)) return false;
        if (menu) return openExecutionMenu(p);
        LivingEntity t = Aim.living(p, 256, e -> Rules.canHarm(p, e));
        if (t == null) return Fx.fail(p, "infinity.extra.no_target");
        strike(p, t);
        return true;
    }

    private static boolean openExecutionMenu(ServerPlayer p) {
        List<String> names = new ArrayList<>(), ids = new ArrayList<>();
        for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
            if (o == p || !Rules.canHarm(p, o)) continue;
            names.add(o.getGameProfile().getName());
            ids.add(o.getUUID().toString());
        }
        List<LivingEntity> mobs = new ArrayList<>(p.serverLevel().getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(MENU_RANGE),
                e -> !(e instanceof Player) && Rules.canHarm(p, e)));
        mobs.sort(java.util.Comparator.comparingDouble(p::distanceToSqr));
        for (LivingEntity m : mobs.subList(0, Math.min(MENU_MOBS, mobs.size()))) {
            names.add(m.getDisplayName().getString() + " (" + (int) Math.sqrt(p.distanceToSqr(m)) + " m)");
            ids.add(m.getUUID().toString());
        }
        if (names.isEmpty()) return Fx.fail(p, "infinity.extra.no_target");
        EXEC_MENU.put(p.getUUID(), p.serverLevel().getGameTime() + 20 * 60);
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new com.infinitystones.tensura.soul.SoulNet.Players(names, ids));
        return true;
    }

    /** A row of the shared list screen was picked. True when it answered an open execution menu (so possession must ignore it). */
    public static boolean pick(ServerPlayer p, String uuid) {
        Long until = EXEC_MENU.remove(p.getUUID());
        if (until == null) return false;
        if (p.serverLevel().getGameTime() > until || !GauntletPowers.ready(p, true)) return true;
        java.util.UUID id;
        try { id = java.util.UUID.fromString(uuid); } catch (IllegalArgumentException e) { return true; }
        Entity t = null;
        ServerPlayer online = p.server.getPlayerList().getPlayer(id);
        if (online != null) t = online;
        else for (ServerLevel l : p.server.getAllLevels()) { t = l.getEntity(id); if (t != null) break; }
        if (!(t instanceof LivingEntity target) || !Rules.canHarm(p, target)
                || (!(target instanceof ServerPlayer) && (target.level() != p.level() || target.distanceToSqr(p) > (MENU_RANGE + 16) * (MENU_RANGE + 16)))) {
            Fx.fail(p, "infinity.extra.no_target");
            return true;
        }
        strike(p, target);
        return true;
    }

    /** The kill itself: the robust path (generic kill, setHealth, die, discard fallback; no death message). */
    private static void strike(ServerPlayer p, LivingEntity v) {
        ServerLevel vl = (ServerLevel) v.level();
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(vl);
        if (bolt != null) { bolt.moveTo(v.position()); bolt.setVisualOnly(true); vl.addFreshEntity(bolt); }
        vl.sendParticles(Fx.dust(0xFFD24A, 1.6F), v.getX(), v.getY() + v.getBbHeight() * 0.5, v.getZ(), 40, 0.4, 0.8, 0.4, 0.05);
        com.infinitystones.tensura.skill.ability.extra.Executions.kill(v);
        GauntletPowers.global(p, SoundEvents.CONDUIT_ACTIVATE, 0.6F, 1F);
        GauntletPowers.global(p, SoundEvents.ENDER_DRAGON_DEATH, 0.4F, 1F);
        GauntletPowers.burn(p);
    }

    // ------------------------------------------------------------ Eternity's Convergence (original)

    public static boolean convergence(ServerPlayer p) {
        if (!GauntletPowers.ready(p, true)) return false;
        int t = 25 * 20;
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, t, 9, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, t, 4, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, t, 4, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, t, 7, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, t, 4, false, false, true));
        p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, t, 0, false, false, true));
        ServerLevel l = p.serverLevel();
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(48), e -> !(e instanceof Player)))
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0, false, false));
        Vec3 c = p.position().add(0, 1, 0);
        Fx.far(l, ParticleTypes.FLASH, c, 1, 0, 0, 96);
        Fx.far(l, ParticleTypes.END_ROD, c, 400, 4, 0.35, 96);
        Fx.far(l, ParticleTypes.DRAGON_BREATH, c, 260, 4, 0.08, 96);
        for (Stone s : Stone.values()) Fx.ring(l, p.position().add(0, 0.3 + s.ordinal() * 0.35, 0), 2.5, 36, Fx.dust(s, 1.4F));
        Fx.sound(l, c, SoundEvents.ENDER_DRAGON_GROWL, 0.7F, 0.65F);
        Fx.sound(l, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6F, 1.4F);
        Fx.bar(p, "infinity.gauntlet.convergence.on");
        return true;
    }

    // ------------------------------------------------------------ Universal Dominion (new)

    /** Left: every creature within 48 blocks becomes your pet (no pet limit). Right: all your loaded pets come to you. */
    public static boolean dominion(ServerPlayer p, boolean recall) {
        if (!GauntletPowers.ready(p, false)) return false;
        ServerLevel l = p.serverLevel();
        int n = 0;
        if (recall) {
            for (Mob m : RealityPets.petsOf(p.getUUID())) {
                Vec3 at = p.position().add(l.random.nextDouble() * 4 - 2, 0, l.random.nextDouble() * 4 - 2);
                if (com.infinitystones.tensura.skill.ability.Relocation.move(m, l, at, 1.2F) != null) n++;
            }
            if (n == 0) return Fx.fail(p, "infinity.extra.pet.none");
            Fx.bar(p, "infinity.extra.pet.come", n);
            return true;
        }
        for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(48), m -> m.isAlive() && !Rules.isBoss(m)
                && RealityPets.ownerOf(m) == null && Rules.foreignOwner(p, m) == null && m.distanceToSqr(p) <= 48 * 48)) {
            RealityPets.makePet(p, m);
            n++;
        }
        if (n == 0) return Fx.fail(p, "infinity.extra.no_target");
        Fx.ring(l, p.position().add(0, 0.2, 0), 6, 60, Fx.dust(Stone.MIND, 1.4F));
        Fx.sound(l, p.position(), SoundEvents.EVOKER_PREPARE_WOLOLO, 0.7F, 0.6F);
        Fx.bar(p, "infinity.gauntlet.dominion.done", n);
        return true;
    }

    // ------------------------------------------------------------ Omniscient Eye (new)

    /** Where everyone is: dimension, coordinates and health of every player; those in your world glow for 30 s. */
    public static boolean eye(ServerPlayer p) {
        if (!GauntletPowers.ready(p, false)) return false;
        p.sendSystemMessage(Component.translatable("infinity.gauntlet.eye.header").withStyle(ChatFormatting.GOLD));
        int n = 0;
        for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
            if (o == p) continue;
            n++;
            p.sendSystemMessage(Component.translatable("infinity.gauntlet.eye.line", o.getDisplayName(),
                    o.level().dimension().location().toString(), Mth.floor(o.getX()) + " " + Mth.floor(o.getY()) + " " + Mth.floor(o.getZ()),
                    String.format("%.0f/%.0f", o.getHealth(), o.getMaxHealth())).withStyle(ChatFormatting.YELLOW));
            if (o.level() == p.level()) o.addEffect(new MobEffectInstance(MobEffects.GLOWING, 600, 0, false, false), p);
        }
        if (n == 0) p.sendSystemMessage(Component.translatable("infinity.extra.mind_read.nobody").withStyle(ChatFormatting.GRAY));
        Fx.at(p.serverLevel(), Fx.dust(Stone.MIND, 1.2F), p.getEyePosition(), 30, 0.5, 0.05);
        Fx.sound(p.serverLevel(), p.position(), SoundEvents.ENDER_EYE_DEATH, 0.6F, 0.6F);
        return true;
    }

    // ------------------------------------------------------------ Absolute Stop (new)

    /** Everything except you and your allies stops for 10 s within 96 blocks. */
    public static boolean absoluteStop(ServerPlayer p) {
        if (!GauntletPowers.ready(p, false)) return false;
        int n = StolenMoment.freeze(p, 96, 200);
        GauntletPowers.global(p, SoundEvents.BELL_BLOCK, 0.6F, 0.4F);
        Fx.far(p.serverLevel(), Fx.dust(Stone.TIME, 2.0F), p.position().add(0, 1, 0), 120, 6, 0.1, 128);
        Fx.bar(p, "infinity.gauntlet.stop.done", n);
        return true;
    }

    // ------------------------------------------------------------ Transmutation of Beings (new)

    private static final EntityType<?>[] HARMLESS = { EntityType.CHICKEN, EntityType.RABBIT, EntityType.SHEEP, EntityType.PIG, EntityType.COD, EntityType.BAT, EntityType.FROG };

    /** Left: the aimed creature becomes a harmless animal. Right: every hostile within 32 blocks does. */
    public static boolean transmute(ServerPlayer p, boolean area) {
        if (!GauntletPowers.ready(p, false)) return false;
        ServerLevel l = p.serverLevel();
        List<Mob> targets = new ArrayList<>();
        if (area) targets.addAll(l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32), m -> m instanceof Enemy && ok(p, m)));
        else {
            LivingEntity t = Aim.living(p, 64, e -> e instanceof Mob m && ok(p, m));
            if (t instanceof Mob m) targets.add(m);
        }
        if (targets.isEmpty()) return Fx.fail(p, "infinity.extra.no_target");
        for (Mob m : targets) {
            EntityType<?> to = HARMLESS[l.random.nextInt(HARMLESS.length)];
            if (m.isInWater() && to != EntityType.COD) to = EntityType.COD;
            Entity n = to.create(l);
            if (n == null) continue;
            n.moveTo(m.getX(), m.getY(), m.getZ(), m.getYRot(), 0);
            if (m.hasCustomName()) n.setCustomName(m.getCustomName());
            Vec3 c = m.position().add(0, m.getBbHeight() / 2, 0);
            l.sendParticles(Fx.dust(Stone.REALITY, 1.3F), c.x, c.y, c.z, 30, 0.4, 0.5, 0.4, 0.05);
            l.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0.02);
            m.discard();
            l.addFreshEntity(n);
        }
        Fx.sound(l, p.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.7F, 0.7F);
        Fx.bar(p, "infinity.gauntlet.transmute.done", targets.size());
        return true;
    }

    private static boolean ok(ServerPlayer p, Mob m) {
        return m.isAlive() && !Rules.isBoss(m) && Rules.canHarm(p, m) && RealityPets.ownerOf(m) == null && Rules.foreignOwner(p, m) == null;
    }

    private GauntletAbilities() {}
}
