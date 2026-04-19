package com.buyerplugin;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Обработчик команды /buyer
 *
 * /buyer                   — открыть GUI магазина
 * /buyer balance           — показать баланс (Vault)
 * /buyer give <player> <amount>  — (admin) выдать монеты через Vault
 * /buyer set  <player> <amount>  — (admin) установить монеты через Vault
 * /buyer reset                   — (admin) сбросить лимиты продаж
 * /buyer info <item>             — показать информацию о предмете
 */
public class BuyerCommand implements CommandExecutor, TabCompleter {

    private final BuyerPlugin plugin;
    private final ShopData    data;
    private final ShopGUI     gui;

    public BuyerCommand(BuyerPlugin plugin, ShopData data, ShopGUI gui) {
        this.plugin = plugin;
        this.data   = data;
        this.gui    = gui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {

        // /buyer — открыть GUI
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cТолько игрок может открыть магазин!");
                return true;
            }
            if (!player.hasPermission("buyer.use")) {
                player.sendMessage("§cУ вас нет прав на использование магазина Байера.");
                return true;
            }
            gui.openMainMenu(player);
            return true;
        }

        switch (args[0].toLowerCase()) {

            // /buyer balance
            case "balance", "bal", "баланс" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cТолько игрок может проверить баланс!");
                    return true;
                }
                Economy eco = plugin.getEconomy();
                double bal = eco.getBalance(player);
                player.sendMessage("§6[Байер] §7Ваш баланс: §6" + eco.format(bal));
            }

            // /buyer give <player> <amount>
            case "give" -> {
                if (!sender.hasPermission("buyer.admin")) {
                    sender.sendMessage("§cНет прав!");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§cИспользование: /buyer give <игрок> <количество>");
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§cИгрок §e" + args[1] + " §cне найден или не в сети.");
                    return true;
                }
                try {
                    double amount = Double.parseDouble(args[2]);
                    if (amount <= 0) {
                        sender.sendMessage("§cКоличество должно быть > 0");
                        return true;
                    }
                    plugin.getEconomy().depositPlayer(target, amount);
                    sender.sendMessage("§a[Байер] §7Выдано §6" + plugin.getEconomy().format(amount)
                            + " §7игроку §e" + target.getName());
                    target.sendMessage("§a[Байер] §7Вам выдано §6" + plugin.getEconomy().format(amount)
                            + " §7администратором.");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cНеверное число: §e" + args[2]);
                }
            }

            // /buyer set <player> <amount>
            case "set" -> {
                if (!sender.hasPermission("buyer.admin")) {
                    sender.sendMessage("§cНет прав!");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§cИспользование: /buyer set <игрок> <количество>");
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§cИгрок §e" + args[1] + " §cне найден или не в сети.");
                    return true;
                }
                try {
                    double amount = Double.parseDouble(args[2]);
                    if (amount < 0) {
                        sender.sendMessage("§cКоличество не может быть отрицательным");
                        return true;
                    }
                    Economy eco = plugin.getEconomy();
                    double current = eco.getBalance(target);
                    if (current > amount) {
                        eco.withdrawPlayer(target, current - amount);
                    } else {
                        eco.depositPlayer(target, amount - current);
                    }
                    sender.sendMessage("§a[Байер] §7Баланс §e" + target.getName()
                            + " §7установлен: §6" + eco.format(amount));
                    target.sendMessage("§a[Байер] §7Ваш баланс установлен на §6" + eco.format(amount));
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cНеверное число: §e" + args[2]);
                }
            }

            // /buyer reset
            case "reset" -> {
                if (!sender.hasPermission("buyer.admin")) {
                    sender.sendMessage("§cНет прав!");
                    return true;
                }
                data.resetLimits();
                sender.sendMessage("§a[Байер] §7Лимиты продаж сброшены.");
            }

            // /buyer info <item>
            case "info" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cИспользование: /buyer info <id предмета>");
                    return true;
                }
                ShopItem item = ShopRegistry.getById(args[1]);
                if (item == null) {
                    sender.sendMessage("§cПредмет §e" + args[1] + " §cне найден в магазине.");
                    return true;
                }
                sender.sendMessage("§6[Байер] §7Информация о §e" + item.getDisplayName() + "§7:");
                sender.sendMessage("  §7ID: §f" + item.getId());
                sender.sendMessage("  §7Цена покупки: §a" + (item.getBuyPrice() == -1 ? "§cНельзя купить" : "§a" + item.getBuyPrice()));
                sender.sendMessage("  §7Цена продажи: §e" + item.getSellPrice());
                sender.sendMessage("  §7Категория: §b" + item.getCategory());
                int sold = data.getServerSold(item.getId());
                int limit = item.getServerLimit();
                sender.sendMessage("  §7Продано сегодня: §c" + sold + "/" + limit);
            }

            default -> sender.sendMessage("§cНеизвестная команда. Используйте /buyer");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String alias,
                                      @NotNull String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("balance"));
            if (sender.hasPermission("buyer.admin")) {
                subs.addAll(List.of("give", "set", "reset", "info"));
            }
            String typed = args[0].toLowerCase();
            subs.stream().filter(s -> s.startsWith(typed)).forEach(result::add);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("give") || sub.equals("set")) {
                plugin.getServer().getOnlinePlayers()
                        .stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .forEach(result::add);
            } else if (sub.equals("info")) {
                ShopRegistry.getAllIds().stream()
                        .filter(id -> id.startsWith(args[1].toLowerCase()))
                        .forEach(result::add);
            }
        }
        return result;
    }
}

