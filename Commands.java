package dev.eclipse.smp;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Commands implements CommandExecutor, TabCompleter {
    private final EclipsePlugin plugin;

    public Commands(EclipsePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (name.equals("abilities")) {
            if (sender instanceof Player p) {
                plugin.menu().open(p);
            } else {
                sender.sendMessage("Players only.");
            }
            return true;
        }
        if (name.equals("essence")) return essence(sender, args);
        if (name.equals("eclipse")) return eclipse(sender, args);
        return false;
    }

    private boolean essence(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) {
                PlayerData d = plugin.data().get(p.getUniqueId());
                p.sendMessage(Text.mm("<light_purple>Essence: <white>" + d.essence));
            } else {
                sender.sendMessage("Usage: /essence [give|take|set] [player] [amount]");
            }
            return true;
        }
        if (!sender.hasPermission("eclipse.admin")) {
            sender.sendMessage(Text.mm("<red>You don't have permission."));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Text.mm("<red>Usage: /essence [give|take|set] [player] [amount]"));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Text.mm("<red>Player not found."));
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException ex) {
            sender.sendMessage(Text.mm("<red>Amount must be a number."));
            return true;
        }
        PlayerData d = plugin.data().get(target.getUniqueId());
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> d.essence += amount;
            case "take" -> d.essence = Math.max(0, d.essence - amount);
            case "set" -> d.essence = Math.max(0, amount);
            default -> {
                sender.sendMessage(Text.mm("<red>Usage: /essence [give|take|set] [player] [amount]"));
                return true;
            }
        }
        sender.sendMessage(Text.mm("<green>" + target.getName() + " now has <white>" + d.essence + " <green>Essence."));
        return true;
    }

    private boolean eclipse(CommandSender sender, String[] args) {
        if (!sender.hasPermission("eclipse.admin")) {
            sender.sendMessage(Text.mm("<red>You don't have permission."));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Text.mm("<gray>/eclipse reload | unlock [player] [ability|all] | reset [player]"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            plugin.reloadConfig();
            sender.sendMessage(Text.mm("<green>Config reloaded."));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Text.mm("<red>Specify a player."));
            return true;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(Text.mm("<red>Player not found."));
            return true;
        }
        PlayerData d = plugin.data().get(target.getUniqueId());
        if (sub.equals("reset")) {
            d.essence = 0;
            d.unlocked.clear();
            d.equipped.clear();
            plugin.items().syncItems(target);
            sender.sendMessage(Text.mm("<green>Reset " + target.getName() + "."));
            return true;
        }
        if (sub.equals("unlock") && args.length >= 3) {
            if (args[2].equalsIgnoreCase("all")) {
                for (Ability a : Ability.values()) d.unlocked.add(a);
            } else {
                Ability a = Ability.byId(args[2]);
                if (a == null) {
                    sender.sendMessage(Text.mm("<red>Unknown ability."));
                    return true;
                }
                d.unlocked.add(a);
            }
            sender.sendMessage(Text.mm("<green>Unlocked for " + target.getName() + "."));
            return true;
        }
        sender.sendMessage(Text.mm("<red>Unknown subcommand."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        String name = cmd.getName().toLowerCase(Locale.ROOT);
        if (name.equals("essence")) {
            if (args.length == 1) {
                out.add("give");
                out.add("take");
                out.add("set");
            } else if (args.length == 2) {
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            }
        } else if (name.equals("eclipse")) {
            if (args.length == 1) {
                out.add("reload");
                out.add("unlock");
                out.add("reset");
            } else if (args.length == 2) {
                for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
            } else if (args.length == 3 && args[0].equalsIgnoreCase("unlock")) {
                out.add("all");
                for (Ability a : Ability.values()) out.add(a.id);
            }
        }
        String last = args.length > 0 ? args[args.length - 1].toLowerCase(Locale.ROOT) : "";
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
