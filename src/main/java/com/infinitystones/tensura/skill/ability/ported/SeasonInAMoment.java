package com.infinitystones.tensura.skill.ability.ported;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneTree;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Port of palladium:season_in_a_moment (Time, 240-tick activation). Four local seasons of 3 s each around the cast
 * point (spring, summer, autumn, winter) with their own particles and sounds. The only world change is bounded growth
 * of existing vanilla crops (wheat, carrots, potatoes, beetroots; up to 96 nearest within 8 blocks): +2 / +3 / +2 / +0
 * ages per season. Ends early if the caster moves more than 32 blocks from the cast point.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class SeasonInAMoment implements Ported {
    private static final Set<Block> CROPS = Set.of(Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS);
    private static final Map<UUID, Season> SEASONS = new HashMap<>();
    /** Season tints (spring, summer, autumn, winter); spring uses the Time Stone green. */
    private static final int[] TINTS = {-1, 0xFFD926, 0xFF590F, 0xA6E6FF};

    private record Crop(BlockPos position, Block block) {}
    private static final class Season {
        final ServerLevel level; final ServerPlayer owner; final Vec3 center; final List<Crop> crops; final long start; final int rgb;
        int phase;
        Season(ServerLevel level, ServerPlayer owner, Vec3 center, List<Crop> crops, long start, int rgb) {
            this.level = level; this.owner = owner; this.center = center; this.crops = crops; this.start = start; this.rgb = rgb;
        }
    }

    public SeasonInAMoment() {}

    @Override
    public void start(ServerPlayer p, StoneTree.Node n) {
        ServerLevel level = p.serverLevel();
        if (!p.isAlive()) return;
        Vec3 center = p.position();
        BlockPos origin = p.blockPosition();
        List<Crop> crops = new ArrayList<>();
        for (int dx = -8; dx <= 8; dx++) for (int dz = -8; dz <= 8; dz++) {
            if (dx * dx + dz * dz > 64) continue;
            for (int dy = -3; dy <= 3; dy++) {
                BlockPos pos = origin.offset(dx, dy, dz);
                if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()
                        || !level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) continue;
                BlockState state = level.getBlockState(pos);
                if (CROPS.contains(state.getBlock()) && !state.hasBlockEntity()) crops.add(new Crop(pos, state.getBlock()));
            }
        }
        crops.sort(Comparator.comparingDouble(c -> c.position.distToCenterSqr(center)));
        Season s = new Season(level, p, center, crops.stream().limit(96).toList(), level.getGameTime(), n.stone().color() & 0xFFFFFF);
        SEASONS.put(p.getUUID(), s);
        beginPhase(s, 0);
        render(s);
        PortFx.sound(level, center.add(0D, 1D, 0D), n, "infinity:time_stone", 0.7F, 1.1F);
    }

    @Override
    public void tick(ServerPlayer p, StoneTree.Node n, int ticks) {
        Season s = SEASONS.get(p.getUUID());
        if (s == null) return;
        if (!valid(s)) { SEASONS.remove(p.getUUID()); return; }
        int phase = Math.min(3, (int) ((s.level.getGameTime() - s.start) / 60L));
        if (phase != s.phase) beginPhase(s, phase);
        if (ticks % 5 == 0) render(s);
    }

    @Override
    public void stop(ServerPlayer p, StoneTree.Node n) { SEASONS.remove(p.getUUID()); }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { SEASONS.remove(e.getEntity().getUUID()); }

    private static boolean valid(Season s) {
        return s.owner.isAlive() && !s.owner.isRemoved() && s.owner.level() == s.level && !s.owner.hasDisconnected()
                && s.level.getGameTime() - s.start < 240L && s.owner.position().distanceToSqr(s.center) <= 32D * 32D
                && Stone.TIME.isHeldBy(s.owner);
    }

    private static void beginPhase(Season s, int phase) {
        s.phase = phase;
        int growth = phase == 0 ? 2 : phase == 1 ? 3 : phase == 2 ? 2 : 0;
        for (Crop crop : s.crops) {
            if (growth == 0 || !s.level.hasChunkAt(crop.position)) continue;
            // respect spawn protection / claims that hook mayInteract
            if (!s.level.mayInteract(s.owner, crop.position)) continue;
            BlockState state = s.level.getBlockState(crop.position);
            if (state.getBlock() != crop.block || state.hasBlockEntity() || !(state.getBlock() instanceof CropBlock plant)) continue;
            int next = Math.min(plant.getMaxAge(), plant.getAge(state) + growth);
            if (next != plant.getAge(state)) {
                s.level.setBlock(crop.position, plant.getStateForAge(next), Block.UPDATE_CLIENTS);
                s.level.sendParticles(ParticleTypes.HAPPY_VILLAGER, crop.position.getX() + 0.5D, crop.position.getY() + 0.55D,
                        crop.position.getZ() + 0.5D, 2, 0.15D, 0.15D, 0.15D, 0D);
            }
        }
        Vec3 c = s.center;
        SoundEvent sound = phase == 0 ? SoundEvents.BEE_POLLINATE : phase == 1 ? SoundEvents.AMETHYST_BLOCK_RESONATE
                : phase == 2 ? SoundEvents.GRASS_BREAK : SoundEvents.SNOW_BREAK;
        PortFx.sound(s.level, c, sound, 0.7F, 0.85F + phase * 0.2F);
        s.level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + 1D, c.z, 16, 2.5D, 0.75D, 2.5D, 0.04D);
    }

    private static void render(Season s) {
        int tint = TINTS[s.phase] < 0 ? s.rgb : TINTS[s.phase];
        DustParticleOptions dust = PortFx.dust(tint, 0.8F);
        DustParticleOptions green = PortFx.dust(s.rgb, 0.5F);
        SimpleParticleType motif = s.phase == 0 ? ParticleTypes.CHERRY_LEAVES : s.phase == 1 ? ParticleTypes.END_ROD
                : s.phase == 2 ? ParticleTypes.ENCHANT : ParticleTypes.SNOWFLAKE;
        Vec3 c = s.center;
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12D + s.level.getGameTime() * 0.018D;
            double r = 2D + (i % 4) * 1.8D;
            double x = c.x + Math.cos(a) * r, z = c.z + Math.sin(a) * r, y = c.y + 0.4D + (i % 3) * 0.55D;
            s.level.sendParticles(i % 6 == 5 ? green : dust, x, y, z, 1, 0D, 0D, 0D, 0D);
            if (i % 3 != 0) continue;
            s.level.sendParticles(motif, x, y + 0.8D, z, 1, 0.1D, 0.25D, 0.1D, 0.01D);
        }
    }
}
