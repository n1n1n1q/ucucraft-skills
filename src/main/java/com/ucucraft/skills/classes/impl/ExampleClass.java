package com.ucucraft.skills.classes.impl;

import com.ucucraft.skills.classes.SkillClass;
import com.ucucraft.skills.config.ConfigManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

/** Reference class. All tunables live under classes.example in config.yml. */
public final class ExampleClass implements SkillClass {

    public static final String ID = "example";
    private static final double BASE_MAX_HEALTH = 20.0;

    private final ConfigManager config;

    public ExampleClass(ConfigManager config) {
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
        double perLevel = config.raw().getDouble("classes.example.max-health-per-level", 1.0);
        AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(BASE_MAX_HEALTH + perLevel * (level - 1));
        }
    }

    @Override
    public void clearBonuses(Player player) {
        AttributeInstance health = player.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(BASE_MAX_HEALTH);
        }
    }
}
