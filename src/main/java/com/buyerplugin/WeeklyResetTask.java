package com.buyerplugin;

import org.bukkit.scheduler.BukkitRunnable;

/** Периодически проверяет, нужен ли плановый сброс склада. */
public class WeeklyResetTask extends BukkitRunnable {

    private final ShopData data;

    public WeeklyResetTask(ShopData data) {
        this.data = data;
    }

    @Override
    public void run() {
        data.checkAndReset();
    }
}