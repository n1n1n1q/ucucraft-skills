# Hunter (МИСЛИВЕЦЬ)

The first ranged class. It crafts and reforges **bows/crossbows** through the same Terraria-style
[modifier system](modifiers.md) the blacksmith uses for metal gear, and gains a ranged ability at
each level. Grant it like any class: `/skills set hunter [player]` or a `hunter` scroll.

Code: [`classes/impl/HunterClass.java`](../src/main/java/com/ucucraft/skills/classes/impl/HunterClass.java)
and the [`hunter`](../src/main/java/com/ucucraft/skills/hunter/) package. All numbers live under
`hunter:` (and `classes.hunter`) in `config.yml`; all strings under `hunter:` in `lang/*.yml`.

## Bows share the modifier system

Bows and crossbows are gear type `BOW` ([`GearType`](../src/main/java/com/ucucraft/skills/smithing/GearType.java)).
Bow modifiers are ordinary modifier files with `categories: [bow]`
(seed set: `rapid`, `hawkeye`, `frayed`). A modifier's `attack_damage` is read by the hunter as
**bonus arrow damage**; other attributes (e.g. `movement_speed`) apply while the bow is held.

- **Hunter crafts a bow/crossbow** → a positive modifier is rolled directly, **no minigame** (unlike
  the blacksmith); odds scale with level (`hunter.levels.<1..4>`, same shape as `smithing.levels`).
- **Regular player crafts a bow/crossbow** → rolls a debuff (`hunter.regular.debuff-chance`).
- Shift-click crafting is blocked so each bow gets its own roll.

The level-2 slow arrow plays a **sound** cue, so `classes.hunter.visual-cue-level` should stay > 0 so
a non-hearing hunter can compensate (accessibility rule, see [CLAUDE.md](../CLAUDE.md)).

## Level abilities

| Level | Ability |
|---|---|
| 1 | **Reforge at the archer table.** Right-click a `hunter.table-material` (default `FLETCHING_TABLE`) holding a bow to instantly reroll its modifier (no minigame) for a material cost (`hunter.reroll.cost`), up to `hunter.reroll.max-rerolls` (5) times per item. Odds scale L1→L4; always yields a positive, so it also clears a debuff. |
| 2 | **Slow arrow** — `hunter.arrow-slow.chance` per shot for `damage-multiplier` damage + slowness, with a sound cue and particle trail. **Highlight prey** — sneak + right-click a bow to make nearby mobs glow and be *marked* (see below). |
| 3 | **Crossbow mastery** — `keep-loaded-chance` to keep the crossbow loaded after firing (the arrow stays in it), so the hunter can fire again immediately without reloading. **Steady aim** — sneak and hold still with a bow/crossbow for `steady-aim.charge-seconds` to charge a focused shot; the next arrow deals `steady-aim.damage-multiplier` damage. Charge progress and the ready state show on the action bar (a visual cue), optionally paired with `ready-sound`. Moving or unsneaking cancels it. |
| 4 | **Hit stacking** — consecutive arrow hits add `damage-per-stack` up to `max-bonus` within a time window. **Marked kill** — killing highlighted prey grants a speed + regen burst with an effect. |

## Highlight ability & external API

[`GlowService`](../src/main/java/com/ucucraft/skills/hunter/GlowService.java) implements the
highlight ability and is registered as a **Bukkit service**. Other plugins can retrieve it and
install a custom [`GlowPolicy`](../src/main/java/com/ucucraft/skills/hunter/GlowPolicy.java) to
decide which players highlight which targets:

```java
GlowService glow = getServer().getServicesManager().load(GlowService.class);
glow.setPolicy((hunter, target) -> hunter.hasPermission("myplugin.mark") && target instanceof Animals);
```

The default policy marks any non-player living entity. Note: Bukkit's `setGlowing` is **server-wide**
(every viewer sees the glow); true per-viewer glow would need packet-level access and is out of scope.

## Reload

`/skills reload` reloads config, language and modifiers; the hunter listeners read config live, so
tuning takes effect immediately.
