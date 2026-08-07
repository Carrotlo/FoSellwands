package me.foesio.foSellwands.listener;

import me.foesio.core.message.FoMessageService;
import me.foesio.foSellwands.config.ConfigManager;
import me.foesio.foSellwands.wand.WandService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;

public final class WandInventoryGuard implements Listener {
    private static final Set<InventoryType> BLOCKED_TYPES = blockedTypes();

    private final ConfigManager configManager;
    private final FoMessageService messages;
    private final WandService wandService;

    public WandInventoryGuard(ConfigManager configManager, FoMessageService messages, WandService wandService) {
        this.configManager = configManager;
        this.messages = messages;
        this.wandService = wandService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!enabled() || !isBlocked(event.getView().getTopInventory())) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        boolean clickedTop = event.getRawSlot() < topSize;
        ItemStack hotbar = null;
        if (event.getHotbarButton() >= 0 && event.getWhoClicked() instanceof Player player) {
            hotbar = player.getInventory().getItem(event.getHotbarButton());
        }

        boolean movesWandIntoBlockedInventory = clickedTop && (wandService.isWandItem(event.getCursor()) || wandService.isWandItem(hotbar));
        boolean editsWandAlreadyInBlockedInventory = clickedTop && wandService.isWandItem(event.getCurrentItem());
        boolean shiftMovesWandIntoBlockedInventory = event.isShiftClick() && !clickedTop && wandService.isWandItem(event.getCurrentItem());
        if (movesWandIntoBlockedInventory || editsWandAlreadyInBlockedInventory || shiftMovesWandIntoBlockedInventory) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                messages.send(player, "messages.wand-protected", "{prefix}{bad}Sellwands cannot be used in that inventory.");
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!enabled() || !isBlocked(event.getView().getTopInventory()) || !wandService.isWandItem(event.getOldCursor())) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player player) {
                    messages.send(player, "messages.wand-protected", "{prefix}{bad}Sellwands cannot be used in that inventory.");
                }
                return;
            }
        }
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

    private boolean isBlocked(Inventory inventory) {
        return BLOCKED_TYPES.contains(inventory.getType());
    }

    private boolean enabled() {
        return configManager.config().getBoolean("safety.block-wand-crafting", true);
    }

    private static Set<InventoryType> blockedTypes() {
        EnumSet<InventoryType> types = EnumSet.noneOf(InventoryType.class);
        addType(types, "ANVIL");
        addType(types, "GRINDSTONE");
        addType(types, "WORKBENCH");
        addType(types, "CRAFTING");
        addType(types, "SMITHING");
        addType(types, "SMITHING_NEW");
        return types;
    }

    private static void addType(Set<InventoryType> types, String name) {
        try {
            types.add(InventoryType.valueOf(name));
        } catch (IllegalArgumentException ignored) {
            // InventoryType names can be removed or renamed between server API versions.
        }
    }
}
