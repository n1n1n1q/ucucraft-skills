package com.ucucraft.skills.runes;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Loads rune definitions from {@code runes/}. Each {@code .yml} file is one rune; the file name
 * (minus extension) is both its id and the vanilla trim pattern it maps to. A pattern with no file
 * here is simply not a rune.
 */
public final class RuneRegistry {

    /** Bundled seed files, copied to the data folder on first run — one per vanilla trim pattern. */
    private static final String[] DEFAULTS = {
            "runes/coast.yml", "runes/dune.yml", "runes/eye.yml", "runes/host.yml",
            "runes/raiser.yml", "runes/rib.yml", "runes/sentry.yml", "runes/shaper.yml",
            "runes/silence.yml", "runes/snout.yml", "runes/spire.yml", "runes/tide.yml",
            "runes/vex.yml", "runes/ward.yml", "runes/wayfinder.yml", "runes/wild.yml",
            "runes/bolt.yml", "runes/flow.yml",
    };

    private final Plugin plugin;
    private final Map<String, Rune> byId = new LinkedHashMap<>();
    private final Map<Enchantment, Rune> byEnchantment = new LinkedHashMap<>();

    public RuneRegistry(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        byId.clear();
        byEnchantment.clear();
        seedDefaults();
        File dir = new File(plugin.getDataFolder(), "runes");
        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String id = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
                Rune rune = parse(id, YamlConfiguration.loadConfiguration(file));
                if (rune != null) {
                    byId.put(id, rune);
                    byEnchantment.put(rune.enchantment(), rune);
                }
            }
        }
        plugin.getLogger().info("Loaded " + byId.size() + " runes.");
    }

    private void seedDefaults() {
        for (String path : DEFAULTS) {
            if (plugin.getResource(path) != null && !new File(plugin.getDataFolder(), path).exists()) {
                plugin.saveResource(path, false);
            }
        }
    }

    private Rune parse(String id, YamlConfiguration yaml) {
        TrimPattern pattern = Registry.TRIM_PATTERN.get(NamespacedKey.minecraft(id));
        if (pattern == null) {
            plugin.getLogger().warning("Rune '" + id + "' does not name a vanilla trim pattern, skipping.");
            return null;
        }
        String enchantId = yaml.getString("enchantment");
        Enchantment enchantment = enchantId == null ? null
                : Registry.ENCHANTMENT.get(NamespacedKey.minecraft(enchantId.toLowerCase(Locale.ROOT)));
        if (enchantment == null) {
            plugin.getLogger().warning("Rune '" + id + "' references unknown enchantment '" + enchantId + "', skipping.");
            return null;
        }
        int minLevel = Math.max(1, yaml.getInt("min-class-level", 1));
        return new Rune(id, pattern, enchantment, minLevel);
    }

    public Rune get(String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    /** The rune (if any) whose pattern grants this exact vanilla enchantment. */
    public Rune byEnchantment(Enchantment enchantment) {
        return enchantment == null ? null : byEnchantment.get(enchantment);
    }

    public List<Rune> all() {
        return List.copyOf(byId.values());
    }
}
