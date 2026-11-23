package com.infinitystones.tensura.client.render;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.client.GroundDisplayLayer;
import com.infinitystones.tensura.ground.GroundDisplay;
import com.infinitystones.tensura.registry.ModItems;
import com.infinitystones.tensura.skill.Stone;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Client-only ambience for relics lying in the world (ground displays and dropped item entities): coloured motes that
 * orbit and rise around the stone, plus star twinkles. Particles are added straight to the particle engine (no
 * registered type needed); sprites come from textures/particle/, which the vanilla particle atlas stitches.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID, value = Dist.CLIENT)
public final class RelicParticles {
    private static final double RANGE_SQ = 48 * 48;
    private static final RandomSource RNG = RandomSource.create();
    /** 1 normally, 0.5 with the "Decreased" particles option. */
    private static float density = 1F;
    private static final int[] GAUNTLET_COLORS = new int[Stone.values().length];

    static {
        for (Stone s : Stone.values()) GAUNTLET_COLORS[s.ordinal()] = s.color();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.isPaused() || mc.options.particles().get() == ParticleStatus.MINIMAL) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        density = mc.options.particles().get() == ParticleStatus.DECREASED ? 0.5F : 1F;
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof ArmorStand) && !(e instanceof ItemEntity)) continue;
            if (e.distanceToSqr(cam) > RANGE_SQ) continue;
            if (e instanceof ArmorStand stand && GroundDisplay.isDisplay(stand)) {
                emit(mc, level, stand.getItemBySlot(EquipmentSlot.CHEST), stand.getX(), stand.getY() + GroundDisplayLayer.hover(stand, 0) + 0.25, stand.getZ(), true);
            } else if (e instanceof ItemEntity item) {
                emit(mc, level, item.getItem(), item.getX(), item.getY() + 0.3, item.getZ(), false);
            }
        }
    }

    private static void emit(Minecraft mc, ClientLevel level, ItemStack stack, double x, double y, double z, boolean display) {
        int color = GlowFx.relicColor(stack);
        if (color == 0) return;
        boolean gauntlet = stack.is(ModItems.INFINITY_GAUNTLET.get());
        float rate = (display ? 1F : 0.6F) * density;
        if (RNG.nextFloat() < 0.55F * rate) {
            int c = gauntlet ? GAUNTLET_COLORS[RNG.nextInt(GAUNTLET_COLORS.length)] : color;
            mc.particleEngine.add(new Mote(level, x, y - 0.25 + RNG.nextDouble() * 0.2, z, c, sprite("stone_mote"), true));
        }
        if (RNG.nextFloat() < 0.18F * rate) {
            int c = gauntlet ? GlowFx.GOLD : lighten(color);
            double a = RNG.nextDouble() * Math.PI * 2, r = 0.1 + RNG.nextDouble() * 0.3;
            mc.particleEngine.add(new Mote(level, x + Math.cos(a) * r, y + (RNG.nextDouble() - 0.5) * 0.4, z + Math.sin(a) * r, c, sprite("stone_spark"), false));
        }
        if (display && RNG.nextFloat() < 0.12F * density) { // embers rising from the ground circle
            int c = gauntlet ? GAUNTLET_COLORS[RNG.nextInt(GAUNTLET_COLORS.length)] : color;
            double a = RNG.nextDouble() * Math.PI * 2;
            Mote m = new Mote(level, x + Math.cos(a) * 0.5, y - GroundDisplayLayer.BASE_HOVER - 0.2, z + Math.sin(a) * 0.5, c, sprite("stone_mote"), false);
            m.rise = 0.035F;
            mc.particleEngine.add(m);
        }
    }

    private static int lighten(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return 0xFF000000 | ((r + (255 - r) / 2) << 16) | ((g + (255 - g) / 2) << 8) | (b + (255 - b) / 2);
    }

    static TextureAtlasSprite sprite(String name) {
        // the particle atlas belongs to the particle engine, not the model manager (getTextureAtlas would crash)
        var tex = Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
        return ((TextureAtlas) tex).getSprite(InfinityStones.id(name));
    }

    /** Additive, full-bright particle sheet (glow adds up instead of covering what's behind). */
    static final ParticleRenderType ADDITIVE = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator tesselator, TextureManager textureManager) {
            RenderSystem.depthMask(false);
            RenderSystem.setShader(GameRenderer::getParticleShader);
            RenderSystem.setShaderTexture(0, TextureAtlas.LOCATION_PARTICLES);
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            return tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        }

        @Override
        public String toString() { return "INFINITY_ADDITIVE"; }
    };

    /** Orbiting (spiral up around the spawn axis) or drifting twinkle; fades in and out. */
    static final class Mote extends TextureSheetParticle {
        private final double cx, cz;
        private double angle, radius;
        private final float spin, baseSize;
        private final boolean orbit;
        float rise;

        Mote(ClientLevel level, double x, double y, double z, int argb, TextureAtlasSprite sprite, boolean orbit) {
            super(level, x, y, z);
            this.orbit = orbit;
            this.cx = x; this.cz = z;
            this.angle = RNG.nextDouble() * Math.PI * 2;
            this.radius = orbit ? 0.32 + RNG.nextDouble() * 0.22 : 0;
            this.spin = (RNG.nextBoolean() ? 1 : -1) * (0.08F + RNG.nextFloat() * 0.06F);
            this.rise = orbit ? 0.012F + RNG.nextFloat() * 0.01F : 0.004F;
            this.lifetime = orbit ? 40 + RNG.nextInt(30) : 12 + RNG.nextInt(10);
            this.baseSize = orbit ? 0.05F + RNG.nextFloat() * 0.04F : 0.09F + RNG.nextFloat() * 0.06F;
            this.quadSize = baseSize;
            this.gravity = 0;
            this.hasPhysics = false;
            this.rCol = ((argb >> 16) & 255) / 255F;
            this.gCol = ((argb >> 8) & 255) / 255F;
            this.bCol = (argb & 255) / 255F;
            this.alpha = 0;
            this.setSprite(sprite);
            if (orbit) { this.x = cx + Math.cos(angle) * radius; this.z = cz + Math.sin(angle) * radius; this.xo = this.x; this.zo = this.z; }
        }

        Mote push(double vx, double vy, double vz) { xd = vx; yd = vy; zd = vz; return this; }

        @Override
        public void tick() {
            xo = x; yo = y; zo = z;
            if (age++ >= lifetime) { remove(); return; }
            float k = age / (float) lifetime;
            if (orbit) {
                angle += spin;
                radius *= 0.985;
                x = cx + Math.cos(angle) * radius;
                z = cz + Math.sin(angle) * radius;
            }
            y += rise;
            if (!orbit) { x += xd; y += yd; z += zd; xd *= 0.82; yd *= 0.82; zd *= 0.82; }
            alpha = Mth.sin(k * Mth.PI) * (orbit ? 0.9F : 1F);
            quadSize = baseSize * (orbit ? (1F - 0.4F * k) : (0.4F + Mth.sin(k * Mth.PI)));
            oRoll = roll;
            if (!orbit) roll += 0.1F;
        }

        @Override protected int getLightColor(float partialTick) { return GlowFx.FULL; }
        @Override public ParticleRenderType getRenderType() { return ADDITIVE; }
    }

    private RelicParticles() {}
}
