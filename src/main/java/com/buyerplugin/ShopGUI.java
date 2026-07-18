package com.buyerplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * GUI магазина Байера.
 *
 * Структура окна (54 слота — 6 рядов × 9):
 *   Строки 0-4 (слоты 0-44): товары в слотах 10-16, 19-25, 28-34, 37-43
 *   Строка 5 (слоты 45-53):  навигация / категории / инфо
 *
 * Категории: Руда | Строительство | Лут с мобов | Фермерство | Еда | Мобы
 *
 * Особые предметы:
 *   - «Суперкирка» (super_pickaxe): при покупке создаётся через SuperPickaxeHelper
 *   - Категория «Мобы»: только покупка (sellPrice = -1)
 *   - «Пузырёк опыта»: только покупка (sellPrice = -1)
 */
public class ShopGUI implements Listener {

    // Слоты товаров (28 слотов в рабочей области)
    private static final int[] ITEM_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    // Слоты нижней панели навигации
    private static final int SLOT_PREV_PAGE    = 45;
    private static final int SLOT_CAT_ORE      = 46;
    private static final int SLOT_CAT_BUILD    = 47;  // Строительство
    private static final int SLOT_CAT_MOB_LOOT = 48;  // Лут с мобов
    private static final int SLOT_CAT_FARM     = 49;  // Фермерство
    private static final int SLOT_INFO         = 52;
    private static final int SLOT_CAT_FOOD     = 51;  // Еда
    private static final int SLOT_CAT_MOBS     = 50;  // Мобы
    private static final int SLOT_NEXT_PAGE    = 53;

    private static final ZoneId            KYIV = ZoneId.of("Europe/Kiev");
    private static final DateTimeFormatter FMT  = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final BuyerPlugin plugin;
    private final ShopData    data;
    private final Map<UUID, PlayerMenuState> openMenus = new HashMap<>();

    public ShopGUI(BuyerPlugin plugin, ShopData data) {
        this.plugin = plugin;
        this.data   = data;
    }

    // =========================================================================
    // ОТКРЫТИЕ / ЗАКРЫТИЕ
    // =========================================================================
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
            if (state != null) openCategory(player, state);
        });
    }

    // =========================================================================
    // ПОСТРОЕНИЕ ИНВЕНТАРЯ
    // =========================================================================
    private Inventory buildInventory(Player player, PlayerMenuState state) {
        Component title = buildTitle(state);
        Inventory inv   = Bukkit.createInventory(null, 54, title);

        // ---- РАМКА ----
        ItemStack black  = makeDecorPane(Material.BLACK_STAINED_GLASS_PANE,  " ");
        ItemStack purple = makeDecorPane(Material.PURPLE_STAINED_GLASS_PANE, " ");
        ItemStack gray   = makeDecorPane(Material.GRAY_STAINED_GLASS_PANE,   " ");

        for (int i = 0;  i < 9;  i++) inv.setItem(i, black);
        for (int i = 45; i < 54; i++) inv.setItem(i, gray);
        for (int i = 1;  i < 8;  i += 9) {
            inv.setItem(i * 9 - i,     purple); // заглушка (упрощение)
        }
        inv.setItem(9,  purple); inv.setItem(17, purple);
        inv.setItem(18, purple); inv.setItem(26, purple);
        inv.setItem(27, purple); inv.setItem(35, purple);
        inv.setItem(36, purple); inv.setItem(44, purple);

        // ---- ТОВАРЫ ----
        Economy eco   = plugin.getEconomy();
        List<ShopItem> items = ShopRegistry.getByCategory(state.category);
        int startIndex = state.page * ITEM_SLOTS.length;
        for (int i = 0; i < ITEM_SLOTS.length; i++) {
            int idx = startIndex + i;
            if (idx >= items.size()) break;
            inv.setItem(ITEM_SLOTS[i], makeShopItemIcon(items.get(idx), eco));
        }

        // ---- КНОПКИ КАТЕГОРИЙ ----
        inv.setItem(SLOT_CAT_ORE,      makeCategoryButton("⛏ Руда",         Material.DIAMOND_ORE,       state.category.equals("Руда")));
        inv.setItem(SLOT_CAT_BUILD,    makeCategoryButton("🧱 Строительство", Material.BRICKS,            state.category.equals("Строительство")));
        inv.setItem(SLOT_CAT_MOB_LOOT, makeCategoryButton("⚔ Лут с мобов",   Material.ROTTEN_FLESH,      state.category.equals("Лут с мобов")));
        inv.setItem(SLOT_CAT_FARM,     makeCategoryButton("🌾 Фермерство",    Material.WHEAT,             state.category.equals("Фермерство")));
        inv.setItem(SLOT_CAT_FOOD,     makeCategoryButton("🍖 Еда",           Material.COOKED_BEEF,       state.category.equals("Еда")));
        inv.setItem(SLOT_CAT_MOBS,     makeCategoryButton("🥚 Мобы",          Material.ZOMBIE_SPAWN_EGG,  state.category.equals("Мобы")));

        // ---- ИНФОРМАЦИОННАЯ КНОПКА ----
        Economy economy = plugin.getEconomy();
        String balance   = economy.format(economy.getBalance(player));
        String resetTime = Instant.ofEpochSecond(data.getNextResetEpoch()).atZone(KYIV).format(FMT);
        inv.setItem(SLOT_INFO, makeInfoButton(balance, resetTime));

        // ---- ПАГИНАЦИЯ ----
        int maxPages = Math.max(1, (int) Math.ceil((double) items.size() / ITEM_SLOTS.length));
        if (state.page > 0) {
            inv.setItem(SLOT_PREV_PAGE, makeNavButton(Material.SPECTRAL_ARROW,
                    "§b« Назад", "§7Страница §f" + state.page + " §7из §f" + maxPages));
        } else {
            inv.setItem(SLOT_PREV_PAGE, gray);
        }
        if (state.page < maxPages - 1) {
            inv.setItem(SLOT_NEXT_PAGE, makeNavButton(Material.SPECTRAL_ARROW,
                    "§bВперёд »", "§7Страница §f" + (state.page + 2) + " §7из §f" + maxPages));
        } else {
            inv.setItem(SLOT_NEXT_PAGE, gray);
        }

        return inv;
    }

    private Component buildTitle(PlayerMenuState state) {
        return Component.text("§5§lБайер §8» §7" + state.category)
                .decoration(TextDecoration.ITALIC, false);
    }

    // =========================================================================
    // ОБРАБОТКА КЛИКОВ
    // =========================================================================
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

        // ---- Навигация ----
        if (slot == SLOT_PREV_PAGE) {
            if (state.page > 0) { state.page--; player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1f); refreshLater(player); }
            return;
        }
        if (slot == SLOT_NEXT_PAGE) {
            List<ShopItem> items = ShopRegistry.getByCategory(state.category);
            int maxPages = Math.max(1, (int) Math.ceil((double) items.size() / ITEM_SLOTS.length));
            if (state.page < maxPages - 1) { state.page++; player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1f); refreshLater(player); }
            return;
        }

        // ---- Категории ----
        if (slot == SLOT_CAT_ORE)      { switchCategory(player, state, "Руда");         return; }
        if (slot == SLOT_CAT_BUILD)    { switchCategory(player, state, "Строительство"); return; }
        if (slot == SLOT_CAT_MOB_LOOT) { switchCategory(player, state, "Лут с мобов");  return; }
        if (slot == SLOT_CAT_FARM)     { switchCategory(player, state, "Фермерство");    return; }
        if (slot == SLOT_CAT_FOOD)     { switchCategory(player, state, "Еда");           return; }
        if (slot == SLOT_CAT_MOBS)     { switchCategory(player, state, "Мобы");          return; }

        // ---- Клик по товару ----
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
        if (click.isLeftClick())  handleBuy(player, item, amount, state);
        else if (click.isRightClick()) handleSell(player, item, amount, state);
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
            state.page     = 0;
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
            refreshLater(player);
        }
    }

    private boolean isOurGUI(Inventory inv) {
        for (PlayerMenuState s : openMenus.values())
            if (inv.equals(s.inventory)) return true;
        return false;
    }

    // =========================================================================
    // ПОКУПКА
    // =========================================================================
    private void handleBuy(Player player, ShopItem item, int amount, PlayerMenuState state) {
        Economy eco = plugin.getEconomy();

        if (item.getBuyPrice() == -1) {
            player.sendMessage("§c✖ §7Этот предмет §cнельзя купить§7!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        int stock = data.getServerStock(item.getId());
        if (stock <= 0) {
            player.sendMessage("§c✖ §7В байере §cнет этого товара§7! Дождитесь продажи игроками.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            refreshLater(player);
            return;
        }

        int canBuy    = Math.min(amount, stock);
        double total  = item.getBuyPrice() * canBuy;

        if (eco.getBalance(player) < total) {
            player.sendMessage("§c✖ §7Недостаточно средств! Нужно §6" + eco.format(total) +
                    "§7, у вас §6" + eco.format(eco.getBalance(player)) + "§7.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        eco.withdrawPlayer(player, total);
        data.subtractStock(item.getId(), canBuy);
        data.save();

        // ---- Специальная обработка суперкирки ----
        if (item.getId().equals("super_pickaxe")) {
            for (int i = 0; i < canBuy; i++) {
                ItemStack superPick = SuperPickaxeHelper.createSuperPickaxe(plugin);
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(superPick);
                leftover.values().forEach(v -> player.getWorld().dropItemNaturally(player.getLocation(), v));
            }
        } else {
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(new ItemStack(item.getMaterial(), canBuy));
            leftover.values().forEach(v -> player.getWorld().dropItemNaturally(player.getLocation(), v));
        }

        player.sendMessage("§a✔ §7Куплено §f" + canBuy + "x §e" + item.getDisplayName() +
                " §7за §6" + eco.format(total) + "§7. Баланс: §6" + eco.format(eco.getBalance(player)) +
                "§7. Склад байера: §f" + data.getServerStock(item.getId()) + "§7/§f" + item.getServerLimit());
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        refreshLater(player);
    }

    // =========================================================================
    // ПРОДАЖА
    // =========================================================================
    private void handleSell(Player player, ShopItem item, int amount, PlayerMenuState state) {
        Economy eco = plugin.getEconomy();

        if (item.getSellPrice() == -1) {
            player.sendMessage("§c✖ §7Этот предмет §cнельзя продать§7!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        int stock = data.getServerStock(item.getId());
        int limit = item.getServerLimit();
        if (stock >= limit) {
            player.sendMessage("§c✖ §7Байер §cпереполнен§7! Дождитесь покупки игроками.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            refreshLater(player);
            return;
        }

        int canSell = Math.min(amount, countInInventory(player, item.getMaterial()));
        if (canSell <= 0) {
            player.sendMessage("§c✖ §7У вас нет §e" + item.getDisplayName() + " §7в инвентаре!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        canSell = Math.min(canSell, limit - stock);

        removeFromInventory(player, item.getMaterial(), canSell);
        double total = item.getSellPrice() * canSell;
        eco.depositPlayer(player, total);
        data.addStock(item.getId(), canSell, limit);
        data.addContribution(player.getUniqueId(), item.getId(), canSell);
        data.save();

        player.sendMessage("§a✔ §7Продано §f" + canSell + "x §e" + item.getDisplayName() +
                " §7за §6" + eco.format(total) + "§7. Баланс: §6" + eco.format(eco.getBalance(player)) +
                "§7. Склад байера: §f" + data.getServerStock(item.getId()) + "§7/§f" + limit);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.0f);
        refreshLater(player);
    }

    // =========================================================================
    // ИКОНКА ТОВАРА
    // =========================================================================
    private ItemStack makeShopItemIcon(ShopItem item, Economy eco) {
        int stock = data.getServerStock(item.getId());
        int limit = item.getServerLimit();
        boolean empty = stock <= 0;
        boolean full  = stock >= limit;
        String progressBar = buildProgressBar(stock, limit, 10);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());

        // Цена покупки
        if (item.getBuyPrice() != -1) {
            if (empty) lore.add(txt(" §c✖ Купить: нет ресурса в байере"));
            else {
                lore.add(txt(" §7▸ Купить: §a" + eco.format(item.getBuyPrice()) + " §7за 1 шт."));
                lore.add(txt(" §7▸ Купить: §a" + eco.format(item.getBuyPrice() * 64) + " §7за 64 шт."));
            }
        } else {
            lore.add(txt(" §7▸ Купить: §c✖ Недоступно"));
        }

        // Цена продажи
        if (item.getSellPrice() != -1) {
            if (full) lore.add(txt(" §c✖ Продать: байер переполнен"));
            else {
                lore.add(txt(" §7▸ Продать: §e" + eco.format(item.getSellPrice()) + " §7за 1 шт."));
                lore.add(txt(" §7▸ Продать: §e" + eco.format(item.getSellPrice() * 64) + " §7за 64 шт."));
            }
        } else {
            lore.add(txt(" §7▸ Продать: §c✖ Недоступно"));
        }

        lore.add(Component.empty());
        lore.add(txt(" §7Склад байера:"));
        lore.add(txt(" " + progressBar + " §f" + stock + "§7/§f" + limit));
        lore.add(Component.empty());

        // Подсказки управления
        if (empty && item.getBuyPrice() == -1) {
            lore.add(txt(" §c§l⚠ БАЙЕР ПУСТ — покупка недоступна"));
        } else if (empty) {
            lore.add(txt(" §c§l⚠ БАЙЕР ПУСТ — покупка невозможна"));
            lore.add(txt(" §7Продайте ресурс, чтобы пополнить байер."));
            if (item.getSellPrice() != -1 && !full) {
                lore.add(Component.empty());
                lore.add(txt(" §e§l[ПКМ]§r§7 Продать ×1"));
                lore.add(txt(" §6§l[Shift+ПКМ]§r§7 Продать ×64"));
            }
        } else if (full) {
            lore.add(txt(" §c§l⚠ БАЙЕР ПОЛОН — продажа невозможна"));
            if (item.getBuyPrice() != -1) {
                lore.add(txt(" §7Купите ресурс, чтобы освободить место."));
                lore.add(Component.empty());
                lore.add(txt(" §a§l[ЛКМ]§r§7 Купить ×1"));
                lore.add(txt(" §b§l[Shift+ЛКМ]§r§7 Купить ×64"));
            }
        } else {
            if (item.getBuyPrice() != -1) {
                lore.add(txt(" §a§l[ЛКМ]§r§7 Купить ×1"));
                lore.add(txt(" §b§l[Shift+ЛКМ]§r§7 Купить ×64"));
            }
            if (item.getSellPrice() != -1) {
                lore.add(txt(" §e§l[ПКМ]§r§7 Продать ×1"));
                lore.add(txt(" §6§l[Shift+ПКМ]§r§7 Продать ×64"));
            }
        }
        lore.add(Component.empty());

        String nameColor = empty ? "§c" : (full ? "§6" : "§f");
        String prefix    = empty ? "§c✖ " : (full ? "§6■ " : "§b✦ ");
        return makeItemWithLore(item.getMaterial(), prefix + nameColor + item.getDisplayName(), lore);
    }

    // =========================================================================
    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ
    // =========================================================================
    private int countInInventory(Player player, Material mat) {
        int count = 0;
        for (ItemStack s : player.getInventory().getContents()) {
            if (s != null && s.getType() == mat) count += s.getAmount();
        }
        return count;
    }

    private void removeFromInventory(Player player, Material mat, int amount) {
        ItemStack[] contents = player.getInventory().getContents();
        int remaining = amount;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack s = contents[i];
            if (s == null || s.getType() != mat) continue;
            int take = Math.min(s.getAmount(), remaining);
            s.setAmount(s.getAmount() - take);
            remaining -= take;
        }
    }

    private String buildProgressBar(int current, int max, int length) {
        if (max <= 0) return "§8[§c||||||||||§8]";
        int filled = (int) Math.round((double) current / max * length);
        StringBuilder sb = new StringBuilder("§7[");
        for (int i = 0; i < length; i++) {
            if (i < filled) {
                String color = (filled >= length) ? "§c" : (filled >= length * 0.7) ? "§e" : "§a";
                sb.append(color).append("|");
            } else sb.append("§8|");
        }
        sb.append("§7]");
        return sb.toString();
    }

    private ItemStack makeCategoryButton(String name, Material mat, boolean active) {
        Material display = active ? Material.LIME_STAINED_GLASS_PANE : mat;
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(active ? txt(" §a§l► Выбранная категория") : txt(" §7Нажмите, чтобы перейти"));
        lore.add(Component.empty());
        return makeItemWithLore(display, (active ? "§a§l► §r" : "§7") + name, lore);
    }

    private ItemStack makeNavButton(Material mat, String name, String hint) {
        return makeItemWithLore(mat, name, List.of(Component.empty(), txt(" " + hint), Component.empty()));
    }

    private ItemStack makeInfoButton(String balance, String resetTime) {
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(txt(" §7Ваш баланс:"));
        lore.add(txt(" §6✦ " + balance));
        lore.add(Component.empty());
        lore.add(txt(" §7Следующий сброс:"));
        lore.add(txt(" §b⏰ " + resetTime));
        lore.add(Component.empty());
        lore.add(txt(" §7Автор: Ruin4ik"));
        lore.add(txt(" §d✦ Специально для TwixRPG"));
        lore.add(Component.empty());
        return makeItemWithLore(Material.CLOCK, "§e§l✦ Информация", lore);
    }

    private ItemStack makeDecorPane(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta  = item.getItemMeta();
        if (meta != null) { meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false)); item.setItemMeta(meta); }
        return item;
    }

    private ItemStack makeItemWithLore(Material mat, String name, List<Component> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta  = item.getItemMeta();
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

    // =========================================================================
    // ВНУТРЕННИЙ КЛАСС СОСТОЯНИЯ
    // =========================================================================
    static class PlayerMenuState {
        String    category;
        int       page;
        Inventory inventory;
        PlayerMenuState(String category, int page) { this.category = category; this.page = page; }
    }
}