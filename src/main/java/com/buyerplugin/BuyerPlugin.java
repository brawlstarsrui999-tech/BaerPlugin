package com.buyerplugin;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Главный класс плагина BuyerPlugin.
 * Версия 2.0 — добавлены:
 *   - Раздел «Строительство»
 *   - Раздел «Мобы» (яйца призыва + Суперкирка)
 *   - Новые предметы во всех существующих разделах
 *   - Начальный склад для особых предметов (нет. звезда, губка, ключ испытаний и др.)
 */
public class BuyerPlugin extends JavaPlugin {

    private Economy  economy;
    private ShopData shopData;
    private ShopGUI  shopGUI;

    @Override
    public void onEnable() {
        getLogger().info("=========================================");
        getLogger().info("  BuyerPlugin 2.0 включается...");
        getLogger().info("=========================================");

        // 1. Vault Economy
        if (!setupEconomy()) {
            getLogger().severe("Vault/Essentials не найден! Плагин отключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Vault Economy подключён: " + economy.getName());

        // 2. Данные
        shopData = new ShopData(this);
        shopData.load();

        // 3. GUI
        shopGUI = new ShopGUI(this, shopData);
        getServer().getPluginManager().registerEvents(shopGUI, this);

        // 4. Команда
        BuyerCommand buyerCommand = new BuyerCommand(this, shopData, shopGUI);
        var cmd = getCommand("buyer");
        if (cmd != null) { cmd.setExecutor(buyerCommand); cmd.setTabCompleter(buyerCommand); }
        else getLogger().severe("Команда 'buyer' не найдена в plugin.yml!");

        // 5. Таймер сброса (каждые 60 секунд)
        new WeeklyResetTask(shopData).runTaskTimer(this, 20L, 1200L);

        getLogger().info("BuyerPlugin 2.0 успешно включён!");
    }

    @Override
    public void onDisable() {
        if (shopData != null) shopData.save();
        getLogger().info("BuyerPlugin выключен. Данные сохранены.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    public Economy  getEconomy()  { return economy;  }
    public ShopData getShopData() { return shopData; }
    public ShopGUI  getShopGUI()  { return shopGUI;  }
}