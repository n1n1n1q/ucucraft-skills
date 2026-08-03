package com.ucucraft.skills.data;

import com.ucucraft.skills.SkillsPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/** One YAML file per player under /playerdata. */
public final class YamlDataStore implements DataStore {

    private final SkillsPlugin plugin;
    private final File dir;

    public YamlDataStore(SkillsPlugin plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "playerdata");
        this.dir.mkdirs();
    }

    @Override
    public PlayerProfile load(UUID uuid) {
        PlayerProfile profile = new PlayerProfile(uuid);
        File file = file(uuid);
        if (file.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            profile.classId(yaml.getString("class"));
            profile.level(yaml.getInt("level", 1));
            profile.xp(yaml.getDouble("xp", 0));
        }
        return profile;
    }

    @Override
    public void save(PlayerProfile profile) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("class", profile.classId());
        yaml.set("level", profile.level());
        yaml.set("xp", profile.xp());
        try {
            yaml.save(file(profile.uuid()));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save profile " + profile.uuid(), e);
        }
    }

    private File file(UUID uuid) {
        return new File(dir, uuid + ".yml");
    }
}
