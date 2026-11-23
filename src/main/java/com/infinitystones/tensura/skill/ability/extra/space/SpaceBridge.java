package com.infinitystones.tensura.skill.ability.extra.space;

/** Keeps client classes out of the server's class loading: only touched from client-side payload handlers. */
final class SpaceBridge {
    static void heist(SpaceNet.HeistState msg) { com.infinitystones.tensura.skill.ability.extra.space.client.HeistClient.accept(msg); }
    static void abyss(SpaceNet.AbyssPanel msg) { com.infinitystones.tensura.skill.ability.extra.space.client.AbyssClient.accept(msg); }
    private SpaceBridge() {}
}
