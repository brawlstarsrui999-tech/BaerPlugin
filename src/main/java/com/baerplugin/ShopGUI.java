package com.baerplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
 * GUI магазина Баера.
 *
 * Структура окна (54 слота — 6 рядов × 9):
 *
 *  Строки 0-4 (слоты 0-44): товары текущей категории (до 25 штук на странице, слоты 10-43 без рамки)
 *  Строка 5 (слоты 45-53): навигация
 *    45 — кнопка «назад / предыдущая страница»
 *    46 — Руда
 *    47 — Лут с мобов
 *    48 — Фермерство
 *    49 — Еда
 *    50 — (пусто)
 *    51 — инфо: время сброса + баланс
 *    52 — (пусто)
 *    53 — следующая страница
 *
 * Клик по товару:
 *   ЛКМ        — купить 1
 *   ПКМ        — продать 1
 *   Shift+ЛКМ  — купить 64
 *   Shift+ПКМ  — продать 64
 */
public class ShopGUI implements Listener {

    // ---- константы слотов ----
    private static final int[] ITEM_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    }; // 28 слотов для товаров

    private static final int SLOT_PREV_PAGE   = 45;
    private static final int SLOT_CAT_ORE     = 46;
    private static final int SLOT_CAT_MOB     = 47;
    private static final int SLOT_CAT_FARM    = 48;
    private static final int SLOT_CAT_FOOD    = 49;
    private static final int SLOT_INFO        = 51;
    private static final int SLOT_NEXT_PAGE   = 53;

    private static final ZoneId KYIV = ZoneId.of("Europe/Kiev");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final BaerPlugin plugin;
    private final ShopData   data;

    // uuid → открытое меню
    private final Map<UUID, PlayerMenuState> openMenus = new HashMap<>();

    public ShopGUI(BaerPlugin plugin, ShopData data) {
        this.plugin = plugin;
        this.data   = data;
    }

    // ======================================================== открытие / закрытие
    public void openMainMenu(Player player) {
        PlayerMenuState state = new PlayerMenuState("Руда", 0);
        openMenus.put(player.getUniqueId(), state);
        openCategory(player, state);
    }

    private void openCategory(Player player, PlayerMenuState state) {
        Inventory inv = buildInventory(player, state);
        player.openInventory(inv);
        state.inventory = inv;
    }

    // ======================================================== построение GUI
    private Inventory buildInventory(Player player, PlayerMenuState state) {
        String title = "§6§lБаер §8» §e" + state.category;
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(title));

        // Заполняем рамку серыми стёклами
        ItemStack glass = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, glass);
        // Очищаем центральные слоты
        for (int slot : ITEM_SLOTS) inv.setItem(slot, null);

        // Товары
        List<ShopItem> items = ShopRegistry.getByCategory(state.category);
        int startIndex = state.page * ITEM_SLOTS.length;
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            int idx = startIndex + i;
            if (idx >= items.size()) break;
            inv.setItem(ITEM_SLOTS[i], makeShopItemIcon(items.get(idx), player));
        }

        // Кнопки категорий
        inv.setItem(SLOT_CAT_ORE,  makeCategoryButton("§aРуда",       Material.DIAMOND,       state.category.equals("Руда")));
        inv.setItem(SLOT_CAT_MOB,  makeCategoryButton("§cЛут с мобов",Material.ROTTEN_FLESH,  state.category.equals("Лут с мобов")));
        inv.setItem(SLOT_CAT_FARM, makeCategoryButton("§2Фермерство",  Material.WHEAT,         state.category.equals("Фермерство")));
        inv.setItem(SLOT_CAT_FOOD, makeCategoryButton("§6Еда",         Material.COOKED_BEEF,   state.category.equals("Еда")));

        // Предыдущая страница
        if (state.page > 0) {
            inv.setItem(SLOT_PREV_PAGE, makeItem(Material.ARROW, "§e◀ Предыдущая страница"));
        } else {
            inv.setItem(SLOT_PREV_PAGE, glass);
        }

        // Следующая страница
        int maxPages = (int) Math.ceil((double) items.size() / ITEM_SLOTS.length);
        if (state.page < maxPages - 1) {
            inv.setItem(SLOT_NEXT_PAGE, makeItem(Material.ARROW, "§eСледующая страница ▶"));
        } else {
            inv.setItem(SLOT_NEXT_PAGE, glass);
        }

        // Инфо-кнопка
        inv.setItem(SLOT_INFO, makeInfoItem(player));

        return inv;
    }

    // ======================================================== иконка товара
    private ItemStack makeShopItemIcon(ShopItem item, Player player) {
        ItemStack stack = new ItemStack(item.getMaterial());
        ItemMeta meta = stack.getItemMeta();

        // Название
        meta.displayName(Component.text("§e§l" + item.getDisplayName())
                .decoration(TextDecoration.ITALIC, false));

        // Лор
        List<Component> lore = new ArrayList<>();
        int sold      = data.getServerSold(item.getId());
        int limit     = item.getWeeklyLimit();
        int remaining = data.getRemainingLimit(item.getId());

        lore.add(Component.text("§7Категория: §f" + item.getCategory()).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("§a✦ Продать Баеру:").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  §f" + item.getSellPrice() + " монет §7за 1 шт.").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(" ").decoration(TextDecoration.ITALIC, false));

        if (item.canBuy()) {
            if (sold > 0) {
                lore.add(Component.text("§c✦ Купить у Баера:").decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("  §f" + item.getBuyPrice() + " монет §7за 1 шт.").decoration(TextDecoration.ITALIC, false));
                lore.add(Component.text("  §7В наличии: §e" + sold + " шт.").decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.text("§8Купить: §7нет в наличии").decoration(TextDecoration.ITALIC, false));
            }
        } else {
            lore.add(Component.text("§8Покупка недоступна").decoration(TextDecoration.ITALIC, false));
        }

        lore.add(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("§7Лимит недели: §e" + sold + "§7/§e" + limit).decoration(TextDecoration.ITALIC, false));

        // Прогресс-бар лимита
        lore.add(buildProgressBar(sold, limit));

        lore.add(Component.text("§7Осталось: §a" + remaining + " шт.").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("§7Ваш баланс: §6" + data.getBalance(player.getUniqueId()) + " монет").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("§eЛКМ §7— купить 1  §e| §eShift+ЛКМ §7— купить 64").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("§eПКМ §7— продать 1 §e| §eShift+ПКМ §7— продать 64").decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private Component buildProgressBar(int current, int max) {
        int filled = (max == 0) ? 20 : (int) ((double) current / max * 20);
        StringBuilder sb = new StringBuilder("§7[");
        for (int i = 0; i < 20; i++) {
            if (i < filled) sb.append("§c|");
            else           sb.append("§a|");
        }
        sb.append("§7]");
        return Component.text(sb.toString()).decoration(TextDecoration.ITALIC, false);
    }

    // ======================================================== вспомогательные иконки
    private ItemStack makeCategoryButton(String name, Material mat, boolean active) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text((active ? "§f§l» " : "") + name)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        if (active) lore.add(Component.text("§7(текущая категория)").decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack makeInfoItem(Player player) {
        ItemStack stack = new ItemStack(Material.CLOCK);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text("§b§lИнформация").decoration(TextDecoration.ITALIC, false));

        String resetTime = Instant.ofEpochSecond(data.getNextResetEpoch())
                .atZone(KYIV).format(FMT);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("§7Баланс: §6" + data.getBalance(player.getUniqueId()) + " монет")
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("§7Сброс лимитов:").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("§e" + resetTime + " (Киев)").decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack makeItem(Material mat, String name) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        stack.setItemMeta(meta);
        return stack;
    }

    // ======================================================== события
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();
        if (!openMenus.containsKey(uuid)) return;

        event.setCancelled(true); // запрещаем перемещение предметов в GUI

        PlayerMenuState state = openMenus.get(uuid);
        Inventory clickedInv = event.getClickedInventory();
        if (clickedInv == null) return;
        if (!clickedInv.equals(state.inventory)) return; // кликнули в инвентарь игрока

        int slot = event.getSlot();
        ClickType click = event.getClick();

        // ---- Кнопки категорий ----
        if (slot == SLOT_CAT_ORE)  { switchCategory(player, state, "Руда"); return; }
        if (slot == SLOT_CAT_MOB)  { switchCategory(player, state, "Лут с мобов"); return; }
        if (slot == SLOT_CAT_FARM) { switchCategory(player, state, "Фермерство"); return; }
        if (slot == SLOT_CAT_FOOD) { switchCategory(player, state, "Еда"); return; }

        // ---- Навигация по страницам ----
        if (slot == SLOT_PREV_PAGE && state.page > 0) {
            state.page--;
            refreshInventory(player, state);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            return;
        }
        if (slot == SLOT_NEXT_PAGE) {
            List<ShopItem> items = ShopRegistry.getByCategory(state.category);
            int maxPages = (int) Math.ceil((double) items.size() / ITEM_SLOTS.length);
            if (state.page < maxPages - 1) {
                state.page++;
                refreshInventory(player, state);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
            }
            return;
        }

        // ---- Клик по товару ----
        ShopItem clickedItem = getClickedShopItem(slot, state);
        if (clickedItem == null) return;

        boolean isShift = click.isShiftClick();
        boolean isLeft  = click.isLeftClick();
        boolean isRight = click.isRightClick();

        int amount = isShift ? 64 : 1;

        if (isLeft) {
            // Покупка у баера
            handleBuy(player, clickedItem, amount);
        } else if (isRight) {
            // Продажа баеру
            handleSell(player, clickedItem, amount);
        }

        // Обновляем GUI
        refreshInventory(player, state);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            openMenus.remove(player.getUniqueId());
        }
    }

    // ======================================================== логика покупки/продажи
    private void handleBuy(Player player, ShopItem item, int requestedAmount) {
        if (!item.canBuy()) {
            player.sendMessage("§c[Баер] Этот товар нельзя купить!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        int inPool = data.getServerSold(item.getId());
        if (inPool <= 0) {
            player.sendMessage("§c[Баер] В наличии нет " + item.getDisplayName() + "!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        int balance = data.getBalance(player.getUniqueId());
        if (balance < item.getBuyPrice()) {
            player.sendMessage("§c[Баер] Недостаточно монет! (нужно " + item.getBuyPrice() + ", у тебя " + balance + ")");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        int bought = data.buyItem(player.getUniqueId(), item.getId(), requestedAmount);
        if (bought <= 0) {
            player.sendMessage("§c[Баер] Не удалось купить товар.");
            return;
        }

        // Выдаём товар игроку
        ItemStack reward = new ItemStack(item.getMaterial(), bought);
        // Раскладываем по инвентарю, остаток выбрасываем на землю
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(reward);
        for (ItemStack left : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }

        int totalCost = bought * item.getBuyPrice();
        player.sendMessage("§a[Баер] Ты купил §e" + bought + "x " + item.getDisplayName()
                + "§a за §6" + totalCost + " монет§a. Баланс: §6" + data.getBalance(player.getUniqueId()) + " монет");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.2f);
    }

    private void handleSell(Player player, ShopItem item, int requestedAmount) {
        // Считаем, сколько есть у игрока
        int inInventory = countInInventory(player, item.getMaterial());
        if (inInventory <= 0) {
            player.sendMessage("§c[Баер] У тебя нет " + item.getDisplayName() + "!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        int remaining = data.getRemainingLimit(item.getId());
        if (remaining <= 0) {
            player.sendMessage("§c[Баер] Недельный лимит на " + item.getDisplayName() + " исчерпан!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
            return;
        }

        int toSell  = Math.min(requestedAmount, Math.min(inInventory, remaining));
        int accepted = data.sellItem(player.getUniqueId(), item.getId(), toSell);
        if (accepted <= 0) {
            player.sendMessage("§c[Баер] Не удалось продать товар.");
            return;
        }

        // Забираем предметы из инвентаря
        removeFromInventory(player, item.getMaterial(), accepted);

        int earned = accepted * item.getSellPrice();
        player.sendMessage("§a[Баер] Ты продал §e" + accepted + "x " + item.getDisplayName()
                + "§a за §6" + earned + " монет§a. Баланс: §6" + data.getBalance(player.getUniqueId()) + " монет");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 0.8f);
    }

    // ======================================================== вспомогательные методы
    private void switchCategory(Player player, PlayerMenuState state, String category) {
        if (state.category.equals(category)) return;
        state.category = category;
        state.page = 0;
        refreshInventory(player, state);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    private void refreshInventory(Player player, PlayerMenuState state) {
        Inventory newInv = buildInventory(player, state);
        state.inventory = newInv;
        player.openInventory(newInv);
    }

    /** Находит ShopItem по кликнутому слоту */
    private ShopItem getClickedShopItem(int slot, PlayerMenuState state) {
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] == slot) {
                List<ShopItem> items = ShopRegistry.getByCategory(state.category);
                int idx = state.page * ITEM_SLOTS.length + i;
                if (idx < items.size()) return items.get(idx);
                break;
            }
        }
        return null;
    }

    /** Считает количество предмета в инвентаре игрока */
    private int countInInventory(Player player, Material mat) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == mat) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    /** Удаляет amount штук материала из инвентаря игрока */
    private void removeFromInventory(Player player, Material mat, int amount) {
        int toRemove = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && toRemove > 0; i++) {
            ItemStack stack = contents[i];
            if (stack != null && stack.getType() == mat) {
                if (stack.getAmount() <= toRemove) {
                    toRemove -= stack.getAmount();
                    contents[i] = null;
                } else {
                    stack.setAmount(stack.getAmount() - toRemove);
                    toRemove = 0;
                }
            }
        }
        player.getInventory().setContents(contents);
    }

    // ======================================================== вложенный класс состояния
    private static class PlayerMenuState {
        String category;
        int    page;
        Inventory inventory;

        PlayerMenuState(String category, int page) {
            this.category = category;
            this.page = page;
        }
    }
}
