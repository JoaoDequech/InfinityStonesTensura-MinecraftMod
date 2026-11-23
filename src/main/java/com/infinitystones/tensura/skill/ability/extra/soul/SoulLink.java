package com.infinitystones.tensura.skill.ability.extra.soul;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Soul Link (Soul). Left (aim): the aimed creature and up to five more within 12 blocks of it are chained together by
 * orange soul threads for 15 s — whatever damage one of them takes, all the others take 70 % of it too, and when one dies
 * the threads snap and tear 6 from each survivor. Right (aim an ally or pet; players must be sneaking): a bond between you
 * and them for 30 s — each of you takes only half of every hit (the other takes the other half) and healing is shared.
 * One web and one bond per player; casting again replaces it. Bosses never join a web.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class SoulLink implements Ability {
    public static final SoulLink INSTANCE = new SoulLink();
    private static final String K = "infinity.extra.link.";
    private static final int WEB_TICKS = 300, BOND_TICKS = 600;

    private static final class Web {
        final UUID owner; final ServerLevel level; final List<LivingEntity> members; final int id = FxNet.newId();
        Tasks.Handle handle; boolean over;
        Web(UUID owner, ServerLevel level, List<LivingEntity> members) { this.owner = owner; this.level = level; this.members = members; }
    }

    private static final class Bond {
        final ServerPlayer owner; final LivingEntity ally; final ServerLevel level; final int id = FxNet.newId();
        Tasks.Handle handle;
        Bond(ServerPlayer owner, LivingEntity ally) { this.owner = owner; this.ally = ally; this.level = owner.serverLevel(); }
    }

    private static final Map<UUID, Web> WEBS = new HashMap<>();
    private static final Map<UUID, Bond> BONDS = new HashMap<>();
    private static boolean sharing;

    @Override public Stone stone() { return Stone.SOUL; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        return alt ? bond(p) : web(p);
    }

    // ------------------------------------------------------------------ web of enemies

    private static boolean web(ServerPlayer p) {
        LivingEntity first = Aim.living(p, 40, e -> Rules.canHarm(p, e) && !Rules.isBoss(e));
        if (first == null) return Fx.fail(p, "infinity.extra.soul.no_target");
        ServerLevel l = p.serverLevel();
        List<LivingEntity> near = new ArrayList<>(l.getEntitiesOfClass(LivingEntity.class, first.getBoundingBox().inflate(12),
                e -> e != first && Rules.canHarm(p, e) && !Rules.isBoss(e)));
        near.sort((a, b) -> Double.compare(a.distanceToSqr(first), b.distanceToSqr(first)));
        List<LivingEntity> members = new ArrayList<>();
        members.add(first);
        for (LivingEntity e : near) { if (members.size() >= 6) break; members.add(e); }
        if (members.size() < 2) return Fx.fail(p, K + "alone");

        Web old = WEBS.remove(p.getUUID());
        if (old != null) { old.over = true; old.handle.cancel(); }
        Web w = new Web(p.getUUID(), l, members);
        WEBS.put(p.getUUID(), w);

        Vec3 start = p.getEyePosition().add(0, -0.3, 0);
        for (LivingEntity e : members) {
            Vec3 at = e.position().add(0, e.getBbHeight() * 0.5, 0);
            FxNet.beam(l, start, at, SoulKit.LIGHT, 0.08F, 10);
            FxNet.runes(l, 0, e.position().add(0, 0.06, 0), Math.max(1.4F, e.getBbWidth() * 1.8F), SoulKit.ORANGE, 40, 2F);
            FxNet.shockwave(l, e.position().add(0, 0.1, 0), 3F, SoulKit.ORANGE, 14);
            SoulKit.soulBurst(l, at, 0.8F);
            SoulKit.glow(e, WEB_TICKS);
        }
        Fx.sound(l, first.position(), SoundEvents.BELL_RESONATE, 0.6F, 0.7F);
        Fx.sound(l, first.position(), SoundEvents.SOUL_ESCAPE.value(), 0.6F, 0.6F);
        FxNet.shake(l, first.position(), 14, 0.25F, 8);
        Fx.bar(p, Stone.SOUL, K + "web", members.size());

        w.handle = Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) {
                if (w.over || WEBS.get(w.owner) != w) return false;
                members.removeIf(e -> !e.isAlive() || e.isRemoved() || e.level() != w.level);
                if (members.size() < 2) return false;
                if (age % 15 == 0) draw(w, age);
                return age < WEB_TICKS;
            }
            @Override public void end() {
                w.over = true;
                WEBS.remove(w.owner, w);
                for (LivingEntity e : members) FxNet.kill(w.level, e.position(), w.id);
            }
        });
        return true;
    }

    /** Threads between neighbours plus a little crown of light on each member. */
    private static void draw(Web w, int age) {
        ServerLevel l = w.level;
        for (int i = 0; i < w.members.size(); i++) {
            LivingEntity a = w.members.get(i), b = w.members.get((i + 1) % w.members.size());
            if (a == b) continue;
            FxNet.tether(l, w.id + i * 4096, a, b, SoulKit.ORANGE, 0.1F, 30);
            if (i == 0 && w.members.size() > 2) FxNet.tether(l, w.id + 99999, a, w.members.get(w.members.size() / 2), SoulKit.PALE, 0.06F, 30);
            Vec3 mid = a.position().add(b.position()).scale(0.5).add(0, 1.0, 0);
            Fx.at(l, ParticleTypes.SOUL, mid, 1, 0.3, 0.02);
        }
        for (LivingEntity e : w.members) {
            Fx.at(l, Fx.dust(SoulKit.ORANGE, 1.1F), e.position().add(0, e.getBbHeight() + 0.4, 0), 2, 0.2, 0);
            if (age % 45 == 0) FxNet.runes(l, 0, e.position().add(0, 0.06, 0), Math.max(1.2F, e.getBbWidth() * 1.6F), SoulKit.LIGHT, 30, -2F);
        }
    }

    private static Web webOf(LivingEntity e) {
        if (WEBS.isEmpty()) return null;
        for (Web w : WEBS.values()) if (w.members.contains(e)) return w;
        return null;
    }

    /** Damage that actually landed on a web member: the rest of the web takes 70 % of it. */
    @SubscribeEvent
    public static void landed(LivingDamageEvent.Post e) {
        if (sharing || WEBS.isEmpty() || !(e.getEntity().level() instanceof ServerLevel)) return;
        LivingEntity hurt = e.getEntity();
        Web w = webOf(hurt);
        float dealt = e.getNewDamage();
        if (w == null || dealt <= 0.5F) return;
        ServerPlayer owner = w.level.getServer().getPlayerList().getPlayer(w.owner);
        if (owner == null) return;
        sharing = true;
        try {
            for (LivingEntity o : new ArrayList<>(w.members)) {
                if (o == hurt || !o.isAlive()) continue;
                o.invulnerableTime = 0;
                o.setLastHurtByPlayer(owner);
                o.hurt(Rules.magic(owner), Math.min(dealt * 0.7F, 400F));
                Vec3 a = hurt.position().add(0, hurt.getBbHeight() * 0.6, 0), b = o.position().add(0, o.getBbHeight() * 0.6, 0);
                FxNet.beam(w.level, a, b, SoulKit.PALE, 0.12F, 5);
                Fx.at(w.level, Fx.dust(SoulKit.ORANGE, 1.4F), b, 6, 0.25, 0);
                Fx.at(w.level, ParticleTypes.SOUL_FIRE_FLAME, b, 2, 0.2, 0.03);
            }
        } finally { sharing = false; }
    }

    /** A member dies: the threads snap and tear the survivors. */
    @SubscribeEvent
    public static void died(LivingDeathEvent e) {
        if (WEBS.isEmpty()) return;
        LivingEntity dead = e.getEntity();
        Web w = webOf(dead);
        if (w == null || !(dead.level() instanceof ServerLevel l)) return;
        ServerPlayer owner = l.getServer().getPlayerList().getPlayer(w.owner);
        w.members.remove(dead);
        Vec3 from = dead.position().add(0, dead.getBbHeight() * 0.5, 0);
        FxNet.shockwave(l, dead.position().add(0, 0.1, 0), 5F, SoulKit.ORANGE, 16);
        if (owner == null) return;
        boolean prev = sharing;
        sharing = true;
        try {
            for (LivingEntity o : new ArrayList<>(w.members)) {
                if (!o.isAlive()) continue;
                FxNet.beam(l, from, o.position().add(0, o.getBbHeight() * 0.5, 0), SoulKit.LIGHT, 0.16F, 8);
                o.invulnerableTime = 0;
                o.setLastHurtByPlayer(owner);
                o.hurt(Rules.magic(owner), 6F);
                SoulKit.soulBurst(l, o.position().add(0, o.getBbHeight() * 0.5, 0), 0.7F);
            }
        } finally { sharing = prev; }
        SoulKit.wisp(l, from, owner, () -> SoulKit.gain(owner, 2F, 6F));
    }

    // ------------------------------------------------------------------ bond with an ally

    private static boolean bond(ServerPlayer p) {
        LivingEntity ally = Aim.living(p, 40, e -> e != p && Rules.isAlly(p, e) && !(e instanceof ServerPlayer sp && !sp.isShiftKeyDown()));
        if (ally == null) {
            LivingEntity any = Aim.living(p, 40, e -> e != p && Rules.isAlly(p, e));
            return Fx.fail(p, any != null ? K + "consent" : K + "no_ally");
        }
        ServerLevel l = p.serverLevel();
        Bond old = BONDS.remove(p.getUUID());
        if (old != null) old.handle.cancel();
        Bond b = new Bond(p, ally);
        BONDS.put(p.getUUID(), b);

        Vec3 pa = p.position(), pb = ally.position();
        FxNet.runes(l, 0, pa.add(0, 0.06, 0), 2.2F, SoulKit.ORANGE, 40, 2F);
        FxNet.runes(l, 0, pb.add(0, 0.06, 0), Math.max(1.6F, ally.getBbWidth() * 2F), SoulKit.ORANGE, 40, -2F);
        FxNet.shockwave(l, pa.add(0, 0.1, 0), 4F, SoulKit.LIGHT, 14);
        FxNet.shockwave(l, pb.add(0, 0.1, 0), 4F, SoulKit.LIGHT, 14);
        FxNet.beam(l, p.getEyePosition(), ally.position().add(0, ally.getBbHeight() * 0.6, 0), SoulKit.PALE, 0.1F, 12);
        SoulKit.soulBurst(l, pa.add(0, 1, 0), 1.0F);
        SoulKit.soulBurst(l, pb.add(0, ally.getBbHeight() * 0.5, 0), 1.0F);
        Fx.sound(l, pa, SoundEvents.BELL_RESONATE, 0.5F, 1.1F);
        Fx.sound(l, pa, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 0.6F);
        Fx.bar(p, Stone.SOUL, K + "bond", ally.getDisplayName());
        if (ally instanceof ServerPlayer sp) sp.displayClientMessage(Component.translatable(K + "bonded", p.getDisplayName()).withColor(SoulKit.ORANGE), true);

        b.handle = Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) {
                if (BONDS.get(p.getUUID()) != b) return false;
                if (!p.isAlive() || p.hasDisconnected() || !ally.isAlive() || ally.isRemoved() || ally.level() != p.level()) return false;
                if (age % 20 == 0) FxNet.chain(l, b.id, p, ally.position().add(0, ally.getBbHeight() * 0.5, 0), ally, SoulKit.ORANGE, 40);
                if (age % 10 == 0) {
                    Vec3 mid = p.position().add(ally.position()).scale(0.5).add(0, 1.1, 0);
                    Fx.at(l, ParticleTypes.SOUL, mid, 1, 0.5, 0.02);
                }
                return age < BOND_TICKS;
            }
            @Override public void end() {
                BONDS.remove(p.getUUID(), b);
                FxNet.kill(l, p.position(), b.id);
                Vec3 m = p.position().add(0, 1, 0);
                Fx.at(l, ParticleTypes.SOUL, m, 12, 0.4, 0.05);
                Fx.sound(l, m, SoundEvents.AMETHYST_BLOCK_BREAK, 0.6F, 0.7F);
                if (!p.hasDisconnected()) Fx.barGray(p, K + "ended");
            }
        });
        return true;
    }

    private static Bond bondOf(LivingEntity e) {
        if (BONDS.isEmpty()) return null;
        for (Bond b : BONDS.values()) if (b.owner == e || b.ally == e) return b;
        return null;
    }

    /** Hits are split in two: half stays, half goes to the partner. */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void incoming(LivingIncomingDamageEvent e) {
        if (sharing || BONDS.isEmpty()) return;
        LivingEntity hurt = e.getEntity();
        Bond b = bondOf(hurt);
        if (b == null || e.getAmount() <= 0.5F) return;
        LivingEntity other = b.owner == hurt ? b.ally : b.owner;
        if (!other.isAlive() || other.level() != hurt.level()) return;
        float half = e.getAmount() * 0.5F;
        e.setAmount(half);
        sharing = true;
        try {
            other.invulnerableTime = 0;
            other.hurt(other.damageSources().magic(), half);
            Vec3 a = hurt.position().add(0, hurt.getBbHeight() * 0.6, 0), c = other.position().add(0, other.getBbHeight() * 0.6, 0);
            FxNet.beam(b.level, a, c, SoulKit.PALE, 0.12F, 5);
            Fx.at(b.level, Fx.dust(SoulKit.ORANGE, 1.3F), c, 6, 0.25, 0);
        } finally { sharing = false; }
    }

    /** Healing is shared too: the partner receives half of it. */
    @SubscribeEvent
    public static void healed(LivingHealEvent e) {
        if (sharing || BONDS.isEmpty()) return;
        LivingEntity healed = e.getEntity();
        Bond b = bondOf(healed);
        if (b == null || e.getAmount() <= 0.2F) return;
        LivingEntity other = b.owner == healed ? b.ally : b.owner;
        if (!other.isAlive() || other.level() != healed.level()) return;
        sharing = true;
        try { other.heal(e.getAmount() * 0.5F); }
        finally { sharing = false; }
        if (healed.tickCount % 10 == 0) Fx.at(b.level, ParticleTypes.HEART, other.position().add(0, other.getBbHeight() + 0.3, 0), 1, 0.2, 0);
    }
}
