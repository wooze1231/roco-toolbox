import ssl, urllib.request, re, json
CTX = ssl.create_default_context(); CTX.check_hostname = False; CTX.verify_mode = ssl.CERT_NONE
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"

def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept-Language": "zh-CN,zh;q=0.9"})
    return urllib.request.urlopen(req, timeout=40, context=CTX).read().decode("utf-8", "ignore")

API = "https://wiki.biligame.com/rocom/api.php"
import urllib.parse
q = urllib.parse.urlencode({
    "action": "query", "titles": "奇丽草", "prop": "images", "format": "json", "formatversion": "2",
})
print(get(API + "?" + q)[:1500])
print("==== parse html 找异色 ====")
q2 = urllib.parse.urlencode({
    "action": "parse", "page": "奇丽草", "prop": "text", "format": "json", "formatversion": "2",
})
j = json.loads(get(API + "?" + q2))
txt = j.get("parse", {}).get("text", "")
imgs = re.findall(r'src="(/images/[^"]+)"', txt) + re.findall(r'src="(https://[^"]+\.(?:png|jpg|webp))"', txt)
print("imgs:", len(imgs))
for i in imgs[:40]:
    print(i)
for m in re.finditer("异色", txt):
    print("CTX:", txt[max(0, m.start()-200):m.start()+200].replace("\n", " ")[:400])
    break
