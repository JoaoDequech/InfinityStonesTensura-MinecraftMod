package com.infinitystones.tensura.skill.ability.extra.mind;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Hive Mind (replaces the base node psionic_overload_aim). Left (aim): golden threads seize up to 8 mobs in the cone in
 * front of you (radius Alt+scroll 6–16) for 20 s; the pack attacks the aimed target (even cows hit). Use again on
 * another target = retarget (and seize more, up to 8). Right: release the pack.
 */
public final class HiveMind implements Ability {
    public static final HiveMind INSTANCE = new HiveMind();
    public static final String MODE = "mind.psionic_overload_aim";
    private static final int MAX = 8, TICKS = 400;

    @Override public Stone stone() { return Stone.MIND; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        ServerLevel l = p.serverLevel();
        if (alt) {
            int n = Thralls.releaseAll(p, Thralls.HIVE);
            if (n == 0) return Fx.fail(p, "infinity.extra.mind.hive.none");
            FxNet.shockwave(l, p.position().add(0, 0.05, 0), 6F, MindKit.PALE, 16);
            Fx.sound(l, p.getEyePosition(), SoundEvents.AMETHYST_BLOCK_BREAK, 0.6F, 1.5F);
            Fx.bar(p, Stone.MIND, "infinity.extra.mind.hive.released", n);
            return true;
        }
        int radius = ExtraSettings.get(p, MODE, 10, 6, 16, 1);
        LivingEntity target = Aim.living(p, 48, e -> Rules.canHarm(p, e) && !p.getUUID().equals(Thralls.ownerOf(e)));
        List<Mob> pack = Thralls.of(p, Thralls.HIVE);
        List<Mob> fresh = new ArrayList<>();
        if (pack.size() < MAX) {
            Vec3 eye = p.getEyePosition(), look = p.getLookAngle().normalize();
            List<Mob> cands = l.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(radius), m -> m != target && m.isAlive()
                    && Rules.canControl(p, m) && !pack.contains(m) && Thralls.ownerOf(m) == null
                    && m.distanceToSqr(p) <= radius * radius
                    && m.getBoundingBox().getCenter().subtract(eye).normalize().dot(look) > 0.45);
            cands.sort(Comparator.comparingDouble(m -> m.distanceToSqr(p)));
            for (Mob m : cands) { if (pack.size() + fresh.size() >= MAX) break; fresh.add(m); }
        }
        if (pack.isEmpty() && fresh.isEmpty()) return Fx.fail(p, "infinity.extra.mind.hive.nobody");
        for (Mob m : fresh) {
            Thralls.bind(p, m, Thralls.HIVE, TICKS, target);
            Vec3 c = m.position().add(0, m.getBbHeight() + 0.2, 0);
            FxNet.beam(l, p.getEyePosition(), c, MindKit.GOLD, 0.08F, 12);
            FxNet.spiral(l, m.position(), Math.max(0.5F, m.getBbWidth()), m.getBbHeight() + 0.6F, MindKit.GOLD, 24, m.getId());
            FxNet.shockwave(l, m.position().add(0, 0.05, 0), 1.6F, MindKit.PALE, 14);
            MindKit.spark(l, c, 12);
        }
        Thralls.command(p, Thralls.HIVE, target, TICKS);

        // caster: crown of command + rune circle + cone ripple
        Vec3 feet = p.position().add(0, 0.05, 0);
        FxNet.runes(l, 0, feet, 2.4F, MindKit.GOLD, 30, 1.6F);
        FxNet.runes(l, 0, feet.add(0, 0.02, 0), 1.3F, MindKit.PALE, 30, -2.5F);
        FxNet.crown(l, 0, p, MindKit.GOLD, 40);
        FxNet.shockwave(l, feet, radius, MindKit.GOLD, 20);
        FxNet.flash(p, MindKit.GOLD, 0.25F, 8);
        Fx.sound(l, p.getEyePosition(), SoundEvents.EVOKER_CAST_SPELL, 0.6F, 1.3F);
        Fx.sound(l, p.getEyePosition(), SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.35F, 1.8F);
        if (target != null) {
            Vec3 tc = target.position();
            FxNet.runes(l, 0, tc.add(0, 0.05, 0), Math.max(1F, target.getBbWidth() * 1.4F), 0xFF8A2B, 40, -2F);
            FxNet.beam(l, p.getEyePosition(), tc.add(0, target.getBbHeight() * 0.6, 0), MindKit.PALE, 0.05F, 10);
            Fx.sound(l, tc, SoundEvents.WARDEN_SONIC_CHARGE, 0.4F, 1.6F);
        }
        Fx.bar(p, Stone.MIND, target == null ? "infinity.extra.mind.hive.guard" : "infinity.extra.mind.hive.attack",
                pack.size() + fresh.size(), target == null ? "" : target.getDisplayName());
        return true;
    }
}
