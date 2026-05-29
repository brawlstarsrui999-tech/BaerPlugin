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

public class ShopData {

    private static final ZoneId   KYIV_ZONE    = ZoneId.of("Europe/Kiev");
    private static final DayOfWeek RESET_DAY   = DayOfWeek.MONDAY;
    private static final int       RESET_HOUR  = 13;
    private static final int       RESET_MINUTE = 0;

    private final BuyerPlugin plugin;
    private final Logger      log;
    private File              dataFile;
    private FileConfiguration dataCfg;

    private final Map<String, Integer>              serverStock   = new HashMap<>();
    private final Map<UUID, Map<String, Integer>>   contributions = new HashMap<>();

    private long nextResetEpoch;
    private long lastResetEpoch;

    public ShopData(BuyerPlugin plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
    }

    // ── load ─────────────────────────────────────────────────────────────────
    public void load() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try { dataFile.createNewFile(); }
            catch (IOException e) { log.severe("Не удалось создать data.yml: " + e.getMessage()); }
        }
        dataCfg = YamlConfiguration.loadConfiguration(dataFile);

        nextResetEpoch = dataCfg.getLong("next-reset", 0L);
        if (nextResetEpoch == 0L) nextResetEpoch = computeNextReset();

        lastResetEpoch = dataCfg.getLong("last-reset", 0L);

        String stockSection = null;
        if (dataCfg.isConfigurationSection("stock"))       stockSection = "stock";
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
                Instant.ofEpochSecond(nextResetEpoch).atZone(KYIV_ZONE).toLocalDateTime() +
                (lastResetEpoch > 0
                        ? " | Последний сброс: " + Instant.ofEpochSecond(lastResetEpoch).atZone(KYIV_ZONE).toLocalDateTime()
                        : " | Сбросов ещё не было"));
    }

    // ── save ─────────────────────────────────────────────────────────────────
    public void save() {
        dataCfg.set("next-reset", nextResetEpoch);
        dataCfg.set("last-reset", lastResetEpoch);
        dataCfg.set("limits", null);
        dataCfg.set("stock", null);
        for (Map.Entry<String, Integer> e : serverStock.entrySet()) {
            dataCfg.set("stock." + e.getKey(), e.getValue());
        }
        dataCfg.set("player-contributions", null);
        for (Map.Entry<UUID, Map<String, Integer>> outer : contributions.entrySet()) {
            String uuidStr = outer.getKey().toString();
            for (Map.Entry<String, Integer> inner : outer.getValue().entrySet()) {
                dataCfg.set("player-contributions." + uuidStr + "." + inner.getKey(), inner.getValue());
            }
        }
        try { dataCfg.save(dataFile); }
        catch (IOException e) { log.severe("Не удалось сохранить data.yml: " + e.getMessage()); }
    }

    // ── reset ─────────────────────────────────────────────────────────────────
    public void checkAndReset() {
        long now = Instant.now().getEpochSecond();
        if (now >= nextResetEpoch) performReset();
    }

    public void performReset() {
        long now = Instant.now().getEpochSecond();
        lastResetEpoch = now;
        resetStock(); // ✅ БАГ #1 ИСПРАВЛЕН: метод переименован и теперь ставит 0
        nextResetEpoch = computeNextReset();
        if (nextResetEpoch <= now) nextResetEpoch = now + 7L * 24 * 3600;
        save();
        log.info("[BuyerPlugin] Склад байера сброшен до 0. Следующий сброс: " +
                Instant.ofEpochSecond(nextResetEpoch).atZone(KYIV_ZONE).toLocalDateTime());
    }

    /**
     * ✅ БАГ #1 ИСПРАВЛЕН.
     *
     * БЫЛО: resetLimits() устанавливал serverLimit / 2 (половина = почти полный для игроков).
     *       Многие трактовали это как "полный", т.к. значение было большим.
     *       Хуже — при некоторых реализациях ставилось serverLimit (100%).
     *
     * СТАЛО: сбрасываем ВСЁ в 0. Байер пуст после каждого еженедельного сброса.
     *        Игроки сами наполняют его продажами.
     */
    private void resetStock() {
        serverStock.clear();
        for (ShopItem item : ShopRegistry.getAllItems()) {
            serverStock.put(item.getId(), 0); // ← БЫЛО: item.getServerLimit() / 2
        }
        log.info("[BuyerPlugin] Все позиции байера сброшены до 0.");
    }

    private long computeNextReset() {
        ZonedDateTime now  = ZonedDateTime.now(KYIV_ZONE);
        ZonedDateTime next = now
                .with(TemporalAdjusters.nextOrSame(RESET_DAY))
                .withHour(RESET_HOUR)
                .withMinute(RESET_MINUTE)
                .withSecond(0)
                .withNano(0);
        if (!next.isAfter(now)) next = next.plusWeeks(1);
        return next.toInstant().getEpochSecond();
    }

    // ── stock API ─────────────────────────────────────────────────────────────
    public int getServerStock(String itemId) {
        return serverStock.getOrDefault(itemId, 0);
    }

    public void addStock(String itemId, int amount, int limit) {
        int cur = serverStock.getOrDefault(itemId, 0);
        serverStock.put(itemId, Math.max(0, Math.min(limit, cur + amount)));
    }

    public void subtractStock(String itemId, int amount) {
        int cur = serverStock.getOrDefault(itemId, 0);
        serverStock.put(itemId, Math.max(0, cur - amount));
    }

    // ── contributions ─────────────────────────────────────────────────────────
    public void addContribution(UUID uuid, String itemId, int amount) {
        contributions.computeIfAbsent(uuid, k -> new HashMap<>())
                .merge(itemId, amount, Integer::sum);
    }

    public int getContribution(UUID uuid, String itemId) {
        Map<String, Integer> m = contributions.get(uuid);
        return m == null ? 0 : m.getOrDefault(itemId, 0);
    }

    // ── getters ───────────────────────────────────────────────────────────────
    public long    getNextResetEpoch() { return nextResetEpoch; }
    public Instant getNextResetTime()  { return Instant.ofEpochSecond(nextResetEpoch); }
    public long    getLastResetEpoch() { return lastResetEpoch; }
    public Instant getLastResetTime()  { return lastResetEpoch > 0 ? Instant.ofEpochSecond(lastResetEpoch) : null; }

    /** @deprecated */
    @Deprecated public int  getServerSold(String itemId)             { return getServerStock(itemId); }
    /** @deprecated */
    @Deprecated public void addServerSold(String itemId, int amount) { /* no-op */ }
}