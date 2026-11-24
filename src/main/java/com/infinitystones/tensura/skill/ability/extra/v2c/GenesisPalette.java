package com.infinitystones.tensura.skill.ability.extra.v2c;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a biome is made of, for Genesis: classification into a profile, hand-tuned tables for the vanilla biomes that
 * need them, and an automatic palette read from the biome's own placed features (ores, disks, patches, springs, lakes,
 * trees...) so modded biomes bring their own blocks, plants, light sources, fluid and placeable features.
 */
final class GenesisPalette {
    enum Profile { NONE, AUTO, DESERT, BADLANDS, SNOW, MUSHROOM, JUNGLE, CHERRY, FOREST, PLAINS, SWAMP, TAIGA, SAVANNA,
        OCEAN, NETHER_WASTES, CRIMSON, WARPED, SOUL_VALLEY, BASALT, END, DEEP_DARK, LUSH, DRIPSTONE }

    /** Hand-tuned theme: weighted top blocks, filler, scatter blocks, small plants, lights. */
    record Hand(BlockState[] top, BlockState filler, BlockState[] scatter, BlockState[] plants, BlockState[] lights, boolean emu) {}

    private static final Set<String> NO_PLACE = Set.of("spring_feature", "lake", "ore", "scattered_ore", "replace_single_block", "geode",
            "monster_room", "fossil", "freeze_top_layer", "iceberg", "blue_ice", "end_spike", "end_island", "end_gateway",
            "bonus_chest", "void_start_platform", "kelp", "seagrass", "simple_random_selector_kelp", "sea_pickle", "coral_tree",
            "coral_mushroom", "coral_claw", "delta_feature", "basalt_pillar", "netherrack_replace_blobs", "basalt_columns",
            "desert_well", "no_op", "replace_blobs", "ice_spike", "forest_rock");
    private static final Set<String> NO_COLLECT = Set.of("tree", "huge_red_mushroom", "huge_brown_mushroom", "huge_fungus", "geode",
            "fossil", "monster_room", "bonus_chest", "end_spike", "iceberg");
    private static final Set<String> SKIP_KEYS = Set.of("placement", "dirt_provider", "decorators", "root_placer", "barrier", "target",
            "can_place_on", "replaceable", "may_replace", "blocks", "predicate", "tag", "allowed_placement", "prohibited_placement");
    private static final Set<String> KEEP_ORES = Set.of("coal_ore", "iron_ore", "copper_ore", "deepslate_coal_ore", "deepslate_iron_ore",
            "deepslate_copper_ore", "granite", "diorite", "andesite", "tuff", "gravel", "dirt", "clay", "sand", "red_sand", "magma_block",
            "packed_ice", "blue_ice", "snow_block", "coarse_dirt", "podzol", "mycelium", "calcite", "smooth_basalt", "moss_block", "mud");

    final Map<Block, BlockState> solids = new LinkedHashMap<>();
    final Map<Block, BlockState> plants = new LinkedHashMap<>();
    final Map<Block, BlockState> lights = new LinkedHashMap<>();
    final List<BlockState> fluids = new ArrayList<>();
    final List<Holder<ConfiguredFeature<?, ?>>> features = new ArrayList<>();
    BlockState stone;

    private GenesisPalette() {}

    // ------------------------------------------------------------------ classification

    static Profile classify(ResourceLocation id, Holder<Biome> b) {
        String p = id.getPath();
        boolean vanilla = "minecraft".equals(id.getNamespace());
        if (p.contains("the_void")) return Profile.NONE;
        if (p.equals("nether_wastes")) return Profile.NETHER_WASTES;
        if (p.equals("crimson_forest")) return Profile.CRIMSON;
        if (p.equals("warped_forest")) return Profile.WARPED;
        if (p.equals("soul_sand_valley")) return Profile.SOUL_VALLEY;
        if (p.equals("basalt_deltas")) return Profile.BASALT;
        if (p.equals("the_end") || p.startsWith("end_") || p.equals("small_end_islands")) return Profile.END;
        if (p.contains("deep_dark")) return Profile.DEEP_DARK;
        if (p.contains("lush_caves")) return Profile.LUSH;
        if (p.contains("dripstone")) return Profile.DRIPSTONE;
        if (!vanilla && (p.contains("cave") || p.contains("hollow") || p.contains("chasm") || p.contains("cavity") || p.contains("abyss"))) return Profile.AUTO;
        if (p.contains("desert")) return Profile.DESERT;
        if (p.contains("badlands") || p.contains("mesa")) return Profile.BADLANDS;
        if (p.contains("mushroom")) return Profile.MUSHROOM;
        if (p.contains("cherry")) return Profile.CHERRY;
        if (p.contains("snowy") || p.contains("frozen") || p.contains("ice") || p.contains("grove")) return Profile.SNOW;
        if (p.contains("jungle") || p.contains("bamboo")) return Profile.JUNGLE;
        if (p.contains("swamp") || p.contains("mangrove")) return Profile.SWAMP;
        if (p.contains("taiga")) return Profile.TAIGA;
        if (p.contains("savanna")) return Profile.SAVANNA;
        if (p.contains("forest") || p.contains("birch")) return Profile.FOREST;
        if (p.contains("ocean") || p.contains("river") || p.contains("beach")) return Profile.OCEAN;
        if (!vanilla) return Profile.AUTO;
        if (p.contains("nether")) return Profile.NETHER_WASTES;
        Biome bi = b.value();
        float t = bi.getBaseTemperature();
        if (!bi.hasPrecipitation() && t > 1.5F) return Profile.DESERT;
        if (t < 0.15F) return Profile.SNOW;
        return Profile.PLAINS;
    }

    private static BlockState s(Block b) { return b.defaultBlockState(); }

    static Hand hand(Profile p, ResourceLocation id) {
        return switch (p) {
            case NETHER_WASTES -> new Hand(new BlockState[]{s(Blocks.NETHERRACK)}, s(Blocks.NETHERRACK),
                    new BlockState[]{s(Blocks.NETHER_QUARTZ_ORE), s(Blocks.NETHER_GOLD_ORE), s(Blocks.MAGMA_BLOCK), s(Blocks.SOUL_SAND), s(Blocks.GRAVEL)},
                    new BlockState[]{s(Blocks.BROWN_MUSHROOM), s(Blocks.RED_MUSHROOM)}, new BlockState[]{s(Blocks.GLOWSTONE), s(Blocks.SHROOMLIGHT)}, false);
            case CRIMSON -> new Hand(new BlockState[]{s(Blocks.CRIMSON_NYLIUM)}, s(Blocks.NETHERRACK),
                    new BlockState[]{s(Blocks.NETHER_GOLD_ORE), s(Blocks.NETHER_WART_BLOCK), s(Blocks.BLACKSTONE)},
                    new BlockState[]{s(Blocks.CRIMSON_ROOTS), s(Blocks.CRIMSON_FUNGUS), s(Blocks.CRIMSON_ROOTS)}, new BlockState[]{s(Blocks.SHROOMLIGHT)}, false);
            case WARPED -> new Hand(new BlockState[]{s(Blocks.WARPED_NYLIUM)}, s(Blocks.NETHERRACK),
                    new BlockState[]{s(Blocks.NETHER_GOLD_ORE), s(Blocks.WARPED_WART_BLOCK), s(Blocks.BLACKSTONE)},
                    new BlockState[]{s(Blocks.WARPED_ROOTS), s(Blocks.WARPED_FUNGUS), s(Blocks.NETHER_SPROUTS)}, new BlockState[]{s(Blocks.SHROOMLIGHT)}, false);
            case SOUL_VALLEY -> new Hand(new BlockState[]{s(Blocks.SOUL_SOIL), s(Blocks.SOUL_SOIL), s(Blocks.SOUL_SAND)}, s(Blocks.SOUL_SOIL),
                    new BlockState[]{s(Blocks.BONE_BLOCK), s(Blocks.SOUL_SAND), s(Blocks.NETHER_QUARTZ_ORE)},
                    new BlockState[]{}, new BlockState[]{}, false);
            case BASALT -> new Hand(new BlockState[]{s(Blocks.BASALT), s(Blocks.BASALT), s(Blocks.BLACKSTONE), s(Blocks.SMOOTH_BASALT)}, s(Blocks.BLACKSTONE),
                    new BlockState[]{s(Blocks.MAGMA_BLOCK), s(Blocks.GILDED_BLACKSTONE), s(Blocks.POLISHED_BASALT), s(Blocks.NETHERRACK)},
                    new BlockState[]{}, new BlockState[]{s(Blocks.GLOWSTONE)}, false);
            case END -> new Hand(new BlockState[]{s(Blocks.END_STONE)}, s(Blocks.END_STONE),
                    new BlockState[]{s(Blocks.END_STONE_BRICKS), s(Blocks.PURPUR_BLOCK), s(Blocks.OBSIDIAN)},
                    new BlockState[]{}, new BlockState[]{}, false);
            case DEEP_DARK -> new Hand(new BlockState[]{s(Blocks.SCULK), s(Blocks.SCULK), s(Blocks.DEEPSLATE), s(Blocks.COBBLED_DEEPSLATE)}, s(Blocks.DEEPSLATE),
                    new BlockState[]{s(Blocks.SCULK), s(Blocks.TUFF), s(Blocks.DEEPSLATE_BRICKS), s(Blocks.DEEPSLATE_TILES)},
                    new BlockState[]{}, new BlockState[]{}, false);
            case LUSH -> new Hand(new BlockState[]{s(Blocks.MOSS_BLOCK), s(Blocks.MOSS_BLOCK), s(Blocks.MOSS_BLOCK), s(Blocks.CLAY), s(Blocks.ROOTED_DIRT)}, s(Blocks.CLAY),
                    new BlockState[]{s(Blocks.CLAY), s(Blocks.ROOTED_DIRT), s(Blocks.FLOWERING_AZALEA_LEAVES), s(Blocks.AZALEA_LEAVES), s(Blocks.COPPER_ORE)},
                    new BlockState[]{s(Blocks.MOSS_CARPET), s(Blocks.AZALEA), s(Blocks.FLOWERING_AZALEA), s(Blocks.FERN), s(Blocks.MOSS_CARPET)},
                    new BlockState[]{s(Blocks.SHROOMLIGHT)}, false);
            case DRIPSTONE -> new Hand(new BlockState[]{s(Blocks.DRIPSTONE_BLOCK)}, s(Blocks.DRIPSTONE_BLOCK),
                    new BlockState[]{s(Blocks.DRIPSTONE_BLOCK), s(Blocks.COPPER_ORE), s(Blocks.GRANITE), s(Blocks.DRIPSTONE_BLOCK)},
                    new BlockState[]{s(Blocks.POINTED_DRIPSTONE), s(Blocks.POINTED_DRIPSTONE)}, new BlockState[]{}, false);
            case OCEAN -> {
                boolean warm = id.getPath().contains("warm") || id.getPath().contains("lukewarm");
                yield new Hand(new BlockState[]{s(Blocks.SAND), s(Blocks.SAND), s(Blocks.GRAVEL)}, s(Blocks.SAND),
                        warm ? new BlockState[]{s(Blocks.DEAD_TUBE_CORAL_BLOCK), s(Blocks.DEAD_BRAIN_CORAL_BLOCK), s(Blocks.DEAD_BUBBLE_CORAL_BLOCK),
                                s(Blocks.DEAD_FIRE_CORAL_BLOCK), s(Blocks.DEAD_HORN_CORAL_BLOCK), s(Blocks.SPONGE), s(Blocks.CLAY)}
                                : new BlockState[]{s(Blocks.GRAVEL), s(Blocks.CLAY), s(Blocks.DRIED_KELP_BLOCK), s(Blocks.PRISMARINE), s(Blocks.CLAY)},
                        warm ? new BlockState[]{s(Blocks.DEAD_TUBE_CORAL), s(Blocks.DEAD_BRAIN_CORAL), s(Blocks.DEAD_BUBBLE_CORAL), s(Blocks.DEAD_FIRE_CORAL),
                                s(Blocks.DEAD_HORN_CORAL), s(Blocks.DEAD_TUBE_CORAL_FAN), s(Blocks.DEAD_BRAIN_CORAL_FAN)}
                                : new BlockState[]{},
                        new BlockState[]{s(Blocks.SEA_LANTERN)}, true);
            }
            default -> null;
        };
    }

    // ------------------------------------------------------------------ extraction

    static GenesisPalette of(ServerLevel l, Holder<Biome> biome) {
        GenesisPalette pal = new GenesisPalette();
        try {
            pal.read(l, biome);
        } catch (RuntimeException | LinkageError ignored) {
        }
        return pal;
    }

    private void read(ServerLevel l, Holder<Biome> biome) {
        var access = l.registryAccess();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, access);
        Registry<PlacedFeature> placed = access.registryOrThrow(Registries.PLACED_FEATURE);
        Registry<ConfiguredFeature<?, ?>> configured = access.registryOrThrow(Registries.CONFIGURED_FEATURE);
        List<HolderSet<PlacedFeature>> steps = biome.value().getGenerationSettings().features();
        for (int step = 0; step < steps.size(); step++) {
            for (Holder<PlacedFeature> ph : steps.get(step)) {
                ConfiguredFeature<?, ?> cf;
                try { cf = ph.value().feature().value(); } catch (RuntimeException ex) { continue; }
                collect(ops, placed, configured, cf, 0);
                boolean placeStep = step == GenerationStep.Decoration.LOCAL_MODIFICATIONS.ordinal()
                        || step == GenerationStep.Decoration.UNDERGROUND_DECORATION.ordinal()
                        || step == GenerationStep.Decoration.VEGETAL_DECORATION.ordinal()
                        || step == GenerationStep.Decoration.TOP_LAYER_MODIFICATION.ordinal();
                if (placeStep && placeable(cf) && features.size() < 40) features.add(ph.value().feature());
            }
        }
    }

    private static String type(ConfiguredFeature<?, ?> cf) {
        ResourceLocation k = BuiltInRegistries.FEATURE.getKey(cf.feature());
        return k == null ? "" : k.getPath();
    }

    private static boolean placeable(ConfiguredFeature<?, ?> cf) {
        ResourceLocation k = BuiltInRegistries.FEATURE.getKey(cf.feature());
        if (k == null) return false;
        return !"minecraft".equals(k.getNamespace()) || !NO_PLACE.contains(k.getPath());
    }

    private void collect(RegistryOps<JsonElement> ops, Registry<PlacedFeature> placed, Registry<ConfiguredFeature<?, ?>> configured,
                         ConfiguredFeature<?, ?> cf, int depth) {
        if (depth > 3) return;
        String t = type(cf);
        JsonElement json = ConfiguredFeature.DIRECT_CODEC.encodeStart(ops, cf).result().orElse(null);
        if (json == null) return;
        boolean fluidOnly = t.equals("lake") || t.equals("spring_feature");
        walk(ops, placed, configured, json, "", t, fluidOnly, NO_COLLECT.contains(t), depth);
    }

    private void walk(RegistryOps<JsonElement> ops, Registry<PlacedFeature> placed, Registry<ConfiguredFeature<?, ?>> configured,
                      JsonElement e, String key, String type, boolean fluidOnly, boolean noCollect, int depth) {
        if (e.isJsonArray()) {
            JsonArray a = e.getAsJsonArray();
            for (int i = 0; i < a.size() && i < 64; i++) walk(ops, placed, configured, a.get(i), key, type, fluidOnly, noCollect, depth);
        } else if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (o.has("Name") && o.get("Name").isJsonPrimitive()) {
                if (!noCollect) state(o, key, fluidOnly);
                return;
            }
            for (Map.Entry<String, JsonElement> en : o.entrySet()) {
                String k = en.getKey();
                if (k.equals("target") && en.getValue().isJsonObject()) { hint(en.getValue().getAsJsonObject()); continue; }
                if (SKIP_KEYS.contains(k)) continue;
                walk(ops, placed, configured, en.getValue(), k, type, fluidOnly, noCollect, depth);
            }
        } else if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isString() && (key.startsWith("feature") || key.equals("default"))) {
            ResourceLocation rl = ResourceLocation.tryParse(e.getAsString());
            if (rl == null) return;
            var ph = placed.getOptional(rl);
            if (ph.isPresent()) { collect(ops, placed, configured, ph.get().feature().value(), depth + 1); return; }
            var cf = configured.getOptional(rl);
            if (cf.isPresent()) collect(ops, placed, configured, cf.get(), depth + 1);
        }
    }

    /** Ore targets that are not vanilla stone name the biome's own stone (e.g. a modded cave rock). */
    private void hint(JsonObject target) {
        if (!target.has("block") || !target.get("block").isJsonPrimitive()) return;
        ResourceLocation rl = ResourceLocation.tryParse(target.get("block").getAsString());
        if (rl == null || "minecraft".equals(rl.getNamespace())) return;
        Block b = BuiltInRegistries.BLOCK.get(rl);
        if (b == Blocks.AIR) return;
        BlockState st = b.defaultBlockState();
        if (!st.hasBlockEntity() && st.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO) && stone == null) stone = st;
    }

    private void state(JsonObject o, String key, boolean fluidOnly) {
        BlockState bs = BlockState.CODEC.parse(JsonOps.INSTANCE, o).result().orElse(null);
        if (bs == null) return;
        Block b = bs.getBlock();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(b);
        boolean modded = !"minecraft".equals(id.getNamespace());
        if (b instanceof LiquidBlock) {
            if (modded && bs.getFluidState().isSource() && fluids.size() < 4) fluids.add(bs);
            else if (modded && b.defaultBlockState().getFluidState().isSource() && fluids.size() < 4) fluids.add(b.defaultBlockState());
            return;
        }
        if (fluidOnly) return;
        if (bs.isAir() || bs.hasBlockEntity() || b.defaultDestroyTime() < 0 || bs.is(BlockTags.LEAVES) || bs.is(BlockTags.LOGS)
                || b instanceof BaseFireBlock || b instanceof TntBlock || id.getPath().contains("budding")) return;
        if (bs.hasProperty(BlockStateProperties.WATERLOGGED)) bs = bs.setValue(BlockStateProperties.WATERLOGGED, false);
        if (!modded && bs.is(Tags.Blocks.ORES) && !KEEP_ORES.contains(id.getPath())) return;
        if (bs.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
            if (solids.size() < 24) solids.putIfAbsent(b, bs);
            if (bs.getLightEmission() >= 8) lights.putIfAbsent(b, bs);
        } else if (b instanceof BushBlock || bs.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty()) {
            if (plants.size() < 16) plants.putIfAbsent(b, bs);
        }
    }
}
