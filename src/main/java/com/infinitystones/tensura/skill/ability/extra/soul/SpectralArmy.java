package com.infinitystones.tensura.skill.ability.extra.soul;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Spectral Army (Soul). Left: the souls of creatures that died around you in the last 60 s climb out of the ground and
 * fight for you for 30 s (same kind of creature, tougher, glowing, never dropping anything). Right: calls spectral
 * knights from the other side when nobody died nearby. Up to 8 ghosts at a time; the oldest fades when you raise more.
 */
public final class SpectralArmy implements Ability {
    public static final SpectralArmy INSTANCE = new SpectralArmy();
    private static final String K = "infinity.extra.army.";
    private static final int LIFE = 600;

    @Override public Stone stone() { return Stone.SOUL; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        ServerLevel l = p.serverLevel();
        int room = Ghosts.CAP_ARMY - Ghosts.count(p.getUUID(), Ghosts.ARMY);
        List<Raise> raises = new ArrayList<>();
        if (alt) {
            for (int i = 0; i < 4; i++) {
                double a = p.getYRot() * Mth.DEG_TO_RAD + (i - 1.5) * 0.7;
                Vec3 at = Ground.below(l, p.position().add(-Math.sin(a) * 3.2, 1.5, Math.cos(a) * 3.2), 6);
                raises.add(new Raise(EntityType.HUSK, at, (float) (p.getYRot() + 180), 40F, 9F));
            }
        } else {
            for (SoulKit.Corpse c : SoulKit.take(l, p.position(), 24, 1200, Ghosts.CAP_ARMY)) {
                float hp = Mth.clamp(c.maxHealth() * 1.2F, 20F, 60F);
                raises.add(new Raise(c.type(), Ground.below(l, c.pos().add(0, 1, 0), 5), c.yaw(), hp, Mth.clamp(hp * 0.28F, 6F, 15F)));
            }
            if (raises.isEmpty()) return Fx.fail(p, K + "none");
        }
        while (raises.size() > Math.max(room, 1)) raises.remove(raises.size() - 1);
        cast(p, l, raises, alt);
        return true;
    }

    private record Raise(EntityType<?> type, Vec3 at, float yaw, float hp, float dmg) {}

    private static void cast(ServerPlayer p, ServerLevel l, List<Raise> raises, boolean alt) {
        Vec3 c = p.position();
        int ORANGE = SoulKit.ORANGE;
        SoulKit.circle(l, c, 6.5F, ORANGE, 60);
        FxNet.pillar(l, 0, c, 1.4F, 7F, ORANGE, 24);
        FxNet.spiral(l, c, 2.4F, 5F, SoulKit.LIGHT, 30, p.getId());
        FxNet.spiral(l, c, 3.4F, -4F, SoulKit.SPIRIT, 24, -1);
        FxNet.shake(l, c, 24, 0.35F, 12);
        FxNet.flash(p, SoulKit.LIGHT, 0.18F, 8);
        Fx.sound(l, c, SoundEvents.EVOKER_PREPARE_SUMMON, 0.6F, 0.7F);
        Fx.sound(l, c, SoundEvents.SOUL_ESCAPE.value(), 0.6F, 0.6F);
        Fx.sound(l, c, SoundEvents.WITHER_AMBIENT, 0.35F, 0.6F);
        Fx.burstRing(l, c.add(0, 0.2, 0), 36, ParticleTypes.SOUL, 0.35);
        Fx.ring(l, c.add(0, 0.1, 0), 6.5, 64, Fx.dust(ORANGE, 1.4F));
        Fx.bar(p, Stone.SOUL, K + (alt ? "called" : "raised"), raises.size());

        // every grave shines first, then the soul climbs out
        for (int i = 0; i < raises.size(); i++) {
            Raise r = raises.get(i);
            Vec3 ground = r.at();
            FxNet.runes(l, 0, ground.add(0, 0.05, 0), 1.9F, ORANGE, 24, 2F);
            FxNet.pillar(l, 0, ground, 0.55F, 3.2F, SoulKit.LIGHT, 16);
            FxNet.beam(l, c.add(0, 1.2, 0), ground.add(0, 1, 0), SoulKit.EMBER, 0.05F, 10);
        }
        Tasks.add(new Tasks.Task() {
            int next;
            @Override public boolean tick(int age) {
                if (!p.isAlive() || p.hasDisconnected()) return false;
                for (Raise r : raises) {
                    if (age < 12 && age % 2 == 0) Fx.at(l, ParticleTypes.SOUL, r.at().add(0, age * 0.12, 0), 2, 0.25, 0.02);
                }
                if (age >= 12 && (age - 12) % 3 == 0 && next < raises.size()) {
                    Raise r = raises.get(next++);
                    Vec3 g = r.at();
                    Ghosts.Ghost gh = Ghosts.spawn(p, r.type(), g, r.yaw(), Ghosts.ARMY, LIFE, r.hp(), r.dmg(), ORANGE, null, 0);
                    if (gh != null) {
                        FxNet.shockwave(l, g.add(0, 0.1, 0), 3F, ORANGE, 14);
                        FxNet.shake(l, g, 10, 0.2F, 6);
                        Fx.at(l, ParticleTypes.SOUL_FIRE_FLAME, g.add(0, 1, 0), 14, 0.35, 0.08);
                        Fx.sound(l, g, SoundEvents.ZOMBIE_VILLAGER_CONVERTED, 0.35F, 1.6F);
                    }
                }
                return next < raises.size() || age < 14;
            }
        });
    }
}
