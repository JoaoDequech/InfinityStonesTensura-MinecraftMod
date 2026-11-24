package com.infinitystones.tensura.skill.ability.extra.v2b;

/** Keeps client classes out of the server's class loading: only touched from client-side payload handlers. */
final class V2BBridge {
    static void accept(V2BNet.Msg msg) { com.infinitystones.tensura.skill.ability.extra.v2b.client.V2BClient.accept(msg); }

    private V2BBridge() {}
}
