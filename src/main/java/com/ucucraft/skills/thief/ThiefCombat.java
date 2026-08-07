package com.ucucraft.skills.thief;

import com.ucucraft.skills.classes.ClassManager;
import com.ucucraft.skills.classes.impl.ThiefClass;
import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.data.PlayerProfile;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.util.Vector;

/**
 * Thief level 2: sneaking while falling negates fall damage up to a configured height, and hitting a
 * target from behind while sneaking lands a backstab crit for bonus damage. Tunables: {@code thief.fall},
 * {@code thief.backstab}.
 */
public final class ThiefCombat implements Listener {

    private final ConfigManager config;
    private final ClassManager classManager;

    public ThiefCombat(ConfigManager config, ClassManager classManager) {
        this.config = config;
        this.classManager = classManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL
                || !(event.getEntity() instanceof Player player)) {
            return;
        }
        if (thiefLevel(player) < config.raw().getInt("thief.fall.min-level", 2) || !player.isSneaking()) {
            return;
        }
        if (player.getFallDistance() <= config.raw().getDouble("thief.fall.max-blocks", 15.0)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBackstab(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)
                || !(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        if (thiefLevel(player) < config.raw().getInt("thief.backstab.min-level", 2) || !player.isSneaking()) {
            return;
        }
        if (!isBehind(player, victim)) {
            return;
        }
        event.setDamage(event.getDamage() * config.raw().getDouble("thief.backstab.multiplier", 1.5));
        if (victim.getWorld() != null) {
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.2);
        }
    }

    /** True when the attacker stands in the victim's rear arc. */
    private boolean isBehind(Player attacker, LivingEntity victim) {
        Vector facing = victim.getLocation().getDirection().setY(0);
        Vector toAttacker = attacker.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0);
        if (facing.lengthSquared() == 0 || toAttacker.lengthSquared() == 0) {
            return false;
        }
        double dot = facing.normalize().dot(toAttacker.normalize());
        return dot < -config.raw().getDouble("thief.backstab.rear-threshold", 0.2);
    }

    private int thiefLevel(Player player) {
        PlayerProfile profile = classManager.profile(player);
        return profile.hasClass() && ThiefClass.ID.equalsIgnoreCase(profile.classId())
                ? profile.level() : 0;
    }
}
