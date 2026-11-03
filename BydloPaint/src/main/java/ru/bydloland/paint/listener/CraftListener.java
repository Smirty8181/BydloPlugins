package ru.bydloland.paint.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import ru.bydloland.paint.BydloPaintPlugin;

public final class CraftListener implements Listener {

    private final BydloPaintPlugin plugin;

    public CraftListener(BydloPaintPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // открыть рецепты в книге крафта
        p.discoverRecipe(new org.bukkit.NamespacedKey(plugin, "canvas_framed"));
        p.discoverRecipe(new org.bukkit.NamespacedKey(plugin, "canvas_simple"));
        p.discoverRecipe(new org.bukkit.NamespacedKey(plugin, "canvas_loose"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepare(PrepareItemCraftEvent e) {
        CraftingInventory inv = e.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        if (matrix == null || matrix.length < 9) return;

        if (isFramed(matrix) || isSimple(matrix)) {
            inv.setResult(plugin.canvases().createBlankCanvas());
        }
    }

    private static boolean isWool(ItemStack s) {
        return s != null && !s.getType().isAir() && s.getType().name().endsWith("_WOOL");
    }

    private static boolean isStick(ItemStack s) {
        return s != null && s.getType() == Material.STICK;
    }

    private static boolean empty(ItemStack s) {
        return s == null || s.getType().isAir();
    }

    /** SSS / SWS / SSS */
    private static boolean isFramed(ItemStack[] m) {
        return isStick(m[0]) && isStick(m[1]) && isStick(m[2])
                && isStick(m[3]) && isWool(m[4]) && isStick(m[5])
                && isStick(m[6]) && isStick(m[7]) && isStick(m[8]);
    }

    /** WWW / WSW / WWW */
    private static boolean isSimple(ItemStack[] m) {
        return isWool(m[0]) && isWool(m[1]) && isWool(m[2])
                && isWool(m[3]) && isStick(m[4]) && isWool(m[5])
                && isWool(m[6]) && isWool(m[7]) && isWool(m[8]);
    }
}
