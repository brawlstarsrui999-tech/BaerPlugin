package com.buyerplugin;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Главный класс плагина BuyerPlugin.
 *
 * FIX #2 — reloadItems():
 *   После перечитывания items.yml вызывается shopData.applyInitialStockOnReload()
 *   вместо applyInitialStockForNewItems(), чтобы initial-stock из конфига
 *   применялся ко ВСЕМ предметам, а не только к новым.
 */
public class BuyerPlugin extends JavaPlugin {

    private Economy    economy;
    private ItemsConfig itemsConfig;
    private ShopData   shopData;
    private ShopGUI    shopGUI;

    @Override
    public void onEnable() {
        getLogger().info("=========================================");
        getLogger().info(" BuyerPlugin 3.0 включается...");
        getLogger().info(" Предметы загружаются из items.yml");
        getLogger().info("=========================================");

        // 1. Vault Economy
        if (!setupEconomy()) {
            getLogger().severe("Vault/Essentials не найден! Плагин отключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Vault Economy подключён: " + economy.getName());

        // 2. Загрузка items.yml → ShopRegistry
        itemsConfig = new ItemsConfig(this);
        int loaded  = itemsConfig.load();
        if (loaded == 0) {
            getLogger().severe("Не удалось загрузить ни одного предмета из items.yml! Плагин отключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Загружено предметов: " + loaded);

        // 3. Данные (stock, вклады, время сброса)
        shopData = new ShopData(this);
        shopData.load();

        // 4. GUI + события
        shopGUI = new ShopGUI(this, shopData);
        getServer().getPluginManager().registerEvents(shopGUI, this);

        // 5. Команда
        BuyerCommand buyerCommand = new BuyerCommand(this, shopData, shopGUI, itemsConfig);
        var cmd = getCommand("buyer");
        if (cmd != null) {
            cmd.setExecutor(buyerCommand);
            cmd.setTabCompleter(buyerCommand);
        } else {
            getLogger().severe("Команда 'buyer' не найдена в plugin.yml!");
        }

        // 6. Таймер сброса (каждые 60 секунд)
        new WeeklyResetTask(shopData).runTaskTimer(this, 20L, 1200L);

        getLogger().info("BuyerPlugin 3.0 успешно включён! Предметов в магазине: " + ShopRegistry.size());
    }

    @Override
    public void onDisable() {
        if (shopData != null) shopData.save();
        getLogger().info("BuyerPlugin выключен. Данные сохранены.");
    }

    /**
     * Горячая перезагрузка items.yml без рестарта сервера.
     *
     * FIX #2: вместо applyInitialStockForNewItems() теперь вызывается
     * applyInitialStockOnReload() — она применяет initial-stock из items.yml
     * ко ВСЕМ предметам (не только к новым).
     */
    public boolean reloadItems() {
        if (shopData != null) shopData.save();

        ShopRegistry.clear();
        int loaded = itemsConfig.load();
        if (loaded == 0) {
            getLogger().severe("[BuyerPlugin] Reload: не загружено ни одного предмета! items.yml содержит ошибки?");
            return false;
        }

        // ── FIX #2: применяем initial-stock ко ВСЕМ предметам ──
        if (shopData != null) {
            shopData.applyInitialStockOnReload();   // <-- было: applyInitialStockForNewItems()
        }

        getLogger().info("[BuyerPlugin] Reload: загружено " + loaded + " предметов из items.yml.");
        return true;
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp =
                getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    public Economy     getEconomy()    { return economy;    }
    public ShopData    getShopData()   { return shopData;   }
    public ShopGUI     getShopGUI()    { return shopGUI;    }
    public ItemsConfig getItemsConfig(){ return itemsConfig; }
}
