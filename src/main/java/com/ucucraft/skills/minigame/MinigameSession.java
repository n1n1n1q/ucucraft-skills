package com.ucucraft.skills.minigame;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Per-player run state. Holds one opaque state object owned by the minigame. */
public final class MinigameSession {

    private final MinigameManager manager;
    private final Player player;
    private final Minigame game;
    private final List<Integer> tasks = new ArrayList<>();
    private Object state;

    public MinigameSession(MinigameManager manager, Player player, Minigame game) {
        this.manager = manager;
        this.player = player;
        this.game = game;
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
