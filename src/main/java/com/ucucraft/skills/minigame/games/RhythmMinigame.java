package com.ucucraft.skills.minigame.games;

import com.ucucraft.skills.minigame.Minigame;
import com.ucucraft.skills.minigame.MinigameResult;
import com.ucucraft.skills.minigame.MinigameSession;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A visual timing game: a marker sweeps across an on-screen bar (shown on the action bar) and the
 * player presses <b>space</b> when it crosses the green target zone. Each pass is one round; landing
 * on the target scores a hit. Purely visual, so hearing is not required.
 */
public final class RhythmMinigame implements Minigame {

    private static final class State {
        int rounds;
        int done;
        int hits;
        int width;
        int targetStart;
        int targetEnd;
        int marker;
        boolean acted;
    }

    @Override
    public String id() {
        return "rhythm";
    }

    @Override
    public void start(MinigameSession session) {
        ConfigurationSection cfg = session.config("minigames.rhythm");
        State state = new State();
        state.rounds = cfg != null ? cfg.getInt("beats", 8) : 8;
        state.width = Math.max(5, cfg != null ? cfg.getInt("track-width", 21) : 21);
        session.state(state);
        session.manager().lang().send(session.player(), "minigame.rhythm-prompt");
        nextRound(state);

        long period = Math.max(1, cfg != null ? cfg.getLong("marker-speed-ticks", 2) : 2);
        int task = session.manager().plugin().getServer().getScheduler().runTaskTimer(
                session.manager().plugin(),
                () -> tick(session),
                period, period).getTaskId();
        session.track(task);
    }

    private void tick(MinigameSession session) {
        State st = (State) session.state();
        if (st.done >= st.rounds) {
            return;
        }
        render(session, st);
        st.marker++;
        if (st.marker >= st.width) {
            resolve(session, false); // marker ran off the end without a press → miss
        }
    }

    @Override
    public void onJump(MinigameSession session) {
        State st = (State) session.state();
        if (st.done >= st.rounds || st.acted) {
            return;
        }
        resolve(session, st.marker >= st.targetStart && st.marker <= st.targetEnd);
    }

    private void resolve(MinigameSession session, boolean hit) {
        State st = (State) session.state();
        st.acted = true;
        st.done++;
        if (hit) {
            st.hits++;
        }
        playFeedback(session, hit);
        if (st.done >= st.rounds) {
            int needed = (int) Math.ceil(st.rounds * 0.6);
            session.finish(st.hits >= needed ? MinigameResult.win(st.hits) : MinigameResult.lose(st.hits));
        } else {
            nextRound(st);
        }
    }

    private void nextRound(State st) {
        int targetWidth = 3;
        int max = st.width - targetWidth;
        st.targetStart = 1 + ThreadLocalRandom.current().nextInt(Math.max(1, max - 1));
        st.targetEnd = st.targetStart + targetWidth - 1;
        st.marker = 0;
        st.acted = false;
    }

    private void render(MinigameSession session, State st) {
        StringBuilder bar = new StringBuilder("<gray>♪ <yellow>" + st.hits + "</yellow>/" + st.rounds + "  <dark_gray>[");
        for (int i = 0; i < st.width; i++) {
            boolean target = i >= st.targetStart && i <= st.targetEnd;
            if (i == st.marker) {
                bar.append(target ? "<green><bold>◆</bold></green>" : "<yellow><bold>◆</bold></yellow>");
            } else if (target) {
                bar.append("<green>▮</green>");
            } else {
                bar.append("<dark_gray>▬</dark_gray>");
            }
        }
        bar.append("<dark_gray>]");
        session.player().sendActionBar(session.manager().lang().parse(bar.toString(), Map.of()));
    }

    private void playFeedback(MinigameSession session, boolean hit) {
        String key = session.manager().config().raw().getString("minigames.rhythm.sound", "");
        if (key == null || key.isBlank()) {
            return;
        }
        session.player().playSound(Sound.sound(Key.key(key), Sound.Source.MASTER, 1f, hit ? 1.6f : 0.6f));
    }
}
