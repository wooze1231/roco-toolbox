import re, ssl, urllib.request
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
req=urllib.request.Request('https://game.gtimg.cn/images/js/fillnewsgicp/v1.4.js',headers={'User-Agent':UA})
js=urllib.request.urlopen(req,timeout=20,context=CTX).read().decode('utf-8','ignore')
print('len',len(js))
for m in re.finditer(r'https?://[a-zA-Z0-9./_?=&%\-]{15,160}', js):
    print('URL', m.group(0))
print('--- 参数片段 ---')
for k in ['target','gameId','source','num','page','cmsId']:
    for m in list(re.finditer(k, js))[:3]:
        print(k,'->', js[max(0,m.start()-100):m.start()+140].replace('\n',' ')[:230])
