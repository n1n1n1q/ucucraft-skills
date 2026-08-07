package com.ucucraft.skills.warrior;

import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.lang.LangManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * War cries (level {@code warrior.warcry.min-level}). Every stance shouts something different, and
 * the cry lives in the stance file, so level 4 re-reads the whole stance list instead of adding one
 * generic shout. The cost is Fervor — everything above {@code spend-down-to} — and the effect scales
 * with how much was spent. Tunables: {@code warrior.warcry} plus the stance's {@code warcry} block.
 */
public final class WarCryService implements Listener {

    private final Plugin plugin;
    private final ConfigManager config;
    private final LangManager lang;
    private final StanceService stances;
    private final FervorService fervor;
    private final StunService stuns;
    private final StanceEffects effects;
    private final WarriorTargets targets;
    private final StanceApplier applier;
    private final WarriorCombat combat;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Long> lastSwap = new HashMap<>();

    public WarCryService(Plugin plugin, ConfigManager config, LangManager lang, StanceService stances,
                         FervorService fervor, StunService stuns, StanceEffects effects,
                         WarriorTargets targets, StanceApplier applier, WarriorCombat combat) {
        this.plugin = plugin;
        this.config = config;
        this.lang = lang;
        this.stances = stances;
        this.fervor = fervor;
        this.stuns = stuns;
        this.effects = effects;
        this.targets = targets;
        this.applier = applier;
        this.combat = combat;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (trigger("drop") && event.getPlayer().isSneaking() && shout(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onLeftClick(PlayerInteractEvent event) {
        if (trigger("left-click-air") && event.getAction() == Action.LEFT_CLICK_AIR
                && event.getPlayer().isSneaking()) {
            shout(event.getPlayer());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (!trigger("double-swap") || !event.getPlayer().isSneaking()) {
            return;
        }
        long now = System.currentTimeMillis();
        Long previous = lastSwap.put(event.getPlayer().getUniqueId(), now);
        if (previous != null && now - previous < 500 && shout(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    private boolean trigger(String id) {
        return id.equalsIgnoreCase(config.raw().getString("warrior.warcry.trigger", "drop"));
    }

    /** Runs the level, stance, cooldown and Fervor gates, then performs the cry. */
    public boolean shout(Player warrior) {
        if (!config.raw().getBoolean("warrior.enabled", true)
                || stances.level(warrior) < config.raw().getInt("warrior.warcry.min-level", 4)) {
            return false;
        }
        Stance stance = stances.active(warrior);
        if (!stance.hasCry()) {
            lang.send(warrior, "warrior.warcry-none");
            return false;
        }
        long now = System.currentTimeMillis();
        Long ready = cooldowns.get(warrior.getUniqueId());
        if (ready != null && now < ready) {
            lang.send(warrior, "warrior.warcry-cooldown", Map.of("seconds", String.valueOf((ready - now) / 1000 + 1)));
            return false;
        }
        double gate = config.raw().getDouble("warrior.warcry.min-fervor", 30);
        if (!fervor.atLeast(warrior, gate)) {
            lang.send(warrior, "warrior.warcry-fervor", Map.of("fervor", String.valueOf(Math.round(gate))));
            return false;
        }
        cooldowns.put(warrior.getUniqueId(),
                now + Math.round(config.raw().getDouble("warrior.warcry.cooldown-seconds", 45) * 1000));

        double spent = fervor.spendAbove(warrior, config.raw().getDouble("warrior.warcry.spend-down-to", 0));
        double power = fervor.enabled() && fervor.max() > 0
                ? Math.max(config.raw().getDouble("warrior.warcry.min-scale", 0.5),
                        Math.min(1.0, spent / fervor.max()))
                : 1.0;
        perform(warrior, stance, power);
        stances.refresh(warrior);
        lang.send(warrior, "warrior.warcry-used", Map.of(
                "cry", lang.plain("warrior.warcry-names." + stance.cry().id()),
                "stance", stances.displayName(stance)));
        return true;
    }

    private void perform(Player warrior, Stance stance, double power) {
        Stance.Cry cry = stance.cry();
        int duration = (int) Math.max(20, Math.round(cry.durationTicks() * power));
        double radius = cry.radius() > 0 ? cry.radius() : config.raw().getDouble("warrior.warcry.radius", 8);
        effects.cry(warrior, stance, radius);

        int allyBonus = stance.flag("cry-scale-with-armor") ? armorBonus(warrior, stance) : 0;
        apply(warrior, cry.self(), duration, 0);
        for (Player ally : targets.allies(warrior, radius)) {
            apply(ally, cry.allies(), duration, allyBonus);
            buff(ally, cry.buff(), duration);
        }

        List<LivingEntity> hostiles = cry.traits().contains(Stance.Trait.CONE)
                ? targets.cone(warrior, radius, stance.effect("cry-cone-degrees", 90))
                : targets.hostiles(warrior, radius, 0);
        for (LivingEntity hostile : hostiles) {
            apply(hostile, cry.hostile(), duration, 0);
            react(warrior, hostile, cry, duration);
        }

        if (cry.traits().contains(Stance.Trait.SWEEP)) {
            targets.splash(warrior, null, attackDamage(warrior) * stance.effect("cleave-percent", 0.5) * 2,
                    radius, (int) stance.effect("cry-max-targets", 8), stance);
        }
        if (cry.traits().contains(Stance.Trait.STUN_NEXT_HIT)) {
            combat.armStun(warrior, duration);
        }
        if (cry.traits().contains(Stance.Trait.DUEL) && !hostiles.isEmpty()) {
            combat.markDuel(warrior, nearest(warrior, hostiles), duration);
        }
    }

    /** Per-target reactions: fleeing, taunting, knockback and the grounded shockwave stun. */
    private void react(Player warrior, LivingEntity hostile, Stance.Cry cry, int duration) {
        if (cry.traits().contains(Stance.Trait.TAUNT) && hostile instanceof Mob mob) {
            mob.setTarget(warrior);
        }
        if (cry.traits().contains(Stance.Trait.FLEE) && hostile instanceof Mob mob) {
            mob.setTarget(null);
        }
        if (cry.traits().contains(Stance.Trait.FLEE) || cry.traits().contains(Stance.Trait.KNOCKBACK)) {
            push(warrior, hostile);
        }
        if (cry.traits().contains(Stance.Trait.SHOCKWAVE) && hostile.isOnGround()) {
            stuns.tryStun(warrior, hostile, duration);
        }
    }

    private void apply(LivingEntity entity, Map<PotionEffectType, Integer> effectMap, int duration, int bonus) {
        for (Map.Entry<PotionEffectType, Integer> entry : effectMap.entrySet()) {
            entity.addPotionEffect(new PotionEffect(entry.getKey(), duration, entry.getValue() + bonus));
        }
    }

    /** Temporary attribute buffs (knockback immunity and friends), stripped when the cry ends. */
    private void buff(LivingEntity entity, Map<Attribute, Double> buffs, int duration) {
        for (Map.Entry<Attribute, Double> entry : buffs.entrySet()) {
            applier.set(entity, "cry", entry.getKey(), entry.getValue());
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (entity.isValid()) {
                    applier.set(entity, "cry", entry.getKey(), 0);
                }
            }, duration);
        }
    }

    private int armorBonus(Player warrior, Stance stance) {
        AttributeInstance armor = warrior.getAttribute(Attribute.ARMOR);
        double perLevel = stance.effect("armor-per-amplifier", 8);
        return armor == null || perLevel <= 0 ? 0 : (int) (armor.getValue() / perLevel);
    }

    private double attackDamage(Player warrior) {
        AttributeInstance damage = warrior.getAttribute(Attribute.ATTACK_DAMAGE);
        return damage == null ? 1 : damage.getValue();
    }

    private void push(Player warrior, LivingEntity target) {
        Vector away = target.getLocation().toVector().subtract(warrior.getLocation().toVector()).setY(0);
        if (away.lengthSquared() < 0.001) {
            return;
        }
        target.setVelocity(away.normalize()
                .multiply(config.raw().getDouble("warrior.warcry.knockback", 0.8)).setY(0.35));
    }

    private LivingEntity nearest(Player warrior, List<LivingEntity> candidates) {
        LivingEntity best = candidates.get(0);
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            double distance = candidate.getLocation().distanceSquared(warrior.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    public void forget(UUID uuid) {
        cooldowns.remove(uuid);
        lastSwap.remove(uuid);
    }
}
