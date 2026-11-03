package ru.bydloland.implants;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ImplantService {

    public static final String[] CATEGORIES = {"nerves", "heart", "brain", "eyes", "arms", "legs"};

    private static final String KEY_HEART = "bydlo_implant_heart";
    private static final String KEY_NERVES = "bydlo_implant_nerves";
    private static final String KEY_ARMS = "bydlo_implant_arms";

    public record ImplantState(String variant, int level) {
    }

    private final BydloImplantsPlugin plugin;
    private final Map<UUID, Map<String, ImplantState>> data = new LinkedHashMap<>();
    /** Выбранный вариант в GUI до установки (не сохранён). */
    private final Map<UUID, Map<String, String>> pendingVariant = new ConcurrentHashMap<>();
    private File playersFile;
    private FileConfiguration playersYaml;

    public ImplantService(BydloImplantsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        playersFile = new File(plugin.getDataFolder(), "players.yml");
        if (!playersFile.exists()) {
            plugin.getDataFolder().mkdirs();
            try {
                playersFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Cannot create players.yml: " + e.getMessage());
            }
        }
        playersYaml = YamlConfiguration.loadConfiguration(playersFile);
        data.clear();
        for (String key : playersYaml.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                ConfigurationSection sec = playersYaml.getConfigurationSection(key);
                if (sec == null) continue;
                Map<String, ImplantState> map = new LinkedHashMap<>();
                for (String cat : sec.getKeys(false)) {
                    ConfigurationSection cs = sec.getConfigurationSection(cat);
                    if (cs != null) {
                        int lvl = Math.min(3, cs.getInt("level", 0));
                        String var = cs.getString("variant", defaultVariant(cat));
                        if (lvl > 0) map.put(cat.toLowerCase(Locale.ROOT), new ImplantState(var, lvl));
                    } else {
                        // старый формат: heart: 2
                        int lvl = Math.min(3, sec.getInt(cat, 0));
                        if (lvl > 0) {
                            map.put(cat.toLowerCase(Locale.ROOT),
                                    new ImplantState(defaultVariant(cat), lvl));
                        }
                    }
                }
                if (!map.isEmpty()) data.put(uuid, map);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        if (playersYaml == null || playersFile == null) return;
        for (String key : new ArrayList<>(playersYaml.getKeys(false))) {
            playersYaml.set(key, null);
        }
        for (Map.Entry<UUID, Map<String, ImplantState>> e : data.entrySet()) {
            String path = e.getKey().toString();
            for (Map.Entry<String, ImplantState> c : e.getValue().entrySet()) {
                ImplantState st = c.getValue();
                if (st != null && st.level() > 0) {
                    playersYaml.set(path + "." + c.getKey() + ".variant", st.variant());
                    playersYaml.set(path + "." + c.getKey() + ".level", st.level());
                }
            }
        }
        try {
            playersYaml.save(playersFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Cannot save players.yml: " + ex.getMessage());
        }
    }

    public int maxInstalled() {
        return Math.max(1, plugin.getConfig().getInt("max-installed", 3));
    }

    public int costForLevel(int level) {
        List<Integer> costs = plugin.getConfig().getIntegerList("cost-ingots");
        if (costs.isEmpty()) costs = List.of(4, 8, 14);
        int idx = Math.max(0, Math.min(level - 1, costs.size() - 1));
        return costs.get(idx);
    }

    public String displayName(String category) {
        String path = "categories." + category + ".display";
        String d = plugin.getConfig().getString(path);
        return d != null ? d : category;
    }

    public String iconCe(String category) {
        return plugin.getConfig().getString("categories." + category + ".icon-ce");
    }

    public Material iconMaterial(String category) {
        String raw = plugin.getConfig().getString("categories." + category + ".icon", "PAPER");
        try {
            return Material.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Material.PAPER;
        }
    }

    public List<String> categoryLore(String category) {
        return plugin.getConfig().getStringList("categories." + category + ".lore");
    }

    public String defaultVariant(String category) {
        String v = plugin.getConfig().getString("categories." + category + ".default-variant");
        if (v != null && !v.isBlank()) return v.toLowerCase(Locale.ROOT);
        List<String> ids = variantIds(category);
        return ids.isEmpty() ? "default" : ids.get(0);
    }

    public List<String> variantIds(String category) {
        ConfigurationSection sec = plugin.getConfig()
                .getConfigurationSection("categories." + category + ".variants");
        if (sec == null) return List.of();
        return new ArrayList<>(sec.getKeys(false));
    }

    public String variantDisplay(String category, String variant) {
        String d = plugin.getConfig().getString(
                "categories." + category + ".variants." + variant + ".display");
        return d != null ? d : variant;
    }

    public List<String> variantLevels(String category, String variant) {
        return plugin.getConfig().getStringList(
                "categories." + category + ".variants." + variant + ".levels");
    }

    public String cyclePendingVariant(UUID uuid, String category) {
        category = category.toLowerCase(Locale.ROOT);
        List<String> ids = variantIds(category);
        if (ids.isEmpty()) return defaultVariant(category);
        Map<String, String> map = pendingVariant.computeIfAbsent(uuid, u -> new ConcurrentHashMap<>());
        String cur = map.getOrDefault(category, selectedOrDefault(uuid, category));
        int idx = ids.indexOf(cur);
        String next = ids.get((idx + 1 + ids.size()) % ids.size());
        map.put(category, next);
        return next;
    }

    public String selectedOrDefault(UUID uuid, String category) {
        category = category.toLowerCase(Locale.ROOT);
        Map<String, String> pend = pendingVariant.get(uuid);
        if (pend != null && pend.containsKey(category)) return pend.get(category);
        ImplantState st = getState(uuid, category);
        if (st != null) return st.variant();
        return defaultVariant(category);
    }

    public ImplantState getState(UUID uuid, String category) {
        Map<String, ImplantState> map = data.get(uuid);
        if (map == null) return null;
        return map.get(category.toLowerCase(Locale.ROOT));
    }

    public Map<String, ImplantState> getInstalled(UUID uuid) {
        Map<String, ImplantState> map = data.get(uuid);
        if (map == null) return Collections.emptyMap();
        return Collections.unmodifiableMap(map);
    }

    public int getLevel(UUID uuid, String category) {
        ImplantState st = getState(uuid, category);
        return st == null ? 0 : st.level();
    }

    public String getVariant(UUID uuid, String category) {
        ImplantState st = getState(uuid, category);
        return st == null ? selectedOrDefault(uuid, category) : st.variant();
    }

    public int installedCount(UUID uuid) {
        Map<String, ImplantState> map = data.get(uuid);
        return map == null ? 0 : map.size();
    }

    public boolean canUseImplants(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("BydloClasses")) return false;
        try {
            Class<?> api = Class.forName("ru.bydloland.classes.BydloClassesAPI");
            try {
                Object ok = api.getMethod("canUseImplants", Player.class).invoke(null, player);
                if (ok instanceof Boolean b) return b;
            } catch (NoSuchMethodException ignored) {
            }
            Object id = api.getMethod("getClassId", Player.class).invoke(null, player);
            if (id instanceof String s && "netrunner".equalsIgnoreCase(s)) return true;
            Object f1 = api.getMethod("hasFlag", Player.class, String.class).invoke(null, player, "implants");
            if (f1 instanceof Boolean b && b) return b;
            Object f2 = api.getMethod("hasFlag", Player.class, String.class).invoke(null, player, "titan_smeltery");
            return f2 instanceof Boolean b && b;
        } catch (Throwable t) {
            plugin.getLogger().warning("BydloClasses check failed: " + t.getMessage());
            return false;
        }
    }

    public String tryInstall(Player player, String category) {
        category = category.toLowerCase(Locale.ROOT);
        if (!isValidCategory(category)) return "§cНеизвестная категория.";
        if (!canUseImplants(player)) return "§cИмпланты доступны только Киберпанкам.";

        ImplantState cur = getState(player.getUniqueId(), category);
        int current = cur == null ? 0 : cur.level();
        if (current >= 3) return "§cУже максимальный уровень.";

        int next = current + 1;
        if (current == 0 && installedCount(player.getUniqueId()) >= maxInstalled()) {
            return "§cЛимит имплантов: §f" + maxInstalled() + "§c. Сними один съёмником.";
        }

        String variant = current == 0
                ? selectedOrDefault(player.getUniqueId(), category)
                : cur.variant();

        int cost = costForLevel(next);
        if (!consumeTitan(player, cost)) {
            return "§cНужно §f" + cost + " §cтитановых слитков.";
        }

        data.computeIfAbsent(player.getUniqueId(), u -> new LinkedHashMap<>())
                .put(category, new ImplantState(variant, next));
        Map<String, String> pend = pendingVariant.get(player.getUniqueId());
        if (pend != null) pend.remove(category);
        save();
        applyEffects(player);
        return null;
    }

    public String tryUninstall(Player player, String category) {
        category = category.toLowerCase(Locale.ROOT);
        if (!isValidCategory(category)) return "§cНеизвестная категория.";
        int current = getLevel(player.getUniqueId(), category);
        if (current <= 0) return "§cЭтот имплант не установлен.";

        Map<String, ImplantState> map = data.get(player.getUniqueId());
        if (map != null) {
            map.remove(category);
            if (map.isEmpty()) data.remove(player.getUniqueId());
        }
        save();
        applyEffects(player);
        return null;
    }

    public static boolean isValidCategory(String category) {
        for (String c : CATEGORIES) {
            if (c.equalsIgnoreCase(category)) return true;
        }
        return false;
    }

    public boolean isHoldingRemover(Player player) {
        return isRemover(player.getInventory().getItemInMainHand())
                || isRemover(player.getInventory().getItemInOffHand());
    }

    public boolean isRemover(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        String ce = getCeItemId(stack);
        if ("bydlo:cyber_chip".equalsIgnoreCase(ce) || "bydlo:implant_remover".equalsIgnoreCase(ce)) return true;
        if (stack.getType() != Material.SHEARS) return false;
        return plainName(stack).contains("съёмник") || plainName(stack).contains("съемник");
    }

    public boolean isTitanIngot(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return false;
        String ce = getCeItemId(stack);
        if ("bydlo:titan_ingot".equalsIgnoreCase(ce)) return true;
        return plainName(stack).contains("титановый слиток");
    }

    public boolean consumeTitan(Player player, int amount) {
        if (amount <= 0) return true;
        int have = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isTitanIngot(stack)) have += stack.getAmount();
        }
        if (have < amount) return false;

        int left = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack stack = contents[i];
            if (!isTitanIngot(stack)) continue;
            int take = Math.min(left, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            if (stack.getAmount() <= 0) contents[i] = null;
            left -= take;
        }
        player.getInventory().setContents(contents);
        return true;
    }

    public int countTitan(Player player) {
        int n = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isTitanIngot(stack)) n += stack.getAmount();
        }
        return n;
    }

    public void applyEffects(Player player) {
        if (!canUseImplants(player)) {
            clearEffects(player);
            return;
        }

        if (isWet(player)) {
            applyWetShort(player);
            return;
        }

        UUID uuid = player.getUniqueId();
        int nerves = getLevel(uuid, "nerves");
        int heart = getLevel(uuid, "heart");
        int brain = getLevel(uuid, "brain");
        int eyes = getLevel(uuid, "eyes");
        int arms = getLevel(uuid, "arms");
        int legs = getLevel(uuid, "legs");
        String vNerves = getVariant(uuid, "nerves");
        String vHeart = getVariant(uuid, "heart");
        String vBrain = getVariant(uuid, "brain");
        String vEyes = getVariant(uuid, "eyes");
        String vArms = getVariant(uuid, "arms");
        String vLegs = getVariant(uuid, "legs");

        clearTimedEffects(player);

        // Руки: без импланта — Mining Fatigue
        if (arms < 1) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 160, 0, true, false, false));
        }

        // Ноги
        if (legs >= 1) {
            if ("jumper".equalsIgnoreCase(vLegs)) {
                int jumpAmp = legs >= 3 ? 1 : 0;
                player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 160, jumpAmp, true, false, false));
                if (legs >= 2) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, 0, true, false, false));
                }
            } else {
                int speedAmp = legs >= 3 ? 1 : 0;
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 160, speedAmp, true, false, false));
            }
        }

        // Глаза / мозг — NV
        boolean nv = false;
        if (eyes >= 1) {
            if ("optic".equalsIgnoreCase(vEyes) && eyes >= 2) nv = true;
            if ("sniper".equalsIgnoreCase(vEyes) && eyes >= 2) nv = true;
            if ("optic".equalsIgnoreCase(vEyes) && eyes >= 3) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.CONDUIT_POWER, 160, 0, true, false, false));
            }
        }
        if (brain >= 1) {
            if ("neural".equalsIgnoreCase(vBrain) && brain >= 2) nv = true;
            if ("overclock".equalsIgnoreCase(vBrain) && brain >= 2) nv = true;
        }
        if (nv) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 320, 0, true, false, false));
        }

        // Сердце
        if (heart >= 1) {
            if ("pump".equalsIgnoreCase(vHeart)) {
                int regenAmp = heart >= 3 ? 1 : 0;
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, regenAmp, true, false, false));
            } else if (heart >= 3) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 160, 0, true, false, false));
            }
        }

        // Мозг haste
        if (brain >= 1) {
            if ("overclock".equalsIgnoreCase(vBrain)) {
                int amp = brain >= 3 ? 1 : 0;
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 160, amp, true, false, false));
            } else if (brain >= 3) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 160, 0, true, false, false));
            }
        }

        // Руки servo haste/strength
        if (arms >= 1 && "servo".equalsIgnoreCase(vArms)) {
            if (arms >= 2) {
                int amp = arms >= 3 ? 1 : 0;
                player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 160, amp, true, false, false));
            }
            if (arms >= 3) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 160, 0, true, false, false));
            }
        }
        if (arms >= 3 && "wrecker".equalsIgnoreCase(vArms)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 160, 0, true, false, false));
        }

        // Нервы reflex resistance
        if (nerves >= 1 && "reflex".equalsIgnoreCase(vNerves)) {
            int amp = nerves >= 3 ? 1 : 0;
            if (nerves >= 2 || nerves >= 1) {
                // L1 редкий — всё равно даём короткий resistance I
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, amp, true, false, false));
            }
        }

        applyHeartModifier(player, heart, vHeart);
        applyNervesModifier(player, nerves, vNerves);
        applyArmsModifier(player, arms, vArms);
        ImplantVisuals.sync(plugin, player);
    }

    public void clearEffects(Player player) {
        removeModifier(player, maxHealthAttr(), KEY_HEART);
        removeModifier(player, attackSpeedAttr(), KEY_NERVES);
        removeModifier(player, attackDamageAttr(), KEY_ARMS);
        clearTimedEffects(player);
        ImplantVisuals.clearCosmetic(player);
    }

    private void clearTimedEffects(Player player) {
        player.removePotionEffect(PotionEffectType.MINING_FATIGUE);
        player.removePotionEffect(PotionEffectType.SPEED);
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
        player.removePotionEffect(PotionEffectType.REGENERATION);
        player.removePotionEffect(PotionEffectType.HASTE);
        player.removePotionEffect(PotionEffectType.STRENGTH);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.removePotionEffect(PotionEffectType.RESISTANCE);
        player.removePotionEffect(PotionEffectType.CONDUIT_POWER);
    }

    private void applyHeartModifier(Player player, int heart, String variant) {
        Attribute attr = maxHealthAttr();
        if (attr == null) return;
        removeModifier(player, attr, KEY_HEART);
        double bonus = 0;
        if (heart > 0) {
            if ("pump".equalsIgnoreCase(variant)) {
                bonus = heart >= 2 ? (heart - 1) * 2.0 : 0;
            } else {
                bonus = heart * 2.0;
            }
        }
        if (bonus <= 0) return;
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;
        inst.addModifier(new AttributeModifier(
                new NamespacedKey(plugin, KEY_HEART),
                bonus,
                AttributeModifier.Operation.ADD_NUMBER
        ));
        if (player.getHealth() > inst.getValue()) {
            player.setHealth(inst.getValue());
        }
    }

    private void applyNervesModifier(Player player, int nerves, String variant) {
        Attribute attr = attackSpeedAttr();
        if (attr == null) return;
        removeModifier(player, attr, KEY_NERVES);
        double delta;
        if ("reflex".equalsIgnoreCase(variant)) {
            delta = nerves <= 0 ? -0.35 : 0; // рефлекс не трогает attack speed
        } else {
            delta = nerves <= 0 ? -0.35 : nerves * 0.2;
        }
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;
        inst.addModifier(new AttributeModifier(
                new NamespacedKey(plugin, KEY_NERVES),
                delta,
                AttributeModifier.Operation.ADD_NUMBER
        ));
    }

    private void applyArmsModifier(Player player, int arms, String variant) {
        Attribute attr = attackDamageAttr();
        if (attr == null) return;
        removeModifier(player, attr, KEY_ARMS);
        if (arms <= 0 || !"wrecker".equalsIgnoreCase(variant)) return;
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;
        inst.addModifier(new AttributeModifier(
                new NamespacedKey(plugin, KEY_ARMS),
                arms * 1.0,
                AttributeModifier.Operation.ADD_NUMBER
        ));
    }

    private void removeModifier(Player player, Attribute attr, String keyName) {
        if (attr == null) return;
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) return;
        NamespacedKey key = new NamespacedKey(plugin, keyName);
        try {
            inst.getClass().getMethod("removeModifier", NamespacedKey.class).invoke(inst, key);
            return;
        } catch (Throwable ignored) {
        }
        for (AttributeModifier mod : new ArrayList<>(inst.getModifiers())) {
            if (key.equals(mod.getKey())) {
                inst.removeModifier(mod);
            }
        }
    }

    private static Attribute maxHealthAttr() {
        try {
            return Attribute.valueOf("MAX_HEALTH");
        } catch (IllegalArgumentException e) {
            try {
                return Attribute.valueOf("GENERIC_MAX_HEALTH");
            } catch (IllegalArgumentException e2) {
                return null;
            }
        }
    }

    private static Attribute attackSpeedAttr() {
        try {
            return Attribute.valueOf("ATTACK_SPEED");
        } catch (IllegalArgumentException e) {
            try {
                return Attribute.valueOf("GENERIC_ATTACK_SPEED");
            } catch (IllegalArgumentException e2) {
                return null;
            }
        }
    }

    private static Attribute attackDamageAttr() {
        try {
            return Attribute.valueOf("ATTACK_DAMAGE");
        } catch (IllegalArgumentException e) {
            try {
                return Attribute.valueOf("GENERIC_ATTACK_DAMAGE");
            } catch (IllegalArgumentException e2) {
                return null;
            }
        }
    }

    private static String plainName(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) return "";
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return "";
        return ChatColor.stripColor(meta.getDisplayName()).toLowerCase(Locale.ROOT);
    }

    public static boolean isWet(Player player) {
        if (player.isInWater()) return true;
        try {
            Object rain = Player.class.getMethod("isInRain").invoke(player);
            if (rain instanceof Boolean b && b) return true;
        } catch (Throwable ignored) {
        }
        org.bukkit.block.Block block = player.getLocation().getBlock();
        org.bukkit.Material type = block.getType();
        if (type == org.bukkit.Material.WATER || type == org.bukkit.Material.BUBBLE_COLUMN) return true;
        if (!player.getWorld().hasStorm()) return false;
        if (block.getLightFromSky() < 13) return false;
        int highest = player.getWorld().getHighestBlockYAt(player.getLocation());
        return player.getLocation().getBlockY() >= highest - 1;
    }

    private void applyWetShort(Player player) {
        UUID uuid = player.getUniqueId();
        clearTimedEffects(player);
        applyHeartModifier(player, 0, getVariant(uuid, "heart"));
        applyNervesModifier(player, 0, getVariant(uuid, "nerves"));
        applyArmsModifier(player, 0, getVariant(uuid, "arms"));
        player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 160, 0, true, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 160, 1, true, false, false));
        if (player.getHealth() > 2.0) {
            player.damage(1.0);
        }
        player.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK,
                player.getLocation().add(0, 1, 0), 10, 0.25, 0.4, 0.25, 0.05);
        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 0.5f, 1.6f);
        player.sendActionBar("§bКороткое замыкание");
        ImplantVisuals.sync(plugin, player);
    }

    public static String getCeItemId(ItemStack stack) {
        if (stack == null) return null;
        try {
            Class<?> ce = Class.forName("net.momirealms.craftengine.bukkit.api.CraftEngineItems");
            Object key = ce.getMethod("getCustomItemId", ItemStack.class).invoke(null, stack);
            if (key == null) return null;
            Object ns = key.getClass().getMethod("namespace").invoke(key);
            Object val = key.getClass().getMethod("value").invoke(key);
            if (ns != null && val != null) return ns + ":" + val;
            return key.toString();
        } catch (Throwable t) {
            return null;
        }
    }
}
