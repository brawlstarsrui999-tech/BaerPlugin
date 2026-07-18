package com.buyerplugin;

import org.bukkit.Material;
import java.util.*;

/**
 * Реестр всех товаров магазина.
 * Цены покупки: buyPrice (монеты Vault).
 * Цены продажи: sellPrice (монеты Vault).
 * buyPrice = -1 означает «нельзя купить».
 * sellPrice = -1 означает «нельзя продать».
 *
 * serverLimit — максимальный запас байера (потолок склада).
 * Начальный запас при первом запуске/сбросе = serverLimit / 2.
 */
public class ShopRegistry {

    private static final Map<String, ShopItem> ITEMS = new LinkedHashMap<>();
    private static final List<String> CATEGORIES = new ArrayList<>();

    static {
        // =====================================================================
        // ---- РУДА ----
        // =====================================================================
        register(new ShopItem("redstone",        "Редстоун",              Material.REDSTONE,              10,   20,   4000, "Руда"));
        register(new ShopItem("lapis",            "Лазурит",               Material.LAPIS_LAZULI,           5,   10,   5000, "Руда"));
        register(new ShopItem("emerald",          "Изумруд",               Material.EMERALD,               10,   20,   3000, "Руда"));
        register(new ShopItem("iron_ingot",       "Железный слиток",       Material.IRON_INGOT,            15,   30,   3000, "Руда"));
        register(new ShopItem("gold_ingot",       "Золотой слиток",        Material.GOLD_INGOT,            20,   40,   3000, "Руда"));
        register(new ShopItem("diamond",          "Алмаз",                 Material.DIAMOND,               60,  120,   1500, "Руда"));
        register(new ShopItem("netherite",        "Незеритовый слиток",    Material.NETHERITE_INGOT,     1000,   -1,    150, "Руда"));
        register(new ShopItem("amethyst_shard",   "Осколок аметиста",      Material.AMETHYST_SHARD,         8,   10,    700, "Руда"));
        register(new ShopItem("amethyst_block",   "Аметистовый блок",      Material.AMETHYST_BLOCK,         2,    5,   1000, "Руда"));
        register(new ShopItem("glowstone",        "Светокамень",           Material.GLOWSTONE,              5,   10,   5000, "Руда"));
        register(new ShopItem("stone", "Камень", Material.STONE, 2, 4, 20000, "Руда"));
        register(new ShopItem("cobblestone", "Булыжник", Material.COBBLESTONE, 1, 2, 40000, "Руда"));
        register(new ShopItem("quartz", "Кварц", Material.QUARTZ, 6, 12, 1500, "Руда"));
        register(new ShopItem("raw_copper", "Не переплавленная медь", Material.RAW_COPPER, 7, 14, 3000, "Руда"));
        register(new ShopItem("coal", "Уголь", Material.COAL, 3, 6, 2500, "Руда"));

        // =====================================================================
        // ---- СТРОИТЕЛЬСТВО ----
        // =====================================================================
        register(new ShopItem("ice",              "Лёд",                   Material.ICE,                    5,    8,   3000, "Строительство"));
        register(new ShopItem("prismarine",       "Призмарин",             Material.PRISMARINE,             6,   10,   1500, "Строительство"));
        register(new ShopItem("soul_sand",        "Песок душ",             Material.SOUL_SAND,              3,    5,   3000, "Строительство"));
        register(new ShopItem("andesite",         "Андезит",               Material.ANDESITE,               3,    4,   2500, "Строительство"));
        register(new ShopItem("diorite",          "Диорит",                Material.DIORITE,                3,    4,   2500, "Строительство"));
        register(new ShopItem("deepslate_bricks", "Глубинносланцевые кирпичи", Material.DEEPSLATE_BRICKS,  2,    3,   5000, "Строительство"));
        register(new ShopItem("obsidian",         "Обсидиан",              Material.OBSIDIAN,              25,   40,    300, "Строительство"));
        register(new ShopItem("oak_log", "Дуб", Material.OAK_LOG, 3, 6, 5000, "Строительство"));
        register(new ShopItem("spruce_log", "Еловое бревно", Material.SPRUCE_LOG, 6, 12, 3000, "Строительство"));
        register(new ShopItem("birch_log", "Берёза", Material.BIRCH_LOG, 5, 10, 3000, "Строительство"));
        register(new ShopItem("glass", "Стекло", Material.GLASS, 8, 16, 1000, "Строительство"));
        register(new ShopItem("torch", "Факел", Material.TORCH, 2, 4, 1000, "Строительство"));

        // =====================================================================
        // ---- ЛУТ С МОБОВ ----
        // =====================================================================
        register(new ShopItem("rotten_flesh",     "Гнилая плоть",          Material.ROTTEN_FLESH,           2,    4,   5000, "Лут с мобов"));
        register(new ShopItem("bone",             "Кость",                 Material.BONE,                   5,   10,   4000, "Лут с мобов"));
        register(new ShopItem("string",           "Нить",                  Material.STRING,                 4,    8,   4000, "Лут с мобов"));
        register(new ShopItem("gunpowder",        "Порох",                 Material.GUNPOWDER,             12,   24,   3000, "Лут с мобов"));
        register(new ShopItem("slimeball",        "Слизь",                 Material.SLIME_BALL,             3,    6,   2500, "Лут с мобов"));
        register(new ShopItem("magma_cream",      "Огненная слизь",        Material.MAGMA_CREAM,           10,   20,   2000, "Лут с мобов"));
        register(new ShopItem("ghast_tear",       "Слёзы гаста",           Material.GHAST_TEAR,           120,  240,    500, "Лут с мобов"));
        register(new ShopItem("blaze_rod",        "Стержень ифрита",       Material.BLAZE_ROD,             30,   60,   1000, "Лут с мобов"));
        register(new ShopItem("breeze_rod",       "Стержень вихря",        Material.BREEZE_ROD,            40,   80,    800, "Лут с мобов"));
        register(new ShopItem("shulker_shell",    "Панцирь шалкера",       Material.SHULKER_SHELL,         70,  140,    400, "Лут с мобов"));
        register(new ShopItem("spider_eye",       "Паучий глаз",           Material.SPIDER_EYE,             8,   16,   2000, "Лут с мобов"));
        register(new ShopItem("leather",          "Кожа",                  Material.LEATHER,               15,   30,   2000, "Лут с мобов"));
        register(new ShopItem("wool",             "Шерсть",                Material.WHITE_WOOL,             3,    6,   5000, "Лут с мобов"));
        register(new ShopItem("trial_key",        "Ключ испытаний",        Material.TRIAL_KEY,            300,  500,     15, "Лут с мобов"));
        register(new ShopItem("sponge",           "Губка",                 Material.SPONGE,               100,  200,    200, "Лут с мобов"));
        register(new ShopItem("feather",          "Перо",                  Material.FEATHER,                5,    8,    300, "Лут с мобов"));
        register(new ShopItem("nether_star",      "Звезда незера",         Material.NETHER_STAR,         1500, 5000,      5, "Лут с мобов"));
        register(new ShopItem("mace_head", "Навершие булавы", Material.MACE, 3000, 6000, 5, "Лут с мобов"));
        register(new ShopItem("ender_pearl", "Эндер жемчуг", Material.ENDER_PEARL, 20, 40, 750, "Лут с мобов"));
        // Пузырёк опыта — только покупка (sellPrice = -1)
        register(new ShopItem("experience_bottle","Пузырёк опыта",         Material.EXPERIENCE_BOTTLE,     -1,    5,    750, "Лут с мобов"));

        // =====================================================================
        // ---- ФЕРМЕРСТВО ----
        // =====================================================================
        register(new ShopItem("cactus",           "Кактус",                Material.CACTUS,                 2,    4,   6000, "Фермерство"));
        register(new ShopItem("sugar_cane",       "Тростник",              Material.SUGAR_CANE,             3,    6,   6000, "Фермерство"));
        register(new ShopItem("melon",            "Арбуз (блок)",          Material.MELON,                  8,   16,   4000, "Фермерство"));
        register(new ShopItem("pumpkin",          "Тыква (блок)",          Material.PUMPKIN,                8,   16,   4000, "Фермерство"));
        register(new ShopItem("chorus_fruit",     "Плод хоруса",           Material.CHORUS_FRUIT,           3,    6,   4000, "Фермерство"));
        // === НОВЫЕ ПОЗИЦИИ ===
        register(new ShopItem("nether_wart",      "Незерский нарост",      Material.NETHER_WART,            5,    8,  10000, "Фермерство"));
        register(new ShopItem("wheat",            "Пшеница",               Material.WHEAT,                  5,   12,   7000, "Фермерство"));
        register(new ShopItem("bamboo",           "Бамбук",                Material.BAMBOO,                 2,    3,  10000, "Фермерство"));
        register(new ShopItem("honey_bottle",     "Бутылочка мёда",        Material.HONEY_BOTTLE,          25,   45,     75, "Фермерство"));

        // =====================================================================
        // ---- ЕДА ----
        // =====================================================================
        register(new ShopItem("carrot",           "Морковь",               Material.CARROT,                 4,    8,   5000, "Еда"));
        register(new ShopItem("cooked_beef",      "Стейк",                 Material.COOKED_BEEF,           14,   28,   3000, "Еда"));
        register(new ShopItem("cooked_pork",      "Жареная свинина",       Material.COOKED_PORKCHOP,       10,   20,   3000, "Еда"));
        register(new ShopItem("cooked_mutton",    "Жареная баранина",      Material.COOKED_MUTTON,         12,   24,   3000, "Еда"));
        register(new ShopItem("beetroot",         "Свёкла",                Material.BEETROOT,               5,   10,   5000, "Еда"));
        register(new ShopItem("apple",            "Яблоко",                Material.APPLE,                 15,   30,   1500, "Еда"));
        register(new ShopItem("enchanted_golden_apple","Зач. золотое яблоко", Material.ENCHANTED_GOLDEN_APPLE, 600, 1000, 30, "Еда"));
        register(new ShopItem("golden_apple",     "Золотое яблоко",        Material.GOLDEN_APPLE,         100,  250,    100, "Еда"));
        register(new ShopItem("glow_berries",     "Светящиеся ягоды",      Material.GLOW_BERRIES,           3,    7,   1500, "Еда"));
        register(new ShopItem("dried_kelp",       "Сушёная ламинария",     Material.DRIED_KELP,             3,    6,   5000, "Еда"));
        register(new ShopItem("sweet_berries",    "Сладкие ягоды",         Material.SWEET_BERRIES,          2,    5,   2500, "Еда"));
        register(new ShopItem("golden_carrot", "Золотая морковь", Material.GOLDEN_CARROT, 7, 14, 500, "Еда"));

        // =====================================================================
        // ---- МОБЫ (только покупка) ----
        // =====================================================================
        // Яйца призыва — только покупка (sellPrice = -1)
        register(new ShopItem("zombie_egg",       "Яйцо зомби",            Material.ZOMBIE_SPAWN_EGG,      -1, 1000,     30, "Мобы"));
        register(new ShopItem("skeleton_egg",     "Яйцо скелета",          Material.SKELETON_SPAWN_EGG,    -1, 1000,     30, "Мобы"));
        register(new ShopItem("spider_egg",       "Яйцо паука",            Material.SPIDER_SPAWN_EGG,      -1, 1000,     30, "Мобы"));
        register(new ShopItem("chicken_egg",      "Яйцо курицы",           Material.CHICKEN_SPAWN_EGG,     -1, 1000,     30, "Мобы"));
        register(new ShopItem("cow_egg",          "Яйцо коровы",           Material.COW_SPAWN_EGG,         -1, 1000,     30, "Мобы"));
        register(new ShopItem("pig_egg",          "Яйцо свиньи",           Material.PIG_SPAWN_EGG,         -1, 1000,     30, "Мобы"));
        register(new ShopItem("sheep_egg",        "Яйцо овечки",           Material.SHEEP_SPAWN_EGG,       -1, 1000,     30, "Мобы"));
        register(new ShopItem("slime_egg",        "Яйцо слизня",           Material.SLIME_SPAWN_EGG,       -1, 3000,     30, "Мобы"));
        // Суперкирка — только покупка (sellPrice = -1)
        // Выдаётся через createSuperPickaxe() — специальный предмет плагина SuperItemsTwix
        register(new ShopItem("super_pickaxe",    "Суперкирка",            Material.NETHERITE_PICKAXE,     -1, 7000,     25, "Мобы"));

        // ---- Формируем список категорий (уникальных, в порядке добавления) ----
        for (ShopItem item : ITEMS.values()) {
            if (!CATEGORIES.contains(item.getCategory())) {
                CATEGORIES.add(item.getCategory());
            }
        }
    }

    private static void register(ShopItem item) {
        ITEMS.put(item.getId(), item);
    }

    public static ShopItem getById(String id) { return ITEMS.get(id); }

    public static List<ShopItem> getByCategory(String category) {
        List<ShopItem> result = new ArrayList<>();
        for (ShopItem item : ITEMS.values())
            if (item.getCategory().equals(category)) result.add(item);
        return result;
    }

    public static List<String> getCategories()  { return new ArrayList<>(CATEGORIES); }
    public static List<String> getAllIds()        { return new ArrayList<>(ITEMS.keySet()); }
    public static Collection<ShopItem> getAllItems() { return ITEMS.values(); }
}