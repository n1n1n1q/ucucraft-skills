package com.ucucraft.skills.smithing;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * Material tier of a gear item. {@code smithable} tiers participate in the modifier system
 * (blacksmith forge / regular-player debuffs); {@code masterOnly} tiers can only be crafted by a
 * sufficiently levelled blacksmith. Rose gold is detected via the {@link RoseGold} soft dependency.
 */
public enum GearTier {
    NONE(false, false),
    LEATHER(false, false),
    COPPER(false, false),
    CHAINMAIL(true, false),
    IRON(true, false),
    GOLD(true, true),
    DIAMOND(true, true),
    ROSE_GOLD(true, true),
    NETHERITE(false, false);

    private final boolean smithable;
    private final boolean masterOnly;

    GearTier(boolean smithable, boolean masterOnly) {
        this.smithable = smithable;
        this.masterOnly = masterOnly;
    }

    /** Whether this tier takes part in the modifier system (forge rolls / debuffs). */
    public boolean smithable() {
        return smithable;
    }

    /** Whether only a qualifying blacksmith may craft this tier. */
    public boolean masterOnly() {
        return masterOnly;
    }

    /** Regular players get a random debuff when crafting these (smithable but not master-locked). */
    public boolean debuffEligible() {
        return smithable && !masterOnly;
    }

    /** Resolves the tier of an item, honouring the rose-gold soft dependency. */
    public static GearTier of(ItemStack item, RoseGold roseGold) {
        if (item == null) {
            return NONE;
        }
        if (roseGold != null && roseGold.isRoseGold(item)) {
            return ROSE_GOLD;
        }
        String name = item.getType().name().toUpperCase(Locale.ROOT);
        if (name.startsWith("LEATHER_")) return LEATHER;
        if (name.startsWith("COPPER_")) return COPPER;
        if (name.startsWith("CHAINMAIL_")) return CHAINMAIL;
        if (name.startsWith("IRON_")) return IRON;
        if (name.startsWith("GOLDEN_")) return GOLD;
        if (name.startsWith("DIAMOND_")) return DIAMOND;
        if (name.startsWith("NETHERITE_")) return NETHERITE;
        return NONE;
    }

    public static GearTier of(Material material) {
        return of(new ItemStack(material), null);
    }
}
