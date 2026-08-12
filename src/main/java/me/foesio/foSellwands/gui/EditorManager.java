package me.foesio.foSellwands.gui;

import me.foesio.core.dialog.DialogButton;
import me.foesio.core.dialog.TextDialogRequest;
import me.foesio.core.editor.EditorDialogInputs;
import me.foesio.core.editor.EditorItemFactory;
import me.foesio.core.editor.EditorSaveResult;
import me.foesio.core.editor.EditorSettingSaver;
import me.foesio.core.editor.CycleOption;
import me.foesio.core.gui.GuiButtonConfig;
import me.foesio.core.gui.GuiSlots;
import me.foesio.core.gui.EntryBrowserClick;
import me.foesio.core.gui.EntryBrowserHolder;
import me.foesio.core.gui.EntryBrowserMenus;
import me.foesio.core.gui.EntryBrowserRequest;
import me.foesio.core.item.FoItemStacks;
import me.foesio.core.logging.FoFileLogger;
import me.foesio.core.material.MaterialTypes;
import me.foesio.core.material.MaterialChooserActionType;
import me.foesio.core.material.MaterialChooserClick;
import me.foesio.core.material.MaterialChooserHolder;
import me.foesio.core.material.MaterialChooserMenus;
import me.foesio.core.material.MaterialChooserMode;
import me.foesio.core.material.MaterialChooserRequest;
import me.foesio.core.material.MaterialSelections;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.number.LargeNumberParser;
import me.foesio.core.text.FoText;
import me.foesio.foSellwands.FoSellwands;
import me.foesio.foSellwands.config.ConfigManager;
import me.foesio.foSellwands.wand.WandService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class EditorManager implements Listener {
    private static final List<String> CLICK_MODES = List.of("LEFT_INSPECT_RIGHT_SELL", "SHIFT_RIGHT_SELL", "RIGHT_INSPECT_CONFIRM");
    private static final GuiButtonConfig BUTTONS = GuiButtonConfig.defaults();

    private final FoSellwands plugin;
    private final ConfigManager configManager;
    private final FoMessageService messages;
    private final WandService wandService;
    private final FoFileLogger fileLogger;
    private final EditorSettingSaver settingSaver;
    private final Set<UUID> suppressConfirmClose = new HashSet<>();

    public EditorManager(
            FoSellwands plugin,
            ConfigManager configManager,
            FoMessageService messages,
            WandService wandService,
            FoFileLogger fileLogger
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.messages = messages;
        this.wandService = wandService;
        this.fileLogger = fileLogger;
        this.settingSaver = new EditorSettingSaver(plugin, () -> { });
    }

    public void close() {
        suppressConfirmClose.clear();
    }

    public void openMain(Player player) {
        fileLogger.debug("Editor opened by " + player.getName() + ". Native dialogs="
                + plugin.core().nativeDialogs().canUseNativeDialogs() + ".");
        MenuHolder holder = new MenuHolder(MenuType.MAIN, null);
        Inventory inventory = Bukkit.createInventory(holder, 45, title("&8ғᴏsᴇʟʟᴡᴀɴᴅs"));
        holder.inventory = inventory;
        fill(inventory);

        inventory.setItem(10, EditorItemFactory.item(Material.GOLDEN_HOE, "#03fc88Sellwands", List.of("#ffffffAdd, remove, and edit sellwand tiers.")));
        inventory.setItem(11, EditorItemFactory.item(Material.CHEST, "#03fc88Containers", List.of("#ffffffEnable or disable container types.")));
        inventory.setItem(14, toggleItem("Tier Permissions", configManager.config().getBoolean("permissions.per-tier", false),
                "Require fosellwands.use.<tier>."));
        inventory.setItem(15, toggleItem("Confirm Selling", configManager.config().getBoolean("settings.confirm-to-sell", false),
                "Require a second click before selling."));
        inventory.setItem(16, toggleItem("Breakdown", configManager.config().getBoolean("breakdown.enabled", true),
                "Show item totals after selling."));
        inventory.setItem(22, toggleItem("Nested Containers", configManager.config().getBoolean("settings.nested-containers", true),
                "Sell contents inside shulkers and bundles."));
        inventory.setItem(23, toggleItem("Sell Empty Shells", configManager.config().getBoolean("settings.sell-empty-container-shells", false),
                "Sell empty shulker or bundle shells when priced."));
        inventory.setItem(12, cycleItem("Click Mode", configManager.config().getString("settings.click-mode", CLICK_MODES.getFirst()), CLICK_MODES));
        inventory.setItem(13, toggleItem("Cooldown", configManager.config().getBoolean("cooldown.enabled", false),
                "Limit successful sales per player."));
        inventory.setItem(28, toggleItem("WorldGuard", configManager.config().getBoolean("protection.worldguard", true),
                "Respect WorldGuard container access."));
        inventory.setItem(29, toggleItem("Actionbar", configManager.config().getBoolean("feedback.actionbar.enabled", true),
                "Show sale feedback above the hotbar."));
        inventory.setItem(30, toggleItem("Title", configManager.config().getBoolean("feedback.title.enabled", false),
                "Show sale feedback as a title."));
        inventory.setItem(25, toggleItem("Sound", configManager.config().getBoolean("feedback.sound.enabled", true),
                "Play a sound after selling."));
        inventory.setItem(24, toggleItem("Particle", configManager.config().getBoolean("feedback.particle.enabled", true),
                "Spawn particles after selling."));
        inventory.setItem(21, toggleItem("Hologram", configManager.config().getBoolean("feedback.hologram.enabled", false),
                "Spawn temporary sale holograms."));
        inventory.setItem(20, toggleItem("File Logging", configManager.config().getBoolean("file-logging", false),
                "Write debug activity to logs/latest.log."));
        inventory.setItem(19, toggleItem("Native Dialogs", plugin.core().nativeDialogs().configEnabled(),
                "Use Paper dialog inputs when supported."));
        player.openInventory(inventory);
    }

    private void openWands(Player player) {
        openWands(player, 0, "");
    }

    private void openWands(Player player, int requestedPage, String query) {
        String normalizedQuery = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        List<EntryBrowserRequest.Entry> entries = wandService.tierIds().stream()
                .filter(tier -> normalizedQuery.isBlank() || tier.toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .map(tier -> EntryBrowserRequest.Entry.of(tier, EditorItemFactory.item(wandService.tierMaterial(tier), "#03fc88" + tier,
                        List.of(
                                "#ffffffMultiplier: #03fc88" + wandService.tierMultiplier(tier) + "x",
                                "#ffffffUses: #03fc88" + wandService.tierUses(tier),
                                "#ffffffPermission: #03fc88fosellwands.use." + tier,
                                "",
                                "#a7b8b0Click to edit."
                        ))))
                .toList();
        EntryBrowserMenus.open(player, EntryBrowserRequest.builder()
                .title("Sellwands")
                .entries(entries)
                .page(requestedPage)
                .filter(normalizedQuery)
                .buttons(BUTTONS)
                .showBack(true)
                .addButton(EditorItemFactory.item(Material.ANVIL, "#3ecf8eAdd Sellwand", List.of("#ffffffCreate a new sellwand tier.")))
                .build());
    }

    private void openTier(Player player, String tier) {
        MenuHolder holder = new MenuHolder(MenuType.TIER, tier);
        Inventory inventory = Bukkit.createInventory(holder, 27, title("&8{tier} ᴇᴅɪᴛᴏʀ").replace("{tier}", tier));
        holder.inventory = inventory;
        fill(inventory);

        inventory.setItem(4, wandService.createWand(tier));
        inventory.setItem(10, EditorItemFactory.item(wandService.tierMaterial(tier), "#03fc88Cursor Item", List.of(
                "#ffffffCurrent: #03fc88" + wandService.tierMaterial(tier).name(),
                "#ffffffHold cursor item and click to copy visuals.",
                "#a7b8b0Empty cursor opens material prompt."
        )));
        inventory.setItem(11, toggleItem("Glow", tierBoolean(tier, "glow", true), "Adds an enchant glint."));
        inventory.setItem(12, EditorItemFactory.item(Material.ITEM_FRAME, "#03fc88Custom Model Data", List.of("#ffffffCurrent: #03fc88" + tierInt(tier, "custom-model-data", 0))));
        inventory.setItem(13, EditorItemFactory.item(Material.TRIPWIRE_HOOK, "#03fc88Permission", List.of("#ffffffNode: #03fc88fosellwands.use." + tier, "#a7b8b0Enable tier permissions in the main menu.")));
        inventory.setItem(14, EditorItemFactory.item(Material.EMERALD, "#03fc88Multiplier", List.of("#ffffffCurrent: #03fc88" + wandService.tierMultiplier(tier) + "x")));
        inventory.setItem(15, EditorItemFactory.item(Material.PAPER, "#03fc88Uses", List.of("#ffffffCurrent: #03fc88" + wandService.tierUses(tier))));
        inventory.setItem(16, EditorItemFactory.item(Material.LAVA_BUCKET, "#ff5d73Delete Sellwand", List.of("#ffffffOpen delete confirmation.")));
        inventory.setItem(GuiSlots.bottomMiddleSlot(3), BUTTONS.back());
        player.openInventory(inventory);
    }

    private void openContainers(Player player) {
        MaterialChooserMenus.open(player, containerRequest());
    }

    private MaterialChooserRequest containerRequest() {
        ConfigurationSection section = configManager.config().getConfigurationSection("containers.enabled");
        List<Material> available = new ArrayList<>();
        Set<Material> enabled = new HashSet<>();
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Material material = MaterialTypes.match(key);
                if (material == null) {
                    continue;
                }
                available.add(material);
                if (section.getBoolean(key, true)) {
                    enabled.add(material);
                }
            }
        }
        return MaterialChooserRequest.builder()
                .title("ᴄᴏɴᴛᴀɪɴᴇʀs")
                .availableMaterials(available)
                .selectedMaterials(enabled)
                .mode(MaterialChooserMode.MULTI_TOGGLE)
                .showBack(true)
                .showSearch(true)
                .buttons(BUTTONS)
                .extraLore(material -> List.of("#ffffffClick to toggle this container."))
                .build();
    }

    private void openContainerPageTwo(Player player, MaterialChooserRequest request) {
        Inventory coreInventory = MaterialChooserMenus.createInventory(request);
        if (!(coreInventory.getHolder() instanceof MaterialChooserHolder coreHolder)) {
            MaterialChooserMenus.open(player, request);
            return;
        }

        ContainerPageTwoHolder holder = new ContainerPageTwoHolder(coreHolder.request(), coreHolder);
        Inventory inventory = Bukkit.createInventory(holder, 27, title("&8ᴄᴏɴᴛᴀɪɴᴇʀs"));
        holder.inventory = inventory;
        fill(inventory);
        for (int slot = 10; slot <= 15; slot++) {
            inventory.setItem(slot, coreInventory.getItem(slot));
        }
        inventory.setItem(16, EditorItemFactory.item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ", List.of()));
        inventory.setItem(18, BUTTONS.previousPage(coreHolder.request().page(), coreHolder.maxPage()));
        inventory.setItem(GuiSlots.bottomMiddleSlot(3), BUTTONS.back());
        inventory.setItem(24, BUTTONS.search(coreHolder.request().filter()));
        if (!coreHolder.request().filter().isBlank()) {
            inventory.setItem(25, BUTTONS.clearSearch("materials"));
        }
        player.openInventory(inventory);
    }

    private void openDeleteConfirm(Player player, String tier) {
        MenuHolder holder = new MenuHolder(MenuType.CONFIRM_DELETE, tier);
        Inventory inventory = Bukkit.createInventory(holder, 27, title("&8ᴄᴏɴғɪʀᴍ"));
        holder.inventory = inventory;
        fill(inventory);

        inventory.setItem(11, EditorItemFactory.item(Material.LIME_DYE, "#3ecf8eConfirm", List.of("#ffffffDelete this sellwand.")));
        inventory.setItem(13, EditorItemFactory.item(Material.LAVA_BUCKET, "#ff5d73" + tier, List.of("#ffffffDelete this sellwand tier.")));
        inventory.setItem(15, EditorItemFactory.item(Material.RED_DYE, "#ff5d73Cancel", List.of("#ffffffKeep this sellwand.")));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Object topHolder = event.getView().getTopInventory().getHolder();
        int slot = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (slot < 0 || slot >= topSize) {
            return;
        }
        if (topHolder instanceof EntryBrowserHolder entryBrowserHolder) {
            event.setCancelled(true);
            handleEntryBrowserClick(player, event.getSlot(), entryBrowserHolder);
            return;
        }
        if (!(topHolder instanceof ContainerPageTwoHolder)
                && !(topHolder instanceof MaterialChooserHolder)
                && !(topHolder instanceof MenuHolder)) {
            return;
        }
        event.setCancelled(true);
        if (topHolder instanceof ContainerPageTwoHolder containerPageTwoHolder) {
            clickContainerPageTwo(player, containerPageTwoHolder, slot);
            return;
        }
        if (topHolder instanceof MaterialChooserHolder materialChooserHolder) {
            clickContainers(player, materialChooserHolder, slot);
            return;
        }
        if (!(topHolder instanceof MenuHolder holder)) {
            return;
        }

        switch (holder.type) {
            case MAIN -> clickMain(player, slot);
            case TIER -> clickTier(player, holder.value, slot, event.getCursor());
            case CONFIRM_DELETE -> clickConfirmDelete(player, holder.value, slot);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof EntryBrowserHolder)) {
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot >= 0 && rawSlot < top.getSize()) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void clickMain(Player player, int slot) {
        switch (slot) {
            case 10 -> openWands(player);
            case 11 -> openContainers(player);
            case 14 -> toggle(player, "permissions.per-tier", "tier-permissions", () -> openMain(player));
            case 15 -> toggle(player, "settings.confirm-to-sell", "confirm-to-sell", () -> openMain(player));
            case 16 -> toggle(player, "breakdown.enabled", "breakdown", () -> openMain(player));
            case 22 -> toggle(player, "settings.nested-containers", "nested-containers", () -> openMain(player));
            case 23 -> toggle(player, "settings.sell-empty-container-shells", "sell-empty-container-shells", () -> openMain(player));
            case 12 -> cycle(player, "settings.click-mode", "click-mode", CLICK_MODES, () -> openMain(player));
            case 13 -> toggle(player, "cooldown.enabled", "cooldown", () -> openMain(player));
            case 28 -> toggle(player, "protection.worldguard", "worldguard", () -> openMain(player));
            case 29 -> toggle(player, "feedback.actionbar.enabled", "actionbar", () -> openMain(player));
            case 30 -> toggle(player, "feedback.title.enabled", "title", () -> openMain(player));
            case 25 -> toggle(player, "feedback.sound.enabled", "sound", () -> openMain(player));
            case 24 -> toggle(player, "feedback.particle.enabled", "particle", () -> openMain(player));
            case 21 -> toggle(player, "feedback.hologram.enabled", "hologram", () -> openMain(player));
            case 20 -> toggle(player, "file-logging", "file-logging", () -> {
                syncFileLogging();
                if (configManager.config().getBoolean("file-logging", false)) {
                    fileLogger.info("Editor enabled file logging by " + player.getName() + ".");
                }
                openMain(player);
            });
            case 19 -> toggle(player, "native-dialogs.enabled", "native-dialogs", () -> {
                plugin.reloadDialogFoundation();
                openMain(player);
            });
            default -> {
            }
        }
    }

    private void handleEntryBrowserClick(Player player, int slot, EntryBrowserHolder holder) {
        EntryBrowserRequest request = holder.request();
        EntryBrowserClick click = EntryBrowserMenus.handleClick(slot, holder);
        switch (click.action()) {
            case ENTRY -> openTier(player, click.entryId());
            case ADD -> prompt(player, inputRequest(
                    "#03fc88New Sellwand",
                    "#a7b8b0Enter a new tier id.",
                    "#ffffffTier ID",
                    "",
                    "new tier id, example epic",
                    DialogButton.save(),
                    96
            ), () -> openWands(player, request.page(), request.filter()), input -> createTier(player, input));
            case BACK -> openMain(player);
            case SEARCH -> prompt(player, inputRequest(
                    "Search",
                    "#a7b8b0Filter sellwand tiers.",
                    "#ffffffSearch",
                    request.filter(),
                    "search text",
                    DialogButton.search("Apply"),
                    64
            ), () -> openWands(player, request.page(), request.filter()), input -> {
                openWands(player, 0, input);
                messages.send(player, "messages.editor-search", "{prefix}{muted}Search set to {theme}{query}{muted}.", Map.of("query", input));
            });
            case CLEAR_SEARCH -> {
                openWands(player, 0, "");
                messages.send(player, "messages.editor-search-cleared", "{prefix}{muted}Search cleared.");
            }
            case PREVIOUS_PAGE -> openWands(player, request.page() - 1, request.filter());
            case NEXT_PAGE -> openWands(player, request.page() + 1, request.filter());
            case NONE -> {
            }
        }
    }

    private void clickTier(Player player, String tier, int slot, ItemStack cursor) {
        if (tier == null || !wandService.isTier(tier)) {
            openWands(player);
            return;
        }
        switch (slot) {
            case 10 -> {
                if (cursor != null && !cursor.getType().isAir()) {
                    saveCursorItem(player, tier, cursor);
                    openTier(player, tier);
                } else {
                    promptMaterial(player, tier, () -> openTier(player, tier));
                }
            }
            case 11 -> toggleTier(player, tier, "glow", tier + ".glow", () -> openTier(player, tier));
            case 12 -> prompt(player, inputRequest(
                    "#03fc88Custom Model Data",
                    "#a7b8b0Enter custom model data, or 0 to disable.",
                    "#ffffffCustom Model Data",
                    Integer.toString(tierInt(tier, "custom-model-data", 0)),
                    "custom model data number, or 0 to disable",
                    DialogButton.save(),
                    32
            ), () -> openTier(player, tier), input -> {
                Integer value = parseInteger(input);
                if (value != null && value >= 0) {
                    saveTier(player, tier, "custom-model-data", value, tier + ".custom-model-data");
                } else {
                    messages.send(player, "messages.invalid-number", "{prefix}{bad}That number is invalid.");
                }
                openTier(player, tier);
            });
            case 14 -> prompt(player, inputRequest(
                    "#03fc88Multiplier",
                    "#a7b8b0Enter a decimal multiplier.",
                    "#ffffffMultiplier",
                    wandService.tierMultiplier(tier),
                    "decimal multiplier, example 1.5",
                    DialogButton.save(),
                    32
            ), () -> openTier(player, tier), input -> {
                Double value = parseDouble(input);
                if (value != null && value > 0) {
                    saveTier(player, tier, "multiplier", value, tier + ".multiplier");
                } else {
                    messages.send(player, "messages.invalid-number", "{prefix}{bad}That number is invalid.");
                }
                openTier(player, tier);
            });
            case 15 -> prompt(player, inputRequest(
                    "#03fc88Uses",
                    "#a7b8b0Enter uses, or -1 for unlimited.",
                    "#ffffffUses",
                    wandService.tierUses(tier),
                    "uses number, or -1 for unlimited",
                    DialogButton.save(),
                    32
            ), () -> openTier(player, tier), input -> {
                Integer value = parseInteger(input);
                if (value != null && (value == -1 || value > 0)) {
                    saveTier(player, tier, "uses", value, tier + ".uses");
                } else {
                    messages.send(player, "messages.invalid-number", "{prefix}{bad}That number is invalid.");
                }
                openTier(player, tier);
            });
            case 16 -> openDeleteConfirm(player, tier);
            case 22 -> openWands(player);
            default -> {
            }
        }
    }

    private void clickConfirmDelete(Player player, String tier, int slot) {
        if (tier == null || !wandService.isTier(tier)) {
            suppressConfirmClose.add(player.getUniqueId());
            openWands(player);
            return;
        }
        if (slot == 11) {
            suppressConfirmClose.add(player.getUniqueId());
            deleteTier(player, tier);
            return;
        }
        if (slot == 15) {
            suppressConfirmClose.add(player.getUniqueId());
            messages.send(player, "messages.editor-cancelled", "{prefix}{muted}Cancelled.");
            openTier(player, tier);
        }
    }

    private void clickContainers(Player player, MaterialChooserHolder holder, int slot) {
        MaterialChooserClick click = MaterialChooserMenus.handleClick(slot, holder);
        if (click.action() == MaterialChooserActionType.BACK) {
            openMain(player);
            return;
        }
        if (click.action() == MaterialChooserActionType.SEARCH) {
            prompt(player, inputRequest(
                    "Search",
                    "#a7b8b0Filter container materials.",
                    "#ffffffSearch",
                    holder.request().filter(),
                    "container material",
                    DialogButton.search("Apply"),
                    64
            ), () -> MaterialChooserMenus.open(player, holder.request()), input ->
                    MaterialChooserMenus.open(player, holder.request().withFilter(input)));
            return;
        }
        if (click.action() == MaterialChooserActionType.NEXT_PAGE && click.nextRequest().page() == 1) {
            openContainerPageTwo(player, click.nextRequest());
            return;
        }
        if (click.action() == MaterialChooserActionType.CLEAR_SEARCH
                || click.action() == MaterialChooserActionType.PREVIOUS_PAGE
                || click.action() == MaterialChooserActionType.NEXT_PAGE) {
            MaterialChooserMenus.open(player, click.nextRequest());
            return;
        }
        if (click.action() != MaterialChooserActionType.TOGGLE || click.material() == null) {
            return;
        }
        Material material = click.material();
        String path = "containers.enabled." + material.name();
        boolean enabled = !holder.request().isSelected(material);
        if (save(player, path, enabled, material.name())) {
            MaterialChooserMenus.open(player, holder.request().withSelectedMaterials(
                    MaterialSelections.toggled(holder.request().selectedMaterials(), material)));
        }
    }

    private void clickContainerPageTwo(Player player, ContainerPageTwoHolder holder, int slot) {
        if (slot == 16) {
            return;
        }
        if (slot == 18) {
            MaterialChooserMenus.open(player, holder.request().withPage(0));
            return;
        }
        if (slot == GuiSlots.bottomMiddleSlot(3)) {
            openMain(player);
            return;
        }
        if (slot == 24) {
            prompt(player, inputRequest(
                    "Search",
                    "#a7b8b0Filter container materials.",
                    "#ffffffSearch",
                    holder.request().filter(),
                    "container material",
                    DialogButton.search("Apply"),
                    64
            ), () -> openContainerPageTwo(player, holder.request()), input ->
                    MaterialChooserMenus.open(player, holder.request().withFilter(input)));
            return;
        }
        if (slot == 25 && !holder.request().filter().isBlank()) {
            MaterialChooserMenus.open(player, holder.request().withFilter(""));
            return;
        }

        MaterialChooserClick click = MaterialChooserMenus.handleClick(slot, holder.coreHolder());
        if (click.action() != MaterialChooserActionType.TOGGLE || click.material() == null) {
            return;
        }
        Material material = click.material();
        String path = "containers.enabled." + material.name();
        boolean enabled = !holder.request().isSelected(material);
        if (save(player, path, enabled, material.name())) {
            openContainerPageTwo(player, holder.request().withSelectedMaterials(
                    MaterialSelections.toggled(holder.request().selectedMaterials(), material)));
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !(event.getInventory().getHolder() instanceof MenuHolder holder)) {
            return;
        }
        if (holder.type != MenuType.CONFIRM_DELETE) {
            return;
        }
        if (suppressConfirmClose.remove(player.getUniqueId())) {
            return;
        }
        messages.send(player, "messages.editor-cancelled", "{prefix}{muted}Cancelled.");
        if (holder.value != null && player.isOnline() && wandService.isTier(holder.value)) {
            plugin.core().scheduler().runGlobal(() -> openTier(player, holder.value));
        }
    }

    private void createTier(Player player, String input) {
        String tier = normalizeTier(input);
        if (tier == null) {
            messages.send(player, "messages.invalid-id", "{prefix}{bad}Use a tier ID with 2-32 letters, numbers, dashes, or underscores.");
            openWands(player);
            return;
        }
        if (wandService.isTier(tier)) {
            messages.send(player, "messages.tier-exists", "{prefix}{bad}That sellwand already exists.");
            openWands(player);
            return;
        }
        configManager.createSellwand(tier);
        fileLogger.info("Editor created sellwand tier " + tier + " by " + player.getName() + ".");
        messages.send(player, "messages.editor-created", "{prefix}{good}Created sellwand {theme}{tier}{muted}.", Map.of("tier", tier));
        openTier(player, tier);
    }

    private void deleteTier(Player player, String tier) {
        if (wandService.tierIds().size() <= 1) {
            messages.send(player, "messages.cannot-delete-last-tier", "{prefix}{bad}You must keep at least one sellwand.");
            openTier(player, tier);
            return;
        }
        configManager.deleteSellwand(tier);
        fileLogger.info("Editor deleted sellwand tier " + tier + " by " + player.getName() + ".");
        messages.send(player, "messages.editor-deleted", "{prefix}{good}Deleted sellwand {theme}{tier}{muted}.", Map.of("tier", tier));
        openWands(player);
    }

    private void promptMaterial(Player player, String tier, Runnable reopen) {
        prompt(player, inputRequest(
                "#03fc88Material Input",
                "#a7b8b0Enter a Bukkit material name.",
                "#ffffffMaterial",
                wandService.tierMaterial(tier).name(),
                "Bukkit material name, example GOLDEN_HOE",
                DialogButton.save(),
                48
        ), reopen, input -> {
            Material material = MaterialTypes.match(input);
            if (material == null || material.isAir()) {
                messages.send(player, "messages.invalid-material", "{prefix}{bad}That material is invalid.");
            } else {
                saveTier(player, tier, "material", material.name(), tier + ".material");
            }
            reopen.run();
        });
    }

    private void saveCursorItem(Player player, String tier, ItemStack cursor) {
        ItemStack copy = FoItemStacks.cloneItem(cursor);
        copy.setAmount(1);
        configManager.setSellwandValue(tier, "material", copy.getType().name());
        ItemMeta meta = copy.getItemMeta();
        if (meta != null) {
            if (meta.hasDisplayName()) {
                configManager.setSellwandValue(tier, "name", meta.getDisplayName());
            }
            if (meta.hasLore()) {
                configManager.setSellwandValue(tier, "lore", meta.getLore());
            }
            configManager.setSellwandValue(tier, "custom-model-data", meta.hasCustomModelData() ? meta.getCustomModelData() : 0);
        }
        messages.send(player, "messages.editor-saved", "{prefix}{good}Saved {theme}{setting}{muted}.", Map.of("setting", tier + ".item"));
        fileLogger.info("Editor copied cursor item settings for " + tier + " by " + player.getName() + ".");
    }

    private void prompt(Player player, TextDialogRequest request, Runnable reopen, Consumer<String> consumer) {
        Runnable onCancel = () -> {
            messages.send(player, "messages.editor-cancelled", "{prefix}{muted}Cancelled.");
            reopen.run();
        };
        EditorDialogInputs.openTextFromInventory(
                plugin,
                plugin.core().inventoryCloseSuppressor(),
                plugin.core().dialogService(),
                player,
                request,
                consumer,
                onCancel
        );
    }

    private TextDialogRequest inputRequest(
            String title,
            String body,
            String fieldLabel,
            String currentValue,
            String placeholder,
            DialogButton submitButton,
            int maxLength
    ) {
        return new TextDialogRequest(
                title,
                List.of(body),
                fieldLabel,
                currentValue,
                placeholder,
                submitButton,
                DialogButton.cancel("Back"),
                320,
                300,
                maxLength,
                true,
                false,
                false
        );
    }

    private void toggle(Player player, String path, String setting, Runnable reopen) {
        boolean newValue = !configManager.config().getBoolean(path, false);
        save(player, path, newValue, setting);
        reopen.run();
    }

    private void cycle(Player player, String path, String setting, List<String> options, Runnable reopen) {
        String current = configManager.config().getString(path, options.getFirst());
        int index = options.indexOf(current);
        String next = options.get((index + 1) % options.size());
        save(player, path, next, setting);
        reopen.run();
    }

    private void toggleTier(Player player, String tier, String path, String setting, Runnable reopen) {
        boolean newValue = !tierBoolean(tier, path, false);
        saveTier(player, tier, path, newValue, setting);
        reopen.run();
    }

    private boolean save(Player player, String path, Object value, String setting) {
        EditorSaveResult result = settingSaver.save(path, value);
        if (!result.successful()) {
            fileLogger.warn("Editor could not save config " + path + ": " + result.errorMessage());
            messages.send(player, "messages.editor-save-failed", "{prefix}{bad}The setting could not be saved.");
            return false;
        }
        fileLogger.info("Editor saved config " + path + "=" + value + " by " + player.getName() + ".");
        messages.send(player, "messages.editor-saved", "{prefix}{good}Saved {theme}{setting}{muted}.", Map.of("setting", setting));
        return true;
    }

    private void saveTier(Player player, String tier, String path, Object value, String setting) {
        configManager.setSellwandValue(tier, path, value);
        fileLogger.info("Editor saved sellwand " + tier + "." + path + "=" + value + " by " + player.getName() + ".");
        messages.send(player, "messages.editor-saved", "{prefix}{good}Saved {theme}{setting}{muted}.", Map.of("setting", setting));
    }

    private ItemStack toggleItem(String name, boolean enabled, String description) {
        Material material = enabled ? Material.LIME_DYE : Material.RED_DYE;
        String color = enabled ? "#3ecf8e" : "#ff5d73";
        String status = enabled ? "Enabled" : "Disabled";
        return EditorItemFactory.item(material, color + name, List.of("#ffffff" + description, "", color + status));
    }

    private ItemStack cycleItem(String name, String current, List<String> options) {
        List<CycleOption> cycleOptions = options.stream()
                .map(option -> new CycleOption(option, option))
                .toList();
        return EditorItemFactory.cycle(messages, name, current, cycleOptions);
    }

    private void syncFileLogging() {
        fileLogger.configure(configManager.config().getBoolean("file-logging", false), false);
    }

    private void fill(Inventory inventory) {
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, EditorItemFactory.filler());
        }
    }

    private String normalizeTier(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.toLowerCase(Locale.ROOT).replace(' ', '_');
        return normalized.matches("[a-z0-9_-]{2,32}") ? normalized : null;
    }

    private String title(String fallback) {
        return FoText.color(fallback);
    }

    private Double parseDouble(String input) {
        var parsed = LargeNumberParser.parseDouble(input);
        return parsed.isPresent() ? parsed.getAsDouble() : null;
    }

    private Integer parseInteger(String input) {
        return LargeNumberParser.parse(input).map(value -> {
            try {
                return value.intValueExact();
            } catch (ArithmeticException exception) {
                return null;
            }
        }).orElse(null);
    }

    private boolean tierBoolean(String tier, String path, boolean fallback) {
        return configManager.sellwand(tier) == null ? fallback : configManager.sellwand(tier).getBoolean(path, fallback);
    }

    private int tierInt(String tier, String path, int fallback) {
        return configManager.sellwand(tier) == null ? fallback : configManager.sellwand(tier).getInt(path, fallback);
    }

    private enum MenuType {
        MAIN,
        TIER,
        CONFIRM_DELETE
    }

    private static final class MenuHolder implements InventoryHolder {
        private final MenuType type;
        private final String value;
        private Inventory inventory;

        private MenuHolder(MenuType type, String value) {
            this.type = type;
            this.value = value;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class ContainerPageTwoHolder implements InventoryHolder {
        private final MaterialChooserRequest request;
        private final MaterialChooserHolder coreHolder;
        private Inventory inventory;

        private ContainerPageTwoHolder(MaterialChooserRequest request, MaterialChooserHolder coreHolder) {
            this.request = request;
            this.coreHolder = coreHolder;
        }

        private MaterialChooserRequest request() {
            return request;
        }

        private MaterialChooserHolder coreHolder() {
            return coreHolder;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

}
