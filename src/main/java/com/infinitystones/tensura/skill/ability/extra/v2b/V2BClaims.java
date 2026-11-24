package com.infinitystones.tensura.skill.ability.extra.v2b;

import com.infinitystones.tensura.compat.ClaimsCompat;
import net.minecraft.server.level.ServerPlayer;

/** Party lookups for the (beneficial) Telepathic Link; claims are not involved. */
final class V2BClaims {
    static boolean hasParty(ServerPlayer p) { return ClaimsCompat.hasParty(p); }

    static boolean sameParty(ServerPlayer a, ServerPlayer b) { return ClaimsCompat.sameParty(a, b); }

    private V2BClaims() {}
}
