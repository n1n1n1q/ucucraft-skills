# Warrior (ВОЇН)

The first melee class. It crafts nothing and has no flat attribute passive: its power is a
**stance** — a persistent combat mode, bound to the weapon in hand, that trades one axis of combat
for another. Skill expression is *when* you switch, not what you rolled. Grant it like any class:
`/skills set warrior [player]` or a `warrior` scroll.

Code: [`classes/impl/WarriorClass.java`](../src/main/java/com/ucucraft/skills/classes/impl/WarriorClass.java)
and the [`warrior`](../src/main/java/com/ucucraft/skills/warrior/) package. All numbers live under
`warrior:` (and `classes.warrior`) in `config.yml`; all strings under `warrior:` in `lang/*.yml`;
each stance is one file under `stances/`.

## Design rules

1. **A stance is a trade, not a buff.** Every stance carries at least one penalty, so none is
   strictly dominant and there is always a reason to switch.
2. **Levels unlock options, not numbers.** Magnitude grows only mildly
   (`warrior.levels.<1..4>.magnitude` — `1.0 / 1.1 / 1.2 / 1.3`, applied to both the gain and the
   cost). Growth in power comes from having more answers available.
3. **Everything is a stance property.** Attributes, combat rolls, aura and war cry all live in the
   stance file. Adding a stance is a `.yml`, not a code change — as long as it reuses an existing
   mechanic. A genuinely new war-cry *behaviour* is a new `Stance.Trait`.
4. **Every random roll is announced on two channels** (sound + particle/action bar). RNG that the
   player cannot see reads as the server cheating.

Stances stack **additively** with [modifiers](modifiers.md) on the weapon — a warrior does not
craft, so a blacksmith-forged sword is the natural partner item.

## Stances

`applies-to` is a list of weapon families (`SWORD`, `AXE`, `TRIDENT`, `MACE`, plus `MELEE` for any
of the four and `ANY` for anything). A stance is *suspended* — bonuses off, no cooldown, no switch
consumed — whenever the held item does not match, and snaps back when re-equipped.

| id | Ukrainian | Weapon | Gain | Cost | Lvl |
|---|---|---|---|---|---|
| `neutral` | Вільна | any | — | — | 1 |
| `onslaught` | Наступ | any melee | +`attack_damage`, +`attack_speed` | −`armor`, −`knockback_resistance`, **glance chance** | 1 |
| `guard` | Оборона | any melee | +`armor`, +`knockback_resistance`, **highest parry** | −`attack_damage`, −`movement_speed` | 1 |
| `skirmish` | Натиск | sword / axe | +`movement_speed`, sprint-attacks always crit | −`armor_toughness`, no bonus while sneaking | 2 |
| `phalanx` | Стіна | trident (spear) | +`entity_interaction_range`, bonus damage vs targets moving **toward** you | −`attack_speed`, −`movement_speed` | 2 |
| `cleave` | Розкол | axe | splash `cleave-percent` to `cleave-max-targets`, armor-shred stacks | −`attack_speed`, double durability cost, **glance chance** | 3 |
| `sunder` | Злам | mace | +smash radius, Slowness on hit, **highest stun** | −`movement_speed`, −`attack_speed` | 3 |
| `riposte` | Відповідь | sword | parry opens a crit window; parries refund Fervor | −`armor` | 3 |

Which level unlocks what comes from `warrior.levels.<n>.stances` in `config.yml`; a stance's own
`min-level` is only the fallback when config does not list it.

## Combat rolls: glance, parry, stun

One shared resolution model in
[`WarriorCombat`](../src/main/java/com/ucucraft/skills/warrior/WarriorCombat.java) — a single
listener at a fixed priority, never scattered across three. All three chances read from the active
stance and are clamped by `warrior.combat.max-roll-chance` (0.35), so a config typo cannot produce a
100% parry.

Resolution order:

1. **Stunned attacker** — a stunned entity's melee is cancelled outright.
2. **Parry** (warrior is the victim, melee only — `parry.blocks-projectiles: false` by default).
   Gated by `parry.cooldown-seconds` (4). Damage cancelled entirely, attacker knocked back,
   +`parry.fervor-gain`, and the **riposte window** opens (`riposte-window-ticks`). Every stance
   opens the window, but only one that defines `riposte-crit-multiplier` turns it into a crit — in
   `riposte` stance the window is also longer and the parry refunds extra Fervor.
   Parry is the payoff that makes the whole RNG layer legible: a parry visibly *does something*
   rather than silently shaving a number.
   > Deviation from the original design sketch: parry resolves **before** the attacker's own rolls,
   > because it cancels the event outright. Resolving it later would mean cleave splash and Fervor
   > gain had already happened for a hit that never landed.
3. **Glance** (warrior is the damager) — `glance-chance`. Damage × `glance-multiplier` (0.5), no
   stance bonuses, −`fervor.glance-loss`. A *reduced* hit rather than a whiff: a full miss on a
   landed swing feels like input loss, a glance reads as a bad angle.
4. **Stance bonuses** — riposte crit window, skirmish sprint crit, phalanx charge bonus, duel bonus,
   then cleave/sunder splash and shred.
5. **Fervor gain**, then the **stun** roll: `stun-chance`, gated by attacker cooldown
   (`stun.cooldown-seconds`, 8), target immunity (`stun.immunity-seconds`, 6), a Fervor floor
   (`stun.min-fervor`, 0 = off) and any stance requirement (`stun-requires-charge`,
   `stun-requires-shield`). Diminishing returns per target inside `stun.dr-window-seconds` (20):
   duration × `1.0 / 0.5 / 0.25 / 0`.

Per-stance defaults (`combat:` block in the stance file):

| stance | glance | parry | stun |
|---|---|---|---|
| `neutral` | — | 0.05 | — |
| `onslaught` | 0.15 | — | 0.05 |
| `guard` | — | 0.25 | 0.10 (shield bash) |
| `skirmish` | 0.05 | 0.10 | — |
| `phalanx` | — | 0.15 | 0.10 (on charge hits) |
| `cleave` | 0.15 | — | — |
| `sunder` | — | — | 0.30 |
| `riposte` | — | 0.20 | 0.05 |

### What a stun actually is

There is no server-side way to freeze a player's client input. The approximation, in order of
honesty:

- **Mobs**: `setAI(false)` for the duration, plus `setTarget(null)` on release. This is a real stun.
- **Players**: `SLOWNESS` amplifier 6 (movement ≈ 0) + `JUMP_BOOST` amplifier 128 (jump height 0) +
  `MINING_FATIGUE`, and their outgoing melee is cancelled for the duration. They can still look
  around, use items and fire a bow — this is documented, not pretended away.
- Duration is short by design: `stun.duration-ticks` 20–30. Anything longer in PvP is a stun-lock
  and the reason the immunity window and DR above are non-optional.
- `stun.pvp-enabled: false` ships as the default; stuns are a PvE tool until you have tuned them.
- [`StunService`](../src/main/java/com/ucucraft/skills/warrior/StunService.java) keeps a map keyed by
  `UUID` with an expiry, cleared on quit and death. The potion effects are never the source of truth.

## Switching

- **sneak + swap-hand (F)** — cycle the stances available for the held weapon.
- **sneak + right-click air, holding a melee weapon** — open the stance menu and pick directly.
  (The weapon check keeps sneaking-to-eat and sneaking-to-draw-a-bow working.)
- A switch has a `warrior.stance.warmup-ticks` (20) telegraph: rising helix in the *new* stance's
  colour + sound, **no bonuses applied** during it, cancelled if you take damage. You cannot flip
  into `guard` mid-swing for free.
- `warrior.stance.switch-cooldown-seconds` (3), halved from `cooldown-halved-level` (3), ignored
  from `cooldown-free-level` (4).
- Stance lives in the player's PDC (persists across relog), resets to `neutral` on death if
  `warrior.stance.reset-on-death`.

## Fervor (Запал)

Landing melee hits and parrying build Fervor (`per-hit`, `parry.fervor-gain`); glances
(`glance-loss`) and time out of combat drain it (`decay-per-second`), cap `max`. Above
`charge-threshold` each stance adds its **charged** attribute block and the aura brightens.
A war cry consumes it.

`warrior.fervor.enabled: false` removes the bar, the charged tier and the Fervor gates; war cries
fall back to a plain cooldown.

## War cries (level 4) — one per stance

Each stance has its own cry in its own file, so level 4 re-reads the whole stance list instead of
adding one generic shout. Trigger: `warrior.warcry.trigger` (default sneak + drop key, event
cancelled). Cost: all Fervor above `spend-down-to`, gated by `min-fervor`; the effect scales with
the amount spent (never below `min-scale`), plus `cooldown-seconds`.

| stance | Cry | Effect | Traits |
|---|---|---|---|
| `neutral` | — | none (nothing to shout about) | — |
| `onslaught` | **Рев** | Hostiles scatter + Weakness; you gain Strength. | `FLEE` |
| `guard` | **Стійте!** | Allies get Resistance + Absorption scaled off your `armor`; hostiles retarget you. | `TAUNT` |
| `skirmish` | **Ривок** | Speed III + knockback immunity for you and allies; your next hit stuns. | `STUN_NEXT_HIT` |
| `phalanx` | **Спис вперед** | Frontal cone: knockback + root on everything approaching; allies get Resistance. | `CONE`, `KNOCKBACK` |
| `cleave` | **Вихор** | Instant 360° sweep at `cleave-percent` × 2, applying shred to everything in reach. | `SWEEP` |
| `sunder` | **Землетрус** | Ground shockwave: stuns every grounded target in radius (still DR-gated). | `SHOCKWAVE` |
| `riposte` | **Виклик** | Duel-mark the nearest target: +damage against it, parry chance vs it doubled. | `DUEL` |

Potion effects (`self-effects`, `ally-effects`, `hostile-effects`) and temporary attribute buffs
(`buff-attributes`) are pure data; the traits above are the named behaviours the code implements.
"Allies" means every player in radius the warrior may not harm (see below), plus themselves.

## Stance auras

Ambient particles, defined per stance, spawned from the one warrior task at
`warrior.particles.interval-ticks` (10) — the same task that decays Fervor and re-checks the stance.

| stance | colour | shape |
|---|---|---|
| `neutral` | — | none |
| `onslaught` | `#C1121F` | tight low ring at the feet |
| `guard` | `#4A6FA5` | slow double orbit at chest height |
| `skirmish` | `#3FA34D` | trailing motes behind movement |
| `phalanx` | `#C89B3C` | forward arc in the facing direction |
| `cleave` | `#E07A1F` | wide sweeping ring |
| `sunder` | `#6A3FA0` | heavy pulses on the ground |
| `riposte` | `#DCDCDC` | thin vertical helix |

- Colour comes from `Particle.DUST` + `DustOptions`; the **shape differs per stance too**, because
  colour alone fails for colour-blind players, and the action bar name is the third channel.
- Fervor scales `count` and `size` — a charged warrior is visibly charged, which is also the tell an
  opponent needs.
- Event bursts reuse the stance colour: warm-up = rising helix, parry = white arc + shield sound,
  stun = orbiting `CRIT` above the target's head, war cry = expanding ground ring.
- Spawned **per viewer** (`player.spawnParticle`) within `warrior.particles.view-distance` (32),
  never `world.spawnParticle`, so `/skills particles off` can opt a client out. When a player opts
  out the sound cues remain — that is the accessibility fallback.

## Stance API

[`StanceService`](../src/main/java/com/ucucraft/skills/warrior/StanceService.java) is registered as a
**Bukkit service**, mirroring the hunter's `GlowService`. Other plugins can read the active stance,
and install a [`StancePolicy`](../src/main/java/com/ucucraft/skills/warrior/StancePolicy.java) to
gate which stances are legal where:

```java
StanceService stances = getServer().getServicesManager().load(StanceService.class);
stances.setPolicy((player, stance) -> !arena.contains(player) || stance.id().equals("neutral"));
```

A stance the policy denies is *suspended*, not removed — the warrior falls back to neutral and gets
their stance back when they leave the arena.

`StanceChangeEvent`, `WarriorParryEvent` and `WarriorStunEvent` are fired and cancellable — the last
two are the hook an arena or anti-cheat plugin will want. `WarriorStunEvent` also exposes a mutable
duration, so a plugin can shorten a stun instead of vetoing it.

## Implementation notes

- Attribute modifiers go through a single
  [`StanceApplier`](../src/main/java/com/ucucraft/skills/warrior/StanceApplier.java) that diffs
  desired against applied, keyed by `NamespacedKey`. They **must** be removed on switch, suspend,
  quit, death, class change and `/skills reload` — attribute modifiers persist on the player
  profile, so a leaked one is permanent and is the main bug risk in this class. Hence: the warrior
  tick strips modifiers from anyone who stopped being a warrior, join clears before it applies, and
  `/skills reload` strips against the *old* definitions before loading the new ones.
- Stun state is a map keyed by `UUID` with expiry, cleared on quit and death; never trust a potion
  effect as the source of truth for "is stunned".
- `entity_interaction_range` needs 1.20.5+, `MACE` needs 1.21 — both are guarded, and a stance whose
  weapon family or attributes the server does not have is dropped from the registry with a warning
  rather than failing the load.
- No vanilla attribute exists for sweep radius or shield raise speed — `cleave`, `riposte` and
  `guard`'s shield behaviour live in the damage listener instead.
- Cleave splash, stuns and war-cry AoE respect the world PvP flag and **Countries** relations
  (`CountriesHook#friendly`, reused from the thief), so a warrior never hits their own country or
  its allies. Splash damage is flagged re-entrant so a splashed target does not roll glance and stun
  all over again.
- Accessibility: war cries are loud, so `classes.warrior.visual-cue-level` must stay > 0 (see
  [CLAUDE.md](../CLAUDE.md)).
- No minigame — deliberately. The blacksmith has rhythm, the thief has sequence/typing; the
  warrior's input skill is switch timing under the warm-up penalty.

## Adding a stance

Drop a file in `stances/`. The file name is the id.

```yaml
id: onslaught
name: "<color:#C1121F>Наступ</color>"   # default; lang warrior.stance-names.<id> wins when present
icon: IRON_SWORD                        # menu icon
applies-to: [MELEE]                     # SWORD | AXE | TRIDENT | MACE | MELEE | ANY
min-level: 1                            # fallback; warrior.levels.<n>.stances overrides it
attributes:                             # the trade — gain and cost together
  attack_damage: 2.0
  armor: -2.0
charged:                                # added above warrior.fervor.charge-threshold
  attack_damage: 1.0
combat:
  glance-chance: 0.15
  parry-chance: 0.0
  stun-chance: 0.05
effects:                                # free-form numbers/booleans the mechanics read
  stun-requires-charge: false
particles:
  particle: DUST
  color: "#C1121F"
  shape: RING_LOW                       # RING_LOW | ORBIT | TRAIL | ARC | WIDE_RING | PULSE | HELIX | NONE
  count: 6
  size: 1.0
warcry:
  id: roar                              # lang key warrior.warcry-names.<id>
  duration-seconds: 6
  radius: 8                             # 0 = use warrior.warcry.radius
  self-effects: { strength: 1 }
  ally-effects: {}
  hostile-effects: { weakness: 0 }
  buff-attributes: {}                   # temporary attribute modifiers, stripped when it ends
  traits: [FLEE]                        # FLEE TAUNT SWEEP SHOCKWAVE CONE DUEL STUN_NEXT_HIT KNOCKBACK
```

Then add its name to `lang/*.yml` (`warrior.stance-names.<id>`, `warrior.warcry-names.<cry>`) and
list the id under the level that unlocks it in `config.yml`. No Java changes.

## Reload

`/skills reload` re-reads config, language and `stances/`; active stances are re-applied from the
reloaded definitions (all modifiers stripped and recomputed), so tuning takes effect immediately.
