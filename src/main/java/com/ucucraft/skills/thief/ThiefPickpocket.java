package com.ucucraft.skills.thief;

import com.ucucraft.skills.classes.ClassManager;
import com.ucucraft.skills.classes.impl.ThiefClass;
import com.ucucraft.skills.config.ConfigManager;
import com.ucucraft.skills.data.PlayerProfile;
import com.ucucraft.skills.lang.LangManager;
import com.ucucraft.skills.minigame.MinigameManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Thief level 3: sneak + right-click a player to attempt a pickpocket. On winning a minigame the
 * thief opens a small menu of up to {@code thief.pickpocket.choices} random items from the victim;
 * clicking one drops it to the thief and the victim is notified. Tunables: {@code thief.pickpocket}.
 */
public final class ThiefPickpocket implements Listener {

    /** Holder that marks the pickpocket menu and maps its slots back to the victim's real slots. */
    private static final class Menu implements InventoryHolder {
        private final UUID victim;
        private final Map<Integer, Integer> slotToVictimSlot;
        private Inventory inventory;

        private Menu(UUID victim, Map<Integer, Integer> slotToVictimSlot) {
            this.victim = victim;
            this.slotToVictimSlot = slotToVictimSlot;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final ConfigManager config;
    private final ClassManager classManager;
    private final LangManager lang;
    private final MinigameManager minigames;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public ThiefPickpocket(ConfigManager config, ClassManager classManager, LangManager lang,
                           MinigameManager minigames) {
        this.config = config;
        this.classManager = classManager;
        this.lang = lang;
        this.minigames = minigames;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || !(event.getRightClicked() instanceof Player victim)) {
            return;
        }
        Player thief = event.getPlayer();
        if (!thief.isSneaking() || thiefLevel(thief) < config.raw().getInt("thief.pickpocket.min-level", 3)) {
            return;
        }
        event.setCancelled(true);

        long cooldownMs = config.raw().getLong("thief.pickpocket.cooldown-seconds", 10) * 1000L;
        long now = System.currentTimeMillis();
        Long until = cooldowns.get(thief.getUniqueId());
        if (until != null && now < until) {
            lang.send(thief, "thief.pickpocket-cooldown",
                    Map.of("seconds", String.valueOf((until - now) / 1000 + 1)));
            return;
        }

        UUID victimId = victim.getUniqueId();
        String game = config.raw().getString("thief.pickpocket.minigame", "typing");
        if (!minigames.start(thief, game, result -> resolve(thief, victimId, result.won()))) {
            lang.send(thief, "thief.pickpocket-unavailable");
            return;
        }
        cooldowns.put(thief.getUniqueId(), now + cooldownMs);
    }

    private void resolve(Player thief, UUID victimId, boolean success) {
        Player victim = Bukkit.getPlayer(victimId);
        if (!success) {
            lang.send(thief, "thief.pickpocket-failed");
            if (victim != null && config.raw().getBoolean("thief.pickpocket.alert-on-fail", true)) {
                lang.send(victim, "thief.pickpocket-caught", Map.of("thief", thief.getName()));
            }
            return;
        }
        if (victim == null) {
            lang.send(thief, "thief.pickpocket-empty");
            return;
        }
        openMenu(thief, victim);
    }

    /** Opens a menu of up to {@code choices} random items from the victim for the thief to pick. */
    private void openMenu(Player thief, Player victim) {
        List<Integer> victimSlots = new ArrayList<>();
        ItemStack[] contents = victim.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null && !contents[i].getType().isAir()) {
                victimSlots.add(i);
            }
        }
        if (victimSlots.isEmpty()) {
            lang.send(thief, "thief.pickpocket-empty");
            return;
        }
        Collections.shuffle(victimSlots);
        int choices = Math.min(Math.max(1, config.raw().getInt("thief.pickpocket.choices", 5)), victimSlots.size());

        Map<Integer, Integer> slotMap = new HashMap<>();
        Menu menu = new Menu(victim.getUniqueId(), slotMap);
        Inventory inv = Bukkit.createInventory(menu, 9,
                lang.component("thief.pickpocket-menu-title", Map.of("player", victim.getName())));
        menu.inventory = inv;

        int start = (9 - choices) / 2;
        for (int i = 0; i < choices; i++) {
            int victimSlot = victimSlots.get(i);
            int guiSlot = start + i;
            inv.setItem(guiSlot, contents[victimSlot].clone());
            slotMap.put(guiSlot, victimSlot);
        }
        thief.openInventory(inv);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true); // display only — the real transfer is handled here
        if (!(event.getWhoClicked() instanceof Player thief)) {
            return;
        }
        Integer victimSlot = menu.slotToVictimSlot.get(event.getRawSlot());
        if (victimSlot == null) {
            return;
        }
        thief.closeInventory();

        Player victim = Bukkit.getPlayer(menu.victim);
        ItemStack inSlot = victim == null ? null : victim.getInventory().getItem(victimSlot);
        if (victim == null || inSlot == null || inSlot.getType().isAir()) {
            lang.send(thief, "thief.pickpocket-empty");
            return;
        }
        int taken = Math.min(Math.max(1, config.raw().getInt("thief.pickpocket.amount", 1)), inSlot.getAmount());
        ItemStack stolen = inSlot.clone();
        stolen.setAmount(taken);
        inSlot.setAmount(inSlot.getAmount() - taken);
        victim.getInventory().setItem(victimSlot, inSlot.getAmount() <= 0 ? null : inSlot);

        thief.getWorld().dropItemNaturally(thief.getLocation(), stolen);
        lang.send(thief, "thief.pickpocket-success",
                Map.of("item", plain(stolen), "amount", String.valueOf(stolen.getAmount())));
        if (config.raw().getBoolean("thief.pickpocket.alert-victim", true)) {
            lang.send(victim, "thief.pickpocket-victim", Map.of("thief", thief.getName()));
        }
    }

    @EventHandler
    public void onMenuDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof Menu) {
            event.setCancelled(true);
        }
    }

    private String plain(ItemStack item) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(item.effectiveName());
    }

    private int thiefLevel(Player player) {
        PlayerProfile profile = classManager.profile(player);
        return profile.hasClass() && ThiefClass.ID.equalsIgnoreCase(profile.classId())
                ? profile.level() : 0;
    }
}
