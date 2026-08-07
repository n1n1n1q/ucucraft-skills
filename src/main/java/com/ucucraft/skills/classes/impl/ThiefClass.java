package com.ucucraft.skills.classes.impl;

import com.ucucraft.skills.classes.SkillClass;
import com.ucucraft.skills.config.ConfigManager;
import org.bukkit.entity.Player;

/**
 * The thief (ЗЛОДІЙ). A stealth/utility class whose power is entirely ability-driven (lockpicking,
 * backstab, fall control, pickpocket, smoke screen) — see the {@code thief} package listeners. It
 * has no body attribute passive. Tunables live under {@code thief} in config.yml.
 */
public final class ThiefClass implements SkillClass {

    public static final String ID = "thief";

    private final ConfigManager config;

    public ThiefClass(ConfigManager config) {
        this.config = config;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayNameKey() {
        return "class-names." + ID;
    }

    @Override
    public int xpForLevel(int level) {
        return (int) Math.round(config.baseXp() * Math.pow(config.multiplier(), level - 1));
    }

    @Override
    public void applyBonuses(Player player, int level) {
        // No passive attribute; the thief's power is its per-level abilities (see thief package).
    }

    @Override
    public void clearBonuses(Player player) {
        // Nothing to revert.
    }
}
