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
 * Who the new abilities may affect (user decision 18/11): offensive powers hit players ALWAYS (no server-PvP check),
 * but never allies — yourself, your team, your pets and tamed animals — nor creative/spectator players.
 * Bosses are immune to control (pet, metamorphosis, suggestion, containment).
 */
public final class Rules {
    public static boolean isBoss(Entity e) {
        if (e instanceof EnderDragonPart part) e = part.parentMob;
        return e instanceof EnderDragon || e instanceof WitherBoss || e.getType().is(Tags.EntityTypes.BOSSES);
    }

    public static boolean isAlly(ServerPlayer caster, Entity e) {
        if (e == caster) return true;
        if (caster.isAlliedTo(e)) return true;
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
        if (e == null || e == caster || !e.isAlive() || e.isSpectator() || isAlly(caster, e)) return false;
        if (e instanceof ArmorStand) return false;
        if (e instanceof Player other) return !other.isCreative();
        return true;
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

    /** Magic damage credited to the caster (kill credit), never a melee hit. */
    public static DamageSource magic(ServerPlayer caster) { return caster.damageSources().indirectMagic(caster, caster); }

    public static void hurt(ServerPlayer caster, LivingEntity target, float amount) {
        target.invulnerableTime = 0;
        target.hurt(magic(caster), amount);
    }

    public static boolean griefing(ServerLevel l) { return l.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING); }

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
