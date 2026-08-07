package com.ucucraft.skills.warrior;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.Set;

/**
 * One combat stance, loaded from a file under {@code stances/}. A stance is always a trade: the
 * {@code attributes} map holds the gain and the cost together, and every extra mechanic (cleave
 * splash, charge bonus, shred, ...) is a plain number under {@code effects}, so adding a stance is
 * a {@code .yml} rather than a code change.
 */
public record Stance(String id, String name, Material icon, Set<WeaponFamily> weapons, int minLevel,
                     Map<Attribute, Double> attributes, Map<Attribute, Double> charged,
                     Rolls rolls, Aura aura, Cry cry, Map<String, Double> effects) {

    /** Chances for the three combat rolls, all clamped by {@code warrior.combat.max-roll-chance}. */
    public record Rolls(double glance, double parry, double stun) {}

    /** Ambient aura. Shape differs per stance too, because colour alone fails colour-blind players. */
    public record Aura(Particle particle, Color color, Shape shape, int count, double size) {}

    /** The stance's war cry (level 4). Stances without a {@code warcry} block have none. */
    public record Cry(String id, int durationTicks, double radius,
                      Map<PotionEffectType, Integer> self,
                      Map<PotionEffectType, Integer> allies,
                      Map<PotionEffectType, Integer> hostile,
                      Map<Attribute, Double> buff,
                      Set<Trait> traits) {}

    public enum Shape { NONE, RING_LOW, ORBIT, TRAIL, ARC, WIDE_RING, PULSE, HELIX }

    /** Extra behaviour a war cry carries beyond its potion effects. */
    public enum Trait { FLEE, TAUNT, SWEEP, SHOCKWAVE, CONE, DUEL, STUN_NEXT_HIT, KNOCKBACK }

    /** True when the stance is active for an item of this material (else it is suspended). */
    public boolean appliesTo(Material material) {
        for (WeaponFamily family : weapons) {
            if (family.matches(material)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasCry() {
        return cry != null;
    }

    public double effect(String key, double fallback) {
        return effects.getOrDefault(key, fallback);
    }

    public boolean flag(String key) {
        return effect(key, 0) > 0;
    }
}
