# UcucraftSkills — agent guide

Paper **26.2** plugin, Java **25**, Gradle (Kotlin DSL). Adds player classes/specialties
("skills") with level progression, a scroll item that grants a class, per-class bonuses, and a
minigame module that revamps regular mechanics.

This file is the map for anyone (human or AI) extending the plugin. Read it before touching code.

## Golden rules
1. Reuse Paper/Adventure and the JDK. Don't reinvent what a standard library already does.
2. Keep it simple. Small classes, small public surfaces, no speculative abstraction.
3. Few comments — only brief javadoc on public interfaces.
4. **Every user-facing string lives in `lang/*.yml`.** Never hardcode a message in Java.
5. **Every tunable lives in `config.yml`.** Classes are code-registered but read numbers from config.
6. The message prefix is `config.yml -> prefix` and is applied by `LangManager`. Never hardcode it.

## Build & run
```
# JDK 25 required. On this machine:
export JAVA_HOME="C:\Users\basys\scoop\apps\openjdk25\current"   # PowerShell: $env:JAVA_HOME=...
gradle build            # -> build/libs/ucucraft-skills-<version>.jar
```
Adjust the Paper API build in `build.gradle.kts` (`paperApiVersion`) to your server.

> Note: the Gradle **wrapper** (`gradlew`/`gradlew.bat`) fails here because the repo path
> contains non-ASCII characters (`Документы`) that cmd.exe/Git Bash mangle in the wrapper script.
> Use a system-installed `gradle`, or move the repo to an ASCII path. Compilation is unaffected.

## Architecture
`SkillsPlugin#onEnable` constructs and wires every module. Modules talk only through public
methods; none reaches into another's internals.

| Package | Responsibility |
|---|---|
| `config` | `ConfigManager` — typed access to `config.yml`. |
| `lang` | `LangManager` — loads `lang/<locale>.yml`, renders MiniMessage, applies prefix. |
| `data` | `DataStore` (interface) + `YamlDataStore`; `PlayerProfile` holds class/level/xp. |
| `classes` | `SkillClass`, `SkillClassRegistry`, `ClassManager` (assign/xp/level/bonuses), `BonusType`. |
| `item` | `ScrollItem` (builds/identifies the scroll via PDC), `ScrollListener`. |
| `minigame` | `Minigame`, `MinigameSession`, `MinigameResult`, `MinigameManager` + `games/`. |
| `smithing` | Blacksmith crafting: `GearTier`/`GearType`, data-driven `Modifier`/`ModifierRegistry`/`ModifierService`/`ModifierRoller`, `HarderRecipes`, `RoseGold` (soft dep), `SmithingManager`. |
| `command` | `SkillsCommand` — the single `/skills` entry point. |

Persistence goes through `DataStore`; only `YamlDataStore` exists today. To add SQLite/MySQL,
implement `DataStore` and swap the instance in `SkillsPlugin` — nothing else changes.

## Accessibility principle ("deaf players nerfed, skills compensate")
Some mechanics use **sound cues** — today the rhythm minigame plays a beat sound. Hearing players
get a natural edge. Classes expose an accessibility bonus (`BonusType.VISUAL_CUE`, config
`classes.<id>.visual-cue-level`) that adds an on-screen equivalent so a non-hearing player can
compensate through their class. `ClassManager#accessibilityCueLevel(player)` is the hook;
`RhythmMinigame` reads it to show a visual beat. Keep new sound-based mechanics paired with a
visual-cue path gated on this bonus.

## Extending
- Add a class → `docs/adding-a-class.md`
- Add a minigame → `docs/adding-a-minigame.md`
- Minigame catalogue & design → `docs/minigames.md`
- Overall layout & data flow → `docs/architecture.md`
- Blacksmith class & crafting gates → `docs/blacksmith.md`
- Weapon/tool/armor modifiers (data-driven) → `docs/modifiers.md`

## Deliberately not done yet (next milestones)
- Making the scroll obtainable (loot/crafting/villager trade).
- Full class roster + balancing; special items and abilities per class.
- Wiring minigames in front of real vanilla mechanics (mining/fishing/combat) — see
  `docs/minigames.md` for the intended hook.
- SQLite/MySQL storage, GUI class-selection menu.
- `plugin.yml` still uses the classic command block; migrating to `paper-plugin.yml` + Brigadier
  is optional and can come later.
