package dev.eclipse.smp;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class AbilityMenu implements Listener {
    private static final int[] SLOTS = {11, 12, 13, 14, 15, 20, 21, 22, 23, 24};
    private final EclipsePlugin plugin;

    public static final class Holder implements InventoryHolder {
        private Inventory inv;

        @Override
        public Inventory getInventory() {
            return inv;
        }
    }

    public AbilityMenu(EclipsePlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        Holder h = new Holder();
        Inventory inv = Bukkit.createInventory(h, 45, Text.mm("<gradient:#ffb347:#ffe29a><bold>Eclipse Abilities</bold></gradient>"));
        h.inv = inv;
        render(p, inv);
        p.openInventory(inv);
    }

    private void render(Player p, Inventory inv) {
        inv.clear();
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        pm.displayName(Component.text(" "));
        pane.setItemMeta(pm);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);

        PlayerData d = plugin.data().get(p.getUniqueId());
        Ability[] all = Ability.values();
        for (int i = 0; i < all.length; i++) {
            inv.setItem(SLOTS[i], plugin.items().icon(all[i], d));
        }
        inv.setItem(40, plugin.items().essenceItem(d));

        int max = plugin.getConfig().getInt("max-equipped", 2);
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.displayName(Text.mm("<gold><bold>Your Loadout"));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Text.mm("<gray>Equipped: <white>" + d.equipped.size() + "/" + max));
        for (Ability a : d.equipped) {
            lore.add(Text.mm(" " + a.tag() + a.display + " <dark_gray>(" + a.activeName + " / " + a.passiveName + ")"));
        }
        lore.add(Component.empty());
        lore.add(Text.mm("<dark_gray>Changing your loadout is free."));
        im.lore(lore);
        info.setItemMeta(im);
        inv.setItem(4, info);
    }

    private Ability abilityAt(int slot) {
        Ability[] all = Ability.values();
        for (int i = 0; i < all.length; i++) {
            if (SLOTS[i] == slot) return all[i];
        }
        return null;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getInventory())) return;
        Ability a = abilityAt(e.getSlot());
        if (a == null) return;

        PlayerData d = plugin.data().get(p.getUniqueId());
        int cost = plugin.getConfig().getInt("unlock-cost", 5);
        int max = plugin.getConfig().getInt("max-equipped", 2);

        if (!d.unlocked.contains(a)) {
            if (d.essence < cost) {
                p.sendMessage(Text.mm("<red>You need <white>" + cost + " Essence <red>to unlock " + a.tag() + a.display
                        + "<red>. You have <white>" + d.essence + "<red>."));
                p.playSound(p.getLocation(), "entity.villager.no", 1f, 1f);
                return;
            }
            d.essence -= cost;
            d.unlocked.add(a);
            p.sendMessage(Text.mm("<green>Unlocked " + a.tag() + a.display + "<green>!"));
            p.playSound(p.getLocation(), "entity.player.levelup", 1f, 1.2f);
            if (d.equipped.size() < max) {
                d.equipped.add(a);
                p.sendMessage(Text.mm("<gray>Equipped automatically."));
            }
        } else if (d.equipped.contains(a)) {
            d.equipped.remove(a);
            p.sendMessage(Text.mm("<gray>Unequipped " + a.tag() + a.display + "<gray>."));
            p.playSound(p.getLocation(), "ui.button.click", 1f, 1f);
        } else {
            if (d.equipped.size() >= max) {
                p.sendMessage(Text.mm("<red>You can only equip " + max + " abilities. Unequip one first."));
                p.playSound(p.getLocation(), "entity.villager.no", 1f, 1f);
                return;
            }
            d.equipped.add(a);
            p.sendMessage(Text.mm("<green>Equipped " + a.tag() + a.display + "<green>."));
            p.playSound(p.getLocation(), "item.armor.equip_iron", 1f, 1.2f);
        }
        plugin.items().syncItems(p);
        render(p, e.getInventory());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Holder) e.setCancelled(true);
    }
}
