package com.ucucraft.skills.hunter;

import com.ucucraft.skills.SkillsPlugin;
import com.ucucraft.skills.classes.ClassManager;
import com.ucucraft.skills.classes.impl.HunterClass;
import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.data.PlayerProfile;
import com.ucucraft.skills.lang.LangManager;
import com.ucucraft.skills.smithing.GearType;
import com.ucucraft.skills.smithing.ModifierRegistry;
import com.ucucraft.skills.smithing.ModifierRoller;
import com.ucucraft.skills.smithing.ModifierService;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The hunter's crafting arm — the bow/crossbow analogue of {@code SmithingManager}, but with no
 * minigame: a hunter's bow/crossbow crafts roll a positive modifier directly (odds scale with level),
 * and regular players roll a debuff. From level {@code hunter.reroll.min-level} the hunter can reforge
 * a bow at an archer table (right-click) for a material cost, up to {@code hunter.reroll.max-rerolls}
 * times per item.
 */
public final class HunterManager implements Listener {

    private final SkillsPlugin plugin;
    private final LangManager lang;
    private final ConfigManager config;
    private final ClassManager classManager;
    private final ModifierService modifiers;
    private final ModifierRoller roller;
    private final ModifierRegistry registry;
    private final NamespacedKey nonceKey;

    public HunterManager(SkillsPlugin plugin, LangManager lang, ConfigManager config,
                         ClassManager classManager, ModifierService modifiers,
                         ModifierRoller roller, ModifierRegistry registry) {
        this.plugin = plugin;
        this.lang = lang;
        this.config = config;
        this.classManager = classManager;
        this.modifiers = modifiers;
        this.roller = roller;
        this.registry = registry;
        this.nonceKey = new NamespacedKey(plugin, "hunter_nonce");
    }

    // --- Craft outcome ------------------------------------------------------------------------

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!enabled() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack result = event.getCurrentItem();
        if (result == null || GearType.of(result.getType()) != GearType.BOW) {
            return;
        }
        if (event.isShiftClick()) {
            event.setCancelled(true);
            lang.send(player, "hunter.craft-one-at-a-time");
            return;
        }
        int level = hunterLevel(player);
        String nonce = stampNonce(result);
        event.setCurrentItem(result);
        // Roll next tick, once the crafted item has settled into the player's inventory.
        plugin.getServer().getScheduler().runTask(plugin, () -> updateNonceItem(player, nonce, item -> {
            clearNonce(item);
            if (level >= 1) {
                Optional<ModifierRoller.Roll> roll = roller.rollPositive("hunter", level, GearType.BOW);
                if (roll.isPresent()) {
                    modifiers.apply(item, roll.get().modifier(), roll.get().tier());
                    lang.send(player, "hunter.modifier-applied", Map.of("name", modName(roll.get())));
                } else {
                    lang.send(player, "hunter.no-modifier");
                }
            } else {
                roller.rollDebuff("hunter", GearType.BOW).ifPresent(roll -> {
                    modifiers.apply(item, roll.modifier(), roll.tier());
                    lang.send(player, "hunter.debuff-applied", Map.of("name", modName(roll)));
                });
            }
        }));
    }

    // --- Reroll at the archer table -----------------------------------------------------------

    @EventHandler
    public void onArcherTable(PlayerInteractEvent event) {
        if (!enabled() || event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != tableMaterial()) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (GearType.of(item.getType()) != GearType.BOW) {
            return;
        }
        event.setCancelled(true); // never open the vanilla fletching UI here

        int level = hunterLevel(player);
        int min = config.raw().getInt("hunter.reroll.min-level", 1);
        if (level < min) {
            lang.send(player, "hunter.reroll-need-level", Map.of("level", String.valueOf(min)));
            return;
        }
        int max = config.raw().getInt("hunter.reroll.max-rerolls", 5);
        if (modifiers.rerollCount(item) >= max) {
            lang.send(player, "hunter.reroll-limit", Map.of("max", String.valueOf(max)));
            return;
        }
        if (!hasCost(player)) {
            lang.send(player, "hunter.reroll-need-materials", Map.of("cost", costText()));
            return;
        }
        consumeCost(player);

        // Guaranteed positive so a paid reforge always changes the bow (and clears any debuff).
        ModifierRoller.Roll roll = roller.rollPositive("hunter", level, GearType.BOW).orElseGet(this::guaranteed);
        if (roll == null) {
            lang.send(player, "hunter.no-modifier");
            return;
        }
        modifiers.apply(item, roll.modifier(), roll.tier());
        modifiers.incrementReroll(item);
        player.getInventory().setItemInMainHand(item);
        lang.send(player, "hunter.reroll-done", Map.of(
                "name", modName(roll), "left", String.valueOf(max - modifiers.rerollCount(item))));
    }

    /** Tier-1 positive fallback so a paid reroll always changes the bow. */
    private ModifierRoller.Roll guaranteed() {
        List<com.ucucraft.skills.smithing.Modifier> pool = registry.positivesFor(GearType.BOW);
        return pool.isEmpty() ? null
                : new ModifierRoller.Roll(pool.get((int) (Math.random() * pool.size())), 1);
    }

    // --- Material cost --------------------------------------------------------------------------

    private List<String> costEntries() {
        return config.raw().getStringList("hunter.reroll.cost");
    }

    private boolean hasCost(Player player) {
        PlayerInventory inv = player.getInventory();
        for (String entry : costEntries()) {
            ItemStack stack = parseCost(entry);
            if (stack != null && !inv.containsAtLeast(stack, stack.getAmount())) {
                return false;
            }
        }
        return true;
    }

    private void consumeCost(Player player) {
        for (String entry : costEntries()) {
            ItemStack stack = parseCost(entry);
            if (stack != null) {
                player.getInventory().removeItem(stack);
            }
        }
    }

    private String costText() {
        return String.join(", ", costEntries());
    }

    private ItemStack parseCost(String entry) {
        String[] parts = entry.split(":");
        Material material = Material.matchMaterial(parts[0].trim());
        if (material == null) {
            return null;
        }
        int amount = parts.length > 1 ? parseAmount(parts[1]) : 1;
        return new ItemStack(material, amount);
    }

    private int parseAmount(String raw) {
        try {
            return Math.max(1, Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    // --- Helpers --------------------------------------------------------------------------------

    private boolean enabled() {
        return config.raw().getBoolean("hunter.enabled", true);
    }

    private Material tableMaterial() {
        Material material = Material.matchMaterial(
                config.raw().getString("hunter.table-material", "FLETCHING_TABLE").toUpperCase(Locale.ROOT));
        return material == null ? Material.FLETCHING_TABLE : material;
    }

    private int hunterLevel(Player player) {
        PlayerProfile profile = classManager.profile(player);
        return profile.hasClass() && HunterClass.ID.equalsIgnoreCase(profile.classId())
                ? profile.level() : 0;
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

    private void updateNonceItem(Player player, String nonce, Consumer<ItemStack> action) {
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
