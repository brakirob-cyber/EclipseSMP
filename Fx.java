package dev.eclipse.smp;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.function.Consumer;

public final class Fx {
    private Fx() {}

    public static void sound(Location l, String key, float volume, float pitch) {
        World w = l.getWorld();
        if (w != null) w.playSound(l, key, volume, pitch);
    }

    /** Calls point for each position along the line from a to b. The Location passed is a fresh copy. */
    public static void line(Location a, Location b, double step, Consumer<Location> point) {
        Vector dir = b.toVector().subtract(a.toVector());
        double len = dir.length();
        if (len < 0.01) return;
        dir.normalize().multiply(step);
        Location cur = a.clone();
        for (double d = 0; d < len; d += step) {
            point.accept(cur.clone());
            cur.add(dir);
        }
    }

    public static void ring(Location c, double radius, int points, Consumer<Location> point) {
        for (int i = 0; i < points; i++) {
            double ang = (Math.PI * 2 * i) / points;
            point.accept(c.clone().add(Math.cos(ang) * radius, 0, Math.sin(ang) * radius));
        }
    }

    /** A humanoid-shaped ghostly column of particles. */
    public static void ghostFigure(Location base) {
        World w = base.getWorld();
        if (w == null) return;
        Particle.DustOptions o = new Particle.DustOptions(Color.fromRGB(200, 235, 255), 1.3f);
        for (double y = 0; y <= 1.8; y += 0.2) {
            double r = y > 1.45 ? 0.15 : 0.3;
            w.spawnParticle(Particle.DUST, base.clone().add(0, y, 0), 3, r, 0.05, r, 0, o);
        }
        w.spawnParticle(Particle.SOUL, base.clone().add(0, 1, 0), 6, 0.3, 0.6, 0.3, 0.02);
    }
}
