"""生成最终贴图表 assets/shiny_icons.json（普通 + 异色直链）。

数据源：nrc wiki（洛克王国本体）页面里的 data-view="pet" / data-view="shiny" 立绘，
        rocom wiki 的 JL_<拼音>[_yise].png 作为补充。
解析失败时先 list=search 找正确页面名再试（解决「拿脑丸/章脑丸」这类别名）。

用法：
    python build_shiny_art.py            # 只处理 S4 默认数据里的精灵（快）
    python build_shiny_art.py --all      # 处理图鉴全量（慢，10 分钟左右）
"""
import ssl, urllib.request, json, urllib.parse, re, time, sys

CTX = ssl.create_default_context(); CTX.check_hostname = False; CTX.verify_mode = ssl.CERT_NONE
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
NRC = "https://wiki.biligame.com/nrc/api.php"
ROCOM = "https://wiki.biligame.com/rocom/api.php"

# 玩家叫法 -> wiki 页面名
ALIAS = {
    "拿脑丸": "章脑丸", "芽眼魇": "芽眼魔", "叮叮卵": "叮叮卯",
    "刺轮砣（上弦）": "刺轮砣（上弦的样子）", "刺轮砣（下弦）": "刺轮砣（下弦的样子）",
}


def get(u, retry=4):
    for i in range(retry):
        try:
            return urllib.request.urlopen(urllib.request.Request(u, headers={"User-Agent": UA}),
                                          timeout=45, context=CTX).read().decode("utf-8", "ignore")
        except Exception:
            time.sleep(3 * (i + 1))
    return ""


def query(api, p):
    t = get(api + "?" + urllib.parse.urlencode(p))
    if not t.strip():
        return {}
    try:
        return json.loads(t)
    except Exception:
        return {}


def nrc_art(page):
    j = query(NRC, {"action": "parse", "page": page, "prop": "text",
                    "format": "json", "formatversion": "2"})
    html = j.get("parse", {}).get("text", "")
    if not html:
        return None, None
    pet = re.search(r'data-view="pet"[^>]*>\s*<img[^>]+src="([^"]+)"', html)
    shiny = re.search(r'data-view="shiny"[^>]*>\s*<img[^>]+src="([^"]+)"', html)
    return (pet.group(1) if pet else None), (shiny.group(1) if shiny else None)


def resolve(name):
    """按名字拿 (pet, shiny)；先直查，失败用别名 / 搜索。"""
    p, y = nrc_art(name)
    if y or p:
        return p, y
    alias = ALIAS.get(name)
    if alias:
        p, y = nrc_art(alias)
        if y or p:
            return p, y
    j = query(NRC, {"action": "query", "list": "search", "srsearch": name, "srlimit": "5",
                    "format": "json", "formatversion": "2"})
    for r in j.get("query", {}).get("search", []):
        t = r["title"]
        if "（物品）" in t or "大量出没" in t:
            continue
        p, y = nrc_art(t)
        if y or p:
            return p, y
        time.sleep(0.3)
    return None, None


def targets(all_mode):
    out = []
    d = json.load(open("shiny_default.json", encoding="utf-8"))
    for s in d["seasons"]:
        for g in s["groups"]:
            for it in g["items"]:
                out.append((it.get("s", ""), it.get("n", "")))
    if all_mode:
        for c in json.load(open("dex_cells.json", encoding="utf-8")):
            if c.get("slug") and c.get("title"):
                out.append((c["slug"], c["title"]))
    # 去重
    seen, uniq = set(), []
    for s, n in out:
        k = (s, n)
        if k in seen:
            continue
        seen.add(k)
        uniq.append((s, n))
    return uniq


def main():
    all_mode = "--all" in sys.argv
    items = targets(all_mode)
    print("targets:", len(items))
    res = {}
    try:
        res.update(json.load(open("nrc_shiny.json", encoding="utf-8")))
    except Exception:
        pass
    hit = 0
    for i, (slug, name) in enumerate(items):
        key = slug or name
        if res.get(key, {}).get("y"):
            hit += 1
            continue
        p, y = resolve(name)
        if p or y:
            r = {"n": name}
            if p: r["p"] = p
            if y: r["y"] = y
            res[key] = r
            if y: hit += 1
        print(i + 1, "/", len(items), name, "shiny:", bool(y))
        time.sleep(0.5)
        if (i + 1) % 50 == 0:
            json.dump(res, open("nrc_shiny.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)

    json.dump(res, open("nrc_shiny.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    # 与 rocom 表合并（rocom 有而 nrc 没命中的补进来）
    merged = dict(res)
    try:
        for k, v in json.load(open("shiny_icons_rocom.json", encoding="utf-8")).items():
            if k not in merged:
                merged[k] = v
    except Exception:
        pass
    json.dump(merged, open("shiny_icons.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    print("total:", len(merged), "with shiny:", len([v for v in merged.values() if v.get("y")]))


if __name__ == "__main__":
    main()
