package com.ucucraft.skills.warrior;

import com.ucucraft.skills.config.ConfigManager;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The stun state machine. There is no server-side way to freeze a client's input, so this is an
 * approximation: mobs lose their AI (a real stun), players get Slowness 7 + Jump Boost 129 +
 * Mining Fatigue and have their outgoing melee cancelled by {@link WarriorCombat} — they can still
 * look around and use items. Durations are short by design, and the attacker cooldown, the target
 * immunity window and the diminishing returns below are what keep that from becoming a stun-lock.
 *
 * <p>This map is the source of truth for "is stunned"; the potion effects never are.
 * Tunables: {@code warrior.combat.stun}.
 */
public final class StunService {

    private record Stun(long expiresAt, boolean hadAi) {}

    private record Dr(int count, long windowStart) {}

    private final Plugin plugin;
    private final ConfigManager config;
    private final StanceEffects effects;
    private final Map<UUID, Stun> stunned = new HashMap<>();
    private final Map<UUID, Long> attackerCooldowns = new HashMap<>();
    private final Map<UUID, Long> immunity = new HashMap<>();
    private final Map<UUID, Dr> diminishing = new HashMap<>();

    public StunService(Plugin plugin, ConfigManager config, StanceEffects effects) {
        this.plugin = plugin;
        this.config = config;
        this.effects = effects;
    }

    public boolean isStunned(UUID uuid) {
        Stun stun = stunned.get(uuid);
        if (stun == null) {
            return false;
        }
        if (System.currentTimeMillis() >= stun.expiresAt()) {
            stunned.remove(uuid);
            return false;
        }
        return true;
    }

    /**
     * Runs every gate (PvP switch, attacker cooldown, target immunity, diminishing returns, the
     * cancellable {@link WarriorStunEvent}) and applies the stun. Returns false when it was denied.
     */
    public boolean tryStun(Player warrior, LivingEntity target, int baseTicks) {
        if (target instanceof Player
                && !config.raw().getBoolean("warrior.combat.stun.pvp-enabled", false)) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long ready = attackerCooldowns.get(warrior.getUniqueId());
        if (ready != null && now < ready) {
            return false;
        }
        Long immuneUntil = immunity.get(target.getUniqueId());
        if (immuneUntil != null && now < immuneUntil) {
            return false;
        }
        int ticks = (int) Math.round(baseTicks * drMultiplier(target.getUniqueId(), now));
        if (ticks <= 0) {
            return false;
        }
        WarriorStunEvent event = new WarriorStunEvent(warrior, target, ticks);
        if (!event.callEvent() || event.durationTicks() <= 0) {
            return false;
        }
        ticks = event.durationTicks();

        attackerCooldowns.put(warrior.getUniqueId(),
                now + config.raw().getLong("warrior.combat.stun.cooldown-seconds", 8) * 1000L);
        immunity.put(target.getUniqueId(), now + ticks * 50L
                + config.raw().getLong("warrior.combat.stun.immunity-seconds", 6) * 1000L);
        apply(target, ticks);
        return true;
    }

    private void apply(LivingEntity target, int ticks) {
        boolean hadAi = target.hasAI();
        if (target instanceof Player player) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 6, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, ticks, 128, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, ticks, 2, false, false));
        } else {
            target.setAI(false);
            if (target instanceof Mob mob) {
                mob.setTarget(null);
            }
        }
        // State and release first, cue second: a stun that never releases would freeze a mob forever.
        stunned.put(target.getUniqueId(), new Stun(System.currentTimeMillis() + ticks * 50L, hadAi));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> release(target, hadAi), ticks);
        effects.stun(target, ticks);
    }

    private void release(LivingEntity target, boolean hadAi) {
        stunned.remove(target.getUniqueId());
        if (!target.isValid() || target instanceof Player) {
            return;
        }
        target.setAI(hadAi);
        if (target instanceof Mob mob) {
            mob.setTarget(null);
        }
    }

    /** Duration multiplier for repeated stuns on the same target inside the DR window. */
    private double drMultiplier(UUID target, long now) {
        long window = config.raw().getLong("warrior.combat.stun.dr-window-seconds", 20) * 1000L;
        Dr previous = diminishing.get(target);
        int count = previous == null || now - previous.windowStart() > window ? 0 : previous.count();
        diminishing.put(target, new Dr(count + 1, count == 0 ? now : previous.windowStart()));

        List<Double> steps = config.raw().getDoubleList("warrior.combat.stun.dr-steps");
        if (steps.isEmpty()) {
            return 1.0;
        }
        return steps.get(Math.min(count, steps.size() - 1));
    }

    public void clear(UUID uuid) {
        stunned.remove(uuid);
        attackerCooldowns.remove(uuid);
        immunity.remove(uuid);
        diminishing.remove(uuid);
    }
}
