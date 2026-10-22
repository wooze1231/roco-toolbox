import ssl, urllib.request, json, re
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
def get(u,ref='https://rocom.qq.com/web202507/sub/index.html'):
    req=urllib.request.Request(u,headers={'User-Agent':UA,'Referer':ref})
    with urllib.request.urlopen(req,timeout=20,context=CTX) as r: return r.read().decode('utf-8','ignore')
for t,label in [('135110','最新'),('135111','公告'),('135112','?')]:
    u='https://apps.game.qq.com/cmc/zmMcnTargetContentList?r0=json&page=1&num=10&target=%s&source=web_pc'%t
    try:
        j=json.loads(get(u))
        res=j.get('data',{}).get('result',[])
        print('== target',t,label,'条数',len(res))
        for r in res[:8]:
            print('   ',r.get('sCreated'),'|',r.get('sTitle'),'|',r.get('sDocId') or r.get('iNewsId'))
    except Exception as e:
        print('FAIL',t,type(e).__name__,str(e)[:70])
