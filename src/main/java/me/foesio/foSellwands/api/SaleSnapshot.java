package me.foesio.foSellwands.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Immutable proposed or completed sale values. History file persistence is separate. */
public record SaleSnapshot(UUID playerId, UUID worldId, int x, int y, int z, String tierId,
                           UUID wandId, int usesRemaining, int itemAmount, double money,
                           Map<String, Integer> materialAmounts, List<SaleLine> breakdown) {
    public SaleSnapshot {
        materialAmounts = Map.copyOf(materialAmounts);
        breakdown = List.copyOf(breakdown);
    }

    /** One display-name aggregate in the sale calculation. */
    public record SaleLine(String itemName, int amount, double money) {
    }
}
