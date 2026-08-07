package com.ucucraft.skills.smithing;

import com.ucucraft.skills.config.ConfigManager;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Decides which modifier (if any) a craft/reroll produces. Positive rolls scale with blacksmith
 * level and minigame hits; regular players only ever roll debuffs. All odds live in {@code config.yml}
 * under {@code smithing.levels.<1..4>} and {@code smithing.regular}.
 */
public final class ModifierRoller {

    /** A rolled modifier at a specific tier. */
    public record Roll(Modifier modifier, int tier) {
    }

    private final ConfigManager config;
    private final ModifierRegistry registry;

    public ModifierRoller(ConfigManager config, ModifierRegistry registry) {
        this.config = config;
        this.registry = registry;
    }

    /** Positive roll for a blacksmith. Empty means "no modifier, but no debuff either". */
    public Optional<Roll> rollPositive(int level, int hits, int maxHits, GearType type) {
        return rollPositive("smithing", level, hits, maxHits, type);
    }

    /** Positive roll with no minigame component (level-only odds), reading {@code <base>.levels}. */
    public Optional<Roll> rollPositive(String base, int level, GearType type) {
        return rollPositive(base, level, 0, 1, type);
    }

    /**
     * Positive roll reading odds from {@code <base>.levels.<1..4>}, letting different classes
     * (smithing, hunter) share the logic with their own tuning. Empty means "no modifier".
     */
    public Optional<Roll> rollPositive(String base, int level, int hits, int maxHits, GearType type) {
        List<Modifier> pool = registry.positivesFor(type);
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        ConfigurationSection cfg = config.raw().getConfigurationSection(
                base + ".levels." + Math.max(1, Math.min(4, level)));
        if (cfg == null) {
            return Optional.empty();
        }
        double hitRatio = maxHits > 0 ? Math.min(1.0, (double) hits / maxHits) : 0.0;
        double chance = clamp01(cfg.getDouble("base-chance", 0.2) + cfg.getDouble("hit-bonus", 0.3) * hitRatio);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (rnd.nextDouble() > chance) {
            return Optional.empty();
        }

        int maxTier = cfg.getInt("max-tier", 2);
        int tier = weightedTier(cfg.getIntegerList("tier-weights"), maxTier);
        // More successes nudge the result up a tier.
        if (tier < maxTier && rnd.nextDouble() < hitRatio) {
            tier++;
        }

        Modifier modifier = pool.get(rnd.nextInt(pool.size()));
        tier = Math.min(tier, modifier.maxTier());
        return tier < 1 ? Optional.empty() : Optional.of(new Roll(modifier, tier));
    }

    /** Debuff roll for a regular crafter; empty when the debuff chance is not met. */
    public Optional<Roll> rollDebuff(GearType type) {
        return rollDebuff("smithing", type);
    }

    /** Debuff roll reading {@code <base>.regular.*}; empty when the debuff chance is not met. */
    public Optional<Roll> rollDebuff(String base, GearType type) {
        double chance = config.raw().getDouble(base + ".regular.debuff-chance", 0.5);
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (rnd.nextDouble() > chance) {
            return Optional.empty();
        }
        List<Modifier> pool = registry.negativesFor(type);
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        Modifier modifier = pool.get(rnd.nextInt(pool.size()));
        int maxDebuffTier = Math.min(config.raw().getInt(base + ".regular.max-debuff-tier", 2), modifier.maxTier());
        int tier = 1 + rnd.nextInt(Math.max(1, maxDebuffTier));
        return Optional.of(new Roll(modifier, tier));
    }

    private static int weightedTier(List<Integer> weights, int maxTier) {
        int total = 0;
        for (int i = 0; i < maxTier && i < weights.size(); i++) {
            total += Math.max(0, weights.get(i));
        }
        if (total <= 0) {
            return 1;
        }
        int pick = ThreadLocalRandom.current().nextInt(total);
        for (int i = 0; i < maxTier && i < weights.size(); i++) {
            pick -= Math.max(0, weights.get(i));
            if (pick < 0) {
                return i + 1;
            }
        }
        return 1;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
