package com.buyerplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * GUI магазина Байера — красивое оформление в стиле крупных проектов.
 */
public class ShopGUI implements Listener {

    private static final int[] ITEM_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private static final int SLOT_PREV_PAGE = 45;
    private static final int SLOT_CAT_ORE   = 46;
    private static final int SLOT_CAT_MOB   = 47;
    private static final int SLOT_CAT_FARM  = 48;
    private static final int SLOT_CAT_FOOD  = 49;
    private static final int SLOT_INFO      = 51;
    private static final int SLOT_NEXT_PAGE = 53;

    private static final ZoneId KYIV = ZoneId.of("Europe/Kiev");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final BuyerPlugin plugin;
    private final ShopData data;

    private final Map<UUID, PlayerMenuState> openMenus = new HashMap<>();

    public ShopGUI(BuyerPlugin plugin, ShopData data) {
        this.plugin = plugin;
        this.data = data;
    }

    // ================================================================= открытие/закрытие

    public void openMainMenu(Player player) {
        PlayerMenuState state = new PlayerMenuState("Руда", 0);
        openMenus.put(player.getUniqueId(), state);
        openCategory(player, state);
    }

    private void openCategory(Player player, PlayerMenuState state) {
        Inventory inv = buildInventory(player, state);
        state.inventory = inv;
        player.openInventory(inv);
    }

    private void refreshLater(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            PlayerMenuState state = openMenus.get(player.getUniqueId());
            if (state != null) {
                openCategory(player, state);
            }
        });
    }

    // ================================================================= построение инвентаря

    private Inventory buildInventory(Player player, PlayerMenuState state) {
        Component title = buildTitle(state);
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // ---- РАМКА ----
        ItemStack blackPane = makeDecorPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack purplePane = makeDecorPane(Material.PURPLE_STAINED_GLASS_PANE, " ");
        ItemStack grayPane = makeDecorPane(Material.GRAY_STAINED_GLASS_PANE, " ");

        for (int i = 0; i < 9; i++) inv.setItem(i, blackPane);
        for (int i = 45; i < 54; i++) inv.setItem(i, blackPane);

        // ---- ТОВАРЫ ----
        Economy eco = plugin.getEconomy();
        List<ShopItem> items = ShopRegistry.getByCategory(state.category);
        int startIndex = state.page * ITEM_SLOTS.length;
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            int idx = startIndex + i;
            if (idx >= items.size()) break;
            inv.setItem(ITEM_SLOTS[i], makeShopItemIcon(items.get(idx), eco));
        }

        // ---- КНОПКИ КАТЕГОРИЙ ----
        inv.setItem(SLOT_CAT_ORE,  makeCategoryButton("⛏ Руда",        Material.DIAMOND_ORE,    state.category.equals("Руда")));
        inv.setItem(SLOT_CAT_MOB,  makeCategoryButton("⚔ Лут с мобов", Material.ROTTEN_FLESH,   state.category.equals("Лут с мобов")));
        inv.setItem(SLOT_CAT_FARM, makeCategoryButton("🌾 Фермерство",  Material.WHEAT,          state.category.equals("Фермерство")));
        inv.setItem(SLOT_CAT_FOOD, makeCategoryButton("🍖 Еда",        Material.COOKED_BEEF,    state.category.equals("Еда")));

        // ---- ПАГИНАЦИЯ ----
        int maxPages = Math.max(1, (int) Math.ceil((double) items.size() / ITEM_SLOTS.length));
        if (state.page > 0) {
            inv.setItem(SLOT_PREV_PAGE, makeNavButton(
                    Material.SPECTRAL_ARROW, "§b« Назад",
                    "§7Страница §f" + (state.page + 1) + " §7из §f" + maxPages
            ));
        } else {
            inv.setItem(SLOT_PREV_PAGE, grayPane);
        }
        if (state.page < maxPages - 1) {
            inv.setItem(SLOT_NEXT_PAGE, makeNavButton(
                    Material.SPECTRAL_ARROW, "§bВперёд »",
                    "§7Страница §f" + (state.page + 1) + " §7из §f" + maxPages
            ));
        } else {
            inv.setItem(SLOT_NEXT_PAGE, grayPane);
        }

        // ---- ИНФОРМАЦИЯ ----
        String balance = eco.format(eco.getBalance(player));
        String resetTime = data.getNextResetTime().atZone(KYIV).format(FMT);

        // ✅ ИСПРАВЛЕНИЕ #2: Показываем реальную дату последнего сброса
        String lastResetStr;
        Instant lastReset = data.getLastResetTime();
        if (lastReset != null) {
            lastResetStr = lastReset.atZone(KYIV).format(FMT);
        } else {
            lastResetStr = "§7Ещё не было";
        }

        inv.setItem(SLOT_INFO, makeInfoButton(balance, resetTime, lastResetStr));

        return inv;
    }

    private Component buildTitle(PlayerMenuState state) {
        return Component.text("Байер § " + state.category + " §7| §f" + (state.page + 1))
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false);
    }

    // ================================================================= обработка кликов

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        PlayerMenuState state = openMenus.get(player.getUniqueId());
        if (state == null) return;
        if (!event.getInventory().equals(state.inventory)) return;

        event.setCancelled(true);
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        ClickType click = event.getClick();

        // ---- Навигация по страницам ----
        if (slot == SLOT_PREV_PAGE) {
            if (state.page > 0) {
                state.page--;
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                refreshLater(player);
            }
            return;
        }
        if (slot == SLOT_NEXT_PAGE) {
            List<ShopItem> items = ShopRegistry.getByCategory(state.category);
            int maxPages = Math.max(1, (int) Math.ceil((double) items.size() / ITEM_SLOTS.length));
            if (state.page < maxPages - 1) {
                state.page++;
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
                refreshLater(player);
            }
            return;
        }

        // ---- Категории ----
        if (slot == SLOT_CAT_ORE)  { switchCategory(player, state, "Руда");        return; }
        if (slot == SLOT_CAT_MOB)  { switchCategory(player, state, "Лут с мобов"); return; }
        if (slot == SLOT_CAT_FARM) { switchCategory(player, state, "Фермерство");   return; }
        if (slot == SLOT_CAT_FOOD) { switchCategory(player, state, "Еда");          return; }

        // ---- Товары ----
        int itemSlotIndex = -1;
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] == slot) { itemSlotIndex = i; break; }
        }
        if (itemSlotIndex == -1) return;

        List<ShopItem> items = ShopRegistry.getByCategory(state.category);
        int idx = state.page * ITEM_SLOTS.length + itemSlotIndex;
        if (idx >= items.size()) return;

        ShopItem item = items.get(idx);
        boolean isShift = click.isShiftClick();
        int amount = isShift ? 64 : 1;

        if (click.isLeftClick()) {
            handleBuy(player, item, amount, state);
        } else if (click.isRightClick()) {
            handleSell(player, item, amount, state);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        PlayerMenuState state = openMenus.get(player.getUniqueId());
        if (state == null) return;
        if (!event.getInventory().equals(state.inventory)) return;
        openMenus.remove(player.getUniqueId());
    }

    private void switchCategory(Player player, PlayerMenuState state, String category) {
        if (!state.category.equals(category)) {
            state.category = category;
            state.page = 0;
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            refreshLater(player);
        }
    }

    // ================================================================= действия

    private void handleBuy(Player player, ShopItem item, int amount, PlayerMenuState state) {
        Economy eco = plugin.getEconomy();
        if (item.getBuyPrice() == -1) {
            player.sendMessage("§c✖ §7Этот предмет §cнельзя купить§7!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        int stock = data.getServerStock(item.getId());
        if (stock <= 0) {
            player.sendMessage("§c✖ §7Байер пуст — покупать нечего!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        int canBuy = Math.min(amount, stock);
        double totalCost = canBuy * item.getBuyPrice();
        double balance = eco.getBalance(player);

        if (balance < totalCost) {
            int afford = (int) (balance / item.getBuyPrice());
            if (afford <= 0) {
                player.sendMessage("§c✖ §7Недостаточно монет! Нужно: §6"
                        + eco.format(totalCost) + "§7, у вас: §6" + eco.format(balance));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            canBuy = Math.min(afford, stock);
            totalCost = canBuy * item.getBuyPrice();
        }

        eco.withdrawPlayer(player, totalCost);
        data.subtractStock(item.getId(), canBuy);

        Map<Integer, ItemStack> leftover = player.getInventory().addItem(
                new ItemStack(item.getMaterial(), canBuy));
        leftover.values().forEach(v ->
                player.getWorld().dropItemNaturally(player.getLocation(), v));

        int newStock = data.getServerStock(item.getId());
        player.sendMessage("§a✔ §7Куплено §f" + canBuy + "x §e" + item.getDisplayName()
                + " §7за §6" + eco.format(totalCost)
                + "§7. Баланс: §6" + eco.format(eco.getBalance(player))
                + "§7. Склад байера: §f" + newStock + "§7/§f" + item.getServerLimit());
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);

        if (canBuy < amount) {
            player.sendMessage("§e⚠ §7Удалось купить только §f" + canBuy
                    + "§7 из §f" + amount + "§7 — склад байера исчерпан.");
        }

        data.save();
        refreshLater(player);
    }

    private void handleSell(Player player, ShopItem item, int amount, PlayerMenuState state) {
        Economy eco = plugin.getEconomy();

        int stock = data.getServerStock(item.getId());
        int limit = item.getServerLimit();
        if (stock >= limit) {
            player.sendMessage("§c✖ §7Байер переполнен — продавать некуда!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        int canSell = Math.min(amount, limit - stock);

        // Проверяем инвентарь игрока
        Material mat = item.getMaterial();
        int available = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != mat) continue;
            available += stack.getAmount();
        }

        if (available <= 0) {
            player.sendMessage("§c✖ §7У вас нет §e" + item.getDisplayName() + "§7!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        int actualSell = Math.min(canSell, available);
        double totalEarn = actualSell * item.getSellPrice();

        // Убираем предметы из инвентаря
        int toRemove = actualSell;
        for (int i = 0; i < contents.length && toRemove > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != mat) continue;
            if (stack.getAmount() <= toRemove) {
                toRemove -= stack.getAmount();
                contents[i] = null;
            } else {
                stack.setAmount(stack.getAmount() - toRemove);
                toRemove = 0;
            }
        }

        eco.depositPlayer(player, totalEarn);
        data.addStock(item.getId(), actualSell, limit);
        data.addContribution(player.getUniqueId(), item.getId(), actualSell);

        int newStock = data.getServerStock(item.getId());
        player.sendMessage("§a✔ §7Продано §f" + actualSell + "x §e" + item.getDisplayName()
                + " §7за §6" + eco.format(totalEarn)
                + "§7. Баланс: §6" + eco.format(eco.getBalance(player))
                + "§7. Склад байера: §f" + newStock + "§7/§f" + limit);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.0f);

        if (actualSell < amount) {
            String reason = available < amount ? "недостаточно предметов" : "байер переполнен";
            player.sendMessage("§e⚠ §7Удалось продать только §f" + actualSell
                    + "§7 из §f" + amount + "§7 — " + reason + ".");
        }

        data.save();
        refreshLater(player);
    }

    // ================================================================= UI-хелперы

    private ItemStack makeShopItemIcon(ShopItem item, Economy eco) {
        int stock = data.getServerStock(item.getId());
        int limit = item.getServerLimit();
        boolean empty = (stock <= 0);
        boolean full = (stock >= limit);
        String progressBar = buildProgressBar(stock, limit, 10);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        if (item.getBuyPrice() != -1) {
            if (empty) {
                lore.add(txt("  §c✖ Купить: нет ресурса в байере"));
            } else {
                lore.add(txt("  §7▸ Купить: §a" + eco.format(item.getBuyPrice()) + " §7за 1 шт."));
                lore.add(txt("  §7▸ Купить: §a" + eco.format(item.getBuyPrice() * 64) + " §7за 64 шт."));
            }
        } else {
            lore.add(txt("  §7▸ Купить: §c✖ Недоступно"));
        }

        if (full) {
            lore.add(txt("  §c✖ Продать: байер переполнен"));
        } else {
            lore.add(txt("  §7▸ Продать: §e" + eco.format(item.getSellPrice()) + " §7за 1 шт."));
            lore.add(txt("  §7▸ Продать: §e" + eco.format(item.getSellPrice() * 64) + " §7за 64 шт."));
        }

        lore.add(Component.empty());
        lore.add(txt("  §7Склад байера:"));
        lore.add(txt("  " + progressBar + " §f" + stock + "§7/§f" + limit));
        lore.add(Component.empty());

        if (empty) {
            lore.add(txt("  §c§l⚠ БАЙЕР ПУСТ — покупка невозможна"));
            lore.add(txt("  §7Продайте ресурс, чтобы пополнить байер."));
            if (!full) {
                lore.add(Component.empty());
                lore.add(txt("  §e§l[ПКМ]§r§7 Продать ×1"));
                lore.add(txt("  §6§l[Shift+ПКМ]§r§7 Продать ×64"));
            }
        } else if (full) {
            lore.add(txt("  §c§l⚠ БАЙЕР ПОЛОН — продажа невозможна"));
            lore.add(txt("  §7Купите ресурс, чтобы освободить место."));
            if (item.getBuyPrice() != -1) {
                lore.add(Component.empty());
                lore.add(txt("  §a§l[ЛКМ]§r§7 Купить ×1"));
                lore.add(txt("  §b§l[Shift+ЛКМ]§r§7 Купить ×64"));
            }
        } else {
            if (item.getBuyPrice() != -1) {
                lore.add(txt("  §a§l[ЛКМ]§r§7 Купить ×1"));
                lore.add(txt("  §b§l[Shift+ЛКМ]§r§7 Купить ×64"));
            }
            lore.add(txt("  §e§l[ПКМ]§r§7 Продать ×1"));
            lore.add(txt("  §6§l[Shift+ПКМ]§r§7 Продать ×64"));
        }

        lore.add(Component.empty());

        String prefix = empty ? "§c✖ " : (full ? "§6■ " : "§b✦ ");
        String nameColor = empty ? "§c" : (full ? "§6" : "§f");
        return makeItemWithLore(item.getMaterial(), prefix + nameColor + item.getDisplayName(), lore);
    }

    private String buildProgressBar(int current, int max, int length) {
        if (max <= 0) return "§8[]" ;
        int filled = (int) ((double) current / max * length);
        StringBuilder sb = new StringBuilder("§7[");
        for (int i = 0; i < length; i++) {
            if (i < filled) {
                String color = (filled >= length) ? "§c" : (filled >= length * 0.7) ? "§e" : "§a";
                sb.append(color).append("|");
            } else {
                sb.append("§8|");
            }
        }
        sb.append("§7]");
        return sb.toString();
    }

    private ItemStack makeCategoryButton(String name, Material mat, boolean active) {
        Material display = active ? Material.LIME_STAINED_GLASS_PANE : mat;
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(txt(active ? "  §a§l► Выбранная категория" : "  §7Нажмите, чтобы перейти"));
        lore.add(Component.empty());
        String prefix = active ? "§a§l► §r" : "§7";
        return makeItemWithLore(display, prefix + name, lore);
    }

    private ItemStack makeNavButton(Material mat, String name, String hint) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(txt("  " + hint));
        lore.add(Component.empty());
        return makeItemWithLore(mat, name, lore);
    }

    // ✅ ИСПРАВЛЕНИЕ #2: Информационная кнопка теперь показывает дату последнего сброса
    private ItemStack makeInfoButton(String balance, String resetTime, String lastResetTime) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(txt("  §7Ваш баланс:"));
        lore.add(txt("  §6✦ " + balance));
        lore.add(Component.empty());
        lore.add(txt("  §7Следующий сброс:"));
        lore.add(txt("  §b⏰ " + resetTime));
        lore.add(Component.empty());
        lore.add(txt("  §7Последний сброс:"));
        lore.add(txt("  §a✅ " + lastResetTime));
        lore.add(Component.empty());
        return makeItemWithLore(Material.CLOCK, "§e§l✦ Информация", lore);
    }

    private ItemStack makeDecorPane(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeItemWithLore(Material mat, String name, List<Component> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private Component txt(String legacy) {
        return Component.text(legacy).decoration(TextDecoration.ITALIC, false);
    }

    // ================================================================= внутренний класс состояния

    static class PlayerMenuState {
        String category;
        int page;
        Inventory inventory;

        PlayerMenuState(String category, int page) {
            this.category = category;
            this.page = page;
        }
    }
}