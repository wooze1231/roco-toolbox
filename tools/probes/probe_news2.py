import re, ssl, urllib.request, json
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
def get(u,ref=None):
    h={'User-Agent':UA}
    if ref: h['Referer']=ref
    req=urllib.request.Request(u,headers=h)
    with urllib.request.urlopen(req,timeout=20,context=CTX) as r: return r.read().decode('utf-8','ignore')
try:
    js=get('https://rocom.qq.com/web202507/index.js')
    print('js len',len(js))
    for m in re.finditer(r'fillNews|zmMcnTargetContentList|target', js):
        s=max(0,m.start()-120); print(' ...', js[s:m.start()+220].replace('\n',' ')[:300])
except Exception as e:
    print('js FAIL', type(e).__name__, str(e)[:70])
print('=== 试 target ===')
for t in ['135126','467']:
    u='https://apps.game.qq.com/cmc/zmMcnTargetContentList?r0=json&page=1&num=16&target=%s&source=web_pc'%t
    try:
        d=get(u,'https://rocom.qq.com/')
        j=json.loads(d)
        res=j.get('data',{}).get('result',[])
        print('OK target',t,'条数',len(res))
        for r in res[:6]:
            print('   -',r.get('sCreated'),r.get('sTitle'))
    except Exception as e:
        print('FAIL',t,type(e).__name__,str(e)[:70])
