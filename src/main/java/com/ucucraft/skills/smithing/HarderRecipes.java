package com.ucucraft.skills.smithing;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Registers the "harder" (block-based) metal-armor recipes non-masters are forced onto. Same
 * config-driven shape as ucucraft-items' RecipeManager, trimmed to the shaped recipes we need.
 */
public final class HarderRecipes {

    private final Plugin plugin;
    private final List<NamespacedKey> registered = new ArrayList<>();
    private final Set<Material> results = EnumSet.noneOf(Material.class);

    public HarderRecipes(Plugin plugin) {
        this.plugin = plugin;
    }

    public void register(ConfigurationSection section) {
        unregister();
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(id);
            if (s == null || !s.getBoolean("enabled", true)) {
                continue;
            }
            try {
                NamespacedKey key = new NamespacedKey(plugin, "hard_" + id);
                ShapedRecipe recipe = build(key, s);
                if (Bukkit.addRecipe(recipe)) {
                    registered.add(key);
                    results.add(recipe.getResult().getType());
                }
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Failed to load harder recipe '" + id + "': " + e.getMessage());
            }
        }
    }

    public void unregister() {
        for (NamespacedKey key : registered) {
            Bukkit.removeRecipe(key);
        }
        registered.clear();
        results.clear();
    }

    /** Whether a harder replacement recipe exists for this result material. */
    public boolean covers(Material material) {
        return results.contains(material);
    }

    private ShapedRecipe build(NamespacedKey key, ConfigurationSection s) {
        ConfigurationSection result = s.getConfigurationSection("result");
        Material resultMaterial = material(result == null ? null : result.getString("material"));
        ItemStack output = new ItemStack(resultMaterial, result == null ? 1 : result.getInt("amount", 1));

        ShapedRecipe recipe = new ShapedRecipe(key, output);
        recipe.shape(s.getStringList("shape").toArray(new String[0]));
        ConfigurationSection ingredients = s.getConfigurationSection("ingredients");
        if (ingredients != null) {
            for (String symbol : ingredients.getKeys(false)) {
                recipe.setIngredient(symbol.charAt(0),
                        new RecipeChoice.MaterialChoice(material(ingredients.getString(symbol))));
            }
        }
        return recipe;
    }

    private Material material(String name) {
        Material material = Material.matchMaterial(name == null ? "" : name);
        if (material == null) {
            throw new IllegalArgumentException("unknown material: " + name);
        }
        return material;
    }
}
