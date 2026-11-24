package com.infinitystones.tensura.skill.ability.extra.v2a;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.compat.ClaimsCompat;
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
 *   <li>Strength 1-10 (scroll). While held, every second a share of the maximum health that grows with the level
 *       (2% at 1, 25% at 9) ignores armour and resistances. Level 10 executes: everyone in the fist dies in a burst of gore.</li>
 *   <li>Right: first click squeezes (strong pulses, or the execution at level 10), the next one opens the hand and lets
 *       everyone down slowly. Prey is locked in a cutscene (fixed camera shot, no input), the caster is rooted but can
 *       still look and use the wheel.</li>
 *   <li>Claims still block damage and killing of players inside land that is not the caster's; holding is allowed.</li>
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

    private static final int HOLD_CAP = 3600, AREA = 8, AIM_RANGE = 96;

    @Override public Stone stone() { return Stone.POWER; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        Session s = ACTIVE.get(p.getUUID());
        if (s != null) {
            if (!alt) return Fx.fail(p, "infinity.v2a.creator.busy");
            s.rightClick(p.isShiftKeyDown());
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
        final float s, shotYaw, dist;
        final float yaw;
        final double yC, hoverY, riseY;
        final List<Cap> caps = new ArrayList<>();
        int state = -1, moveStart, dur = 1, age, squeezeStart, holdStart, relStart;
        double y0, y1;
        boolean releasing, squeezed, execute;
        Vec3 anchor;

        Session(ServerPlayer p, ServerLevel l, Vec3 ground, LivingEntity single) {
            this.p = p; this.l = l; this.ground = ground; this.single = single;
            this.s = single == null ? Mth.clamp(AREA / 2.2F, 1.6F, 6F)
                    : Mth.clamp(Math.max(single.getBbHeight() * 0.55F, single.getBbWidth() * 0.8F), 1.8F, 6F);
            this.shotYaw = p.getYRot();
            this.dist = 9F + 4.2F * s;
            double dx = p.getX() - ground.x, dz = p.getZ() - ground.z;
            float toCaster = dx * dx + dz * dz < 1 ? (float) Math.toRadians(-p.getYRot() + 180) : (float) Math.atan2(dx, dz);
            this.yaw = toCaster + (float) Math.PI;
            this.yC = Math.min(l.getMaxBuildHeight() - 6, ground.y + 76 + 2 * s);
            this.hoverY = ground.y + 2.7 * s + 0.3;
            this.riseY = Math.min(yC - 14, ground.y + 26 + 1.5 * s);
        }

        int level() { return ExtraSettings.get(p, MODE, 5, 1, 10, 1); }

        double yb() { return V2aAnim.lerp(y0, y1, V2aAnim.ease((age - moveStart) / (float) Math.max(1, dur))); }

        void broadcast(V2aNet.Msg m) {
            for (ServerPlayer sp : l.players())
                if (sp == p || sp.position().distanceToSqr(ground) <= 260 * 260) PacketDistributor.sendToPlayer(sp, m);
        }

        void setState(int st, double toY, int ticks, int life) {
            double cur = state < 0 ? yC : yb();
            y0 = cur; y1 = toY; moveStart = age; dur = ticks; state = st;
            broadcast(V2aNet.msg(V2aNet.GAUNTLET, id, st, life, s, ground, yaw, y0, y1, ticks, AREA));
        }

        Vec3 centre() {
            double yb = yb();
            return new Vec3(ground.x + Math.sin(yaw) * 0.85 * s, yb + 1.0 * s, ground.z + Math.cos(yaw) * 0.85 * s);
        }

        void rightClick(boolean shift) {
            if (releasing) return;
            if (state == V2aAnim.G_HOLD && !squeezed && !shift) {
                squeezed = true;
                execute = level() >= 10;
                squeezeStart = age;
                setState(V2aAnim.G_SQUEEZE, riseY, 1, 1200);
            } else release();
        }

        void syncLocks() {
            HandLock.send(p, 2, id, shotYaw, dist, s);
            for (Cap c : caps) if (c.e instanceof ServerPlayer sp) HandLock.send(sp, 1, id, shotYaw, dist, s);
        }

        /** The caster stays where he is (looking and the wheel still work). */
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
                Vec3 sky = new Vec3(ground.x, yC, ground.z);
                float rc = 4.2F * s;
                Vec3 up = new Vec3(0, 1, 0);
                PowerNet.circle(l, PowerNet.newId(), sky, up, rc, Bits.GOLD, 0xFFF3C4, 74, 2.5F, PowerNet.RAYS);
                PowerNet.circle(l, PowerNet.newId(), sky.add(0, -0.3, 0), up, rc * 0.6F, 0xFFF3C4, Bits.GOLD, 74, -4F, PowerNet.GLYPHS_ONLY);
                Bits.farSound(l, ground, SoundEvents.BEACON_ACTIVATE, 0.6F, 0.5F, 140);
                Bits.farSound(l, ground, SoundEvents.ENDER_DRAGON_GROWL, 0.3F, 0.7F, 140);
                if (single != null) take(single, Vec3.ZERO);
                syncLocks();
            }
            if (age > 0 && age < V2aAnim.G_DESC_TICKS) {
                Vec3 h = new Vec3(ground.x, yb(), ground.z);
                FxNet.pillar(l, 0, h, 0.7F * s * 0.5F, 18F, Bits.GOLD, 3);
                if (age % 2 == 0) l.sendParticles(Fx.dust(Bits.GOLD, 2.6F), h.x, h.y + 2, h.z, 8, s, 2, s, 0);
                if (age == 8) Bits.farSound(l, ground, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.6F, 0.5F, 120);
                if (age == 20) Bits.farSound(l, h, SoundEvents.WIND_CHARGE_BURST, 0.6F, 0.4F, 120);
                if (age == 30) Bits.farSound(l, h, SoundEvents.BELL_RESONATE, 0.5F, 0.5F, 120);
                if (age == V2aAnim.G_DESC_TICKS - 4) FxNet.shake(l, ground, 50, 2.2F, 14);
            }
            if (age == V2aAnim.G_DESC_TICKS) {
                setState(V2aAnim.G_OPEN, hoverY, V2aAnim.G_OPEN_TICKS, 1200);
                FxNet.shockwave(l, ground.add(0, 0.1, 0), AREA * 1.4F, Bits.GOLD, 22);
                Bits.farSound(l, ground, SoundEvents.ANVIL_LAND, 0.4F, 0.6F, 120);
            }
            int openAge = age - V2aAnim.G_DESC_TICKS;
            if (state == V2aAnim.G_OPEN && openAge >= 0 && openAge % 3 == 0 && openAge / 3 < 6) {
                int i = openAge / 3;
                Fx.sound(l, ground, SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6F, 0.6F + 0.14F * i);
                FxNet.flare(l, centre().add(0, 0.5 * s, 0), s * 1.2F, PowerNet.STONE_COLORS[i], 8);
            }
            int closeAt = V2aAnim.G_DESC_TICKS + V2aAnim.G_OPEN_TICKS;
            int closedAt = closeAt + V2aAnim.G_CLOSE_TICKS;
            if (age == closeAt) {
                if (single == null) capture();
                setState(V2aAnim.G_CLOSE, hoverY, V2aAnim.G_CLOSE_TICKS, 1200);
                Bits.farSound(l, ground, SoundEvents.ANVIL_LAND, 0.6F, 0.5F, 120);
                Bits.farSound(l, ground, SoundEvents.IRON_GOLEM_ATTACK, 0.7F, 0.5F, 120);
                FxNet.shake(l, ground, 50, 2F, 14);
                FxNet.flashAround(l, ground, AREA + 6, Bits.GOLD, 0.5F, 10);
            }
            if (age == closedAt) {
                if (caps.isEmpty()) { release(); return true; }
                if (level() >= 10) {
                    squeezed = true; execute = true; squeezeStart = age;
                    setState(V2aAnim.G_SQUEEZE, hoverY, 1, 1200);
                } else {
                    setState(V2aAnim.G_RISE, riseY, V2aAnim.G_RISE_TICKS, 1200);
                    Bits.farSound(l, centre(), SoundEvents.WIND_CHARGE_BURST, 0.5F, 0.5F, 120);
                }
            }
            if (age == closedAt + V2aAnim.G_RISE_TICKS && state == V2aAnim.G_RISE) {
                holdStart = age;
                setState(V2aAnim.G_HOLD, riseY, 1, 1200);
            }
            if (state == V2aAnim.G_SQUEEZE) squeeze(age - squeezeStart);
            if (releasing) return true;
            if (age >= closedAt && (state == V2aAnim.G_RISE || state == V2aAnim.G_HOLD || state == V2aAnim.G_SQUEEZE)) {
                if (age % 4 == 0) sparkle(1F);
                if (age % 20 == 10) Bits.farSound(l, centre(), SoundEvents.WARDEN_HEARTBEAT, 0.5F, 0.6F, 70);
                if (age % 20 == 0 && age > closedAt && !execute) drainTick();
                if (state == V2aAnim.G_HOLD && age - holdStart > HOLD_CAP) { release(); return true; }
            }
            if (!caps.isEmpty()) holdAll(closeAt);
            if (releasing) return true;
            if (age > closedAt && caps.isEmpty()) { release(); return true; }
            if (age % 20 == 0) syncLocks();
            return true;
        }

        private void sparkle(float k) {
            Vec3 c = centre();
            l.sendParticles(Fx.dust(Bits.GOLD, 2.2F), c.x, c.y, c.z, (int) (6 * k), s * 0.7, s * 0.6, s * 0.7, 0);
            l.sendParticles(ParticleTypes.END_ROD, c.x, c.y - 0.5 * s, c.z, 2, s * 0.5, s * 0.5, s * 0.5, 0.02);
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

        /** Everyone in the fist loses a share of the maximum health that grows with the strength. */
        private void drainTick() {
            float k = pct(level());
            for (Cap cap : new ArrayList<>(caps)) {
                LivingEntity e = cap.e;
                if (!e.isAlive() || !Rules.canHarm(p, e) || protectedPlayer(e)) continue;
                drain(e, e.getMaxHealth() * k);
            }
        }

        private boolean protectedPlayer(LivingEntity e) { return e instanceof ServerPlayer sp && ClaimsCompat.isProtected(sp, p); }

        private void squeeze(int t) {
            Vec3 c = centre();
            if (execute) {
                if (t == 0) {
                    Bits.farSound(l, c, SoundEvents.WARDEN_SONIC_CHARGE, 0.6F, 0.6F, 100);
                    FxNet.shake(l, c, 70, 1.5F, 10);
                }
                if (t == 8) executeAll();
                if (t >= 8) release();
                return;
            }
            if (t % 10 != 0 || t > 30) {
                if (t >= 40) setState(V2aAnim.G_HOLD, riseY, 1, 1200);
                return;
            }
            FxNet.shake(l, c, 70, 3.2F, 10);
            FxNet.flare(l, c, s * 2.4F, 0xFFFFFF, 8);
            FxNet.shockwave(l, new Vec3(ground.x, c.y, ground.z), s * 3F, Bits.GOLD, 14);
            Bits.farSound(l, c, SoundEvents.ANVIL_LAND, 0.55F, 0.5F + 0.1F * (t / 10), 100);
            Bits.farSound(l, c, SoundEvents.IRON_GOLEM_DAMAGE, 0.7F, 0.5F, 100);
            float pulse = pct(level()) * 0.5F;
            for (Cap cap : new ArrayList<>(caps)) {
                LivingEntity e = cap.e;
                if (e.isAlive() && Rules.canHarm(p, e) && !protectedPlayer(e)) drain(e, e.getMaxHealth() * pulse);
                if (e instanceof ServerPlayer sp) FxNet.flash(sp, 0xFFFFFF, 0.35F, 5);
            }
        }

        /** Level 10: the fist closes for good. Players inside land that is not the caster's are spared. */
        private void executeAll() {
            Vec3 c = centre();
            FxNet.flare(l, c, s * 3.6F, 0xFFFFFF, 12);
            FxNet.flare(l, c, s * 5.5F, Bits.GOLD, 16);
            FxNet.flashAround(l, c, 90, Bits.GOLD, 0.95F, 20);
            FxNet.shake(l, c, 100, 4.5F, 24);
            FxNet.shockwave(l, new Vec3(ground.x, c.y, ground.z), s * 4F, Bits.GOLD, 18);
            Bits.farSound(l, c, SoundEvents.GENERIC_EXPLODE.value(), 0.7F, 0.45F, 120);
            Bits.farSound(l, c, SoundEvents.WARDEN_SONIC_BOOM, 0.6F, 0.7F, 120);
            Bits.farSound(l, c, SoundEvents.IRON_GOLEM_DAMAGE, 0.7F, 0.4F, 120);
            for (Iterator<Cap> it = caps.iterator(); it.hasNext(); ) {
                Cap cap = it.next();
                LivingEntity e = cap.e;
                if (!e.isAlive() || !Rules.canHarm(p, e) || protectedPlayer(e)) continue;
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

        /** Opens the hand and lets everyone down gently. */
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
            Bits.farSound(l, centre(), SoundEvents.BEACON_DEACTIVATE, 0.5F, 0.8F, 120);
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
