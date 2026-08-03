package com.ucucraft.skills.config;

import com.ucucraft.skills.SkillsPlugin;
import org.bukkit.configuration.file.FileConfiguration;

/** Typed access to config.yml. */
public final class ConfigManager {

    private final SkillsPlugin plugin;

    public ConfigManager(SkillsPlugin plugin) {
        this.plugin = plugin;
    }

    public FileConfiguration raw() {
        return plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
    }

    public String prefix() {
        return raw().getString("prefix", "");
    }

    public String locale() {
        return raw().getString("locale", "en");
    }

    public String storageType() {
        return raw().getString("storage.type", "yaml");
    }

    public double baseXp() {
        return raw().getDouble("progression.base-xp", 100);
    }

    public double multiplier() {
        return raw().getDouble("progression.multiplier", 1.35);
    }

    public int maxLevel() {
        return raw().getInt("progression.max-level", 50);
    }
}
