package com.baerplugin;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Главный класс плагина BaerPlugin.
 *
 * Инициализирует все компоненты:
 *  - ShopData  — хранение данных (файл data.yml)
 *  - ShopGUI   — обработчик графического интерфейса
 *  - BaerCommand — обработчик команды /baer
 *  - WeeklyResetTask — таймер сброса лимитов (каждые 60 секунд проверка)
 */
public class BaerPlugin extends JavaPlugin {

    private ShopData shopData;
    private ShopGUI  shopGUI;

    @Override
    public void onEnable() {
        getLogger().info("==============================");
        getLogger().info("  BaerPlugin включается...");
        getLogger().info("==============================");

        // 1. Загружаем данные
        shopData = new ShopData(this);
        shopData.load();

        // 2. Создаём GUI и регистрируем его как слушатель событий
        shopGUI = new ShopGUI(this, shopData);
        getServer().getPluginManager().registerEvents(shopGUI, this);

        // 3. Регистрируем команду /baer
        BaerCommand baerCommand = new BaerCommand(this, shopData, shopGUI);
        var cmd = getCommand("baer");
        if (cmd != null) {
            cmd.setExecutor(baerCommand);
            cmd.setTabCompleter(baerCommand);
        } else {
            getLogger().severe("Команда 'baer' не найдена в plugin.yml! Проверьте конфигурацию.");
        }

        // 4. Запускаем таймер проверки сброса (каждые 60 секунд = 1200 тиков)
        new WeeklyResetTask(shopData).runTaskTimer(this, 0L, 1200L);

        getLogger().info("BaerPlugin успешно включён!");
        getLogger().info("Версия: " + getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        // Сохраняем данные при выключении
        if (shopData != null) {
            shopData.save();
        }
        getLogger().info("BaerPlugin выключен. Данные сохранены.");
    }

    public ShopData getShopData() { return shopData; }
    public ShopGUI  getShopGUI()  { return shopGUI;  }
}
