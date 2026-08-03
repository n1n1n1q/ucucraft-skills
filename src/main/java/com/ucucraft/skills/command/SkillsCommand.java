package com.ucucraft.skills.command;

import com.ucucraft.skills.SkillsPlugin;
import com.ucucraft.skills.classes.ClassManager;
import com.ucucraft.skills.classes.SkillClass;
import com.ucucraft.skills.classes.SkillClassRegistry;
import com.ucucraft.skills.data.PlayerProfile;
import com.ucucraft.skills.item.ScrollItem;
import com.ucucraft.skills.lang.LangManager;
import com.ucucraft.skills.minigame.MinigameManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Single entry point for /skills. */
public final class SkillsCommand implements TabExecutor {

    private static final List<String> ROOT = List.of(
            "help", "class", "minigame", "scroll", "set", "addxp", "reload");

    private final SkillsPlugin plugin;
    private final LangManager lang;
    private final ClassManager classManager;
    private final SkillClassRegistry registry;
    private final ScrollItem scroll;
    private final MinigameManager minigames;

    public SkillsCommand(SkillsPlugin plugin, LangManager lang, ClassManager classManager,
                         SkillClassRegistry registry, ScrollItem scroll, MinigameManager minigames) {
        this.plugin = plugin;
        this.lang = lang;
        this.classManager = classManager;
        this.registry = registry;
        this.scroll = scroll;
        this.minigames = minigames;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase();
        switch (sub) {
            case "class" -> classCommand(sender, args);
            case "minigame" -> minigameCommand(sender, args);
            case "scroll" -> scrollCommand(sender, args);
            case "set" -> setCommand(sender, args);
            case "addxp" -> addXpCommand(sender, args);
            case "reload" -> reloadCommand(sender);
            case "help" -> help(sender);
            default -> lang.send(sender, "general.unknown-command");
        }
        return true;
    }

    private void help(CommandSender sender) {
        sender.sendMessage(lang.msg("help.header"));
        for (String entry : lang.list("help.entries")) {
            sender.sendMessage(lang.parse(entry, Map.of()));
        }
    }

    private void classCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            lang.send(sender, "general.players-only");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("remove")) {
            classManager.unassign(player);
            lang.send(player, "class.removed");
            return;
        }
        PlayerProfile profile = classManager.profile(player);
        if (!profile.hasClass()) {
            lang.send(player, "class.none");
            return;
        }
        SkillClass skillClass = registry.get(profile.classId());
        int next = skillClass.xpForLevel(profile.level());
        lang.send(player, "class.info", Map.of(
                "class", lang.plain(skillClass.displayNameKey()),
                "level", String.valueOf(profile.level()),
                "xp", String.valueOf((long) profile.xp()),
                "next", String.valueOf(next)));
    }

    private void minigameCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            lang.send(sender, "general.players-only");
            return;
        }
        if (args.length < 2) {
            lang.send(player, "minigame.unknown", Map.of("id", "", "list", ids()));
            return;
        }
        if (!minigames.start(player, args[1])) {
            lang.send(player, "minigame.unknown", Map.of("id", args[1], "list", ids()));
        }
    }

    private void scrollCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("skills.admin")) {
            lang.send(sender, "general.no-permission");
            return;
        }
        if (!(sender instanceof Player player)) {
            lang.send(sender, "general.players-only");
            return;
        }
        String classId = args.length >= 2 ? args[1]
                : plugin.getConfig().getString("scroll.grants", "example");
        SkillClass skillClass = registry.get(classId);
        if (skillClass == null) {
            lang.send(sender, "class.unknown", Map.of("id", classId));
            return;
        }
        player.getInventory().addItem(scroll.create(classId));
        lang.send(player, "scroll.received", Map.of("class", lang.plain(skillClass.displayNameKey())));
    }

    private void setCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("skills.admin")) {
            lang.send(sender, "general.no-permission");
            return;
        }
        if (args.length < 2) {
            lang.send(sender, "general.unknown-command");
            return;
        }
        Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2])
                : (sender instanceof Player p ? p : null);
        if (target == null) {
            lang.send(sender, "general.players-only");
            return;
        }
        if (!classManager.assign(target, args[1])) {
            lang.send(sender, "class.unknown", Map.of("id", args[1]));
            return;
        }
        SkillClass skillClass = registry.get(args[1]);
        lang.send(target, "class.assigned", Map.of("class", lang.plain(skillClass.displayNameKey())));
    }

    private void addXpCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("skills.admin")) {
            lang.send(sender, "general.no-permission");
            return;
        }
        if (args.length < 2) {
            lang.send(sender, "general.unknown-command");
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            lang.send(sender, "general.unknown-command");
            return;
        }
        Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2])
                : (sender instanceof Player p ? p : null);
        if (target == null) {
            lang.send(sender, "general.players-only");
            return;
        }
        classManager.addXp(target, amount);
    }

    private void reloadCommand(CommandSender sender) {
        if (!sender.hasPermission("skills.admin")) {
            lang.send(sender, "general.no-permission");
            return;
        }
        plugin.reloadAll();
        lang.send(sender, "general.reloaded");
    }

    private String ids() {
        return String.join(", ", minigames.ids());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return filter(ROOT, args[0]);
        }
        String sub = args[0].toLowerCase();
        if (args.length == 2) {
            switch (sub) {
                case "class" -> {
                    return filter(List.of("remove"), args[1]);
                }
                case "minigame" -> {
                    return filter(new ArrayList<>(minigames.ids()), args[1]);
                }
                case "scroll", "set" -> {
                    return filter(registry.all().stream().map(SkillClass::id).collect(Collectors.toList()), args[1]);
                }
                default -> {
                    return List.of();
                }
            }
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(lower)).collect(Collectors.toList());
    }
}
