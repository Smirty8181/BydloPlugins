package ru.bydloland.prison.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerEditBookEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTakeLecternBookEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import ru.bydloland.prison.service.PrisonService;

/**
 * Заключённый: только локальный чат (~80 блоков, как OCD_chat).
 * Глобал (!) режем ДО OCD_chat (LOWEST); радиус форсим и после него (HIGHEST).
 */
public final class PrisonRestrictListener implements Listener {

    /** Совпадает с OCD_chat local_chat.distance */
    private static final double LOCAL_CHAT_BLOCKS = 80.0;

    private final PrisonService prison;

    public PrisonRestrictListener(PrisonService prison) {
        this.prison = prison;
    }

    private boolean deny(Player player, String key) {
        if (!prison.isImprisoned(player)) return false;
        prison.denyAction(player, key);
        return true;
    }

    /** Раньше OCD_chat: запрет глобала с префиксом ! */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onChatGlobalEarly(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!prison.isImprisoned(player)) return;
        String plain = event.signedMessage().message().trim();
        if (!isGlobalAttempt(plain)) return;
        event.setCancelled(true);
        try {
            event.viewers().clear();
        } catch (Throwable ignored) {
        }
        Bukkit.getScheduler().runTask(
                Bukkit.getPluginManager().getPlugin("BydloPrison"),
                () -> prison.denyAction(player, "banned-global"));
    }

    /** После форматтеров: даже если ! уже снят — оставляем только локальных зрителей */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChatForceLocal(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!prison.isImprisoned(player)) return;
        if (event.isCancelled()) return;
        try {
            double r2 = LOCAL_CHAT_BLOCKS * LOCAL_CHAT_BLOCKS;
            event.viewers().removeIf(viewer -> {
                if (!(viewer instanceof Player other)) return true;
                if (other.getUniqueId().equals(player.getUniqueId())) return false;
                if (!other.getWorld().equals(player.getWorld())) return true;
                return other.getLocation().distanceSquared(player.getLocation()) > r2;
            });
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onLegacyChatGlobalEarly(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!prison.isImprisoned(player)) return;
        String msg = event.getMessage() == null ? "" : event.getMessage().trim();
        if (!isGlobalAttempt(msg)) return;
        event.setCancelled(true);
        event.getRecipients().clear();
        prison.denyAction(player, "banned-global");
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLegacyChatForceLocal(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!prison.isImprisoned(player)) return;
        if (event.isCancelled()) return;
        double r2 = LOCAL_CHAT_BLOCKS * LOCAL_CHAT_BLOCKS;
        event.getRecipients().removeIf(other -> {
            if (other.getUniqueId().equals(player.getUniqueId())) return false;
            if (!other.getWorld().equals(player.getWorld())) return true;
            return other.getLocation().distanceSquared(player.getLocation()) > r2;
        });
    }

    private static boolean isGlobalAttempt(String msg) {
        if (msg == null || msg.isEmpty()) return false;
        // OCD_chat: глобал с префиксом !
        return msg.startsWith("!");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!prison.isImprisoned(player)) return;
        String msg = event.getMessage();
        String label = msg.startsWith("/") ? msg.substring(1) : msg;
        int sp = label.indexOf(' ');
        if (sp > 0) label = label.substring(0, sp);
        if (prison.isCommandAllowed(label)) return;
        event.setCancelled(true);
        prison.denyAction(player, "banned-command");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandSend(PlayerCommandSendEvent event) {
        if (!prison.isImprisoned(event.getPlayer())) return;
        event.getCommands().clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (deny(event.getPlayer(), "banned-action")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (deny(event.getPlayer(), "banned-action")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        if (deny(event.getPlayer(), "banned-action")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (deny(event.getPlayer(), "banned-action")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (deny(event.getPlayer(), "banned-action")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (deny(event.getPlayer(), "banned-action")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInvClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (deny(player, "banned-action")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInvDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (deny(player, "banned-action")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInvOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (deny(player, "banned-action")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (deny(event.getPlayer(), "banned-action")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        if (deny(event.getPlayer(), "banned-action")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (deny(event.getPlayer(), "banned-action")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (deny(event.getPlayer(), "banned-action")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (deny(event.getPlayer(), "banned-action")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBed(PlayerBedEnterEvent event) {
        if (deny(event.getPlayer(), "banned-action")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEditBook(PlayerEditBookEvent event) {
        if (deny(event.getPlayer(), "banned-action")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShear(PlayerShearEntityEvent event) {
        if (deny(event.getPlayer(), "banned-action")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (deny(event.getPlayer(), "banned-action")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLectern(PlayerTakeLecternBookEvent event) {
        if (deny(event.getPlayer(), "banned-action")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        if (deny(event.getPlayer(), "banned-action")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicle(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player && deny(player, "banned-action")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlight(PlayerToggleFlightEvent event) {
        if (deny(event.getPlayer(), "banned-action")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        if (!prison.isImprisoned(event.getPlayer())) return;
        if (event.getNewGameMode() != org.bukkit.GameMode.SPECTATOR) {
            event.setCancelled(true);
            prison.denyAction(event.getPlayer(), "banned-action");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && prison.isImprisoned(player)) {
            event.setCancelled(true);
            return;
        }
        if (prison.pigs().isPrisonPig(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageBy(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && prison.isImprisoned(player)) {
            event.setCancelled(true);
            prison.denyAction(player, "banned-action");
        }
        if (prison.pigs().isPrisonPig(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() instanceof Player player && prison.isImprisoned(player)) {
            event.setCancelled(true);
        }
        if (prison.pigs().isPrisonPig(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && prison.isImprisoned(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (prison.isImprisoned(event.getEntity())) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        // Spectator on entity — movement is limited; no extra action
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!prison.isImprisoned(event.getPlayer())) return;
        // Allow plugin teleports to pig / release; block others except SPECTATE / PLUGIN from us
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.SPECTATE
                || cause == PlayerTeleportEvent.TeleportCause.PLUGIN
                || cause == PlayerTeleportEvent.TeleportCause.UNKNOWN) {
            return;
        }
        event.setCancelled(true);
        prison.denyAction(event.getPlayer(), "banned-action");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickupArrow(PlayerPickupArrowEvent event) {
        if (deny(event.getPlayer(), "banned-action")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (prison.isImprisoned(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && deny(player, "banned-action")) {
            event.setCancelled(true);
        }
    }
}
