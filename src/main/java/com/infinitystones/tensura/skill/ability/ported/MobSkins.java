package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Zombie;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mob "powers" of the original (data/infinity/palladium/powers/trasferimento_zombie, pugmeowla_zombie, dark_zombie,
 * thanos_classic). They were pure skins: Invocar Zumbi (function infinity:zombies) summons three named zombies and
 * three plain ones, and every one of them is drawn as a Thanos player model (the named ones with their own skin on
 * top and the zombie body hidden). The function tags the zombies; the tag is what the server reads and tells viewers.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class MobSkins {
    public static final String TAG_THANOS = "thanos_zombie";
    public static final int PLAIN = 0, TRANSFER = 1, PUGMEOWLA = 2, DARK = 3;
    /** Client side: entity id → skin kind. */
    public static final Map<Integer, Integer> CLIENT = new ConcurrentHashMap<>();

    /** -1 = not a skinned zombie. */
    public static int kind(Entity e) {
        if (!(e instanceof Zombie) || !e.getTags().contains(TAG_THANOS)) return -1;
        if (e.getTags().contains("transfer_zombie")) return TRANSFER;
        if (e.getTags().contains("pugmeowla_zombie")) return PUGMEOWLA;
        if (e.getTags().contains("dark_zombie")) return DARK;
        return PLAIN;
    }

    @SubscribeEvent
    public static void startTracking(PlayerEvent.StartTracking e) {
        if (!(e.getEntity() instanceof ServerPlayer viewer)) return;
        int k = kind(e.getTarget());
        if (k >= 0) PacketDistributor.sendToPlayer(viewer, new Sync(e.getTarget().getId(), k));
    }

    public record Sync(int entityId, int kind) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(InfinityStones.id("port_mob_skin"));
        public static final StreamCodec<ByteBuf, Sync> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Sync::entityId, ByteBufCodecs.VAR_INT, Sync::kind, Sync::new);
        @Override public Type<Sync> type() { return TYPE; }
    }

    @EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Net {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent e) {
            e.registrar("infinity_port_mobskin").playToClient(Sync.TYPE, Sync.CODEC,
                    (msg, ctx) -> CLIENT.put(msg.entityId(), msg.kind()));
        }
    }

    private MobSkins() {}
}
