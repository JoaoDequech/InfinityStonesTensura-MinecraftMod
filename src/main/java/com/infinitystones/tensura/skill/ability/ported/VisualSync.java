package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player visual state the server tells every client that can see the player (the originals' render layers and
 * overlays were driven by Palladium ability state, which the port has no equivalent of). Bit flags per player;
 * a packet goes out only when the flags change, and to a viewer who starts tracking the player.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class VisualSync {
    /** Intangibility "vibrate" ghost copies. */
    public static final int VIBRATE = 1;
    /** Radiation skin is building up (Cosmic Explosion / held bare Power Stone). */
    public static final int RADIATION = 2;
    /** Radiation skin switched off by the player (tag no_radiation_texture). */
    public static final int HIDE_RADIATION = 4;
    /** Green Time screen should show (some Time power is running and no_time_screen is not set). */
    public static final int TIME_SCREEN = 8;
    /** Walking on water (Time Freeze). */
    public static final int WATER_WALK = 16;
    /** Reality Invisibility: viewers do not render the player at all (body, armour, items, name tag, layers). */
    public static final int INVISIBLE = 32;
    /** Soul Glow: the user sees every living entity around outlined. */
    public static final int GLOW = 64;

    private static final Map<UUID, Integer> SERVER = new HashMap<>();
    /** Client side: entity id → flags. Written by the packet handler, read by client renderers. */
    public static final Map<Integer, Integer> CLIENT = new ConcurrentHashMap<>();

    public static boolean has(ServerPlayer p, int flag) { return (SERVER.getOrDefault(p.getUUID(), 0) & flag) != 0; }

    public static void set(ServerPlayer p, int flag, boolean on) {
        int old = SERVER.getOrDefault(p.getUUID(), 0);
        int now = on ? old | flag : old & ~flag;
        if (now == old) return;
        if (now == 0) SERVER.remove(p.getUUID()); else SERVER.put(p.getUUID(), now);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new Flags(p.getId(), now));
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking e) {
        if (e.getEntity() instanceof ServerPlayer viewer && e.getTarget() instanceof ServerPlayer target) {
            int f = SERVER.getOrDefault(target.getUUID(), 0);
            if (f != 0) PacketDistributor.sendToPlayer(viewer, new Flags(target.getId(), f));
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && SERVER.remove(p.getUUID()) != null)
            PacketDistributor.sendToPlayersTrackingEntity(p, new Flags(p.getId(), 0));
    }

    @SubscribeEvent
    public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            int f = SERVER.getOrDefault(p.getUUID(), 0);
            if (f != 0) PacketDistributor.sendToPlayer(p, new Flags(p.getId(), f));
        }
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            SERVER.remove(p.getUUID());
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(p, new Flags(p.getId(), 0));
        }
    }

    public record Flags(int entityId, int flags) implements CustomPacketPayload {
        public static final Type<Flags> TYPE = new Type<>(InfinityStones.id("port_visual_flags"));
        public static final StreamCodec<ByteBuf, Flags> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Flags::entityId, ByteBufCodecs.VAR_INT, Flags::flags, Flags::new);
        @Override public Type<Flags> type() { return TYPE; }
    }

    @EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Net {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent e) {
            e.registrar("infinity_port_visual").playToClient(Flags.TYPE, Flags.CODEC, (msg, ctx) -> {
                if (msg.flags() == 0) CLIENT.remove(msg.entityId()); else CLIENT.put(msg.entityId(), msg.flags());
            });
        }
    }

    private VisualSync() {}
}
