package com.baerplugin;

import org.bukkit.scheduler.BukkitRunnable;

/**
 * Задача, которая каждую минуту проверяет,
 * не наступило ли время еженедельного сброса лимитов.
 */
public class WeeklyResetTask extends BukkitRunnable {

    private final ShopData data;

    public WeeklyResetTask(ShopData data) {
        this.data = data;
    }

    @Override
    public void run() {
        if (data.isResetDue()) {
            data.performReset();
        }
    }
}