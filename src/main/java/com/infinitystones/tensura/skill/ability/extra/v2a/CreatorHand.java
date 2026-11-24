package com.infinitystones.tensura.skill.ability.extra.v2a;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneProgress;
import com.infinitystones.tensura.skill.StoneTier;
import com.infinitystones.tensura.skill.StoneTree;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Executions;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.MenuNet;
import com.infinitystones.tensura.skill.ability.extra.Move;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import com.infinitystones.tensura.skill.ability.extra.power.PowerNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hand of the Creator (complete Infinity Gauntlet, hidden, very strong). A colossal golden gauntlet with the six stones
 * comes down out of the sky, opens its fingers (the stones light one after another) and closes its fist over its prey,
 * then lifts it into the sky.
 * <ul>
 *   <li>Left (aim): the aimed living being (player or any mob, bosses too) or, aiming at a point with nobody on it, every
 *       living being around the point. Middle click: a list of the players in the dimension; the choice does exactly
 *       what grabbing by aim does, at any distance.</li>
 *   <li>Strength 1-10 (scroll, live while holding). Holding alone hurts nobody. Left while holding squeezes: one decisive squeeze deals a share of the
 *       maximum health (2% at 1, 25% at 9, ignoring armour); left held down squeezes on and on at that share per second. At strength 10 a squeeze executes:
 *       everyone in the fist dies in a burst of gore.</li>
 *   <li>Right while holding: opens the hand and lets everyone down slowly (slow falling); the arm climbs back into the circle, which closes.
 *       Prey is locked in a cutscene (smooth camera shot, no input), the caster is rooted but can look around.</li>
 * </ul>
 * Everyone is released when the caster logs out, dies, changes dimension or no longer carries the whole gauntlet, and
 * put on the ground if the server stops. Captives are held by velocity and position only (no entity data is changed).
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class CreatorHand implements Ability {
    public static final CreatorHand INSTANCE = new CreatorHand();
    static final String MODE = "power.creator_hand";
    private static final String NODE = "creator_hand";
    private static final Map<UUID, Session> ACTIVE = new HashMap<>();
    private static final Map<UUID, Session> HELD = new HashMap<>();

    private static final int HOLD_CAP = 3600, AREA = 10, AIM_RANGE = 96;

    @Override public Stone stone() { return Stone.POWER; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        Session s = ACTIVE.get(p.getUUID());
        if (s != null) {
            if (alt) s.release(); else s.squeezeInput();
            return true;
        }
        if (alt) return Fx.fail(p, "infinity.v2a.creator.none");
        LivingEntity t = Aim.living(p, AIM_RANGE, e -> Rules.canHarm(p, e) && !HELD.containsKey(e.getUUID()));
        if (t != null) return begin(p, t);
        ServerLevel l = p.serverLevel();
        Vec3 aim = Aim.point(p, 48);
        Vec3 ground = Ground.below(l, aim.add(0, 0.3, 0), 40);
        if (!l.hasChunkAt(BlockPos.containing(ground))) return Fx.fail(p, "infinity.v2a.far");
        return launch(new Session(p, l, ground, null));
    }

    /** Caster controls while holding (client key handler): 1 = squeeze (repeated while held), 2 = release. */
    static void input(ServerPlayer p, int action) {
        Session s = ACTIVE.get(p.getUUID());
        if (s == null || s.p != p) return;
        if (action == 1) s.squeezeInput();
        else if (action == 2) s.release();
    }

    /** Middle click: the death list of the players that can be picked. The reply (V2aNet.Pick) comes back to {@link #pick}. */
    @Override
    public List<MenuNet.Option> menu(ServerPlayer p) {
        if (ACTIVE.containsKey(p.getUUID())) { Fx.fail(p, "infinity.v2a.creator.busy"); return null; }
        List<V2aNet.Target> list = new ArrayList<>();
        for (ServerPlayer sp : p.serverLevel().players()) {
            if (list.size() >= 64) break;
            if (sp == p || HELD.containsKey(sp.getUUID()) || !Rules.canHarm(p, sp)) continue;
            list.add(new V2aNet.Target(sp.getUUID(), sp.getGameProfile().getName(), (int) p.distanceTo(sp)));
        }
        if (list.isEmpty()) { Fx.fail(p, "infinity.v2a.creator.nobody"); return null; }
        PacketDistributor.sendToPlayer(p, new V2aNet.Targets(list));
        return null;
    }

    static void pick(ServerPlayer p, UUID id) {
        StoneTree.Node n = StoneTree.of(Stone.POWER).nodes.get(NODE);
        if (n == null || !Stone.POWER.isHeldBy(p) || !StoneProgress.isUnlocked(p, Stone.POWER, NODE) || StoneTier.hidden(p, n)) return;
        if (ACTIVE.containsKey(p.getUUID())) return;
        ServerPlayer t = p.server.getPlayerList().getPlayer(id);
        if (t == null || t.level() != p.level() || HELD.containsKey(id) || !Rules.canHarm(p, t)) { Fx.fail(p, "infinity.v2a.creator.nobody"); return; }
        begin(p, t);
    }

    static boolean isHeld(Entity e) { return HELD.containsKey(e.getUUID()); }

    private static boolean begin(ServerPlayer p, LivingEntity target) {
        ServerLevel l = p.serverLevel();
        Vec3 g = Ground.below(l, target.position().add(0, 0.3, 0), 40);
        if (target.getY() - g.y > 2.5) g = new Vec3(g.x, target.getY() - 0.4, g.z);
        if (!l.hasChunkAt(BlockPos.containing(g))) return Fx.fail(p, "infinity.v2a.far");
        return launch(new Session(p, l, g, target));
    }

    private static boolean launch(Session s) {
        ACTIVE.put(s.p.getUUID(), s);
        Tasks.add(s);
        return true;
    }

    private static boolean holdsAll(ServerPlayer p) {
        for (Stone s : Stone.values()) if (!s.isHeldBy(p)) return false;
        return true;
    }

    /** Share of the maximum health taken each second at strength 1-9. */
    private static float pct(int level) { return (2F + (Mth.clamp(level, 1, 9) - 1) * (23F / 8F)) / 100F; }

    // ------------------------------------------------------------------ damage that nothing softens

    private static void drain(LivingEntity e, float amount) {
        Executions.silently(e.getServer(), () -> {
            float before = e.getHealth();
            e.invulnerableTime = 0;
            e.hurt(e.damageSources().genericKill(), amount);
            if (e.isAlive() && e.getHealth() > before - amount * 0.5F) {
                float left = before - amount;
                if (left <= 0.5F) kill(e); else e.setHealth(left);
            }
            if (e.isAlive()) e.level().broadcastEntityEvent(e, (byte) 2);
        });
    }

    /** Kills for sure and without a death message (see {@link Executions}). */
    private static void kill(LivingEntity e) { Executions.kill(e); }

    // ------------------------------------------------------------------ the show

    private static final class Cap {
        final LivingEntity e; final Vec3 off; final Vec3 at;
        Cap(LivingEntity e, Vec3 off) { this.e = e; this.off = off; this.at = e.position(); }
    }

    private static final class Session implements Tasks.Task {
        final ServerPlayer p; final ServerLevel l; final Vec3 ground;
        final LivingEntity single;
        final int id = V2aNet.newId();
        final int[] circles = new int[8];
        final float s, shotYaw, dist, rc;
        final float yaw;
        final double yC, hoverY, riseY;
        final List<Cap> caps = new ArrayList<>();
        int state = -1, moveStart, dur = 1, age, squeezeStart, relStart, squeezeUntil = -100, lastLevel;
        double y0, y1;
        boolean releasing, exec, chain;
        Vec3 anchor;

        Session(ServerPlayer p, ServerLevel l, Vec3 ground, LivingEntity single) {
            this.p = p; this.l = l; this.ground = ground; this.single = single;
            this.s = single == null ? 9F
                    : Mth.clamp(7.5F + Math.max(single.getBbHeight() * 0.35F, single.getBbWidth() * 0.6F), 8F, 12F);
            this.rc = 3.4F * s;
            this.shotYaw = p.getYRot();
            this.dist = 8F + 4.6F * s;
            double dx = p.getX() - ground.x, dz = p.getZ() - ground.z;
            float toCaster = dx * dx + dz * dz < 1 ? (float) Math.toRadians(-p.getYRot() + 180) : (float) Math.atan2(dx, dz);
            this.yaw = toCaster + (float) Math.PI;
            this.yC = Math.min(l.getMaxBuildHeight() - 6, ground.y + 60 + 4.5 * s);
            this.hoverY = ground.y + 2.7 * s + 0.3;
            this.riseY = Math.min(yC - 14, ground.y + 18 + 3.2 * s);
            for (int i = 0; i < circles.length; i++) circles[i] = PowerNet.newId();
            this.lastLevel = level();
        }

        int level() { return ExtraSettings.get(p, MODE, 5, 1, 10, 1); }

        double yb() { return V2aAnim.lerp(y0, y1, V2aAnim.ease((age - moveStart) / (float) Math.max(1, dur))); }

        void broadcast(V2aNet.Msg m) {
            for (ServerPlayer sp : l.players())
                if (sp == p || sp.position().distanceToSqr(ground) <= 300 * 300) PacketDistributor.sendToPlayer(sp, m);
        }

        void setState(int st, double toY, int ticks, int life) {
            double cur = state < 0 ? yC : yb();
            y0 = cur; y1 = toY; moveStart = age; dur = ticks; state = st;
            broadcast(V2aNet.msg(V2aNet.GAUNTLET, id, st, life, s, ground, yaw, y0, y1, ticks, level()));
            if (st == V2aAnim.G_HOLD || st == V2aAnim.G_SQUEEZE) syncLocks();
        }

        Vec3 centre() {
            double yb = yb();
            return new Vec3(ground.x + Math.sin(yaw) * 0.85 * s, yb + 1.0 * s, ground.z + Math.cos(yaw) * 0.85 * s);
        }

        int ready() { return releasing ? 0 : (state == V2aAnim.G_HOLD || state == V2aAnim.G_SQUEEZE) ? 3 : 2; }

        void circles(int life) {
            Vec3 sky = new Vec3(ground.x, yC, ground.z), up = new Vec3(0, 1, 0);
            PowerNet.circle(l, circles[0], sky, up, rc, Bits.GOLD, 0xFFF3C4, life, 2.2F, PowerNet.RAYS);
            PowerNet.circle(l, circles[1], sky.add(0, -0.3, 0), up, rc * 0.62F, 0xFFF3C4, Bits.GOLD, life, -3.5F, PowerNet.GLYPHS_ONLY);
            for (int i = 0; i < 6; i++) {
                double a = Math.toRadians(60 * i + 30);
                Vec3 at = sky.add(Math.cos(a) * rc * 0.8, -0.5, Math.sin(a) * rc * 0.8);
                int col = PowerNet.STONE_COLORS[i];
                PowerNet.circle(l, circles[2 + i], at, up, rc * 0.17F, col, PowerNet.tint(col, 0.5F), life, 5F, PowerNet.SIMPLE);
            }
        }

        void squeezeInput() {
            if (releasing) return;
            if (state == V2aAnim.G_HOLD || state == V2aAnim.G_SQUEEZE) squeezeUntil = age + 6;
        }

        void syncLocks() {
            int lv = level();
            HandLock.send(p, 2, id, shotYaw, dist, s, lv, ready());
            for (Cap c : caps) if (c.e instanceof ServerPlayer sp) HandLock.send(sp, 1, id, shotYaw, dist, s, lv, 0);
        }

        /** The caster stays where he is (looking still works). */
        void root() {
            if (anchor == null) anchor = p.position();
            double dx = p.getX() - anchor.x, dz = p.getZ() - anchor.z;
            if (dx * dx + dz * dz > 1.0) Bits.place(p, new Vec3(anchor.x, p.getY(), anchor.z));
        }

        @Override
        public boolean tick(int a) {
            age = a;
            if (releasing) {
                if (age % 3 == 0) sparkle(0.6F);
                return age < relStart + V2aAnim.G_RELEASE_TICKS + 6;
            }
            if (!p.isAlive() || p.hasDisconnected() || p.isRemoved() || p.level() != l || !holdsAll(p)) {
                release();
                return true;
            }
            root();
            if (age == 0) {
                setState(V2aAnim.G_DESC, hoverY, V2aAnim.G_DESC_TICKS, 1200);
                circles(70);
                Bits.farSound(l, ground, SoundEvents.BEACON_ACTIVATE, 0.6F, 0.5F, 160);
                Bits.farSound(l, ground, SoundEvents.ENDER_DRAGON_GROWL, 0.3F, 0.7F, 160);
                if (single != null) take(single, Vec3.ZERO);
                syncLocks();
            } else if (age % 30 == 0) circles(70);
            if (age > 0 && age < V2aAnim.G_DESC_TICKS) {
                Vec3 h = new Vec3(ground.x, yb(), ground.z);
                FxNet.pillar(l, 0, h, 0.35F * s, 18F, Bits.GOLD, 3);
                if (age % 2 == 0) l.sendParticles(Fx.dust(Bits.GOLD, 2.6F), h.x, h.y + 2, h.z, 10, s * 0.5, 2, s * 0.5, 0);
                for (int i = 0; i < 6; i++) {
                    if (age == (i + 1) * V2aAnim.G_DESC_TICKS / 8) {
                        Fx.sound(l, ground, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6F, 0.6F + 0.14F * i);
                        FxNet.flare(l, new Vec3(h.x, h.y + 1.2 * s, h.z), s * 0.7F, PowerNet.STONE_COLORS[i], 8);
                    }
                }
                if (age == 8) Bits.farSound(l, ground, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.6F, 0.5F, 140);
                if (age == 20) Bits.farSound(l, h, SoundEvents.WIND_CHARGE_BURST, 0.6F, 0.4F, 140);
                if (age == 30) Bits.farSound(l, h, SoundEvents.BELL_RESONATE, 0.5F, 0.5F, 140);
                if (age == V2aAnim.G_DESC_TICKS - 5) FxNet.shake(l, ground, 90, 2.4F, 16);
            }
            if (age == V2aAnim.G_DESC_TICKS) {
                setState(V2aAnim.G_OPEN, hoverY, V2aAnim.G_OPEN_TICKS, 1200);
                FxNet.shockwave(l, ground.add(0, 0.1, 0), AREA * 1.6F, Bits.GOLD, 24);
                Bits.farSound(l, ground, SoundEvents.ANVIL_LAND, 0.4F, 0.6F, 140);
            }
            int closeAt = V2aAnim.G_DESC_TICKS + V2aAnim.G_OPEN_TICKS;
            int closedAt = closeAt + V2aAnim.G_CLOSE_TICKS;
            if (age == closeAt) {
                if (single == null) capture();
                setState(V2aAnim.G_CLOSE, hoverY, V2aAnim.G_CLOSE_TICKS, 1200);
                Bits.farSound(l, ground, SoundEvents.ANVIL_LAND, 0.6F, 0.5F, 140);
                Bits.farSound(l, ground, SoundEvents.IRON_GOLEM_ATTACK, 0.7F, 0.5F, 140);
                FxNet.shake(l, ground, 90, 2.4F, 16);
                FxNet.flashAround(l, ground, AREA + 6, Bits.GOLD, 0.5F, 10);
            }
            if (age == closedAt) {
                if (caps.isEmpty()) { release(); return true; }
                setState(V2aAnim.G_RISE, riseY, V2aAnim.G_RISE_TICKS, 1200);
                Bits.farSound(l, centre(), SoundEvents.WIND_CHARGE_BURST, 0.5F, 0.5F, 140);
            }
            if (age == closedAt + V2aAnim.G_RISE_TICKS && state == V2aAnim.G_RISE) setState(V2aAnim.G_HOLD, riseY, 1, 1200);
            if (state == V2aAnim.G_HOLD && age < squeezeUntil) { chain = false; startSqueeze(); }
            if (state == V2aAnim.G_SQUEEZE) squeezeTick(age - squeezeStart);
            if (releasing) return true;
            if (age >= closedAt && (state == V2aAnim.G_RISE || state == V2aAnim.G_HOLD || state == V2aAnim.G_SQUEEZE)) {
                if (age % 4 == 0) sparkle(1F);
                if (age % 20 == 10) Bits.farSound(l, centre(), SoundEvents.WARDEN_HEARTBEAT, 0.5F, 0.6F, 70);
                if (age - closedAt > HOLD_CAP) { release(); return true; }
            }
            if (!caps.isEmpty()) holdAll(closeAt);
            if (releasing) return true;
            if (age > closedAt && caps.isEmpty()) { release(); return true; }
            int lv = level();
            if (age % 20 == 0 || lv != lastLevel) { lastLevel = lv; syncLocks(); }
            return true;
        }

        private void sparkle(float k) {
            Vec3 c = centre();
            l.sendParticles(Fx.dust(Bits.GOLD, 2.2F), c.x, c.y, c.z, (int) (8 * k), s * 0.7, s * 0.6, s * 0.7, 0);
            l.sendParticles(ParticleTypes.END_ROD, c.x, c.y - 0.5 * s, c.z, 3, s * 0.5, s * 0.5, s * 0.5, 0.02);
        }

        private void take(LivingEntity e, Vec3 off) {
            e.stopRiding();
            caps.add(new Cap(e, off));
            HELD.put(e.getUUID(), this);
            if (e instanceof ServerPlayer sp) FxNet.flash(sp, Bits.GOLD, 0.7F, 14);
        }

        private void capture() {
            Vec3 at = new Vec3(ground.x, ground.y, ground.z);
            AABB box = new AABB(at, at).inflate(AREA, AREA * 0.8 + 3, AREA);
            List<LivingEntity> found = l.getEntitiesOfClass(LivingEntity.class, box, e ->
                    Rules.isLiving(e) && Rules.canHarm(p, e) && !HELD.containsKey(e.getUUID())
                            && Math.hypot(e.getX() - ground.x, e.getZ() - ground.z) <= AREA);
            int n = Math.min(found.size(), 64);
            for (int i = 0; i < n; i++) {
                double ang = i * 2.399963, rr = 0.42 * s * Math.sqrt((i + 0.5) / n);
                take(found.get(i), new Vec3(Math.cos(ang) * rr, (l.random.nextDouble() - 0.5) * 0.3 * s, Math.sin(ang) * rr));
            }
            syncLocks();
        }

        private void holdAll(int closeAt) {
            Vec3 c = centre();
            boolean frozen = age < closeAt;
            boolean closing = age < closeAt + V2aAnim.G_CLOSE_TICKS + 4;
            for (Iterator<Cap> it = caps.iterator(); it.hasNext(); ) {
                Cap cap = it.next();
                LivingEntity e = cap.e;
                if (e.isRemoved() || !e.isAlive() || e.level() != l) { drop(cap); it.remove(); continue; }
                if (e instanceof ServerPlayer sp) HandLock.enforce(sp);
                if (frozen) {
                    if (e.position().distanceToSqr(cap.at) > 0.04) Bits.place(e, cap.at);
                    else { Move.set(e, Vec3.ZERO); e.fallDistance = 0; }
                    continue;
                }
                Vec3 tc = c.add(cap.off);
                Vec3 feet = tc.subtract(0, e.getBbHeight() * 0.5, 0);
                double d = e.position().distanceTo(feet);
                if (!closing && d > 1.8) Bits.place(e, feet);
                else if (d < 0.35) { Move.set(e, Vec3.ZERO); e.fallDistance = 0; }
                else Move.steer(e, tc, 0.28, 0.9);
            }
        }

        private void startSqueeze() {
            exec = level() >= 10;
            squeezeStart = age;
            setState(V2aAnim.G_SQUEEZE, riseY, 1, 1200);
        }

        private void squeezeTick(int t) {
            Vec3 c = centre();
            if (exec) {
                if (t == 0) {
                    Bits.farSound(l, c, SoundEvents.WARDEN_SONIC_CHARGE, 0.6F, 0.6F, 120);
                    FxNet.shake(l, c, 120, 1.6F, 12);
                }
                if (t == 10) {
                    executeAll();
                    release();
                }
                return;
            }
            if (t == 4) crush(c);
            int period = chain ? 10 : V2aAnim.G_SQUEEZE_TICKS;
            if (t >= period - 1) {
                if (age < squeezeUntil) { chain = true; startSqueeze(); }
                else if (t >= V2aAnim.G_SQUEEZE_TICKS - 1) setState(V2aAnim.G_HOLD, riseY, 1, 1200);
            }
        }

        /** One squeeze: a share of the maximum health that grows with the force, ignoring armour and resistances. */
        private void crush(Vec3 c) {
            int lv = level();
            float k = Mth.clamp(lv / 10F, 0.1F, 1F);
            FxNet.shake(l, c, 100, 1.4F + 3F * k, 10);
            FxNet.flare(l, c, s * (1.4F + 1.2F * k), 0xFFFFFF, 8);
            FxNet.shockwave(l, new Vec3(ground.x, c.y, ground.z), s * (1.6F + 1.4F * k), Bits.GOLD, 14);
            Bits.farSound(l, c, SoundEvents.ANVIL_LAND, 0.6F, 0.4F + 0.05F * lv, 110);
            Bits.farSound(l, c, SoundEvents.IRON_GOLEM_DAMAGE, 0.8F, 0.5F, 110);
            Bits.farSound(l, c, SoundEvents.WARDEN_ATTACK_IMPACT, 0.5F, 0.6F, 110);
            float amount = pct(lv) * (chain ? 0.5F : 1F);
            for (Cap cap : new ArrayList<>(caps)) {
                LivingEntity e = cap.e;
                if (e.isAlive() && Rules.canHarm(p, e)) drain(e, e.getMaxHealth() * amount);
                if (e instanceof ServerPlayer sp) FxNet.flash(sp, 0xFFFFFF, 0.35F, 5);
            }
        }

        /** Force 10: the fist closes for good. */
        private void executeAll() {
            Vec3 c = centre();
            FxNet.flare(l, c, s * 2.4F, 0xFFFFFF, 12);
            FxNet.flare(l, c, s * 3.6F, Bits.GOLD, 16);
            FxNet.flashAround(l, c, 90, Bits.GOLD, 0.95F, 20);
            FxNet.shake(l, c, 140, 5F, 24);
            FxNet.shockwave(l, new Vec3(ground.x, c.y, ground.z), s * 4F, Bits.GOLD, 18);
            Bits.farSound(l, c, SoundEvents.GENERIC_EXPLODE.value(), 0.7F, 0.45F, 140);
            Bits.farSound(l, c, SoundEvents.WARDEN_SONIC_BOOM, 0.6F, 0.7F, 140);
            Bits.farSound(l, c, SoundEvents.IRON_GOLEM_DAMAGE, 0.7F, 0.4F, 140);
            for (Iterator<Cap> it = caps.iterator(); it.hasNext(); ) {
                Cap cap = it.next();
                LivingEntity e = cap.e;
                if (!e.isAlive() || !Rules.canHarm(p, e)) continue;
                gore(e);
                kill(e);
                drop(cap);
                it.remove();
            }
        }

        private void gore(LivingEntity e) {
            AABB bb = e.getBoundingBox();
            Vec3 c = bb.getCenter();
            V2aNet.Gore g = new V2aNet.Gore(e.getId(), c.x, c.y, c.z, (float) bb.getXsize(), (float) bb.getYsize());
            for (ServerPlayer sp : l.players())
                if (sp == p || sp == e || sp.position().distanceToSqr(c) < 192 * 192) PacketDistributor.sendToPlayer(sp, g);
        }

        private void drop(Cap cap) {
            HELD.remove(cap.e.getUUID(), this);
            if (cap.e instanceof ServerPlayer sp) HandLock.free(sp);
        }

        /** Opens the hand and lets everyone down gently; the arm climbs back into the circle, which closes. */
        void release() {
            if (releasing) return;
            releasing = true;
            relStart = age;
            for (Cap cap : caps) {
                LivingEntity e = cap.e;
                drop(cap);
                e.fallDistance = 0;
                if (e.isAlive()) e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 240, 0, true, false, false));
            }
            caps.clear();
            HandLock.free(p);
            double up = Math.min(l.getMaxBuildHeight() - 6, yC + 4);
            setState(V2aAnim.G_RELEASE, up, V2aAnim.G_RELEASE_TICKS, V2aAnim.G_RELEASE_TICKS + 8);
            circles(V2aAnim.G_RELEASE_TICKS + 16);
            Bits.farSound(l, centre(), SoundEvents.BEACON_DEACTIVATE, 0.5F, 0.8F, 140);
        }

        @Override
        public void end() {
            ACTIVE.remove(p.getUUID(), this);
            for (Cap cap : caps) {
                drop(cap);
                putDown(cap.e);
            }
            caps.clear();
            HandLock.free(p);
            V2aNet.kill(l, ground, id);
            for (int c : circles) PowerNet.kill(l, new Vec3(ground.x, yC, ground.z), c);
        }

        void putDown(Entity e) {
            if (e.isRemoved()) return;
            Vec3 g = e.level() instanceof ServerLevel sl ? Ground.below(sl, e.position().add(0, 1, 0), 120) : e.position();
            Bits.place(e, g);
        }

        void forget(Entity e) {
            caps.removeIf(c -> c.e == e);
            HELD.remove(e.getUUID(), this);
        }
    }

    /** A captive who leaves the game is put on the ground first, so the save never holds them in mid-air. */
    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        Session s = HELD.get(sp.getUUID());
        if (s != null) {
            s.forget(sp);
            s.putDown(sp);
        }
        Session own = ACTIVE.get(sp.getUUID());
        if (own != null) own.release();
    }
}
