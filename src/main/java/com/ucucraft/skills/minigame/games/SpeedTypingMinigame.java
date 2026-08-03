package com.ucucraft.skills.minigame.games;

import com.ucucraft.skills.minigame.Minigame;
import com.ucucraft.skills.minigame.MinigameResult;
import com.ucucraft.skills.minigame.MinigameSession;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/** Type the shown word before the timer runs out. */
public final class SpeedTypingMinigame implements Minigame {

    @Override
    public String id() {
        return "typing";
    }

    @Override
    public void start(MinigameSession session) {
        ConfigurationSection cfg = session.manager().config().raw()
                .getConfigurationSection("minigames.speed-typing");
        List<String> words = cfg != null ? cfg.getStringList("words") : List.of();
        int duration = cfg != null ? cfg.getInt("duration-seconds", 10) : 10;
        String word = words.isEmpty() ? "redstone"
                : words.get(ThreadLocalRandom.current().nextInt(words.size()));

        session.state(word);
        session.manager().lang().send(session.player(), "minigame.typing-prompt", Map.of("word", word));

        int task = session.manager().plugin().getServer().getScheduler().runTaskLater(
                session.manager().plugin(),
                () -> {
                    session.manager().lang().send(session.player(), "minigame.typing-timeout");
                    session.finish(MinigameResult.lose(0));
                },
                duration * 20L).getTaskId();
        session.track(task);
    }

    @Override
    public void onChat(MinigameSession session, String message) {
        if (message.trim().equalsIgnoreCase((String) session.state())) {
            session.finish(MinigameResult.win(1));
        }
    }
}
