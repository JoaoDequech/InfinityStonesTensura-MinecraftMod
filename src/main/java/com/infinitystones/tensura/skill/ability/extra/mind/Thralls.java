package com.infinitystones.tensura.skill.ability.extra.mind;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Mind-controlled mobs (Hive Mind pack, Suggestion "Fight for me"). A thrall walks to its owner's target and hits it;
 * mobs that cannot attack (cows, villagers) deal a mind-strike instead. Thralls never target their owner or allies,
 * and when the target dies they pick the nearest enemy of the owner. In memory only (short effects).
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class Thralls {
    public static final int HIVE = 0, SUGGESTED = 1;

    static final class Thrall {
        final Mob mob;
        final UUID owner;
        final int kind;
        @Nullable LivingEntity target;
        int left, cd, age;
        final int threadId = FxNet.newId(), crownId = FxNet.newId();
        Thrall(Mob mob, UUID owner, int kind, int ticks) { this.mob = mob; this.owner = owner; this.kind = kind; this.left = ticks; }
    }

    private static final Map<Mob, Thrall> ALL = new LinkedHashMap<>();

    public static boolean isThrall(LivingEntity e) { return e instanceof Mob m && ALL.containsKey(m); }

    public static @Nullable UUID ownerOf(LivingEntity e) {
        Thrall t = e instanceof Mob m ? ALL.get(m) : null;
        return t == null ? null : t.owner;
    }

    public static void bind(ServerPlayer owner, Mob m, int kind, int ticks, @Nullable LivingEntity target) {
        Thrall t = ALL.get(m);
        if (t == null || !t.owner.equals(owner.getUUID()) || t.kind != kind) {
            if (t != null) release(t, false);
            t = new Thrall(m, owner.getUUID(), kind, ticks);
            ALL.put(m, t);
        }
        t.left = Math.max(t.left, ticks);
        m.setTarget(null);
        if (m.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) m.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        if (m.getBrain().hasMemoryValue(MemoryModuleType.ANGRY_AT)) m.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
        setTarget(t, target);
        refreshFx(owner, t);
    }

    public static List<Mob> of(ServerPlayer owner, int kind) {
        List<Mob> out = new ArrayList<>();
        for (Thrall t : ALL.values()) if (t.owner.equals(owner.getUUID()) && t.kind == kind) out.add(t.mob);
        return out;
    }

    /** New target for every thrall of the owner/kind. */
    public static void command(ServerPlayer owner, int kind, @Nullable LivingEntity target, int extendTo) {
        for (Thrall t : ALL.values()) if (t.owner.equals(owner.getUUID()) && t.kind == kind) {
            setTarget(t, target);
            t.left = Math.max(t.left, extendTo);
        }
    }

    public static int releaseAll(ServerPlayer owner, int kind) {
        int n = 0;
        for (Iterator<Thrall> it = ALL.values().iterator(); it.hasNext(); ) {
            Thrall t = it.next();
            if (t.owner.equals(owner.getUUID()) && t.kind == kind) { it.remove(); release(t, true); n++; }
        }
        return n;
    }

    private static void setTarget(Thrall t, @Nullable LivingEntity target) {
        t.target = target;
        if (target != null) {
            ALLOW_SET = true;
            try { t.mob.setTarget(target); } finally { ALLOW_SET = false; }
            if (t.mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, net.minecraft.world.entity.ai.memory.MemoryStatus.REGISTERED))
                t.mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        }
    }

    private static boolean ALLOW_SET;

    private static void release(Thrall t, boolean fx) {
        if (t.mob.isAlive()) {
            t.mob.setTarget(null);
            if (t.mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) t.mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            t.mob.getNavigation().stop();
        }
        if (t.mob.level() instanceof ServerLevel l) {
            FxNet.kill(l, t.mob.position(), t.threadId);
            FxNet.kill(l, t.mob.position(), t.crownId);
            if (fx && t.mob.isAlive()) {
                Vec3 c = t.mob.position().add(0, t.mob.getBbHeight() + 0.3, 0);
                Fx.at(l, Fx.light(MindKit.MIND, 0.9F), c, 10, 0.3, 0.05);
            }
        }
    }

    private static void refreshFx(ServerPlayer owner, Thrall t) {
        ServerLevel l = (ServerLevel) t.mob.level();
        if (owner.level() == l) FxNet.tether(l, t.threadId, owner, t.mob, MindKit.GOLD, 0.035F, 40);
        FxNet.crown(l, t.crownId, t.mob, MindKit.GOLD, 40);
    }

    private static float strike(Mob m) {
        AttributeInstance a = m.getAttribute(Attributes.ATTACK_DAMAGE);
        return a == null ? 0 : (float) a.getValue();
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post e) {
        if (ALL.isEmpty()) return;
        var server = e.getServer();
        for (Iterator<Thrall> it = ALL.values().iterator(); it.hasNext(); ) {
            Thrall t = it.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(t.owner);
            if (--t.left <= 0 || owner == null || !t.mob.isAlive() || t.mob.isRemoved() || owner.level() != t.mob.level()) {
                it.remove();
                release(t, true);
                continue;
            }
            t.age++;
            if (t.cd > 0) t.cd--;
            Mob m = t.mob;
            ServerLevel l = (ServerLevel) m.level();
            if (t.target != null && (!t.target.isAlive() || t.target.level() != l || t.target.distanceToSqr(m) > 48 * 48
                    || !Rules.canHarm(owner, t.target) || t.target == m)) t.target = null;
            if (t.target == null && t.age % 10 == 0) setTarget(t, nearestEnemy(owner, m));
            if (t.age % 20 == 0) refreshFx(owner, t);
            LivingEntity target = t.target;
            if (target == null) {
                // no foe: stay close to the owner
                if (t.age % 10 == 0 && m instanceof PathfinderMob pm && m.distanceToSqr(owner) > 36)
                    pm.getNavigation().moveTo(owner, 1.2);
                continue;
            }
            if (m.getTarget() != target) setTarget(t, target);
            if (t.age % 5 == 0) m.getNavigation().moveTo(target, 1.3);
            m.getLookControl().setLookAt(target, 30, 30);
            double reach = m.getBbWidth() * 0.5 + target.getBbWidth() * 0.5 + 1.3;
            if (t.cd == 0 && m.distanceToSqr(target) <= reach * reach) {
                float own = strike(m);
                // mobs without a melee attack (or with a weak one) strike with borrowed mind power
                if (own < 2F || !(m instanceof net.minecraft.world.entity.monster.Enemy)) {
                    m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                    Rules.hurt(owner, target, Math.max(3F, own + 1F));
                    Vec3 c = target.position().add(0, target.getBbHeight() * 0.6, 0);
                    Fx.at(l, Fx.dust(MindKit.MIND, 1F), c, 8, 0.25, 0.02);
                    Fx.sound(l, c, net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_HIT, 0.5F, 1.4F);
                    t.cd = 20;
                } else t.cd = 10;
            }
            if (t.age % 6 == 0) Fx.at(l, Fx.light(MindKit.MIND, 0.6F), m.position().add(0, m.getBbHeight() + 0.25, 0), 1, 0.15, 0.0);
        }
    }

    private static @Nullable LivingEntity nearestEnemy(ServerPlayer owner, Mob m) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        // what the owner last hit counts first
        LivingEntity last = owner.getLastHurtMob();
        if (last != null && last.isAlive() && last != m && !isThrallOf(last, owner) && Rules.canHarm(owner, last)
                && last.distanceToSqr(m) < 24 * 24 && owner.tickCount - owner.getLastHurtMobTimestamp() < 200) return last;
        for (LivingEntity e : m.level().getEntitiesOfClass(LivingEntity.class, m.getBoundingBox().inflate(14),
                e -> e != m && Rules.isEnemy(owner, e) && !isThrallOf(e, owner))) {
            double d = e.distanceToSqr(m);
            if (d < bd) { bd = d; best = e; }
        }
        return best;
    }

    private static boolean isThrallOf(LivingEntity e, ServerPlayer owner) {
        Thrall t = e instanceof Mob mm ? ALL.get(mm) : null;
        return t != null && t.owner.equals(owner.getUUID());
    }

    /** A thrall never turns on its owner/allies, and only takes targets we give it. */
    @SubscribeEvent
    public static void target(LivingChangeTargetEvent e) {
        if (!(e.getEntity() instanceof Mob m)) return;
        Thrall t = ALL.get(m);
        if (t == null || ALLOW_SET) return;
        LivingEntity nt = e.getNewAboutToBeSetTarget();
        if (nt == null) { if (t.target != null && t.target.isAlive()) e.setNewAboutToBeSetTarget(t.target); return; }
        ServerPlayer owner = m.getServer() == null ? null : m.getServer().getPlayerList().getPlayer(t.owner);
        if (owner == null) return;
        if (Rules.isAlly(owner, nt) || isThrallOf(nt, owner)) {
            if (t.target != null && t.target.isAlive()) e.setNewAboutToBeSetTarget(t.target);
            else e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) { ALL.clear(); }

    private Thralls() {}
}
