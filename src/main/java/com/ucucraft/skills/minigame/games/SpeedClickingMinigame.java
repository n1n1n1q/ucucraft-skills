package com.ucucraft.skills.minigame.games;

import com.ucucraft.skills.minigame.Minigame;
import com.ucucraft.skills.minigame.MinigameResult;
import com.ucucraft.skills.minigame.MinigameSession;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;

/** Left-click as many times as possible before time runs out. */
public final class SpeedClickingMinigame implements Minigame {

    @Override
    public String id() {
        return "clicking";
    }

    @Override
    public void start(MinigameSession session) {
        ConfigurationSection cfg = session.config("minigames.speed-clicking");
        int target = cfg != null ? cfg.getInt("target-clicks", 30) : 30;
        int duration = cfg != null ? cfg.getInt("duration-seconds", 5) : 5;

        session.state(new int[]{0, target});
        session.manager().lang().send(session.player(), "minigame.clicking-prompt",
                Map.of("seconds", String.valueOf(duration)));

        int task = session.manager().plugin().getServer().getScheduler().runTaskLater(
                session.manager().plugin(),
                () -> {
                    int[] state = (int[]) session.state();
                    session.finish(state[0] >= state[1]
                            ? MinigameResult.win(state[0])
                            : MinigameResult.lose(state[0]));
                },
                duration * 20L).getTaskId();
        session.track(task);
    }

    @Override
    public void onClick(MinigameSession session) {
        int[] state = (int[]) session.state();
        state[0]++;
        session.player().sendActionBar(session.manager().lang().component("minigame.clicking-progress",
                Map.of("clicks", String.valueOf(state[0]), "target", String.valueOf(state[1]))));
        if (state[0] >= state[1]) {
            session.finish(MinigameResult.win(state[0]));
        }
    }
}
