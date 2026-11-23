package com.infinitystones.tensura.command;

import com.infinitystones.tensura.InfinityStones;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Commands used by the original datapack functions (Vormir is placed once from a very large template). */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class ModCommands {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("infinity_place_template_strict")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("template", ResourceLocationArgument.id())
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> place(ctx.getSource(), ResourceLocationArgument.getId(ctx, "template"),
                                        BlockPosArgument.getLoadedBlockPos(ctx, "pos"))))));
        // Ground relics: the executing armor stand shows <item> (vanilla /item replace refuses non-armour in armour slots).
        event.getDispatcher().register(Commands.literal("infinity_ground_display")
                .requires(source -> source.hasPermission(2))
                .then(Commands.argument("item", ResourceLocationArgument.id())
                        .executes(ctx -> {
                            if (!(ctx.getSource().getEntity() instanceof net.minecraft.world.entity.decoration.ArmorStand stand)) return 0;
                            var id = ResourceLocationArgument.getId(ctx, "item");
                            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id).orElse(null);
                            if (item == null) { ctx.getSource().sendFailure(Component.literal("Unknown item " + id)); return 0; }
                            if (!stand.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(item)) {
                                stand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(item));                            }
                            return 1;
                        })));
    }

    /** Places a template exactly (entities included) and reports a failure instead of silently doing nothing. */
    private static int place(CommandSourceStack source, ResourceLocation id, BlockPos pos) {
        ServerLevel level = source.getLevel();
        StructureTemplate template;
        try {
            template = level.getStructureManager().get(id).orElse(null);
        } catch (RuntimeException e) {
            InfinityStones.LOGGER.error("Could not read structure template {}", id, e);
            source.sendFailure(Component.literal("Unable to load structure template " + id));
            return 0;
        }
        if (template == null) {
            source.sendFailure(Component.literal("Unknown structure template " + id));
            return 0;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings().setKnownShape(true).setIgnoreEntities(false);
        if (!template.placeInWorld(level, pos, pos, settings, level.getRandom(), 2)) {
            source.sendFailure(Component.literal("Failed to place structure template " + id));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Placed structure template " + id), true);
        return 1;
    }

    private ModCommands() {}
}
