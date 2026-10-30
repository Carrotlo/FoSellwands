package me.foesio.foSellwands.api.event;

import me.foesio.foSellwands.api.SaleSnapshot;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired after a positive sale is calculated, before inventory, payout, or wand mutation. */
public final class SellwandPreSaleEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final SaleSnapshot sale;
    private boolean cancelled;

    public SellwandPreSaleEvent(Player player, SaleSnapshot sale, boolean asynchronous) {
        super(asynchronous);
        this.player = player;
        this.sale = sale;
    }

    public Player getPlayer() {
        return player;
    }

    public SaleSnapshot getSale() {
        return sale;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
