package com.ucucraft.skills.item;

import com.ucucraft.skills.SkillsPlugin;
import com.ucucraft.skills.classes.SkillClass;
import com.ucucraft.skills.classes.SkillClassRegistry;
import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.lang.LangManager;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

/** Builds and identifies the specialty scroll. The class id is stored in the item's PDC. */
public final class ScrollItem {

    private final NamespacedKey classKey;
    private final ConfigManager config;
    private final SkillClassRegistry registry;
    private final LangManager lang;

    public ScrollItem(SkillsPlugin plugin, ConfigManager config, SkillClassRegistry registry, LangManager lang) {
        this.classKey = new NamespacedKey(plugin, "scroll_class");
        this.config = config;
        this.registry = registry;
        this.lang = lang;
    }

    public ItemStack create(String classId) {
        Material material = Material.matchMaterial(config.raw().getString("scroll.material", "PAPER"));
        ItemStack item = new ItemStack(material == null ? Material.PAPER : material);
        ItemMeta meta = item.getItemMeta();

        SkillClass skillClass = registry.get(classId);
        String className = skillClass != null ? lang.plain(skillClass.displayNameKey()) : classId;
        meta.displayName(lang.parse(config.raw().getString("scroll.name", "<class>"),
                Map.of("class", className)));

        if (config.raw().getBoolean("scroll.glow", true)) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.getPersistentDataContainer().set(classKey, PersistentDataType.STRING, classId);
        item.setItemMeta(meta);
        return item;
    }

    /** Returns the class id stored on the item, or null if it isn't a scroll. */
    public String classOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .get(classKey, PersistentDataType.STRING);
    }
}
