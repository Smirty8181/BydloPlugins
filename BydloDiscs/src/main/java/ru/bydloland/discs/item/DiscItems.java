package ru.bydloland.discs.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import ru.bydloland.discs.BydloDiscsPlugin;

import java.util.List;
import java.util.UUID;

public final class DiscItems {
    private final NamespacedKey ownerKey;
    private final NamespacedKey titleKey;
    private final NamespacedKey customKey;

    public DiscItems(BydloDiscsPlugin plugin) {
        this.ownerKey = new NamespacedKey(plugin, "owner");
        this.titleKey = new NamespacedKey(plugin, "title");
        this.customKey = new NamespacedKey(plugin, "custom");
    }

    public ItemStack create(UUID owner, String ownerName, String title) {
        ItemStack disc = new ItemStack(Material.MUSIC_DISC_CAT);
        ItemMeta meta = disc.getItemMeta();
        meta.displayName(Component.text("Пластинка — " + title)
                .color(NamedTextColor.LIGHT_PURPLE)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Владелец: " + ownerName).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Вставь в проигрыватель").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Смена трека: /пластина <ссылка> (1ч)").color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        var pdc = meta.getPersistentDataContainer();
        pdc.set(customKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(ownerKey, PersistentDataType.STRING, owner.toString());
        pdc.set(titleKey, PersistentDataType.STRING, title);
        // Без ванильной песни: иначе проигрыватель параллельно крутит Cat.
        meta.setJukeboxPlayable(null);
        disc.setItemMeta(meta);
        return disc;
    }

    /** Копия для слота проигрывателя — без ванильного jukebox_playable. */
    public ItemStack asSilentRecord(ItemStack src) {
        ItemStack disc = src.clone();
        disc.setAmount(1);
        ItemMeta meta = disc.getItemMeta();
        if (meta != null) {
            meta.setJukeboxPlayable(null);
            disc.setItemMeta(meta);
        }
        return disc;
    }

    public boolean isCustom(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(customKey, PersistentDataType.BYTE);
    }

    public UUID owner(ItemStack item) {
        if (!isCustom(item)) return null;
        String s = item.getItemMeta().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        try {
            return s == null ? null : UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String title(ItemStack item) {
        if (!isCustom(item)) return null;
        return item.getItemMeta().getPersistentDataContainer().get(titleKey, PersistentDataType.STRING);
    }
}
