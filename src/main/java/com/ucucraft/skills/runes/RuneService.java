package com.ucucraft.skills.runes;

import com.ucucraft.skills.lang.LangManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The single place that reads/writes a rune on an item: PDC + the rendered trim/enchant/lore. The
 * PDC is the source of truth — trim and enchant are always rebuilt from it (see docs/runesmith.md),
 * so nothing else (an anvil, another plugin) can quietly change the real level.
 */
public final class RuneService {

    private static final Map<TrimMaterial, Color> TRIM_COLORS = new LinkedHashMap<>();
    static {
        TRIM_COLORS.put(TrimMaterial.COPPER, Color.fromRGB(0xB4684D));
        TRIM_COLORS.put(TrimMaterial.IRON, Color.fromRGB(0xD8D8D8));
        TRIM_COLORS.put(TrimMaterial.GOLD, Color.fromRGB(0xDEB12D));
        TRIM_COLORS.put(TrimMaterial.LAPIS, Color.fromRGB(0x21497B));
        TRIM_COLORS.put(TrimMaterial.REDSTONE, Color.fromRGB(0x971607));
        TRIM_COLORS.put(TrimMaterial.AMETHYST, Color.fromRGB(0x9A5CC6));
        TRIM_COLORS.put(TrimMaterial.EMERALD, Color.fromRGB(0x11A036));
        TRIM_COLORS.put(TrimMaterial.QUARTZ, Color.fromRGB(0xE3D4C4));
        TRIM_COLORS.put(TrimMaterial.DIAMOND, Color.fromRGB(0x6EECD2));
        TRIM_COLORS.put(TrimMaterial.NETHERITE, Color.fromRGB(0x413E45));
    }

    private static final String[] ROMAN_TENS = {"", "X", "XX", "XXX", "XL"};
    private static final String[] ROMAN_ONES = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};

    private final LangManager lang;
    private final NamespacedKey patternKey;
    private final NamespacedKey materialKey;
    private final NamespacedKey levelKey;
    private final NamespacedKey upgradesKey;
    private final NamespacedKey cursedKey;

    public RuneService(Plugin plugin, LangManager lang) {
        this.lang = lang;
        this.patternKey = new NamespacedKey(plugin, "rune_pattern");
        this.materialKey = new NamespacedKey(plugin, "rune_material");
        this.levelKey = new NamespacedKey(plugin, "rune_level");
        this.upgradesKey = new NamespacedKey(plugin, "rune_upgrades");
        this.cursedKey = new NamespacedKey(plugin, "rune_cursed");
    }

    /** Applies (or replaces) a rune on the item: PDC, trim (armor only) and enchant, plus lore. */
    public void apply(ItemStack item, Rune rune, TrimMaterial material, int level, boolean armor) {
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(patternKey, PersistentDataType.STRING, rune.id());
        pdc.set(materialKey, PersistentDataType.STRING, material.key().value());
        pdc.set(levelKey, PersistentDataType.INTEGER, level);
        pdc.remove(cursedKey);

        if (armor && meta instanceof ArmorMeta armorMeta) {
            armorMeta.setTrim(new ArmorTrim(material, rune.pattern()));
        }
        meta.addEnchant(rune.enchantment(), level, true);

        Component name = lang.parse(lang.plain("runes.pattern-names." + rune.id()), Map.of())
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(name.append(Component.text(" ")).append(Component.translatable(item.getType()))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(lang.component("runes.rune-lore", Map.of(
                "name", lang.plain("runes.pattern-names." + rune.id()),
                "roman", roman(level))).decoration(TextDecoration.ITALIC, false)));

        item.setItemMeta(meta);
    }

    /** Marks the item cursed and applies the given curse enchantment (Binding/Vanishing). */
    public void applyCurse(ItemStack item, Enchantment curse) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(cursedKey, PersistentDataType.BOOLEAN, true);
        meta.addEnchant(curse, 1, true);
        item.setItemMeta(meta);
    }

    /** Strips a rune-curse (both the flag and any Binding/Vanishing enchant it added). */
    public void stripCurse(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().remove(cursedKey);
        meta.removeEnchant(Enchantment.BINDING_CURSE);
        meta.removeEnchant(Enchantment.VANISHING_CURSE);
        item.setItemMeta(meta);
    }

    /** Removes a rune entirely: PDC, trim and its enchantment. Used by transfer's source side. */
    public void clearRune(ItemStack item, Enchantment enchantment) {
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.remove(patternKey);
        pdc.remove(materialKey);
        pdc.remove(levelKey);
        pdc.remove(upgradesKey);
        meta.removeEnchant(enchantment);
        if (meta instanceof ArmorMeta armorMeta && armorMeta.hasTrim()) {
            armorMeta.setTrim(null);
        }
        meta.displayName(null);
        meta.lore(null);
        item.setItemMeta(meta);
    }

    public boolean hasRune(ItemStack item) {
        return read(item, patternKey, PersistentDataType.STRING) != null;
    }

    public String patternId(ItemStack item) {
        return read(item, patternKey, PersistentDataType.STRING);
    }

    public TrimMaterial material(ItemStack item) {
        String key = read(item, materialKey, PersistentDataType.STRING);
        return key == null ? null : org.bukkit.Registry.TRIM_MATERIAL
                .get(NamespacedKey.minecraft(key));
    }

    public int level(ItemStack item) {
        Integer level = read(item, levelKey, PersistentDataType.INTEGER);
        return level == null ? 0 : level;
    }

    public int upgrades(ItemStack item) {
        Integer count = read(item, upgradesKey, PersistentDataType.INTEGER);
        return count == null ? 0 : count;
    }

    public void incrementUpgrade(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(upgradesKey, PersistentDataType.INTEGER, upgrades(item) + 1);
        item.setItemMeta(meta);
    }

    public boolean isCursed(ItemStack item) {
        Boolean cursed = read(item, cursedKey, PersistentDataType.BOOLEAN);
        return cursed != null && cursed;
    }

    /** Trim colour used for the resonance proc's particle; white if the material is unmapped. */
    public Color colorOf(TrimMaterial material) {
        return TRIM_COLORS.getOrDefault(material, Color.WHITE);
    }

    public static String roman(int number) {
        if (number <= 0 || number >= 50) {
            return String.valueOf(number);
        }
        return ROMAN_TENS[number / 10] + ROMAN_ONES[number % 10];
    }

    private <T, Z> Z read(ItemStack item, NamespacedKey key, PersistentDataType<T, Z> type) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(key, type);
    }
}
