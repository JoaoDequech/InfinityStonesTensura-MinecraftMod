package com.infinitystones.tensura.skill.ability.extra.v2d;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.MenuNet;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import com.infinitystones.tensura.skill.ability.extra.time.TimeNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Race Against Time (Time). Left (aim): place a numbered checkpoint gate (up to 12; gate 1 is start and finish).
 * Right (menu): start the race / clear the course. Alt+scroll = laps (1-5). After a 3-2-1 countdown on giant clock
 * digits everyone who runs through the start gate takes part; each lap is timed, a translucent green ghost of the best
 * run races alongside, and the ranking is shown only to the participants. Sneak + left clears the course; it also ends
 * when the caster logs out.
 */
@EventBusSubscriber(modid = com.infinitystones.tensura.InfinityStones.MOD_ID)
public final class TimeRace implements Ability {
    public static final TimeRace INSTANCE = new TimeRace();
    private static final String NODE = "time_race", MODE = "time.time_race";
    private static final int GREEN = 0x2BD56A, MAX_GATES = 12, COUNTDOWN = 60, JOIN_WINDOW = 20 * 15, MAX_SAMPLES = 20 * 60 * 10, FINISH_GRACE = 20 * 90;
    private static final float RADIUS = 3F;

    private record Sample(double x, double y, double z, float yaw) {}

    private static final class Gate {
        final Vec3 pos; Vec3 normal; final int id = V2dNet.newId();
        Gate(Vec3 pos, Vec3 normal) { this.pos = pos; this.normal = normal; }
        Vec3 center() { return pos.add(0, RADIUS, 0); }
    }

    private static final class Racer {
        final UUID id; String name; int startTick; int lap = 1, next = 1; int lapStart; long finishTick = -1;
        final List<Integer> laps = new ArrayList<>();
        final List<Sample> run = new ArrayList<>();
        Vec3 last; boolean out;
        Racer(UUID id, String name, int startTick) { this.id = id; this.name = name; this.startTick = startTick; this.lapStart = startTick; }
    }

    private static final class Course {
        final UUID owner; final ServerLevel level; final List<Gate> gates = new ArrayList<>();
        final Map<UUID, Racer> racers = new LinkedHashMap<>();
        List<Sample> best; int bestTime = Integer.MAX_VALUE;
        boolean racing, dead; int raceAge = -COUNTDOWN, laps = 3, firstFinish = -1;
        Tasks.Handle handle;
        Course(UUID owner, ServerLevel level) { this.owner = owner; this.level = level; }
    }

    private static final Map<UUID, Course> COURSES = new HashMap<>();

    @Override public Stone stone() { return Stone.TIME; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        if (alt) return MenuNet.open(p, Stone.TIME, NODE, this) || Fx.fail(p, "infinity.extra.v2d.race.no_menu");
        if (p.isShiftKeyDown()) return clear(p);
        return place(p);
    }

    @Override
    public List<MenuNet.Option> menu(ServerPlayer p) {
        return List.of(MenuNet.opt("infinity.extra.v2d.race.start", "minecraft:clock", false),
                MenuNet.opt("infinity.extra.v2d.race.clear", "minecraft:barrier", false));
    }

    @Override
    public void pick(ServerPlayer p, int index) {
        if (index == 0) begin(p);
        else if (index == 1) clear(p);
    }

    // ------------------------------------------------------------------ course

    private static boolean place(ServerPlayer p) {
        ServerLevel l = p.serverLevel();
        Course c = COURSES.get(p.getUUID());
        if (c != null && c.level != l) { end(c); c = null; }
        if (c != null && c.racing) return Fx.fail(p, "infinity.extra.v2d.race.running");
        if (c == null) {
            c = new Course(p.getUUID(), l);
            COURSES.put(p.getUUID(), c);
            Course course = c;
            c.handle = Tasks.add(new Tasks.Task() {
                @Override public boolean tick(int age) { return tickCourse(course, age); }
                @Override public void end() { TimeRace.end(course); }
            });
        }
        if (c.gates.size() >= MAX_GATES) return Fx.fail(p, "infinity.extra.v2d.race.max");
        Vec3 at = Aim.ground(p, 64);
        Vec3 look = p.getLookAngle();
        Vec3 normal = new Vec3(look.x, 0, look.z);
        if (normal.lengthSqr() < 1e-4) normal = new Vec3(0, 0, 1);
        c.gates.add(new Gate(at, normal.normalize()));
        orient(c);
        c.best = null; c.bestTime = Integer.MAX_VALUE;
        Gate g = c.gates.get(c.gates.size() - 1);
        sendGate(c, c.gates.size() - 1, 80);
        FxNet.shockwave(l, at.add(0, 0.1, 0), 4F, GREEN, 18);
        FxNet.flare(l, g.center(), 3F, GREEN, 10);
        Fx.sound(l, at, SoundEvents.BEACON_ACTIVATE, 0.5F, 1.6F);
        Fx.sound(l, at, SoundEvents.NOTE_BLOCK_CHIME, 0.5F, 0.8F + 0.06F * c.gates.size());
        Fx.at(l, Fx.light(Stone.TIME, 1F), at.add(0, RADIUS, 0), 30, RADIUS * 0.6, 0.02);
        Fx.bar(p, Stone.TIME, "infinity.extra.v2d.race.placed", c.gates.size(), MAX_GATES);
        return true;
    }

    /** Every gate faces along the course: from the previous gate towards the next one. */
    private static void orient(Course c) {
        int n = c.gates.size();
        if (n < 2) return;
        for (int i = 0; i < n; i++) {
            Vec3 from = c.gates.get(Math.max(0, i - 1)).pos, to = c.gates.get(Math.min(n - 1, i + 1)).pos;
            if (i == 0) { from = c.gates.get(0).pos; to = c.gates.get(1).pos; }
            Vec3 d = new Vec3(to.x - from.x, 0, to.z - from.z);
            if (d.lengthSqr() > 1e-3) c.gates.get(i).normal = d.normalize();
        }
        for (int i = 0; i < n; i++) sendGate(c, i, 80);
    }

    private static void sendGate(Course c, int i, int life) {
        Gate g = c.gates.get(i);
        V2dNet.gate(c.level, g.id, g.center(), g.normal, RADIUS, GREEN, i + 1, i == 0, life);
    }

    private static boolean clear(ServerPlayer p) {
        Course c = COURSES.get(p.getUUID());
        if (c == null) return Fx.fail(p, "infinity.extra.v2d.race.none");
        end(c);
        Fx.bar(p, Stone.TIME, "infinity.extra.v2d.race.cleared");
        return true;
    }

    private static void end(Course c) {
        if (c.dead && COURSES.get(c.owner) != c) return;
        c.dead = true;
        COURSES.remove(c.owner, c);
        for (Gate g : c.gates) {
            V2dNet.kill(c.level, g.pos, g.id);
            Fx.at(c.level, Fx.light(Stone.TIME, 1F), g.center(), 20, RADIUS * 0.7, 0.03);
        }
        if (c.handle != null) c.handle.cancel();
    }

    // ------------------------------------------------------------------ the race

    private static void begin(ServerPlayer p) {
        Course c = COURSES.get(p.getUUID());
        if (c == null || c.gates.size() < 2) { Fx.fail(p, "infinity.extra.v2d.race.need_two"); return; }
        if (c.racing) { Fx.fail(p, "infinity.extra.v2d.race.running"); return; }
        c.racing = true;
        c.raceAge = -COUNTDOWN;
        c.firstFinish = -1;
        c.racers.clear();
        c.laps = ExtraSettings.get(p, MODE, 3, 1, 5, 1);
        Fx.bar(p, Stone.TIME, "infinity.extra.v2d.race.countdown", c.laps);
    }

    private static boolean tickCourse(Course c, int age) {
        if (c.dead) return false;
        ServerLevel l = c.level;
        ServerPlayer owner = l.getServer().getPlayerList().getPlayer(c.owner);
        if (owner == null) return false;
        if (age % 40 == 20) for (int i = 0; i < c.gates.size(); i++) sendGate(c, i, 80);
        if (!c.racing) return true;
        Gate start = c.gates.get(0);
        int t = c.raceAge++;
        if (t < 0) {
            int left = -t;
            if (left % 20 == 0) {
                int num = left / 20;
                V2dNet.digit(l, start.center().add(0, 0.3, 0), num, 2.4F, GREEN, 22);
                Fx.sound(l, start.pos, SoundEvents.NOTE_BLOCK_HAT, 0.7F, 0.7F + (3 - num) * 0.2F);
                Fx.sound(l, start.pos, SoundEvents.BELL_BLOCK, 0.4F, 1.0F + (3 - num) * 0.25F);
                FxNet.shockwave(l, start.pos.add(0, 0.1, 0), 3F + (3 - num), GREEN, 14);
                FxNet.shake(l, start.pos, 24, 0.25F, 4);
            }
            return true;
        }
        if (t == 0) {
            V2dNet.digit(l, start.center().add(0, 0.3, 0), 0, 2.6F, 0xFFFFFF, 26);
            FxNet.shockwave(l, start.pos.add(0, 0.1, 0), 10F, GREEN, 24);
            FxNet.flare(l, start.center(), 6F, GREEN, 14);
            FxNet.flashAround(l, start.pos, 14, 0xD8FFE8, 0.25F, 8);
            Fx.sound(l, start.pos, SoundEvents.BEACON_POWER_SELECT, 0.7F, 1.4F);
            Fx.sound(l, start.pos, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.6F, 1.0F);
            join(c, owner, 0);
            for (ServerPlayer sp : l.players())
                if (sp != owner && sp.position().distanceToSqr(start.pos) <= 36 && !c.racers.containsKey(sp.getUUID())) join(c, sp, 0);
        }
        // players walking into the start gate during the join window
        if (t < JOIN_WINDOW) for (ServerPlayer sp : l.players()) {
            if (sp.isSpectator() || c.racers.containsKey(sp.getUUID())) continue;
            if (sp.getBoundingBox().getCenter().distanceToSqr(start.center()) <= RADIUS * RADIUS) join(c, sp, t);
        }
        for (Racer r : c.racers.values()) tickRacer(c, r, l, t);
        // ghost of the best run
        if (c.best != null && t < c.best.size()) {
            Sample s = c.best.get(t);
            TimeNet.ghost(l, new Vec3(s.x, s.y, s.z), 0.6F, 1.8F, 0, 2, s.yaw);
        }
        boolean allDone = true;
        for (Racer r : c.racers.values()) if (!r.out && r.finishTick < 0) allDone = false;
        if (c.firstFinish >= 0 && t - c.firstFinish > FINISH_GRACE) allDone = true;
        if (allDone && !c.racers.isEmpty() && t > 40) finishRace(c);
        return true;
    }

    private static void join(Course c, ServerPlayer p, int t) {
        Racer r = new Racer(p.getUUID(), p.getGameProfile().getName(), t);
        r.last = p.position();
        c.racers.put(p.getUUID(), r);
        Gate start = c.gates.get(0);
        Fx.sound(c.level, p.position(), SoundEvents.NOTE_BLOCK_BELL, 0.5F, 1.4F);
        Fx.at(c.level, Fx.light(Stone.TIME, 1F), p.position().add(0, 1, 0), 20, 0.4, 0.05);
        Fx.bar(p, Stone.TIME, "infinity.extra.v2d.race.joined", c.laps);
        FxNet.flare(c.level, start.center(), 4F, GREEN, 8);
    }

    private static void tickRacer(Course c, Racer r, ServerLevel l, int t) {
        if (r.out || r.finishTick >= 0) return;
        ServerPlayer p = l.getServer().getPlayerList().getPlayer(r.id);
        if (p == null || p.isRemoved() || p.serverLevel() != l || !p.isAlive()) { r.out = true; return; }
        Vec3 now = p.position();
        if (r.run.size() < MAX_SAMPLES) r.run.add(new Sample(now.x, now.y, now.z, p.getYRot()));
        Vec3 prev = r.last == null ? now : r.last;
        r.last = now;
        int gateIndex = r.next % c.gates.size();
        Gate g = c.gates.get(gateIndex);
        if (crossed(g, prev.add(0, 0.9, 0), now.add(0, 0.9, 0))) {
            boolean lapDone = gateIndex == 0;
            Fx.sound(l, g.pos, SoundEvents.NOTE_BLOCK_CHIME, 0.6F, 1.0F + 0.08F * r.next);
            FxNet.shockwave(l, g.pos.add(0, 0.1, 0), 4.5F, GREEN, 12);
            FxNet.flare(l, g.center(), 4.5F, lapDone ? 0xFFFFFF : GREEN, 8);
            Fx.at(l, ParticleTypes.END_ROD, g.center(), 24, RADIUS * 0.8, 0.05);
            if (lapDone) {
                int lapTime = t - r.lapStart;
                r.laps.add(lapTime);
                r.lapStart = t;
                Fx.sound(l, g.pos, SoundEvents.PLAYER_LEVELUP, 0.5F, 1.2F);
                if (r.lap >= c.laps) {
                    r.finishTick = t - r.startTick;
                    if (c.firstFinish < 0) c.firstFinish = t;
                    if (r.finishTick < c.bestTime && r.run.size() < MAX_SAMPLES) {
                        c.bestTime = (int) r.finishTick;
                        c.best = new ArrayList<>(r.run);
                    }
                    FxNet.shockwave(l, g.pos.add(0, 0.1, 0), 9F, 0xFFFFFF, 20);
                    Fx.sound(l, g.pos, SoundEvents.FIREWORK_ROCKET_TWINKLE, 0.7F, 1.0F);
                    Fx.sound(l, g.pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.5F, 1.0F);
                    title(p, Component.translatable("infinity.extra.v2d.race.finished").withColor(GREEN), Component.literal(fmt((int) r.finishTick)).withColor(0xFFFFFF));
                    return;
                }
                r.lap++;
                Fx.bar(p, Stone.TIME, "infinity.extra.v2d.race.lap", r.lap, c.laps, fmt(lapTime));
            }
            r.next++;
        }
        if (t % 4 == 0) Fx.bar(p, Stone.TIME, "infinity.extra.v2d.race.hud", r.lap, c.laps, (r.next % c.gates.size()) + 1, fmt(t - r.startTick));
    }

    /** The segment a to b passes through the gate's plane inside its ring. */
    private static boolean crossed(Gate g, Vec3 a, Vec3 b) {
        Vec3 center = g.center();
        double d0 = a.subtract(center).dot(g.normal), d1 = b.subtract(center).dot(g.normal);
        if (d0 == d1 || (d0 < 0) == (d1 < 0)) return false;
        Vec3 hit = a.lerp(b, d0 / (d0 - d1));
        return hit.distanceToSqr(center) <= (RADIUS * 1.15) * (RADIUS * 1.15);
    }

    private static void finishRace(Course c) {
        c.racing = false;
        List<Racer> order = new ArrayList<>(c.racers.values());
        order.sort(Comparator.<Racer>comparingLong(r -> r.finishTick < 0 ? Long.MAX_VALUE : r.finishTick));
        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            Racer r = order.get(i);
            lines.add(Component.translatable(r.finishTick < 0 ? "infinity.extra.v2d.race.rank_dnf" : "infinity.extra.v2d.race.rank",
                    i + 1, r.name, r.finishTick < 0 ? "" : fmt((int) r.finishTick)));
        }
        Map<UUID, Integer> place = new HashMap<>();
        for (int i = 0; i < order.size(); i++) place.put(order.get(i).id, i + 1);
        ServerLevel l = c.level;
        for (Racer r : order) {
            ServerPlayer p = l.getServer().getPlayerList().getPlayer(r.id);
            if (p == null) continue;
            int rank = place.get(r.id);
            title(p, Component.translatable("infinity.extra.v2d.race.place", rank, order.size()).withColor(rank == 1 ? 0xFFD54A : GREEN),
                    Component.translatable("infinity.extra.v2d.race.total", r.finishTick < 0 ? "--" : fmt((int) r.finishTick)));
        }
        Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) {
                if (age % 50 != 20) return age < 50 * (lines.size() + 3);
                int shown = age / 50;
                if (shown >= lines.size() + 1) return false;
                for (Racer r : order) {
                    ServerPlayer p = l.getServer().getPlayerList().getPlayer(r.id);
                    if (p != null) p.displayClientMessage(shown < lines.size() ? lines.get(shown).copy().withColor(GREEN) : Component.translatable("infinity.extra.v2d.race.best", c.bestTime == Integer.MAX_VALUE ? "--" : fmt(c.bestTime)).withColor(GREEN), true);
                }
                return true;
            }
        });
    }

    private static void title(ServerPlayer p, Component title, Component sub) {
        p.connection.send(new ClientboundSetTitlesAnimationPacket(5, 70, 15));
        p.connection.send(new ClientboundSetSubtitleTextPacket(sub));
        p.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    private static String fmt(int ticks) {
        int cs = ticks * 5;
        return String.format("%d:%02d.%02d", cs / 6000, (cs / 100) % 60, cs % 100);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) { Course c = COURSES.get(p.getUUID()); if (c != null) end(c); }
    }

    private TimeRace() {}
}
