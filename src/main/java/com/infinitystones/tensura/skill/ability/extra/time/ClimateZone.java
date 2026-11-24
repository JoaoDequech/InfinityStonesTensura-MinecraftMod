package com.infinitystones.tensura.skill.ability.extra.time;

import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.ability.extra.Ability;
import com.infinitystones.tensura.skill.ability.extra.Aim;
import com.infinitystones.tensura.skill.ability.extra.ExtraSettings;
import com.infinitystones.tensura.skill.ability.extra.Fx;
import com.infinitystones.tensura.skill.ability.extra.MenuNet;
import com.infinitystones.tensura.skill.ability.extra.Rules;
import com.infinitystones.tensura.skill.ability.extra.Tasks;
import com.infinitystones.tensura.skill.ability.extra.fx.FxNet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Climate Zone: left = mark the area at the aimed point (radius from Alt+scroll, 6–24); right/middle = menu of
 * climates applied ONLY inside the area (everyone there sees and feels it). 3 minutes, one zone per player.
 * Storm (cloud layer + heavy rain + real lightning), Rain, Snow (thin snow + freezing edge water with mobGriefing),
 * Fog, Scorching sun (fire on players without a helmet after 5 s, crops grow), Aurora (Regeneration I),
 * Sandstorm, Clear (ends). Harmful effects (slowness, heat, lightning) spare the caster and allies.
 * Also: Blizzard (whiteout, freezing, wind gusts), Ashfall (ember rain, small safe fires), Cherry blossom (regeneration),
 * Supercell (rotating wall cloud, frequent lightning in the zone only), Meteor shower (night sky, shooting stars),
 * Toxic fog (poison to hostiles), Starfall night (night sky + aurora, light-footed), Heatwave (crops grow, hunger),
 * Rainbow (drizzle, then a rainbow; luck). Radius 6-24, more with the secret tier.
 */
public final class ClimateZone implements Ability {
    public static final ClimateZone INSTANCE = new ClimateZone();
    static final String NODE = "climate_zone", MODE = "time.climate_zone";
    private static final int DURATION = 20 * 60 * 3;
    private static final int MAX_R = 100;
    private static final String[] KEYS = {"storm", "rain", "snow", "fog", "sun", "aurora", "sand",
            "blizzard", "ash", "cherry", "supercell", "meteor", "toxic", "starfall", "heat", "rainbow", "clear"};
    private static final String[] ICONS = {"minecraft:lightning_rod", "minecraft:water_bucket", "minecraft:snowball", "minecraft:cobweb",
            "minecraft:sunflower", "minecraft:prismarine_crystals", "minecraft:sand",
            "minecraft:powder_snow_bucket", "minecraft:blaze_powder", "minecraft:pink_petals", "minecraft:trident", "minecraft:fire_charge",
            "minecraft:fermented_spider_eye", "minecraft:nether_star", "minecraft:magma_cream", "minecraft:prismarine_shard", "minecraft:glass"};
    private static final int NIGHT_VISION_TICKS = 230;

    private static final class Zone {
        final UUID owner; final ServerLevel level; final int id = TimeNet.newId();
        Vec3 c; float r; int climate = TimeNet.C_NONE; int age;
        final Map<UUID, Integer> heat = new HashMap<>();
        final List<LivingEntity> wind = new ArrayList<>();
        final Set<UUID> lit = new HashSet<>();
        Tasks.Handle handle;
        Zone(UUID owner, ServerLevel level, Vec3 c, float r) { this.owner = owner; this.level = level; this.c = c; this.r = r; }
    }

    private static final Map<UUID, Zone> ZONES = new HashMap<>();

    @Override public Stone stone() { return Stone.TIME; }

    @Override
    public boolean press(ServerPlayer p, boolean alt) {
        if (alt) return MenuNet.open(p, Stone.TIME, NODE, this) || Fx.fail(p, "infinity.extra.time.climate.no_menu");
        Vec3 at = Aim.ground(p, 64);
        float r = ExtraSettings.get(p, MODE, 12, 6, MAX_R, 1);
        Zone z = ZONES.get(p.getUUID());
        int keep = z != null ? z.climate : TimeNet.C_NONE;
        place(p, at, r, keep);
        Fx.bar(p, Stone.TIME, keep == TimeNet.C_NONE ? "infinity.extra.time.climate.marked" : "infinity.extra.time.climate.moved", (int) r);
        return true;
    }

    @Override
    public List<MenuNet.Option> menu(ServerPlayer p) {
        Zone z = ZONES.get(p.getUUID());
        int cur = z == null ? -1 : z.climate - 1;
        MenuNet.Option[] o = new MenuNet.Option[KEYS.length];
        for (int i = 0; i < KEYS.length; i++) o[i] = MenuNet.opt("infinity.extra.time.climate." + KEYS[i], ICONS[i], i == cur);
        return List.of(o);
    }

    @Override
    public void pick(ServerPlayer p, int index) {
        if (index < 0 || index >= KEYS.length) return;
        Zone z = ZONES.get(p.getUUID());
        if (index == KEYS.length - 1) {
            if (z == null) { Fx.fail(p, "infinity.extra.time.climate.none"); return; }
            end(z, true);
            Fx.bar(p, Stone.TIME, "infinity.extra.time.climate.cleared");
            return;
        }
        int climate = index + 1;
        ExtraSettings.setChoice(p, MODE, climate);
        if (z == null || z.level != p.serverLevel()) {
            float r = ExtraSettings.get(p, MODE, 12, 6, MAX_R, 1);
            place(p, com.infinitystones.tensura.skill.ability.extra.Ground.below(p.serverLevel(), p.position().add(0, 0.5, 0), 6), r, climate);
            z = ZONES.get(p.getUUID());
        } else {
            cleanup(z);
            z.climate = climate;
            z.heat.clear();
            TimeNet.zone(z.level, z.id, z.c, z.r, climate, TimeNet.ZONE_LIFE, z.age);
        }
        z.age = Math.min(z.age, 40); // a new climate refreshes the 3 minutes
        announce(z);
        Fx.bar(p, Stone.TIME, "infinity.extra.time.climate.set", net.minecraft.network.chat.Component.translatable("infinity.extra.time.climate." + KEYS[index]));
    }

    private static void announce(Zone z) {
        ServerLevel l = z.level;
        Vec3 top = z.c.add(0, 6, 0);
        switch (z.climate) {
            case TimeNet.C_STORM -> { Fx.sound(l, top, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.5F, 0.7F); FxNet.flashAround(l, z.c, z.r + 8, 0x0A0C12, 0.35F, 14); }
            case TimeNet.C_RAIN -> Fx.sound(l, top, SoundEvents.WEATHER_RAIN_ABOVE, 0.7F, 0.9F);
            case TimeNet.C_SNOW -> Fx.sound(l, top, SoundEvents.POWDER_SNOW_PLACE, 0.7F, 0.6F);
            case TimeNet.C_FOG -> Fx.sound(l, top, SoundEvents.SOUL_ESCAPE.value(), 0.6F, 0.5F);
            case TimeNet.C_SUN -> { Fx.sound(l, top, SoundEvents.BLAZE_SHOOT, 0.4F, 0.5F); FxNet.flashAround(l, z.c, z.r + 8, 0xFFC060, 0.3F, 12); }
            case TimeNet.C_AURORA -> Fx.sound(l, top, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, 0.6F);
            case TimeNet.C_SAND -> Fx.sound(l, top, SoundEvents.SAND_BREAK, 0.7F, 0.5F);
            case TimeNet.C_BLIZZARD -> { Fx.sound(l, top, SoundEvents.POWDER_SNOW_BREAK, 0.8F, 0.5F); Fx.sound(l, top, SoundEvents.ELYTRA_FLYING, 0.3F, 0.5F); FxNet.flashAround(l, z.c, z.r + 8, 0xE8F4FF, 0.3F, 14); }
            case TimeNet.C_ASH -> { Fx.sound(l, top, SoundEvents.LAVA_POP, 0.7F, 0.5F); Fx.sound(l, top, SoundEvents.GENERIC_EXPLODE, 0.25F, 0.5F); FxNet.flashAround(l, z.c, z.r + 8, 0xFF6A1A, 0.3F, 14); }
            case TimeNet.C_CHERRY -> { Fx.sound(l, top, SoundEvents.CHERRY_LEAVES_PLACE, 0.8F, 0.8F); Fx.sound(l, top, SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, 1.6F); }
            case TimeNet.C_SUPERCELL -> { Fx.sound(l, top, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6F, 0.5F); FxNet.flashAround(l, z.c, z.r + 8, 0x0A0C12, 0.4F, 16); }
            case TimeNet.C_METEOR -> { Fx.sound(l, top, SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.5F, 0.6F); Fx.sound(l, top, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.6F, 1.2F); }
            case TimeNet.C_TOXIC -> { Fx.sound(l, top, SoundEvents.SLIME_SQUISH, 0.7F, 0.5F); Fx.sound(l, top, SoundEvents.BREWING_STAND_BREW, 0.6F, 0.6F); }
            case TimeNet.C_STARFALL -> { Fx.sound(l, top, SoundEvents.BEACON_ACTIVATE, 0.4F, 1.6F); Fx.sound(l, top, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, 0.5F); }
            case TimeNet.C_HEAT -> { Fx.sound(l, top, SoundEvents.FIRE_AMBIENT, 0.6F, 0.5F); Fx.sound(l, top, SoundEvents.BLAZE_SHOOT, 0.4F, 0.5F); FxNet.flashAround(l, z.c, z.r + 8, 0xFFD070, 0.3F, 12); }
            case TimeNet.C_RAINBOW -> { Fx.sound(l, top, SoundEvents.WEATHER_RAIN_ABOVE, 0.4F, 1.1F); Fx.sound(l, top, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 1.4F); }
            default -> { }
        }
        Fx.sound(l, z.c, SoundEvents.BELL_RESONATE, 0.5F, 1.4F);
        TimeNet.markRing(l, z.c.add(0, 0.05, 0), z.r, 0x2BD56A, 26);
    }

    private static void place(ServerPlayer p, Vec3 at, float r, int climate) {
        Zone old = ZONES.remove(p.getUUID());
        if (old != null) end(old, false);
        ServerLevel l = p.serverLevel();
        Zone z = new Zone(p.getUUID(), l, at, r);
        z.climate = climate;
        ZONES.put(p.getUUID(), z);
        TimeNet.zone(l, z.id, at, r, climate, TimeNet.ZONE_LIFE, 0);
        TimeNet.markRing(l, at.add(0, 0.05, 0), r, 0x2BD56A, 24);
        FxNet.clock(l, 0, at, Math.min(4, r * 0.3F), Stone.TIME.color(), 30, 4F);
        Fx.sound(l, at, SoundEvents.BELL_BLOCK, 0.5F, 1.5F);
        Fx.sound(l, at, SoundEvents.AMETHYST_BLOCK_CHIME, 0.7F, 0.8F);
        Fx.ring(l, at.add(0, 0.2, 0), r, (int) (r * 6), Fx.dust(Stone.TIME, 1.4F));
        z.handle = Tasks.add(new Tasks.Task() {
            @Override public boolean tick(int t) { return ZONES.get(z.owner) == z && tickZone(z); }
            @Override public void end() { if (ZONES.get(z.owner) == z) ZONES.remove(z.owner); cleanup(z); TimeNet.kill(z.level, z.c, z.id); }
        });
    }

    private static void end(Zone z, boolean fx) {
        ZONES.remove(z.owner, z);
        if (z.handle != null) z.handle.cancel();
        cleanup(z);
        TimeNet.kill(z.level, z.c, z.id);
        if (fx) {
            TimeNet.markRing(z.level, z.c.add(0, 0.1, 0), z.r, 0x9CFFC4, 20);
            Fx.sound(z.level, z.c, SoundEvents.AMETHYST_BLOCK_BREAK, 0.6F, 1.2F);
            Fx.at(z.level, ParticleTypes.END_ROD, z.c.add(0, 2, 0), 30, z.r * 0.4, 0.02);
        }
    }

    private static boolean tickZone(Zone z) {
        ServerLevel l = z.level;
        ServerPlayer owner = l.getServer().getPlayerList().getPlayer(z.owner);
        if (owner == null || ++z.age > DURATION) return false;
        if (z.age % TimeNet.ZONE_REFRESH == 1) TimeNet.zone(l, z.id, z.c, z.r, z.climate, TimeNet.ZONE_LIFE, z.age);
        if (z.age == DURATION - 200) Fx.bar(owner, Stone.TIME, "infinity.extra.time.climate.ending");
        if (z.climate == TimeNet.C_NONE) return true;
        RandomSource rnd = l.random;
        boolean grief = true; // terrain changes no longer depend on mobGriefing; claims are checked per block
        float boost = Math.min(3F, Math.max(1F, z.r / 24F));
        switch (z.climate) {
            case TimeNet.C_STORM -> {
                if (z.age % 10 == 0 && rnd.nextFloat() < 0.22F * boost) lightning(z, owner, false);
            }
            case TimeNet.C_SNOW -> {
                if (grief && z.age % 4 == 0) for (int i = 0, n = more(z, 3); i < n; i++) snow(z, rnd);
                if (grief && z.age % 6 == 0) for (int i = 0, n = more(z, 3); i < n; i++) freezeEdge(z, rnd);
            }
            case TimeNet.C_SUN -> {
                if (z.age % 3 == 0) for (int i = 0, n = more(z, 4); i < n; i++) grow(z, rnd);
            }
            case TimeNet.C_BLIZZARD -> {
                if (z.age % 4 == 0) for (int i = 0, n = more(z, 4); i < n; i++) snow(z, rnd);
                if (z.age % 6 == 0) for (int i = 0, n = more(z, 3); i < n; i++) freezeEdge(z, rnd);
                wind(z);
            }
            case TimeNet.C_ASH -> {
                if (z.age % 10 == 0) for (int i = 0, n = more(z, 1); i < n; i++) if (rnd.nextFloat() < 0.5F) ember(z, rnd);
            }
            case TimeNet.C_CHERRY -> {
                if (z.age % 4 == 0) for (int i = 0, n = more(z, 2); i < n; i++) grow(z, rnd);
            }
            case TimeNet.C_SUPERCELL -> {
                if (z.age % 10 == 0) for (int i = 0, n = rnd.nextFloat() < 0.5F * boost ? (boost > 1.5F ? 2 : 1) : 0; i < n; i++) lightning(z, owner, true);
                wind(z);
            }
            case TimeNet.C_METEOR -> {
                if (z.age % 100 == 50 && rnd.nextFloat() < 0.6F) sparkle(z, rnd);
            }
            case TimeNet.C_HEAT -> {
                if (z.age % 3 == 0) for (int i = 0, n = more(z, 6); i < n; i++) grow(z, rnd);
            }
            case TimeNet.C_RAINBOW -> {
                if (z.age % 6 == 0) for (int i = 0, n = more(z, 1); i < n; i++) grow(z, rnd);
            }
            default -> { }
        }
        if (z.age % 10 == 0) affect(z, owner);
        return true;
    }

    /** Attempts per tick grow with the area beyond a radius of 24 (x1 up to x8). */
    private static int more(Zone z, int base) {
        double f = Math.max(1, (z.r / 24.0) * (z.r / 24.0));
        return (int) Math.min(base * 8L, Math.round(base * f));
    }

    private static void cleanup(Zone z) {
        for (UUID id : new ArrayList<>(z.lit)) unlight(z, id);
        z.lit.clear();
        z.wind.clear();
    }

    private static void unlight(Zone z, UUID id) {
        ServerPlayer sp = z.level.getServer().getPlayerList().getPlayer(id);
        if (sp == null) return;
        MobEffectInstance ni = sp.getEffect(MobEffects.NIGHT_VISION);
        if (ni != null && ni.isAmbient() && !ni.isVisible() && ni.getDuration() <= NIGHT_VISION_TICKS) sp.removeEffect(MobEffects.NIGHT_VISION);
    }

    /** Wind gusts (blizzard) and a constant swirl (supercell) push whoever may be harmed, every second tick. */
    private static void wind(Zone z) {
        if (z.age % 2 != 0 || z.wind.isEmpty()) return;
        boolean swirl = z.climate == TimeNet.C_SUPERCELL;
        float g = TimeNet.gust(z.id, z.age);
        if (!swirl && g < 0) return;
        double ang = TimeNet.gustAngle(z.id, z.age);
        for (Iterator<LivingEntity> it = z.wind.iterator(); it.hasNext(); ) {
            LivingEntity e = it.next();
            if (!e.isAlive() || e.level() != z.level || !inside(z, e.position())) { it.remove(); continue; }
            if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator() || pl.getAbilities().flying)) continue;
            double dx, dz, k;
            if (swirl) {
                double rx = e.getX() - z.c.x, rz = e.getZ() - z.c.z, len = Math.max(1, Math.sqrt(rx * rx + rz * rz));
                dx = -rz / len; dz = rx / len; k = 0.03;
            } else { dx = Math.cos(ang); dz = Math.sin(ang); k = 0.1 * g; }
            e.push(dx * k, 0, dz * k);
            e.hurtMarked = true;
        }
    }

    private static void sparkle(Zone z, RandomSource rnd) {
        BlockPos top = column(z, rnd, 0);
        if (top == null || Math.abs(top.getY() - z.c.y) > 30) return;
        Vec3 at = Vec3.atBottomCenterOf(top).add(0, 0.4, 0);
        Fx.at(z.level, ParticleTypes.END_ROD, at, 16, 0.5, 0.1);
        Fx.at(z.level, ParticleTypes.FIREWORK, at, 10, 0.4, 0.15);
        Fx.sound(z.level, at, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.2F + rnd.nextFloat() * 0.6F);
    }

    /** An ember lands: a small fire only where nothing flammable is near, outside foreign claims, never on infiniburn blocks. */
    private static void ember(Zone z, RandomSource rnd) {
        BlockPos top = column(z, rnd, 0);
        if (top == null || Math.abs(top.getY() - z.c.y) > 30) return;
        ServerLevel l = z.level;
        Vec3 v = Vec3.atBottomCenterOf(top);
        Fx.at(l, ParticleTypes.LAVA, v.add(0, 0.2, 0), 3, 0.2, 0);
        BlockPos below = top.below();
        BlockState bs = l.getBlockState(below);
        if (!l.getBlockState(top).isAir() || !bs.isFaceSturdy(l, below, Direction.UP) || bs.is(l.dimensionType().infiniburn())) return;
        for (int dx = -2; dx <= 2; dx++) for (int dy = -1; dy <= 5; dy++) for (int dz = -2; dz <= 2; dz++) {
            BlockPos q = top.offset(dx, dy, dz);
            if (!l.hasChunkAt(q)) return;
            BlockState s = l.getBlockState(q);
            if (!s.isAir() && s.getFlammability(l, q, Direction.UP) > 0) return;
        }
        if (!com.infinitystones.tensura.compat.ClaimsCompat.canModify(z.owner, l, top)) return;
        BlockState fire = Blocks.FIRE.defaultBlockState();
        if (fire.canSurvive(l, top)) l.setBlock(top, fire, 3);
    }

    private static boolean inside(Zone z, Vec3 v) {
        double dx = v.x - z.c.x, dz = v.z - z.c.z;
        return dx * dx + dz * dz <= z.r * z.r && v.y > z.c.y - 8 && v.y < z.c.y + 40;
    }

    private static void affect(Zone z, ServerPlayer owner) {
        AABB box = new AABB(z.c.x - z.r, z.c.y - 8, z.c.z - z.r, z.c.x + z.r, z.c.y + 40, z.c.z + z.r);
        java.util.Set<UUID> seen = new java.util.HashSet<>();
        Set<UUID> seenLit = new HashSet<>();
        boolean windy = z.climate == TimeNet.C_BLIZZARD || z.climate == TimeNet.C_SUPERCELL;
        if (windy) z.wind.clear();
        RandomSource rnd = z.level.random;
        for (LivingEntity e : z.level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive() && Rules.isLiving(e) && inside(z, e.position()))) {
            boolean harmable = Rules.canHarm(owner, e);
            switch (z.climate) {
                case TimeNet.C_STORM, TimeNet.C_RAIN -> { if (e.isOnFire()) e.clearFire(); }
                case TimeNet.C_SUPERCELL -> {
                    if (e.isOnFire()) e.clearFire();
                    if (harmable) z.wind.add(e);
                }
                case TimeNet.C_BLIZZARD -> {
                    if (e.isOnFire()) e.clearFire();
                    if (harmable) {
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1, true, false, true));
                        if (e.canFreeze()) e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 40, e.getTicksFrozen() + 30));
                        z.wind.add(e);
                    }
                }
                case TimeNet.C_ASH -> {
                    if (harmable && e.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && z.level.canSeeSky(BlockPos.containing(e.getX(), e.getEyeY(), e.getZ()))
                            && rnd.nextFloat() < 0.12F) e.igniteForSeconds(2);
                }
                case TimeNet.C_CHERRY -> {
                    if (!(e instanceof Enemy)) e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, true, true));
                }
                case TimeNet.C_METEOR, TimeNet.C_STARFALL -> {
                    if (e instanceof ServerPlayer sp) {
                        sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_TICKS, 0, true, false, false));
                        z.lit.add(sp.getUUID());
                        seenLit.add(sp.getUUID());
                    }
                    if (z.climate == TimeNet.C_METEOR) {
                        if (e instanceof Enemy && harmable) e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, true, false, false));
                    } else if (!(e instanceof Enemy)) {
                        e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0, true, false, true));
                        e.addEffect(new MobEffectInstance(MobEffects.JUMP, 60, 0, true, false, true));
                    }
                }
                case TimeNet.C_TOXIC -> {
                    if ((e instanceof Enemy || e instanceof Player) && harmable)
                        e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0, true, true, true));
                }
                case TimeNet.C_HEAT -> {
                    if (e instanceof Player && harmable) e.addEffect(new MobEffectInstance(MobEffects.HUNGER, 80, 0, true, false, true));
                }
                case TimeNet.C_RAINBOW -> {
                    if (z.age < 700 && e.isOnFire()) e.clearFire();
                    if (!(e instanceof Enemy)) e.addEffect(new MobEffectInstance(MobEffects.LUCK, 100, 0, true, false, true));
                }
                case TimeNet.C_SNOW -> {
                    if (e.isOnFire()) e.clearFire();
                    if (harmable) e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, true, false, true));
                }
                case TimeNet.C_SAND -> {
                    if (harmable) {
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 0, true, false, true));
                        e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 30, 0, true, false, true));
                    }
                }
                case TimeNet.C_SUN -> {
                    if (e instanceof Player pl && harmable) {
                        seen.add(pl.getUUID());
                        if (pl.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
                            int h = z.heat.merge(pl.getUUID(), 10, Integer::sum);
                            if (h >= 100) { pl.igniteForSeconds(3); }
                            else if (h >= 60 && pl instanceof ServerPlayer sp) Fx.barGray(sp, "infinity.extra.time.climate.hot");
                        } else z.heat.remove(pl.getUUID());
                    }
                }
                case TimeNet.C_AURORA -> {
                    if (!(e instanceof Enemy)) e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, true, true, true));
                }
                default -> { }
            }
        }
        if (z.climate == TimeNet.C_SUN) z.heat.keySet().retainAll(seen);
        if (!z.lit.isEmpty()) {
            for (Iterator<UUID> it = z.lit.iterator(); it.hasNext(); ) {
                UUID id = it.next();
                if (!seenLit.contains(id)) { unlight(z, id); it.remove(); }
            }
        }
    }

    /** A random surface column of the zone, or null when that chunk is not loaded (a zone never loads or generates chunks). */
    private static BlockPos column(Zone z, RandomSource rnd, double minFrac) {
        double ang = rnd.nextDouble() * Math.PI * 2, rr = z.r * Math.sqrt(minFrac + (1 - minFrac) * rnd.nextDouble());
        int x = (int) Math.floor(z.c.x + Math.cos(ang) * rr), zz = (int) Math.floor(z.c.z + Math.sin(ang) * rr);
        if (!z.level.hasChunk(x >> 4, zz >> 4)) return null;
        return z.level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, new BlockPos(x, 0, zz));
    }

    private static void lightning(Zone z, ServerPlayer owner, boolean claims) {
        for (int tries = 0; tries < 4; tries++) {
            BlockPos top = column(z, z.level.random, 0);
            if (top == null || Math.abs(top.getY() - z.c.y) > 30) continue;
            if (claims && !com.infinitystones.tensura.compat.ClaimsCompat.canModify(z.owner, z.level, top)) continue;
            Vec3 at = Vec3.atBottomCenterOf(top);
            boolean nearFriend = false;
            for (Player pl : z.level.players()) if (Rules.isAlly(owner, pl) && pl.position().distanceToSqr(at) < 16) { nearFriend = true; break; }
            if (nearFriend) continue;
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(z.level);
            if (bolt == null) return;
            bolt.moveTo(at);
            bolt.setCause(owner);
            z.level.addFreshEntity(bolt);
            return;
        }
    }

    private static void snow(Zone z, RandomSource rnd) {
        BlockPos pos = column(z, rnd, 0);
        if (pos == null || Math.abs(pos.getY() - z.c.y) > 30) return;
        BlockState snow = Blocks.SNOW.defaultBlockState();
        if (z.level.getBlockState(pos).isAir() && snow.canSurvive(z.level, pos) && com.infinitystones.tensura.compat.ClaimsCompat.canModify(z.owner, z.level, pos)) z.level.setBlock(pos, snow, 3);
    }

    private static void freezeEdge(Zone z, RandomSource rnd) {
        BlockPos top = column(z, rnd, 0.78);
        if (top == null) return;
        BlockPos water = top.below();
        if (Math.abs(water.getY() - z.c.y) > 30) return;
        BlockState s = z.level.getBlockState(water);
        if (s.is(Blocks.WATER) && z.level.getFluidState(water).isSourceOfType(Fluids.WATER) && z.level.getBlockState(top).isAir()
                && com.infinitystones.tensura.compat.ClaimsCompat.canModify(z.owner, z.level, water))
            z.level.setBlock(water, Blocks.FROSTED_ICE.defaultBlockState(), 3);
    }

    private static void grow(Zone z, RandomSource rnd) {
        BlockPos pos = column(z, rnd, 0);
        if (pos == null) return;
        for (int dy = 0; dy >= -1; dy--) {
            BlockPos p = pos.above(dy);
            BlockState s = z.level.getBlockState(p);
            if ((s.getBlock() instanceof BonemealableBlock || s.getBlock() instanceof BushBlock) && s.isRandomlyTicking()) {
                s.randomTick(z.level, p, rnd);
                Fx.at(z.level, ParticleTypes.HAPPY_VILLAGER, Vec3.atCenterOf(p), 2, 0.3, 0);
                return;
            }
        }
    }
}
