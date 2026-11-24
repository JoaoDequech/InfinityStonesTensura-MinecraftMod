package com.infinitystones.tensura.skill.ability.extra.v2e;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Network of the Enchanter: open the table (server → caster), apply / remove (caster → server), the cauldron show (server → nearby). */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class V2eNet {
    /** Target slots, in the order of the slot tabs of the screen. */
    public static final EquipmentSlot[] SLOTS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    public static final int MAX_LEVEL = 255;
    public static final int FLAG_CANCEL = 1, FLAG_REMOVE = 2;

    public record Open() implements CustomPacketPayload {
        public static final Type<Open> TYPE = new Type<>(InfinityStones.id("v2e_open"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Open> CODEC = StreamCodec.unit(new Open());
        @Override public Type<Open> type() { return TYPE; }
    }

    /** level 0 = remove the enchantment. The server checks everything again. */
    public record Apply(int slot, ResourceLocation id, int level) implements CustomPacketPayload {
        public static final Type<Apply> TYPE = new Type<>(InfinityStones.id("v2e_apply"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Apply> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Apply::slot, ResourceLocation.STREAM_CODEC, Apply::id, ByteBufCodecs.VAR_INT, Apply::level, Apply::new);
        @Override public Type<Apply> type() { return TYPE; }
    }

    /** The show around the caster. flags: {@link #FLAG_CANCEL} ends it early, {@link #FLAG_REMOVE} = an enchantment is taken off (no glint). */
    public record Anim(int caster, int slot, ItemStack stack, double x, double y, double z, float yaw, int flags) implements CustomPacketPayload {
        public static final Type<Anim> TYPE = new Type<>(InfinityStones.id("v2e_anim"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Anim> CODEC = StreamCodec.of((buf, m) -> {
            buf.writeVarInt(m.caster); buf.writeByte(m.slot); ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, m.stack);
            buf.writeDouble(m.x); buf.writeDouble(m.y); buf.writeDouble(m.z); buf.writeFloat(m.yaw); buf.writeByte(m.flags);
        }, buf -> new Anim(buf.readVarInt(), buf.readByte(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readByte()));
        @Override public Type<Anim> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var r = event.registrar("1").optional();
        r.playToClient(Open.TYPE, Open.CODEC,
                (msg, ctx) -> ctx.enqueueWork(() -> com.infinitystones.tensura.skill.ability.extra.v2e.client.EnchanterClient.open()));
        r.playToClient(Anim.TYPE, Anim.CODEC,
                (msg, ctx) -> ctx.enqueueWork(() -> com.infinitystones.tensura.skill.ability.extra.v2e.client.EnchanterClient.accept(msg)));
        r.playToServer(Apply.TYPE, Apply.CODEC, (msg, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) Enchanter.apply(sp, msg);
        }));
    }

    private V2eNet() {}
}
