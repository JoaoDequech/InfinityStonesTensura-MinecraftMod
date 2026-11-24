package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.registry.ModSounds;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Original command nodes whose commands needed Palladium ("ability lock", flight attribute), Pehkui ("scale") or the
 * TimeClock mod ("timeclock ..."), none of which are in the pack. Same intent, vanilla Java, no OP:
 * <ul>
 *   <li>mind.flight-dummy (passive): Mind Stone flight (original palladium:flight_speed 3) unless "Flight Toggle" is on.</li>
 *   <li>mind.flight_toggle / power.radiation_texture_toggle / time.screen_toggle (now wheel toggles): set the original
 *       tags no_fly / no_radiation_texture / no_time_screen.</li>
 *   <li>time.daylight_cycle_toggle (wheel toggle): on = doDaylightCycle false, off = true.</li>
 *   <li>time.time_freeze (toggle, original "timeclock pauseTime"): freezes the world's tick (vanilla tick freeze —
 *       players keep moving); undone when toggled off, the stone leaves, the caster logs out or the server stops.</li>
 *   <li>time.reset_time_speed (press): left = slower world (tickrateCycle +1 up to 5, caster gets the original Speed/Haste),
 *       sneak/right = faster world (−1 down to −5); 0 = natural. Same tick rates as the original timeclock table.</li>
 *   <li>reality.size_toggle (press): left = bigger (SizeCycle −1 … −4 → ×2…×5), sneak/right = smaller (+1 … +9 → ×0.9…×0.1),
 *       via the vanilla "scale" attribute (+ movement like Pehkui motion). Back to ×1 when passing 0 or dropping the stone.</li>
 *   <li>power.size_toggle (press): original scroll abilities — left = DamageCycle +1, sneak/right = −1 (0..11) → base
 *       attack damage and Power Blast damage 2^cycle.</li>
 *   <li>mind.intangibility_effects: handled by {@link Intangibility}.</li>
 * </ul>
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class CommandPorts {
    public static void register(Map<String, Ported> byMode) {
        byMode.put("mind.flight-dummy", new Flight());
        byMode.put("mind.flight_toggle", new TagToggle("no_fly", "flight"));
        byMode.put("power.radiation_texture_toggle", new TagToggle("no_radiation_texture", "radiation"));
        byMode.put("time.screen_toggle", new TagToggle("no_time_screen", "screen"));
        byMode.put("time.daylight_cycle_toggle", new Daylight());
        byMode.put("time.time_freeze", new TimeFreeze());
        byMode.put("time.reset_time_speed", new TimeSpeed());
        byMode.put("reality.size_toggle", new Size());
        byMode.put("power.size_toggle", new DamageCycle());
        byMode.put("power.explosion2", new CosmicExplosion());
        byMode.put("time.fast_forward", new FastForward());
        byMode.put("soul.invulnerability", new Invulnerability());
        byMode.put("soul.glow", new SoulGlow());
        byMode.put("time.water_walking", new WaterWalking());
        byMode.put("power.explosion_timer", new PowerPunishment());
        // wheel scroll (VariableAbility) applies at once; the 4-tick watcher below is the fallback
        com.infinitystones.tensura.wheel.VariableAbility.onChange(TIME_SPEED_MODE, (p, n, v) -> {
            if (Stone.TIME.isHeldBy(p) && v != p.getPersistentData().getInt(TICK_CYCLE)) timeSpeedChanged(p, Math.clamp(v, -5, 5));
        });
        com.infinitystones.tensura.wheel.VariableAbility.onChange(PUNCH_MODE, (p, n, v) -> {
            if (Stone.POWER.isHeldBy(p) && p.getPersistentData().getBoolean(PUNCH_ON)) applyPunch(p, Math.clamp(v, 0, maxPunch(p)), true);
        });
        // original black_hole_on could not be used while a black hole was on (unlocking: not has_tag blackholeon)
        byMode.put("space.black_hole_on", new Ported() {
            @Override public Boolean press(ServerPlayer p, StoneTree.Node n, boolean alt) {
                if (!p.getTags().contains("blackholeon")) return null;
                msg(p, "black_hole_active", ChatFormatting.DARK_AQUA);
                return false;
            }
        });
        // original exit_soulworld only worked inside the soul world (tag in_soul); otherwise the original commands replay
        byMode.put("soul.exit_soulworld", new Ported() {
            @Override public Boolean press(ServerPlayer p, StoneTree.Node n, boolean alt) {
                if (p.getTags().contains("in_soul")) return null;
                msg(p, "not_in_soulworld", ChatFormatting.GOLD);
                return false;
            }
        });
        byMode.put("mind.intangibility_effects", new Ported() {});
        byMode.put("mind.intangibility_flight", new Ported() {});
        byMode.put("mind.intangibility_ground", new Ported() {});
    }

    static void msg(ServerPlayer p, String key, ChatFormatting color, Object... args) {
        if (com.infinitystones.tensura.skill.NodeEffects.quiet()) return;
        p.displayClientMessage(Component.translatable("ability.infinity.port." + key, args).withStyle(color), true);
    }

    // ---------------- Mind flight ----------------

    /** Players we granted flight to (so we never revoke flight given by Tensura, creative, etc.). */
    private static final Set<UUID> FLYERS = new HashSet<>();

    static final class Flight implements Ported {
        @Override public void tick(ServerPlayer p, StoneTree.Node n, int ticks) {
            boolean blocked = p.getTags().contains("no_fly");
            if (!blocked && !p.getAbilities().mayfly) {
                p.getAbilities().mayfly = true;
                FLYERS.add(p.getUUID());
                p.onUpdateAbilities();
            } else if (blocked && FLYERS.contains(p.getUUID())) {
                revoke(p);
            }
        }
        @Override public void stop(ServerPlayer p, StoneTree.Node n) {
            // a switch found ON at login was left by a crash: the saved abilities still carry its flight
            if (FLYERS.contains(p.getUUID()) || com.infinitystones.tensura.skill.NodeEffects.loginCleanup()) revoke(p);
            com.infinitystones.tensura.wheel.FlightControl.restoreSpeed(p);
        }
    }

    static void revoke(ServerPlayer p) {
        FLYERS.remove(p.getUUID());
        BLAST_IMMUNE.remove(p.getUUID());
        if (p.isCreative() || p.isSpectator()) return;
        p.getAbilities().mayfly = false;
        p.getAbilities().flying = false;
        p.onUpdateAbilities();
    }

    // ---------------- simple tag toggles ----------------

    static final class TagToggle implements Ported {
        private final String tag, key;
        TagToggle(String tag, String key) { this.tag = tag; this.key = key; }
        @Override public void start(ServerPlayer p, StoneTree.Node n) {
            p.addTag(tag);
            msg(p, "toggle." + key + ".on", ChatFormatting.GRAY);
        }
        @Override public void stop(ServerPlayer p, StoneTree.Node n) {
            p.removeTag(tag);
            msg(p, "toggle." + key + ".off", ChatFormatting.GREEN);
        }
    }

    // ---------------- daylight cycle ----------------

    static final class Daylight implements Ported {
        @Override public void start(ServerPlayer p, StoneTree.Node n) {
            p.server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, p.server);
            PortFx.sound(p.serverLevel(), p.position(), ModSounds.TIME_STONE, 0.5F, 1F);
        }
        @Override public void stop(ServerPlayer p, StoneTree.Node n) {
            p.server.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(true, p.server);
            PortFx.sound(p.serverLevel(), p.position(), ModSounds.TIME_STONE, 0.5F, 0.8F);
        }
    }

    // ---------------- time freeze (server-wide, like timeclock) ----------------

    private static @Nullable UUID freezer;

    static boolean isFreezer(ServerPlayer p) { return p.getUUID().equals(freezer); }
    static boolean timeSpeedOn(ServerPlayer p) { return p.getPersistentData().getInt(TICK_CYCLE) != 0; }
    static boolean isFastForward(ServerPlayer p) { return FAST_FORWARDERS.contains(p.getUUID()); }
    private static final Set<UUID> FAST_FORWARDERS = new HashSet<>();

    static final class TimeFreeze implements Ported {
        @Override public void start(ServerPlayer p, StoneTree.Node n) {
            PortFx.sound(p.serverLevel(), p.position(), ModSounds.TIME_STONE, 0.6F, 1F);
            p.addTag("pauseTime");
            freezer = p.getUUID();
            p.server.tickRateManager().setFrozen(true);
            greenPulse(p);
        }
        @Override public void stop(ServerPlayer p, StoneTree.Node n) {
            PortFx.sound(p.serverLevel(), p.position(), ModSounds.TIME_STONE, 0.6F, 0.8F);
            p.removeTag("pauseTime");
            if (p.getUUID().equals(freezer)) { freezer = null; p.server.tickRateManager().setFrozen(false); }
            greenPulse(p);
        }
    }

    private static void greenPulse(ServerPlayer p) {
        for (int r = 1; r <= 4; r++) PortFx.ring(p.serverLevel(), p.position().add(0, 0.2, 0), r * 1.5, 24 + r * 8,
                PortFx.dust(Stone.TIME, 1.4F), 0);
    }

    // ---------------- time speed (server-wide tick rate, like timeclock) ----------------

    private static final String TICK_CYCLE = "infinity_tickrate_cycle";
    private static @Nullable UUID timeSpeeder;
    /** Original table: -5..-1 → tickrate 40,36,32,28,24 ; 0 → 20 ; 1..5 → virtual tickrate 16,12,8,4,2. */
    private static float rate(int cycle) {
        return switch (cycle) {
            case -5 -> 40; case -4 -> 36; case -3 -> 32; case -2 -> 28; case -1 -> 24;
            case 1 -> 16; case 2 -> 12; case 3 -> 8; case 4 -> 4; case 5 -> 2;
            default -> 20;
        };
    }

    static final class TimeSpeed implements Ported {
        @Override public Boolean press(ServerPlayer p, StoneTree.Node n, boolean alt) {
            int cur = p.getPersistentData().getInt(TICK_CYCLE);
            int cycle = Levels.set(p, TIME_SPEED_MODE, -5, 5, cur + (alt ? -1 : 1));
            if (cycle == cur) return false;
            timeSpeedChanged(p, cycle);
            return true;
        }
    }

    static final String TIME_SPEED_MODE = "time.reset_time_speed", SIZE_MODE = "reality.size_toggle", PUNCH_MODE = "power.size_toggle";

    static void timeSpeedChanged(ServerPlayer p, int cycle) {
        applyTimeSpeed(p, cycle);
        PortFx.sound(p.serverLevel(), p.position(), SoundEvents.COMPARATOR_CLICK, 0.5F, 1F);
        PortFx.sound(p.serverLevel(), p.position(), ModSounds.USE_INFINITY_GAUNTLET, 0.4F, 1F);
        greenPulse(p);
        PolishFx.timeSpeed(p, cycle);
        FxNet.circleOn(p, 0.05, 3F + Math.abs(cycle) * 0.5F, Stone.TIME, 30, FxNet.TIERS_2 | FxNet.HEPTA | (cycle < 0 ? FxNet.FAST : 0));
        msg(p, "time_speed", ChatFormatting.GREEN, String.format(java.util.Locale.ROOT, "%.0f", rate(cycle)));
    }

    private static final int[] HASTE = {0, 105, 110, 112, 114, 116}; // original time_stone.json amplifiers

    static void applyTimeSpeed(ServerPlayer p, int cycle) {
        p.getPersistentData().putInt(TICK_CYCLE, cycle);
        p.server.tickRateManager().setTickRate(rate(cycle));
        timeSpeeder = cycle == 0 ? null : p.getUUID();
        p.removeEffect(MobEffects.MOVEMENT_SPEED);
        p.removeEffect(MobEffects.DIG_SPEED);
        if (cycle > 0) { // original: Speed cycle-1 (amplifier = cycle) and huge Haste while the world is slowed
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, MobEffectInstance.INFINITE_DURATION, cycle, false, false, true));
            p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, MobEffectInstance.INFINITE_DURATION, HASTE[cycle], false, false, true));
        }
    }

    private static void resetTimeSpeed(ServerPlayer p) {
        if (p.getPersistentData().getInt(TICK_CYCLE) != 0 || p.getUUID().equals(timeSpeeder)) applyTimeSpeed(p, 0);
    }

    // ---------------- Reality size ----------------

    private static final String SIZE_CYCLE = "infinity_size_cycle";
    private static final ResourceLocation SIZE_ID = ResourceLocation.fromNamespaceAndPath("infinity", "port/reality/size");
    private static final ResourceLocation SIZE_MOTION_ID = ResourceLocation.fromNamespaceAndPath("infinity", "port/reality/size_motion");

    static double scale(int cycle) { return cycle < 0 ? 1 - cycle : cycle > 0 ? Math.max(0.1, 1 - cycle * 0.1) : 1; }

    static final class Size implements Ported {
        @Override public Boolean press(ServerPlayer p, StoneTree.Node n, boolean alt) {
            int cur = p.getPersistentData().getInt(SIZE_CYCLE);
            int next = Levels.set(p, SIZE_MODE, -4, 9, cur + (alt ? 1 : -1)); // original clamps (no wrap)
            if (next == cur) return false;
            applySize(p, next);
            FxNet.circleOn(p, 0.05, (float) (1.2 * scale(next)) + 0.6F, Stone.REALITY, 24, FxNet.TIERS_1);
            PolishFx.sizeChange(p, next, !alt);
            PortFx.sound(p.serverLevel(), p.position(), ModSounds.REALITY_STONE_SIZE, 0.5F, alt ? 1.3F : 0.8F);
            p.serverLevel().sendParticles(PortFx.dust(Stone.REALITY, 1.5F), p.getX(), p.getY(0.5), p.getZ(), 60,
                    p.getBbWidth() * 0.6, p.getBbHeight() * 0.4, p.getBbWidth() * 0.6, 0);
            msg(p, "size", ChatFormatting.RED, String.format(java.util.Locale.ROOT, "%.1f", scale(next)));
            return true;
        }
    }

    static void applySize(ServerPlayer p, int cycle) {
        p.getPersistentData().putInt(SIZE_CYCLE, cycle);
        double s = scale(cycle);
        AttributeModifier.remove(p, Attributes.MOVEMENT_SPEED, SIZE_MOTION_ID);
        if (cycle == 0) {
            AttributeModifier.remove(p, Attributes.SCALE, SIZE_ID);
        } else {
            AttributeModifier.apply(p, Attributes.SCALE, SIZE_ID, s - 1,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            // original: pehkui motion x2..x5 only when growing; normal speed when small
            if (cycle < 0) AttributeModifier.apply(p, Attributes.MOVEMENT_SPEED, SIZE_MOTION_ID, s - 1,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        }
    }

    // ---------------- Power damage cycle ----------------

    /**
     * Power "Damage Control" (original size_toggle + scroll abilities): now an on/off switch on the wheel. ON = base
     * attack damage 2^level (level 0..11 = 1..2048, gated by the bought unlockN_dummy nodes) or level 12 = INFINITE
     * punch (needs 2048 bought). OFF = normal punch (1). The level is the wheel scroll setting ("settings" 0..12).
     */
    static final class DamageCycle implements Ported {
        @Override public void start(ServerPlayer p, StoneTree.Node n) {
            p.getPersistentData().putBoolean(PUNCH_ON, true);
            applyPunch(p, Levels.get(p, PUNCH_MODE, 0, maxPunch(p), 0), true);
        }
        @Override public void stop(ServerPlayer p, StoneTree.Node n) {
            p.getPersistentData().putBoolean(PUNCH_ON, false);
            EnergyBeam.setDamageCycle(p, 0);
            var attr = p.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attr != null) attr.setBaseValue(1);
            PortFx.sound(p.serverLevel(), p.position(), SoundEvents.COMPARATOR_CLICK, 0.5F, 0.6F);
            msg(p, "punch_off", ChatFormatting.GRAY);
        }
        @Override public Boolean press(ServerPlayer p, StoneTree.Node n, boolean alt) { // legacy press: step the level
            int c = Levels.set(p, PUNCH_MODE, 0, maxPunch(p), Levels.get(p, PUNCH_MODE, 0, maxPunch(p), 0) + (alt ? -1 : 1));
            applyPunch(p, c, true);
            return true;
        }
    }

    static final String PUNCH_ON = "infinity_punch_on";
    public static final int PUNCH_INFINITE = 12;

    public static boolean infinitePunch(ServerPlayer p) {
        return p.getPersistentData().getBoolean(PUNCH_ON) && EnergyBeam.damageCycle(p) == PUNCH_INFINITE && Stone.POWER.isHeldBy(p);
    }

    static void applyPunch(ServerPlayer p, int level, boolean feedback) {
        EnergyBeam.setDamageCycle(p, level);
        if (!feedback) return;
        PortFx.sound(p.serverLevel(), p.position(), SoundEvents.COMPARATOR_CLICK, 0.5F, 0.8F + level * 0.08F);
        p.serverLevel().sendParticles(PortFx.dust(Stone.POWER, 1.2F), p.getX(), p.getY(0.6), p.getZ(), 12 + level * 4, 0.4, 0.5, 0.4, 0);
        FxNet.circleOn(p, 0.05, 1.5F + level * 0.25F, Stone.POWER, 24, level >= 8 ? FxNet.TIERS_2 | FxNet.RAYS : FxNet.TIERS_1);
        if (level == PUNCH_INFINITE) msg(p, "damage_cycle_infinite", ChatFormatting.GOLD);
        else msg(p, "damage_cycle", ChatFormatting.LIGHT_PURPLE, (int) Math.pow(2, level));
    }

    /** Infinite punch: a melee hit from the player kills anything mortal (the attribute itself caps at 2048). */
    @SubscribeEvent
    public static void infinitePunchHit(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent e) {
        if (e.getSource().getDirectEntity() instanceof ServerPlayer p && e.getSource().getEntity() == p
                && e.getSource().is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK) && infinitePunch(p)) {
            e.getEntity().invulnerableTime = 0;
            e.setAmount(Float.MAX_VALUE);
        }
    }

    // ---------------- Time: fast forward (original "time add 100" every tick while held) ----------------

    /** Wheel setting −10..+10 (default 5 = original): each tick the day moves by setting × 20 ticks; negative = rewind. */
    static final class FastForward implements Ported {
        @Override public void start(ServerPlayer p, StoneTree.Node n) {
            PortFx.sound(p.serverLevel(), p.position(), ModSounds.TIME_STONE, 0.5F, 1.2F);
            FAST_FORWARDERS.add(p.getUUID());
        }
        @Override public void stop(ServerPlayer p, StoneTree.Node n) { FAST_FORWARDERS.remove(p.getUUID()); }
        @Override public void tick(ServerPlayer p, StoneTree.Node n, int ticks) {
            int speed = Levels.get(p, n.modeId(), -10, 10, 5);
            if (speed == 0) return;
            for (var level : p.server.getAllLevels()) level.setDayTime(Math.max(0, level.getDayTime() + speed * 20L));
            p.server.forceTimeSynchronization();
            if (ticks % 20 == 0) FxNet.circle(p.serverLevel(), p.position().add(0, 6, 0), new net.minecraft.world.phys.Vec3(0, -1, 0),
                    4F + Math.abs(speed) * 0.4F, Stone.TIME, 24, FxNet.TIERS_2 | FxNet.HEPTA | (Math.abs(speed) >= 6 ? FxNet.FAST : 0));
        }
    }

    // ---------------- Power: Cosmic Explosion (explosion2 + hidden explosion3 immunity) ----------------

    private static final Set<UUID> BLAST_IMMUNE = new HashSet<>();
    private static final int[][] RING = {{8, 0}, {-8, 0}, {0, 8}, {0, -8}, {8, 8}, {-8, -8}, {8, -8}, {-8, 8}};

    /**
     * Original: while held, "summon tnt ~±8 ~ ~±8 {Fuse:0}" x8 every tick, power_stone_explode on the first tick, and the
     * hidden explosion3 made the caster immune to explosions. Same ring of TNT-strength blasts, but the terrain is carved
     * without drops (claims, spawn protection and the world border respected, mobGriefing ignored), entities take a share
     * of their max HP, and a violet magic circle marks the blast ring.
     */
    static final class CosmicExplosion implements Ported {
        @Override public void start(ServerPlayer p, StoneTree.Node n) {
            BLAST_IMMUNE.add(p.getUUID());
            Radiation.cause(p, "cosmic", true);
            PortFx.sound(p.serverLevel(), p.position(), ModSounds.POWER_STONE_EXPLODE, 0.6F, 1F);
            FxNet.circleOn(p, 0.1, 9F, Stone.POWER, 60, FxNet.TIERS_3 | FxNet.RAYS | FxNet.FAST);
        }
        @Override public void tick(ServerPlayer p, StoneTree.Node n, int ticks) {
            var level = p.serverLevel();
            // two ring points per tick, so each of the eight is carved every four ticks (no drops, claims respected)
            for (int i = 0; i < 2; i++) {
                int[] o = RING[(ticks % 4 * 2 + i) % 8];
                net.minecraft.world.phys.Vec3 c = new net.minecraft.world.phys.Vec3(p.getX() + o[0], p.getY(), p.getZ() + o[1]);
                com.infinitystones.tensura.skill.ability.extra.Terrain.carve(level, p, c, 4F, 150F);
                if (ticks % 4 == 0) com.infinitystones.tensura.skill.ability.extra.Terrain.bang(level, c, 4F);
            }
            if (ticks % 5 == 0) {
                // strong public power: 4% of max HP four times a second to everything in the blast ring
                for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, p.getBoundingBox().inflate(13),
                        e -> com.infinitystones.tensura.skill.ability.extra.Rules.isLiving(e) && com.infinitystones.tensura.skill.ability.extra.Rules.canHarm(p, e)))
                    com.infinitystones.tensura.skill.ability.extra.Rules.hurtPct(p, e, 8F, 0.04F);
            }
            if (ticks % 20 == 0) FxNet.circleOn(p, 0.1, 9F, Stone.POWER, 30, FxNet.TIERS_2 | FxNet.RAYS | FxNet.FAST);
        }
        @Override public void stop(ServerPlayer p, StoneTree.Node n) {
            BLAST_IMMUNE.remove(p.getUUID());
            Radiation.cause(p, "cosmic", false);
        }
    }

    @SubscribeEvent
    public static void blastImmunity(net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent e) {
        if (!BLAST_IMMUNE.isEmpty() && BLAST_IMMUNE.contains(e.getEntity().getUUID())
                && e.getSource().is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) e.setCanceled(true);
    }

    /** Highest DamageCycle bought (original unlock1..11_dummy nodes gate 2..2048 damage). */
    static int maxPunch(ServerPlayer p) {
        int max = 0; // 12 = infinite, available once 2048 (unlock11) is bought
        for (int i = 1; i <= 11; i++) {
            if (com.infinitystones.tensura.skill.StoneProgress.isUnlocked(p, Stone.POWER, "unlock" + i + "_dummy")) max = i; else break;
        }
        int top = max == 11 ? PUNCH_INFINITE : max;
        return com.infinitystones.tensura.skill.StoneTier.has(p, Stone.POWER) ? top : Math.min(top, 6);
    }

    /** Applies values changed elsewhere (the wheel's scroll-over-the-power control writes the same settings). */
    private static void watchSettings(ServerPlayer p) {
        net.minecraft.nbt.CompoundTag all = p.getPersistentData().getCompound(Levels.TAG);
        if (all.isEmpty()) return;
        if (all.contains(TIME_SPEED_MODE) && Stone.TIME.isHeldBy(p)) {
            int want = Levels.get(p, TIME_SPEED_MODE, -5, 5, 0);
            if (want != p.getPersistentData().getInt(TICK_CYCLE)) timeSpeedChanged(p, want);
        }
        if (all.contains(SIZE_MODE) && Stone.REALITY.isHeldBy(p)) {
            int want = Levels.get(p, SIZE_MODE, -4, 9, 0);
            if (want != p.getPersistentData().getInt(SIZE_CYCLE)) {
                boolean grow = want < p.getPersistentData().getInt(SIZE_CYCLE);
                applySize(p, want);
                PolishFx.sizeChange(p, want, grow);
                PortFx.sound(p.serverLevel(), p.position(), ModSounds.REALITY_STONE_SIZE, 0.5F, want < 0 ? 0.8F : 1.3F);
                FxNet.circleOn(p, 0.05, (float) (1.2 * scale(want)) + 0.6F, Stone.REALITY, 24, FxNet.TIERS_1);
                msg(p, "size", ChatFormatting.RED, String.format(java.util.Locale.ROOT, "%.1f", scale(want)));
            }
        }
        if (all.contains(PUNCH_MODE) && Stone.POWER.isHeldBy(p) && p.getPersistentData().getBoolean(PUNCH_ON)) {
            int want = Levels.get(p, PUNCH_MODE, 0, maxPunch(p), 0);
            if (want != EnergyBeam.damageCycle(p)) applyPunch(p, want, true);
        }
    }

    // ---------------- cleanup: stone dropped, logout, server stop ----------------

    @SubscribeEvent
    public static void tick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        if (p.tickCount % 4 == 1) watchSettings(p);
        if (p.tickCount % 20 != 7) return;
        if (p.getPersistentData().getInt(SIZE_CYCLE) != 0 && !Stone.REALITY.isHeldBy(p)) applySize(p, 0);
        if (p.getUUID().equals(timeSpeeder) && !Stone.TIME.isHeldBy(p)) resetTimeSpeed(p);
        if (p.getUUID().equals(freezer) && !Stone.TIME.isHeldBy(p)) { freezer = null; p.server.tickRateManager().setFrozen(false); }
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        // size and time speed never survive a session (see VolatileModes): back to normal on every login
        if (e.getEntity() instanceof ServerPlayer p) resetLevels(p, true);
    }

    /**
     * Size and time speed are wheel levels rather than switches: put both back to normal (level 0) and keep them there.
     * Logout / server stop undo the world tick rate too when this player set it; login only clears what a crash left.
     */
    public static void resetLevels(ServerPlayer p, boolean login) {
        var data = p.getPersistentData();
        if (data.getInt(SIZE_CYCLE) != 0) applySize(p, 0);
        if (data.getInt(TICK_CYCLE) != 0 || p.getUUID().equals(timeSpeeder)) {
            if (login && !p.getUUID().equals(timeSpeeder)) {
                data.putInt(TICK_CYCLE, 0);
                p.removeEffect(MobEffects.MOVEMENT_SPEED);
                p.removeEffect(MobEffects.DIG_SPEED);
            } else {
                applyTimeSpeed(p, 0);
            }
        }
        net.minecraft.nbt.CompoundTag all = data.getCompound(Levels.TAG);
        boolean changed = false;
        for (String mode : new String[]{SIZE_MODE, TIME_SPEED_MODE}) {
            if (all.contains(mode) && all.getInt(mode) != 0) { all.putInt(mode, 0); changed = true; }
        }
        if (changed) data.put(Levels.TAG, all);
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        FLYERS.remove(p.getUUID());
        BLAST_IMMUNE.remove(p.getUUID());
        FAST_FORWARDERS.remove(p.getUUID());
        if (p.getUUID().equals(timeSpeeder)) resetTimeSpeed(p);
        if (p.getUUID().equals(freezer)) { freezer = null; p.server.tickRateManager().setFrozen(false); }
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) {
        MinecraftServer s = e.getServer();
        if (timeSpeeder != null) s.tickRateManager().setTickRate(20);
        if (freezer != null) s.tickRateManager().setFrozen(false);
        timeSpeeder = null; freezer = null; FLYERS.clear();
    }
}
