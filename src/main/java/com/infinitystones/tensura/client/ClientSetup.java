package com.infinitystones.tensura.client;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.registry.ModMisc;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = InfinityStones.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    @SubscribeEvent
    public static void particles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModMisc.SHOCKWAVE.get(), ShockwaveParticle.Provider::new);
    }

    /** Port of the original Palladium particle: lifetime 20, quad size 2, no physics, full brightness, opaque. */
    public static final class ShockwaveParticle extends TextureSheetParticle {
        private final SpriteSet sprites;

        ShockwaveParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
            super(level, x, y, z);
            this.sprites = sprites;
            this.lifetime = 20;
            this.quadSize = 2F;
            this.gravity = 0F;
            this.hasPhysics = false;
            this.setSpriteFromAge(sprites);
        }

        @Override public void tick() { super.tick(); this.setSpriteFromAge(this.sprites); }
        @Override protected int getLightColor(float partialTick) { return 0xF000F0; }
        @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_OPAQUE; }

        public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
            @Override
            public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
                return new ShockwaveParticle(level, x, y, z, sprites);
            }
        }
    }

    private ClientSetup() {}
}
