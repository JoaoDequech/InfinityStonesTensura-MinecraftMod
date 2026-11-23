package com.infinitystones.tensura.skill.ability.extra.space;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.BanishLog;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vigia do Abismo / Abyss Watch — the eye over Spatial Banishment's destinations (central End island, top of the
 * Nether roof). Left: holographic panel with who is there (coordinates, distance, time there, "banished by you"), mob
 * counts near the landing points and your recent banishments. Right: watch on/off (persisted) — while on and carrying
 * the Space Stone you get a title + sound when a player arrives at or leaves those places.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class AbyssWatch implements Ability {
    public static final AbyssWatch INSTANCE = new AbyssWatch();
    private static final String K = "infinity.extra.abyss_watch.", FLAG = "infinity_abyss_watch";
    private static final int NONE = 0, END = 1, ROOF = 2;
    private static final double END_RADIUS = 200;

    private record Where(int zone, long since) {}
    private static final Map<UUID, Where> ZONES = new HashMap<>();

    @Override public Stone stone() { return Stone.SPACE; }

    private static int col() { return Stone.SPACE.color() & 0xFFFFFF; }

    static int zoneOf(ServerPlayer p) {
        if (p.isSpectator()) return NONE;
        if (p.level().dimension() == Level.END && p.getX() * p.getX() + p.getZ() * p.getZ() <= END_RADIUS * END_RADIUS) return END;
        if (p.level().dimension() == Level.NETHER && p.getY() >= 127) return ROOF;
        return NONE;
    }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        ServerLevel l = p.serverLevel();
        if (alt) {
            boolean on = !Rules.data(p).getBoolean(FLAG);
            Rules.data(p).putBoolean(FLAG, on);
            Fx.bar(p, Stone.SPACE, on ? K + "on" : K + "off");
            Fx.sound(l, p.position(), on ? SoundEvents.ENDER_EYE_DEATH : SoundEvents.ENDER_EYE_LAUNCH, 0.6F, on ? 1.4F : 0.8F);
            FxNet.crown(l, 0, p, col(), on ? 40 : 15);
            return true;
        }
        SpaceNet.to(p, new SpaceNet.AbyssPanel(Component.translatable(K + "panel"), lines(p), 20 * 14));
        Vec3 at = p.position();
        FxNet.runes(l, 0, at.add(0, 0.03, 0), 2.6F, col(), 50, -1.2F);
        FxNet.shockwave(l, at.add(0, 0.05, 0), 5F, col(), 18);
        FxNet.orb(l, 0, at, 0.35F, col(), 50, p.getId(), new Vec3(0, p.getBbHeight() + 0.7, 0));
        FxNet.flash(p, col(), 0.2F, 8);
        Fx.at(l, net.minecraft.core.particles.ParticleTypes.PORTAL, at.add(0, 1, 0), 40, 0.6, 0.6);
        Fx.sound(l, at, SoundEvents.ENDER_EYE_DEATH, 0.6F, 0.7F);
        Fx.sound(l, at, SoundEvents.BEACON_AMBIENT, 0.7F, 1.6F);
        return true;
    }

    private static List<Component> lines(ServerPlayer viewer) {
        MinecraftServer server = viewer.server;
        java.util.ArrayList<Component> out = new java.util.ArrayList<>();
        List<BanishLog.Entry> mine = BanishLog.list(viewer);
        for (int zone : new int[]{END, ROOF}) {
            out.add(Component.translatable(zone == END ? K + "end" : K + "roof").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
            int n = 0;
            for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
                if (zoneOf(sp) != zone) continue;
                n++;
                Where w = ZONES.get(sp.getUUID());
                long secs = w == null || w.zone != zone ? 0 : (server.overworld().getGameTime() - w.since) / 20;
                MutableComponent line = Component.literal(" ▸ ").append(sp.getDisplayName().copy().withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(String.format("  %d %d %d", sp.getBlockX(), sp.getBlockY(), sp.getBlockZ())).withStyle(ChatFormatting.GRAY));
                if (sp.level() == viewer.level()) line.append(Component.literal(String.format("  %.0fm", sp.distanceTo(viewer))).withStyle(ChatFormatting.DARK_AQUA));
                line.append(Component.literal(String.format("  %d:%02d", secs / 60, secs % 60)).withStyle(ChatFormatting.BLUE));
                for (BanishLog.Entry e : mine) if (e.target().equals(sp.getUUID())) { line.append(Component.translatable(K + "banished_by_you").withStyle(ChatFormatting.LIGHT_PURPLE)); break; }
                out.add(line);
            }
            if (n == 0) out.add(Component.translatable(K + "empty").withStyle(ChatFormatting.DARK_GRAY));
            ServerLevel lv = server.getLevel(zone == END ? Level.END : Level.NETHER);
            if (lv != null) {
                AABB box = zone == END ? new AABB(-96, 0, -96, 96, 160, 96) : new AABB(-64, 127, -64, 64, 256, 64);
                int mobs = lv.getEntitiesOfClass(Mob.class, box, m -> m.isAlive()).size();
                out.add(Component.translatable(K + "mobs", mobs).withStyle(ChatFormatting.GRAY));
            }
        }
        if (!mine.isEmpty()) {
            out.add(Component.translatable(K + "yours").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD));
            for (BanishLog.Entry e : mine) {
                String dim = e.toDim() == Level.END ? "End" : e.toDim() == Level.NETHER ? "Nether" : e.toDim().location().getPath();
                out.add(Component.literal(" ▸ " + e.name() + "  ").withStyle(ChatFormatting.WHITE)
                        .append(Component.literal(String.format("%s %.0f %.0f %.0f", dim, e.to().x, e.to().y, e.to().z)).withStyle(ChatFormatting.GRAY)));
            }
        }
        out.add(Component.translatable(Rules.data(viewer).getBoolean(FLAG) ? K + "watch_on" : K + "watch_off").withStyle(ChatFormatting.DARK_AQUA));
        return out;
    }

    // ------------------------------------------------------------------ tracker + alerts

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        long now = server.overworld().getGameTime();
        if (now % 20 != 0) return;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            int z = zoneOf(sp);
            Where w = ZONES.get(sp.getUUID());
            int was = w == null ? NONE : w.zone;
            if (z == was) continue;
            ZONES.put(sp.getUUID(), new Where(z, now));
            if (w == null && z == NONE) continue;
            if (was != NONE) alert(server, sp, was, false);
            if (z != NONE) alert(server, sp, z, true);
        }
    }

    private static void alert(MinecraftServer server, ServerPlayer who, int zone, boolean arrived) {
        for (ServerPlayer w : server.getPlayerList().getPlayers()) {
            if (w == who || !Rules.data(w).getBoolean(FLAG) || !Stone.SPACE.isHeldBy(w)) continue;
            boolean banished = false;
            for (BanishLog.Entry en : BanishLog.list(w)) if (en.target().equals(who.getUUID())) { banished = true; break; }
            Component place = Component.translatable(zone == END ? K + "end" : K + "roof");
            MutableComponent sub = Component.translatable(arrived ? K + "arrived" : K + "left", who.getDisplayName(), place)
                    .withColor(col());
            if (banished) sub.append(Component.translatable(K + "banished_by_you").withStyle(ChatFormatting.LIGHT_PURPLE));
            w.connection.send(new ClientboundSetTitlesAnimationPacket(5, 40, 15));
            w.connection.send(new ClientboundSetTitleTextPacket(Component.literal("◉").withColor(col())));
            w.connection.send(new ClientboundSetSubtitleTextPacket(sub));
            w.displayClientMessage(sub, false);
            com.infinitystones.tensura.skill.ability.extra.Priv.sound(w, arrived ? SoundEvents.ENDER_EYE_DEATH : SoundEvents.ENDER_EYE_LAUNCH, w.position(), 0.7F, arrived ? 0.6F : 1.2F);
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { ZONES.remove(e.getEntity().getUUID()); }
}
