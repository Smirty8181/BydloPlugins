package ru.bydloland.paint.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import ru.bydloland.paint.BydloPaintPlugin;
import ru.bydloland.paint.gui.SizeGui;
import ru.bydloland.paint.service.CanvasService;

public final class SizeGuiListener implements Listener {

    private final BydloPaintPlugin plugin;
    private final CanvasService canvases;

    public SizeGuiListener(BydloPaintPlugin plugin, CanvasService canvases) {
        this.plugin = plugin;
        this.canvases = canvases;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        if (!SizeGui.isSizeGui(e.getView().title())) return;
        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem();
        Integer size = SizeGui.readSize(plugin, clicked);
        if (size == null) return;

        SizeGui.take(player);

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!canvases.isCanvas(hand) || canvases.isSized(hand)) {
            player.closeInventory();
            player.sendMessage(plugin.msg("craft-hint"));
            return;
        }

        int w = Math.max(1, Math.min(canvases.maxSize(), size));
        int h = w;
        canvases.setSize(hand, w, h);
        player.closeInventory();
        player.sendMessage(plugin.msg("sized")
                .replace("%w%", String.valueOf(w))
                .replace("%h%", String.valueOf(h)));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        if (SizeGui.isSizeGui(e.getView().title())) {
            SizeGui.take(player);
        }
    }
}
