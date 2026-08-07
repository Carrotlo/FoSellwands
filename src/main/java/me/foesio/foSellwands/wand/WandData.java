package me.foesio.foSellwands.wand;

import java.util.UUID;

public record WandData(
        String id,
        UUID uuid,
        double multiplier,
        int uses,
        int maxUses,
        int soldItems,
        double soldMoney,
        boolean axConverted
) {
    public WandData withSale(int newUses, int addedItems, double addedMoney) {
        return new WandData(id, uuid, multiplier, newUses, maxUses, soldItems + addedItems, soldMoney + addedMoney, axConverted);
    }

    public String usesDisplay() {
        return uses < 0 ? "∞" : Integer.toString(uses);
    }

    public String maxUsesDisplay() {
        return maxUses < 0 ? "∞" : Integer.toString(maxUses);
    }
}
