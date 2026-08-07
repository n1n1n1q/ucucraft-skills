package com.ucucraft.skills.smithing;

import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlotGroup;

import java.util.Locale;

/** What a piece of gear is, and which equipment slots its modifiers apply in. */
public enum GearType {
    WEAPON(EquipmentSlotGroup.HAND),
    TOOL(EquipmentSlotGroup.HAND),
    ARMOR(EquipmentSlotGroup.ARMOR),
    BOW(EquipmentSlotGroup.HAND);

    private final EquipmentSlotGroup slotGroup;

    GearType(EquipmentSlotGroup slotGroup) {
        this.slotGroup = slotGroup;
    }

    public EquipmentSlotGroup slotGroup() {
        return slotGroup;
    }

    /** Classifies a material by name suffix, or null if it is not modifiable gear. */
    public static GearType of(Material material) {
        if (material == null) {
            return null;
        }
        String name = material.name().toUpperCase(Locale.ROOT);
        if (name.equals("BOW") || name.equals("CROSSBOW")) {
            return BOW;
        }
        if (name.endsWith("_SWORD") || name.equals("TRIDENT") || name.endsWith("_AXE")) {
            // Axes double as weapons; treated as weapons so damage modifiers make sense.
            return name.endsWith("_AXE") ? TOOL : WEAPON;
        }
        if (name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE")) {
            return TOOL;
        }
        if (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS")) {
            return ARMOR;
        }
        return null;
    }

    public static GearType fromId(String id) {
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
