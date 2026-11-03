package ru.bydloland.classes;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Кастомные зачары ExcellentEnchants — только раса маг (флаг mage_gear).
 * Ванильные острота/прочность и т.д. доступны всем.
 */
public final class MageEnchantListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private final Map<UUID, Long> lastDeny = new ConcurrentHashMap<>();

    static boolean isCustom(Enchantment enchantment) {
        if (enchantment == null) return false;
        NamespacedKey key = enchantment.getKey();
        return "excellentenchants".equalsIgnoreCase(key.getNamespace());
    }

    static boolean hasCustom(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        for (Enchantment e : stack.getEnchantments().keySet()) {
            if (isCustom(e)) return true;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storage) {
            for (Enchantment e : storage.getStoredEnchants().keySet()) {
                if (isCustom(e)) return true;
            }
        }
        return false;
    }

    static List<Enchantment> customOn(ItemStack stack) {
        List<Enchantment> out = new ArrayList<>();
        if (stack == null || stack.getType().isAir()) return out;
        for (Enchantment e : stack.getEnchantments().keySet()) {
            if (isCustom(e)) out.add(e);
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storage) {
            for (Enchantment e : storage.getStoredEnchants().keySet()) {
                if (isCustom(e) && !out.contains(e)) out.add(e);
            }
        }
        return out;
    }

    static String ruName(Enchantment enchantment) {
        try {
            String name = PLAIN.serialize(enchantment.displayName(1)).replace('\n', ' ').trim();
            if (name.endsWith(" I")) name = name.substring(0, name.length() - 2).trim();
            if (!name.isEmpty()) return name;
        } catch (Throwable ignored) {
        }
        String key = enchantment.getKey().getKey().replace('_', ' ');
        return key.isEmpty() ? "улучшенное зачарование" : key;
    }

    static ItemStack stripCustom(ItemStack stack) {
        if (stack == null || !hasCustom(stack)) return stack;
        ItemStack out = stack.clone();
        Map<Enchantment, Integer> keep = new LinkedHashMap<>();
        for (var e : out.getEnchantments().entrySet()) {
            if (!isCustom(e.getKey())) keep.put(e.getKey(), e.getValue());
            out.removeEnchantment(e.getKey());
        }
        keep.forEach(out::addUnsafeEnchantment);
        ItemMeta meta = out.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storage) {
            for (Enchantment e : storage.getStoredEnchants().keySet().toArray(Enchantment[]::new)) {
                if (isCustom(e)) storage.removeStoredEnchant(e);
            }
            out.setItemMeta(storage);
        }
        return out;
    }

    private boolean allowed(Player player) {
        return BydloClassesAPI.canUseCustomEnchants(player);
    }

    private void deny(Player player, Collection<Enchantment> blocked) {
        if (blocked == null || blocked.isEmpty()) {
            player.sendMessage("§cВы не можете зачаровать на улучшенные зачарования, потому что вы не маг.");
        } else {
            StringBuilder names = new StringBuilder();
            for (Enchantment e : blocked) {
                if (names.length() > 0) names.append("§7, §f");
                names.append(ruName(e));
            }
            player.sendMessage("§cВы не можете зачаровать на §f" + names + "§c, потому что вы не маг.");
        }
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.45f, 0.95f);
    }

    private void denyThrottled(Player player, Collection<Enchantment> blocked) {
        long now = System.currentTimeMillis();
        Long prev = lastDeny.get(player.getUniqueId());
        if (prev != null && now - prev < 1600) return;
        lastDeny.put(player.getUniqueId(), now);
        deny(player, blocked);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEnchantTable(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        if (allowed(player)) return;
        List<Enchantment> blocked = new ArrayList<>();
        event.getEnchantsToAdd().keySet().removeIf(e -> {
            if (!isCustom(e)) return false;
            blocked.add(e);
            return true;
        });
        if (!blocked.isEmpty()) {
            deny(player, blocked);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnvil(PrepareAnvilEvent event) {
        if (!(event.getView().getPlayer() instanceof Player player)) return;
        if (allowed(player)) return;
        ItemStack result = event.getResult();
        List<Enchantment> blocked = customOn(result);
        if (blocked.isEmpty()) return;
        denyThrottled(player, blocked);
        ItemStack stripped = stripCustom(result);
        ItemStack left = event.getInventory().getFirstItem();
        if (left != null && sameEnchantIds(stripped, left)) {
            event.setResult(null);
        } else {
            event.setResult(stripped);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (allowed(player)) return;
        if (event.getClickedInventory() == null) return;

        if (event.getInventory() instanceof AnvilInventory && event.getRawSlot() == 2) {
            ItemStack result = event.getCurrentItem();
            List<Enchantment> blocked = customOn(result);
            if (!blocked.isEmpty()) {
                event.setCancelled(true);
                deny(player, blocked);
            }
            return;
        }

        if (event.getInventory() instanceof MerchantInventory && event.getRawSlot() == 2) {
            ItemStack result = event.getCurrentItem();
            List<Enchantment> blocked = customOn(result);
            if (!blocked.isEmpty()) {
                event.setCancelled(true);
                deny(player, blocked);
            }
        }
    }

    private static boolean sameEnchantIds(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        return a.getEnchantments().keySet().equals(b.getEnchantments().keySet());
    }
}
