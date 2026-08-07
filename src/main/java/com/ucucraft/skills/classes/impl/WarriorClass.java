package com.ucucraft.skills.classes.impl;

import com.ucucraft.skills.classes.SkillClass;
import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.warrior.StanceService;
import org.bukkit.entity.Player;

/**
 * The warrior (ВОЇН). The first melee class: it crafts nothing and has no flat attribute passive —
 * its power is the stance, a persistent combat mode bound to the weapon in hand (the {@code warrior}
 * package). Levels unlock more stances rather than bigger numbers, so "applying bonuses" here just
 * means recomputing the active stance. Tunables live under {@code warrior} and {@code classes.warrior}.
 */
public final class WarriorClass implements SkillClass {

    public static final String ID = "warrior";

    private final ConfigManager config;
    private StanceService stances;

    public WarriorClass(ConfigManager config) {
        this.config = config;
    }

    /** Injected once {@link StanceService} exists, which needs the class registry to be built first. */
    public void bind(StanceService stances) {
        this.stances = stances;
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
        if (stances != null) {
            stances.refresh(player);
        }
    }

    @Override
    public void clearBonuses(Player player) {
        if (stances != null) {
            stances.reset(player);
        }
    }
}
