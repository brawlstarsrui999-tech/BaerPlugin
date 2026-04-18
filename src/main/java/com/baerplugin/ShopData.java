package com.baerplugin;

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
 * - сколько каждого ресурса продано на сервер (серверный лимит)
 * - сколько каждый игрок принёс (чтобы считать его долю при покупке)
 * - время следующего сброса
 * - баланс каждого игрока
 *
 * Формат data.yml:
 *   next-reset: <epochSecond>
 *   limits:
 *     <itemId>: <amount>          ← сколько уже продано (серверный итог)
 *   player-balance:
 *     <uuid>: <coins>
 *   player-contributions:         ← сколько каждый игрок принёс баеру
 *     <uuid>:
 *       <itemId>: <amount>
 */
public class ShopData {

    // Часовой пояс Киева
    private static final ZoneId KYIV_ZONE = ZoneId.of("Europe/Kiev");
    // Сброс: каждый понедельник в 13:00 по Киеву
    private static final DayOfWeek RESET_DAY  = DayOfWeek.MONDAY;
    private static final int       RESET_HOUR  = 13;
    private static final int       RESET_MINUTE = 0;

    private final BaerPlugin plugin;
    private final Logger log;

    private File dataFile;
    private FileConfiguration dataCfg;

    // itemId → сколько продано в этот лимитный период (серверный счётчик)
    private final Map<String, Integer> serverSold = new HashMap<>();

    // uuid → баланс (монеты)
    private final Map<UUID, Integer> balances = new HashMap<>();

    // uuid → (itemId → количество принесено)
    private final Map<UUID, Map<String, Integer>> contributions = new HashMap<>();

    // Следующий момент сброса (UTC epochSecond)
    private long nextResetEpoch;

    public ShopData(BaerPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    // ------------------------------------------------------------------ load
    public void load() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try { dataFile.createNewFile(); } catch (IOException e) { log.severe("Не удалось создать data.yml: " + e.getMessage()); }
        }
        dataCfg = YamlConfiguration.loadConfiguration(dataFile);

        // Загружаем время следующего сброса
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

        // Балансы
        if (dataCfg.isConfigurationSection("player-balance")) {
            for (String uuidStr : dataCfg.getConfigurationSection("player-balance").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    balances.put(uuid, dataCfg.getInt("player-balance." + uuidStr, 0));
                } catch (IllegalArgumentException ignored) {}
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

        log.info("ShopData загружен. Следующий сброс: " + Instant.ofEpochSecond(nextResetEpoch)
                .atZone(KYIV_ZONE).toLocalDateTime());
    }

    // ------------------------------------------------------------------ save
    public void save() {
        dataCfg.set("next-reset", nextResetEpoch);

        // Лимиты
        for (Map.Entry<String, Integer> e : serverSold.entrySet()) {
            dataCfg.set("limits." + e.getKey(), e.getValue());
        }

        // Балансы
        for (Map.Entry<UUID, Integer> e : balances.entrySet()) {
            dataCfg.set("player-balance." + e.getKey().toString(), e.getValue());
        }

        // Вклады
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

    // ------------------------------------------------------------------ reset
    /** Вызывается при наступлении времени сброса */
    public void performReset() {
        serverSold.clear();
        contributions.clear();
        nextResetEpoch = computeNextReset();
        save();
        log.info("[BaerPlugin] Еженедельный сброс лимитов выполнен!");
        // Объявить всем на сервере
        plugin.getServer().broadcast(
                net.kyori.adventure.text.Component.text(
                        "§6[Баер] §aЛимиты магазина сброшены! Новая неделя — новые продажи!"));
    }

    /** Нужен ли сброс прямо сейчас? */
    public boolean isResetDue() {
        return Instant.now().getEpochSecond() >= nextResetEpoch;
    }

    public long getNextResetEpoch() { return nextResetEpoch; }

    // ------------------------------------------------------------------ limits
    /** Сколько уже продано серверу по данному itemId */
    public int getServerSold(String itemId) {
        return serverSold.getOrDefault(itemId, 0);
    }

    /** Сколько ещё можно продать */
    public int getRemainingLimit(String itemId) {
        ShopItem item = ShopRegistry.getById(itemId);
        if (item == null) return 0;
        return Math.max(0, item.getWeeklyLimit() - getServerSold(itemId));
    }

    /**
     * Игрок продаёт amount единиц itemId баеру.
     * Возвращает реально принятое количество (может быть меньше из-за лимита).
     * Начисляет монеты игроку.
     */
    public int sellItem(UUID player, String itemId, int amount) {
        ShopItem item = ShopRegistry.getById(itemId);
        if (item == null) return 0;

        int remaining = getRemainingLimit(itemId);
        int accepted  = Math.min(amount, remaining);
        if (accepted <= 0) return 0;

        // Увеличиваем серверный счётчик
        serverSold.merge(itemId, accepted, Integer::sum);

        // Увеличиваем вклад игрока
        contributions.computeIfAbsent(player, k -> new HashMap<>())
                .merge(itemId, accepted, Integer::sum);

        // Начисляем монеты
        int earned = accepted * item.getSellPrice();
        balances.merge(player, earned, Integer::sum);

        save();
        return accepted;
    }

    /**
     * Игрок покупает amount единиц itemId у баера.
     * Возвращает реально купленное количество (ограничено балансом и наличием в пуле).
     * Списывает монеты с игрока.
     */
    public int buyItem(UUID player, String itemId, int amount) {
        ShopItem item = ShopRegistry.getById(itemId);
        if (item == null || !item.canBuy()) return 0;

        // Купить можно столько, сколько в пуле (serverSold)
        int available = getServerSold(itemId);
        int wantToBuy = Math.min(amount, available);
        if (wantToBuy <= 0) return 0;

        int cost = wantToBuy * item.getBuyPrice();
        int balance = getBalance(player);
        if (balance < cost) {
            // Сколько можно купить на имеющиеся деньги
            wantToBuy = balance / item.getBuyPrice();
            if (wantToBuy <= 0) return 0;
            cost = wantToBuy * item.getBuyPrice();
        }

        // Списываем монеты
        balances.merge(player, -cost, Integer::sum);

        // Уменьшаем пул (покупатель забирает товар из пула)
        serverSold.merge(itemId, -wantToBuy, Integer::sum);

        save();
        return wantToBuy;
    }

    // ------------------------------------------------------------------ balance
    public int getBalance(UUID player) {
        return balances.getOrDefault(player, 0);
    }

    public void setBalance(UUID player, int amount) {
        balances.put(player, Math.max(0, amount));
        save();
    }

    public void addBalance(UUID player, int amount) {
        balances.merge(player, amount, Integer::sum);
        save();
    }

    // ------------------------------------------------------------------ helpers
    private long computeNextReset() {
        ZonedDateTime now  = ZonedDateTime.now(KYIV_ZONE);
        ZonedDateTime next = now.with(TemporalAdjusters.nextOrSame(RESET_DAY))
                .withHour(RESET_HOUR)
                .withMinute(RESET_MINUTE)
                .withSecond(0)
                .withNano(0);
        // Если сегодня понедельник и время ещё не наступило — оставляем сегодня,
        // иначе берём следующий понедельник.
        if (!next.isAfter(now)) {
            next = next.with(TemporalAdjusters.next(RESET_DAY));
        }
        return next.toEpochSecond();
    }
}