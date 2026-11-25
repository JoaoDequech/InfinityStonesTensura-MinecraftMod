package com.infinitystones.tensura.shard;

/** Client-side payload handlers behind one door, so the server never loads client classes. */
final class ShardClientBridge {
    static void list(ShardNet.ListMsg m) { ShardClientState.list(m); }
    static void wheel(ShardNet.WheelMsg m) { ShardClientState.pages(m); }
    static void fx(ShardNet.FxMsg m) { com.infinitystones.tensura.skill.ability.extra.fx.ShardFxClient.accept(m); }
    private ShardClientBridge() {}
}
