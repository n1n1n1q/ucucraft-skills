package com.ucucraft.skills.hunter;

import com.ucucraft.skills.SkillsPlugin;
import com.ucucraft.skills.classes.ClassManager;
import com.ucucraft.skills.classes.impl.HunterClass;
import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.data.PlayerProfile;
import com.ucucraft.skills.smithing.GearType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The hunter's "highlight prey" ability (level {@code hunter.highlight.min-level}+): sneak + right-click
 * a bow to make nearby animals/mobs glow for a short time, marking them as prey. Marked prey killed by
 * a level-4 hunter triggers the speed/regen burst in {@link HunterCombat}.
 *
 * <p>Exposed as a Bukkit service so other plugins can retrieve it and install a custom
 * {@link GlowPolicy} to decide which players glow which targets. Note: Bukkit's {@code setGlowing}
 * is server-wide (all viewers see the glow); per-viewer glow would need packet-level access.
 */
public final class GlowService implements Listener {

    private final SkillsPlugin plugin;
    private final ConfigManager config;
    private final ClassManager classManager;
    private final NamespacedKey markKey;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private volatile GlowPolicy policy = (hunter, target) -> !(target instanceof Player);

    public GlowService(SkillsPlugin plugin, ConfigManager config, ClassManager classManager) {
        this.plugin = plugin;
        this.config = config;
        this.classManager = classManager;
        this.markKey = new NamespacedKey(plugin, "hunter_mark");
    }

    /** Replace the policy that decides which targets a hunter may highlight. */
    public void setPolicy(GlowPolicy policy) {
        this.policy = policy == null ? (h, t) -> !(t instanceof Player) : policy;
    }

    public GlowPolicy getPolicy() {
        return policy;
    }

    /** True when the entity is currently marked as prey by the given hunter. */
    public boolean isMarkedBy(LivingEntity entity, UUID hunter) {
        String owner = entity.getPersistentDataContainer().get(markKey, PersistentDataType.STRING);
        return owner != null && owner.equals(hunter.toString());
    }

    public void clearMark(LivingEntity entity) {
        entity.getPersistentDataContainer().remove(markKey);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.useItemInHand() == org.bukkit.event.Event.Result.DENY // a reroll already consumed this click
                || event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()
                || GearType.of(player.getInventory().getItemInMainHand().getType()) != GearType.BOW) {
            return;
        }
        if (hunterLevel(player) < config.raw().getInt("hunter.highlight.min-level", 2)) {
            return;
        }
        long now = System.currentTimeMillis();
        long cooldownMs = config.raw().getLong("hunter.highlight.cooldown-seconds", 8) * 1000L;
        Long last = cooldowns.get(player.getUniqueId());
        if (last != null && now - last < cooldownMs) {
            return;
        }
        cooldowns.put(player.getUniqueId(), now);

        int radius = config.raw().getInt("hunter.highlight.radius", 16);
        int durationTicks = config.raw().getInt("hunter.highlight.duration-seconds", 6) * 20;
        int marked = highlight(player, radius, durationTicks);
        if (marked > 0) {
            playSound(player);
        }
    }

    /**
     * Highlights (glows + marks as prey) living entities within {@code radius} of the hunter that the
     * current {@link GlowPolicy} allows, for {@code durationTicks}. Returns how many were marked.
     */
    public int highlight(Player hunter, double radius, int durationTicks) {
        int count = 0;
        for (org.bukkit.entity.Entity nearby : hunter.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity target) || target instanceof Player) {
                continue;
            }
            if (!policy.allow(hunter, target)) {
                continue;
            }
            target.getPersistentDataContainer().set(markKey, PersistentDataType.STRING,
                    hunter.getUniqueId().toString());
            target.setGlowing(true);
            scheduleUnmark(target, durationTicks);
            count++;
        }
        return count;
    }

    private void scheduleUnmark(LivingEntity target, int durationTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (target.isValid()) {
                target.setGlowing(false);
                clearMark(target);
            }
        }, durationTicks);
    }

    private void playSound(Player player) {
        String key = config.raw().getString("hunter.highlight.sound", "");
        if (key == null || key.isBlank()) {
            return;
        }
        player.playSound(Sound.sound(Key.key(key), Sound.Source.PLAYER, 1f, 1f));
    }

    private int hunterLevel(Player player) {
        PlayerProfile profile = classManager.profile(player);
        return profile.hasClass() && HunterClass.ID.equalsIgnoreCase(profile.classId())
                ? profile.level() : 0;
    }
}
