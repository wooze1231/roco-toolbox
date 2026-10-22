package com.wooze.rocomerchant

/**
 * 洛克助手商品池（离线内置）。
 *
 * 来源：官方版本更新公告 + 攻略站整理 + 历史售卖记录。目的：
 *   ① 订阅列表尽量列全（不再只有内置图标那 27 个 + 见过的）；
 *   ② 商品分类以池内归属为准，避免关键字误判（如「黄石榴石」被当成道具）。
 *
 * 游戏会持续新增道具：池外的商品走 [Categories] 的关键字兜底，
 * 并会被自动记入「历史见过」列表，下次打开订阅页就会出现。
 */
object ItemPool {

    /** 精灵球类 */
    val BALLS = listOf(
        "普通咕噜球", "高级咕噜球", "国王球",
        "光合球", "调温球", "美妙球",
        "变幻球", "棱镜球", "绝缘球",
        "网兜球", "暗星球",
        "好战球", "淘沙球", "可可果球",
    )

    /** 矿石宝石类 */
    val GEMS = listOf(
        "蓝晶碧玺", "紫莲刚玉", "黑晶琉璃",
        "黄石榴石",
    )

    /** 血脉秘药类 */
    val BLOODS = listOf(
        "首领血脉秘药", "奇异血脉秘药", "万能血脉秘药",
        "地系血脉秘药", "水系血脉秘药", "火系血脉秘药",
        "草系血脉秘药", "幽系血脉秘药", "电系血脉秘药",
        "普通血脉秘药",
        "光系血脉秘药", "冰系血脉秘药", "龙系血脉秘药",
        "毒系血脉秘药", "虫系血脉秘药", "武系血脉秘药",
        "翼系血脉秘药", "萌系血脉秘药", "恶系血脉秘药",
        "机械血脉秘药", "幻系血脉秘药",
    )

    /** 粉尘类 */
    val DUSTS = listOf(
        "萌系粉尘", "机械粉尘", "龙系粉尘",
        "火系粉尘", "草系粉尘", "翼系粉尘",
        "毒系粉尘", "地系粉尘", "光系粉尘",
        "幽系粉尘", "恶系粉尘", "武系粉尘",
        "电系粉尘", "魔法粉尘", "水系粉尘",
        "普通粉尘", "冰系粉尘", "虫系粉尘",
        "幻系粉尘",
    )

    /** 灵碎类（19 系，与粉尘一一对应） */
    val LINGSUI = listOf(
        "草系灵碎", "火系灵碎", "水系灵碎",
        "光系灵碎", "恶系灵碎", "幽系灵碎",
        "普通灵碎", "地系灵碎", "冰系灵碎",
        "龙系灵碎", "电系灵碎", "毒系灵碎",
        "虫系灵碎", "武系灵碎", "翼系灵碎",
        "萌系灵碎", "机械灵碎", "幻系灵碎",
        "愿力灵碎",
    )

    /** 蛋与精灵类 */
    val EGGS = listOf(
        "炫彩蛋", "神奇的蛋",
    )

    /** 经验果类 */
    val FRUITS = listOf(
        "魔力果", "可可果", "无花果",
    )

    /** 道具材料类 */
    val MATS = listOf(
        "残缺魔镜", "适格钥匙", "能力钥匙", "祝福项坠",
    )

    /** 分类名 -> 该类商品，顺序即订阅页展示顺序。 */
    val GROUPS: List<Pair<String, List<String>>> = listOf(
        "精灵球类" to BALLS,
        "矿石宝石类" to GEMS,
        "血脉秘药类" to BLOODS,
        "粉尘类" to DUSTS,
        "灵碎类" to LINGSUI,
        "蛋与精灵类" to EGGS,
        "经验果类" to FRUITS,
        "道具材料类" to MATS,
    )

    /** 池内全部商品名。 */
    val ALL_NAMES: List<String> = GROUPS.flatMap { it.second }

    private val EXACT: Map<String, String> =
        GROUPS.flatMap { (cat, names) -> names.map { it to cat } }.toMap()

    /**
     * 商品别名 → 统一名。远行商人/其它数据源叫法不同时，一律归一到同一个名字。
     * 「炫彩精灵蛋」和「炫彩蛋」是同一样东西，固定叫「炫彩蛋」。
     */
    private val ITEM_ALIAS = mapOf(
        "炫彩精灵蛋" to "炫彩蛋",
    )

    /** 归一化商品名（去首尾空白 + 别名统一）。所有商品名比对前都应过一遍。 */
    fun canonical(name: String): String {
        val t = name.trim()
        return ITEM_ALIAS[t] ?: t
    }

    /** 池内精确命中（忽略数量后缀等噪声）。 */
    fun exactCategory(name: String): String? {
        val n = canonical(name)
        EXACT[n]?.let { return it }
        // 去尾部数字/单位，如「紫色矿石100」
        val stripped = name.replace(Regex("[\\d．.×xX*]+$"), "").trim()
        if (stripped != name) EXACT[stripped]?.let { return it }
        return null
    }

    /** 池内模糊命中：商品名包含池内某名（或反之），取最长匹配，避免歧义。 */
    fun fuzzyCategory(name: String): String? {
        var best: String? = null
        var bestLen = 0
        for ((pool, cat) in EXACT) {
            if (name.contains(pool) || (pool.length >= 3 && pool.contains(name))) {
                if (pool.length > bestLen) { bestLen = pool.length; best = cat }
            }
        }
        return best
    }
}
