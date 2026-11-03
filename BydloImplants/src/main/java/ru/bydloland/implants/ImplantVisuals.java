package ru.bydloland.implants;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;

/**
 * Визуал имплантов по слотам брони (не путаем ноги с руками):
 * голова — мозг/глаза; грудь — руки; ботинки — ноги.
 */
public final class ImplantVisuals {

    public static final String CE_INTERFACE = "bydlo:cyber_interface";
    public static final String CE_EYES = "bydlo:cyber_eyes";
    public static final String CE_ARMS = "bydlo:cyber_arms";
    public static final String CE_LEGS = "bydlo:cyber_legs";
    /** Старая оболочка на шлеме — ещё чистим, если висит. */
    @Deprecated
    public static final String CE_SKIN = "bydlo:cyber_skin";

    private static final String MARK = "§7bydlo-implant-cosmetic";

    private ImplantVisuals() {
    }

    public static void sync(BydloImplantsPlugin plugin, Player player) {
        ImplantService svc = plugin.service();
        if (!svc.canUseImplants(player) || svc.installedCount(player.getUniqueId()) <= 0) {
            clearCosmetic(player);
            return;
        }
        if (plugin.getConfig().getBoolean("auto-cosmetic", true)) {
            ensureCosmetic(plugin, player, svc);
        }
        if (plugin.getConfig().getBoolean("particle-visuals", true)) {
            spawnParticles(player, svc);
        }
    }

    private static void ensureCosmetic(BydloImplantsPlugin plugin, Player player, ImplantService svc) {
        PlayerInventory inv = player.getInventory();
        var uuid = player.getUniqueId();

        // Голова: только мозг / глаза (не nanomachines и не кулаки)
        String headWant = null;
        if (svc.getLevel(uuid, "brain") >= 1) {
            headWant = CE_INTERFACE;
        } else if (svc.getLevel(uuid, "eyes") >= 1) {
            headWant = CE_EYES;
        }
        applySlot(inv, Slot.HEAD, headWant);

        // Грудь: руки
        String chestWant = svc.getLevel(uuid, "arms") >= 1 ? CE_ARMS : null;
        applySlot(inv, Slot.CHEST, chestWant);

        // Ботинки: ноги (раньше ошибочно шла оболочка на руки)
        String feetWant = svc.getLevel(uuid, "legs") >= 1 ? CE_LEGS : null;
        applySlot(inv, Slot.FEET, feetWant);
    }

    private enum Slot { HEAD, CHEST, FEET }

    private static void applySlot(PlayerInventory inv, Slot slot, String wantCe) {
        ItemStack current = switch (slot) {
            case HEAD -> inv.getHelmet();
            case CHEST -> inv.getChestplate();
            case FEET -> inv.getBoots();
        };
        boolean ours = isOurCosmetic(current);

        if (wantCe == null) {
            if (ours) setSlot(inv, slot, null);
            return;
        }

        // чужой предмет в слоте — не трогаем
        if (current != null && !current.getType().isAir() && !ours) {
            return;
        }

        if (ours && sameCe(current, wantCe)) {
            return;
        }

        ItemStack cosmetic = CeItems.build(wantCe);
        if (cosmetic == null) {
            // fallback: ванильная кожа нужного слота
            cosmetic = fallbackLeather(slot, wantCe);
        }
        if (cosmetic == null) return;
        markCosmetic(cosmetic);
        tintFor(cosmetic, wantCe);
        setSlot(inv, slot, cosmetic);
    }

    private static ItemStack fallbackLeather(Slot slot, String wantCe) {
        Material mat = switch (slot) {
            case HEAD -> Material.LEATHER_HELMET;
            case CHEST -> Material.LEATHER_CHESTPLATE;
            case FEET -> Material.LEATHER_BOOTS;
        };
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            String name = switch (wantCe) {
                case CE_EYES -> "§aКибер-окуляры";
                case CE_INTERFACE -> "§dНейроинтерфейс";
                case CE_ARMS -> "§6Серво-каркас";
                case CE_LEGS -> "§eКибер-ускорители";
                default -> "§bКибер-косметика";
            };
            meta.setDisplayName(name);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static void setSlot(PlayerInventory inv, Slot slot, ItemStack stack) {
        switch (slot) {
            case HEAD -> inv.setHelmet(stack);
            case CHEST -> inv.setChestplate(stack);
            case FEET -> inv.setBoots(stack);
        }
    }

    private static void tintFor(ItemStack stack, String wantCe) {
        ItemMeta meta = stack.getItemMeta();
        if (!(meta instanceof LeatherArmorMeta leather)) return;
        Color color = switch (wantCe) {
            case CE_EYES -> Color.fromRGB(80, 220, 140);
            case CE_INTERFACE -> Color.fromRGB(160, 90, 220);
            case CE_ARMS -> Color.fromRGB(220, 140, 40);
            case CE_LEGS -> Color.fromRGB(240, 210, 50);
            default -> Color.fromRGB(40, 50, 60);
        };
        leather.setColor(color);
        stack.setItemMeta(leather);
    }

    public static void clearCosmetic(Player player) {
        PlayerInventory inv = player.getInventory();
        if (isOurCosmetic(inv.getHelmet())) inv.setHelmet(null);
        if (isOurCosmetic(inv.getChestplate())) inv.setChestplate(null);
        if (isOurCosmetic(inv.getBoots())) inv.setBoots(null);
        // убрать старую оболочку, если ещё висит
        if (isOurCosmetic(inv.getLeggings())) {
            String ce = ImplantService.getCeItemId(inv.getLeggings());
            if (CE_SKIN.equalsIgnoreCase(ce) || isMarked(inv.getLeggings())) {
                inv.setLeggings(null);
            }
        }
    }

    private static void spawnParticles(Player player, ImplantService svc) {
        Location base = player.getLocation().add(0, 1.0, 0);
        var uuid = player.getUniqueId();
        if (svc.getLevel(uuid, "eyes") >= 1) {
            Location eyes = base.clone().add(0, 0.55, 0);
            player.getWorld().spawnParticle(Particle.END_ROD, eyes, 2, 0.15, 0.04, 0.15, 0);
            player.getWorld().spawnParticle(Particle.DUST, eyes, 3, 0.12, 0.03, 0.12, 0,
                    new Particle.DustOptions(Color.fromRGB(80, 255, 140), 0.85f));
        }
        if (svc.getLevel(uuid, "heart") >= 1) {
            player.getWorld().spawnParticle(Particle.DUST, base.clone().add(0, 0.1, 0), 2, 0.15, 0.1, 0.15, 0,
                    new Particle.DustOptions(Color.fromRGB(220, 40, 60), 0.7f));
        }
        if (svc.getLevel(uuid, "nerves") >= 1) {
            player.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, base, 2, 0.25, 0.35, 0.25, 0);
        }
        if (svc.getLevel(uuid, "arms") >= 1) {
            player.getWorld().spawnParticle(Particle.CRIT, base.clone().add(0.4, 0, 0), 2, 0.05, 0.2, 0.05, 0);
            player.getWorld().spawnParticle(Particle.CRIT, base.clone().add(-0.4, 0, 0), 2, 0.05, 0.2, 0.05, 0);
        }
        if (svc.getLevel(uuid, "legs") >= 1) {
            Location feet = player.getLocation().add(0, 0.12, 0);
            player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, feet, 2, 0.18, 0.02, 0.18, 0);
            player.getWorld().spawnParticle(Particle.DUST, feet, 3, 0.22, 0.02, 0.22, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 220, 60), 0.85f));
        }
        if (svc.getLevel(uuid, "brain") >= 1) {
            player.getWorld().spawnParticle(Particle.ENCHANT, base.clone().add(0, 0.7, 0), 3, 0.2, 0.1, 0.2, 0);
        }
    }

    public static boolean isOurCosmetic(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        String ce = ImplantService.getCeItemId(stack);
        if (CE_SKIN.equalsIgnoreCase(ce) || CE_INTERFACE.equalsIgnoreCase(ce)
                || CE_EYES.equalsIgnoreCase(ce) || CE_ARMS.equalsIgnoreCase(ce)
                || CE_LEGS.equalsIgnoreCase(ce)) {
            return true;
        }
        return isMarked(stack);
    }

    private static boolean isMarked(ItemStack stack) {
        if (!stack.hasItemMeta()) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasLore() || meta.getLore() == null) return false;
        return meta.getLore().stream().anyMatch(l -> l != null && l.contains("bydlo-implant-cosmetic"));
    }

    private static boolean sameCe(ItemStack stack, String id) {
        String ce = ImplantService.getCeItemId(stack);
        return id.equalsIgnoreCase(ce);
    }

    private static void markCosmetic(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return;
        java.util.List<String> lore = meta.hasLore() && meta.getLore() != null
                ? new java.util.ArrayList<>(meta.getLore())
                : new java.util.ArrayList<>();
        if (lore.stream().noneMatch(l -> l != null && l.contains("bydlo-implant-cosmetic"))) {
            lore.add(MARK);
        }
        meta.setLore(lore);
        meta.setUnbreakable(true);
        stack.setItemMeta(meta);
    }
}
