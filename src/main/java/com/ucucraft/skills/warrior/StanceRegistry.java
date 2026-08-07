package com.ucucraft.skills.warrior;

import com.ucucraft.skills.config.ConfigManager;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loads stance definitions from {@code stances/}. Each {@code .yml} file is one stance and the file
 * name (minus extension) is its id. A stance whose weapon family or attributes are unsupported by
 * the running server is dropped with a warning instead of breaking the load.
 */
public final class StanceRegistry {

    /** Bundled seed files, copied to the data folder on first run. */
    private static final String[] DEFAULTS = {
            "stances/neutral.yml",
            "stances/onslaught.yml",
            "stances/guard.yml",
            "stances/skirmish.yml",
            "stances/phalanx.yml",
            "stances/cleave.yml",
            "stances/sunder.yml",
            "stances/riposte.yml",
    };

    private static final String NEUTRAL_ID = "neutral";

    private final Plugin plugin;
    private final ConfigManager config;
    private final Map<String, Stance> byId = new LinkedHashMap<>();
    private final Set<Attribute> touched = new LinkedHashSet<>();
    private Stance neutral;

    public StanceRegistry(Plugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void load() {
        byId.clear();
        touched.clear();
        touched.add(Attribute.ARMOR); // cleave's shred always targets it, stance files or not
        seedDefaults();

        Map<String, Integer> unlockLevels = unlockLevels();
        File dir = new File(plugin.getDataFolder(), "stances");
        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String id = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
                Stance stance = parse(id, YamlConfiguration.loadConfiguration(file), unlockLevels);
                if (stance != null) {
                    byId.put(id, stance);
                    touched.addAll(stance.attributes().keySet());
                    touched.addAll(stance.charged().keySet());
                    if (stance.hasCry()) {
                        touched.addAll(stance.cry().buff().keySet());
                    }
                }
            }
        }
        neutral = byId.get(NEUTRAL_ID);
        if (neutral == null) {
            neutral = fallbackNeutral();
            byId.put(NEUTRAL_ID, neutral);
        }
        plugin.getLogger().info("Loaded " + byId.size() + " warrior stances.");
    }

    private void seedDefaults() {
        for (String path : DEFAULTS) {
            if (plugin.getResource(path) != null && !new File(plugin.getDataFolder(), path).exists()) {
                plugin.saveResource(path, false);
            }
        }
    }

    /** Stance id to the class level that unlocks it, from {@code warrior.levels.<n>.stances}. */
    private Map<String, Integer> unlockLevels() {
        Map<String, Integer> levels = new LinkedHashMap<>();
        ConfigurationSection section = config.raw().getConfigurationSection("warrior.levels");
        if (section == null) {
            return levels;
        }
        for (String key : section.getKeys(false)) {
            int level;
            try {
                level = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                continue;
            }
            for (String id : section.getStringList(key + ".stances")) {
                levels.put(id.toLowerCase(Locale.ROOT), level);
            }
        }
        return levels;
    }

    private Stance parse(String id, YamlConfiguration yaml, Map<String, Integer> unlockLevels) {
        Set<WeaponFamily> weapons = EnumSet.noneOf(WeaponFamily.class);
        for (String raw : yaml.getStringList("applies-to")) {
            WeaponFamily family = WeaponFamily.fromId(raw);
            if (family == null) {
                plugin.getLogger().warning("Stance '" + id + "' names unknown weapon family '" + raw + "'.");
            } else if (family.supported()) {
                weapons.add(family);
            }
        }
        if (weapons.isEmpty()) {
            plugin.getLogger().warning("Stance '" + id + "' has no supported weapon family, skipping.");
            return null;
        }

        Map<Attribute, Double> attributes = attributes(id, yaml.getConfigurationSection("attributes"));
        Map<Attribute, Double> charged = attributes(id, yaml.getConfigurationSection("charged"));
        if (attributes == null || charged == null) {
            plugin.getLogger().warning("Stance '" + id + "' needs an attribute this server lacks, skipping.");
            return null;
        }

        int minLevel = unlockLevels.getOrDefault(id, yaml.getInt("min-level", 1));
        return new Stance(id,
                yaml.getString("name", id),
                material(yaml.getString("icon"), Material.IRON_SWORD),
                weapons,
                minLevel,
                attributes,
                charged,
                rolls(yaml.getConfigurationSection("combat")),
                aura(id, yaml.getConfigurationSection("particles")),
                cry(id, yaml.getConfigurationSection("warcry")),
                effects(yaml.getConfigurationSection("effects")));
    }

    /** Parses an attribute block, or null when it references an attribute this server lacks. */
    private Map<Attribute, Double> attributes(String id, ConfigurationSection section) {
        Map<Attribute, Double> values = new LinkedHashMap<>();
        if (section == null) {
            return values;
        }
        for (String key : section.getKeys(false)) {
            Attribute attribute = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
            if (attribute == null) {
                plugin.getLogger().warning("Stance '" + id + "' references unknown attribute '" + key + "'.");
                return null;
            }
            values.put(attribute, section.getDouble(key));
        }
        return values;
    }

    private Stance.Rolls rolls(ConfigurationSection section) {
        if (section == null) {
            return new Stance.Rolls(0, 0, 0);
        }
        return new Stance.Rolls(
                section.getDouble("glance-chance", 0),
                section.getDouble("parry-chance", 0),
                section.getDouble("stun-chance", 0));
    }

    private Stance.Aura aura(String id, ConfigurationSection section) {
        if (section == null) {
            return new Stance.Aura(Particle.DUST, Color.WHITE, Stance.Shape.NONE, 0, 1.0);
        }
        Particle particle;
        try {
            particle = Particle.valueOf(section.getString("particle", "DUST").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Stance '" + id + "' names unknown particle, falling back to DUST.");
            particle = Particle.DUST;
        }
        Stance.Shape shape;
        try {
            shape = Stance.Shape.valueOf(section.getString("shape", "NONE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            shape = Stance.Shape.NONE;
        }
        return new Stance.Aura(particle, color(section.getString("color", "#FFFFFF")), shape,
                section.getInt("count", 6), section.getDouble("size", 1.0));
    }

    private Stance.Cry cry(String id, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        Set<Stance.Trait> traits = EnumSet.noneOf(Stance.Trait.class);
        List<String> names = new ArrayList<>(section.getStringList("traits"));
        // The shorthand form (`flee: true`) is accepted alongside the `traits:` list.
        for (Stance.Trait trait : Stance.Trait.values()) {
            if (section.getBoolean(trait.name().toLowerCase(Locale.ROOT).replace('_', '-'), false)) {
                names.add(trait.name());
            }
        }
        for (String raw : names) {
            try {
                traits.add(Stance.Trait.valueOf(raw.toUpperCase(Locale.ROOT).replace('-', '_')));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Stance '" + id + "' names unknown war cry trait '" + raw + "'.");
            }
        }
        Map<Attribute, Double> buff = attributes(id, section.getConfigurationSection("buff-attributes"));
        return new Stance.Cry(section.getString("id", id),
                (int) Math.round(section.getDouble("duration-seconds", 6) * 20),
                section.getDouble("radius", 0),
                effectMap(id, section.getConfigurationSection("self-effects")),
                effectMap(id, section.getConfigurationSection("ally-effects")),
                effectMap(id, section.getConfigurationSection("hostile-effects")),
                buff == null ? Map.of() : buff,
                traits);
    }

    private Map<PotionEffectType, Integer> effectMap(String id, ConfigurationSection section) {
        Map<PotionEffectType, Integer> effects = new LinkedHashMap<>();
        if (section == null) {
            return effects;
        }
        for (String key : section.getKeys(false)) {
            PotionEffectType type = Registry.EFFECT.get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
            if (type == null) {
                plugin.getLogger().warning("Stance '" + id + "' references unknown potion effect '" + key + "'.");
                continue;
            }
            effects.put(type, section.getInt(key));
        }
        return effects;
    }

    /** Free-form numbers driving stance-specific mechanics; booleans read as 1/0. */
    private Map<String, Double> effects(ConfigurationSection section) {
        Map<String, Double> values = new LinkedHashMap<>();
        if (section == null) {
            return values;
        }
        for (String key : section.getKeys(true)) {
            Object value = section.get(key);
            if (value instanceof Boolean bool) {
                values.put(key, bool ? 1.0 : 0.0);
            } else if (value instanceof Number number) {
                values.put(key, number.doubleValue());
            }
        }
        return values;
    }

    private Color color(String hex) {
        try {
            return Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16));
        } catch (IllegalArgumentException e) {
            return Color.WHITE;
        }
    }

    private Material material(String name, Material fallback) {
        Material material = name == null ? null : Material.getMaterial(name.toUpperCase(Locale.ROOT));
        return material == null ? fallback : material;
    }

    private Stance fallbackNeutral() {
        return new Stance(NEUTRAL_ID, NEUTRAL_ID, Material.IRON_SWORD, EnumSet.of(WeaponFamily.ANY), 1,
                Map.of(), Map.of(), new Stance.Rolls(0, 0, 0),
                new Stance.Aura(Particle.DUST, Color.WHITE, Stance.Shape.NONE, 0, 1.0), null, Map.of());
    }

    /** The always-available fallback stance; never null after {@link #load()}. */
    public Stance neutral() {
        return neutral;
    }

    public Stance get(String id) {
        return id == null ? null : byId.get(id.toLowerCase(Locale.ROOT));
    }

    public List<Stance> all() {
        return List.copyOf(byId.values());
    }

    /**
     * Every attribute any stance can touch, including war cry buffs and the shred target — what
     * {@link StanceApplier} diffs against and, more importantly, what it has to strip.
     */
    public Set<Attribute> attributes() {
        return touched;
    }
}
