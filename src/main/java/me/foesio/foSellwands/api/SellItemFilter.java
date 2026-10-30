package me.foesio.foSellwands.api;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Register with Bukkit ServicesManager to exclude items from sellwand sales. */
@FunctionalInterface
public interface SellItemFilter {
    /** Return false to keep the item and, for a nested container item, all its contents. */
    boolean canSell(Player player, Block sourceContainer, ItemStack item);
}
