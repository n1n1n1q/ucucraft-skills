# Architecture

## Startup
`SkillsPlugin#onEnable` is the only wiring point:

1. `saveDefaultConfig()` writes `config.yml` on first run.
2. `ConfigManager` wraps that config; `LangManager` loads `lang/<locale>.yml` (falling back to
   `en`) and copies the bundled defaults out on first run.
3. `YamlDataStore` (a `DataStore`) is created.
4. `SkillClassRegistry` is populated (currently just `ExampleClass`).
5. `ClassManager`, `ScrollItem`, `MinigameManager` are built and minigames registered.
6. Listeners (`ClassManager`, `MinigameManager`, `ScrollListener`) and the `/skills` command
   are registered.

`onDisable` calls `ClassManager#saveAll`.

## Data flow
```
Player joins ─▶ ClassManager loads PlayerProfile (via DataStore) ─▶ applies class bonuses
/skills, scroll, minigame ─▶ managers mutate PlayerProfile ─▶ DataStore.save
Player quits ─▶ ClassManager saves + evicts from cache
```
`PlayerProfile` is the single source of truth for a player's class/level/xp. It is cached in
`ClassManager` while the player is online and persisted through `DataStore`.

## Boundaries
- Managers expose small public APIs and never touch each other's fields.
- All persistence is behind `DataStore`. Swapping storage = new `DataStore` impl + one line in
  `SkillsPlugin`.
- All strings come from `LangManager`; all numbers from `ConfigManager`.

## Message rendering
`LangManager` uses Adventure MiniMessage. `msg(key, placeholders)` prepends the config prefix and
is what you send to players; `component(key, …)` is the prefix-less variant for action bars and
item names; `parse(mm, …)` renders an arbitrary MiniMessage string. Placeholders are passed as a
`Map<String,String>` and resolved as MiniMessage tags (`<name>`).
