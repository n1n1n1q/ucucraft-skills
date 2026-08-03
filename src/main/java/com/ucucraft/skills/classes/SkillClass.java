package com.ucucraft.skills.classes;

import org.bukkit.entity.Player;

/** A class/specialty. Numbers come from config; behaviour is defined here. */
public interface SkillClass {

    String id();

    /** Language key of the display name (resolved via LangManager). */
    String displayNameKey();

    /** XP required to advance from {@code level} to {@code level + 1}. */
    int xpForLevel(int level);

    /** (Re)apply this class's bonuses for the given level. */
    void applyBonuses(Player player, int level);

    /** Remove this class's bonuses. */
    void clearBonuses(Player player);
}
