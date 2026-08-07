package com.ucucraft.skills.smithing;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loads modifier definitions from {@code modifiers/positive/} and {@code modifiers/negative/}.
 * Each {@code .yml} file is one modifier; the file name (minus extension) is its id.
 */
public final class ModifierRegistry {

    /** Bundled seed files, copied to the data folder on first run. */
    private static final String[] DEFAULTS = {
            "modifiers/positive/keen.yml",
            "modifiers/positive/brutal.yml",
            "modifiers/positive/reinforced.yml",
            "modifiers/positive/warding.yml",
            "modifiers/positive/precise.yml",
            "modifiers/positive/rapid.yml",
            "modifiers/positive/hawkeye.yml",
            "modifiers/negative/dull.yml",
            "modifiers/negative/brittle.yml",
            "modifiers/negative/heavy.yml",
            "modifiers/negative/frayed.yml",
    };

    private final Plugin plugin;
    private final Map<String, Modifier> byId = new LinkedHashMap<>();
    private final List<Modifier> positives = new ArrayList<>();
    private final List<Modifier> negatives = new ArrayList<>();

    public ModifierRegistry(Plugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        byId.clear();
        positives.clear();
        negatives.clear();
        seedDefaults();
        loadDir(new File(plugin.getDataFolder(), "modifiers/positive"), true);
        loadDir(new File(plugin.getDataFolder(), "modifiers/negative"), false);
        plugin.getLogger().info("Loaded " + positives.size() + " positive and "
                + negatives.size() + " negative modifiers.");
    }

    private void seedDefaults() {
        for (String path : DEFAULTS) {
            if (plugin.getResource(path) != null && !new File(plugin.getDataFolder(), path).exists()) {
                plugin.saveResource(path, false);
            }
        }
    }

    private void loadDir(File dir, boolean positiveDir) {
        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
            Modifier modifier = parse(id, YamlConfiguration.loadConfiguration(file), positiveDir);
            if (modifier != null) {
                register(modifier);
            }
        }
    }

    private Modifier parse(String id, YamlConfiguration yaml, boolean positiveDir) {
        boolean positive = yaml.getBoolean("positive", positiveDir);
        Set<GearType> categories = EnumSet.noneOf(GearType.class);
        for (String cat : yaml.getStringList("categories")) {
            GearType type = GearType.fromId(cat);
            if (type != null) {
                categories.add(type);
            }
        }
        if (categories.isEmpty()) {
            plugin.getLogger().warning("Modifier '" + id + "' has no valid categories, skipping.");
            return null;
        }
        ConfigurationSection tiersSection = yaml.getConfigurationSection("tiers");
        if (tiersSection == null) {
            plugin.getLogger().warning("Modifier '" + id + "' has no tiers, skipping.");
            return null;
        }
        Map<Integer, ModifierTier> tiers = new LinkedHashMap<>();
        for (String key : tiersSection.getKeys(false)) {
            int tier;
            try {
                tier = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                continue;
            }
            ConfigurationSection s = tiersSection.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            tiers.put(tier, new ModifierTier(tier,
                    s.getString("name", id),
                    parseAttributes(id, s.getConfigurationSection("attributes"))));
        }
        if (tiers.isEmpty()) {
            plugin.getLogger().warning("Modifier '" + id + "' defines no valid tiers, skipping.");
            return null;
        }
        return new Modifier(id, positive, categories, tiers);
    }

    private Map<Attribute, Double> parseAttributes(String id, ConfigurationSection section) {
        Map<Attribute, Double> attributes = new LinkedHashMap<>();
        if (section == null) {
            return attributes;
        }
        for (String key : section.getKeys(false)) {
            Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
            if (attribute == null) {
                plugin.getLogger().warning("Modifier '" + id + "' references unknown attribute '" + key + "'.");
                continue;
            }
            attributes.put(attribute, section.getDouble(key));
        }
        return attributes;
    }

    private void register(Modifier modifier) {
        byId.put(modifier.id(), modifier);
        (modifier.positive() ? positives : negatives).add(modifier);
    }

    public Modifier get(String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    public List<Modifier> positivesFor(GearType type) {
        return positives.stream().filter(m -> m.appliesTo(type)).toList();
    }

    public List<Modifier> negativesFor(GearType type) {
        return negatives.stream().filter(m -> m.appliesTo(type)).toList();
    }
}
