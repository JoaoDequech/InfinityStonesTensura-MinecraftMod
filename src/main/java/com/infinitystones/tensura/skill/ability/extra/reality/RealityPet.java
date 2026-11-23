package com.infinitystones.tensura.skill.ability.extra.reality;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.MenuNet;
import com.infinitystones.tensura.skill.ability.extra.Priv;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Rewrite Loyalty (reality_pet). Left (aim) on a mob: it becomes your pet; on your own pet: select it (orders then go
 * to it alone). Right: order menu — Sit / Follow / Attack target / Guard here / Release. After "Attack target" the next
 * left click on a creature (any distance up to 48, or the wheel press) sets the target for all your pets nearby.
 * Max 8 pets; no bosses; somebody else's pet can't be stolen. Ownership and order are saved on the mob.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class RealityPet implements Ability {
    public static final RealityPet INSTANCE = new RealityPet();
    static final String NODE = "reality_pet", MODE = "reality.reality_pet";
    static final String TAG_OWNER = "infinity_pet_owner", TAG_ORDER = "infinity_pet_order", TAG_GUARD = "infinity_pet_guard",
            TAG_NAMED = "infinity_pet_named";
    static final int MAX = 8;
    private static final double RANGE = 32, NEAR = 32;
    private static final String K = "infinity.extra.reality_pet.";

    private static final Map<UUID, UUID> SELECTED = new HashMap<>();
    private static final Map<UUID, Long> ARMED = new HashMap<>();

    @Override public Stone stone() { return Stone.REALITY; }

    /** Owner of a Reality pet, or null. Used by {@link Rules} (allies). */
    public static @Nullable UUID ownerOf(@Nullable Entity e) {
        if (!(e instanceof Mob m)) return null;
        CompoundTag t = m.getPersistentData();
        return t.hasUUID(TAG_OWNER) ? t.getUUID(TAG_OWNER) : null;
    }

    /** Owner, the owner's team, other pets of the owner, tamed animals of the owner. */
    static boolean isFriend(UUID owner, @Nullable Entity e, Level l) {
        if (e == null) return false;
        if (owner.equals(e.getUUID()) || owner.equals(ownerOf(e))) return true;
        if (e instanceof OwnableEntity o && owner.equals(o.getOwnerUUID())) return true;
        Player p = l.getPlayerByUUID(owner);
        return p != null && p.isAlliedTo(e);
    }

    // ------------------------------------------------------------------ wheel

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        if (alt) return MenuNet.open(p, Stone.REALITY, NODE, this);
        if (isArmed(p)) {
            LivingEntity t = Aim.living(p, 48, e -> Rules.canHarm(p, e) && !p.getUUID().equals(ownerOf(e)));
            if (t == null) return Fx.fail(p, K + "no_target");
            return orderAttack(p, t);
        }
        LivingEntity aimed = Aim.living(p, RANGE * Rules.mult(p, "reality"), e -> e instanceof Mob);
        if (!(aimed instanceof Mob m)) return Fx.fail(p, K + "no_mob");
        if (p.getUUID().equals(ownerOf(m))) return select(p, m);
        return tame(p, m);
    }

    private boolean tame(ServerPlayer p, Mob m) {
        if (Rules.isBoss(m)) return Fx.fail(p, K + "boss");
        if (Rules.foreignOwner(p, m) != null) return Fx.fail(p, K + "taken");
        if (m instanceof OwnableEntity o && p.getUUID().equals(o.getOwnerUUID())) return Fx.fail(p, K + "already");
        if (!Rules.canControl(p, m)) return Fx.fail(p, K + "no_mob");
        PetRegistry reg = PetRegistry.get(p.server);
        if (reg.count(p.server, p.getUUID()) >= MAX) return Fx.fail(p, K + "max", MAX);

        CompoundTag t = m.getPersistentData();
        t.putUUID(TAG_OWNER, p.getUUID());
        t.putInt(TAG_ORDER, PetBrain.FOLLOW);
        if (m.getCustomName() == null) {
            m.setCustomName(Component.translatable(K + "name", p.getGameProfile().getName()).withColor(Stone.REALITY.color() & 0xFFFFFF));
            t.putBoolean(TAG_NAMED, true);
        }
        m.setPersistenceRequired();
        m.setTarget(null);
        m.getNavigation().stop();
        PetBrain.install(m);
        reg.add(p.getUUID(), m.getUUID());

        tameFx(p, m);
        Fx.bar(p, Stone.REALITY, K + "tamed", m.getDisplayName(), reg.count(p.server, p.getUUID()), MAX);
        return true;
    }

    /** Gauntlet "Universal Dominion": binds m to p without the per-player cap (caller already checked bosses/owners). */
    public static void makePet(ServerPlayer p, Mob m) {
        CompoundTag t = m.getPersistentData();
        t.putUUID(TAG_OWNER, p.getUUID());
        t.putInt(TAG_ORDER, PetBrain.FOLLOW);
        if (m.getCustomName() == null) {
            m.setCustomName(Component.translatable(K + "name", p.getGameProfile().getName()).withColor(Stone.REALITY.color() & 0xFFFFFF));
            t.putBoolean(TAG_NAMED, true);
        }
        m.setPersistenceRequired();
        m.setTarget(null);
        m.getNavigation().stop();
        PetBrain.install(m);
        PetRegistry.get(p.server).add(p.getUUID(), m.getUUID());
        tameFx(p, m);
    }

    /** Loaded pets of an owner (any dimension). */
    public static List<Mob> petsOf(UUID owner) {
        var server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        List<Mob> out = new java.util.ArrayList<>();
        if (server == null) return out;
        for (UUID id : PetRegistry.get(server).of(owner))
            for (ServerLevel l : server.getAllLevels())
                if (l.getEntity(id) instanceof Mob m && m.isAlive()) { out.add(m); break; }
        return out;
    }

    private static void tameFx(ServerPlayer p, Mob m) {
        ServerLevel l = p.serverLevel();
        int c = Stone.REALITY.color();
        Vec3 base = m.position();
        Vec3 mid = base.add(0, m.getBbHeight() / 2, 0);
        float w = Math.max(1.2F, m.getBbWidth() * 1.4F);
        int r1 = FxNet.newId(), r2 = FxNet.newId(), tether = FxNet.newId();
        FxNet.tether(l, tether, p, m, c, 0.12F, 24);
        FxNet.runes(l, r1, base.add(0, 0.05, 0), w * 1.6F, c, 50, 1.5F);
        FxNet.runes(l, r2, base.add(0, 0.08, 0), w, 0xFF8FA0, 50, -2.5F);
        FxNet.spiral(l, base, w * 0.8F, m.getBbHeight() + 1F, c, 30, m.getId());
        FxNet.flare(l, mid, 2.2F, c, 14);
        FxNet.shockwave(l, base.add(0, 0.1, 0), w * 3F, c, 20);
        FxNet.crown(l, PetBrain.state(m) != null ? PetBrain.state(m).crownId : FxNet.newId(), m, c, 60);
        FxNet.shake(l, base, 10, 0.25F, 8);
        Fx.shell(l, mid, w, 40, Fx.dust(Stone.REALITY, 1.2F));
        Fx.at(l, ParticleTypes.HEART, base.add(0, m.getBbHeight() + 0.4, 0), 4, 0.4, 0);
        Fx.sound(l, mid, SoundEvents.ILLUSIONER_CAST_SPELL, 0.6F, 0.6F);
        Fx.sound(l, mid, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, 0.5F);
        Fx.sound(l, mid, SoundEvents.ZOMBIE_VILLAGER_CURE, 0.35F, 1.6F);
        Tasks.add(age -> {
            if (!m.isAlive() || age > 16) return false;
            double a = age * 0.6;
            for (int i = 0; i < 3; i++) {
                double b = a + i * Math.PI * 2 / 3;
                Fx.at(l, Fx.light(Stone.REALITY, 0.9F), base.add(Math.cos(b) * w, age * 0.12, Math.sin(b) * w), 1, 0, 0);
            }
            return true;
        });
    }

    private boolean select(ServerPlayer p, Mob m) {
        UUID cur = SELECTED.get(p.getUUID());
        if (m.getUUID().equals(cur)) {
            SELECTED.remove(p.getUUID());
            Priv.glow(p, m, false);
            Fx.barGray(p, K + "deselected");
            return true;
        }
        if (cur != null) {
            Entity old = p.serverLevel().getEntity(cur);
            if (old != null) Priv.glow(p, old, false);
        }
        SELECTED.put(p.getUUID(), m.getUUID());
        Fx.bar(p, Stone.REALITY, K + "selected", m.getDisplayName());
        Fx.sound(p.serverLevel(), m.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.3F);
        UUID petId = m.getUUID(), ownerId = p.getUUID();
        Tasks.add(age -> {
            if (!petId.equals(SELECTED.get(ownerId)) || !m.isAlive() || p.hasDisconnected()) { Priv.glow(p, m, false); return false; }
            if (age % 20 == 0) Priv.glow(p, m, true);
            if (age > 20 * 60) { SELECTED.remove(ownerId); Priv.glow(p, m, false); return false; }
            return true;
        });
        return true;
    }

    // ------------------------------------------------------------------ orders menu

    @Override
    public List<MenuNet.Option> menu(ServerPlayer p) {
        List<Mob> pets = targets(p);
        int order = pets.isEmpty() ? -1 : PetBrain.order(pets.get(0));
        return List.of(
                MenuNet.opt(K + "sit", "minecraft:oak_stairs", order == PetBrain.SIT),
                MenuNet.opt(K + "follow", "minecraft:lead", order == PetBrain.FOLLOW),
                MenuNet.opt(K + "attack", "minecraft:iron_sword", isArmed(p)),
                MenuNet.opt(K + "guard", "minecraft:shield", order == PetBrain.GUARD),
                MenuNet.opt(K + "release", "minecraft:feather", false));
    }

    @Override
    public void pick(ServerPlayer p, int index) {
        List<Mob> pets = targets(p);
        if (pets.isEmpty() && index != 2) { Fx.barGray(p, K + "none"); return; }
        switch (index) {
            case 0 -> setOrder(p, pets, PetBrain.SIT, "sit");
            case 1 -> setOrder(p, pets, PetBrain.FOLLOW, "follow");
            case 2 -> {
                if (nearbyPets(p).isEmpty()) { Fx.barGray(p, K + "none"); return; }
                ARMED.put(p.getUUID(), p.serverLevel().getGameTime());
                RealityNet.arm(p, true);
                Fx.bar(p, Stone.REALITY, K + "armed");
                Fx.sound(p.serverLevel(), p.position(), SoundEvents.ARMOR_EQUIP_IRON.value(), 0.6F, 0.7F);
            }
            case 3 -> {
                for (Mob m : pets) {
                    CompoundTag t = m.getPersistentData();
                    t.putInt(TAG_ORDER, PetBrain.GUARD);
                    t.putDouble(TAG_GUARD + "X", p.getX()); t.putDouble(TAG_GUARD + "Y", p.getY()); t.putDouble(TAG_GUARD + "Z", p.getZ());
                    PetBrain.State s = PetBrain.state(m);
                    if (s != null) s.attack = null;
                }
                ServerLevel l = p.serverLevel();
                int id = FxNet.newId();
                FxNet.runes(l, id, p.position().add(0, 0.05, 0), 8F, Stone.REALITY.color(), 60, 0.6F);
                FxNet.shockwave(l, p.position().add(0, 0.1, 0), 8F, Stone.REALITY.color(), 24);
                Fx.ring(l, p.position().add(0, 0.2, 0), 8, 64, Fx.dust(Stone.REALITY, 1.2F));
                Fx.sound(l, p.position(), SoundEvents.BEACON_ACTIVATE, 0.5F, 1.4F);
                Fx.bar(p, Stone.REALITY, K + "order.guard", pets.size());
            }
            case 4 -> {
                for (Mob m : pets) release(m, true);
                SELECTED.remove(p.getUUID());
                Fx.bar(p, Stone.REALITY, K + "order.release", pets.size());
            }
            default -> { }
        }
    }

    private static void setOrder(ServerPlayer p, List<Mob> pets, int order, String key) {
        for (Mob m : pets) {
            m.getPersistentData().putInt(TAG_ORDER, order);
            PetBrain.State s = PetBrain.state(m);
            if (s != null) s.attack = null;
            m.setTarget(null);
            Fx.at(p.serverLevel(), Fx.dust(Stone.REALITY, 1F), m.position().add(0, m.getBbHeight() + 0.3, 0), 6, 0.25, 0);
        }
        Fx.sound(p.serverLevel(), p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, order == PetBrain.SIT ? 0.8F : 1.2F);
        Fx.bar(p, Stone.REALITY, K + "order." + key, pets.size());
    }

    /** The selected pet alone, or all pets near the owner. */
    private static List<Mob> targets(ServerPlayer p) {
        UUID sel = SELECTED.get(p.getUUID());
        if (sel != null && p.serverLevel().getEntity(sel) instanceof Mob m && m.isAlive() && p.getUUID().equals(ownerOf(m))) return List.of(m);
        if (sel != null) SELECTED.remove(p.getUUID());
        return nearbyPets(p);
    }

    static List<Mob> nearbyPets(ServerPlayer p) {
        return p.serverLevel().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(NEAR, 16, NEAR),
                m -> m.isAlive() && p.getUUID().equals(ownerOf(m)));
    }

    static void release(Mob m, boolean fx) {
        UUID owner = ownerOf(m);
        CompoundTag t = m.getPersistentData();
        if (t.getBoolean(TAG_NAMED)) m.setCustomName(null);
        t.remove(TAG_OWNER); t.remove(TAG_ORDER); t.remove(TAG_NAMED);
        t.remove(TAG_GUARD + "X"); t.remove(TAG_GUARD + "Y"); t.remove(TAG_GUARD + "Z");
        PetBrain.uninstall(m);
        if (owner != null && m.getServer() != null) PetRegistry.get(m.getServer()).remove(owner, m.getUUID());
        if (fx && m.level() instanceof ServerLevel l) {
            Vec3 mid = m.position().add(0, m.getBbHeight() / 2, 0);
            Fx.shell(l, mid, Math.max(0.8, m.getBbWidth()), 30, Fx.dust(Stone.REALITY, 1F));
            FxNet.flare(l, mid, 1.4F, Stone.REALITY.color(), 10);
            Fx.sound(l, mid, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.6F, 0.7F);
        }
    }

    // ------------------------------------------------------------------ attack target

    private static boolean isArmed(ServerPlayer p) {
        Long at = ARMED.get(p.getUUID());
        if (at == null) return false;
        if (p.serverLevel().getGameTime() - at > 20 * 20) { ARMED.remove(p.getUUID()); return false; }
        return true;
    }

    static void pickById(ServerPlayer p, int id) {
        if (!isArmed(p)) return;
        Entity e = p.serverLevel().getEntity(id);
        if (!(e instanceof LivingEntity t) || t.distanceToSqr(p) > 56 * 56) return;
        if (!Rules.canHarm(p, t) || p.getUUID().equals(ownerOf(t))) { Fx.barGray(p, K + "no_target"); return; }
        orderAttack(p, t);
    }

    private static boolean orderAttack(ServerPlayer p, LivingEntity t) {
        ARMED.remove(p.getUUID());
        RealityNet.arm(p, false);
        List<Mob> pets = nearbyPets(p);
        if (pets.isEmpty()) return Fx.fail(p, K + "none");
        ServerLevel l = p.serverLevel();
        int c = Stone.REALITY.color();
        for (Mob m : pets) {
            PetBrain.State s = PetBrain.state(m);
            if (s == null) continue;
            s.attack = t;
            s.attackCd = 0;
            FxNet.tether(l, FxNet.newId(), m, t, c, 0.06F, 16);
        }
        FxNet.cube(l, FxNet.newId(), t.position().add(0, t.getBbHeight() / 2, 0), Math.max(0.7F, t.getBbWidth() * 0.8F), c, 30, t.getId());
        FxNet.runes(l, FxNet.newId(), t.position().add(0, 0.05, 0), Math.max(1.2F, t.getBbWidth() * 1.5F), c, 40, 3F);
        Fx.at(l, Fx.dust(Stone.REALITY, 1.4F), t.position().add(0, t.getBbHeight() + 0.3, 0), 16, 0.3, 0);
        Fx.sound(l, t.position(), SoundEvents.WOLF_GROWL, 0.6F, 0.6F);
        Fx.sound(l, p.position(), SoundEvents.EVOKER_CAST_SPELL, 0.5F, 1.4F);
        if (t instanceof ServerPlayer victim) Priv.glow(p, victim, true);
        Fx.bar(p, Stone.REALITY, K + "order.attack", t.getDisplayName(), pets.size());
        return true;
    }

    /** Pets near the owner turn on attacker (those sitting stay put). */
    private static void rally(ServerPlayer owner, LivingEntity attacker, boolean includeGuards) {
        if (attacker == owner || isFriend(owner.getUUID(), attacker, owner.level())) return;
        for (Mob m : owner.serverLevel().getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(24, 12, 24),
                m -> m.isAlive() && owner.getUUID().equals(ownerOf(m)))) {
            PetBrain.State s = PetBrain.state(m);
            if (s == null || s.attack != null) continue;
            int order = PetBrain.order(m);
            if (order == PetBrain.SIT || (order == PetBrain.GUARD && !includeGuards)) continue;
            s.attack = attacker;
        }
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void join(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() && e.getEntity() instanceof Mob m && ownerOf(m) != null) PetBrain.install(m);
    }

    @SubscribeEvent
    public static void changeTarget(LivingChangeTargetEvent e) {
        LivingEntity next = e.getNewAboutToBeSetTarget();
        if (next == null || e.getEntity().level().isClientSide()) return;
        UUID owner = ownerOf(e.getEntity());
        if (owner != null && isFriend(owner, next, e.getEntity().level())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void damage(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide()) return;
        Entity src = e.getSource().getEntity();
        // a pet never hurts its owner or the owner's friends (creeper blasts included)
        UUID petOwner = ownerOf(src);
        if (petOwner != null && isFriend(petOwner, victim, victim.level())) { e.setCanceled(true); return; }
        if (!(src instanceof LivingEntity attacker)) return;
        if (victim instanceof ServerPlayer owner) rally(owner, attacker, false);
        UUID owner = ownerOf(victim);
        if (owner != null && !isFriend(owner, attacker, victim.level())) {
            PetBrain.State s = victim instanceof Mob m ? PetBrain.state(m) : null;
            if (s != null && s.attack == null && PetBrain.order((Mob) victim) != PetBrain.SIT) s.attack = attacker;
            if (victim.getServer() != null && victim.getServer().getPlayerList().getPlayer(owner) instanceof ServerPlayer op
                    && op.level() == victim.level() && op.distanceToSqr(victim) < 32 * 32) rally(op, attacker, true);
        }
    }

    @SubscribeEvent
    public static void attack(AttackEntityEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getTarget() instanceof LivingEntity t)) return;
        if (isArmed(p) && Stone.REALITY.isHeldBy(p)) {
            if (Rules.canHarm(p, t) && !p.getUUID().equals(ownerOf(t))) { orderAttack(p, t); e.setCanceled(true); }
            return;
        }
        // like wolves: pets that follow you join the fight you start
        if (Rules.canHarm(p, t)) rally(p, t, false);
    }

    @SubscribeEvent
    public static void death(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof Mob m) || m.level().isClientSide()) return;
        UUID owner = ownerOf(m);
        if (owner == null) return;
        if (m.getServer() != null) {
            PetRegistry.get(m.getServer()).remove(owner, m.getUUID());
            ServerPlayer p = m.getServer().getPlayerList().getPlayer(owner);
            if (p != null) Fx.barGray(p, K + "died", m.getDisplayName());
        }
        PetBrain.uninstall(m);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        SELECTED.remove(e.getEntity().getUUID());
        ARMED.remove(e.getEntity().getUUID());
    }
}
