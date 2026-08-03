package com.ucucraft.skills;

import com.ucucraft.skills.classes.ClassManager;
import com.ucucraft.skills.classes.SkillClassRegistry;
import com.ucucraft.skills.classes.impl.ExampleClass;
import com.ucucraft.skills.command.SkillsCommand;
import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.data.DataStore;
import com.ucucraft.skills.data.YamlDataStore;
import com.ucucraft.skills.item.ScrollItem;
import com.ucucraft.skills.item.ScrollListener;
import com.ucucraft.skills.lang.LangManager;
import com.ucucraft.skills.minigame.MinigameManager;
import com.ucucraft.skills.minigame.games.RhythmMinigame;
import com.ucucraft.skills.minigame.games.SequenceMemoryMinigame;
import com.ucucraft.skills.minigame.games.SpeedClickingMinigame;
import com.ucucraft.skills.minigame.games.SpeedTypingMinigame;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/** Entry point. Wires the modules together and registers listeners and the command. */
public final class SkillsPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private LangManager lang;
    private ClassManager classManager;
    private MinigameManager minigameManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        lang = new LangManager(this, configManager);

        DataStore dataStore = new YamlDataStore(this);
        SkillClassRegistry classRegistry = new SkillClassRegistry();
        classRegistry.register(new ExampleClass(configManager));

        classManager = new ClassManager(dataStore, classRegistry, lang, configManager);

        ScrollItem scrollItem = new ScrollItem(this, configManager, classRegistry, lang);

        minigameManager = new MinigameManager(this, lang, configManager, classManager);
        minigameManager.register(new SpeedTypingMinigame());
        minigameManager.register(new SpeedClickingMinigame());
        minigameManager.register(new RhythmMinigame());
        minigameManager.register(new SequenceMemoryMinigame());

        getServer().getPluginManager().registerEvents(classManager, this);
        getServer().getPluginManager().registerEvents(minigameManager, this);
        getServer().getPluginManager().registerEvents(
                new ScrollListener(scrollItem, classManager, classRegistry, lang), this);

        Objects.requireNonNull(getCommand("skills")).setExecutor(
                new SkillsCommand(this, lang, classManager, classRegistry, scrollItem, minigameManager));
    }

    @Override
    public void onDisable() {
        if (classManager != null) {
            classManager.saveAll();
        }
    }

    /** Reload config and language files at runtime. */
    public void reloadAll() {
        configManager.reload();
        lang.reload();
    }
}
