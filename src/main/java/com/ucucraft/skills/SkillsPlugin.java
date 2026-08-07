package com.ucucraft.skills;

import com.ucucraft.skills.classes.ClassManager;
import com.ucucraft.skills.classes.SkillClassRegistry;
import com.ucucraft.skills.classes.impl.BlacksmithClass;
import com.ucucraft.skills.classes.impl.ExampleClass;
import com.ucucraft.skills.classes.impl.HunterClass;
import com.ucucraft.skills.classes.impl.ThiefClass;
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
import com.ucucraft.skills.hunter.GlowService;
import com.ucucraft.skills.hunter.HunterCombat;
import com.ucucraft.skills.hunter.HunterCrossbow;
import com.ucucraft.skills.hunter.HunterManager;
import com.ucucraft.skills.smithing.HarderRecipes;
import com.ucucraft.skills.smithing.ModifierRegistry;
import com.ucucraft.skills.smithing.ModifierRoller;
import com.ucucraft.skills.smithing.ModifierService;
import com.ucucraft.skills.smithing.RoseGold;
import com.ucucraft.skills.smithing.SmithingManager;
import com.ucucraft.skills.thief.CountriesHook;
import com.ucucraft.skills.thief.Lockpick;
import com.ucucraft.skills.thief.ThiefCombat;
import com.ucucraft.skills.thief.ThiefManager;
import com.ucucraft.skills.thief.ThiefPickpocket;
import com.ucucraft.skills.thief.ThiefSmoke;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

/** Entry point. Wires the modules together and registers listeners and the command. */
public final class SkillsPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private LangManager lang;
    private ClassManager classManager;
    private MinigameManager minigameManager;
    private ModifierRegistry modifierRegistry;
    private HarderRecipes harderRecipes;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        configManager = new ConfigManager(this);
        lang = new LangManager(this, configManager);

        DataStore dataStore = new YamlDataStore(this);
        SkillClassRegistry classRegistry = new SkillClassRegistry();
        classRegistry.register(new ExampleClass(configManager));
        classRegistry.register(new BlacksmithClass(configManager));
        classRegistry.register(new HunterClass(configManager));
        classRegistry.register(new ThiefClass(configManager));

        classManager = new ClassManager(dataStore, classRegistry, lang, configManager);

        ScrollItem scrollItem = new ScrollItem(this, configManager, classRegistry, lang);

        minigameManager = new MinigameManager(this, lang, configManager, classManager);
        minigameManager.register(new SpeedTypingMinigame());
        minigameManager.register(new SpeedClickingMinigame());
        minigameManager.register(new RhythmMinigame());
        minigameManager.register(new SequenceMemoryMinigame());

        modifierRegistry = new ModifierRegistry(this);
        modifierRegistry.load();
        ModifierService modifierService = new ModifierService(this, lang);
        ModifierRoller modifierRoller = new ModifierRoller(configManager, modifierRegistry);
        RoseGold roseGold = new RoseGold(configManager);
        harderRecipes = new HarderRecipes(this);
        harderRecipes.register(configManager.raw().getConfigurationSection("crafting.harder-recipes"));

        SmithingManager smithingManager = new SmithingManager(this, lang, configManager, classManager,
                minigameManager, modifierService, modifierRoller, modifierRegistry, roseGold, harderRecipes);

        HunterManager hunterManager = new HunterManager(this, lang, configManager, classManager,
                modifierService, modifierRoller, modifierRegistry);
        GlowService glowService = new GlowService(this, configManager, classManager);
        HunterCombat hunterCombat = new HunterCombat(this, configManager, classManager,
                modifierService, modifierRegistry, glowService);
        HunterCrossbow hunterCrossbow = new HunterCrossbow(this, configManager, classManager);
        // Expose the glow ability so other plugins can install a custom GlowPolicy.
        getServer().getServicesManager().register(GlowService.class, glowService, this,
                org.bukkit.plugin.ServicePriority.Normal);

        Lockpick lockpick = new Lockpick(configManager);
        CountriesHook countriesHook = new CountriesHook();
        ThiefManager thiefManager = new ThiefManager(this, lang, configManager, classManager,
                minigameManager, lockpick, countriesHook);
        ThiefCombat thiefCombat = new ThiefCombat(configManager, classManager);
        ThiefPickpocket thiefPickpocket = new ThiefPickpocket(configManager, classManager, lang, minigameManager);
        ThiefSmoke thiefSmoke = new ThiefSmoke(configManager, classManager, lang);

        getServer().getPluginManager().registerEvents(classManager, this);
        getServer().getPluginManager().registerEvents(minigameManager, this);
        getServer().getPluginManager().registerEvents(smithingManager, this);
        getServer().getPluginManager().registerEvents(hunterManager, this);
        getServer().getPluginManager().registerEvents(glowService, this);
        getServer().getPluginManager().registerEvents(hunterCombat, this);
        getServer().getPluginManager().registerEvents(hunterCrossbow, this);
        getServer().getPluginManager().registerEvents(thiefManager, this);
        getServer().getPluginManager().registerEvents(thiefCombat, this);
        getServer().getPluginManager().registerEvents(thiefPickpocket, this);
        getServer().getPluginManager().registerEvents(thiefSmoke, this);
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
        if (harderRecipes != null) {
            harderRecipes.unregister();
        }
    }

    /** Reload config, language, modifiers and crafting recipes at runtime. */
    public void reloadAll() {
        configManager.reload();
        lang.reload();
        modifierRegistry.load();
        harderRecipes.register(configManager.raw().getConfigurationSection("crafting.harder-recipes"));
    }
}
