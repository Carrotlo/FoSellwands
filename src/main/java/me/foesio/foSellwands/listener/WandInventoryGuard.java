package me.foesio.foSellwands.listener;

import me.foesio.foSellwands.config.ConfigManager;
import me.foesio.foSellwands.wand.WandService;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public final class WandInventoryGuard implements Listener {
    private final ConfigManager configManager;
    private final WandService wandService;

    public WandInventoryGuard(ConfigManager configManager, WandService wandService) {
        this.configManager = configManager;
        this.wandService = wandService;
    }

    @EventHandler
    public void onCraftPrepare(PrepareItemCraftEvent event) {
        if (!enabled()) {
            return;
        }
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (wandService.isWandItem(item)) {
                event.getInventory().setResult(new ItemStack(Material.AIR));
                return;
            }
        }
    }

    @EventHandler
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        clearResultIfContainsWand(event.getInventory());
        if (containsWand(event.getInventory())) {
            event.setResult(null);
        }
    }

    @EventHandler
    public void onGrindstonePrepare(PrepareGrindstoneEvent event) {
        if (containsWand(event.getInventory())) {
            event.setResult(null);
        }
    }

    @EventHandler
    public void onSmithingPrepare(PrepareSmithingEvent event) {
        if (containsWand(event.getInventory())) {
            event.setResult(null);
        }
    }

    private void clearResultIfContainsWand(Inventory inventory) {
        if (containsWand(inventory)) {
            inventory.setItem(2, null);
        }
    }

    private boolean containsWand(Inventory inventory) {
        if (!enabled()) {
            return false;
        }
        for (ItemStack item : inventory.getContents()) {
            if (wandService.isWandItem(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean enabled() {
        return configManager.config().getBoolean("safety.block-wand-crafting", true);
    }
}
