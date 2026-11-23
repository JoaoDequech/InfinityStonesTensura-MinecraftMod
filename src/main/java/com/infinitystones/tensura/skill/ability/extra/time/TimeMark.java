package com.infinitystones.tensura.skill.ability.extra.time;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Time Mark: left = mark the aimed creature (or yourself when nothing is aimed); right = mark yourself; using it again
 * (either click) rewinds now, otherwise it rewinds by itself after 10 s. A green hourglass floats over the marked one;
 * at rewind it goes back to the marked instant along a trail of green ghosts. Allies (and you) also get their health,
 * fire and hunger back; enemies only their position. Bosses cannot be marked. One mark per player.
 */
public final class TimeMark implements Ability {
    public static final TimeMark INSTANCE = new TimeMark();
    private static final int DURATION = 200;

    private static final class Mark {
        final UUID owner; final LivingEntity target; final ServerLevel level; final Vec3 pos; final float yaw, pitch, health;
        final int fire, food; final float sat; final boolean friend;
        final List<Vec3> path = new ArrayList<>();
        final int id = TimeNet.newId();
        Tasks.Handle handle; boolean rewind;
        Mark(ServerPlayer owner, LivingEntity t) {
            this.owner = owner.getUUID(); target = t; level = (ServerLevel) t.level(); pos = t.position(); yaw = t.getYRot(); pitch = t.getXRot();
            health = t.getHealth(); fire = t.getRemainingFireTicks(); friend = Rules.isAlly(owner, t);
            if (t instanceof ServerPlayer sp) { food = sp.getFoodData().getFoodLevel(); sat = sp.getFoodData().getSaturationLevel(); }
            else { food = -1; sat = 0; }
        }
    }

    private static final Map<UUID, Mark> MARKS = new HashMap<>();

    @Override public Stone stone() { return Stone.TIME; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        Mark m = MARKS.get(p.getUUID());
        if (m != null) { m.rewind = true; m.handle.cancel(); return true; }
        LivingEntity t = alt ? p : Aim.living(p, 40, e -> Rules.isAlly(p, e) || Rules.canControl(p, e));
        if (t == null) t = p;
        Mark mk = new Mark(p, t);
        MARKS.put(p.getUUID(), mk);
        ServerLevel l = mk.level;
        Vec3 c = t.position();
        TimeNet.hourglass(l, mk.id, t, DURATION + 10, 0, false);
        FxNet.clock(l, 0, c.add(0, 0.05, 0), Math.max(1.2F, t.getBbWidth() * 1.5F), Stone.TIME.color(), 40, 3F);
        FxNet.spiral(l, c, t.getBbWidth() + 0.4F, t.getBbHeight() + 1, Stone.TIME.color(), 20, t.getId());
        Fx.sound(l, c, SoundEvents.BELL_BLOCK, 0.5F, 1.8F);
        Fx.sound(l, c, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.2F);
        if (t instanceof ServerPlayer sp && sp != p) Fx.barGray(sp, "infinity.extra.time.mark.marked_you");
        Fx.bar(p, Stone.TIME, "infinity.extra.time.mark.on", t.getDisplayName());
        mk.handle = Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) {
                if (MARKS.get(mk.owner) != mk) return false;
                if (!mk.target.isAlive()) return false;
                if (age % 4 == 0 && mk.path.size() < 60 && mk.target.level() == mk.level) mk.path.add(mk.target.position());
                if (age % 20 == 0 && age > 0) {
                    Fx.sound(mk.level, mk.target.position(), SoundEvents.NOTE_BLOCK_HAT.value(), 0.3F, 1.4F);
                    TimeNet.hourglass(mk.level, mk.id, mk.target, DURATION - age + 10, age / (float) DURATION, false);
                }
                if (age >= DURATION) { mk.rewind = true; return false; }
                return true;
            }
            @Override public void end() { finish(mk); }
        });
        return true;
    }

    private static void finish(Mark m) {
        MARKS.remove(m.owner, m);
        ServerLevel l = m.level;
        LivingEntity t = m.target;
        TimeNet.kill(l, m.pos, m.id);
        if (!m.rewind) return;
        ServerPlayer owner = l.getServer().getPlayerList().getPlayer(m.owner);
        if (!t.isAlive()) { if (owner != null) Fx.fail(owner, "infinity.extra.time.mark.lost"); return; }
        if (t.level() != l && !(t instanceof ServerPlayer)) { if (owner != null) Fx.fail(owner, "infinity.extra.time.mark.lost"); return; }
        Vec3 from = t.position();
        // ghost trail replaying the path backwards
        if (t.level() == l) {
            m.path.add(from);
            int n = m.path.size();
            int ghosts = Math.min(16, n);
            for (int g = 0; g < ghosts; g++) {
                int idx = n - 1 - (int) Math.round(g * (n - 1) / (double) Math.max(1, ghosts - 1));
                TimeNet.ghost(l, m.path.get(idx), t.getBbWidth(), t.getBbHeight(), g * 2, 14, t.getYRot());
            }
            TimeNet.hourglass(l, m.id + 1_000_000, t, 20, 0, true);
            FxNet.spiral(l, from, t.getBbWidth() + 0.5F, -(t.getBbHeight() + 1.5F), Stone.TIME.color(), 16, -1);
            Fx.at(l, Fx.light(Stone.TIME, 1.2F), from.add(0, t.getBbHeight() / 2, 0), 25, 0.4, 0.05);
        }
        // back to the instant
        if (t instanceof ServerPlayer sp) sp.teleportTo(l, m.pos.x, m.pos.y, m.pos.z, m.yaw, m.pitch);
        else { t.teleportTo(m.pos.x, m.pos.y, m.pos.z); t.setYRot(m.yaw); t.setYHeadRot(m.yaw); }
        t.setDeltaMovement(Vec3.ZERO);
        t.hurtMarked = true;
        t.fallDistance = 0;
        if (m.friend) {
            t.setHealth(Math.min(t.getMaxHealth(), Math.max(t.getHealth(), m.health)));
            t.setRemainingFireTicks(m.fire);
            if (t instanceof ServerPlayer sp && m.food >= 0) {
                sp.getFoodData().setFoodLevel(Math.max(sp.getFoodData().getFoodLevel(), m.food));
                sp.getFoodData().setSaturation(Math.max(sp.getFoodData().getSaturationLevel(), m.sat));
            }
        }
        Vec3 to = m.pos;
        FxNet.shockwave(l, to.add(0, 0.1, 0), 3.5F, Stone.TIME.color(), 18);
        FxNet.clock(l, 0, to.add(0, 0.05, 0), 2F, Stone.TIME.color(), 30, -8F);
        FxNet.flare(l, to.add(0, t.getBbHeight() / 2, 0), 2.5F, Stone.TIME.color(), 10);
        Fx.at(l, ParticleTypes.REVERSE_PORTAL, to.add(0, t.getBbHeight() / 2, 0), 40, 0.5, 0.1);
        Fx.sound(l, to, SoundEvents.ENDERMAN_TELEPORT, 0.6F, 0.6F);
        Fx.sound(l, to, SoundEvents.BELL_RESONATE, 0.6F, 1.6F);
        if (t instanceof ServerPlayer sp) { FxNet.flash(sp, 0xBFFFD6, 0.45F, 10); FxNet.shake(l, to, 2, 0.5F, 6); }
        if (owner != null) Fx.bar(owner, Stone.TIME, "infinity.extra.time.mark.rewound", t.getDisplayName());
    }
}
