package com.ucucraft.skills.warrior;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a warrior's parry roll succeeds, before the incoming damage is cancelled. Cancelling
 * this event lets the hit through as if the parry had never happened.
 */
public final class WarriorParryEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Entity attacker;
    private final Stance stance;
    private final double damage;
    private boolean cancelled;

    public WarriorParryEvent(Player player, Entity attacker, Stance stance, double damage) {
        super(player);
        this.attacker = attacker;
        this.stance = stance;
        this.damage = damage;
    }

    public Entity attacker() {
        return attacker;
    }

    public Stance stance() {
        return stance;
    }

    /** The damage that will be cancelled if this event goes through. */
    public double damage() {
        return damage;
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
