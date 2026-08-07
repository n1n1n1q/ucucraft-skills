package com.ucucraft.skills.hunter;

import com.ucucraft.skills.SkillsPlugin;
import com.ucucraft.skills.classes.ClassManager;
import com.ucucraft.skills.classes.impl.HunterClass;
import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.data.PlayerProfile;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Level-3 crossbow mastery: a chance to keep the crossbow loaded after firing (the arrow "remains in
 * the crossbow") and a passive faster reload — a {@link Enchantment#QUICK_CHARGE} bonus applied while
 * a qualifying hunter holds a crossbow and stripped again when they switch away. Vanilla has no
 * loading-speed attribute, so quick-charge is the standard stand-in. Tunables: {@code hunter.crossbow}.
 */
public final class HunterCrossbow implements Listener {

    private final SkillsPlugin plugin;
    private final ConfigManager config;
    private final ClassManager classManager;
    private final NamespacedKey quickChargeKey;

    public HunterCrossbow(SkillsPlugin plugin, ConfigManager config, ClassManager classManager) {
        this.plugin = plugin;
        this.config = config;
        this.classManager = classManager;
        this.quickChargeKey = new NamespacedKey(plugin, "hunter_qc");
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

    // --- Passive loading speed (quick-charge while held) ------------------------------------------

    @EventHandler
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        strip(player.getInventory().getItem(event.getPreviousSlot()));
        ItemStack next = player.getInventory().getItem(event.getNewSlot());
        if (hunterLevel(player) >= crossbowMinLevel()) {
            apply(next);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (hunterLevel(player) >= crossbowMinLevel()) {
            apply(player.getInventory().getItemInMainHand());
        }
    }

    private void apply(ItemStack item) {
        int level = config.raw().getInt("hunter.crossbow.quick-charge-level", 2);
        if (level <= 0 || item == null || item.getType() != Material.CROSSBOW) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta.hasEnchant(Enchantment.QUICK_CHARGE)) {
            return; // don't clobber a real quick-charge the player already has
        }
        meta.addEnchant(Enchantment.QUICK_CHARGE, level, true);
        meta.getPersistentDataContainer().set(quickChargeKey, PersistentDataType.INTEGER, level);
        item.setItemMeta(meta);
    }

    /** Removes only the quick-charge we added (flagged in PDC). */
    private void strip(ItemStack item) {
        if (item == null || item.getType() != Material.CROSSBOW) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta.getPersistentDataContainer().has(quickChargeKey, PersistentDataType.INTEGER)) {
            meta.removeEnchant(Enchantment.QUICK_CHARGE);
            meta.getPersistentDataContainer().remove(quickChargeKey);
            item.setItemMeta(meta);
        }
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
