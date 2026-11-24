package com.infinitystones.tensura.soul;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Soul — Possession.
 * <ul>
 *   <li>Player mode: the caster's camera rides the chosen player's eyes. The caster's body becomes a ghost (never sent to
 *   other clients, never rendered, invulnerable, ignored by mobs) that follows the target so its chunks stay loaded.
 *   Nothing is shown or played to the target. "Take over" makes the server drop every gameplay packet of the target's
 *   own client (see mixin PuppetPacketMixin) and drive the target with the caster's input instead.</li>
 *   <li>Mob mode: the aimed mob's AI is suspended and it is driven by the caster's input with a free third-person
 *   camera; right/middle click fire the mob's native attacks (MobPowers).</li>
 * </ul>
 * Releasing (or any failure: target dies, logs out, changes dimension as a mob…) restores everything and puts the
 * caster's body back where it was. The origin is also stored on the player, so a crash/logout restores it on login.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class Possession {
    public static final int ACT_RELEASE = 0, ACT_CONTROL = 1, ACT_INVENTORY = 2, ACT_DROP = 3, ACT_ABILITY = 4,
            ACT_ABILITY2 = 5, ACT_ATTACK = 6, ACT_USE = 7;
    private static final String ORIGIN = "infinity_possess_origin";
    private static final String MOB_TAG = "infinity_possessed_noai";

    static final class Session {
        final UUID owner;
        final UUID target;
        int mode;
        int ticks;
        boolean wasInvulnerable;
        boolean mobNoAi, mobNoGravity;
        // latest input
        float forward, strafe, yaw, pitch;
        boolean jump, sneak, sprint, attack, use, prevUse;
        int slot = -1;
        Vec3 vel = Vec3.ZERO;
        @Nullable BlockPos mining;
        float mineProgress;
        Session(UUID owner, UUID target, int mode) { this.owner = owner; this.target = target; this.mode = mode; }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** Possessors' bodies (read from the network thread by the mixins). */
    private static final Set<UUID> GHOSTS = ConcurrentHashMap.newKeySet();
    /** Players whose own client is currently ignored (taken over). */
    private static final Set<UUID> PUPPETS = ConcurrentHashMap.newKeySet();
    /** Who may use the possession menu right now (opened from the wheel), until game time. */
    private static final Map<UUID, Long> MENU_TOKEN = new HashMap<>();

    public static boolean isGhost(Entity e) { return e instanceof Player && GHOSTS.contains(e.getUUID()); }
    public static boolean isPuppet(Player p) { return PUPPETS.contains(p.getUUID()); }
    public static boolean isPossessing(Player p) { return SESSIONS.containsKey(p.getUUID()); }
    public static boolean isPossessed(Entity e) {
        for (Session s : SESSIONS.values()) if (s.target.equals(e.getUUID())) return true;
        return false;
    }

    // ------------------------------------------------------------------ wheel entry points

    /** Left: possess the aimed mob. */
    public static boolean possessAimedMob(ServerPlayer p) {
        if (isPossessing(p)) return Fx.fail(p, "infinity.soul.possess.already");
        LivingEntity t = Aim.living(p, 48, e -> e instanceof Mob && !isPossessed(e));
        if (!(t instanceof Mob m)) return Fx.fail(p, "infinity.extra.no_target");
        if (Rules.foreignOwner(p, m) != null) return Fx.fail(p, "infinity.extra.pet.owned");
        return startMob(p, m, true);
    }

    /** Right: open the player list. */
    public static boolean openPlayerMenu(ServerPlayer p) {
        if (isPossessing(p)) return Fx.fail(p, "infinity.soul.possess.already");
        List<String> names = new ArrayList<>(), ids = new ArrayList<>();
        for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
            if (o == p || isGhost(o) || o.isSpectator() || isPossessed(o) || com.infinitystones.tensura.skill.ability.extra.space.SpiritualGathering.isTrapped(o)) continue;
            names.add(o.getGameProfile().getName());
            ids.add(o.getUUID().toString());
        }
        if (names.isEmpty()) return Fx.fail(p, "infinity.soul.possess.nobody");
        MENU_TOKEN.put(p.getUUID(), p.serverLevel().getGameTime() + 20 * 120);
        PacketDistributor.sendToPlayer(p, new SoulNet.Players(names, ids));
        SoulFx.sound(p.serverLevel(), p.position(), SoundEvents.SOUL_ESCAPE.value(), 0.8F, 0.7F, p);
        return true;
    }

    /** A player was picked in the menu. */
    static void pick(ServerPlayer p, String uuid) {
        if (com.infinitystones.tensura.gauntlet.GauntletAbilities.pick(p, uuid)) return;
        Long until = MENU_TOKEN.remove(p.getUUID());
        if (until == null || p.serverLevel().getGameTime() > until || isPossessing(p) || isGhost(p)) return;
        UUID id;
        try { id = UUID.fromString(uuid); } catch (IllegalArgumentException e) { return; }
        ServerPlayer t = p.server.getPlayerList().getPlayer(id);
        if (t == null || t == p || !t.isAlive() || isGhost(t) || isPossessed(t) || t.isSpectator() || com.infinitystones.tensura.skill.ability.extra.space.SpiritualGathering.isTrapped(t)) { Fx.fail(p, "infinity.extra.no_target"); return; }
        startPlayer(p, t);
    }

    // ------------------------------------------------------------------ start / stop

    private static void startPlayer(ServerPlayer p, ServerPlayer t) {
        Session s = new Session(p.getUUID(), t.getUUID(), SoulNet.SPECTATE);
        begin(p, s);
        // Effects for the caster alone: the target must not notice anything.
        ServerLevel l = p.serverLevel();
        SoulFx.sigil(l, p.position().add(0, 0.05, 0), 2.2, 0, p);
        SoulFx.burst(l, p.position().add(0, 1, 0), 1.5, p);
        SoulFx.sound(l, p.position(), SoundEvents.SOUL_ESCAPE.value(), 1.0F, 0.5F, p);
        SoulFx.sound(l, p.position(), SoundEvents.WARDEN_HEARTBEAT, 1.0F, 0.8F, p);
        follow(p, t);
        send(p, s, t);
        Fx.bar(p, "infinity.soul.possess.spectating", t.getDisplayName());
    }

    static boolean startMob(ServerPlayer p, Mob m, boolean fx) {
        if (isPossessing(p) || isPossessed(m) || !m.isAlive()) return false;
        Session s = new Session(p.getUUID(), m.getUUID(), SoulNet.MOB);
        s.mobNoAi = m.isNoAi();
        s.mobNoGravity = m.isNoGravity();
        s.yaw = m.getYRot();
        s.pitch = m.getXRot();
        m.getPersistentData().putBoolean(MOB_TAG, s.mobNoAi);
        m.setNoAi(true);
        m.setTarget(null);
        m.getNavigation().stop();
        if (m instanceof EnderDragon) m.setNoGravity(true);
        begin(p, s);
        if (fx) {
            ServerLevel l = m.level() instanceof ServerLevel sl ? sl : p.serverLevel();
            Vec3 c = m.getBoundingBox().getCenter();
            SoulFx.helix(l, p.getEyePosition(), c, 3, 0.6, 0, SoulFx.D_ORANGE, null);
            SoulFx.sigil(l, m.position().add(0, 0.05, 0), Math.max(2, m.getBbWidth() * 1.6), 0, null);
            SoulFx.burst(l, c, Math.max(1.2, m.getBbHeight()), null);
            SoulFx.sound(l, c, SoundEvents.SOUL_ESCAPE.value(), 1.6F, 0.5F, null);
            SoulFx.sound(l, c, SoundEvents.EVOKER_PREPARE_SUMMON, 0.9F, 0.7F, null);
            SoulFx.sound(l, c, SoundEvents.BEACON_POWER_SELECT, 1.0F, 0.6F, null);
        }
        follow(p, m);
        send(p, s, m);
        Fx.bar(p, "infinity.soul.possess.mob", m.getDisplayName());
        return true;
    }

    private static void begin(ServerPlayer p, Session s) {
        CompoundTag o = new CompoundTag();
        o.putString("dim", p.level().dimension().location().toString());
        o.putDouble("x", p.getX()); o.putDouble("y", p.getY()); o.putDouble("z", p.getZ());
        o.putFloat("yaw", p.getYRot()); o.putFloat("pitch", p.getXRot());
        o.putBoolean("inv", p.isInvulnerable());
        Rules.data(p).put(ORIGIN, o);
        s.wasInvulnerable = p.isInvulnerable();
        SESSIONS.put(p.getUUID(), s);
        p.stopRiding();
        p.ejectPassengers();
        p.closeContainer();
        GHOSTS.add(p.getUUID());
        ghostFlags(p);
        broadcastGhosts(p.server);
    }

    private static void ghostFlags(ServerPlayer p) {
        p.setInvisible(true);
        p.setInvulnerable(true);
        p.setNoGravity(true);
        p.fallDistance = 0;
        p.setDeltaMovement(Vec3.ZERO);
        p.setAirSupply(p.getMaxAirSupply());
    }

    /** Ends the caster's possession (any reason). */
    public static void release(ServerPlayer p) {
        Session s = SESSIONS.remove(p.getUUID());
        if (s == null) return;
        PUPPETS.remove(s.target);
        Entity t = resolve(p.server, s);
        if (s.mining != null && t != null) t.level().destroyBlockProgress(t.getId(), s.mining, -1);
        if (t instanceof Mob m) {
            restoreMob(m, s.mobNoAi);
            m.setNoGravity(s.mobNoGravity);
            if (m.level() instanceof ServerLevel l) {
                Vec3 c = m.getBoundingBox().getCenter();
                SoulFx.helix(l, c, c.add(0, 4, 0), 2, 0.5, 0, SoulFx.D_GOLD, null);
                SoulFx.emit(l, ParticleTypes.SOUL, c, 25, 0.5, 0.06, null);
                SoulFx.sound(l, c, SoundEvents.SOUL_ESCAPE.value(), 1.2F, 1.2F, null);
            }
        }
        if (t instanceof ServerPlayer tp) tp.connection.teleport(tp.getX(), tp.getY(), tp.getZ(), tp.getYRot(), tp.getXRot());
        if (p.containerMenu instanceof ChestMenu cm && cm.getContainer() instanceof PuppetInventory) p.closeContainer();
        PacketDistributor.sendToPlayer(p, new SoulNet.State(SoulNet.NONE, -1, ""));
        GHOSTS.remove(p.getUUID());
        p.setInvisible(false);
        p.setNoGravity(false);
        p.setInvulnerable(s.wasInvulnerable);
        returnHome(p);
        broadcastGhosts(p.server);
        SoulFx.sound(p.serverLevel(), p.position(), SoundEvents.SOUL_ESCAPE.value(), 1.0F, 1.5F, p);
        SoulFx.emit(p.serverLevel(), ParticleTypes.SOUL, p.position().add(0, 1, 0), 20, 0.4, 0.04, null);
    }

    private static void restoreMob(Mob m, boolean noAi) {
        m.setNoAi(noAi);
        m.getPersistentData().remove(MOB_TAG);
    }

    private static void returnHome(ServerPlayer p) {
        CompoundTag o = Rules.data(p).getCompound(ORIGIN);
        Rules.data(p).remove(ORIGIN);
        if (o.isEmpty()) return;
        ResourceLocation dim = ResourceLocation.tryParse(o.getString("dim"));
        ServerLevel level = dim == null ? null : p.server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
        if (level == null) level = p.server.overworld();
        p.teleportTo(level, o.getDouble("x"), o.getDouble("y"), o.getDouble("z"), o.getFloat("yaw"), o.getFloat("pitch"));
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
    }

    // ------------------------------------------------------------------ client input

    static void input(ServerPlayer p, SoulNet.Input in) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null || s.mode == SoulNet.SPECTATE) return;
        if (!Float.isFinite(in.forward()) || !Float.isFinite(in.strafe()) || !Float.isFinite(in.yaw()) || !Float.isFinite(in.pitch())) return;
        s.forward = Mth.clamp(in.forward(), -1, 1);
        s.strafe = Mth.clamp(in.strafe(), -1, 1);
        s.yaw = Mth.wrapDegrees(in.yaw());
        s.pitch = Mth.clamp(in.pitch(), -90, 90);
        s.jump = in.jump(); s.sneak = in.sneak(); s.sprint = in.sprint(); s.attack = in.attack(); s.use = in.use();
        if (in.slot() >= 0 && in.slot() < 9) s.slot = in.slot();
    }

    static void action(ServerPlayer p, int action, int arg) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null) return;
        Entity t = resolve(p.server, s);
        if (t == null) { release(p); return; }
        switch (action) {
            case ACT_RELEASE -> release(p);
            case ACT_CONTROL -> {
                if (!(t instanceof ServerPlayer tp)) { release(p); return; }
                if (s.mode == SoulNet.SPECTATE) {
                    s.mode = SoulNet.CONTROL;
                    s.yaw = tp.getYRot(); s.pitch = tp.getXRot(); s.vel = tp.getDeltaMovement();
                    s.slot = tp.getInventory().selected;
                    PUPPETS.add(tp.getUUID());
                    SoulFx.sound(p.serverLevel(), p.position(), SoundEvents.WARDEN_HEARTBEAT, 1.2F, 0.6F, p);
                    SoulFx.sound(p.serverLevel(), p.position(), SoundEvents.SCULK_CLICKING, 1.0F, 0.7F, p);
                    Fx.bar(p, "infinity.soul.possess.control", tp.getDisplayName());
                } else {
                    s.mode = SoulNet.SPECTATE;
                    PUPPETS.remove(tp.getUUID());
                    if (s.mining != null) { tp.level().destroyBlockProgress(tp.getId(), s.mining, -1); s.mining = null; }
                    if (tp.isUsingItem()) tp.releaseUsingItem();
                    tp.connection.teleport(tp.getX(), tp.getY(), tp.getZ(), tp.getYRot(), tp.getXRot());
                    Fx.bar(p, "infinity.soul.possess.spectating", tp.getDisplayName());
                }
                send(p, s, t);
            }
            case ACT_INVENTORY -> { if (s.mode == SoulNet.CONTROL && t instanceof ServerPlayer tp) openInventory(p, tp); }
            case ACT_DROP -> { if (s.mode == SoulNet.CONTROL && t instanceof ServerPlayer tp) tp.drop(arg != 0); }
            case ACT_ATTACK -> {
                if (s.mode == SoulNet.CONTROL && t instanceof ServerPlayer tp) attackPlayer(tp);
                else if (s.mode == SoulNet.MOB && t instanceof Mob m) MobPowers.melee(p, m);
            }
            case ACT_USE -> {
                if (s.mode == SoulNet.CONTROL && t instanceof ServerPlayer tp) usePlayer(tp);
                else if (s.mode == SoulNet.MOB && t instanceof Mob m) MobPowers.ability(p, m, false);
            }
            case ACT_ABILITY, ACT_ABILITY2 -> { if (s.mode == SoulNet.MOB && t instanceof Mob m) MobPowers.ability(p, m, action == ACT_ABILITY2); }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post e) {
        if (SESSIONS.isEmpty()) return;
        MinecraftServer server = e.getServer();
        for (Session s : new ArrayList<>(SESSIONS.values())) {
            ServerPlayer p = server.getPlayerList().getPlayer(s.owner);
            if (p == null) { SESSIONS.remove(s.owner); PUPPETS.remove(s.target); GHOSTS.remove(s.owner); continue; }
            Entity t = resolve(server, s);
            if (t == null || !t.isAlive() || (t instanceof ServerPlayer tp && (tp.isSpectator() || tp.hasDisconnected()))) {
                Fx.barGray(p, "infinity.soul.possess.lost");
                release(p);
                continue;
            }
            s.ticks++;
            if (t instanceof Mob m && m.level() != p.level() && s.ticks > 2) { release(p); continue; }
            if (t instanceof Mob m && !m.isNoAi()) m.setNoAi(true);
            if (s.mode == SoulNet.CONTROL && t instanceof ServerPlayer tp) drivePlayer(s, tp);
            else if (s.mode == SoulNet.MOB && t instanceof Mob m) driveMob(s, m);
            if (!follow(p, t)) send(p, s, t);
            ghostFlags(p);
            if (s.ticks % 40 == 0) send(p, s, t);
            if (t instanceof ServerPlayer tp && s.ticks % 4 == 0) {
                List<ItemStack> bar = new ArrayList<>(9);
                for (int i = 0; i < 9; i++) bar.add(tp.getInventory().getItem(i).copy());
                PacketDistributor.sendToPlayer(p, new SoulNet.Hud(bar, tp.getInventory().selected, tp.getHealth(), tp.getMaxHealth()));
            }
            if (s.mode == SoulNet.MOB && s.ticks % 3 == 0 && t.level() instanceof ServerLevel l) {
                // a faint ember aura so everyone sees the mob is possessed
                SoulFx.emit(l, SoulFx.D_ORANGE, t.getBoundingBox().getCenter(), 2, t.getBbWidth() * 0.4, 0.01, null);
                if (s.ticks % 9 == 0) SoulFx.emit(l, ParticleTypes.SOUL_FIRE_FLAME, t.position().add(0, t.getBbHeight() + 0.2, 0), 1, 0.15, 0.01, null);
            }
        }
    }

    private static @Nullable Entity resolve(MinecraftServer server, Session s) {
        ServerPlayer pl = server.getPlayerList().getPlayer(s.target);
        if (pl != null) return pl;
        for (ServerLevel l : server.getAllLevels()) {
            Entity e = l.getEntity(s.target);
            if (e != null) return e;
        }
        return null;
    }

    /** Keeps the ghost body just above the target (chunks + tracking). Returns false when it changed dimension. */
    private static boolean follow(ServerPlayer p, Entity t) {
        double y = t.getY() + t.getBbHeight() + 0.6;
        if (p.level() != t.level() && t.level() instanceof ServerLevel tl) {
            p.teleportTo(tl, t.getX(), y, t.getZ(), p.getYRot(), p.getXRot());
            return false;
        }
        p.absMoveTo(t.getX(), y, t.getZ(), p.getYRot(), p.getXRot());
        p.serverLevel().getChunkSource().move(p);
        return true;
    }

    private static void send(ServerPlayer p, Session s, Entity t) {
        PacketDistributor.sendToPlayer(p, new SoulNet.State(s.mode, t.getId(), t.getDisplayName().getString()));
    }

    private static void broadcastGhosts(MinecraftServer server) {
        List<Integer> ids = new ArrayList<>();
        for (UUID g : GHOSTS) { ServerPlayer gp = server.getPlayerList().getPlayer(g); if (gp != null) ids.add(gp.getId()); }
        PacketDistributor.sendToAllPlayers(new SoulNet.Ghosts(ids));
    }

    // ------------------------------------------------------------------ driving bodies

    /** Shared walking/flying/swimming kinematics. Returns the velocity used. */
    private static void move(Session s, LivingEntity e, double speed, double jumpPower, boolean flying, boolean swimmer) {
        double f = s.forward, st = s.strafe;
        double len = Math.sqrt(f * f + st * st);
        if (len > 1) { f /= len; st /= len; }
        float yr = s.yaw * Mth.DEG_TO_RAD;
        double sin = Mth.sin(yr), cos = Mth.cos(yr);
        double tx = (st * cos - f * sin) * speed, tz = (f * cos + st * sin) * speed;
        Vec3 v = s.vel;
        boolean wet = e.isInWater() || e.isInLava();
        if (flying || (swimmer && wet)) {
            double ty = ((s.jump ? 1 : 0) - (s.sneak ? 1 : 0)) * speed;
            if (f != 0) ty += -Mth.sin(s.pitch * Mth.DEG_TO_RAD) * f * speed;
            v = v.lerp(new Vec3(tx, ty, tz), 0.25);
            if (!flying) v = v.add(0, 0.005, 0);
        } else if (wet) {
            double vy = v.y * 0.8 - 0.02 + (s.jump ? 0.06 : 0) - (s.sneak ? 0.03 : 0);
            v = new Vec3(Mth.lerp(0.3, v.x, tx * 0.55), vy, Mth.lerp(0.3, v.z, tz * 0.55));
        } else {
            double k = e.onGround() ? 0.55 : 0.09;
            double vy = e.onGround() ? (s.jump ? jumpPower : -0.08) : (v.y - 0.08) * 0.98;
            v = new Vec3(Mth.lerp(k, v.x, tx), vy, Mth.lerp(k, v.z, tz));
        }
        e.move(MoverType.SELF, v);
        if (e.verticalCollision) v = new Vec3(v.x, 0, v.z);
        if (e.horizontalCollision) {
            v = new Vec3(e.horizontalCollision ? v.x * 0.5 : v.x, v.y, v.z * 0.5);
            if (e instanceof Mob && e.onGround() && len > 0.1 && !flying) v = new Vec3(v.x, jumpPower, v.z); // mobs auto-jump
        }
        e.setDeltaMovement(v);
        s.vel = v;
    }

    private static void drivePlayer(Session s, ServerPlayer t) {
        if (t.isPassenger()) t.stopRiding();
        t.setSprinting(s.sprint && s.forward > 0);
        t.setShiftKeyDown(s.sneak);
        t.setYRot(s.yaw); t.setXRot(s.pitch); t.setYHeadRot(s.yaw);
        double speed = (t.getAttributeValue(Attributes.MOVEMENT_SPEED) * 2.16) * (t.isSprinting() ? 1.3 : 1) * (s.sneak ? 0.3 : 1);
        boolean flying = t.getAbilities().flying;
        move(s, t, flying ? speed * 2.5 : speed, t.getAttributeValue(Attributes.JUMP_STRENGTH), flying, false);
        if (t.onGround()) t.resetFallDistance();
        t.connection.teleport(t.getX(), t.getY(), t.getZ(), s.yaw, s.pitch);
        // hotbar
        if (s.slot >= 0 && s.slot != t.getInventory().selected) {
            t.getInventory().selected = s.slot;
            t.connection.send(new ClientboundSetCarriedItemPacket(s.slot));
        }
        // keep using (bows, food, shields) while the button is held
        if (s.prevUse && !s.use && t.isUsingItem()) t.releaseUsingItem();
        s.prevUse = s.use;
        // mining
        HitResult hr = s.attack ? pick(t, t.blockInteractionRange(), t.entityInteractionRange()) : null;
        if (hr instanceof BlockHitResult bh && hr.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = bh.getBlockPos();
            if (!pos.equals(s.mining)) {
                if (s.mining != null) t.level().destroyBlockProgress(t.getId(), s.mining, -1);
                s.mining = pos; s.mineProgress = 0;
            }
            BlockState st = t.level().getBlockState(pos);
            if (!st.isAir() && t.mayBuild()) {
                s.mineProgress += t.isCreative() ? 1 : st.getDestroyProgress(t, t.level(), pos);
                if (s.ticks % 4 == 0) t.swing(InteractionHand.MAIN_HAND, true);
                if (s.mineProgress >= 1) {
                    t.level().destroyBlockProgress(t.getId(), pos, -1);
                    t.gameMode.destroyBlock(pos);
                    s.mining = null; s.mineProgress = 0;
                } else t.level().destroyBlockProgress(t.getId(), pos, (int) (s.mineProgress * 10) - 1);
            }
        } else if (s.mining != null) {
            t.level().destroyBlockProgress(t.getId(), s.mining, -1);
            s.mining = null; s.mineProgress = 0;
        }
    }

    private static void driveMob(Session s, Mob m) {
        m.setYRot(s.yaw); m.setXRot(s.pitch);
        m.setYHeadRot(s.yaw); m.yBodyRot = s.yaw;
        m.getNavigation().stop();
        boolean flying = MobPowers.flies(m);
        boolean swimmer = m instanceof WaterAnimal;
        double base = m.getAttributes().hasAttribute(Attributes.MOVEMENT_SPEED) ? m.getAttributeValue(Attributes.MOVEMENT_SPEED) : 0.25;
        if (flying && m.getAttributes().hasAttribute(Attributes.FLYING_SPEED)) base = Math.max(base, m.getAttributeValue(Attributes.FLYING_SPEED) * 0.6);
        double speed = Mth.clamp(base * (flying ? 1.6 : 1.0), 0.12, 0.55);
        if (flying && (m instanceof FlyingMob || m instanceof EnderDragon || m instanceof WitherBoss)) speed = Math.max(speed, m instanceof EnderDragon ? 0.9 : 0.35);
        if (s.sprint) speed *= 1.45;
        double jump = m.getAttributes().hasAttribute(Attributes.JUMP_STRENGTH) ? m.getAttributeValue(Attributes.JUMP_STRENGTH) : 0.42;
        if (m.getBbHeight() > 2.2) jump *= 1.2;
        move(s, m, speed, jump, flying, swimmer);
        m.setYRot(s.yaw); m.setYHeadRot(s.yaw); m.yBodyRot = s.yaw;
        if (m.onGround()) m.resetFallDistance();
        m.hasImpulse = true;
        if (s.attack && s.ticks % 10 == 0) MobPowers.melee(m.level().getServer().getPlayerList().getPlayer(s.owner), m);
    }

    /** What the body is looking at (block or entity), like the vanilla crosshair pick. */
    static HitResult pick(LivingEntity e, double blockReach, double entityReach) {
        Vec3 eye = e.getEyePosition();
        Vec3 look = e.getViewVector(1F);
        double reach = Math.max(blockReach, entityReach);
        BlockHitResult b = e.level().clip(new ClipContext(eye, eye.add(look.scale(blockReach)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, e));
        double max = b.getType() == HitResult.Type.MISS ? entityReach * entityReach : Math.min(entityReach * entityReach, b.getLocation().distanceToSqr(eye));
        AABB box = e.getBoundingBox().expandTowards(look.scale(reach)).inflate(1);
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(e, eye, eye.add(look.scale(entityReach)), box,
                x -> !x.isSpectator() && x.isPickable() && !isGhost(x) && x != e, max);
        return eh != null ? eh : b;
    }

    private static void attackPlayer(ServerPlayer t) {
        HitResult hr = pick(t, t.blockInteractionRange(), t.entityInteractionRange());
        if (hr instanceof EntityHitResult eh) t.attack(eh.getEntity());
        t.swing(InteractionHand.MAIN_HAND, true);
        t.resetLastActionTime();
    }

    private static void usePlayer(ServerPlayer t) {
        HitResult hr = pick(t, t.blockInteractionRange(), t.entityInteractionRange());
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = t.getItemInHand(hand);
            InteractionResult r = InteractionResult.PASS;
            if (hr instanceof EntityHitResult eh) r = t.interactOn(eh.getEntity(), hand);
            else if (hr instanceof BlockHitResult bh && hr.getType() == HitResult.Type.BLOCK) r = t.gameMode.useItemOn(t, t.level(), stack, hand, bh);
            if (!r.consumesAction()) r = t.gameMode.useItem(t, t.level(), stack, hand);
            if (r.consumesAction()) {
                if (r.shouldSwing()) t.swing(hand, true);
                return;
            }
        }
    }

    private static void openInventory(ServerPlayer p, ServerPlayer t) {
        PuppetInventory inv = new PuppetInventory(t, p.getUUID());
        p.openMenu(new SimpleMenuProvider((id, own, pl) -> new ChestMenu(MenuType.GENERIC_9x5, id, own, inv, 5),
                Component.translatable("infinity.soul.possess.inventory", t.getDisplayName())));
    }

    /** The possessed player's inventory laid out as 5 rows: main ×3, hotbar, armour + offhand. */
    static final class PuppetInventory implements Container {
        private final ServerPlayer t;
        private final UUID owner;
        PuppetInventory(ServerPlayer t, UUID owner) { this.t = t; this.owner = owner; }
        private static int map(int slot) {
            if (slot < 27) return 9 + slot;
            if (slot < 36) return slot - 27;
            if (slot < 40) return 39 - (slot - 36);
            if (slot == 40) return 40;
            return -1;
        }
        private Inventory inv() { return t.getInventory(); }
        @Override public int getContainerSize() { return 45; }
        @Override public boolean isEmpty() { return inv().isEmpty(); }
        @Override public ItemStack getItem(int slot) { int i = map(slot); return i < 0 ? ItemStack.EMPTY : inv().getItem(i); }
        @Override public ItemStack removeItem(int slot, int n) { int i = map(slot); return i < 0 ? ItemStack.EMPTY : inv().removeItem(i, n); }
        @Override public ItemStack removeItemNoUpdate(int slot) { int i = map(slot); return i < 0 ? ItemStack.EMPTY : inv().removeItemNoUpdate(i); }
        @Override public void setItem(int slot, ItemStack stack) {
            int i = map(slot);
            if (i >= 0) { inv().setItem(i, stack); return; }
            if (!stack.isEmpty() && !inv().add(stack)) t.drop(stack, false); // the 4 spare cells never keep anything
        }
        @Override public boolean canPlaceItem(int slot, ItemStack stack) { return map(slot) >= 0; }
        @Override public void setChanged() { inv().setChanged(); }
        @Override public boolean stillValid(Player player) {
            Session s = SESSIONS.get(owner);
            return s != null && s.mode == SoulNet.CONTROL && s.target.equals(t.getUUID()) && t.isAlive() && !t.hasDisconnected();
        }
        @Override public void clearContent() { }
    }

    // ------------------------------------------------------------------ safety events

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent e) {
        if (isGhost(e.getNewAboutToBeSetTarget())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent e) {
        if (isGhost(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        MENU_TOKEN.remove(p.getUUID());
        for (Session s : new ArrayList<>(SESSIONS.values())) {
            if (!s.target.equals(p.getUUID())) continue;
            ServerPlayer owner = p.server.getPlayerList().getPlayer(s.owner);
            if (owner != null) { Fx.barGray(owner, "infinity.soul.possess.lost"); release(owner); }
        }
        Session mine = SESSIONS.get(p.getUUID());
        if (mine != null) {
            // Restore the body now when it is in the same dimension; otherwise the login handler does it.
            Entity t = resolve(p.server, mine);
            if (t instanceof Mob m) { restoreMob(m, mine.mobNoAi); m.setNoGravity(mine.mobNoGravity); }
            SESSIONS.remove(p.getUUID());
            PUPPETS.remove(mine.target);
            GHOSTS.remove(p.getUUID());
            p.setInvisible(false); p.setNoGravity(false); p.setInvulnerable(mine.wasInvulnerable);
            CompoundTag o = Rules.data(p).getCompound(ORIGIN);
            if (!o.isEmpty() && p.level().dimension().location().toString().equals(o.getString("dim"))) {
                p.absMoveTo(o.getDouble("x"), o.getDouble("y"), o.getDouble("z"), o.getFloat("yaw"), o.getFloat("pitch"));
                Rules.data(p).remove(ORIGIN);
            }
            broadcastGhosts(p.server);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        CompoundTag o = Rules.data(p).getCompound(ORIGIN);
        if (!o.isEmpty() && !isPossessing(p)) {
            p.setInvisible(false); p.setNoGravity(false); p.setInvulnerable(o.getBoolean("inv"));
            returnHome(p);
        }
        PacketDistributor.sendToPlayer(p, new SoulNet.State(SoulNet.NONE, -1, ""));
        broadcastGhosts(p.server);
    }

    /** A mob saved while possessed (crash, chunk unload) gets its own AI back. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof Mob m)) return;
        if (m.getPersistentData().contains(MOB_TAG) && !isPossessed(m)) restoreMob(m, m.getPersistentData().getBoolean(MOB_TAG));
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        for (Session s : new ArrayList<>(SESSIONS.values())) {
            ServerPlayer p = e.getServer().getPlayerList().getPlayer(s.owner);
            if (p != null) release(p);
        }
        SESSIONS.clear(); PUPPETS.clear(); GHOSTS.clear();
    }

    private Possession() {}
}
