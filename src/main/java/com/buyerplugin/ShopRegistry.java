package com.buyerplugin;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Реестр всех товаров магазина.
 * Цены покупки: buyPrice (монеты Vault).
 * Цены продажи: sellPrice (монеты Vault).
 * buyPrice = -1 означает «нельзя купить».
 *
 * serverLimit — максимальный запас байера (потолок склада).
 * Начальный запас при первом запуске/сбросе = serverLimit / 2.
 */
public class ShopRegistry {

    private static final Map<String, ShopItem> ITEMS      = new LinkedHashMap<>();
    private static final List<String>          CATEGORIES = new ArrayList<>();

    static {
        // ---- РУДА ----
        register(new ShopItem("redstone",    "Редстоун",           Material.REDSTONE,         10,   20,   4000, "Руда"));
        register(new ShopItem("lapis",       "Лазурит",            Material.LAPIS_LAZULI,      5,   10,   5000, "Руда"));
        register(new ShopItem("emerald",     "Изумруд",            Material.EMERALD,          10,   20,   3000, "Руда"));
        register(new ShopItem("iron_ingot",  "Железный слиток",    Material.IRON_INGOT,       15,   30,   3000, "Руда"));
        register(new ShopItem("gold_ingot",  "Золотой слиток",     Material.GOLD_INGOT,       20,   40,   3000, "Руда"));
        register(new ShopItem("diamond",     "Алмаз",              Material.DIAMOND,          60,  120,   1500, "Руда"));
        register(new ShopItem("netherite",   "Незеритовый слиток", Material.NETHERITE_INGOT, 1000,   -1,   150, "Руда"));

        // ---- ЛУТ С МОБОВ ----
        register(new ShopItem("rotten_flesh",  "Гнилая плоть",     Material.ROTTEN_FLESH,    2,   4,  5000, "Лут с мобов"));
        register(new ShopItem("bone",          "Кость",            Material.BONE,             5,  10,  4000, "Лут с мобов"));
        register(new ShopItem("string",        "Нить",             Material.STRING,           4,   8,  4000, "Лут с мобов"));
        register(new ShopItem("gunpowder",     "Порох",            Material.GUNPOWDER,       12,  24,  3000, "Лут с мобов"));
        register(new ShopItem("slimeball",     "Слизь",            Material.SLIME_BALL,       3,   6,  2500, "Лут с мобов"));
        register(new ShopItem("magma_cream",   "Огненная слизь",   Material.MAGMA_CREAM,     10,  20,  2000, "Лут с мобов"));
        register(new ShopItem("ghast_tear",    "Слёзы гаста",      Material.GHAST_TEAR,     120, 240,   500, "Лут с мобов"));
        register(new ShopItem("blaze_rod",     "Стержень ифрита",  Material.BLAZE_ROD,       30,  60,  1000, "Лут с мобов"));
        register(new ShopItem("breeze_rod",    "Стержень вихря",   Material.BREEZE_ROD,      40,  80,   800, "Лут с мобов"));
        register(new ShopItem("shulker_shell", "Панцирь шалкера",  Material.SHULKER_SHELL,   70, 140,   400, "Лут с мобов"));
        register(new ShopItem("spider_eye",    "Паучий глаз",      Material.SPIDER_EYE,       8,  16,  2000, "Лут с мобов"));
        register(new ShopItem("leather",       "Кожа",             Material.LEATHER,         15,  30,  2000, "Лут с мобов"));
        register(new ShopItem("wool",          "Шерсть",           Material.WHITE_WOOL,       3,   6,  5000, "Лут с мобов"));

        // ---- ФЕРМЕРСТВО ----
        register(new ShopItem("cactus",       "Кактус",        Material.CACTUS,       2,  4,  6000, "Фермерство"));
        register(new ShopItem("sugar_cane",   "Тростник",      Material.SUGAR_CANE,   3,  6,  6000, "Фермерство"));
        register(new ShopItem("melon",        "Арбуз (блок)",  Material.MELON,        8, 16,  4000, "Фермерство"));
        register(new ShopItem("pumpkin",      "Тыква (блок)",  Material.PUMPKIN,      8, 16,  4000, "Фермерство"));
        register(new ShopItem("chorus_fruit", "Плод хоруса",   Material.CHORUS_FRUIT, 3,  6,  4000, "Фермерство"));

        // ---- ЕДА ----
        register(new ShopItem("carrot",        "Морковь",           Material.CARROT,          4,  8, 5000, "Еда"));
        register(new ShopItem("cooked_beef",   "Стейк",             Material.COOKED_BEEF,    14, 28, 3000, "Еда"));
        register(new ShopItem("cooked_pork",   "Жареная свинина",   Material.COOKED_PORKCHOP,10, 20, 3000, "Еда"));
        register(new ShopItem("cooked_mutton", "Жареная баранина",  Material.COOKED_MUTTON,  12, 24, 3000, "Еда"));
        register(new ShopItem("beetroot",      "Свёкла",            Material.BEETROOT,        5, 10, 5000, "Еда"));
        register(new ShopItem("apple",         "Яблоко",            Material.APPLE,          15, 30, 1500, "Еда"));

        // Формируем список категорий (уникальных, в порядке добавления)
        for (ShopItem item : ITEMS.values()) {
            if (!CATEGORIES.contains(item.getCategory())) {
                CATEGORIES.add(item.getCategory());
            }
        }
    }

    private static void register(ShopItem item) {
        ITEMS.put(item.getId(), item);
    }

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
        return new ArrayList<>(CATEGORIES);
    }

    public static List<String> getAllIds() {
        return new ArrayList<>(ITEMS.keySet());
    }

    /** Возвращает все предметы (используется при сбросе склада). */
    public static Collection<ShopItem> getAllItems() {
        return ITEMS.values();
    }
}