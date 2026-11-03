package ru.bydloland.mage;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class TowerInteractListener implements Listener {

    private final BydloMagePlugin plugin;

    public TowerInteractListener(BydloMagePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getPlayer().isSneaking()) return;
        String id = CeItems.blockIdAt(event.getClickedBlock());
        if (!"bydlo:magic_tower".equals(id)) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!ClassesBridge.canUseMagicTower(player)) {
            player.sendMessage("§cБашней магии могут пользоваться только маги.");
            return;
        }
        plugin.towerGui().open(player);
    }
}
