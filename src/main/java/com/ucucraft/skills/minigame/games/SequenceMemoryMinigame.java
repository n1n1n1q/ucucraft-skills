package com.ucucraft.skills.minigame.games;

import com.ucucraft.skills.minigame.Minigame;
import com.ucucraft.skills.minigame.MinigameResult;
import com.ucucraft.skills.minigame.MinigameSession;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** A shown number sequence grows each round; type it back to advance. */
public final class SequenceMemoryMinigame implements Minigame {

    private static final class State {
        int length;
        int maxLength;
        String expected;
    }

    @Override
    public String id() {
        return "sequence";
    }

    @Override
    public void start(MinigameSession session) {
        ConfigurationSection cfg = session.manager().config().raw()
                .getConfigurationSection("minigames.sequence-memory");
        State state = new State();
        state.length = cfg != null ? cfg.getInt("start-length", 3) : 3;
        state.maxLength = cfg != null ? cfg.getInt("max-length", 8) : 8;
        session.state(state);

        session.manager().lang().send(session.player(), "minigame.sequence-prompt");
        nextRound(session);
    }

    private void nextRound(MinigameSession session) {
        State state = (State) session.state();
        state.expected = IntStream.range(0, state.length)
                .mapToObj(i -> String.valueOf(ThreadLocalRandom.current().nextInt(1, 5)))
                .collect(Collectors.joining(" "));
        session.manager().lang().send(session.player(), "minigame.sequence-show",
                Map.of("sequence", state.expected));
        session.manager().lang().send(session.player(), "minigame.sequence-your-turn");
    }

    @Override
    public void onChat(MinigameSession session, String message) {
        State state = (State) session.state();
        String normalized = message.trim().replaceAll("\\s+", " ");
        if (!normalized.equals(state.expected)) {
            session.manager().lang().send(session.player(), "minigame.sequence-wrong");
            session.finish(MinigameResult.lose(state.length - 1));
            return;
        }
        if (state.length >= state.maxLength) {
            session.finish(MinigameResult.win(state.length));
            return;
        }
        state.length++;
        nextRound(session);
    }
}
