import ssl, urllib.request, re
CTX=ssl.create_default_context(); CTX.check_hostname=False; CTX.verify_mode=ssl.CERT_NONE
UA="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
def get(url):
    req=urllib.request.Request(url,headers={"User-Agent":UA})
    return urllib.request.urlopen(req,timeout=30,context=CTX).read().decode("utf-8","ignore")

d=get("https://rocokingdomworld.org/zh/pokedex")
cards=re.findall(r'<a[^>]+href="(/zh/pokedex/[^"]+)"[^>]*>([\s\S]{0,1500}?)</a>',d)
n=0
for u,body in cards:
    if "/type/" in u: continue
    if not re.match(r'^[\u4e00-\u9fff]', re.sub(r'\s+','',re.sub(r'<[^>]+>','',body))[:8]):
        continue
    print("=========",u)
    print(body[:1200].replace("\n"," "))
    n+=1
    if n>=3: break
