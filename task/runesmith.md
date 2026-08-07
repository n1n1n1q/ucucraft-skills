# Runesmith (РУНАР)

The second crafting class, and the one that owns **enchanting**. Vanilla enchanting still works for
everyone but is capped low (`enchanting.default-max-level`, default 2); a **rune** — an armour trim
carved by a runesmith — is the only way to push one enchantment past that cap, or to grant one that
is otherwise unobtainable. Grant it like any class: `/skills set runesmith [player]` or a
`runesmith` scroll.

Code: [`classes/impl/RunesmithClass.java`](../src/main/java/com/ucucraft/skills/classes/impl/RunesmithClass.java)
and the [`runes`](../src/main/java/com/ucucraft/skills/runes/) package. Everything is config: numbers
under `enchanting:` and `runes:` in `config.yml`, one file per rune in `runes/`, all strings under
`runes:` in `lang/*.yml`.

The blacksmith forges the base and rolls its [modifier](modifiers.md); the runesmith carves the rune.
A finished item needs two players.

## Cap and override

| | Regular player | Runesmith |
|---|---|---|
| Enchanting table, anvil, villagers, loot | levels I–II; `overrides` at 0 never appear | same — the table is not their tool |
| Runes | can **use** a runed item, not carve one | one enchantment per item raised to III–V, or granted outright |

Mending, Silk Touch and Infinity ship at `overrides: 0` — they exist only as runes. Every one of
those choices is a config line, not a design commitment.

## The rune

`smithing template (pattern) + trim material (tier) + item`, at a vanilla smithing table.
**Pattern picks the enchantment, material picks the level** — so the strength of a build is legible
off someone's armour across a field. `runes.materials` maps the ten trim materials to five tiers
(copper/iron → 1 … diamond/netherite → 5) and `runes.tier-levels` maps tiers to enchantment levels;
both are freely remappable. The 18 vanilla patterns each get a file in `runes/`; a pattern with no
file is simply not a rune.

**One rune per item** (`runes.one-per-item`) — vanilla allows one trim, and that constraint is the
balance lever. Capped vanilla enchants still stack underneath; the rune is the specialisation slot.

## Carving

Placing the inputs runs a **minigame** (`runes.minigame.id`, default `sequence-memory`) — the
runesmith's forge:

- **win** → the rune applies at the material's level
- **partial** → one level lower
- **fail** → the material is consumed and the item takes a vanilla **curse** (Binding or Vanishing),
  strippable only by a level-3 runesmith

Odds come from `runes.levels.<1..4>`, same shape as `smithing.levels`. Shift-click is blocked so each
carve gets its own roll.

## Level abilities

| Level | Ability |
|---|---|
| 1 | **Carving** — tier 1–2 runes on **armour**. **Read the rune** — right-click any item to see its rune, modifier, upgrade count and remaining rerolls; regular players see only the vanilla tooltip. |
| 2 | **Weapons and tools** (`runes.non-armor`, rendered as lore/model since vanilla trims are armour-only). Tier 3. **Template duplication** at `runes.duplication.cost`. |
| 3 | **Upgrade** — re-carve the same pattern with a better material, keeping the rune, up to `runes.max-upgrades`. **Transfer** a rune between items. **Strip curse**. Tier 4. |
| 4 | Tier 5 and every `min-class-level: 4` rune — Mending, Silk Touch, Infinity sit here. **Resonance** (passive): `runes.resonance.chance` for a held runed item to act one level higher, with a sound and a particle in the trim's colour. |

## Notes

- The gate is sealed on six paths, not one: enchanting table, **anvil** (two capped books must not
  combine past the cap — the one that leaks in practice), villager trades, chest loot, fishing,
  grindstone. One `EnchantmentGate` holds the clamp; `skills.enchanting.bypass` skips all six.
- The rune lives in the item's PDC; the trim and the applied `Enchantment` are rendered from it, so
  an anvil can never quietly change the real level.
- Vanilla smithing refuses already-trimmed armour, so upgrades cancel `PrepareSmithingEvent` and
  build the result via `ArmorMeta#setTrim`.
- Every rune maps to a **vanilla** enchantment — no custom registrations, so nothing else on the
  server needs to know runes exist.
- Accessibility: the carve minigame is visual, so it needs no compensation; the resonance proc has a
  sound, so `classes.runesmith.visual-cue-level` must stay > 0 (see [CLAUDE.md](../CLAUDE.md)).

## Reload

`/skills reload` re-reads config, language and `runes/`; the gate and rune definitions update live,
and carved items re-render from the new definitions.