package me.foesio.foSellwands.api;

import org.bukkit.inventory.ItemStack;

import java.util.Optional;
import java.util.Set;

/** The supported FoSellwands item API. Obtain it through Bukkit services; call on the owning server thread, never asynchronously. */
public interface SellwandApi {
    /** Returns the currently configured tier IDs, as a detached set. */
    Set<String> tierIds();

    /** Returns a fresh wand for an existing tier, or empty if the tier does not exist. */
    Optional<ItemStack> createTierWand(String tierId);

    /** Returns a fresh custom wand. Multiplier must be finite and positive; uses must be -1 or positive. */
    ItemStack createCustomWand(double multiplier, int uses);

    /** Reads current item data without modifying the item. Legacy AxSellwands IDs are absent until conversion. */
    Optional<WandSnapshot> readWand(ItemStack item);
}
