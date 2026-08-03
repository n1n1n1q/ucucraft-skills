package com.ucucraft.skills.classes;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Holds every registered class, keyed by id. */
public final class SkillClassRegistry {

    private final Map<String, SkillClass> classes = new LinkedHashMap<>();

    public void register(SkillClass skillClass) {
        classes.put(skillClass.id().toLowerCase(Locale.ROOT), skillClass);
    }

    public SkillClass get(String id) {
        return id == null ? null : classes.get(id.toLowerCase(Locale.ROOT));
    }

    public boolean exists(String id) {
        return get(id) != null;
    }

    public Collection<SkillClass> all() {
        return classes.values();
    }
}
