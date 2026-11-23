package com.infinitystones.tensura.skill.ability.extra.space;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Dobrar Distâncias / Fold Distance. Left: space folds and you step to the aimed point (Alt+scroll distance 8–96,
 * first safe spot on the line); players crouching within 3 blocks and your pets come along. Right: folds the aimed
 * mob/player (no bosses) to right in front of you.
 */
public final class FoldDistance implements Ability {
    public static final FoldDistance INSTANCE = new FoldDistance();
    private static final String MODE = "space.fold_distance", K = "infinity.extra.fold_distance.";

    @Override public Stone stone() { return Stone.SPACE; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        int dist = ExtraSettings.get(p, MODE, 32, 8, 96, 8);
        return alt ? pull(p, dist) : fold(p, dist);
    }

    private static int col() { return Stone.SPACE.color() & 0xFFFFFF; }

    private boolean fold(ServerPlayer p, int dist) {
        ServerLevel l = p.serverLevel();
        Vec3 look = p.getLookAngle();
        Vec3 target = Aim.point(p, dist).subtract(look.scale(0.45));
        Vec3 from = p.position();
        Vec3 spot = Ground.safeAlong(l, p, from.add(0, 0.1, 0), target);
        if (spot == null || spot.distanceToSqr(from) < 4) return Fx.fail(p, K + "blocked");

        // passengers: crouching players nearby (consent) + own pets
        List<Entity> crew = new ArrayList<>();
        for (Player o : l.getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(3), o -> o != p && o.isShiftKeyDown() && !o.isSpectator() && !com.infinitystones.tensura.skill.ability.extra.space.SpiritualGathering.isTrapped(o)))
            crew.add(o);
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(10), e -> e != p && e.isAlive()
                && !e.isPassenger() && (e instanceof OwnableEntity o && p.getUUID().equals(o.getOwnerUUID()))
                && !(e instanceof TamableAnimal t && t.isOrderedToSit())))
            crew.add(e);

        departFx(l, p, from, spot);
        if (p.isPassenger()) p.stopRiding();
        p.teleportTo(spot.x, spot.y, spot.z);
        p.resetFallDistance();
        p.setDeltaMovement(Vec3.ZERO);
        int moved = 0;
        for (Entity e : crew) {
            Vec3 off = e.position().subtract(from);
            Vec3 want = spot.add(off.x, 0, off.z);
            Vec3 at = Ground.fits(l, e, want) ? want : Ground.fits(l, e, spot) ? spot : null;
            if (at == null) continue;
            if (e.isPassenger()) e.stopRiding();
            Vec3 old = e.position();
            e.teleportTo(at.x, at.y, at.z);
            e.resetFallDistance();
            Fx.at(l, ParticleTypes.REVERSE_PORTAL, old.add(0, e.getBbHeight() / 2, 0), 20, 0.3, 0.1);
            FxNet.spiral(l, at, 0.6F, e.getBbHeight() + 0.4F, col(), 16, e.getId());
            moved++;
        }
        arriveFx(l, p, spot);
        if (moved > 0) Fx.bar(p, Stone.SPACE, K + "crew", moved);
        return true;
    }

    private boolean pull(ServerPlayer p, int dist) {
        ServerLevel l = p.serverLevel();
        LivingEntity t = Aim.living(p, dist, e -> Rules.canControl(p, e) && !com.infinitystones.tensura.skill.ability.extra.space.SpiritualGathering.isTrapped(e));
        if (t == null) return Fx.fail(p, K + "no_target");
        Vec3 fwd = new Vec3(p.getLookAngle().x, 0, p.getLookAngle().z);
        if (fwd.lengthSqr() < 1e-4) fwd = new Vec3(0, 0, 1);
        fwd = fwd.normalize();
        Vec3 front = p.position().add(fwd.scale(1.6 + t.getBbWidth()));
        Vec3 spot = Ground.safeAlong(l, t, p.position().add(0, 0.1, 0), front);
        if (spot == null) return Fx.fail(p, K + "blocked");
        Vec3 from = t.position();
        if (t.isPassenger()) t.stopRiding();
        FxNet.tunnel(l, from.add(0, t.getBbHeight() / 2, 0), spot.add(0, t.getBbHeight() / 2, 0), 1.0F, col(), 18);
        FxNet.portal(l, 0, from.add(0, t.getBbHeight() / 2, 0), p.position().subtract(from).normalize(), 1.3F, col(), 16);
        Fx.at(l, ParticleTypes.REVERSE_PORTAL, from.add(0, t.getBbHeight() / 2, 0), 40, 0.5, 0.2);
        Fx.sound(l, from, SoundEvents.ENDERMAN_TELEPORT, 0.6F, 0.6F);
        float yaw = p.getYRot() + 180F;
        t.teleportTo(spot.x, spot.y, spot.z);
        t.setYRot(yaw); t.setYHeadRot(yaw);
        t.setDeltaMovement(Vec3.ZERO);
        t.resetFallDistance();
        t.hurtMarked = true;
        FxNet.flare(l, spot.add(0, t.getBbHeight() / 2, 0), 2.2F, col(), 14);
        FxNet.shockwave(l, spot.add(0, 0.05, 0), 3F, col(), 14);
        FxNet.spiral(l, spot, 0.7F, t.getBbHeight() + 0.5F, col(), 18, t.getId());
        Fx.sound(l, spot, SoundEvents.AMETHYST_BLOCK_BREAK, 0.7F, 0.6F);
        Fx.sound(l, spot, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.6F, 0.8F);
        if (t instanceof ServerPlayer sp) FxNet.flash(sp, col(), 0.35F, 8);
        return true;
    }

    private static void departFx(ServerLevel l, ServerPlayer p, Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from).normalize();
        Vec3 a = from.add(0, 1, 0), b = to.add(0, 1, 0);
        FxNet.tunnel(l, a, b, 1.2F, col(), 20);
        FxNet.beam(l, a, b, col(), 0.12F, 10);
        FxNet.portal(l, 0, a.add(dir.scale(0.8)), dir, 1.5F, col(), 18);
        FxNet.shockwave(l, from.add(0, 0.05, 0), 3.5F, col(), 16);
        Fx.at(l, ParticleTypes.REVERSE_PORTAL, a, 50, 0.4, 0.15);
        Fx.line(l, a, b, 1.2, Fx.light(Stone.SPACE, 1.0F));
        Fx.sound(l, from, SoundEvents.ENDERMAN_TELEPORT, 0.6F, 1.3F);
        Fx.sound(l, from, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 1.6F);
    }

    private static void arriveFx(ServerLevel l, ServerPlayer p, Vec3 at) {
        Vec3 c = at.add(0, 1, 0);
        FxNet.portal(l, 0, c, p.getLookAngle().scale(-1), 1.7F, col(), 18);
        FxNet.flare(l, c, 3F, col(), 12);
        FxNet.shockwave(l, at.add(0, 0.05, 0), 4.5F, col(), 18);
        FxNet.shockwave(l, at.add(0, 0.08, 0), 2.5F, 0xFFFFFF, 12);
        FxNet.spiral(l, at, 0.8F, 2.4F, col(), 18, p.getId());
        FxNet.flash(p, col(), 0.35F, 7);
        FxNet.shake(l, at, 10, 0.6F, 6);
        Fx.at(l, ParticleTypes.END_ROD, c, 24, 0.5, 0.08);
        Fx.burstRing(l, at.add(0, 0.2, 0), 24, Fx.dust(Stone.SPACE, 1.4F), 0.3);
        Fx.sound(l, at, SoundEvents.CHORUS_FRUIT_TELEPORT, 0.6F, 1.2F);
        Fx.sound(l, at, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.6F, 1.5F);
    }
}
