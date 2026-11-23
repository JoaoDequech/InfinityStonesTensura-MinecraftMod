package com.infinitystones.tensura.skill.ability.extra.space;

import com.infinitystones.tensura.skill.ability.extra.space.client.GatherClient;

/** Keeps client classes out of the server's class loading: only touched from client-side payload handlers. */
final class GatherBridge {
    static void open(GatherNet.Open msg) { GatherClient.open(msg); }
    static void state(GatherNet.State msg) { GatherClient.accept(msg); }
    static void barrier(GatherNet.Barrier msg) { GatherClient.accept(msg); }
    static void hand(GatherNet.Hand msg) { GatherClient.accept(msg); }
    private GatherBridge() {}
}
