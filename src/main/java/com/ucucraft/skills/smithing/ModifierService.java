package com.ucucraft.skills.smithing;

import com.ucucraft.skills.lang.LangManager;
import com.google.common.collect.Multimap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The single place that reads/writes a modifier on an item (PDC + attribute modifiers + name/lore). */
public final class ModifierService {

    private final Plugin plugin;
    private final LangManager lang;
    private final NamespacedKey idKey;
    private final NamespacedKey tierKey;
    private final NamespacedKey debuffKey;
    private final NamespacedKey rerollKey;

    public ModifierService(Plugin plugin, LangManager lang) {
        this.plugin = plugin;
        this.lang = lang;
        this.idKey = new NamespacedKey(plugin, "modifier_id");
        this.tierKey = new NamespacedKey(plugin, "modifier_tier");
        this.debuffKey = new NamespacedKey(plugin, "modifier_debuff");
        this.rerollKey = new NamespacedKey(plugin, "reroll_count");
    }

    /** Applies (or replaces) a modifier on the item, updating attributes, name and lore. */
    public void apply(ItemStack item, Modifier modifier, int tier) {
        ModifierTier data = modifier.tier(tier);
        if (data == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        removeOurAttributes(meta);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(idKey, PersistentDataType.STRING, modifier.id());
        pdc.set(tierKey, PersistentDataType.INTEGER, tier);
        pdc.set(debuffKey, PersistentDataType.BOOLEAN, !modifier.positive());

        EquipmentSlotGroup slot = slotGroup(item);
        for (Map.Entry<Attribute, Double> e : data.attributes().entrySet()) {
            NamespacedKey modKey = new NamespacedKey(plugin, "mod_" + e.getKey().getKey().getKey());
            meta.addAttributeModifier(e.getKey(), new AttributeModifier(
                    modKey, e.getValue(), AttributeModifier.Operation.ADD_NUMBER, slot));
        }

        Component proxy = lang.parse(data.name(), Map.of()).decoration(TextDecoration.ITALIC, false);
        meta.displayName(proxy.append(Component.text(" "))
                .append(Component.translatable(item.getType()))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(lang.component("smithing.modifier-lore", Map.of(
                "name", data.name(),
                "tier", stars(tier, modifier.maxTier())))
                .decoration(TextDecoration.ITALIC, false)));

        item.setItemMeta(meta);
    }

    /** Strips any modifier from the item, reverting attributes, name and lore. */
    public void clearModifier(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        removeOurAttributes(meta);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.remove(idKey);
        pdc.remove(tierKey);
        pdc.remove(debuffKey);
        meta.displayName(null);
        meta.lore(null);
        item.setItemMeta(meta);
    }

    public boolean hasModifier(ItemStack item) {
        return read(item, idKey, PersistentDataType.STRING) != null;
    }

    /** The stored modifier id, or null if the item carries none. */
    public String modifierId(ItemStack item) {
        return read(item, idKey, PersistentDataType.STRING);
    }

    /** The stored modifier tier, or 0 if the item carries none. */
    public int modifierTier(ItemStack item) {
        Integer tier = read(item, tierKey, PersistentDataType.INTEGER);
        return tier == null ? 0 : tier;
    }

    public boolean isDebuff(ItemStack item) {
        Boolean debuff = read(item, debuffKey, PersistentDataType.BOOLEAN);
        return debuff != null && debuff;
    }

    public int rerollCount(ItemStack item) {
        Integer count = read(item, rerollKey, PersistentDataType.INTEGER);
        return count == null ? 0 : count;
    }

    public void incrementReroll(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(rerollKey, PersistentDataType.INTEGER, rerollCount(item) + 1);
        item.setItemMeta(meta);
    }

    private <T, Z> Z read(ItemStack item, NamespacedKey key, PersistentDataType<T, Z> type) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key, type);
    }

    private void removeOurAttributes(ItemMeta meta) {
        Multimap<Attribute, AttributeModifier> modifiers = meta.getAttributeModifiers();
        if (modifiers == null) {
            return;
        }
        List<Map.Entry<Attribute, AttributeModifier>> ours = new ArrayList<>();
        for (Map.Entry<Attribute, AttributeModifier> e : modifiers.entries()) {
            if (e.getValue().getKey().getNamespace().equals(plugin.getName().toLowerCase(java.util.Locale.ROOT))) {
                ours.add(e);
            }
        }
        for (Map.Entry<Attribute, AttributeModifier> e : ours) {
            meta.removeAttributeModifier(e.getKey(), e.getValue());
        }
    }

    private EquipmentSlotGroup slotGroup(ItemStack item) {
        GearType type = GearType.of(item.getType());
        return type == null ? EquipmentSlotGroup.ANY : type.slotGroup();
    }

    private static String stars(int tier, int maxTier) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxTier; i++) {
            sb.append(i < tier ? '★' : '☆');
        }
        return sb.toString();
    }
}
