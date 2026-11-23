package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.registry.ModSounds;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.server.level.ServerPlayer;

/**
 * Soul "glow" (hold): every living entity and player within {@link #RANGE} blocks gets the glowing outline, seen only by
 * the user (the original gave the vanilla glowing effect to everything for everybody). The server just raises
 * {@link VisualSync#GLOW} on the user while the channel runs; the client outlines the entities itself
 * (see client.port.GlowView and GlowMixin). Plus the soul_stone sound the hidden vibrate_sound helper played.
 */
public final class SoulGlow implements Ported {
    public static final double RANGE = 100;

    @Override public void start(ServerPlayer p, StoneTree.Node n) {
        PortFx.sound(p.serverLevel(), p.position(), ModSounds.SOUL_STONE, 0.5F, 1F);
        VisualSync.set(p, VisualSync.GLOW, true);
    }

    @Override public void tick(ServerPlayer p, StoneTree.Node n, int ticks) {
        VisualSync.set(p, VisualSync.GLOW, true);
    }

    @Override public void stop(ServerPlayer p, StoneTree.Node n) {
        VisualSync.set(p, VisualSync.GLOW, false);
    }
}
