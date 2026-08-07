package com.ucucraft.skills.classes.impl;

import com.ucucraft.skills.classes.SkillClass;
import com.ucucraft.skills.config.ConfigManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

/**
 * The hunter (МИСЛИВЕЦЬ). A ranged specialist: crafts and reforges bows/crossbows (the modifier
 * system, {@code HunterManager}) and gains combat abilities per level (slow arrow, prey highlight,
 * crossbow mastery, hit-stacking) handled by the listeners in the {@code hunter} package. The only
 * body passive is a small, config-tuned movement speed. Tunables live under {@code classes.hunter}.
 */
public final class HunterClass implements SkillClass {

    public static final String ID = "hunter";

    private final ConfigManager config;

    public HunterClass(ConfigManager config) {
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
        double perLevel = config.raw().getDouble("classes.hunter.movement-speed-per-level", 0.0);
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(0.1 + perLevel * (level - 1));
        }
    }

    @Override
    public void clearBonuses(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(0.1);
        }
    }
}
