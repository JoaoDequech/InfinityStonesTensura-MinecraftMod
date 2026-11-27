package com.infinitystones.tensura.skill.ability.extra.fear;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneProgress;
import com.infinitystones.tensura.skill.StoneTier;
import com.infinitystones.tensura.skill.ability.extra.Executions;
import com.infinitystones.tensura.skill.ability.extra.Priv;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCameraPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The hidden "Fear Dimension" mode of Nightmare. A caster with the secret tier sends players into their own arena of the
 * fear realm and directs the nightmare from the control room: switch scenes, spawn creatures (SCARE or KILLER), trigger
 * apparitions and effects, change the atmosphere, watch through their eyes or from an orbit, steal, heal, freeze, pull back
 * or kill. Everything is validated here; the exact origin of everybody is persisted ({@link FearData}).
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class FearSessions {
    private static final String NODE = "nightmare";
    static final int POV = 1, ORBIT = 2;

    private static final class Watch {
        final UUID victim; final int mode; int age;
        Watch(UUID victim, int mode) { this.victim = victim; this.mode = mode; }
    }

    static final Map<UUID, FearLive> LIVE = new HashMap<>();
    private static final Map<UUID, Watch> WATCH = new HashMap<>();
    private static final Set<UUID> PANEL = new HashSet<>();
    private static final List<UUID> KILLS = new ArrayList<>();
    private static MinecraftServer server;

    record WatchView(UUID victim, int mode) {}

    static @Nullable WatchView watchOf(UUID who) { Watch w = WATCH.get(who); return w == null ? null : new WatchView(w.victim, w.mode); }

    static boolean isWatching(UUID who) { return WATCH.containsKey(who); }

    static void resumeWatch(UUID who) { Watch w = WATCH.get(who); if (w != null) w.age = 0; }

    // ------------------------------------------------------------------ permissions / api for the other parts

    /** The secret tier of Mind, the Nightmare power unlocked and both stones carried. */
    public static boolean canCast(ServerPlayer p) {
        return StoneTier.has(p, Stone.MIND) && Stone.MIND.isHeldBy(p) && Stone.SOUL.isHeldBy(p) && StoneProgress.isUnlocked(p, Stone.MIND, NODE);
    }

    /** True while this player is somebody's victim. */
    public static boolean isVictim(Player p) { return LIVE.containsKey(p.getUUID()); }

    /** Where a victim came from (real world position, for the "home" and "claims" scenes). Null when not a victim. */
    public static @Nullable FearData.Origin originOf(MinecraftServer s, UUID victim) {
        FearData.Victim v = FearData.get(s).victims.get(victim);
        return v == null ? null : v.origin();
    }

    /** Called by a creature that has finished its victim in KILLER mode: the real, silent kill at the origin. */
    public static void finalKill(ServerPlayer victim) {
        if (LIVE.containsKey(victim.getUUID()) && !KILLS.contains(victim.getUUID())) KILLS.add(victim.getUUID());
    }

    private static ServerPlayer player(MinecraftServer s, UUID id) { return s.getPlayerList().getPlayer(id); }

    private static @Nullable FearLive own(ServerPlayer c, UUID victim) {
        FearLive l = LIVE.get(victim);
        return l != null && l.caster.equals(c.getUUID()) ? l : null;
    }

    // ------------------------------------------------------------------ control room

    /** True while the caster is inside as his own victim: every stone power is locked except leaving. */
    public static boolean selfLocked(Player p) {
        FearLive l = LIVE.get(p.getUUID());
        return l != null && l.self;
    }

    /** The single unlocked power while inside: "Sair da Dimensão do Medo". */
    public static boolean selfExit(ServerPlayer p) {
        FearLive l = LIVE.get(p.getUUID());
        if (l == null || !l.self) return false;
        release(p.server, p.getUUID(), true);
        return true;
    }

    static void request(ServerPlayer p, boolean open) {
        if (!open) { PANEL.remove(p.getUUID()); return; }
        if (!canCast(p)) return;
        if (selfLocked(p)) { sendPanel(p); return; }
        PANEL.add(p.getUUID());
        FearPresets.send(p);
        sendPanel(p);
    }

    private static void sendPanel(ServerPlayer c) {
        MinecraftServer s = c.server;
        List<FearNet.VInfo> vs = new ArrayList<>();
        Watch w = WATCH.get(c.getUUID());
        int fog = 50, dark = 85, sky = 0, weather = 0;
        boolean auto = true;
        boolean first = true;
        List<FearNet.Hl> hl = new ArrayList<>();
        for (FearLive l : LIVE.values()) {
            if (!l.caster.equals(c.getUUID())) continue;
            ServerPlayer v = player(s, l.victim);
            if (v == null) continue;
            int flags = (l.flash ? 1 : 0) | (l.frozen ? 2 : 0) | (l.hunt ? 4 : 0) | (w != null && w.victim.equals(l.victim) ? 8 : 0);
            vs.add(new FearNet.VInfo(l.victim, v.getGameProfile().getName(), v.getHealth(), (int) l.fear, l.scene, l.total / 20, flags, (int) l.battery, l.hud, l.slot));
            for (int i = 0; i < l.rec.list.size(); i++) hl.add(new FearNet.Hl(l.victim, i, l.rec.list.get(i).label, Math.max(0, (l.total - l.rec.list.get(i).at) / 20)));
            if (first || (w != null && w.victim.equals(l.victim))) { fog = l.fog; dark = l.dark; sky = l.sky; weather = l.weather; auto = l.auto; first = false; }
        }
        List<FearNet.Cand> cs = new ArrayList<>();
        for (ServerPlayer o : s.getPlayerList().getPlayers())
            if (o != c && !LIVE.containsKey(o.getUUID()) && o.level().dimension() != FearRealm.DIM) cs.add(new FearNet.Cand(o.getUUID(), o.getGameProfile().getName()));
        FearApi.Scenes sc = FearApi.scenes();
        FearApi.Creatures cr = FearApi.creatures();
        List<FearNet.Cand> au = new ArrayList<>();
        for (Map.Entry<UUID, Watch> en : WATCH.entrySet()) {
            if (en.getValue().mode != 3 || !FearExtras.isAudience(en.getKey(), c.getUUID())) continue;
            ServerPlayer a = player(s, en.getKey());
            if (a != null) au.add(new FearNet.Cand(a.getUUID(), a.getGameProfile().getName()));
        }
        PacketDistributor.sendToPlayer(c, new FearNet.Panel(vs, cs, auto, sc == null ? List.of() : sc.ids(), cr == null ? List.of() : cr.ids(),
                cr == null ? List.of() : cr.apparitionKinds(), fog, dark, sky, weather, hl.size() > 24 ? hl.subList(hl.size() - 24, hl.size()) : hl, au));
    }

    static void cmd(ServerPlayer c, FearNet.Cmd m) {
        if (m.action() == FearNet.STOP_WATCH) { stopWatch(c); return; }
        if (m.action() == FearNet.ACCEPT || m.action() == FearNet.DECLINE) { FearExtras.answer(c, m.action() == FearNet.ACCEPT); return; }
        if (!canCast(c) || m.arg().length() > 60) return;
        FearLive mine = LIVE.get(c.getUUID());
        if (mine != null && mine.self) {
            boolean ok = c.getUUID().equals(m.target()) && switch (m.action()) {
                case FearNet.AUTO, FearNet.FX, FearNet.SCENE, FearNet.MODE, FearNet.SPAWN, FearNet.DESPAWN, FearNet.ENV, FearNet.PULL, FearNet.APPARITION -> true;
                default -> false;
            };
            if (!ok) return;
        }
        if (m.action() == FearNet.PRESET_DELETE) { FearPresets.delete(c, m.arg()); return; }
        if (m.action() == FearNet.SELF_ENTER) { FearPresets.enter(c, m.arg()); return; }
        MinecraftServer s = c.server;
        ServerPlayer v = player(s, m.target());
        FearLive live = own(c, m.target());
        FearApi.Creatures cr = FearApi.creatures();
        switch (m.action()) {
            case FearNet.SEND -> { if (v != null) send(c, v, m.arg()); }
            case FearNet.RELEASE_ALL -> releaseAllOf(s, c.getUUID());
            case FearNet.BOOTH -> FearBooth.go(c);
            case FearNet.INVITE -> {
                if (v != null) {
                    try { FearExtras.invite(c, v, UUID.fromString(m.arg())); } catch (IllegalArgumentException ignored) { }
                }
            }
            case FearNet.KICK_AUDIENCE -> { if (v != null && FearExtras.isAudience(v.getUUID(), c.getUUID())) stopWatch(v); }
            default -> {
                if (live == null || v == null) break;
                switch (m.action()) {
                    case FearNet.SCENE -> { if (live.state == FearLive.ACTIVE) startScene(live, v, m.arg()); }
                    case FearNet.SPAWN -> {
                        if (cr != null && live.state == FearLive.ACTIVE) {
                            int mode = m.num() == FearApi.HUNT ? FearApi.HUNT : FearApi.SCARE;
                            if (mode == FearApi.HUNT) live.hunt = true;
                            cr.spawn(v.serverLevel(), m.arg(), v, nearby(v, 12), mode);
                            live.scare(10F);
                        }
                    }
                    case FearNet.DESPAWN -> { if (cr != null) { cr.clear(v.getUUID()); live.hunt = false; } }
                    case FearNet.MODE -> {
                        if (cr == null) break;
                        int mode = m.num() == FearApi.HUNT ? FearApi.HUNT : FearApi.SCARE;
                        cr.setMode(v.getUUID(), m.arg().isEmpty() ? null : m.arg(), mode);
                        if (mode == FearApi.HUNT) live.hunt = true; else if (m.arg().isEmpty()) live.hunt = false;
                    }
                    case FearNet.APPARITION -> { if (live.state == FearLive.ACTIVE) FearDirector.apparition(live, v, m.arg().isEmpty() ? null : m.arg()); }
                    case FearNet.FX -> FearDirector.fire(live, v, m.num());
                    case FearNet.ENV -> env(live, m.arg(), m.num());
                    case FearNet.AUTO -> live.auto = m.num() != 0;
                    case FearNet.POV -> { if (live.state == FearLive.ACTIVE) startWatch(c, v, POV); }
                    case FearNet.ORBIT -> { if (live.state == FearLive.ACTIVE) startWatch(c, v, ORBIT); }
                    case FearNet.STEAL -> { stopWatch(c); FearSteal.open(c, v); }
                    case FearNet.KILL -> kill(s, v);
                    case FearNet.HEAL -> { v.setHealth(v.getMaxHealth()); v.getFoodData().setFoodLevel(20); live.fear = 0F; }
                    case FearNet.FREEZE -> {
                        live.frozen = !live.frozen;
                        if (!live.frozen) { v.removeEffect(MobEffects.MOVEMENT_SLOWDOWN); v.removeEffect(MobEffects.JUMP); }
                    }
                    case FearNet.PULL -> release(s, m.target(), true);
                    case FearNet.VOICE -> FearExtras.voice(live, v, m.arg());
                    case FearNet.BATTERY -> FearExtras.battery(live, v, m.num());
                    case FearNet.HUD -> FearExtras.hud(live, v, m.arg(), m.num() != 0);
                    case FearNet.REPLAY -> FearExtras.replay(c, live, m.num());
                    case FearNet.SPLIT -> FearExtras.split(c, live);
                    case FearNet.JOIN -> {
                        try {
                            FearLive other = own(c, UUID.fromString(m.arg()));
                            if (other != null) FearExtras.join(c, live, other);
                        } catch (IllegalArgumentException ignored) { }
                    }
                    default -> { }
                }
            }
        }
        if (PANEL.contains(c.getUUID())) sendPanel(c);
    }

    private static void env(FearLive live, String what, int v) {
        switch (what) {
            case "intensity" -> live.intensity = Mth.clamp(v, 0, 100);
            case "fog" -> live.fog = Mth.clamp(v, 0, 100);
            case "dark" -> live.dark = Mth.clamp(v, 0, 100);
            case "sky" -> live.sky = Mth.clamp(v, 0, 3);
            case "weather" -> live.weather = Mth.clamp(v, 0, 3);
            default -> { }
        }
    }

    static void flash(ServerPlayer v, boolean on) {
        FearLive l = LIVE.get(v.getUUID());
        if (l != null) l.flash = on && l.battery > 0F;
    }

    static void respawn(ServerPlayer v) {
        FearLive live = LIVE.get(v.getUUID());
        if (live == null || live.fakeDeadUntil < 0) return;
        live.fakeDeadUntil = -1;
        ServerLevel l = v.serverLevel();
        if (l.dimension() != FearRealm.DIM) return;
        BlockPos o = live.spawn != null ? live.spawn : live.origin();
        double a = v.getRandom().nextDouble() * Math.PI * 2, d = 6 + v.getRandom().nextDouble() * 20;
        double x = o.getX() + Math.cos(a) * d, z = o.getZ() + Math.sin(a) * d;
        v.teleportTo(l, x, FearDirector.ground(l, x, z, o.getY() + 2), z, v.getRandom().nextFloat() * 360F, 0F);
        FearNet.send(v, FearNet.WAKE_FLASH, 1, 0);
    }

    private static BlockPos nearby(ServerPlayer v, double dist) {
        double a = v.getRandom().nextDouble() * Math.PI * 2;
        double x = v.getX() + Math.cos(a) * dist, z = v.getZ() + Math.sin(a) * dist;
        return BlockPos.containing(x, FearDirector.ground(v.serverLevel(), x, z, v.getY()), z);
    }

    // ------------------------------------------------------------------ sending / scenes / returning

    static void send(ServerPlayer c, ServerPlayer v, String scene) {
        MinecraftServer s = c.server;
        ServerLevel realm = FearRealm.level(s);
        if (realm == null || v == c || LIVE.containsKey(v.getUUID()) || !v.isAlive() || v.level().dimension() == FearRealm.DIM || v.isSpectator()) {
            c.displayClientMessage(Component.translatable("infinity.fear.cannot"), true);
            return;
        }
        FearData d = FearData.get(s);
        int slot = d.slotFor(v.getUUID());
        d.victims.put(v.getUUID(), new FearData.Victim(c.getUUID(), origin(v)));
        d.setDirty();
        FearLive live = new FearLive(v.getUUID(), c.getUUID(), slot);
        LIVE.put(v.getUUID(), live);
        FearNet.send(v, FearNet.ENTER, 0, 0);
        c.displayClientMessage(Component.translatable("infinity.fear.sent", v.getDisplayName()), true);
        FearApi.Scenes sc = FearApi.scenes();
        String id = scene;
        if ((id == null || id.isEmpty()) && sc != null && !sc.ids().isEmpty()) id = sc.ids().get(0);
        beginScene(live, v, id == null ? "" : id);
    }

    /** Switches an active victim to another scene with a transition. */
    private static void startScene(FearLive live, ServerPlayer v, String scene) {
        FearApi.Scenes sc = FearApi.scenes();
        if (sc == null || !sc.ids().contains(scene)) return;
        FearApi.Creatures cr = FearApi.creatures();
        ServerLevel realm = FearRealm.level(v.server);
        if (realm != null) sc.clear(realm, live.owner);
        for (FearLive m : new ArrayList<>(LIVE.values())) {
            if (m.slot != live.slot || m.state != FearLive.ACTIVE) continue;
            ServerPlayer mv = player(v.server, m.victim);
            if (mv == null) continue;
            FearNet.send(mv, FearNet.SWAP, 0, 0);
            m.state = FearLive.ENTERING;
            m.firstEntry = false;
            m.timers.clear();
            m.fallLeft = -1; m.fakeDeadUntil = -1;
            if (cr != null) cr.clear(m.victim);
            m.hunt = false;
            FearExtras.clearCandles(m, mv.serverLevel());
            m.scene = scene;
            if (m != live) { m.follow = live.victim; m.owner = live.victim; m.enterAge = 0; }
        }
        live.owner = live.victim;
        live.follow = null;
        beginScene(live, v, scene);
    }

    static void beginScene(FearLive live, ServerPlayer v, String scene) {
        live.scene = scene;
        live.enterAge = 0;
        live.spawn = null;
        FearApi.Scenes sc = FearApi.scenes();
        ServerLevel realm = FearRealm.level(v.server);
        if (sc != null && realm != null && !scene.isEmpty()) {
            final FearLive target = live;
            sc.build(realm, v, scene, live.origin(), pos -> { if (pos != null) target.spawn = pos; });
        } else {
            live.spawn = live.origin().above();
        }
    }

    static FearData.Origin origin(ServerPlayer p) {
        return new FearData.Origin(p.level().dimension().location(), p.getX(), p.getY(), p.getZ(), p.getYRot(), p.getXRot(), p.gameMode.getGameModeForPlayer().getId());
    }

    private static void teleport(MinecraftServer s, ServerPlayer p, FearData.Origin o) {
        ServerLevel l = s.getLevel(ResourceKey.create(Registries.DIMENSION, o.dim()));
        if (l == null) {
            l = s.overworld();
            var sp = l.getSharedSpawnPos();
            p.teleportTo(l, sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, 0F, 0F);
            return;
        }
        p.teleportTo(l, o.x(), o.y(), o.z(), o.yaw(), o.pitch());
    }

    /** Takes the victim home (exact origin) and ends their nightmare. Offline victims stay recorded until they log in. */
    static void release(MinecraftServer s, UUID victim, boolean teleport) {
        FearData d = FearData.get(s);
        FearLive live = LIVE.remove(victim);
        FearData.Victim rec = d.victims.get(victim);
        FearApi.Creatures cr = FearApi.creatures();
        if (cr != null) cr.clear(victim);
        FearApi.Scenes sc = FearApi.scenes();
        ServerLevel realm = FearRealm.level(s);
        if (live != null && realm != null) { ServerPlayer lv = player(s, victim); FearExtras.clearCandles(live, lv != null ? lv.serverLevel() : realm); }
        if (sc != null && realm != null && live != null && !sharesSlot(live)) sc.clear(realm, live.owner);
        else if (sc != null && realm != null && live == null && rec != null) sc.clear(realm, victim);
        FearExtras.cleanup(victim);
        for (Map.Entry<UUID, Watch> en : new ArrayList<>(WATCH.entrySet())) {
            if (!en.getValue().victim.equals(victim)) continue;
            ServerPlayer c = player(s, en.getKey());
            if (c != null) stopWatch(c);
            else WATCH.remove(en.getKey());
        }
        ServerPlayer v = player(s, victim);
        if (v == null) return;
        if (rec != null && teleport && v.level().dimension() == FearRealm.DIM) {
            v.setDeltaMovement(Vec3.ZERO);
            teleport(s, v, rec.origin());
            v.fallDistance = 0F;
        }
        if (rec != null) { d.victims.remove(victim); d.setDirty(); }
        v.removeEffect(MobEffects.DARKNESS);
        v.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        v.removeEffect(MobEffects.JUMP);
        FearNet.send(v, FearNet.EXIT, 0, 0);
    }

    private static boolean sharesSlot(FearLive l) {
        for (FearLive o : LIVE.values()) if (o != l && o.slot == l.slot) return true;
        return false;
    }

    /** The victim leaves a shared arena: the scene is only cleared when nobody else is in it. */
    static void leaveSlot(FearLive l, ServerPlayer v) {
        FearApi.Scenes sc = FearApi.scenes();
        ServerLevel realm = FearRealm.level(v.server);
        if (sc != null && realm != null && !sharesSlot(l)) sc.clear(realm, l.owner);
    }

    private static void releaseAllOf(MinecraftServer s, UUID caster) {
        FearData d = FearData.get(s);
        List<UUID> ids = new ArrayList<>();
        for (Map.Entry<UUID, FearData.Victim> e : d.victims.entrySet()) if (e.getValue().caster().equals(caster)) ids.add(e.getKey());
        for (FearLive l : LIVE.values()) if (l.caster.equals(caster) && !ids.contains(l.victim)) ids.add(l.victim);
        for (UUID id : ids) release(s, id, true);
    }

    private static void kill(MinecraftServer s, ServerPlayer v) {
        FearLive l = LIVE.get(v.getUUID());
        if (l != null) l.rec.markNow("death", l.total);
        release(s, v.getUUID(), true);
        Executions.kill(v);
    }

    // ------------------------------------------------------------------ watching

    static void startWatch(ServerPlayer c, ServerPlayer v, int mode) {
        MinecraftServer s = c.server;
        ServerLevel realm = FearRealm.level(s);
        if (realm == null || v.level().dimension() != FearRealm.DIM) return;
        FearData d = FearData.get(s);
        Watch old = WATCH.get(c.getUUID());
        if (old == null) {
            d.watchers.put(c.getUUID(), origin(c));
            d.setDirty();
            c.setGameMode(GameType.SPECTATOR);
            c.teleportTo(realm, v.getX(), v.getY(), v.getZ(), c.getYRot(), c.getXRot());
        } else {
            if (c.getCamera() != c) c.setCamera(c);
            if (!old.victim.equals(v.getUUID())) c.teleportTo(realm, v.getX(), v.getY(), v.getZ(), c.getYRot(), c.getXRot());
        }
        WATCH.put(c.getUUID(), new Watch(v.getUUID(), mode));
        PacketDistributor.sendToPlayer(c, new FearNet.Watch(mode, v.getId(), v.getGameProfile().getName()));
        FearLive l = LIVE.get(v.getUUID());
        if (l != null) PacketDistributor.sendToPlayer(c, envOf(l));
    }

    static void stopWatch(ServerPlayer c) {
        MinecraftServer s = c.server;
        FearData d = FearData.get(s);
        Watch w = WATCH.remove(c.getUUID());
        FearData.Origin o = d.watchers.remove(c.getUUID());
        if (w == null && o == null) return;
        d.setDirty();
        if (c.getCamera() != c) c.setCamera(c);
        PacketDistributor.sendToPlayer(c, new FearNet.Watch(0, 0, ""));
        if (o != null) {
            c.setGameMode(GameType.byId(o.gameType()));
            teleport(s, c, o);
        }
    }

    private static void watchTick(MinecraftServer s) {
        for (Map.Entry<UUID, Watch> en : new ArrayList<>(WATCH.entrySet())) {
            ServerPlayer c = player(s, en.getKey());
            Watch w = en.getValue();
            FearLive live = LIVE.get(w.victim);
            ServerPlayer v = live == null ? null : player(s, w.victim);
            if (c == null) { WATCH.remove(en.getKey()); continue; }
            if (!c.isAlive() || v == null || c.level().dimension() != FearRealm.DIM) { stopWatch(c); continue; }
            if (live.state != FearLive.ACTIVE || FearExtras.replaying(en.getKey())) continue;
            w.age++;
            if (w.mode == POV || w.mode == 3) {
                if (w.age == 6) c.setCamera(v);
                if (w.age == 40 && c.getCamera() == v) c.connection.send(new ClientboundSetCameraPacket(v));
                if (w.age > 14 && c.getCamera() != v) stopWatch(c);
            } else {
                if (w.age > 10 && c.isShiftKeyDown()) { stopWatch(c); continue; }
                if (w.age % 10 == 0 && c.distanceToSqr(v) > 9) {
                    c.teleportTo(v.serverLevel(), v.getX(), v.getY(), v.getZ(), EnumSet.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT), 0F, 0F);
                }
            }
        }
    }

    // ------------------------------------------------------------------ server events

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        MinecraftServer s = e.getServer();
        server = s;
        if (LIVE.isEmpty() && WATCH.isEmpty()) return;
        for (FearLive live : new ArrayList<>(LIVE.values())) {
            ServerPlayer c = player(s, live.caster);
            if (c == null || !c.isAlive()) { endCaster(s, live.caster); continue; }
            ServerPlayer v = player(s, live.victim);
            if (v == null || !v.isAlive()) continue;
            live.total++;
            if (live.state == FearLive.ENTERING) enter(s, live, v);
            else active(s, live, v);
            if (live.total % 10 == 0) pushEnv(s, live, v);
        }
        if (!KILLS.isEmpty()) {
            for (UUID id : new ArrayList<>(KILLS)) { ServerPlayer v = player(s, id); if (v != null) kill(s, v); }
            KILLS.clear();
        }
        watchTick(s);
        FearExtras.tick(s);
        if (s.getTickCount() % 10 == 0) for (ServerPlayer c : s.getPlayerList().getPlayers()) {
            if (c.level().dimension() == FearRealm.DIM && !PANEL.contains(c.getUUID()) && FearBooth.inside(c.getX(), c.getY(), c.getZ()) && canCast(c) && !selfLocked(c)) sendPanel(c);
        }
        if (s.getTickCount() % 10 == 0) for (UUID id : new ArrayList<>(PANEL)) {
            ServerPlayer c = player(s, id);
            if (c == null || !canCast(c)) PANEL.remove(id); else sendPanel(c);
        }
    }

    private static FearNet.Env envOf(FearLive l) {
        return new FearNet.Env(l.fog, l.dark, l.sky, l.weather, (int) l.fear, l.hud | (l.frozen ? FearNet.F_FROZEN : 0), (int) l.battery, l.lit, l.candles.isEmpty() ? 0 : 5);
    }

    private static void pushEnv(MinecraftServer s, FearLive live, ServerPlayer v) {
        FearNet.Env env = envOf(live);
        PacketDistributor.sendToPlayer(v, env);
        for (Map.Entry<UUID, Watch> en : WATCH.entrySet()) {
            if (!en.getValue().victim.equals(live.victim)) continue;
            ServerPlayer c = player(s, en.getKey());
            if (c != null) PacketDistributor.sendToPlayer(c, env);
        }
    }

    private static void enter(MinecraftServer s, FearLive live, ServerPlayer v) {
        live.enterAge++;
        if (live.follow != null) {
            FearLive o = LIVE.get(live.follow);
            if (o != null && o.spawn != null && o.state == FearLive.ENTERING) live.spawn = o.spawn;
            else if (o == null) live.follow = null;
        }
        int min = live.firstEntry ? 96 : 56;
        if (live.spawn == null && live.enterAge > 700) live.spawn = live.origin().above();
        if (live.enterAge < min || live.spawn == null) return;
        ServerLevel realm = FearRealm.level(s);
        if (realm == null) { release(s, live.victim, false); return; }
        BlockPos.MutableBlockPos p = live.spawn.mutable();
        for (int i = 0; i < 8 && !realm.getBlockState(p).getCollisionShape(realm, p).isEmpty(); i++) p.move(0, 1, 0);
        FearData.Victim rec = FearData.get(s).victims.get(live.victim);
        float yaw = rec == null ? v.getYRot() : rec.origin().yaw();
        v.teleportTo(realm, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, yaw, 0F);
        v.setDeltaMovement(Vec3.ZERO);
        v.fallDistance = 0F;
        live.state = FearLive.ACTIVE;
        live.age = 0;
        v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 95, 9, true, false, false));
        v.addEffect(new MobEffectInstance(MobEffects.JUMP, 95, 128, true, false, false));
        FearNet.send(v, FearNet.LAND, 0, 0);
        FearApi.Creatures cr = FearApi.creatures();
        if (cr != null && live.follow == null) {
            if (!live.creatureList.isEmpty()) {
                int i = 0;
                for (String e : live.creatureList) {
                    int colon = e.lastIndexOf(':');
                    String id = colon > 0 ? e.substring(0, colon) : e;
                    int mode = colon > 0 && e.endsWith(":" + FearApi.HUNT) ? FearApi.HUNT : FearApi.SCARE;
                    cr.spawn(realm, id, v, nearby(v, 14 + 3 * i++), mode);
                }
            } else {
                String id = cr.forScene(live.scene);
                if (id != null) cr.spawn(realm, id, v, nearby(v, 14), FearApi.SCARE);
            }
        }
        if (!live.pendingInvites.isEmpty()) {
            for (UUID id : live.pendingInvites) {
                ServerPlayer who = player(s, id);
                if (who != null) FearExtras.invite(v, who, live.victim);
            }
            live.pendingInvites.clear();
        }
        if ((live.hud & FearNet.F_REAL) != 0) FearExtras.placeCandles(live, v);
    }

    private static void active(MinecraftServer s, FearLive live, ServerPlayer v) {
        if (v.level().dimension() != FearRealm.DIM) { release(s, live.victim, false); return; }
        live.age++;
        v.clearFire();
        if (live.flash) {
            live.battery -= 0.0208F;
            if (live.battery <= 0F) { live.battery = 0F; live.flash = false; }
        }
        live.rec.tick(v, live.total, live.fear);
        if (live.duration > 0 && live.total > live.duration) { release(s, live.victim, true); return; }
        if (live.sceneSeq.size() > 1) {
            if (live.nextSceneAt == 0) live.nextSceneAt = live.age + 3600;
            else if (live.age >= live.nextSceneAt) {
                live.nextSceneAt = 0;
                live.seqIdx = (live.seqIdx + 1) % live.sceneSeq.size();
                startScene(live, v, live.sceneSeq.get(live.seqIdx));
                return;
            }
        }
        if (live.frozen && live.total % 10 == 0) {
            v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 9, true, false, false));
            v.addEffect(new MobEffectInstance(MobEffects.JUMP, 30, 128, true, false, false));
        }
        if (live.fallLeft < 0) bounds(live, v);
        FearDirector.tick(live, v);
    }

    private static void bounds(FearLive live, ServerPlayer v) {
        BlockPos o = live.origin();
        double dx = v.getX() - o.getX(), dz = v.getZ() - o.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        double max = FearApi.scenes() != null ? 420 : FearTerrain.RING - 5;
        ServerLevel l = v.serverLevel();
        if (d > max) {
            double k = (max - 12) / d;
            double x = o.getX() + dx * k, z = o.getZ() + dz * k;
            v.teleportTo(l, x, FearDirector.ground(l, x, z, v.getY()), z, v.getYRot(), v.getXRot());
            Priv.sound(v, SoundEvents.WARDEN_SONIC_BOOM, v.getEyePosition(), 0.35F, 0.5F);
            FearNet.send(v, FearNet.PULSE, 40, 0);
        } else if (v.getY() < 2) {
            BlockPos sp = live.spawn != null ? live.spawn : o.above();
            v.teleportTo(l, sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, v.getYRot(), v.getXRot());
        }
    }

    /** The caster left, died or lost the power: everybody goes home. */
    private static void endCaster(MinecraftServer s, UUID caster) {
        ServerPlayer c = player(s, caster);
        PANEL.remove(caster);
        if (c != null) stopWatch(c);
        FearData d = FearData.get(s);
        List<UUID> ids = new ArrayList<>();
        for (FearLive l : LIVE.values()) if (l.caster.equals(caster)) ids.add(l.victim);
        for (Map.Entry<UUID, FearData.Victim> e : d.victims.entrySet()) if (e.getValue().caster().equals(caster) && !ids.contains(e.getKey())) ids.add(e.getKey());
        for (UUID id : ids) release(s, id, true);
    }

    @SubscribeEvent
    public static void damage(LivingIncomingDamageEvent e) {
        if (LIVE.isEmpty() || !(e.getEntity() instanceof ServerPlayer p)) return;
        FearLive live = LIVE.get(p.getUUID());
        if (live == null) return;
        Entity src = e.getSource().getEntity();
        boolean creature = live.hunt && live.state == FearLive.ACTIVE && src != null && !(src instanceof Player) && p.level().dimension() == FearRealm.DIM;
        if (!creature) { e.setCanceled(true); return; }
        // real, slow damage from KILLER creatures; the lethal blow becomes the silent kill at the origin
        if (p.getHealth() + p.getAbsorptionAmount() - e.getAmount() <= 1F) {
            e.setCanceled(true);
            finalKill(p);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void death(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        MinecraftServer s = p.server;
        FearLive live = LIVE.get(p.getUUID());
        if (live != null) {
            e.setCanceled(true);
            p.setHealth(p.getMaxHealth());
            release(s, p.getUUID(), true);
            return;
        }
        if (!LIVE.isEmpty() || !WATCH.isEmpty()) {
            boolean casting = WATCH.containsKey(p.getUUID());
            for (FearLive l : LIVE.values()) if (l.caster.equals(p.getUUID())) casting = true;
            if (casting) endCaster(s, p.getUUID());
        }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        MinecraftServer s = p.server;
        FearData d = FearData.get(s);
        FearData.Victim rec = d.victims.get(p.getUUID());
        if (rec != null && !LIVE.containsKey(p.getUUID())) {
            if (p.level().dimension() == FearRealm.DIM) teleport(s, p, rec.origin());
            d.victims.remove(p.getUUID());
            d.setDirty();
        }
        FearData.Origin o = d.watchers.get(p.getUUID());
        if (o != null && !WATCH.containsKey(p.getUUID())) {
            d.watchers.remove(p.getUUID());
            d.setDirty();
            p.setGameMode(GameType.byId(o.gameType()));
            teleport(s, p, o);
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        MinecraftServer s = p.server;
        PANEL.remove(p.getUUID());
        boolean casting = WATCH.containsKey(p.getUUID());
        for (FearLive l : LIVE.values()) if (l.caster.equals(p.getUUID())) casting = true;
        if (casting) endCaster(s, p.getUUID());
        FearLive own = LIVE.get(p.getUUID());
        if (own != null && own.state == FearLive.ENTERING) release(s, p.getUUID(), false);
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) {
        MinecraftServer s = e.getServer();
        for (UUID c : new ArrayList<>(WATCH.keySet())) {
            ServerPlayer p = player(s, c);
            try { if (p != null) stopWatch(p); } catch (RuntimeException ignored) { }
        }
        for (UUID v : new ArrayList<>(LIVE.keySet())) {
            try { release(s, v, true); } catch (RuntimeException ignored) { }
        }
        LIVE.clear();
        WATCH.clear();
        PANEL.clear();
        KILLS.clear();
        FearExtras.reset();
        server = null;
    }

    private FearSessions() {}
}
