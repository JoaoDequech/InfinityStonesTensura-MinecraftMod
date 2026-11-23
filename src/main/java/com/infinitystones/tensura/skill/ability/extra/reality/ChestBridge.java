package com.infinitystones.tensura.skill.ability.extra.reality;

/** Keeps client classes out of the server's class loading: only touched from client-side payload handlers. */
final class ChestBridge {
    static void open(ChestNet.Open msg) { com.infinitystones.tensura.skill.ability.extra.reality.client.ChestClient.open(msg); }
    static void holo(ChestNet.Holo msg) { com.infinitystones.tensura.skill.ability.extra.reality.client.ChestHolo.accept(msg); }
    private ChestBridge() {}
}
