import ssl, urllib.request, json, urllib.parse
CTX = ssl.create_default_context(); CTX.check_hostname = False; CTX.verify_mode = ssl.CERT_NONE
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36"
API = "https://wiki.biligame.com/rocom/api.php"

def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    return urllib.request.urlopen(req, timeout=40, context=CTX).read().decode("utf-8", "ignore")

for t in ["文件:JL_qilicao_yise.png", "File:JL_qilicao_yise.png", "JL_qilicao_yise.png"]:
    q = urllib.parse.urlencode({"action": "query", "titles": t, "prop": "imageinfo",
                                "iiprop": "url", "format": "json", "formatversion": "2"})
    print("---", t)
    print(get(API + "?" + q)[:600])
