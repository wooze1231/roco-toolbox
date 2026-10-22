import re, ssl, urllib.request, json
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
def get(u,ref=None):
    h={'User-Agent':UA}
    if ref: h['Referer']=ref
    req=urllib.request.Request(u,headers=h)
    with urllib.request.urlopen(req,timeout=20,context=CTX) as r: return r.read().decode('utf-8','ignore')
for u in ['https://rocom.qq.com/web202507/sub/index.html']:
    try:
        h=get(u,'https://rocom.qq.com/')
        print('OK',u,len(h))
        print('title:', re.search(r'<title>(.*?)</title>',h,re.S).group(1)[:80] if re.search(r'<title>(.*?)</title>',h,re.S) else '')
        for k in ['fillNews','target=','zmMcn','cmsid','newsid','data-id']:
            for m in list(re.finditer(re.escape(k),h))[:3]:
                print('  ',k,'->',h[max(0,m.start()-80):m.start()+160].replace('\n',' ')[:220])
        js=set(re.findall(r'src="([^"]+\.js)"',h))
        print('  js:', [x for x in js if 'index' in x or 'news' in x or 'sub' in x][:6])
    except Exception as e:
        print('FAIL',u,type(e).__name__,str(e)[:70])
# 试 target 数字（上一轮被截断，重跑）
for t in ['135126','467','135127']:
    u2='https://apps.game.qq.com/cmc/zmMcnTargetContentList?r0=json&page=1&num=10&target=%s&source=web_pc'%t
    try:
        j=json.loads(get(u2,'https://rocom.qq.com/'))
        res=j.get('data',{}).get('result',[])
        print('target',t,'->',len(res))
        for r in res[:5]: print('    ',r.get('sCreated'),'|',r.get('sTitle'))
    except Exception as e:
        print('target',t,'FAIL',type(e).__name__,str(e)[:60])
