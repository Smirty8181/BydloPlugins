package ru.bydloland.paint.listener;

import org.bukkit.GameMode;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import ru.bydloland.paint.BydloPaintPlugin;
import ru.bydloland.paint.paint.CanvasPlacer;
import ru.bydloland.paint.paint.FramePainter;
import ru.bydloland.paint.paint.MapColors;
import ru.bydloland.paint.paint.TileStore;
import ru.bydloland.paint.service.CanvasService;

public final class PaintListener implements Listener {

    private final BydloPaintPlugin plugin;
    private final CanvasService canvases;
    private final CanvasPlacer placer;
    private final FramePainter painter;

    public PaintListener(BydloPaintPlugin plugin, CanvasService canvases) {
        this.plugin = plugin;
        this.canvases = canvases;
        TileStore store = plugin.tileStore();
        this.placer = new CanvasPlacer(plugin, canvases, store);
        this.painter = new FramePainter(plugin, canvases, store);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player player = e.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        // Paint with dye while looking at canvas frames
        if (MapColors.isDye(hand.getType())
                && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            int brush = player.isSneaking() ? 1 : Math.max(1, plugin.getConfig().getInt("brush-default", 2));
            if (painter.tryPaint(player, hand, brush)) {
                e.setCancelled(true);
                if (player.getGameMode() != GameMode.CREATIVE) {
                    hand.setAmount(hand.getAmount() - 1);
                }
            }
            return;
        }

        if (!canvases.isCanvas(hand)) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        if (!canvases.isSized(hand)) {
            e.setCancelled(true);
            plugin.openSizeGui(player, hand);
            return;
        }

        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null && e.getBlockFace() != null) {
            e.setCancelled(true);
            if (placer.place(player, hand, e.getClickedBlock(), e.getBlockFace())) {
                if (player.getGameMode() != GameMode.CREATIVE) {
                    hand.setAmount(hand.getAmount() - 1);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFrame(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!(e.getRightClicked() instanceof ItemFrame frame)) return;
        Player player = e.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();

        ItemStack inFrame = frame.getItem();
        boolean canvasTile = canvases.isPaintingTile(inFrame) || canvases.isCanvas(inFrame);

        if (MapColors.isDye(hand.getType()) && canvasTile) {
            e.setCancelled(true);
            int brush = player.isSneaking() ? 1 : Math.max(1, plugin.getConfig().getInt("brush-default", 2));
            if (painter.tryPaint(player, hand, brush)) {
                if (player.getGameMode() != GameMode.CREATIVE) {
                    hand.setAmount(hand.getAmount() - 1);
                }
            }
            return;
        }

        // Block rotating / stealing unfinished feel — allow take only if empty dye intent
        if (canvasTile && !hand.getType().isAir()) {
            // allow placing nothing; prevent non-dye interactions messing art
            if (!MapColors.isDye(hand.getType())) {
                e.setCancelled(true);
            }
        }
    }
}
