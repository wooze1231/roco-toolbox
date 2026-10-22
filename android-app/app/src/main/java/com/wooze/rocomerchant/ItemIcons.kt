package com.wooze.rocomerchant

import android.content.Context

/**
 * 洛克助手商品名 -> 真实游戏图标映射（Activity 与桌面组件共用）。
 * 图标为 patchwiki / 图鉴站原图，构建时内置。
 */
object ItemIcons {
    val MAP: Map<String, Int> = mapOf(
        "蓝晶碧玺" to R.drawable.item_0,
        "魔力果" to R.drawable.item_1,
        "普通咕噜球" to R.drawable.item_2,
        "炫彩精灵蛋" to R.drawable.item_3,
        "紫莲刚玉" to R.drawable.item_4,
        "神奇的蛋" to R.drawable.item_5,
        "天使草" to R.drawable.item_6,
        "美妙球" to R.drawable.item_7,
        "石耳" to R.drawable.item_8,
        "喷气菇" to R.drawable.item_9,
        "星霜花" to R.drawable.item_10,
        "光合球" to R.drawable.item_11,
        "地系血脉秘药" to R.drawable.item_12,
        "火系粉尘" to R.drawable.item_13,
        "首领血脉秘药" to R.drawable.item_14,
        "火系血脉秘药" to R.drawable.item_15,
        "毒系粉尘" to R.drawable.item_16,
        "龙系粉尘" to R.drawable.item_17,
        "机械粉尘" to R.drawable.item_18,
        "翼系粉尘" to R.drawable.item_19,
        "调温球" to R.drawable.item_20,
        "萌系粉尘" to R.drawable.item_21,
        "国王球" to R.drawable.item_22,
        "高级咕噜球" to R.drawable.item_23,
        "草系粉尘" to R.drawable.item_24,
        "草系血脉秘药" to R.drawable.item_25,
        "炫彩蛋" to R.drawable.item_26,
        // —— 第二批（补抓：新版本商品 / 当前在售）——
        "黄石榴石" to R.drawable.item_27,
        "黑晶琉璃" to R.drawable.item_28,
        "网兜球" to R.drawable.item_29,
        "棱镜球" to R.drawable.item_30,
        "变幻球" to R.drawable.item_31,
        "暗星球" to R.drawable.item_32,
        "绝缘球" to R.drawable.item_33,
        "适格钥匙" to R.drawable.item_34,
        "光系粉尘" to R.drawable.item_35,
        "幽系粉尘" to R.drawable.item_36,
        "地系粉尘" to R.drawable.item_37,
        "万能血脉秘药" to R.drawable.item_38,
        "残缺魔镜" to R.drawable.item_39,
        "武系粉尘" to R.drawable.item_40,
        "恶系粉尘" to R.drawable.item_41,
        "魔法粉尘" to R.drawable.item_42,
        "奇异血脉秘药" to R.drawable.item_43,
        "能力钥匙" to R.drawable.item_44,
        "电系粉尘" to R.drawable.item_45,
        // —— 第三批（单张补录）——
        "祝福项坠" to R.drawable.item_46
    )

    /** 精确匹配 -> 模糊兜底（处理「紫色矿石100」这类带数量后缀）-> 占位图 */
    fun res(name: String): Int {
        MAP[name]?.let { return it }
        for ((k, v) in MAP) {
            if (name.contains(k) || k.contains(name)) return v
        }
        return R.drawable.widget_icon_placeholder
    }
}
