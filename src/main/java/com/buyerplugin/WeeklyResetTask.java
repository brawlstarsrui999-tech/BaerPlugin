package com.buyerplugin;

import org.bukkit.scheduler.BukkitRunnable;

/**
 * Периодическая задача проверки необходимости еженедельного сброса склада байера.
 * Запускается каждые 1200 тиков (60 секунд).
 */
public class WeeklyResetTask extends BukkitRunnable {

    private final ShopData shopData;

    public WeeklyResetTask(ShopData shopData) {
        this.shopData = shopData;
    }

    @Override
    public void run() {
        shopData.checkAndReset();
    }
}

