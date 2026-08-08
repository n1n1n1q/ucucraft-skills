package com.ucucraft.skills.warrior;

import com.ucucraft.skills.SkillsPlugin;
import com.ucucraft.skills.classes.ClassManager;
import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.lang.LangManager;
import com.ucucraft.skills.thief.CountriesHook;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Wires the warrior module together and owns its single repeating task: Fervor decay, the attribute
 * refresh (so a stance suspends and snaps back on its own) and the ambient aura all run from there
 * at {@code warrior.particles.interval-ticks}.
 *
 * <p>Also the input layer: sneak + swap-hand shouts a war cry when one is ready and otherwise
 * cycles stances, and taking damage cancels a pending switch.
 */
public final class WarriorManager implements Listener {

    private final SkillsPlugin plugin;
    private final LangManager lang;
    private final ConfigManager config;
    private final StanceRegistry registry;
    private final StanceApplier applier;
    private final FervorService fervor;
    private final StanceEffects effects;
    private final StanceService stances;
    private final StunService stuns;
    private final WarriorTargets targets;
    private final DuelService duels;
    private final WarriorCombat combat;
    private final WarCryService warcries;
    private final Set<UUID> tracked = new HashSet<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private BukkitTask task;
    private int tick;

    public WarriorManager(SkillsPlugin plugin, LangManager lang, ConfigManager config,
                          ClassManager classes, CountriesHook countries) {
        this.plugin = plugin;
        this.lang = lang;
        this.config = config;
        this.registry = new StanceRegistry(plugin, config);
        this.applier = new StanceApplier(plugin);
        this.fervor = new FervorService(config);
        this.effects = new StanceEffects(plugin, config);
        this.stances = new StanceService(plugin, config, lang, classes, registry, applier, fervor, effects);
        this.stuns = new StunService(plugin, config, effects);
        this.targets = new WarriorTargets(plugin, config, countries, applier);
        this.duels = new DuelService(plugin, config, lang, effects);
        this.combat = new WarriorCombat(config, lang, stances, fervor, stuns, effects, targets, duels);
        this.warcries = new WarCryService(plugin, config, lang, stances, fervor, stuns, effects,
                targets, applier, combat, duels);
    }

    /** Loads the stances, registers every listener and exposes {@link StanceService} as a service. */
    public void register() {
        registry.load();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getPluginManager().registerEvents(combat, plugin);
        plugin.getServer().getPluginManager().registerEvents(duels, plugin);
        plugin.getServer().getServicesManager().register(StanceService.class, stances, plugin,
                ServicePriority.Normal);
        startTask();
    }

    public StanceService stances() {
        return stances;
    }

    public StanceEffects effects() {
        return effects;
    }

    /** Re-reads the stance files and recomputes every online warrior from the new definitions. */
    public void reload() {
        // Strip against the OLD definitions first — an attribute dropped from a stance file would
        // otherwise no longer be known, and its modifier would stay on the player forever.
        duels.endAll();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            stances.clear(player);
        }
        registry.load();
        startTask();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            stances.refresh(player);
        }
    }

    /** Strips every stance modifier — a leaked attribute modifier would be permanent. */
    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        duels.endAll();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            hideBar(player);
            stances.clear(player);
        }
    }

    private void startTask() {
        if (task != null) {
            task.cancel();
        }
        long interval = Math.max(1, config.raw().getLong("warrior.particles.interval-ticks", 10));
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(interval), interval, interval);
    }

    private void tick(long interval) {
        tick++;
        if (!enabled()) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!stances.isWarrior(player)) {
                // Someone who just changed class keeps their modifiers otherwise, and they persist.
                if (tracked.remove(player.getUniqueId())) {
                    stances.clear(player);
                    hideBar(player);
                }
                continue;
            }
            tracked.add(player.getUniqueId());
            fervor.decay(player, interval / 20.0);
            stances.refresh(player);
            Stance active = stances.active(player);
            effects.aura(player, active, fervor.ratio(player), tick);
            hud(player, active);
        }
    }

    /**
     * Third cue channel: a boss bar carrying the stance name and Fervor (the bar's fill IS the Fervor
     * fraction), shown while a melee weapon is in hand. Hidden otherwise, so the bar rides the weapon.
     */
    private void hud(Player player, Stance active) {
        if (!config.raw().getBoolean("warrior.hud.enabled", true) || stances.warmingUp(player)
                || duels.inDuel(player)
                || WeaponFamily.of(player.getInventory().getItemInMainHand().getType()) == null) {
            // A duel replaces the Fervor bar with its own countdown bar, owned by DuelService.
            hideBar(player);
            return;
        }
        Component title;
        float progress;
        BossBar.Color color;
        long cooldown = warcries.cooldownRemaining(player);
        if (cooldown > 0) {
            // A war cry just fired: until it recharges, the bar counts the cooldown down instead of
            // Fervor — that, not Fervor, is why shift+F cycles stances rather than shouting again.
            title = lang.component("warrior.hud-cooldown", Map.of(
                    "stance", stances.displayName(stances.selected(player)),
                    "seconds", String.valueOf(cooldown / 1000 + 1)));
            progress = (float) cooldown / Math.max(1, warcries.cooldownTotalMs());
            color = barColor("boss-bar-cooldown-color", BossBar.Color.BLUE);
        } else {
            Map<String, String> placeholders = Map.of(
                    "stance", stances.displayName(stances.selected(player)),
                    "fervor", String.valueOf(Math.round(fervor.value(player))));
            title = lang.component(
                    stances.suspended(player) ? "warrior.hud-suspended" : "warrior.hud", placeholders);
            progress = (float) fervor.ratio(player);
            color = fervor.charged(player)
                    ? barColor("boss-bar-charged-color", BossBar.Color.RED)
                    : barColor("boss-bar-color", BossBar.Color.YELLOW);
        }
        if (config.raw().getBoolean("warrior.hud.boss-bar", true)) {
            showBar(player, title, progress, color);
        } else {
            hideBar(player);
            player.sendActionBar(title);
        }
    }

    /** Creates the player's boss bar on first show, then only updates it in place afterwards. */
    private void showBar(Player player, Component title, float progress, BossBar.Color color) {
        float clamped = Math.max(0f, Math.min(1f, progress));
        BossBar bar = bars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(title, clamped, color, barOverlay());
            bars.put(player.getUniqueId(), bar);
            player.showBossBar(bar);
        } else {
            bar.name(title);
            bar.progress(clamped);
            bar.color(color);
        }
    }

    private void hideBar(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    private BossBar.Color barColor(String key, BossBar.Color fallback) {
        String value = config.raw().getString("warrior.hud." + key, fallback.name());
        try {
            return BossBar.Color.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    private BossBar.Overlay barOverlay() {
        String value = config.raw().getString("warrior.hud.boss-bar-overlay", "PROGRESS");
        try {
            return BossBar.Overlay.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return BossBar.Overlay.PROGRESS;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Clear first: a war cry buff or an armour shred that outlived the last session is still on
        // the profile, and attribute modifiers do not expire on their own.
        stances.clear(event.getPlayer());
        stances.refresh(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cleanup(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (config.raw().getBoolean("warrior.stance.reset-on-death", true)) {
            stances.reset(player);
        }
        cleanup(player);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> stances.refresh(event.getPlayer()));
    }

    private void cleanup(Player player) {
        UUID uuid = player.getUniqueId();
        tracked.remove(uuid);
        hideBar(player);
        stances.clear(player);
        fervor.reset(player);
        stuns.clear(uuid);
        combat.forget(uuid);
        warcries.forget(uuid);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!enabled() || !player.isSneaking() || !stances.isWarrior(player)) {
            return;
        }
        event.setCancelled(true);
        // One gesture: shout when a cry is ready to fire, otherwise cycle to the next stance.
        if (warcries.ready(player)) {
            warcries.shout(player);
        } else {
            stances.cycle(player);
        }
    }

    @EventHandler
    public void onHeldItem(PlayerItemHeldEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> stances.refresh(event.getPlayer()));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player
                && config.raw().getBoolean("warrior.stance.cancel-warmup-on-damage", true)
                && stances.warmingUp(player)) {
            stances.cancelWarmup(player);
            lang.send(player, "warrior.stance-interrupted");
        }
    }

    private boolean enabled() {
        return config.raw().getBoolean("warrior.enabled", true);
    }
}
