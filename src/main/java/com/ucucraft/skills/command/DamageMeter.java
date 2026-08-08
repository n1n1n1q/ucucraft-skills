package com.ucucraft.skills.command;

import com.ucucraft.skills.lang.LangManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The admin-only {@code /damage} toggle: while it is on for a player, every hit they land flashes the
 * final damage dealt on their action bar. Read-only — it runs at {@code MONITOR} and never touches
 * the event, so it reports exactly what the target loses after every other plugin and vanilla armour.
 */
public final class DamageMeter implements TabExecutor, Listener {

    private final LangManager lang;
    private final Set<UUID> showing = new HashSet<>();

    public DamageMeter(LangManager lang) {
        this.lang = lang;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("skills.admin")) {
            lang.send(sender, "general.no-permission");
            return true;
        }
        if (!(sender instanceof Player player)) {
            lang.send(sender, "general.players-only");
            return true;
        }
        UUID uuid = player.getUniqueId();
        boolean wanted = args.length >= 1
                ? args[0].equalsIgnoreCase("on")
                : !showing.contains(uuid);
        if (wanted) {
            showing.add(uuid);
        } else {
            showing.remove(uuid);
        }
        lang.send(player, wanted ? "damage.on" : "damage.off");
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = attackerOf(event);
        if (attacker == null || !showing.contains(attacker.getUniqueId())) {
            return;
        }
        attacker.sendActionBar(lang.component("damage.hit", Map.of(
                "target", targetName(event.getEntity()),
                "damage", String.format(Locale.ROOT, "%.1f", event.getFinalDamage()))));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        showing.remove(event.getPlayer().getUniqueId());
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

    private String targetName(Entity entity) {
        return entity instanceof Player player ? player.getName() : entity.getName();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && sender.hasPermission("skills.admin")) {
            String lower = args[0].toLowerCase(Locale.ROOT);
            return List.of("on", "off").stream().filter(o -> o.startsWith(lower)).toList();
        }
        return List.of();
    }
}
