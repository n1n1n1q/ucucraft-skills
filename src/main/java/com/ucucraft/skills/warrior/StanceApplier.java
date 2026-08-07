package com.ucucraft.skills.warrior;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.Set;

/**
 * The single place that puts stance attribute modifiers on a player and takes them off. Attribute
 * modifiers persist on the player profile, so a leaked one is permanent — everything goes through
 * {@link #apply} (which diffs desired against applied) or {@link #clear}, keyed by a namespaced key
 * per attribute. Also used for the temporary buffs a war cry hands out.
 */
public final class StanceApplier {

    /** Every key prefix this class owns; {@link #clear} strips all of them. */
    private static final String[] PREFIXES = {"stance", "cry", "shred"};

    private final Plugin plugin;

    public StanceApplier(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Makes the entity's stance modifiers match {@code desired}; every attribute in {@code known}
     * that is not desired has its modifier removed. Unchanged values are left untouched.
     */
    public void apply(LivingEntity entity, Map<Attribute, Double> desired, Set<Attribute> known) {
        for (Attribute attribute : known) {
            set(entity, "stance", attribute, desired.getOrDefault(attribute, 0.0));
        }
        for (Map.Entry<Attribute, Double> e : desired.entrySet()) {
            if (!known.contains(e.getKey())) {
                set(entity, "stance", e.getKey(), e.getValue());
            }
        }
    }

    /** Removes every modifier this class can hand out — stance, war cry buff and armour shred. */
    public void clear(LivingEntity entity, Set<Attribute> known) {
        for (String prefix : PREFIXES) {
            for (Attribute attribute : known) {
                set(entity, prefix, attribute, 0.0);
            }
        }
    }

    /**
     * Sets a separately keyed modifier — war cry buffs ({@code cry}) and cleave's armour shred
     * ({@code shred}) both live here. An amount of 0 removes it.
     */
    public void set(LivingEntity entity, String prefix, Attribute attribute, double amount) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        NamespacedKey key = new NamespacedKey(plugin, prefix + "_" + attribute.getKey().getKey());
        AttributeModifier existing = null;
        for (AttributeModifier modifier : instance.getModifiers()) {
            if (key.equals(modifier.getKey())) {
                existing = modifier;
                break;
            }
        }
        if (existing != null) {
            if (existing.getAmount() == amount) {
                return;
            }
            instance.removeModifier(existing);
        }
        if (amount != 0) {
            instance.addModifier(new AttributeModifier(key, amount,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
        }
    }
}
