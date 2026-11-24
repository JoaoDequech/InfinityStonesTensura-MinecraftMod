package com.infinitystones.tensura.skill.ability.extra.v2d;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.compat.ClaimsCompat;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import com.infinitystones.tensura.skill.ability.extra.time.TimeNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Temporal Lag (Time, hidden). Left (aim at a player or mob): for 12 s everything the target does happens late (Alt+scroll
 * 1-4 s). The server keeps the path the target tries to walk and replays it delayed: players are pulled back
 * towards the delayed position (their client is corrected), mobs simply move late; a player's attacks and item use are
 * queued and fire late. A green clock and afterimages trail the target. Right: you get the inverse for 8 s - an aura in
 * which projectiles and creatures near you are shown 1 s ahead as prediction ghosts (only you see them).
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class TemporalLag implements Ability {
    public static final TemporalLag INSTANCE = new TemporalLag();
    private static final String MODE = "time.temporal_lag";
    private static final int GREEN = 0x2BD56A, DURATION = 240, AURA = 160, MAX_PER_CASTER = 4, MAX_QUEUE = 24;
    private static boolean replaying;

    private static final class Action {
        final int due; final Runnable run;
        Action(int due, Runnable run) { this.due = due; this.run = run; }
    }

    private static final class Lag {
        final Entity target; final ServerLevel level; final UUID caster; final int delay; final int id = V2dNet.newId();
        final Deque<Vec3> history = new ArrayDeque<>();
        final List<Action> queue = new ArrayList<>();
        Vec3 intent, last; int age;
        Lag(Entity target, UUID caster, int delayTicks) {
            this.target = target; this.level = (ServerLevel) target.level(); this.caster = caster; this.delay = delayTicks;
            intent = last = target.position();
        }
    }

    private static final Map<UUID, Lag> LAGS = new HashMap<>();
    private static final Map<UUID, Tasks.Handle> AURAS = new HashMap<>();

    @Override public Stone stone() { return Stone.TIME; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        return alt ? aura(p) : lag(p);
    }

    // ------------------------------------------------------------------ left: lag the target

    private boolean lag(ServerPlayer p) {
        LivingEntity t = Aim.living(p, 40, e -> Rules.canControl(p, e));
        if (t == null) return Fx.fail(p, "infinity.extra.v2d.lag.no_target");
        if (t instanceof ServerPlayer vp && ClaimsCompat.isProtected(vp, p)) return Fx.fail(p, "infinity.extra.v2d.lag.protected");
        long mine = LAGS.values().stream().filter(l -> l.caster.equals(p.getUUID())).count();
        Lag old = LAGS.get(t.getUUID());
        if (old == null && mine >= MAX_PER_CASTER) return Fx.fail(p, "infinity.extra.v2d.lag.max");
        int seconds = ExtraSettings.get(p, MODE, 2, 1, 4, 1);
        Lag lag = new Lag(t, p.getUUID(), seconds * 20);
        LAGS.put(t.getUUID(), lag);
        ServerLevel l = lag.level;
        Vec3 mid = t.getBoundingBox().getCenter();
        FxNet.shockwave(l, t.position().add(0, 0.1, 0), 3F, GREEN, 16);
        FxNet.flare(l, mid, 2.5F, GREEN, 10);
        Fx.sound(l, mid, SoundEvents.BEACON_DEACTIVATE, 0.6F, 1.5F);
        Fx.sound(l, mid, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 0.5F);
        Fx.at(l, Fx.light(Stone.TIME, 1F), mid, 25, 0.5, 0.05);
        Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) { return step(lag, age); }
            @Override public void end() { stop(lag); }
        });
        Fx.bar(p, Stone.TIME, "infinity.extra.v2d.lag.on", seconds);
        return true;
    }

    private static boolean step(Lag g, int age) {
        Entity t = g.target;
        if (LAGS.get(t.getUUID()) != g || age >= DURATION || !t.isAlive() || t.isRemoved() || t.level() != g.level) return false;
        g.age = age;
        ServerLevel l = g.level;
        Vec3 cur = t.position();
        Vec3 delta = cur.subtract(g.last);
        if (delta.lengthSqr() > 9) {
            g.intent = cur; g.last = cur; g.history.clear();
        } else if (!t.isPassenger()) {
            g.intent = g.intent.add(delta);
        }
        g.history.addLast(g.intent);
        while (g.history.size() > g.delay + 1) g.history.pollFirst();
        Vec3 want = t.isPassenger() ? cur : g.history.peekFirst();
        boolean forced = false;
        if (want != null && want.distanceToSqr(cur) > 0.0025) {
            if (t instanceof ServerPlayer sp) {
                if ((age & 1) == 0) {
                    sp.connection.teleport(want.x, want.y, want.z, sp.getYRot(), sp.getXRot());
                    sp.fallDistance = 0;
                    forced = true;
                }
            } else if (Ground.fits(l, t, want)) {
                t.setPos(want);
                t.fallDistance = 0;
                forced = true;
            }
        }
        g.last = forced ? want : t.position();
        // delayed attacks and item use
        for (int i = 0; i < g.queue.size(); i++) {
            Action a = g.queue.get(i);
            if (age < a.due) continue;
            g.queue.remove(i--);
            replaying = true;
            try { a.run.run(); } catch (RuntimeException ignored) { } finally { replaying = false; }
        }
        // look: clock glyph above the head, afterimage where they are really going
        if (age % 5 == 0) {
            Vec3 head = t.position().add(0, t.getBbHeight() + 0.8, 0);
            V2dNet.clock(l, g.id, head, Vec3.ZERO, 0.55F, GREEN, 0xB9FFD2, 10, (age % 40) / 40F);
        }
        if (age % 3 == 0) {
            TimeNet.ghost(l, g.intent, Math.max(0.5F, t.getBbWidth()), t.getBbHeight(), 0, 6, t.getYRot());
            Fx.at(l, Fx.dust(GREEN, 0.9F), t.getBoundingBox().getCenter(), 2, t.getBbWidth() * 0.4, 0);
        }
        if (age % 20 == 0) {
            Fx.sound(l, t.position(), SoundEvents.NOTE_BLOCK_HAT, 0.3F, 0.6F);
            Fx.at(l, ParticleTypes.END_ROD, g.intent.add(0, t.getBbHeight() * 0.5, 0), 4, 0.3, 0.02);
        }
        return true;
    }

    private static void stop(Lag g) {
        LAGS.remove(g.target.getUUID(), g);
        V2dNet.kill(g.level, g.target.position(), g.id);
        Fx.at(g.level, Fx.light(Stone.TIME, 1F), g.target.getBoundingBox().getCenter(), 14, 0.4, 0.04);
    }

    // ------------------------------------------------------------------ right: prediction aura

    private boolean aura(ServerPlayer p) {
        Tasks.Handle old = AURAS.remove(p.getUUID());
        if (old != null) old.cancel();
        ServerLevel l = p.serverLevel();
        int ring = FxNet.newId(), clock = V2dNet.newId();
        UUID id = p.getUUID();
        Vec3[] at = {p.position()};
        FxNet.shockwave(l, p.position().add(0, 0.1, 0), 7F, GREEN, 20);
        FxNet.flare(l, p.getBoundingBox().getCenter(), 3F, GREEN, 10);
        Fx.sound(l, p.position(), SoundEvents.BEACON_ACTIVATE, 0.5F, 1.9F);
        Fx.sound(l, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.2F);
        Tasks.Handle h = Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) {
                ServerPlayer owner = l.getServer().getPlayerList().getPlayer(id);
                if (age >= AURA || owner == null || owner.isRemoved() || owner.serverLevel() != l) return false;
                at[0] = owner.position();
                if (age % 20 == 0) {
                    FxNet.runes(l, ring, owner.position().add(0, 0.05, 0), 3.5F, GREEN, 30, -2F);
                }
                if (age % 5 == 0) V2dNet.clock(l, clock, owner.position().add(0, owner.getBbHeight() + 0.8, 0), Vec3.ZERO, 0.5F, GREEN, 0xB9FFD2, 10, (age % 20) / 20F);
                if (age % 2 == 0) predict(owner, l);
                return true;
            }
            @Override public void end() {
                AURAS.remove(id);
                V2dNet.kill(l, at[0], clock);
                FxNet.kill(l, at[0], ring);
            }
        });
        AURAS.put(id, h);
        Fx.bar(p, Stone.TIME, "infinity.extra.v2d.lag.aura");
        return true;
    }

    private static void predict(ServerPlayer owner, ServerLevel l) {
        Vec3 c = owner.position();
        AABB box = new AABB(c, c).inflate(14);
        List<Entity> found = new ArrayList<>();
        found.addAll(l.getEntitiesOfClass(LivingEntity.class, box, e -> e != owner && e.isAlive() && Rules.isLiving(e) && !(e instanceof ServerPlayer sp && sp.isSpectator())));
        found.addAll(l.getEntitiesOfClass(Projectile.class, box, e -> e.isAlive() && !e.onGround() && e.getDeltaMovement().lengthSqr() > 0.01));
        found.sort(Comparator.comparingDouble(e -> e.distanceToSqr(owner)));
        int shown = 0;
        for (Entity e : found) {
            if (shown++ >= 14) break;
            Vec3 from = e.getBoundingBox().getCenter(), future;
            if (e instanceof Projectile) {
                Vec3 v = e.getDeltaMovement(), pos = e.position();
                for (int i = 0; i < 20; i++) { pos = pos.add(v); v = v.scale(0.99).add(0, -0.05, 0); }
                future = pos;
            } else {
                Vec3 v = e.position().subtract(new Vec3(e.xo, e.yo, e.zo));
                Vec3 ahead = new Vec3(v.x * 14, Math.max(-1, Math.min(1, v.y * 6)), v.z * 14);
                if (ahead.lengthSqr() > 36) ahead = ahead.normalize().scale(6);
                if (ahead.lengthSqr() < 0.04) continue;
                future = e.position().add(ahead);
            }
            boolean proj = e instanceof Projectile;
            PacketDistributor.sendToPlayer(owner, TimeNet.of(TimeNet.GHOST, 0, GREEN, 5, proj ? 0.35F : Math.max(0.4F, e.getBbWidth()),
                    proj ? 0.35F : e.getBbHeight(), future, new Vec3(0, e.getYRot(), 0), -1));
            Vec3 mid = future.add(0, proj ? 0 : e.getBbHeight() * 0.5, 0);
            for (int i = 1; i < 6; i++) {
                Vec3 q = from.lerp(mid, i / 6.0);
                l.sendParticles(owner, Fx.dust(GREEN, 0.7F), true, q.x, q.y, q.z, 1, 0, 0, 0, 0);
            }
        }
    }

    // ------------------------------------------------------------------ delayed actions of lagged players

    private static Lag lagOf(Entity e) { return LAGS.isEmpty() || replaying ? null : LAGS.get(e.getUUID()); }

    private static void enqueue(Lag g, Runnable r) {
        if (g.queue.size() < MAX_QUEUE) g.queue.add(new Action(g.age + g.delay, r));
    }

    @SubscribeEvent
    public static void attack(AttackEntityEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        Lag g = lagOf(sp);
        if (g == null) return;
        Entity target = e.getTarget();
        e.setCanceled(true);
        enqueue(g, () -> { if (target.isAlive() && sp.isAlive() && sp.distanceToSqr(target) < 36) sp.attack(target); });
    }

    @SubscribeEvent
    public static void useItem(PlayerInteractEvent.RightClickItem e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        Lag g = lagOf(sp);
        if (g == null) return;
        InteractionHand hand = e.getHand();
        ItemStack used = e.getItemStack();
        e.setCanceled(true);
        enqueue(g, () -> {
            ItemStack now = sp.getItemInHand(hand);
            if (sp.isAlive() && ItemStack.isSameItem(now, used)) sp.gameMode.useItem(sp, sp.level(), now, hand);
        });
    }

    @SubscribeEvent
    public static void useBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        Lag g = lagOf(sp);
        if (g == null) return;
        InteractionHand hand = e.getHand();
        ItemStack used = e.getItemStack();
        BlockHitResult hit = e.getHitVec();
        e.setCanceled(true);
        enqueue(g, () -> {
            ItemStack now = sp.getItemInHand(hand);
            if (sp.isAlive() && ItemStack.isSameItem(now, used) && sp.blockPosition().distSqr(hit.getBlockPos()) < 64)
                sp.gameMode.useItemOn(sp, sp.serverLevel(), now, hand, hit);
        });
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        Lag g = LAGS.remove(e.getEntity().getUUID());
        if (g != null) V2dNet.kill(g.level, g.target.position(), g.id);
        Tasks.Handle h = AURAS.remove(e.getEntity().getUUID());
        if (h != null) h.cancel();
    }

    private TemporalLag() {}
}
