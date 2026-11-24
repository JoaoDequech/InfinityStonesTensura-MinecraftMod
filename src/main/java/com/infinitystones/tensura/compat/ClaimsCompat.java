package com.infinitystones.tensura.compat;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Open Parties and Claims: players standing in a claim are protected from damage dealt by Infinity Stone bearers who
 * are not the claim owner or in the owner's party. Non-damaging powers (teleport, possession, control) are untouched.
 * Reflection only, so the mod runs with or without OPAC.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class ClaimsCompat {
    private static Method apiGet, claimsManager, partyManager, claimGet, claimOwner, partyByMember, partyMember;
    private static boolean broken;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDamage(LivingIncomingDamageEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer victim)) return;
        Entity src = e.getSource().getEntity();
        if (!(src instanceof ServerPlayer attacker) || attacker == victim) return;
        if (!bearer(attacker)) return;
        if (isProtected(victim, attacker)) e.setCanceled(true);
    }

    private static boolean bearer(ServerPlayer p) {
        for (Stone s : Stone.values()) if (s.isHeldBy(p)) return true;
        return false;
    }

    /** True when the victim stands in a claim whose owner is neither the attacker nor in the attacker's party. */
    public static boolean isProtected(ServerPlayer victim, ServerPlayer attacker) {
        if (broken || !ModList.get().isLoaded("openpartiesandclaims")) return false;
        try {
            if (apiGet == null) {
                Class<?> api = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
                apiGet = api.getMethod("get", MinecraftServer.class);
                claimsManager = api.getMethod("getServerClaimsManager");
                partyManager = api.getMethod("getPartyManager");
                claimGet = Class.forName("xaero.pac.common.claims.api.IClaimsManagerAPI")
                        .getMethod("get", net.minecraft.resources.ResourceLocation.class, net.minecraft.core.BlockPos.class);
                claimOwner = Class.forName("xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI").getMethod("getPlayerId");
                partyByMember = Class.forName("xaero.pac.common.server.parties.party.api.IPartyManagerAPI").getMethod("getPartyByMember", UUID.class);
                partyMember = Class.forName("xaero.pac.common.parties.party.api.IPartyAPI").getMethod("getMemberInfo", UUID.class);
            }
            Object api = apiGet.invoke(null, victim.server);
            Object claim = claimGet.invoke(claimsManager.invoke(api), victim.level().dimension().location(), victim.blockPosition());
            if (claim == null) return false;
            UUID owner = (UUID) claimOwner.invoke(claim);
            if (owner == null || owner.equals(attacker.getUUID())) return false;
            Object party = partyByMember.invoke(partyManager.invoke(api), owner);
            return party == null || partyMember.invoke(party, attacker.getUUID()) == null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            broken = true;
            InfinityStones.LOGGER.warn("[infinity] Open Parties and Claims integration disabled: {}", ex.toString());
            return false;
        }
    }

    /** v2d: may this player change the block at pos? False inside a claim owned by somebody outside the player's party. */
    public static boolean canModify(ServerPlayer actor, net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos pos) {
        if (broken || !ModList.get().isLoaded("openpartiesandclaims")) return true;
        try {
            if (apiGet == null) {
                Class<?> api = Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
                apiGet = api.getMethod("get", MinecraftServer.class);
                claimsManager = api.getMethod("getServerClaimsManager");
                partyManager = api.getMethod("getPartyManager");
                claimGet = Class.forName("xaero.pac.common.claims.api.IClaimsManagerAPI")
                        .getMethod("get", net.minecraft.resources.ResourceLocation.class, net.minecraft.core.BlockPos.class);
                claimOwner = Class.forName("xaero.pac.common.claims.player.api.IPlayerChunkClaimAPI").getMethod("getPlayerId");
                partyByMember = Class.forName("xaero.pac.common.server.parties.party.api.IPartyManagerAPI").getMethod("getPartyByMember", UUID.class);
                partyMember = Class.forName("xaero.pac.common.parties.party.api.IPartyAPI").getMethod("getMemberInfo", UUID.class);
            }
            Object api = apiGet.invoke(null, level.getServer());
            Object claim = claimGet.invoke(claimsManager.invoke(api), level.dimension().location(), pos);
            if (claim == null) return true;
            UUID owner = (UUID) claimOwner.invoke(claim);
            if (owner == null || owner.equals(actor.getUUID())) return true;
            Object party = partyByMember.invoke(partyManager.invoke(api), owner);
            return party != null && partyMember.invoke(party, actor.getUUID()) != null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
            broken = true;
            InfinityStones.LOGGER.warn("[infinity] Open Parties and Claims integration disabled: {}", ex.toString());
            return true;
        }
    }

    private ClaimsCompat() {}
}
