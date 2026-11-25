package com.infinitystones.tensura.skill.ability.extra.fear.client;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.ability.extra.fear.FearRealm;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;

/** Full-screen chromatic aberration, wobble, glitch slices, film grain and vignette. Off while a shader pack is active. */
final class FearPost {
    private static PostChain chain;
    private static boolean failed;
    private static int lw, lh;

    static void reset() { if (chain != null) chain.close(); chain = null; failed = false; }

    static void apply(float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (failed || mc.level == null || mc.level.dimension() != FearRealm.DIM || FearCompat.shaders()) return;
        boolean victim = FearState.victim || FearState.watchMode == 1 || FearState.watchMode == 3;
        if (!victim) return;
        float t = (FearState.ticks + pt) / 20F;
        float ab = 0.15F + FearState.jump.rem(pt) * 2.2F + FearState.tear.rem(pt) * 1.6F + FearState.enter.p(pt) * (FearState.enter.on() ? 2F : 0F) + FearState.loudFlash.rem(pt) + FearState.fear / 160F;
        float dist = FearState.pulse.rem(pt) * 0.8F + FearState.lava.rem(pt) * 0.9F + (FearState.voidFall.on() ? 0.7F : 0F) + FearState.tear.rem(pt) + (FearState.enter.on() ? FearState.enter.p(pt) : 0F) + FearState.fear / 400F;
        float grain = 0.18F + FearState.fear / 220F + FearState.jump.rem(pt);
        float vig = 0.35F + FearState.fear / 250F;
        if (chain == null) {
            try {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), InfinityStones.id("shaders/post/fear_fx.json"));
                lw = lh = 0;
            } catch (Exception | LinkageError e) { failed = true; return; }
        }
        int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
        if (w != lw || h != lh) { chain.resize(w, h); lw = w; lh = h; }
        chain.setUniform("Time", t);
        chain.setUniform("Aberration", Math.min(ab, 3F));
        chain.setUniform("Distort", Math.min(dist, 1.5F));
        chain.setUniform("Grain", Math.min(grain, 1.5F));
        chain.setUniform("Vignette", Math.min(vig, 0.8F));
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.resetTextureMatrix();
        chain.process(pt);
        mc.getMainRenderTarget().bindWrite(true);
    }

    private FearPost() {}
}
