package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.registry.ModSounds;
import com.infinitystones.tensura.skill.StoneTree;
import com.infinitystones.tensura.skill.ability.extra.Priv;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * palladium:invisibility (Reality "Invisibility" toggle) plus its hidden companion "invisibility_effect + sound":
 * reality_stone_invis sound (heard only by the user) and an infinite hidden Invisibility effect (no particles).
 * The player also vanishes server-side ({@link Vanish}): other clients are never told he exists, his sounds are not
 * broadcast and Open Parties and Claims stops sharing his position. The flag {@link VisualSync#INVISIBLE} additionally
 * hides the whole model from the user's own third-person view. No particles are spawned.
 */
public final class Invisibility implements Ported {
    @Override
    public void start(ServerPlayer p, StoneTree.Node n) {
        Priv.sound(p, ModSounds.REALITY_STONE_INVIS, p.position(), 0.6F, 1F);
        tick(p, n, 0);
    }

    @Override
    public void tick(ServerPlayer p, StoneTree.Node n, int ticks) {
        MobEffectInstance cur = p.getEffect(MobEffects.INVISIBILITY);
        if (cur == null || (!cur.isInfiniteDuration() && cur.getDuration() < 40) || cur.isVisible() || cur.isAmbient())
            p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, MobEffectInstance.INFINITE_DURATION, 0, false, false, true));
        VisualSync.set(p, VisualSync.INVISIBLE, true);
        Vanish.set(p, true);
    }

    @Override
    public void stop(ServerPlayer p, StoneTree.Node n) {
        Vanish.set(p, false);
        VisualSync.set(p, VisualSync.INVISIBLE, false);
        MobEffectInstance cur = p.getEffect(MobEffects.INVISIBILITY);
        if (cur != null && cur.isInfiniteDuration()) p.removeEffect(MobEffects.INVISIBILITY);
        Priv.sound(p, ModSounds.REALITY_STONE_INVIS, p.position(), 0.6F, 0.9F);
    }
}
