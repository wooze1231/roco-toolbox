import urllib.request, ssl
CTX = ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
HDR={'Referer':'https://servicewechat.com/wx38b0bed275763e1b/91/page-frame.html',
     'User-Agent':'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 MicroMessenger/8.0.20'}
base='https://roco-toolkit-img.oss-cn-shenzhen.aliyuncs.com/'
for p in ['?list-type=2&max-keys=200','?prefix=&max-keys=200','?list-type=2&prefix=&max-keys=1000']:
    try:
        req=urllib.request.Request(base+p,headers=HDR)
        with urllib.request.urlopen(req,timeout=15,context=CTX) as r:
            d=r.read()
        print('OK  ',p,len(d)); print(d[:1200].decode('utf-8','ignore'))
    except Exception as e:
        print('FAIL',p,type(e).__name__,str(e)[:90])
