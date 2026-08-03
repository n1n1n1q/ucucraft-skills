package com.ucucraft.skills.item;

import com.ucucraft.skills.classes.ClassManager;
import com.ucucraft.skills.classes.SkillClass;
import com.ucucraft.skills.classes.SkillClassRegistry;
import com.ucucraft.skills.lang.LangManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/** Right-clicking a scroll grants its class and consumes one item. */
public final class ScrollListener implements Listener {

    private final ScrollItem scroll;
    private final ClassManager classManager;
    private final SkillClassRegistry registry;
    private final LangManager lang;

    public ScrollListener(ScrollItem scroll, ClassManager classManager, SkillClassRegistry registry, LangManager lang) {
        this.scroll = scroll;
        this.classManager = classManager;
        this.registry = registry;
        this.lang = lang;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        String classId = scroll.classOf(item);
        if (classId == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();

        SkillClass skillClass = registry.get(classId);
        if (skillClass == null) {
            lang.send(player, "class.unknown", Map.of("id", classId));
            return;
        }

        String name = lang.plain(skillClass.displayNameKey());
        classManager.assign(player, classId);
        lang.send(player, "scroll.used", Map.of("class", name));
        lang.send(player, "class.assigned", Map.of("class", name));
        item.setAmount(item.getAmount() - 1);
    }
}
