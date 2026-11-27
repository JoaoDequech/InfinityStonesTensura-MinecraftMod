package com.infinitystones.tensura.skill.ability.extra.fear.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/**
 * Base of everything that haunts one victim: bound to a player, invulnerable, silent unless it chooses to make a sound, never
 * saved, gone the moment its victim is gone. Contains no client classes; the model state is plain data read by the client renderer.
 */
public abstract class FearEntity extends PathfinderMob {
    public static final int A_IDLE = 0, A_SCREAM = 1, A_ATTACK = 2, A_STARE = 3, A_VANISH = 4, A_APPEAR = 5, A_FROZEN = 6,
            A_EMERGE = 7, A_KILL = 8, A_TOY = 9;
    public static final String[] ACTION_CLIPS = {"idle", "scream", "attack", "stare", "vanish", "appear", "frozen", "emerge", "kill", "toy"};

    private static final EntityDataAccessor<Integer> ACTION = SynchedEntityData.defineId(FearEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> FAST = SynchedEntityData.defineId(FearEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> SHOWN = SynchedEntityData.defineId(FearEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CLIMBING = SynchedEntityData.defineId(FearEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> VARIANT = SynchedEntityData.defineId(FearEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> VICTIM = SynchedEntityData.defineId(FearEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    /** Client animation bookkeeping (plain fields, only the renderer touches them). */
    public static final class Anim {
        public String cur, prev;
        public float curStart, prevStart, blendStart, blendLen = 4F;
        public float fade, fadeO;
    }

    public final Anim anim = new Anim();
    protected UUID victimId;
    private ServerPlayer victimCache;

    protected FearEntity(EntityType<? extends FearEntity> type, Level level) {
        super(type, level);
        setInvulnerable(true);
        setPersistenceRequired();
        xpReward = 0;
    }

    public static AttributeSupplier.Builder attributes(double speed) {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40).add(Attributes.MOVEMENT_SPEED, speed).add(Attributes.FOLLOW_RANGE, 96)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.STEP_HEIGHT, 1.1).add(Attributes.ATTACK_DAMAGE, 1);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        super.defineSynchedData(b);
        b.define(ACTION, 0).define(FAST, false).define(SHOWN, true).define(CLIMBING, false).define(VARIANT, 0).define(VICTIM, Optional.empty());
    }

    public int action() { return entityData.get(ACTION); }
    public void act(int a) { if (entityData.get(ACTION) != a) entityData.set(ACTION, a); }
    public boolean fast() { return entityData.get(FAST); }
    public void setFast(boolean v) { if (entityData.get(FAST) != v) entityData.set(FAST, v); }
    public boolean shown() { return entityData.get(SHOWN); }
    public void setShown(boolean v) { if (entityData.get(SHOWN) != v) entityData.set(SHOWN, v); }
    public boolean climbing() { return entityData.get(CLIMBING); }
    public void setClimbing(boolean v) { if (entityData.get(CLIMBING) != v) entityData.set(CLIMBING, v); }
    public int variant() { return entityData.get(VARIANT); }
    public void setVariant(int v) { entityData.set(VARIANT, v); }
    public Optional<UUID> victimUuid() { return entityData.get(VICTIM); }

    public void bind(ServerPlayer victim) {
        victimId = victim.getUUID();
        victimCache = victim;
        entityData.set(VICTIM, Optional.of(victimId));
        FearMobs.track(victimId, this);
    }

    public UUID victimId() { return victimId; }

    /** The bound victim if it is still in this very level and alive; null otherwise (server side). */
    protected ServerPlayer victim() {
        if (victimId == null || level().getServer() == null) return null;
        if (victimCache == null || victimCache.isRemoved() || victimCache.hasDisconnected()) {
            victimCache = level().getServer().getPlayerList().getPlayer(victimId);
        }
        ServerPlayer v = victimCache;
        if (v == null || !v.isAlive() || v.level() != level()) return null;
        return v;
    }

    /** Client: the bound player, for skins. */
    public Player clientVictim() {
        Optional<UUID> id = victimUuid();
        return id.isPresent() ? level().getPlayerByUUID(id.get()) : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            anim.fadeO = anim.fade;
            float target = shown() ? 1F : 0F;
            float rate = shown() ? .16F : .1F;
            anim.fade += Mth.clamp(target - anim.fade, -rate, rate);
        } else {
            ServerPlayer v = victim();
            if (v == null) { discard(); return; }
            serverTick(v);
        }
    }

    protected abstract void serverTick(ServerPlayer victim);

    public float fadeAlpha(float partial) { return Mth.lerp(partial, anim.fadeO, anim.fade); }

    /** Is the victim looking toward this entity (view cone plus line of sight)? */
    protected boolean observedBy(ServerPlayer v) {
        Vec3 to = getEyePosition().subtract(v.getEyePosition());
        double len = to.length();
        if (len < .01) return true;
        Vec3 look = v.getViewVector(1F);
        if (look.dot(to.scale(1 / len)) < .62) return false;
        return v.hasLineOfSight(this);
    }

    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override protected void doPush(net.minecraft.world.entity.Entity e) {}
    @Override public void push(double x, double y, double z) {}
    @Override public boolean fireImmune() { return true; }
    @Override public boolean causeFallDamage(float f, float m, DamageSource s) { return false; }
    @Override protected boolean shouldDropLoot() { return false; }
    @Override public int getBaseExperienceReward() { return 0; }
    @Override public boolean canBeLeashed() { return false; }
    @Override public boolean shouldShowName() { return false; }
    @Override public boolean isOnFire() { return false; }
    @Override public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance e) { return false; }
    @Override public boolean isAffectedByPotions() { return false; }
    @Override protected void playStepSound(BlockPos p, net.minecraft.world.level.block.state.BlockState s) {}
    @Override public boolean onClimbable() { return climbing(); }
    @Override public AABB getBoundingBoxForCulling() { return super.getBoundingBoxForCulling().inflate(3.0); }

    @Override
    public void remove(RemovalReason reason) {
        FearMobs.untrack(victimId, this);
        super.remove(reason);
    }

    @Override public void addAdditionalSaveData(CompoundTag t) {}
    @Override public void readAdditionalSaveData(CompoundTag t) {}
}
