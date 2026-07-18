package com.buyerplugin;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Вспомогательный класс для создания «Суперкирки» (SpawnerPickaxe)
 * прямо внутри плагина BaerPlugin.
 *
 * Создаёт предмет NETHERITE_PICKAXE с тем же NamespacedKey и лором,
 * что и оригинальный плагин SuperItemsTwix, чтобы SpawnerPickaxeListener
 * корректно распознавал кирку.
 *
 * ВАЖНО: плагин SuperItemsTwix должен быть установлен на сервере, чтобы
 *        SpawnerPickaxeListener работал. Этот класс лишь создаёт предмет
 *        с правильными данными.
 */
public class SuperPickaxeHelper {

    /**
     * NamespacedKey, используемый плагином SuperItemsTwix.
     * Namespace = "superitemstwix" (id плагина), key = "spawner_pickaxe".
     */
    private static final String PLUGIN_NAMESPACE = "superitemstwix";
    private static final String KEY_NAME         = "spawner_pickaxe";

    /**
     * Создаёт предмет «Суперкирки» совместимый с SpawnerPickaxeListener.
     *
     * @param plugin ссылка на BuyerPlugin (для получения сервера)
     * @return готовый ItemStack с мета-данными
     */
    public static ItemStack createSuperPickaxe(BuyerPlugin plugin) {
        // Создаём NamespacedKey с namespace плагина SuperItemsTwix
        NamespacedKey key = new NamespacedKey(PLUGIN_NAMESPACE, KEY_NAME);

        ItemStack item = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta meta  = item.getItemMeta();

        if (meta != null) {
            // Название — точно как в config.yml SuperItemsTwix
            meta.setItemName(colorize("&#a855f7⛏ &#d4af37Кирка Спавнера &#a855f7⛏"));

            // Лор
            meta.setLore(List.of(
                    colorize("&#c7c7c7Может сломать только спавнер!"),
                    colorize("&#ff5555⚠ Одноразовая — исчезнет после использования")
            ));

            // Помечаем PDC — это главное для isSpawnerPickaxe()
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);

            // Отключаем блеск зачарований
            meta.setEnchantmentGlintOverride(false);

            item.setItemMeta(meta);
        }
        return item;
    }

    /** Конвертация HEX-цветов &#rrggbb и &-кодов в §-коды Minecraft. */
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