package com.infinitystones.tensura.skill.ability.extra.fx;

/** Keeps client classes out of the server's class loading: only touched from client-side payload handlers. */
final class ClientBridge {
    static void fx(FxNet.Fx msg) { FxClient.accept(msg); }
    private ClientBridge() {}
}
