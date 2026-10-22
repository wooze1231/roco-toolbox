import urllib.request, ssl, re
CTX = ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA='Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
u='https://pd.qq.com/g/roco135790/post/B_5185aa6abd880a001441152196270699980X60'
try:
    req=urllib.request.Request(u,headers={'User-Agent':UA,'Accept-Language':'zh-CN,zh;q=0.9'})
    with urllib.request.urlopen(req,timeout=25,context=CTX) as r:
        d=r.read().decode('utf-8','ignore')
    print('OK', len(d))
    print('title:', d[d.find('<title'):d.find('</title>')+8][:150])
    print('活动命中:', d.count('活动'))
    for m in re.findall(r'.{0,30}活动时间.{0,120}', d)[:5]:
        print('  >', re.sub(r'<[^>]+>','',m)[:150])
except Exception as e:
    print('FAIL', type(e).__name__, str(e)[:120])
# 频道列表/API 探测
for u2 in ['https://pd.qq.com/g/roco135790',
           'https://api.pd.qq.com/cgi-bin/v1/guild/post/list?guild_id=roco135790']:
    try:
        req=urllib.request.Request(u2,headers={'User-Agent':UA})
        with urllib.request.urlopen(req,timeout=20,context=CTX) as r:
            d=r.read().decode('utf-8','ignore')
        print('OK ',u2,len(d),'活动数',d.count('活动'))
    except Exception as e:
        print('FAIL',u2,type(e).__name__,str(e)[:80])
