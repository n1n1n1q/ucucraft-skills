package com.ucucraft.skills.data;

import java.util.UUID;

/** Mutable per-player state: which class, level and XP toward the next level. */
public final class PlayerProfile {

    private final UUID uuid;
    private String classId;
    private int level = 1;
    private double xp;

    public PlayerProfile(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID uuid() {
        return uuid;
    }

    public String classId() {
        return classId;
    }

    public void classId(String classId) {
        this.classId = classId;
    }

    public boolean hasClass() {
        return classId != null && !classId.isBlank();
    }

    public int level() {
        return level;
    }

    public void level(int level) {
        this.level = level;
    }

    public double xp() {
        return xp;
    }

    public void xp(double xp) {
        this.xp = xp;
    }
}
