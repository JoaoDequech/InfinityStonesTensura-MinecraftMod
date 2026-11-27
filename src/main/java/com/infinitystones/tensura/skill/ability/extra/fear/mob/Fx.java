package com.infinitystones.tensura.skill.ability.extra.fear.mob;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Server-side particles and layered sounds of the creatures. Everything is heard and seen only by whoever is in the realm. */
public final class Fx {
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(.45F, .01F, .02F), 1.5F);
    private static final DustParticleOptions BLOOD_FINE = new DustParticleOptions(new Vector3f(.62F, .03F, .03F), .8F);

    public static void play(ServerLevel l, Vec3 p, SoundEvent e, float vol, float pitch) {
        l.playSound(null, p.x, p.y, p.z, e, SoundSource.HOSTILE, vol, pitch);
    }

    public static void play(ServerLevel l, Vec3 p, net.neoforged.neoforge.registries.DeferredHolder<SoundEvent, SoundEvent> e, float vol, float pitch) {
        play(l, p, e.get(), vol, pitch);
    }

    public static void blood(ServerLevel l, Vec3 p, int n) {
        l.sendParticles(BLOOD, p.x, p.y, p.z, n, .28, .28, .28, .06);
        l.sendParticles(BLOOD_FINE, p.x, p.y, p.z, n, .5, .4, .5, .02);
        BlockState flesh = (l.random.nextBoolean() ? Blocks.REDSTONE_BLOCK : Blocks.NETHER_WART_BLOCK).defaultBlockState();
        l.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, flesh), p.x, p.y, p.z, n / 2 + 2, .25, .25, .25, .14);
        l.sendParticles(ParticleTypes.DAMAGE_INDICATOR, p.x, p.y, p.z, 2, .2, .2, .2, .0);
    }

    public static void gore(ServerLevel l, Vec3 p) {
        blood(l, p, 28);
        play(l, p, FearSounds.SQUELCH, 1.1F, .8F + l.random.nextFloat() * .3F);
        play(l, p, SoundEvents.GENERIC_EAT, .8F, .6F);
        play(l, p, SoundEvents.HONEY_BLOCK_BREAK, .9F, .5F);
    }

    public static void fog(ServerLevel l, Vec3 p, double w, double h, int n) {
        l.sendParticles(ParticleTypes.LARGE_SMOKE, p.x, p.y + h * .5, p.z, n, w, h * .5, w, .01);
        l.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, p.x, p.y + .2, p.z, n / 3 + 1, w, .1, w, .004);
        l.sendParticles(ParticleTypes.WHITE_ASH, p.x, p.y + h * .5, p.z, n, w * 1.4, h * .5, w * 1.4, .02);
    }

    public static void burst(ServerLevel l, Vec3 p, boolean blood) {
        fog(l, p, .5, 1.6, 36);
        l.sendParticles(ParticleTypes.SOUL, p.x, p.y + 1, p.z, 10, .4, .8, .4, .04);
        if (blood) blood(l, p.add(0, 1, 0), 22);
    }

    public static void stinger(ServerLevel l, Vec3 p, float vol) {
        play(l, p, FearSounds.STINGER, vol, .9F + l.random.nextFloat() * .2F);
        play(l, p, SoundEvents.SCULK_SHRIEKER_SHRIEK, vol * .5F, 1.3F);
    }

    public static void scream(ServerLevel l, Vec3 p, Kind k) {
        RandomSource r = l.random;
        float j = 1F + (r.nextFloat() - .5F) * .1F;
        switch (k) {
            case WENDIGO -> { play(l, p, FearSounds.SCREAM_A, 2F, .8F * j); play(l, p, SoundEvents.ENDERMAN_SCREAM, 1.2F, .45F); play(l, p, SoundEvents.WARDEN_ROAR, .5F, 1.3F); }
            case LADY -> { play(l, p, FearSounds.SCREAM_B, 1.7F, 1.15F * j); play(l, p, SoundEvents.GHAST_SCREAM, .8F, 1.3F); play(l, p, SoundEvents.GLASS_BREAK, .8F, .6F); }
            case SURGEON -> { play(l, p, FearSounds.SCREAM_LOW, 1.7F, .9F * j); play(l, p, SoundEvents.GRINDSTONE_USE, 1.1F, .6F); play(l, p, SoundEvents.ANVIL_LAND, .5F, .5F); }
            case PRIEST -> { play(l, p, FearSounds.SCREAM_LOW, 1.5F, .7F * j); play(l, p, FearSounds.CHANT, 1.5F, 1F); play(l, p, SoundEvents.BELL_BLOCK, 1.2F, .5F); }
            case MASCOT -> { play(l, p, FearSounds.SCREAM_B, 1.5F, .7F * j); play(l, p, FearSounds.SERVO, 1.6F, 1F); play(l, p, SoundEvents.NOTE_BLOCK_BELL.value(), 1.4F, .5F); }
            case DROWNED -> { play(l, p, FearSounds.SCREAM_A, 1.6F, 1.3F * j); play(l, p, SoundEvents.DROWNED_DEATH, 1F, .5F); play(l, p, SoundEvents.GENERIC_SPLASH, 1F, .6F); }
            case GRAVEKEEPER -> { play(l, p, FearSounds.SCREAM_LOW, 1.8F, .6F * j); play(l, p, SoundEvents.ZOMBIE_VILLAGER_CURE, 1F, .5F); play(l, p, SoundEvents.GRAVEL_BREAK, 1F, .6F); }
            case SMILER -> { play(l, p, FearSounds.SCREAM_B, 1.7F, .6F * j); play(l, p, FearSounds.WHISPER, 1.4F, .7F); play(l, p, SoundEvents.SCULK_SHRIEKER_SHRIEK, 1F, .8F); }
            case FROSTGIANT -> { play(l, p, FearSounds.SCREAM_LOW, 2.3F, .45F); play(l, p, SoundEvents.WARDEN_ROAR, 1.3F, .6F); play(l, p, SoundEvents.GLASS_BREAK, 1F, .5F); }
            case STALKER -> { play(l, p, FearSounds.SCREAM_A, 1.7F, .6F * j); play(l, p, SoundEvents.RAVAGER_ROAR, 1.2F, .5F); play(l, p, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, 1F, .6F); }
            case DOPPELGANGER -> { play(l, p, FearSounds.SCREAM_B, 1.3F, .9F * j); play(l, p, SoundEvents.PLAYER_HURT, 1F, .6F); play(l, p, FearSounds.BREATH, 1F, .8F); }
        }
        l.sendParticles(ParticleTypes.SONIC_BOOM, p.x, p.y + k.height * .8, p.z, 1, 0, 0, 0, 0);
    }

    public static void step(ServerLevel l, Vec3 p, Kind k) {
        float j = .85F + l.random.nextFloat() * .3F;
        switch (k) {
            case WENDIGO -> { play(l, p, FearSounds.CLICK, .5F, j); play(l, p, SoundEvents.MUD_STEP, .6F, .6F); }
            case LADY -> { play(l, p, FearSounds.CLICK, .4F, 1.5F * j); play(l, p, SoundEvents.AMETHYST_BLOCK_CHIME, .25F, 1.6F); }
            case SURGEON -> play(l, p, SoundEvents.CHAIN_STEP, .8F, .6F * j);
            case PRIEST -> { play(l, p, SoundEvents.WOOL_STEP, .6F, .7F); play(l, p, FearSounds.RATTLE, .3F, j); }
            case MASCOT -> { play(l, p, FearSounds.SERVO, .5F, j); play(l, p, SoundEvents.IRON_GOLEM_STEP, .7F, .6F); }
            case DROWNED -> { play(l, p, SoundEvents.MUD_STEP, .8F, .5F); play(l, p, SoundEvents.PLAYER_SPLASH, .25F, .6F); }
            case GRAVEKEEPER -> play(l, p, SoundEvents.GRAVEL_STEP, .9F, .6F * j);
            case SMILER -> {}
            case FROSTGIANT -> { play(l, p, SoundEvents.STONE_STEP, 1.6F, .4F); play(l, p, SoundEvents.SNOW_STEP, 1.4F, .5F); }
            case STALKER -> { play(l, p, SoundEvents.WOOD_STEP, .8F, .6F * j); play(l, p, SoundEvents.ROOTS_STEP, .5F, .7F); }
            case DOPPELGANGER -> {
                BlockState s = l.getBlockState(BlockPos.containing(p).below());
                play(l, p, s.getSoundType().getStepSound(), .7F, .85F * j);
            }
        }
    }

    public static void ambient(ServerLevel l, Vec3 p, Kind k) {
        RandomSource r = l.random;
        float j = .85F + r.nextFloat() * .3F;
        switch (k) {
            case WENDIGO -> { play(l, p, FearSounds.BREATH, .9F, .7F * j); if (r.nextInt(3) == 0) play(l, p, FearSounds.CLICK, .6F, .8F); }
            case LADY -> play(l, p, FearSounds.WHISPER, .8F, 1.1F * j);
            case SURGEON -> { play(l, p, FearSounds.RATTLE, .9F, .8F * j); if (r.nextBoolean()) play(l, p, SoundEvents.GRINDSTONE_USE, .5F, 1.1F); }
            case PRIEST -> play(l, p, FearSounds.CHANT, .9F, .8F * j);
            case MASCOT -> { play(l, p, FearSounds.SERVO, .8F, j); play(l, p, SoundEvents.NOTE_BLOCK_CHIME.value(), .9F, .5F + r.nextInt(6) * .09F); }
            case DROWNED -> { play(l, p, SoundEvents.POINTED_DRIPSTONE_DRIP_WATER, 1F, .7F); play(l, p, FearSounds.BREATH, .7F, 1.2F); }
            case GRAVEKEEPER -> { play(l, p, SoundEvents.GRAVEL_BREAK, .9F, .7F); play(l, p, FearSounds.BREATH, .5F, .6F); }
            case SMILER -> play(l, p, FearSounds.DRONE, .8F, .8F * j);
            case FROSTGIANT -> play(l, p, FearSounds.DRONE, 1.2F, .5F);
            case STALKER -> { play(l, p, SoundEvents.WOOD_HIT, 1F, .5F); play(l, p, FearSounds.RATTLE, .6F, .6F); }
            case DOPPELGANGER -> play(l, p, FearSounds.BREATH, .6F, 1F * j);
        }
    }

    private Fx() {}
}
