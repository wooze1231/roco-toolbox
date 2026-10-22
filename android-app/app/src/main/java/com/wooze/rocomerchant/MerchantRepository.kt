package com.wooze.rocomerchant

import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * 洛克助手实时商品数据源。
 * 对接「罗科工具箱」公开静态 JSON（即各微信小程序背后使用的同一数据源）。
 * OSS 有 Referer 防盗链，需带微信 UA + Referer 头。
 */
object MerchantRepository {
    private const val TAG = "MerchantRepo"
    private const val OSS_URL =
        "https://roco-toolkit-img.oss-cn-shenzhen.aliyuncs.com/merchant_data.json"
    private const val REFERRER =
        "https://servicewechat.com/wx38b0bed275763e1b/91/page-frame.html"
    private const val UA =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 MicroMessenger/8.0.20"

    const val sourceLabel = "罗科工具箱"

    data class Merchant(
        val items: List<String>,
        val settledHour: Int,   // 服务端当前轮起始小时（新数据源常返回 0，仅作兼容）
        val updateTime: Long,   // 服务端数据最后更新时间（epoch ms），用于判定当前轮次
        val source: String
    )

    fun fetch(): Merchant? {
        return try {
            val url = URL("$OSS_URL?t=${System.currentTimeMillis()}")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Referer", REFERRER)
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            val code = conn.responseCode
            if (code != 200) {
                conn.disconnect()
                Log.w(TAG, "HTTP $code")
                return null
            }
            val text = conn.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
            conn.disconnect()
            parse(text)
        } catch (e: Exception) {
            Log.e(TAG, "fetch failed: ${e.message}")
            null
        }
    }

    private fun parse(json: String): Merchant? {
        return try {
            val obj = JSONObject(json)
            val items = mutableListOf<String>()
            obj.optJSONArray("items")?.let { arr ->
                for (i in 0 until arr.length()) items.add(arr.getString(i))
            }
            val settledHour = obj.optInt("settledHour", 8)
            val updateTime = obj.optLong("updateTime", 0L)
            Merchant(items, settledHour, updateTime, "roco-toolkit")
        } catch (e: Exception) {
            Log.e(TAG, "parse failed: ${e.message}")
            null
        }
    }
}
