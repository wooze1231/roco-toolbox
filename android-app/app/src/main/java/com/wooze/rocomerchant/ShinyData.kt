package com.wooze.rocomerchant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 异色记录数据（本地文件保存，不是 SharedPreferences、也不是 cache 目录）。
 *
 * 存储位置：`filesDir/shiny/shiny_data.json` —— filesDir 属于应用私有数据，
 * 系统「清理缓存」不会把它删掉；只有卸载 App 或用户主动删除才会消失。
 * 另外支持用系统文件选择器导出 / 导入 JSON。
 *
 * 结构：
 * { "version":1, "seasons":[ { "name":"S4", "groups":[ { "name":"赤月", "items":[
 *     {"n":"名字","no":编号,"s":"slug","i":"/img/xx.webp","own":true,"note":""} ] } ] } ] }
 */
object ShinyData {

    class Entry(
        var name: String,
        var no: Int = 0,
        var slug: String = "",
        var icon: String = "",
        /** 拥有的异色只数：0 = 没有，1 = 一只，2 = 两只…… */
        var count: Int = 0,
        var note: String = "",
        /** 关联蛋数：与精灵只数独立计数，本地留存（JSON 字段 "e"）。 */
        var eggCount: Int = 0
    ) {
        /** 兼容旧逻辑：只看有没有。 */
        val owned: Boolean get() = count > 0
    }

    class Group(var name: String, val items: MutableList<Entry> = mutableListOf())
    class Season(var name: String, val groups: MutableList<Group> = mutableListOf())
    class Root(val seasons: MutableList<Season> = mutableListOf())

    private const val FILE = "shiny_data.json"

    private fun dir(c: Context): File = File(c.filesDir, "shiny").apply { mkdirs() }
    fun file(c: Context): File = File(dir(c), FILE)

    fun load(c: Context): Root {
        val f = file(c)
        if (!f.exists()) {
            val d = default(c)
            save(c, d)
            return d
        }
        return try {
            fromJson(JSONObject(f.readText()))
        } catch (e: Exception) {
            e.printStackTrace()
            default(c)
        }
    }

    fun save(c: Context, root: Root) {
        try {
            file(c).writeText(toJson(root).toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** 导出用的文本（同 save 的内容）。withQuantity=false 时不写入只数/蛋数。 */
    fun toText(root: Root, withQuantity: Boolean = true): String = toJson(root, withQuantity).toString(2)

    /** 导入：解析失败返回 null，由调用方提示原因。 */
    fun fromText(text: String): Root? = try {
        val o = JSONObject(text)
        if (!o.has("seasons")) null else fromJson(o)
    } catch (e: Exception) {
        null
    }

    private fun toJson(root: Root, withQuantity: Boolean = true): JSONObject {
        val seasons = JSONArray()
        for (s in root.seasons) {
            val groups = JSONArray()
            for (g in s.groups) {
                val items = JSONArray()
                for (it in g.items) {
                    items.put(JSONObject().apply {
                        put("n", it.name)
                        put("no", it.no)
                        put("s", it.slug)
                        put("i", it.icon)
                        if (withQuantity) {
                            put("c", it.count)
                            put("e", it.eggCount)
                        }
                        if (it.note.isNotBlank()) put("note", it.note)
                    })
                }
                groups.put(JSONObject().apply {
                    put("name", g.name)
                    put("items", items)
                })
            }
            seasons.put(JSONObject().apply {
                put("name", s.name)
                put("groups", groups)
            })
        }
        return JSONObject().apply {
            put("version", 1)
            put("seasons", seasons)
        }
    }

    private fun fromJson(o: JSONObject): Root {
        val root = Root()
        val ss = o.optJSONArray("seasons") ?: return root
        for (i in 0 until ss.length()) {
            val so = ss.getJSONObject(i)
            val season = Season(so.optString("name", "未命名赛季"))
            val gs = so.optJSONArray("groups") ?: JSONArray()
            for (j in 0 until gs.length()) {
                val go = gs.getJSONObject(j)
                val group = Group(go.optString("name", ""))
                val items = go.optJSONArray("items") ?: JSONArray()
                for (k in 0 until items.length()) {
                    val io = items.getJSONObject(k)
                    group.items.add(
                        Entry(
                            name = io.optString("n", ""),
                            no = io.optInt("no", 0),
                            slug = io.optString("s", ""),
                            icon = io.optString("i", ""),
                            // 兼容老数据：own:true 视为 1 只
                            count = io.optInt("c", if (io.optBoolean("own", false)) 1 else 0),
                            note = io.optString("note", ""),
                            eggCount = io.optInt("e", 0)
                        )
                    )
                }
                season.groups.add(group)
            }
            // 兼容没有分组的老数据：整季条目塞进「未分类」
            if (season.groups.isEmpty()) {
                val g = Group("")
                val items = so.optJSONArray("items") ?: JSONArray()
                for (k in 0 until items.length()) {
                    val io = items.getJSONObject(k)
                    g.items.add(
                        Entry(
                            io.optString("n", ""), io.optInt("no", 0),
                            io.optString("s", ""), io.optString("i", ""),
                            io.optInt("c", if (io.optBoolean("own", false)) 1 else 0),
                            io.optString("note", ""),
                            io.optInt("e", 0)
                        )
                    )
                }
                season.groups.add(g)
            }
            root.seasons.add(season)
        }
        return root
    }

    /** 首次使用时内置的 S4 赛季数据（assets/shiny_default.json）。 */
    private fun default(c: Context): Root {
        return try {
            fromJson(JSONObject(c.assets.open("shiny_default.json").bufferedReader().readText()))
        } catch (e: Exception) {
            e.printStackTrace()
            Root(mutableListOf(Season("S4", mutableListOf(Group("未分类")))))
        }
    }

    fun ensureSeason(root: Root, name: String): Season {
        return root.seasons.firstOrNull { it.name == name } ?: Season(name).also { root.seasons.add(it) }
    }

    fun ensureGroup(season: Season, name: String): Group {
        return season.groups.firstOrNull { it.name == name } ?: Group(name).also { season.groups.add(it) }
    }

    // —— 备份（导入前自动保存，可从「还原备份」找回） ——

    /** 备份文件路径：与正式数据同目录，文件名带 _backup。 */
    fun backupFile(c: Context): File = File(dir(c), "shiny_data_backup.json")

    fun hasBackup(c: Context): Boolean = backupFile(c).exists()

    /** 把当前数据写一份备份（不抛异常，失败静默）。 */
    fun saveBackup(c: Context, root: Root) {
        try { backupFile(c).writeText(toJson(root).toString()) } catch (e: Exception) { e.printStackTrace() }
    }

    /** 读出备份；没有或损坏返回 null。 */
    fun loadBackup(c: Context): Root? {
        val f = backupFile(c)
        if (!f.exists()) return null
        return try { fromJson(JSONObject(f.readText())) } catch (e: Exception) { null }
    }

    /** 统计：已获得的「种类数」/ 总种类数。 */
    fun progress(root: Root, season: Season? = null): Pair<Int, Int> {
        var got = 0
        var total = 0
        val list = if (season != null) listOf(season) else root.seasons
        for (s in list) for (g in s.groups) for (it in g.items) {
            total++
            if (it.owned) got++
        }
        return got to total
    }

    /** 统计：拥有的异色「总只数」（同一种有多只时累加）。 */
    fun pieces(root: Root, season: Season? = null): Int {
        val list = if (season != null) listOf(season) else root.seasons
        var sum = 0
        for (s in list) for (g in s.groups) for (it in g.items) sum += it.count
        return sum
    }

    /** 某个分组内的总只数。 */
    fun piecesOf(group: Group): Int = group.items.sumOf { it.count }
}
