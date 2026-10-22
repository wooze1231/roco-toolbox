package com.wooze.rocomerchant

import android.content.Context

/**
 * 商品分类器（纯本地关键字匹配，无网络）。
 *
 * 判定优先级：
 *   1. 商品池精确命中（[ItemPool.exactCategory]）——池内商品的归属是权威的
 *   2. 商品池模糊命中（[ItemPool.fuzzyCategory]）——处理「紫色矿石100」这类带数量后缀的
 *   3. 关键字兜底（[fallback]）——处理池外的新道具
 *
 * 修复记录：黄石榴石/神奇的蛋 曾因关键字缺失被误分到「道具材料类」。
 */
object Categories {

    val ALL: List<String> = ItemPool.GROUPS.map { it.first }

    /** 带 Context 的版本：会先看用户在「编辑商品池」里改过的归属。 */
    fun of(c: Context, name: String): String = ItemCatalog.category(c, name)

    /** 明确不是矿石的含「石」名称，避免关键字兜底误伤。 */
    private val NON_GEM = setOf("石耳", "石蒜", "石莲")

    fun of(name: String): String {
        val n = ItemPool.canonical(name)
        ItemPool.exactCategory(n)?.let { return it }
        ItemPool.fuzzyCategory(n)?.let { return it }
        return fallback(n)
    }

    private fun fallback(name: String): String {
        if ("球" in name) return "精灵球类"
        if ("粉尘" in name || "魔尘" in name) return "粉尘类"
        if ("秘药" in name || "密钥" in name || "血脉" in name || "钥匙" in name) return "血脉秘药类"
        if ("经验果" in name || "果" in name) return "经验果类"
        if ("蛋" in name || "精灵" in name) return "蛋与精灵类"
        // 矿石宝石：显式关键字
        val gemKeys = listOf(
            "矿", "碧玺", "刚玉", "石榴石", "水晶", "宝石", "琉璃", "黑金",
            "玛瑙", "琥珀", "翡翠", "玉", "砂"
        )
        if (gemKeys.any { it in name }) return "矿石宝石类"
        // 以「石 / 矿」结尾且不属于排除名单
        if ((name.endsWith("石") || name.endsWith("矿") || name.endsWith("石矿")) && name !in NON_GEM)
            return "矿石宝石类"
        return "道具材料类"
    }
}
