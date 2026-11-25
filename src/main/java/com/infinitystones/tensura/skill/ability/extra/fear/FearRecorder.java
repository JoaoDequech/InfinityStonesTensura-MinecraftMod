package com.infinitystones.tensura.skill.ability.extra.fear;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Rolling camera recorder of one victim (a sample every 2 ticks, the last 20 s) that marks the scariest moments:
 * jumpscares, fake or real death, sprinting away, sudden turns. A highlight keeps 10 s before and 3 s after the mark.
 */
final class FearRecorder {
    static final int CAP = 200, BEFORE = 100, AFTER = 30, MAX_HL = 24;

    static final class Highlight {
        final String label; final int at; final float[] data;
        Highlight(String label, int at, float[] data) { this.label = label; this.at = at; this.data = data; }
    }

    private final float[] ring = new float[CAP * 5];
    private int head, count;
    final List<Highlight> list = new ArrayList<>();
    private final List<Object[]> open = new ArrayList<>(); // {label, at, float[] before, ArrayList<float[]> after}
    private int runStreak;
    private final java.util.Map<String, Integer> cooldown = new java.util.HashMap<>();

    /** Call every tick. */
    void tick(ServerPlayer v, int total, float fear) {
        if (total % 2 != 0) return;
        int i = head * 5;
        ring[i] = (float) v.getX(); ring[i + 1] = (float) v.getEyeY(); ring[i + 2] = (float) v.getZ(); ring[i + 3] = v.getYRot(); ring[i + 4] = v.getXRot();
        head = (head + 1) % CAP;
        count = Math.min(CAP, count + 1);
        for (var it = open.iterator(); it.hasNext(); ) {
            Object[] o = it.next();
            @SuppressWarnings("unchecked") ArrayList<float[]> after = (ArrayList<float[]>) o[3];
            after.add(new float[]{ring[i], ring[i + 1], ring[i + 2], ring[i + 3], ring[i + 4]});
            if (after.size() >= AFTER) { finish(o); it.remove(); }
        }
        if (count > 6) {
            float prevYaw = at(count - 4)[3], yaw = v.getYRot();
            if (Math.abs(Mth.wrapDegrees(yaw - prevYaw)) > 115F) mark("turn", total);
            float[] a = at(count - 1), b = at(count - 2);
            double sp = Math.hypot(a[0] - b[0], a[2] - b[2]);
            runStreak = sp > 0.5 ? runStreak + 1 : 0;
            if (runStreak > 12 && fear > 35F) { mark("run", total); runStreak = 0; }
        }
    }

    void mark(String label, int total) {
        if (cooldown.getOrDefault(label, -9999) > total - 160 || count < 4) return;
        cooldown.put(label, total);
        int n = Math.min(count, BEFORE);
        float[] before = last(n);
        open.add(new Object[]{label, total, before, new ArrayList<float[]>()});
    }

    /** The highlight is closed at once (the victim is about to leave: real death). */
    void markNow(String label, int total) {
        mark(label, total);
        for (Object[] o : new ArrayList<>(open)) if (label.equals(o[0])) { finish(o); open.remove(o); }
    }

    private void finish(Object[] o) {
        @SuppressWarnings("unchecked") ArrayList<float[]> after = (ArrayList<float[]>) o[3];
        float[] before = (float[]) o[2];
        float[] all = new float[before.length + after.size() * 5];
        System.arraycopy(before, 0, all, 0, before.length);
        for (int i = 0; i < after.size(); i++) System.arraycopy(after.get(i), 0, all, before.length + i * 5, 5);
        list.add(new Highlight("infinity.fear.hl." + o[0], (Integer) o[1], all));
        while (list.size() > MAX_HL) list.remove(0);
    }

    private float[] at(int idxFromOldest) {
        int start = (head - count + CAP) % CAP;
        int i = ((start + idxFromOldest) % CAP) * 5;
        return new float[]{ring[i], ring[i + 1], ring[i + 2], ring[i + 3], ring[i + 4]};
    }

    private float[] last(int n) {
        float[] out = new float[n * 5];
        for (int k = 0; k < n; k++) System.arraycopy(at(count - n + k), 0, out, k * 5, 5);
        return out;
    }
}
