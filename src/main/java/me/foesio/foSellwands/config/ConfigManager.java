package me.foesio.foSellwands.config;

import me.foesio.core.config.FoConfigDefaults;
import me.foesio.core.config.ResourceFiles;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ConfigManager {
    private static final List<String> DEFAULT_LORE = List.of(
            "",
            "#03fc88ɪɴғᴏ",
            " &8❙ &fMultiplier: #03fc88{multiplier}x",
            " &8❙ &fUses: #03fc88{uses}/{max_uses} &8({uses_bar}&8)",
            "",
            "#03fc88sᴛᴀᴛs",
            " &8❙ &fSold items: #03fc88{sold_items}",
            " &8❙ &fMoney made: #03fc88${sold_money}",
            "",
            "#03fc88▶ #a7b8b0Left click &8- &fInspect",
            "#03fc88▶ #a7b8b0Right click &8- &fSell"
    );

    private final JavaPlugin plugin;
    private File sellwandsFolder;
    private final Map<String, YamlConfiguration> sellwands = new LinkedHashMap<>();
    private final Map<String, File> sellwandFiles = new LinkedHashMap<>();

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        FoConfigDefaults.ensureDefaultConfig(plugin);
        plugin.saveConfig();

        this.sellwandsFolder = ResourceFiles.dataFile(plugin, "sellwands");
        loadSellwands();
    }

    public FileConfiguration config() {
        return plugin.getConfig();
    }

    public Set<String> sellwandIds() {
        return sellwands.keySet();
    }

    public YamlConfiguration sellwand(String id) {
        return sellwands.get(normalizeId(id));
    }

    public boolean hasSellwand(String id) {
        return sellwands.containsKey(normalizeId(id));
    }

    public void createSellwand(String id) {
        String normalized = normalizeId(id);
        YamlConfiguration file = new YamlConfiguration();
        file.set("material", "GOLDEN_HOE");
        file.set("glow", true);
        file.set("custom-model-data", 0);
        file.set("multiplier", 1.0D);
        file.set("uses", 100);
        file.set("name", "#03fc88" + normalized + " sᴇʟʟᴡᴀɴᴅ &8(&f{multiplier}x&8)");
        file.set("lore", DEFAULT_LORE);
        sellwands.put(normalized, file);
        sellwandFiles.put(normalized, ResourceFiles.dataFile(plugin, "sellwands/" + normalized + ".yml"));
        saveSellwand(normalized);
    }

    public void deleteSellwand(String id) {
        String normalized = normalizeId(id);
        File file = sellwandFiles.remove(normalized);
        sellwands.remove(normalized);
        if (file != null && file.exists() && !file.delete()) {
            plugin.getLogger().warning("Could not delete sellwand file " + file.getName());
        }
    }

    public void setSellwandValue(String id, String path, Object value) {
        String normalized = normalizeId(id);
        YamlConfiguration file = sellwands.get(normalized);
        if (file == null) {
            return;
        }
        file.set(path, value);
        saveSellwand(normalized);
    }

    public boolean isContainerEnabled(Material material) {
        String path = "containers.enabled." + material.name();
        return !config().contains(path) || config().getBoolean(path, true);
    }

    private void loadSellwands() {
        sellwands.clear();
        sellwandFiles.clear();
        if (!sellwandsFolder.exists() && !sellwandsFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create sellwands folder.");
        }
        migrateInlineSellwands();
        saveBundledSellwand("basic.yml");
        saveBundledSellwand("rare.yml");
        saveBundledSellwand("legendary.yml");
        File[] files = sellwandsFolder.listFiles((directory, name) -> name.endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String id = normalizeId(file.getName().substring(0, file.getName().length() - 4));
                sellwands.put(id, YamlConfiguration.loadConfiguration(file));
                sellwandFiles.put(id, file);
            }
        }
        if (sellwands.isEmpty()) {
            createDefaultSellwands();
        }
    }

    private void migrateInlineSellwands() {
        ConfigurationSection section = config().getConfigurationSection("wand.tiers");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            File target = ResourceFiles.dataFile(plugin, "sellwands/" + normalizeId(id) + ".yml");
            if (target.exists()) {
                continue;
            }
            YamlConfiguration file = new YamlConfiguration();
            ConfigurationSection tier = section.getConfigurationSection(id);
            if (tier == null) {
                continue;
            }
            for (String key : tier.getKeys(false)) {
                file.set(key, tier.get(key));
            }
            try {
                file.save(target);
            } catch (IOException exception) {
                plugin.getLogger().warning("Could not migrate sellwand " + id + ": " + exception.getMessage());
            }
        }
    }

    private void createDefaultSellwands() {
        createSellwand("basic");
        setSellwandValue("basic", "multiplier", 1.0D);
        setSellwandValue("basic", "uses", 100);
        setSellwandValue("basic", "name", "#03fc88ʙᴀsɪᴄ sᴇʟʟᴡᴀɴᴅ &8(&f{multiplier}x&8)");

        createSellwand("rare");
        setSellwandValue("rare", "multiplier", 1.5D);
        setSellwandValue("rare", "uses", 250);
        setSellwandValue("rare", "name", "#03fc88ʀᴀʀᴇ sᴇʟʟᴡᴀɴᴅ &8(&f{multiplier}x&8)");

        createSellwand("legendary");
        setSellwandValue("legendary", "multiplier", 2.0D);
        setSellwandValue("legendary", "uses", -1);
        setSellwandValue("legendary", "name", "#03fc88ʟᴇɢᴇɴᴅᴀʀʏ sᴇʟʟᴡᴀɴᴅ &8(&f{multiplier}x&8)");
    }

    private void saveBundledSellwand(String name) {
        ResourceFiles.saveDefault(plugin, "sellwands/" + name);
    }

    private void saveSellwand(String id) {
        YamlConfiguration file = sellwands.get(normalizeId(id));
        File target = sellwandFiles.get(normalizeId(id));
        if (file == null || target == null) {
            return;
        }
        try {
            file.save(target);
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not save sellwand " + id + ": " + exception.getMessage());
        }
    }

    private String normalizeId(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
