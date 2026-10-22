package com.wooze.rocomerchant

import android.content.Context
import android.content.SharedPreferences

/**
 * 订阅提醒（纯本地：把拉取到的商品列表挨个与订阅项比对，命中即提醒）。
 * - 总分类订阅：cat:<分类名>
 * - 细分订阅：item:<商品名>
 */
object Subscriptions {
    private const val PREFS = "subs_prefs"
    private const val CATS = "cats"
    private const val ITEMS = "items"
    private const val SEEN = "seen"
    private const val LAST_NOTIFY = "last_notify_key"

    private fun p(c: Context): SharedPreferences = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun cats(c: Context): Set<String> = p(c).getStringSet(CATS, emptySet()) ?: emptySet()
    fun items(c: Context): Set<String> = p(c).getStringSet(ITEMS, emptySet()) ?: emptySet()
    fun seen(c: Context): Set<String> = p(c).getStringSet(SEEN, emptySet()) ?: emptySet()

    fun setCat(c: Context, cat: String, on: Boolean) {
        val s = cats(c).toMutableSet(); if (on) s.add(cat) else s.remove(cat)
        p(c).edit().putStringSet(CATS, s).apply()
    }

    fun setItem(c: Context, item: String, on: Boolean) {
        val s = items(c).toMutableSet(); if (on) s.add(item) else s.remove(item)
        p(c).edit().putStringSet(ITEMS, s).apply()
    }

    /** 批量勾选/取消细分商品（用于「整个分类」联动）。 */
    fun setItems(c: Context, names: Collection<String>, on: Boolean) {
        if (names.isEmpty()) return
        val s = items(c).toMutableSet()
        if (on) s.addAll(names) else s.removeAll(names.toSet())
        p(c).edit().putStringSet(ITEMS, s).apply()
    }

    /** 批量勾选/取消总分类。 */
    fun setCats(c: Context, cats: Collection<String>, on: Boolean) {
        if (cats.isEmpty()) return
        val s = cats(c).toMutableSet()
        if (on) s.addAll(cats) else s.removeAll(cats.toSet())
        p(c).edit().putStringSet(CATS, s).apply()
    }

    /** 清空全部订阅。 */
    fun clearAll(c: Context) {
        p(c).edit().putStringSet(CATS, emptySet()).putStringSet(ITEMS, emptySet()).apply()
    }

    /** 记住历史出现过的商品，供订阅列表展示。 */
    fun rememberSeen(c: Context, list: List<String>) {
        if (list.isEmpty()) return
        val s = seen(c).toMutableSet(); s.addAll(list)
        p(c).edit().putStringSet(SEEN, s).apply()
    }

    /** 改名时迁移「历史见过」记录。 */
    fun renameSeen(c: Context, old: String, new: String) {
        val s = seen(c).toMutableSet()
        if (s.remove(old)) { s.add(new); p(c).edit().putStringSet(SEEN, s).apply() }
    }

    /** 比对当前在售列表与订阅，返回命中的分类集合。 */
    fun match(c: Context, list: List<String>): Set<String> {
        val cats = cats(c); val items = items(c)
        val hit = linkedSetOf<String>()
        for (raw in list) {
            val n = ItemPool.canonical(raw)
            val cat = Categories.of(c, n)
            if (items.contains(n) || cats.contains(cat)) hit.add(cat)
        }
        return hit
    }

    /**
     * 命中且本刷新点尚未通知过时，发一条通知。
     * @param pointKey 刷新点唯一键（如 "2026-09-22-20"；手动刷新传 "manual:<时间戳>"）
     */
    fun checkAndNotify(c: Context, list: List<String>, pointKey: String): Set<String> {
        val hit = match(c, list)
        if (hit.isEmpty()) return emptySet()
        if (p(c).getString(LAST_NOTIFY, "") == pointKey) return hit
        p(c).edit().putString(LAST_NOTIFY, pointKey).apply()
        NotificationHelper.notifySubscribed(c, hit, list)
        return hit
    }
}
