package com.ucucraft.skills.runes;

import com.ucucraft.skills.classes.ClassManager;
import com.ucucraft.skills.classes.impl.RunesmithClass;
import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.data.PlayerProfile;
import com.ucucraft.skills.lang.LangManager;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Level-4 passive: a held/worn runed item has a chance to act one level higher. Scoped to what
 * Bukkit's public API can pre-compute — Fortune drops, melee Sharpness/Smite/Bane damage,
 * Protection reduction, and Mending's repair amount (which has no vanilla levels, so it's scaled
 * instead). Every proc still fires its sound + trim-coloured particle even outside that curated
 * set, so the passive is never silently inert; see docs/runesmith.md for the scoping rationale.
 */
public final class ResonanceService implements Listener {

    private final ConfigManager config;
    private final LangManager lang;
    private final ClassManager classManager;
    private final RuneService runes;
    private final RuneRegistry registry;

    public ResonanceService(ConfigManager config, LangManager lang, ClassManager classManager,
                            RuneService runes, RuneRegistry registry) {
        this.config = config;
        this.lang = lang;
        this.classManager = classManager;
        this.runes = runes;
        this.registry = registry;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!eligible(player)) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        Rune rune = runeOf(tool);
        if (rune == null || !roll()) {
            return;
        }
        proc(player, tool);
        if (rune.enchantment() != Enchantment.FORTUNE) {
            return;
        }
        Block block = event.getBlock();
        ItemStack bumped = tool.clone();
        ItemMeta meta = bumped.getItemMeta();
        meta.addEnchant(Enchantment.FORTUNE, runes.level(tool) + 1, true);
        bumped.setItemMeta(meta);
        event.setDropItems(false);
        for (ItemStack drop : block.getDrops(bumped, player)) {
            block.getWorld().dropItemNaturally(block.getLocation(), drop);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker && eligible(attacker)) {
            ItemStack weapon = attacker.getInventory().getItemInMainHand();
            Rune rune = runeOf(weapon);
            if (rune != null && roll()) {
                proc(attacker, weapon);
                if (isSharpnessFamily(rune.enchantment())) {
                    event.setDamage(event.getDamage() + 0.5);
                }
            }
        }
        if (event.getEntity() instanceof Player victim && eligible(victim)) {
            for (ItemStack piece : victim.getInventory().getArmorContents()) {
                Rune rune = runeOf(piece);
                if (rune == null || !roll()) {
                    continue;
                }
                proc(victim, piece);
                if (rune.enchantment() == Enchantment.PROTECTION) {
                    event.setDamage(Math.max(0, event.getDamage() * 0.96));
                }
            }
        }
    }

    @EventHandler
    public void onMend(PlayerItemMendEvent event) {
        Player player = event.getPlayer();
        if (!eligible(player)) {
            return;
        }
        Rune rune = runeOf(event.getItem());
        if (rune == null || rune.enchantment() != Enchantment.MENDING || !roll()) {
            return;
        }
        proc(player, event.getItem());
        double bonus = config.raw().getDouble("runes.resonance.mend-bonus", 0.5);
        event.setRepairAmount((int) Math.round(event.getRepairAmount() * (1 + bonus)));
    }

    // --- Helpers --------------------------------------------------------------------------------

    private boolean eligible(Player player) {
        PlayerProfile profile = classManager.profile(player);
        if (!profile.hasClass() || !RunesmithClass.ID.equalsIgnoreCase(profile.classId())) {
            return false;
        }
        int minLevel = config.raw().getInt("runes.resonance.min-level", 4);
        return profile.level() >= minLevel;
    }

    private boolean roll() {
        double chance = config.raw().getDouble("runes.resonance.chance", 0.15);
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    private Rune runeOf(ItemStack item) {
        if (item == null || !runes.hasRune(item)) {
            return null;
        }
        return registry.get(runes.patternId(item));
    }

    private boolean isSharpnessFamily(Enchantment enchantment) {
        return enchantment == Enchantment.SHARPNESS || enchantment == Enchantment.SMITE
                || enchantment == Enchantment.BANE_OF_ARTHROPODS;
    }

    private void proc(Player player, ItemStack item) {
        Location loc = player.getLocation().add(0, 1, 0);
        String soundId = config.raw().getString("runes.resonance.sound", "");
        if (!soundId.isBlank()) {
            player.getWorld().playSound(loc, soundId, 1.0f, 1.2f);
        }
        var color = runes.colorOf(runes.material(item));
        player.getWorld().spawnParticle(Particle.DUST, loc, 12, 0.4, 0.4, 0.4,
                new Particle.DustOptions(color, 1.2f));
        player.sendActionBar(lang.component("runes.resonance-proc"));
    }
}
