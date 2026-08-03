package com.ucucraft.skills.minigame;

/**
 * A self-contained minigame. Implementations schedule their own tasks through the
 * session and call {@link MinigameManager#finish} when done. Register one in
 * {@link MinigameManager} to make it playable.
 */
public interface Minigame {

    String id();

    void start(MinigameSession session);

    /** Chat input, already on the main thread. */
    default void onChat(MinigameSession session, String message) {
    }

    /** Left-click input. */
    default void onClick(MinigameSession session) {
    }
}
