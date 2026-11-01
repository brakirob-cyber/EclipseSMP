package dev.eclipse.smp;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class Items {
    private final EclipsePlugin plugin;
    private final NamespacedKey abilityKey;

    public Items(EclipsePlugin plugin) {
        this.plugin = plugin;
        this.abilityKey = new NamespacedKey(plugin, "ability");
    }

    public Ability getAbility(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String id = it.getItemMeta().getPersistentDataContainer().get(abilityKey, PersistentDataType.STRING);
        return id == null ? null : Ability.byId(id);
    }

    public boolean isAbilityItem(ItemStack it) {
        return getAbility(it) != null;
    }

    /** The real, usable ability item that lives in a player's inventory. */
    public ItemStack ability(Ability a) {
        ItemStack it = build(a, true);
        ItemMeta m = it.getItemMeta();
        m.setEnchantmentGlintOverride(true);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack build(Ability a, boolean real) {
        ItemStack it = new ItemStack(Material.ECHO_SHARD);
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.mm(a.tag() + "<bold>" + a.display + "</bold> <dark_gray>[" + a.category + "]"));
        NamespacedKey model = NamespacedKey.fromString("eclipse:" + a.id);
        if (model != null) m.setItemModel(model);
        m.setMaxStackSize(1);
        if (real) m.getPersistentDataContainer().set(abilityKey, PersistentDataType.STRING, a.id);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Text.mm("<gray>Active: " + a.tag() + a.activeName));
        lore.addAll(Text.wrap("<dark_gray>", a.activeDesc, 40));
        lore.add(Component.empty());
        lore.add(Text.mm("<gray>Passive: " + a.tag() + a.passiveName));
        lore.addAll(Text.wrap("<dark_gray>", a.passiveDesc, 40));
        if (real) {
            lore.add(Component.empty());
            lore.add(Text.mm("<yellow>Right-click <gray>to use the active"));
            lore.add(Text.mm("<dark_gray>Passive is always on while equipped"));
        }
        m.lore(lore);
        it.setItemMeta(m);
        return it;
    }

    /** A menu icon (not a usable ability item). */
    public ItemStack icon(Ability a, PlayerData d) {
        int cost = plugin.getConfig().getInt("unlock-cost", 5);
        int max = plugin.getConfig().getInt("max-equipped", 2);
        ItemStack it = build(a, false);
        ItemMeta m = it.getItemMeta();
        List<Component> lore = new ArrayList<>();
        List<Component> existing = m.lore();
        if (existing != null) lore.addAll(existing);
        lore.add(Component.empty());
        if (d.equipped.contains(a)) {
            lore.add(Text.mm("<green>[Equipped] <gray>Click to unequip"));
            m.setEnchantmentGlintOverride(true);
        } else if (d.unlocked.contains(a)) {
            lore.add(Text.mm("<yellow>Click to equip <gray>(" + d.equipped.size() + "/" + max + " slots used)"));
        } else {
            lore.add(Text.mm("<red>Locked <gray>- costs <white>" + cost + " Essence"));
            if (d.essence >= cost) {
                lore.add(Text.mm("<yellow>Click to unlock"));
            } else {
                lore.add(Text.mm("<gray>You have <white>" + d.essence + " <gray>Essence"));
            }
        }
        m.lore(lore);
        it.setItemMeta(m);
        return it;
    }

    public ItemStack essenceItem(PlayerData d) {
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta m = it.getItemMeta();
        m.displayName(Text.mm("<light_purple><bold>Essence"));
        NamespacedKey model = NamespacedKey.fromString("eclipse:essence");
        if (model != null) m.setItemModel(model);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Text.mm("<gray>You have <white>" + d.essence + " <gray>Essence"));
        lore.add(Text.mm("<dark_gray>Earn Essence by getting kills."));
        m.lore(lore);
        it.setItemMeta(m);
        return it;
    }

    /** Makes sure the player holds exactly the ability items they have equipped. */
    public void syncItems(Player p) {
        PlayerData d = plugin.data().get(p.getUniqueId());
        PlayerInventory inv = p.getInventory();
        Set<Ability> have = EnumSet.noneOf(Ability.class);
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            Ability a = getAbility(it);
            if (a == null) continue;
            if (!d.equipped.contains(a) || have.contains(a)) {
                inv.setItem(i, null);
            } else {
                have.add(a);
            }
        }
        for (Ability a : d.equipped) {
            if (have.contains(a)) continue;
            if (!inv.addItem(ability(a)).isEmpty()) {
                p.sendMessage(Text.mm("<red>Your inventory is full. Free a slot to receive " + a.tag() + a.display + "<red>."));
            }
        }
    }
}
