package com.ucucraft.skills.minigame.games;

import com.ucucraft.skills.minigame.Minigame;
import com.ucucraft.skills.minigame.MinigameResult;
import com.ucucraft.skills.minigame.MinigameSession;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;

/**
 * Left-click in time with each beat. The beat is a sound, so hearing players have an
 * edge — a class's accessibility (visual cue) bonus adds an on-screen beat to compensate.
 */
public final class RhythmMinigame implements Minigame {

    private static final class State {
        int totalBeats;
        int beatsPlayed;
        int hits;
        long lastBeatMs;
        boolean hitThisBeat;
    }

    @Override
    public String id() {
        return "rhythm";
    }

    @Override
    public void start(MinigameSession session) {
        ConfigurationSection cfg = session.manager().config().raw()
                .getConfigurationSection("minigames.rhythm");
        long intervalMs = cfg != null ? cfg.getLong("beat-interval-ms", 800) : 800;
        long toleranceMs = cfg != null ? cfg.getLong("tolerance-ms", 200) : 200;
        int beats = cfg != null ? cfg.getInt("beats", 8) : 8;
        String soundKey = cfg != null ? cfg.getString("sound", "minecraft:block.note_block.pling")
                : "minecraft:block.note_block.pling";

        State state = new State();
        state.totalBeats = beats;
        session.state(state);
        session.manager().lang().send(session.player(), "minigame.rhythm-prompt");

        Sound sound = Sound.sound(Key.key(soundKey), Sound.Source.MASTER, 1f, 1f);
        long toleranceMsFinal = toleranceMs;
        boolean showVisual = session.manager().classManager().accessibilityCueLevel(session.player()) > 0;
        long periodTicks = Math.max(1, intervalMs / 50);

        int task = session.manager().plugin().getServer().getScheduler().runTaskTimer(
                session.manager().plugin(),
                () -> {
                    State st = (State) session.state();
                    if (st.beatsPlayed >= st.totalBeats) {
                        return;
                    }
                    st.beatsPlayed++;
                    st.lastBeatMs = System.currentTimeMillis();
                    st.hitThisBeat = false;
                    session.player().playSound(sound);
                    if (showVisual) {
                        session.player().sendActionBar(
                                session.manager().lang().component("minigame.rhythm-beat"));
                    }
                    if (st.beatsPlayed >= st.totalBeats) {
                        scheduleFinish(session, toleranceMsFinal);
                    }
                },
                periodTicks, periodTicks).getTaskId();
        session.track(task);
    }

    private void scheduleFinish(MinigameSession session, long toleranceMs) {
        int task = session.manager().plugin().getServer().getScheduler().runTaskLater(
                session.manager().plugin(),
                () -> {
                    State st = (State) session.state();
                    int needed = (int) Math.ceil(st.totalBeats * 0.6);
                    session.finish(st.hits >= needed
                            ? MinigameResult.win(st.hits)
                            : MinigameResult.lose(st.hits));
                },
                Math.max(1, toleranceMs / 50)).getTaskId();
        session.track(task);
    }

    @Override
    public void onClick(MinigameSession session) {
        State st = (State) session.state();
        long tolerance = session.manager().config().raw().getLong("minigames.rhythm.tolerance-ms", 200);
        long since = System.currentTimeMillis() - st.lastBeatMs;
        if (st.lastBeatMs > 0 && !st.hitThisBeat && since <= tolerance) {
            st.hits++;
            st.hitThisBeat = true;
            session.manager().lang().send(session.player(), "minigame.rhythm-hit");
        } else {
            session.manager().lang().send(session.player(), "minigame.rhythm-miss");
        }
    }
}
