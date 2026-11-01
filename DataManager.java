package dev.eclipse.smp;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DataManager {
    private final EclipsePlugin plugin;
    private final File file;
    private final Map<UUID, PlayerData> map = new HashMap<>();

    public DataManager(EclipsePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public PlayerData get(UUID id) {
        return map.computeIfAbsent(id, k -> new PlayerData());
    }

    public void load() {
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection sec = y.getConfigurationSection("players");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            PlayerData d = new PlayerData();
            d.essence = sec.getInt(key + ".essence", 0);
            for (String s : sec.getStringList(key + ".unlocked")) {
                Ability a = Ability.byId(s);
                if (a != null) d.unlocked.add(a);
            }
            for (String s : sec.getStringList(key + ".equipped")) {
                Ability a = Ability.byId(s);
                if (a != null && d.unlocked.contains(a) && !d.equipped.contains(a)) d.equipped.add(a);
            }
            map.put(id, d);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerData> en : map.entrySet()) {
            String base = "players." + en.getKey();
            PlayerData d = en.getValue();
            y.set(base + ".essence", d.essence);
            y.set(base + ".unlocked", d.unlocked.stream().map(a -> a.id).toList());
            y.set(base + ".equipped", d.equipped.stream().map(a -> a.id).toList());
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save data.yml: " + ex.getMessage());
        }
    }
}
