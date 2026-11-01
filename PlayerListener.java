package dev.eclipse.smp;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

public final class PlayerListener implements Listener {
    private final EclipsePlugin plugin;

    public PlayerListener(EclipsePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isAbility(ItemStack it) {
        return plugin.items().isAbilityItem(it);
    }

    // ------------------------------------------------ join / quit / respawn
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        plugin.data().get(p.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.items().syncItems(p), 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        plugin.cleanup(e.getPlayer().getUniqueId());
        plugin.data().save();
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.items().syncItems(p), 2L);
    }

    // ------------------------------------------------ essence from kills
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player dead = e.getEntity();
        e.getDrops().removeIf(this::isAbility);
        plugin.cleanup(dead.getUniqueId());
        Player killer = dead.getKiller();
        if (killer != null && !killer.equals(dead)) {
            plugin.giveEssence(killer, plugin.getConfig().getInt("essence-per-player-kill", 1));
        }
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) return;
        LivingEntity dead = e.getEntity();
        Player killer = dead.getKiller();
        int amount = plugin.getConfig().getInt("essence-per-mob-kill", 0);
        if (killer != null && amount > 0) {
            plugin.giveEssence(killer, amount);
        }
    }

    // ------------------------------------------------ using ability items
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Ability a = plugin.items().getAbility(e.getItem());
        if (a == null) return;
        Player p = e.getPlayer();
        Action act = e.getAction();

        if (act == Action.RIGHT_CLICK_AIR || act == Action.RIGHT_CLICK_BLOCK) {
            if (act == Action.RIGHT_CLICK_BLOCK && !p.isSneaking()
                    && e.getClickedBlock() != null && e.getClickedBlock().getType().isInteractable()) {
                return; // let chests, doors, etc. work
            }
            e.setCancelled(true);
            plugin.actives().activate(p, a);
        } else if (act == Action.LEFT_CLICK_AIR) {
            String mode = plugin.getConfig().getString("left-click-action", "cycle");
            if ("active".equalsIgnoreCase(mode)) {
                plugin.actives().activate(p, a);
            } else if ("cycle".equalsIgnoreCase(mode)) {
                cycle(p);
            }
        }
    }

    private void cycle(Player p) {
        PlayerInventory inv = p.getInventory();
        int cur = inv.getHeldItemSlot();
        int first = -1;
        int next = -1;
        for (int i = 0; i < 9; i++) {
            if (!isAbility(inv.getItem(i))) continue;
            if (first < 0) first = i;
            if (i > cur && next < 0) next = i;
        }
        if (next < 0) next = first;
        if (next >= 0 && next != cur) {
            inv.setHeldItemSlot(next);
            Ability a = plugin.items().getAbility(inv.getItem(next));
            if (a != null) p.sendActionBar(Text.mm("<gray>Selected: " + a.tag() + a.activeName));
        }
    }

    // ------------------------------------------------ protecting ability items
    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (isAbility(e.getItemDrop().getItemStack())) e.setCancelled(true);
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent e) {
        if (isAbility(e.getMainHandItem()) || isAbility(e.getOffHandItem())) e.setCancelled(true);
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent e) {
        if (e.getRightClicked() instanceof ItemFrame && isAbility(e.getPlayer().getInventory().getItemInMainHand())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onStandManipulate(PlayerArmorStandManipulateEvent e) {
        if (plugin.clones.contains(e.getRightClicked())) e.setCancelled(true);
    }

    @EventHandler
    public void onInvClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof AbilityMenu.Holder) return; // handled by menu
        if (!(e.getWhoClicked() instanceof Player pl)) return;
        if (e.getView().getTopInventory().getType() == InventoryType.CRAFTING) return; // own inventory only
        boolean foreign = e.getClickedInventory() != null && e.getClickedInventory() != pl.getInventory();
        boolean bad = false;
        if (isAbility(e.getCurrentItem()) && (e.isShiftClick() || foreign)) bad = true;
        if (isAbility(e.getCursor()) && foreign) bad = true;
        if (e.getClick() == ClickType.NUMBER_KEY && foreign) {
            int btn = e.getHotbarButton();
            if (btn >= 0 && isAbility(pl.getInventory().getItem(btn))) bad = true;
        }
        if (e.getClick() == ClickType.SWAP_OFFHAND && foreign && isAbility(pl.getInventory().getItemInOffHand())) {
            bad = true;
        }
        if (bad) e.setCancelled(true);
    }

    @EventHandler
    public void onInvDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof AbilityMenu.Holder) return;
        if (!isAbility(e.getOldCursor())) return;
        int topSize = e.getView().getTopInventory().getSize();
        for (int raw : e.getRawSlots()) {
            if (raw < topSize) {
                e.setCancelled(true);
                return;
            }
        }
    }
}
