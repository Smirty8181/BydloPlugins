package ru.bydloland.crafts;

import org.bukkit.Material;

import java.util.List;

public record CraftRecipe(
        String id,
        String displayName,
        String categoryId,
        String[] grid, // 9 ids or null for empty, row-major
        String resultId,
        int resultCount,
        String note // optional note under grid (smelting etc.)
) {
    public static CraftRecipe shaped(String id, String name, String cat, String result, String... cells) {
        String[] g = new String[9];
        for (int i = 0; i < 9; i++) {
            g[i] = (i < cells.length && cells[i] != null && !cells[i].isBlank()) ? cells[i] : null;
        }
        return new CraftRecipe(id, name, cat, g, result, 1, null);
    }

    public static CraftRecipe noteOnly(String id, String name, String cat, String result, String note) {
        return new CraftRecipe(id, name, cat, new String[9], result, 1, note);
    }

    public Material iconMaterial() {
        return switch (categoryId) {
            case "mithril" -> Material.NETHERITE_PICKAXE;
            case "samurai" -> Material.IRON_SWORD;
            case "titan" -> Material.IRON_INGOT;
            case "mage" -> Material.BLAZE_ROD;
            case "bar" -> Material.HONEY_BOTTLE;
            case "misc" -> Material.GOLD_NUGGET;
            default -> Material.BOOK;
        };
    }
}
