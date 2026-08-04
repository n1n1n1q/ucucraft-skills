package com.ucucraft.skills.smithing;

import com.ucucraft.skills.config.ConfigManager;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Soft dependency on the ucucraft-items plugin. Rose gold gear is detected purely by reading the
 * other plugin's item-id PDC string, so there is no compile-time link and it degrades gracefully
 * when that plugin is absent. Namespace/key/prefix are all configurable.
 */
public final class RoseGold {

    private final NamespacedKey key;
    private final String idPrefix;

    public RoseGold(ConfigManager config) {
        String namespace = config.raw().getString("crafting.rose-gold.namespace", "ucucraftitems");
        String keyName = config.raw().getString("crafting.rose-gold.key", "item_id");
        this.idPrefix = config.raw().getString("crafting.rose-gold.id-prefix", "rose_gold")
                .toLowerCase(java.util.Locale.ROOT);
        this.key = new NamespacedKey(namespace, keyName);
    }

    public boolean isRoseGold(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String id = item.getItemMeta().getPersistentDataContainer()
                .get(key, PersistentDataType.STRING);
        return id != null && id.toLowerCase(java.util.Locale.ROOT).startsWith(idPrefix);
    }
}
