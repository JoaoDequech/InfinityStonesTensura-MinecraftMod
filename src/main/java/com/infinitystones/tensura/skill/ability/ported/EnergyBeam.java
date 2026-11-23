package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.registry.ModSounds;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * palladium:aim + palladium:energy_beam, rebuilt from the original power JSONs and Palladium's EnergyBeamAbility.
 *
 * <p>In the original, holding an "aim" node raised the arm (1 tick) and enabled hidden energy_beam abilities. Each
 * tick the beam ray-traces from the eyes (blocks + entities, 100 blocks for the Power Stone), hurts what it hits,
 * sets it on fire, smelts the hit block (sand → glass, cobblestone → stone…), lights fire on top and runs
 * "particle minecraft:lava" at the hit. The Power Stone damage came from the DamageCycle score (2^0..2^11) and at
 * the top step, carrying all six stones, the beam turned rainbow, did 2147483647 damage and "summon tnt" at the hit.</p>
 *
 * <p>Now: four intensity levels (Alt + scroll, see {@link BeamLevel}): 0 weak (about a sword hit), 1 strong (the
 * original numbers of each beam), 2 lethal (the original maximum, 2048, ignores hit cooldown), 3 explosive / infinite
 * (kills anything mortal, pierces every block, explosions along the path; rainbow with all six stones like the
 * original). World effects of the original stay on every level. Lethal levels hit players even with PvP off (project
 * decision). Visuals are drawn by every client from {@link BeamNet} (client/port/BeamClient).</p>
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class EnergyBeam implements Ported {
    /**
     * One original beam. damage[level]; origin/colours/rotation from assets/infinity/palladium/energy_beams/*.json.
     * smelt/fire/fireSeconds from the original ability. lavaOnHit = original commands_on_block_hit "particle minecraft:lava".
     */
    private record Profile(float[] damage, float distance, int fireSeconds, boolean causeFire, boolean smelt, boolean lavaOnHit,
                           int origin, int glow, int core, boolean lightning, float rotationSpeed, float width,
                           @Nullable String loopSound, float loopVolume, float loopPitch) {}

    private static final float INF = Float.MAX_VALUE;
    // scepter_beam.json: right arm, #ADD8E6; mind_stone.json "power_blast": 20 dmg, 50 blocks, fire 1 s, cause_fire
    private static final Profile SCEPTER = new Profile(new float[]{6, 20, 2048, INF}, 50, 1, true, false, false,
            BeamNet.ORIGIN_MAIN_ARM, 0xADD8E6, 0xEAF6FF, false, 20, 0.11F, null, 0, 0);
    // mind_beam.json: head, #ffff12, size 0.7; "power_blast_head": 20 dmg, 50 blocks, fire 1 s
    private static final Profile MIND_HEAD = new Profile(new float[]{6, 20, 2048, INF}, 50, 1, true, false, false,
            BeamNet.ORIGIN_HEAD, 0xFFFF12, 0xFFFFD8, false, 26, 0.08F, null, 0, 0);
    // "psionic_overload": mind_beam, 180 dmg, 100 blocks, no fire; looping guardian.attack 0.8 / 1.55
    private static final Profile PSIONIC = new Profile(new float[]{10, 180, 2048, INF}, 100, 0, false, false, false,
            BeamNet.ORIGIN_HEAD, 0xFFD21A, 0xFFFBE0, false, 26, 0.12F, "minecraft:entity.guardian.attack", 0.8F, 1.55F);
    // "celestial_annihilation": power_stone_beam (right arm #BB00FF), 320 dmg, 120 blocks, fire 12 s, smelt; looping wither.spawn 0.35 / 1.65
    private static final Profile CELESTIAL = new Profile(new float[]{12, 320, 2048, INF}, 120, 12, true, true, true,
            BeamNet.ORIGIN_MAIN_ARM, 0xBB00FF, 0xFFE6FF, true, 20, 0.16F, "minecraft:entity.wither.spawn", 0.35F, 1.65F);
    // "power_blast0..11": gauntlet_beam (palladium:lightning, off arm, glow #BB00FF core #FFFFFF, size 2), 100 blocks, fire 1 s, smelt
    private static final Profile POWER = new Profile(new float[]{7, 64, 2048, INF}, 100, 1, true, true, true,
            BeamNet.ORIGIN_OFF_ARM, 0xBB00FF, 0xFFFFFF, true, 20, 0.13F, null, 0, 0);

    public static final String DAMAGE_CYCLE = "DamageCycle";

    private static final class Active {
        final StoneTree.Node node; final Profile profile; int ticks; long lastTick; int level = -1; boolean rainbow;
        @Nullable BlockPos heatPos; int heat;
        Active(StoneTree.Node node, Profile profile) { this.node = node; this.profile = profile; }
    }
    private static final Map<UUID, Active> ACTIVE = new HashMap<>();

    private static @Nullable Profile profileFor(StoneTree.Node n) {
        return switch (n.modeId()) {
            case "mind.aim" -> SCEPTER;
            case "mind.power_blast_head" -> MIND_HEAD;
            case "mind.psionic_overload_aim" -> PSIONIC;
            case "power.celestial_annihilation_aim" -> CELESTIAL;
            case "power.power_blast_aim" -> POWER;
            default -> n.originalType().equals("palladium:energy_beam") && n.activation() != StoneTree.Activation.PASSIVE
                    ? new Profile(new float[]{6, PortFx.f(n, "damage", 0), 2048, INF}, PortFx.f(n, "max_distance", 30),
                    PortFx.i(n, "set_on_fire_seconds", 0), PortFx.b(n, "cause_fire", false), PortFx.b(n, "smelt_blocks", false), false,
                    BeamNet.ORIGIN_MAIN_ARM, n.stone().color() & 0xFFFFFF, 0xFFFFFF, false, 20, 0.11F, null, 0, 0)
                    : null;
        };
    }

    // ---------------- lifecycle (hold: start / tick / stop; also usable as a toggle by the aim mode) ----------------

    @Override
    public void start(ServerPlayer p, StoneTree.Node n) {
        if (n.activation() == StoneTree.Activation.PASSIVE) return; // hidden beam halves are driven by their aim node
        Profile profile = profileFor(n);
        if (profile == null) return;
        if ("mind.aim".equals(n.modeId()) && !holdsScepter(p)) {
            p.displayClientMessage(Component.translatable("ability.infinity.port.needs_scepter").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        Active a = new Active(n, profile);
        a.lastTick = p.server.getTickCount();
        ACTIVE.put(p.getUUID(), a);
        ServerLevel level = p.serverLevel();
        // original first_tick "playsound infinity:power_stone_beam1" (aim_command) for the Power Stone
        if (profile == POWER || profile == CELESTIAL) PortFx.sound(level, p.getEyePosition(), ModSounds.POWER_STONE_BEAM1, 0.6F, 1F);
        else PortFx.sound(level, p.getEyePosition(), SoundEvents.BEACON_ACTIVATE, 0.45F, 1.7F);
        refresh(p, a);
    }

    @Override
    public void tick(ServerPlayer p, StoneTree.Node n, int ticks) {
        if (n.activation() == StoneTree.Activation.PASSIVE) return;
        Active a = ACTIVE.get(p.getUUID());
        if (a == null || !a.node.modeId().equals(n.modeId())) { start(p, n); a = ACTIVE.get(p.getUUID()); }
        if (a == null) return;
        if ("mind.aim".equals(n.modeId()) && !holdsScepter(p)) { stop(p, n); return; }
        a.lastTick = p.server.getTickCount();
        a.ticks++;
        int lvl = BeamLevel.get(p, n);
        boolean rainbow = a.profile == POWER && lvl == BeamLevel.MAX && StoneHelpers.carriesAll(p);
        if (lvl != a.level || rainbow != a.rainbow || a.ticks % 10 == 0) { a.level = lvl; a.rainbow = rainbow; refresh(p, a); }
        if (a.ticks < 2) return; // original aim "time": 1 tick to raise the arm before the beam fires
        fire(p, a);
    }

    @Override
    public void stop(ServerPlayer p, StoneTree.Node n) {
        Active a = ACTIVE.get(p.getUUID());
        if (a != null && a.node.modeId().equals(n.modeId())) end(p);
    }

    private static void end(ServerPlayer p) {
        if (ACTIVE.remove(p.getUUID()) != null) {
            BeamNet.stop(p);
            PortFx.sound(p.serverLevel(), p.getEyePosition(), SoundEvents.BEACON_DEACTIVATE, 0.35F, 1.8F);
        }
    }

    /** Alt + scroll from the client while firing (temporary; see {@link BeamLevel}). */
    public static void stepLevel(ServerPlayer p, int delta) {
        Active a = ACTIVE.get(p.getUUID());
        if (a == null || !a.node.stone().isHeldBy(p)) return;
        BeamLevel.step(p, a.node, delta);
    }

    /** True while this player's beam is firing (for other systems, e.g. the gauntlet pulse or the aim HUD). */
    public static boolean isFiring(ServerPlayer p) { return ACTIVE.containsKey(p.getUUID()); }

    private static void refresh(ServerPlayer p, Active a) {
        Profile pr = a.profile;
        int lvl = Math.max(0, a.level);
        float width = pr.width() * (1F + lvl * 0.45F) * (lvl == 3 ? 1.35F : 1F);
        int flags = (pr.lightning() || lvl >= 2 ? 1 : 0) | (a.rainbow ? 2 : 0) | (lvl == 3 ? 4 : 0);
        BeamNet.send(p, new BeamNet.Visual(pr.origin(), pr.glow(), pr.core(), lvl, width, pr.distance(), flags, pr.rotationSpeed()));
    }

    // ---------------- the beam ----------------

    private static void fire(ServerPlayer p, Active a) {
        Profile pr = a.profile;
        int lvl = Math.max(0, a.level);
        ServerLevel level = p.serverLevel();
        Vec3 start = p.getEyePosition();
        Vec3 dir = p.getViewVector(1F);
        Vec3 end = start.add(dir.scale(pr.distance()));

        if (lvl == BeamLevel.MAX) { pierce(p, a, start, end, dir); return; }

        BlockHitResult block = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        EntityHitResult ent = ProjectileUtil.getEntityHitResult(level, p, start, stop,
                new AABB(start, stop).inflate(1D), e -> e.isPickable() && !e.isSpectator() && e != p, 0.3F + 0.15F * lvl);

        if (pr.loopSound() != null && a.ticks % 20 == 2) PortFx.sound(level, start, pr.loopSound(), pr.loopVolume(), pr.loopPitch());

        if (ent != null) {
            Entity target = ent.getEntity();
            if (!targetable(p, target)) return;
            if (pr.fireSeconds() > 0) target.setRemainingFireTicks(Math.max(target.getRemainingFireTicks(), pr.fireSeconds() * 20));
            hurt(p, target, pr.damage()[lvl], lvl);
            if (lvl >= 1 && target instanceof LivingEntity le && le.isAlive())
                le.push(dir.x * 0.06 * lvl, 0.02 * lvl, dir.z * 0.06 * lvl);
            return;
        }
        if (block.getType() == HitResult.Type.MISS) { a.heatPos = null; return; }
        hitBlock(p, a, block, lvl);
    }

    /** Original block hit: lava particle, smelt (sand → glass…), fire on top. Heat builds a little so the change is visible. */
    private static void hitBlock(ServerPlayer p, Active a, BlockHitResult bh, int lvl) {
        ServerLevel level = p.serverLevel();
        Profile pr = a.profile;
        BlockPos pos = bh.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;
        Vec3 at = bh.getLocation();
        if (pr.lavaOnHit() && a.ticks % 2 == 0) level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        if (!PortFx.mayEdit(p, pos)) return;
        if (pos.equals(a.heatPos)) a.heat++; else { a.heatPos = pos; a.heat = 1; }
        int needed = switch (lvl) { case 0 -> 4; case 1 -> 2; default -> 1; };
        if (pr.smelt() && a.heat >= needed) {
            if (smelt(level, pos)) {
                a.heat = 0;
                level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.02);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 2, 0.15, 0.15, 0.15, 0.01);
                if (a.ticks % 4 == 0) PortFx.sound(level, at, SoundEvents.FIRE_EXTINGUISH, 0.25F, 1.6F);
            }
            if (lvl >= 2 && a.ticks % 3 == 0) { // lethal beam: the heat spreads to the exposed blocks around the hit
                for (Direction d : Direction.values()) {
                    BlockPos o = pos.relative(d);
                    if (o.equals(pos.relative(bh.getDirection())) || !PortFx.mayEdit(p, o)) continue;
                    smelt(level, o);
                }
            }
        }
        if (pr.causeFire()) {
            BlockState now = level.getBlockState(pos);
            BlockPos firePos = pos.relative(bh.getDirection());
            if (!now.isAir() && level.isEmptyBlock(firePos) && BaseFireBlock.canBePlacedAt(level, firePos, bh.getDirection()))
                level.setBlockAndUpdate(firePos, BaseFireBlock.getState(level, firePos));
        }
    }

    /** Replaces the block with its furnace result when that result is a block (like Palladium's smelt_blocks). */
    private static boolean smelt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(level, pos) < 0) return false;
        SingleRecipeInput in = new SingleRecipeInput(new ItemStack(state.getBlock()));
        var recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, in, level);
        if (recipe.isEmpty()) return false;
        ItemStack out = recipe.get().value().assemble(in, level.registryAccess());
        Block b = Block.byItem(out.getItem());
        if (out.isEmpty() || b == Blocks.AIR || b == state.getBlock()) return false;
        level.setBlockAndUpdate(pos, b.defaultBlockState());
        return true;
    }

    /**
     * Level 3: the beam goes through every block (like the rainbow beam that blew up whatever it touched): kills every
     * mortal thing along the full length and blows up the terrain where it enters solid ground.
     */
    private static void pierce(ServerPlayer p, Active a, Vec3 start, Vec3 end, Vec3 dir) {
        ServerLevel level = p.serverLevel();
        Profile pr = a.profile;
        double radius = 0.6 + pr.width() * 2;
        AABB box = new AABB(start, end).inflate(radius + 1);
        for (Entity e : level.getEntities(p, box, e -> e.isAlive() && !e.isSpectator())) {
            if (!targetable(p, e)) continue;
            AABB bb = e.getBoundingBox().inflate(radius);
            if (bb.clip(start, end).isEmpty() && !bb.contains(start)) continue;
            if (pr.fireSeconds() > 0) e.setRemainingFireTicks(Math.max(e.getRemainingFireTicks(), pr.fireSeconds() * 20));
            hurt(p, e, INF, BeamLevel.MAX);
        }
        if (pr.loopSound() != null && a.ticks % 20 == 2) PortFx.sound(level, start, pr.loopSound(), pr.loopVolume(), pr.loopPitch());
        if (a.ticks % 5 != 0) return;
        // explosions where the beam enters solid ground (first surface + up to 3 more, at least 8 blocks apart)
        List<Vec3> blasts = new ArrayList<>();
        boolean inside = false;
        double len = start.distanceTo(end);
        for (double d = 1.5; d < len && blasts.size() < 4; d += 0.5) {
            Vec3 pt = start.add(dir.scale(d));
            BlockPos bp = BlockPos.containing(pt);
            boolean solid = !level.getBlockState(bp).getCollisionShape(level, bp).isEmpty();
            if (solid && !inside && (blasts.isEmpty() || blasts.get(blasts.size() - 1).distanceTo(pt) >= 8)) blasts.add(pt);
            inside = solid;
        }
        ExplosionDamageCalculator spareCaster = new ExplosionDamageCalculator() {
            @Override public boolean shouldDamageEntity(Explosion explosion, Entity entity) { return entity != p && super.shouldDamageEntity(explosion, entity); }
        };
        for (Vec3 b : blasts) {
            boolean grief = PortFx.mayEdit(p, BlockPos.containing(b));
            level.explode(p, null, spareCaster, b.x, b.y, b.z, 4F, pr.causeFire(),
                    grief ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
            if (pr.smelt() && grief) smelt(level, BlockPos.containing(b).below());
            if (a.ticks % 10 == 0) FxNet.circle(level, b.subtract(dir.scale(0.6)), dir.scale(-1), 6F, a.rainbow ? 0xFFFFFF : pr.glow(),
                    pr.core(), 24, FxNet.TIERS_1 | FxNet.RAYS | FxNet.FAST);
        }
    }

    // ---------------- damage ----------------

    /** Who the beam may hit: not the caster, its pets or allies; players ALWAYS (project decision), except creative/spectator. */
    static boolean targetable(ServerPlayer caster, Entity target) {
        if (target == caster || !target.isAlive() || target.isSpectator() || target instanceof ArmorStand) return false;
        if (target instanceof Player pl) return !pl.isCreative();
        if (target instanceof OwnableEntity own && caster.getUUID().equals(own.getOwnerUUID())) return false;
        if (target instanceof TamableAnimal t && t.isOwnedBy(caster)) return false;
        return !caster.isAlliedTo(target);
    }

    private static void hurt(ServerPlayer p, Entity target, float damage, int lvl) {
        if (damage <= 0) return;
        if (lvl >= 2) target.invulnerableTime = 0; // lethal: no hit cooldown
        boolean blockedPvp = target instanceof Player other && (!p.server.isPvpAllowed() || !p.canHarmPlayer(other));
        DamageSource src;
        if (lvl == BeamLevel.MAX) {
            var kill = p.serverLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.GENERIC_KILL);
            src = blockedPvp ? new DamageSource(kill) : new DamageSource(kill, p);
        } else if (blockedPvp) {
            src = new DamageSource(p.serverLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(DamageTypes.MAGIC));
        } else {
            src = PortFx.attack(p);
        }
        target.hurt(src, damage);
    }

    // ---------------- safety: a beam never outlives its owner's input ----------------

    @SubscribeEvent
    public static void serverTick(ServerTickEvent.Post e) {
        if (ACTIVE.isEmpty()) return;
        int now = e.getServer().getTickCount();
        for (UUID id : List.copyOf(ACTIVE.keySet())) {
            Active a = ACTIVE.get(id);
            ServerPlayer p = e.getServer().getPlayerList().getPlayer(id);
            if (p == null) { ACTIVE.remove(id); continue; }
            if (now - a.lastTick > 3 || !p.isAlive() || !a.node.stone().isHeldBy(p)) end(p);
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) end(p);
    }

    @SubscribeEvent
    public static void changeDim(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) end(p);
    }

    private static boolean holdsScepter(ServerPlayer p) {
        var scepter = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("infinity", "scepter"));
        return p.getMainHandItem().is(scepter) || p.getOffhandItem().is(scepter);
    }

    // ---------------- Power Stone damage cycle (original scroll abilities; melee attack damage 2^cycle) ----------------

    public static int damageCycle(ServerPlayer p) {
        Scoreboard sb = p.getScoreboard();
        Objective o = sb.getObjective(DAMAGE_CYCLE);
        if (o == null) return 0;
        var score = sb.getPlayerScoreInfo(p, o);
        return score == null ? 0 : score.value();
    }

    /** Sets the cycle (0..11) and, like the original, the base attack damage 2^cycle. */
    public static int setDamageCycle(ServerPlayer p, int cycle) {
        cycle = Math.clamp(cycle, 0, 12); // 12 = infinite punch (CommandPorts), attribute caps at 2048
        Scoreboard sb = p.getScoreboard();
        Objective o = sb.getObjective(DAMAGE_CYCLE);
        if (o == null) o = sb.addObjective(DAMAGE_CYCLE, ObjectiveCriteria.DUMMY, Component.literal(DAMAGE_CYCLE),
                ObjectiveCriteria.RenderType.INTEGER, false, null);
        sb.getOrCreatePlayerScore(ScoreHolder.forNameOnly(p.getScoreboardName()), o).set(cycle);
        var attr = p.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attr != null) attr.setBaseValue(Math.pow(2, Math.min(cycle, 11)));
        return cycle;
    }

    /** Small shared helper so other ports can ask "does the caster carry all six?" */
    public static final class StoneHelpers {
        public static boolean carriesAll(ServerPlayer p) {
            for (Stone s : Stone.values()) if (!s.isHeldBy(p)) return false;
            return true;
        }
        private StoneHelpers() {}
    }
}
