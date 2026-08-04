package com.ucucraft.skills.smithing;

import com.ucucraft.skills.SkillsPlugin;
import com.ucucraft.skills.classes.ClassManager;
import com.ucucraft.skills.classes.impl.BlacksmithClass;
import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.data.PlayerProfile;
import com.ucucraft.skills.lang.LangManager;
import com.ucucraft.skills.minigame.MinigameManager;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Ties the crafting flow to the blacksmith class: gates master-only tiers, forces non-masters onto
 * the harder armor recipes, rolls a rhythm-minigame modifier on a blacksmith's crafts, applies
 * debuffs to regular crafters, and handles shift-right-click reroll/fix on held gear.
 */
public final class SmithingManager implements Listener {

    private final SkillsPlugin plugin;
    private final LangManager lang;
    private final ConfigManager config;
    private final ClassManager classManager;
    private final MinigameManager minigames;
    private final ModifierService modifiers;
    private final ModifierRoller roller;
    private final ModifierRegistry registry;
    private final RoseGold roseGold;
    private final HarderRecipes harderRecipes;
    private final NamespacedKey nonceKey;

    public SmithingManager(SkillsPlugin plugin, LangManager lang, ConfigManager config,
                           ClassManager classManager, MinigameManager minigames,
                           ModifierService modifiers, ModifierRoller roller,
                           ModifierRegistry registry, RoseGold roseGold, HarderRecipes harderRecipes) {
        this.plugin = plugin;
        this.lang = lang;
        this.config = config;
        this.classManager = classManager;
        this.minigames = minigames;
        this.modifiers = modifiers;
        this.roller = roller;
        this.registry = registry;
        this.roseGold = roseGold;
        this.harderRecipes = harderRecipes;
        this.nonceKey = new NamespacedKey(plugin, "forge_nonce");
    }

    // --- Recipe gating (preview) --------------------------------------------------------------

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (!enabled() || !(event.getView().getPlayer() instanceof Player player)) {
            return;
        }
        ItemStack result = event.getInventory().getResult();
        if (result == null) {
            return;
        }
        GearTier tier = GearTier.of(result, roseGold);
        GearType type = GearType.of(result.getType());
        if (type == null || !tier.smithable()) {
            return;
        }
        int level = blacksmithLevel(player);
        if (tier.masterOnly() && level < masterMinLevel()) {
            event.getInventory().setResult(null);
        } else if (type == GearType.ARMOR && tier.debuffEligible() && level == 0
                && isVanilla(event.getRecipe()) && harderRecipes.covers(result.getType())) {
            event.getInventory().setResult(null); // force the harder (block) recipe
        }
    }

    // --- Craft outcome ------------------------------------------------------------------------

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!enabled() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack result = event.getCurrentItem();
        GearTier tier = GearTier.of(result, roseGold);
        GearType type = GearType.of(result == null ? null : result.getType());
        if (result == null || type == null || !tier.smithable()) {
            return;
        }
        int level = blacksmithLevel(player);
        if (tier.masterOnly() && level < masterMinLevel()) {
            event.setCancelled(true);
            lang.send(player, "smithing.master-locked", Map.of(
                    "tier", tier.name().toLowerCase(), "level", String.valueOf(masterMinLevel())));
            return;
        }
        if (event.isShiftClick()) {
            event.setCancelled(true);
            lang.send(player, "smithing.craft-one-at-a-time");
            return;
        }

        String nonce = stampNonce(result);
        event.setCurrentItem(result);
        if (level >= 1) {
            plugin.getServer().getScheduler().runTask(plugin, () -> startForge(player, nonce, level));
        } else {
            plugin.getServer().getScheduler().runTask(plugin, () -> applyDebuff(player, nonce, type));
        }
    }

    private void startForge(Player player, String nonce, int level) {
        int maxHits = config.raw().getInt("minigames.rhythm.beats", 8);
        minigames.start(player, "rhythm", result -> updateNonceItem(player, nonce, item -> {
            clearNonce(item);
            Optional<ModifierRoller.Roll> roll =
                    roller.rollPositive(level, result.score(), maxHits, GearType.of(item.getType()));
            if (roll.isPresent()) {
                modifiers.apply(item, roll.get().modifier(), roll.get().tier());
                lang.send(player, "smithing.modifier-applied", Map.of("name", modName(roll.get())));
            } else {
                lang.send(player, "smithing.no-modifier");
            }
        }));
    }

    private void applyDebuff(Player player, String nonce, GearType type) {
        updateNonceItem(player, nonce, item -> {
            clearNonce(item);
            Optional<ModifierRoller.Roll> roll = roller.rollDebuff(type);
            if (roll.isPresent()) {
                modifiers.apply(item, roll.get().modifier(), roll.get().tier());
                lang.send(player, "smithing.debuff-applied", Map.of("name", modName(roll.get())));
            }
        });
    }

    // --- Reroll / fix (shift-right-click held gear) -------------------------------------------

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!enabled() || event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        GearType type = GearType.of(item.getType());
        if (type == null) {
            return;
        }
        int level = blacksmithLevel(player);
        if (level == 0) {
            return;
        }
        event.setCancelled(true);

        if (modifiers.isDebuff(item)) {
            if (level < config.raw().getInt("smithing.fix.min-level", 1)) {
                lang.send(player, "smithing.fix-need-level", Map.of(
                        "level", String.valueOf(config.raw().getInt("smithing.fix.min-level", 1))));
                return;
            }
            modifiers.clearModifier(item);
            player.getInventory().setItemInMainHand(item);
            lang.send(player, "smithing.fix-done");
            return;
        }

        int rerollMin = config.raw().getInt("smithing.reroll.min-level", 2);
        int maxRerolls = config.raw().getInt("smithing.reroll.max-rerolls", 5);
        if (level < rerollMin) {
            lang.send(player, "smithing.reroll-need-level", Map.of("level", String.valueOf(rerollMin)));
            return;
        }
        if (modifiers.rerollCount(item) >= maxRerolls) {
            lang.send(player, "smithing.reroll-limit", Map.of("max", String.valueOf(maxRerolls)));
            return;
        }
        int maxHits = config.raw().getInt("minigames.rhythm.beats", 8);
        minigames.start(player, "rhythm", result -> {
            ModifierRoller.Roll roll = roller.rollPositive(level, result.score(), maxHits, type)
                    .orElseGet(() -> guaranteed(type));
            if (roll == null) {
                lang.send(player, "smithing.no-modifier");
                return;
            }
            modifiers.apply(item, roll.modifier(), roll.tier());
            modifiers.incrementReroll(item);
            player.getInventory().setItemInMainHand(item);
            lang.send(player, "smithing.reroll-done", Map.of("name", modName(roll)));
        });
    }

    /** Falls back to a tier-1 positive so a reroll always changes the item. */
    private ModifierRoller.Roll guaranteed(GearType type) {
        var pool = registry.positivesFor(type);
        return pool.isEmpty() ? null
                : new ModifierRoller.Roll(pool.get((int) (Math.random() * pool.size())), 1);
    }

    // --- Helpers ------------------------------------------------------------------------------

    private boolean enabled() {
        return config.raw().getBoolean("smithing.enabled", true);
    }

    private int masterMinLevel() {
        return config.raw().getInt("smithing.master-tiers.min-level", 3);
    }

    private int blacksmithLevel(Player player) {
        PlayerProfile profile = classManager.profile(player);
        return profile.hasClass() && BlacksmithClass.ID.equalsIgnoreCase(profile.classId())
                ? profile.level() : 0;
    }

    private boolean isVanilla(Recipe recipe) {
        return recipe instanceof Keyed keyed
                && keyed.getKey().getNamespace().equals(NamespacedKey.MINECRAFT);
    }

    private String modName(ModifierRoller.Roll roll) {
        return roll.modifier().tier(roll.tier()).name();
    }

    private String stampNonce(ItemStack item) {
        String nonce = UUID.randomUUID().toString();
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(nonceKey, PersistentDataType.STRING, nonce);
        item.setItemMeta(meta);
        return nonce;
    }

    private void clearNonce(ItemStack item) {
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(nonceKey);
        item.setItemMeta(meta);
    }

    /** Locates the freshly-crafted item by its nonce, runs {@code action}, and writes it back. */
    private void updateNonceItem(Player player, String nonce, java.util.function.Consumer<ItemStack> action) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (matchesNonce(item, nonce)) {
                action.accept(item);
                inv.setItem(i, item);
                return;
            }
        }
        ItemStack cursor = player.getItemOnCursor();
        if (matchesNonce(cursor, nonce)) {
            action.accept(cursor);
            player.setItemOnCursor(cursor);
        }
    }

    private boolean matchesNonce(ItemStack item, String nonce) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return nonce.equals(item.getItemMeta().getPersistentDataContainer()
                .get(nonceKey, PersistentDataType.STRING));
    }
}
