package com.ucucraft.skills.hunter;

import com.ucucraft.skills.SkillsPlugin;
import com.ucucraft.skills.classes.ClassManager;
import com.ucucraft.skills.classes.impl.HunterClass;
import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.data.PlayerProfile;
import com.ucucraft.skills.lang.LangManager;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Level-3 steady aim: sneak and hold still with a bow or crossbow to charge a "focused" shot whose
 * next arrow deals bonus damage. Charging progress and the ready state show on the action bar (a
 * visual cue so a non-hearing hunter isn't disadvantaged), optionally paired with a sound. Moving or
 * stopping sneaking cancels the charge. Tunables live under {@code hunter.steady-aim} in config.yml.
 */
public final class HunterSteadyAim implements Listener {

    private static final int INTERVAL = 4;   // ticks between checks
    private static final int BAR_SEGMENTS = 10;

    private final SkillsPlugin plugin;
    private final ConfigManager config;
    private final ClassManager classManager;
    private final LangManager lang;
    private final NamespacedKey steadyKey;
    private final Map<UUID, Focus> focus = new HashMap<>();

    public HunterSteadyAim(SkillsPlugin plugin, ConfigManager config, ClassManager classManager, LangManager lang) {
        this.plugin = plugin;
        this.config = config;
        this.classManager = classManager;
        this.lang = lang;
        this.steadyKey = new NamespacedKey(plugin, "arrow_steady");
    }

    /** Starts the charge ticker; call once after the listener is registered. */
    public void start() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, INTERVAL, INTERVAL);
    }

    // --- Charge ticker ----------------------------------------------------------------------------

    private void tick() {
        if (!config.raw().getBoolean("hunter.enabled", true)) {
            return;
        }
        int minLevel = config.raw().getInt("hunter.steady-aim.min-level", 3);
        long needed = Math.round(config.raw().getDouble("hunter.steady-aim.charge-seconds", 1.5) * 20);
        double tolerance = config.raw().getDouble("hunter.steady-aim.move-tolerance", 0.08);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (hunterLevel(player) < minLevel || !holdingRanged(player) || !player.isSneaking()) {
                focus.remove(player.getUniqueId());
                continue;
            }
            Location loc = player.getLocation();
            Focus f = focus.get(player.getUniqueId());
            if (f == null) {
                focus.put(player.getUniqueId(), new Focus(loc)); // settle for one interval, then charge
                continue;
            }
            boolean moved = f.lastLoc.getWorld() != loc.getWorld()
                    || f.lastLoc.distanceSquared(loc) > tolerance * tolerance;
            f.lastLoc = loc;
            if (moved) {
                f.charge = 0;
                f.ready = false;
                continue;
            }
            if (f.ready) {
                player.sendActionBar(lang.component("hunter.steady-ready"));
                continue;
            }
            f.charge += INTERVAL;
            if (f.charge >= needed) {
                f.ready = true;
                playSound(player, config.raw().getString("hunter.steady-aim.ready-sound", ""));
                player.sendActionBar(lang.component("hunter.steady-ready"));
            } else {
                player.sendActionBar(lang.component("hunter.steady-charging",
                        Map.of("bar", progressBar(f.charge, needed))));
            }
        }
    }

    // --- Consume the focus on shot, apply the bonus on hit ----------------------------------------

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !(event.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }
        Focus f = focus.remove(player.getUniqueId());
        if (f == null || !f.ready) {
            return;
        }
        arrow.getPersistentDataContainer().set(steadyKey, PersistentDataType.BYTE, (byte) 1);
        player.sendActionBar(lang.component("hunter.steady-fired"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof AbstractArrow arrow)
                || !arrow.getPersistentDataContainer().has(steadyKey, PersistentDataType.BYTE)) {
            return;
        }
        double multiplier = config.raw().getDouble("hunter.steady-aim.damage-multiplier", 1.6);
        event.setDamage(event.getDamage() * multiplier);
    }

    // --- Helpers ----------------------------------------------------------------------------------

    private String progressBar(long charge, long needed) {
        int filled = (int) Math.min(BAR_SEGMENTS, charge * BAR_SEGMENTS / Math.max(1, needed));
        return "▊".repeat(filled) + "░".repeat(BAR_SEGMENTS - filled);
    }

    private boolean holdingRanged(Player player) {
        Material material = player.getInventory().getItemInMainHand().getType();
        return material == Material.BOW || material == Material.CROSSBOW;
    }

    private void playSound(Player player, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        player.getWorld().playSound(
                Sound.sound(Key.key(key), Sound.Source.PLAYER, 1f, 1f), player.getLocation());
    }

    private int hunterLevel(Player player) {
        PlayerProfile profile = classManager.profile(player);
        return profile.hasClass() && HunterClass.ID.equalsIgnoreCase(profile.classId())
                ? profile.level() : 0;
    }

    private static final class Focus {
        Location lastLoc;
        long charge;
        boolean ready;

        Focus(Location lastLoc) {
            this.lastLoc = lastLoc;
        }
    }
}
