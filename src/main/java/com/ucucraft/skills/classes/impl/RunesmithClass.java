package com.ucucraft.skills.classes.impl;

import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.classes.SkillClass;
import org.bukkit.entity.Player;

/**
 * The runesmith (РУНАР). The second crafting class: it owns enchanting. It has no flat attribute
 * passive — carving, the level-gated abilities and the resonance proc all live in the
 * {@code runes} package, driven off {@link com.ucucraft.skills.classes.ClassManager#profile}
 * checks the same way the thief's do. Tunables live under {@code runes}, {@code enchanting} and
 * {@code classes.runesmith}.
 */
public final class RunesmithClass implements SkillClass {

    public static final String ID = "runesmith";

    private final ConfigManager config;

    public RunesmithClass(ConfigManager config) {
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
        // No flat passive; see the runes package.
    }

    @Override
    public void clearBonuses(Player player) {
        // No flat passive; see the runes package.
    }
}
