package com.infinitystones.tensura.skill.ability.extra.fear.client;

import com.infinitystones.tensura.skill.ability.extra.fear.FearNet;
import com.infinitystones.tensura.skill.ability.extra.fear.FearRealm;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import static com.infinitystones.tensura.skill.ability.extra.fear.client.FearGfx.*;

/** All 2D layers of the nightmare: flashlight and darkness, heartbeat, arrival and exit transitions, scares, objectives. */
final class FearHud {
    private static float light = 1F, swayX, swayY, lastYaw, lastPitch;
    private static boolean init;

    static float lightLevel() { return light; }

    static void render(GuiGraphics g, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui && !FearState.victim) return;
        int w = g.guiWidth(), h = g.guiHeight();
        boolean realm = mc.level.dimension() == FearRealm.DIM;
        boolean victimView = (FearState.victim && realm) || FearState.watchMode == 1 || FearState.watchMode == 3;
        boolean anyFx = FearState.victim || FearState.watchMode != 0;
        float t = (FearState.ticks + pt);
        if (realm && victimView) darkness(g, w, h, pt, t, mc);
        else if (realm && FearState.watchMode == 0 && !FearState.victim) vignette(g, w, h, 0x000000, 0.5F);
        else if (realm) vignette(g, w, h, 0x000000, 0.35F);
        if (!anyFx) return;
        heartbeat(g, w, h, t);
        if (FearState.eyes.on()) eyes(g, w, h, pt, t, mc);
        if (FearState.lava.on()) lava(g, w, h, pt, t);
        if (FearState.voidFall.on()) voidFall(g, w, h, pt, t);
        if (FearState.lights.on()) blackout(g, w, h, pt, t);
        if (FearState.tear.on()) tear(g, w, h, pt, t);
        if (FearState.pulse.on()) vignette(g, w, h, 0x6A0010, 0.35F * Mth.sin(FearState.pulse.p(pt) * 18F) * Mth.sin(FearState.pulse.p(pt) * 18F) * FearState.pulse.rem(pt));
        if (FearState.whisper.on()) whisper(g, w, h, t);
        if (FearState.speak.on()) speak(g, w, h, pt, t, mc.font);
        if (FearState.inv.on() || !FearState.items.isEmpty()) fallingItems(g);
        if (FearState.flick.on() && (FearState.ticks / 2) % 3 == 0) g.fill(0, 0, w, h, argb(0.55F, 0));
        if (FearState.victim && realm) objectives(g, w, h, mc.font);
        if (FearState.enter.on()) arrival(g, w, h, pt, t);
        if (FearState.wake.on()) waking(g, w, h, pt, t);
        if (FearState.exit.on()) g.fill(0, 0, w, h, argb(FearState.exit.rem(pt), 0xFFFFFF));
        if (FearState.wakeFlash.on()) g.fill(0, 0, w, h, argb(FearState.wakeFlash.rem(pt) * FearState.wakeFlash.rem(pt), 0xFFFFFF));
        if (FearState.lightning.on()) g.fill(0, 0, w, h, argb(Math.min(1F, FearState.lightning.rem(pt) * 1.4F), 0xE8EEFF));
        if (FearState.loudFlash.on()) g.fill(0, 0, w, h, argb(FearState.loudFlash.rem(pt) * 0.8F, 0xFFD0C0));
        if (FearState.jump.on()) jumpscare(g, w, h, pt, t);
        if (FearState.watchMode != 0) watchBar(g, w, h, mc.font);
    }

    // ------------------------------------------------------------------ flashlight

    private static void darkness(GuiGraphics g, int w, int h, float pt, float t, Minecraft mc) {
        boolean flashOk = FearState.watchMode != 0 ? FearState.battery > 0 : FearState.flashOn && FearState.battery > 0;
        if (FearState.flashOff.on() || FearState.lights.on()) flashOk = false;
        float target = flashOk ? 1F : 0F;
        float lowBat = FearState.battery < 18 && flashOk ? 0.55F + 0.45F * FearGfx.hash((int) (t * 0.6F), 3) : 1F;
        float flick = 1F;
        if (FearState.flick.on()) flick = ((int) (t * 1.5F) % 3 == 0) ? 0.15F : 1F;
        light += (target - light) * (flashOk ? 0.5F : 0.35F);
        float L = light * lowBat * flick;
        float yaw = mc.player.getViewYRot(pt), pitch = mc.player.getViewXRot(pt);
        if (!init) { lastYaw = yaw; lastPitch = pitch; init = true; }
        swayX += (-Mth.wrapDegrees(yaw - lastYaw) * 5F - swayX) * 0.18F;
        swayY += (-(pitch - lastPitch) * 5F - swayY) * 0.18F;
        lastYaw = yaw; lastPitch = pitch;
        float cx = w / 2F + Mth.clamp(swayX, -60, 60), cy = h / 2F + Mth.clamp(swayY, -40, 40) + h * 0.02F;
        float base = Math.min(w, h) * 0.34F * L;
        float dk = 0.55F + 0.44F * FearState.dark / 100F;
        if (L < 0.02F) dk = Math.max(dk, 0.985F);
        float r0 = Math.max(base * 0.12F, 5F), r1 = Math.max(base * 0.62F, 9F), r2 = Math.max(base, 16F), r3 = r2 * 1.7F, far = (float) Math.hypot(w, h) * 1.2F;
        rings(g, cx, cy, new float[]{0F, r0, r1, r2, r3, far},
                new int[]{argb(0F, 0), argb(0F, 0), argb(dk * 0.28F, 0), argb(dk * 0.78F, 0), argb(dk, 0), argb(dk, 0)}, 1.18F, 40);
        if (L > 0.05F) {
            float a = 0.10F * L * FearCompat.glow();
            disc(g, cx, cy, r2 * 1.1F, r2 * 0.95F, argb(a, 0xFFE9BF), argb(0F, 0xFFE9BF), 36, true);
            for (int i = 0; i < 34; i++) {
                float px = FearGfx.hash(i, 1), py = FearGfx.hash(i, 2), sp = 0.15F + FearGfx.hash(i, 3);
                float ang = (px + t * 0.002F * sp) * 6.283F, rad = Mth.sqrt(py) * r2 * 1.05F;
                float x = cx + Mth.cos(ang) * rad * 1.18F, y = cy + Mth.sin(ang * 1.3F + t * 0.01F * sp) * rad * 0.9F + Mth.sin(t * 0.04F * sp + i) * 3F;
                float al = (1F - rad / (r2 * 1.1F)) * 0.45F * L * FearCompat.glow();
                if (al > 0.02F) g.fill((int) x, (int) y, (int) x + 2, (int) y + 2, argb(al, 0xFFF3DA));
            }
        }
        if (FearState.fear > 45) {
            float puff = (t % 90) / 90F;
            if (puff < 0.6F) disc(g, w / 2F, h * 0.97F, w * (0.1F + puff * 0.25F), h * (0.04F + puff * 0.08F), argb(0.08F * (1F - puff / 0.6F), 0xE8F0FF), argb(0F, 0xE8F0FF), 20, false);
        }
        if (FearState.victim && FearState.watchMode == 0) battery(g, w, h);
    }

    private static void battery(GuiGraphics g, int w, int h) {
        int x = w - 46, y = h - 20, bat = FearState.battery;
        int col = bat > 40 ? 0xB8E8A8 : bat > 15 ? 0xE8C060 : 0xE04040;
        g.fill(x - 1, y - 1, x + 33, y + 9, argb(0.7F, 0));
        g.fill(x + 33, y + 2, x + 35, y + 6, argb(0.7F, 0));
        for (int i = 0; i < 5; i++) if (bat > i * 20) g.fill(x + 1 + i * 6, y + 1, x + 6 + i * 6, y + 8, argb(FearState.flashOn ? 0.9F : 0.35F, col));
    }

    private static void vignette(GuiGraphics g, int w, int h, int rgb, float a) {
        if (a <= 0.01F) return;
        float far = (float) Math.hypot(w, h) * 0.75F;
        rings(g, w / 2F, h / 2F, new float[]{0F, far * 0.55F, far * 0.8F, far * 1.1F}, new int[]{argb(0F, rgb), argb(0F, rgb), argb(a * 0.6F, rgb), argb(a, rgb)}, 1F, 32);
    }

    private static void heartbeat(GuiGraphics g, int w, int h, float t) {
        float fear = FearState.fear / 100F + (FearState.heart.on() ? 0.5F : 0F);
        if (fear < 0.05F) return;
        float bpm = 62 + fear * 90;
        float ph = (t * bpm / 1200F) % 1F;
        float beat = (float) Math.pow(Math.max(0, Math.sin(ph * Math.PI * 2)), 8) + 0.5F * (float) Math.pow(Math.max(0, Math.sin(ph * Math.PI * 2 - 1.2)), 10);
        vignette(g, w, h, 0x7A0008, Mth.clamp(0.1F + fear * 0.35F + beat * 0.3F * fear, 0, 0.8F));
    }

    // ------------------------------------------------------------------ transitions

    private static void arrival(GuiGraphics g, int w, int h, float pt, float t) {
        FearState.Timer e = FearState.enter;
        float p = e.p(pt);
        boolean full = e.total >= 90;
        float cx = w / 2F, cy = h / 2F;
        if (full && p < 0.55F) {
            int hands = 8;
            float rise = easeOutCubic(Mth.clamp(p / 0.32F, 0, 1));
            float grip = Mth.clamp((p - 0.3F) / 0.2F, 0, 1);
            for (int i = 0; i < hands; i++) {
                float hx = w * (0.08F + 0.84F * (i + 0.5F * hash(i, 9)) / hands);
                float hh = h * (0.25F + 0.4F * hash(i, 4)) * rise + Mth.sin(t * 0.3F + i) * 6F;
                float lean = (cx - hx) * 0.25F * grip;
                float tipx = hx + lean + Mth.sin(t * 0.25F + i * 2) * 5F, tipy = h - hh;
                line(g, hx, h + 6, tipx, tipy + 22, 22 - hh / h * 6, argb(1F, 0x020202));
                for (int f = 0; f < 5; f++) {
                    float spread = (f - 2) * 0.42F * (1F - grip * 0.85F);
                    float len = 26 + 8 * hash(i, f);
                    line(g, tipx, tipy + 22, tipx + Mth.sin(spread) * len + lean * 0.2F, tipy + 22 - Mth.cos(spread) * len, 5F, argb(1F, 0x020202));
                }
            }
        }
        if (p > 0.18F && p < 0.62F) {
            float k = Mth.clamp((p - 0.18F) / 0.12F, 0, 1);
            long seed = e.total * 7L;
            for (int i = 0; i < 16; i++) {
                float a = hash(i, 11) * 6.283F;
                float x = cx, y = cy;
                float len = (0.3F + hash(i, 12) * 0.75F) * Math.max(w, h) * 0.6F * k;
                for (int s = 0; s < 7; s++) {
                    float na = a + (hash(i, 20 + s) - 0.5F) * 0.9F;
                    float nx = x + Mth.cos(na) * len / 7, ny = y + Mth.sin(na) * len / 7;
                    line(g, x, y, nx, ny, 2.2F - s * 0.2F, argb(0.9F * (1F - p * 0.5F), 0xE8E8F0));
                    line(g, x + 1, y + 1, nx + 1, ny + 1, 1F, argb(0.8F, 0x000000));
                    x = nx; y = ny; a = na;
                }
            }
        }
        float ink = Mth.clamp((p - (full ? 0.45F : 0.05F)) / (full ? 0.45F : 0.85F), 0, 1);
        if (ink > 0F) {
            float far = (float) Math.hypot(w, h) * 0.62F * easeInOut(ink) * 1.25F;
            for (int arm = 0; arm < 4; arm++) {
                float ang = t * 0.08F + arm * 1.5708F;
                for (int s = 0; s < 22; s++) {
                    float r = far * (s / 22F), r2 = far * ((s + 1) / 22F);
                    float a0 = ang + s * 0.22F, a1 = ang + (s + 1) * 0.22F;
                    line(g, cx + Mth.cos(a0) * r, cy + Mth.sin(a0) * r, cx + Mth.cos(a1) * r2, cy + Mth.sin(a1) * r2, 10 + s * 3.2F * ink, argb(0.96F, 0x010101));
                }
            }
            disc(g, cx, cy, far * 0.98F, far * 0.98F, argb(1F, 0), argb(ink > 0.85F ? 1F : 0.9F * ink, 0), 40, false);
            if (ink >= 0.97F) g.fill(0, 0, w, h, 0xFF000000);
        }
        if (p < 0.08F) g.fill(0, 0, w, h, argb(1F - p / 0.08F, 0xFFFFFF));
    }

    private static void waking(GuiGraphics g, int w, int h, float pt, float t) {
        float p = FearState.wake.p(pt);
        if (p < 0.12F) g.fill(0, 0, w, h, argb(1F - p / 0.12F, 0));
        float open = easeOutCubic(Mth.clamp((p - 0.04F) / 0.5F, 0, 1));
        float flutter = p < 0.5F ? 0.08F * Mth.sin(t * 0.7F) * (1F - open) : 0F;
        float lid = (1F - Mth.clamp(open + flutter, 0, 1)) * h * 0.5F;
        if (lid > 1F) {
            vgrad(g, 0, 0, w, lid, argb(1F, 0), argb(1F, 0));
            vgrad(g, 0, h - lid, w, h, argb(1F, 0), argb(1F, 0));
            vgrad(g, 0, lid, w, lid + 24, argb(0.9F, 0), argb(0F, 0));
            vgrad(g, 0, h - lid - 24, w, h - lid, argb(0F, 0), argb(0.9F, 0));
        }
        vignette(g, w, h, 0, 0.8F * (1F - p));
    }

    // ------------------------------------------------------------------ scares

    private static void jumpscare(GuiGraphics g, int w, int h, float pt, float t) {
        FearState.Timer j = FearState.jump;
        float p = j.p(pt);
        int seed = FearState.seed;
        if (p < 0.9F) g.fill(0, 0, w, h, argb(p < 0.1F ? 0.7F : 0.93F, p < 0.06F ? 0x400000 : 0x000000));
        float s = p < 0.1F ? easeOutBack(p / 0.1F) * 1.2F : 1.2F + (p - 0.1F) * 0.35F;
        float size = h * s * 1.05F;
        float jx = (hash((int) (t * 3), seed) - 0.5F) * 22F * (1F - p), jy = (hash((int) (t * 3), seed + 1) - 0.5F) * 18F * (1F - p);
        boolean flick = p > 0.62F && hash((int) (t * 2), seed) < 0.35F;
        if (!flick && p < 0.96F) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.blit(FearFace.get(FearState.jumpVariant), (int) (w / 2F - size / 2 + jx), (int) (h / 2F - size / 2 + jy), (int) size, (int) size, 0, 0, FearFace.SIZE, FearFace.SIZE, FearFace.SIZE, FearFace.SIZE);
        }
        for (int i = 0; i < 18; i++) {
            float bx = hash(i, seed) * w, by = hash(i, seed + 5) * h, grow = Mth.clamp((p * 7F) - hash(i, 7), 0, 1);
            float r = (14 + hash(i, 8) * 70) * easeOutCubic(grow);
            if (r > 1) disc(g, bx, by, r, r * 0.85F, argb(0.8F, 0x7A0004), argb(0.55F, 0x3A0002), 14, false);
            float dl = h * 0.5F * hash(i, 9) * grow * (0.4F + p);
            if (dl > 2) g.fill((int) bx - 2, (int) by, (int) bx + 2, (int) Math.min(h, by + dl), argb(0.7F, 0x600003));
        }
        if (p < 0.2F) g.fill(0, 0, w, h, argb((0.2F - p) * 3F, 0xCC1010));
        vignette(g, w, h, 0x300000, 0.9F);
        float grain = 0.25F;
        for (int i = 0; i < 70; i++) g.fill((int) (hash(i, (int) t) * w), (int) (hash(i, (int) t + 9) * h), (int) (hash(i, (int) t) * w) + 2, (int) (hash(i, (int) t + 9) * h) + 2, argb(grain, 0xDDDDDD));
    }

    private static void lava(GuiGraphics g, int w, int h, float pt, float t) {
        float k = FearState.lava.rem(pt), in = Math.min(1F, FearState.lava.p(pt) * 8F);
        float a = Math.min(in, k * 2.5F);
        vignette(g, w, h, 0xFF4400, 0.75F * a);
        vgrad(g, 0, h * 0.35F, w, h, argb(0F, 0xFF3000), argb(0.55F * a, 0xFF5A00));
        TextureAtlasSprite fire = ModelBakery.FIRE_1.sprite();
        int fh = (int) (h * 0.62F), fw = fh;
        RenderSystem.enableBlend();
        for (int x = -fw / 2; x < w; x += fw * 2 / 3) {
            int dy = (int) (Mth.sin(t * 0.5F + x) * 6);
            g.blit(x, h - fh + (int) ((1F - a) * fh) + dy, 0, fw, fh, fire);
        }
        for (int i = 0; i < 24; i++) {
            float x = hash(i, (int) (t * 0.3F)) * w, y = h - ((t * (3 + i % 5) + i * 40) % h);
            g.fill((int) x, (int) y, (int) x + 2, (int) y + 3, argb(0.8F * a * FearCompat.glow(), 0xFFB040));
        }
    }

    private static void voidFall(GuiGraphics g, int w, int h, float pt, float t) {
        float a = Math.min(Math.min(1F, FearState.voidFall.p(pt) * 10F), FearState.voidFall.rem(pt) * 6F);
        float cx = w / 2F, cy = h / 2F;
        vignette(g, w, h, 0, 0.95F * a);
        for (int i = 0; i < 46; i++) {
            float ang = hash(i, 1) * 6.283F, sp = ((t * (0.04F + hash(i, 2) * 0.05F) + hash(i, 3)) % 1F);
            float r0 = 20 + sp * sp * w * 0.7F, r1 = r0 + 20 + sp * 160;
            line(g, cx + Mth.cos(ang) * r0, cy + Mth.sin(ang) * r0, cx + Mth.cos(ang) * r1, cy + Mth.sin(ang) * r1, 1.5F + sp * 2F, argb(0.5F * a * FearCompat.glow() * sp, 0xCCD0E0));
        }
    }

    private static void blackout(GuiGraphics g, int w, int h, float pt, float t) {
        float p = FearState.lights.p(pt);
        float a = (p < 0.06F || p > 0.94F) ? (hash((int) (t * 1.5F), 77) < 0.5F ? 0.2F : 1F) : 1F;
        g.fill(0, 0, w, h, argb(0.97F * a, 0));
    }

    private static void tear(GuiGraphics g, int w, int h, float pt, float t) {
        float k = FearState.tear.rem(pt);
        for (int i = 0; i < 9; i++) {
            int y = (int) (hash(i, (int) (t * 2)) * h), th = 3 + (int) (hash(i, 5) * 26), off = (int) ((hash(i, (int) (t * 2) + 3) - 0.5F) * 90 * k);
            g.fill(Math.max(0, off), y, w + Math.min(0, off), y + th, argb(0.35F * k, hash(i, 6) < 0.5F ? 0xFF1030 : 0x20E0FF));
            g.fill(0, y + th, w, y + th + 1, argb(0.7F * k, 0));
        }
        g.fill(0, 0, 4, h, argb(0.3F * k, 0xFF2030));
        g.fill(w - 4, 0, w, h, argb(0.3F * k, 0x20E0FF));
    }

    private static void whisper(GuiGraphics g, int w, int h, float t) {
        Font f = Minecraft.getInstance().font;
        float k = FearState.whisper.rem(0) * 0.45F;
        for (int i = 0; i < 6; i++) {
            float x = hash(i, (int) (t / 6)) * w * 0.8F + w * 0.1F, y = hash(i, (int) (t / 6) + 7) * h * 0.8F + h * 0.1F;
            g.pose().pushPose();
            g.pose().translate(x, y, 0);
            g.pose().scale(1.5F + hash(i, 2), 1.5F + hash(i, 2), 1F);
            g.drawString(f, FearState.whisperName, 0, 0, argb(k * hash((int) (t / 3), i), 0xA01010), false);
            g.pose().popPose();
        }
    }

    private static void speak(GuiGraphics g, int w, int h, float pt, float t, Font f) {
        String s = FearState.speakText;
        float p = FearState.speak.p(pt), fade = Math.min(Math.min(1F, p * 6F), FearState.speak.rem(pt) * 4F);
        int n = s.length();
        float scale = 2.6F, total = f.width(s) * scale;
        float x0 = w / 2F - total / 2F, y0 = h * 0.38F;
        float cx = x0;
        for (int i = 0; i < n; i++) {
            String c = String.valueOf(s.charAt(i));
            float cw = f.width(c) * scale;
            float jx = (hash((int) (t * 2), i) - 0.5F) * 5F * (1F + p), jy = (hash((int) (t * 2), i + 40) - 0.5F) * 6F;
            float drift = Mth.sin(t * 0.07F + i * 0.8F) * 9F - p * 40F * hash(i, 3);
            float al = fade * (0.55F + 0.45F * hash((int) (t * 3), i + 9));
            g.pose().pushPose();
            g.pose().translate(cx + jx, y0 + jy + drift, 0);
            g.pose().scale(scale, scale, 1F);
            g.drawString(f, c, 0, 0, argb(al * 0.5F, 0x600008), false);
            g.pose().translate(0.35F, 0.2F, 0);
            g.drawString(f, c, 0, 0, argb(al, 0xD8D0C8), false);
            g.pose().popPose();
            cx += cw;
        }
    }

    private static void eyes(GuiGraphics g, int w, int h, float pt, float t, Minecraft mc) {
        float yaw = mc.player.getViewYRot(pt), pitch = mc.player.getViewXRot(pt);
        float k = Math.min(1F, Math.min(FearState.eyes.p(pt) * 8F, FearState.eyes.rem(pt) * 6F));
        float fov = mc.options.fov().get();
        float ppd = w / (fov * 1.35F);
        for (float[] e : FearState.eyePairs) {
            float dx = Mth.wrapDegrees(e[0] - yaw), dy = e[1] - pitch;
            if (Math.abs(dx) > fov) continue;
            float x = w / 2F + dx * ppd, y = h / 2F + dy * ppd;
            float blink = ((t + e[3] * 100) % 90 < 5) ? 0.1F : 1F;
            float sz = e[2] * (h / 540F);
            for (int s = -1; s <= 1; s += 2) {
                float ex = x + s * sz * 5;
                disc(g, ex, y, sz * 5, sz * 3, argb(0.9F * k * blink * FearCompat.glow(), 0xFFF2C0), argb(0F, 0xFF3010), 12, true);
                disc(g, ex, y, sz * 1.8F, sz * 1.4F, argb(k * blink, 0xFFFFFF), argb(0.6F * k * blink, 0xFFE090), 10, false);
            }
        }
    }

    private static void fallingItems(GuiGraphics g) {
        int w = g.guiWidth(), h = g.guiHeight();
        for (FearState.Falling f : FearState.items) {
            g.pose().pushPose();
            g.pose().translate(f.x + w / 2F - 90, f.y + h - 20, 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(f.rot));
            g.pose().scale(1.4F, 1.4F, 1F);
            g.renderItem(f.stack, -8, -8);
            g.pose().popPose();
        }
    }

    // ------------------------------------------------------------------ objectives and meters

    private static void objectives(GuiGraphics g, int w, int h, Font f) {
        int fl = FearState.flags;
        int y = 8;
        if ((fl & FearNet.F_METER) != 0) {
            int bw = 70, bx = w / 2 - bw / 2;
            g.fill(bx - 1, 5, bx + bw + 1, 9, argb(0.5F, 0));
            float k = FearState.fear / 100F;
            g.fill(bx, 6, bx + (int) (bw * k), 8, argb(0.85F, k > 0.66F ? 0xC02020 : k > 0.33F ? 0xC08020 : 0x70A070));
            y = 14;
        }
        if ((fl & FearNet.F_FAKE) != 0) {
            g.drawString(f, Component.translatable("infinity.fear.obj.fake.title"), w - 150, y, argb(0.85F, 0xE0D8C8), true);
            g.drawString(f, Component.translatable("infinity.fear.obj.fake.1"), w - 150, y + 11, argb(0.7F, 0xB8B0A0), false);
            g.drawString(f, Component.translatable("infinity.fear.obj.fake.2"), w - 150, y + 21, argb(0.7F, 0xB8B0A0), false);
            y += 34;
        }
        if ((fl & FearNet.F_REAL) != 0) {
            g.drawString(f, Component.translatable("infinity.fear.obj.real.title"), w - 150, y, argb(0.9F, 0xF0E0A0), true);
            g.drawString(f, Component.translatable("infinity.fear.obj.real.candles", FearState.candles, Math.max(5, FearState.candlesTotal)), w - 150, y + 11, argb(0.8F, 0xE8D090), false);
        }
    }

    private static void watchBar(GuiGraphics g, int w, int h, Font f) {
        String key = FearState.watchMode == 3 ? "infinity.fear.watch.audience" : FearState.watchMode == 1 ? "infinity.fear.watch.pov" : "infinity.fear.watch.orbit";
        Component c = Component.translatable(key, FearState.watchName);
        g.fill(0, 0, w, 14, argb(0.55F, 0));
        g.drawCenteredString(f, c, w / 2, 3, 0xFFD8C8C8);
        if (FearState.replay != null) g.drawCenteredString(f, Component.translatable("infinity.fear.watch.replay"), w / 2, 18, 0xFFE0A050);
    }

    private FearHud() {}
}
