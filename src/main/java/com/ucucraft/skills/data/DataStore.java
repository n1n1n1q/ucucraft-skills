package com.ucucraft.skills.data;

import java.util.UUID;

/** Persistence abstraction. Swap the implementation to move off flatfiles. */
public interface DataStore {

    PlayerProfile load(UUID uuid);

    void save(PlayerProfile profile);
}
