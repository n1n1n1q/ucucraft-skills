package com.ucucraft.skills.thief;

import com.ucucraft.skills.classes.ClassManager;
import com.ucucraft.skills.classes.impl.ThiefClass;
import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.data.PlayerProfile;
import com.ucucraft.skills.lang.LangManager;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Thief level 4 "Smoke Screen": sneak + swap-hand (F) drops a smoke bomb — a thick smoke cloud that
 * blinds nearby enemies while granting the thief Speed II and Invisibility, on a cooldown. Tunables:
 * {@code thief.smoke}.
 */
public final class ThiefSmoke implements Listener {

    private final ConfigManager config;
    private final ClassManager classManager;
    private final LangManager lang;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public ThiefSmoke(ConfigManager config, ClassManager classManager, LangManager lang) {
        this.config = config;
        this.classManager = classManager;
        this.lang = lang;
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player thief = event.getPlayer();
        if (!thief.isSneaking() || thiefLevel(thief) < config.raw().getInt("thief.smoke.min-level", 4)) {
            return;
        }
        event.setCancelled(true);

        long cooldownMs = config.raw().getLong("thief.smoke.cooldown-seconds", 30) * 1000L;
        long now = System.currentTimeMillis();
        Long until = cooldowns.get(thief.getUniqueId());
        if (until != null && now < until) {
            lang.send(thief, "thief.smoke-cooldown",
                    Map.of("seconds", String.valueOf((until - now) / 1000 + 1)));
            return;
        }
        cooldowns.put(thief.getUniqueId(), now + cooldownMs);

        deploySmoke(thief);
        lang.send(thief, "thief.smoke-deployed");
    }

    private void deploySmoke(Player thief) {
        Location loc = thief.getLocation().add(0, 1, 0);
        Particle particle = parseParticle(config.raw().getString("thief.smoke.particle", "CAMPFIRE_SIGNAL_SMOKE"));
        if (particle != null && loc.getWorld() != null) {
            int count = config.raw().getInt("thief.smoke.particle-count", 120);
            loc.getWorld().spawnParticle(particle, loc, count, 1.5, 1.0, 1.5, 0.02);
        }
        String soundKey = config.raw().getString("thief.smoke.sound", "minecraft:entity.tnt.primed");
        if (soundKey != null && !soundKey.isBlank() && loc.getWorld() != null) {
            loc.getWorld().playSound(Sound.sound(Key.key(soundKey), Sound.Source.PLAYER, 1f, 1.4f), loc);
        }

        double radius = config.raw().getInt("thief.smoke.radius", 6);
        int blindTicks = config.raw().getInt("thief.smoke.blindness-seconds", 4) * 20;
        for (org.bukkit.entity.Entity nearby : thief.getNearbyEntities(radius, radius, radius)) {
            if (nearby instanceof LivingEntity enemy && !enemy.getUniqueId().equals(thief.getUniqueId())) {
                enemy.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindTicks, 0));
            }
        }

        thief.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                config.raw().getInt("thief.smoke.self-speed-seconds", 5) * 20,
                config.raw().getInt("thief.smoke.self-speed-amplifier", 1)));
        thief.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                config.raw().getInt("thief.smoke.invisibility-seconds", 5) * 20, 0));
    }

    private Particle parseParticle(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Particle.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private int thiefLevel(Player player) {
        PlayerProfile profile = classManager.profile(player);
        return profile.hasClass() && ThiefClass.ID.equalsIgnoreCase(profile.classId())
                ? profile.level() : 0;
    }
}
