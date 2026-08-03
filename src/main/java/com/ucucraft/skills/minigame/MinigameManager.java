package com.ucucraft.skills.minigame;

import com.ucucraft.skills.SkillsPlugin;
import com.ucucraft.skills.classes.ClassManager;
import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.lang.LangManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Registers minigames and routes input to the player's active session. */
public final class MinigameManager implements Listener {

    private final SkillsPlugin plugin;
    private final LangManager lang;
    private final ConfigManager config;
    private final ClassManager classManager;
    private final Map<String, Minigame> games = new LinkedHashMap<>();
    private final Map<Player, MinigameSession> active = new HashMap<>();

    public MinigameManager(SkillsPlugin plugin, LangManager lang, ConfigManager config, ClassManager classManager) {
        this.plugin = plugin;
        this.lang = lang;
        this.config = config;
        this.classManager = classManager;
    }

    public void register(Minigame game) {
        games.put(game.id().toLowerCase(Locale.ROOT), game);
    }

    public Set<String> ids() {
        return games.keySet();
    }

    public SkillsPlugin plugin() {
        return plugin;
    }

    public LangManager lang() {
        return lang;
    }

    public ConfigManager config() {
        return config;
    }

    public ClassManager classManager() {
        return classManager;
    }

    /** Returns false only when no minigame has the given id. */
    public boolean start(Player player, String id) {
        Minigame game = games.get(id.toLowerCase(Locale.ROOT));
        if (game == null) {
            return false;
        }
        if (active.containsKey(player)) {
            lang.send(player, "minigame.already-running");
            return true;
        }
        MinigameSession session = new MinigameSession(this, player, game);
        active.put(player, session);
        lang.send(player, "minigame.starting", Map.of("game", game.id()));
        game.start(session);
        return true;
    }

    public void finish(MinigameSession session, MinigameResult result) {
        if (active.remove(session.player()) == null) {
            return;
        }
        session.cancelTasks();
        lang.send(session.player(), result.won() ? "minigame.win" : "minigame.lose",
                Map.of("score", String.valueOf(result.score())));
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        MinigameSession session = active.get(event.getPlayer());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        plugin.getServer().getScheduler().runTask(plugin,
                () -> session.game().onChat(session, message));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }
        MinigameSession session = active.get(event.getPlayer());
        if (session == null) {
            return;
        }
        event.setCancelled(true);
        session.game().onClick(session);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        MinigameSession session = active.remove(event.getPlayer());
        if (session != null) {
            session.cancelTasks();
        }
    }
}
