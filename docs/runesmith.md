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
| Enchanting table, anvil, villagers, loot, fishing, grindstone | levels I–II; `overrides` at 0 never appear | same — none of the six paths are their tool |
| Runes | can **use** a runed item, not carve one | one enchantment per item raised to III–V, or granted outright |

Mending, Silk Touch and Infinity ship at `overrides: 0` — they exist only as runes (`raiser`, `silence`
and `host` respectively). Every one of those choices is a config line, not a design commitment.

`enchanting.banned` goes one step further: an id listed there is unobtainable by **any** means,
vanilla or rune. It's a hard 0 cap with no rune exemption (`EnchantmentGate#isBanned`), and
`RunesmithManager` itself refuses to carve, upgrade or transfer a rune whose enchantment is banned
(`runes.carve-banned`), so the rune can never even be minted in the first place. Empty by default —
add ids to disable specific enchantments outright rather than merely capping them.

## The rune

`smithing template (pattern) + trim material (tier) + item`, at a vanilla smithing table.
**Pattern picks the enchantment, material picks the level** — so the strength of a build is legible
off someone's armour across a field. `runes.materials` maps the ten trim materials to five tiers
(copper/iron → 1 … diamond/netherite → 5) and `runes.tier-levels` maps tiers to enchantment levels
(`{1:3, 2:3, 3:4, 4:4, 5:5}` by default); both are freely remappable. All 18 vanilla patterns ship
mapped in `runes/` (see the table there) — a pattern with no file would simply not be a rune, but
every one already has one, so every vanilla trim now goes through the runesmith's forge instead of
being purely decorative.

**One rune per item** (`runes.one-per-item`) — vanilla allows one trim, and that constraint is the
balance lever. Capped vanilla enchants still stack underneath; the rune is the specialisation slot.

**Template consumption is deliberate**: vanilla no longer consumes a trim template on use, but this
plugin's carve flow does (it cancels the vanilla result-slot click and rebuilds everything itself) —
otherwise level-2 **Template duplication** would have nothing to be for. Not a bug.

## Carving

Clicking the result slot at a smithing table with a registered pattern + a mapped material + an
eligible item runs a **minigame** (`runes.minigame.id`, default `sequence`, i.e. the sequence-memory
game) — the runesmith's forge:

- **lose the minigame** → the material is consumed and the item takes a vanilla **curse** (Binding or
  Vanishing, `runes.curse.enchantments`), strippable only by a level-3 runesmith
- **win the minigame** → rolls `runes.levels.<class level>` (`base-chance + hit-bonus * hitRatio`,
  same shape as `smithing.levels`): success grants the material's full level, a miss on that roll
  still succeeds but **one level lower** (a partial)

Every click on the result slot is intercepted uniformly, so shift-click can't skip the roll — each
carve gets its own.

Weapons/tools/bows (level 2+) never reach the smithing table at all — vanilla's trim slot only
accepts armour — so they run the same forge through a sneak-right-click instead (weapon in main
hand, template in off hand, material drawn from anywhere in the inventory). See the level table.

## Level abilities

| Level | Ability |
|---|---|
| 1 | **Carving** — tier 1–2 runes on **armour**, at a vanilla smithing table (template + material + armour piece, click the result). **Read the rune** — plain right-click any runed item to see its rune, modifier, upgrade count and remaining rerolls; regular players (and non-runed items) see only the vanilla tooltip. |
| 2 | **Weapons, tools and bows** (`runes.non-armor`, rendered as lore only — vanilla trims are armour-only, and vanilla's smithing table *only* accepts armour in that slot, so this doesn't go through the table at all: sneak-right-click with the weapon/tool/bow in the main hand and the template in the off hand; the material is drawn from wherever it is in your inventory). Tier 3. **Template duplication** (sneak-right-click a single template alone) at `runes.duplication.cost` plus a trim material — see below. |
| 3 | **Upgrade** — re-carve the same pattern with a better material, keeping the rune, up to `runes.max-upgrades`. **Transfer** a rune (sneak-right-click the source, target in off hand). **Strip curse** (sneak-right-click a cursed item). Tier 4. |
| 4 | Tier 5 and every `min-class-level: 4` rune — Mending, Silk Touch, Infinity sit here. **Resonance** (passive): `runes.resonance.chance` for a held/worn runed item to act one level higher. |

### Template duplication has a budget

Duplication isn't free copying: it also spends a trim material (drawn from anywhere in the
inventory, same as a non-armor carve) on top of the flat `runes.duplication.cost`, and only works
on a single template (`amount == 1` — split the stack first). The **first** time a given template
is duplicated, that material's tier picks a copy budget from `runes.duplication.max-copies`
(higher tier = more copies) and stamps it on that exact item's PDC; every later duplication of the
same template spends the same budget down by one regardless of what material is used then, and the
template is refused once its budget hits 0. The freshly-made copy is flagged and can never itself
be duplicated — no chains, so the budget is the only lever, not "copy the copy."

### Resonance's scope

Resonance is a genuine mechanic, not just a sound cue, but it's scoped to what Bukkit's public API
can actually pre-compute rather than a full NMS-accurate "every enchant acts one level higher":
Fortune's drops are recomputed with the bumped level, Sharpness/Smite/Bane-family melee damage gets
the vanilla per-level flat bonus (+0.5), Protection gets an extra ~4% reduction, and Mending (which
has no vanilla levels to bump) has its repair amount scaled by `runes.resonance.mend-bonus` instead.
Every proc still plays its sound and a trim-coloured particle even for runes outside that set, so the
passive is never silently inert — it just doesn't mechanically double-dip for enchant families Bukkit
gives no pre-effect hook for.

## Notes

- The gate is sealed on six paths, not one: enchanting table, **anvil** (two capped books must not
  combine past the cap — the one that leaks in practice), villager trades, chest loot, fishing,
  grindstone. One [`EnchantmentGate`](../src/main/java/com/ucucraft/skills/runes/EnchantmentGate.java)
  holds the clamp; `enchanting.bypass-permission` (`skills.enchanting.bypass`) skips all six.
- The rune lives in the item's PDC; the trim and the applied `Enchantment` are rendered from it, so
  an anvil can never quietly change the real level — see
  [`RuneService`](../src/main/java/com/ucucraft/skills/runes/RuneService.java).
- Vanilla smithing refuses already-trimmed armour, so `onPrepareSmithing` builds a cosmetic preview
  manually for the upgrade case; the real work happens on the result-slot click.
- Every rune maps to a **vanilla** enchantment — no custom registrations, so nothing else on the
  server needs to know runes exist.
- Accessibility: the carve minigame is visual, so it needs no compensation; the resonance proc has a
  sound, so `classes.runesmith.visual-cue-level` must stay > 0 (see [CLAUDE.md](../CLAUDE.md)).

## Reload

`/skills reload` re-reads config, language and `runes/`; the gate and rune definitions update live,
and carved items re-render from the new definitions the next time they're touched.
