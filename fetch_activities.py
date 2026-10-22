# -*- coding: utf-8 -*-
"""
抓取《洛克王国：世界》官方活动（来源：腾讯频道 roco135790 的官方更新公告帖）。
公告正文为服务端渲染，包含结构化条目：
    >>>「活动名」活动时间：xxx - xxx 参与条件：xxx 活动说明：xxx
输出 activities.json（供 App 使用）与控制台清单。
"""
import json
import re
import ssl
import sys
import urllib.parse
import urllib.request

CTX = ssl.create_default_context()
CTX.check_hostname = False
CTX.verify_mode = ssl.CERT_NONE

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
CHANNEL = "https://pd.qq.com/g/roco135790"
KEYWORDS = ("更新公告", "活动", "预告", "版本", "远行商人")

# 官方公告帖直链（腾讯频道）。频道首页 feed 是玩家帖且为 JS 渲染，
# 抓不到公告列表，因此用「已知公告直链 + 频道页兜底」的方式。
KNOWN_POSTS = [
    "https://pd.qq.com/g/roco135790/post/B_5185aa6abd880a001441152196270699980X60",  # 9/17 版本更新公告
]


def get(url):
    req = urllib.request.Request(url, headers={
        "User-Agent": UA, "Accept-Language": "zh-CN,zh;q=0.9"})
    with urllib.request.urlopen(req, timeout=25, context=CTX) as r:
        return r.read().decode("utf-8", "ignore")


def strip_html(s):
    s = re.sub(r"<script[\s\S]*?</script>", " ", s)
    s = re.sub(r"<style[\s\S]*?</style>", " ", s)
    s = re.sub(r"<br\s*/?>", "\n", s)
    s = re.sub(r"</p>|</div>|</li>", "\n", s)
    s = re.sub(r"<[^>]+>", "", s)
    s = (s.replace("&nbsp;", " ").replace("&amp;", "&")
          .replace("&quot;", '"').replace("&gt;", ">").replace("&lt;", "<")
          .replace("&#39;", "'"))
    return re.sub(r"[ \t]+", " ", s)


def find_posts(html):
    """从频道页提取公告帖链接。"""
    ids = re.findall(r"/g/roco135790/post/([A-Za-z0-9_\-]+)", html)
    seen, out = set(), []
    for i in ids:
        if i in seen:
            continue
        seen.add(i)
        out.append("https://pd.qq.com/g/roco135790/post/" + i)
    return out


def parse_activities(text):
    """从公告纯文本里切出活动条目。"""
    acts = []
    # 以「>>>「活动名」…活动时间」为锚点，两条锚点之间即一个活动块
    anchors = list(re.finditer(
        r">>>\s*[「\[]([^」\]]{2,30})[」\]](?=[\s\S]{0,15}?活动时间)", text))
    for i, m in enumerate(anchors):
        name = m.group(1).strip()
        b = text[m.start(): (anchors[i + 1].start() if i + 1 < len(anchors) else len(text))]
        tm = re.search(
            r"活动时间[：:]\s*(\d{4})年(\d{1,2})月(\d{1,2})日\s*(\d{1,2}):(\d{2})"
            r"\s*[-~至]\s*(\d{4})年(\d{1,2})月(\d{1,2})日\s*(\d{1,2}):(\d{2})", b)
        if not tm:
            continue
        if not name:
            continue
        start = "%s-%02d-%02d %02d:%02d" % (tm.group(1), int(tm.group(2)),
                                            int(tm.group(3)), int(tm.group(4)), int(tm.group(5)))
        end = "%s-%02d-%02d %02d:%02d" % (tm.group(6), int(tm.group(7)),
                                          int(tm.group(8)), int(tm.group(9)), int(tm.group(10)))
        cond = re.search(r"参与条件[：:]\s*([^\n]{0,120})", b)
        desc = re.search(r"活动说明[：:]\s*([\s\S]{0,300}?)(?=\n\s*(?:>>>|[「\[])|$)", b)
        acts.append({
            "name": name,
            "start": start,
            "end": end,
            "cond": cond.group(1).strip() if cond else "",
            "desc": re.sub(r"\s+", " ", desc.group(1)).strip() if desc else "",
        })
    return acts


def main():
    html = get(CHANNEL)
    posts = find_posts(html)
    print("频道页抓到帖子链接 %d 条" % len(posts), file=sys.stderr)

    result = []
    used = []
    candidates = KNOWN_POSTS + posts[:8]
    for url in candidates:
        try:
            page = get(url)
        except Exception as e:
            print("  跳过 %s (%s)" % (url, type(e).__name__), file=sys.stderr)
            continue
        title_m = re.search(r"<title>(.*?)</title>", page, re.S)
        title = strip_html(title_m.group(1)).strip() if title_m else ""
        if not any(k in title for k in KEYWORDS):
            continue
        text = strip_html(page)
        acts = parse_activities(text)
        if not acts:
            continue
        used.append({"title": title, "url": url, "count": len(acts)})
        for a in acts:
            a["source"] = title
            a["url"] = url
        result.extend(acts)
        if len(used) >= 2:      # 最近两篇公告足够覆盖当前活动
            break

    # 去重（同名同起止）
    uniq, seen = [], set()
    for a in result:
        k = (a["name"], a["start"])
        if k in seen:
            continue
        seen.add(k)
        uniq.append(a)
    uniq.sort(key=lambda x: x["start"])

    with open("activities.json", "w", encoding="utf-8") as f:
        json.dump({"updated": __import__("time").time(), "activities": uniq},
                  f, ensure_ascii=False, indent=2)

    print("公告来源：")
    for u in used:
        print("  -", u["title"], "(%d 条)" % u["count"])
    print("\n共解析活动 %d 条：\n" % len(uniq))
    for a in uniq:
        print("【%s】%s ~ %s" % (a["name"], a["start"], a["end"]))
        if a["cond"]:
            print("   条件：%s" % a["cond"])
        if a["desc"]:
            print("   说明：%s" % a["desc"][:110])
    print("\n已写入 activities.json")


if __name__ == "__main__":
    main()
