package com.ucucraft.skills.hunter;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/**
 * Decides whether a given target may be highlighted for a given hunter. Other plugins can install
 * their own policy through {@link GlowService#setPolicy(GlowPolicy)} to control exactly which
 * players trigger highlighting and which entities light up (ideally only the hunter's prey).
 */
@FunctionalInterface
public interface GlowPolicy {

    boolean allow(Player hunter, LivingEntity target);
}
