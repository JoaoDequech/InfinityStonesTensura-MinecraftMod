package com.infinitystones.tensura.skill.ability.extra.reality;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Ground;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Pocket Universe Card (Reality). Left (aim): you throw a spinning red card that homes in on the aimed creature; where it
 * lands the creature is shrunk to a fraction of its size and sealed for 8 s in a tiny floating red box with a galaxy of
 * stars inside, over a rune circle. Right: the cards come back and every box you made opens now. Bosses and allies cannot be
 * caged, a creature can only be in one box, at most 3 boxes per caster. No damage is dealt, nothing is touched in the world.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class PocketCard implements Ability {
    public static final PocketCard INSTANCE = new PocketCard();
    private static final String K = "infinity.extra.card.";
    private static final int RED = Stone.REALITY.color() & 0xFFFFFF, HOLD = 160, MAX_CAGES = 3;
    private static final ResourceLocation SHRINK = InfinityStones.id("pocket_card_shrink");

    private static final class Cage {
        final UUID owner; final LivingEntity t; final ServerLevel level; final Vec3 anchor;
        final int id = FxNet.newId(), id2 = FxNet.newId(), id3 = FxNet.newId();
        final boolean wasNoAi, wasNoGravity;
        boolean release, restored;
        Cage(UUID owner, LivingEntity t, Vec3 anchor) {
            this.owner = owner; this.t = t; this.level = (ServerLevel) t.level(); this.anchor = anchor;
            wasNoAi = t instanceof Mob m && m.isNoAi();
            wasNoGravity = t.isNoGravity();
        }
        void restore() {
            if (restored) return;
            restored = true;
            AttributeInstance sc = t.getAttribute(Attributes.SCALE);
            if (sc != null) sc.removeModifier(SHRINK);
            if (t instanceof Mob m) m.setNoAi(wasNoAi);
            t.setNoGravity(wasNoGravity);
            t.fallDistance = 0;
            TARGETS.remove(t.getUUID(), this);
            List<Cage> mine = BY_OWNER.get(owner);
            if (mine != null) { mine.remove(this); if (mine.isEmpty()) BY_OWNER.remove(owner); }
        }
    }

    private static final Map<UUID, List<Cage>> BY_OWNER = new HashMap<>();
    private static final Map<UUID, Cage> TARGETS = new HashMap<>();

    @Override public Stone stone() { return Stone.REALITY; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        List<Cage> mine = BY_OWNER.get(p.getUUID());
        if (alt) {
            if (mine == null || mine.isEmpty()) return Fx.fail(p, K + "none");
            for (Cage c : new ArrayList<>(mine)) c.release = true;
            Fx.bar(p, Stone.REALITY, K + "opened");
            return true;
        }
        if (mine != null && mine.size() >= MAX_CAGES) return Fx.fail(p, K + "max");
        LivingEntity t = Aim.living(p, 40, e -> Rules.canControl(p, e) && !TARGETS.containsKey(e.getUUID()));
        if (t == null) return Fx.fail(p, K + "no_target");
        throwCard(p, t);
        return true;
    }

    private static void throwCard(ServerPlayer p, LivingEntity t) {
        ServerLevel l = p.serverLevel();
        Vec3 start = p.getEyePosition().add(p.getLookAngle().scale(0.8)).add(0, -0.2, 0);
        Fx.sound(l, start, SoundEvents.ENDER_EYE_LAUNCH, 0.6F, 1.5F);
        Fx.sound(l, start, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.8F);
        FxNet.flare(l, start, 0.9F, RED, 6);
        final Vec3[] pos = {start};
        final int cardId = FxNet.newId();
        Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) {
                if (!t.isAlive() || t.level() != l || p.hasDisconnected() || age > 60) return false;
                Vec3 target = t.position().add(0, t.getBbHeight() * 0.55, 0);
                Vec3 d = target.subtract(pos[0]);
                double dist = d.length();
                if (dist < 1.3) { seal(p, t); return false; }
                Vec3 step = d.normalize().scale(Math.min(dist, 1.9 + age * 0.08));
                Vec3 next = pos[0].add(step);
                Fx.line(l, pos[0], next, 0.3, Fx.dust(RED, 1.2F));
                Fx.at(l, ParticleTypes.END_ROD, next, 1, 0.05, 0.01);
                double a = age * 0.9;
                Vec3 normal = new Vec3(Math.cos(a), Math.sin(a * 0.7), Math.sin(a)).normalize();
                FxNet.panel(l, cardId, next, normal, 0.38F, RED, 3);
                pos[0] = next;
                return true;
            }
            @Override public void end() { FxNet.kill(l, pos[0], cardId); }
        });
    }

    private static void seal(ServerPlayer p, LivingEntity t) {
        if (!Rules.canControl(p, t) || TARGETS.containsKey(t.getUUID()) || !(t.level() instanceof ServerLevel l)) return;
        List<Cage> mine = BY_OWNER.computeIfAbsent(p.getUUID(), k -> new ArrayList<>());
        if (mine.size() >= MAX_CAGES) return;
        Vec3 base = t.position();
        Vec3 up = base.add(0, 1.0, 0);
        Vec3 anchor = Ground.fits(l, t, up) ? up : base;
        Cage cage = new Cage(p.getUUID(), t, anchor);
        mine.add(cage);
        TARGETS.put(t.getUUID(), cage);
        AttributeInstance sc = t.getAttribute(Attributes.SCALE);
        if (sc != null) {
            sc.removeModifier(SHRINK);
            sc.addTransientModifier(new AttributeModifier(SHRINK, -0.65, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        if (t instanceof Mob m) { m.setNoAi(true); m.setTarget(null); }
        t.setNoGravity(true);
        t.setDeltaMovement(Vec3.ZERO);
        pin(cage);
        Vec3 c = anchor.add(0, 0.45, 0);
        Vec3 g = Ground.below(l, base, 6).add(0, 0.06, 0);
        FxNet.cube(l, cage.id, c, 0.95F, RED, HOLD + 10, -1);
        FxNet.cube(l, cage.id2, c, 1.2F, 0xFF8A94, HOLD + 10, -1);
        FxNet.runes(l, cage.id3, g, 2.2F, RED, HOLD + 10, 1.4F);
        FxNet.shockwave(l, g, 4.5F, RED, 16);
        FxNet.flare(l, c, 2.4F, RED, 10);
        FxNet.shake(l, c, 12, 0.3F, 6);
        Fx.sound(l, c, SoundEvents.RESPAWN_ANCHOR_SET_SPAWN, 0.6F, 1.4F);
        Fx.sound(l, c, SoundEvents.ENDER_CHEST_CLOSE, 0.7F, 0.8F);
        Fx.at(l, ParticleTypes.REVERSE_PORTAL, c, 30, 0.6, 0.15);
        if (t instanceof ServerPlayer sp) Fx.barGray(sp, K + "caged");
        Fx.bar(p, Stone.REALITY, K + "caged_target", t.getDisplayName());
        Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) {
                if (cage.release || cage.restored || age >= HOLD || !t.isAlive() || t.isRemoved() || t.level() != l) return false;
                if (t instanceof ServerPlayer sp && sp.hasDisconnected()) return false;
                pin(cage);
                if (age % 2 == 0) {
                    double a = age * 0.35;
                    for (int i = 0; i < 3; i++) {
                        double aa = a + i * Math.PI * 2 / 3, rr = 0.55 + 0.1 * Math.sin(age * 0.2 + i);
                        Vec3 v = c.add(Math.cos(aa) * rr, Math.sin(a * 0.8 + i) * 0.5, Math.sin(aa) * rr);
                        Fx.at(l, i == 0 ? ParticleTypes.END_ROD : Fx.dust(i == 1 ? RED : 0xFFFFFF, 0.8F), v, 1, 0, 0);
                    }
                }
                if (age % 40 == 0) Fx.sound(l, c, SoundEvents.BEACON_AMBIENT, 0.35F, 1.8F);
                if (age % 30 == 0) {
                    FxNet.shockwave(l, g, 2.4F, RED, 14);
                    FxNet.cube(l, cage.id, c, 0.95F, RED, HOLD - age + 10, -1);
                    FxNet.cube(l, cage.id2, c, 1.2F, 0xFF8A94, HOLD - age + 10, -1);
                }
                return true;
            }
            @Override public void end() {
                cage.restore();
                FxNet.kill(l, c, cage.id);
                FxNet.kill(l, c, cage.id2);
                FxNet.kill(l, g, cage.id3);
                FxNet.shockwave(l, g, 5F, RED, 16);
                FxNet.flare(l, c, 2.6F, RED, 10);
                FxNet.flashAround(l, c, 6, 0xFFB0B8, 0.25F, 8);
                Fx.at(l, ParticleTypes.POOF, c, 14, 0.5, 0.08);
                Fx.at(l, Fx.light(Stone.REALITY, 1.2F), c, 20, 0.6, 0.12);
                Fx.sound(l, c, SoundEvents.CHORUS_FLOWER_GROW, 0.7F, 1.2F);
                Fx.sound(l, c, SoundEvents.ENDERMAN_TELEPORT, 0.5F, 1.6F);
            }
        });
    }

    private static void pin(Cage c) {
        LivingEntity t = c.t;
        if (t instanceof ServerPlayer sp) {
            sp.setDeltaMovement(Vec3.ZERO);
            sp.hurtMarked = true;
            if (sp.position().distanceToSqr(c.anchor) > 0.04)
                sp.connection.teleport(c.anchor.x, c.anchor.y, c.anchor.z, 0F, 0F, Set.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT));
        } else {
            t.setPos(c.anchor.x, c.anchor.y, c.anchor.z);
            t.setDeltaMovement(Vec3.ZERO);
            if (t instanceof Mob m) m.getNavigation().stop();
        }
        t.fallDistance = 0;
    }

    @SubscribeEvent
    public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent e) {
        Cage c = TARGETS.get(e.getEntity().getUUID());
        if (c != null) c.restore();
    }
}
