package com.ucucraft.skills.warrior;

import com.ucucraft.skills.classes.ClassManager;
import com.ucucraft.skills.classes.impl.WarriorClass;
import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.data.PlayerProfile;
import com.ucucraft.skills.lang.LangManager;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the warrior's active stance: selection, the warm-up telegraph, the switch cooldown and the
 * attribute modifiers that follow from it. Registered as a Bukkit service so other plugins can read
 * the active stance and install a {@link StancePolicy}.
 *
 * <p>A stance is <em>suspended</em> — bonuses off, no cooldown, no switch consumed — while the held
 * item does not match its weapon families, while a switch is warming up, or while the policy denies
 * it; {@link #active(Player)} then reports neutral and snaps back on its own.
 */
public final class StanceService {

    private record Warmup(Stance target, BukkitTask task) {}

    private final Plugin plugin;
    private final ConfigManager config;
    private final LangManager lang;
    private final ClassManager classes;
    private final StanceRegistry registry;
    private final StanceApplier applier;
    private final FervorService fervor;
    private final StanceEffects effects;
    private final NamespacedKey stanceKey;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Warmup> warmups = new HashMap<>();
    private volatile StancePolicy policy = (player, stance) -> true;

    public StanceService(Plugin plugin, ConfigManager config, LangManager lang, ClassManager classes,
                         StanceRegistry registry, StanceApplier applier, FervorService fervor,
                         StanceEffects effects) {
        this.plugin = plugin;
        this.config = config;
        this.lang = lang;
        this.classes = classes;
        this.registry = registry;
        this.applier = applier;
        this.fervor = fervor;
        this.effects = effects;
        this.stanceKey = new NamespacedKey(plugin, "warrior_stance");
    }

    /** Replace the policy deciding which stances are legal for a player. */
    public void setPolicy(StancePolicy policy) {
        this.policy = policy == null ? (p, s) -> true : policy;
    }

    public StancePolicy getPolicy() {
        return policy;
    }

    public StanceRegistry registry() {
        return registry;
    }

    public boolean isWarrior(Player player) {
        return level(player) > 0;
    }

    /** The player's warrior level, or 0 when they are not a warrior. */
    public int level(Player player) {
        PlayerProfile profile = classes.profile(player);
        return profile.hasClass() && WarriorClass.ID.equalsIgnoreCase(profile.classId())
                ? profile.level() : 0;
    }

    /** The stance the player has chosen, regardless of whether it currently applies. */
    public Stance selected(Player player) {
        String id = player.getPersistentDataContainer().get(stanceKey, PersistentDataType.STRING);
        Stance stance = registry.get(id);
        return stance == null || stance.minLevel() > level(player) ? registry.neutral() : stance;
    }

    /** The stance actually in force right now — neutral while suspended. */
    public Stance active(Player player) {
        Stance stance = selected(player);
        if (warmups.containsKey(player.getUniqueId())
                || !stance.appliesTo(player.getInventory().getItemInMainHand().getType())
                || !policy.allow(player, stance)
                || (stance.flag("no-bonus-while-sneaking") && player.isSneaking())) {
            return registry.neutral();
        }
        return stance;
    }

    public boolean suspended(Player player) {
        return !active(player).id().equals(selected(player).id());
    }

    /** Stances the player's level allows and their held weapon accepts. */
    public List<Stance> availableFor(Player player) {
        int level = level(player);
        var held = player.getInventory().getItemInMainHand().getType();
        List<Stance> available = new ArrayList<>();
        for (Stance stance : registry.all()) {
            if (stance.minLevel() <= level && stance.appliesTo(held) && policy.allow(player, stance)) {
                available.add(stance);
            }
        }
        return available;
    }

    /** Cycles to the next stance available for the held weapon. */
    public void cycle(Player player) {
        List<Stance> available = availableFor(player);
        if (available.size() < 2) {
            lang.send(player, "warrior.stance-none");
            return;
        }
        String current = selected(player).id();
        int index = 0;
        for (int i = 0; i < available.size(); i++) {
            if (available.get(i).id().equals(current)) {
                index = i + 1;
                break;
            }
        }
        request(player, available.get(index % available.size()));
    }

    /**
     * Full switch flow: level, policy, weapon and cooldown checks, then the warm-up telegraph.
     * Returns false when the switch was refused (nothing is consumed in that case).
     */
    public boolean request(Player player, Stance target) {
        if (!isWarrior(player) || target == null) {
            return false;
        }
        if (target.minLevel() > level(player)) {
            lang.send(player, "warrior.stance-locked",
                    Map.of("stance", displayName(target), "level", String.valueOf(target.minLevel())));
            return false;
        }
        if (!policy.allow(player, target)) {
            lang.send(player, "warrior.stance-blocked", Map.of("stance", displayName(target)));
            return false;
        }
        if (!target.appliesTo(player.getInventory().getItemInMainHand().getType())) {
            lang.send(player, "warrior.stance-wrong-weapon", Map.of("stance", displayName(target)));
            return false;
        }
        if (selected(player).id().equals(target.id())) {
            return false;
        }
        long remaining = cooldownRemaining(player);
        if (remaining > 0) {
            lang.send(player, "warrior.stance-cooldown",
                    Map.of("seconds", String.valueOf(remaining / 1000 + 1)));
            return false;
        }
        if (!new StanceChangeEvent(player, selected(player), target).callEvent()) {
            return false;
        }
        beginWarmup(player, target);
        return true;
    }

    private void beginWarmup(Player player, Stance target) {
        cancelWarmup(player);
        int warmupTicks = config.raw().getInt("warrior.stance.warmup-ticks", 20);
        if (warmupTicks <= 0) {
            commit(player, target);
            return;
        }
        BukkitTask task = new BukkitRunnable() {
            private int elapsed;

            @Override
            public void run() {
                Warmup warmup = warmups.get(player.getUniqueId());
                if (!player.isOnline() || warmup == null || !warmup.target().id().equals(target.id())) {
                    cancel();
                    return;
                }
                if (elapsed >= warmupTicks) {
                    cancel();
                    warmups.remove(player.getUniqueId());
                    commit(player, target);
                    return;
                }
                effects.warmup(player, target, (double) elapsed / warmupTicks);
                elapsed += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
        warmups.put(player.getUniqueId(), new Warmup(target, task));
        refresh(player); // no bonuses at all while the switch telegraphs
        lang.send(player, "warrior.stance-warmup", Map.of("stance", displayName(target)));
    }

    /** Aborts a pending switch (taking damage does this when configured). */
    public void cancelWarmup(Player player) {
        Warmup warmup = warmups.remove(player.getUniqueId());
        if (warmup != null) {
            warmup.task().cancel();
            refresh(player);
        }
    }

    public boolean warmingUp(Player player) {
        return warmups.containsKey(player.getUniqueId());
    }

    /** Sets the stance immediately, skipping cooldown and warm-up (the API entry point). */
    public void commit(Player player, Stance target) {
        player.getPersistentDataContainer().set(stanceKey, PersistentDataType.STRING, target.id());
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + switchCooldownMs(player));
        refresh(player);
        effects.switched(player, target);
        lang.send(player, "warrior.stance-changed", Map.of("stance", displayName(target)));
    }

    /** Recomputes and applies the attribute modifiers implied by the current situation. */
    public void refresh(Player player) {
        if (!isWarrior(player)) {
            applier.clear(player, registry.attributes());
            return;
        }
        Stance stance = active(player);
        double magnitude = magnitude(level(player));
        Map<Attribute, Double> desired = new LinkedHashMap<>();
        stance.attributes().forEach((attribute, value) -> desired.merge(attribute, value * magnitude, Double::sum));
        if (fervor.charged(player)) {
            stance.charged().forEach((attribute, value) -> desired.merge(attribute, value * magnitude, Double::sum));
        }
        applier.apply(player, desired, registry.attributes());
    }

    /** Strips every stance modifier — quit, death, class change and reload all go through here. */
    public void clear(Player player) {
        cancelWarmupSilently(player);
        applier.clear(player, registry.attributes());
    }

    /** Resets the selection to neutral (on death, when configured). */
    public void reset(Player player) {
        player.getPersistentDataContainer().remove(stanceKey);
        cooldowns.remove(player.getUniqueId());
        clear(player);
    }

    private void cancelWarmupSilently(Player player) {
        Warmup warmup = warmups.remove(player.getUniqueId());
        if (warmup != null) {
            warmup.task().cancel();
        }
    }

    /** Applied to both the gain and the cost, so higher levels bring options rather than numbers. */
    public double magnitude(int level) {
        return config.raw().getDouble("warrior.levels." + Math.max(1, level) + ".magnitude",
                config.raw().getDouble("warrior.levels.1.magnitude", 1.0));
    }

    public long cooldownRemaining(Player player) {
        Long until = cooldowns.get(player.getUniqueId());
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    private long switchCooldownMs(Player player) {
        double seconds = config.raw().getDouble("warrior.stance.switch-cooldown-seconds", 3);
        int level = level(player);
        if (level >= config.raw().getInt("warrior.stance.cooldown-free-level", 4)) {
            return 0;
        }
        if (level >= config.raw().getInt("warrior.stance.cooldown-halved-level", 3)) {
            seconds /= 2;
        }
        return Math.round(seconds * 1000);
    }

    /** Localised stance name: {@code warrior.stance-names.<id>}, falling back to the stance file. */
    public String displayName(Stance stance) {
        String key = "warrior.stance-names." + stance.id();
        String name = lang.plain(key);
        return name.equals(key) ? stance.name() : name;
    }
}
