package com.infinitystones.tensura.registry;

import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Materials that the original content took from Palladium (titanium, vibranium, circuits, fabrics).
 * Palladium is GPL-3 and is not in the target modpack, so these are clean-room equivalents with new art.
 */
public final class ModMaterials {
    // Titanium: overworld ore, mirrors vanilla iron progression (stone pickaxe, smelts to infinity:titanium_ingot).
    public static final DeferredBlock<Block> TITANIUM_ORE = ModBlocks.BLOCKS.register("titanium_ore", () -> new DropExperienceBlock(UniformInt.of(0, 2),
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3F, 3F).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> DEEPSLATE_TITANIUM_ORE = ModBlocks.BLOCKS.register("deepslate_titanium_ore", () -> new DropExperienceBlock(UniformInt.of(0, 2),
            BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(4.5F, 3F).sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops()));
    public static final DeferredBlock<Block> RAW_TITANIUM_BLOCK = ModBlocks.BLOCKS.registerSimpleBlock("raw_titanium_block",
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(5F, 6F).requiresCorrectToolForDrops());
    // Vibranium: rare deep ore (diamond tier), blast/smelts to vibranium_ingot.
    public static final DeferredBlock<Block> DEEPSLATE_VIBRANIUM_ORE = ModBlocks.BLOCKS.register("deepslate_vibranium_ore", () -> new DropExperienceBlock(UniformInt.of(3, 7),
            BlockBehaviour.Properties.of().mapColor(MapColor.DEEPSLATE).strength(5.5F, 9F).sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops()));

    public static final DeferredItem<BlockItem> TITANIUM_ORE_ITEM = ModItems.ITEMS.registerSimpleBlockItem("titanium_ore", TITANIUM_ORE);
    public static final DeferredItem<BlockItem> DEEPSLATE_TITANIUM_ORE_ITEM = ModItems.ITEMS.registerSimpleBlockItem("deepslate_titanium_ore", DEEPSLATE_TITANIUM_ORE);
    public static final DeferredItem<BlockItem> RAW_TITANIUM_BLOCK_ITEM = ModItems.ITEMS.registerSimpleBlockItem("raw_titanium_block", RAW_TITANIUM_BLOCK);
    public static final DeferredItem<BlockItem> DEEPSLATE_VIBRANIUM_ORE_ITEM = ModItems.ITEMS.registerSimpleBlockItem("deepslate_vibranium_ore", DEEPSLATE_VIBRANIUM_ORE);

    public static final DeferredItem<Item> RAW_TITANIUM = simple("raw_titanium", Rarity.COMMON);
    public static final DeferredItem<Item> RAW_VIBRANIUM = simple("raw_vibranium", Rarity.RARE);
    public static final DeferredItem<Item> VIBRANIUM_INGOT = simple("vibranium_ingot", Rarity.RARE);
    public static final DeferredItem<Item> LEAD_CIRCUIT = simple("lead_circuit", Rarity.COMMON);
    public static final DeferredItem<Item> QUARTZ_CIRCUIT = simple("quartz_circuit", Rarity.COMMON);
    public static final DeferredItem<Item> VIBRANIUM_CIRCUIT = simple("vibranium_circuit", Rarity.RARE);
    public static final DeferredItem<Item> VIBRANIUM_FLUX_CAPACITOR = simple("vibranium_flux_capacitor", Rarity.EPIC);
    public static final DeferredItem<Item> YELLOW_FABRIC = simple("yellow_fabric", Rarity.COMMON);
    public static final DeferredItem<Item> BLUE_FABRIC = simple("blue_fabric", Rarity.COMMON);
    public static final DeferredItem<Item> BLACK_FABRIC = simple("black_fabric", Rarity.COMMON);

    private static DeferredItem<Item> simple(String id, Rarity rarity) {
        return ModItems.ITEMS.register(id, () -> new Item(new Item.Properties().rarity(rarity)));
    }

    /** Forces class loading so the static registrations above reach the DeferredRegisters before they are attached. */
    public static void init() {}

    private ModMaterials() {}
}
