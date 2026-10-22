#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""补抓远行商人新增/常见商品图标（patchwiki 独立页原图），追加到 drawable-nodpi/item_N.png（从 27 起）。
不覆盖已有 item_0..item_26。输出 more_icons_map.txt。
"""
import urllib.request, ssl, re, os, threading, time
from urllib.parse import quote

CTX = ssl.create_default_context(); CTX.check_hostname = False; CTX.verify_mode = ssl.CERT_NONE
HDR = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36',
       'Referer': 'https://wiki.biligame.com/rocom/'}
ROOT = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(ROOT, 'app', 'src', 'main', 'res', 'drawable-nodpi')
os.makedirs(OUT, exist_ok=True)

START_INDEX = 27

NAMES = [
    # 矿石宝石（当前在售优先）
    '黑晶琉璃', '黄石榴石', '紫色矿石', '黑色矿石', '琉璃矿', '蓝色矿石', '粉晶矿石',
    # 精灵球
    '变幻球', '棱镜球', '绝缘球', '网兜球', '暗星球', '补光球', '中级咕噜球', '条件球',
    # 血脉 / 钥匙 / 稀有
    '奇异血脉秘药', '万能血脉秘药', '首领血脉密钥', '适格钥匙', '能力钥匙', '祝福项坠', '残缺魔镜',
    # 粉尘
    '地系粉尘', '光系粉尘', '幽系粉尘', '恶系粉尘', '武系粉尘', '电系粉尘', '魔法粉尘',
    # 果 / 蛋
    '经验果', '大经验果', '精品大果', '神奇妙妙蛋', '普通精灵蛋', '彩虹果', '荧光草',
    # 其他材料
    '水脉秘药', '天水碧', '寒冰石', '火山石',
]

lock = threading.Lock()
kmap = []
idx = START_INDEX
done = set()


def fetch_bytes(url, timeout=30):
    req = urllib.request.Request(url, headers=HDR)
    with urllib.request.urlopen(req, timeout=timeout, context=CTX) as r:
        return r.read()


def is_image(d):
    return d[:8] == b'\x89PNG\r\n\x1a\n' or d[:3] == b'\xff\xd8\xff'


def extract_real_icon(html):
    imgs = re.findall(r'<img\b[^>]*patchwiki\.biligame\.com/images/rocom/[^>]*>', html)
    for tag in imgs:
        src = re.search(r'src="([^"]+)"', tag)
        alt = re.search(r'alt="([^"]*)"', tag)
        if not src:
            continue
        s = src.group(1)
        a = alt.group(1) if alt else ''
        if 'Logo' in a or 'Logo' in s:
            continue
        if a == '图标 物品 来源.png':
            continue
        if '/thumb/' in s:
            continue
        return s
    return None


def worker(name):
    global idx
    if name in done:
        return
    data = None
    try:
        req = urllib.request.Request('https://wiki.biligame.com/rocom/' + quote(name), headers=HDR)
        html = urllib.request.urlopen(req, timeout=25, context=CTX).read().decode('utf-8', 'ignore')
        icon = extract_real_icon(html)
        if icon:
            data = fetch_bytes(icon)
    except Exception as e:
        with lock:
            print(f'  skip {name}: {type(e).__name__} {str(e)[:50]}', flush=True)
        return
    if not data or not is_image(data):
        with lock:
            print(f'  no-icon {name}', flush=True)
        return
    with lock:
        i = idx
        idx += 1
        with open(os.path.join(OUT, f'item_{i}.png'), 'wb') as f:
            f.write(data)
        kmap.append((name, f'R.drawable.item_{i}'))
        done.add(name)
        print(f'  + {name} -> item_{i}.png ({len(data)}B)', flush=True)


print(f'start scraping {len(NAMES)} items from index {START_INDEX} ...', flush=True)
ts = []
for n in NAMES:
    t = threading.Thread(target=worker, args=(n,))
    t.start(); ts.append(t); time.sleep(0.05)
for t in ts:
    t.join()

lines = [f'    "{n}" to {r},' for n, r in kmap]
with open(os.path.join(ROOT, 'more_icons_map.txt'), 'w', encoding='utf-8') as f:
    f.write('\n'.join(lines) + '\n')
print(f'\nDONE: {len(kmap)} new icons -> more_icons_map.txt', flush=True)
