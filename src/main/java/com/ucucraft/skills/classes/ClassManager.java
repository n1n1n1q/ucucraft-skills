package com.ucucraft.skills.classes;

import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.data.DataStore;
import com.ucucraft.skills.data.PlayerProfile;
import com.ucucraft.skills.lang.LangManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Owns assignment, XP, level-ups and bonus application. */
public final class ClassManager implements Listener {

    private final DataStore store;
    private final SkillClassRegistry registry;
    private final LangManager lang;
    private final ConfigManager config;
    private final Map<UUID, PlayerProfile> cache = new HashMap<>();

    public ClassManager(DataStore store, SkillClassRegistry registry, LangManager lang, ConfigManager config) {
        this.store = store;
        this.registry = registry;
        this.lang = lang;
        this.config = config;
    }

    public PlayerProfile profile(Player player) {
        return cache.computeIfAbsent(player.getUniqueId(), store::load);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerProfile profile = profile(player);
        SkillClass skillClass = registry.get(profile.classId());
        if (skillClass != null) {
            skillClass.applyBonuses(player, profile.level());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PlayerProfile profile = cache.remove(uuid);
        if (profile != null) {
            store.save(profile);
        }
    }

    public boolean assign(Player player, String classId) {
        SkillClass skillClass = registry.get(classId);
        if (skillClass == null) {
            return false;
        }
        PlayerProfile profile = profile(player);
        profile.classId(skillClass.id());
        profile.level(1);
        profile.xp(0);
        skillClass.applyBonuses(player, 1);
        store.save(profile);
        return true;
    }

    public void unassign(Player player) {
        PlayerProfile profile = profile(player);
        SkillClass skillClass = registry.get(profile.classId());
        if (skillClass != null) {
            skillClass.clearBonuses(player);
        }
        profile.classId(null);
        profile.level(1);
        profile.xp(0);
        store.save(profile);
    }

    public void addXp(Player player, double amount) {
        PlayerProfile profile = profile(player);
        SkillClass skillClass = registry.get(profile.classId());
        if (skillClass == null) {
            lang.send(player, "class.none");
            return;
        }
        lang.send(player, "class.xp-added", Map.of("amount", format(amount)));
        profile.xp(profile.xp() + amount);

        int maxLevel = config.maxLevel();
        boolean leveled = false;
        while (profile.level() < maxLevel && profile.xp() >= skillClass.xpForLevel(profile.level())) {
            profile.xp(profile.xp() - skillClass.xpForLevel(profile.level()));
            profile.level(profile.level() + 1);
            leveled = true;
        }

        if (leveled) {
            skillClass.applyBonuses(player, profile.level());
            lang.send(player, "class.level-up", Map.of(
                    "class", lang.plain(skillClass.displayNameKey()),
                    "level", String.valueOf(profile.level())));
            if (profile.level() >= maxLevel) {
                lang.send(player, "class.max-level");
            }
        }
        store.save(profile);
    }

    /** Strength of the class's accessibility (visual cue) bonus, 0 if none. */
    public int accessibilityCueLevel(Player player) {
        PlayerProfile profile = profile(player);
        if (!profile.hasClass()) {
            return 0;
        }
        return config.raw().getInt("classes." + profile.classId() + ".visual-cue-level", 0);
    }

    public void saveAll() {
        for (PlayerProfile profile : cache.values()) {
            store.save(profile);
        }
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }
}
