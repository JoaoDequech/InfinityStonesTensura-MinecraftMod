package com.infinitystones.tensura.skill.ability.extra.time;

/** Keeps client classes out of the server's class loading: only touched from client-side payload handlers. */
final class TimeClientBridge {
    static void accept(TimeNet.T msg) { com.infinitystones.tensura.skill.ability.extra.time.client.TimeClient.accept(msg); }
    private TimeClientBridge() {}
}
