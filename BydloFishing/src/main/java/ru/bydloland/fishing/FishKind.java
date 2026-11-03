package ru.bydloland.fishing;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public enum FishKind {
    PLOTVA("Плотва", Rarity.COMMON, false, Material.COD, 26),
    KARAS("Карась", Rarity.COMMON, false, Material.COD, 22),
    CARP("Карп", Rarity.UNCOMMON, false, Material.SALMON, 16),
    SUDAK("Судак", Rarity.UNCOMMON, false, Material.SALMON, 12),
    SHCHUKA("Щука", Rarity.RARE, false, Material.TROPICAL_FISH, 7),
    SOM("Сом", Rarity.RARE, false, Material.PUFFERFISH, 4),
    GOLDEN("Золотая рыбка", Rarity.LEGENDARY, false, Material.GOLDEN_APPLE, 1),

    HELL_PERCH("Адский окунь", Rarity.COMMON, true, Material.MAGMA_CREAM, 22),
    LAVA_EEL("Лавовый угорь", Rarity.UNCOMMON, true, Material.BLAZE_ROD, 14),
    MAGMA_CAT("Магмовый сом", Rarity.RARE, true, Material.GHAST_TEAR, 7),
    TITAN_FISH("Титановая рыба", Rarity.LEGENDARY, true, Material.NETHERITE_SCRAP, 2);

    public enum Rarity {
        COMMON("обычная", NamedTextColor.WHITE, 1.0),
        UNCOMMON("нечастая", NamedTextColor.GREEN, 1.15),
        RARE("редкая", NamedTextColor.AQUA, 1.35),
        LEGENDARY("легенда", NamedTextColor.GOLD, 1.6);

        public final String title;
        public final NamedTextColor color;
        public final double fight;

        Rarity(String title, NamedTextColor color, double fight) {
            this.title = title;
            this.color = color;
            this.fight = fight;
        }
    }

    public final String title;
    public final Rarity rarity;
    public final boolean lava;
    public final Material material;
    public final int weight;

    FishKind(String title, Rarity rarity, boolean lava, Material material, int weight) {
        this.title = title;
        this.rarity = rarity;
        this.lava = lava;
        this.material = material;
        this.weight = weight;
    }

    public static FishKind roll(boolean lava, RodType rod) {
        int bonus = switch (rod) {
            case STEEL -> 2;
            case NETHER -> 3;
            case ABYSS -> 6;
            default -> 0;
        };
        int total = 0;
        for (FishKind k : values()) {
            if (k.lava != lava) continue;
            total += k.effectiveWeight(bonus);
        }
        if (total <= 0) return lava ? HELL_PERCH : PLOTVA;
        int pick = ThreadLocalRandom.current().nextInt(total);
        int acc = 0;
        for (FishKind k : values()) {
            if (k.lava != lava) continue;
            acc += k.effectiveWeight(bonus);
            if (pick < acc) return k;
        }
        return lava ? HELL_PERCH : PLOTVA;
    }

    private int effectiveWeight(int rareBonus) {
        int w = weight;
        if (rarity == Rarity.RARE) w += rareBonus;
        if (rarity == Rarity.LEGENDARY) w += rareBonus * 2;
        return Math.max(1, w);
    }

    public ItemStack toItem() {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(title).color(rarity.color).decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    Component.text("★ " + rarity.title).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                    Component.text(lava ? "Поймана в лаве" : "Поймана в воде")
                            .color(NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
            ));
            item.setItemMeta(meta);
        }
        return item;
    }
}
