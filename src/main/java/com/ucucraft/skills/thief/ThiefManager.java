package com.ucucraft.skills.thief;

import com.ucucraft.skills.SkillsPlugin;
import com.ucucraft.skills.classes.ClassManager;
import com.ucucraft.skills.classes.impl.ThiefClass;
import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.data.PlayerProfile;
import com.ucucraft.skills.lang.LangManager;
import com.ucucraft.skills.minigame.MinigameManager;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Thief level 1: right-click a container with a lockpick to break in. When the chunk holds members of
 * the owning country ({@link CountriesHook}) a lockpicking minigame runs — and a loud sound plays
 * during and at the end, so the theft is risky. On success the container opens.
 */
public final class ThiefManager implements Listener {

    private final SkillsPlugin plugin;
    private final LangManager lang;
    private final ConfigManager config;
    private final ClassManager classManager;
    private final MinigameManager minigames;
    private final Lockpick lockpick;
    private final CountriesHook countries;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public ThiefManager(SkillsPlugin plugin, LangManager lang, ConfigManager config,
                        ClassManager classManager, MinigameManager minigames,
                        Lockpick lockpick, CountriesHook countries) {
        this.plugin = plugin;
        this.lang = lang;
        this.config = config;
        this.classManager = classManager;
        this.minigames = minigames;
        this.lockpick = lockpick;
        this.countries = countries;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!enabled() || event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !(block.getState() instanceof Container)) {
            return;
        }
        Player player = event.getPlayer();
        if (!lockpick.isLockpick(player.getInventory().getItemInMainHand())) {
            return;
        }
        event.setCancelled(true); // don't open normally — a lockpick always goes through the minigame

        if (thiefLevel(player) < 1) {
            lang.send(player, "thief.lockpick-not-thief");
            return;
        }
        int members = countries.membersInChunk(block.getLocation());
        if (config.raw().getBoolean("thief.lockpick.require-country-members", true)
                && members == 0) { // absent Countries returns UNAVAILABLE and is allowed through
            lang.send(player, "thief.lockpick-no-target");
            return;
        }
        long cooldownMs = config.raw().getLong("thief.lockpick.cooldown-seconds", 0) * 1000L;
        long now = System.currentTimeMillis();
        Long until = cooldowns.get(player.getUniqueId());
        if (cooldownMs > 0 && until != null && now < until) {
            lang.send(player, "thief.lockpick-cooldown",
                    Map.of("seconds", String.valueOf((until - now) / 1000 + 1)));
            return;
        }
        if (cooldownMs > 0) {
            cooldowns.put(player.getUniqueId(), now + cooldownMs);
        }

        Location loc = block.getLocation();
        playLoud(loc);
        String game = config.raw().getString("thief.lockpick.minigame", "sequence");
        var difficulty = config.raw().getConfigurationSection("thief.lockpick.difficulty");
        if (!minigames.start(player, game, result -> finishLockpick(player, block, result.won()), difficulty)) {
            lang.send(player, "thief.lockpick-unavailable");
            plugin.getLogger().warning("thief.lockpick.minigame '" + game + "' is not a registered minigame.");
        }
    }

    private void finishLockpick(Player player, Block block, boolean success) {
        playLoud(block.getLocation()); // the tell-tale click at the end
        if (!success) {
            lang.send(player, "thief.lockpick-failed");
            return;
        }
        if (block.getState() instanceof Container container) {
            player.openInventory(container.getInventory());
            lang.send(player, "thief.lockpick-success");
        }
    }

    private void playLoud(Location loc) {
        String key = config.raw().getString("thief.lockpick.sound", "minecraft:block.chain.break");
        if (key == null || key.isBlank() || loc.getWorld() == null) {
            return;
        }
        float volume = (float) config.raw().getDouble("thief.lockpick.sound-volume", 4.0);
        loc.getWorld().playSound(Sound.sound(Key.key(key), Sound.Source.BLOCK, volume, 1f), loc);
    }

    private boolean enabled() {
        return config.raw().getBoolean("thief.enabled", true);
    }

    private int thiefLevel(Player player) {
        PlayerProfile profile = classManager.profile(player);
        return profile.hasClass() && ThiefClass.ID.equalsIgnoreCase(profile.classId())
                ? profile.level() : 0;
    }
}
