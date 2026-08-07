package me.foesio.foSellwands.wand;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableItemNBT;
import me.foesio.core.material.MaterialTypes;
import me.foesio.core.text.FoText;
import me.foesio.foSellwands.config.ConfigManager;
import me.foesio.foSellwands.hook.EconomyService;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

public final class WandService {
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final NamespacedKey wandIdKey;
    private final NamespacedKey uuidKey;
    private final NamespacedKey multiplierKey;
    private final NamespacedKey usesKey;
    private final NamespacedKey maxUsesKey;
    private final NamespacedKey soldItemsKey;
    private final NamespacedKey soldMoneyKey;

    public WandService(JavaPlugin plugin, ConfigManager configManager, EconomyService economyService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.wandIdKey = new NamespacedKey(plugin, "wand_id");
        this.uuidKey = new NamespacedKey(plugin, "uuid");
        this.multiplierKey = new NamespacedKey(plugin, "multiplier");
        this.usesKey = new NamespacedKey(plugin, "uses");
        this.maxUsesKey = new NamespacedKey(plugin, "max_uses");
        this.soldItemsKey = new NamespacedKey(plugin, "sold_items");
        this.soldMoneyKey = new NamespacedKey(plugin, "sold_money");
    }

    public ItemStack createWand(double multiplier, int uses) {
        return createWand("custom", multiplier, uses);
    }

    public ItemStack createWand(String tierId) {
        String normalized = normalizeTierId(tierId);
        double multiplier = tierConfig(normalized).getDouble("multiplier", defaultMultiplier());
        int uses = tierConfig(normalized).getInt("uses", defaultUses());
        return createWand(normalized, multiplier, uses);
    }

    public ItemStack createWand(String id, double multiplier, int uses) {
        String normalized = normalizeTierId(id);
        Material material = templateMaterial(normalized);
        ItemStack item = new ItemStack(material, 1);
        WandData data = new WandData(
                normalized,
                UUID.randomUUID(),
                multiplier,
                uses,
                uses,
                0,
                0,
                false
        );
        applyData(item, data);
        return item;
    }

    public boolean isTier(String tierId) {
        return configManager.hasSellwand(normalizeTierId(tierId));
    }

    public Set<String> tierIds() {
        return new LinkedHashSet<>(configManager.sellwandIds());
    }

    public String firstTierId() {
        return tierIds().stream().findFirst().orElse("basic");
    }

    public Material tierMaterial(String tierId) {
        return templateMaterial(tierId);
    }

    public String tierMultiplier(String tierId) {
        String normalized = normalizeTierId(tierId);
        return trim(tierConfig(normalized).getDouble("multiplier", defaultMultiplier()));
    }

    public String tierUses(String tierId) {
        String normalized = normalizeTierId(tierId);
        int uses = tierConfig(normalized).getInt("uses", defaultUses());
        return uses < 0 ? "∞" : Integer.toString(uses);
    }

    public Optional<WandData> read(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return Optional.empty();
        }
        Optional<WandData> own = readOwn(item);
        if (own.isPresent()) {
            return own;
        }
        if (!configManager.config().getBoolean("axsellwands.runtime-conversion", true)) {
            return Optional.empty();
        }
        return readAxSellwand(item);
    }

    public boolean isWandItem(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        if (readOwn(item).isPresent()) {
            return true;
        }
        try {
            return NBT.get(item, (Function<ReadableItemNBT, Boolean>) nbt -> nbt.hasTag("axsellwands-type"));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private Optional<WandData> readOwn(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String id = pdc.get(wandIdKey, PersistentDataType.STRING);
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String uuidRaw = pdc.get(uuidKey, PersistentDataType.STRING);
        UUID uuid = parseUuid(uuidRaw);
        double multiplier = defaultDouble(pdc.get(multiplierKey, PersistentDataType.DOUBLE), 1.0);
        int uses = defaultInt(pdc.get(usesKey, PersistentDataType.INTEGER), -1);
        int maxUses = defaultInt(pdc.get(maxUsesKey, PersistentDataType.INTEGER), uses);
        int soldItems = defaultInt(pdc.get(soldItemsKey, PersistentDataType.INTEGER), 0);
        double soldMoney = defaultDouble(pdc.get(soldMoneyKey, PersistentDataType.DOUBLE), 0);
        return Optional.of(new WandData(id, uuid, multiplier, uses, maxUses, soldItems, soldMoney, false));
    }

    private Optional<WandData> readAxSellwand(ItemStack item) {
        try {
            return NBT.get(item, nbt -> {
                if (!nbt.hasTag("axsellwands-type")) {
                    return Optional.<WandData>empty();
                }
                String type = nbt.getString("axsellwands-type");
                if (type == null || type.isBlank()) {
                    return Optional.<WandData>empty();
                }
                AxDefaults defaults = loadAxDefaults(type);
                double multiplier = nbt.hasTag("axsellwands-multiplier") ? nbt.getFloat("axsellwands-multiplier") : defaults.multiplier();
                int uses = nbt.hasTag("axsellwands-uses") ? nbt.getInteger("axsellwands-uses") : defaults.uses();
                int maxUses = nbt.hasTag("axsellwands-max-uses") ? nbt.getInteger("axsellwands-max-uses") : uses;
                int soldItems = nbt.hasTag("axsellwands-sold-amount") ? nbt.getInteger("axsellwands-sold-amount") : 0;
                double soldMoney = nbt.hasTag("axsellwands-sold-price") ? nbt.getDouble("axsellwands-sold-price") : 0D;
                return Optional.of(new WandData(type, UUID.randomUUID(), multiplier, uses, maxUses, soldItems, soldMoney, true));
            });
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private AxDefaults loadAxDefaults(String type) {
        if (!configManager.config().getBoolean("axsellwands.folder-mapping", true)) {
            return new AxDefaults(1.0D, -1);
        }
        String rawFolder = configManager.config().getString("axsellwands.folder", "plugins/AxSellwands/sellwands");
        File folder = new File(rawFolder == null ? "" : rawFolder);
        if (!folder.isAbsolute()) {
            File pluginsFolder = plugin.getDataFolder().getParentFile();
            File serverRoot = pluginsFolder == null ? plugin.getDataFolder() : pluginsFolder.getParentFile();
            folder = new File(serverRoot == null ? plugin.getDataFolder() : serverRoot, rawFolder);
        }
        File file = new File(folder, type + ".yml");
        if (!file.isFile()) {
            return new AxDefaults(1.0D, -1);
        }
        YamlConfiguration axConfig = YamlConfiguration.loadConfiguration(file);
        return new AxDefaults(
                axConfig.getDouble("multiplier", 1.0D),
                axConfig.getInt("uses", -1)
        );
    }

    public void applyData(ItemStack item, WandData data) {
        item.setType(templateMaterial(data.id()));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        String name = replace(templateString(data.id(), "name", "#03fc88sᴇʟʟᴡᴀɴᴅ"), data);
        meta.setDisplayName(FoText.color(name));

        List<String> lore = new ArrayList<>();
        for (String line : templateLore(data.id())) {
            lore.add(replace(line, data));
        }
        meta.setLore(FoText.color(lore));

        if (templateBoolean(data.id(), "glow", true)) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            meta.removeEnchant(Enchantment.UNBREAKING);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);

        int customModelData = customModelData(data);
        if (customModelData > 0) {
            meta.setCustomModelData(customModelData);
        } else if (meta.hasCustomModelData()) {
            meta.setCustomModelData(null);
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(wandIdKey, PersistentDataType.STRING, data.id());
        pdc.set(uuidKey, PersistentDataType.STRING, data.uuid().toString());
        pdc.set(multiplierKey, PersistentDataType.DOUBLE, data.multiplier());
        pdc.set(usesKey, PersistentDataType.INTEGER, data.uses());
        pdc.set(maxUsesKey, PersistentDataType.INTEGER, data.maxUses());
        pdc.set(soldItemsKey, PersistentDataType.INTEGER, data.soldItems());
        pdc.set(soldMoneyKey, PersistentDataType.DOUBLE, data.soldMoney());
        item.setItemMeta(meta);
    }

    public String formatMoney(double amount) {
        String pattern = configManager.config().getString("settings.number-format", "#,##0.##");
        return new DecimalFormat(pattern).format(amount);
    }

    public double defaultMultiplier() {
        String firstTier = firstTierId();
        return tierConfig(firstTier).getDouble("multiplier", 1.0D);
    }

    public int defaultUses() {
        String firstTier = firstTierId();
        return tierConfig(firstTier).getInt("uses", 100);
    }

    private String replace(String input, WandData data) {
        Map<String, String> replacements = Map.of(
                "{multiplier}", trim(data.multiplier()),
                "{uses}", data.usesDisplay(),
                "{max_uses}", data.maxUsesDisplay(),
                "{uses_bar}", usesBar(data),
                "{sold_items}", Integer.toString(data.soldItems()),
                "{sold_money}", formatMoney(data.soldMoney())
        );
        String output = input == null ? "" : input;
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            output = output.replace(entry.getKey(), entry.getValue());
        }
        return output;
    }

    private String trim(double value) {
        if (value == Math.rint(value)) {
            return Integer.toString((int) value);
        }
        return Double.toString(value);
    }

    private String usesBar(WandData data) {
        if (data.uses() < 0 || data.maxUses() < 0) {
            return "∞";
        }
        int max = Math.max(1, data.maxUses());
        int remaining = Math.max(0, data.uses());
        int filled = Math.max(0, Math.min(10, (int) Math.round((remaining / (double) max) * 10D)));
        return "#03fc88" + "|".repeat(filled) + "#a7b8b0" + "|".repeat(10 - filled);
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            return UUID.randomUUID();
        }
    }

    private int defaultInt(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private double defaultDouble(Double value, double fallback) {
        return value == null ? fallback : value;
    }

    private String normalizeTierId(String tierId) {
        if (tierId == null || tierId.isBlank()) {
            return "default";
        }
        return tierId.toLowerCase().replace(' ', '_');
    }

    private boolean hasTierValue(String tierId, String child) {
        return tierConfig(tierId).contains(child);
    }

    private Material templateMaterial(String tierId) {
        String raw = hasTierValue(tierId, "material")
                ? tierConfig(tierId).getString("material", "GOLDEN_HOE")
                : tierConfig(firstTierId()).getString("material", "GOLDEN_HOE");
        Material material = MaterialTypes.match(raw);
        return material == null ? Material.GOLDEN_HOE : material;
    }

    private String templateString(String tierId, String child, String fallback) {
        if (hasTierValue(tierId, child)) {
            return tierConfig(tierId).getString(child, fallback);
        }
        return tierConfig(firstTierId()).getString(child, fallback);
    }

    private boolean templateBoolean(String tierId, String child, boolean fallback) {
        if (hasTierValue(tierId, child)) {
            return tierConfig(tierId).getBoolean(child, fallback);
        }
        return tierConfig(firstTierId()).getBoolean(child, fallback);
    }

    private List<String> templateLore(String tierId) {
        if (hasTierValue(tierId, "lore")) {
            return tierConfig(tierId).getStringList("lore");
        }
        return tierConfig(firstTierId()).getStringList("lore");
    }

    private int customModelData(WandData data) {
        if (hasTierValue(data.id(), "custom-model-data")) {
            int tierValue = tierConfig(data.id()).getInt("custom-model-data", 0);
            if (tierValue > 0) {
                return tierValue;
            }
        }

        return tierConfig(firstTierId()).getInt("custom-model-data", 0);
    }

    private YamlConfiguration tierConfig(String tierId) {
        YamlConfiguration config = configManager.sellwand(normalizeTierId(tierId));
        if (config != null) {
            return config;
        }
        YamlConfiguration fallback = configManager.sellwand(firstTierId());
        return fallback == null ? new YamlConfiguration() : fallback;
    }

    private record AxDefaults(double multiplier, int uses) {
    }
}
