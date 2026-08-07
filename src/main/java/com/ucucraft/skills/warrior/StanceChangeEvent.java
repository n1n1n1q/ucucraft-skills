package com.ucucraft.skills.warrior;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/** Fired before a warrior starts the warm-up into a new stance. Cancelling keeps the old stance. */
public final class StanceChangeEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Stance from;
    private final Stance to;
    private boolean cancelled;

    public StanceChangeEvent(Player player, Stance from, Stance to) {
        super(player);
        this.from = from;
        this.to = to;
    }

    public Stance from() {
        return from;
    }

    public Stance to() {
        return to;
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
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
