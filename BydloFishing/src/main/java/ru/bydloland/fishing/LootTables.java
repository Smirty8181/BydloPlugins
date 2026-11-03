package ru.bydloland.fishing;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.concurrent.ThreadLocalRandom;

public final class LootTables {
    private LootTables() {
    }

    public static void give(BydloFishingPlugin plugin, Player p, FishSession s) {
        ItemStack fish = s.fish != null ? s.fish.toItem() : FishKind.PLOTVA.toItem();
        giveOne(p, fish);
        wearRod(p);

        String rare = s.fish == null ? "" : switch (s.fish.rarity) {
            case LEGENDARY -> "&6✦ ";
            case RARE -> "&b★ ";
            case UNCOMMON -> "&a• ";
            default -> "";
        };
        Msg.send(p, "&aПоймал " + rare + "&f" + (s.fish == null ? "рыбу" : s.fish.title));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.4f, s.fish != null && s.fish.rarity == FishKind.Rarity.LEGENDARY ? 0.8f : 1.55f);
        p.playSound(p.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1f, 1.2f);
        p.sendTitle("§aПОЙМАЛ", "§f" + (s.fish == null ? "рыбу" : s.fish.title), 0, 35, 8);

        double extra = plugin.getConfig().getDouble("loot.extra-chance", 0.22);
        if (s.rod == RodType.ABYSS) extra += 0.12;
        else if (s.rod == RodType.NETHER || s.rod == RodType.STEEL) extra += 0.06;
        if (ThreadLocalRandom.current().nextDouble() < extra) {
            ItemStack drop = roll(plugin, s.lava ? "lava" : "water");
            if (drop != null) {
                giveOne(p, drop);
                Msg.send(p, "&7+ бонус: &f" + pretty(drop));
            }
        }
    }

    private static void giveOne(Player p, ItemStack drop) {
        var leftover = p.getInventory().addItem(drop);
        leftover.values().forEach(it -> p.getWorld().dropItemNaturally(p.getLocation(), it));
    }

    private static String pretty(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return LegacyComponentSerializer.legacySection().serialize(meta.displayName()).replace('§', '&');
        }
        return item.getType().name().toLowerCase().replace('_', ' ');
    }

    private static ItemStack roll(BydloFishingPlugin plugin, String table) {
        var list = plugin.getConfig().getMapList("loot." + table);
        int total = 0;
        int[] weights = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            Object w = list.get(i).get("weight");
            int weight = w instanceof Number n ? n.intValue() : 1;
            weights[i] = Math.max(1, weight);
            total += weights[i];
        }
        if (total <= 0 || list.isEmpty()) return null;
        int pick = ThreadLocalRandom.current().nextInt(total);
        int acc = 0;
        int idx = 0;
        for (int i = 0; i < weights.length; i++) {
            acc += weights[i];
            if (pick < acc) {
                idx = i;
                break;
            }
        }
        var row = list.get(idx);
        String id = String.valueOf(row.get("id"));
        int min = num(row.get("min"), 1);
        int max = Math.max(min, num(row.get("max"), min));
        int amount = ThreadLocalRandom.current().nextInt(min, max + 1);
        ItemStack item = CeItems.of(id);
        if (item == null) return null;
        item.setAmount(amount);
        if (item.getType() == org.bukkit.Material.ENCHANTED_BOOK) {
            enchantBook(item);
        }
        return item;
    }

    private static void enchantBook(ItemStack item) {
        if (!(item.getItemMeta() instanceof EnchantmentStorageMeta meta)) return;
        Enchantment[] all = Enchantment.values();
        Enchantment e = all[ThreadLocalRandom.current().nextInt(all.length)];
        int lvl = Math.max(1, ThreadLocalRandom.current().nextInt(1, e.getMaxLevel() + 1));
        meta.addStoredEnchant(e, lvl, true);
        item.setItemMeta(meta);
    }

    private static int num(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }

    private static void wearRod(Player p) {
        ItemStack rod = p.getInventory().getItemInMainHand();
        if (rod.getType() != org.bukkit.Material.FISHING_ROD) {
            rod = p.getInventory().getItemInOffHand();
        }
        if (rod.getType() != org.bukkit.Material.FISHING_ROD) return;
        ItemMeta meta = rod.getItemMeta();
        if (meta instanceof Damageable d) {
            d.setDamage(d.getDamage() + 1);
            rod.setItemMeta(meta);
            if (d.getDamage() >= rod.getType().getMaxDurability()) {
                rod.setAmount(0);
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            }
        }
    }
}
