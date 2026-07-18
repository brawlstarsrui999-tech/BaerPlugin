package com.buyerplugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Менеджер данных магазина.
 *
 * Хранит:
 * - серверный склад каждого предмета (сколько ресурса СЕЙЧАС в байере)
 *   При продаже игрока → байеру: stock увеличивается (до serverLimit)
 *   При покупке игрока ← байера: stock уменьшается (до 0)
 * - вклад каждого игрока
 * - время следующего сброса
 *
 * Формат data.yml:
 *   next-reset: <epochSecond>
 *   stock:
 *     <itemId>: <int>
 *   player-contributions:
 *     <uuid>:
 *       <itemId>: <int>
 */
public class ShopData {

    private static final ZoneId    KYIV_ZONE    = ZoneId.of("Europe/Kiev");
    private static final DayOfWeek RESET_DAY    = DayOfWeek.MONDAY;
    private static final int       RESET_HOUR   = 13;
    private static final int       RESET_MINUTE = 0;

    private final BuyerPlugin    plugin;
    private final Logger         log;
    private File                 dataFile;
    private FileConfiguration    dataCfg;

    // itemId → сколько ресурса сейчас лежит в байере (0..serverLimit)
    private final Map<String, Integer>              serverStock   = new HashMap<>();
    // uuid → (itemId → количество проданного игроком суммарно)
    private final Map<UUID, Map<String, Integer>>   contributions = new HashMap<>();
    // Следующий момент сброса (UTC epochSecond)
    private long nextResetEpoch;

    public ShopData(BuyerPlugin plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
    }

    // -------------------------------------------------------------------------
    // LOAD
    // -------------------------------------------------------------------------
    public void load() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try { dataFile.createNewFile(); }
            catch (IOException e) { log.severe("Не удалось создать data.yml: " + e.getMessage()); }
        }
        dataCfg = YamlConfiguration.loadConfiguration(dataFile);

        // Загружаем время сброса
        nextResetEpoch = dataCfg.getLong("next-reset", 0L);
        if (nextResetEpoch == 0L) nextResetEpoch = computeNextReset();

        // Загружаем склад байера (поддерживаем старый ключ "limits")
        String stockSection = null;
        if (dataCfg.isConfigurationSection("stock"))  stockSection = "stock";
        else if (dataCfg.isConfigurationSection("limits")) stockSection = "limits";

        if (stockSection != null) {
            for (String key : dataCfg.getConfigurationSection(stockSection).getKeys(false)) {
                serverStock.put(key, dataCfg.getInt(stockSection + "." + key, 0));
            }
            if ("limits".equals(stockSection)) {
                dataCfg.set("limits", null);
                log.info("Мигрированы данные из 'limits' -> 'stock'");
            }
        }

        // =====================================================================
        // НАЧАЛЬНЫЙ СКЛАД ДЛЯ ПРЕДМЕТОВ «СКЛАДА БАЙЕРА»
        // Некоторые предметы уже имеют начальное значение при первом старте.
        // =====================================================================
        initDefaultStock("nether_star",              1,  5);   // 1/5
        initDefaultStock("mace_head",                1,  5);   // 1/5  (навершие булавы)
        initDefaultStock("sponge",                  20, 200);  // 20/200
        initDefaultStock("trial_key",                5,  15);  // 5/15
        initDefaultStock("enchanted_golden_apple",   5,  30);  // 5/30
        initDefaultStock("experience_bottle",   750,  750);  // 750/750
        initDefaultStock("zombie_egg",   30,  30);  // 30/30
        initDefaultStock("skeleton_egg",   30,  30);  // 30/30
        initDefaultStock("spider_egg",   30,  30);  // 30/30
        initDefaultStock("chicken_egg",   30,  30);  // 30/30
        initDefaultStock("cow_egg",   30,  30);  // 30/30
        initDefaultStock("pig_egg",   30,  30);  // 30/30
        initDefaultStock("sheep_egg",   30,  30);  // 30/30
        initDefaultStock("slime_egg",   30,  30);  // 30/30
        initDefaultStock("super_pickaxe",   25,  25);  // 25/25

        // Загружаем вклады игроков
        if (dataCfg.isConfigurationSection("player-contributions")) {
            for (String uuidStr : dataCfg.getConfigurationSection("player-contributions").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    Map<String, Integer> map = new HashMap<>();
                    if (dataCfg.isConfigurationSection("player-contributions." + uuidStr)) {
                        for (String itemId : dataCfg.getConfigurationSection("player-contributions." + uuidStr).getKeys(false)) {
                            map.put(itemId, dataCfg.getInt("player-contributions." + uuidStr + "." + itemId, 0));
                        }
                    }
                    contributions.put(uuid, map);
                } catch (IllegalArgumentException ignored) {}
            }
        }

        log.info("ShopData загружен. Следующий сброс: " +
                Instant.ofEpochSecond(nextResetEpoch).atZone(KYIV_ZONE).toLocalDateTime());
    }

    /**
     * Устанавливает начальное значение склада ТОЛЬКО если ключ ещё не был сохранён.
     * @param itemId    ID предмета
     * @param initValue начальное значение
     * @param maxLimit  максимальный лимит (для проверки)
     */
    private void initDefaultStock(String itemId, int initValue, int maxLimit) {
        if (!serverStock.containsKey(itemId)) {
            serverStock.put(itemId, Math.min(initValue, maxLimit));
        }
    }

    // -------------------------------------------------------------------------
    // SAVE
    // -------------------------------------------------------------------------
    public void save() {
        dataCfg.set("next-reset", nextResetEpoch);
        dataCfg.set("limits", null); // удаляем устаревший раздел

        for (Map.Entry<String, Integer> e : serverStock.entrySet()) {
            dataCfg.set("stock." + e.getKey(), e.getValue());
        }
        for (Map.Entry<UUID, Map<String, Integer>> outer : contributions.entrySet()) {
            String uuidStr = outer.getKey().toString();
            for (Map.Entry<String, Integer> inner : outer.getValue().entrySet()) {
                dataCfg.set("player-contributions." + uuidStr + "." + inner.getKey(), inner.getValue());
            }
        }
        try { dataCfg.save(dataFile); }
        catch (IOException e) { log.severe("Не удалось сохранить data.yml: " + e.getMessage()); }
    }

    // -------------------------------------------------------------------------
    // RESET
    // -------------------------------------------------------------------------
    public void checkAndReset() {
        long now = Instant.now().getEpochSecond();
        if (now >= nextResetEpoch) {
            resetLimits();
            nextResetEpoch = computeNextReset();
            log.info("[BuyerPlugin] Склад сброшен. Следующий сброс: " +
                    Instant.ofEpochSecond(nextResetEpoch).atZone(KYIV_ZONE).toLocalDateTime());
            save();
        }
    }

    /**
     * Сброс: возвращаем склад каждого предмета на половину от лимита.
     * Для предметов со специальным начальным значением используем его.
     */
    public void resetLimits() {
        serverStock.clear();
        contributions.clear();
        for (ShopItem item : ShopRegistry.getAllItems()) {
            serverStock.put(item.getId(), item.getServerLimit() / 2);
        }
        // Восстанавливаем специальные начальные значения после сброса
        initDefaultStock("nether_star",            1,  5);
        initDefaultStock("mace_head",              1,  5);
        initDefaultStock("sponge",                20, 200);
        initDefaultStock("trial_key",              5,  15);
        initDefaultStock("enchanted_golden_apple", 5,  30);
        initDefaultStock("experience_bottle",   750,  750);  // 750/750
        initDefaultStock("zombie_egg",   30,  30);  // 30/30
        initDefaultStock("skeleton_egg",   30,  30);  // 30/30
        initDefaultStock("spider_egg",   30,  30);  // 30/30
        initDefaultStock("chicken_egg",   30,  30);  // 30/30
        initDefaultStock("cow_egg",   30,  30);  // 30/30
        initDefaultStock("pig_egg",   30,  30);  // 30/30
        initDefaultStock("sheep_egg",   30,  30);  // 30/30
        initDefaultStock("slime_egg",   30,  30);  // 30/30
        initDefaultStock("super_pickaxe",   25,  25);  // 25/25
    }

    private long computeNextReset() {
        ZonedDateTime now  = ZonedDateTime.now(KYIV_ZONE);
        ZonedDateTime next = now.with(TemporalAdjusters.nextOrSame(RESET_DAY))
                .withHour(RESET_HOUR).withMinute(RESET_MINUTE).withSecond(0).withNano(0);
        if (!next.isAfter(now)) next = next.with(TemporalAdjusters.next(RESET_DAY));
        return next.toEpochSecond();
    }

    // -------------------------------------------------------------------------
    // API (склад)
    // -------------------------------------------------------------------------
    public int getServerStock(String itemId)                       { return serverStock.getOrDefault(itemId, 0); }
    public void subtractStock(String itemId, int amount)           { serverStock.put(itemId, Math.max(0, serverStock.getOrDefault(itemId, 0) - amount)); }
    public void addStock(String itemId, int amount, int maxLimit)  { serverStock.put(itemId, Math.min(maxLimit, serverStock.getOrDefault(itemId, 0) + amount)); }

    // -------------------------------------------------------------------------
    // Вклады игроков
    // -------------------------------------------------------------------------
    public void addContribution(UUID uuid, String itemId, int amount) {
        contributions.computeIfAbsent(uuid, k -> new HashMap<>()).merge(itemId, amount, Integer::sum);
    }
    public int getContribution(UUID uuid, String itemId) {
        Map<String, Integer> m = contributions.get(uuid);
        return m == null ? 0 : m.getOrDefault(itemId, 0);
    }

    // -------------------------------------------------------------------------
    // Прочее
    // -------------------------------------------------------------------------
    public long    getNextResetEpoch() { return nextResetEpoch; }
    public Instant getNextResetTime()  { return Instant.ofEpochSecond(nextResetEpoch); }

    /** @deprecated Используй getServerStock() */
    @Deprecated public int  getServerSold(String itemId)            { return getServerStock(itemId); }
    /** @deprecated Логика теперь в ShopGUI */
    @Deprecated public void addServerSold(String itemId, int amount) { /* no-op */ }
}