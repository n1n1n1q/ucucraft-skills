package com.ucucraft.skills.smithing;

import java.util.Map;
import java.util.Set;

/**
 * A weapon/tool/armor modifier loaded from a file under {@code modifiers/}. Positive modifiers are
 * blacksmith-only rewards; negative ones (debuffs) hit regular crafters and can be fixed by a smith.
 */
public record Modifier(String id, boolean positive, Set<GearType> categories,
                       Map<Integer, ModifierTier> tiers) {

    public boolean appliesTo(GearType type) {
        return type != null && categories.contains(type);
    }

    public ModifierTier tier(int tier) {
        return tiers.get(tier);
    }

    public int maxTier() {
        return tiers.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
    }
}
