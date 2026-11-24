package com.infinitystones.tensura.skill.ability.extra;

import com.infinitystones.tensura.skill.ability.extra.reality.RealityPet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Who the new abilities may affect (user decision 18/11, revised 24/11): offensive powers hit players and mobs ALWAYS: no PvP, team,
 * party or claim check. Only the caster himself, his own pets and creative/spectator players are skipped.
 * Bosses are immune to control (pet, metamorphosis, suggestion, containment).
 */
public final class Rules {
    public static boolean isBoss(Entity e) {
        if (e instanceof EnderDragonPart part) e = part.parentMob;
        return e instanceof EnderDragon || e instanceof WitherBoss || e.getType().is(Tags.EntityTypes.BOSSES);
    }

    /** The caster himself or something that belongs to him (tamed pets, Reality pets, ghosts): the only things harmful powers skip. */
    public static boolean isSelfOrPet(ServerPlayer caster, Entity e) {
        if (e == caster) return true;
        if (caster.getUUID().equals(RealityPet.ownerOf(e))) return true;
        if (caster.getUUID().equals(com.infinitystones.tensura.skill.ability.extra.soul.Ghosts.ownerOf(e))) return true;
        return e instanceof OwnableEntity o && caster.getUUID().equals(o.getOwnerUUID());
    }

    /** Friendly for BENEFICIAL effects (buffs, links): the caster, his team, his OPAC party and his pets. Harmful powers use {@link #isSelfOrPet}. */
    public static boolean isAlly(ServerPlayer caster, Entity e) {
        if (e == caster) return true;
        if (caster.isAlliedTo(e)) return true;
        if (e instanceof ServerPlayer other && com.infinitystones.tensura.compat.ClaimsCompat.sameParty(caster, other)) return true;
        if (caster.getUUID().equals(RealityPet.ownerOf(e))) return true;
        if (caster.getUUID().equals(com.infinitystones.tensura.skill.ability.extra.soul.Ghosts.ownerOf(e))) return true;
        return e instanceof OwnableEntity o && caster.getUUID().equals(o.getOwnerUUID());
    }

    /** Owner of a tamed animal / Reality pet when it is somebody other than the caster. */
    public static @Nullable UUID foreignOwner(ServerPlayer caster, Entity e) {
        UUID owner = e instanceof OwnableEntity o ? o.getOwnerUUID() : null;
        if (owner == null) owner = RealityPet.ownerOf(e);
        return owner == null || owner.equals(caster.getUUID()) ? null : owner;
    }

    /** May the caster harm this entity? */
    public static boolean canHarm(ServerPlayer caster, @Nullable Entity e) {
        if (e == null || e == caster || !e.isAlive() || e.isSpectator() || isSelfOrPet(caster, e)) return false;
        if (e instanceof ArmorStand) return false;
        return !(e instanceof Player other && other.isCreative());
    }

    /** May the caster take control of this entity (pet, metamorphosis, containment...)? */
    public static boolean canControl(ServerPlayer caster, @Nullable Entity e) {
        return canHarm(caster, e) && !isBoss(e);
    }

    /** Hostile = monster, a player we may hurt, or a mob currently attacking the caster/allies. */
    public static boolean isEnemy(ServerPlayer caster, Entity e) {
        if (!canHarm(caster, e)) return false;
        if (e instanceof Player || e instanceof Enemy) return true;
        return e instanceof Mob m && m.getTarget() != null && isAlly(caster, m.getTarget());
    }

    /**
     * Damage model. Players on the server run from 100 to 1000+ HP (Tensura races), so powers deal
     * max(flat, pct of the target's max HP): humble public powers ~3-8%, strong public ones ~10-25%, hidden ones 40%+.
     * Public powers use Tensura's magic damage type (Magic / Resistance skills apply); hidden ones ignore every
     * resistance and nullification (resistance bypass level 2). Bosses take at most BOSS_CAP per hit.
     */
    public static final float PUBLIC_BOSS_CAP = 60F, HIDDEN_BOSS_CAP = 160F;

    /** Tensura magic damage credited to the caster (kill credit), never a melee hit. */
    public static DamageSource magic(ServerPlayer caster) { return source(caster, null, false); }

    /** Where PvP is off or the target is a protected teammate the source loses its owner (the hit still lands, project decision). */
    public static DamageSource source(ServerPlayer caster, @Nullable Entity target, boolean hidden) {
        var type = caster.serverLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                .getHolderOrThrow(io.github.manasmods.tensura.damage.TensuraDamageTypes.MAGIC_GENERIC);
        boolean noOwner = target instanceof Player other && (!caster.server.isPvpAllowed() || !caster.canHarmPlayer(other) || com.infinitystones.tensura.compat.ClaimsCompat.opacLoaded());
        // without a causing entity ServerPlayer.hurt skips its PvP check; the caster stays the direct entity so claims can still tell who did it
        DamageSource s = noOwner ? new DamageSource(type, caster, null) : new DamageSource(type, caster, caster);
        if (hidden) {
            var t = (io.github.manasmods.tensura.damage.TensuraDamageSource) s;
            t.tensura$setResistanceBypassLevel(2F); // Nullification ignored entirely
            t.tensura$setBarrierBypassLevel(1F);    // Tensura: Barriers honours this
        }
        return s;
    }

    /** The damage a hit of (flat, pct of max HP) does to this target. */
    public static float amount(LivingEntity target, float flat, float pct, boolean hidden) {
        return amount(target, flat, pct, hidden, hidden ? HIDDEN_BOSS_CAP : PUBLIC_BOSS_CAP);
    }

    /** The flat part is only a floor for small mobs (capped at 40, hidden 100) so it never one-shots a 100 HP player. */
    public static float amount(LivingEntity target, float flat, float pct, boolean hidden, float bossCap) {
        float floor = Math.min(flat, hidden ? 100F : 40F);
        float a = Math.max(floor, pct * target.getMaxHealth());
        if (isBoss(target)) a = Math.min(a, Math.max(floor, bossCap));
        return a;
    }

    /** Full control (continuous beams use a lower boss cap per hit). */
    public static boolean hit(ServerPlayer caster, LivingEntity target, float flat, float pct, boolean hidden, float bossCap) {
        target.invulnerableTime = 0;
        DamageSource src = source(caster, target, hidden);
        float dmg = amount(target, flat, pct, hidden, bossCap);
        if (!hidden) return target.hurt(src, dmg);
        boolean[] landed = new boolean[1]; // hidden powers leave no death message behind
        Executions.silently(caster.server, () -> landed[0] = target.hurt(src, dmg));
        return landed[0];
    }

    /** Legacy flat numbers (tuned for 20 HP players) read as a percentage: 4 → 3%, 10 → 6%, 24 → 14%, 40+ → 25%. */
    public static float pctFor(float flat) { return Math.min(0.25F, Math.max(0.03F, flat * 0.006F)); }

    /** Public power hit: legacy flat amount, scaled to the target's health. */
    public static boolean hurt(ServerPlayer caster, LivingEntity target, float amount) {
        return hurtPct(caster, target, amount, pctFor(amount));
    }

    /** Public power hit: max(flat, pct of max HP), Tensura magic damage. */
    public static boolean hurtPct(ServerPlayer caster, LivingEntity target, float flat, float pct) {
        target.invulnerableTime = 0;
        return target.hurt(source(caster, target, false), amount(target, flat, pct, false));
    }

    /** Hidden power hit: ignores resistances and nullifications. */
    public static boolean hurtHidden(ServerPlayer caster, LivingEntity target, float flat, float pct) {
        return hit(caster, target, flat, pct, true, HIDDEN_BOSS_CAP);
    }

    /** Armour-ignoring source (generic kill type) that Tensura's resistances, nullification and barriers do not reduce. */
    public static DamageSource trueSource(ServerPlayer caster, @Nullable Entity target) {
        var type = caster.serverLevel().registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.DAMAGE_TYPE)
                .getHolderOrThrow(net.minecraft.world.damagesource.DamageTypes.GENERIC_KILL);
        boolean noOwner = target instanceof Player other && (!caster.server.isPvpAllowed() || !caster.canHarmPlayer(other) || com.infinitystones.tensura.compat.ClaimsCompat.opacLoaded());
        DamageSource s = noOwner ? new DamageSource(type, caster, null) : new DamageSource(type, caster, caster);
        var t = (io.github.manasmods.tensura.damage.TensuraDamageSource) s;
        t.tensura$setResistanceBypassLevel(2F);
        t.tensura$setBarrierBypassLevel(1F);
        return s;
    }

    /**
     * True damage: exactly {@code amount} off the target's health (absorption first), whatever resistance, armour or other mod
     * is in the way. If something still softened the hit the remainder is applied directly; a lethal remainder executes.
     */
    public static void trueHit(ServerPlayer caster, LivingEntity target, float amount) {
        if (!target.isAlive() || amount <= 0) return;
        float before = target.getHealth() + target.getAbsorptionAmount();
        target.invulnerableTime = 0;
        target.hurt(trueSource(caster, target), amount);
        if (!target.isAlive()) return;
        float now = target.getHealth() + target.getAbsorptionAmount();
        if (before - now >= amount - 0.01F) return;
        float left = before - amount;
        if (left <= 0.5F) { Executions.kill(target); return; }
        target.setAbsorptionAmount(0F);
        target.setHealth(Math.min(left, target.getMaxHealth()));
    }

    /** Hidden execution: anything mortal dies; bosses take a heavy capped hit instead. */
    public static void execute(ServerPlayer caster, LivingEntity target) {
        if (isBoss(target)) { Executions.silently(caster.server, () -> hurtHidden(caster, target, HIDDEN_BOSS_CAP, 0.2F)); return; }
        Executions.silently(caster.server, () -> {
            target.invulnerableTime = 0;
            if (!target.hurt(source(caster, target, true), Float.MAX_VALUE) && target.isAlive())
                target.hurt(caster.damageSources().genericKill(), Float.MAX_VALUE);
        });
    }

    /** Kept so older call sites compile: powers no longer depend on the mobGriefing gamerule (project decision). */
    @Deprecated
    public static boolean griefing(ServerLevel l) { return true; }

    /** Spawn protection, world border and claims only. No mobGriefing, no OP (having the mod is the permission). */
    public static boolean mayEdit(ServerPlayer caster, net.minecraft.core.BlockPos pos) {
        return Terrain.may(caster, caster.serverLevel(), pos);
    }

    /** Broken Mode multiplier for Reality abilities (1 otherwise). */
    public static float mult(ServerPlayer p, @Nullable String stoneKey) {
        if (stoneKey != null && !"reality".equals(stoneKey)) return 1F;
        return com.infinitystones.tensura.skill.ability.extra.reality.BrokenMode.isOn(p) ? 1.5F : 1F;
    }

    /** Player data that survives death (NeoForge copies only "PlayerPersisted" on respawn). */
    public static CompoundTag data(Player p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }

    /** Turns a wheel toggle off (same storage as NodeEffects); its stop() runs on the next player tick. */
    public static void forceToggleOff(net.minecraft.server.level.ServerPlayer p, String modeId) {
        CompoundTag all = p.getPersistentData().getCompound("infinity_toggles");
        if (!all.getBoolean(modeId)) return;
        all.putBoolean(modeId, false);
        p.getPersistentData().put("infinity_toggles", all);
    }

    public static boolean isLiving(Entity e) { return e instanceof LivingEntity l && l.isAlive() && !(e instanceof ArmorStand); }

    private Rules() {}
}
