package com.ucucraft.skills.minigame;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Per-player run state. Holds one opaque state object owned by the minigame. */
public final class MinigameSession {

    private final MinigameManager manager;
    private final Player player;
    private final Minigame game;
    private final List<Integer> tasks = new ArrayList<>();
    private final Consumer<MinigameResult> onComplete;
    private Object state;

    public MinigameSession(MinigameManager manager, Player player, Minigame game) {
        this(manager, player, game, null);
    }

    public MinigameSession(MinigameManager manager, Player player, Minigame game,
                           Consumer<MinigameResult> onComplete) {
        this.manager = manager;
        this.player = player;
        this.game = game;
        this.onComplete = onComplete;
    }

    /** Optional callback invoked with the result; when set, suppresses the default win/lose message. */
    public Consumer<MinigameResult> onComplete() {
        return onComplete;
    }

    public MinigameManager manager() {
        return manager;
    }

    public Player player() {
        return player;
    }

    public Minigame game() {
        return game;
    }

    public Object state() {
        return state;
    }

    public void state(Object state) {
        this.state = state;
    }

    public void track(int taskId) {
        tasks.add(taskId);
    }

    public void cancelTasks() {
        for (int taskId : tasks) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
        tasks.clear();
    }

    /** Shortcut for {@code manager().finish(this, result)}. */
    public void finish(MinigameResult result) {
        manager.finish(this, result);
    }
}
