package com.wooze.rocomerchant

import android.content.Context

/**
 * 商品目录：把「内置商品池」和「用户自己的增删改」合并成最终生效的目录。
 *
 * 用户可编辑的三类数据（都存本地 SharedPreferences）：
 *   - hidden    ：从列表里删掉的商品名（内置池里有，但用户认为不该出现 / 有问题）
 *   - custom    ：用户自己新增的商品（名称 -> 分类）
 *   - catOverride：用户改过的分类归属（名称 -> 分类），优先级最高
 *
 * 生效规则：用户改过 > 内置池 > 关键字兜底。
 */
object ItemCatalog {

    private const val PREFS = "item_catalog"
    private const val HIDDEN = "hidden"
    private const val CUSTOM = "custom"      // 每项 "名称\u0001分类"
    private const val CAT_OVERRIDE = "cat"   // 每项 "名称\u0001分类"

    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // —— 读取 ——

    fun hidden(c: Context): Set<String> = p(c).getStringSet(HIDDEN, emptySet()) ?: emptySet()

    fun customMap(c: Context): Map<String, String> =
        (p(c).getStringSet(CUSTOM, emptySet()) ?: emptySet())
            .mapNotNull { line ->
                val a = line.split("\u0001")
                if (a.size >= 2) a[0] to a[1] else null
            }.toMap()

    fun catOverrides(c: Context): Map<String, String> =
        (p(c).getStringSet(CAT_OVERRIDE, emptySet()) ?: emptySet())
            .mapNotNull { line ->
                val a = line.split("\u0001")
                if (a.size >= 2) a[0] to a[1] else null
            }.toMap()

    // —— 写入 ——

    /** 删除一个商品：自定义的移除条目，内置的记入隐藏名单。 */
    fun remove(c: Context, name: String) {
        val custom = customMap(c)
        if (custom.containsKey(name)) {
            val s = custom.filterKeys { it != name }.map { it.key + "\u0001" + it.value }.toSet()
            p(c).edit().putStringSet(CUSTOM, s).apply()
        } else {
            val h = hidden(c).toMutableSet(); h.add(name)
            p(c).edit().putStringSet(HIDDEN, h).apply()
        }
        // 同时清掉分类改写与订阅
        clearCatOverride(c, name)
        Subscriptions.setItem(c, name, false)
    }

    /** 新增自定义商品。 */
    fun addCustom(c: Context, name: String, cat: String) {
        val s = customMap(c).toMutableMap()
        s[name] = cat
        p(c).edit()
            .putStringSet(CUSTOM, s.map { it.key + "\u0001" + it.value }.toSet())
            .putStringSet(HIDDEN, hidden(c) - name)
            .apply()
    }

    /** 修改某商品的分类归属（内置/自定义都可）。 */
    fun setCatOverride(c: Context, name: String, cat: String?) {
        val m = catOverrides(c).toMutableMap()
        if (cat == null) m.remove(name) else m[name] = cat
        p(c).edit().putStringSet(CAT_OVERRIDE, m.map { it.key + "\u0001" + it.value }.toSet()).apply()
        // 自定义商品同步改自身分类
        val cm = customMap(c)
        if (cm.containsKey(name) && cat != null) addCustom(c, name, cat)
    }

    private fun clearCatOverride(c: Context, name: String) {
        val m = catOverrides(c).toMutableMap()
        if (m.remove(name) != null) {
            p(c).edit().putStringSet(CAT_OVERRIDE, m.map { it.key + "\u0001" + it.value }.toSet()).apply()
        }
    }

    /** 恢复一个被删掉的商品。 */
    fun restore(c: Context, name: String) {
        p(c).edit().putStringSet(HIDDEN, hidden(c) - name).apply()
    }

    /**
     * 改名：把 [old] 改成 [new]（分类沿用原来的）。
     *
     * 内置商品改名 = 「隐藏原名 + 新增自定义名」，这样池子里的错名能就地纠错，
     * 也不影响别的内置项。自定义商品改名就是搬个 key。
     * 订阅勾选、分类改写、历史见过记录一并迁移到新名。
     */
    fun rename(c: Context, old: String, new: String): Boolean {
        val to = new.trim()
        if (to.isEmpty() || to == old) return false
        // 目标名已存在就别覆盖（避免两项打架）
        if (allNames(c).contains(to)) return false

        val cat = category(c, old)
        val wasCustom = customMap(c).containsKey(old)

        if (wasCustom) {
            val cm = customMap(c).toMutableMap()
            cm.remove(old); cm[to] = cat
            p(c).edit()
                .putStringSet(CUSTOM, cm.map { it.key + "\u0001" + it.value }.toSet())
                .apply()
        } else {
            // 内置：藏旧名 + 记新名（带分类）
            val h = hidden(c).toMutableSet(); h.add(old)
            p(c).edit().putStringSet(HIDDEN, h).apply()
            addCustom(c, to, cat)
        }

        // 迁移：分类改写
        catOverrides(c)[old]?.let { vo ->
            val m = catOverrides(c).toMutableMap()
            m.remove(old); m[to] = vo
            p(c).edit().putStringSet(CAT_OVERRIDE, m.map { it.key + "\u0001" + it.value }.toSet()).apply()
        }
        // 迁移：订阅勾选
        if (Subscriptions.items(c).contains(old)) {
            Subscriptions.setItem(c, old, false)
            Subscriptions.setItem(c, to, true)
        }
        // 迁移：历史见过（否则新名不会出现在订阅页候选里）
        Subscriptions.renameSeen(c, old, to)
        return true
    }

    /** 一键恢复默认（清空所有用户改动）。 */
    fun reset(c: Context) {
        p(c).edit().clear().apply()
    }

    // —— 生效目录 ——

    /** 最终商品名单：内置池（去掉被删的）+ 自定义新增。 */
    fun allNames(c: Context): List<String> {
        val hidden = hidden(c)
        val custom = customMap(c)
        return LinkedHashSet<String>().apply {
            addAll(ItemPool.ALL_NAMES.filter { it !in hidden })
            addAll(custom.keys)
        }.toList()
    }

    /** 最终分类：用户改写 > 自定义 > 内置池 > 关键字兜底。 */
    fun category(c: Context, name: String): String =
        catOverrides(c)[name]
            ?: customMap(c)[name]
            ?: Categories.of(name)

    /** 批量取分类（一次读盘，供列表页循环使用，避免每项重复读 SharedPreferences）。 */
    fun categoryMap(c: Context, names: Collection<String>): Map<String, String> {
        val ov = catOverrides(c)
        val cu = customMap(c)
        return names.associateWith { ov[it] ?: cu[it] ?: Categories.of(it) }
    }
}
