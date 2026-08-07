package com.ucucraft.skills.thief;

import com.ucucraft.skills.config.ConfigManager;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

/**
 * Soft dependency on ucucraft-items' lockpick. Identified purely by reading that plugin's item-id
 * PDC string, so there is no compile-time link. Namespace/key/id are configurable under
 * {@code thief.lockpick}.
 */
public final class Lockpick {

    private final NamespacedKey key;
    private final String id;

    public Lockpick(ConfigManager config) {
        String namespace = config.raw().getString("thief.lockpick.namespace", "ucucraftitems");
        String keyName = config.raw().getString("thief.lockpick.key", "item_id");
        this.id = config.raw().getString("thief.lockpick.id", "lockpick").toLowerCase(Locale.ROOT);
        this.key = new NamespacedKey(namespace, keyName);
    }

    public boolean isLockpick(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        String value = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return value != null && value.toLowerCase(Locale.ROOT).equals(id);
    }
}
