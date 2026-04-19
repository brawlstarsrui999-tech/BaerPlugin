package com.buyerplugin;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Главный класс плагина BuyerPlugin.
 *
 * Инициализирует все компоненты:
 *  - Vault/Essentials Economy — источник монет
 *  - ShopData  — хранение данных (файл data.yml)
 *  - ShopGUI   — обработчик графического интерфейса
 *  - BuyerCommand — обработчик команды /buyer
 *  - WeeklyResetTask — таймер сброса лимитов (каждые 60 секунд проверки)
 */
public class BuyerPlugin extends JavaPlugin {

    private Economy economy;
    private ShopData shopData;
    private ShopGUI  shopGUI;

    @Override
    public void onEnable() {
        getLogger().info("=========================================");
        getLogger().info("  BuyerPlugin включается...");
        getLogger().info("=========================================");

        // 1. Подключаем Vault Economy
        if (!setupEconomy()) {
            getLogger().severe("Vault/Essentials не найден! Плагин отключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Vault Economy подключён: " + economy.getName());

        // 2. Загружаем данные
        shopData = new ShopData(this);
        shopData.load();

        // 3. Создаём GUI и регистрируем его как слушатель событий
        shopGUI = new ShopGUI(this, shopData);
        getServer().getPluginManager().registerEvents(shopGUI, this);

        // 4. Регистрируем команду /buyer
        BuyerCommand buyerCommand = new BuyerCommand(this, shopData, shopGUI);
        var cmd = getCommand("buyer");
        if (cmd != null) {
            cmd.setExecutor(buyerCommand);
            cmd.setTabCompleter(buyerCommand);
        } else {
            getLogger().severe("Команда 'buyer' не найдена в plugin.yml! Проверьте конфигурацию.");
        }

        // 5. Запускаем таймер проверки еженедельного сброса (каждые 1200 тиков = 60 секунд)
        new WeeklyResetTask(shopData).runTaskTimer(this, 0L, 1200L);

        getLogger().info("BuyerPlugin успешно включён!");
        getLogger().info("Версия: " + getDescription().getVersion());
    }

    @Override
    public void onDisable() {
        // Сохраняем данные при выключении
        if (shopData != null) {
            shopData.save();
        }
        getLogger().info("BuyerPlugin выключен. Данные сохранены.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    public Economy getEconomy() { return economy; }
    public ShopData getShopData() { return shopData; }
    public ShopGUI  getShopGUI()  { return shopGUI; }
}
