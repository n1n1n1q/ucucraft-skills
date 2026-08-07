package com.ucucraft.skills.warrior;

import org.bukkit.entity.Player;

/**
 * Decides whether a warrior may hold a given stance. Other plugins install their own policy through
 * {@link StanceService#setPolicy(StancePolicy)} to gate stances per region, arena or game mode; a
 * disallowed stance is suspended (the warrior falls back to neutral) rather than removed.
 */
@FunctionalInterface
public interface StancePolicy {

    boolean allow(Player player, Stance stance);
}
