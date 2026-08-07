package com.ucucraft.skills.warrior;

import org.bukkit.Material;

import java.util.Locale;

/**
 * Weapon families a stance can bind to. {@link #ANY} matches any held item, {@link #MELEE} matches
 * any of the four real families — a stance is suspended while the held item matches none of them.
 */
public enum WeaponFamily {
    SWORD, AXE, TRIDENT, MACE, ANY, MELEE;

    /** The family of a held material, or null when it is not a melee weapon. */
    public static WeaponFamily of(Material material) {
        if (material == null) {
            return null;
        }
        String name = material.name().toUpperCase(Locale.ROOT);
        if (name.endsWith("_SWORD")) {
            return SWORD;
        }
        if (name.endsWith("_AXE")) {
            return AXE;
        }
        if (name.equals("TRIDENT")) {
            return TRIDENT;
        }
        if (name.equals("MACE")) {
            return MACE;
        }
        return null;
    }

    public static WeaponFamily fromId(String id) {
        try {
            return valueOf(id.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean matches(Material material) {
        return switch (this) {
            case ANY -> true;
            case MELEE -> of(material) != null;
            default -> this == of(material);
        };
    }

    /** False when the family's item does not exist on the running server (MACE needs 1.21). */
    public boolean supported() {
        return switch (this) {
            case MACE -> Material.getMaterial("MACE") != null;
            case TRIDENT -> Material.getMaterial("TRIDENT") != null;
            default -> true;
        };
    }
}
