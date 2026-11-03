package ru.bydlolend.bydlomod.frame;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import ru.bydlolend.bydlomod.BydloMod;

/** ПКМ ножницами по рамке → скрыть / показать рамку (предмет остаётся). */
public final class ShearsInvisibleFrameListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShearsFrame(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof ItemFrame frame)) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.getType() != Material.SHEARS) {
            return;
        }

        event.setCancelled(true);

        boolean nowVisible = !frame.isVisible();
        frame.setVisible(nowVisible);

        if (player.getGameMode() != GameMode.CREATIVE) {
            damageShears(tool, player);
        }

        player.playSound(frame.getLocation(), Sound.ENTITY_SHEEP_SHEAR, 0.7f, nowVisible ? 0.8f : 1.2f);
        if (nowVisible) {
            player.sendActionBar(BydloMod.color("&aРамка снова видна"));
        } else {
            player.sendActionBar(BydloMod.color("&dРамка скрыта &7(ещё раз ножницами — показать)"));
        }
    }

    private static void damageShears(ItemStack shears, Player player) {
        ItemMeta meta = shears.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }
        int max = shears.getType().getMaxDurability();
        if (max <= 0) {
            return;
        }
        int next = damageable.getDamage() + 1;
        if (next >= max) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 1f);
            return;
        }
        damageable.setDamage(next);
        shears.setItemMeta(meta);
    }
}
