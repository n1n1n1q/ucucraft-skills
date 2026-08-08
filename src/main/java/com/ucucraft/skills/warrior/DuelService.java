package com.ucucraft.skills.warrior;

import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.lang.LangManager;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The riposte war cry's formal duel. For its {@code duration-seconds} the two fighters deal and take
 * only {@code outsider-damage-multiplier} to everyone outside the duel (player-vs-player only — mobs
 * are unaffected), while against each other damage is full and the initiating warrior deals
 * {@code warrior-damage-multiplier}. Whoever dealt more when the timer runs out — or the survivor if
 * one dies — wins; only a warrior win pays out ({@code win-effects}). Both fighters carry a countdown
 * boss bar and a private particle aura only they can see. Tunables: {@code warrior.combat.duel}.
 */
public final class DuelService implements Listener {

    /** One live duel; the two fighters both key to this same instance in {@link #byPlayer}. */
    private static final class Duel {
        private final UUID warrior;
        private final UUID opponent;
        private final long endAt;
        private double warriorDamage;
        private double opponentDamage;
        private BukkitTask task;
        private BossBar warriorBar;
        private BossBar opponentBar;

        private Duel(UUID warrior, UUID opponent, long endAt) {
            this.warrior = warrior;
            this.opponent = opponent;
            this.endAt = endAt;
        }
    }

    private final Plugin plugin;
    private final ConfigManager config;
    private final LangManager lang;
    private final StanceEffects effects;
    private final Map<UUID, Duel> byPlayer = new HashMap<>();

    public DuelService(Plugin plugin, ConfigManager config, LangManager lang, StanceEffects effects) {
        this.plugin = plugin;
        this.config = config;
        this.lang = lang;
        this.effects = effects;
    }

    /** Starts a duel between the warrior and the opponent; refused if either is already dueling. */
    public boolean start(Player warrior, Player opponent) {
        if (warrior.equals(opponent) || inDuel(warrior) || inDuel(opponent)) {
            return false;
        }
        long total = Math.max(1, config.raw().getInt("warrior.combat.duel.duration-seconds", 15)) * 1000L;
        Duel duel = new Duel(warrior.getUniqueId(), opponent.getUniqueId(),
                System.currentTimeMillis() + total);
        duel.warriorBar = BossBar.bossBar(Component.empty(), 1f, barColor(), BossBar.Overlay.PROGRESS);
        duel.opponentBar = BossBar.bossBar(Component.empty(), 1f, barColor(), BossBar.Overlay.PROGRESS);
        warrior.showBossBar(duel.warriorBar);
        opponent.showBossBar(duel.opponentBar);
        byPlayer.put(warrior.getUniqueId(), duel);
        byPlayer.put(opponent.getUniqueId(), duel);

        lang.send(warrior, "warrior.duel-start",
                Map.of("opponent", opponent.getName(), "seconds", String.valueOf(total / 1000)));
        lang.send(opponent, "warrior.duel-start",
                Map.of("opponent", warrior.getName(), "seconds", String.valueOf(total / 1000)));
        duel.task = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, () -> tick(duel, total), 0L, 4L);
        return true;
    }

    public boolean inDuel(Player player) {
        return byPlayer.containsKey(player.getUniqueId());
    }

    /** The uuid of this player's current duel opponent, or {@code null} when they are not dueling. */
    public UUID opponentOf(Player player) {
        Duel duel = byPlayer.get(player.getUniqueId());
        if (duel == null) {
            return null;
        }
        return duel.warrior.equals(player.getUniqueId()) ? duel.opponent : duel.warrior;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!config.raw().getBoolean("warrior.enabled", true)
                || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attackerOf(event);
        Duel attackerDuel = attacker == null ? null : byPlayer.get(attacker.getUniqueId());
        Duel victimDuel = byPlayer.get(victim.getUniqueId());
        if (attackerDuel == null && victimDuel == null) {
            return;
        }
        if (attacker != null && attackerDuel != null && attackerDuel == victimDuel) {
            resolveDuelHit(attackerDuel, attacker, event);
            return;
        }
        // One side is dueling someone else: this is an outsider hit. Scale player-vs-player only —
        // a non-player damager (mob, fire, explosion) reaches neither the tally nor the reduction.
        if (attacker == null) {
            return;
        }
        event.setDamage(event.getDamage()
                * config.raw().getDouble("warrior.combat.duel.outsider-damage-multiplier", 0.25));
    }

    private void resolveDuelHit(Duel duel, Player attacker, EntityDamageByEntityEvent event) {
        if (attacker.getUniqueId().equals(duel.warrior)) {
            event.setDamage(event.getDamage()
                    * config.raw().getDouble("warrior.combat.duel.warrior-damage-multiplier", 1.1));
            duel.warriorDamage += event.getFinalDamage();
        } else {
            duel.opponentDamage += event.getFinalDamage();
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Duel duel = byPlayer.get(event.getEntity().getUniqueId());
        if (duel != null) {
            finish(duel, event.getEntity().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Duel duel = byPlayer.get(event.getPlayer().getUniqueId());
        if (duel != null) {
            finish(duel, event.getPlayer().getUniqueId());
        }
    }

    /** Ends every live duel with no winner — called on plugin shutdown and reload. */
    public void endAll() {
        for (Duel duel : List.copyOf(byPlayer.values())) {
            if (byPlayer.containsKey(duel.warrior)) {
                cancel(duel);
            }
        }
    }

    private void tick(Duel duel, long total) {
        Player warrior = plugin.getServer().getPlayer(duel.warrior);
        Player opponent = plugin.getServer().getPlayer(duel.opponent);
        if (warrior == null || opponent == null) {
            finish(duel, warrior == null ? duel.warrior : duel.opponent);
            return;
        }
        long remaining = duel.endAt - System.currentTimeMillis();
        if (remaining <= 0) {
            finish(duel, null);
            return;
        }
        float progress = Math.max(0f, Math.min(1f, (float) remaining / total));
        long seconds = (remaining + 999) / 1000;
        updateBar(duel.warriorBar, seconds, duel.warriorDamage, duel.opponentDamage, progress);
        updateBar(duel.opponentBar, seconds, duel.opponentDamage, duel.warriorDamage, progress);
        effects.duelAura(warrior, opponent);
    }

    private void updateBar(BossBar bar, long seconds, double mine, double theirs, float progress) {
        bar.name(lang.component("warrior.duel-bar", Map.of(
                "seconds", String.valueOf(seconds),
                "mine", String.valueOf(Math.round(mine)),
                "theirs", String.valueOf(Math.round(theirs)))));
        bar.progress(progress);
    }

    /** Resolves the duel: {@code loser} is set when someone died or quit, otherwise damage decides. */
    private void finish(Duel duel, UUID loser) {
        cancel(duel);
        Player warrior = plugin.getServer().getPlayer(duel.warrior);
        Player opponent = plugin.getServer().getPlayer(duel.opponent);

        UUID winner;
        if (loser != null) {
            winner = loser.equals(duel.warrior) ? duel.opponent : duel.warrior;
        } else if (duel.warriorDamage > duel.opponentDamage) {
            winner = duel.warrior;
        } else if (duel.opponentDamage > duel.warriorDamage) {
            winner = duel.opponent;
        } else {
            winner = null; // a draw pays out to no one
        }

        if (duel.warrior.equals(winner) && warrior != null) {
            reward(warrior);
        }
        report(warrior, opponent == null ? "?" : opponent.getName(), duel.warrior.equals(winner), winner == null);
        report(opponent, warrior == null ? "?" : warrior.getName(), duel.opponent.equals(winner), winner == null);
    }

    /** Hides both bars, stops the ticker and forgets the duel. */
    private void cancel(Duel duel) {
        if (duel.task != null) {
            duel.task.cancel();
            duel.task = null;
        }
        byPlayer.remove(duel.warrior);
        byPlayer.remove(duel.opponent);
        Player warrior = plugin.getServer().getPlayer(duel.warrior);
        Player opponent = plugin.getServer().getPlayer(duel.opponent);
        if (warrior != null && duel.warriorBar != null) {
            warrior.hideBossBar(duel.warriorBar);
        }
        if (opponent != null && duel.opponentBar != null) {
            opponent.hideBossBar(duel.opponentBar);
        }
    }

    private void report(Player player, String opponentName, boolean won, boolean draw) {
        if (player == null) {
            return;
        }
        String key = draw ? "warrior.duel-draw" : won ? "warrior.duel-win" : "warrior.duel-loss";
        lang.send(player, key, Map.of("opponent", opponentName));
    }

    /** The warrior's prize for winning: the configured potion effects for {@code win-duration-seconds}. */
    private void reward(Player warrior) {
        ConfigurationSection section =
                config.raw().getConfigurationSection("warrior.combat.duel.win-effects");
        if (section == null) {
            return;
        }
        int duration = Math.max(1,
                config.raw().getInt("warrior.combat.duel.win-duration-seconds", 20)) * 20;
        for (String key : section.getKeys(false)) {
            PotionEffectType type =
                    Registry.EFFECT.get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
            if (type != null) {
                warrior.addPotionEffect(new PotionEffect(type, duration, section.getInt(key)));
            }
        }
    }

    private Player attackerOf(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player direct) {
            return direct;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    private BossBar.Color barColor() {
        String value = config.raw().getString("warrior.combat.duel.bar-color", "RED");
        try {
            return BossBar.Color.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BossBar.Color.RED;
        }
    }
}
