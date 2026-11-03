package ru.bydloland.crafts;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CraftCatalog {

    public record Category(String id, String title, String color) {
    }

    private final List<Category> categories;
    private final Map<String, List<CraftRecipe>> byCategory;
    private final Map<String, CraftRecipe> byId;

    private CraftCatalog(List<Category> categories, Map<String, List<CraftRecipe>> byCategory, Map<String, CraftRecipe> byId) {
        this.categories = categories;
        this.byCategory = byCategory;
        this.byId = byId;
    }

    public List<Category> categories() {
        return categories;
    }

    public List<CraftRecipe> recipes(String categoryId) {
        return byCategory.getOrDefault(categoryId, List.of());
    }

    public CraftRecipe byId(String id) {
        return byId.get(id);
    }

    public static CraftCatalog create() {
        List<Category> cats = List.of(
                new Category("mithril", "Дворф", "§a"),
                new Category("samurai", "Самурай", "§f"),
                new Category("titan", "Киберпанк", "§b"),
                new Category("mage", "Маг", "§d"),
                new Category("bar", "Бар", "§6"),
                new Category("misc", "Разное", "§e")
        );

        List<CraftRecipe> all = new ArrayList<>();
        String M = "bydlo:mithril_ingot";
        String G = "minecraft:gold_ingot";
        String S = "minecraft:stick";
        String RM = "bydlo:raw_mithril";
        String F = "minecraft:furnace";
        String ST = "bydlo:steel_ingot";
        String IR = "minecraft:iron_ingot";
        String T = "bydlo:titan_ingot";
        String RT = "bydlo:raw_titan";
        String BF = "minecraft:blast_furnace";
        String STR = "minecraft:string";

        // Mithril tools
        all.add(CraftRecipe.shaped("mithril_sword", "Мифриловый меч", "mithril", "bydlo:mithril_sword",
                M, null, null, M, null, null, S, null, null));
        all.add(CraftRecipe.shaped("mithril_pickaxe", "Мифриловая кирка", "mithril", "bydlo:mithril_pickaxe",
                G, M, G, null, S, null, null, S, null));
        all.add(CraftRecipe.shaped("mithril_axe", "Мифриловый топор", "mithril", "bydlo:mithril_axe",
                M, G, null, G, S, null, null, S, null));
        all.add(CraftRecipe.shaped("mithril_shovel", "Мифриловая лопата", "mithril", "bydlo:mithril_shovel",
                M, null, null, S, null, null, S, null, null));
        all.add(CraftRecipe.shaped("mithril_hoe", "Мифриловая мотыга", "mithril", "bydlo:mithril_hoe",
                M, G, null, null, S, null, null, S, null));
        // Mithril armor
        all.add(CraftRecipe.shaped("mithril_helmet", "Мифриловый шлем", "mithril", "bydlo:mithril_helmet",
                M, G, M, G, null, G, null, null, null));
        all.add(CraftRecipe.shaped("mithril_chestplate", "Мифриловый нагрудник", "mithril", "bydlo:mithril_chestplate",
                G, null, G, M, G, M, G, M, G));
        all.add(CraftRecipe.shaped("mithril_leggings", "Мифриловые поножи", "mithril", "bydlo:mithril_leggings",
                M, G, M, G, null, G, G, null, G));
        all.add(CraftRecipe.shaped("mithril_boots", "Мифриловые ботинки", "mithril", "bydlo:mithril_boots",
                M, null, M, G, null, G, null, null, null));
        all.add(CraftRecipe.shaped("mithril_smeltery", "Мифриловая плавильня", "mithril", "bydlo:mithril_smeltery",
                RM, RM, RM, RM, F, RM, RM, RM, RM));

        // Samurai
        all.add(CraftRecipe.noteOnly("steel", "Сталь", "samurai", "bydlo:steel_ingot",
                "§7Железный слиток → печь / плавильня"));
        all.add(CraftRecipe.shaped("katana", "Катана", "samurai", "bydlo:katana",
                null, ST, null, null, ST, null, null, S, null));
        all.add(CraftRecipe.shaped("wakizashi", "Вакидзаси", "samurai", "bydlo:wakizashi",
                ST, null, null, S, null, null, null, null, null));
        all.add(CraftRecipe.shaped("odachi", "Одачи", "samurai", "bydlo:odachi",
                null, ST, null, null, ST, null, ST, IR, S));
        all.add(CraftRecipe.shaped("samurai_helmet", "Самурайский шлем", "samurai", "bydlo:samurai_helmet",
                ST, ST, ST, ST, null, ST, null, null, null));
        all.add(CraftRecipe.shaped("samurai_chestplate", "Самурайский нагрудник", "samurai", "bydlo:samurai_chestplate",
                ST, null, ST, ST, ST, ST, ST, ST, ST));
        all.add(CraftRecipe.shaped("samurai_leggings", "Самурайские поножи", "samurai", "bydlo:samurai_leggings",
                ST, ST, ST, ST, null, ST, ST, null, ST));
        all.add(CraftRecipe.shaped("samurai_boots", "Самурайские ботинки", "samurai", "bydlo:samurai_boots",
                ST, null, ST, ST, null, ST, null, null, null));
        all.add(CraftRecipe.shaped("green_tea", "Зелёный чай", "samurai", "bydlo:green_tea",
                null, "minecraft:oak_leaves", null,
                null, "minecraft:sugar", null,
                null, "minecraft:paper", null));
        all.add(CraftRecipe.shaped("japan_lantern", "Японский фонарь", "samurai", "bydlo:japan_lantern",
                "minecraft:paper", "minecraft:paper", "minecraft:paper",
                "minecraft:paper", "minecraft:torch", "minecraft:paper",
                "minecraft:paper", "minecraft:gold_nugget", "minecraft:paper"));
        all.add(CraftRecipe.shaped("tatami", "Татами", "samurai", "bydlo:tatami",
                "minecraft:wheat", "minecraft:wheat", "minecraft:wheat",
                "minecraft:wheat", "minecraft:stick", "minecraft:wheat",
                "minecraft:wheat", "minecraft:wheat", "minecraft:wheat"));
        all.add(CraftRecipe.shaped("norimono", "Норимоно", "samurai", "bydlo:norimono",
                "minecraft:stick", "minecraft:chest", "minecraft:stick",
                "minecraft:red_wool", "minecraft:red_wool", "minecraft:red_wool",
                "minecraft:stick", null, "minecraft:stick"));

        // Titan
        all.add(CraftRecipe.shaped("titan_katana", "Титановая катана", "titan", "bydlo:titan_katana",
                null, null, T, null, T, null, S, null, null));
        all.add(CraftRecipe.shaped("titan_revolver", "Архангел", "titan", "bydlo:titan_revolver",
                T, T, T, null, STR, null, null, STR, null));
        all.add(CraftRecipe.shaped("titan_pickaxe", "Титановый киркойд", "titan", "bydlo:titan_pickaxe",
                T, T, T, null, S, null, null, S, null));
        all.add(CraftRecipe.shaped("titan_axe", "Дата-жнец", "titan", "bydlo:titan_axe",
                T, T, null, T, S, null, null, S, null));
        all.add(CraftRecipe.shaped("titan_shovel", "Техно-лопата", "titan", "bydlo:titan_shovel",
                null, T, null, null, S, null, null, S, null));
        all.add(CraftRecipe.shaped("titan_hammer", "Молот Сасквотча", "titan", "bydlo:titan_hammer",
                T, T, T, T, S, T, null, S, null));
        all.add(CraftRecipe.shaped("cyber_furnace", "Киберпечь", "titan", "bydlo:cyber_furnace",
                RT, RT, RT, RT, BF, RT, RT, RT, RT));
        all.add(CraftRecipe.shaped("implant_remover", "Съёмник имплантов", "titan", "bydlo:implant_remover",
                null, T, null, T, "minecraft:shears", T, null, T, null));
        all.add(CraftRecipe.shaped("ripper_table", "Стол рипера", "titan", "bydlo:ripper_table",
                T, T, T, T, "minecraft:crafting_table", T, IR, IR, IR));

        // Mage
        String E = "bydlo:etherium_ingot";
        String EC = "bydlo:etherium_crystal";
        String AM = "minecraft:amethyst_block";
        String CT = "minecraft:crafting_table";
        all.add(CraftRecipe.noteOnly("etherium_smelt", "Эфириумовый слиток", "mage", "bydlo:etherium_ingot",
                "§7Сырой эфириум → печь / плавильня"));
        all.add(CraftRecipe.shaped("etherium_crystal", "Эфириумовый кристалл", "mage", EC,
                E, null, null, E, null, null, E, null, null));
        all.add(CraftRecipe.shaped("magic_tower", "Башня магии", "mage", "bydlo:magic_tower",
                E, AM, E, E, CT, E, E, E, E));
        all.add(CraftRecipe.shaped("staff_dig", "Посох копания", "mage", "bydlo:staff_dig",
                null, E, null, null, E, null, null, S, null));
        all.add(CraftRecipe.shaped("staff_lumber", "Посох лесоруба", "mage", "bydlo:staff_lumber",
                null, E, null, null, "minecraft:oak_log", null, null, S, null));
        all.add(CraftRecipe.shaped("mage_hood", "Капюшон мага", "mage", "bydlo:mage_hood",
                E, E, E, E, null, E, null, null, null));
        all.add(CraftRecipe.shaped("mage_robe", "Мантия мага", "mage", "bydlo:mage_robe",
                E, null, E, E, E, E, E, E, E));
        all.add(CraftRecipe.shaped("mage_pants", "Штаны мага", "mage", "bydlo:mage_pants",
                E, E, E, E, null, E, E, null, E));
        all.add(CraftRecipe.shaped("mage_boots", "Сапоги мага", "mage", "bydlo:mage_boots",
                E, null, E, E, null, E, null, null, null));
        all.add(CraftRecipe.shaped("familiar_charm", "Амулет фамильяра", "mage", "bydlo:familiar_charm",
                null, E, null,
                "minecraft:feather", "minecraft:bone", "minecraft:feather",
                null, "minecraft:string", null));

        // Bar — варка в котле Brewery, не верстак
        all.add(CraftRecipe.noteOnly("beer", "Светлое пиво", "bar", "bydlo:beer",
                "§7Котёл Brewery · пшеница · /варка"));
        all.add(CraftRecipe.noteOnly("vodka", "Русский Стандарт", "bar", "bydlo:vodka",
                "§7Котёл + дистилляция · /варка"));
        all.add(CraftRecipe.noteOnly("wine", "Красное вино", "bar", "bydlo:wine",
                "§7Котёл · ягоды/виноград · /варка"));
        all.add(CraftRecipe.noteOnly("mead", "Медовуха", "bar", "bydlo:mead",
                "§7Котёл · мёд · /варка"));
        all.add(CraftRecipe.noteOnly("gin", "Джин", "bar", "bydlo:gin",
                "§7Котёл · пшеница + синие цветы + яблоко"));
        all.add(CraftRecipe.noteOnly("tequila", "Текила", "bar", "bydlo:tequila",
                "§7Котёл · кактус · выдержка в бочке"));
        all.add(CraftRecipe.noteOnly("cider", "Яблочный сидр", "bar", "bydlo:cider",
                "§7Котёл · яблоки · выдержка"));
        all.add(CraftRecipe.noteOnly("whiskey", "Виски", "bar", "bydlo:whiskey",
                "§7Котёл · /варка · бочка меняет силу"));
        all.add(CraftRecipe.noteOnly("absinthe", "Абсент", "bar", "bydlo:absinthe",
                "§7Котёл · трава · много дистилляций"));
        all.add(CraftRecipe.noteOnly("baltika", "Балтика 9", "bar", "bydlo:baltika9",
                "§7Котёл · /варка"));

        // Misc
        all.add(CraftRecipe.shaped("cardboard_box", "Картонная коробка", "misc", "bydlo:cardboard_box",
                "minecraft:paper", "minecraft:paper", "minecraft:paper",
                "minecraft:paper", null, "minecraft:paper",
                null, null, null));
        all.add(CraftRecipe.shaped("paper_bag", "Бумажный пакет", "misc", "bydlo:paper_bag",
                "minecraft:paper", null, "minecraft:paper",
                "minecraft:paper", null, "minecraft:paper",
                "minecraft:paper", null, "minecraft:paper"));
        all.add(CraftRecipe.shaped("cardboard_vest", "Картонный жилет", "misc", "bydlo:cardboard_vest",
                "minecraft:paper", null, "minecraft:paper",
                "minecraft:paper", "minecraft:paper", "minecraft:paper",
                "minecraft:paper", "minecraft:paper", "minecraft:paper"));
        all.add(CraftRecipe.shaped("trash_pants", "Штаны из мешка", "misc", "bydlo:trash_pants",
                "minecraft:leather", "minecraft:leather", "minecraft:leather",
                "minecraft:paper", null, "minecraft:paper",
                "minecraft:paper", null, "minecraft:paper"));
        all.add(CraftRecipe.shaped("valenki", "Валенки", "misc", "bydlo:valenki",
                "minecraft:white_wool", null, "minecraft:white_wool",
                "minecraft:leather", null, "minecraft:leather",
                null, null, null));
        all.add(CraftRecipe.shaped("nail_bat", "Бита с гвоздями", "misc", "bydlo:nail_bat",
                null, "minecraft:iron_nugget", null,
                null, "minecraft:stick", null,
                null, "minecraft:stick", null));
        all.add(CraftRecipe.shaped("basement_key", "Ключ от подвала", "misc", "bydlo:basement_key",
                null, "minecraft:iron_nugget", null,
                null, "minecraft:gold_nugget", null,
                null, S, null));
        all.add(CraftRecipe.shaped("light_remote", "Пульт от света", "misc", "bydlo:light_remote",
                null, "minecraft:redstone", null,
                "minecraft:redstone", "minecraft:redstone_lamp", "minecraft:redstone",
                null, "minecraft:redstone", null));
        all.add(CraftRecipe.noteOnly("lighter", "Зажигалка", "misc", "bydlo:lighter",
                "§7Огниво + любой краситель → цветная зажигалка"));
        all.add(CraftRecipe.noteOnly("joint", "Касяк", "misc", "bydlo:joint",
                "§7Шифт+ПКМ: травка + бумага в инвентаре"));
        all.add(CraftRecipe.noteOnly("cigarette", "Сигарета", "misc", "bydlo:cigarette",
                "§7Шифт+ПКМ: табак + бумага"));
        all.add(CraftRecipe.noteOnly("opium_joint", "Опиумный касяк", "misc", "bydlo:opium_joint",
                "§7Шифт+ПКМ: опиум + бумага"));
        all.add(CraftRecipe.noteOnly("opium_pipe", "Трубка", "misc", "bydlo:opium_pipe",
                "§7Смотри /нарко · курить с зажигалкой во 2-й руке"));
        all.add(CraftRecipe.shaped("rod_oak", "Дубовая удочка", "misc", "bydlo:rod_oak",
                null, null, S,
                null, S, "minecraft:string",
                S, null, "minecraft:string"));
        all.add(CraftRecipe.shaped("rod_steel", "Стальная удочка", "misc", "bydlo:rod_steel",
                null, null, "minecraft:iron_ingot",
                null, "bydlo:rod_oak", "minecraft:iron_ingot",
                "bydlo:rod_oak", null, null));
        all.add(CraftRecipe.shaped("rod_nether", "Адская удочка", "misc", "bydlo:rod_nether",
                null, null, "minecraft:blaze_rod",
                null, "bydlo:rod_steel", "minecraft:magma_cream",
                "bydlo:rod_steel", null, null));
        all.add(CraftRecipe.shaped("rod_abyss", "Удочка бездны", "misc", "bydlo:rod_abyss",
                null, null, "minecraft:ender_pearl",
                null, "bydlo:rod_nether", "bydlo:mithril_ingot",
                "bydlo:rod_nether", null, null));

        Map<String, List<CraftRecipe>> byCat = new LinkedHashMap<>();
        Map<String, CraftRecipe> byId = new LinkedHashMap<>();
        for (Category c : cats) byCat.put(c.id(), new ArrayList<>());
        for (CraftRecipe r : all) {
            byCat.computeIfAbsent(r.categoryId(), k -> new ArrayList<>()).add(r);
            byId.put(r.id(), r);
        }
        return new CraftCatalog(cats, byCat, byId);
    }
}
