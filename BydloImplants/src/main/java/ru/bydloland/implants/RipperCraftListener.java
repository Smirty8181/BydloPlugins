package ru.bydloland.implants;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;

/** Стол рипера крафтят только Киберпанки. */
public final class RipperCraftListener implements Listener {

    private final BydloImplantsPlugin plugin;

    public RipperCraftListener(BydloImplantsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareItemCraftEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (!isTable(result)) return;
        if (!(event.getView().getPlayer() instanceof Player player)) {
            event.getInventory().setResult(null);
            return;
        }
        if (!plugin.service().canUseImplants(player)) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!isTable(event.getCurrentItem())) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (plugin.service().canUseImplants(player)) return;
        event.setCancelled(true);
        player.sendMessage("§cСтол рипера крафтят только Киберпанки.");
    }

    private static boolean isTable(ItemStack stack) {
        return "bydlo:ripper_table".equals(ImplantService.getCeItemId(stack));
    }
}
