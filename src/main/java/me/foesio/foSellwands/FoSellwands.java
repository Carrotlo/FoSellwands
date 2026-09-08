package me.foesio.foSellwands;

import me.foesio.core.FoCoreContext;
import me.foesio.core.FoPluginCore;
import me.foesio.core.command.CommandVisibilityService;
import me.foesio.core.logging.FoFileLogger;
import me.foesio.core.message.FoMessageMigrations;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.core.sound.FoAdminSounds;
import me.foesio.core.sound.FoEditorSounds;
import me.foesio.core.sound.FoSoundMigrations;
import me.foesio.core.sound.FoSoundService;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foSellwands.command.FoSellwandsCommand;
import me.foesio.foSellwands.config.ConfigManager;
import me.foesio.foSellwands.gui.EditorManager;
import me.foesio.foSellwands.hook.EconomyService;
import me.foesio.foSellwands.hook.HistoryService;
import me.foesio.foSellwands.hook.HologramService;
import me.foesio.foSellwands.hook.ProtectionService;
import me.foesio.foSellwands.hook.ShopPriceService;
import me.foesio.foSellwands.listener.WandInventoryGuard;
import me.foesio.foSellwands.sell.SellService;
import me.foesio.foSellwands.wand.WandService;
import org.bukkit.plugin.java.JavaPlugin;

public final class FoSellwands extends JavaPlugin {
    private FoCoreContext core;
    private ConfigManager configManager;
    private FoMessageService messages;
    private FoReloadRegistry reloadRegistry;
    private FoSoundService sounds;
    private FoEditorSounds editorSounds;
    private FoAdminSounds adminSounds;
    private EconomyService economyService;
    private ShopPriceService shopPriceService;
    private FoFileLogger fileLogger;
    private CommandVisibilityService commandVisibility;
    private ProtectionService protectionService;
    private HistoryService historyService;
    private HologramService hologramService;
    private UpdateNoticeService updateNotices;
    private WandService wandService;
    private SellService sellService;
    private EditorManager editorManager;
    private WandInventoryGuard wandInventoryGuard;

    @Override
    public void onEnable() {
        this.configManager = new ConfigManager(this);
        this.configManager.load();
        this.core = FoPluginCore.create(this);
        this.core.warnIfNativeDialogsUnavailable();
        this.core.metrics(33184);
        this.sounds = core.createSounds(soundMigrations());
        this.editorSounds = FoEditorSounds.create(sounds);
        this.adminSounds = FoAdminSounds.create(sounds);
        this.messages = FoMessageService.load(this, messageMigrations());
        migrateSprites();
        this.fileLogger = FoFileLogger.create(this);
        this.fileLogger.configure(configManager.config().getBoolean("file-logging", false), true);
        fileLogger.info("Plugin enable started.");
        this.economyService = new EconomyService(this);
        this.shopPriceService = new ShopPriceService(this, configManager, fileLogger);
        this.protectionService = new ProtectionService(this, configManager, fileLogger);
        this.historyService = new HistoryService(this, configManager, core.scheduler(), fileLogger);
        this.hologramService = new HologramService(this, configManager, messages);
        this.wandService = new WandService(this, configManager, economyService);
        this.sellService = new SellService(this, configManager, messages, sounds, economyService, shopPriceService, protectionService, wandService, historyService, hologramService, fileLogger);
        this.editorManager = new EditorManager(this, configManager, messages, sounds, editorSounds, wandService, fileLogger);
        this.wandInventoryGuard = new WandInventoryGuard(configManager, wandService);

        if (!economyService.setup()) {
            fileLogger.warn("Vault economy provider missing. Disabling plugin.");
            getLogger().severe("Vault economy provider was not found. Disabling FoSellwands.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.updateNotices = core.createUpdateNotices(messages, "fosellwands", adminSounds);
        this.reloadRegistry = FoReloadRegistry.create()
                .add("config", configManager::load)
                .add("file-logging", this::reloadFileLogging)
                .add("sounds", sounds::reload)
                .addMessages(messages)
                .add("dialog-foundation", this::reloadDialogFoundation)
                .add("protection", protectionService::reload)
                .add("shop-price", shopPriceService::reload)
                .add("update-notices", () -> {
                    if (updateNotices != null) {
                        updateNotices.checkAsync();
                    }
                });

        getServer().getPluginManager().registerEvents(sellService, this);
        getServer().getPluginManager().registerEvents(editorManager, this);
        getServer().getPluginManager().registerEvents(hologramService, this);
        getServer().getPluginManager().registerEvents(wandInventoryGuard, this);
        historyService.start();
        hologramService.cleanupLoadedHolograms();

        FoSellwandsCommand.register(this, messages, wandService, editorManager, updateNotices, reloadRegistry);
        this.commandVisibility = CommandVisibilityService.builder(this)
                .hideWithout("fosellwands.admin", "fosellwandsadmin", "sellwandsadmin", "fosellwands", "sellwands")
                .register();

        fileLogger.info("Integrations: Vault economy=true, " + shopPriceService.describeIntegrations());
        updateNotices.start();
        fileLogger.info("Plugin enabled.");
    }

    @Override
    public void onDisable() {
        if (editorManager != null) {
            editorManager.close();
        }
        if (historyService != null) {
            historyService.close();
        }
        if (fileLogger != null) {
            fileLogger.shutdown();
        }
        if (commandVisibility != null) {
            commandVisibility.close();
            commandVisibility = null;
        }
        if (core != null) {
            core.close();
            core = null;
        }
    }

    private FoMessageMigrations messageMigrations() {
        return FoMessageMigrations.create()
                .removeExact("messages.prompt", "{prefix}{muted}Type {theme}{format} {muted}in chat, or {bad}cancel{muted}.")
                .removeExact("messages.native-dialogs-fallback", "{prefix}{bad}Native dialogs are not supported here. {muted}Using chat input.")
                .removeExact("messages.version-current", "{prefix}{muted}FoSellwands {theme}{version} {muted}Author: {theme}Carrotio{muted}. Latest version installed.")
                .removeExact("messages.version-update", "{prefix}{muted}FoSellwands {theme}{version} {muted}Author: {theme}Carrotio{muted}. Update: {theme}{latest} {muted}- {theme}{link}")
                .removeExact("messages.version-unknown", "{prefix}{muted}FoSellwands {theme}{version} {muted}Author: {theme}Carrotio{muted}. Latest version unknown.")
                .removeExact("messages.update-available", "{prefix}{muted}Update available: {theme}{latest} {muted}- {theme}{link}")
                .build();
    }

    private void migrateSprites() {
        messages.migrateToVersion(core.migrations(), 1, config -> {
            boolean changed = false;
            changed |= FoMessageService.addMissingToken(config, "tokens.prefix", ":golden_hoe:", null);
            changed |= FoMessageService.addMissingToken(config, "messages.reload", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "messages.reload-failed", ":redstone:");
            changed |= FoMessageService.addMissingToken(config, "messages.give", ":gold_ingot:");
            changed |= FoMessageService.addMissingToken(config, "messages.received", ":gold_ingot:");
            changed |= FoMessageService.addMissingToken(config, "messages.nothing-sold", ":redstone:");
            changed |= FoMessageService.addMissingToken(config, "messages.sold", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "messages.wand-broken", ":redstone:");
            changed |= FoMessageService.addMissingToken(config, "messages.editor-saved", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "messages.editor-created", ":emerald:");
            changed |= FoMessageService.addMissingToken(config, "messages.editor-deleted", ":lava_bucket:");
            return changed;
        });
        messages.reload();
    }

    private FoSoundMigrations soundMigrations() {
        return FoSoundMigrations.create()
                .add(sounds -> {
                    boolean changed = sounds.moveFromConfig("feedback.sound", "sell.success");
                    var migrated = sounds.configuration().getConfigurationSection("sell.success");
                    if (migrated != null && migrated.contains("sell")) {
                        migrated.set("sound", migrated.getString("sell"));
                        migrated.set("sell", null);
                        changed = true;
                    }
                    return changed;
                })
                .build();
    }

    public void reloadDialogFoundation() {
        if (core != null) {
            core.close();
            core = FoPluginCore.create(this);
            core.warnIfNativeDialogsUnavailable();
            core.metrics(33184);
        }
    }

    private void reloadFileLogging() {
        if (fileLogger != null) {
            fileLogger.configure(configManager.config().getBoolean("file-logging", false), false);
        }
    }

    public ConfigManager configManager() {
        return configManager;
    }

    public FoCoreContext core() {
        return core;
    }

    public FoMessageService messages() {
        return messages;
    }

    public UpdateNoticeService updateNotices() {
        return updateNotices;
    }

    public FoFileLogger fileLogger() {
        return fileLogger;
    }

    public FoSoundService sounds() {
        return sounds;
    }

    public FoAdminSounds adminSounds() {
        return adminSounds;
    }
}
