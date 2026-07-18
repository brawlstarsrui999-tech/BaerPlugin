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
 *  - серверный склад каждого предмета (сколько СЕЙЧАС в байере)
 *  - вклад каждого игрока
 *  - время следующего сброса
 *
 * Начальные запасы при первом старте/сбросе берутся из ShopRegistry.getInitialStock()
 * (которые в свою очередь загружены из items.yml — поле initial-stock).
 *
 * Формат data.yml:
 *   next-reset: <epochSecond>
 *   stock:
 *     <itemId>: <amount>
 *   player-contributions:
 *     <uuid>:
 *       <itemId>: <amount>
 */
public class ShopData {

    private static final ZoneId KYIV_ZONE = ZoneId.of("Europe/Kiev");
    private static final DayOfWeek RESET_DAY    = DayOfWeek.MONDAY;
    private static final int       RESET_HOUR   = 13;
    private static final int       RESET_MINUTE = 0;

    private final BuyerPlugin plugin;
    private final Logger      log;

    private File              dataFile;
    private FileConfiguration dataCfg;

    /** itemId → сколько ресурса сейчас лежит в байере (0..serverLimit) */
    private final Map<String, Integer> serverStock = new HashMap<>();

    /** uuid → (itemId → количество проданного игроком суммарно) */
    private final Map<UUID, Map<String, Integer>> contributions = new HashMap<>();

    /** Следующий момент сброса (UTC epochSecond) */
    private long nextResetEpoch;

    public ShopData(BuyerPlugin plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOAD
    // ─────────────────────────────────────────────────────────────────────────

    public void load() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try { dataFile.createNewFile(); }
            catch (IOException e) { log.severe("Не удалось создать data.yml: " + e.getMessage()); }
        }

        dataCfg = YamlConfiguration.loadConfiguration(dataFile);

        // Время сброса
        nextResetEpoch = dataCfg.getLong("next-reset", 0L);
        if (nextResetEpoch == 0L) nextResetEpoch = computeNextReset();

        // Склад байера (поддержка старого ключа "limits")
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

        // Начальный склад для предметов, которых нет в data.yml
        // (новые предметы добавленные через items.yml после первого запуска)
        applyInitialStockForNewItems();

        // Вклады игроков
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
     * Для каждого предмета из ShopRegistry, которого нет в data.yml,
     * устанавливаем initial-stock из items.yml.
     * Это позволяет добавлять новые предметы без сброса всего склада.
     */
    public void applyInitialStockForNewItems() {
        for (ShopItem item : ShopRegistry.getAllItems()) {
            if (!serverStock.containsKey(item.getId())) {
                int initStock = ShopRegistry.getInitialStock(item.getId());
                serverStock.put(item.getId(), Math.min(initStock, item.getServerLimit()));
                log.info("[BuyerPlugin] Новый предмет '" + item.getId() + "': начальный склад = " + initStock);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SAVE
    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────
    // RESET
    // ─────────────────────────────────────────────────────────────────────────

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
     * Сброс склада.
     * Возвращаем каждый предмет к его initial-stock из items.yml.
     * Если initial-stock не задан — используем serverLimit / 2.
     */
    public void resetLimits() {
        serverStock.clear();
        contributions.clear();

        for (ShopItem item : ShopRegistry.getAllItems()) {
            int initStock = ShopRegistry.getInitialStock(item.getId());
            // Если initial-stock = 0 и не указан явно — fallback к половине лимита
            if (initStock == 0) initStock = item.getServerLimit() / 2;
            serverStock.put(item.getId(), Math.min(initStock, item.getServerLimit()));
        }
    }

    private long computeNextReset() {
        ZonedDateTime now  = ZonedDateTime.now(KYIV_ZONE);
        ZonedDateTime next = now.with(TemporalAdjusters.nextOrSame(RESET_DAY))
                .withHour(RESET_HOUR).withMinute(RESET_MINUTE).withSecond(0).withNano(0);
        if (!next.isAfter(now)) next = next.with(TemporalAdjusters.next(RESET_DAY));
        return next.toEpochSecond();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // API (склад)
    // ─────────────────────────────────────────────────────────────────────────

    public int getServerStock(String itemId) {
        return serverStock.getOrDefault(itemId, 0);
    }

    public void subtractStock(String itemId, int amount) {
        serverStock.put(itemId, Math.max(0, serverStock.getOrDefault(itemId, 0) - amount));
    }

    public void addStock(String itemId, int amount, int maxLimit) {
        serverStock.put(itemId, Math.min(maxLimit, serverStock.getOrDefault(itemId, 0) + amount));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Вклады игроков
    // ─────────────────────────────────────────────────────────────────────────

    public void addContribution(UUID uuid, String itemId, int amount) {
        contributions.computeIfAbsent(uuid, k -> new HashMap<>()).merge(itemId, amount, Integer::sum);
    }

    public int getContribution(UUID uuid, String itemId) {
        Map<String, Integer> m = contributions.get(uuid);
        return m == null ? 0 : m.getOrDefault(itemId, 0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Прочее
    // ─────────────────────────────────────────────────────────────────────────

    public long getNextResetEpoch()  { return nextResetEpoch; }
    public Instant getNextResetTime() { return Instant.ofEpochSecond(nextResetEpoch); }

    /** @deprecated Используй getServerStock() */
    @Deprecated
    public int getServerSold(String itemId) { return getServerStock(itemId); }
}
