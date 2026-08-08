package com.ucucraft.skills.lang;

import com.ucucraft.skills.SkillsPlugin;
import com.ucucraft.skills.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Loads lang/&lt;locale&gt;.yml and renders MiniMessage components. */
public final class LangManager {

    private final SkillsPlugin plugin;
    private final ConfigManager config;
    private final MiniMessage mini = MiniMessage.miniMessage();
    private YamlConfiguration messages;

    public LangManager(SkillsPlugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        reload();
    }

    public void reload() {
        String locale = config.locale();
        saveIfMissing("lang/en.yml");
        saveIfMissing("lang/" + locale + ".yml");

        File file = new File(plugin.getDataFolder(), "lang/" + locale + ".yml");
        if (!file.exists()) {
            file = new File(plugin.getDataFolder(), "lang/en.yml");
        }
        messages = YamlConfiguration.loadConfiguration(file);

        var stream = plugin.getResource("lang/" + locale + ".yml");
        if (stream == null) {
            stream = plugin.getResource("lang/en.yml");
        }
        if (stream != null) {
            messages.setDefaults(YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)));
        }
    }

    private void saveIfMissing(String path) {
        if (plugin.getResource(path) != null
                && !new File(plugin.getDataFolder(), path).exists()) {
            plugin.saveResource(path, false);
        }
    }

    /** Message with the configured prefix, ready to send. */
    public Component msg(String key, Map<String, String> placeholders) {
        return parse(config.prefix() + string(key), placeholders);
    }

    public Component msg(String key) {
        return msg(key, Map.of());
    }

    /** Message without the prefix (action bars, item names, inline text). */
    public Component component(String key, Map<String, String> placeholders) {
        return parse(string(key), placeholders);
    }

    public Component component(String key) {
        return component(key, Map.of());
    }

    /** Parse an arbitrary MiniMessage string with placeholders. */
    public Component parse(String miniMessage, Map<String, String> placeholders) {
        TagResolver[] resolvers = placeholders.entrySet().stream()
                .map(e -> Placeholder.parsed(e.getKey(), e.getValue()))
                .toArray(TagResolver[]::new);
        return mini.deserialize(miniMessage, resolvers);
    }

    /** Raw MiniMessage string for a key (used as a placeholder value). */
    public String plain(String key) {
        return string(key);
    }

    /**
     * The string for a key, falling back to the key itself when it is defined nowhere. Consults the
     * bundled jar defaults on purpose: {@code getString(key, key)} would pass an explicit default and
     * so never reach them, leaving a stale data-folder lang file (one predating a new key) rendering
     * the raw key instead of the shipped text.
     */
    private String string(String key) {
        String value = messages.getString(key);
        return value != null ? value : key;
    }

    public List<String> list(String key) {
        return messages.getStringList(key);
    }

    public void send(CommandSender to, String key, Map<String, String> placeholders) {
        to.sendMessage(msg(key, placeholders));
    }

    public void send(CommandSender to, String key) {
        to.sendMessage(msg(key));
    }
}
