package com.infinitystones.tensura.wheel;

import java.util.List;

/** Latest wheel state received from the server (safe to load on a dedicated server: plain data only). */
public final class WheelClientState {
    private static volatile WheelPayloads.State state = new WheelPayloads.State(List.of(), "", 0);
    private static volatile long receivedAt;

    static void accept(WheelPayloads.State s) {
        state = s;
        receivedAt = System.currentTimeMillis();
        com.infinitystones.tensura.skill.StoneTier.setClientMask(s.tiers());
    }

    public static WheelPayloads.State get() { return state; }
    public static long receivedAt() { return receivedAt; }

    private WheelClientState() {}
}
