package me.foesio.foSellwands.command;

import me.foesio.core.command.FoAdminArguments;
import me.foesio.core.command.FoAdminCommand;
import me.foesio.core.command.FoAdminCommandContext;
import me.foesio.core.command.FoAdminMessages;
import me.foesio.core.command.FoAdminSubcommand;
import me.foesio.core.number.LargeNumberParser;
import me.foesio.core.inventory.OverflowPolicy;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foSellwands.FoSellwands;
import me.foesio.foSellwands.gui.EditorManager;
import me.foesio.foSellwands.wand.WandService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

public final class FoSellwandsCommand {
    private static final String USAGE = "{prefix}{muted}Use {theme}/fosellwandsadmin <editor|reload|version|list|give> [player] [multiplier|tier] [uses] [amount]{muted}.";

    private FoSellwandsCommand() {
    }

    public static FoAdminCommand register(
            FoSellwands plugin,
            FoMessageService messages,
            WandService wandService,
            EditorManager editorManager,
            UpdateNoticeService updateNotices,
            FoReloadRegistry reloadRegistry
    ) {
        FoAdminMessages adminMessages = FoAdminMessages.builder()
                .generalNoPermission("messages.no-permission", "{prefix}{bad}You cannot do that.")
                .generalPlayerOnly("messages.player-only", "{prefix}{bad}Only players can use that.")
                .usage("messages.usage", USAGE)
                .reloadSuccess("messages.reload", "{prefix}{good}Reloaded files.")
                .reloadFailed("messages.reload-failed", "{prefix}{bad}Reload failed at {theme}{step}{bad}. {muted}{error}")
                .commandFailed("messages.command-failed", "{prefix}{bad}The command failed. {muted}{error}")
                .build();

        return FoAdminCommand.builder(plugin, messages)
                .commandName("fosellwandsadmin")
                .permission("fosellwands.admin")
                .reloads(reloadRegistry)
                .updates(updateNotices)
                .adminMessages(adminMessages)
                .adminSounds(plugin.adminSounds())
                .addSubcommand(FoAdminSubcommand.builder("editor", context -> {
                    editorManager.openMain(context.playerOrNull());
                    return true;
                }).usage("editor").playerOnly().build())
                .addSubcommand(FoAdminSubcommand.builder("list", context -> {
                    list(messages, wandService, context);
                    return true;
                }).usage("list").build())
                .addSubcommand(FoAdminSubcommand.builder("help", context -> {
                    help(messages, context);
                    return true;
                }).usage("help").build())
                .addSubcommand(FoAdminSubcommand.builder("give", context -> {
                    give(plugin, messages, wandService, context);
                    return true;
                }).usage("give <player> <tier> [amount] | give <player> <multiplier> <uses> [amount]")
                        .tabCompleter(context -> completeGive(wandService, context))
                        .build())
                .register();
    }

    private static void list(FoMessageService messages, WandService wandService, FoAdminCommandContext context) {
        messages.send(context.sender(), "messages.tiers-header", "{prefix}{muted}Configured sellwand tiers:");
        for (String tier : wandService.tierIds()) {
            messages.send(context.sender(), "messages.tiers-line", "{prefix} &8• {theme}{tier} {muted}- {multiplier}x, {uses} uses", Map.of(
                    "tier", tier,
                    "multiplier", wandService.tierMultiplier(tier),
                    "uses", wandService.tierUses(tier)
            ));
        }
    }

    private static void help(FoMessageService messages, FoAdminCommandContext context) {
        List<String> lines = messages.renderList("messages.help", List.of(
                "{prefix}{theme}/fosellwandsadmin editor {muted}- Open the editor.",
                "{prefix}{theme}/fosellwandsadmin reload {muted}- Reload files.",
                "{prefix}{theme}/fosellwandsadmin version {muted}- Check version.",
                "{prefix}{theme}/fosellwandsadmin list {muted}- List sellwand tiers.",
                "{prefix}{theme}/fosellwandsadmin give <player> <tier> [amount] {muted}- Give a tiered wand.",
                "{prefix}{theme}/fosellwandsadmin give <player> <multiplier> <uses> [amount] {muted}- Give a custom wand."
        ), Map.of());
        if (lines.isEmpty()) {
            messages.send(context.sender(), "messages.usage", USAGE);
            return;
        }
        lines.forEach(context.sender()::sendMessage);
    }

    private static void give(FoSellwands plugin, FoMessageService messages, WandService wandService, FoAdminCommandContext context) {
        Optional<Player> targetOptional = FoAdminArguments.onlinePlayer().parse(context.subArg(0));
        if (targetOptional.isEmpty()) {
            plugin.fileLogger().warn("Give command failed for " + context.sender().getName() + ": player not online.");
            messages.send(context.sender(), "messages.invalid-player", "{prefix}{bad}That player is not online.");
            return;
        }

        Player target = targetOptional.get();
        String type = context.subArg(1);
        String tier = null;
        double multiplier = 0D;
        int uses = 0;
        int amount;
        if (type.isBlank()) {
            tier = wandService.firstTierId();
            amount = 1;
        } else if (wandService.isTier(type)) {
            tier = type.toLowerCase(java.util.Locale.ROOT);
            Integer parsedAmount = parseInteger(context.subArg(2), 1);
            if (parsedAmount == null || parsedAmount < 1 || parsedAmount > 2304) {
                invalidNumber(messages, context);
                return;
            }
            amount = parsedAmount;
        } else {
            if (context.subArg(2).isBlank()) {
                messages.send(context.sender(), "messages.usage", USAGE);
                return;
            }
            OptionalDouble parsedMultiplier = LargeNumberParser.parsePositiveDouble(type);
            if (parsedMultiplier.isEmpty()) {
                invalidNumber(messages, context);
                return;
            }
            multiplier = parsedMultiplier.getAsDouble();
            Integer parsedUses = parseInteger(context.subArg(2), 0);
            if (parsedUses == null || parsedUses == 0 || parsedUses < -1) {
                invalidNumber(messages, context);
                return;
            }
            uses = parsedUses;
            Integer parsedAmount = parseInteger(context.subArg(3), 1);
            if (parsedAmount == null || parsedAmount < 1 || parsedAmount > 2304) {
                invalidNumber(messages, context);
                return;
            }
            amount = parsedAmount;
        }

        for (int i = 0; i < amount; i++) {
            ItemStack item = tier == null ? wandService.createWand(multiplier, uses) : wandService.createWand(tier);
            plugin.core().inventoryDeposits().deposit(target, item, OverflowPolicy.DROP_OVERFLOW);
        }

        messages.send(context.sender(), "messages.give", "{prefix}{good}Gave {theme}{amount}x {muted}sellwand to {theme}{player}{muted}.", Map.of(
                "amount", Integer.toString(amount),
                "player", target.getName()
        ));
        plugin.fileLogger().info("Gave " + amount + " sellwand(s) to " + target.getName() + " by " + context.sender().getName() + ".");
        if (!context.sender().equals(target)) {
            messages.send(target, "messages.received", "{prefix}{good}You received {theme}{amount}x {muted}sellwand.", Map.of("amount", Integer.toString(amount)));
        }
    }

    private static void invalidNumber(FoMessageService messages, FoAdminCommandContext context) {
        messages.send(context.sender(), "messages.invalid-number", "{prefix}{bad}That number is invalid.");
    }

    private static Integer parseInteger(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        Optional<BigDecimal> parsed = LargeNumberParser.parse(raw);
        if (parsed.isEmpty()) {
            return null;
        }
        try {
            return parsed.get().intValueExact();
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    private static List<String> completeGive(WandService wandService, FoAdminCommandContext context) {
        if (context.args().length == 2) {
            return FoAdminArguments.onlinePlayer().complete(context.arg(1));
        }
        if (context.args().length == 3) {
            List<String> options = new ArrayList<>(wandService.tierIds());
            options.addAll(List.of("1.0", "1.5", "2.0"));
            return FoAdminArguments.completeOptions(options, context.arg(2));
        }
        if (context.args().length == 4) {
            if (wandService.isTier(context.arg(2))) {
                return FoAdminArguments.completeOptions(List.of("1", "8", "16", "32"), context.arg(3));
            }
            return FoAdminArguments.completeOptions(List.of("-1", "100", "250", "500"), context.arg(3));
        }
        if (context.args().length == 5 && !wandService.isTier(context.arg(2))) {
            return FoAdminArguments.completeOptions(List.of("1", "8", "16", "32"), context.arg(4));
        }
        return List.of();
    }
}
