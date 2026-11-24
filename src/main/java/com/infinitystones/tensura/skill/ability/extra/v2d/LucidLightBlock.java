package com.infinitystones.tensura.skill.ability.extra.v2d;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.joml.Vector3f;

/**
 * A thought made solid (Lucid Dream): a golden-red block of light with no loot table, so it never drops anything. Its
 * own scheduled tick makes it vanish with a shimmer after {@link #LIFETIME} ticks, which also survives unloading and
 * server restarts (scheduled ticks are saved with the chunk).
 */
public final class LucidLightBlock extends Block {
    public static final int LIFETIME = 20 * 120;
    public static final ResourceLocation ID = InfinityStones.id("lucid_light");
    private static final DustParticleOptions GOLD = new DustParticleOptions(new Vector3f(1F, 0.72F, 0.2F), 0.9F);
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(1F, 0.22F, 0.12F), 0.9F);
    private static Block instance;

    public LucidLightBlock(Properties p) { super(p); }

    public static Block block() { return instance; }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        vanish(level, pos);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6) != 0) return;
        double x = pos.getX() + random.nextDouble(), y = pos.getY() + random.nextDouble(), z = pos.getZ() + random.nextDouble();
        level.addParticle(random.nextBoolean() ? GOLD : RED, x, y, z, 0, 0.02, 0);
    }

    /** Removes the block with a shimmer (no drops, no neighbour updates beyond the usual). */
    public static void vanish(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(instance)) return;
        level.removeBlock(pos, false);
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        level.sendParticles(GOLD, x, y, z, 6, 0.4, 0.4, 0.4, 0.02);
        level.sendParticles(RED, x, y, z, 6, 0.4, 0.4, 0.4, 0.02);
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD, x, y, z, 2, 0.3, 0.3, 0.3, 0.03);
        if (level.random.nextInt(4) == 0) level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.25F, 1.4F + level.random.nextFloat() * 0.4F);
    }

    /** Places a lucid block at pos (air only) and schedules its end. */
    public static boolean place(ServerLevel level, BlockPos pos) {
        if (instance == null || !level.getBlockState(pos).isAir()) return false;
        level.setBlock(pos, instance.defaultBlockState(), 3);
        level.scheduleTick(pos, instance, LIFETIME);
        return true;
    }

    @EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent
        public static void register(RegisterEvent e) {
            e.register(Registries.BLOCK, helper -> {
                instance = new LucidLightBlock(BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(0.4F, 3600000F)
                        .sound(SoundType.AMETHYST).lightLevel(s -> 15).noLootTable().pushReaction(PushReaction.DESTROY).isValidSpawn((s, g, p, t) -> false));
                helper.register(ID, instance);
            });
        }
    }
}
