# Adding a minigame

A minigame is a `Minigame` implementation registered in `MinigameManager`.

## Contract
- `id()` — lowercase id used in `/skills minigame <id>`.
- `start(session)` — set up state, message the player, schedule timers.
- `onChat(session, message)` — chat input (already on the main thread). Optional.
- `onClick(session)` — left-click input. Optional.
- Call `session.finish(MinigameResult.win(score))` / `.lose(score)` when done. `finish` cancels
  the session's tracked tasks and sends the win/lose message.

The manager guarantees one active session per player and routes input to it. It cancels chat and
left-click events while a session is active.

## State & scheduling
Store per-run data in `session.state(obj)` / `session.state()` — one opaque object owned by your
game (a small `int[]`, a `String`, or a private static state class). **Track every scheduled task
id with `session.track(taskId)`** so it is cancelled on finish/quit. Schedule via
`session.manager().plugin().getServer().getScheduler()`.

## Template
```java
public final class ExampleGame implements Minigame {
    public String id() { return "example"; }

    public void start(MinigameSession s) {
        var cfg = s.manager().config().raw().getConfigurationSection("minigames.example");
        s.state(/* your state */);
        s.manager().lang().send(s.player(), "minigame.example-prompt", Map.of());
        int task = s.manager().plugin().getServer().getScheduler()
            .runTaskLater(s.manager().plugin(),
                () -> s.finish(MinigameResult.lose(0)), 20L * 10).getTaskId();
        s.track(task);
    }

    public void onClick(MinigameSession s) { /* update state, maybe finish */ }
}
```

## Wiring
1. Register in `SkillsPlugin#onEnable`: `minigameManager.register(new ExampleGame());`
2. Add tunables under `minigames.example` in `config.yml`.
3. Add every message key to all `lang/*.yml` under `minigame:`.

## Sound and accessibility
If your minigame uses sound, also provide a visual path gated on
`session.manager().classManager().accessibilityCueLevel(player) > 0` (see `RhythmMinigame`), so
players who can't hear can compensate through their class. See CLAUDE.md.

## Revamping a vanilla mechanic
To gate a real mechanic behind a minigame, cancel the vanilla event in a listener and start a
minigame; on `finish`, apply the outcome. Example sketch (not yet implemented):
```java
@EventHandler
public void onBlockBreak(BlockBreakEvent e) {
    if (isSpecialOre(e.getBlock())) {
        e.setCancelled(true);
        minigameManager.start(e.getPlayer(), "clicking"); // reward on win
    }
}
```
