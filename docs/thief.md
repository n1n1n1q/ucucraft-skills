# Thief (ЗЛОДІЙ)

A stealth/utility class. Its power is entirely ability-driven — no attribute passive — and unlocks
one ability per level. Grant it like any class: `/skills set thief [player]` or a `thief` scroll.

Code: [`classes/impl/ThiefClass.java`](../src/main/java/com/ucucraft/skills/classes/impl/ThiefClass.java)
and the [`thief`](../src/main/java/com/ucucraft/skills/thief/) package. All numbers live under
`thief:` in `config.yml`; all strings under `thief:` in `lang/*.yml`.

## Soft dependencies

Both are optional; the plugin runs without them (declared as `softdepend` in `plugin.yml`).

- **ucucraft-items** — provides the lockpick. Detected by its item-id PDC
  ([`Lockpick`](../src/main/java/com/ucucraft/skills/thief/Lockpick.java)), no compile-time link.
- **Countries** — its [public API](../../ucucraft-countries/src/main/java/com/ucucraft/countries/api)
  is compiled against (`libs/countries-1.0.0.jar`, `compileOnly`) and resolved at runtime through
  [`CountriesHook`](../src/main/java/com/ucucraft/skills/thief/CountriesHook.java). When absent, the
  members-in-chunk rule is simply skipped.

## Level abilities

| Level | Ability |
|---|---|
| 1 | **Lockpicking.** Right-click a container holding a lockpick. If the chunk holds members of the owning country (`thief.lockpick.require-country-members`), a minigame runs; a **loud** sound plays during and at the end (the theft is meant to be risky). On success the container opens. |
| 2 | **Fall control** — sneaking while falling negates fall damage for falls up to `thief.fall.max-blocks` (15). **Backstab** — a sneaking hit from behind deals `thief.backstab.multiplier`× damage. |
| 3 | **Pickpocket** — sneak + right-click a player to start a minigame (`thief.pickpocket.minigame`, default `typing`); win it to open a menu of `thief.pickpocket.choices` (5) random items from the victim — the one you click is dropped to you and the victim is notified. The victim must still be within `thief.pickpocket.max-distance` (4) blocks when the minigame ends, or it fails (they slipped away). On a cooldown. |
| 4 | **Smoke Screen** — sneak + swap-hand (F) drops a smoke bomb (`thief.smoke.cooldown-seconds`, default 30s): a `CAMPFIRE_SIGNAL_SMOKE` cloud, Blindness to nearby entities, and Speed II + Invisibility for the thief. |

## Notes

- Lockpicking runs whatever minigame id is set in `thief.lockpick.minigame` (default `sequence-memory`);
  it reuses the existing minigame framework, so the loud-sound risk is layered on top separately.
- The members-in-chunk rule counts online members of the **owning** country standing in the chest's
  chunk. Wilderness or an empty claim counts as zero (no target); a missing Countries plugin skips the
  check entirely.
- `/skills reload` re-reads config and language live.
