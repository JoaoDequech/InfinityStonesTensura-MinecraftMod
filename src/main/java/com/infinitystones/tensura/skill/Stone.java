package com.infinitystones.tensura.skill;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** The six stones, the items that carry each one, and the original Palladium power id used by tree requirements. */
public enum Stone {
    MIND("mind", "infinity:mind_stone", "infinity:mind_stone", "infinity:scepter", "infinity:cosmi-rod_mind"),
    POWER("power", "infinity:power_stone_passive", "infinity:power_stone", "infinity:orb", "infinity:cosmi-rod_power"),
    SPACE("space", "infinity:space_stone", "infinity:space_stone", "infinity:tesseract", "infinity:cosmi-rod_space"),
    REALITY("reality", "infinity:reality_stone", "infinity:reality_stone", "infinity:aether", "infinity:cosmi-rod_reality"),
    SOUL("soul", "infinity:soul_stone", "infinity:soul_stone", "infinity:cosmi-rod_soul"),
    TIME("time", "infinity:time_stone", "infinity:time_stone", "infinity:eye_of_agamotto", "infinity:cosmi-rod_time");

    public final String key;
    /** Original Palladium power id, as referenced by "has_power" tree requirements. */
    public final String originalPower;
    private final List<ResourceLocation> carrierIds;
    private Set<Item> carriers;
    private ResourceLocation skillId;
    private static Map<Item, Integer> carrierMasks;
    private static final int ALL = (1 << 6) - 1;

    Stone(String key, String originalPower, String... carriers) {
        this.key = key;
        this.originalPower = originalPower;
        this.carrierIds = java.util.Arrays.stream(carriers).map(ResourceLocation::parse).toList();
    }

    /** Signature colour (ARGB) used by the wheel, its particles and the in-world cast burst. */
    public int color() {
        return switch (this) {
            case MIND -> 0xFFF2D02B; case POWER -> 0xFFA43CE6; case SPACE -> 0xFF2F8CFF;
            case REALITY -> 0xFFE22B3B; case SOUL -> 0xFFF28A1A; case TIME -> 0xFF2BD56A;
        };
    }

    public ResourceLocation skillId() {
        ResourceLocation id = skillId;
        if (id == null) skillId = id = InfinityStones.id(key + "_stone");
        return id;
    }

    public Set<Item> carriers() {
        if (carriers == null) carriers = carrierIds.stream().map(BuiltInRegistries.ITEM::get).collect(Collectors.toUnmodifiableSet());
        return carriers;
    }

    /** Item → bit mask of the stones it carries (an item may carry several). Built on first use, after registries are frozen. */
    private static Map<Item, Integer> carrierMasks() {
        Map<Item, Integer> m = carrierMasks;
        if (m == null) {
            m = new IdentityHashMap<>();
            for (Stone s : values()) for (Item i : s.carriers()) m.merge(i, 1 << s.ordinal(), (a, b) -> a | b);
            carrierMasks = m;
        }
        return m;
    }

    /** First stone (declaration order) that this stack carries as a non-emptied carrier item, or null. */
    public static Stone carrierOf(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Integer m = carrierMasks().get(stack.getItem());
        if (m == null || com.infinitystones.tensura.gauntlet.ArtifactSwap.isEmptied(stack)) return null;
        return values()[Integer.numberOfTrailingZeros(m)];
    }

    /** Stones (bit per ordinal) that this one stack holds: carrier items (unless emptied) and gauntlet sockets. */
    private static int stackMask(ItemStack s) {
        if (s.isEmpty()) return 0;
        int m = 0;
        Integer carried = carrierMasks().get(s.getItem());
        if (carried != null && !com.infinitystones.tensura.gauntlet.ArtifactSwap.isEmptied(s)) m = carried;
        if (GauntletStones.isGauntlet(s)) m |= GauntletStones.mask(s);
        return m;
    }

    /** Bit mask of every stone the player carries (inventory, hands, armour, accessory slots) in one pass over the slots. */
    public static int heldMask(Player player) {
        int m = 0;
        Inventory inv = player.getInventory();
        for (ItemStack s : inv.items) m |= stackMask(s);
        for (ItemStack s : inv.offhand) m |= stackMask(s);
        for (ItemStack s : inv.armor) m |= stackMask(s);
        if (m == ALL) return m;
        int[] acc = {m};
        CuriosCompat.forEach(player, s -> acc[0] |= stackMask(s));
        return acc[0];
    }

    /** Carrier item (stone, artifact, cosmi-rod) that is not emptied (an emptied Orb does not count). */
    public boolean isCarrier(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return carriers().contains(stack.getItem()) && !com.infinitystones.tensura.gauntlet.ArtifactSwap.isEmptied(stack);
    }

    /** True when the player carries this stone: inventory, hands, armour, or any Curios slot. */
    public boolean isHeldBy(Player player) {
        int bit = 1 << ordinal();
        Inventory inv = player.getInventory();
        for (ItemStack s : inv.items) if ((stackMask(s) & bit) != 0) return true;
        for (ItemStack s : inv.offhand) if ((stackMask(s) & bit) != 0) return true;
        for (ItemStack s : inv.armor) if ((stackMask(s) & bit) != 0) return true;
        return CuriosCompat.has(player, s -> (stackMask(s) & bit) != 0);
    }

    /** The stone itself / one of its carriers, or an Infinity Gauntlet with this stone set in it. */
    public boolean holds(ItemStack s) {
        return isCarrier(s) || GauntletStones.has(s, this);
    }

    public static Stone byOriginalPower(String powerId) {
        for (Stone s : values()) if (s.originalPower.equals(powerId)) return s;
        return null;
    }
}
