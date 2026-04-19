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
import java.time.*;

/**
 * Менеджер данных магазина.
 *
 * Хранит:
 * - серверный счётчик продаж каждого предмета (сбрасывается еженедельно)
 * - вклад каждого игрока (сколько каждого предмета продал)
 * - время следующего сброса
 *
 * ВАЖНО: баланс игроков больше НЕ хранится здесь.
 *        Все операции с деньгами идут через Vault (Economy).
 *
 * Формат data.yml:
 *   next-reset: <epochSecond>
 *   limits:
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

    // itemId → количество проданного (серверный счётчик)
    private final Map<String, Integer> serverSold    = new HashMap<>();

    // uuid → (itemId → количество проданного игроком)
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

        // Лимиты
        if (dataCfg.isConfigurationSection("limits")) {
            for (String key : dataCfg.getConfigurationSection("limits").getKeys(false)) {
                serverSold.put(key, dataCfg.getInt("limits." + key, 0));
            }
        }
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

        log.info("ShopData загружен. Следующий сброс: "
                + Instant.ofEpochSecond(nextResetEpoch).atZone(KYIV_ZONE).toLocalDateTime());
    }

    // ------------------------------------------------------------------------------------- save
    public void save() {
        dataCfg.set("next-reset", nextResetEpoch);

        for (Map.Entry<String, Integer> e : serverSold.entrySet()) {
            dataCfg.set("limits." + e.getKey(), e.getValue());
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

    // ------------------------------------------------------------------------------------- reset
    public void checkAndReset() {
        long now = Instant.now().getEpochSecond();
        if (now >= nextResetEpoch) {
            resetLimits();
            nextResetEpoch = computeNextReset();
            log.info("[BuyerPlugin] Лимиты сброшены. Следующий сброс: "
                    + Instant.ofEpochSecond(nextResetEpoch).atZone(KYIV_ZONE).toLocalDateTime());
            save();
        }
    }

    public void resetLimits() {
        serverSold.clear();
        contributions.clear();
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

    // ------------------------------------------------------------------------------------- API
    public int getServerSold(String itemId) {
        return serverSold.getOrDefault(itemId, 0);
    }

    public void addServerSold(String itemId, int amount) {
        serverSold.merge(itemId, amount, Integer::sum);
    }

    public void addContribution(UUID uuid, String itemId, int amount) {
        contributions.computeIfAbsent(uuid, k -> new HashMap<>())
                .merge(itemId, amount, Integer::sum);
    }

    public int getContribution(UUID uuid, String itemId) {
        Map<String, Integer> m = contributions.get(uuid);
        return m == null ? 0 : m.getOrDefault(itemId, 0);
    }

    public long getNextResetEpoch() { return nextResetEpoch; }

    // Возвращает момент следующего сброса как Instant (используется в ShopGUI)
    public Instant getNextResetTime() {
        return Instant.ofEpochSecond(nextResetEpoch);
    }

}  // ← одна закрывающая скобка класса, самая последняя
