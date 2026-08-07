# Implementation prompt — enchantment gate + rune system

Paste this into Claude Code with the `ucucraft-skills` repo open.

## Goal

Vanilla enchanting stays available to everyone but is **capped low** (default: level 2). Everything
above the cap, and everything on the blocked list, exists only as a **rune**: an armour trim carved
onto an item by the **runesmith** class, which raises one chosen enchantment past the cap or grants
one that is otherwise unobtainable.

Two subsystems, independently switchable:

- `enchanting:` — the **gate**. Clamps every path an enchantment can enter the world through.
- `runes:` — the **override**. The only legal way past the gate.

Either can be disabled without breaking the other (`enabled: false`): gate-only = a hard-mode server;
runes-only = vanilla enchanting plus a cosmetic-visible upgrade path.

## Non-goals

- No custom `Enchantment` registrations. Every rune maps to a **vanilla** `Enchantment`, so no other
  plugin, mob, datapack or client needs to know runes exist.
- No new currency. Runes ride on smithing templates and trim materials, which are already scarce.
- No changes to the existing modifier system. Blacksmith modifiers and runes coexist on one item.

## Hard rule: policy in YAML, mechanism in Java

**No enchantment id, level number, material, chance, pattern or mapping may appear as a literal in
Java.** Java holds the mechanism; `config.yml` and `runes/*.yml` hold every policy decision. Concretely:

- Adding a rune = dropping a file in `runes/`. No code change, no restart beyond `/skills reload`.
- Changing which enchantments are capped, and at what level, is one config block.
- Remapping any trim pattern to a different enchantment is one line.
- Every chance, cost, cooldown and level threshold is a config key with a documented default.
- If a value would be a constant in Java, it belongs in config. The only Java constants allowed are
  `NamespacedKey` names and config **key paths**.

Follow the existing conventions: namespaced config values, all strings in `lang/*.yml`, soft
dependencies declared in `plugin.yml`, services registered on the Bukkit `ServicesManager`,
dual-channel (sound + visual) feedback per `CLAUDE.md`.

## Packages and types

```
runes/
  EnchantmentGate.java     // single clamp authority: clamp(ItemStack), clamp(Map<Enchantment,Integer>)
  GateConfig.java          // parsed enchanting: block, reloadable
  GateListener.java        // all six sources, delegating to EnchantmentGate
  Rune.java                // record: id, pattern, enchantment, level rules, applies-to, min level
  RuneRegistry.java        // loads runes/*.yml, hot-reloadable, pattern->Rune and enchant->Rune
  RuneKeys.java            // PDC NamespacedKeys: pattern, material, level, upgrades
  RuneApplier.java         // PDC -> trim + enchantment; reconciles on join and inventory click
  RuneSmithingListener.java// PrepareSmithingEvent: cancel vanilla, build result, run minigame
  RuneService.java         // Bukkit service, public API (see below)
  RunePolicy.java          // pluggable allow/deny, like GlowPolicy / StancePolicy
classes/impl/RunesmithClass.java
```

`RuneService` mirrors `GlowService`: `getRune(ItemStack)`, `canApply(Player, Rune, ItemStack)`,
`apply(...)`, `strip(...)`, `setPolicy(RunePolicy)`. Fire cancellable `RuneApplyEvent` and
`RuneUpgradeEvent`.

## Config schema

Every key below must be read at runtime and re-read on `/skills reload`. Defaults shown.

```yaml
enchanting:
  enabled: true
  default-max-level: 2          # applies to every enchantment without an override
  on-exceeded: downgrade        # downgrade | remove
  bypass-permission: skills.enchanting.bypass
  overrides:                    # per-enchantment cap; 0 = unobtainable without a rune
    MENDING: 0
    SILK_TOUCH: 0
    INFINITY: 0
    FORTUNE: 1
    PROTECTION: 2
    SHARPNESS: 2
  sources:                      # every path an enchantment can enter through
    enchanting-table:
      clamp: true
      hide-blocked-offers: true # blocked enchants never appear in the offer preview
    anvil:
      clamp: true               # two capped books MUST NOT combine past the cap
      allow-rune-items: true    # runed items may still be repaired/renamed
    villager-trades:
      clamp: true
      reroll-blocked: true      # librarian offering a 0-cap book is re-rolled, not deleted
    loot:
      clamp: true
    fishing:
      clamp: true
    grindstone:
      clamp: true
      strips-runes: false       # a rune should cost a runesmith to remove
  migrate:
    on-join: false              # clamp legacy items in player inventories on join
    on-container-open: false

runes:
  enabled: true
  mode: absolute                # absolute: rune level replaces the enchant level
                                # additive: rune tier adds to whatever the item already has
  one-per-item: true            # the rune slot is the specialisation choice
  max-upgrades: 3               # per item, tracked in PDC
  allow-downgrade: false        # re-carving with a weaker material
  materials:                    # trim material -> tier; freely remappable
    COPPER: 1
    IRON: 1
    LAPIS: 2
    QUARTZ: 2
    GOLD: 3
    REDSTONE: 3
    AMETHYST: 4
    EMERALD: 4
    DIAMOND: 5
    NETHERITE: 5
  tier-levels:                  # tier -> enchantment level (absolute) or bonus (additive)
    1: 3
    2: 3
    3: 4
    4: 4
    5: 5
  minigame:
    enabled: true
    id: sequence-memory         # any id from the existing minigame framework
    on-partial: level-minus-one # level-minus-one | fail | success
    on-fail: curse              # curse | consume-only | nothing
    curses: [BINDING_CURSE, VANISHING_CURSE]
  cost:
    xp-levels: 5
    consume-template: false     # false = template survives, matching vanilla trims
    extra: {}                   # e.g. { LAPIS_LAZULI: 8 }
  non-armor:
    enabled: true
    render: lore                # lore | model | both
    custom-model-data-base: 7100
  duplication:
    enabled: true
    cost: { DIAMOND: 2 }        # vanilla is 7
  resonance:                    # level 4 passive
    chance: 0.10
    min-class-level: 4
  levels:                       # runesmith class levels, same shape as smithing.levels
    1: { max-tier: 2, armor-only: true,  win: 0.60, partial: 0.30, fail: 0.10, can-upgrade: false, can-transfer: false }
    2: { max-tier: 3, armor-only: false, win: 0.70, partial: 0.25, fail: 0.05, can-upgrade: false, can-transfer: false }
    3: { max-tier: 4, armor-only: false, win: 0.80, partial: 0.17, fail: 0.03, can-upgrade: true,  can-transfer: true }
    4: { max-tier: 5, armor-only: false, win: 0.90, partial: 0.09, fail: 0.01, can-upgrade: true,  can-transfer: true }

classes:
  runesmith:
    visual-cue-level: 1
```

## Rune file schema (`runes/<id>.yml`)

One file per rune. Unknown keys are a load warning, not a crash; a malformed file disables that rune
only.

```yaml
id: raiser
pattern: RAISER               # vanilla TrimPattern key
name: "Руна Воскресіння"      # falls back to lang if absent
enchantment: MENDING
applies-to: [ARMOR, SWORD, AXE, PICKAXE, SHOVEL, HOE, BOW, CROSSBOW, TRIDENT, SHIELD]
min-class-level: 4            # overrides the tier gate in runes.levels
max-level: 1                  # cap for this enchantment regardless of material tier
grants: true                  # true = works even when enchanting.overrides sets it to 0
tier-levels: {}               # optional per-rune override of runes.tier-levels
cost:                         # optional per-rune override of runes.cost
  xp-levels: 10
lore: "Речі, що пам'ятають свого носія."
```

Ship the 18 vanilla patterns as files; a pattern with no file is simply not a rune.

## Listeners and resolution order

`EnchantmentGate` is the only place that knows the cap. All six call into it:

1. `PrepareItemEnchantEvent` + `EnchantItemEvent`
2. `PrepareAnvilEvent` — **the one that leaks in practice**; two level-2 books must not make a 3
3. `VillagerAcquireTradeEvent` + `PlayerTradeEvent`
4. `LootGenerateEvent`
5. `PlayerFishEvent`
6. `PrepareGrindstoneEvent` + `GrindstoneEvent`

Clamp order inside the gate: read PDC rune → if present, that one enchantment is exempt up to its
rune level → clamp everything else to `overrides[e] ?: default-max-level` → apply `on-exceeded`.

## Edge cases that must be handled

- **Vanilla smithing rejects already-trimmed armour.** Upgrade and re-carve cannot use the vanilla
  recipe: cancel `PrepareSmithingEvent` and build the result with `ArmorMeta#setTrim` yourself.
- **PDC is the source of truth**, never the applied `Enchantment` level — an anvil or another plugin
  can change the latter behind you. `RuneApplier` reconciles PDC → enchantments on join and on
  inventory click.
- A runed item passing through a **grindstone**, **anvil rename**, or **crafting-table repair** must
  keep its PDC.
- `one-per-item: true` must be enforced on **transfer** as well as apply.
- Shift-click carving is blocked, matching the blacksmith.
- `bypass-permission` is checked in all six listeners, not just the table.
- Config that is invalid (unknown enchantment, unknown material, tier with no level) logs a clear
  warning naming the key path and falls back to the default; it never prevents plugin load.

## Acceptance criteria

- [ ] `enchanting.enabled: false` leaves vanilla enchanting completely untouched.
- [ ] `runes.enabled: false` leaves the gate working and the runesmith class with no carve ability.
- [ ] Changing `default-max-level` to 5 and reloading makes the server behave like vanilla.
- [ ] Remapping `sentry` to a different enchantment in `runes/sentry.yml` takes effect on reload,
      and already-carved items re-render to the new enchantment.
- [ ] Two capped enchanted books cannot be combined past the cap in an anvil.
- [ ] A librarian never offers a book above the cap, in a fresh world and after `/reload`.
- [ ] A carved item keeps its rune through grindstone, anvil rename, repair and relog.
- [ ] No enchantment id or level literal appears anywhere in `src/main/java`.
- [ ] `/skills reload` applies every key above without a restart.