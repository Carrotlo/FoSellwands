package me.foesio.foSellwands.api;

import java.util.Optional;
import java.util.UUID;

/** Immutable data stored on a wand item, not tier defaults. */
public record WandSnapshot(String tierId, Optional<UUID> wandId, double multiplier, int uses,
                           int maxUses, int soldItems, double soldMoney, boolean legacyAxSellwand) {
    public WandSnapshot {
        wandId = wandId == null ? Optional.empty() : wandId;
    }
}
