package com.buyerplugin;

import org.bukkit.Material;

public class ShopItem {

    private final String   id;
    private final String   displayName;
    private final Material material;
    private final double   sellPrice;
    private final double   buyPrice;
    private final int      serverLimit;
    private final String   category;

    public ShopItem(String id,
                    String displayName,
                    Material material,
                    double sellPrice,
                    double buyPrice,
                    int serverLimit,
                    String category) {
        this.id          = id;
        this.displayName = displayName;
        this.material    = material;
        this.sellPrice   = sellPrice;
        this.buyPrice    = buyPrice;
        this.serverLimit = serverLimit;
        this.category    = category;
    }

    public String   getId()          { return id; }
    public String   getDisplayName() { return displayName; }
    public Material getMaterial()    { return material; }
    public double   getSellPrice()   { return sellPrice; }
    public double   getBuyPrice()    { return buyPrice; }
    public int      getServerLimit() { return serverLimit; }
    public String   getCategory()    { return category; }
}
