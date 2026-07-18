package com.buyerplugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.List;

public class SuperPickaxeHelper {

    private static final String SUPER_PLUGIN_NAME = "SpawnerPickaxe";
    private static final String PLUGIN_NAMESPACE = "spawnerpickaxe";
    private static final String KEY_NAME = "spawner_pickaxe";

    public static ItemStack createSuperPickaxe(BuyerPlugin plugin) {

        // ── Способ 1: делегируем самому SpawnerPickaxe ──
        Plugin rawPlugin = Bukkit.getPluginManager().getPlugin(SUPER_PLUGIN_NAME);

        if (rawPlugin instanceof ru.spawnerpickaxe.SpawnerPickaxe sp) {
            // ✅ Используем существующий метод из плагина
            return sp.createSpawnerPickaxe(); // КЛЮЧ ГАРАНТИРОВАННО ВЕРНЫЙ!
        }

        // ── Способ 2 (fallback): создаём вручную ──
        plugin.getLogger().warning(
                "[BuyerPlugin] Плагин " + SUPER_PLUGIN_NAME +
                        " не найден! Создаём кирку в fallback-режиме."
        );

        NamespacedKey key = NamespacedKey.fromString(PLUGIN_NAMESPACE + ":" + KEY_NAME);
        if (key == null) {
            plugin.getLogger().severe("[BuyerPlugin] Не удалось создать NamespacedKey!");
            return new ItemStack(Material.NETHERITE_PICKAXE);
        }

        ItemStack item = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setItemName(colorize("&#a855f7⛏ &#d4af37Кирка Спавнера &#a855f7⛏"));
            meta.setLore(List.of(
                    colorize("&#c7c7c7Может сломать только спавнер!"),
                    colorize("&#ff5555⚠ Одноразовая — исчезнет после использования"),
                    " ",
                    colorize("&#c7c7c7Автор: Ruin4ik"),
                    colorize("&#ff5555✦ Специально для TwixRPG")
            ));

            meta.getPersistentDataContainer().set(
                    key, PersistentDataType.BYTE, (byte) 1
            );
            meta.setEnchantmentGlintOverride(false);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String colorize(String text) {
        if (text == null) return "";
        text = text.replaceAll("&#([0-9a-fA-F]{6})", "§x$1");
        StringBuilder sb = new StringBuilder();
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            if (chars[i] == '§' && i + 1 < chars.length && chars[i + 1] == 'x') {
                sb.append("§x");
                i += 2;
                for (int j = 0; j < 6 && i < chars.length; j++) {
                    sb.append('§').append(chars[i]);
                    i++;
                }
                i--;
            } else sb.append(chars[i]);
        }
        return sb.toString().replace("&", "§");
    }
}