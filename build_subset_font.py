"""
纯 Python 的 TTF 子集化：从 simhei.ttf 中提取字符，生成 ocr_subset.ttf。

为什么这么做：旧字体只有 7 张表（缺 OS/2、name、post），Android 的
Typeface.createFromAsset() 加载后渲染出来是 .notdef 空心框，
导致 OCR 的 top1 命中率长期为 0。

字表构成（三档权重的字符来源，共 3500+ 字）：
  1) 图鉴字　—— shiny_catalog.json 里所有精灵名出现的字（含生僻字，**必须全保留**）
     实测有 82 个字不在《通用规范汉字表》一级字表内，如 霹雳喵嘟噬鼬鳗 等
  2) 常用字　——《通用规范汉字表》一级字表 3500 字
  3) 数字 + 少量必要符号
  取并集，保证「认得出图鉴精灵名」+「遇到新精灵也有字可用」。

做法：
1. 解析源字体表目录；
2. 解析 cmap（format 4）拿到 字符 -> glyphIndex 映射；
3. 用 maxp/hmtx/loca/glyf 抽出需要的字形；
4. 重新拼 loca/glyf（保持 4 字节对齐）；
5. 写入完整表集：cmap/glyf/head/hhea/hmtx/loca/maxp + OS/2/name/post。
"""
import struct
import json
import sys
import os

SRC = r"C:\Windows\Fonts\simhei.ttf"
BASE = r"C:\Users\wooze\WorkBuddy\2026-09-22-16-31-24\roco-merchant-reminder"
CATALOG = os.path.join(BASE, "android-app", "app", "src", "main", "assets", "shiny_catalog.json")
LEVEL1 = os.path.join(BASE, "tmp", "level1_3500.txt")
OUT = os.path.join(BASE, "android-app", "app", "src", "main", "assets", "fonts", "ocr_subset.ttf")
# 导出字符分层，供 Kotlin 侧做三档权重
WEIGHTS_OUT = os.path.join(BASE, "android-app", "app", "src", "main", "assets", "fonts", "ocr_char_tiers.json")


def u16(b, o):
    return struct.unpack(">H", b[o:o + 2])[0]


def s16(b, o):
    return struct.unpack(">h", b[o:o + 2])[0]


def u32(b, o):
    return struct.unpack(">I", b[o:o + 4])[0]


def read_tables(d):
    num = u16(d, 4)
    tabs = {}
    for i in range(num):
        off = 12 + i * 16
        tag = d[off:off + 4].decode("latin1")
        o, l = struct.unpack(">II", d[off + 8:off + 16])
        tabs[tag] = (o, l)
    return tabs


def parse_cmap(d, o, l):
    """解析 cmap，返回 {codepoint: glyphId}（优先 format 4）。"""
    n = u16(d, o + 2)
    best = None
    for i in range(n):
        pid = u16(d, o + 4 + i * 8)
        eid = u16(d, o + 6 + i * 8)
        sub = u32(d, o + 8 + i * 8)
        if sub == 0:
            continue
        fmt = u16(d, o + sub)
        if fmt == 4:
            # 优先 (3,1) Unicode BMP
            prio = 0 if (pid == 3 and eid == 1) else 1
            if best is None or prio < best[0]:
                best = (prio, o + sub)
    if best is None:
        return {}
    so = best[1]
    segX2 = u16(d, so + 6)
    seg = segX2 // 2
    endO = so + 14
    startO = endO + segX2 + 2
    deltaO = startO + segX2
    rangeO = deltaO + segX2
    m = {}
    for i in range(seg):
        end = u16(d, endO + i * 2)
        start = u16(d, startO + i * 2)
        delta = s16(d, deltaO + i * 2)
        ro = u16(d, rangeO + i * 2)
        if start == 0xFFFF:
            continue
        for c in range(start, end + 1):
            if ro == 0:
                g = (c + delta) & 0xFFFF
            else:
                gi = rangeO + i * 2 + ro + (c - start) * 2
                if gi + 2 > len(d):
                    continue
                g = u16(d, gi)
                if g != 0:
                    g = (g + delta) & 0xFFFF
            if g != 0:
                m[c] = g
    return m


def main():
    d = open(SRC, "rb").read()
    tabs = read_tables(d)
    print("源字体表:", sorted(tabs.keys()))

    # —— 收集需要的字符（分三层，供 Kotlin 做权重）——
    digits = set("0123456789")

    # 第 1 层：图鉴字（最高权重）—— 精灵名出现过的所有字，含生僻字
    cat = json.load(open(CATALOG, encoding="utf-8"))
    tier_dex = set()
    for it in cat:
        for ch in it.get("n", ""):
            tier_dex.add(ch)
    # 别名也要能认
    for it in cat:
        for a in it.get("alias", []) or []:
            for ch in str(a):
                tier_dex.add(ch)

    # 第 2 层：常用字（兜底）—— 通用规范汉字表一级字表 3500
    tier_common = set()
    if os.path.exists(LEVEL1):
        for ch in open(LEVEL1, encoding="utf-8").read():
            if "\u4e00" <= ch <= "\u9fff":
                tier_common.add(ch)
    else:
        print("!! 缺 tmp/level1_3500.txt，常用字兜底层为空")

    # 并集：图鉴字 + 常用字 + 数字
    chars = set(digits)
    chars |= tier_dex
    chars |= tier_common

    # 分层导出（Kotlin 侧用它建权重表）；图鉴字优先级最高，从常用字层里剔除
    tiers = {
        "dex": sorted(c for c in tier_dex if ord(c) > 0x20),
        "common": sorted(c for c in tier_common if c not in tier_dex and ord(c) > 0x20),
        "digits": sorted(digits),
    }
    print("图鉴字层:", len(tiers["dex"]))
    print("常用字层:", len(tiers["common"]))
    print("数字层:", len(tiers["digits"]))
    print("候选字符数:", len(chars))

    cmap = parse_cmap(d, *tabs["cmap"])
    print("源 cmap 条目:", len(cmap))

    # 丢掉源字体里没有字形的字，并同步收敛分层表（避免权重表里出现认不出的字）
    avail = set()
    for c in sorted(chars):
        if ord(c) in cmap:
            avail.add(c)
        else:
            print("  源字体缺字形，跳过:", repr(c))
    chars = avail
    for k in tiers:
        tiers[k] = [c for c in tiers[k] if c in chars]
    print("实际打进字体:", len(chars))

    # —— 建字形映射：保留 glyph 0（.notdef）——
    glyphs = {0: 0}  # oldGid -> newGid
    order = [0]
    new_cmap = {}

    # .notdef 必须存在
    for c in sorted(chars):
        g = cmap.get(ord(c))
        if g is None:
            continue
        if g not in glyphs:
            glyphs[g] = len(order)
            order.append(g)
        new_cmap[ord(c)] = glyphs[g]

    print("打包字形数:", len(order), " cmap 条目:", len(new_cmap))

    # —— 抽取 head/hhea/maxp 基础参数 ——
    ho = tabs["head"][0]
    unitsPerEm = u16(d, ho + 18)
    xMin, yMin, xMax, yMax = struct.unpack(">hhhh", d[ho + 36:ho + 44])

    # hhea
    hho = tabs["hhea"][0]
    ascender = s16(d, hho + 4)
    descender = s16(d, hho + 6)
    lineGap = s16(d, hho + 8)
    numberOfHMetrics = u16(d, hho + 34)

    # loca / glyf
    lo = tabs["loca"][0]
    go = tabs["glyf"][0]
    gl = tabs["glyf"][1]
    ilf = s16(d, ho + 50)  # indexToLocFormat

    ng_src = u16(d, tabs["maxp"][0] + 4)

    def glyph_range(gid):
        if ilf == 0:
            s = u16(d, lo + gid * 2) * 2
            e = u16(d, lo + (gid + 1) * 2) * 2
        else:
            s = u32(d, lo + gid * 4)
            e = u32(d, lo + (gid + 1) * 4)
        return s, e

    # hmtx
    hmo = tabs["hmtx"][0]

    def hmetric(gid):
        if gid < numberOfHMetrics:
            aw = u16(d, hmo + gid * 4)
            lsb = s16(d, hmo + gid * 4 + 2)
        else:
            aw = u16(d, hmo + (numberOfHMetrics - 1) * 4)
            idx = hmo + numberOfHMetrics * 4 + (gid - numberOfHMetrics) * 2
            lsb = s16(d, idx)
        return aw, lsb

    # —— 组装新 glyf + loca ——
    new_glyf = bytearray()
    offsets = []
    hmetrics = []

    for gid in order:
        offsets.append(len(new_glyf))
        s, e = glyph_range(gid)
        raw = d[go + s:go + e] if e > s else b""
        # 4 字节对齐
        pad = (-len(raw)) % 4
        new_glyf += raw + b"\x00" * pad
        hmetrics.append(hmetric(gid))
    offsets.append(len(new_glyf))

    # 用 long 格式（indexToLocFormat=1）最省事
    new_loca = bytearray()
    for o in offsets:
        new_loca += struct.pack(">I", o)

    numGlyphs = len(order)
    maxPoints = 0
    maxContours = 0
    # 大致估算，取保守上限（OCR 模板渲染只关心轮廓，不关心这些域精确值）
    maxPoints = 200
    maxContours = 40
    maxComponentPoints = 200
    maxComponentContours = 40
    maxComponentDepth = 4

    def pad4(b):
        return b + b"\x00" * ((-len(b)) % 4)

    tables = {}

    # head
    head = bytearray(54)
    struct.pack_into(">I", head, 0, 0x00010000)
    struct.pack_into(">I", head, 4, 0x00010000)
    struct.pack_into(">I", head, 8, 0)
    struct.pack_into(">I", head, 12, 0x5F0F3CF5)
    struct.pack_into(">H", head, 16, 0x000B)
    struct.pack_into(">H", head, 18, unitsPerEm)
    struct.pack_into(">q", head, 20, 0)
    struct.pack_into(">q", head, 28, 0)
    struct.pack_into(">hhhh", head, 36, xMin, yMin, xMax, yMax)
    struct.pack_into(">H", head, 44, 0)   # macStyle
    struct.pack_into(">H", head, 46, 8)   # lowestRecPPEM
    struct.pack_into(">h", head, 48, 2)   # fontDirectionHint
    struct.pack_into(">h", head, 50, 1)   # indexToLocFormat = long
    struct.pack_into(">h", head, 52, 0)   # glyphDataFormat
    tables["head"] = bytes(head)

    # hhea
    hhea = bytearray(36)
    struct.pack_into(">I", hhea, 0, 0x00010000)
    struct.pack_into(">h", hhea, 4, ascender)
    struct.pack_into(">h", hhea, 6, descender)
    struct.pack_into(">h", hhea, 8, lineGap)
    struct.pack_into(">H", hhea, 10, max(1000, xMax - xMin))
    struct.pack_into(">h", hhea, 12, xMin)
    struct.pack_into(">h", hhea, 14, 200)
    struct.pack_into(">h", hhea, 16, xMax)
    struct.pack_into(">h", hhea, 18, 100)
    struct.pack_into(">h", hhea, 20, 200)
    struct.pack_into(">h", hhea, 22, 0)
    struct.pack_into(">h", hhea, 24, 0)
    struct.pack_into(">h", hhea, 26, 0)
    struct.pack_into(">h", hhea, 28, 0)
    struct.pack_into(">h", hhea, 30, 0)
    struct.pack_into(">H", hhea, 32, numGlyphs)
    struct.pack_into(">H", hhea, 34, numGlyphs)  # numberOfHMetrics = 全部
    tables["hhea"] = bytes(hhea)

    # maxp
    maxp = bytearray(32)
    struct.pack_into(">I", maxp, 0, 0x00010000)
    struct.pack_into(">H", maxp, 4, numGlyphs)
    struct.pack_into(">H", maxp, 6, maxPoints)
    struct.pack_into(">H", maxp, 8, maxContours)
    struct.pack_into(">H", maxp, 10, maxComponentPoints)
    struct.pack_into(">H", maxp, 12, maxComponentContours)
    struct.pack_into(">H", maxp, 14, 2)   # maxZones
    struct.pack_into(">H", maxp, 16, 0)   # maxTwilightPoints
    struct.pack_into(">H", maxp, 18, 0)   # maxStorage
    struct.pack_into(">H", maxp, 20, 0)   # maxFunctionDefs
    struct.pack_into(">H", maxp, 22, 0)   # maxInstructionDefs
    struct.pack_into(">H", maxp, 24, 0)   # maxStackElements
    struct.pack_into(">H", maxp, 26, 0)   # maxSizeOfInstructions
    struct.pack_into(">H", maxp, 28, maxComponentDepth)
    tables["maxp"] = bytes(maxp)

    # hmtx
    hmtx = bytearray()
    for aw, lsb in hmetrics:
        hmtx += struct.pack(">Hh", aw, lsb)
    tables["hmtx"] = bytes(hmtx)

    tables["loca"] = bytes(new_loca)
    tables["glyf"] = bytes(new_glyf)

    # cmap（format 4, 3/1）
    items = sorted(new_cmap.items())
    segs = []
    i = 0
    while i < len(items):
        start = items[i][0]
        end = start
        j = i
        while j + 1 < len(items) and items[j + 1][0] == end + 1:
            j += 1
            end = items[j][0]
        segs.append((start, end, i))
        i = j + 1
    segs.append((0xFFFF, 0xFFFF, len(items)))

    segCount = len(segs)
    segX2 = segCount * 2
    sub = bytearray()
    sub += struct.pack(">HHHHHHH", 4, 16 + segX2 * 4 + 2, 0, segX2, 0, segCount,
                       segCount ** 2 if False else 0)
    # 上面简化：重写正确头
    sub = bytearray()
    length = 16 + segX2 * 4 + 2
    sub += struct.pack(">HHH", 4, length, 0)
    sub += struct.pack(">HHH", segX2, 0, segCount)
    # endCode[]
    for s, e, _ in segs:
        sub += struct.pack(">H", e)
    sub += struct.pack(">H", 0)  # reservedPad
    for s, e, _ in segs:
        sub += struct.pack(">H", s)
    # idDelta
    for s, e, idx in segs:
        if s == 0xFFFF:
            sub += struct.pack(">H", 1)
        else:
            sub += struct.pack(">H", (items[idx][1] - s) & 0xFFFF)
    # idRangeOffset 全 0（用 idDelta）
    for _ in segs:
        sub += struct.pack(">H", 0)

    cmap = bytearray()
    cmap += struct.pack(">HH", 0, 1)          # version, numTables
    cmap += struct.pack(">HHI", 3, 1, 12)     # platform 3, encoding 1, offset 12
    cmap += sub
    tables["cmap"] = bytes(cmap)

    # OS/2（关键：旧字体缺这张表，Android 解析容易失败）
    os2 = bytearray(96)
    struct.pack_into(">H", os2, 0, 4)          # version
    struct.pack_into(">h", os2, 2, 1000)       # xAvgCharWidth
    struct.pack_into(">H", os2, 4, 400)        # usWeightClass
    struct.pack_into(">H", os2, 6, 5)          # usWidthClass
    struct.pack_into(">H", os2, 8, 0)          # fsType
    struct.pack_into(">h", os2, 10, 200)       # ySubscriptXSize
    struct.pack_into(">h", os2, 12, 200)
    struct.pack_into(">h", os2, 14, 100)
    struct.pack_into(">h", os2, 16, 200)
    struct.pack_into(">h", os2, 18, 200)
    struct.pack_into(">h", os2, 20, 100)
    struct.pack_into(">h", os2, 22, 300)
    struct.pack_into(">h", os2, 24, 300)
    struct.pack_into(">h", os2, 26, 100)
    struct.pack_into(">h", os2, 28, 0)         # yStrikeoutSize
    struct.pack_into(">h", os2, 30, 260)       # yStrikeoutPosition
    struct.pack_into(">h", os2, 32, 0)         # sFamilyClass
    os2[34:44] = bytes([2, 0, 5, 3, 0, 0, 0, 0, 0, 0])  # panose
    struct.pack_into(">I", os2, 44, 0x00000001)  # ulUnicodeRange1 (Basic Latin)
    struct.pack_into(">I", os2, 48, 0)
    struct.pack_into(">I", os2, 52, 0)
    struct.pack_into(">I", os2, 56, 0)
    os2[60:64] = b"ROCK"       # achVendID
    struct.pack_into(">H", os2, 64, 0x0040)    # fsSelection (REGULAR)
    struct.pack_into(">H", os2, 66, min(new_cmap.keys()))   # usFirstCharIndex
    struct.pack_into(">H", os2, 68, min(max(new_cmap.keys()), 0xFFFF))  # usLastCharIndex
    struct.pack_into(">h", os2, 70, ascender)
    struct.pack_into(">h", os2, 72, descender)
    struct.pack_into(">h", os2, 74, lineGap)
    struct.pack_into(">H", os2, 76, ascender)
    struct.pack_into(">H", os2, 78, -descender)
    struct.pack_into(">I", os2, 80, 0)
    struct.pack_into(">I", os2, 84, 0)
    struct.pack_into(">h", os2, 86, 0)   # sxHeight
    struct.pack_into(">h", os2, 88, 0)   # sCapHeight
    struct.pack_into(">H", os2, 90, 0)   # usDefaultChar
    struct.pack_into(">H", os2, 92, 0x0020)  # usBreakChar
    struct.pack_into(">H", os2, 94, 1)   # usMaxContext
    tables["OS/2"] = bytes(os2)

    # name（Android 需要它来识别字体名）
    def name_record(pid, eid, lid, nid, text, enc):
        b = text.encode(enc)
        return struct.pack(">HHHHHH", pid, eid, lid, nid, len(b), 0), b

    recs = []
    strings = bytearray()
    base = 6 + 12 * 6
    def add(pid, eid, lid, nid, text, enc):
        r, b = name_record(pid, eid, lid, nid, text, enc)
        offset = base + len(strings)
        r = r[:10] + struct.pack(">H", offset)
        recs.append(r)
        strings.extend(b)

    add(3, 1, 0x409, 1, "RocoOcrSubset", "utf-16-be")
    add(3, 1, 0x409, 2, "Regular", "utf-16-be")
    add(3, 1, 0x409, 4, "RocoOcrSubset Regular", "utf-16-be")
    add(3, 1, 0x409, 6, "RocoOcrSubset", "utf-16-be")
    add(1, 0, 0, 1, "RocoOcrSubset", "latin1")
    add(1, 0, 0, 2, "Regular", "latin1")

    name = bytearray()
    name += struct.pack(">HHH", 0, len(recs), base)
    for r in recs:
        name += r
    name += strings
    tables["name"] = bytes(name)

    # post（version 3 = 无字形名）
    post = bytearray(32)
    struct.pack_into(">I", post, 0, 0x00030000)
    struct.pack_into(">i", post, 4, 0)
    struct.pack_into(">h", post, 8, -100)
    struct.pack_into(">h", post, 10, 50)
    struct.pack_into(">I", post, 12, 0)
    struct.pack_into(">I", post, 16, 0)
    struct.pack_into(">I", post, 20, 0)
    struct.pack_into(">I", post, 24, 0)
    struct.pack_into(">I", post, 28, 0)
    tables["post"] = bytes(post)

    # —— 写出 TTF ——
    tags = sorted(tables.keys())
    n = len(tags)
    # 计算 searchRange 等
    entrySelector = 0
    while (1 << (entrySelector + 1)) <= n:
        entrySelector += 1
    searchRange = (1 << entrySelector) * 16
    rangeShift = n * 16 - searchRange

    header = struct.pack(">IHHHH", 0x00010000, n, searchRange, entrySelector, rangeShift)
    offset = 12 + n * 16
    dirs = bytearray()
    body = bytearray()
    for tag in tags:
        data = tables[tag]
        dirs += tag.encode("latin1").ljust(4, b" ")
        dirs += struct.pack(">III", 0, offset, len(data))
        body += pad4(data)
        offset += len(pad4(data))

    out = bytearray()
    out += header
    out += dirs
    out += body

    # 重新对齐并写校验和（简化：checksumAdjustment 置 0）
    open(OUT, "wb").write(bytes(out))
    print("已写出:", OUT, len(out), "字节")
    print("表:", tags)
    print("字形数:", numGlyphs, " cmap 条目:", len(new_cmap))

    # —— 导出字符分层（供 Kotlin 三档权重使用）——
    payload = {
        "source": "simhei + 通用规范汉字表一级字表3500",
        "tiers": {
            # 图鉴字 + 自定义精灵字：权重最高
            "dex": "".join(tiers["dex"]),
            # 常用字：兜底
            "common": "".join(tiers["common"]),
        }
    }
    with open(WEIGHTS_OUT, "w", encoding="utf-8") as f:
        json.dump(payload, f, ensure_ascii=False, indent=1)
    print("已写出分层表:", WEIGHTS_OUT)
    print("  dex 层:", len(tiers["dex"]), " common 层:", len(tiers["common"]))


main()
