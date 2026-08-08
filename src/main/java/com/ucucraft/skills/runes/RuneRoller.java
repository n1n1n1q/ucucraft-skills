package com.ucucraft.skills.runes;

import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.minigame.MinigameResult;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.meta.trim.TrimMaterial;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Turns a carve's inputs (class level, material, minigame result) into an {@link Outcome}. Odds
 * live in {@code runes.levels.<1..4>}, the same shape as {@code smithing.levels}: losing the carve
 * minigame always fails the carve; winning it still rolls {@code base-chance + hit-bonus * hitRatio}
 * for a full-level WIN, falling back to a one-level-lower PARTIAL on a miss.
 */
public final class RuneRoller {

    /** Result of a carve attempt. */
    public enum Outcome { WIN, PARTIAL, FAIL }

    private final ConfigManager config;

    public RuneRoller(ConfigManager config) {
        this.config = config;
    }

    /** The trim material's tier (1-5), or 0 if it is not a mapped trim material. */
    public int materialTier(TrimMaterial material) {
        if (material == null) {
            return 0;
        }
        return config.raw().getInt("runes.materials." + material.key().value(), 0);
    }

    /** The enchantment level a given material tier grants on a full success. */
    public int tierLevel(int tier) {
        return config.raw().getInt("runes.tier-levels." + tier, tier);
    }

    /** The most times a template duplicated with this material tier may itself be duplicated. */
    public int maxCopies(int tier) {
        return config.raw().getInt("runes.duplication.max-copies." + tier, 1);
    }

    /** The highest material tier a runesmith of this class level may carve with. */
    public int maxTierFor(int classLevel) {
        ConfigurationSection cfg = levelSection(classLevel);
        return cfg == null ? 0 : cfg.getInt("max-tier", 0);
    }

    /** Decides the carve outcome from the class level and the carve minigame's result. */
    public Outcome roll(int classLevel, MinigameResult result, int maxScore) {
        if (!result.won()) {
            return Outcome.FAIL;
        }
        ConfigurationSection cfg = levelSection(classLevel);
        double hitRatio = maxScore > 0 ? Math.min(1.0, (double) result.score() / maxScore) : 1.0;
        double chance = cfg == null ? 0.5
                : clamp01(cfg.getDouble("base-chance", 0.5) + cfg.getDouble("hit-bonus", 0.3) * hitRatio);
        return ThreadLocalRandom.current().nextDouble() < chance ? Outcome.WIN : Outcome.PARTIAL;
    }

    private ConfigurationSection levelSection(int classLevel) {
        return config.raw().getConfigurationSection("runes.levels." + Math.max(1, Math.min(4, classLevel)));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
