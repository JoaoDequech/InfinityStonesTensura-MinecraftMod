package com.infinitystones.tensura.skill.ability.extra.mind;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Priv;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Psychic Avatar (toggle). The body stays behind as a kneeling golden shell (armor stand dressed as the player); the
 * mind flies free as an invisible golden projection: flight, no block/entity interaction, mobs ignore it, living
 * beings nearby glow for the owner, other players see a golden particle silhouette and a thread back to the body.
 * Leash (Alt+scroll 32–96): going past it, the body being hit, or the projection taking damage snaps the mind back
 * (flash + shake) and the damage lands on the real player. Always restored on toggle off, stone dropped, logout,
 * death, dimension change and server stop; a crash is recovered at the next login (persisted body position).
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class PsychicAvatar implements Ability {
    public static final PsychicAvatar INSTANCE = new PsychicAvatar();
    public static final String MODE = "mind.psychic_avatar";
    private static final String DATA = "infinity_mind_avatar";
    private static final String TOGGLES = "infinity_toggles";
    private static final double SIGHT = 24;

    @Override public Stone stone() { return Stone.MIND; }

    private static final class State {
        ServerLevel level;
        Vec3 body;
        float yaw, pitch;
        boolean mayfly, flying;
        ArmorStand shell;
        final int cordId = FxNet.newId(), runeId = FxNet.newId(), rune2Id = FxNet.newId(), orbId = FxNet.newId(), crownId = FxNet.newId();
        final Set<Integer> glowing = new HashSet<>();
        int age;
        boolean warned;
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    public static boolean isAvatar(Entity e) { return e != null && ACTIVE.containsKey(e.getUUID()) && e instanceof ServerPlayer; }

    public static int leash(ServerPlayer p) { return ExtraSettings.get(p, MODE, 48, 32, 96, 8); }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void start(ServerPlayer p) {
        if (ACTIVE.containsKey(p.getUUID())) return;
        if (p.isSpectator() || p.isSleeping() || !p.isAlive()) { toggleOff(p); Fx.barGray(p, "infinity.extra.mind.avatar.cant"); return; }
        if (p.isPassenger()) p.stopRiding();
        ServerLevel l = p.serverLevel();
        State s = new State();
        s.level = l;
        s.body = p.position();
        s.yaw = p.getYRot();
        s.pitch = p.getXRot();
        s.mayfly = p.getAbilities().mayfly;
        s.flying = p.getAbilities().flying;
        ACTIVE.put(p.getUUID(), s);
        persist(p, s);
        spawnShell(p, s);

        p.getAbilities().mayfly = true;
        p.getAbilities().flying = true;
        p.onUpdateAbilities();
        p.setInvisible(true);
        MindKit.hideEquipment(p, 40);
        MindKit.push(p, new Vec3(0, 0.45, 0));
        forgetMe(p, l, 32);

        // exit FX: golden burst out of the body
        Vec3 c = s.body.add(0, 1, 0);
        FxNet.flash(p, MindKit.GOLD, 0.55F, 14);
        MindKit.shake(p, 0.8F, 10);
        FxNet.shockwave(l, s.body.add(0, 0.05, 0), 6F, MindKit.GOLD, 26);
        FxNet.shockwave(l, s.body.add(0, 0.1, 0), 3.5F, MindKit.PALE, 18);
        FxNet.spiral(l, s.body, 1.1F, 3.2F, MindKit.GOLD, 30, -1);
        FxNet.flare(l, c, 2.4F, MindKit.PALE, 16);
        FxNet.pillar(l, 0, s.body, 0.6F, 6F, MindKit.GOLD, 24);
        MindKit.spark(l, c, 40);
        Fx.burstRing(l, c, 28, Fx.light(Stone.MIND, 1.1F), 0.25);
        Fx.sound(l, c, SoundEvents.BEACON_ACTIVATE, 0.6F, 1.6F);
        Fx.sound(l, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, 0.8F);
        Fx.sound(l, c, SoundEvents.SOUL_ESCAPE, 0.7F, 1.2F);
        refreshFx(p, s);
        Fx.bar(p, Stone.MIND, "infinity.extra.mind.avatar.on", leash(p));
    }

    @Override
    public void tick(ServerPlayer p) {
        State s = ACTIVE.get(p.getUUID());
        if (s == null) { toggleOff(p); return; }
        s.age++;
        if (p.level() != s.level || !p.isAlive()) { snap(p, null, 0); return; }
        int leash = leash(p);
        double d = p.position().distanceTo(s.body);
        if (d > leash) { Fx.barGray(p, "infinity.extra.mind.avatar.leash"); snap(p, null, 0); return; }
        if (d > leash * 0.85) {
            if (!s.warned || s.age % 20 == 0) Fx.bar(p, Stone.MIND, "infinity.extra.mind.avatar.far", (int) d, leash);
            s.warned = true;
        } else s.warned = false;

        // keep the projection state (other mods may flip these)
        if (!p.getAbilities().mayfly) { p.getAbilities().mayfly = true; p.getAbilities().flying = true; p.onUpdateAbilities(); }
        if (!p.isInvisible()) p.setInvisible(true);
        if (p.isPassenger()) p.stopRiding();
        p.fallDistance = 0;
        if (s.age % 20 == 0) MindKit.hideEquipment(p, 40);

        if (s.shell == null || s.shell.isRemoved()) {
            if (s.level.isLoaded(net.minecraft.core.BlockPos.containing(s.body))) spawnShell(p, s);
        } else if (s.age % 4 == 0) breathe(s);

        if (s.age % 2 == 0) silhouette(p, s);
        if (s.age % 30 == 0) refreshFx(p, s);
        if (s.age % 20 == 0) sight(p, s);
        if (s.age % 40 == 0) {
            forgetMe(p, s.level, 24);
            Fx.sound(s.level, s.body.add(0, 1, 0), SoundEvents.AMETHYST_BLOCK_CHIME, 0.4F, 0.6F + s.level.random.nextFloat() * 0.4F);
        }
    }

    @Override
    public void stop(ServerPlayer p) { restore(p, true, true); }

    // ------------------------------------------------------------------ snap back / restore

    /** The mind returns to the body (with impact), then the damage that caused it (if any) lands. */
    private static void snap(ServerPlayer p, DamageSource src, float amount) {
        if (!ACTIVE.containsKey(p.getUUID())) return;
        restore(p, true, true);
        if (src != null && amount > 0) {
            p.invulnerableTime = 0;
            p.hurt(src, amount);
        }
    }

    private static void restore(ServerPlayer p, boolean teleport, boolean fx) {
        State s = ACTIVE.remove(p.getUUID());
        toggleOff(p);
        if (s == null) return;
        ServerLevel l = s.level;
        if (teleport && (p.isAlive() || p.level() == l)) {
            p.stopRiding();
            p.teleportTo(l, s.body.x, s.body.y, s.body.z, s.yaw, s.pitch);
            p.setDeltaMovement(Vec3.ZERO);
            p.fallDistance = 0;
        }
        boolean gmFly = p.gameMode.getGameModeForPlayer() == GameType.CREATIVE || p.isSpectator();
        p.getAbilities().mayfly = s.mayfly || gmFly;
        p.getAbilities().flying = (s.flying && p.getAbilities().mayfly) && !p.onGround();
        p.onUpdateAbilities();
        p.setInvisible(p.hasEffect(MobEffects.INVISIBILITY));
        MindKit.hideEquipment(p, 0);
        MindKit.sendEquipment(p, false);
        for (int id : s.glowing) {
            Entity e = l.getEntity(id);
            if (e != null) Priv.glow(p, e, false);
        }
        s.glowing.clear();
        Vec3 body = s.body;
        if (s.shell != null) MindKit.discard(s.shell);
        FxNet.screen(p, FxNet.AVATAR_TINT, 0);
        for (int id : new int[]{s.cordId, s.runeId, s.rune2Id, s.orbId, s.crownId}) FxNet.kill(l, body, id);
        Rules.data(p).remove(DATA);
        if (fx && p.isAlive()) {
            Vec3 c = body.add(0, 1, 0);
            FxNet.flash(p, 0xFFF6C8, 0.85F, 12);
            MindKit.shake(p, 2.2F, 14);
            FxNet.shockwave(l, body.add(0, 0.05, 0), 5F, MindKit.GOLD, 18);
            FxNet.flare(l, c, 3F, 0xFFFFFF, 10);
            FxNet.spiral(l, body.add(0, 3, 0), 0.9F, -3F, MindKit.GOLD, 16, -1);
            MindKit.spark(l, c, 30);
            Fx.sound(l, c, SoundEvents.AMETHYST_BLOCK_BREAK, 0.7F, 0.6F);
            Fx.sound(l, c, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 0.5F, 0.5F);
            Fx.sound(l, c, SoundEvents.BEACON_DEACTIVATE, 0.5F, 1.4F);
        }
    }

    /** Leaves the persisted toggle OFF so it never restarts by itself (relog / picking the stone up again). */
    private static void toggleOff(ServerPlayer p) {
        CompoundTag all = p.getPersistentData().getCompound(TOGGLES);
        if (all.getBoolean(MODE)) {
            all.putBoolean(MODE, false);
            p.getPersistentData().put(TOGGLES, all);
        }
    }

    private static void persist(ServerPlayer p, State s) {
        CompoundTag t = new CompoundTag();
        t.putString("dim", s.level.dimension().location().toString());
        t.putDouble("x", s.body.x); t.putDouble("y", s.body.y); t.putDouble("z", s.body.z);
        t.putFloat("yaw", s.yaw); t.putFloat("pitch", s.pitch);
        t.putBoolean("mayfly", s.mayfly); t.putBoolean("flying", s.flying);
        Rules.data(p).put(DATA, t);
    }

    // ------------------------------------------------------------------ visuals

    private static void spawnShell(ServerPlayer p, State s) {
        // kneeling/meditating: lowered so the bent legs rest on the floor
        ArmorStand a = MindKit.body(p, s.body.add(0, -0.55, 0), s.yaw, true, false);
        if (a == null) return;
        MindKit.pose(a, 22, 0, -62, -12, -62, 12, -88, -88);
        a.setBodyPose(new net.minecraft.core.Rotations(8, 0, 0));
        MindKit.goldGlow(a);
        s.shell = a;
    }

    private static void breathe(State s) {
        float b = Mth.sin(s.age * 0.08F) * 3F;
        s.shell.setHeadPose(new net.minecraft.core.Rotations(22 + b, 0, 0));
    }

    private static void refreshFx(ServerPlayer p, State s) {
        ServerLevel l = s.level;
        FxNet.screen(p, FxNet.AVATAR_TINT, 60);
        if (s.shell != null && !s.shell.isRemoved()) FxNet.tether(l, s.cordId, s.shell, p, MindKit.GOLD, 0.05F, 60);
        FxNet.runes(l, s.runeId, s.body.add(0, 0.03, 0), 1.6F, MindKit.GOLD, 60, 0.6F);
        FxNet.runes(l, s.rune2Id, s.body.add(0, 0.06, 0), 0.9F, MindKit.PALE, 60, -1.4F);
        FxNet.orb(l, s.orbId, s.body.add(0, 1, 0), 0.55F, MindKit.GOLD, 60, -1, Vec3.ZERO);
        FxNet.crown(l, s.crownId, p, MindKit.GOLD, 60);
    }

    /** Golden particle silhouette of the projection, sent to everyone but the owner (who would see it inside the camera). */
    private static void silhouette(ServerPlayer p, State s) {
        ServerLevel l = s.level;
        List<ServerPlayer> viewers = new ArrayList<>();
        for (ServerPlayer sp : l.players()) if (sp != p && sp.distanceToSqr(p) < 64 * 64) viewers.add(sp);
        Vec3 base = p.position();
        float yaw = p.yBodyRot * Mth.DEG_TO_RAD;
        double cos = Mth.cos(yaw), sin = Mth.sin(yaw);
        ParticleOptions gold = Fx.dust(MindKit.GOLD, 0.7F), pale = Fx.dust(MindKit.PALE, 0.55F);
        float sw = Mth.sin(s.age * 0.25F) * 0.12F;
        List<double[]> pts = new ArrayList<>();
        // head ring
        for (int i = 0; i < 8; i++) { double a = Math.PI * 2 * i / 8; pts.add(new double[]{Math.cos(a) * 0.26, 1.62 + Math.sin(a) * 0.26, 0}); }
        // torso
        for (int i = 0; i <= 4; i++) { double y = 0.78 + i * 0.15; pts.add(new double[]{-0.22, y, 0}); pts.add(new double[]{0.22, y, 0}); }
        // arms (floating, slightly raised)
        for (int i = 0; i <= 3; i++) { double y = 1.35 - i * 0.17; pts.add(new double[]{-0.38 - i * 0.03, y, sw * i}); pts.add(new double[]{0.38 + i * 0.03, y, -sw * i}); }
        // legs fading into a wisp
        for (int i = 0; i <= 3; i++) { double y = 0.7 - i * 0.18; double w = 0.12 * (1 - i / 4.0); pts.add(new double[]{-w, y, -i * 0.08}); pts.add(new double[]{w, y, -i * 0.08}); }
        for (ServerPlayer v : viewers) {
            int k = 0;
            for (double[] q : pts) {
                double x = q[0] * cos - q[2] * sin, z = q[0] * sin + q[2] * cos;
                l.sendParticles(v, (k++ & 3) == 0 ? pale : gold, false, base.x + x, base.y + q[1], base.z + z, 1, 0, 0, 0, 0);
            }
        }
        // trail of sparks (everyone, including owner: they fall behind the camera)
        if (s.age % 4 == 0) Fx.at(l, ParticleTypes.END_ROD, base.add(0, 0.6, 0), 1, 0.25, 0.01);
        Fx.at(l, Fx.light(Stone.MIND, 0.5F), base.add(0, 0.3, 0), 2, 0.2, 0.0);
    }

    /** Living beings near the projection glow for the owner only (seen through walls). */
    private static void sight(ServerPlayer p, State s) {
        Set<Integer> now = new HashSet<>();
        for (LivingEntity e : s.level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(SIGHT),
                e -> e != p && Rules.isLiving(e) && !MindKit.isBody(e))) {
            if (now.size() >= 48) break;
            now.add(e.getId());
            Priv.glow(p, e, true);
        }
        for (int id : s.glowing) if (!now.contains(id)) {
            Entity e = s.level.getEntity(id);
            if (e != null) Priv.glow(p, e, false);
        }
        s.glowing.clear();
        s.glowing.addAll(now);
    }

    /** Mobs already targeting the projection forget it. */
    private static void forgetMe(ServerPlayer p, ServerLevel l, double r) {
        for (Mob m : l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(r), m -> m.getTarget() == p)) {
            m.setTarget(null);
            if (m.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) m.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
    }

    private static UUID ownerOfShell(Entity e) {
        if (!(e instanceof ArmorStand)) return null;
        for (Map.Entry<UUID, State> en : ACTIVE.entrySet()) if (en.getValue().shell == e) return en.getKey();
        return null;
    }

    private static ServerPlayer player(Entity any, UUID id) {
        return any.getServer() == null ? null : any.getServer().getPlayerList().getPlayer(id);
    }

    // ------------------------------------------------------------------ rules while projected

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void damage(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !isAvatar(p)) return;
        DamageSource src = e.getSource();
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) { restore(p, true, true); return; }
        float amount = e.getAmount();
        e.setCanceled(true);
        snap(p, src, amount);
    }

    @SubscribeEvent
    public static void attack(AttackEntityEvent e) {
        if (isAvatar(e.getEntity())) { e.setCanceled(true); return; }
        UUID owner = ownerOfShell(e.getTarget());
        if (owner == null) return;
        e.setCanceled(true);
        ServerPlayer p = player(e.getTarget(), owner);
        if (p == null) return;
        Player attacker = e.getEntity();
        float dmg = (float) Math.max(1, attacker.getAttributeValue(Attributes.ATTACK_DAMAGE));
        Fx.barGray(p, "infinity.extra.mind.avatar.hit");
        snap(p, attacker.damageSources().playerAttack(attacker), dmg);
    }

    @SubscribeEvent
    public static void projectile(ProjectileImpactEvent e) {
        if (!(e.getRayTraceResult() instanceof EntityHitResult hit)) return;
        Entity t = hit.getEntity();
        if (isAvatar(t)) { e.setCanceled(true); return; } // passes through the projection
        UUID owner = ownerOfShell(t);
        if (owner == null) return;
        e.setCanceled(true);
        ServerPlayer p = player(t, owner);
        if (p == null) return;
        Projectile pr = e.getProjectile();
        LivingEntity thrower = pr.getOwner() instanceof LivingEntity le ? le : null;
        pr.discard();
        Fx.barGray(p, "infinity.extra.mind.avatar.hit");
        snap(p, p.damageSources().mobProjectile(pr, thrower), 4F);
    }

    @SubscribeEvent
    public static void target(LivingChangeTargetEvent e) {
        if (isAvatar(e.getNewAboutToBeSetTarget())) e.setCanceled(true);
    }

    @SubscribeEvent public static void rcBlock(PlayerInteractEvent.RightClickBlock e) { if (isAvatar(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void lcBlock(PlayerInteractEvent.LeftClickBlock e) { if (isAvatar(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void rcItem(PlayerInteractEvent.RightClickItem e) { if (isAvatar(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void rcEntity(PlayerInteractEvent.EntityInteract e) { if (isAvatar(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void rcEntity2(PlayerInteractEvent.EntityInteractSpecific e) { if (isAvatar(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent e) { if (isAvatar(e.getPlayer())) e.setCanceled(true); }
    @SubscribeEvent public static void place(BlockEvent.EntityPlaceEvent e) { if (isAvatar(e.getEntity())) e.setCanceled(true); }
    @SubscribeEvent public static void pickup(ItemEntityPickupEvent.Pre e) { if (isAvatar(e.getPlayer())) e.setCanPickup(TriState.FALSE); }
    @SubscribeEvent public static void travel(EntityTravelToDimensionEvent e) { if (isAvatar(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void death(LivingDeathEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && isAvatar(p)) restore(p, true, false);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && isAvatar(p)) restore(p, true, false);
    }

    /** Crash recovery: the body position was persisted, put the player back and give the flight state back. */
    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || isAvatar(p)) return;
        CompoundTag t = Rules.data(p).getCompound(DATA);
        toggleOff(p);
        if (t.isEmpty()) return;
        Rules.data(p).remove(DATA);
        ServerLevel l = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(t.getString("dim"))));
        if (l != null) p.teleportTo(l, t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getFloat("yaw"), t.getFloat("pitch"));
        boolean gmFly = p.gameMode.getGameModeForPlayer() == GameType.CREATIVE || p.isSpectator();
        p.getAbilities().mayfly = t.getBoolean("mayfly") || gmFly;
        p.getAbilities().flying = t.getBoolean("flying") && p.getAbilities().mayfly;
        p.onUpdateAbilities();
        p.setInvisible(p.hasEffect(MobEffects.INVISIBILITY));
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) {
        for (UUID id : new ArrayList<>(ACTIVE.keySet())) {
            ServerPlayer p = e.getServer().getPlayerList().getPlayer(id);
            if (p != null) restore(p, true, false);
        }
        ACTIVE.clear();
    }

    @SuppressWarnings("unused")
    private static boolean sameLevel(Entity a, Level b) { return a.level() == b; }
}
