package com.ucucraft.skills.smithing;

import org.bukkit.attribute.Attribute;

import java.util.Map;

/**
 * One "legendary level" of a modifier: its proxy name (MiniMessage prefix shown on the item) and
 * the attribute deltas it applies.
 */
public record ModifierTier(int tier, String name, Map<Attribute, Double> attributes) {
}
