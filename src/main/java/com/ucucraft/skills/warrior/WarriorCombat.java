package com.ucucraft.skills.warrior;

import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.lang.LangManager;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The one listener that resolves every warrior combat roll, in a fixed order:
 *
 * <ol>
 *   <li>a stunned attacker's melee is cancelled outright;</li>
 *   <li><b>parry</b> (warrior is the victim) — resolved before the attacker's own rolls because a
 *       parry cancels the hit entirely; it also opens the riposte window;</li>
 *   <li><b>glance</b> (warrior is the victim) — a defensive deflection: an incoming blow that grazes
 *       the warrior for a fraction of its damage. It is a buff, not the warrior's own attack missing;</li>
 *   <li>stance bonuses — riposte crit, sprint crit, charge bonus, duel bonus, cleave/sunder splash;</li>
 *   <li>Fervor gain, then the <b>stun</b> roll with all of its gates.</li>
 * </ol>
 *
 * Nothing here is scattered across other listeners, so the interaction order stays defined.
 * Tunables: {@code warrior.combat}; per-stance chances live in the stance files.
 */
public final class WarriorCombat implements Listener {

    private final ConfigManager config;
    private final LangManager lang;
    private final StanceService stances;
    private final FervorService fervor;
    private final StunService stuns;
    private final StanceEffects effects;
    private final WarriorTargets targets;
    private final DuelService duels;
    private final Map<UUID, Long> parryCooldowns = new HashMap<>();
    private final Map<UUID, Long> riposteWindows = new HashMap<>();
    private final Map<UUID, Long> armedStuns = new HashMap<>();

    public WarriorCombat(ConfigManager config, LangManager lang, StanceService stances,
                         FervorService fervor, StunService stuns, StanceEffects effects,
                         WarriorTargets targets, DuelService duels) {
        this.config = config;
        this.lang = lang;
        this.stances = stances;
        this.fervor = fervor;
        this.stuns = stuns;
        this.effects = effects;
        this.targets = targets;
        this.duels = duels;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!config.raw().getBoolean("warrior.enabled", true)) {
            return;
        }
        if (event.getDamager() instanceof LivingEntity attacker
                && stuns.isStunned(attacker.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (event.getEntity() instanceof Player defender && stances.isWarrior(defender)) {
            if (tryParry(defender, event)) {
                return; // a parry cancels the hit outright
            }
            tryGlance(defender, event); // otherwise a blow may still glance off for reduced damage
        }
        if (event.getDamager() instanceof Player warrior && stances.isWarrior(warrior)
                && event.getEntity() instanceof LivingEntity target
                && isMelee(event) && !targets.splashing(warrior)) {
            resolveAttack(warrior, target, event);
        }
    }

    private boolean tryParry(Player warrior, EntityDamageByEntityEvent event) {
        Entity damager = event.getDamager();
        boolean projectile = damager instanceof Projectile;
        if (projectile && !config.raw().getBoolean("warrior.combat.parry.blocks-projectiles", false)) {
            return false;
        }
        if (!projectile && !(damager instanceof LivingEntity)) {
            return false;
        }
        Stance stance = stances.active(warrior);
        double chance = clamp(stance.rolls().parry());
        UUID opponent = duels.opponentOf(warrior);
        if (opponent != null && opponent.equals(damager.getUniqueId())) {
            chance = clamp(chance * stance.effect("duel-parry-multiplier", 2));
        }
        if (chance <= 0) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long ready = parryCooldowns.get(warrior.getUniqueId());
        if ((ready != null && now < ready) || !roll(chance)) {
            return false;
        }
        if (!new WarriorParryEvent(warrior, damager, stance, event.getDamage()).callEvent()) {
            return false;
        }

        parryCooldowns.put(warrior.getUniqueId(),
                now + Math.round(config.raw().getDouble("warrior.combat.parry.cooldown-seconds", 4) * 1000));
        event.setCancelled(true);
        knockback(warrior, damager);
        fervor.add(warrior, config.raw().getDouble("warrior.combat.parry.fervor-gain", 15)
                + stance.effect("parry-fervor-refund", 0));
        stances.refresh(warrior);
        riposteWindows.put(warrior.getUniqueId(), now + 50L * (long) stance.effect("riposte-window-ticks",
                config.raw().getInt("warrior.combat.parry.riposte-window-ticks", 40)));
        effects.parry(warrior);
        warrior.sendActionBar(lang.component("warrior.parry"));
        return true;
    }

    /**
     * A glancing blow: an incoming melee or projectile hit that deflects off the warrior for a
     * fraction of its damage. Chance is the stance's own {@code glance-chance}; the on-screen cue and
     * the sound each toggle via {@code warrior.combat.glance.hud} / {@code .sound}.
     */
    private boolean tryGlance(Player warrior, EntityDamageByEntityEvent event) {
        boolean projectile = event.getDamager() instanceof Projectile;
        if (!projectile && !isMelee(event)) {
            return false; // only weapon blows glance — not fire, fall or explosions
        }
        double chance = clamp(stances.active(warrior).rolls().glance());
        if (chance <= 0 || !roll(chance)) {
            return false;
        }
        event.setDamage(event.getDamage() * config.raw().getDouble("warrior.combat.glance.multiplier", 0.5));
        effects.glance(warrior);
        if (config.raw().getBoolean("warrior.combat.glance.hud", true)) {
            warrior.sendActionBar(lang.component("warrior.glance"));
        }
        if (config.raw().getBoolean("warrior.combat.glance.sound", true)) {
            effects.sound(warrior.getLocation(), "glance", 0.6f);
        }
        return true;
    }

    private void resolveAttack(Player warrior, LivingEntity target, EntityDamageByEntityEvent event) {
        Stance stance = stances.active(warrior);
        boolean charging = movingToward(target, warrior, stance.effect("charge-threshold", 0.2));
        double damage = event.getDamage();
        // Every parry opens the window, but only a stance that defines a multiplier crits from it.
        boolean windowOpen = consumeRiposte(warrior);
        double riposteCrit = stance.effect("riposte-crit-multiplier", 1.0);
        if (windowOpen && riposteCrit > 1.0) {
            damage *= riposteCrit;
            warrior.sendActionBar(lang.component("warrior.riposte"));
        }
        if (stance.flag("sprint-crit") && warrior.isSprinting()) {
            damage *= stance.effect("sprint-crit-multiplier", 1.5);
        }
        if (charging) {
            damage += stance.effect("charge-bonus", 0);
        }
        UUID opponent = duels.opponentOf(warrior);
        if (opponent != null && opponent.equals(target.getUniqueId())) {
            damage += stance.effect("duel-damage-bonus", 0);
        }
        event.setDamage(damage);

        double cleave = stance.effect("cleave-percent", 0);
        if (cleave > 0) {
            targets.splash(warrior, target, damage * cleave,
                    stance.effect("cleave-radius", targets.defaultRadius()),
                    (int) stance.effect("cleave-max-targets", 3), stance);
            targets.shred(target, stance);
            wear(warrior, (int) stance.effect("durability-multiplier", 1) - 1);
        }
        double smash = stance.effect("smash-radius", 0);
        if (smash > 0) {
            targets.splash(warrior, target, damage * stance.effect("smash-percent", 0.4), smash,
                    (int) stance.effect("smash-max-targets", 4), stance);
            target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                    (int) stance.effect("slowness-ticks", 60), (int) stance.effect("slowness-amplifier", 1)));
        }

        fervor.add(warrior, config.raw().getDouble("warrior.fervor.per-hit", 8));
        stances.refresh(warrior);
        tryStun(warrior, target, stance, charging);
    }

    private void tryStun(Player warrior, LivingEntity target, Stance stance, boolean charging) {
        int duration = config.raw().getInt("warrior.combat.stun.duration-ticks", 25);
        if (consumeArmedStun(warrior)) {
            stuns.tryStun(warrior, target, duration);
            return;
        }
        double chance = clamp(stance.rolls().stun());
        if (chance <= 0
                || (stance.flag("stun-requires-charge") && !charging)
                || (stance.flag("stun-requires-shield") && !holdsShield(warrior))
                || !fervor.atLeast(warrior, config.raw().getDouble("warrior.combat.stun.min-fervor", 0))
                || !roll(chance)) {
            return;
        }
        if (stuns.tryStun(warrior, target, duration)
                && config.raw().getBoolean("warrior.combat.stun.hud", true)) {
            warrior.sendActionBar(lang.component("warrior.stun"));
        }
    }

    /** Arms the warrior's next landed hit to stun regardless of the stance's own chance. */
    public void armStun(Player warrior, int windowTicks) {
        armedStuns.put(warrior.getUniqueId(), System.currentTimeMillis() + windowTicks * 50L);
    }

    public void forget(UUID uuid) {
        parryCooldowns.remove(uuid);
        riposteWindows.remove(uuid);
        armedStuns.remove(uuid);
    }

    private boolean consumeRiposte(Player warrior) {
        Long until = riposteWindows.remove(warrior.getUniqueId());
        return until != null && System.currentTimeMillis() < until;
    }

    private boolean consumeArmedStun(Player warrior) {
        Long until = armedStuns.remove(warrior.getUniqueId());
        return until != null && System.currentTimeMillis() < until;
    }

    private void knockback(Player warrior, Entity attacker) {
        double strength = config.raw().getDouble("warrior.combat.parry.knockback", 0.6);
        if (strength <= 0) {
            return;
        }
        Vector away = attacker.getLocation().toVector().subtract(warrior.getLocation().toVector()).setY(0);
        if (away.lengthSquared() < 0.001) {
            return;
        }
        attacker.setVelocity(away.normalize().multiply(strength).setY(0.25));
    }

    /** True when the target is closing on the warrior — velocity first, facing as the fallback. */
    private boolean movingToward(LivingEntity target, Player warrior, double threshold) {
        Vector toWarrior = warrior.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
        if (toWarrior.lengthSquared() < 0.001) {
            return false;
        }
        toWarrior = toWarrior.normalize();
        Vector velocity = target.getVelocity().setY(0);
        if (velocity.lengthSquared() > 0.0025) {
            return velocity.normalize().dot(toWarrior) >= threshold;
        }
        Vector facing = target.getLocation().getDirection().setY(0);
        return facing.lengthSquared() > 0.001 && facing.normalize().dot(toWarrior) >= threshold;
    }

    /** Extra durability cost (cleave). Never destroys the weapon on its own. */
    private void wear(Player warrior, int extra) {
        ItemStack item = warrior.getInventory().getItemInMainHand();
        short max = item.getType().getMaxDurability();
        if (extra <= 0 || max <= 0 || !(item.getItemMeta() instanceof Damageable damageable)) {
            return;
        }
        damageable.setDamage(Math.min(max - 1, damageable.getDamage() + extra));
        item.setItemMeta(damageable);
    }

    private boolean holdsShield(Player warrior) {
        return warrior.getInventory().getItemInOffHand().getType() == Material.SHIELD
                || warrior.getInventory().getItemInMainHand().getType() == Material.SHIELD;
    }

    private boolean isMelee(EntityDamageByEntityEvent event) {
        return event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK;
    }

    /** No config typo can produce a 100% parry. */
    private double clamp(double chance) {
        return Math.max(0, Math.min(config.raw().getDouble("warrior.combat.max-roll-chance", 0.35), chance));
    }

    private boolean roll(double chance) {
        return chance > 0 && ThreadLocalRandom.current().nextDouble() < chance;
    }
}
