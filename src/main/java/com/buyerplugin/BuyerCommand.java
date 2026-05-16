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
 * /buyer             — открыть GUI магазина
 * /buyer balance     — показать баланс (Vault)
 * /buyer give        — (admin) выдать монеты через Vault
 * /buyer set         — (admin) установить монеты через Vault
 * /buyer reset       — (admin) сбросить лимиты/склад байера
 * /buyer info        — показать информацию о предмете (склад байера)
 * /buyer setstock    — (admin) вручную задать склад байера для предмета
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
                double  bal = eco.getBalance(player);
                player.sendMessage("§6[Байер] §7Ваш баланс: §6" + eco.format(bal));
            }

            // /buyer give <игрок> <сумма>
            case "give" -> {
                if (!sender.hasPermission("buyer.admin")) {
                    sender.sendMessage("§cНет прав!");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§cИспользование: /buyer give <игрок> <сумма>");
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§cИгрок §e" + args[1] + " §cне найден или не в сети.");
                    return true;
                }
                try {
                    double amount = Double.parseDouble(args[2]);
                    if (amount <= 0) { sender.sendMessage("§cСумма должна быть > 0"); return true; }
                    plugin.getEconomy().depositPlayer(target, amount);
                    sender.sendMessage("§a[Байер] §7Выдано §6" + plugin.getEconomy().format(amount)
                            + " §7игроку §e" + target.getName());
                    target.sendMessage("§a[Байер] §7Вам выдано §6"
                            + plugin.getEconomy().format(amount) + " §7администратором.");
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cНеверное число: §e" + args[2]);
                }
            }

            // /buyer set <игрок> <сумма>
            case "set" -> {
                if (!sender.hasPermission("buyer.admin")) {
                    sender.sendMessage("§cНет прав!");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§cИспользование: /buyer set <игрок> <сумма>");
                    return true;
                }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§cИгрок §e" + args[1] + " §cне найден или не в сети.");
                    return true;
                }
                try {
                    double amount  = Double.parseDouble(args[2]);
                    if (amount < 0) { sender.sendMessage("§cСумма не может быть отрицательной."); return true; }
                    Economy eco     = plugin.getEconomy();
                    double  current = eco.getBalance(target);
                    if (current > amount) { eco.withdrawPlayer(target, current - amount); }
                    else                  { eco.depositPlayer(target, amount - current); }
                    sender.sendMessage("§a[Байер] §7Баланс §e" + target.getName()
                            + " §7установлен: §6" + eco.format(amount));
                    target.sendMessage("§a[Байер] §7Ваш баланс установлен на §6" + eco.format(amount));
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cНеверное число: §e" + args[2]);
                }
            }

            // /buyer reset — сбросить склад байера (всем предметам ставится лимит/2)
            case "reset" -> {
                if (!sender.hasPermission("buyer.admin")) {
                    sender.sendMessage("§cНет прав!");
                    return true;
                }
                data.resetLimits();
                data.save();
                sender.sendMessage("§a[Байер] §7Склад байера сброшен (все предметы = лимит/2).");
            }

            // /buyer info <itemId> — информация о предмете и текущем складе
            case "info" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cИспользование: /buyer info <itemId>");
                    return true;
                }
                ShopItem item = ShopRegistry.getById(args[1]);
                if (item == null) {
                    sender.sendMessage("§cПредмет §e" + args[1] + " §cне найден в магазине.");
                    return true;
                }
                int stock = data.getServerStock(item.getId());
                int limit = item.getServerLimit();
                sender.sendMessage("§6[Байер] §7Информация о §e" + item.getDisplayName() + "§7:");
                sender.sendMessage("  §7ID: §f" + item.getId());
                sender.sendMessage("  §7Цена покупки:  §a" + (item.getBuyPrice() == -1 ? "§cНельзя купить" : item.getBuyPrice()));
                sender.sendMessage("  §7Цена продажи: §e" + item.getSellPrice());
                sender.sendMessage("  §7Категория: §b" + item.getCategory());
                sender.sendMessage("  §7Склад байера: §f" + stock + "§7/§f" + limit
                        + (stock == 0 ? " §c(ПУСТО — покупка невозможна)" : "")
                        + (stock >= limit ? " §c(ПОЛНО — продажа невозможна)" : ""));
            }

            // /buyer setstock <itemId> <количество> — вручную задать склад
            case "setstock" -> {
                if (!sender.hasPermission("buyer.admin")) {
                    sender.sendMessage("§cНет прав!");
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§cИспользование: /buyer setstock <itemId> <количество>");
                    return true;
                }
                ShopItem item = ShopRegistry.getById(args[1]);
                if (item == null) {
                    sender.sendMessage("§cПредмет §e" + args[1] + " §cне найден в магазине.");
                    return true;
                }
                try {
                    int amount = Integer.parseInt(args[2]);
                    if (amount < 0 || amount > item.getServerLimit()) {
                        sender.sendMessage("§cКоличество должно быть от 0 до §e" + item.getServerLimit());
                        return true;
                    }
                    // Обнуляем старое значение и устанавливаем новое
                    data.addStock(item.getId(), amount - data.getServerStock(item.getId()), item.getServerLimit());
                    data.save();
                    sender.sendMessage("§a[Байер] §7Склад §e" + item.getDisplayName()
                            + " §7установлен: §f" + data.getServerStock(item.getId())
                            + "§7/§f" + item.getServerLimit());
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cНеверное число: §e" + args[2]);
                }
            }

            default -> sender.sendMessage("§cНеизвестная команда. Используйте §e/buyer");
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
                subs.addAll(List.of("give", "set", "reset", "info", "setstock"));
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
            } else if (sub.equals("info") || sub.equals("setstock")) {
                ShopRegistry.getAllIds().stream()
                        .filter(id -> id.startsWith(args[1].toLowerCase()))
                        .forEach(result::add);
            }
        }

        return result;
    }
}
