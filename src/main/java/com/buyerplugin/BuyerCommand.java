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
 * /buyer                      — открыть GUI магазина
 * /buyer balance              — показать баланс (Vault)
 * /buyer give <p> <n>         — (admin) выдать монеты
 * /buyer set  <p> <n>         — (admin) установить монеты
 * /buyer reset                — (admin) сбросить склад байера
 * /buyer info <id>            — показать информацию о предмете
 * /buyer setstock <id> <n>    — (admin) вручную задать склад
 * /buyer reload               — (admin) перезагрузить items.yml без рестарта!
 */
public class BuyerCommand implements CommandExecutor, TabCompleter {

    private final BuyerPlugin plugin;
    private final ShopData    data;
    private final ShopGUI     gui;
    private final ItemsConfig itemsConfig;

    public BuyerCommand(BuyerPlugin plugin, ShopData data, ShopGUI gui, ItemsConfig itemsConfig) {
        this.plugin      = plugin;
        this.data        = data;
        this.gui         = gui;
        this.itemsConfig = itemsConfig;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {

        if (args.length == 0) {
            if (!(sender instanceof Player player)) { sender.sendMessage("§cТолько игрок!"); return true; }
            if (!player.hasPermission("buyer.use"))  { player.sendMessage("§cНет прав!"); return true; }
            gui.openMainMenu(player);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "balance", "bal", "баланс" -> {
                if (!(sender instanceof Player player)) { sender.sendMessage("§cТолько игрок!"); return true; }
                Economy eco = plugin.getEconomy();
                player.sendMessage("§6[Байер] §7Ваш баланс: §6" + eco.format(eco.getBalance(player)));
            }

            case "give" -> {
                if (!sender.hasPermission("buyer.admin")) { sender.sendMessage("§cНет прав!"); return true; }
                if (args.length < 3) { sender.sendMessage("§cИспользование: /buyer give <игрок> <сумма>"); return true; }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) { sender.sendMessage("§cИгрок не найден."); return true; }
                try {
                    double amount = Double.parseDouble(args[2]);
                    if (amount <= 0) { sender.sendMessage("§cСумма должна быть > 0"); return true; }
                    plugin.getEconomy().depositPlayer(target, amount);
                    sender.sendMessage("§a[Байер] §7Выдано §6" + plugin.getEconomy().format(amount) + " §7игроку §e" + target.getName());
                    target.sendMessage("§a[Байер] §7Вам выдано §6" + plugin.getEconomy().format(amount));
                } catch (NumberFormatException e) { sender.sendMessage("§cНеверное число: §e" + args[2]); }
            }

            case "set" -> {
                if (!sender.hasPermission("buyer.admin")) { sender.sendMessage("§cНет прав!"); return true; }
                if (args.length < 3) { sender.sendMessage("§cИспользование: /buyer set <игрок> <сумма>"); return true; }
                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) { sender.sendMessage("§cИгрок не найден."); return true; }
                try {
                    double amount = Double.parseDouble(args[2]);
                    if (amount < 0) { sender.sendMessage("§cСумма должна быть >= 0"); return true; }
                    Economy eco = plugin.getEconomy();
                    double current = eco.getBalance(target);
                    if (current > amount) eco.withdrawPlayer(target, current - amount);
                    else eco.depositPlayer(target, amount - current);
                    sender.sendMessage("§a[Байер] §7Баланс §e" + target.getName() + " §7установлен: §6" + eco.format(amount));
                    target.sendMessage("§a[Байер] §7Ваш баланс установлен на §6" + eco.format(amount));
                } catch (NumberFormatException e) { sender.sendMessage("§cНеверное число: §e" + args[2]); }
            }

            case "reset" -> {
                if (!sender.hasPermission("buyer.admin")) { sender.sendMessage("§cНет прав!"); return true; }
                data.resetLimits();
                data.save();
                sender.sendMessage("§a[Байер] §7Склад байера сброшен.");
            }

            case "info" -> {
                if (args.length < 2) { sender.sendMessage("§cИспользование: /buyer info <id>"); return true; }
                ShopItem item = ShopRegistry.getById(args[1]);
                if (item == null) { sender.sendMessage("§cПредмет §e" + args[1] + " §cне найден."); return true; }
                int stock = data.getServerStock(item.getId());
                int limit = item.getServerLimit();
                int initStock = ShopRegistry.getInitialStock(item.getId());
                sender.sendMessage("§6[Байер] §7Информация о §e" + item.getDisplayName() + "§7:");
                sender.sendMessage("  §7ID: §f"              + item.getId());
                sender.sendMessage("  §7Материал: §f"        + item.getMaterial().name());
                sender.sendMessage("  §7Категория: §b"       + item.getCategory());
                sender.sendMessage("  §7Продажа игр→байер: §a" + (item.getSellPrice() == -1 ? "§cНельзя" : item.getSellPrice()));
                sender.sendMessage("  §7Покупка байер→игр: §e" + (item.getBuyPrice()  == -1 ? "§cНельзя" : item.getBuyPrice()));
                sender.sendMessage("  §7Склад: §f"           + stock + "§7/§f" + limit);
                sender.sendMessage("  §7Нач. запас: §f"      + initStock);
            }

            case "setstock" -> {
                if (!sender.hasPermission("buyer.admin")) { sender.sendMessage("§cНет прав!"); return true; }
                if (args.length < 3) { sender.sendMessage("§cИспользование: /buyer setstock <id> <кол-во>"); return true; }
                ShopItem item = ShopRegistry.getById(args[1]);
                if (item == null) { sender.sendMessage("§cПредмет §e" + args[1] + " §cне найден."); return true; }
                try {
                    int amount = Integer.parseInt(args[2]);
                    if (amount < 0 || amount > item.getServerLimit()) {
                        sender.sendMessage("§cКоличество от 0 до §e" + item.getServerLimit());
                        return true;
                    }
                    data.addStock(item.getId(), amount - data.getServerStock(item.getId()), item.getServerLimit());
                    data.save();
                    sender.sendMessage("§a[Байер] §7Склад §e" + item.getDisplayName() +
                            " §7установлен: §f" + data.getServerStock(item.getId()) + "§7/§f" + item.getServerLimit());
                } catch (NumberFormatException e) { sender.sendMessage("§cНеверное число: §e" + args[2]); }
            }

            // ─── НОВАЯ КОМАНДА: /buyer reload ───────────────────────────────
            case "reload" -> {
                if (!sender.hasPermission("buyer.admin")) { sender.sendMessage("§cНет прав!"); return true; }
                sender.sendMessage("§e[Байер] §7Перезагружаю items.yml...");
                boolean ok = plugin.reloadItems();
                if (ok) {
                    sender.sendMessage("§a[Байер] §7✓ items.yml перезагружен! Предметов в магазине: §f" + ShopRegistry.size());
                    sender.sendMessage("§7Новые предметы получили свой initial-stock из items.yml.");
                    sender.sendMessage("§7Склад существующих предметов §EННЕ§7 изменился.");
                } else {
                    sender.sendMessage("§c[Байер] ✗ Ошибка перезагрузки! Проверь консоль и items.yml.");
                }
            }

            // ─── /buyer list [категория] ─────────────────────────────────────
            case "list" -> {
                String category = args.length >= 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)) : null;
                List<ShopItem> items = category != null
                        ? ShopRegistry.getByCategory(category)
                        : new ArrayList<>(ShopRegistry.getAllItems());

                if (items.isEmpty()) {
                    sender.sendMessage("§c[Байер] Предметы не найдены" + (category != null ? " в категории '" + category + "'" : "") + ".");
                    return true;
                }
                sender.sendMessage("§6[Байер] §7Предметы" + (category != null ? " (§b" + category + "§7)" : "") + ":");
                for (ShopItem it : items) {
                    sender.sendMessage("  §f" + it.getId() + " §7— §e" + it.getDisplayName() +
                            " §8[" + it.getCategory() + "] §7Склад: §f" +
                            data.getServerStock(it.getId()) + "§7/§f" + it.getServerLimit());
                }
            }

            default -> {
                sender.sendMessage("§6[Байер] §7Команды:");
                sender.sendMessage("  §f/buyer §7— открыть магазин");
                sender.sendMessage("  §f/buyer balance §7— ваш баланс");
                sender.sendMessage("  §f/buyer info <id> §7— информация о предмете");
                sender.sendMessage("  §f/buyer list [категория] §7— список предметов");
                if (sender.hasPermission("buyer.admin")) {
                    sender.sendMessage("  §c/buyer reload §7— перезагрузить items.yml §c(admin)");
                    sender.sendMessage("  §c/buyer reset §7— сбросить склад §c(admin)");
                    sender.sendMessage("  §c/buyer setstock <id> <n> §7— задать склад §c(admin)");
                    sender.sendMessage("  §c/buyer give <игрок> <n> §7— выдать монеты §c(admin)");
                    sender.sendMessage("  §c/buyer set <игрок> <n> §7— установить монеты §c(admin)");
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        List<String> result = new ArrayList<>();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("balance", "info", "list"));
            if (sender.hasPermission("buyer.admin"))
                subs.addAll(List.of("give", "set", "reset", "reload", "setstock"));
            String typed = args[0].toLowerCase();
            subs.stream().filter(s -> s.startsWith(typed)).forEach(result::add);

        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("give") || sub.equals("set")) {
                plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                        .forEach(result::add);
            } else if (sub.equals("info") || sub.equals("setstock")) {
                String typed = args[1].toLowerCase();
                ShopRegistry.getAllIds().stream()
                        .filter(id -> id.startsWith(typed))
                        .forEach(result::add);
            } else if (sub.equals("list")) {
                String typed = args[1].toLowerCase();
                ShopRegistry.getCategories().stream()
                        .filter(c -> c.toLowerCase().startsWith(typed))
                        .forEach(result::add);
            }
        }

        return result;
    }
}
