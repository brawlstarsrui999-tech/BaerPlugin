package com.baerplugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Обработчик команды /baer
 *
 * /baer                  — открывает GUI
 * /baer balance          — показывает баланс
 * /baer give <player> <amount>   — (admin) выдать монеты
 * /baer set  <player> <amount>   — (admin) установить баланс
 * /baer reset            — (admin) сбросить лимиты досрочно
 * /baer info <item>      — показать информацию о предмете
 */
public class BaerCommand implements CommandExecutor, TabCompleter {

    private final BaerPlugin plugin;
    private final ShopData   data;
    private final ShopGUI    gui;

    public BaerCommand(BaerPlugin plugin, ShopData data, ShopGUI gui) {
        this.plugin = plugin;
        this.data   = data;
        this.gui    = gui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        // /baer — открыть GUI
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cТолько игрок может открыть магазин!");
                return true;
            }
            if (!player.hasPermission("baer.use")) {
                player.sendMessage("§cУ вас нет прав на использование магазина Баера.");
                return true;
            }
            gui.openMainMenu(player);
            return true;
        }

        switch (args[0].toLowerCase()) {

            // /baer balance
            case "balance", "bal", "баланс" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cТолько игрок может проверить баланс!");
                    return true;
                }
                int bal = data.getBalance(player.getUniqueId());
                player.sendMessage("§6[Баер] §7Ваш баланс: §6" + bal + " монет");
            }

            // /baer give <player> <amount>
            case "give" -> {
                if (!sender.hasPermission("baer.admin")) {
                    sender.sendMessage("§cНет прав!");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§cИспользование: /baer give <игрок> <количество>");
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§cИгрок §e" + args[1] + " §cне найден или не в сети.");
                    return true;
                }
                try {
                    int amount = Integer.parseInt(args[2]);
                    if (amount <= 0) { sender.sendMessage("§cКоличество должно быть > 0"); return true; }
                    data.addBalance(target.getUniqueId(), amount);
                    sender.sendMessage("§a[Баер] Выдано §6" + amount + " монет §aигроку §e" + target.getName());
                    target.sendMessage("§a[Баер] Вам выдано §6" + amount + " монет §aот администратора.");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cНеверное число: §e" + args[2]);
                }
            }

            // /baer set <player> <amount>
            case "set" -> {
                if (!sender.hasPermission("baer.admin")) {
                    sender.sendMessage("§cНет прав!");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§cИспользование: /baer set <игрок> <количество>");
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§cИгрок §e" + args[1] + " §cне найден или не в сети.");
                    return true;
                }
                try {
                    int amount = Integer.parseInt(args[2]);
                    if (amount < 0) { sender.sendMessage("§cКоличество не может быть отрицательным"); return true; }
                    data.setBalance(target.getUniqueId(), amount);
                    sender.sendMessage("§a[Баер] Баланс §e" + target.getName() + " §aустановлен: §6" + amount + " монет");
                    target.sendMessage("§a[Баер] Ваш баланс установлен на §6" + amount + " монет.");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cНеверное число: §e" + args[2]);
                }
            }

            // /baer reset
            case "reset" -> {
                if (!sender.hasPermission("baer.admin")) {
                    sender.sendMessage("§cНет прав!");
                    return true;
                }
                data.performReset();
                sender.sendMessage("§a[Баер] Лимиты сброшены досрочно.");
            }

            // /baer info <itemId>
            case "info" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cИспользование: /baer info <id_товара>");
                    return true;
                }
                ShopItem item = ShopRegistry.getById(args[1].toLowerCase());
                if (item == null) {
                    sender.sendMessage("§cТовар §e" + args[1] + " §cне найден.");
                    return true;
                }
                int sold      = data.getServerSold(item.getId());
                int limit     = item.getWeeklyLimit();
                int remaining = data.getRemainingLimit(item.getId());
                sender.sendMessage("§6[Баер] §eИнформация о §f" + item.getDisplayName());
                sender.sendMessage("  §7Лимит: §e" + sold + "§7/§e" + limit + " §7(осталось: §a" + remaining + "§7)");
                sender.sendMessage("  §7Цена продажи (вы → баер): §a" + item.getSellPrice() + " монет/шт.");
                if (item.canBuy()) {
                    sender.sendMessage("  §7Цена покупки (баер → вы): §c" + item.getBuyPrice() + " монет/шт.");
                } else {
                    sender.sendMessage("  §7Покупка: §8недоступна");
                }
            }

            // Неизвестная подкоманда
            default -> {
                sender.sendMessage("§c[Баер] Неизвестная подкоманда. Используйте §e/baer §cдля открытия магазина.");
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String alias,
                                      @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> base = new ArrayList<>(List.of("balance", "info"));
            if (sender.hasPermission("baer.admin")) {
                base.add("give");
                base.add("set");
                base.add("reset");
            }
            for (String s : base) {
                if (s.startsWith(args[0].toLowerCase())) completions.add(s);
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("info")) {
                for (ShopItem item : ShopRegistry.getAllItems()) {
                    if (item.getId().startsWith(args[1].toLowerCase())) completions.add(item.getId());
                }
            } else if ((args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("set"))
                    && sender.hasPermission("baer.admin")) {
                for (Player p : plugin.getServer().getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) completions.add(p.getName());
                }
            }
        }
        return completions;
    }
}
