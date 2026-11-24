package com.infinitystones.tensura.skill.ability.extra.v2c;

final class V2CClientBridge {
    static void accept(V2CNet.Msg m) {
        switch (m.kind()) {
            case V2CNet.SKY, V2CNet.SKY_END -> com.infinitystones.tensura.skill.ability.extra.v2c.client.SkyClient.accept(m);
            case V2CNet.GENESIS, V2CNet.GENESIS_END -> com.infinitystones.tensura.skill.ability.extra.v2c.client.GenesisClient.accept(m);
            default -> com.infinitystones.tensura.skill.ability.extra.v2c.client.SoulClient.accept(m);
        }
    }

    private V2CClientBridge() {}
}
