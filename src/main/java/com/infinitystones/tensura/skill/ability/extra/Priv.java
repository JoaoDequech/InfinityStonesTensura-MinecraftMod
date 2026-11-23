package com.infinitystones.tensura.skill.ability.extra;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Things only ONE player sees or hears: per-viewer glow outline, fake blocks, private sounds. */
public final class Priv {
    /** Makes e glow (outline through walls) for viewer only. Re-send about every second while it should last. */
    public static void glow(ServerPlayer viewer, Entity e, boolean on) {
        byte flags = e.getEntityData().get(Flags.ID);
        byte v = on ? (byte) (flags | 0x40) : flags;
        viewer.connection.send(new ClientboundSetEntityDataPacket(e.getId(), List.of(SynchedEntityData.DataValue.create(Flags.ID, v))));
    }

    /** Viewer sees state at pos (the real block is unchanged). Resent blocks / chunk reloads undo it. */
    public static void fakeBlock(ServerPlayer viewer, BlockPos pos, BlockState state) {
        viewer.connection.send(new ClientboundBlockUpdatePacket(pos, state));
    }

    /** Shows the real block again. */
    public static void realBlock(ServerPlayer viewer, BlockPos pos) {
        viewer.connection.send(new ClientboundBlockUpdatePacket(viewer.level(), pos));
    }

    public static void sound(ServerPlayer viewer, Holder<SoundEvent> s, Vec3 at, float vol, float pitch) {
        viewer.connection.send(new ClientboundSoundPacket(s, SoundSource.HOSTILE, at.x, at.y, at.z, vol, pitch, viewer.getRandom().nextLong()));
    }

    public static void sound(ServerPlayer viewer, SoundEvent s, Vec3 at, float vol, float pitch) { sound(viewer, Holder.direct(s), at, vol, pitch); }

    /** Access to the protected shared-flags accessor of Entity (never instantiated). */
    private abstract static class Flags extends Entity {
        static final EntityDataAccessor<Byte> ID = DATA_SHARED_FLAGS_ID;
        private Flags(EntityType<?> t, Level l) { super(t, l); }
    }

    private Priv() {}
}
