# Modifiers

Weapon/tool/armor modifiers are data, not code. They live as one YAML file per modifier under
two directories in the plugin data folder:

```
plugins/UcucraftSkills/modifiers/positive/   # blacksmith rewards
plugins/UcucraftSkills/modifiers/negative/   # debuffs on regular crafters (a smith can fix)
```

A seed set is copied there on first run
([`ModifierRegistry`](../src/main/java/com/ucucraft/skills/smithing/ModifierRegistry.java)). Add,
edit or delete files freely and run `/skills reload`.

## File schema

The file name (minus `.yml`) is the modifier id. Each modifier has up to 4 "legendary levels", each
with a **proxy name** (the prefix shown on the item) and attribute deltas.

```yaml
# modifiers/positive/keen.yml
positive: true                 # optional; defaults to the directory (positive/ vs negative/)
categories: [weapon]           # weapon | tool | armor (one or more)
tiers:
  1: { name: "<gray>Sharp</gray>",     attributes: { attack_damage: 1.0 } }
  2: { name: "<white>Keen</white>",    attributes: { attack_damage: 2.0 } }
  3: { name: "<aqua>Deadly</aqua>",    attributes: { attack_damage: 3.0, attack_speed: 0.2 } }
  4: { name: "<gold>Legendary</gold>", attributes: { attack_damage: 5.0, attack_speed: 0.4 } }
```

- **name** — MiniMessage. Rendered as the item's name prefix (`Keen Iron Sword`) and in its lore.
- **attributes** — keys are vanilla attribute ids (the part after `minecraft:`), e.g.
  `attack_damage`, `attack_speed`, `armor`, `armor_toughness`, `max_health`, `knockback_resistance`,
  `movement_speed`. Values are added (`ADD_NUMBER`) and applied in the slot implied by the category
  (hand for weapon/tool, armor slots for armor). Unknown keys are logged and skipped.
- Negative modifiers use the same schema with negative amounts (place them in `negative/`).

Tiers need not start at 1 or be contiguous, but rolls only ever pick tiers a modifier actually
defines. `ModifierService` writes the modifier id/tier to the item's PDC and manages its attribute
modifiers under the `ucucraftskills` namespace, so re-rolling or fixing never stacks.

## How a tier gets chosen

[`ModifierRoller`](../src/main/java/com/ucucraft/skills/smithing/ModifierRoller.java) reads
`smithing.levels.<1..4>` (blacksmith) or `smithing.regular` (debuffs) from `config.yml`. Minigame
hits raise both the chance of getting *any* modifier and the odds of a higher tier. See
[blacksmith.md](blacksmith.md).
