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
 * - время последнего сброса
 *
 * Формат data.yml:
 * next-reset: <epoch>
 * last-reset: <epoch>
 * stock:
 *   <itemId>: <amount>
 * player-contributions:
 *   <uuid>:
 *     <itemId>: <amount>
 */
public class ShopData {

    private static final ZoneId KYIV_ZONE = ZoneId.of("Europe/Kiev");
    private static final DayOfWeek RESET_DAY = DayOfWeek.MONDAY;
    private static final int RESET_HOUR = 13;
    private static final int RESET_MINUTE = 0;

    private final BuyerPlugin plugin;
    private final Logger log;

    private File dataFile;
    private FileConfiguration dataCfg;

    // itemId → сколько ресурса сейчас лежит в байере (0..serverLimit)
    private final Map<String, Integer> serverStock = new HashMap<>();

    // uuid → (itemId → количество проданного игроком суммарно)
    private final Map<UUID, Map<String, Integer>> contributions = new HashMap<>();

    // Следующий момент сброса (UTC epochSecond)
    private long nextResetEpoch;

    // ✅ ИСПРАВЛЕНИЕ #2: Последний момент сброса (UTC epochSecond)
    private long lastResetEpoch;

    public ShopData(BuyerPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    // ------------------------------------------------------------------------------------- load
    public void load() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                log.severe("Не удалось создать data.yml: " + e.getMessage());
            }
        }
        dataCfg = YamlConfiguration.loadConfiguration(dataFile);

        // Загружаем время сброса
        nextResetEpoch = dataCfg.getLong("next-reset", 0L);
        if (nextResetEpoch == 0L) {
            nextResetEpoch = computeNextReset();
        }

        // ✅ ИСПРАВЛЕНИЕ #2: Загружаем время последнего сброса
        lastResetEpoch = dataCfg.getLong("last-reset", 0L);

        // Загружаем склад байера
        // Поддерживаем старый ключ "limits" для обратной совместимости
        String stockSection = null;
        if (dataCfg.isConfigurationSection("stock")) {
            stockSection = "stock";
        } else if (dataCfg.isConfigurationSection("limits")) {
            stockSection = "limits";
        }
        if (stockSection != null) {
            for (String key : dataCfg.getConfigurationSection(stockSection).getKeys(false)) {
                serverStock.put(key, dataCfg.getInt(stockSection + "." + key, 0));
            }
            if ("limits".equals(stockSection)) {
                dataCfg.set("limits", null);
                log.info("Мигрированы данные из 'limits' -> 'stock'");
            }
        }

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

        log.info("ShopData загружен. Следующий сброс: "
                + Instant.ofEpochSecond(nextResetEpoch).atZone(KYIV_ZONE).toLocalDateTime()
                + (lastResetEpoch > 0 ? " | Последний сброс: "
                + Instant.ofEpochSecond(lastResetEpoch).atZone(KYIV_ZONE).toLocalDateTime() : " | Сбросов ещё не было"));
    }

    // ------------------------------------------------------------------------------------- save
    public void save() {
        dataCfg.set("next-reset", nextResetEpoch);

        // ✅ ИСПРАВЛЕНИЕ #2: Сохраняем время последнего сброса
        dataCfg.set("last-reset", lastResetEpoch);

        dataCfg.set("limits", null);

        // ✅ Очищаем секцию stock перед записью, чтобы убрать устаревшие записи
        dataCfg.set("stock", null);
        for (Map.Entry<String, Integer> e : serverStock.entrySet()) {
            dataCfg.set("stock." + e.getKey(), e.getValue());
        }

        // Сохраняем вклады
        dataCfg.set("player-contributions", null);
        for (Map.Entry<UUID, Map<String, Integer>> outer : contributions.entrySet()) {
            String uuidStr = outer.getKey().toString();
            for (Map.Entry<String, Integer> inner : outer.getValue().entrySet()) {
                dataCfg.set("player-contributions." + uuidStr + "." + inner.getKey(), inner.getValue());
            }
        }

        try {
            dataCfg.save(dataFile);
        } catch (IOException e) {
            log.severe("Не удалось сохранить data.yml: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------------------------- reset
    public void checkAndReset() {
        long now = Instant.now().getEpochSecond();
        if (now >= nextResetEpoch) {
            performReset();
        }
    }

    /**
     * Выполняет сброс склада и обновляет таймеры.
     * ✅ ИСПРАВЛЕНИЕ #1 и #2: Вынесено в отдельный метод для переиспользования.
     */
    public void performReset() {
        long now = Instant.now().getEpochSecond();

        // ✅ ИСПРАВЛЕНИЕ #2: Записываем реальное время последнего сброса
        lastResetEpoch = now;

        resetLimits();

        // ✅ ИСПРАВЛЕНИЕ #1: Надёжное вычисление следующего сброса
        nextResetEpoch = computeNextReset();

        // ✅ ИСПРАВЛЕНИЕ #1: Защита — если computeNextReset вернул время в прошлом,
        // принудительно прибавляем 7 дней
        if (nextResetEpoch <= now) {
            nextResetEpoch = now + 7 * 24 * 60 * 60;
            log.warning("[BuyerPlugin] computeNextReset вернул время в прошлом! Принудительно +7 дней.");
        }

        log.info("[BuyerPlugin] Склад сброшен. Следующий сброс: "
                + Instant.ofEpochSecond(nextResetEpoch).atZone(KYIV_ZONE).toLocalDateTime());
        save();
    }

    /**
     * Сброс: возвращаем склад каждого предмета на лимит (стартовый баланс).
     */
    public void resetLimits() {
        serverStock.clear();
        contributions.clear();
        for (ShopItem item : ShopRegistry.getAllItems()) {
            serverStock.put(item.getId(), item.getServerLimit());
        }
    }

    /**
     * ✅ ИСПРАВЛЕНИЕ #1: Переписанный метод вычисления следующего сброса.
     *
     * Используется next() вместо nextOrSame() для избежания пограничных случаев,
     * когда nextOrSame может вернуть сегодняшний день и затем withHour/withMinute
     * создаёт время в прошлом.
     *
     * Алгоритм:
     * 1. Если сегодня день сброса И время сброса ещё не наступило → вернуть сегодня
     * 2. Иначе → вернуть следующий день сброса (next)
     */
    private long computeNextReset() {
        ZonedDateTime now = ZonedDateTime.now(KYIV_ZONE);

        // Вычисляем время сброса СЕГОДНЯ
        ZonedDateTime todayReset = now
                .withHour(RESET_HOUR)
                .withMinute(RESET_MINUTE)
                .withSecond(0)
                .withNano(0);

        ZonedDateTime nextReset;

        if (now.getDayOfWeek() == RESET_DAY && now.isBefore(todayReset)) {
            // Сегодня день сброса, и время ещё не наступило
            nextReset = todayReset;
        } else {
            // Используем next() — ВСЕГДА следующий понедельник (не сегодня)
            nextReset = now.with(TemporalAdjusters.next(RESET_DAY))
                    .withHour(RESET_HOUR)
                    .withMinute(RESET_MINUTE)
                    .withSecond(0)
                    .withNano(0);
        }

        return nextReset.toEpochSecond();
    }

    // ------------------------------------------------------------------------------------- API (склад)

    public int getServerStock(String itemId) {
        return serverStock.getOrDefault(itemId, 0);
    }

    public void subtractStock(String itemId, int amount) {
        int current = serverStock.getOrDefault(itemId, 0);
        serverStock.put(itemId, Math.max(0, current - amount));
    }

    public void addStock(String itemId, int amount, int maxLimit) {
        int current = serverStock.getOrDefault(itemId, 0);
        serverStock.put(itemId, Math.min(maxLimit, current + amount));
    }

    // ------------------------------------------------------------------------------------- Вклады игроков

    public void addContribution(UUID uuid, String itemId, int amount) {
        contributions.computeIfAbsent(uuid, k -> new HashMap<>())
                .merge(itemId, amount, Integer::sum);
    }

    public int getContribution(UUID uuid, String itemId) {
        Map<String, Integer> m = contributions.get(uuid);
        return m == null ? 0 : m.getOrDefault(itemId, 0);
    }

    // ------------------------------------------------------------------------------------- Прочее

    public long getNextResetEpoch() {
        return nextResetEpoch;
    }

    public Instant getNextResetTime() {
        return Instant.ofEpochSecond(nextResetEpoch);
    }

    // ✅ ИСПРАВЛЕНИЕ #2: Геттер для времени последнего сброса
    public long getLastResetEpoch() {
        return lastResetEpoch;
    }

    public Instant getLastResetTime() {
        return lastResetEpoch > 0 ? Instant.ofEpochSecond(lastResetEpoch) : null;
    }

    // ---- Устаревшие методы — оставлены для совместимости с BuyerCommand ----

    /** @deprecated Используй getServerStock() */
    @Deprecated
    public int getServerSold(String itemId) {
        return getServerStock(itemId);
    }

    /** @deprecated Используй addStock() / subtractStock() */
    @Deprecated
    public void addServerSold(String itemId, int amount) {
        // no-op: оставлен для совместимости, логика теперь в ShopGUI
    }
}