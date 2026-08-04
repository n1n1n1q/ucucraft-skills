# Blacksmith (РЕМІСНИК)

The first crafting class. Its power is not an attribute buff but control over **crafting**: which
metal tiers a player may forge, and a Terraria-style **modifier** rolled on every piece they make.
Grant it like any class: `/skills set blacksmith [player]` or a `blacksmith` scroll.

Code: [`classes/impl/BlacksmithClass.java`](../src/main/java/com/ucucraft/skills/classes/impl/BlacksmithClass.java)
(the class) and the [`smithing`](../src/main/java/com/ucucraft/skills/smithing/) package (the system).
All numbers live under `smithing:` and `crafting:` in `config.yml`; all strings under `smithing:`
in `lang/*.yml`.

## Who can craft what

| Tier | Regular player | Blacksmith |
|---|---|---|
| Leather / Copper | vanilla | vanilla |
| Iron, Chainmail | craftable, but **random debuff**; armor forced onto the harder (block) recipe | forge minigame → positive modifier |
| Gold, Diamond, Rose Gold | **blocked** | needs level `smithing.master-tiers.min-level` (3) |
| Netherite | vanilla (left alone) | vanilla |

Rose gold is detected through the **ucucraft-items** soft dependency
([`RoseGold`](../src/main/java/com/ucucraft/skills/smithing/RoseGold.java)) — no hard dependency; if
that plugin is absent nothing rose-gold ever matches.

## The forge (any craft)

There is no bench block: whenever a blacksmith crafts iron+ gear, a **rhythm minigame** runs (the
existing [`RhythmMinigame`](../src/main/java/com/ucucraft/skills/minigame/games/RhythmMinigame.java)
via a result callback). The number of beat hits, plus the blacksmith's level, decides the modifier —
see [`ModifierRoller`](../src/main/java/com/ucucraft/skills/smithing/ModifierRoller.java) and
`smithing.levels.<1..4>` in config. Level 1 usually yields a clean item (no modifier, no debuff);
higher levels unlock higher tiers and better odds. Because the beat is a **sound**, the blacksmith's
`classes.blacksmith.visual-cue-level` must stay > 0 (accessibility rule, see
[CLAUDE.md](../CLAUDE.md)).

Shift-click crafting of gated gear is blocked ("forge one at a time") so each piece gets its own roll.

## Reroll & fix (shift-right-click held gear)

- **Reroll** (needs level `smithing.reroll.min-level`, default 2): shift-right-click a modifier-free
  or positively-modified piece to re-run the minigame and reforge it, up to
  `smithing.reroll.max-rerolls` (5) times per item (tracked in the item's PDC).
- **Fix** (needs `smithing.fix.min-level`, default 1): shift-right-click a **debuffed** piece to
  hammer the debuff out.

## Harder recipes

Regular players can't produce clean iron armor: the vanilla recipe result is cleared for them and
they must use the block-based recipe under `crafting.harder-recipes` (one ingot becomes a block).
Blacksmiths keep the vanilla recipes. Only the pieces listed in config are gated — see
[`HarderRecipes`](../src/main/java/com/ucucraft/skills/smithing/HarderRecipes.java).
