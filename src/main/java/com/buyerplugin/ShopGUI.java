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
 *
 * Структура окна (54 слота — 6 рядов × 9):
 *
 *  Строки 0-4 (слоты 0-44): товары (7 товаров × 4 ряда = 28 штук)
 *     Слоты 10-16, 19-25, 28-34, 37-43 — товары
 *     Слоты по краям (0-9, 17-18, 26-27, 35-36, 44) — декоративные рамки
 *
 *  Строка 5 (слоты 45-53): навигация/категории
 *     45 — кнопка НАЗАД/пред. страница
 *     46 — категория «Руда»
 *     47 — категория «Лут с мобов»
 *     48 — категория «Фермерство»
 *     49 — категория «Еда»
 *     50 — (пусто)
 *     51 — инфо: время сброса + баланс
 *     52 — (пусто)
 *     53 — кнопка ВПЕРЁД/след. страница
 */
public class ShopGUI implements Listener {

    // ---- слоты товаров ----
    private static final int[] ITEM_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    }; // 28 слотов для товаров

    private static final int SLOT_PREV_PAGE = 45;
    private static final int SLOT_CAT_ORE   = 46;
    private static final int SLOT_CAT_MOB   = 47;
    private static final int SLOT_CAT_FARM  = 48;
    private static final int SLOT_CAT_FOOD  = 49;
    private static final int SLOT_INFO      = 51;
    private static final int SLOT_NEXT_PAGE = 53;

    // ---- Уникальный тег для идентификации нашего GUI ----
    // ИСПРАВЛЕНО: убраны §v и §n которые ломали отображение.
    // Используем корректный невидимый тег через §r§0 в конце.
    private static final String GUI_TAG = "\u00a7r\u00a70\u00a7rBayerShopGUI\u00a7r";

    private static final ZoneId            KYIV = ZoneId.of("Europe/Kiev");
    private static final DateTimeFormatter FMT  = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final BuyerPlugin plugin;
    private final ShopData    data;

    // uuid → состояние меню игрока
    private final Map<UUID, PlayerMenuState> openMenus = new HashMap<>();

    public ShopGUI(BuyerPlugin plugin, ShopData data) {
        this.plugin = plugin;
        this.data   = data;
    }

    // ================================================================= открытие/закрытие
    public void openMainMenu(Player player) {
        PlayerMenuState state = new PlayerMenuState("Руда", 0);
        openMenus.put(player.getUniqueId(), state);
        openCategory(player, state);
    }

    private void openCategory(Player player, PlayerMenuState state) {
        state.isRefreshing = true;
        Inventory inv = buildInventory(player, state);
        player.openInventory(inv);
        state.inventory    = inv;
        state.isRefreshing = false;
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

        // ---- КРАСИВОЕ НАЗВАНИЕ GUI (ИСПРАВЛЕНО) ----
        // Используем Adventure API для красивого заголовка без §-артефактов
        Component title = buildTitle(state);
        Inventory inv = Bukkit.createInventory(null, 54, title);

        // ---- РАМКА: чёрное стекло по периметру ----
        ItemStack blackPane  = makeDecorPane(Material.BLACK_STAINED_GLASS_PANE,  " ");
        ItemStack purplePane = makeDecorPane(Material.PURPLE_STAINED_GLASS_PANE, " ");
        ItemStack grayPane   = makeDecorPane(Material.GRAY_STAINED_GLASS_PANE,   " ");

        // Верхний ряд (0-8)
        for (int i = 0; i <= 8; i++) {
            inv.setItem(i, i == 0 || i == 8 ? purplePane : blackPane);
        }
        // Левая / правая стенки рядов 1-4
        for (int row = 1; row <= 4; row++) {
            int base = row * 9;
            inv.setItem(base,     purplePane);
            inv.setItem(base + 8, purplePane);
            // Разделитель между левой стенкой и товарами (слот +1)
            inv.setItem(base + 1, grayPane);
            // Разделитель между товарами и правой стенкой (слот +7)
            inv.setItem(base + 7, grayPane);
        }
        // Нижняя панель (45-53) — фон для кнопок
        for (int i = 45; i <= 53; i++) {
            inv.setItem(i, blackPane);
        }

        // ---- ТОВАРЫ ----
        Economy eco = plugin.getEconomy();
        List<ShopItem> items = ShopRegistry.getByCategory(state.category);
        int startIndex = state.page * ITEM_SLOTS.length;
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            int idx = startIndex + i;
            if (idx >= items.size()) break;
            inv.setItem(ITEM_SLOTS[i], makeShopItemIcon(items.get(idx), player, eco));
        }

        // ---- КНОПКИ КАТЕГОРИЙ (стильные) ----
        inv.setItem(SLOT_CAT_ORE,  makeCategoryButton("⛏ Руда",        Material.DIAMOND_ORE,    state.category.equals("Руда")));
        inv.setItem(SLOT_CAT_MOB,  makeCategoryButton("⚔ Лут с мобов", Material.ROTTEN_FLESH,   state.category.equals("Лут с мобов")));
        inv.setItem(SLOT_CAT_FARM, makeCategoryButton("🌾 Фермерство",  Material.WHEAT,           state.category.equals("Фермерство")));
        inv.setItem(SLOT_CAT_FOOD, makeCategoryButton("🍖 Еда",         Material.COOKED_BEEF,     state.category.equals("Еда")));

        // ---- ПАГИНАЦИЯ ----
        int maxPages = Math.max(1, (int) Math.ceil((double) items.size() / ITEM_SLOTS.length));
        if (state.page > 0) {
            inv.setItem(SLOT_PREV_PAGE, makeNavButton(
                    Material.SPECTRAL_ARROW,
                    "§b« Назад",
                    "§7Страница §f" + state.page + " §7из §f" + maxPages
            ));
        } else {
            inv.setItem(SLOT_PREV_PAGE, makeDecorPane(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        if (state.page < maxPages - 1) {
            inv.setItem(SLOT_NEXT_PAGE, makeNavButton(
                    Material.SPECTRAL_ARROW,
                    "§b»  Вперёд",
                    "§7Страница §f" + (state.page + 2) + " §7из §f" + maxPages
            ));
        } else {
            inv.setItem(SLOT_NEXT_PAGE, makeDecorPane(Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        // ---- ИНФО-БЛОК (баланс + сброс) ----
        String resetTime = FMT.format(data.getNextResetTime().atZone(KYIV));
        double balance   = eco.getBalance(player);
        inv.setItem(SLOT_INFO, makeInfoButton(eco.format(balance), resetTime));

        return inv;
    }

    // ================================================================= построение заголовка

    /**
     * Красивый заголовок без артефактов §v/§n.
     * Формат: ✦ Байер | Категория  (страница X)
     */
    private Component buildTitle(PlayerMenuState state) {
        // Скрытый идентификатор вшит как невидимый текст цветом == фону (чёрный на чёрном)
        // Это гарантирует уникальность без артефактов в названии
        return Component.empty()
                // Невидимый тег-идентификатор
                .append(Component.text(GUI_TAG)
                        .color(TextColor.fromHexString("#000000"))
                        .decoration(TextDecoration.ITALIC, false))
                // «✦ »
                .append(Component.text("✦ ")
                        .color(TextColor.fromHexString("#aa00ff"))
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false))
                // «Байер»
                .append(Component.text("Байер")
                        .color(TextColor.fromHexString("#ffffff"))
                        .decoration(TextDecoration.BOLD, true)
                        .decoration(TextDecoration.ITALIC, false))
                // « | »
                .append(Component.text(" | ")
                        .color(TextColor.fromHexString("#555555"))
                        .decoration(TextDecoration.BOLD, false)
                        .decoration(TextDecoration.ITALIC, false))
                // Название категории
                .append(Component.text(state.category)
                        .color(TextColor.fromHexString("#ffaa00"))
                        .decoration(TextDecoration.BOLD, false)
                        .decoration(TextDecoration.ITALIC, false));
    }

    // ================================================================= идентификация GUI
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inv = event.getInventory();
        if (!isOurGUI(inv)) return;

        event.setCancelled(true);

        PlayerMenuState state = openMenus.get(player.getUniqueId());
        if (state == null) return;

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
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                refreshLater(player);
            }
            return;
        }

        // ---- Переключение категорий ----
        if (slot == SLOT_CAT_ORE)  { switchCategory(player, state, "Руда");        return; }
        if (slot == SLOT_CAT_MOB)  { switchCategory(player, state, "Лут с мобов"); return; }
        if (slot == SLOT_CAT_FARM) { switchCategory(player, state, "Фермерство");  return; }
        if (slot == SLOT_CAT_FOOD) { switchCategory(player, state, "Еда");         return; }

        // ---- Клик на товар ----
        int itemSlotIndex = -1;
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] == slot) { itemSlotIndex = i; break; }
        }
        if (itemSlotIndex < 0) return;

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
        if (state.isRefreshing) return;
        openMenus.remove(player.getUniqueId());
    }

    private void switchCategory(Player player, PlayerMenuState state, String category) {
        if (!state.category.equals(category)) {
            state.category = category;
            state.page     = 0;
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            refreshLater(player);
        }
    }

    /** Проверяет, что открытый инвентарь принадлежит нашему GUI по тегу в заголовке. */
    private boolean isOurGUI(Inventory inv) {
        for (PlayerMenuState s : openMenus.values()) {
            if (inv.equals(s.inventory)) return true;
        }
        return false;
    }

    // ================================================================= действия
    private void handleBuy(Player player, ShopItem item, int amount, PlayerMenuState state) {
        Economy eco = plugin.getEconomy();
        if (item.getBuyPrice() == -1) {
            player.sendMessage("§c✖ §7Этот предмет §cнельзя купить§7!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        double totalCost = item.getBuyPrice() * amount;
        double balance   = eco.getBalance(player);
        if (balance < totalCost) {
            player.sendMessage("§c✖ §7Недостаточно средств! Нужно §e" + eco.format(totalCost)
                    + "§7, у вас §e" + eco.format(balance) + "§7.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        eco.withdrawPlayer(player, totalCost);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(item.getMaterial(), amount));
        leftover.values().forEach(v -> player.getWorld().dropItemNaturally(player.getLocation(), v));

        player.sendMessage("§a✔ §7Куплено §f" + amount + "x §e" + item.getDisplayName()
                + " §7за §6" + eco.format(totalCost) + "§7. Баланс: §6" + eco.format(eco.getBalance(player)));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        refreshLater(player);
    }

    private void handleSell(Player player, ShopItem item, int amount, PlayerMenuState state) {
        Economy eco        = plugin.getEconomy();
        int serverSold     = data.getServerSold(item.getId());
        int serverLimit    = item.getServerLimit();
        if (serverSold >= serverLimit) {
            player.sendMessage("§c✖ §7Серверный лимит продаж исчерпан! §8(§c"
                    + serverLimit + "§7/§c" + serverLimit + "§8)");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        int canSell  = Math.min(amount, serverLimit - serverSold);
        int hasAmount = countItem(player, item.getMaterial());
        if (hasAmount == 0) {
            player.sendMessage("§c✖ §7У вас нет §e" + item.getDisplayName() + "§7!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        canSell = Math.min(canSell, hasAmount);
        double totalEarned = item.getSellPrice() * canSell;

        removeItem(player, item.getMaterial(), canSell);
        eco.depositPlayer(player, totalEarned);
        data.addServerSold(item.getId(), canSell);
        data.addContribution(player.getUniqueId(), item.getId(), canSell);

        player.sendMessage("§a✔ §7Продано §f" + canSell + "x §e" + item.getDisplayName()
                + " §7за §6" + eco.format(totalEarned) + "§7. Баланс: §6" + eco.format(eco.getBalance(player)));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

        if (canSell < amount) {
            player.sendMessage("§e⚠ §7Продано меньше, чем запрошено: лимит или нехватка предметов.");
        }
        refreshLater(player);
    }

    // ================================================================= утилиты инвентаря
    private int countItem(Player player, Material mat) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == mat) count += stack.getAmount();
        }
        return count;
    }

    private void removeItem(Player player, Material mat, int amount) {
        ItemStack[] contents = player.getInventory().getContents();
        int remaining = amount;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (stack == null || stack.getType() != mat) continue;
            if (stack.getAmount() <= remaining) {
                remaining -= stack.getAmount();
                contents[i] = null;
            } else {
                stack.setAmount(stack.getAmount() - remaining);
                remaining = 0;
            }
        }
        player.getInventory().setContents(contents);
    }

    // ================================================================= создание иконок

    /** Иконка товара с красивым лором в стиле крупных проектов. */
    private ItemStack makeShopItemIcon(ShopItem item, Player player, Economy eco) {
        int sold  = data.getServerSold(item.getId());
        int limit = item.getServerLimit();
        boolean full = sold >= limit;

        // Прогресс-бар лимита
        String progressBar = buildProgressBar(sold, limit, 10);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        // Цены
        if (item.getBuyPrice() != -1) {
            lore.add(txt("  §7▸ Купить:  §a" + eco.format(item.getBuyPrice()) + " §7за 1 шт."));
            lore.add(txt("  §7▸ Купить:  §a" + eco.format(item.getBuyPrice() * 64) + " §7за 64 шт."));
        } else {
            lore.add(txt("  §7▸ Купить:  §c✖ Недоступно"));
        }
        lore.add(txt("  §7▸ Продать: §e" + eco.format(item.getSellPrice()) + " §7за 1 шт."));
        lore.add(txt("  §7▸ Продать: §e" + eco.format(item.getSellPrice() * 64) + " §7за 64 шт."));

        lore.add(Component.empty());

        // Серверный лимит
        lore.add(txt("  §7Серверный лимит:"));
        lore.add(txt("  " + progressBar + " §f" + sold + "§7/§f" + limit));
        lore.add(Component.empty());

        if (full) {
            lore.add(txt("  §c§l⚠ ЛИМИТ ИСЧЕРПАН"));
            lore.add(txt("  §7Сброс каждую неделю."));
        } else {
            if (item.getBuyPrice() != -1) {
                lore.add(txt("  §a§l[ЛКМ]§r§7 Купить ×1"));
                lore.add(txt("  §b§l[Shift+ЛКМ]§r§7 Купить ×64"));
            }
            lore.add(txt("  §e§l[ПКМ]§r§7 Продать ×1"));
            lore.add(txt("  §6§l[Shift+ПКМ]§r§7 Продать ×64"));
        }
        lore.add(Component.empty());

        // Цвет названия
        String nameColor = full ? "§c" : "§f";
        String prefix    = full ? "§c⚠ " : "§b✦ ";
        return makeItemWithLore(item.getMaterial(), prefix + nameColor + item.getDisplayName(), lore);
    }

    /** Строит текстовый прогресс-бар. */
    private String buildProgressBar(int current, int max, int length) {
        if (max <= 0) return "§7[§c!!§7]";
        int filled = (int) Math.round((double) current / max * length);
        filled = Math.max(0, Math.min(filled, length));
        StringBuilder sb = new StringBuilder("§7[");
        for (int i = 0; i < length; i++) {
            if (i < filled) {
                sb.append(filled >= length ? "§c" : "§e").append("|");
            } else {
                sb.append("§8|");
            }
        }
        sb.append("§7]");
        return sb.toString();
    }

    /** Красивая кнопка категории. */
    private ItemStack makeCategoryButton(String name, Material mat, boolean active) {
        Material display = active ? Material.LIME_STAINED_GLASS_PANE : mat;
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        if (active) {
            lore.add(txt("  §a§l► Выбранная категория"));
        } else {
            lore.add(txt("  §7Нажмите, чтобы перейти"));
        }
        lore.add(Component.empty());
        String prefix = active ? "§a§l► §r" : "§7";
        return makeItemWithLore(display, prefix + name, lore);
    }

    /** Кнопка пагинации (вперёд/назад). */
    private ItemStack makeNavButton(Material mat, String name, String hint) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(txt("  " + hint));
        lore.add(Component.empty());
        return makeItemWithLore(mat, name, lore);
    }

    /** Информационная кнопка (баланс + сброс). */
    private ItemStack makeInfoButton(String balance, String resetTime) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(txt("  §7Ваш баланс:"));
        lore.add(txt("  §6✦ " + balance));
        lore.add(Component.empty());
        lore.add(txt("  §7Следующий сброс:"));
        lore.add(txt("  §b⏰ " + resetTime));
        lore.add(Component.empty());
        return makeItemWithLore(Material.CLOCK, "§e§l✦ Информация", lore);
    }

    /** Декоративная стеклянная панель. */
    private ItemStack makeDecorPane(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Создаёт предмет с именем. */
    private ItemStack makeItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Создаёт предмет с именем и лором (Adventure Component). */
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

    /** Хелпер: создаёт Component из строки с §-кодами без курсива. */
    private Component txt(String legacy) {
        return Component.text(legacy).decoration(TextDecoration.ITALIC, false);
    }

    // ================================================================= внутренний класс состояния
    static class PlayerMenuState {
        String    category;
        int       page;
        Inventory inventory;
        boolean   isRefreshing = false; // ← КЛЮЧЕВОЙ ФЛАГ

        PlayerMenuState(String category, int page) {
            this.category = category;
            this.page     = page;
        }
    }
}
