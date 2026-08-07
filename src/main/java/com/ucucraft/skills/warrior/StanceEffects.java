package com.ucucraft.skills.warrior;

import com.ucucraft.skills.config.ConfigManager;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Every visual and audible cue the warrior produces: the ambient stance aura plus the event bursts
 * (warm-up, parry, glance, stun, war cry). Particles are spawned per viewer so a client can opt out
 * with {@code /skills particles off}; the sound cues stay, which is the accessibility fallback.
 * Tunables: {@code warrior.particles} and {@code warrior.sounds}.
 */
public final class StanceEffects {

    private final Plugin plugin;
    private final ConfigManager config;
    private final Set<UUID> muted = new HashSet<>();

    public StanceEffects(Plugin plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
    }

    /** Turns this viewer's warrior particles on or off; returns the new state. */
    public boolean toggle(Player player) {
        if (!muted.remove(player.getUniqueId())) {
            muted.add(player.getUniqueId());
            return false;
        }
        return true;
    }

    public boolean shown(Player player) {
        return !muted.contains(player.getUniqueId());
    }

    /** The ambient aura, brightened by Fervor. Called once per warrior tick. */
    public void aura(Player owner, Stance stance, double fervorRatio, int tick) {
        Stance.Aura aura = stance.aura();
        if (!enabled() || aura.shape() == Stance.Shape.NONE || aura.count() <= 0) {
            return;
        }
        boolean scale = config.raw().getBoolean("warrior.particles.scale-with-fervor", true);
        double boost = scale ? 1 + fervorRatio : 1;
        int count = (int) Math.max(1, Math.round(aura.count() * boost));
        render(owner.getLocation(), shape(owner, aura.shape(), count, tick),
                aura.particle(), aura.color(), aura.size() * boost, 1);
    }

    /** Rising helix in the new stance's colour while the switch warms up. */
    public void warmup(Player player, Stance target, double progress) {
        Location origin = player.getLocation();
        double angle = progress * Math.PI * 6;
        List<Vector> points = List.of(
                new Vector(Math.cos(angle) * 0.6, progress * 2.2, Math.sin(angle) * 0.6),
                new Vector(-Math.cos(angle) * 0.6, progress * 2.2, -Math.sin(angle) * 0.6));
        render(origin, points, target.aura().particle(), target.aura().color(), 1.2, 1);
        sound(origin, "warmup", 1f + (float) progress);
    }

    public void switched(Player player, Stance stance) {
        render(player.getLocation(), ring(1.0, 1.0, 16, 0), stance.aura().particle(), stance.aura().color(), 1.4, 1);
        sound(player.getLocation(), "switch", 1.2f);
    }

    /** White arc plus a shield-like sound: the parry has to visibly do something. */
    public void parry(Player player) {
        render(player.getLocation(), arc(player, 1.3, 1.1, 12), Particle.DUST, Color.WHITE, 1.3, 1);
        sound(player.getLocation(), "parry", 1.4f);
    }

    /** Dull scrape plus a grey puff: a glance reads as a bad angle, not as input loss. */
    public void glance(Player player, Entity victim) {
        Location at = victim.getLocation().add(0, 1, 0);
        render(at, List.of(new Vector()), Particle.DUST, Color.fromRGB(0x8A8A8A), 1.4, 8);
        sound(at, "glance", 0.6f);
    }

    /** Orbiting crit marks above a stunned target for the whole duration. */
    public void stun(LivingEntity target, int durationTicks) {
        sound(target.getLocation(), "stun", 1f);
        if (!enabled()) {
            return;
        }
        new BukkitRunnable() {
            private int elapsed;

            @Override
            public void run() {
                if (!target.isValid() || elapsed >= durationTicks) {
                    cancel();
                    return;
                }
                Location head = target.getLocation().add(0, target.getHeight() + 0.4, 0);
                render(head, ring(0.4, 0, 3, elapsed * 0.4), Particle.CRIT, Color.WHITE, 1.0, 1);
                elapsed += 4;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    /** Expanding ground ring in the stance colour. */
    public void cry(Player player, Stance stance, double radius) {
        sound(player.getLocation(), "warcry", 1f);
        if (!enabled()) {
            return;
        }
        Location origin = player.getLocation();
        for (int step = 0; step < 5; step++) {
            double r = radius * (step + 1) / 5.0;
            plugin.getServer().getScheduler().runTaskLater(plugin,
                    () -> render(origin, ring(r, 0.1, (int) Math.max(8, r * 6), 0),
                            stance.aura().particle(), stance.aura().color(), 1.5, 1),
                    step * 2L);
        }
    }

    public void sound(Location where, String id, float pitch) {
        String key = config.raw().getString("warrior.sounds." + id, "");
        if (key == null || key.isBlank() || where.getWorld() == null) {
            return;
        }
        float volume = (float) config.raw().getDouble("warrior.sounds.volume", 0.8);
        where.getWorld().playSound(Sound.sound(Key.key(key), Sound.Source.PLAYER, volume, pitch), where);
    }

    private boolean enabled() {
        return config.raw().getBoolean("warrior.particles.enabled", true);
    }

    private void render(Location origin, List<Vector> points, Particle particle, Color color,
                        double size, int perPoint) {
        if (!enabled() || origin.getWorld() == null || points.isEmpty()) {
            return;
        }
        double view = config.raw().getDouble("warrior.particles.view-distance", 32);
        Particle.DustOptions dust = particle == Particle.DUST
                ? new Particle.DustOptions(color, (float) Math.max(0.1, size)) : null;
        for (Player viewer : origin.getWorld().getPlayers()) {
            if (muted.contains(viewer.getUniqueId())
                    || viewer.getLocation().distanceSquared(origin) > view * view) {
                continue;
            }
            for (Vector point : points) {
                Location at = origin.clone().add(point);
                if (dust == null) {
                    viewer.spawnParticle(particle, at, perPoint, 0, 0, 0, 0);
                } else {
                    viewer.spawnParticle(particle, at, perPoint, 0, 0, 0, 0, dust);
                }
            }
        }
    }

    private List<Vector> shape(Player owner, Stance.Shape shape, int count, int tick) {
        double spin = tick * 0.35;
        return switch (shape) {
            case RING_LOW -> ring(0.6, 0.1, count, spin);
            case ORBIT -> orbit(count, spin);
            case TRAIL -> trail(owner, count);
            case ARC -> arc(owner, 1.2, 1.0, count);
            case WIDE_RING -> ring(1.6, 0.1, count, spin * 2);
            case PULSE -> pulse(count, tick);
            case HELIX -> helix(count, spin);
            case NONE -> List.of();
        };
    }

    private List<Vector> ring(double radius, double y, int count, double spin) {
        List<Vector> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = spin + i * 2 * Math.PI / count;
            points.add(new Vector(Math.cos(angle) * radius, y, Math.sin(angle) * radius));
        }
        return points;
    }

    private List<Vector> orbit(int count, double spin) {
        List<Vector> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = spin * 0.5 + i * 2 * Math.PI / count;
            points.add(new Vector(Math.cos(angle) * 0.8, 1.1 + Math.sin(angle) * 0.15, Math.sin(angle) * 0.8));
        }
        return points;
    }

    private List<Vector> trail(Player owner, int count) {
        Vector back = owner.getVelocity().setY(0);
        if (back.lengthSquared() < 0.001) {
            back = owner.getLocation().getDirection().setY(0);
        }
        if (back.lengthSquared() < 0.001) {
            return List.of();
        }
        back = back.normalize().multiply(-1);
        List<Vector> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(back.clone().multiply(0.3 + i * 0.25).setY(0.3));
        }
        return points;
    }

    private List<Vector> arc(Player owner, double radius, double y, int count) {
        Vector facing = owner.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() < 0.001) {
            return List.of();
        }
        double base = Math.atan2(facing.getZ(), facing.getX());
        List<Vector> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = base - Math.PI / 3 + i * (2 * Math.PI / 3) / Math.max(1, count - 1);
            points.add(new Vector(Math.cos(angle) * radius, y, Math.sin(angle) * radius));
        }
        return points;
    }

    private List<Vector> pulse(int count, int tick) {
        List<Vector> points = new ArrayList<>(count);
        double radius = 0.4 + 0.8 * Math.abs(Math.sin(tick * 0.25));
        for (int i = 0; i < count; i++) {
            double angle = i * 2 * Math.PI / count;
            points.add(new Vector(Math.cos(angle) * radius, 0.05, Math.sin(angle) * radius));
        }
        return points;
    }

    private List<Vector> helix(int count, double spin) {
        List<Vector> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double angle = spin + i * 0.8;
            points.add(new Vector(Math.cos(angle) * 0.45, i * (2.0 / count), Math.sin(angle) * 0.45));
        }
        return points;
    }
}
