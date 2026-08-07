package com.ucucraft.skills.warrior;

import com.ucucraft.skills.lang.LangManager;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The stance picker opened with sneak + right-click air. Clicking a stance requests the switch. */
public final class StanceMenu implements Listener {

    /** Holder that marks the menu and maps its slots to stances. */
    private static final class Holder implements InventoryHolder {
        private final List<Stance> slots = new ArrayList<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final LangManager lang;
    private final StanceService stances;

    public StanceMenu(LangManager lang, StanceService stances) {
        this.lang = lang;
        this.stances = stances;
    }

    public void open(Player player) {
        List<Stance> all = stances.registry().all();
        Holder holder = new Holder();
        int size = Math.max(9, (int) Math.ceil(all.size() / 9.0) * 9);
        Inventory inventory = Bukkit.createInventory(holder, size, lang.component("warrior.menu-title"));
        holder.inventory = inventory;

        int level = stances.level(player);
        var held = player.getInventory().getItemInMainHand().getType();
        String active = stances.selected(player).id();
        for (Stance stance : all) {
            ItemStack icon = new ItemStack(stance.icon());
            ItemMeta meta = icon.getItemMeta();
            meta.displayName(lang.parse(stances.displayName(stance), Map.of())
                    .decoration(TextDecoration.ITALIC, false));
            String state = stance.minLevel() > level ? "warrior.menu-locked"
                    : !stance.appliesTo(held) ? "warrior.menu-wrong-weapon"
                    : stance.id().equals(active) ? "warrior.menu-active" : "warrior.menu-available";
            meta.lore(List.of(lang.component(state, Map.of("level", String.valueOf(stance.minLevel())))
                    .decoration(TextDecoration.ITALIC, false)));
            icon.setItemMeta(meta);
            holder.slots.add(stance);
            inventory.setItem(holder.slots.size() - 1, icon);
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) {
            return;
        }
        event.setCancelled(true); // display only
        int slot = event.getRawSlot();
        if (!(event.getWhoClicked() instanceof Player player) || slot < 0 || slot >= holder.slots.size()) {
            return;
        }
        player.closeInventory();
        stances.request(player, holder.slots.get(slot));
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }
}
