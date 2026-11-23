package com.infinitystones.tensura.skill.ability.extra.space;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneProgress;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Priv;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import com.infinitystones.tensura.InfinityStones;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Space Stone "Reunião Espiritual": the caster rises 30 blocks and flies; chosen players are pulled in by angelic hands and
 * stood in a circle around him (invisible floor, locked: they can only look), inside a client-drawn Japanese dojo scenery
 * and a ring of water. Use again: the scenery dissolves, hands send everybody home (Haste III + Regeneration II), then the
 * caster snaps back to where he started. Everything is server-authoritative and saved on the players, so a logout, death,
 * crash or server stop never leaves anyone stranded in the sky.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class SpiritualGathering implements Ability {
    public static final SpiritualGathering INSTANCE = new SpiritualGathering();
    public static final String NODE = "spiritual_gathering";

    private static final String K = "infinity.space.gather.";
    private static final String TAG = "infinity_gather";
    private static final int GOLD = 0xFFE08A, WHITE = 0xFFFFFF;

    // timeline (ticks)
    private static final int RISE = 50;
    private static final int T_HAND = 16, T_REACH = 34, T_GRAB = 42, T_FLASH = 44, T_TP = 48, HAND_LIFE = 76;
    private static final int DISSOLVE = 40, AFTER_ALL = 40;
    private static final int MAX_MEMBERS = 32;

    private static final int WAIT = 0, PULL = 1, IN = 2, RWAIT = 3, RPULL = 4;

    private static final class M {
        final UUID id; final boolean caster;
        ResourceKey<Level> dim; double x, y, z; float yaw, pitch;
        boolean mayfly, flying, noGravity;
        Vec3 slot = Vec3.ZERO; float slotYaw;
        int state = WAIT, at, held;
        boolean nv;
        M(UUID id, boolean caster) { this.id = id; this.caster = caster; }
    }

    private static final class S {
        final int id = NEXT_ID++;
        final UUID caster; final ServerLevel level; final Vec3 origin, center; final float oyaw, opitch;
        final double ringR, sceneR; final long seed;
        final M cm; final List<M> ms = new ArrayList<>();
        int age, phase, endAge = -1, casterReturnAt = -1;
        boolean casterGone, casterDead;
        S(ServerPlayer c, Vec3 center, double ringR) {
            caster = c.getUUID(); level = c.serverLevel(); origin = c.position(); this.center = center;
            oyaw = c.getYRot(); opitch = c.getXRot(); this.ringR = ringR; sceneR = ringR + 34; seed = c.getRandom().nextLong();
            cm = new M(c.getUUID(), true);
        }
    }

    private static int NEXT_ID = 1;
    private static boolean internal;
    private static final Map<UUID, S> SESSIONS = new LinkedHashMap<>();
    /** Every player that belongs to a session (caster included) -> that session. */
    private static final Map<UUID, S> MEMBER_OF = new HashMap<>();
    /** Gathered players that are inside the scenery (locked). */
    private static final Map<UUID, S> TRAPPED = new HashMap<>();
    private static final Map<UUID, Long> MENU = new HashMap<>();
    private static final Map<UUID, Long> PRAYED = new HashMap<>();
    private static final Set<UUID> NV_OWNED = new HashSet<>();

    @Override public Stone stone() { return Stone.SPACE; }

    // ------------------------------------------------------------------ public checks (used by other powers)

    /** True for a player held inside a Spiritual Gathering: teleport-type powers must not move him. */
    public static boolean isTrapped(Entity e) { return e instanceof ServerPlayer p && TRAPPED.containsKey(p.getUUID()); }

    /** The caster or any participant of a running gathering (fall damage and similar protections). */
    private static boolean inSession(Entity e) { return e instanceof ServerPlayer p && MEMBER_OF.containsKey(p.getUUID()); }

    // ------------------------------------------------------------------ wheel

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        S s = SESSIONS.get(p.getUUID());
        if (s != null) {
            if (s.phase != 0) return Fx.fail(p, K + "ending");
            beginEnd(s, false);
            return true;
        }
        if (MEMBER_OF.containsKey(p.getUUID())) return Fx.fail(p, K + "busy");
        List<GatherNet.Entry> list = new ArrayList<>();
        for (ServerPlayer o : p.server.getPlayerList().getPlayers()) {
            if (!eligible(p, o)) continue;
            list.add(new GatherNet.Entry(o.getUUID().toString(), o.getGameProfile().getName(), o.level().dimension().location().toString()));
            if (list.size() >= 64) break;
        }
        MENU.put(p.getUUID(), p.serverLevel().getGameTime() + 20 * 120);
        PacketDistributor.sendToPlayer(p, new GatherNet.Open(list));
        Vec3 at = p.position();
        Priv.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, at, 0.5F, 1.5F);
        Priv.sound(p, SoundEvents.ENCHANTMENT_TABLE_USE, at, 0.5F, 1.3F);
        return true;
    }

    private static boolean eligible(ServerPlayer caster, ServerPlayer o) {
        return o != caster && o.isAlive() && !o.isSpectator() && !MEMBER_OF.containsKey(o.getUUID());
    }

    static void confirm(ServerPlayer p, GatherNet.Confirm msg) {
        Long until = MENU.remove(p.getUUID());
        if (until == null || p.serverLevel().getGameTime() > until) return;
        if (!p.isAlive() || p.isSpectator() || MEMBER_OF.containsKey(p.getUUID())) return;
        if (!Stone.SPACE.isHeldBy(p) || !StoneProgress.isUnlocked(p, Stone.SPACE, NODE)) return;
        List<ServerPlayer> chosen = new ArrayList<>();
        if (msg.all()) {
            for (ServerPlayer o : p.server.getPlayerList().getPlayers()) if (eligible(p, o)) chosen.add(o);
        } else {
            for (String id : msg.ids()) {
                try {
                    ServerPlayer o = p.server.getPlayerList().getPlayer(UUID.fromString(id));
                    if (o != null && eligible(p, o) && !chosen.contains(o)) chosen.add(o);
                } catch (IllegalArgumentException ignored) { }
            }
        }
        if (chosen.size() > MAX_MEMBERS) chosen = chosen.subList(0, MAX_MEMBERS);
        start(p, chosen);
    }

    // ------------------------------------------------------------------ start

    private static void start(ServerPlayer c, List<ServerPlayer> chosen) {
        ServerLevel l = c.serverLevel();
        int n = chosen.size();
        double ringR = Math.max(5, n * 1.2);
        double cy = Math.min(c.getY() + 30, l.getMaxBuildHeight() - 24);
        S s = new S(c, new Vec3(c.getX(), cy, c.getZ()), ringR);
        if (c.isPassenger()) c.stopRiding();
        remember(c, s.cm);
        saveTag(c, s.cm);
        SESSIONS.put(c.getUUID(), s);
        MEMBER_OF.put(c.getUUID(), s);
        double base = Math.toRadians(c.getYRot());
        var rnd = c.getRandom();
        for (int i = 0; i < n; i++) {
            ServerPlayer o = chosen.get(i);
            M m = new M(o.getUUID(), false);
            double a = base + Math.PI * 2 * i / n;
            m.slot = s.center.add(Math.cos(a) * ringR, 0, Math.sin(a) * ringR);
            Vec3 d = s.center.subtract(m.slot);
            m.slotYaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            m.at = 22 + i * 8 + rnd.nextInt(5);
            s.ms.add(m);
            MEMBER_OF.put(o.getUUID(), s);
        }
        abilities(c, true, true);
        c.setDeltaMovement(Vec3.ZERO);
        sendState(s, c, s.cm, 1);
        PacketDistributor.sendToAllPlayers(new GatherNet.Barrier(true, s.id, l.dimension().location().toString(),
                s.center.x, s.center.y, s.center.z, (float) (ringR + 5)));
        // show begins
        Vec3 o = s.origin;
        FxNet.runes(l, 40000 + s.id, o.add(0, 0.05, 0), 3.2F, GOLD, RISE + 30, 1.2F);
        FxNet.pillar(l, 41000 + s.id, o, 1.4F, 34F, WHITE, RISE + 16);
        FxNet.shockwave(l, o.add(0, 0.1, 0), 7F, GOLD, 26);
        FxNet.flare(l, o.add(0, 1, 0), 3.4F, WHITE, 18);
        play(l, o, SoundEvents.BEACON_ACTIVATE, 0.7F, 1.25F);
        play(l, o, SoundEvents.CONDUIT_ACTIVATE, 0.6F, 1.1F);
        play(l, o, SoundEvents.BELL_RESONATE, 0.6F, 0.8F);
        play(l, o, SoundEvents.ELYTRA_FLYING, 0.5F, 0.6F);
        chord(l, o, 0.5F, 0.5F, 0.63F, 0.75F, 1.0F);
        Fx.bar(c, Stone.SPACE, K + (n == 0 ? "started_alone" : "started"), n);
    }

    private static void remember(ServerPlayer p, M m) {
        m.dim = p.level().dimension();
        m.x = p.getX(); m.y = p.getY(); m.z = p.getZ(); m.yaw = p.getYRot(); m.pitch = p.getXRot();
        m.mayfly = p.getAbilities().mayfly; m.flying = p.getAbilities().flying; m.noGravity = p.isNoGravity();
        m.held = p.getInventory().selected;
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post e) {
        if (SESSIONS.isEmpty()) return;
        for (S s : new ArrayList<>(SESSIONS.values())) {
            try { tick(s); }
            catch (Exception ex) {
                InfinityStones.LOGGER.error("[extra] tick failed, ending", ex);
                hardEnd(s);
            }
        }
    }

    private static void tick(S s) {
        MinecraftServer srv = s.level.getServer();
        ServerPlayer c = srv.getPlayerList().getPlayer(s.caster);
        s.age++;
        if (s.phase == 0 && (c == null || !c.isAlive() || !Stone.SPACE.isHeldBy(c) || c.level() != s.level)) {
            beginEnd(s, c == null || !c.isAlive());
        }
        if (c != null && c.isAlive() && !s.casterGone) casterTick(s, c);
        for (Iterator<M> it = s.ms.iterator(); it.hasNext(); ) {
            M m = it.next();
            ServerPlayer p = srv.getPlayerList().getPlayer(m.id);
            if (p == null || !p.isAlive()) { forget(s, m, null); it.remove(); continue; }
            if (step(s, m, p)) { it.remove(); }
        }
        if (s.phase == 0) ambience(s);
        if (s.phase == 1 && s.ms.isEmpty()) {
            if (s.casterGone) { finish(s, c); return; }
            if (s.casterReturnAt < 0) s.casterReturnAt = s.age + AFTER_ALL;
            if (s.age >= s.casterReturnAt && s.age >= s.endAge + DISSOLVE) finish(s, c);
        }
    }

    private static void casterTick(S s, ServerPlayer c) {
        if (s.age <= RISE && s.phase == 0) {
            float t = Mth.clamp(s.age / (float) RISE, 0, 1);
            float ease = t * t * (3 - 2 * t);
            double y = Mth.lerp(ease, s.origin.y, s.center.y);
            abilities(c, true, true);
            c.connection.teleport(s.origin.x, y, s.origin.z, 0, 0, RelativeMovement.ROTATION);
            c.setDeltaMovement(Vec3.ZERO);
            c.fallDistance = 0;
            ServerLevel l = s.level;
            Vec3 at = new Vec3(s.origin.x, y, s.origin.z);
            double a = s.age * 0.55;
            for (int k = 0; k < 2; k++) {
                double aa = a + Math.PI * k;
                Fx.at(l, ParticleTypes.END_ROD, at.add(Math.cos(aa) * 0.9, 0.1 + (s.age % 10) * 0.18, Math.sin(aa) * 0.9), 1, 0.02, 0.0);
            }
            Fx.at(l, Fx.dust(GOLD, 1.4F), at.add(0, 1, 0), 3, 0.5, 0.0);
            if (s.age % 6 == 0) play(l, at, SoundEvents.AMETHYST_BLOCK_CHIME, 0.45F, 0.6F + t * 1.3F);
            if (s.age == RISE) formationFx(s);
            return;
        }
        c.fallDistance = 0;
        if (!c.getAbilities().mayfly || !c.getAbilities().flying) abilities(c, true, true);
        if (s.casterGone) return;
        Vec3 p = c.position();
        double dx = p.x - s.center.x, dz = p.z - s.center.z, r = Math.sqrt(dx * dx + dz * dz), max = s.sceneR - 3;
        double ny = Mth.clamp(p.y, s.center.y, s.center.y + 22);
        if (r > max || ny != p.y) {
            double k = r > max ? max / r : 1;
            c.connection.teleport(s.center.x + dx * k, ny, s.center.z + dz * k, 0, 0, RelativeMovement.ROTATION);
        }
    }

    /** The ring closes: everybody is (or is about to be) in place and the water wall rises. */
    private static void formationFx(S s) {
        ServerLevel l = s.level;
        Vec3 c = s.center;
        FxNet.shockwave(l, c.add(0, 0.1, 0), (float) s.ringR + 2, WHITE, 24);
        FxNet.flare(l, c.add(0, 1, 0), 4F, GOLD, 20);
        play(l, c, SoundEvents.BEACON_POWER_SELECT, 0.6F, 1.0F);
        play(l, c, SoundEvents.BELL_BLOCK, 0.7F, 1.0F);
        play(l, c, SoundEvents.PLAYER_SPLASH, 0.7F, 0.7F);
        play(l, c, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 0.7F, 0.8F);
        priv(s, SoundEvents.WATER_AMBIENT, 0.6F, 0.9F);
        privChord(s, 0.45F, 0.5F, 0.667F, 0.84F, 1.0F);
    }

    private static void ambience(S s) {
        ServerLevel l = s.level;
        var rnd = l.random;
        if (s.age % 5 == 0) {
            double R = s.ringR + 5;
            for (int i = 0; i < 10; i++) {
                double u = rnd.nextDouble() * 2 - 1, t = rnd.nextDouble() * Math.PI * 2, q = Math.sqrt(1 - u * u);
                Fx.far(l, ParticleTypes.END_ROD, s.center.add(q * Math.cos(t) * R, u * R, q * Math.sin(t) * R), 1, 0.05, 0.005, 96);
            }
        }
        if (s.age > RISE && s.age % 28 == 0) {
            float[] pent = {0.5F, 0.561F, 0.667F, 0.749F, 0.841F, 1.0F, 1.122F};
            priv(s, rnd.nextBoolean() ? SoundEvents.NOTE_BLOCK_CHIME : SoundEvents.NOTE_BLOCK_BELL, 0.3F, pent[rnd.nextInt(pent.length)]);
        }
        if (s.age > RISE && s.age % 90 == 0) {
            priv(s, SoundEvents.WATER_AMBIENT, 0.35F, 0.8F + rnd.nextFloat() * 0.3F);
            priv(s, SoundEvents.AMBIENT_CAVE.value(), 0.15F, 1.6F);
        }
        if (s.age % 100 == 1) {
            sendAll(s, 1);
            for (M m : participants(s)) {
                ServerPlayer p = player(s, m.id);
                if (p != null) nightVision(p, m);
            }
        }
    }

    // ------------------------------------------------------------------ members

    /** @return true when the member is finished and must be removed from the session. */
    private static boolean step(S s, M m, ServerPlayer p) {
        int t = s.age - m.at;
        if (s.phase == 0) {
            if (m.state == WAIT && s.age >= m.at) {
                m.state = PULL;
                handFx(s, m, p, false, t);
            } else if (m.state == PULL) {
                handBeats(s, m, p, false, t);
                if (t >= T_TP) { pullIn(s, m, p); }
            }
        } else {
            if (m.state == RWAIT && s.age >= m.at) { m.state = RPULL; handFx(s, m, p, true, 0); }
            else if (m.state == RPULL) {
                handBeats(s, m, p, true, t);
                if (t >= T_TP) { returnHome(s, m, p, true); return true; }
            }
        }
        if (m.state >= IN) pin(s, m, p);
        return false;
    }

    private static Vec3 grabPoint(M m, ServerPlayer p) { return m.state >= IN ? m.slot.add(0, 1.1, 0) : p.position().add(0, 1.1, 0); }

    /** Portal opens in front of the player (pulling in) or behind him on the outer side of the ring (sending back). */
    private static void handFx(S s, M m, ServerPlayer p, boolean back, int t) {
        Vec3 target, portal;
        if (back) {
            Vec3 out = m.slot.subtract(s.center).multiply(1, 0, 1).normalize();
            target = m.slot.add(0, 1.1, 0);
            portal = target.add(out.scale(2.9));
        } else {
            Vec3 fwd = Vec3.directionFromRotation(0, p.getYRot());
            target = p.position().add(0, 1.1, 0);
            portal = target.add(fwd.scale(2.9));
        }
        Vec3 n = target.subtract(portal).normalize();
        GatherNet.Hand h = new GatherNet.Hand(p.getId(), portal.x, portal.y, portal.z, n.x, n.y, n.z, target.x, target.y, target.z, HAND_LIFE, back);
        ServerLevel l = (ServerLevel) p.level();
        PacketDistributor.sendToPlayersNear(l, null, portal.x, portal.y, portal.z, 110, h);
        float pitch = back ? 0.75F : 1.0F;
        play(l, portal, SoundEvents.PORTAL_TRIGGER, 0.35F, 1.2F * pitch);
        play(l, portal, SoundEvents.END_PORTAL_SPAWN, 0.25F, 1.5F * pitch);
        play(l, portal, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.5F, 1.1F * pitch);
        play(l, portal, SoundEvents.BEACON_ACTIVATE, 0.4F, 1.6F * pitch);
        Fx.at(l, ParticleTypes.END_ROD, portal, 24, 0.7, 0.06);
        Fx.at(l, ParticleTypes.REVERSE_PORTAL, portal, 30, 0.6, 0.12);
        Priv.sound(p, SoundEvents.PORTAL_AMBIENT, p.position(), 0.5F, 1.4F * pitch);
    }

    private static void handBeats(S s, M m, ServerPlayer p, boolean back, int t) {
        ServerLevel l = (ServerLevel) p.level();
        Vec3 target = grabPoint(m, p);
        Vec3 dir = back ? m.slot.subtract(s.center).multiply(1, 0, 1).normalize() : Vec3.directionFromRotation(0, p.getYRot());
        Vec3 portal = target.add(dir.scale(2.9));
        float pk = back ? 0.8F : 1.0F;
        switch (t) {
            case T_HAND -> {
                play(l, portal, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 1.3F * pk);
                play(l, portal, SoundEvents.NOTE_BLOCK_FLUTE, 0.5F, 1.0F * pk);
                chord(l, portal, 0.45F, 0.667F * pk, 0.84F * pk, 1.0F * pk);
                Fx.at(l, Fx.dust(GOLD, 1.5F), portal.lerp(target, 0.25), 18, 0.35, 0.0);
            }
            case T_REACH -> {
                play(l, target, SoundEvents.CONDUIT_AMBIENT_SHORT, 0.55F, 1.3F * pk);
                play(l, target, SoundEvents.ENCHANTMENT_TABLE_USE, 0.6F, 1.7F * pk);
                Fx.at(l, ParticleTypes.END_ROD, portal.lerp(target, 0.6), 14, 0.3, 0.05);
            }
            case T_GRAB -> {
                play(l, target, SoundEvents.CONDUIT_ACTIVATE, 0.5F, 1.5F * pk);
                play(l, target, SoundEvents.BELL_RESONATE, 0.55F, 1.4F * pk);
                play(l, target, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.6F, 1.2F * pk);
                Fx.at(l, ParticleTypes.FLASH, target, 1, 0, 0);
                Fx.at(l, Fx.dust(WHITE, 1.8F), target, 20, 0.4, 0.02);
            }
            case T_FLASH -> {
                if (!back) FxNet.flash(p, WHITE, 1.0F, 26);
                else FxNet.flash(p, WHITE, 0.7F, 18);
                priv(p, SoundEvents.BEACON_POWER_SELECT, 0.5F, 1.8F);
                priv(p, SoundEvents.FIREWORK_ROCKET_TWINKLE, 0.5F, 0.8F);
            }
            default -> { }
        }
    }

    private static void pullIn(S s, M m, ServerPlayer p) {
        remember(p, m);
        if (p.isPassenger()) p.stopRiding();
        saveTag(p, m);
        ServerLevel from = (ServerLevel) p.level();
        Vec3 old = p.position();
        FxNet.flare(from, old.add(0, 1, 0), 2.6F, WHITE, 10);
        FxNet.shockwave(from, old.add(0, 0.1, 0), 3F, GOLD, 14);
        play(from, old, SoundEvents.ENDERMAN_TELEPORT, 0.5F, 1.4F);
        play(from, old, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5F, 1.9F);
        tp(p, s.level, m.slot.x, m.slot.y, m.slot.z, m.slotYaw, 8F);
        abilities(p, true, true);
        p.setNoGravity(true);
        p.fallDistance = 0;
        p.setDeltaMovement(Vec3.ZERO);
        m.state = IN;
        m.held = p.getInventory().selected;
        TRAPPED.put(p.getUUID(), s);
        nightVision(p, m);
        sendAll(s, 1);
        ServerLevel l = s.level;
        FxNet.flare(l, m.slot.add(0, 1, 0), 2.4F, WHITE, 12);
        FxNet.shockwave(l, m.slot.add(0, 0.1, 0), 3.5F, GOLD, 16);
        FxNet.spiral(l, m.slot, 0.8F, 2.4F, GOLD, 26, p.getId());
        play(l, m.slot, SoundEvents.BEACON_POWER_SELECT, 0.5F, 1.3F);
        play(l, m.slot, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, 0.4F, 1.4F);
        priv(p, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 1.0F);
        priv(p, SoundEvents.PLAYER_LEVELUP, 0.3F, 1.6F);
        p.displayClientMessage(Component.translatable(K + "arrived").withStyle(ChatFormatting.GOLD), true);
        boolean all = true;
        for (M o : s.ms) if (o.state < IN) all = false;
        if (all) {
            play(l, s.center, SoundEvents.BELL_BLOCK, 0.5F, 1.5F);
            privChord(s, 0.35F, 0.667F, 0.84F, 1.0F, 1.26F);
        }
    }

    private static void pin(S s, M m, ServerPlayer p) {
        if (p.level() != s.level || p.distanceToSqr(m.slot.x, m.slot.y, m.slot.z) > 0.09) {
            if (p.level() != s.level) tp(p, s.level, m.slot.x, m.slot.y, m.slot.z, p.getYRot(), p.getXRot());
            else { internal = true; try { p.connection.teleport(m.slot.x, m.slot.y, m.slot.z, 0, 0, RelativeMovement.ROTATION); } finally { internal = false; } }
        }
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
        if (!p.isNoGravity()) p.setNoGravity(true);
        if (!p.getAbilities().mayfly || !p.getAbilities().flying) abilities(p, true, true);
        if (p.isUsingItem()) p.stopUsingItem();
        if (p.containerMenu != p.inventoryMenu) p.closeContainer();
        if (p.isPassenger()) p.stopRiding();
        if (p.isFallFlying()) p.stopFallFlying();
        p.setSprinting(false);
        if (p.getInventory().selected != m.held) {
            p.getInventory().selected = m.held;
            p.connection.send(new ClientboundSetCarriedItemPacket(m.held));
        }
    }

    /** Puts a player back where he was; alive returners get the buffs. */
    private static void returnHome(S s, M m, ServerPlayer p, boolean buffs) {
        ServerLevel at = s.level.getServer().getLevel(m.dim);
        if (at == null) { at = s.level.getServer().overworld(); var sp = at.getSharedSpawnPos(); m.x = sp.getX() + 0.5; m.y = sp.getY(); m.z = sp.getZ() + 0.5; }
        ServerLevel from = (ServerLevel) p.level();
        Vec3 here = p.position();
        FxNet.flare(from, here.add(0, 1, 0), 2.6F, WHITE, 10);
        play(from, here, SoundEvents.ENDERMAN_TELEPORT, 0.5F, 1.2F);
        tp(p, at, m.x, m.y, m.z, m.yaw, m.pitch);
        restoreFlags(p, m);
        p.setDeltaMovement(Vec3.ZERO);
        p.fallDistance = 0;
        forget(s, m, p);
        Vec3 now = new Vec3(m.x, m.y, m.z);
        FxNet.flare(at, now.add(0, 1, 0), 2.8F, WHITE, 12);
        FxNet.shockwave(at, now.add(0, 0.1, 0), 3.2F, GOLD, 16);
        FxNet.spiral(at, now, 0.8F, 2.4F, GOLD, 22, p.getId());
        play(at, now, SoundEvents.BEACON_POWER_SELECT, 0.5F, 1.6F);
        play(at, now, SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, 1.4F);
        Priv.sound(p, SoundEvents.PLAYER_LEVELUP, now, 0.35F, 1.2F);
        if (buffs && p.isAlive()) {
            p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 6000, 2));
            p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 6000, 1));
            p.displayClientMessage(Component.translatable(K + "returned").withStyle(ChatFormatting.GOLD), true);
        }
    }

    /** Releases the member from every map and tells his client to drop the scenery. The saved position stays unless p != null. */
    private static void forget(S s, M m, ServerPlayer p) {
        MEMBER_OF.remove(m.id);
        TRAPPED.remove(m.id);
        if (p != null) {
            clearTag(p);
            dropNightVision(p, m);
            PacketDistributor.sendToPlayer(p, new GatherNet.State(0, false, false, 0, 0, 0, 0, 0, 0, 0, new int[0]));
        }
    }

    // ------------------------------------------------------------------ end

    private static void beginEnd(S s, boolean casterGone) {
        if (s.phase != 0) return;
        s.phase = 1;
        s.endAge = s.age;
        s.casterGone = casterGone;
        var rnd = s.level.random;
        for (Iterator<M> it = s.ms.iterator(); it.hasNext(); ) {
            M m = it.next();
            if (m.state >= IN) { m.state = RWAIT; m.at = s.age + DISSOLVE + rnd.nextInt(70); }
            else { forget(s, m, null); it.remove(); }
        }
        PacketDistributor.sendToAllPlayers(new GatherNet.Barrier(false, s.id, s.level.dimension().location().toString(), 0, 0, 0, 0));
        sendAll(s, 2);
        ServerLevel l = s.level;
        Vec3 c = s.center;
        FxNet.shockwave(l, c, (float) s.ringR + 8, GOLD, 34);
        FxNet.flare(l, c.add(0, 1, 0), 5F, WHITE, 22);
        Fx.burstRing(l, c.add(0, 1, 0), 60, ParticleTypes.CHERRY_LEAVES, 0.6);
        Fx.burstRing(l, c.add(0, 2, 0), 40, ParticleTypes.END_ROD, 0.35);
        Fx.far(l, ParticleTypes.CHERRY_LEAVES, c.add(0, 2, 0), 80, s.ringR * 0.6, 0.15, 96);
        play(l, c, SoundEvents.BEACON_DEACTIVATE, 0.7F, 1.0F);
        play(l, c, SoundEvents.CONDUIT_DEACTIVATE, 0.6F, 0.9F);
        play(l, c, SoundEvents.BELL_RESONATE, 0.5F, 0.6F);
        priv(s, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 0.7F);
        priv(s, SoundEvents.ELYTRA_FLYING, 0.4F, 1.6F);
        float[] fall = {1.5F, 1.26F, 1.0F, 0.84F, 0.667F};
        for (int i = 0; i < fall.length; i++) {
            final float pitch = fall[i];
            final int delay = i * 4;
            com.infinitystones.tensura.skill.ability.extra.Tasks.add(age -> {
                if (age < delay) return true;
                priv(s, SoundEvents.NOTE_BLOCK_CHIME, 0.35F, pitch);
                return false;
            });
        }
        ServerPlayer c0 = player(s, s.caster);
        if (c0 != null) Fx.bar(c0, Stone.SPACE, K + "ended");
    }

    /** Last step: the caster snaps back to where he activated the power. */
    private static void finish(S s, ServerPlayer c) {
        SESSIONS.remove(s.caster);
        MEMBER_OF.remove(s.caster);
        TRAPPED.remove(s.caster);
        if (c != null && c.isAlive() && !s.casterDead) {
            ServerLevel l = s.level.getServer().getLevel(s.cm.dim);
            if (l == null) l = s.level;
            Vec3 here = c.position();
            FxNet.flare(s.level, here.add(0, 1, 0), 2.2F, WHITE, 8);
            tp(c, l, s.cm.x, s.cm.y, s.cm.z, s.cm.yaw, s.cm.pitch);
            restoreFlags(c, s.cm);
            c.setDeltaMovement(Vec3.ZERO);
            c.fallDistance = 0;
            Vec3 now = new Vec3(s.cm.x, s.cm.y, s.cm.z);
            FxNet.flare(l, now.add(0, 1, 0), 2.4F, GOLD, 10);
            play(l, now, SoundEvents.ENDERMAN_TELEPORT, 0.55F, 0.9F);
            play(l, now, SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, 0.8F);
        }
        if (c != null) {
            clearTag(c);
            dropNightVision(c, s.cm);
            PacketDistributor.sendToPlayer(c, new GatherNet.State(0, false, false, 0, 0, 0, 0, 0, 0, 0, new int[0]));
        }
    }

    /** Immediate teardown (server stop, internal error): everybody is put back right now, no show. */
    private static void hardEnd(S s) {
        MinecraftServer srv = s.level.getServer();
        if (s.phase == 0) PacketDistributor.sendToAllPlayers(new GatherNet.Barrier(false, s.id, "", 0, 0, 0, 0));
        s.phase = 1;
        for (M m : new ArrayList<>(s.ms)) {
            ServerPlayer p = srv.getPlayerList().getPlayer(m.id);
            if (p != null && m.state >= IN) returnHome(s, m, p, false);
            else forget(s, m, p);
        }
        s.ms.clear();
        finish(s, srv.getPlayerList().getPlayer(s.caster));
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) {
        for (S s : new ArrayList<>(SESSIONS.values())) hardEnd(s);
        SESSIONS.clear(); MEMBER_OF.clear(); TRAPPED.clear(); MENU.clear(); PRAYED.clear(); NV_OWNED.clear();
    }

    // ------------------------------------------------------------------ persistence (logout / death / crash)

    private static void saveTag(ServerPlayer p, M m) {
        CompoundTag t = new CompoundTag();
        t.putString("dim", m.dim.location().toString());
        t.putDouble("x", m.x); t.putDouble("y", m.y); t.putDouble("z", m.z);
        t.putFloat("yaw", m.yaw); t.putFloat("pitch", m.pitch);
        t.putBoolean("mayfly", m.mayfly); t.putBoolean("flying", m.flying); t.putBoolean("ng", m.noGravity);
        Rules.data(p).put(TAG, t);
    }

    private static void clearTag(ServerPlayer p) { Rules.data(p).remove(TAG); }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        CompoundTag root = Rules.data(p);
        if (root.contains(TAG, 10)) {
            CompoundTag t = root.getCompound(TAG);
            ResourceLocation id = ResourceLocation.tryParse(t.getString("dim"));
            ServerLevel at = id == null ? null : p.server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
            double x = t.getDouble("x"), y = t.getDouble("y"), z = t.getDouble("z");
            if (at == null) { at = p.server.overworld(); var sp = at.getSharedSpawnPos(); x = sp.getX() + 0.5; y = sp.getY(); z = sp.getZ() + 0.5; }
            tp(p, at, x, y, z, t.getFloat("yaw"), t.getFloat("pitch"));
            boolean mayfly = t.getBoolean("mayfly") || p.isCreative() || p.isSpectator();
            p.getAbilities().mayfly = mayfly;
            p.getAbilities().flying = t.getBoolean("flying") && mayfly;
            p.onUpdateAbilities();
            p.setNoGravity(t.getBoolean("ng"));
            p.fallDistance = 0;
            p.setDeltaMovement(Vec3.ZERO);
            p.removeEffect(MobEffects.NIGHT_VISION);
            root.remove(TAG);
            NV_OWNED.remove(p.getUUID());
        }
        for (S s : SESSIONS.values()) if (s.phase == 0)
            PacketDistributor.sendToPlayer(p, new GatherNet.Barrier(true, s.id, s.level.dimension().location().toString(),
                    s.center.x, s.center.y, s.center.z, (float) (s.ringR + 5)));
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        UUID id = p.getUUID();
        MENU.remove(id); PRAYED.remove(id); NV_OWNED.remove(id);
        S own = SESSIONS.get(id);
        if (own != null) { beginEnd(own, true); return; }
        S s = MEMBER_OF.get(id);
        if (s != null) {
            s.ms.removeIf(m -> m.id.equals(id));
            MEMBER_OF.remove(id);
            TRAPPED.remove(id);
            sendAll(s, s.phase == 0 ? 1 : 2);
        }
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        UUID id = p.getUUID();
        S own = SESSIONS.get(id);
        if (own != null) { own.casterDead = true; clearTag(p); beginEnd(own, true); return; }
        S s = MEMBER_OF.get(id);
        if (s == null) return;
        s.ms.removeIf(m -> m.id.equals(id));
        MEMBER_OF.remove(id);
        TRAPPED.remove(id);
        clearTag(p);
        NV_OWNED.remove(id);
        PacketDistributor.sendToPlayer(p, new GatherNet.State(0, false, false, 0, 0, 0, 0, 0, 0, 0, new int[0]));
        sendAll(s, s.phase == 0 ? 1 : 2);
    }

    // ------------------------------------------------------------------ locks (server side)

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void rightBlock(PlayerInteractEvent.RightClickBlock e) { if (isTrapped(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void rightItem(PlayerInteractEvent.RightClickItem e) { if (isTrapped(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void leftBlock(PlayerInteractEvent.LeftClickBlock e) { if (isTrapped(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void entityInteract(PlayerInteractEvent.EntityInteract e) { if (isTrapped(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void entityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific e) { if (isTrapped(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void attack(AttackEntityEvent e) { if (isTrapped(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void breakBlock(BlockEvent.BreakEvent e) { if (isTrapped(e.getPlayer())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void toss(ItemTossEvent e) { if (isTrapped(e.getPlayer())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void useItem(LivingEntityUseItemEvent.Start e) { if (isTrapped(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void fall(LivingFallEvent e) { if (inSession(e.getEntity())) e.setCanceled(true); }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void knockback(LivingKnockBackEvent e) { if (isTrapped(e.getEntity())) e.setCanceled(true); }

    /** Generic teleport guard (pearls, chorus fruit, commands...): trapped players stay, unless the gathering itself moves them. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void teleport(EntityTeleportEvent e) { if (!internal && isTrapped(e.getEntity())) e.setCanceled(true); }

    // ------------------------------------------------------------------ prayer

    static void pray(ServerPlayer p) {
        if (!p.isAlive() || p.isSpectator() || MEMBER_OF.containsKey(p.getUUID())) return;
        for (Stone st : Stone.values()) if (st.isHeldBy(p)) return;
        long now = p.serverLevel().getGameTime();
        Long last = PRAYED.get(p.getUUID());
        if (last != null && now - last < 100) return;
        PRAYED.put(p.getUUID(), now);
        ServerLevel l = p.serverLevel();
        Vec3 at = p.position();
        Priv.sound(p, SoundEvents.AMETHYST_BLOCK_CHIME, at, 0.5F, 1.4F);
        Priv.sound(p, SoundEvents.NOTE_BLOCK_CHIME, at, 0.4F, 1.26F);
        Priv.sound(p, SoundEvents.NOTE_BLOCK_BELL, at, 0.3F, 1.0F);
        p.displayClientMessage(Component.translatable(K + "prayer.sent").withStyle(ChatFormatting.GOLD), true);
        FxNet.flare(l, at.add(0, 1.0, 0), 1.3F, GOLD, 16);
        Fx.at(l, ParticleTypes.END_ROD, at.add(0, 0.6, 0), 10, 0.25, 0.04);
        Fx.at(l, ParticleTypes.GLOW, at.add(0, 1.2, 0), 6, 0.3, 0.02);
        for (ServerPlayer h : p.server.getPlayerList().getPlayers()) {
            if (h == p || !Stone.SPACE.isHeldBy(h)) continue;
            h.sendSystemMessage(Component.translatable(K + "prayer.received", p.getDisplayName()).withStyle(ChatFormatting.GOLD));
            h.displayClientMessage(Component.translatable(K + "prayer.received", p.getDisplayName()).withStyle(ChatFormatting.GOLD), true);
            Vec3 ha = h.position();
            Priv.sound(h, SoundEvents.BELL_RESONATE, ha, 0.5F, 1.1F);
            Priv.sound(h, SoundEvents.AMETHYST_BLOCK_RESONATE, ha, 0.5F, 1.3F);
            Priv.sound(h, SoundEvents.NOTE_BLOCK_CHIME, ha, 0.4F, 1.5F);
            ((ServerLevel) h.level()).sendParticles(ParticleTypes.END_ROD, ha.x, ha.y + 2.1, ha.z, 8, 0.25, 0.15, 0.25, 0.02);
        }
    }

    // ------------------------------------------------------------------ helpers

    private static ServerPlayer player(S s, UUID id) { return s.level.getServer().getPlayerList().getPlayer(id); }

    private static List<M> participants(S s) {
        List<M> out = new ArrayList<>();
        out.add(s.cm);
        for (M m : s.ms) if (m.state >= IN) out.add(m);
        return out;
    }

    private static int[] ids(S s) {
        List<Integer> out = new ArrayList<>();
        for (M m : participants(s)) { ServerPlayer p = player(s, m.id); if (p != null) out.add(p.getId()); }
        int[] a = new int[out.size()];
        for (int i = 0; i < a.length; i++) a[i] = out.get(i);
        return a;
    }

    private static void sendState(S s, ServerPlayer p, M m, int phase) {
        PacketDistributor.sendToPlayer(p, new GatherNet.State(phase, m.caster, !m.caster, s.center.x, s.center.y, s.center.z,
                (float) s.ringR, (float) s.sceneR, s.seed, DISSOLVE, ids(s)));
    }

    private static void sendAll(S s, int phase) {
        for (M m : participants(s)) {
            ServerPlayer p = player(s, m.id);
            if (p != null) sendState(s, p, m, phase);
        }
    }

    private static void abilities(ServerPlayer p, boolean mayfly, boolean flying) {
        var a = p.getAbilities();
        boolean change = a.mayfly != mayfly || a.flying != flying;
        a.mayfly = mayfly;
        a.flying = flying;
        if (change) p.onUpdateAbilities();
    }

    private static void restoreFlags(ServerPlayer p, M m) {
        boolean mayfly = m.mayfly || p.isCreative() || p.isSpectator();
        p.getAbilities().mayfly = mayfly;
        p.getAbilities().flying = m.flying && mayfly;
        p.onUpdateAbilities();
        p.setNoGravity(m.noGravity);
    }

    private static void nightVision(ServerPlayer p, M m) {
        MobEffectInstance cur = p.getEffect(MobEffects.NIGHT_VISION);
        if (cur != null && !NV_OWNED.contains(p.getUUID())) return;
        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, true, false, false));
        NV_OWNED.add(p.getUUID());
        m.nv = true;
    }

    private static void dropNightVision(ServerPlayer p, M m) {
        if (NV_OWNED.remove(p.getUUID())) p.removeEffect(MobEffects.NIGHT_VISION);
    }

    /** Moves a player on behalf of the gathering (never blocked by the teleport guard). */
    private static void tp(ServerPlayer p, ServerLevel to, double x, double y, double z, float yaw, float pitch) {
        internal = true;
        try {
            if (p.level() == to) p.connection.teleport(x, y, z, yaw, pitch);
            else p.teleportTo(to, x, y, z, Set.of(), yaw, pitch);
        } finally { internal = false; }
    }

    private static void play(ServerLevel l, Vec3 at, SoundEvent e, float vol, float pitch) {
        l.playSound(null, at.x, at.y, at.z, e, SoundSource.PLAYERS, Math.min(vol, 0.7F), pitch);
    }

    private static void play(ServerLevel l, Vec3 at, Holder<SoundEvent> e, float vol, float pitch) { play(l, at, e.value(), vol, pitch); }

    private static void chord(ServerLevel l, Vec3 at, float vol, float... pitches) {
        for (float pt : pitches) {
            play(l, at, SoundEvents.NOTE_BLOCK_CHIME, vol, pt);
            play(l, at, SoundEvents.NOTE_BLOCK_BELL, vol * 0.6F, pt * 0.5F);
        }
    }

    /** Private to one player (heard at full volume wherever he is). */
    private static void priv(ServerPlayer p, SoundEvent e, float vol, float pitch) { Priv.sound(p, e, p.position(), vol, pitch); }
    private static void priv(ServerPlayer p, Holder<SoundEvent> e, float vol, float pitch) { priv(p, e.value(), vol, pitch); }

    /** Private to every participant. */
    private static void priv(S s, SoundEvent e, float vol, float pitch) {
        for (M m : participants(s)) { ServerPlayer p = player(s, m.id); if (p != null) priv(p, e, vol, pitch); }
    }
    private static void priv(S s, Holder<SoundEvent> e, float vol, float pitch) { priv(s, e.value(), vol, pitch); }

    private static void privChord(S s, float vol, float... pitches) {
        for (float pt : pitches) {
            priv(s, SoundEvents.NOTE_BLOCK_CHIME, vol, pt);
            priv(s, SoundEvents.NOTE_BLOCK_BELL, vol * 0.6F, pt * 0.5F);
        }
    }

    private SpiritualGathering() {}
}
