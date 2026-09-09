package me.foesio.foSellwands.sell;

import me.foesio.core.item.FoItemStacks;
import me.foesio.core.economy.VaultEconomyBridge;
import me.foesio.core.logging.FoFileLogger;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.number.DurationParser;
import me.foesio.core.sound.FoSoundService;
import me.foesio.foSellwands.config.ConfigManager;
import me.foesio.foSellwands.hook.HistoryService;
import me.foesio.foSellwands.hook.HologramService;
import me.foesio.foSellwands.hook.ProtectionService;
import me.foesio.foSellwands.hook.ShopPriceService;
import me.foesio.foSellwands.wand.WandData;
import me.foesio.foSellwands.wand.WandService;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SellService implements Listener {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FoMessageService messages;
    private final FoSoundService sounds;
    private final VaultEconomyBridge economyService;
    private final ShopPriceService shopPriceService;
    private final ProtectionService protectionService;
    private final WandService wandService;
    private final HistoryService historyService;
    private final HologramService hologramService;
    private final FoFileLogger fileLogger;
    private final Map<UUID, PendingConfirm> confirms = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public SellService(
            JavaPlugin plugin,
            ConfigManager configManager,
            FoMessageService messages,
            FoSoundService sounds,
            VaultEconomyBridge economyService,
            ShopPriceService shopPriceService,
            ProtectionService protectionService,
            WandService wandService,
            HistoryService historyService,
            HologramService hologramService,
            FoFileLogger fileLogger
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messages = messages;
        this.sounds = sounds;
        this.economyService = economyService;
        this.shopPriceService = shopPriceService;
        this.protectionService = protectionService;
        this.wandService = wandService;
        this.historyService = historyService;
        this.hologramService = hologramService;
        this.fileLogger = fileLogger;
    }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND && wandService.isWandItem(event.getItem())) {
            event.setCancelled(true);
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!isWandAction(event.getAction())) {
            return;
        }
        ItemStack wand = event.getItem();
        Optional<WandData> dataOptional = wandService.read(wand);
        if (dataOptional.isEmpty()) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (wand.getAmount() > 1) {
            sounds.play(player, "sell.error");
            messages.send(player, "messages.stacked-wand", "{prefix}{bad}Unstack this sellwand before using it.");
            return;
        }
        if (!player.hasPermission("fosellwands.use")) {
            sounds.play(player, "sell.error");
            messages.send(player, "messages.use-no-permission", "{prefix}{bad}You cannot use sellwands.");
            return;
        }
        WandData data = dataOptional.get();
        if (data.uses() == 0) {
            player.getInventory().setItemInMainHand(null);
            sounds.play(player, "sell.error");
            messages.send(player, "messages.wand-broken", "{prefix}{bad}Your sellwand ran out of uses.");
            return;
        }
        if (!canUseTier(player, data)) {
            sounds.play(player, "sell.error");
            messages.send(player, "messages.tier-no-permission", "{prefix}{bad}You cannot use this sellwand tier.");
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) {
            sounds.play(player, "sell.error");
            messages.send(player, "messages.not-container", "{prefix}{bad}That is not a supported container.");
            return;
        }
        if (!configManager.isContainerEnabled(block.getType())) {
            sounds.play(player, "sell.error");
            messages.send(player, "messages.disabled-container", "{prefix}{bad}Sellwands cannot be used on this container.");
            return;
        }
        if (!protectionService.canUse(player, block)) {
            sounds.play(player, "sell.error");
            messages.send(player, "messages.protected", "{prefix}{bad}You cannot sell there.");
            return;
        }

        Inventory inventory = resolveInventory(player, block);
        if (inventory == null) {
            sounds.play(player, "sell.error");
            messages.send(player, "messages.not-container", "{prefix}{bad}That is not a supported container.");
            return;
        }

        ClickAction clickAction = clickAction(event);
        if (clickAction == ClickAction.INSPECT) {
            preview(player, inventory, data);
            return;
        }
        if (clickAction == ClickAction.NONE) {
            return;
        }

        if (needsConfirmation() && !isConfirmed(player, block)) {
            previewConfirm(player, block, inventory, data);
            return;
        }

        long cooldownRemaining = cooldownRemaining(player);
        if (cooldownRemaining > 0) {
            sounds.play(player, "sell.error");
            messages.send(player, "messages.cooldown", "{prefix}{bad}Wait {theme}{time}s {bad}before using a sellwand again.", Map.of("time", Long.toString(cooldownRemaining)));
            return;
        }

        sell(player, block, inventory, wand, data);
    }

    private ClickAction clickAction(PlayerInteractEvent event) {
        String mode = configManager.config().getString("settings.click-mode", "LEFT_INSPECT_RIGHT_SELL");
        ClickMode clickMode = ClickMode.from(mode);
        if (event.getAction() == Action.LEFT_CLICK_BLOCK || event.getAction() == Action.LEFT_CLICK_AIR) {
            return ClickAction.INSPECT;
        }
        return switch (clickMode) {
            case LEFT_INSPECT_RIGHT_SELL, RIGHT_INSPECT_CONFIRM -> ClickAction.SELL;
            case SHIFT_RIGHT_SELL -> event.getPlayer().isSneaking() ? ClickAction.SELL : ClickAction.INSPECT;
        };
    }

    private boolean isWandAction(Action action) {
        return action == Action.RIGHT_CLICK_BLOCK
                || action == Action.LEFT_CLICK_BLOCK
                || action == Action.RIGHT_CLICK_AIR
                || action == Action.LEFT_CLICK_AIR;
    }

    private boolean needsConfirmation() {
        return ClickMode.from(configManager.config().getString("settings.click-mode", "LEFT_INSPECT_RIGHT_SELL")) == ClickMode.RIGHT_INSPECT_CONFIRM
                || configManager.config().getBoolean("settings.confirm-to-sell", false);
    }

    private boolean canUseTier(Player player, WandData data) {
        if (!configManager.config().getBoolean("permissions.per-tier", false)) {
            return true;
        }
        if (!wandService.isTier(data.id())) {
            return true;
        }
        return player.hasPermission("fosellwands.use." + data.id().toLowerCase());
    }

    private Inventory resolveInventory(Player player, Block block) {
        if (block.getType() == Material.ENDER_CHEST) {
            return player.getEnderChest();
        }
        if (block.getState() instanceof Container container) {
            return container.getInventory();
        }
        return null;
    }

    private void preview(Player player, Inventory inventory, WandData data) {
        ItemStack[] working = cloneContents(inventory.getContents());
        SellResult result = processContents(player, working, data.multiplier(), maxDepth());
        if (result.itemAmount() <= 0 || result.money() <= 0) {
            sounds.play(player, "sell.empty");
            messages.send(player, "messages.nothing-sold", "{prefix}{bad}Nothing sellable was found.");
            return;
        }
        sounds.play(player, "sell.inspect");
        messages.send(player, "messages.inspect", "{prefix}{muted}This container is worth {theme}${price} {muted}from {theme}{amount} items{muted}.", replacements(result));
    }

    private void previewConfirm(Player player, Block block, Inventory inventory, WandData data) {
        ItemStack[] working = cloneContents(inventory.getContents());
        SellResult result = processContents(player, working, data.multiplier(), maxDepth());
        if (result.itemAmount() <= 0 || result.money() <= 0) {
            sounds.play(player, "sell.empty");
            messages.send(player, "messages.nothing-sold", "{prefix}{bad}Nothing sellable was found.");
            return;
        }
        long expiresAt = System.currentTimeMillis() + configuredSeconds("settings.confirm-time-seconds", 6) * 1000L;
        confirms.put(player.getUniqueId(), new PendingConfirm(blockKey(block), expiresAt));
        sounds.play(player, "sell.confirm");
        messages.send(player, "messages.confirm", "{prefix}{muted}Click again to sell {theme}{amount} items {muted}for {theme}${price}{muted}.", replacements(result));
    }

    private boolean isConfirmed(Player player, Block block) {
        PendingConfirm pending = confirms.remove(player.getUniqueId());
        return pending != null && pending.blockKey().equals(blockKey(block)) && pending.expiresAt() >= System.currentTimeMillis();
    }

    private void sell(Player player, Block block, Inventory inventory, ItemStack wand, WandData data) {
        ItemStack[] original = cloneContents(inventory.getContents());
        ItemStack[] working = cloneContents(original);
        SellResult result = processContents(player, working, data.multiplier(), maxDepth());
        if (result.itemAmount() <= 0 || result.money() <= 0) {
            sounds.play(player, "sell.empty");
            messages.send(player, "messages.nothing-sold", "{prefix}{bad}Nothing sellable was found.");
            return;
        }

        int newUses = data.uses();
        if (newUses > 0) {
            newUses--;
        }
        boolean deposited = false;
        try {
            inventory.setContents(working);
            updateContainer(block);
            if (!economyService.deposit(player, result.money())) {
                restoreInventory(block, inventory, original);
                fileLogger.warn("Vault payout failed for " + player.getName() + "; sale rolled back.");
                sounds.play(player, "sell.error");
                messages.send(player, "messages.vault-failed", "{prefix}{bad}The sale could not be paid. No items were removed.");
                return;
            }
            deposited = true;

            if (newUses == 0) {
                player.getInventory().setItemInMainHand(null);
            } else {
                WandData updated = data.withSale(newUses, result.itemAmount(), result.money());
                wandService.applyData(wand, updated);
                player.getInventory().setItemInMainHand(wand);
            }
        } catch (RuntimeException exception) {
            restoreInventory(block, inventory, original);
            if (deposited && !economyService.withdraw(player, result.money())) {
                plugin.getLogger().severe("Could not withdraw rolled-back sellwand payout of " + result.money() + " from " + player.getName());
                fileLogger.error("Could not withdraw rolled-back sellwand payout of " + result.money() + " from " + player.getName() + ".", exception);
            }
            plugin.getLogger().warning("Safely rolled back sellwand sale for " + player.getName() + ": " + exception.getMessage());
            fileLogger.error("Safely rolled back sellwand sale for " + player.getName() + ".", exception);
            sounds.play(player, "sell.error");
            messages.send(player, "messages.sale-failed", "{prefix}{bad}The sale failed safely. No items were removed.");
            return;
        }

        messages.send(player, "messages.sold", "{prefix}{good}Sold {theme}{amount} items {muted}for {theme}${price}{muted}.", replacements(result));
        sendBreakdown(player, result);
        playFeedback(player, block, result);
        if (data.axConverted()) {
            messages.send(player, "messages.ax-converted", "{prefix}{good}Converted your old AxSellwands item.");
        }
        if (newUses == 0) {
            messages.send(player, "messages.wand-broken", "{prefix}{bad}Your sellwand ran out of uses.");
        }
        applyCooldown(player);
        historyService.logSale(player, block, data, newUses, result);
    }

    private SellResult processContents(Player player, ItemStack[] contents, double multiplier, int depth) {
        SellResult result = new SellResult();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType().isAir()) {
                continue;
            }
            if (wandService.isWandItem(item)) {
                continue;
            }

            NestedProcess nested = processNested(player, item, multiplier, depth);
            result.merge(nested.result());
            item = nested.item();
            contents[i] = item;

            if (nested.wasContainer() && nested.hadContents()) {
                continue;
            }
            if (nested.wasContainer() && !configManager.config().getBoolean("settings.sell-empty-container-shells", false)) {
                continue;
            }

            double price = shopPriceService.getSellPrice(player, FoItemStacks.cloneItem(item));
            if (price <= 0) {
                continue;
            }
            result.add(prettyItemName(item.getType()), item.getAmount(), price * multiplier);
            contents[i] = null;
        }
        return result;
    }

    private NestedProcess processNested(Player player, ItemStack item, double multiplier, int depth) {
        if (!configManager.config().getBoolean("settings.nested-containers", true) || depth <= 0) {
            return new NestedProcess(item, new SellResult(), false, false);
        }
        NestedProcess blockState = processBlockStateContainer(player, item, multiplier, depth);
        if (blockState.wasContainer()) {
            return blockState;
        }
        return processBundle(player, item, multiplier, depth);
    }

    private NestedProcess processBlockStateContainer(Player player, ItemStack item, double multiplier, int depth) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof BlockStateMeta blockStateMeta) || !(blockStateMeta.getBlockState() instanceof Container container)) {
            return new NestedProcess(item, new SellResult(), false, false);
        }
        boolean hadContents = hasAnyItem(container.getInventory().getContents());
        if (item.getAmount() != 1 || !hadContents) {
            return new NestedProcess(item, new SellResult(), true, hadContents);
        }

        ItemStack[] nestedContents = cloneContents(container.getInventory().getContents());
        SellResult result = processContents(player, nestedContents, multiplier, depth - 1);
        if (result.changed()) {
            container.getInventory().setContents(nestedContents);
            blockStateMeta.setBlockState(container);
            item.setItemMeta(blockStateMeta);
        }
        return new NestedProcess(item, result, true, true);
    }

    @SuppressWarnings("unchecked")
    private NestedProcess processBundle(Player player, ItemStack item, double multiplier, int depth) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return new NestedProcess(item, new SellResult(), false, false);
        }
        try {
            Method getItems = meta.getClass().getMethod("getItems");
            Object rawItems = getItems.invoke(meta);
            if (!(rawItems instanceof List<?> rawList)) {
                return new NestedProcess(item, new SellResult(), false, false);
            }
            List<ItemStack> bundleItems = new ArrayList<>();
            for (Object object : rawList) {
                if (object instanceof ItemStack stack) {
                    bundleItems.add(FoItemStacks.cloneItem(stack));
                }
            }
            boolean hadContents = !bundleItems.isEmpty();
            if (item.getAmount() != 1 || !hadContents) {
                return new NestedProcess(item, new SellResult(), true, hadContents);
            }

            ItemStack[] nestedContents = cloneContents(bundleItems.toArray(new ItemStack[0]));
            SellResult result = processContents(player, nestedContents, multiplier, depth - 1);
            if (result.changed()) {
                List<ItemStack> updated = Arrays.stream(nestedContents)
                        .filter(stack -> stack != null && !stack.getType().isAir())
                        .toList();
                Method setItems = meta.getClass().getMethod("setItems", List.class);
                setItems.invoke(meta, updated);
                item.setItemMeta(meta);
            }
            return new NestedProcess(item, result, true, true);
        } catch (NoSuchMethodException exception) {
            return new NestedProcess(item, new SellResult(), false, false);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            plugin.getLogger().warning("Could not read bundle contents: " + exception.getMessage());
            fileLogger.warn("Could not read bundle contents: " + exception.getMessage());
            return new NestedProcess(item, new SellResult(), true, true);
        }
    }

    private ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] cloned = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            cloned[i] = contents[i] == null ? null : FoItemStacks.cloneItem(contents[i]);
        }
        return cloned;
    }

    private boolean hasAnyItem(ItemStack[] contents) {
        for (ItemStack item : contents) {
            if (item != null && !item.getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    private int maxDepth() {
        return Math.max(0, configManager.config().getInt("settings.nested-container-max-depth", 4));
    }

    private long cooldownRemaining(Player player) {
        if (!configManager.config().getBoolean("cooldown.enabled", false)) {
            return 0;
        }
        long nextUse = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        long remainingMillis = nextUse - System.currentTimeMillis();
        if (remainingMillis <= 0) {
            cooldowns.remove(player.getUniqueId());
            return 0;
        }
        return Math.max(1L, (long) Math.ceil(remainingMillis / 1000D));
    }

    private void applyCooldown(Player player) {
        if (!configManager.config().getBoolean("cooldown.enabled", false)) {
            return;
        }
        long seconds = configuredSeconds("cooldown.seconds", 3);
        if (seconds > 0) {
            cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + seconds * 1000L);
        }
    }

    private long configuredSeconds(String path, long fallback) {
        String raw = configManager.config().getString(path, Long.toString(fallback));
        return DurationParser.parse(raw)
                .map(duration -> duration.secondsFloor())
                .orElse(configManager.config().getLong(path, fallback));
    }

    private void restoreInventory(Block block, Inventory inventory, ItemStack[] original) {
        try {
            inventory.setContents(cloneContents(original));
            updateContainer(block);
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("Could not restore container after failed sellwand sale: " + exception.getMessage());
            fileLogger.error("Could not restore container after failed sellwand sale.", exception);
        }
    }

    private void updateContainer(Block block) {
        if (block.getState() instanceof Container container) {
            container.update(true);
        }
    }

    private void sendBreakdown(Player player, SellResult result) {
        if (!configManager.config().getBoolean("breakdown.enabled", false)) {
            return;
        }
        List<SellResult.BreakdownEntry> entries = result.breakdown();
        if (entries.isEmpty()) {
            return;
        }
        messages.send(player, "messages.breakdown-header", "{prefix}{muted}Sale breakdown:", replacements(result));
        int maxLines = Math.max(1, configManager.config().getInt("breakdown.max-lines", 8));
        for (int i = 0; i < Math.min(entries.size(), maxLines); i++) {
            SellResult.BreakdownEntry entry = entries.get(i);
            messages.send(player, "messages.breakdown-line", "{prefix}{amount}x {item} = ${price}", Map.of(
                    "item", entry.itemName(),
                    "amount", Integer.toString(entry.amount()),
                    "price", wandService.formatMoney(entry.price())
            ));
        }
        int remaining = entries.size() - maxLines;
        if (remaining > 0) {
            messages.send(player, "messages.breakdown-more", "{prefix}{muted}And {amount} more item types.", Map.of("amount", Integer.toString(remaining)));
        }
    }

    private void playFeedback(Player player, Block block, SellResult result) {
        Map<String, String> replacements = replacements(result);
        if (configManager.config().getBoolean("feedback.actionbar.enabled", true)) {
            String raw = messages.render("messages.feedback-actionbar-sell", "#3ecf8eSold {amount} items for ${price}", replacements);
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(raw));
        }
        if (configManager.config().getBoolean("feedback.title.enabled", false)) {
            player.sendTitle(
                    messages.render("messages.feedback-title-title", "#03fc88sᴏʟᴅ", replacements),
                    messages.render("messages.feedback-title-subtitle", "#a7b8b0{amount} items for #03fc88${price}", replacements),
                    configManager.config().getInt("feedback.title.fade-in", 5),
                    configManager.config().getInt("feedback.title.stay", 35),
                    configManager.config().getInt("feedback.title.fade-out", 10)
            );
        }
        sounds.play(player, "sell.success");
        spawnParticles(block);
        hologramService.spawn(block, replacements);
    }

    private void spawnParticles(Block block) {
        if (!configManager.config().getBoolean("feedback.particle.enabled", true)) {
            return;
        }
        Particle particle = parseParticle(configManager.config().getString("feedback.particle.sell", "HAPPY_VILLAGER"), "HAPPY_VILLAGER");
        if (particle == null) {
            return;
        }
        block.getWorld().spawnParticle(
                particle,
                block.getLocation().add(0.5D, 0.8D, 0.5D),
                configManager.config().getInt("feedback.particle.amount", 18),
                configManager.config().getDouble("feedback.particle.offset-x", 0.45D),
                configManager.config().getDouble("feedback.particle.offset-y", 0.55D),
                configManager.config().getDouble("feedback.particle.offset-z", 0.45D)
        );
    }

    private Particle parseParticle(String raw, String fallback) {
        Particle parsed = particleValue(raw);
        if (parsed != null) {
            return parsed;
        }
        String value = raw == null ? "" : raw;
        plugin.getLogger().warning("Invalid sell particle in config.yml: " + value + ". Falling back to " + fallback + ".");
        fileLogger.warn("Invalid sell particle in config.yml: " + value + ". Falling back to " + fallback + ".");
        return particleValue(fallback);
    }

    private Particle particleValue(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Particle.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private Map<String, String> replacements(SellResult result) {
        return Map.of(
                "amount", Integer.toString(result.itemAmount()),
                "price", wandService.formatMoney(result.money())
        );
    }

    private String prettyItemName(Material material) {
        String[] parts = material.name().toLowerCase().split("_");
        List<String> words = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            words.add(Character.toUpperCase(part.charAt(0)) + part.substring(1));
        }
        return String.join(" ", words);
    }

    private String blockKey(Block block) {
        return block.getWorld().getUID() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    private record PendingConfirm(String blockKey, long expiresAt) {
    }

    private record NestedProcess(ItemStack item, SellResult result, boolean wasContainer, boolean hadContents) {
    }

    private enum ClickAction {
        INSPECT,
        SELL,
        NONE
    }

    private enum ClickMode {
        LEFT_INSPECT_RIGHT_SELL,
        SHIFT_RIGHT_SELL,
        RIGHT_INSPECT_CONFIRM;

        private static ClickMode from(String raw) {
            if (raw == null) {
                return LEFT_INSPECT_RIGHT_SELL;
            }
            try {
                return ClickMode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return LEFT_INSPECT_RIGHT_SELL;
            }
        }
    }
}
