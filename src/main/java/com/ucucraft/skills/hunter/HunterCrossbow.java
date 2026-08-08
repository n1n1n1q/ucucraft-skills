package com.ucucraft.skills.hunter;

import com.ucucraft.skills.SkillsPlugin;
import com.ucucraft.skills.classes.ClassManager;
import com.ucucraft.skills.classes.impl.HunterClass;
import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.data.PlayerProfile;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Level-3 crossbow mastery: a chance to keep the crossbow loaded after firing (the arrow "remains in
 * the crossbow"), so a qualifying hunter can fire again immediately without reloading. Tunables:
 * {@code hunter.crossbow}.
 */
public final class HunterCrossbow implements Listener {

    private final SkillsPlugin plugin;
    private final ConfigManager config;
    private final ClassManager classManager;

    public HunterCrossbow(SkillsPlugin plugin, ConfigManager config, ClassManager classManager) {
        this.plugin = plugin;
        this.config = config;
        this.classManager = classManager;
    }

    // --- Keep-loaded chance -----------------------------------------------------------------------

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || event.getBow() == null || event.getBow().getType() != Material.CROSSBOW) {
            return;
        }
        if (hunterLevel(player) < crossbowMinLevel()) {
            return;
        }
        if (ThreadLocalRandom.current().nextDouble() >= config.raw().getDouble("hunter.crossbow.keep-loaded-chance", 0.25)) {
            return;
        }
        var hand = event.getHand();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            ItemStack crossbow = player.getInventory().getItem(hand);
            if (crossbow == null || crossbow.getType() != Material.CROSSBOW) {
                return;
            }
            if (crossbow.getItemMeta() instanceof CrossbowMeta meta && !meta.hasChargedProjectiles()) {
                meta.addChargedProjectile(new ItemStack(Material.ARROW));
                crossbow.setItemMeta(meta);
                player.getInventory().setItem(hand, crossbow);
            }
        });
    }

    private int crossbowMinLevel() {
        return config.raw().getInt("hunter.crossbow.min-level", 3);
    }

    private int hunterLevel(Player player) {
        PlayerProfile profile = classManager.profile(player);
        return profile.hasClass() && HunterClass.ID.equalsIgnoreCase(profile.classId())
                ? profile.level() : 0;
    }
}
