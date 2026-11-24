package com.infinitystones.tensura.skill.ability.extra.v2c;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.MenuNet;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.soul.SoulKit;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Soul, normal: an animated will-o'-wisp that flies ahead toward the last death, the bed or an ally (shown only to its owner). */
public final class WispGuide implements Ability {
    public static final WispGuide INSTANCE = new WispGuide();
    static final String NODE = "wisp_guide", MODE = "soul.wisp_guide", K = "infinity.extra.v2c.wisp.", ALLY = "infinity_wisp_ally";
    private static final int MAX_ALLIES = 12;

    private static final class Wisp {
        final UUID owner; final ServerLevel level; final int id = V2CNet.newId();
        Vec3 pos; Vec3 goal; @Nullable UUID follow; int age; Tasks.Handle handle;
        Wisp(UUID owner, ServerLevel level, Vec3 pos, Vec3 goal, @Nullable UUID follow) { this.owner = owner; this.level = level; this.pos = pos; this.goal = goal; this.follow = follow; }
    }

    private record Goal(@Nullable ResourceKey<Level> dim, Vec3 pos, @Nullable UUID follow) {}

    private static final Map<UUID, Wisp> WISPS = new HashMap<>();

    @Override public Stone stone() { return Stone.SOUL; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        if (alt) return MenuNet.open(p, Stone.SOUL, NODE, this) || Fx.fail(p, K + "no_menu");
        if (WISPS.containsKey(p.getUUID())) {
            stop(p.getUUID(), false);
            Fx.bar(p, Stone.SOUL, K + "off");
            return true;
        }
        return begin(p);
    }

    private static boolean begin(ServerPlayer p) {
        Goal g = goal(p);
        if (g == null) return false;
        ServerLevel l = p.serverLevel();
        Vec3 start = p.getEyePosition().add(p.getLookAngle().scale(2));
        if (g.dim() != null && g.dim() != l.dimension()) {
            Fx.bar(p, Stone.SOUL, K + "other_dim", Component.literal(g.dim().location().toString()));
            Fx.sound(l, start, SoundEvents.SOUL_ESCAPE.value(), 0.5F, 1.2F);
            int id = V2CNet.newId();
            V2CNet.to(p, V2CNet.msg(V2CNet.WISP, id, 2, 0, 0, start, 0));
            Tasks.add(new Tasks.Task() {
                @Override public boolean tick(int age) {
                    ServerPlayer pl = V2CNet.player(l.getServer(), p.getUUID());
                    if (pl == null || age > 50) return false;
                    V2CNet.to(pl, V2CNet.msg(V2CNet.WISP, id, 2, 0, 0, start.add(0, age * 0.18, 0), 0));
                    return true;
                }
                @Override public void end() {
                    ServerPlayer pl = V2CNet.player(l.getServer(), p.getUUID());
                    if (pl != null) V2CNet.to(pl, V2CNet.msg(V2CNet.WISP_END, id, 0, 0, 0, start, 0));
                }
            });
            return true;
        }
        Wisp w = new Wisp(p.getUUID(), l, start, g.pos(), g.follow());
        WISPS.put(p.getUUID(), w);
        SoulKit.circle(l, p.position(), 2.6F, SoulKit.ORANGE, 30);
        Fx.sound(l, start, SoundEvents.SOUL_ESCAPE.value(), 0.6F, 1.1F);
        Fx.sound(l, start, SoundEvents.FIRECHARGE_USE, 0.4F, 1.4F);
        Fx.bar(p, Stone.SOUL, K + "on");
        w.handle = Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) { return WispGuide.tick(w); }
            @Override public void end() { WispGuide.finish(w, false); }
        });
        return true;
    }

    private static @Nullable Goal goal(ServerPlayer p) {
        int kind = ExtraSettings.choice(p, MODE, 0);
        if (kind == 1) {
            BlockPos bed = p.getRespawnPosition();
            if (bed == null) {
                BlockPos sp = p.server.overworld().getSharedSpawnPos();
                return new Goal(Level.OVERWORLD, Vec3.atCenterOf(sp), null);
            }
            return new Goal(p.getRespawnDimension(), Vec3.atCenterOf(bed), null);
        }
        if (kind == 2) {
            String s = Rules.data(p).getString(ALLY);
            ServerPlayer ally = null;
            try { ally = s.isEmpty() ? null : V2CNet.player(p.server, UUID.fromString(s)); } catch (IllegalArgumentException ignored) { }
            if (ally == null || ally == p) { Fx.fail(p, K + "no_ally"); return null; }
            if (ally.serverLevel() != p.serverLevel()) { Fx.fail(p, K + "ally_dim"); return null; }
            return new Goal(null, ally.position().add(0, 1, 0), ally.getUUID());
        }
        var death = p.getLastDeathLocation();
        if (death.isEmpty()) { Fx.fail(p, K + "no_death"); return null; }
        GlobalPos gp = death.get();
        return new Goal(gp.dimension(), Vec3.atCenterOf(gp.pos()), null);
    }

    private static boolean tick(Wisp w) {
        if (WISPS.get(w.owner) != w) return false;
        ServerPlayer p = V2CNet.player(w.level.getServer(), w.owner);
        if (p == null || !p.isAlive() || p.serverLevel() != w.level) return false;
        w.age++;
        if (w.follow != null) {
            ServerPlayer ally = V2CNet.player(w.level.getServer(), w.follow);
            if (ally == null || ally.serverLevel() != w.level) { Fx.bar(p, Stone.SOUL, K + "ally_lost"); return false; }
            w.goal = ally.position().add(0, 1, 0);
        }
        double speed = 0.14 + 0.07 * ExtraSettings.get(p, MODE, 3, 1, 8, 1);
        Vec3 eye = p.getEyePosition();
        if (p.position().distanceToSqr(w.goal) < 25) {
            w.pos = w.pos.lerp(w.goal.add(0, 1.2, 0), 0.5);
            arrive(w, p);
            return false;
        }
        Vec3 toGoal = w.goal.subtract(eye);
        Vec3 dir = toGoal.normalize();
        int state = 0;
        double ahead = w.pos.subtract(eye).dot(dir);
        double dist = w.pos.distanceTo(eye);
        if (dist > 48) {
            w.pos = eye.add(dir.scale(4));
        } else if (ahead < 1.5) {
            Vec3 lead = eye.add(dir.scale(4.5));
            Vec3 d = lead.subtract(w.pos);
            w.pos = w.pos.add(d.length() > speed * 3 ? d.normalize().scale(speed * 3) : d);
        } else if (dist > 14) {
            state = 1;
        } else {
            Vec3 move = dir;
            for (int i = 0; i < 4; i++) {
                var hit = w.level.clip(new ClipContext(w.pos, w.pos.add(move.scale(speed + 1.8)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
                if (hit.getType() == HitResult.Type.MISS) break;
                move = move.add(0, 1.1, 0).normalize();
            }
            Vec3 next = w.pos.add(move.scale(speed));
            if (next.subtract(eye).lengthSqr() < 13 * 13) w.pos = next;
            else state = 1;
        }
        V2CNet.to(p, V2CNet.msg(V2CNet.WISP, w.id, state, 0, (float) speed, w.pos, 0));
        if (w.age % 32 == 0) Fx.sound(w.level, w.pos, SoundEvents.SOUL_ESCAPE.value(), 0.25F, 1.5F);
        if (state == 1 && w.age % 40 == 0) Fx.sound(w.level, w.pos, SoundEvents.AMETHYST_BLOCK_CHIME, 0.3F, 1.8F);
        return true;
    }

    private static void arrive(Wisp w, ServerPlayer p) {
        V2CNet.to(p, V2CNet.msg(V2CNet.WISP, w.id, 2, 0, 0, w.pos, 0));
        V2CNet.to(p, V2CNet.msg(V2CNet.WISP_END, w.id, 1, 0, 0, w.pos, 0));
        Fx.sound(w.level, w.pos, SoundEvents.BELL_RESONATE, 0.5F, 1.6F);
        Fx.sound(w.level, w.pos, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.0F);
        Fx.sound(w.level, w.pos, SoundEvents.SOUL_ESCAPE.value(), 0.5F, 0.8F);
        Fx.bar(p, Stone.SOUL, K + "arrived");
        WISPS.remove(w.owner, w);
    }

    private static void finish(Wisp w, boolean arrived) {
        if (WISPS.get(w.owner) == w) {
            WISPS.remove(w.owner);
            ServerPlayer p = V2CNet.player(w.level.getServer(), w.owner);
            if (p != null) V2CNet.to(p, V2CNet.msg(V2CNet.WISP_END, w.id, 0, 0, 0, w.pos, 0));
        }
    }

    private static void stop(UUID owner, boolean arrived) {
        Wisp w = WISPS.get(owner);
        if (w == null) return;
        finish(w, arrived);
        if (w.handle != null) w.handle.cancel();
    }

    // ------------------------------------------------------------------ menu

    private static List<ServerPlayer> allies(ServerPlayer p) {
        List<ServerPlayer> out = new ArrayList<>();
        for (ServerPlayer o : p.serverLevel().players()) if (o != p && !o.isSpectator()) out.add(o);
        out.sort(Comparator.comparing(o -> o.getGameProfile().getName()));
        return out.size() > MAX_ALLIES ? out.subList(0, MAX_ALLIES) : out;
    }

    @Override
    public List<MenuNet.Option> menu(ServerPlayer p) {
        int kind = ExtraSettings.choice(p, MODE, 0);
        String cur = Rules.data(p).getString(ALLY);
        List<MenuNet.Option> out = new ArrayList<>();
        out.add(MenuNet.opt(K + "death", "minecraft:skeleton_skull", kind == 0));
        out.add(MenuNet.opt(K + "bed", "minecraft:red_bed", kind == 1));
        for (ServerPlayer o : allies(p))
            out.add(new MenuNet.Option(o.getGameProfile().getName(), K + "ally.desc", "minecraft:player_head", kind == 2 && o.getUUID().toString().equals(cur)));
        return out;
    }

    @Override
    public void pick(ServerPlayer p, int index) {
        if (index < 0) return;
        if (index <= 1) ExtraSettings.setChoice(p, MODE, index);
        else {
            List<ServerPlayer> al = allies(p);
            if (index - 2 >= al.size()) return;
            ExtraSettings.setChoice(p, MODE, 2);
            Rules.data(p).putString(ALLY, al.get(index - 2).getUUID().toString());
        }
        Fx.bar(p, Stone.SOUL, K + "chosen" + (index == 0 ? ".death" : index == 1 ? ".bed" : ".ally"));
        if (WISPS.containsKey(p.getUUID())) {
            stop(p.getUUID(), false);
            begin(p);
        }
    }

    private WispGuide() {}
}
