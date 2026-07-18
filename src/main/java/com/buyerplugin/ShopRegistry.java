package com.buyerplugin;

import java.util.*;

/**
 * Реестр всех товаров магазина.
 *
 * В отличие от оригинала — предметы НЕ захардкожены в коде!
 * Они загружаются из items.yml через ItemsConfig при старте плагина.
 *
 * Вызов жизненного цикла:
 *   1. BuyerPlugin.onEnable() создаёт ItemsConfig и вызывает itemsConfig.load()
 *   2. ItemsConfig.load() вызывает ShopRegistry.register(item, initialStock)
 *      для каждого предмета из items.yml
 *   3. При /buyer reload: ShopRegistry.clear() → ItemsConfig.load() → ShopData.applyInitialStock()
 */
public class ShopRegistry {

    // Все зарегистрированные предметы: itemId → ShopItem
    private static final Map<String, ShopItem> ITEMS = new LinkedHashMap<>();

    // Список категорий в порядке добавления
    private static final List<String> CATEGORIES = new ArrayList<>();

    // Начальные запасы склада: itemId → initialStock
    // Используется при первом старте или после /buyer reload
    private static final Map<String, Integer> INITIAL_STOCKS = new LinkedHashMap<>();

    /**
     * Очистить реестр (вызывается перед перезагрузкой items.yml).
     */
    public static void clear() {
        ITEMS.clear();
        CATEGORIES.clear();
        INITIAL_STOCKS.clear();
    }

    /**
     * Зарегистрировать предмет из items.yml.
     *
     * @param item         предмет магазина
     * @param initialStock начальный запас при старте / сбросе
     */
    public static void register(ShopItem item, int initialStock) {
        ITEMS.put(item.getId(), item);
        if (!CATEGORIES.contains(item.getCategory())) {
            CATEGORIES.add(item.getCategory());
        }
        INITIAL_STOCKS.put(item.getId(), initialStock);
    }

    // ─── Чтение ──────────────────────────────────────────────────────────────

    public static ShopItem getById(String id) {
        return ITEMS.get(id);
    }

    public static List<ShopItem> getByCategory(String category) {
        List<ShopItem> result = new ArrayList<>();
        for (ShopItem item : ITEMS.values()) {
            if (item.getCategory().equals(category)) result.add(item);
        }
        return result;
    }

    public static List<String> getCategories() {
        // Возвращаем в фиксированном порядке GUI (Руда, Строительство, ...)
        List<String> ordered = new ArrayList<>();
        String[] GUI_ORDER = {"Руда", "Строительство", "Лут с мобов", "Фермерство", "Еда", "Мобы"};
        for (String cat : GUI_ORDER) {
            if (CATEGORIES.contains(cat)) ordered.add(cat);
        }
        // Любые дополнительные категории в конец
        for (String cat : CATEGORIES) {
            if (!ordered.contains(cat)) ordered.add(cat);
        }
        return ordered;
    }

    public static List<String> getAllIds() {
        return new ArrayList<>(ITEMS.keySet());
    }

    public static Collection<ShopItem> getAllItems() {
        return ITEMS.values();
    }

    /**
     * Получить начальный запас для предмета.
     * Используется в ShopData при первом старте и после /buyer reload.
     */
    public static int getInitialStock(String itemId) {
        return INITIAL_STOCKS.getOrDefault(itemId, 0);
    }

    /**
     * @return количество зарегистрированных предметов
     */
    public static int size() {
        return ITEMS.size();
    }
}
