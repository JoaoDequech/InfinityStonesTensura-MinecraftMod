package com.infinitystones.tensura.command;

import com.infinitystones.tensura.InfinityStones;
import com.infinitystones.tensura.skill.Stone;
import com.infinitystones.tensura.skill.StoneProgress;
import com.infinitystones.tensura.skill.StoneTier;
import com.infinitystones.tensura.skill.StoneTree;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * Interim tree interface until the high-end tree screen exists: /infinity_tree <stone> [buy <node>].
 * Permission level 0 — players on a server without OP must be able to unlock their own powers.
 */
@EventBusSubscriber(modid = InfinityStones.MOD_ID)
public final class TreeCommand {
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("infinity_tree")
                .then(Commands.argument("stone", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(Stone.values()).map(s -> s.key), b))
                        .executes(TreeCommand::list)
                        .then(Commands.literal("buy").then(Commands.argument("node", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    Stone s = stone(c);
                                    var me = c.getSource().getPlayer();
                                    return s == null ? b.buildFuture() : SharedSuggestionProvider.suggest(
                                            StoneTree.of(s).nodes.values().stream()
                                                    .filter(n -> n.cost() != null && !(me != null && StoneTier.hidden(me, n))).map(StoneTree.Node::id), b);
                                })
                                .executes(TreeCommand::buy)))));
    }

    private static Stone stone(CommandContext<CommandSourceStack> c) {
        String key = StringArgumentType.getString(c, "stone").toLowerCase(Locale.ROOT);
        for (Stone s : Stone.values()) if (s.key.equals(key)) return s;
        return null;
    }

    private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Stone s = stone(c);
        if (s == null) { c.getSource().sendFailure(Component.translatable("infinity.tree.unknown_stone")); return 0; }
        var inst = StoneProgress.instance(p, s);
        if (inst.isEmpty()) { c.getSource().sendFailure(Component.translatable("infinity.tree.not_learned")); return 0; }
        Set<String> bought = StoneProgress.bought(inst.get());
        Set<Stone> held = StoneProgress.heldStones(p);
        c.getSource().sendSuccess(() -> Component.translatable("infinity.tree.header", Component.translatable("infinity.skill." + s.key + "_stone"))
                .withStyle(ChatFormatting.GOLD), false);
        for (StoneTree.Node n : StoneTree.of(s).nodes.values()) {
            if (!n.inTree() || n.title().isBlank() || StoneTier.hidden(p, n)) continue;
            boolean unlocked = StoneProgress.isUnlocked(p, s, n.id(), held);
            MutableComponent line = Component.literal(unlocked ? " ✔ " : " ✖ ").withStyle(unlocked ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY)
                    .append(n.displayName().copy().withStyle(unlocked ? ChatFormatting.WHITE : ChatFormatting.GRAY))
                    .append(Component.literal(" [" + n.id() + "]").withStyle(ChatFormatting.DARK_GRAY));
            if (n.cost() != null && !bought.contains(n.id())) {
                String cost = "xp_levels".equals(n.cost().type()) ? n.cost().amount() + " XP" : n.cost().amount() + "x Aspects";
                line.append(Component.literal("  " + cost).withStyle(ChatFormatting.YELLOW));
            }
            c.getSource().sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int buy(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        Stone s = stone(c);
        if (s == null) { c.getSource().sendFailure(Component.translatable("infinity.tree.unknown_stone")); return 0; }
        String node = StringArgumentType.getString(c, "node");
        StoneProgress.BuyResult r = StoneProgress.buy(p, s, node);
        if (r == StoneProgress.BuyResult.OK) {
            StoneTree.Node n = StoneTree.of(s).nodes.get(node);
            c.getSource().sendSuccess(() -> Component.translatable("infinity.tree.result.ok", n.displayName()).withStyle(ChatFormatting.GREEN), false);
            return 1;
        }
        c.getSource().sendFailure(StoneProgress.describe(r));
        return 0;
    }

    private TreeCommand() {}
}
