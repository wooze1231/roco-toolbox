import urllib.request, ssl
CTX = ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
HDR={'Referer':'https://servicewechat.com/wx38b0bed275763e1b/91/page-frame.html',
     'User-Agent':'Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 MicroMessenger/8.0.20'}
base='https://roco-toolkit-img.oss-cn-shenzhen.aliyuncs.com/'
pats=['67001.png','67001.jpg','items/67001.png','item/67001.png','icon/67001.png','icons/67001.png',
      'img/67001.png','images/67001.png','merchant/67001.png','prop/67001.png','props/67001.png',
      'item_67001.png','67001.webp','items/67001.webp']
for p in pats:
    url=base+p
    try:
        req=urllib.request.Request(url,headers=HDR)
        with urllib.request.urlopen(req,timeout=12,context=CTX) as r:
            d=r.read()
        sig = 'PNG' if d[:8]==b'\x89PNG\r\n\x1a\n' else ('JPG' if d[:3]==b'\xff\xd8\xff' else ('WEBP' if d[8:12]==b'WEBP' else 'other'))
        print('OK  ',p,'len',len(d),'type',sig)
    except Exception as e:
        print('FAIL',p,type(e).__name__,str(e)[:60])
