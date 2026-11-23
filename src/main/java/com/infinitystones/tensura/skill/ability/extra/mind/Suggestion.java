package com.infinitystones.tensura.skill.ability.extra.mind;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.Displays;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.MenuNet;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Suggestion: mental orders on the aimed creature. Left = give the chosen order; right = order menu.
 * Sleep (15 s, mob frozen with golden Zzz, wakes when hurt; player: drowsy), Flee (10 s; player: pushed away),
 * Fight for me (20 s thrall, attacks your foes), Freeze (6 s, also players: rooted, look free),
 * Persuade (villager: positive gossip → better prices; piglin: one free barter per 5 min + calm; player: camera
 * pulled to look at you 3 s; other mobs: calm toward you 30 s). Bosses are immune.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class Suggestion implements Ability {
    public static final Suggestion INSTANCE = new Suggestion();
    public static final String MODE = "mind.suggestion";
    public static final int SLEEP = 0, FLEE = 1, FIGHT = 2, FREEZE = 3, PERSUADE = 4;
    private static final String[] KEYS = {"sleep", "flee", "fight", "freeze", "persuade"};
    private static final String[] ICONS = {"minecraft:red_bed", "minecraft:feather", "minecraft:iron_sword", "minecraft:packed_ice", "minecraft:emerald"};
    /** Mob tag while asleep/frozen by us (NoAI); a leftover after a crash is cleaned when the mob loads. */
    private static final String HELD_TAG = "infinity_mind_held";
    private static final String BARTER_TAG = "infinity_mind_barter";

    @Override public Stone stone() { return Stone.MIND; }

    @Override
    public List<MenuNet.Option> menu(ServerPlayer p) {
        int cur = ExtraSettings.choice(p, MODE, SLEEP);
        return java.util.stream.IntStream.range(0, KEYS.length)
                .mapToObj(i -> MenuNet.opt("infinity.extra.mind.suggestion." + KEYS[i], ICONS[i], i == cur)).toList();
    }

    @Override
    public void pick(ServerPlayer p, int index) {
        if (index < 0 || index >= KEYS.length) return;
        ExtraSettings.setChoice(p, MODE, index);
        Fx.bar(p, Stone.MIND, "infinity.extra.mind.suggestion.chosen", Component.translatable("infinity.extra.mind.suggestion." + KEYS[index]));
        Fx.sound(p.serverLevel(), p.getEyePosition(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, 1.4F);
    }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        if (alt) return MenuNet.open(p, Stone.MIND, "suggestion", this);
        int order = ExtraSettings.choice(p, MODE, SLEEP);
        LivingEntity t = Aim.living(p, 32, e -> order == PERSUADE ? e != p && !Rules.isBoss(e) : Rules.canControl(p, e));
        if (t == null) return Fx.fail(p, "infinity.extra.mind.no_target");
        if (Rules.isBoss(t)) return Fx.fail(p, "infinity.extra.mind.boss");
        boolean player = t instanceof ServerPlayer;
        boolean ok = switch (order) {
            case SLEEP -> sleep(p, t);
            case FLEE -> flee(p, t, 200);
            case FIGHT -> {
                if (!(t instanceof Mob m)) yield Fx.fail(p, "infinity.extra.mind.suggestion.fight_player");
                Thralls.bind(p, m, Thralls.SUGGESTED, 400, null);
                yield true;
            }
            case FREEZE -> freeze(p, t, 120, false);
            default -> persuade(p, t);
        };
        if (!ok) return false;
        castFx(p, t, order);
        Fx.bar(p, Stone.MIND, "infinity.extra.mind.suggestion.done", Component.translatable("infinity.extra.mind.suggestion." + KEYS[order]), t.getDisplayName());
        if (player) Fx.bar((ServerPlayer) t, Stone.MIND, "infinity.extra.mind.suggestion.victim." + KEYS[order], p.getDisplayName());
        return true;
    }

    private static void castFx(ServerPlayer p, LivingEntity t, int order) {
        ServerLevel l = p.serverLevel();
        Vec3 eye = p.getEyePosition(), tc = t.position().add(0, t.getBbHeight() * 0.8, 0);
        FxNet.beam(l, eye, tc, MindKit.GOLD, 0.06F, 10);
        FxNet.tunnel(l, eye.add(p.getLookAngle()), tc, 0.45F, MindKit.PALE, 14);
        FxNet.runes(l, 0, t.position().add(0, 0.05, 0), Math.max(1F, t.getBbWidth() * 1.3F), MindKit.GOLD, 40, order % 2 == 0 ? 1.5F : -1.5F);
        FxNet.crown(l, 0, t, MindKit.GOLD, 50);
        FxNet.shockwave(l, t.position().add(0, 0.05, 0), 3F, MindKit.PALE, 14);
        MindKit.spark(l, tc, 16);
        Fx.sound(l, eye, SoundEvents.ILLUSIONER_CAST_SPELL, 0.5F, 1.4F);
        Fx.sound(l, tc, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 1.2F);
    }

    // ------------------------------------------------------------------ Sleep / Freeze (held mobs)

    private static final Map<LivingEntity, Tasks.Handle> HELD = new HashMap<>();

    private static boolean sleep(ServerPlayer p, LivingEntity t) {
        if (t instanceof ServerPlayer sp) {
            // players: heavy drowsiness instead of losing control
            sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
            sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 3));
            sp.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 2));
            FxNet.screen(sp, FxNet.NIGHTMARE, 30);
            return true;
        }
        if (!(t instanceof Mob m)) return false;
        hold(p, m, 300, true);
        return true;
    }

    private static boolean freeze(ServerPlayer p, LivingEntity t, int ticks, boolean quiet) {
        if (t instanceof ServerPlayer sp) {
            Vec3 at = sp.position();
            MindKit.lock(sp, true);
            int id = FxNet.newId();
            Tasks.add(new Tasks.Task() {
                @Override public boolean tick(int age) {
                    if (!sp.isAlive() || sp.hasDisconnected()) return false;
                    if (sp.position().distanceToSqr(at) > 0.04) sp.connection.teleport(at.x, at.y, at.z, 0, 0,
                            java.util.EnumSet.of(net.minecraft.world.entity.RelativeMovement.X_ROT, net.minecraft.world.entity.RelativeMovement.Y_ROT));
                    if (age % 20 == 0) FxNet.cube(sp.serverLevel(), id, at.add(0, 1, 0), 0.75F, MindKit.GOLD, 30, sp.getId());
                    if (age % 3 == 0) Fx.ring(sp.serverLevel(), at.add(0, 0.1 + (age % 30) / 15.0, 0), 0.6, 10, Fx.dust(MindKit.MIND, 0.7F));
                    return age < ticks;
                }
                @Override public void end() { MindKit.lock(sp, false); FxNet.kill(sp.serverLevel(), at, id); }
            });
            return true;
        }
        if (!(t instanceof Mob m)) return false;
        hold(p, m, ticks, false);
        return true;
    }

    /** NoAI hold with an ice/zzz look; asleep mobs wake when hurt. */
    private static void hold(ServerPlayer p, Mob m, int ticks, boolean asleep) {
        Tasks.Handle old = HELD.remove(m);
        if (old != null) old.cancel();
        boolean wasNoAi = m.isNoAi() && !m.getTags().contains(HELD_TAG);
        if (wasNoAi) return; // already frozen by something else
        m.addTag(HELD_TAG);
        m.setNoAi(true);
        m.setTarget(null);
        m.getNavigation().stop();
        ServerLevel l = (ServerLevel) m.level();
        int id = FxNet.newId();
        Display.TextDisplay zzz = asleep ? Displays.text(l, Component.literal("Z z z").withColor(MindKit.GOLD), m.position().add(0, m.getBbHeight() + 0.5, 0), 0, 1.2F) : null;
        Tasks.Handle[] h = new Tasks.Handle[1];
        h[0] = Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int age) {
                if (!m.isAlive() || m.isRemoved()) return false;
                if (age % 20 == 0) {
                    if (asleep) FxNet.orb(l, id, m.position(), Math.max(0.6F, m.getBbWidth() * 0.9F), MindKit.PALE, 30, m.getId(), new Vec3(0, m.getBbHeight() * 0.5, 0));
                    else FxNet.cube(l, id, m.position().add(0, m.getBbHeight() / 2, 0), Math.max(0.6F, m.getBbWidth() * 0.7F), MindKit.GOLD, 30, m.getId());
                }
                if (zzz != null) {
                    zzz.moveTo(m.getX(), m.getY() + m.getBbHeight() + 0.4 + Math.sin(age * 0.1) * 0.12, m.getZ());
                    if (age % 10 == 0) Fx.at(l, Fx.light(MindKit.MIND, 0.6F), m.position().add(0, m.getBbHeight() + 0.3, 0), 2, 0.25, 0.01);
                    if (age % 50 == 0) Fx.sound(l, m.position(), SoundEvents.FOX_SLEEP, 0.4F, 0.8F);
                }
                return age < ticks;
            }
            @Override public void end() {
                HELD.remove(m, h[0]);
                if (m.getTags().contains(HELD_TAG)) { m.removeTag(HELD_TAG); m.setNoAi(false); }
                Displays.remove(zzz);
                FxNet.kill(l, m.position(), id);
                if (m.isAlive()) Fx.at(l, Fx.light(MindKit.MIND, 0.8F), m.position().add(0, m.getBbHeight(), 0), 8, 0.3, 0.05);
            }
        });
        if (asleep) SLEEPING.put(m, h[0]);
        HELD.put(m, h[0]);
    }

    private static final Map<LivingEntity, Tasks.Handle> SLEEPING = new java.util.WeakHashMap<>();

    @SubscribeEvent
    public static void wake(LivingIncomingDamageEvent e) {
        Tasks.Handle h = SLEEPING.remove(e.getEntity());
        if (h != null) h.cancel();
    }

    @SubscribeEvent
    public static void leftover(EntityJoinLevelEvent e) {
        if (!e.getLevel().isClientSide() && e.getEntity() instanceof Mob m && m.getTags().contains(HELD_TAG) && !HELD.containsKey(m)) {
            m.removeTag(HELD_TAG);
            m.setNoAi(false);
        }
    }

    // ------------------------------------------------------------------ Flee

    private static final Map<LivingEntity, Calm> CALM = new java.util.WeakHashMap<>();
    private record Calm(UUID from, long until) {}

    /** Shared with Nightmare: the target runs from the caster for ticks. Players are thrown away instead. */
    static boolean flee(ServerPlayer p, LivingEntity t, int ticks) {
        if (t instanceof ServerPlayer sp) {
            Vec3 away = sp.position().subtract(p.position()).multiply(1, 0, 1);
            if (away.lengthSqr() < 1.0E-4) away = p.getLookAngle().multiply(1, 0, 1);
            MindKit.push(sp, away.normalize().scale(1.6).add(0, 0.45, 0));
            sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1));
            return true;
        }
        if (!(t instanceof Mob m)) return false;
        CALM.put(m, new Calm(p.getUUID(), m.level().getGameTime() + ticks));
        m.setTarget(null);
        if (m.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) m.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        Tasks.add(age -> {
            if (!m.isAlive() || !p.isAlive() || m.level() != p.level()) return false;
            if (age % 10 == 0 && m instanceof PathfinderMob pm) {
                Vec3 to = DefaultRandomPos.getPosAway(pm, 16, 7, p.position());
                if (to != null) pm.getNavigation().moveTo(to.x, to.y, to.z, 1.5);
            }
            if (age % 4 == 0) Fx.at((ServerLevel) m.level(), Fx.dust(MindKit.MIND, 0.7F), m.position().add(0, m.getBbHeight() + 0.2, 0), 1, 0.2, 0);
            return age < ticks;
        });
        return true;
    }

    /** Calmed/fleeing mobs do not target the caster. */
    @SubscribeEvent
    public static void target(LivingChangeTargetEvent e) {
        Calm c = CALM.get(e.getEntity());
        if (c == null) return;
        if (e.getEntity().level().getGameTime() > c.until) { CALM.remove(e.getEntity()); return; }
        LivingEntity nt = e.getNewAboutToBeSetTarget();
        if (nt != null && nt.getUUID().equals(c.from)) e.setCanceled(true);
    }

    // ------------------------------------------------------------------ Persuade

    private static boolean persuade(ServerPlayer p, LivingEntity t) {
        ServerLevel l = p.serverLevel();
        if (t instanceof ServerPlayer sp) {
            Tasks.add(age -> {
                if (!sp.isAlive() || !p.isAlive() || sp.level() != p.level()) return false;
                MindKit.look(sp, MindKit.yawTo(sp, p.getEyePosition()), MindKit.pitchTo(sp, p.getEyePosition()));
                return age < 60;
            });
            FxNet.screen(sp, FxNet.AVATAR_TINT, 60);
            return true;
        }
        if (t instanceof Villager v) {
            v.getGossips().add(p.getUUID(), GossipType.MAJOR_POSITIVE, 10);
            v.getGossips().add(p.getUUID(), GossipType.MINOR_POSITIVE, 25);
            v.setUnhappyCounter(0);
            l.broadcastEntityEvent(v, (byte) 14); // happy villager particles
            Fx.sound(l, v.position(), SoundEvents.VILLAGER_CELEBRATE, 0.6F, 1F);
            calm(p, v, 600);
            return true;
        }
        if (t instanceof AbstractVillager v) { // wandering trader
            l.broadcastEntityEvent(v, (byte) 14);
            calm(p, v, 600);
            return true;
        }
        if (t instanceof AbstractPiglin pig) {
            calm(p, pig, 1200);
            pig.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT);
            pig.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            if (pig instanceof net.minecraft.world.entity.monster.piglin.Piglin piglin) {
                long now = l.getGameTime();
                long last = piglin.getPersistentData().getLong(BARTER_TAG);
                if (last == 0 || now - last > 6000) {
                    piglin.getPersistentData().putLong(BARTER_TAG, now);
                    barter(p, piglin);
                } else Fx.barGray(p, "infinity.extra.mind.suggestion.barter_wait");
            }
            return true;
        }
        if (t instanceof Mob m) { calm(p, m, 600); return true; }
        return false;
    }

    private static void calm(ServerPlayer p, Mob m, int ticks) {
        CALM.put(m, new Calm(p.getUUID(), m.level().getGameTime() + ticks));
        if (m.getTarget() == p) m.setTarget(null);
        if (m.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) m.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
    }

    /** One roll of the piglin bartering loot, thrown to the caster (rate-limited per piglin). */
    private static void barter(ServerPlayer p, net.minecraft.world.entity.monster.piglin.Piglin piglin) {
        ServerLevel l = p.serverLevel();
        LootTable table = l.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.PIGLIN_BARTERING);
        List<ItemStack> items = table.getRandomItems(new LootParams.Builder(l).withParameter(LootContextParams.THIS_ENTITY, piglin)
                .create(LootContextParamSets.PIGLIN_BARTER));
        piglin.swing(net.minecraft.world.InteractionHand.OFF_HAND);
        Vec3 from = piglin.position().add(0, piglin.getBbHeight() * 0.7, 0);
        Vec3 dir = p.position().subtract(from).normalize().scale(0.3).add(0, 0.2, 0);
        for (ItemStack s : items) {
            ItemEntity ie = new ItemEntity(l, from.x, from.y, from.z, s, dir.x, dir.y, dir.z);
            ie.setDefaultPickUpDelay();
            l.addFreshEntity(ie);
        }
        Fx.sound(l, from, SoundEvents.PIGLIN_ADMIRING_ITEM, 0.6F, 1F);
    }

}
