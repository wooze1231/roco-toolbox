import urllib.request, ssl, os, time, sys

URL = sys.argv[1] if len(sys.argv) > 1 else 'https://services.gradle.org/distributions/gradle-8.9-bin.zip'
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'gradle-8.9-bin.zip')
TMP = OUT + '.part'
LOG = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'gradle_dl.log')

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

# resume support
start = 0
if os.path.exists(TMP):
    start = os.path.getsize(TMP)
    print(f'resuming from {start} bytes', flush=True)

req = urllib.request.Request(URL, headers={'User-Agent': 'Mozilla/5.0'})
if start:
    req.add_header('Range', f'bytes={start}-')

t0 = time.time()
total = 0
with urllib.request.urlopen(req, timeout=600, context=ctx) as resp:
    # content-length may be absent when resuming
    clen = resp.headers.get('Content-Length')
    mode = 'ab' if start else 'wb'
    with open(TMP, mode) as f:
        while True:
            buf = resp.read(1 << 20)  # 1 MiB chunks
            if not buf:
                break
            f.write(buf)
            total += len(buf)
            if total % (8 << 20) < (1 << 20):  # ~ every 8 MiB
                elapsed = time.time() - t0
                speed = (start + total) / elapsed / 1024 / 1024 if elapsed > 0 else 0
                msg = f'[{time.strftime("%H:%M:%S")}] {start+total>>20} MiB  {speed:.2f} MiB/s'
                print(msg, flush=True)
                with open(LOG, 'a') as lg:
                    lg.write(msg + '\n')

os.rename(TMP, OUT)
final = os.path.getsize(OUT)
print(f'DONE gradle-8.9-bin.zip size={final} ({final>>20} MiB)', flush=True)
with open(LOG, 'a') as lg:
    lg.write(f'DONE size={final}\n')
