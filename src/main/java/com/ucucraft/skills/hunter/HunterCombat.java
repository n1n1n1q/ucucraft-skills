package com.ucucraft.skills.hunter;

import com.ucucraft.skills.SkillsPlugin;
import com.ucucraft.skills.classes.ClassManager;
import com.ucucraft.skills.classes.impl.HunterClass;
import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.data.PlayerProfile;
import com.ucucraft.skills.smithing.Modifier;
import com.ucucraft.skills.smithing.ModifierRegistry;
import com.ucucraft.skills.smithing.ModifierService;
import com.ucucraft.skills.smithing.ModifierTier;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The hunter's arrow combat: a bow's modifier adds to arrow damage; level 2 grants a chance-based
 * "slow arrow" (bonus damage + slowness, with sound and particle trail); level 4 stacks damage on
 * consecutive hits and rewards killing marked prey with a speed/regen burst. All numbers live under
 * {@code hunter.*} in config.yml.
 */
public final class HunterCombat implements Listener {

    private final SkillsPlugin plugin;
    private final ConfigManager config;
    private final ClassManager classManager;
    private final ModifierService modifiers;
    private final ModifierRegistry registry;
    private final GlowService glow;

    private final NamespacedKey dmgKey;
    private final NamespacedKey slowKey;
    private final Map<UUID, int[]> stacks = new HashMap<>(); // uuid -> [count, lastHitTick]

    public HunterCombat(SkillsPlugin plugin, ConfigManager config, ClassManager classManager,
                        ModifierService modifiers, ModifierRegistry registry, GlowService glow) {
        this.plugin = plugin;
        this.config = config;
        this.classManager = classManager;
        this.modifiers = modifiers;
        this.registry = registry;
        this.glow = glow;
        this.dmgKey = new NamespacedKey(plugin, "arrow_bonus");
        this.slowKey = new NamespacedKey(plugin, "arrow_slow");
    }

    // --- On shoot: stamp the arrow with its bow's bonus and (maybe) the slow-arrow effect ---------

    @EventHandler
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !(event.getProjectile() instanceof AbstractArrow arrow)) {
            return;
        }
        int level = hunterLevel(player);
        if (level < 1) {
            return;
        }
        double bonus = bowDamageBonus(event.getBow());
        if (bonus != 0.0) {
            arrow.getPersistentDataContainer().set(dmgKey, PersistentDataType.DOUBLE, bonus);
        }
        if (level >= config.raw().getInt("hunter.arrow-slow.min-level", 2)
                && ThreadLocalRandom.current().nextDouble() < config.raw().getDouble("hunter.arrow-slow.chance", 0.2)) {
            arrow.getPersistentDataContainer().set(slowKey, PersistentDataType.BYTE, (byte) 1);
            playSound(player.getLocation(), config.raw().getString("hunter.arrow-slow.sound", ""));
            startTrail(arrow);
        }
    }

    // --- On hit: apply bow bonus, slow-arrow effect, and level-4 hit stacking ---------------------

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof AbstractArrow arrow)
                || !(arrow.getShooter() instanceof Player player)) {
            return;
        }
        double damage = event.getDamage();
        Double bonus = arrow.getPersistentDataContainer().get(dmgKey, PersistentDataType.DOUBLE);
        if (bonus != null) {
            damage += bonus;
        }
        boolean slow = arrow.getPersistentDataContainer().has(slowKey, PersistentDataType.BYTE);
        if (slow) {
            damage *= config.raw().getDouble("hunter.arrow-slow.damage-multiplier", 1.5);
            if (event.getEntity() instanceof LivingEntity victim) {
                victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                        config.raw().getInt("hunter.arrow-slow.slowness-seconds", 4) * 20,
                        config.raw().getInt("hunter.arrow-slow.slowness-amplifier", 1)));
            }
        }
        if (hunterLevel(player) >= config.raw().getInt("hunter.stacking.min-level", 4)) {
            damage += stackBonus(player);
        }
        event.setDamage(Math.max(0.0, damage));
    }

    // --- On kill: reward killing marked prey with a burst -----------------------------------------

    @EventHandler
    public void onKill(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || hunterLevel(killer) < config.raw().getInt("hunter.marked-kill.min-level", 4)) {
            return;
        }
        if (!glow.isMarkedBy(victim, killer.getUniqueId())) {
            return;
        }
        glow.clearMark(victim);
        killer.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                config.raw().getInt("hunter.marked-kill.speed-seconds", 5) * 20,
                config.raw().getInt("hunter.marked-kill.speed-amplifier", 1)));
        killer.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                config.raw().getInt("hunter.marked-kill.regen-seconds", 5) * 20,
                config.raw().getInt("hunter.marked-kill.regen-amplifier", 1)));
        spawnParticle(killer.getLocation().add(0, 1, 0), config.raw().getString("hunter.marked-kill.particle", ""));
        playSound(killer.getLocation(), config.raw().getString("hunter.marked-kill.sound", ""));
    }

    // --- Helpers ----------------------------------------------------------------------------------

    /** Consecutive-hit damage bonus for the hunter, reset when the time window lapses. */
    private double stackBonus(Player player) {
        int window = config.raw().getInt("hunter.stacking.window-seconds", 4) * 20;
        int nowTick = player.getServer().getCurrentTick();
        int[] state = stacks.get(player.getUniqueId());
        if (state == null || nowTick - state[1] > window) {
            state = new int[]{0, nowTick};
        }
        state[0]++;
        state[1] = nowTick;
        stacks.put(player.getUniqueId(), state);

        double perStack = config.raw().getDouble("hunter.stacking.damage-per-stack", 1.0);
        double max = config.raw().getDouble("hunter.stacking.max-bonus", 6.0);
        return Math.min(max, (state[0] - 1) * perStack);
    }

    private double bowDamageBonus(ItemStack bow) {
        if (bow == null) {
            return 0.0;
        }
        String id = modifiers.modifierId(bow);
        Modifier modifier = registry.get(id);
        if (modifier == null) {
            return 0.0;
        }
        ModifierTier tier = modifier.tier(modifiers.modifierTier(bow));
        if (tier == null) {
            return 0.0;
        }
        Double value = tier.attributes().get(Attribute.ATTACK_DAMAGE);
        return value == null ? 0.0 : value;
    }

    private void startTrail(AbstractArrow arrow) {
        Particle particle = parseParticle(config.raw().getString("hunter.arrow-slow.particle", "SNOWFLAKE"));
        if (particle == null) {
            return;
        }
        int[] task = new int[1];
        task[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!arrow.isValid() || arrow.isDead() || arrow.isOnGround()) {
                plugin.getServer().getScheduler().cancelTask(task[0]);
                return;
            }
            arrow.getWorld().spawnParticle(particle, arrow.getLocation(), 4, 0.05, 0.05, 0.05, 0.0);
        }, 1L, 1L).getTaskId();
    }

    private void playSound(Location loc, String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        loc.getWorld().playSound(Sound.sound(Key.key(key), Sound.Source.PLAYER, 1f, 1f), loc);
    }

    private void spawnParticle(Location loc, String name) {
        Particle particle = parseParticle(name);
        if (particle != null) {
            loc.getWorld().spawnParticle(particle, loc, 30, 0.4, 0.6, 0.4, 0.05);
        }
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

    private int hunterLevel(Player player) {
        PlayerProfile profile = classManager.profile(player);
        return profile.hasClass() && HunterClass.ID.equalsIgnoreCase(profile.classId())
                ? profile.level() : 0;
    }
}
