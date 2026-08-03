# Minigames

The minigame module replaces or gates ordinary actions with a short skill challenge. Each game is
independent, registered in `MinigameManager`, and playable with `/skills minigame <id>`. Only one
runs per player at a time.

All timings, targets and word lists live under `minigames:` in `config.yml`. All prompts live
under `minigame:` in `lang/*.yml`.

## Shipped games

| id | Input | Goal | Config key |
|---|---|---|---|
| `typing` | chat | Type the shown word before the timer ends. | `minigames.speed-typing` |
| `clicking` | left-click | Reach the click target before time runs out. | `minigames.speed-clicking` |
| `rhythm` | left-click | Hit each beat within tolerance. Uses **sound**. | `minigames.rhythm` |
| `sequence` | chat | Repeat a growing number sequence. | `minigames.sequence-memory` |

### typing
Picks a random word from `words`, prompts, and starts a `duration-seconds` timer. Correct chat →
win; timeout → lose.

### clicking
Counts left-clicks for `duration-seconds`, showing progress on the action bar. Win if clicks reach
`target-clicks`.

### rhythm
Plays `beats` beats `beat-interval-ms` apart using `sound`. A click within `tolerance-ms` of a
beat scores a hit; win needs ≥60% hits. Because the beat is audio, players with a class
accessibility bonus (`visual-cue-level > 0`) also see an on-screen beat — see the accessibility
note in [CLAUDE.md](../CLAUDE.md).

### sequence
Shows a sequence of `start-length` numbers (1–4); type it back to grow it by one, up to
`max-length`. A wrong entry ends the run.

## Design intent
Minigames exist to **revamp regular mechanics** — mining, fishing, combat, enchanting, etc. —
by putting a skill check in front of the reward. Today they run standalone for testing; the hook
for gating a vanilla event is documented in
[adding-a-minigame.md](adding-a-minigame.md#revamping-a-vanilla-mechanic).

Ideas for future games: precise-timing bar (stop a moving cursor in a zone), reaction test
(click when the screen changes), pattern trace, hold-and-release charge.
