package me.foesio.foSellwands.sell;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SellResult {
    private final java.util.List<org.bukkit.inventory.ItemStack> soldItems = new java.util.ArrayList<>();
    public void recordItem(org.bukkit.inventory.ItemStack item) { soldItems.add(item.clone()); }
    public java.util.List<org.bukkit.inventory.ItemStack> soldItems() { return soldItems.stream().map(org.bukkit.inventory.ItemStack::clone).toList(); }
    private int itemAmount;
    private double money;
    private boolean changed;
    private final Map<String, BreakdownEntry> breakdown = new LinkedHashMap<>();

    public int itemAmount() {
        return itemAmount;
    }

    public double money() {
        return money;
    }

    public boolean changed() {
        return changed;
    }

    public List<BreakdownEntry> breakdown() {
        return new ArrayList<>(breakdown.values());
    }

    public void add(String itemName, int amount, double price) {
        if (amount <= 0 || price <= 0) {
            return;
        }
        this.itemAmount += amount;
        this.money += price;
        this.changed = true;
        breakdown.compute(itemName, (key, previous) -> {
            if (previous == null) {
                return new BreakdownEntry(itemName, amount, price);
            }
            return previous.add(amount, price);
        });
    }

    public void merge(SellResult other) {
        if (other == null) {
            return;
        }
        this.itemAmount += other.itemAmount;
        this.soldItems.addAll(other.soldItems);
        this.money += other.money;
        this.changed = this.changed || other.changed;
        for (BreakdownEntry entry : other.breakdown.values()) {
            this.breakdown.compute(entry.itemName(), (key, previous) -> {
                if (previous == null) {
                    return entry;
                }
                return previous.add(entry.amount(), entry.price());
            });
        }
    }

    public record BreakdownEntry(String itemName, int amount, double price) {
        private BreakdownEntry add(int addedAmount, double addedPrice) {
            return new BreakdownEntry(itemName, amount + addedAmount, price + addedPrice);
        }
    }
}
