package com.ucucraft.skills.warrior;

import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.thief.CountriesHook;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Target selection and area damage shared by {@link WarriorCombat} and {@link WarCryService}:
 * who a warrior is allowed to hit (world PvP flag and Countries relations — a warrior never
 * splashes their own country), the cone/radius pickers, the armour-shred stacks and the splash
 * itself.
 */
public final class WarriorTargets {

    private final Plugin plugin;
    private final ConfigManager config;
    private final CountriesHook countries;
    private final StanceApplier applier;
    private final Set<UUID> splashing = new HashSet<>();
    private final Map<UUID, Integer> shredStacks = new HashMap<>();
    private final Map<UUID, Long> shredExpiry = new HashMap<>();

    public WarriorTargets(Plugin plugin, ConfigManager config, CountriesHook countries,
                          StanceApplier applier) {
        this.plugin = plugin;
        this.config = config;
        this.countries = countries;
        this.applier = applier;
    }

    /** True when the warrior may damage this entity: PvP must be on and they must not be allies. */
    public boolean canHarm(Player warrior, Entity target) {
        if (!(target instanceof LivingEntity living) || target instanceof ArmorStand
                || !living.isValid() || target.getUniqueId().equals(warrior.getUniqueId())) {
            return false;
        }
        if (target instanceof Player other) {
            return warrior.getWorld().getPVP() && !countries.friendly(warrior, other);
        }
        return true;
    }

    /** Players in radius the warrior may not harm — their allies, plus themselves. */
    public List<Player> allies(Player warrior, double radius) {
        List<Player> allies = new ArrayList<>();
        allies.add(warrior);
        for (Entity nearby : warrior.getNearbyEntities(radius, radius, radius)) {
            if (nearby instanceof Player other && !canHarm(warrior, other)) {
                allies.add(other);
            }
        }
        return allies;
    }

    public List<LivingEntity> hostiles(Player warrior, double radius, int max) {
        List<LivingEntity> targets = new ArrayList<>();
        for (Entity nearby : warrior.getNearbyEntities(radius, radius, radius)) {
            if (canHarm(warrior, nearby)) {
                targets.add((LivingEntity) nearby);
                if (max > 0 && targets.size() >= max) {
                    break;
                }
            }
        }
        return targets;
    }

    /** Hostiles inside a frontal cone of {@code degrees} around where the warrior is looking. */
    public List<LivingEntity> cone(Player warrior, double radius, double degrees) {
        Vector facing = warrior.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() < 0.001) {
            return List.of();
        }
        facing = facing.normalize();
        double threshold = Math.cos(Math.toRadians(degrees / 2));
        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity target : hostiles(warrior, radius, 0)) {
            Vector toTarget = target.getLocation().toVector()
                    .subtract(warrior.getLocation().toVector()).setY(0);
            if (toTarget.lengthSquared() > 0.001 && facing.dot(toTarget.normalize()) >= threshold) {
                targets.add(target);
            }
        }
        return targets;
    }

    /**
     * Damages up to {@code maxTargets} hostiles around the warrior and applies the stance's armour
     * shred to each. Re-entrant damage is flagged so {@link WarriorCombat} does not roll glance,
     * stun and splash again for every splashed entity.
     */
    public int splash(Player warrior, Entity exclude, double damage, double radius,
                      int maxTargets, Stance stance) {
        if (damage <= 0 || radius <= 0) {
            return 0;
        }
        splashing.add(warrior.getUniqueId());
        int hit = 0;
        try {
            for (LivingEntity target : hostiles(warrior, radius, 0)) {
                if (exclude != null && target.getUniqueId().equals(exclude.getUniqueId())) {
                    continue;
                }
                target.damage(damage, DamageSource.builder(DamageType.PLAYER_ATTACK)
                        .withCausingEntity(warrior).withDirectEntity(warrior).build());
                shred(target, stance);
                if (++hit >= maxTargets && maxTargets > 0) {
                    break;
                }
            }
        } finally {
            splashing.remove(warrior.getUniqueId());
        }
        return hit;
    }

    /** True while this warrior's own splash damage is being applied. */
    public boolean splashing(Player warrior) {
        return splashing.contains(warrior.getUniqueId());
    }

    /** Stacking armour reduction; stacks refresh the timer and clear together when it runs out. */
    public void shred(LivingEntity target, Stance stance) {
        double amount = stance.effect("shred-amount", 0);
        if (amount <= 0) {
            return;
        }
        int maxStacks = (int) stance.effect("shred-max-stacks", 4);
        int seconds = (int) stance.effect("shred-seconds", 6);
        UUID id = target.getUniqueId();
        int stacks = Math.min(maxStacks, shredStacks.getOrDefault(id, 0) + 1);
        shredStacks.put(id, stacks);
        shredExpiry.put(id, System.currentTimeMillis() + seconds * 1000L);
        applier.set(target, "shred", Attribute.ARMOR, -amount * stacks);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            Long expiry = shredExpiry.get(id);
            if (expiry == null || System.currentTimeMillis() < expiry) {
                return;
            }
            shredStacks.remove(id);
            shredExpiry.remove(id);
            if (target.isValid()) {
                applier.set(target, "shred", Attribute.ARMOR, 0);
            }
        }, seconds * 20L);
    }

    /** How far a cleave/sweep reaches by default. */
    public double defaultRadius() {
        return config.raw().getDouble("warrior.combat.splash-radius", 3.5);
    }
}
