package com.ucucraft.skills.classes.impl;

import com.ucucraft.skills.classes.SkillClass;
import com.ucucraft.skills.config.ConfigManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

/**
 * The blacksmith (РЕМІСНИК). Its real power is the crafting gates and forge modifier rolls handled
 * by {@code SmithingManager}; the passive here is a small, config-tuned knockback resistance.
 * Tunables live under {@code classes.blacksmith}.
 */
public final class BlacksmithClass implements SkillClass {

    public static final String ID = "blacksmith";

    private final ConfigManager config;

    public BlacksmithClass(ConfigManager config) {
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
        double perLevel = config.raw().getDouble("classes.blacksmith.knockback-resistance-per-level", 0.0);
        AttributeInstance resistance = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (resistance != null) {
            resistance.setBaseValue(perLevel * (level - 1));
        }
    }

    @Override
    public void clearBonuses(Player player) {
        AttributeInstance resistance = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
        if (resistance != null) {
            resistance.setBaseValue(0.0);
        }
    }
}
