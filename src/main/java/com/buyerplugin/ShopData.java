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
 *     <itemId>: <amount>
 *   player-contributions:
 *     <uuid>:
 *       <itemId>: <amount>
 */
public class ShopData {

    private static final ZoneId    KYIV_ZONE    = ZoneId.of("Europe/Kiev");
    private static final DayOfWeek RESET_DAY    = DayOfWeek.MONDAY;
    private static final int       RESET_HOUR   = 13;
    private static final int       RESET_MINUTE = 0;

    private final BuyerPlugin plugin;
    private final Logger      log;

    private File              dataFile;
    private FileConfiguration dataCfg;

    // itemId → сколько ресурса сейчас лежит в байере (0..serverLimit)
    private final Map<String, Integer> serverStock = new HashMap<>();

    // uuid → (itemId → количество проданного игроком суммарно)
    private final Map<UUID, Map<String, Integer>> contributions = new HashMap<>();

    // Следующий момент сброса (UTC epochSecond)
    private long nextResetEpoch;

    public ShopData(BuyerPlugin plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
    }

    // ------------------------------------------------------------------------------------- load
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
        if (nextResetEpoch == 0L) {
            nextResetEpoch = computeNextReset();
        }

        // Загружаем склад байера
        // Поддерживаем старый ключ "limits" для обратной совместимости
        String stockSection = dataCfg.isConfigurationSection("stock") ? "stock" : "limits";
        if (dataCfg.isConfigurationSection(stockSection)) {
            for (String key : dataCfg.getConfigurationSection(stockSection).getKeys(false)) {
                serverStock.put(key, dataCfg.getInt(stockSection + "." + key, 0));
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

        log.info("ShopData загружен. Следующий сброс: " +
                Instant.ofEpochSecond(nextResetEpoch).atZone(KYIV_ZONE).toLocalDateTime());
    }

    // ------------------------------------------------------------------------------------- save
    public void save() {
        dataCfg.set("next-reset", nextResetEpoch);

        // Сохраняем склад
        for (Map.Entry<String, Integer> e : serverStock.entrySet()) {
            dataCfg.set("stock." + e.getKey(), e.getValue());
        }

        // Сохраняем вклады
        for (Map.Entry<UUID, Map<String, Integer>> outer : contributions.entrySet()) {
            String uuidStr = outer.getKey().toString();
            for (Map.Entry<String, Integer> inner : outer.getValue().entrySet()) {
                dataCfg.set("player-contributions." + uuidStr + "." + inner.getKey(), inner.getValue());
            }
        }

        try { dataCfg.save(dataFile); }
        catch (IOException e) { log.severe("Не удалось сохранить data.yml: " + e.getMessage()); }
    }

    // ------------------------------------------------------------------------------------- reset
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
     * Сброс: возвращаем склад каждого предмета на половину от лимита (стартовый баланс).
     * Можно изменить на любое другое значение (например, 0 или limit).
     */
    public void resetLimits() {
        serverStock.clear();
        contributions.clear();
        // При сбросе выставляем стартовый запас = половина лимита для каждого предмета
        for (ShopItem item : ShopRegistry.getAllItems()) {
            serverStock.put(item.getId(), item.getServerLimit() / 2);
        }
    }

    private long computeNextReset() {
        ZonedDateTime now  = ZonedDateTime.now(KYIV_ZONE);
        ZonedDateTime next = now.with(TemporalAdjusters.nextOrSame(RESET_DAY))
                .withHour(RESET_HOUR).withMinute(RESET_MINUTE).withSecond(0).withNano(0);
        if (!next.isAfter(now)) {
            next = next.with(TemporalAdjusters.next(RESET_DAY));
        }
        return next.toEpochSecond();
    }

    // ------------------------------------------------------------------------------------- API (склад)

    /**
     * Сколько единиц ресурса сейчас в байере (доступно для покупки).
     */
    public int getServerStock(String itemId) {
        return serverStock.getOrDefault(itemId, 0);
    }

    /**
     * Уменьшить склад при ПОКУПКЕ игрока из байера.
     * @param itemId  ID предмета
     * @param amount  количество купленного
     */
    public void subtractStock(String itemId, int amount) {
        int current = serverStock.getOrDefault(itemId, 0);
        serverStock.put(itemId, Math.max(0, current - amount));
    }

    /**
     * Увеличить склад при ПРОДАЖЕ игрока в байер.
     * @param itemId   ID предмета
     * @param amount   количество проданного
     * @param maxLimit максимальный лимит (serverLimit предмета)
     */
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

    public long getNextResetEpoch() { return nextResetEpoch; }

    public Instant getNextResetTime() { return Instant.ofEpochSecond(nextResetEpoch); }

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
