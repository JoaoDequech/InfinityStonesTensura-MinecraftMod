package com.infinitystones.tensura.skill.ability.extra.soul;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Priv;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Soul Sight (Soul), toggle. While it is on, every living soul within the radius (Alt+scroll 16–64 blocks) burns with an
 * orange outline that shows through walls, wisps rise from their heads and you hear their heartbeats, deeper for the big
 * ones and a ghostly whisper for the undead. Everything is private: only you see and hear it, nothing changes in the world
 * and the real teams of the creatures are given back when it turns off.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = com.infinitystones.tensura.InfinityStones.MOD_ID)
public final class SoulSight implements Ability {
    public static final SoulSight INSTANCE = new SoulSight();
    private static final String MODE = "soul.soul_sight", K = "infinity.extra.sight.";
    private static final int ORANGE = Stone.SOUL.color() & 0xFFFFFF, MAX = 64;
    private static final String TEAM = "inf_soul_sight";

    private static final class State {
        final PlayerTeam team = new PlayerTeam(new Scoreboard(), TEAM);
        final Map<Integer, String> names = new HashMap<>();
        State() { team.setColor(ChatFormatting.GOLD); team.setCollisionRule(Team.CollisionRule.NEVER); }
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    @Override public Stone stone() { return Stone.SOUL; }

    @Override
    public void start(ServerPlayer p) {
        if (STATES.containsKey(p.getUUID())) return;
        State s = new State();
        STATES.put(p.getUUID(), s);
        p.connection.send(ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(s.team, true));
        ServerLevel l = p.serverLevel();
        Vec3 g = p.position().add(0, 0.06, 0);
        SoulKit.circle(l, p.position(), 3F, ORANGE, 30);
        FxNet.spiral(l, p.position(), 1.2F, 2.6F, ORANGE, 20, p.getId());
        FxNet.flash(p, 0xFFB35C, 0.35F, 12);
        Fx.sound(l, g, SoundEvents.SOUL_ESCAPE.value(), 0.6F, 0.7F);
        Fx.sound(l, g, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.5F, 0.6F);
        Fx.bar(p, Stone.SOUL, K + "on");
    }

    @Override
    public void tick(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        if (s == null) { start(p); return; }
        if (p.tickCount % 10 != 0) return;
        ServerLevel l = p.serverLevel();
        double r = com.infinitystones.tensura.skill.ability.extra.ExtraSettings.get(p, MODE, 40, 16, 128, 4);
        List<LivingEntity> near = new ArrayList<>(l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(r),
                e -> e != p && e.isAlive() && !e.isSpectator() && e.distanceToSqr(p) <= r * r));
        near.sort(Comparator.comparingDouble(e -> e.distanceToSqr(p)));
        if (near.size() > MAX) near = near.subList(0, MAX);
        Set<Integer> now = new HashSet<>();
        int beats = 0, wisps = 0;
        for (LivingEntity e : near) {
            now.add(e.getId());
            if (!s.names.containsKey(e.getId())) {
                String name = e.getScoreboardName();
                s.names.put(e.getId(), name);
                p.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(s.team, name, ClientboundSetPlayerTeamPacket.Action.ADD));
            }
            Priv.glow(p, e, true);
            double d = Math.sqrt(e.distanceToSqr(p));
            if (wisps < 24) {
                wisps++;
                Vec3 top = e.position().add(0, e.getBbHeight() + 0.35, 0);
                l.sendParticles(p, ParticleTypes.SOUL, true, top.x, top.y, top.z, 1, 0.12, 0.2, 0.12, 0.015);
                if (e instanceof ServerPlayer) l.sendParticles(p, Fx.dust(ORANGE, 1.2F), true, top.x, top.y, top.z, 3, 0.2, 0.2, 0.2, 0);
            }
            if (beats < 6 && (p.tickCount / 10 + e.getId()) % 3 == 0) {
                beats++;
                float vol = (float) Mth.clamp(1.0 - d / r, 0.12, 0.55);
                boolean undead = e.isInvertedHealAndHarm();
                float low = e.getHealth() / Math.max(1F, e.getMaxHealth());
                float pitch = Mth.clamp(1.5F - e.getBbHeight() * 0.35F + (1F - low) * 0.25F, 0.5F, 1.8F);
                Vec3 at = e.position().add(0, e.getBbHeight() * 0.5, 0);
                if (undead) Priv.sound(p, SoundEvents.SOUL_ESCAPE.value(), at, vol * 0.7F, pitch);
                else Priv.sound(p, SoundEvents.WARDEN_HEARTBEAT, at, vol, pitch);
            }
        }
        List<Integer> gone = new ArrayList<>();
        for (Map.Entry<Integer, String> en : s.names.entrySet()) {
            if (now.contains(en.getKey())) continue;
            gone.add(en.getKey());
            release(p, s, l.getEntity(en.getKey()), en.getValue());
        }
        for (Integer id : gone) s.names.remove(id);
        if (p.tickCount % 60 == 0) Fx.bar(p, Stone.SOUL, K + "count", now.size());
    }

    @Override
    public void stop(ServerPlayer p) {
        State s = STATES.remove(p.getUUID());
        if (s == null) return;
        ServerLevel l = p.serverLevel();
        for (Map.Entry<Integer, String> en : s.names.entrySet()) release(p, s, l.getEntity(en.getKey()), en.getValue());
        p.connection.send(ClientboundSetPlayerTeamPacket.createRemovePacket(s.team));
        Fx.sound(l, p.position(), SoundEvents.SOUL_ESCAPE.value(), 0.4F, 1.4F);
        Fx.bar(p, Stone.SOUL, K + "off");
    }

    /** Takes the outline away and puts the creature back into its real team (as the viewer's client knows it). */
    private static void release(ServerPlayer p, State s, Entity e, String name) {
        if (e != null) Priv.glow(p, e, false);
        Team real = e == null ? null : e.getTeam();
        if (real instanceof PlayerTeam pt) p.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(pt, name, ClientboundSetPlayerTeamPacket.Action.ADD));
        else p.connection.send(ClientboundSetPlayerTeamPacket.createPlayerPacket(s.team, name, ClientboundSetPlayerTeamPacket.Action.REMOVE));
    }

    @net.neoforged.bus.api.SubscribeEvent
    public static void loggedOut(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e) { STATES.remove(e.getEntity().getUUID()); }
}
