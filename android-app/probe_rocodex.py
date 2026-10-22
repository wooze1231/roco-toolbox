import urllib.request, ssl, re
ctx = ssl.create_default_context(); ctx.check_hostname = False; ctx.verify_mode = ssl.CERT_NONE
hdr = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36'}
req = urllib.request.Request('https://rocodex.org/items', headers=hdr)
html = urllib.request.urlopen(req, timeout=25, context=ctx).read().decode('utf-8', 'ignore')

print('has application/json:', 'application/json' in html)
m = re.search(r'<script[^>]*id="__NEXT_DATA__"[^>]*>(.*?)</script>', html, re.S)
print('next_data block:', bool(m))
if m:
    print('next_data len:', len(m.group(1)))

refs = set(re.findall(r'(?:src|data-src)="([^"]*icons/[^"]+\.(?:png|webp))"', html))
print('icon refs (src/data-src):', len(refs))
for r in list(refs)[:25]:
    print('  ', r)

assets = set(re.findall(r'https://assets\.rocodex\.org/[^"\'\s]+\.(?:png|webp|jpg)', html))
print('all assets count:', len(assets))

for kw in ['蓝晶碧玺', '魔力果', '国王球', '矿石', '粉尘', '精灵蛋', '咕噜球']:
    print(kw, 'in html:', kw in html)

# 看 next_data 里有没有物品数据
if m:
    nd = m.group(1)
    for kw in ['蓝晶碧玺', '魔力果', '国王球', 'item', 'items', 'icon']:
        print('next_data has', kw, ':', kw in nd)
    # 打印前 600 字符
    print('--- next_data head ---')
    print(nd[:600])
