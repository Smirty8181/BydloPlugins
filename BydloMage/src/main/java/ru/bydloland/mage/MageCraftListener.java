package ru.bydloland.mage;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/** Крафт маг-снаряжения только для магов. */
public final class MageCraftListener implements Listener {

    private static final Set<String> LOCKED = Set.of(
            "bydlo:staff_dig", "bydlo:staff_lumber",
            "bydlo:mage_hood", "bydlo:mage_robe", "bydlo:mage_pants", "bydlo:mage_boots",
            "bydlo:magic_tower", "bydlo:etherium_crystal",
            "bydlo:familiar_charm"
    );

    private final BydloMagePlugin plugin;

    public MageCraftListener(BydloMagePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepare(PrepareItemCraftEvent event) {
        CraftingInventory inv = event.getInventory();
        ItemStack result = inv.getResult();
        if (result == null || result.getType().isAir()) return;
        String id = CeItems.idOf(result);
        if (id == null || !LOCKED.contains(id)) return;

        Player player = null;
        if (event.getView().getPlayer() instanceof Player p) player = p;
        if (player == null) {
            inv.setResult(null);
            return;
        }
        if (!ClassesBridge.canUseMageGear(player)) {
            inv.setResult(null);
        }
    }
}
