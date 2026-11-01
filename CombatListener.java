package dev.eclipse.smp;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CombatListener implements Listener {
    private final EclipsePlugin plugin;
    private final Map<UUID, Combo> combos = new HashMap<>();

    private static final class Combo {
        UUID target;
        int count;
        long last;
    }

    public CombatListener(EclipsePlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------ Phoenix fall protection
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(e.getEntity() instanceof Player p)) return;
        Long until = plugin.fallProtection.get(p.getUniqueId());
        if (until == null) return;
        plugin.fallProtection.remove(p.getUniqueId());
        if (System.currentTimeMillis() <= until) {
            e.setCancelled(true);
            Location l = p.getLocation();
            p.getWorld().spawnParticle(Particle.FLAME, l, 30, 0.6, 0.1, 0.6, 0.05);
            Fx.sound(l, "block.fire.extinguish", 0.6f, 1.2f);
        }
    }

    // ------------------------------------------------ Melee / entity damage
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Entity source = resolve(e.getDamager());

        if (e.getEntity() instanceof Player victim) {
            if (defend(e, victim, source)) return;
        }
        if (e.getDamager() instanceof Player atk && !plugin.isInternalDamage()) {
            offense(e, atk);
        }
    }

    private Entity resolve(Entity damager) {
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Entity shooter) {
            return shooter;
        }
        return damager;
    }

    /** Returns true if the hit was cancelled. */
    private boolean defend(EntityDamageByEntityEvent e, Player victim, Entity source) {
        UUID id = victim.getUniqueId();

        // Counter Stance
        Long until = plugin.stance.get(id);
        if (until != null) {
            plugin.stance.remove(id);
            if (System.currentTimeMillis() <= until) {
                e.setCancelled(true);
                Location l = victim.getLocation().add(0, 1, 0);
                victim.getWorld().spawnParticle(Particle.CRIT, l, 25, 0.5, 0.5, 0.5, 0.3);
                Fx.sound(l, "item.shield.block", 1f, 1f);
                victim.sendActionBar(Text.mm(Ability.COUNTER.tag() + "Blocked!"));
                if (source instanceof LivingEntity le && !le.equals(victim)) {
                    Location from = victim.getLocation();
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.knock(le, from, 1.8, 0.45));
                }
                return true;
            }
        }

        // Phase Shift (Afterimage passive)
        if (plugin.has(victim, Ability.AFTERIMAGE)
                && plugin.cooldownLeft(id, "phase") <= 0
                && plugin.random().nextDouble() < 0.12) {
            plugin.setCooldown(id, "phase", 12000);
            e.setCancelled(true);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 15, 0, false, false, false));
            Fx.ghostFigure(victim.getLocation());
            Fx.sound(victim.getLocation(), "entity.phantom.flap", 1f, 1.6f);
            victim.sendActionBar(Text.mm(Ability.AFTERIMAGE.tag() + "Phase Shift!"));
            return true;
        }

        // Retaliation (Counter passive)
        if (plugin.has(victim, Ability.COUNTER) && plugin.random().nextDouble() < 0.15) {
            Location l = victim.getLocation();
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Entity en : victim.getWorld().getNearbyEntities(l, 3.5, 2.5, 3.5)) {
                    if (!plugin.validTarget(victim, en)) continue;
                    plugin.knock(en, l, 1.1, 0.35);
                }
            });
            Particle.DustOptions blue = new Particle.DustOptions(Color.fromRGB(77, 166, 255), 1.5f);
            Fx.ring(l.clone().add(0, 0.3, 0), 2.5, 24, loc ->
                    victim.getWorld().spawnParticle(Particle.DUST, loc, 1, 0, 0.1, 0, 0, blue));
            Fx.sound(l, "entity.generic.explode", 0.4f, 1.6f);
        }
        return false;
    }

    private void offense(EntityDamageByEntityEvent e, Player atk) {
        EntityDamageEvent.DamageCause cause = e.getCause();
        if (cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && cause != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) return;
        if (!(e.getEntity() instanceof LivingEntity target) || target instanceof ArmorStand) return;

        UUID id = atk.getUniqueId();
        plugin.touchCombat(id);
        double extra = 0;
        double mult = 1.0;

        // Phantom - Ghost Trail
        if (plugin.has(atk, Ability.PHANTOM)
                && plugin.hit(id, "ghost", 6)
                && plugin.random().nextDouble() < 0.20) {
            atk.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 30, 0, false, false, false));
            Fx.ghostFigure(atk.getLocation());
            Fx.sound(atk.getLocation(), "entity.phantom.flap", 1f, 1.5f);
        }

        // Crosscut - Blood Combo
        if (plugin.has(atk, Ability.CROSSCUT)) {
            Combo c = combos.computeIfAbsent(id, k -> new Combo());
            long now = System.currentTimeMillis();
            if (target.getUniqueId().equals(c.target) && now - c.last <= 4000) {
                c.count++;
            } else {
                c.target = target.getUniqueId();
                c.count = 1;
            }
            c.last = now;
            if (c.count % 3 == 0) {
                extra += 1.0;
                redSlash(atk, target);
            }
        }

        // Singularity - Gravity Well
        if (plugin.has(atk, Ability.SINGULARITY) && plugin.hit(id, "gravity", 5)) {
            Location from = atk.getLocation();
            Bukkit.getScheduler().runTask(plugin, () -> {
                Vector v = from.toVector().subtract(target.getLocation().toVector());
                v.setY(0);
                if (v.lengthSquared() > 0.01) {
                    v.normalize().multiply(0.7);
                    v.setY(0.2);
                    target.setVelocity(v);
                }
            });
            target.getWorld().spawnParticle(Particle.PORTAL, target.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.4);
        }

        // Chain Reaction - Static
        if (plugin.has(atk, Ability.CHAINREACTION) && plugin.hit(id, "static", 5)) {
            extra += 1.0;
            Location tl = target.getLocation().add(0, 1, 0);
            target.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, tl, 25, 0.3, 0.5, 0.3, 0.3);
            Fx.sound(tl, "entity.lightning_bolt.impact", 0.3f, 2.0f);
        }

        // Overload - Berserker
        if (plugin.has(atk, Ability.OVERLOAD) && atk.getHealth() < 10.0) {
            mult *= 1.04;
        }

        // Soul Chains - Leech
        if (plugin.has(atk, Ability.SOULCHAINS) && plugin.hit(id, "leech", 8)) {
            AttributeInstance max = atk.getAttribute(Attribute.MAX_HEALTH);
            double mx = max != null ? max.getValue() : 20.0;
            atk.setHealth(Math.min(mx, atk.getHealth() + 1.0));
            atk.getWorld().spawnParticle(Particle.HEART, atk.getLocation().add(0, 2, 0), 3, 0.3, 0.2, 0.3, 0);
        }

        if (extra != 0 || mult != 1.0) {
            e.setDamage((e.getDamage() + extra) * mult);
        }
    }

    private void redSlash(Player atk, LivingEntity target) {
        Location c = target.getBoundingBox().getCenter().toLocation(target.getWorld());
        Vector side = atk.getLocation().getDirection().crossProduct(new Vector(0, 1, 0));
        if (side.lengthSquared() < 0.0001) side = new Vector(1, 0, 0);
        side.normalize();
        Location a = c.clone().add(side.clone().multiply(0.9)).add(0, 0.7, 0);
        Location b = c.clone().subtract(side.clone().multiply(0.9)).subtract(0, 0.7, 0);
        Particle.DustOptions red = new Particle.DustOptions(Color.fromRGB(210, 15, 15), 1.3f);
        Fx.line(a, b, 0.15, pt -> target.getWorld().spawnParticle(Particle.DUST, pt, 1, 0, 0, 0, 0, red));
        Fx.sound(c, "entity.player.attack.crit", 1f, 1.2f);
    }

    // ------------------------------------------------ Second Wind & Rebirth
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageMonitor(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;
        plugin.touchCombat(p.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> afterDamage(p));
    }

    private void afterDamage(Player p) {
        if (!p.isOnline() || p.isDead()) return;
        double hp = p.getHealth();
        if (hp >= 8.0) return; // 4 hearts
        UUID id = p.getUniqueId();

        // Rewind - Second Wind
        if (plugin.has(p, Ability.REWIND) && plugin.secondWindUsed.add(id)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 80, 1, false, true, true));
            p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.05);
            p.sendActionBar(Text.mm(Ability.REWIND.tag() + "Second Wind!"));
            Fx.sound(p.getLocation(), "entity.allay.item_thrown", 1f, 1.4f);
        }

        // Phoenix - Rebirth
        if (plugin.has(p, Ability.PHOENIX) && plugin.cooldownLeft(id, "rebirth") <= 0) {
            plugin.setCooldown(id, "rebirth", 90000);
            Location l = p.getLocation();
            for (Entity en : p.getWorld().getNearbyEntities(l, 5, 3, 5)) {
                if (!plugin.validTarget(p, en)) continue;
                plugin.knock(en, l, 1.6, 0.5);
            }
            p.getWorld().spawnParticle(Particle.FLAME, l.clone().add(0, 1, 0), 100, 1.5, 0.8, 1.5, 0.2);
            p.getWorld().spawnParticle(Particle.EXPLOSION, l.clone().add(0, 1, 0), 1);
            Fx.sound(l, "entity.blaze.shoot", 1f, 0.6f);
            p.sendActionBar(Text.mm(Ability.PHOENIX.tag() + "Rebirth!"));
        }
    }
}
