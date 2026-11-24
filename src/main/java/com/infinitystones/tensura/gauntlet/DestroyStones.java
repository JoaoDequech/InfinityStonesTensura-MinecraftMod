package com.infinitystones.tensura.gauntlet;

import com.infinitystones.tensura.skill.GauntletStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Original "Destroy Stones" (toggle): a 17.5 s ritual (350 ticks, like the original animation timer) with the six
 * colours spiralling into the gauntlet; turning it off before the end cancels it. At the end the six stones set in the
 * gauntlet you carry are destroyed for good, your arm is burned and you are thrown into the Soulworld.
 */
public final class DestroyStones {
    public static final String MODE = "reality.gauntlet_destroy";
    private static final int LENGTH = 350;
    private static final Map<UUID, Integer> RITUAL = new HashMap<>();

    public static void start(ServerPlayer p) {
        if (!GauntletPowers.ready(p, true)) {
            Rules.forceToggleOff(p, MODE);
            return;
        }
        RITUAL.put(p.getUUID(), 0);
        Fx.bar(p, "infinity.gauntlet.destroy.start");
    }

    public static void tick(ServerPlayer p) {
        Integer t = RITUAL.get(p.getUUID());
        if (t == null) return;
        ItemStack g = GauntletStones.completeGauntlet(p);
        if (g.isEmpty()) { RITUAL.remove(p.getUUID()); Rules.forceToggleOff(p, MODE); return; }
        RITUAL.put(p.getUUID(), ++t);
        ServerLevel l = p.serverLevel();
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 100, false, false));
        double f = t / (double) LENGTH;
        for (Stone s : Stone.values()) {
            double a = t * 0.12 + s.ordinal() * Math.PI / 3, r = 2.2 * (1 - f) + 0.2;
            Vec3 v = p.position().add(Math.cos(a) * r, 1 + Math.sin(t * 0.05 + s.ordinal()) * 0.4, Math.sin(a) * r);
            l.sendParticles(Fx.dust(s, 1.0F), v.x, v.y, v.z, 2, 0.05, 0.05, 0.05, 0);
        }
        if (t % 40 == 0) Fx.sound(l, p.position(), SoundEvents.BEACON_AMBIENT, 0.6F, 0.5F + (float) f);
        if (t % 20 == 0) Fx.bar(p, "infinity.gauntlet.destroy.progress", (int) (f * 100));
        if (t >= LENGTH) finish(p, g);
    }

    public static void stop(ServerPlayer p) {
        Integer t = RITUAL.remove(p.getUUID());
        if (t != null && t < LENGTH) Fx.barGray(p, "infinity.gauntlet.destroy.cancel");
    }

    private static void finish(ServerPlayer p, ItemStack g) {
        RITUAL.remove(p.getUUID());
        ServerLevel l = p.serverLevel();
        Vec3 c = p.position().add(0, 1, 0);
        GauntletStones.setSockets(g, NonNullList.withSize(6, ItemStack.EMPTY));
        Fx.at(l, ParticleTypes.FLASH, c, 1, 0, 0);
        for (Stone s : Stone.values()) Fx.at(l, Fx.dust(s, 2.0F), c, 50, 1.5, 0.1);
        Fx.sound(l, c, SoundEvents.FIREWORK_ROCKET_BLAST, 0.7F, 2F);
        Fx.sound(l, c, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, 0.7F, 0.16F);
        GauntletPowers.burn(p);
        GauntletPowers.advancement(p, "infinity:destroy_stones");
        GauntletPowers.announce(p, "infinity.gauntlet.destroy.announce", p.getDisplayName());
        Rules.forceToggleOff(p, MODE);
        ServerLevel soul = p.server.getLevel(Snap.SOULWORLD);
        if (soul != null) p.teleportTo(soul, p.getX(), 72, p.getZ(), p.getYRot(), p.getXRot());
    }

    private DestroyStones() {}
}
