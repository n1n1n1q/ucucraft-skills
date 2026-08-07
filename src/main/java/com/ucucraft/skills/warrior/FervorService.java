package com.ucucraft.skills.warrior;

import com.ucucraft.skills.config.ConfigManager;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fervor (Запал): built by landing melee hits and parrying, drained by glances and by time out of
 * combat. Above {@code charge-threshold} each stance adds its {@code charged} attributes and the
 * aura brightens; a war cry spends it. Tunables: {@code warrior.fervor}.
 *
 * <p>With {@code warrior.fervor.enabled: false} nothing is tracked: no charged tier, no gates, and
 * war cries fall back to a plain cooldown.
 */
public final class FervorService {

    private final ConfigManager config;
    private final Map<UUID, Double> values = new HashMap<>();

    public FervorService(ConfigManager config) {
        this.config = config;
    }

    public boolean enabled() {
        return config.raw().getBoolean("warrior.fervor.enabled", true);
    }

    public double max() {
        return config.raw().getDouble("warrior.fervor.max", 100);
    }

    public double value(Player player) {
        return enabled() ? values.getOrDefault(player.getUniqueId(), 0.0) : 0.0;
    }

    /** Fervor as a 0..1 fraction of the cap; 0 when Fervor is disabled. */
    public double ratio(Player player) {
        double max = max();
        return max <= 0 ? 0 : Math.min(1.0, value(player) / max);
    }

    public void add(Player player, double amount) {
        if (!enabled()) {
            return;
        }
        double next = Math.max(0, Math.min(max(), value(player) + amount));
        values.put(player.getUniqueId(), next);
    }

    public boolean charged(Player player) {
        return enabled() && value(player) >= config.raw().getDouble("warrior.fervor.charge-threshold", 60);
    }

    /** True when the player meets a Fervor gate; always true while Fervor is disabled. */
    public boolean atLeast(Player player, double required) {
        return !enabled() || required <= 0 || value(player) >= required;
    }

    /** Spends everything above {@code floor}; returns the amount spent (0 when Fervor is off). */
    public double spendAbove(Player player, double floor) {
        if (!enabled()) {
            return 0;
        }
        double spent = Math.max(0, value(player) - floor);
        values.put(player.getUniqueId(), floor);
        return spent;
    }

    public void decay(Player player, double seconds) {
        add(player, -config.raw().getDouble("warrior.fervor.decay-per-second", 4) * seconds);
    }

    public void reset(Player player) {
        values.remove(player.getUniqueId());
    }
}
