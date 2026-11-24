package com.infinitystones.tensura.skill.ability.extra.v2b;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.UUID;

/** Open Parties and Claims lookups by reflection (block claims and parties); everything is allowed when OPAC is absent. */
final class V2BClaims {
    private static Method apiGet, claimsManager, partyManager, claimGet, claimOwner, partyByMember, partyMember;
    private static boolean broken;

    private static boolean init() {
        if (broken || !ModList.get().isLoaded("openpartiesandclaims")) return false;
        try {
            if (apiGet == null) {
                Class<?> api = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
                apiGet = api.getMethod("get", MinecraftServer.class);
                claimsManager = api.getMethod("getServerClaimsManager");
                partyManager = api.getMethod("getPartyManager");
                claimGet = Class.forName("xaero.pac.common.claims.api.IClaimsManagerAPI")
                        .getMethod("get", net.minecraft.resources.ResourceLocation.class, BlockPos.class);
                claimOwner = Class.forName("xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI").getMethod("getPlayerId");
                partyByMember = Class.forName("xaero.pac.common.server.parties.party.api.IPartyManagerAPI").getMethod("getPartyByMember", UUID.class);
                partyMember = Class.forName("xaero.pac.common.parties.party.api.IPartyAPI").getMethod("getMemberInfo", UUID.class);
            }
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            broken = true;
            return false;
        }
    }

    /** True when pos lies in a claim whose owner is neither p nor in the owner's party. */
    static boolean claimedByOther(ServerPlayer p, ServerLevel l, BlockPos pos) {
        if (!init()) return false;
        try {
            Object api = apiGet.invoke(null, l.getServer());
            Object claim = claimGet.invoke(claimsManager.invoke(api), l.dimension().location(), pos);
            if (claim == null) return false;
            UUID owner = (UUID) claimOwner.invoke(claim);
            if (owner == null || owner.equals(p.getUUID())) return false;
            Object party = partyByMember.invoke(partyManager.invoke(api), owner);
            return party == null || partyMember.invoke(party, p.getUUID()) == null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            broken = true;
            return false;
        }
    }

    /** Does p belong to an OPAC party? */
    static boolean hasParty(ServerPlayer p) {
        if (!init()) return false;
        try {
            Object api = apiGet.invoke(null, p.server);
            return partyByMember.invoke(partyManager.invoke(api), p.getUUID()) != null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            broken = true;
            return false;
        }
    }

    static boolean sameParty(ServerPlayer a, ServerPlayer b) {
        if (!init()) return false;
        try {
            Object api = apiGet.invoke(null, a.server);
            Object party = partyByMember.invoke(partyManager.invoke(api), a.getUUID());
            return party != null && partyMember.invoke(party, b.getUUID()) != null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            broken = true;
            return false;
        }
    }

    private V2BClaims() {}
}
