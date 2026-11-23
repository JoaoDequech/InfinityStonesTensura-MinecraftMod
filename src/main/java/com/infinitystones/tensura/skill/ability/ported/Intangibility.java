package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.registry.ModSounds;
import com.infinitystones.tensura.skill.StoneTree;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * palladium:intangibility (Mind "Intangibility" toggle + its hidden helpers intangibility_ground / _flight /
 * _effects / vibrate_sound). While on: walk through blocks horizontally; through floors/ceilings only while flying
 * (original "vertical" when hovering/flying); no suffocation; cannot hit or use things (original Pehkui reach -1).
 * Collision is removed by a BlockStateBase mixin (infinity_port.mixins.json) on both sides — the client learns its own
 * state through a tiny payload. Unbreakable blocks (bedrock, barrier, end portal frame, reinforced deepslate) stay solid.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class Intangibility implements Ported {
    private static final Set<UUID> SERVER = ConcurrentHashMap.newKeySet();
    /** Local player's own state on the client (set by {@link Sync}). */
    public static volatile boolean clientSelf;

    @Override
    public void start(ServerPlayer p, StoneTree.Node n) {
        if (!"intangibility".equals(n.id())) return; // ground/flight helpers are folded into the main toggle
        SERVER.add(p.getUUID());
        PacketDistributor.sendToPlayer(p, new Sync(true));
        VisualSync.set(p, VisualSync.VIBRATE, true);
        PortFx.sound(p.serverLevel(), p.position(), ModSounds.MIND_STONE_PHASE, 0.5F, 1F);
        tick(p, n, 0);
    }

    @Override
    public void tick(ServerPlayer p, StoneTree.Node n, int ticks) {
        if (!"intangibility".equals(n.id())) return;
        if (!SERVER.contains(p.getUUID())) {
            SERVER.add(p.getUUID());
            PacketDistributor.sendToPlayer(p, new Sync(true));
        }
        VisualSync.set(p, VisualSync.VIBRATE, true);
        AttributeModifier.apply(p, Attributes.ENTITY_INTERACTION_RANGE, AttributeModifier.modifierId(n, "_reach"), -1,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        AttributeModifier.apply(p, Attributes.BLOCK_INTERACTION_RANGE, AttributeModifier.modifierId(n, "_breach"), -1,
                net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    @Override
    public void stop(ServerPlayer p, StoneTree.Node n) {
        if (!"intangibility".equals(n.id())) return;
        SERVER.remove(p.getUUID());
        PacketDistributor.sendToPlayer(p, new Sync(false));
        VisualSync.set(p, VisualSync.VIBRATE, false);
        AttributeModifier.remove(p, Attributes.ENTITY_INTERACTION_RANGE, AttributeModifier.modifierId(n, "_reach"));
        AttributeModifier.remove(p, Attributes.BLOCK_INTERACTION_RANGE, AttributeModifier.modifierId(n, "_breach"));
        PortFx.sound(p.serverLevel(), p.position(), ModSounds.MIND_STONE_PHASE, 0.4F, 0.8F);
    }

    // ---------------- queried by the mixin ----------------

    public static boolean isIntangible(Entity e) {
        if (!(e instanceof Player p)) return false;
        return p.level().isClientSide ? clientSelf && p.isLocalPlayer() : SERVER.contains(p.getUUID());
    }

    /** Whether the entity may pass through this block (vertical = also the floor/ceiling). */
    public static boolean canPass(Entity e, BlockState state) {
        return !(state.is(Blocks.BEDROCK) || state.is(Blocks.BARRIER) || state.is(Blocks.END_PORTAL_FRAME)
                || state.is(Blocks.REINFORCED_DEEPSLATE) || state.is(Blocks.END_GATEWAY));
    }

    public static boolean vertical(Entity e) {
        return e instanceof Player p && (p.getAbilities().flying || p.isFallFlying());
    }

    /** Original "isAbove": the block is under the entity's feet. */
    public static boolean isAbove(Entity entity, double shapeMaxY, BlockPos pos) {
        return entity.getY() > pos.getY() + shapeMaxY - (entity.onGround() ? 8.05 / 16.0 : 0.0015);
    }

    @SubscribeEvent
    public static void noSuffocation(LivingIncomingDamageEvent e) {
        if (e.getSource().is(DamageTypes.IN_WALL) && isIntangible(e.getEntity())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { SERVER.remove(e.getEntity().getUUID()); }

    // ---------------- network ----------------

    public record Sync(boolean on) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = new Type<>(InfinityStones.id("port_intangible"));
        public static final StreamCodec<ByteBuf, Sync> CODEC = ByteBufCodecs.BOOL.map(Sync::new, Sync::on);
        @Override public Type<Sync> type() { return TYPE; }
    }

    @EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Net {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent e) {
            e.registrar("infinity_port_core").playToClient(Sync.TYPE, Sync.CODEC, (msg, ctx) -> clientSelf = msg.on());
        }
    }
}
