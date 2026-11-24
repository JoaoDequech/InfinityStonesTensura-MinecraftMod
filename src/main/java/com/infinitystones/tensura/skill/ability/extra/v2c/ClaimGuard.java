package com.infinitystones.tensura.skill.ability.extra.v2c;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.UUID;

/** Open Parties and Claims check for permanent block/biome changes (same reflection style as compat.ClaimsCompat). */
final class ClaimGuard {
    private static Method apiGet, claimsManager, partyManager, claimGet, claimOwner, partyByMember, partyMember;
    private static boolean broken;

    /** False when the chunk is claimed by somebody the player is not allied with. */
    static boolean canModify(ServerPlayer p, ServerLevel level, ChunkPos cp) {
        if (broken || !ModList.get().isLoaded("openpartiesandclaims")) return true;
        try {
            if (apiGet == null) {
                Class<?> api = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
                apiGet = api.getMethod("get", net.minecraft.server.MinecraftServer.class);
                claimsManager = api.getMethod("getServerClaimsManager");
                partyManager = api.getMethod("getPartyManager");
                claimGet = Class.forName("xaero.pac.common.claims.api.IClaimsManagerAPI")
                        .getMethod("get", net.minecraft.resources.ResourceLocation.class, BlockPos.class);
                claimOwner = Class.forName("xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI").getMethod("getPlayerId");
                partyByMember = Class.forName("xaero.pac.common.server.parties.party.api.IPartyManagerAPI").getMethod("getPartyByMember", UUID.class);
                partyMember = Class.forName("xaero.pac.common.parties.party.api.IPartyAPI").getMethod("getMemberInfo", UUID.class);
            }
            Object api = apiGet.invoke(null, level.getServer());
            Object claim = claimGet.invoke(claimsManager.invoke(api), level.dimension().location(), cp.getMiddleBlockPosition(64));
            if (claim == null) return true;
            UUID owner = (UUID) claimOwner.invoke(claim);
            if (owner == null || owner.equals(p.getUUID())) return true;
            Object party = partyByMember.invoke(partyManager.invoke(api), owner);
            return party != null && partyMember.invoke(party, p.getUUID()) != null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            broken = true;
            return false;
        }
    }

    private ClaimGuard() {}
}
