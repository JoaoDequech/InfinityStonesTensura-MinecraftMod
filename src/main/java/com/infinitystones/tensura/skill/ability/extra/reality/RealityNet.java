package com.infinitystones.tensura.skill.ability.extra.reality;

import com.infinitystones.tensura.InfinityStones;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Reality payloads: pet "pick the attack target" mode (server → client arm, client → server pick). */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class RealityNet {
    /** on = the next left click on a creature picks the pets' target. */
    public record PetArm(boolean on) implements CustomPacketPayload {
        public static final Type<PetArm> TYPE = new Type<>(InfinityStones.id("extra_reality_pet_arm"));
        public static final StreamCodec<ByteBuf, PetArm> CODEC = StreamCodec.composite(ByteBufCodecs.BOOL, PetArm::on, PetArm::new);
        @Override public Type<PetArm> type() { return TYPE; }
    }

    public record PetPick(int entity) implements CustomPacketPayload {
        public static final Type<PetPick> TYPE = new Type<>(InfinityStones.id("extra_reality_pet_pick"));
        public static final StreamCodec<ByteBuf, PetPick> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, PetPick::entity, PetPick::new);
        @Override public Type<PetPick> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1").optional();
        r.playToClient(PetArm.TYPE, PetArm.CODEC, (msg, ctx) -> ctx.enqueueWork(
                () -> com.infinitystones.tensura.skill.ability.extra.reality.client.RealityClient.arm(msg.on())));
        r.playToServer(PetPick.TYPE, PetPick.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) RealityPet.pickById(sp, msg.entity());
        }));
    }

    static void arm(ServerPlayer p, boolean on) {
        try { PacketDistributor.sendToPlayer(p, new PetArm(on)); } catch (Exception ignored) { }
    }

    private RealityNet() {}
}
