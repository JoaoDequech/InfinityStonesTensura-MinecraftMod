package com.infinitystones.tensura.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Open Parties and Claims. The only thing claims stop is a physical block change inside a claim the caster neither owns
 * nor shares a party with ({@link #canModify}). Damage, teleports, possession, effects and control ignore claims and parties.
 * Reflection only, so the mod runs with or without OPAC; when the lookup fails, edits are allowed.
 */
public final class ClaimsCompat {
    private static Method apiGet, claimsManager, partyManager, claimGet, claimOwner, partyByMember, partyMember;
    private static boolean broken;

    private static boolean init() {
        if (broken || !ModList.get().isLoaded("openpartiesandclaims")) return false;
        try {
            if (apiGet == null) {
                Class<?> api = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
                Method get = api.getMethod("get", MinecraftServer.class);
                Method cm = api.getMethod("getServerClaimsManager");
                Method pm = api.getMethod("getPartyManager");
                Method cg = Class.forName("xaero.pac.common.claims.api.IClaimsManagerAPI").getMethod("get", ResourceLocation.class, BlockPos.class);
                Method co = Class.forName("xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI").getMethod("getPlayerId");
                Method pbm = Class.forName("xaero.pac.common.server.parties.party.api.IPartyManagerAPI").getMethod("getPartyByMember", UUID.class);
                partyMember = Class.forName("xaero.pac.common.parties.party.api.IPartyAPI").getMethod("getMemberInfo", UUID.class);
                claimsManager = cm; partyManager = pm; claimGet = cg; claimOwner = co; partyByMember = pbm; apiGet = get;
            }
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            broken = true;
            return false;
        }
    }

    /** True when Open Parties and Claims is installed: powers then hit players with an owner-less damage source so its own PvP protection never triggers. */
    public static boolean opacLoaded() { return ModList.get().isLoaded("openpartiesandclaims"); }

    /**
     * Kept for the powers that still call it: claims no longer protect anybody from harm, so this is always false.
     * @deprecated claims only guard block edits, see {@link #canModify}
     */
    @Deprecated
    public static boolean isProtected(ServerPlayer victim, ServerPlayer attacker) { return false; }

    /** Same Open Parties and Claims party. Only for BENEFICIAL effects (buffs, links); harmful ones never look at parties. */
    public static boolean sameParty(ServerPlayer a, ServerPlayer b) {
        if (a == b) return true;
        if (!init()) return false;
        try {
            Object party = partyByMember.invoke(partyManager.invoke(apiGet.invoke(null, a.server)), a.getUUID());
            return party != null && partyMember.invoke(party, b.getUUID()) != null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            broken = true;
            return false;
        }
    }

    /** Does the player belong to any OPAC party? */
    public static boolean hasParty(ServerPlayer p) {
        if (!init()) return false;
        try {
            return partyByMember.invoke(partyManager.invoke(apiGet.invoke(null, p.server)), p.getUUID()) != null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            broken = true;
            return false;
        }
    }

    /** May this player change the block at pos? False only when pos lies in a claim owned by somebody outside the player's party. */
    public static boolean canModify(ServerPlayer actor, ServerLevel level, BlockPos pos) {
        return canModify(actor.getUUID(), level, pos);
    }

    public static boolean canModify(UUID actor, ServerLevel level, BlockPos pos) {
        if (!init()) return true;
        try {
            Object api = apiGet.invoke(null, level.getServer());
            Object claim = claimGet.invoke(claimsManager.invoke(api), level.dimension().location(), pos);
            if (claim == null) return true;
            UUID owner = (UUID) claimOwner.invoke(claim);
            if (owner == null || owner.equals(actor)) return true;
            Object party = partyByMember.invoke(partyManager.invoke(api), owner);
            return party != null && partyMember.invoke(party, actor) != null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return true;
        }
    }

    private ClaimsCompat() {}
}
