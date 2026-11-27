package com.infinitystones.tensura.shard;

/** Client-side payload handlers behind one door, so the server never loads client classes. */
final class ShardClientBridge {
    static void list(ShardNet.ListMsg m) { ShardClientState.list(m); }
    static void wheel(ShardNet.WheelMsg m) { ShardClientState.pages(m); }
    static void belt(ShardNet.BeltMsg m) { ShardClientState.belt(m); }
    static void burst(ShardNet.BurstMsg m) { com.infinitystones.tensura.skill.ability.extra.fx.ShardFxClient.burst(m); }
    static void fx(ShardNet.FxMsg m) { com.infinitystones.tensura.skill.ability.extra.fx.ShardFxClient.accept(m); }
    private ShardClientBridge() {}
}
