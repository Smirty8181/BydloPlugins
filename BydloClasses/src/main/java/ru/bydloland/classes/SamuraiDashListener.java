package ru.bydloland.classes;

import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Самурай: рывок, чайная пауза, честь, заряженный удар.
 */
public final class SamuraiDashListener implements Listener {

    private static final long DASH_CD_MS = 7000L;
    private static final long HEAVY_CD_MS = 14000L;
    private static final long TEA_BUFF_MS = 180_000L;
    private static final long CHARGE_MS = 1200L;
    private static final int TEA_SIT_TICKS = 16; // 16 * 10t = 8s
    private static final Set<String> WEAPONS = Set.of(
            "bydlo:katana", "bydlo:wakizashi", "bydlo:odachi"
    );

    private final BydloClassesPlugin plugin;
    private final Map<UUID, Long> dashUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> heavyUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Long> teaBuffUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> teaSit = new ConcurrentHashMap<>();
    private final Map<UUID, Long> chargingSince = new ConcurrentHashMap<>();
    private final Map<UUID, Long> heavyReadyUntil = new ConcurrentHashMap<>();

    public SamuraiDashListener(BydloClassesPlugin plugin) {
        this.plugin = plugin;
    }

    public void resetDash(UUID uuid) {
        dashUntil.remove(uuid);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        dashUntil.remove(id);
        heavyUntil.remove(id);
        teaSit.remove(id);
        chargingSince.remove(id);
        heavyReadyUntil.remove(id);
    }

    public void tick(Player player) {
        if (!BydloClassesAPI.canUseSamuraiGear(player)) {
            teaSit.remove(player.getUniqueId());
            chargingSince.remove(player.getUniqueId());
            return;
        }
        tickTea(player);
        tickCharge(player);
    }

    private void tickTea(Player player) {
        UUID id = player.getUniqueId();
        boolean holding = CeIds.is(player.getInventory().getItemInMainHand(), "bydlo:green_tea")
                || CeIds.is(player.getInventory().getItemInOffHand(), "bydlo:green_tea");
        if (holding && isSitting(player)) {
            int n = teaSit.getOrDefault(id, 0) + 1;
            if (n < TEA_SIT_TICKS) {
                teaSit.put(id, n);
                int left = TEA_SIT_TICKS - n;
                player.sendActionBar("§aЧайная пауза §7· §f" + (left / 2) + "с");
                return;
            }
            teaSit.remove(id);
            consumeTea(player);
            teaBuffUntil.put(id, System.currentTimeMillis() + TEA_BUFF_MS);
            resetDash(id);
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 1, true, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 20, 0, true, false, false));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_GENERIC_DRINK, 0.8f, 1.4f);
            player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 10, 0.3, 0.4, 0.3, 0);
            player.sendMessage("§aЧай. Рывок готов, разум чист.");
        } else {
            teaSit.remove(id);
        }
    }

    private void tickCharge(Player player) {
        UUID id = player.getUniqueId();
        Long start = chargingSince.get(id);
        if (start == null) return;
        if (!player.isSneaking() || !holdingWeapon(player)) {
            chargingSince.remove(id);
            return;
        }
        long held = System.currentTimeMillis() - start;
        int pct = (int) Math.min(10, held * 10 / CHARGE_MS);
        String bar = "§6" + "█".repeat(pct) + "§8" + "█".repeat(10 - pct);
        player.sendActionBar("§eЗаряд " + bar);
        if (held < CHARGE_MS) return;
        chargingSince.remove(id);
        heavyReadyUntil.put(id, System.currentTimeMillis() + 5000L);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 0.4f, 1.8f);
        player.sendActionBar("§6§lЗаряжено — ударь");
        player.sendMessage("§6Тяжёлый взмах готов.");
    }

    private static boolean isSitting(Player player) {
        if (player.isInsideVehicle()) return true;
        String pose = player.getPose().name();
        if ("SITTING".equals(pose) || "SLEEPING".equals(pose) || "CROUCHING".equals(pose) && player.isInsideVehicle()) {
            return true;
        }
        try {
            Class<?> api = Class.forName("dev.geco.gsit.api.GSitAPI");
            for (String m : new String[]{"isPlayerSitting", "isSitting", "isPlayerPosing"}) {
                try {
                    Object v = api.getMethod(m, Player.class).invoke(null, player);
                    if (v instanceof Boolean b && b) return true;
                } catch (NoSuchMethodException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static void consumeTea(Player player) {
        ItemStack main = player.getInventory().getItemInMainHand();
        if (CeIds.is(main, "bydlo:green_tea")) {
            main.setAmount(main.getAmount() - 1);
            return;
        }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (CeIds.is(off, "bydlo:green_tea")) {
            off.setAmount(off.getAmount() - 1);
        }
    }

    private static boolean holdingWeapon(Player player) {
        return CeIds.is(player.getInventory().getItemInMainHand(),
                "bydlo:katana", "bydlo:wakizashi", "bydlo:odachi");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action a = event.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        if (!holdingWeapon(player)) return;
        if (!BydloClassesAPI.canUseSamuraiGear(player)) {
            player.sendMessage("§cЭто техника только самураев.");
            return;
        }

        if (player.isSneaking()) {
            event.setCancelled(true);
            UUID id = player.getUniqueId();
            Long until = heavyUntil.get(id);
            long now = System.currentTimeMillis();
            if (until != null && until > now) {
                player.sendMessage("§7Тяжёлый удар: §f" + ((until - now + 999) / 1000) + "с");
                return;
            }
            chargingSince.putIfAbsent(id, now);
            return;
        }

        event.setCancelled(true);
        dash(player);
    }

    @EventHandler
    public void onUnSneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) return;
        chargingSince.remove(event.getPlayer().getUniqueId());
    }

    private void dash(Player player) {
        long now = System.currentTimeMillis();
        UUID id = player.getUniqueId();
        Long until = dashUntil.get(id);
        if (until != null && until > now) {
            player.sendMessage("§7Рывок перезарядка: §f" + ((until - now + 999) / 1000) + "с");
            return;
        }
        dashUntil.put(id, now + DASH_CD_MS);

        boolean tea = teaBuffUntil.getOrDefault(id, 0L) > now;
        double power = tea ? 1.85 : 1.05;
        Vector dir = player.getLocation().getDirection().normalize();
        Vector dash = new Vector(dir.getX(), 0, dir.getZ());
        if (dash.lengthSquared() < 0.001) dash = new Vector(0, 0, 1);
        dash.normalize().multiply(power);
        dash.setY(tea ? 0.22 : 0.12);
        player.setVelocity(dash);
        player.setFallDistance(0f);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.9f, tea ? 1.35f : 0.9f);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 1, 0), tea ? 12 : 6, 0.3, 0.2, 0.3, 0.02);
        player.sendMessage(tea ? "§f§lРывок!" : "§7Рывок (без чая слабее)");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHonor(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!BydloClassesAPI.canUseSamuraiGear(player)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long ready = heavyReadyUntil.get(id);
        if (ready != null && ready > now && holdingWeapon(player)) {
            heavyReadyUntil.remove(id);
            heavyUntil.put(id, now + HEAVY_CD_MS);
            event.setDamage(event.getDamage() * 2.15);
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
            player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, victim.getLocation().add(0, 1, 0), 4, 0.2, 0.2, 0.2, 0);
            player.sendMessage("§6Тяжёлый взмах!");
            return;
        }

        Vector toVictim = victim.getLocation().toVector().subtract(player.getLocation().toVector());
        toVictim.setY(0);
        if (toVictim.lengthSquared() < 0.0001) return;
        toVictim.normalize();
        Vector look = player.getLocation().getDirection();
        look.setY(0);
        if (look.lengthSquared() < 0.0001) return;
        look.normalize();
        Vector victimLook = victim.getLocation().getDirection();
        victimLook.setY(0);
        if (victimLook.lengthSquared() < 0.0001) victimLook = new Vector(0, 0, 1);
        else victimLook.normalize();

        boolean frontal = look.dot(toVictim) > 0.35;
        boolean backstab = victimLook.dot(toVictim) < -0.45;
        boolean duel = isDuel(player, victim);

        double m = 1.0;
        if (backstab) m *= 0.62;
        else if (frontal) m *= 1.18;
        if (duel) m *= 1.12;
        else m *= 0.90;
        event.setDamage(event.getDamage() * m);
    }

    private static boolean isDuel(Player attacker, LivingEntity victim) {
        int others = 0;
        for (Entity e : victim.getNearbyEntities(8, 4, 8)) {
            if (e.equals(attacker) || e.equals(victim)) continue;
            if (e instanceof Player p && p.getUniqueId().equals(attacker.getUniqueId())) continue;
            if (e instanceof LivingEntity living && !living.isDead()) {
                if (e instanceof Player || living.getKiller() != null || living.getLocation().distanceSquared(attacker.getLocation()) < 25) {
                    if (e instanceof Player) others++;
                }
            }
        }
        return others == 0;
    }
}
