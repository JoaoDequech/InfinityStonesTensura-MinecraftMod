package com.infinitystones.tensura.client.port;

import com.infinitystones.tensura.skill.ability.ported.SoulGlow;
import com.infinitystones.tensura.skill.ability.ported.VisualSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Soul Glow on the client: whether the local player should see this entity outlined (read by GlowMixin). */
public final class GlowView {
    private static final double R2 = SoulGlow.RANGE * SoulGlow.RANGE;

    public static boolean outlined(Entity e) {
        if (!(e instanceof LivingEntity) || VisualSync.CLIENT.isEmpty()) return false;
        LocalPlayer me = Minecraft.getInstance().player;
        if (me == null || e == me || e.isSpectator()) return false;
        if ((VisualSync.CLIENT.getOrDefault(me.getId(), 0) & VisualSync.GLOW) == 0) return false;
        return e.distanceToSqr(me) <= R2;
    }

    private GlowView() {}
}
