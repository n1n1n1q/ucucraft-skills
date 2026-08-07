package com.ucucraft.skills.thief;

import com.ucucraft.countries.api.CountriesAPI;
import com.ucucraft.countries.api.CountriesProvider;
import com.ucucraft.countries.api.CountryView;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Soft dependency on the Countries plugin. Compiled against its API jar but resolved at runtime;
 * every call degrades gracefully (returns {@link #UNAVAILABLE}) when the plugin is absent.
 */
public final class CountriesHook {

    /** Returned when the Countries plugin is not present, so its rule cannot be evaluated. */
    public static final int UNAVAILABLE = -1;

    public boolean available() {
        try {
            return Bukkit.getPluginManager().getPlugin("Countries") != null && CountriesProvider.get() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Number of the owning country's members currently standing in the chunk of {@code location}.
     * {@link #UNAVAILABLE} when Countries is absent; 0 for wilderness or an empty claim.
     */
    public int membersInChunk(Location location) {
        try {
            if (!available()) {
                return UNAVAILABLE;
            }
            CountriesAPI api = CountriesProvider.get();
            CountryView country = api.getCountryAt(location).orElse(null);
            if (country == null) {
                return 0;
            }
            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;
            int count = 0;
            for (Player online : Bukkit.getOnlinePlayers()) {
                Location loc = online.getLocation();
                if (loc.getWorld() == location.getWorld()
                        && (loc.getBlockX() >> 4) == chunkX && (loc.getBlockZ() >> 4) == chunkZ
                        && country.members().contains(online.getUniqueId())) {
                    count++;
                }
            }
            return count;
        } catch (Throwable t) {
            return UNAVAILABLE;
        }
    }
}
