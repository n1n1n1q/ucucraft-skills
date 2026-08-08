package com.ucucraft.skills.warrior;

import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.lang.LangManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
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
public final class WarCryService {

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
    private final DuelService duels;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public WarCryService(Plugin plugin, ConfigManager config, LangManager lang, StanceService stances,
                         FervorService fervor, StunService stuns, StanceEffects effects,
                         WarriorTargets targets, StanceApplier applier, WarriorCombat combat,
                         DuelService duels) {
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
        this.duels = duels;
    }

    /**
     * True when a war cry would fire right now — level, warm-up, stance, cooldown and Fervor all pass.
     * The sneak + swap-hand input shouts when this holds and cycles stances otherwise.
     */
    public boolean ready(Player warrior) {
        if (!config.raw().getBoolean("warrior.enabled", true)
                || stances.level(warrior) < config.raw().getInt("warrior.warcry.min-level", 4)
                || stances.warmingUp(warrior)
                || !stances.selected(warrior).hasCry()) {
            return false;
        }
        Long readyAt = cooldowns.get(warrior.getUniqueId());
        if (readyAt != null && System.currentTimeMillis() < readyAt) {
            return false;
        }
        return fervor.atLeast(warrior, config.raw().getDouble("warrior.warcry.min-fervor", 30));
    }

    /** Milliseconds left on this warrior's war-cry cooldown, or 0 when a cry is ready to shout. */
    public long cooldownRemaining(Player warrior) {
        Long until = cooldowns.get(warrior.getUniqueId());
        return until == null ? 0 : Math.max(0, until - System.currentTimeMillis());
    }

    /** The full cooldown length in milliseconds — the denominator for the HUD's cooldown bar. */
    public long cooldownTotalMs() {
        return Math.round(config.raw().getDouble("warrior.warcry.cooldown-seconds", 45) * 1000);
    }

    /** Console trace of the war-cry pipeline; off unless {@code warrior.warcry.debug} is set. */
    private void debug(Player player, String message) {
        if (config.raw().getBoolean("warrior.warcry.debug", false)) {
            plugin.getLogger().info("[warcry] " + player.getName() + ": " + message);
        }
    }

    /** Runs the level, stance, cooldown and Fervor gates, then performs the cry. */
    public boolean shout(Player warrior) {
        if (!config.raw().getBoolean("warrior.enabled", true)) {
            debug(warrior, "shout blocked: warrior module disabled");
            return false;
        }
        int minLevel = config.raw().getInt("warrior.warcry.min-level", 4);
        if (stances.level(warrior) < minLevel) {
            debug(warrior, "shout blocked: level " + stances.level(warrior) + " < min " + minLevel);
            lang.send(warrior, "warrior.warcry-locked", Map.of("level", String.valueOf(minLevel)));
            return false;
        }
        if (stances.warmingUp(warrior)) {
            debug(warrior, "shout blocked: stance is warming up");
            return false;
        }
        // The cry belongs to the stance the player CHOSE, not the one currently in force — sneaking to
        // shout suspends a `no-bonus-while-sneaking` stance (e.g. skirmish) to neutral, which has none.
        Stance stance = stances.selected(warrior);
        if (!stance.hasCry()) {
            debug(warrior, "shout blocked: selected stance '" + stance.id() + "' has no war cry");
            lang.send(warrior, "warrior.warcry-none");
            return false;
        }
        long now = System.currentTimeMillis();
        Long ready = cooldowns.get(warrior.getUniqueId());
        if (ready != null && now < ready) {
            debug(warrior, "shout blocked: on cooldown for " + ((ready - now) / 1000 + 1) + "s");
            lang.send(warrior, "warrior.warcry-cooldown", Map.of("seconds", String.valueOf((ready - now) / 1000 + 1)));
            return false;
        }
        double gate = config.raw().getDouble("warrior.warcry.min-fervor", 30);
        if (!fervor.atLeast(warrior, gate)) {
            debug(warrior, "shout blocked: Fervor " + Math.round(fervor.value(warrior)) + " < gate " + Math.round(gate));
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
        debug(warrior, "SHOUTING '" + stance.cry().id() + "' (stance " + stance.id()
                + ", spent " + Math.round(spent) + " Fervor, power " + String.format("%.2f", power) + ")");
        perform(warrior, stance, power);
        stances.refresh(warrior);
        announce(warrior, stance);
        return true;
    }

    /** The caster hears their own cry; everyone within its radius sees it announced in chat. */
    private void announce(Player warrior, Stance stance) {
        String cry = lang.plain("warrior.warcry-names." + stance.cry().id());
        lang.send(warrior, "warrior.warcry-used", Map.of("cry", cry, "stance", stances.displayName(stance)));
        Map<String, String> broadcast = Map.of(
                "player", warrior.getName(), "cry", cry, "stance", stances.displayName(stance));
        for (Player nearby : warrior.getWorld().getNearbyPlayers(warrior.getLocation(), cryRadius(stance))) {
            if (!nearby.equals(warrior)) {
                lang.send(nearby, "warrior.warcry-broadcast", broadcast);
            }
        }
    }

    private double cryRadius(Stance stance) {
        return stance.cry().radius() > 0
                ? stance.cry().radius() : config.raw().getDouble("warrior.warcry.radius", 8);
    }

    private void perform(Player warrior, Stance stance, double power) {
        Stance.Cry cry = stance.cry();
        int duration = (int) Math.max(20, Math.round(cry.durationTicks() * power));
        double radius = cryRadius(stance);
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
        if (cry.traits().contains(Stance.Trait.DUEL)) {
            Player opponent = nearestPlayer(warrior, hostiles);
            if (opponent != null) {
                duels.start(warrior, opponent);
            } else {
                lang.send(warrior, "warrior.duel-no-target");
            }
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

    /** The nearest player among the candidates — a duel needs another player, not a mob. */
    private Player nearestPlayer(Player warrior, List<LivingEntity> candidates) {
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            if (!(candidate instanceof Player other)) {
                continue;
            }
            double distance = other.getLocation().distanceSquared(warrior.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = other;
            }
        }
        return best;
    }

    public void forget(UUID uuid) {
        cooldowns.remove(uuid);
    }
}
