package com.ucucraft.skills.runes;

import com.ucucraft.skills.SkillsPlugin;
import com.ucucraft.skills.classes.ClassManager;
import com.ucucraft.skills.classes.impl.RunesmithClass;
import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.data.PlayerProfile;
import com.ucucraft.skills.lang.LangManager;
import com.ucucraft.skills.minigame.MinigameManager;
import com.ucucraft.skills.smithing.GearType;
import com.ucucraft.skills.smithing.ModifierService;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.persistence.PersistentDataType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * The runesmith's forge: carving/upgrading a rune at a vanilla smithing table, plus the sneak-click
 * abilities (duplicate a template, transfer a rune, strip a curse) and the plain-right-click "read
 * the rune" info. See docs/runesmith.md.
 */
public final class RunesmithManager implements Listener {

    private static final String TEMPLATE_SUFFIX = "_ARMOR_TRIM_SMITHING_TEMPLATE";

    private static final Map<Material, TrimMaterial> TRIM_INGREDIENTS = Map.ofEntries(
            Map.entry(Material.COPPER_INGOT, TrimMaterial.COPPER),
            Map.entry(Material.IRON_INGOT, TrimMaterial.IRON),
            Map.entry(Material.GOLD_INGOT, TrimMaterial.GOLD),
            Map.entry(Material.LAPIS_LAZULI, TrimMaterial.LAPIS),
            Map.entry(Material.REDSTONE, TrimMaterial.REDSTONE),
            Map.entry(Material.AMETHYST_SHARD, TrimMaterial.AMETHYST),
            Map.entry(Material.EMERALD, TrimMaterial.EMERALD),
            Map.entry(Material.QUARTZ, TrimMaterial.QUARTZ),
            Map.entry(Material.DIAMOND, TrimMaterial.DIAMOND),
            Map.entry(Material.NETHERITE_INGOT, TrimMaterial.NETHERITE));

    private final SkillsPlugin plugin;
    private final LangManager lang;
    private final ConfigManager config;
    private final ClassManager classManager;
    private final MinigameManager minigames;
    private final RuneRegistry registry;
    private final RuneRoller roller;
    private final RuneService runes;
    private final ModifierService modifiers;
    private final EnchantmentGate gate;
    private final NamespacedKey nonceKey;
    private final NamespacedKey copiesRemainingKey;
    private final NamespacedKey copyFlagKey;

    public RunesmithManager(SkillsPlugin plugin, LangManager lang, ConfigManager config,
                            ClassManager classManager, MinigameManager minigames, RuneRegistry registry,
                            RuneRoller roller, RuneService runes, ModifierService modifiers, EnchantmentGate gate) {
        this.plugin = plugin;
        this.lang = lang;
        this.config = config;
        this.classManager = classManager;
        this.minigames = minigames;
        this.registry = registry;
        this.roller = roller;
        this.runes = runes;
        this.modifiers = modifiers;
        this.gate = gate;
        this.nonceKey = new NamespacedKey(plugin, "rune_carve_nonce");
        this.copiesRemainingKey = new NamespacedKey(plugin, "rune_template_copies_remaining");
        this.copyFlagKey = new NamespacedKey(plugin, "rune_template_is_copy");
    }

    // --- Smithing table: carve / upgrade preview -----------------------------------------------

    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        if (!enabled()) {
            return;
        }
        SmithingInventory inv = event.getInventory();
        Rune rune = patternRune(inv.getInputTemplate());
        TrimMaterial material = trimMaterialOf(inv.getInputMineral());
        ItemStack base = inv.getInputEquipment();
        if (rune == null || material == null || base == null || GearType.of(base.getType()) != GearType.ARMOR) {
            return;
        }
        // Vanilla refuses to preview a trim over already-trimmed armor (the upgrade case); show one
        // anyway so the slot isn't misleadingly empty. Cosmetic only — onSmithingClick does the work.
        ItemStack preview = base.clone();
        ItemMeta meta = preview.getItemMeta();
        if (meta instanceof ArmorMeta armorMeta) {
            armorMeta.setTrim(new ArmorTrim(material, rune.pattern()));
            preview.setItemMeta(armorMeta);
            event.setResult(preview);
        }
    }

    @EventHandler
    public void onSmithingClick(InventoryClickEvent event) {
        if (!enabled() || !(event.getInventory() instanceof SmithingInventory smithing)
                || event.getSlotType() != InventoryType.SlotType.RESULT
                || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack templateItem = smithing.getInputTemplate();
        ItemStack materialItem = smithing.getInputMineral();
        ItemStack base = smithing.getInputEquipment();
        Rune rune = patternRune(templateItem);
        if (rune == null || materialItem == null || base == null) {
            return; // not one of our combos; let vanilla behave normally (or do nothing)
        }
        // Ours from here — always cancel (this also covers "shift-click blocked": every click on
        // the result slot is intercepted uniformly, so a carve can never happen twice per click).
        event.setCancelled(true);

        int level = runesmithLevel(player);
        if (level < 1) {
            lang.send(player, "runes.carve-not-runesmith");
            return;
        }
        TrimMaterial trimMaterial = trimMaterialOf(materialItem);
        int tier = trimMaterial == null ? 0 : roller.materialTier(trimMaterial);
        if (tier < 1) {
            lang.send(player, "runes.carve-no-rune");
            return;
        }
        if (tier > roller.maxTierFor(level)) {
            lang.send(player, "runes.carve-tier-locked", Map.of("level", String.valueOf(level)));
            return;
        }
        GearType category = GearType.of(base.getType());
        boolean armor = category == GearType.ARMOR;
        if (!armor) {
            // Vanilla only lets armor into a smithing table's trim slot in the first place, so this
            // is a defensive check, not a reachable path — non-armor carving goes through the
            // sneak-click dispatcher below instead (see onInteract / carveNonArmor).
            lang.send(player, "runes.carve-no-rune");
            return;
        }

        Runnable consumeInputs = () -> {
            smithing.setInputTemplate(decremented(templateItem));
            smithing.setInputMineral(decremented(materialItem));
            smithing.setInputEquipment(null);
        };
        beginCarve(player, base, rune, trimMaterial, tier, level, true, consumeInputs);
    }

    /**
     * Shared validation (tier, min-class-level, one-per-item/upgrade) and minigame kickoff for both
     * the smithing-table carve and the sneak-click non-armor carve. {@code consumeInputs} removes
     * the template and material — the two paths do this differently (table slots vs. off-hand +
     * inventory scan) — and only runs once every check has passed.
     */
    private void beginCarve(Player player, ItemStack base, Rune rune, TrimMaterial trimMaterial, int tier,
                            int level, boolean armor, Runnable consumeInputs) {
        if (gate.isBanned(rune.enchantment())) {
            lang.send(player, "runes.carve-banned", Map.of("rune", patternName(rune)));
            return;
        }
        if (rune.minClassLevel() > level) {
            lang.send(player, "runes.carve-min-level", Map.of(
                    "rune", patternName(rune), "level", String.valueOf(rune.minClassLevel())));
            return;
        }
        boolean upgrade = false;
        if (runes.hasRune(base)) {
            String existingId = runes.patternId(base);
            if (!rune.id().equalsIgnoreCase(existingId)) {
                lang.send(player, "runes.carve-one-per-item", Map.of("rune", patternName(existingId)));
                return;
            }
            int upgradeMin = config.raw().getInt("runes.upgrade.min-level", 3);
            if (level < upgradeMin) {
                lang.send(player, "runes.carve-upgrade-locked", Map.of("level", String.valueOf(upgradeMin)));
                return;
            }
            int maxUpgrades = config.raw().getInt("runes.max-upgrades", 3);
            if (runes.upgrades(base) >= maxUpgrades) {
                lang.send(player, "runes.carve-upgrade-limit", Map.of("max", String.valueOf(maxUpgrades)));
                return;
            }
            int existingTier = roller.materialTier(runes.material(base));
            if (tier <= existingTier) {
                lang.send(player, "runes.carve-upgrade-no-gain");
                return;
            }
            upgrade = true;
        }

        consumeInputs.run();

        ItemStack carried = base.clone();
        carried.setAmount(1);
        String nonce = stampNonce(carried);
        giveBack(player, carried);

        boolean upgradeFinal = upgrade;
        int levelFinal = level;
        int tierFinal = tier;

        String gameId = config.raw().getString("runes.minigame.id", "sequence");
        var difficulty = config.raw().getConfigurationSection("runes.minigame.difficulty");
        int maxScore = maxScoreFor(difficulty);
        Consumer<com.ucucraft.skills.minigame.MinigameResult> onComplete = result ->
                updateNonceItem(player, nonce, item -> {
                    clearNonce(item);
                    finishCarve(player, item, rune, trimMaterial, tierFinal, levelFinal, armor, upgradeFinal, result, maxScore);
                });
        if (!minigames.start(player, gameId, onComplete, difficulty)) {
            plugin.getLogger().warning("Runesmith carve could not start minigame '" + gameId + "'.");
        }
    }

    private void finishCarve(Player player, ItemStack item, Rune rune, TrimMaterial material, int tier,
                             int classLevel, boolean armor, boolean upgrade,
                             com.ucucraft.skills.minigame.MinigameResult result, int maxScore) {
        RuneRoller.Outcome outcome = roller.roll(classLevel, result, maxScore);
        switch (outcome) {
            case WIN -> {
                int grantedLevel = roller.tierLevel(tier);
                runes.apply(item, rune, material, grantedLevel, armor);
                if (upgrade) {
                    runes.incrementUpgrade(item);
                }
                lang.send(player, "runes.carve-win", Map.of(
                        "rune", patternName(rune), "roman", RuneService.roman(grantedLevel)));
            }
            case PARTIAL -> {
                int grantedLevel = Math.max(1, roller.tierLevel(tier) - 1);
                runes.apply(item, rune, material, grantedLevel, armor);
                if (upgrade) {
                    runes.incrementUpgrade(item);
                }
                lang.send(player, "runes.carve-partial", Map.of(
                        "rune", patternName(rune), "roman", RuneService.roman(grantedLevel)));
            }
            case FAIL -> {
                runes.applyCurse(item, randomCurse());
                lang.send(player, "runes.carve-fail");
            }
        }
    }

    // --- Sneak-click abilities: duplicate / strip curse / transfer, plus plain-click read --------

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!enabled() || event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack main = player.getInventory().getItemInMainHand();
        int level = runesmithLevel(player);

        if (!player.isSneaking()) {
            if (level >= 1 && runes.hasRune(main)) {
                readRune(player, main);
            }
            return; // never intercepts a plain right-click
        }
        if (level == 0) {
            return;
        }

        String templatePattern = templatePatternId(main);
        if (templatePattern != null && registry.get(templatePattern) != null) {
            event.setCancelled(true);
            duplicate(player, main, level);
            return;
        }
        if (runes.hasRune(main) && runes.isCursed(main)) {
            event.setCancelled(true);
            stripCurse(player, main, level);
            return;
        }
        // Weapon/tool/bow in main hand + a registered template in the off hand: the smithing table
        // won't accept a non-armor item in its trim slot, so this is the only way in for them —
        // including an upgrade, so this must be checked before the plain "transfer" branch below
        // (which would otherwise unconditionally claim any runed main-hand item).
        GearType mainCategory = GearType.of(main.getType());
        if (mainCategory != null && mainCategory != GearType.ARMOR) {
            ItemStack off = player.getInventory().getItemInOffHand();
            String offPattern = templatePatternId(off);
            Rune rune = offPattern == null ? null : registry.get(offPattern);
            if (rune != null) {
                event.setCancelled(true);
                int nonArmorMin = config.raw().getInt("runes.non-armor.min-level", 2);
                if (level < nonArmorMin) {
                    lang.send(player, "runes.carve-non-armor-locked", Map.of("level", String.valueOf(nonArmorMin)));
                } else {
                    carveNonArmor(player, main, off, rune, level);
                }
                return;
            }
        }
        if (runes.hasRune(main)) {
            event.setCancelled(true);
            transfer(player, main, level);
        }
    }

    private void carveNonArmor(Player player, ItemStack base, ItemStack templateItem, Rune rune, int level) {
        TrimMaterial trimMaterial = bestOwnedMaterial(player);
        if (trimMaterial == null) {
            lang.send(player, "runes.carve-need-material");
            return;
        }
        int tier = roller.materialTier(trimMaterial);
        if (tier > roller.maxTierFor(level)) {
            lang.send(player, "runes.carve-tier-locked", Map.of("level", String.valueOf(level)));
            return;
        }
        TrimMaterial materialFinal = trimMaterial;
        Runnable consumeInputs = () -> {
            player.getInventory().setItemInOffHand(decremented(templateItem));
            removeOneMaterial(player, materialFinal);
            player.getInventory().setItemInMainHand(null);
        };
        beginCarve(player, base, rune, trimMaterial, tier, level, false, consumeInputs);
    }

    private void readRune(Player player, ItemStack item) {
        Rune rune = registry.get(runes.patternId(item));
        String modifier = modifiers.hasModifier(item)
                ? modifiers.modifierId(item) + " " + RuneService.roman(modifiers.modifierTier(item))
                : "-";
        lang.send(player, "runes.read-header");
        lang.send(player, "runes.read-line", Map.of(
                "rune", rune == null ? runes.patternId(item) : patternName(rune),
                "roman", RuneService.roman(runes.level(item)),
                "modifier", modifier,
                "upgrades", String.valueOf(runes.upgrades(item)),
                "max", String.valueOf(config.raw().getInt("runes.max-upgrades", 3)),
                "rerolls", String.valueOf(modifiers.rerollCount(item))));
        if (runes.isCursed(item)) {
            lang.send(player, "runes.read-cursed");
        }
    }

    /**
     * Duplicating a template spends its budget, not just a flat cost: the first duplication of a
     * given template stamps it with a copy budget picked from the trim material's tier
     * ({@code runes.duplication.max-copies}), every duplication after that spends the same budget
     * down by one, and the resulting copy is flagged so it can never be duplicated itself — no
     * duplication chains, and a template is refused outright once its own budget hits 0.
     */
    private void duplicate(Player player, ItemStack template, int level) {
        int minLevel = config.raw().getInt("runes.duplication.min-level", 2);
        if (level < minLevel) {
            lang.send(player, "runes.duplicate-need-level", Map.of("level", String.valueOf(minLevel)));
            return;
        }
        if (isCopy(template)) {
            lang.send(player, "runes.duplicate-is-copy");
            return;
        }
        if (template.getAmount() != 1) {
            lang.send(player, "runes.duplicate-need-single");
            return;
        }
        Integer remaining = copiesRemaining(template);
        if (remaining != null && remaining <= 0) {
            lang.send(player, "runes.duplicate-exhausted");
            return;
        }
        TrimMaterial material = bestOwnedMaterial(player);
        if (material == null) {
            lang.send(player, "runes.duplicate-need-material");
            return;
        }
        List<String> cost = config.raw().getStringList("runes.duplication.cost");
        if (!hasCost(player, cost)) {
            lang.send(player, "runes.duplicate-need-materials", Map.of("cost", String.join(", ", cost)));
            return;
        }
        if (remaining == null) {
            remaining = roller.maxCopies(roller.materialTier(material));
        }
        payCost(player, cost);
        removeOneMaterial(player, material);
        remaining -= 1;

        ItemMeta meta = template.getItemMeta();
        meta.getPersistentDataContainer().set(copiesRemainingKey, PersistentDataType.INTEGER, remaining);
        template.setItemMeta(meta);
        player.getInventory().setItemInMainHand(template);

        ItemStack extra = template.clone();
        extra.setAmount(1);
        ItemMeta extraMeta = extra.getItemMeta();
        extraMeta.getPersistentDataContainer().remove(copiesRemainingKey);
        extraMeta.getPersistentDataContainer().set(copyFlagKey, PersistentDataType.BYTE, (byte) 1);
        extra.setItemMeta(extraMeta);
        giveBack(player, extra);

        lang.send(player, "runes.duplicate-done", Map.of("remaining", String.valueOf(remaining)));
    }

    private Integer copiesRemaining(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(copiesRemainingKey, PersistentDataType.INTEGER)
                ? pdc.get(copiesRemainingKey, PersistentDataType.INTEGER) : null;
    }

    private boolean isCopy(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(copyFlagKey, PersistentDataType.BYTE);
    }

    private void stripCurse(Player player, ItemStack item, int level) {
        int minLevel = config.raw().getInt("runes.strip-curse.min-level", 3);
        if (level < minLevel) {
            lang.send(player, "runes.strip-need-level", Map.of("level", String.valueOf(minLevel)));
            return;
        }
        runes.stripCurse(item);
        player.getInventory().setItemInMainHand(item);
        lang.send(player, "runes.strip-done");
    }

    private void transfer(Player player, ItemStack source, int level) {
        int minLevel = config.raw().getInt("runes.transfer.min-level", 3);
        if (level < minLevel) {
            lang.send(player, "runes.transfer-need-level", Map.of("level", String.valueOf(minLevel)));
            return;
        }
        ItemStack target = player.getInventory().getItemInOffHand();
        if (target.getType() == Material.AIR) {
            lang.send(player, "runes.transfer-need-target");
            return;
        }
        if (runes.hasRune(target)) {
            lang.send(player, "runes.transfer-target-invalid");
            return;
        }
        Rune rune = registry.get(runes.patternId(source));
        GearType targetCategory = GearType.of(target.getType());
        boolean targetArmor = targetCategory == GearType.ARMOR;
        if (rune == null || (!targetArmor && targetCategory == null)) {
            lang.send(player, "runes.transfer-target-invalid");
            return;
        }
        if (gate.isBanned(rune.enchantment())) {
            lang.send(player, "runes.carve-banned", Map.of("rune", patternName(rune)));
            return;
        }
        if (!targetArmor && level < config.raw().getInt("runes.non-armor.min-level", 2)) {
            lang.send(player, "runes.transfer-target-invalid");
            return;
        }
        TrimMaterial material = runes.material(source);
        int grantedLevel = runes.level(source);
        runes.clearRune(source, rune.enchantment());
        player.getInventory().setItemInMainHand(source);
        runes.apply(target, rune, material, grantedLevel, targetArmor);
        player.getInventory().setItemInOffHand(target);
        lang.send(player, "runes.transfer-done");
    }

    // --- Helpers --------------------------------------------------------------------------------

    private boolean enabled() {
        return config.raw().getBoolean("runes.enabled", true);
    }

    private int runesmithLevel(Player player) {
        PlayerProfile profile = classManager.profile(player);
        return profile.hasClass() && RunesmithClass.ID.equalsIgnoreCase(profile.classId())
                ? profile.level() : 0;
    }

    private String templatePatternId(ItemStack item) {
        if (item == null) {
            return null;
        }
        String name = item.getType().name();
        return name.endsWith(TEMPLATE_SUFFIX)
                ? name.substring(0, name.length() - TEMPLATE_SUFFIX.length()).toLowerCase(Locale.ROOT)
                : null;
    }

    private Rune patternRune(ItemStack template) {
        String id = templatePatternId(template);
        return id == null ? null : registry.get(id);
    }

    private TrimMaterial trimMaterialOf(ItemStack item) {
        return item == null ? null : TRIM_INGREDIENTS.get(item.getType());
    }

    /** The highest-tier trim material the player is carrying anywhere in their inventory, if any. */
    private TrimMaterial bestOwnedMaterial(Player player) {
        TrimMaterial best = null;
        int bestTier = 0;
        for (Map.Entry<Material, TrimMaterial> e : TRIM_INGREDIENTS.entrySet()) {
            if (!player.getInventory().containsAtLeast(new ItemStack(e.getKey()), 1)) {
                continue;
            }
            int tier = roller.materialTier(e.getValue());
            if (tier > bestTier) {
                bestTier = tier;
                best = e.getValue();
            }
        }
        return best;
    }

    private void removeOneMaterial(Player player, TrimMaterial material) {
        for (Map.Entry<Material, TrimMaterial> e : TRIM_INGREDIENTS.entrySet()) {
            if (e.getValue() == material) {
                player.getInventory().removeItem(new ItemStack(e.getKey(), 1));
                return;
            }
        }
    }

    private String patternName(Rune rune) {
        return patternName(rune.id());
    }

    private String patternName(String id) {
        return lang.plain("runes.pattern-names." + (id == null ? "" : id.toLowerCase(Locale.ROOT)));
    }

    private Enchantment randomCurse() {
        List<String> ids = config.raw().getStringList("runes.curse.enchantments");
        if (ids.isEmpty()) {
            return Enchantment.VANISHING_CURSE;
        }
        String pick = ids.get(ThreadLocalRandom.current().nextInt(ids.size()));
        Enchantment enchant = org.bukkit.Registry.ENCHANTMENT.get(
                NamespacedKey.minecraft(pick.toLowerCase(Locale.ROOT)));
        return enchant == null ? Enchantment.VANISHING_CURSE : enchant;
    }

    private int maxScoreFor(org.bukkit.configuration.ConfigurationSection difficulty) {
        var cfg = difficulty != null ? difficulty
                : config.raw().getConfigurationSection("minigames.sequence-memory");
        return cfg == null ? 8 : cfg.getInt("max-length", 8);
    }

    private ItemStack decremented(ItemStack item) {
        if (item.getAmount() <= 1) {
            return null;
        }
        ItemStack copy = item.clone();
        copy.setAmount(item.getAmount() - 1);
        return copy;
    }

    /** Cost list parsing shared with the hunter's reroll cost, "MATERIAL:amount" per entry. */
    private boolean hasCost(Player player, List<String> cost) {
        for (Map.Entry<Material, Integer> e : parseCost(cost).entrySet()) {
            if (!player.getInventory().containsAtLeast(new ItemStack(e.getKey()), e.getValue())) {
                return false;
            }
        }
        return true;
    }

    private void payCost(Player player, List<String> cost) {
        for (Map.Entry<Material, Integer> e : parseCost(cost).entrySet()) {
            player.getInventory().removeItem(new ItemStack(e.getKey(), e.getValue()));
        }
    }

    private Map<Material, Integer> parseCost(List<String> cost) {
        Map<Material, Integer> parsed = new LinkedHashMap<>();
        for (String entry : cost) {
            String[] parts = entry.split(":", 2);
            Material material = Material.matchMaterial(parts[0]);
            if (material == null) {
                continue;
            }
            int amount = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1;
            parsed.merge(material, amount, Integer::sum);
        }
        return parsed;
    }

    private void giveBack(Player player, ItemStack item) {
        ItemStack cursor = player.getItemOnCursor();
        if (cursor == null || cursor.getType() == Material.AIR) {
            player.setItemOnCursor(item);
            return;
        }
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item);
        for (ItemStack leftover : overflow.values()) {
            player.getWorld().dropItem(player.getLocation(), leftover);
        }
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

    /** Locates the carried item by its nonce (cursor, then inventory), runs {@code action}, writes it back. */
    private void updateNonceItem(Player player, String nonce, Consumer<ItemStack> action) {
        ItemStack cursor = player.getItemOnCursor();
        if (matchesNonce(cursor, nonce)) {
            action.accept(cursor);
            player.setItemOnCursor(cursor);
            return;
        }
        var inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (matchesNonce(item, nonce)) {
                action.accept(item);
                inv.setItem(i, item);
                return;
            }
        }
    }

    private boolean matchesNonce(ItemStack item, String nonce) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return nonce.equals(item.getItemMeta().getPersistentDataContainer().get(nonceKey, PersistentDataType.STRING));
    }
}
