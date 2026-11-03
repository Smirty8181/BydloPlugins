package ru.bydloland.mage;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StaffListener implements Listener {

    private static final int TREE_LIMIT = 120;

    private final BydloMagePlugin plugin;
    private final Map<UUID, Long> digCd = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lumberCd = new ConcurrentHashMap<>();

    public StaffListener(BydloMagePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        String id = CeItems.idOf(hand);
        if (id == null) return;

        if ("bydlo:staff_dig".equals(id)) {
            event.setCancelled(true);
            useDig(player, event.getClickedBlock(), event.getBlockFace());
        } else if ("bydlo:staff_lumber".equals(id)) {
            event.setCancelled(true);
            useLumber(player, event.getClickedBlock());
        }
    }

    private void useDig(Player player, Block clicked, BlockFace face) {
        if (!ClassesBridge.canUseMageGear(player)) {
            player.sendMessage("§cПосохи доступны только магам.");
            return;
        }
        long now = System.currentTimeMillis();
        Long until = digCd.get(player.getUniqueId());
        if (until != null && until > now) {
            player.sendMessage("§7КД посоха копания: §f" + ((until - now + 999) / 1000) + "с");
            return;
        }

        Block origin = clicked != null ? clicked : player.getTargetBlockExact(5);
        if (origin == null || origin.getType().isAir()) {
            player.sendMessage("§7Посмотри на блок.");
            return;
        }

        BlockFace breakFace = face != null ? face : player.getFacing();
        boolean depth2 = plugin.progress().digDepth2(player);
        Set<Block> targets = digArea(origin, breakFace, depth2);
        int broken = 0;
        for (Block b : targets) {
            if (b.getType().isAir() || b.getType() == Material.BEDROCK || b.getType() == Material.BARRIER) continue;
            if (b.getY() < player.getWorld().getMinHeight()) continue;
            if (player.getGameMode() == GameMode.CREATIVE) {
                b.setType(Material.AIR);
            } else {
                b.breakNaturally(player.getInventory().getItemInMainHand());
            }
            broken++;
        }
        if (broken == 0) {
            player.sendMessage("§7Нечего ломать.");
            return;
        }
        digCd.put(player.getUniqueId(), now + plugin.progress().digCooldownMs(player));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_BREAK, 0.8f, 1.4f);
        player.getWorld().spawnParticle(Particle.END_ROD, origin.getLocation().add(0.5, 0.5, 0.5), 16, 0.6, 0.4, 0.6, 0.02);
        player.sendMessage("§dПосох копания §7×" + broken + (depth2 ? " §7(3×3×2)" : " §7(3×3)"));
    }

    private static Set<Block> digArea(Block origin, BlockFace face, boolean depth2) {
        Set<Block> out = new HashSet<>();
        // plane perpendicular to face
        int depth = depth2 ? 2 : 1;
        for (int d = 0; d < depth; d++) {
            Block layer = d == 0 ? origin : origin.getRelative(face.getOppositeFace(), d);
            for (int u = -1; u <= 1; u++) {
                for (int v = -1; v <= 1; v++) {
                    out.add(offsetOnPlane(layer, face, u, v));
                }
            }
        }
        return out;
    }

    private static Block offsetOnPlane(Block base, BlockFace face, int u, int v) {
        return switch (face) {
            case UP, DOWN -> base.getRelative(u, 0, v);
            case NORTH, SOUTH -> base.getRelative(u, v, 0);
            case EAST, WEST -> base.getRelative(0, v, u);
            default -> base.getRelative(u, v, 0);
        };
    }

    private void useLumber(Player player, Block clicked) {
        if (!ClassesBridge.canUseMageGear(player)) {
            player.sendMessage("§cПосохи доступны только магам.");
            return;
        }
        long now = System.currentTimeMillis();
        Long until = lumberCd.get(player.getUniqueId());
        if (until != null && until > now) {
            player.sendMessage("§7КД посоха лесоруба: §f" + ((until - now + 999) / 1000) + "с");
            return;
        }
        Block start = clicked != null ? clicked : player.getTargetBlockExact(5);
        if (start == null || !isLog(start.getType())) {
            player.sendMessage("§7Кликни по бревну.");
            return;
        }

        Set<Block> tree = floodTree(start);
        int n = 0;
        for (Block b : tree) {
            if (player.getGameMode() == GameMode.CREATIVE) {
                b.setType(Material.AIR);
            } else {
                b.breakNaturally(player.getInventory().getItemInMainHand());
            }
            n++;
        }
        lumberCd.put(player.getUniqueId(), now + plugin.progress().lumberCooldownMs(player));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_WOOD_BREAK, 1f, 0.8f);
        player.sendMessage("§aПосох лесоруба §7×" + n + " блоков");
    }

    private static boolean isLog(Material m) {
        return Tag.LOGS.isTagged(m);
    }

    private static boolean isLeaf(Material m) {
        return Tag.LEAVES.isTagged(m);
    }

    private static Set<Block> floodTree(Block start) {
        Set<Block> visited = new HashSet<>();
        ArrayDeque<Block> q = new ArrayDeque<>();
        q.add(start);
        while (!q.isEmpty() && visited.size() < TREE_LIMIT) {
            Block b = q.poll();
            if (!visited.add(b)) continue;
            Material t = b.getType();
            if (!isLog(t) && !isLeaf(t)) {
                visited.remove(b);
                continue;
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block n = b.getRelative(dx, dy, dz);
                        Material nt = n.getType();
                        if ((isLog(nt) || isLeaf(nt)) && !visited.contains(n)) {
                            q.add(n);
                        }
                    }
                }
            }
        }
        return visited;
    }
}
