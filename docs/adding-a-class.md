# Adding a class

A class is a `SkillClass` implementation plus its config and language entries.

## 1. Implement `SkillClass`
Put it in `com.ucucraft.skills.classes.impl`. Read every number from `ConfigManager` — don't
hardcode. Use `ExampleClass` as the template.

```java
public final class MageClass implements SkillClass {
    public static final String ID = "mage";
    private final ConfigManager config;
    public MageClass(ConfigManager config) { this.config = config; }

    public String id() { return ID; }
    public String displayNameKey() { return "class-names." + ID; }

    public int xpForLevel(int level) {
        return (int) Math.round(config.baseXp() * Math.pow(config.multiplier(), level - 1));
    }

    public void applyBonuses(Player player, int level) {
        // Apply attributes/effects using Paper APIs. Scale by level and config values.
    }
    public void clearBonuses(Player player) {
        // Undo whatever applyBonuses set.
    }
}
```

`applyBonuses` is called on assign, on level-up, and on join. `clearBonuses` on unassign. Make
them idempotent (set absolute values, don't stack).

## 2. Register it
In `SkillsPlugin#onEnable`:
```java
classRegistry.register(new MageClass(configManager));
```

## 3. Config
Add a block under `classes:` in `config.yml` for its tunables, e.g.:
```yaml
classes:
  mage:
    spell-power-per-level: 0.5
    visual-cue-level: 1   # accessibility bonus, see CLAUDE.md
```

## 4. Language
Add the display name to **every** `lang/*.yml`:
```yaml
class-names:
  mage: "<blue>Mage</blue>"
```

## 5. Grant it
- Give a scroll: `/skills scroll mage`
- Assign directly: `/skills set mage [player]`
- Or set `scroll.grants: mage` in config to change what the default scroll grants.
