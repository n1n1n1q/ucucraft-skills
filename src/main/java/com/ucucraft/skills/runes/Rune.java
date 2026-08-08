package com.ucucraft.skills.runes;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.trim.TrimPattern;

/**
 * One carveable rune: a vanilla smithing template pattern mapped to the vanilla enchantment it
 * grants. Loaded from {@code runes/<id>.yml} by {@link RuneRegistry} — {@code id} is the file name
 * and also the vanilla trim pattern's key.
 */
public record Rune(String id, TrimPattern pattern, Enchantment enchantment, int minClassLevel) {
}
