package ru.bydloland.paint.recipe;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import ru.bydloland.paint.BydloPaintPlugin;

public final class CanvasRecipes {
    private CanvasRecipes() {}

    public static void register(BydloPaintPlugin plugin) {
        ItemStack result = plugin.canvases().createBlankCanvas();
        RecipeChoice.MaterialChoice wool = new RecipeChoice.MaterialChoice(Tag.WOOL);

        NamespacedKey framedKey = new NamespacedKey(plugin, "canvas_framed");
        NamespacedKey simpleKey = new NamespacedKey(plugin, "canvas_simple");
        NamespacedKey looseKey = new NamespacedKey(plugin, "canvas_loose");
        Bukkit.removeRecipe(framedKey);
        Bukkit.removeRecipe(simpleKey);
        Bukkit.removeRecipe(looseKey);

        // Рамка из палок, шерсть в центре
        ShapedRecipe framed = new ShapedRecipe(framedKey, result.clone());
        framed.shape("SSS", "SWS", "SSS");
        framed.setIngredient('S', Material.STICK);
        framed.setIngredient('W', wool);
        framed.setGroup("bydlo_canvas");
        Bukkit.addRecipe(framed);

        // 8 шерсти вокруг палки
        ShapedRecipe simple = new ShapedRecipe(simpleKey, result.clone());
        simple.shape("WWW", "WSW", "WWW");
        simple.setIngredient('W', wool);
        simple.setIngredient('S', Material.STICK);
        simple.setGroup("bydlo_canvas");
        Bukkit.addRecipe(simple);

        // Запасной: 4 шерсти + 4 палки (shapeless) — проще найти
        ShapelessRecipe loose = new ShapelessRecipe(looseKey, result.clone());
        loose.addIngredient(4, Material.STICK);
        loose.addIngredient(wool);
        loose.addIngredient(wool);
        loose.addIngredient(wool);
        loose.addIngredient(wool);
        loose.setGroup("bydlo_canvas");
        Bukkit.addRecipe(loose);

        plugin.getLogger().info("Registered canvas recipes (framed / simple / loose)");
    }
}
