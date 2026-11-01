package dev.eclipse.smp;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.UUID;

public final class Actives {
    private final EclipsePlugin plugin;

    public Actives(EclipsePlugin plugin) {
        this.plugin = plugin;
    }

    public void activate(Player p, Ability a) {
        UUID id = p.getUniqueId();
        PlayerData d = plugin.data().get(id);
        if (!d.equipped.contains(a)) {
            p.sendActionBar(Text.mm("<red>That ability is not equipped."));
            plugin.items().syncItems(p);
            return;
        }
        long left = plugin.cooldownLeft(id, a.id);
        if (left > 0) {
            p.sendActionBar(Text.mm(a.tag() + a.activeName + " <gray>- ready in <white>" + ((left + 999) / 1000) + "s"));
            return;
        }
        boolean used = switch (a) {
            case PHANTOM -> phantom(p);
            case CROSSCUT -> crosscut(p);
            case COUNTER -> counter(p);
            case SINGULARITY -> singularity(p);
            case CHAINREACTION -> chain(p);
            case OVERLOAD -> overload(p);
            case AFTERIMAGE -> afterimage(p);
            case REWIND -> rewind(p);
            case SOULCHAINS -> soulChains(p);
            case PHOENIX -> phoenix(p);
        };
        if (used) {
            plugin.setCooldown(id, a.id, (long) (plugin.cooldownSeconds(a) * 1000));
        }
    }

    private boolean fail(Player p, String msg) {
        p.sendActionBar(Text.mm("<red>" + msg));
        return false;
    }

    private Location center(LivingEntity e) {
        return e.getBoundingBox().getCenter().toLocation(e.getWorld());
    }

    // ------------------------------------------------------------ PHANTOM
    private boolean phantom(Player p) {
        LivingEntity t = plugin.targetEntity(p, 7, 0.6);
        if (t == null) return fail(p, "No target within 7 blocks.");
        Location tl = t.getLocation();
        Vector dir = tl.getDirection().setY(0);
        if (dir.lengthSquared() < 0.0001) dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 0.0001) dir = new Vector(0, 0, 1);
        dir.normalize();
        Location dest = null;
        for (double dist : new double[]{1.6, 1.2, 0.8}) {
            Location c = tl.clone().subtract(dir.clone().multiply(dist));
            if (plugin.isSafe(c)) {
                dest = c;
                break;
            }
        }
        if (dest == null) return fail(p, "No room behind that target.");
        dest.setDirection(dir);
        Location from = p.getLocation();
        puff(from);
        p.teleport(dest);
        puff(dest);
        Fx.sound(dest, "entity.enderman.teleport", 1f, 1.3f);
        return true;
    }

    private void puff(Location l) {
        Fx.ghostFigure(l);
        l.getWorld().spawnParticle(Particle.PORTAL, l.clone().add(0, 1, 0), 30, 0.3, 0.6, 0.3, 0.3);
    }

    // ------------------------------------------------------------ CROSSCUT
    private boolean crosscut(Player p) {
        slash(p, false);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && !p.isDead()) slash(p, true);
        }, 5L);
        return true;
    }

    private void slash(Player p, boolean flip) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Vector right = dir.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 0.0001) right = new Vector(1, 0, 0);
        right.normalize();
        Vector up = right.clone().crossProduct(dir).normalize();
        double tilt = flip ? -1 : 1;
        Particle.DustOptions opt = new Particle.DustOptions(Color.fromRGB(255, 70, 70), 1.4f);
        for (int a = -60; a <= 60; a += 5) {
            double rad = Math.toRadians(a);
            Location pt = eye.clone()
                    .add(dir.clone().multiply(Math.cos(rad) * 3.0))
                    .add(right.clone().multiply(Math.sin(rad) * 3.0))
                    .add(up.clone().multiply(Math.sin(rad) * 0.9 * tilt));
            w.spawnParticle(Particle.DUST, pt, 2, 0.03, 0.03, 0.03, 0, opt);
        }
        w.spawnParticle(Particle.SWEEP_ATTACK, eye.clone().add(dir.clone().multiply(1.8)), 1);
        Fx.sound(eye, "entity.player.attack.sweep", 1f, flip ? 1.3f : 1.0f);

        for (Entity en : w.getNearbyEntities(eye, 4.5, 4.5, 4.5)) {
            if (!plugin.validTarget(p, en)) continue;
            LivingEntity le = (LivingEntity) en;
            Vector to = le.getBoundingBox().getCenter().subtract(eye.toVector());
            double dist = to.length();
            if (dist > 4.5 || dist < 0.01) continue;
            if (to.normalize().dot(dir) < 0.5) continue;
            plugin.abilityDamage(p, le, plugin.dmg("double-slash-hit", 1.0));
        }
    }

    // ------------------------------------------------------------ COUNTER
    private boolean counter(Player p) {
        plugin.stance.put(p.getUniqueId(), System.currentTimeMillis() + 800);
        p.sendActionBar(Text.mm(Ability.COUNTER.tag() + "Counter Stance!"));
        Fx.sound(p.getLocation(), "item.armor.equip_iron", 1f, 0.9f);
        Particle.DustOptions opt = new Particle.DustOptions(Color.fromRGB(77, 166, 255), 1.3f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ >= 16 || !p.isOnline()) {
                    cancel();
                    return;
                }
                Fx.ring(p.getLocation().add(0, 1, 0), 0.9, 14, loc ->
                        p.getWorld().spawnParticle(Particle.DUST, loc, 1, 0, 0.3, 0, 0, opt));
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    // ------------------------------------------------------------ SINGULARITY
    private boolean singularity(Player p) {
        World w = p.getWorld();
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult r = w.rayTraceBlocks(eye, dir, 12, FluidCollisionMode.NEVER, true);
        final Location center;
        if (r != null) {
            center = r.getHitPosition().toLocation(w).subtract(dir.clone().multiply(0.8));
        } else {
            center = eye.clone().add(dir.clone().multiply(12));
        }
        Fx.sound(center, "block.respawn_anchor.charge", 1.2f, 0.6f);
        Particle.DustOptions core = new Particle.DustOptions(Color.fromRGB(40, 10, 70), 2.0f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ >= 40) {
                    w.spawnParticle(Particle.EXPLOSION, center, 1);
                    cancel();
                    return;
                }
                for (int i = 0; i < 3; i++) {
                    double a = t * 0.5 + i * (Math.PI * 2 / 3);
                    double rad = 2.2 - (t % 20) * 0.1;
                    Location pt = center.clone().add(Math.cos(a) * rad, Math.sin(a * 0.7) * 0.3, Math.sin(a) * rad);
                    w.spawnParticle(Particle.PORTAL, pt, 3, 0.05, 0.05, 0.05, 0.1);
                }
                w.spawnParticle(Particle.DUST, center, 4, 0.15, 0.15, 0.15, 0, core);
                w.spawnParticle(Particle.SQUID_INK, center, 2, 0.1, 0.1, 0.1, 0.01);

                for (Entity en : w.getNearbyEntities(center, 7, 7, 7)) {
                    if (!plugin.validTarget(p, en)) continue;
                    Vector pull = center.toVector().subtract(((LivingEntity) en).getBoundingBox().getCenter());
                    double dist = pull.length();
                    if (dist < 0.7) {
                        en.setVelocity(new Vector(0, 0.05, 0));
                        continue;
                    }
                    en.setVelocity(pull.normalize().multiply(Math.min(0.6, 0.25 + dist * 0.06)));
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    // ------------------------------------------------------------ CHAIN REACTION
    private boolean chain(Player p) {
        LivingEntity first = plugin.targetEntity(p, 20, 1.0);
        if (first == null) return fail(p, "No target in sight.");
        Location start = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(0.6)).subtract(0, 0.25, 0);
        Location c1 = center(first);
        bolt(start, c1);
        plugin.abilityDamage(p, first, plugin.dmg("chain-lightning-first", 2.0));
        Fx.sound(c1, "entity.lightning_bolt.impact", 0.5f, 1.8f);

        LivingEntity second = null;
        double best = 6.0 * 6.0;
        for (Entity en : first.getNearbyEntities(6, 6, 6)) {
            if (en.equals(first) || !plugin.validTarget(p, en)) continue;
            double dsq = en.getLocation().distanceSquared(first.getLocation());
            if (dsq < best) {
                best = dsq;
                second = (LivingEntity) en;
            }
        }
        if (second != null) {
            final LivingEntity sec = second;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!sec.isValid() || sec.isDead()) return;
                Location from = first.isValid() ? center(first) : c1;
                Location to = center(sec);
                bolt(from, to);
                plugin.abilityDamage(p, sec, plugin.dmg("chain-lightning-jump", 1.5));
                Fx.sound(to, "entity.lightning_bolt.impact", 0.4f, 2.0f);
            }, 3L);
        }
        return true;
    }

    private void bolt(Location a, Location b) {
        World w = a.getWorld();
        Particle.DustOptions yellow = new Particle.DustOptions(Color.fromRGB(255, 240, 120), 1.0f);
        Fx.line(a, b, 0.35, pt -> {
            pt.add((Math.random() - 0.5) * 0.3, (Math.random() - 0.5) * 0.3, (Math.random() - 0.5) * 0.3);
            w.spawnParticle(Particle.ELECTRIC_SPARK, pt, 2, 0.02, 0.02, 0.02, 0.01);
            w.spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, yellow);
        });
    }

    // ------------------------------------------------------------ OVERLOAD
    private boolean overload(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 22, 1, false, false));
        Fx.sound(p.getLocation(), "block.beacon.power_select", 1f, 0.5f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (!p.isOnline() || p.isDead()) {
                    cancel();
                    return;
                }
                t++;
                double r = 3.0 * (1 - t / 20.0) + 0.3;
                Fx.ring(p.getLocation().add(0, 1, 0), r, 16, loc ->
                        p.getWorld().spawnParticle(Particle.FLAME, loc, 1, 0, 0, 0, 0.01));
                if (t >= 20) {
                    cancel();
                    explode(p);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    private void explode(Player p) {
        World w = p.getWorld();
        Location l = p.getLocation().add(0, 1, 0);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, l, 1);
        w.spawnParticle(Particle.FLAME, l, 80, 1.5, 0.8, 1.5, 0.15);
        Fx.sound(l, "entity.generic.explode", 1.2f, 0.9f);
        for (Entity en : w.getNearbyEntities(l, 5, 5, 5)) {
            if (!plugin.validTarget(p, en)) continue;
            LivingEntity le = (LivingEntity) en;
            if (le.getLocation().distanceSquared(l) > 25) continue;
            plugin.abilityDamage(p, le, plugin.dmg("overload-explosion", 3.0));
            plugin.knock(le, p.getLocation(), 1.6, 0.55);
        }
    }

    // ------------------------------------------------------------ AFTERIMAGE
    private boolean afterimage(Player p) {
        Location base = p.getLocation();
        Vector face = base.getDirection().setY(0);
        if (face.lengthSquared() < 0.0001) face = new Vector(0, 0, 1);
        face.normalize();
        for (double ang : new double[]{-50, 50}) {
            Vector dir = face.clone().rotateAroundY(Math.toRadians(ang));
            spawnClone(p, base.clone(), dir);
        }
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, false, false));
        Fx.sound(base, "entity.illusioner.mirror_move", 1f, 1f);
        Fx.ghostFigure(base);
        return true;
    }

    private ItemStack dyed(Material m) {
        ItemStack it = new ItemStack(m);
        LeatherArmorMeta lm = (LeatherArmorMeta) it.getItemMeta();
        lm.setColor(Ability.AFTERIMAGE.bukkitColor());
        it.setItemMeta(lm);
        return it;
    }

    private boolean stepOk(Location l) {
        return l.getBlock().isPassable() && l.clone().add(0, 1, 0).getBlock().isPassable();
    }

    private void spawnClone(Player owner, Location start, Vector dir) {
        World w = start.getWorld();
        start.setDirection(dir);
        ArmorStand stand = w.spawn(start, ArmorStand.class, s -> {
            s.setArms(true);
            s.setBasePlate(false);
            s.setInvulnerable(true);
            s.setPersistent(false);
            s.setSilent(true);
            s.setGravity(false);
            s.setCanPickupItems(false);
            EntityEquipment eq = s.getEquipment();
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) head.getItemMeta();
            sm.setOwningPlayer(owner);
            head.setItemMeta(sm);
            eq.setHelmet(head);
            eq.setChestplate(dyed(Material.LEATHER_CHESTPLATE));
            eq.setLeggings(dyed(Material.LEATHER_LEGGINGS));
            eq.setBoots(dyed(Material.LEATHER_BOOTS));
        });
        plugin.clones.add(stand);
        Particle.DustOptions trail = new Particle.DustOptions(Ability.AFTERIMAGE.bukkitColor(), 1.0f);
        new BukkitRunnable() {
            int t = 0;
            Location cur = start.clone();

            @Override
            public void run() {
                if (!stand.isValid() || t++ >= 60) {
                    if (stand.isValid()) {
                        w.spawnParticle(Particle.POOF, stand.getLocation().add(0, 1, 0), 12, 0.3, 0.5, 0.3, 0.02);
                        stand.remove();
                    }
                    plugin.clones.remove(stand);
                    cancel();
                    return;
                }
                Location next = cur.clone().add(dir.clone().multiply(0.32));
                if (!stepOk(next)) {
                    Location up = next.clone().add(0, 1, 0);
                    next = stepOk(up) ? up : cur.clone();
                }
                if (next.clone().subtract(0, 0.2, 0).getBlock().isPassable()) {
                    next.subtract(0, 0.35, 0);
                }
                next.setDirection(dir);
                cur = next;
                stand.teleport(cur);
                double sw = Math.sin(t * 0.7) * 0.9;
                stand.setRightArmPose(new EulerAngle(sw, 0, 0));
                stand.setLeftArmPose(new EulerAngle(-sw, 0, 0));
                stand.setRightLegPose(new EulerAngle(-sw, 0, 0));
                stand.setLeftLegPose(new EulerAngle(sw, 0, 0));
                w.spawnParticle(Particle.DUST, cur.clone().add(0, 1, 0), 2, 0.2, 0.4, 0.2, 0, trail);
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    // ------------------------------------------------------------ REWIND
    private boolean rewind(Player p) {
        UUID id = p.getUniqueId();
        Location saved = plugin.rewindPoints.remove(id);
        if (saved != null) {
            Fx.ghostFigure(p.getLocation());
            p.teleport(saved);
            Fx.ghostFigure(saved);
            Fx.sound(saved, "entity.enderman.teleport", 1f, 0.6f);
            p.sendActionBar(Text.mm(Ability.REWIND.tag() + "Rewound!"));
            return true; // cooldown starts now
        }
        final Location point = p.getLocation().clone();
        plugin.rewindPoints.put(id, point);
        p.sendActionBar(Text.mm(Ability.REWIND.tag() + "Position saved <gray>- activate again within 4 seconds"));
        Fx.sound(point, "block.beacon.activate", 1f, 1.6f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (plugin.rewindPoints.get(id) != point) {
                    cancel(); // already used
                    return;
                }
                if (t >= 80 || !p.isOnline()) {
                    plugin.rewindPoints.remove(id);
                    plugin.setCooldown(id, Ability.REWIND.id, (long) (plugin.cooldownSeconds(Ability.REWIND) * 1000));
                    if (p.isOnline()) p.sendActionBar(Text.mm("<gray>Rewind point expired."));
                    cancel();
                    return;
                }
                Fx.ring(point.clone().add(0, 0.2, 0), 0.7, 10, loc ->
                        point.getWorld().spawnParticle(Particle.REVERSE_PORTAL, loc, 1, 0, 0.5, 0, 0.02));
                point.getWorld().spawnParticle(Particle.END_ROD, point.clone().add(0, 1, 0), 1, 0.1, 0.5, 0.1, 0.01);
                t += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
        return false; // no cooldown until used or expired
    }

    // ------------------------------------------------------------ SOUL CHAINS
    private boolean soulChains(Player p) {
        LivingEntity target = plugin.targetEntity(p, 15, 1.0);
        if (target == null) return fail(p, "No target in sight.");
        final Location anchor = target.getLocation().clone();
        final double radius = 3.0;
        Fx.sound(anchor, "block.chain.place", 1.2f, 0.7f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ >= 40 || !p.isOnline() || !target.isValid() || target.isDead()) {
                    cancel();
                    return;
                }
                World w = target.getWorld();
                if (t % 2 == 0) {
                    Location from = p.getEyeLocation().subtract(0, 0.4, 0);
                    Fx.line(from, center(target), 0.4, pt -> w.spawnParticle(Particle.SOUL, pt, 1, 0.02, 0.02, 0.02, 0));
                }
                if (t % 4 == 0) {
                    Fx.ring(anchor.clone().add(0, 0.1, 0), radius, 24, loc ->
                            w.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 1, 0, 0, 0, 0));
                }
                Location tl = target.getLocation();
                double dx = tl.getX() - anchor.getX();
                double dz = tl.getZ() - anchor.getZ();
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius) {
                    Vector back = new Vector(-dx, 0, -dz).normalize();
                    target.setVelocity(new Vector(back.getX() * 0.5, target.getVelocity().getY(), back.getZ() * 0.5));
                    if (dist > radius + 1.2) {
                        Location tp = tl.clone();
                        tp.setX(anchor.getX() + dx / dist * radius);
                        tp.setZ(anchor.getZ() + dz / dist * radius);
                        if (plugin.isSafe(tp)) target.teleport(tp);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }

    // ------------------------------------------------------------ PHOENIX
    private boolean phoenix(Player p) {
        Vector v = p.getVelocity();
        v.setY(1.5);
        p.setVelocity(v);
        plugin.fallProtection.put(p.getUniqueId(), System.currentTimeMillis() + 12000);
        World w = p.getWorld();
        Location l = p.getLocation();
        w.spawnParticle(Particle.FLAME, l, 80, 0.5, 0.2, 0.5, 0.25);
        w.spawnParticle(Particle.LAVA, l, 20, 0.5, 0.2, 0.5, 0);
        w.spawnParticle(Particle.EXPLOSION, l, 1);
        Fx.sound(l, "entity.blaze.shoot", 1f, 0.7f);
        Fx.sound(l, "entity.firework_rocket.launch", 1f, 0.8f);
        new BukkitRunnable() {
            int t = 0;

            @Override
            public void run() {
                if (t++ >= 25 || !p.isOnline()) {
                    cancel();
                    return;
                }
                Location pl = p.getLocation();
                w.spawnParticle(Particle.FLAME, pl, 10, 0.25, 0.1, 0.25, 0.03);
                w.spawnParticle(Particle.SMOKE, pl, 3, 0.2, 0.1, 0.2, 0.01);
            }
        }.runTaskTimer(plugin, 0L, 1L);
        return true;
    }
}
