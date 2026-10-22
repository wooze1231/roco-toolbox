import urllib.request, ssl, re, urllib.parse

ctx = ssl.create_default_context(); ctx.check_hostname = False; ctx.verify_mode = ssl.CERT_NONE
hdr = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36',
       'Referer': 'https://wiki.biligame.com/rocom/'}

names = ['国王球', '蓝晶碧玺', '魔力果', '炫彩精灵蛋', '黑金', '琉璃', '紫莲刚玉', '首领血脉秘药', '萌系粉尘', '高级咕噜球']
for name in names:
    u = 'https://wiki.biligame.com/rocom/' + urllib.parse.quote(name)
    try:
        req = urllib.request.Request(u, headers=hdr)
        html = urllib.request.urlopen(req, timeout=20, context=ctx).read().decode('utf-8', 'ignore')
    except Exception as e:
        print(name, 'FAIL', e); continue
    # 所有 patchwiki 图片 img 标签
    imgs = re.findall(r'<img\b[^>]*patchwiki\.biligame\.com/images/rocom/[^>]*>', html)
    print('===', name, 'imgs:', len(imgs))
    for tag in imgs[:6]:
        src = re.search(r'src="([^"]+)"', tag)
        alt = re.search(r'alt="([^"]*)"', tag)
        cls = re.search(r'class="([^"]*)"', tag)
        print('   src=', (src.group(1)[:90] if src else None),
              '| alt=', (alt.group(1) if alt else ''),
              '| cls=', (cls.group(1) if cls else ''))
