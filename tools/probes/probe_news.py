import re, ssl, urllib.request, json
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
def get(u,ref=None):
    h={'User-Agent':UA}
    if ref: h['Referer']=ref
    req=urllib.request.Request(u,headers=h)
    with urllib.request.urlopen(req,timeout=20,context=CTX) as r: return r.read().decode('utf-8','ignore')
# 1) 官网首页找新闻链接
h=get('https://rocom.qq.com/main/index.html')
links=set(re.findall(r'https?://[a-zA-Z0-9./_?=&%#-]{8,140}', h))
for k in ['news','notice','announce','bulletin','detail','gicp','apps.game.qq.com','iwan','kuaikan']:
    for x in sorted([l for l in links if k in l.lower()])[:6]:
        print('LINK',k,'->',x)
print('---- 试腾讯通用新闻接口 ----')
for u in ['https://apps.game.qq.com/cmc/zmMcnTargetContentList?r0=jsonp&page=1&num=16&target=24&source=web_pc',
          'https://apps.game.qq.com/cmc/zmMcnTargetContentList?r0=json&page=1&num=16&target=24&source=web_pc']:
    try:
        d=get(u,'https://rocom.qq.com/')
        print('OK',u,len(d)); print(d[:600].replace('\n',' '))
    except Exception as e:
        print('FAIL',u,type(e).__name__,str(e)[:70])
