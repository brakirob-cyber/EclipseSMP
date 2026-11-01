package dev.eclipse.smp;

import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class EclipsePlugin extends JavaPlugin {

    private DataManager data;
    private Items items;
    private Actives actives;
    private AbilityMenu menu;
    private final Random random = new Random();

    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> hitCounters = new HashMap<>();

    // Shared runtime state
    public final Map<UUID, Long> stance = new HashMap<>();
    public final Map<UUID, Long> fallProtection = new HashMap<>();
    public final Map<UUID, Location> rewindPoints = new HashMap<>();
    public final Map<UUID, Long> lastCombat = new HashMap<>();
    public final Set<UUID> secondWindUsed = new HashSet<>();
    public final Set<ArmorStand> clones = new HashSet<>();

    private boolean internalDamage = false;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        data = new DataManager(this);
        data.load();
        items = new Items(this);
        actives = new Actives(this);
        menu = new AbilityMenu(this);

        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CombatListener(this), this);
        Bukkit.getPluginManager().registerEvents(menu, this);

        Commands cmds = new Commands(this);
        for (String name : new String[]{"abilities", "essence", "eclipse"}) {
            PluginCommand pc = getCommand(name);
            if (pc != null) {
                pc.setExecutor(cmds);
                pc.setTabCompleter(cmds);
            }
        }

        Bukkit.getScheduler().runTaskTimer(this, () -> data.save(), 6000L, 6000L);
        for (Player p : Bukkit.getOnlinePlayers()) {
            items.syncItems(p);
        }
        getLogger().info("EclipseSMP enabled.");
    }

    @Override
    public void onDisable() {
        for (ArmorStand s : new ArrayList<>(clones)) {
            s.remove();
        }
        clones.clear();
        if (data != null) data.save();
    }

    // ---------- accessors ----------
    public DataManager data() { return data; }
    public Items items() { return items; }
    public Actives actives() { return actives; }
    public AbilityMenu menu() { return menu; }
    public Random random() { return random; }

    public boolean has(Player p, Ability a) {
        return data.get(p.getUniqueId()).equipped.contains(a);
    }

    // ---------- config helpers ----------
    public double cooldownSeconds(Ability a) {
        return getConfig().getDouble("cooldowns." + a.id, a.defaultCooldown);
    }

    /** Reads a damage value in hearts from the config and returns it as health points. */
    public double dmg(String key, double defaultHearts) {
        return getConfig().getDouble("damage." + key, defaultHearts) * 2.0;
    }

    // ---------- cooldowns ----------
    public long cooldownLeft(UUID id, String key) {
        Map<String, Long> m = cooldowns.get(id);
        if (m == null) return 0;
        Long until = m.get(key);
        if (until == null) return 0;
        return Math.max(0, until - System.currentTimeMillis());
    }

    public void setCooldown(UUID id, String key, long millis) {
        cooldowns.computeIfAbsent(id, k -> new HashMap<>()).put(key, System.currentTimeMillis() + millis);
    }

    // ---------- hit counters ----------
    /** Increments a counter and returns true every Nth call. */
    public boolean hit(UUID id, String key, int every) {
        Map<String, Integer> m = hitCounters.computeIfAbsent(id, k -> new HashMap<>());
        int n = m.merge(key, 1, Integer::sum);
        if (n >= every) {
            m.put(key, 0);
            return true;
        }
        return false;
    }

    // ---------- combat ----------
    public void touchCombat(UUID id) {
        long now = System.currentTimeMillis();
        long timeout = getConfig().getLong("combat-timeout-seconds", 15) * 1000L;
        Long last = lastCombat.get(id);
        if (last == null || now - last > timeout) {
            secondWindUsed.remove(id);
        }
        lastCombat.put(id, now);
    }

    public boolean isInternalDamage() { return internalDamage; }

    public void abilityDamage(Player source, LivingEntity target, double amount) {
        internalDamage = true;
        try {
            target.setNoDamageTicks(0);
            target.damage(amount, source);
        } finally {
            internalDamage = false;
        }
    }

    public void knock(Entity e, Location from, double strength, double up) {
        Vector v = e.getLocation().toVector().subtract(from.toVector());
        v.setY(0);
        if (v.lengthSquared() < 0.0001) {
            v = new Vector(random.nextDouble() - 0.5, 0, random.nextDouble() - 0.5);
            if (v.lengthSquared() < 0.0001) v = new Vector(1, 0, 0);
        }
        v.normalize().multiply(strength);
        v.setY(up);
        e.setVelocity(v);
    }

    // ---------- targeting ----------
    public boolean validTarget(Player src, Entity e) {
        if (!(e instanceof LivingEntity le)) return false;
        if (e.equals(src) || e instanceof ArmorStand) return false;
        if (le.isDead() || !le.isValid()) return false;
        if (e instanceof Player pl && pl.getGameMode() == GameMode.SPECTATOR) return false;
        return true;
    }

    public LivingEntity targetEntity(Player p, double range, double raySize) {
        Location eye = p.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult r = p.getWorld().rayTraceEntities(eye, dir, range, raySize, e -> validTarget(p, e));
        if (r == null || !(r.getHitEntity() instanceof LivingEntity le)) return null;
        RayTraceResult wall = p.getWorld().rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        if (wall != null) {
            double wallDist = wall.getHitPosition().distance(eye.toVector());
            double entDist = r.getHitPosition().distance(eye.toVector());
            if (wallDist < entDist) return null;
        }
        return le;
    }

    public boolean isSafe(Location l) {
        Block feet = l.getBlock();
        Block head = l.clone().add(0, 1, 0).getBlock();
        return feet.isPassable() && head.isPassable() && !feet.isLiquid() && !head.isLiquid();
    }

    // ---------- essence ----------
    public void giveEssence(Player p, int amount) {
        if (amount <= 0) return;
        PlayerData d = data.get(p.getUniqueId());
        d.essence += amount;
        p.sendActionBar(Text.mm("<light_purple>+" + amount + " Essence <gray>(" + d.essence + " total)"));
        p.playSound(p.getLocation(), "entity.experience_orb.pickup", 1f, 1.4f);
    }

    public void cleanup(UUID id) {
        stance.remove(id);
        rewindPoints.remove(id);
        fallProtection.remove(id);
    }
}
