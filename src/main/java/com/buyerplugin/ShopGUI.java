package com.buyerplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
 * GUI магазина Байера.
 *
 * Структура окна (54 слота — 6 рядов × 9):
 *
 *  Строки 0-4 (слоты 0-44): товары (7 товаров × 4 ряда = 28 штук)
 *    Слоты 10-16, 19-25, 28-34, 37-43 — товары
 *
 *  Строка 5 (слоты 45-53): навигация
 *    45 — кнопка НАЗАД/пред. страница
 *    46 — категория «Руда»
 *    47 — категория «Лут с мобов»
 *    48 — категория «Фермерство»
 *    49 — категория «Еда»
 *    50 — (пусто)
 *    51 — инфо: время сброса + баланс
 *    52 — (пусто)
 *    53 — кнопка ВПЕРЁД/след. страница
 *
 * Клики по товару:
 *   ЛКМ        — купить 1
 *   ПКМ        — продать 1
 *   Shift+ЛКМ  — купить 64
 *   Shift+ПКМ  — продать 64
 *
 * ===== ИСПРАВЛЕНИЕ ГЛАВНОЙ ОШИБКИ =====
 * Ключевая проблема оригинала: после первого действия (клика) состояние
 * игрока (PlayerMenuState) терялось, потому что openCategory() пересоздавало
 * инвентарь и Bukkit генерировал InventoryCloseEvent, который удалял запись
 * из openMenus. Решение:
 *   1. Флаг state.isRefreshing — подавляет удаление из openMenus при
 *      автоматическом переоткрытии.
 *   2. event.setCancelled(true) вызывается ВСЕГДА в onInventoryClick для
 *      инвентарей с нашим тайтлом.
 *   3. Обновление GUI делается через Bukkit.getScheduler().runTask() —
 *      отложенное на 1 тик, чтобы избежать гонки событий Bukkit.
 */
public class ShopGUI implements Listener {

    // ---- слоты товаров ----
    private static final int[] ITEM_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    }; // 28 слотов для товаров

    private static final int SLOT_PREV_PAGE    = 45;
    private static final int SLOT_CAT_ORE      = 46;
    private static final int SLOT_CAT_MOB      = 47;
    private static final int SLOT_CAT_FARM     = 48;
    private static final int SLOT_CAT_FOOD     = 49;
    private static final int SLOT_INFO         = 51;
    private static final int SLOT_NEXT_PAGE    = 53;

    // Идентификатор GUI в заголовке (используется для определения «наш» инвентарь)
    private static final String GUI_TAG = "§v§n§БайерМагазин";

    private static final ZoneId KYIV = ZoneId.of("Europe/Kiev");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

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
        // Ставим флаг: мы сами открываем инвентарь — не удалять запись в onClose
        state.isRefreshing = true;
        Inventory inv = buildInventory(player, state);
        player.openInventory(inv);
        state.inventory    = inv;
        state.isRefreshing = false;
    }

    // Отложенное обновление GUI на следующий тик
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
        // Заголовок содержит скрытый тег для идентификации
        String title = GUI_TAG + " §6§lБайер §8| §e" + state.category;
        Inventory inv = Bukkit.createInventory(null, 54, Component.text(title));

        // Заполняем рамку серым стеклом
        ItemStack glass = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, glass);

        // Очищаем слоты для товаров
        for (int slot : ITEM_SLOTS) inv.setItem(slot, null);

        // Товары текущей страницы
        List<ShopItem> items = ShopRegistry.getByCategory(state.category);
        int startIndex = state.page * ITEM_SLOTS.length;
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            int idx = startIndex + i;
            if (idx >= items.size()) break;
            inv.setItem(ITEM_SLOTS[i], makeShopItemIcon(items.get(idx), player));
        }

        // Кнопки категорий
        inv.setItem(SLOT_CAT_ORE,  makeCategoryButton("§aРуда",            Material.DIAMOND,      state.category.equals("Руда")));
        inv.setItem(SLOT_CAT_MOB,  makeCategoryButton("§cЛут с мобов",    Material.ROTTEN_FLESH,  state.category.equals("Лут с мобов")));
        inv.setItem(SLOT_CAT_FARM, makeCategoryButton("§2Фермерство",      Material.WHEAT,         state.category.equals("Фермерство")));
        inv.setItem(SLOT_CAT_FOOD, makeCategoryButton("§6Еда",             Material.COOKED_BEEF,   state.category.equals("Еда")));

        // Кнопка предыдущей страницы
        if (state.page > 0) {
            inv.setItem(SLOT_PREV_PAGE, makeItem(Material.ARROW, "§e⇐ Предыдущая страница"));
        } else {
            inv.setItem(SLOT_PREV_PAGE, glass);
        }

        // Кнопка следующей страницы
        int maxPages = (int) Math.ceil((double) items.size() / ITEM_SLOTS.length);
        if (maxPages == 0) maxPages = 1;
        if (state.page < maxPages - 1) {
            inv.setItem(SLOT_NEXT_PAGE, makeItem(Material.ARROW, "§eСледующая страница ⇒"));
        } else {
            inv.setItem(SLOT_NEXT_PAGE, glass);
        }

        // Инфо-кнопка: баланс + сброс
        Economy eco  = plugin.getEconomy();
        double  bal  = eco.getBalance(player);
        String  reset = Instant.ofEpochSecond(data.getNextResetEpoch())
                .atZone(KYIV).toLocalDateTime().format(FMT);

        ItemStack info = makeItemWithLore(
                Material.CLOCK,
                "§b§lИнформация",
                List.of(
                        "§7Ваш баланс: §6" + eco.format(bal),
                        "§7Сброс лимитов: §f" + reset,
                        "",
                        "§7§oЛКМ = купить 1   §9Shift+ЛКМ = купить 64",
                        "§7§oПКМ = продать 1  §9Shift+ПКМ = продать 64"
                )
        );
        inv.setItem(SLOT_INFO, info);

        return inv;
    }

    // ================================================================= обработка кликов
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Проверяем: это наш инвентарь?
        String title = event.getView().title().toString();
        // Используем проверку по тексту заголовка
        Inventory topInv = event.getView().getTopInventory();
        PlayerMenuState state = openMenus.get(player.getUniqueId());

        if (state == null || state.inventory == null) return;
        // Сравниваем объекты инвентаря
        if (!topInv.equals(state.inventory)) return;

        // ВСЕГДА отменяем клик — предотвращаем любое перемещение предметов
        event.setCancelled(true);

        // Клик в нижнем инвентаре игрока — игнорируем
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(topInv)) return;

        int slot = event.getRawSlot();
        ClickType click = event.getClick();

        // --- Навигация ---
        if (slot == SLOT_PREV_PAGE) {
            if (state.page > 0) {
                state.page--;
                refreshLater(player);
            }
            return;
        }
        if (slot == SLOT_NEXT_PAGE) {
            List<ShopItem> items = ShopRegistry.getByCategory(state.category);
            int maxPages = (int) Math.ceil((double) items.size() / ITEM_SLOTS.length);
            if (maxPages == 0) maxPages = 1;
            if (state.page < maxPages - 1) {
                state.page++;
                refreshLater(player);
            }
            return;
        }
        if (slot == SLOT_CAT_ORE)  { switchCategory(player, state, "Руда");         return; }
        if (slot == SLOT_CAT_MOB)  { switchCategory(player, state, "Лут с мобов"); return; }
        if (slot == SLOT_CAT_FARM) { switchCategory(player, state, "Фермерство");  return; }
        if (slot == SLOT_CAT_FOOD) { switchCategory(player, state, "Еда");         return; }
        if (slot == SLOT_INFO)     { return; }

        // --- Клик по товару ---
        int itemSlotIndex = -1;
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            if (ITEM_SLOTS[i] == slot) { itemSlotIndex = i; break; }
        }
        if (itemSlotIndex == -1) return; // клик по пустому месту/стеклу

        List<ShopItem> items = ShopRegistry.getByCategory(state.category);
        int idx = state.page * ITEM_SLOTS.length + itemSlotIndex;
        if (idx >= items.size()) return;

        ShopItem item = items.get(idx);

        boolean isShift   = click.isShiftClick();
        boolean isLeft    = click.isLeftClick();
        boolean isRight   = click.isRightClick();

        int amount = isShift ? 64 : 1;

        if (isLeft) {
            handleBuy(player, item, amount, state);
        } else if (isRight) {
            handleSell(player, item, amount, state);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        PlayerMenuState state = openMenus.get(player.getUniqueId());
        if (state == null) return;

        // Если мы сами переоткрываем (refreshLater), НЕ удаляем состояние
        if (state.isRefreshing) return;

        openMenus.remove(player.getUniqueId());
    }

    // ================================================================= действия
    private void handleBuy(Player player, ShopItem item, int amount, PlayerMenuState state) {
        Economy eco = plugin.getEconomy();

        if (item.getBuyPrice() == -1) {
            player.sendMessage("§c[Байер] Этот предмет нельзя купить!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        double totalCost = item.getBuyPrice() * amount;
        double balance   = eco.getBalance(player);

        if (balance < totalCost) {
            player.sendMessage("§c[Байер] Недостаточно средств! Нужно §e" + eco.format(totalCost)
                    + "§c, у вас §e" + eco.format(balance));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // Снимаем деньги
        eco.withdrawPlayer(player, totalCost);

        // Выдаём предмет
        ItemStack give = new ItemStack(item.getMaterial(), amount);
        player.getInventory().addItem(give).forEach((k, v) -> {
            // Если инвентарь полон — выбрасываем на землю
            player.getWorld().dropItemNaturally(player.getLocation(), v);
        });

        player.sendMessage("§a[Байер] §7Куплено §e" + amount + "x " + item.getDisplayName()
                + " §7за §6" + eco.format(totalCost)
                + "§7. Остаток: §6" + eco.format(eco.getBalance(player)));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);

        // Обновляем GUI
        refreshLater(player);
    }

    private void handleSell(Player player, ShopItem item, int amount, PlayerMenuState state) {
        Economy eco = plugin.getEconomy();

        // Проверяем серверный лимит
        int serverSold  = data.getServerSold(item.getId());
        int serverLimit = item.getServerLimit();
        if (serverSold >= serverLimit) {
            player.sendMessage("§c[Байер] Серверный лимит продаж исчерпан! (" + serverLimit + "/" + serverLimit + ")");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        // Сколько можно продать с учётом лимита
        int canSell = Math.min(amount, serverLimit - serverSold);

        // Проверяем наличие предмета в инвентаре
        int hasAmount = countItem(player, item.getMaterial());
        if (hasAmount == 0) {
            player.sendMessage("§c[Байер] У вас нет §e" + item.getDisplayName() + "§c!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        canSell = Math.min(canSell, hasAmount);

        double totalEarned = item.getSellPrice() * canSell;

        // Забираем предметы
        removeItem(player, item.getMaterial(), canSell);

        // Начисляем деньги через Vault
        eco.depositPlayer(player, totalEarned);

        // Обновляем лимиты
        data.addServerSold(item.getId(), canSell);
        data.addContribution(player.getUniqueId(), item.getId(), canSell);

        player.sendMessage("§a[Байер] §7Продано §e" + canSell + "x " + item.getDisplayName()
                + " §7за §6" + eco.format(totalEarned)
                + "§7. Баланс: §6" + eco.format(eco.getBalance(player)));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);

        if (canSell < amount) {
            player.sendMessage("§e[Байер] §7Продано только §e" + canSell + "§7 (лимит/инвентарь).");
        }

        // Обновляем GUI
        refreshLater(player);
    }

    // ================================================================= вспомогательные
    private void switchCategory(Player player, PlayerMenuState state, String category) {
        if (!state.category.equals(category)) {
            state.category = category;
            state.page     = 0;
        }
        refreshLater(player);
    }

    private int countItem(Player player, Material mat) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == mat) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    private void removeItem(Player player, Material mat, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
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

    // ================================================================= иконки
    private ItemStack makeShopItemIcon(ShopItem item, Player player) {
        Economy eco  = plugin.getEconomy();
        int sold     = data.getServerSold(item.getId());
        int limit    = item.getServerLimit();
        boolean full = sold >= limit;

        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("§7Цена покупки: " + (item.getBuyPrice() == -1 ? "§cНельзя" : "§a" + eco.format(item.getBuyPrice())));
        lore.add("§7Цена продажи: §e" + eco.format(item.getSellPrice()));
        lore.add("");
        lore.add("§7Серверный лимит: " + (full ? "§c" : "§a") + sold + "§7/" + limit);
        lore.add("");
        if (full) {
            lore.add("§c§lЛИМИТ ИСЧЕРПАН");
        } else {
            lore.add("§a§lЛКМ §7— купить 1");
            lore.add("§e§lПКМ §7— продать 1");
            lore.add("§a§lShift+ЛКМ §7— купить 64");
            lore.add("§e§lShift+ПКМ §7— продать 64");
        }

        return makeItemWithLore(item.getMaterial(), (full ? "§c" : "§f") + item.getDisplayName(), lore);
    }

    private ItemStack makeCategoryButton(String name, Material mat, boolean active) {
        Material display = active ? Material.LIME_STAINED_GLASS_PANE : mat;
        List<String> lore = active
                ? List.of("§a§l► Активная категория")
                : List.of("§7Нажмите для перехода");
        return makeItemWithLore(display, name, lore);
    }

    private ItemStack makeItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack makeItemWithLore(Material mat, String name, List<String> loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            for (String line : loreLines) {
                lore.add(Component.text(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
        }
        return item;
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

