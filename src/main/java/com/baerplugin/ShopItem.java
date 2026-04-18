package com.baerplugin;

import org.bukkit.Material;

/**
 * Представляет один товар в магазине Баера.
 *
 * sellPrice  — цена, по которой ИГРОК продаёт товар БАЕРУ (за 1 штуку)
 * buyPrice   — цена, по которой ИГРОК покупает товар У БАЕРА (за 1 штуку)
 *              Если buyPrice == -1, товар нельзя купить у Баера.
 * weeklyLimit — максимальное количество, которое можно продать за неделю (весь сервер)
 * canBuy     — можно ли покупать этот товар у Баера
 */
public class ShopItem {

    private final String id;           // уникальный идентификатор (для сохранения)
    private final String displayName;  // отображаемое имя (на русском)
    private final Material material;   // материал Bukkit
    private final int sellPrice;       // цена продажи игроком → баеру
    private final int buyPrice;        // цена покупки игроком у баера (-1 = нельзя купить)
    private final int weeklyLimit;     // серверный лимит в неделю
    private final String category;     // категория для GUI
    private final boolean canBuy;      // можно ли купить у баера

    public ShopItem(String id, String displayName, Material material,
                    int sellPrice, int buyPrice, int weeklyLimit, String category) {
        this.id = id;
        this.displayName = displayName;
        this.material = material;
        this.sellPrice = sellPrice;
        this.buyPrice = buyPrice;
        this.weeklyLimit = weeklyLimit;
        this.category = category;
        this.canBuy = (buyPrice > 0);
    }

    public String getId()          { return id; }
    public String getDisplayName() { return displayName; }
    public Material getMaterial()  { return material; }
    public int getSellPrice()      { return sellPrice; }
    public int getBuyPrice()       { return buyPrice; }
    public int getWeeklyLimit()    { return weeklyLimit; }
    public String getCategory()    { return category; }
    public boolean canBuy()        { return canBuy; }
}
