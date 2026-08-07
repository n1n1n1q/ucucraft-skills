package com.ucucraft.skills.warrior;

import com.ucucraft.skills.SkillsPlugin;
import com.ucucraft.skills.classes.ClassManager;
import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.lang.LangManager;
import com.ucucraft.skills.thief.CountriesHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Wires the warrior module together and owns its single repeating task: Fervor decay, the attribute
 * refresh (so a stance suspends and snaps back on its own) and the ambient aura all run from there
 * at {@code warrior.particles.interval-ticks}.
 *
 * <p>Also the input layer: sneak + swap-hand cycles stances, sneak + right-click air opens the
 * picker, and taking damage cancels a pending switch.
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
    private final WarriorCombat combat;
    private final WarCryService warcries;
    private final StanceMenu menu;
    private final Set<UUID> tracked = new HashSet<>();
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
        this.combat = new WarriorCombat(config, lang, stances, fervor, stuns, effects, targets);
        this.warcries = new WarCryService(plugin, config, lang, stances, fervor, stuns, effects,
                targets, applier, combat);
        this.menu = new StanceMenu(lang, stances);
    }

    /** Loads the stances, registers every listener and exposes {@link StanceService} as a service. */
    public void register() {
        registry.load();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getServer().getPluginManager().registerEvents(combat, plugin);
        plugin.getServer().getPluginManager().registerEvents(warcries, plugin);
        plugin.getServer().getPluginManager().registerEvents(menu, plugin);
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
        for (Player player : plugin.getServer().getOnlinePlayers()) {
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

    /** Third cue channel: the stance name (and Fervor) on the action bar. */
    private void hud(Player player, Stance active) {
        if (!config.raw().getBoolean("warrior.hud.enabled", true) || stances.warmingUp(player)) {
            return;
        }
        Map<String, String> placeholders = Map.of(
                "stance", stances.displayName(stances.selected(player)),
                "bar", bar(fervor.ratio(player)),
                "fervor", String.valueOf(Math.round(fervor.value(player))));
        player.sendActionBar(lang.component(
                stances.suspended(player) ? "warrior.hud-suspended" : "warrior.hud", placeholders));
    }

    private String bar(double ratio) {
        int width = Math.max(0, config.raw().getInt("warrior.hud.bar-width", 10));
        int filled = (int) Math.round(ratio * width);
        return glyph("filled", "|").repeat(filled) + glyph("empty", ".").repeat(width - filled);
    }

    private String glyph(String key, String fallback) {
        String value = config.raw().getString("warrior.hud." + key, fallback);
        return value == null || value.isEmpty() ? fallback : value.substring(0, 1);
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
        stances.cycle(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        // Only with a melee weapon in hand, so sneaking to eat or to draw a bow still works.
        if (!enabled() || event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_AIR
                || !player.isSneaking() || !stances.isWarrior(player)
                || WeaponFamily.of(player.getInventory().getItemInMainHand().getType()) == null) {
            return;
        }
        event.setCancelled(true);
        menu.open(player);
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
