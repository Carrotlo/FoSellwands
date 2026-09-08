package me.foesio.foSellwands.hook;

import me.foesio.core.item.FoItemStacks;
import me.foesio.core.logging.FoFileLogger;
import me.foesio.foSellwands.config.ConfigManager;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ShopPriceService {
    /** Total stack payout, after the wand multiplier and any final price provider. */
    public record SaleQuote(double total, boolean reportToFoShop) {}
    private record PriceApi(Plugin owner, Object instance, Method quote, Method report) {}
    private Plugin checkedApiOwner;
    private boolean apiChecked;
    private PriceApi cachedPriceApi;
    private long nextApiWarning;

    public SaleQuote getFinalQuote(Player player, ItemStack item, double multiplier) {
        if (item == null || item.getType().isAir() || !Double.isFinite(multiplier) || multiplier <= 0)
            return new SaleQuote(0, false);
        for (String provider : providerPriority()) {
            double total;
            if (provider.equalsIgnoreCase("FoShop")) {
                try {
                    PriceApi api = priceApi();
                    if (api != null) {
                        Object result = api.quote().invoke(api.instance(), player, FoItemStacks.cloneItem(item), multiplier);
                        double unit = result instanceof Number number ? number.doubleValue() : 0;
                        total = unit * item.getAmount();
                        // A final API denial must not fall through to another provider.
                        return new SaleQuote(Double.isFinite(total) && total > 0 ? total : 0, true);
                    }
                } catch (ReflectiveOperationException | RuntimeException failure) {
                    warnApiFailure();
                    return new SaleQuote(0, false);
                }
                total = getFoShopPrice(player, item) * multiplier;
            } else if (provider.equalsIgnoreCase("ShopGUIPlus")) {
                total = getShopGuiPlusPrice(player, item) * multiplier;
            } else continue;
            if (Double.isFinite(total) && total > 0) return new SaleQuote(total, false);
        }
        return new SaleQuote(0, false);
    }

    public void reportSale(List<ItemStack> items) {
        if (items.isEmpty()) return;
        try {
            PriceApi api = priceApi();
            if (api != null) api.report().invoke(api.instance(), java.util.UUID.randomUUID(), "FoSellwands", items);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            // Payment is already committed. Never roll back or repeat a payout here.
            warnApiFailure();
        }
    }

    private PriceApi priceApi() throws ReflectiveOperationException {
        Plugin owner = plugin.getServer().getPluginManager().getPlugin("FoShop");
        if (owner == null || !owner.isEnabled()) return null;
        if (apiChecked && owner == checkedApiOwner) return cachedPriceApi;
        Method getter;
        try { getter = owner.getClass().getMethod("getPriceApi"); }
        catch (NoSuchMethodException legacy) {
            checkedApiOwner = owner; apiChecked = true; cachedPriceApi = null;
            return null;
        }
        Object api = getter.invoke(owner);
        PriceApi resolved = new PriceApi(owner, api,
                api.getClass().getMethod("quote", Player.class, ItemStack.class, double.class),
                api.getClass().getMethod("reportSale", java.util.UUID.class, String.class, List.class));
        checkedApiOwner = owner; cachedPriceApi = resolved; apiChecked = true;
        return resolved;
    }

    private void warnApiFailure() {
        long now = System.currentTimeMillis();
        if (now >= nextApiWarning) {
            nextApiWarning = now + 60000;
            fileLogger.warn("FoShop final-price API failed; affected quotes denied or completed sale notification missed.");
        }
    }
    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final FoFileLogger fileLogger;
    private boolean warnedNoProvider;
    private Plugin cachedFoShopPlugin;
    private Object cachedFoShopManager;
    private Method cachedFoShopPriceWithPlayer;
    private Method cachedFoShopLegacyPrice;
    private List<String> cachedProviderPriority = List.of("FoShop", "ShopGUIPlus");

    public ShopPriceService(JavaPlugin plugin, ConfigManager configManager, FoFileLogger fileLogger) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.fileLogger = fileLogger;
        refreshProviderPriority();
    }

    public double getSellPrice(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0D;
        }
        for (String provider : providerPriority()) {
            double price = switch (provider.toLowerCase(Locale.ROOT)) {
                case "foshop" -> getFoShopPrice(player, item);
                case "shopguiplus" -> getShopGuiPlusPrice(player, item);
                default -> 0D;
            };
            if (price > 0D && Double.isFinite(price)) {
                return price;
            }
        }
        if (!warnedNoProvider && !anyProviderEnabled()) {
            warnedNoProvider = true;
            fileLogger.warn("No sell price provider enabled. Install FoShop or ShopGUIPlus.");
        }
        return 0D;
    }

    public void reload() {
        warnedNoProvider = false;
        clearFoShopCache();
        apiChecked = false; checkedApiOwner = null; cachedPriceApi = null;
        refreshProviderPriority();
    }

    public String describeIntegrations() {
        return "FoShop=" + enabled("FoShop") + ", ShopGUIPlus=" + enabled("ShopGUIPlus");
    }

    private List<String> providerPriority() {
        return cachedProviderPriority;
    }

    private void refreshProviderPriority() {
        List<String> configured = configManager.config().getStringList("pricing.provider-priority");
        if (configured.isEmpty()) {
            cachedProviderPriority = List.of("FoShop", "ShopGUIPlus");
            return;
        }
        List<String> providers = new ArrayList<>();
        for (String provider : configured) {
            if (provider == null || provider.isBlank()) {
                continue;
            }
            providers.add(provider);
        }
        cachedProviderPriority = providers.isEmpty() ? List.of("FoShop", "ShopGUIPlus") : List.copyOf(providers);
    }

    private double getFoShopPrice(Player player, ItemStack item) {
        Plugin foShop = plugin.getServer().getPluginManager().getPlugin("FoShop");
        if (foShop == null || !foShop.isEnabled()) {
            return 0D;
        }
        try {
            Object rawPrice = invokeFoShopSellPrice(foShop, player, item);
            if (!(rawPrice instanceof Number number)) {
                return 0D;
            }
            double unitPrice = number.doubleValue();
            return unitPrice > 0D ? unitPrice * item.getAmount() : 0D;
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException | RuntimeException exception) {
            clearFoShopCache();
            fileLogger.warn("FoShop price hook failed: " + exception.getMessage());
            return 0D;
        }
    }

    private Object invokeFoShopSellPrice(Plugin foShop, Player player, ItemStack item)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        Object shopManager = cachedFoShopManager(foShop);
        Method priceWithPlayer = cachedFoShopPriceWithPlayer;
        if (priceWithPlayer != null) {
            return priceWithPlayer.invoke(shopManager, player, FoItemStacks.cloneItem(item));
        }
        return cachedFoShopLegacyPrice.invoke(shopManager, FoItemStacks.cloneItem(item));
    }

    private Object cachedFoShopManager(Plugin foShop) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        if (foShop == cachedFoShopPlugin && cachedFoShopManager != null
                && (cachedFoShopPriceWithPlayer != null || cachedFoShopLegacyPrice != null)) {
            return cachedFoShopManager;
        }

        Object shopManager = foShop.getClass().getMethod("getShopManager").invoke(foShop);
        cachedFoShopPriceWithPlayer = methodOrNull(shopManager, Player.class, ItemStack.class);
        cachedFoShopLegacyPrice = cachedFoShopPriceWithPlayer == null
                ? shopManager.getClass().getMethod("getSellPrice", ItemStack.class)
                : methodOrNull(shopManager, ItemStack.class);
        cachedFoShopPlugin = foShop;
        cachedFoShopManager = shopManager;
        return shopManager;
    }

    private Method methodOrNull(Object target, Class<?>... parameterTypes) {
        try {
            return target.getClass().getMethod("getSellPrice", parameterTypes);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private void clearFoShopCache() {
        cachedFoShopPlugin = null;
        cachedFoShopManager = null;
        cachedFoShopPriceWithPlayer = null;
        cachedFoShopLegacyPrice = null;
    }

    private double getShopGuiPlusPrice(Player player, ItemStack item) {
        Plugin shopGuiPlus = plugin.getServer().getPluginManager().getPlugin("ShopGUIPlus");
        if (shopGuiPlus == null || !shopGuiPlus.isEnabled()) {
            return 0D;
        }
        try {
            Class<?> apiClass = Class.forName("net.brcdev.shopgui.ShopGuiPlusApi", true, shopGuiPlus.getClass().getClassLoader());
            Method getPrice = apiClass.getMethod("getItemStackPriceSell", Player.class, ItemStack.class);
            Object rawPrice = getPrice.invoke(null, player, FoItemStacks.cloneItem(item));
            if (!(rawPrice instanceof Number number)) {
                return 0D;
            }
            double price = number.doubleValue();
            return price > 0D ? price : 0D;
        } catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException | NoSuchMethodException | RuntimeException exception) {
            fileLogger.warn("ShopGUIPlus price hook failed: " + exception.getMessage());
            return 0D;
        }
    }

    private boolean enabled(String name) {
        Plugin target = plugin.getServer().getPluginManager().getPlugin(name);
        return target != null && target.isEnabled();
    }

    private boolean anyProviderEnabled() {
        return enabled("FoShop") || enabled("ShopGUIPlus");
    }
}
