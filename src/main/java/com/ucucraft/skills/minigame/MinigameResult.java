package com.ucucraft.skills.minigame;

/** Outcome of a minigame run. */
public record MinigameResult(boolean won, int score) {

    public static MinigameResult win(int score) {
        return new MinigameResult(true, score);
    }

    public static MinigameResult lose(int score) {
        return new MinigameResult(false, score);
    }
}
