package me.foesio.foSellwands.api;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Register with Bukkit ServicesManager to price items before built-in shop providers. */
@FunctionalInterface
public interface SellPriceProvider {
    /** Return the whole-stack sell price, or zero for unpriced. The passed item is a defensive copy. */
    double getSellPrice(Player player, ItemStack item);
}
