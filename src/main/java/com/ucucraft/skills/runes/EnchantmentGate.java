package com.ucucraft.skills.runes;

import com.ucucraft.skills.config.ConfigManager;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The single clamp behind the vanilla enchant cap, sealed on every path vanilla can grant or raise
 * an enchantment: table, anvil, villager trades, chest loot, fishing, grindstone. A rune's own
 * enchantment is exempt up to exactly the level carved into the item's PDC (see {@link RuneService})
 * — nothing else can push it higher, so an anvil (the one that leaks in practice) can never quietly
 * combine two capped books past the cap.
 * <p>
 * {@code enchanting.banned} ({@link #isBanned}) goes further: those enchantments get no rune
 * exemption at all (cap 0, unconditionally) and {@link RunesmithManager} refuses to carve or
 * transfer one, so they're unobtainable by any means rather than merely capped.
 */
public final class EnchantmentGate implements Listener {

    private final ConfigManager config;
    private final RuneRegistry registry;
    private final RuneService runes;

    public EnchantmentGate(ConfigManager config, RuneRegistry registry, RuneService runes) {
        this.config = config;
        this.registry = registry;
        this.runes = runes;
    }

    @EventHandler
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (!enabled() || bypassed(event.getEnchanter())) {
            return;
        }
        for (EnchantmentOffer offer : event.getOffers()) {
            if (offer == null) {
                continue;
            }
            int max = cap(offer.getEnchantment());
            if (max > 0 && offer.getEnchantmentLevel() > max) {
                offer.setEnchantmentLevel(max);
            }
        }
    }

    @EventHandler
    public void onEnchantItem(EnchantItemEvent event) {
        if (!enabled() || bypassed(event.getEnchanter())) {
            return;
        }
        Map<Enchantment, Integer> toAdd = event.getEnchantsToAdd();
        toAdd.entrySet().removeIf(e -> cap(e.getKey()) <= 0);
        for (Map.Entry<Enchantment, Integer> e : toAdd.entrySet()) {
            int max = cap(e.getKey());
            if (e.getValue() > max) {
                e.setValue(max);
            }
        }
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (!enabled()) {
            return;
        }
        ItemStack result = event.getResult();
        if (result == null || !result.hasItemMeta()) {
            return;
        }
        for (var viewer : event.getInventory().getViewers()) {
            if (viewer instanceof Player player && bypassed(player)) {
                return;
            }
        }
        ItemMeta meta = result.getItemMeta();
        boolean changed = false;
        for (Map.Entry<Enchantment, Integer> e : new LinkedHashMap<>(meta.getEnchants()).entrySet()) {
            Integer runeLevel = runeGrantedLevel(result, e.getKey());
            int allowed = runeLevel != null ? runeLevel : cap(e.getKey());
            if (allowed <= 0) {
                meta.removeEnchant(e.getKey());
                changed = true;
            } else if (e.getValue() > allowed) {
                meta.addEnchant(e.getKey(), allowed, true);
                changed = true;
            }
        }
        if (meta instanceof EnchantmentStorageMeta stored) {
            for (Map.Entry<Enchantment, Integer> e : new LinkedHashMap<>(stored.getStoredEnchants()).entrySet()) {
                Integer runeLevel = runeGrantedLevel(result, e.getKey());
                int allowed = runeLevel != null ? runeLevel : cap(e.getKey());
                if (allowed <= 0) {
                    stored.removeStoredEnchant(e.getKey());
                    changed = true;
                } else if (e.getValue() > allowed) {
                    stored.addStoredEnchant(e.getKey(), allowed, true);
                    changed = true;
                }
            }
        }
        if (changed) {
            result.setItemMeta(meta);
            event.setResult(result);
        }
    }

    @EventHandler
    public void onVillagerTrade(VillagerAcquireTradeEvent event) {
        if (!enabled()) {
            return;
        }
        MerchantRecipe recipe = event.getRecipe();
        if (clampFresh(recipe.getResult())) {
            event.setRecipe(recipe);
        }
    }

    @EventHandler
    public void onLootGenerate(LootGenerateEvent event) {
        if (!enabled()) {
            return;
        }
        for (ItemStack stack : event.getLoot()) {
            clampFresh(stack);
        }
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        if (!enabled() || event.getState() != PlayerFishEvent.State.CAUGHT_FISH
                || bypassed(event.getPlayer()) || !(event.getCaught() instanceof Item item)) {
            return;
        }
        ItemStack stack = item.getItemStack();
        if (clampFresh(stack)) {
            item.setItemStack(stack);
        }
    }

    @EventHandler
    public void onPrepareGrindstone(PrepareGrindstoneEvent event) {
        if (!enabled()) {
            return;
        }
        GrindstoneInventory inv = event.getInventory();
        if (runes.isCursed(inv.getItem(0)) || runes.isCursed(inv.getItem(1))) {
            // Vanilla grindstone can never strip a rune-curse; only the Strip Curse ability can.
            event.setResult(null);
            return;
        }
        ItemStack result = event.getResult();
        if (result != null && clampFresh(result)) {
            event.setResult(result);
        }
    }

    // --- Helpers --------------------------------------------------------------------------------

    private boolean enabled() {
        return config.raw().getBoolean("enchanting.enabled", true);
    }

    private boolean bypassed(Player player) {
        String permission = config.raw().getString("enchanting.bypass-permission", "skills.enchanting.bypass");
        return player != null && player.hasPermission(permission);
    }

    /** The enchant id -> hard cap, from {@code enchanting.overrides} else {@code default-max-level}. */
    private int cap(Enchantment enchantment) {
        if (isBanned(enchantment)) {
            return 0;
        }
        String key = enchantment.getKey().getKey();
        if (config.raw().isSet("enchanting.overrides." + key)) {
            return config.raw().getInt("enchanting.overrides." + key);
        }
        return config.raw().getInt("enchanting.default-max-level", 2);
    }

    /**
     * Whether this enchantment is hard-banned ({@code enchanting.banned}): unobtainable by any
     * means, vanilla or rune. Unlike an {@code overrides} entry of 0 — which a rune can still
     * exceed — a banned enchant is refused by {@link com.ucucraft.skills.runes.RunesmithManager}
     * at carve time too, so no path ever grants it.
     */
    public boolean isBanned(Enchantment enchantment) {
        if (enchantment == null) {
            return false;
        }
        String key = enchantment.getKey().getKey();
        return config.raw().getStringList("enchanting.banned").stream().anyMatch(key::equalsIgnoreCase);
    }

    /** The level the item's own rune grants for this exact enchantment, or null if it grants none. */
    private Integer runeGrantedLevel(ItemStack item, Enchantment enchantment) {
        if (item == null || !runes.hasRune(item) || isBanned(enchantment)) {
            return null;
        }
        Rune rune = registry.byEnchantment(enchantment);
        String pattern = runes.patternId(item);
        if (rune == null || pattern == null || !rune.id().equalsIgnoreCase(pattern)) {
            return null;
        }
        return runes.level(item);
    }

    /** Clamps every enchant on a freshly-generated stack (villager/loot/fishing) to its cap. */
    private boolean clampFresh(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = stack.getItemMeta();
        boolean changed = false;
        for (Map.Entry<Enchantment, Integer> e : new LinkedHashMap<>(meta.getEnchants()).entrySet()) {
            int max = cap(e.getKey());
            if (max <= 0) {
                meta.removeEnchant(e.getKey());
                changed = true;
            } else if (e.getValue() > max) {
                meta.addEnchant(e.getKey(), max, true);
                changed = true;
            }
        }
        if (meta instanceof EnchantmentStorageMeta stored) {
            for (Map.Entry<Enchantment, Integer> e : new LinkedHashMap<>(stored.getStoredEnchants()).entrySet()) {
                int max = cap(e.getKey());
                if (max <= 0) {
                    stored.removeStoredEnchant(e.getKey());
                    changed = true;
                } else if (e.getValue() > max) {
                    stored.addStoredEnchant(e.getKey(), max, true);
                    changed = true;
                }
            }
        }
        if (changed) {
            stack.setItemMeta(meta);
        }
        return changed;
    }
}
