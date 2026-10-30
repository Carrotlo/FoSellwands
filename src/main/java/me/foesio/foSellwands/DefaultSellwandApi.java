package me.foesio.foSellwands;

import me.foesio.foSellwands.api.SellwandApi;
import me.foesio.foSellwands.api.WandSnapshot;
import me.foesio.foSellwands.wand.WandData;
import me.foesio.foSellwands.wand.WandService;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;
import java.util.Set;

final class DefaultSellwandApi implements SellwandApi {
    private final JavaPlugin plugin;
    private final WandService wands;

    DefaultSellwandApi(JavaPlugin plugin, WandService wands) {
        this.plugin = plugin;
        this.wands = wands;
    }

    @Override
    public Set<String> tierIds() {
        requireEnabled();
        return Set.copyOf(wands.tierIds());
    }

    @Override
    public Optional<ItemStack> createTierWand(String tierId) {
        requireEnabled();
        return tierId != null && wands.isTier(tierId) ? Optional.of(wands.createWand(tierId)) : Optional.empty();
    }

    @Override
    public ItemStack createCustomWand(double multiplier, int uses) {
        requireEnabled();
        if (!Double.isFinite(multiplier) || multiplier <= 0 || (uses != -1 && uses <= 0)) {
            throw new IllegalArgumentException("Invalid wand multiplier or uses");
        }
        return wands.createWand(multiplier, uses);
    }

    @Override
    public Optional<WandSnapshot> readWand(ItemStack item) {
        requireEnabled();
        return wands.read(item).map(DefaultSellwandApi::snapshot);
    }

    private void requireEnabled() {
        if (!plugin.isEnabled()) {
            throw new IllegalStateException("FoSellwands is disabled");
        }
    }

    private static WandSnapshot snapshot(WandData data) {
        return new WandSnapshot(data.id(), data.axConverted() ? Optional.empty() : Optional.of(data.uuid()),
                data.multiplier(), data.uses(), data.maxUses(), data.soldItems(), data.soldMoney(), data.axConverted());
    }
}
