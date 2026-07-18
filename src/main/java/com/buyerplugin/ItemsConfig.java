package com.buyerplugin;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

/**
 * Загружает items.yml из папки плагина и наполняет ShopRegistry.
 *
 * Структура items.yml:
 * items:
 *   <item-id>:
 *     display-name: "Алмаз"
 *     material: DIAMOND
 *     category: "Руда"
 *     sell-price: 60        # цена продажи игрока → байеру (-1 = нельзя)
 *     buy-price: 120        # цена покупки игрока ← байером (-1 = нельзя)
 *     server-limit: 1500    # максимальный запас байера
 *     initial-stock: 750    # начальный/сбросный запас
 *     enabled: true         # включён ли предмет в магазин
 */
public class ItemsConfig {

    private static final String FILE_NAME = "items.yml";

    // Фиксированные разделы — создавать новые нельзя!
    static final String[] ALLOWED_CATEGORIES = {
            "Руда", "Строительство", "Лут с мобов", "Фермерство", "Еда", "Мобы"
    };

    private final BuyerPlugin plugin;
    private final Logger log;

    public ItemsConfig(BuyerPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    /**
     * Загружает items.yml.
     * Если файл не существует — создаёт дефолтный (со всеми оригинальными предметами).
     * Возвращает количество загруженных предметов.
     */
    public int load() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }

        File configFile = new File(dataFolder, FILE_NAME);
        if (!configFile.exists()) {
            generateDefaultConfig(configFile);
        }

        FileConfiguration cfg;
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(configFile), StandardCharsets.UTF_8)) {
            cfg = YamlConfiguration.loadConfiguration(reader);
        } catch (IOException e) {
            log.severe("[BuyerPlugin] Не удалось прочитать " + FILE_NAME + ": " + e.getMessage());
            return 0;
        }

        ConfigurationSection itemsSection = cfg.getConfigurationSection("items");
        if (itemsSection == null) {
            log.warning("[BuyerPlugin] Секция 'items' не найдена в " + FILE_NAME);
            return 0;
        }

        int loaded = 0;
        int skipped = 0;

        for (String itemId : itemsSection.getKeys(false)) {
            ConfigurationSection sec = itemsSection.getConfigurationSection(itemId);
            if (sec == null) continue;

            // Читаем поля
            String displayName = sec.getString("display-name", itemId);
            String materialName = sec.getString("material", "").toUpperCase();
            String category = sec.getString("category", "");
            double sellPrice = sec.getDouble("sell-price", -1);
            double buyPrice = sec.getDouble("buy-price", -1);
            int serverLimit = sec.getInt("server-limit", 100);
            int initialStock = sec.getInt("initial-stock", 0);
            boolean enabled = sec.getBoolean("enabled", true);

            // Пропускаем отключённые
            if (!enabled) {
                log.info("[BuyerPlugin] Предмет '" + itemId + "' пропущен (enabled: false)");
                skipped++;
                continue;
            }

            // Проверяем категорию
            if (!isCategoryAllowed(category)) {
                log.warning("[BuyerPlugin] Предмет '" + itemId + "': недопустимая категория '" + category + "'. Пропущен.");
                skipped++;
                continue;
            }

            // Разбираем Material
            Material material;
            try {
                material = Material.valueOf(materialName);
            } catch (IllegalArgumentException e) {
                log.warning("[BuyerPlugin] Предмет '" + itemId + "': неизвестный Material '" + materialName + "'. Пропущен.");
                skipped++;
                continue;
            }

            // Валидация лимитов
            if (serverLimit < 1) serverLimit = 1;
            if (initialStock < 0) initialStock = 0;
            if (initialStock > serverLimit) {
                log.warning("[BuyerPlugin] Предмет '" + itemId + "': initial-stock (" + initialStock
                        + ") > server-limit (" + serverLimit + "). Урезаем до server-limit.");
                initialStock = serverLimit;
            }

            // Регистрируем в ShopRegistry
            ShopItem shopItem = new ShopItem(itemId, displayName, material, sellPrice, buyPrice, serverLimit, category);
            ShopRegistry.register(shopItem, initialStock);
            loaded++;
        }

        log.info("[BuyerPlugin] items.yml загружен: " + loaded + " предметов активно, " + skipped + " пропущено.");
        return loaded;
    }

    private boolean isCategoryAllowed(String category) {
        for (String allowed : ALLOWED_CATEGORIES) {
            if (allowed.equals(category)) return true;
        }
        return false;
    }

    /**
     * Генерирует дефолтный items.yml с полным набором предметов оригинального плагина.
     */
    private void generateDefaultConfig(File file) {
        log.info("[BuyerPlugin] items.yml не найден — создаю дефолтный конфиг...");

        String content =
                "# ============================================================\n" +
                        "# BaerPlugin — items.yml\n" +
                        "# Добавляй и убирай предметы здесь — без изменения кода плагина!\n" +
                        "# После изменений: /buyer reload\n" +
                        "# ============================================================\n" +
                        "#\n" +
                        "# Доступные категории (нельзя создавать новые!):\n" +
                        "#   Руда | Строительство | Лут с мобов | Фермерство | Еда | Мобы\n" +
                        "#\n" +
                        "# Поля каждого предмета:\n" +
                        "#   display-name  — название в GUI (поддерживает §-коды)\n" +
                        "#   material      — Bukkit Material (напр. DIAMOND, EMERALD)\n" +
                        "#   category      — категория (только из списка выше!)\n" +
                        "#   sell-price    — игрок продаёт → байеру  (-1 = нельзя продать)\n" +
                        "#   buy-price     — игрок покупает ← байера (-1 = нельзя купить)\n" +
                        "#   server-limit  — максимальный запас байера для этого предмета\n" +
                        "#   initial-stock — сколько лежит в байере при старте/сбросе\n" +
                        "#   enabled       — включён ли предмет (true/false)\n" +
                        "#\n" +
                        "items:\n" +
                        "\n" +
                        "  # ── Руда ──────────────────────────────────────────────────────\n" +
                        "\n" +
                        "  redstone:\n" +
                        "    display-name: \"Редстоун\"\n" +
                        "    material: REDSTONE\n" +
                        "    category: \"Руда\"\n" +
                        "    sell-price: 10\n" +
                        "    buy-price: 20\n" +
                        "    server-limit: 4000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  lapis:\n" +
                        "    display-name: \"Лазурит\"\n" +
                        "    material: LAPIS_LAZULI\n" +
                        "    category: \"Руда\"\n" +
                        "    sell-price: 5\n" +
                        "    buy-price: 10\n" +
                        "    server-limit: 5000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  emerald:\n" +
                        "    display-name: \"Изумруд\"\n" +
                        "    material: EMERALD\n" +
                        "    category: \"Руда\"\n" +
                        "    sell-price: 10\n" +
                        "    buy-price: 20\n" +
                        "    server-limit: 3000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  iron_ingot:\n" +
                        "    display-name: \"Железный слиток\"\n" +
                        "    material: IRON_INGOT\n" +
                        "    category: \"Руда\"\n" +
                        "    sell-price: 15\n" +
                        "    buy-price: 30\n" +
                        "    server-limit: 3000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  gold_ingot:\n" +
                        "    display-name: \"Золотой слиток\"\n" +
                        "    material: GOLD_INGOT\n" +
                        "    category: \"Руда\"\n" +
                        "    sell-price: 20\n" +
                        "    buy-price: 40\n" +
                        "    server-limit: 3000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  diamond:\n" +
                        "    display-name: \"Алмаз\"\n" +
                        "    material: DIAMOND\n" +
                        "    category: \"Руда\"\n" +
                        "    sell-price: 60\n" +
                        "    buy-price: 120\n" +
                        "    server-limit: 1500\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  netherite:\n" +
                        "    display-name: \"Незеритовый слиток\"\n" +
                        "    material: NETHERITE_INGOT\n" +
                        "    category: \"Руда\"\n" +
                        "    sell-price: 1000\n" +
                        "    buy-price: -1\n" +
                        "    server-limit: 150\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  amethyst_shard:\n" +
                        "    display-name: \"Осколок аметиста\"\n" +
                        "    material: AMETHYST_SHARD\n" +
                        "    category: \"Руда\"\n" +
                        "    sell-price: 8\n" +
                        "    buy-price: 10\n" +
                        "    server-limit: 700\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  amethyst_block:\n" +
                        "    display-name: \"Аметистовый блок\"\n" +
                        "    material: AMETHYST_BLOCK\n" +
                        "    category: \"Руда\"\n" +
                        "    sell-price: 2\n" +
                        "    buy-price: 5\n" +
                        "    server-limit: 1000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  glowstone:\n" +
                        "    display-name: \"Светокамень\"\n" +
                        "    material: GLOWSTONE\n" +
                        "    category: \"Руда\"\n" +
                        "    sell-price: 5\n" +
                        "    buy-price: 10\n" +
                        "    server-limit: 5000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  stone:\n" +
                        "    display-name: \"Камень\"\n" +
                        "    material: STONE\n" +
                        "    category: \"Руда\"\n" +
                        "    sell-price: 2\n" +
                        "    buy-price: 4\n" +
                        "    server-limit: 20000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  cobblestone:\n" +
                        "    display-name: \"Булыжник\"\n" +
                        "    material: COBBLESTONE\n" +
                        "    category: \"Руда\"\n" +
                        "    sell-price: 1\n" +
                        "    buy-price: 2\n" +
                        "    server-limit: 40000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  quartz:\n" +
                        "    display-name: \"Кварц\"\n" +
                        "    material: QUARTZ\n" +
                        "    category: \"Руда\"\n" +
                        "    sell-price: 6\n" +
                        "    buy-price: 12\n" +
                        "    server-limit: 1500\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  raw_copper:\n" +
                        "    display-name: \"Не переплавленная медь\"\n" +
                        "    material: RAW_COPPER\n" +
                        "    category: \"Руда\"\n" +
                        "    sell-price: 7\n" +
                        "    buy-price: 14\n" +
                        "    server-limit: 3000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  coal:\n" +
                        "    display-name: \"Уголь\"\n" +
                        "    material: COAL\n" +
                        "    category: \"Руда\"\n" +
                        "    sell-price: 3\n" +
                        "    buy-price: 6\n" +
                        "    server-limit: 2500\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  # ── Строительство ─────────────────────────────────────────────\n" +
                        "\n" +
                        "  ice:\n" +
                        "    display-name: \"Лёд\"\n" +
                        "    material: ICE\n" +
                        "    category: \"Строительство\"\n" +
                        "    sell-price: 5\n" +
                        "    buy-price: 8\n" +
                        "    server-limit: 3000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  prismarine:\n" +
                        "    display-name: \"Призмарин\"\n" +
                        "    material: PRISMARINE\n" +
                        "    category: \"Строительство\"\n" +
                        "    sell-price: 6\n" +
                        "    buy-price: 10\n" +
                        "    server-limit: 1500\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  soul_sand:\n" +
                        "    display-name: \"Песок душ\"\n" +
                        "    material: SOUL_SAND\n" +
                        "    category: \"Строительство\"\n" +
                        "    sell-price: 3\n" +
                        "    buy-price: 5\n" +
                        "    server-limit: 3000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  andesite:\n" +
                        "    display-name: \"Андезит\"\n" +
                        "    material: ANDESITE\n" +
                        "    category: \"Строительство\"\n" +
                        "    sell-price: 3\n" +
                        "    buy-price: 4\n" +
                        "    server-limit: 2500\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  diorite:\n" +
                        "    display-name: \"Диорит\"\n" +
                        "    material: DIORITE\n" +
                        "    category: \"Строительство\"\n" +
                        "    sell-price: 3\n" +
                        "    buy-price: 4\n" +
                        "    server-limit: 2500\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  deepslate_bricks:\n" +
                        "    display-name: \"Глубинносланцевые кирпичи\"\n" +
                        "    material: DEEPSLATE_BRICKS\n" +
                        "    category: \"Строительство\"\n" +
                        "    sell-price: 2\n" +
                        "    buy-price: 3\n" +
                        "    server-limit: 5000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  obsidian:\n" +
                        "    display-name: \"Обсидиан\"\n" +
                        "    material: OBSIDIAN\n" +
                        "    category: \"Строительство\"\n" +
                        "    sell-price: 25\n" +
                        "    buy-price: 40\n" +
                        "    server-limit: 300\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  oak_log:\n" +
                        "    display-name: \"Дуб\"\n" +
                        "    material: OAK_LOG\n" +
                        "    category: \"Строительство\"\n" +
                        "    sell-price: 3\n" +
                        "    buy-price: 6\n" +
                        "    server-limit: 5000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  spruce_log:\n" +
                        "    display-name: \"Еловое бревно\"\n" +
                        "    material: SPRUCE_LOG\n" +
                        "    category: \"Строительство\"\n" +
                        "    sell-price: 6\n" +
                        "    buy-price: 12\n" +
                        "    server-limit: 3000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  birch_log:\n" +
                        "    display-name: \"Берёза\"\n" +
                        "    material: BIRCH_LOG\n" +
                        "    category: \"Строительство\"\n" +
                        "    sell-price: 5\n" +
                        "    buy-price: 10\n" +
                        "    server-limit: 3000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  glass:\n" +
                        "    display-name: \"Стекло\"\n" +
                        "    material: GLASS\n" +
                        "    category: \"Строительство\"\n" +
                        "    sell-price: 8\n" +
                        "    buy-price: 16\n" +
                        "    server-limit: 1000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  torch:\n" +
                        "    display-name: \"Факел\"\n" +
                        "    material: TORCH\n" +
                        "    category: \"Строительство\"\n" +
                        "    sell-price: 2\n" +
                        "    buy-price: 4\n" +
                        "    server-limit: 1000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  # ── Лут с мобов ───────────────────────────────────────────────\n" +
                        "\n" +
                        "  rotten_flesh:\n" +
                        "    display-name: \"Гнилая плоть\"\n" +
                        "    material: ROTTEN_FLESH\n" +
                        "    category: \"Лут с мобов\"\n" +
                        "    sell-price: 2\n" +
                        "    buy-price: 4\n" +
                        "    server-limit: 5000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  bone:\n" +
                        "    display-name: \"Кость\"\n" +
                        "    material: BONE\n" +
                        "    category: \"Лут с мобов\"\n" +
                        "    sell-price: 5\n" +
                        "    buy-price: 10\n" +
                        "    server-limit: 4000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  string:\n" +
                        "    display-name: \"Нить\"\n" +
                        "    material: STRING\n" +
                        "    category: \"Лут с мобов\"\n" +
                        "    sell-price: 4\n" +
                        "    buy-price: 8\n" +
                        "    server-limit: 4000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  gunpowder:\n" +
                        "    display-name: \"Порох\"\n" +
                        "    material: GUNPOWDER\n" +
                        "    category: \"Лут с мобов\"\n" +
                        "    sell-price: 12\n" +
                        "    buy-price: 24\n" +
                        "    server-limit: 3000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  slimeball:\n" +
                        "    display-name: \"Слизь\"\n" +
                        "    material: SLIME_BALL\n" +
                        "    category: \"Лут с мобов\"\n" +
                        "    sell-price: 3\n" +
                        "    buy-price: 6\n" +
                        "    server-limit: 2500\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  magma_cream:\n" +
                        "    display-name: \"Огненная слизь\"\n" +
                        "    material: MAGMA_CREAM\n" +
                        "    category: \"Лут с мобов\"\n" +
                        "    sell-price: 10\n" +
                        "    buy-price: 20\n" +
                        "    server-limit: 2000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  ghast_tear:\n" +
                        "    display-name: \"Слёзы гаста\"\n" +
                        "    material: GHAST_TEAR\n" +
                        "    category: \"Лут с мобов\"\n" +
                        "    sell-price: 120\n" +
                        "    buy-price: 240\n" +
                        "    server-limit: 500\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  blaze_rod:\n" +
                        "    display-name: \"Стержень ифрита\"\n" +
                        "    material: BLAZE_ROD\n" +
                        "    category: \"Лут с мобов\"\n" +
                        "    sell-price: 30\n" +
                        "    buy-price: 60\n" +
                        "    server-limit: 1000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  breeze_rod:\n" +
                        "    display-name: \"Стержень вихря\"\n" +
                        "    material: BREEZE_ROD\n" +
                        "    category: \"Лут с мобов\"\n" +
                        "    sell-price: 40\n" +
                        "    buy-price: 80\n" +
                        "    server-limit: 800\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  shulker_shell:\n" +
                        "    display-name: \"Панцирь шалкера\"\n" +
                        "    material: SHULKER_SHELL\n" +
                        "    category: \"Лут с мобов\"\n" +
                        "    sell-price: 70\n" +
                        "    buy-price: 140\n" +
                        "    server-limit: 400\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  spider_eye:\n" +
                        "    display-name: \"Паучий глаз\"\n" +
                        "    material: SPIDER_EYE\n" +
                        "    category: \"Лут с мобов\"\n" +
                        "    sell-price: 8\n" +
                        "    buy-price: 16\n" +
                        "    server-limit: 2000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  leather:\n" +
                        "    display-name: \"Кожа\"\n" +
                        "    material: LEATHER\n" +
                        "    category: \"Лут с мобов\"\n" +
                        "    sell-price: 15\n" +
                        "    buy-price: 30\n" +
                        "    server-limit: 2000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  wool:\n" +
                        "    display-name: \"Шерсть\"\n" +
                        "    material: WHITE_WOOL\n" +
                        "    category: \"Лут с мобов\"\n" +
                        "    sell-price: 3\n" +
                        "    buy-price: 6\n" +
                        "    server-limit: 5000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  trial_key:\n" +
                        "    display-name: \"Ключ испытаний\"\n" +
                        "    material: TRIAL_KEY\n" +
                        "    category: \"Лут с мобов\"\n" +
                        "    sell-price: 300\n" +
                        "    buy-price: 500\n" +
                        "    server-limit: 15\n" +
                        "    initial-stock: 5\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  sponge:\n" +
                        "    display-name: \"Губка\"\n" +
                        "    material: SPONGE\n" +
                        "    category: \"Лут с мобов\"\n" +
                        "    sell-price: 100\n" +
                        "    buy-price: 200\n" +
                        "    server-limit: 200\n" +
                        "    initial-stock: 20\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  feather:\n" +
                        "    display-name: \"Перо\"\n" +
                        "    material: FEATHER\n" +
                        "    category: \"Лут с мобов\"\n" +
                        "    sell-price: 5\n" +
                        "    buy-price: 8\n" +
                        "    server-limit: 300\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  nether_star:\n" +
                        "    display-name: \"Звезда незера\"\n" +
                        "    material: NETHER_STAR\n" +
                        "    category: \"Лут с мобов\"\n" +
                        "    sell-price: 1500\n" +
                        "    buy-price: 5000\n" +
                        "    server-limit: 5\n" +
                        "    initial-stock: 1\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  mace_head:\n" +
                        "    display-name: \"Навершие булавы\"\n" +
                        "    material: MACE\n" +
                        "    category: \"Лут с мобов\"\n" +
                        "    sell-price: 3000\n" +
                        "    buy-price: 6000\n" +
                        "    server-limit: 5\n" +
                        "    initial-stock: 1\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  ender_pearl:\n" +
                        "    display-name: \"Эндер жемчуг\"\n" +
                        "    material: ENDER_PEARL\n" +
                        "    category: \"Лут с мобов\"\n" +
                        "    sell-price: 20\n" +
                        "    buy-price: 40\n" +
                        "    server-limit: 750\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  experience_bottle:\n" +
                        "    display-name: \"Пузырёк опыта\"\n" +
                        "    material: EXPERIENCE_BOTTLE\n" +
                        "    category: \"Лут с мобов\"\n" +
                        "    sell-price: -1\n" +
                        "    buy-price: 5\n" +
                        "    server-limit: 750\n" +
                        "    initial-stock: 750\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  # ── Фермерство ────────────────────────────────────────────────\n" +
                        "\n" +
                        "  cactus:\n" +
                        "    display-name: \"Кактус\"\n" +
                        "    material: CACTUS\n" +
                        "    category: \"Фермерство\"\n" +
                        "    sell-price: 2\n" +
                        "    buy-price: 4\n" +
                        "    server-limit: 6000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  sugar_cane:\n" +
                        "    display-name: \"Тростник\"\n" +
                        "    material: SUGAR_CANE\n" +
                        "    category: \"Фермерство\"\n" +
                        "    sell-price: 3\n" +
                        "    buy-price: 6\n" +
                        "    server-limit: 6000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  melon:\n" +
                        "    display-name: \"Арбуз (блок)\"\n" +
                        "    material: MELON\n" +
                        "    category: \"Фермерство\"\n" +
                        "    sell-price: 8\n" +
                        "    buy-price: 16\n" +
                        "    server-limit: 4000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  pumpkin:\n" +
                        "    display-name: \"Тыква (блок)\"\n" +
                        "    material: PUMPKIN\n" +
                        "    category: \"Фермерство\"\n" +
                        "    sell-price: 8\n" +
                        "    buy-price: 16\n" +
                        "    server-limit: 4000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  chorus_fruit:\n" +
                        "    display-name: \"Плод хоруса\"\n" +
                        "    material: CHORUS_FRUIT\n" +
                        "    category: \"Фермерство\"\n" +
                        "    sell-price: 3\n" +
                        "    buy-price: 6\n" +
                        "    server-limit: 4000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  nether_wart:\n" +
                        "    display-name: \"Незерский нарост\"\n" +
                        "    material: NETHER_WART\n" +
                        "    category: \"Фермерство\"\n" +
                        "    sell-price: 5\n" +
                        "    buy-price: 8\n" +
                        "    server-limit: 10000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  wheat:\n" +
                        "    display-name: \"Пшеница\"\n" +
                        "    material: WHEAT\n" +
                        "    category: \"Фермерство\"\n" +
                        "    sell-price: 5\n" +
                        "    buy-price: 12\n" +
                        "    server-limit: 7000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  bamboo:\n" +
                        "    display-name: \"Бамбук\"\n" +
                        "    material: BAMBOO\n" +
                        "    category: \"Фермерство\"\n" +
                        "    sell-price: 2\n" +
                        "    buy-price: 3\n" +
                        "    server-limit: 10000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  honey_bottle:\n" +
                        "    display-name: \"Бутылочка мёда\"\n" +
                        "    material: HONEY_BOTTLE\n" +
                        "    category: \"Фермерство\"\n" +
                        "    sell-price: 25\n" +
                        "    buy-price: 45\n" +
                        "    server-limit: 75\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  # ── Еда ───────────────────────────────────────────────────────\n" +
                        "\n" +
                        "  carrot:\n" +
                        "    display-name: \"Морковь\"\n" +
                        "    material: CARROT\n" +
                        "    category: \"Еда\"\n" +
                        "    sell-price: 4\n" +
                        "    buy-price: 8\n" +
                        "    server-limit: 5000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  cooked_beef:\n" +
                        "    display-name: \"Стейк\"\n" +
                        "    material: COOKED_BEEF\n" +
                        "    category: \"Еда\"\n" +
                        "    sell-price: 14\n" +
                        "    buy-price: 28\n" +
                        "    server-limit: 3000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  cooked_pork:\n" +
                        "    display-name: \"Жареная свинина\"\n" +
                        "    material: COOKED_PORKCHOP\n" +
                        "    category: \"Еда\"\n" +
                        "    sell-price: 10\n" +
                        "    buy-price: 20\n" +
                        "    server-limit: 3000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  cooked_mutton:\n" +
                        "    display-name: \"Жареная баранина\"\n" +
                        "    material: COOKED_MUTTON\n" +
                        "    category: \"Еда\"\n" +
                        "    sell-price: 12\n" +
                        "    buy-price: 24\n" +
                        "    server-limit: 3000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  beetroot:\n" +
                        "    display-name: \"Свёкла\"\n" +
                        "    material: BEETROOT\n" +
                        "    category: \"Еда\"\n" +
                        "    sell-price: 5\n" +
                        "    buy-price: 10\n" +
                        "    server-limit: 5000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  apple:\n" +
                        "    display-name: \"Яблоко\"\n" +
                        "    material: APPLE\n" +
                        "    category: \"Еда\"\n" +
                        "    sell-price: 15\n" +
                        "    buy-price: 30\n" +
                        "    server-limit: 1500\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  enchanted_golden_apple:\n" +
                        "    display-name: \"Зач. золотое яблоко\"\n" +
                        "    material: ENCHANTED_GOLDEN_APPLE\n" +
                        "    category: \"Еда\"\n" +
                        "    sell-price: 600\n" +
                        "    buy-price: 1000\n" +
                        "    server-limit: 30\n" +
                        "    initial-stock: 5\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  golden_apple:\n" +
                        "    display-name: \"Золотое яблоко\"\n" +
                        "    material: GOLDEN_APPLE\n" +
                        "    category: \"Еда\"\n" +
                        "    sell-price: 100\n" +
                        "    buy-price: 250\n" +
                        "    server-limit: 100\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  glow_berries:\n" +
                        "    display-name: \"Светящиеся ягоды\"\n" +
                        "    material: GLOW_BERRIES\n" +
                        "    category: \"Еда\"\n" +
                        "    sell-price: 3\n" +
                        "    buy-price: 7\n" +
                        "    server-limit: 1500\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  dried_kelp:\n" +
                        "    display-name: \"Сушёная ламинария\"\n" +
                        "    material: DRIED_KELP\n" +
                        "    category: \"Еда\"\n" +
                        "    sell-price: 3\n" +
                        "    buy-price: 6\n" +
                        "    server-limit: 5000\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  sweet_berries:\n" +
                        "    display-name: \"Сладкие ягоды\"\n" +
                        "    material: SWEET_BERRIES\n" +
                        "    category: \"Еда\"\n" +
                        "    sell-price: 2\n" +
                        "    buy-price: 5\n" +
                        "    server-limit: 2500\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  golden_carrot:\n" +
                        "    display-name: \"Золотая морковь\"\n" +
                        "    material: GOLDEN_CARROT\n" +
                        "    category: \"Еда\"\n" +
                        "    sell-price: 7\n" +
                        "    buy-price: 14\n" +
                        "    server-limit: 500\n" +
                        "    initial-stock: 0\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  # ── Мобы ──────────────────────────────────────────────────────\n" +
                        "\n" +
                        "  zombie_egg:\n" +
                        "    display-name: \"Яйцо зомби\"\n" +
                        "    material: ZOMBIE_SPAWN_EGG\n" +
                        "    category: \"Мобы\"\n" +
                        "    sell-price: -1\n" +
                        "    buy-price: 1000\n" +
                        "    server-limit: 30\n" +
                        "    initial-stock: 30\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  skeleton_egg:\n" +
                        "    display-name: \"Яйцо скелета\"\n" +
                        "    material: SKELETON_SPAWN_EGG\n" +
                        "    category: \"Мобы\"\n" +
                        "    sell-price: -1\n" +
                        "    buy-price: 1000\n" +
                        "    server-limit: 30\n" +
                        "    initial-stock: 30\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  spider_egg:\n" +
                        "    display-name: \"Яйцо паука\"\n" +
                        "    material: SPIDER_SPAWN_EGG\n" +
                        "    category: \"Мобы\"\n" +
                        "    sell-price: -1\n" +
                        "    buy-price: 1000\n" +
                        "    server-limit: 30\n" +
                        "    initial-stock: 30\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  chicken_egg:\n" +
                        "    display-name: \"Яйцо курицы\"\n" +
                        "    material: CHICKEN_SPAWN_EGG\n" +
                        "    category: \"Мобы\"\n" +
                        "    sell-price: -1\n" +
                        "    buy-price: 1000\n" +
                        "    server-limit: 30\n" +
                        "    initial-stock: 30\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  cow_egg:\n" +
                        "    display-name: \"Яйцо коровы\"\n" +
                        "    material: COW_SPAWN_EGG\n" +
                        "    category: \"Мобы\"\n" +
                        "    sell-price: -1\n" +
                        "    buy-price: 1000\n" +
                        "    server-limit: 30\n" +
                        "    initial-stock: 30\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  pig_egg:\n" +
                        "    display-name: \"Яйцо свиньи\"\n" +
                        "    material: PIG_SPAWN_EGG\n" +
                        "    category: \"Мобы\"\n" +
                        "    sell-price: -1\n" +
                        "    buy-price: 1000\n" +
                        "    server-limit: 30\n" +
                        "    initial-stock: 30\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  sheep_egg:\n" +
                        "    display-name: \"Яйцо овечки\"\n" +
                        "    material: SHEEP_SPAWN_EGG\n" +
                        "    category: \"Мобы\"\n" +
                        "    sell-price: -1\n" +
                        "    buy-price: 1000\n" +
                        "    server-limit: 30\n" +
                        "    initial-stock: 30\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  slime_egg:\n" +
                        "    display-name: \"Яйцо слизня\"\n" +
                        "    material: SLIME_SPAWN_EGG\n" +
                        "    category: \"Мобы\"\n" +
                        "    sell-price: -1\n" +
                        "    buy-price: 3000\n" +
                        "    server-limit: 30\n" +
                        "    initial-stock: 30\n" +
                        "    enabled: true\n" +
                        "\n" +
                        "  super_pickaxe:\n" +
                        "    display-name: \"Суперкирка\"\n" +
                        "    material: NETHERITE_PICKAXE\n" +
                        "    category: \"Мобы\"\n" +
                        "    sell-price: -1\n" +
                        "    buy-price: 7000\n" +
                        "    server-limit: 25\n" +
                        "    initial-stock: 25\n" +
                        "    enabled: true\n";

        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write(content);
            log.info("[BuyerPlugin] Создан дефолтный items.yml в " + file.getAbsolutePath());
        } catch (IOException e) {
            log.severe("[BuyerPlugin] Не удалось создать items.yml: " + e.getMessage());
        }
    }
}
