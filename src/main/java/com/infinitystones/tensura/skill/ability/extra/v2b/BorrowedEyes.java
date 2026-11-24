package com.infinitystones.tensura.skill.ability.extra.v2b;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.MenuNet;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import com.infinitystones.tensura.skill.ability.extra.mind.MindKit;
import com.infinitystones.tensura.skill.ability.extra.mind.PsychicAvatar;
import net.minecraft.core.Rotations;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Borrowed Eyes (Mind, hidden). Left: look through another player's eyes (aimed, or picked from a menu of players within
 * 256 blocks of the same dimension) for 10-40 s. The body stays behind as a kneeling golden shell while the real player is
 * parked, unseen, on the target (vanilla spectator camera: the server keeps the target's chunks and tracking, the target is
 * told nothing). Right: Confusion on an aimed player, whose W/S and A/D are swapped and whose view drifts for a while (done
 * on that client). Ends on timeout, sneaking, logout, death, target loss and dimension change; a crash is recovered at login.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class BorrowedEyes implements Ability {
    public static final BorrowedEyes INSTANCE = new BorrowedEyes();
    public static final String MODE = "mind.borrowed_eyes";
    private static final String NODE = "borrowed_eyes", K = "infinity.extra.v2b.eyes.", DATA = "infinity_v2b_eyes";
    private static final double AIM_RANGE = 128, MENU_RANGE = 256, CONFUSE_RANGE = 64;
    private static final int GOLD = 0xFFD34A, PALE = 0xFFF1B0;

    @Override public Stone stone() { return Stone.MIND; }

    private static final class State {
        ServerLevel level;
        Vec3 body;
        float yaw, pitch;
        GameType gm = GameType.SURVIVAL;
        UUID target;
        int age, left;
        ArmorStand shell;
        final int runeId = FxNet.newId(), rune2Id = FxNet.newId(), orbId = FxNet.newId();
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();
    private static final Map<UUID, List<UUID>> MENU = new HashMap<>();
    private static final Map<UUID, Tasks.Handle> CONFUSED = new HashMap<>();

    public static boolean isBorrowing(ServerPlayer p) { return ACTIVE.containsKey(p.getUUID()); }

    private static int seconds(ServerPlayer p) { return ExtraSettings.get(p, MODE, 20, 10, 40, 5); }

    // ------------------------------------------------------------------ press / menu

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        State own = ACTIVE.get(p.getUUID());
        if (alt) {
            ServerPlayer t = own != null ? p.server.getPlayerList().getPlayer(own.target)
                    : Aim.entity(p, CONFUSE_RANGE, ServerPlayer.class, x -> Rules.canHarm(p, x));
            if (t == null || !Rules.canHarm(p, t)) return Fx.fail(p, K + "no_target");
            return confuse(p, t);
        }
        if (own != null) { restore(p, own, true); return true; }
        ServerPlayer t = Aim.entity(p, AIM_RANGE, ServerPlayer.class, x -> x.isAlive() && !x.isSpectator());
        if (t != null) return begin(p, t);
        return MenuNet.open(p, Stone.MIND, NODE, this) || Fx.fail(p, K + "nobody");
    }

    private static List<ServerPlayer> candidates(ServerPlayer p) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer sp : p.serverLevel().players())
            if (sp != p && sp.isAlive() && !sp.isSpectator() && sp.distanceToSqr(p) <= MENU_RANGE * MENU_RANGE) out.add(sp);
        out.sort((a, b) -> a.getGameProfile().getName().compareToIgnoreCase(b.getGameProfile().getName()));
        return out;
    }

    @Override
    public List<MenuNet.Option> menu(ServerPlayer p) {
        List<ServerPlayer> list = candidates(p);
        if (list.isEmpty()) return null;
        List<UUID> ids = new ArrayList<>();
        List<MenuNet.Option> out = new ArrayList<>();
        for (ServerPlayer sp : list) {
            ids.add(sp.getUUID());
            out.add(new MenuNet.Option(sp.getGameProfile().getName(), K + "player.desc", "minecraft:player_head", false));
        }
        MENU.put(p.getUUID(), ids);
        return out;
    }

    @Override
    public void pick(ServerPlayer p, int index) {
        List<UUID> ids = MENU.remove(p.getUUID());
        if (ids == null || index < 0 || index >= ids.size()) return;
        ServerPlayer t = p.server.getPlayerList().getPlayer(ids.get(index));
        if (t == null || !t.isAlive() || t.serverLevel() != p.serverLevel() || t.distanceToSqr(p) > MENU_RANGE * MENU_RANGE) { Fx.fail(p, K + "gone"); return; }
        begin(p, t);
    }

    // ------------------------------------------------------------------ eyes

    private static boolean begin(ServerPlayer p, ServerPlayer t) {
        if (ACTIVE.containsKey(p.getUUID())) return false;
        if (p.isSpectator() || p.isSleeping() || !p.isAlive() || PsychicAvatar.isAvatar(p)) return Fx.fail(p, K + "cant");
        if (t == p || !t.isAlive() || t.isSpectator() || t.serverLevel() != p.serverLevel()) return Fx.fail(p, K + "no_target");
        if (p.isPassenger()) p.stopRiding();
        ServerLevel l = p.serverLevel();
        State s = new State();
        s.level = l;
        s.body = p.position();
        s.yaw = p.getYRot();
        s.pitch = p.getXRot();
        s.gm = p.gameMode.getGameModeForPlayer();
        s.target = t.getUUID();
        s.left = seconds(p) * 20;
        ACTIVE.put(p.getUUID(), s);
        persist(p, s);
        spawnShell(p, s);

        Vec3 c = s.body.add(0, 1, 0);
        FxNet.flash(p, GOLD, 0.6F, 12);
        FxNet.shockwave(l, s.body.add(0, 0.05, 0), 5F, GOLD, 22);
        FxNet.spiral(l, s.body, 1F, 3F, GOLD, 26, -1);
        FxNet.flare(l, c, 2.2F, PALE, 14);
        MindKit.spark(l, c, 30);
        Fx.sound(l, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 0.9F);
        Fx.sound(l, c, SoundEvents.SOUL_ESCAPE, 0.5F, 1.4F);

        p.setGameMode(GameType.SPECTATOR);
        p.setCamera(t);
        FxNet.screen(p, FxNet.AVATAR_TINT, 60);
        refreshFx(s);
        Fx.bar(p, Stone.MIND, K + "on", t.getDisplayName(), s.left / 20);
        return true;
    }

    private static void spawnShell(ServerPlayer p, State s) {
        ArmorStand a = MindKit.body(p, s.body.add(0, -0.55, 0), s.yaw, true, false);
        if (a == null) return;
        MindKit.pose(a, 22, 0, -62, -12, -62, 12, -88, -88);
        a.setBodyPose(new Rotations(8, 0, 0));
        MindKit.goldGlow(a);
        s.shell = a;
    }

    private static void refreshFx(State s) {
        ServerLevel l = s.level;
        FxNet.runes(l, s.runeId, s.body.add(0, 0.03, 0), 1.6F, GOLD, 60, 0.6F);
        FxNet.runes(l, s.rune2Id, s.body.add(0, 0.06, 0), 0.9F, PALE, 60, -1.4F);
        FxNet.orb(l, s.orbId, s.body.add(0, 1, 0), 0.5F, GOLD, 60, -1, Vec3.ZERO);
    }

    private static void tickOne(ServerPlayer p, State s) {
        s.age++;
        ServerPlayer t = p.server.getPlayerList().getPlayer(s.target);
        boolean lost = t == null || !t.isAlive() || t.serverLevel() != s.level || p.serverLevel() != s.level || p.getCamera() != t;
        if (lost || --s.left <= 0 || !p.isAlive()) { restore(p, s, true); return; }
        if (s.shell == null || s.shell.isRemoved()) {
            if (s.level.isLoaded(net.minecraft.core.BlockPos.containing(s.body))) spawnShell(p, s);
        } else if (s.age % 4 == 0) s.shell.setHeadPose(new Rotations(22 + Mth.sin(s.age * 0.08F) * 3F, 0, 0));
        if (s.age % 30 == 0) { FxNet.screen(p, FxNet.AVATAR_TINT, 60); refreshFx(s); }
        if (s.age % 40 == 0) Fx.sound(s.level, s.body.add(0, 1, 0), SoundEvents.AMETHYST_BLOCK_CHIME, 0.3F, 0.6F + s.level.random.nextFloat() * 0.4F);
        if (s.age % 20 == 0) Fx.bar(p, Stone.MIND, K + "left", t.getDisplayName(), (s.left + 19) / 20);
    }

    private static void restore(ServerPlayer p, State s, boolean fx) {
        ACTIVE.remove(p.getUUID());
        try {
            if (p.getCamera() != p) p.setCamera(p);
            if (p.gameMode.getGameModeForPlayer() != s.gm) p.setGameMode(s.gm);
            if (p.isAlive() || p.level() == s.level) {
                p.teleportTo(s.level, s.body.x, s.body.y, s.body.z, s.yaw, s.pitch);
                p.setDeltaMovement(Vec3.ZERO);
                p.fallDistance = 0;
            }
        } finally {
            if (s.shell != null) MindKit.discard(s.shell);
            for (int id : new int[]{s.runeId, s.rune2Id, s.orbId}) FxNet.kill(s.level, s.body, id);
            FxNet.screen(p, FxNet.AVATAR_TINT, 0);
            Rules.data(p).remove(DATA);
        }
        if (fx && p.isAlive()) {
            Vec3 c = s.body.add(0, 1, 0);
            FxNet.flash(p, 0xFFF6C8, 0.8F, 12);
            FxNet.shockwave(s.level, s.body.add(0, 0.05, 0), 4F, GOLD, 18);
            FxNet.flare(s.level, c, 2.6F, 0xFFFFFF, 10);
            MindKit.spark(s.level, c, 24);
            Fx.sound(s.level, c, SoundEvents.AMETHYST_BLOCK_BREAK, 0.6F, 0.7F);
            Fx.sound(s.level, c, SoundEvents.BEACON_DEACTIVATE, 0.4F, 1.5F);
        }
    }

    private static void persist(ServerPlayer p, State s) {
        CompoundTag t = new CompoundTag();
        t.putString("dim", s.level.dimension().location().toString());
        t.putDouble("x", s.body.x); t.putDouble("y", s.body.y); t.putDouble("z", s.body.z);
        t.putFloat("yaw", s.yaw); t.putFloat("pitch", s.pitch);
        t.putInt("gm", s.gm.getId());
        Rules.data(p).put(DATA, t);
    }

    // ------------------------------------------------------------------ confusion

    private static boolean confuse(ServerPlayer p, ServerPlayer t) {
        int ticks = Math.max(100, seconds(p) * 10);
        Tasks.Handle old = CONFUSED.remove(t.getUUID());
        if (old != null) old.cancel();
        V2BNet.to(t, V2BNet.msg(V2BNet.CONFUSE, 0, GOLD, ticks, 0));
        UUID id = t.getUUID();
        MinecraftServer server = t.server;
        CONFUSED.put(id, Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) {
                ServerPlayer v = server.getPlayerList().getPlayer(id);
                if (v == null || !v.isAlive() || age >= ticks) return false;
                ServerLevel l = v.serverLevel();
                double a = age * 0.35;
                for (int i = 0; i < 2; i++) {
                    double ang = a + i * Math.PI, h = (age * 0.04 + i * 0.5) % 1.0;
                    Vec3 at = v.position().add(Math.cos(ang) * 0.7, 0.3 + h * (v.getBbHeight() + 0.4), Math.sin(ang) * 0.7);
                    l.sendParticles(Fx.dust(GOLD, 0.7F), at.x, at.y, at.z, 1, 0, 0, 0, 0);
                }
                return true;
            }
            @Override public void end() {
                CONFUSED.remove(id);
                ServerPlayer v = server.getPlayerList().getPlayer(id);
                if (v != null && !v.isAlive()) V2BNet.to(v, V2BNet.msg(V2BNet.CONFUSE, 0, 0, 0, 0));
            }
        }));
        ServerLevel l = p.serverLevel();
        Vec3 c = t.position().add(0, t.getBbHeight() * 0.6, 0);
        Fx.at(l, ParticleTypes.ENCHANT, c, 14, 0.5, 0.2);
        Fx.sound(l, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.35F, 1.9F);
        Fx.bar(p, Stone.MIND, K + "confused", t.getDisplayName(), ticks / 20);
        return true;
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        if (ACTIVE.isEmpty()) return;
        for (Map.Entry<UUID, State> en : new ArrayList<>(ACTIVE.entrySet())) {
            ServerPlayer p = en.getValue().level.getServer().getPlayerList().getPlayer(en.getKey());
            if (p == null) { ACTIVE.remove(en.getKey()); continue; }
            tickOne(p, en.getValue());
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        State s = ACTIVE.get(p.getUUID());
        if (s != null) restore(p, s, false);
        MENU.remove(p.getUUID());
        Tasks.Handle h = CONFUSED.remove(p.getUUID());
        if (h != null) h.cancel();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void death(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        State s = ACTIVE.get(p.getUUID());
        if (s != null) restore(p, s, false);
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        State s = ACTIVE.get(p.getUUID());
        if (s != null) restore(p, s, false);
    }

    /** A crash or kill while borrowing left the player parked as a spectator: put them back at their body. */
    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || ACTIVE.containsKey(p.getUUID())) return;
        CompoundTag t = Rules.data(p).getCompound(DATA);
        if (t.isEmpty()) return;
        Rules.data(p).remove(DATA);
        ResourceLocation dim = ResourceLocation.tryParse(t.getString("dim"));
        ServerLevel l = dim == null ? null : p.server.getLevel(ResourceKey.create(Registries.DIMENSION, dim));
        GameType gm = GameType.byId(t.getInt("gm"));
        if (p.gameMode.getGameModeForPlayer() != gm) p.setGameMode(gm);
        if (l != null) p.teleportTo(l, t.getDouble("x"), t.getDouble("y"), t.getDouble("z"), t.getFloat("yaw"), t.getFloat("pitch"));
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) {
        for (Map.Entry<UUID, State> en : new ArrayList<>(ACTIVE.entrySet())) {
            ServerPlayer p = e.getServer().getPlayerList().getPlayer(en.getKey());
            if (p != null) restore(p, en.getValue(), false);
        }
        ACTIVE.clear(); MENU.clear();
        for (Tasks.Handle h : CONFUSED.values()) h.cancel();
        CONFUSED.clear();
    }

    private BorrowedEyes() {}
}
