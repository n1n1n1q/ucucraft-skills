package com.ucucraft.skills.warrior;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired after a stun has passed its cooldown, immunity and diminishing-returns gates but before it
 * is applied. The duration is mutable, so an arena plugin can shorten a stun instead of vetoing it.
 */
public final class WarriorStunEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final LivingEntity target;
    private int durationTicks;
    private boolean cancelled;

    public WarriorStunEvent(Player player, LivingEntity target, int durationTicks) {
        super(player);
        this.target = target;
        this.durationTicks = durationTicks;
    }

    public LivingEntity target() {
        return target;
    }

    public int durationTicks() {
        return durationTicks;
    }

    public void durationTicks(int durationTicks) {
        this.durationTicks = durationTicks;
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
