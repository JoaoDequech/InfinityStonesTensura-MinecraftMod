package com.infinitystones.tensura.skill.ability.extra.fear.client;

import com.infinitystones.tensura.skill.ability.extra.fear.FearNet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Everything the client knows about the nightmare it is in (or the one it is watching). Cleared on logout. */
final class FearState {
    static final class Timer {
        int left, total;
        void set(int n) { left = total = n; }
        boolean on() { return left > 0; }
        void tick() { if (left > 0) left--; }
        /** 0 at the start, 1 at the end. */
        float p(float pt) { return total <= 0 ? 1F : Mth.clamp((total - left + pt) / total, 0F, 1F); }
        float rem(float pt) { return 1F - p(pt); }
        void clear() { left = total = 0; }
    }

    static final Timer enter = new Timer(), exit = new Timer(), jump = new Timer(), flip = new Timer(), pulse = new Timer(),
            lights = new Timer(), lava = new Timer(), voidFall = new Timer(), inv = new Timer(), wake = new Timer(), whisper = new Timer(),
            loudFlash = new Timer(), flashOff = new Timer(), flick = new Timer(), tear = new Timer(), heart = new Timer(), eyes = new Timer(),
            speak = new Timer(), lightning = new Timer(), wakeFlash = new Timer();
    private static final Timer[] ALL = {enter, exit, jump, flip, pulse, lights, lava, voidFall, inv, wake, whisper, loudFlash, flashOff, flick, tear, heart, eyes, speak, lightning, wakeFlash};

    static int jumpVariant, seed;
    static float shakeAmp; static int shakeLeft, shakeTotal;
    static String whisperName = "", speakText = "";
    static final List<float[]> whisperSpots = new ArrayList<>();
    static final List<Falling> items = new ArrayList<>();
    static final List<float[]> eyePairs = new ArrayList<>(); // yaw, pitch, size, phase

    static final class Falling { ItemStack stack; float x, y, vx, vy, rot, vr; }

    // ---- victim side
    static boolean victim, self, flashOn = true;
    static int fog = 50, dark = 85, sky, weather, fear, flags, battery = 100, candles, candlesTotal;
    static long ticks;

    // ---- caster side
    static int watchMode, watchEntity;
    static String watchName = "";
    static float orbitDist = 7F;
    static Entity dummy;
    static Vec3 orbitPos;
    static float[] replay; static int replayTick;
    static FearNet.Panel panel;
    static final List<CompoundTag> presets = new ArrayList<>();
    static boolean fakeDeath, fakeLost;

    static void shake(float amp, int ticks) {
        float cur = shakeLeft <= 0 ? 0 : shakeAmp * shakeLeft / Math.max(1, shakeTotal);
        if (amp >= cur) { shakeAmp = amp; shakeLeft = shakeTotal = Math.max(1, ticks); }
    }

    static float shakeNow(float pt) { return shakeLeft <= 0 ? 0F : shakeAmp * Math.max(0F, (shakeLeft - pt) / Math.max(1, shakeTotal)); }

    static void tick() {
        ticks++;
        for (Timer t : ALL) t.tick();
        if (shakeLeft > 0) shakeLeft--;
        for (var it = items.iterator(); it.hasNext(); ) {
            Falling f = it.next();
            f.vy += 0.9F; f.x += f.vx; f.y += f.vy; f.rot += f.vr;
            if (f.y > 700) it.remove();
        }
        if (!eyes.on()) eyePairs.clear();
    }

    static void clear() {
        for (Timer t : ALL) t.clear();
        shakeLeft = 0; whisperSpots.clear(); items.clear(); eyePairs.clear();
        victim = self = false; flashOn = true; watchMode = 0; dummy = null; orbitPos = null; replay = null; panel = null;
        fakeDeath = fakeLost = false;
        fear = 0; flags = 0; battery = 100; candles = candlesTotal = 0;
        FearWorld.clear();
    }

    private FearState() {}
}
