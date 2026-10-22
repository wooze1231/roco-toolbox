# -*- coding: utf-8 -*-
"""
洛克王国世界 · 远行商人 桌面挂机提醒（零依赖纯标准库版）
============================================================
固定时间表（社区共识）：每日 08:00 / 12:00 / 16:00 / 20:00 刷新，
每轮持续 4 小时，00:00–08:00 不营业。
功能：控制台常驻 + 每秒实时倒计时 + 到点响铃 + Windows 通知。
时间表可在同目录 merchant_config.json 中自行修改。
仅用 Python 标准库，无需安装任何第三方包；Windows 双击或 python 运行即可。
"""
import os
import sys
import json
import base64
import subprocess
import datetime
import time

try:
    import winsound
except Exception:
    winsound = None

CONFIG_NAME = "merchant_config.json"

DEFAULT_CONFIG = {
    "refresh_times": ["08:00", "12:00", "16:00", "20:00"],
    "round_duration_hours": 4,
    "advance_notify_minutes": 5,
    "notify_on_start": True,
    "sound": True,
    "check_interval_seconds": 1,
    "title": "洛克王国世界 · 远行商人提醒"
}

# ---- ANSI 颜色（Windows 10+ 控制台支持，失败则无颜色）----
GREEN = "\033[92m"
RED = "\033[91m"
YELLOW = "\033[93m"
CYAN = "\033[96m"
BOLD = "\033[1m"
RESET = "\033[0m"


def config_path():
    if getattr(sys, "frozen", False):
        base = os.path.dirname(sys.executable)
    else:
        base = os.path.dirname(os.path.abspath(__file__))
    return os.path.join(base, CONFIG_NAME)


def load_config():
    p = config_path()
    cfg = dict(DEFAULT_CONFIG)
    if os.path.exists(p):
        try:
            with open(p, "r", encoding="utf-8") as f:
                cfg.update(json.load(f))
        except Exception:
            pass
    else:
        try:
            with open(p, "w", encoding="utf-8") as f:
                json.dump(cfg, f, ensure_ascii=False, indent=2)
        except Exception:
            pass
    return cfg, p


def parse_times(times):
    out = []
    for t in times:
        h, m = str(t).split(":")
        out.append((int(h), int(m)))
    return sorted(out)


def compute_state(cfg, now):
    today = datetime.datetime(now.year, now.month, now.day)
    starts = []
    for (h, m) in parse_times(cfg["refresh_times"]):
        starts.append(today.replace(hour=h, minute=m, second=0, microsecond=0))
    dur = datetime.timedelta(hours=cfg["round_duration_hours"])

    current = None
    for s in starts:
        if s <= now < s + dur:
            current = s
            break

    if current is not None:
        end = current + dur
        remaining = end - now
        nxt = None
        for s in starts:
            if s > now:
                nxt = s
                break
        if nxt is None:
            nxt = starts[0] + datetime.timedelta(days=1)
        return {
            "status": "open",
            "current": current,
            "end": end,
            "remaining": remaining,
            "next": nxt,
            "round_index": starts.index(current) + 1,
            "next_index": (starts.index(nxt) + 1) if nxt in starts else 1,
            "duration": dur,
        }
    else:
        nxt = None
        for s in starts:
            if s > now:
                nxt = s
                break
        if nxt is None:
            nxt = starts[0] + datetime.timedelta(days=1)
        countdown = nxt - now
        return {
            "status": "closed",
            "next": nxt,
            "countdown": countdown,
            "round_index": (starts.index(nxt) + 1) if nxt in starts else 1,
        }


def fmt_delta(td):
    if td < datetime.timedelta(0):
        td = datetime.timedelta(0)
    total = int(td.total_seconds())
    h, rem = divmod(total, 3600)
    m, s = divmod(rem, 60)
    if h > 0:
        return f"{h}时{m:02d}分{s:02d}秒"
    return f"{m}分{s:02d}秒"


def sanitize(s):
    return str(s).replace('"', " ").replace("'", " ")


def show_toast(title, msg):
    """尽力而为的 Windows Toast 通知，失败不影响主提醒。"""
    title = sanitize(title)
    msg = sanitize(msg)
    script = (
        "$template = [Windows.UI.Notifications.ToastNotificationManager]::"
        "GetTemplateContent([Windows.UI.Notifications.ToastTemplateType]::ToastText02)\n"
        "$texts = $template.GetElementsByTagName('text')\n"
        "$texts.Item(0).AppendChild($template.CreateTextNode('" + title + "')) | Out-Null\n"
        "$texts.Item(1).AppendChild($template.CreateTextNode('" + msg + "')) | Out-Null\n"
        "$doc = New-Object Windows.Data.Xml.Dom.XmlDocument\n"
        "$doc.LoadXml($template.GetXml())\n"
        "$toast = [Windows.UI.Notifications.ToastNotification]::new($doc)\n"
        "[Windows.UI.Notifications.ToastNotificationManager]::"
        "CreateToastNotifier('RocoMerchant').Show($toast)\n"
    )
    try:
        enc = base64.b64encode(script.encode("utf-16-le")).decode("ascii")
        subprocess.Popen(
            ["powershell", "-NoProfile", "-NonInteractive", "-EncodedCommand", enc],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, shell=True,
        )
    except Exception:
        pass


def beep(kind="start"):
    if winsound is None:
        return
    try:
        if kind == "start":
            winsound.Beep(880, 300)
            winsound.Beep(1175, 300)
        else:
            winsound.Beep(660, 250)
            winsound.Beep(880, 250)
    except Exception:
        pass


def enable_ansi():
    if sys.platform == "win32":
        try:
            import ctypes
            kernel32 = ctypes.windll.kernel32
            handle = kernel32.GetStdHandle(-11)
            mode = ctypes.c_uint32()
            if kernel32.GetConsoleMode(handle, ctypes.byref(mode)):
                kernel32.SetConsoleMode(handle, mode.value | 0x0004)
        except Exception:
            pass


def clear_screen():
    try:
        os.system("cls" if os.name == "nt" else "clear")
    except Exception:
        pass


def key_pressed():
    try:
        import msvcrt
        if msvcrt.kbhit():
            ch = msvcrt.getch()
            return ch in (b"q", b"Q", b"\x03")
    except Exception:
        pass
    return False


def alert_round(cfg, st):
    title = f"第{st['round_index']}轮已刷新！"
    msg = "远行商人来啦，快打开地图传送去蹲货~"
    print(f"\n{YELLOW}{BOLD}{'=' * 46}{RESET}")
    print(f"{YELLOW}{BOLD}  ★ {title}{RESET}")
    print(f"{YELLOW}{BOLD}  {msg}{RESET}")
    print(f"{YELLOW}{BOLD}{'=' * 46}{RESET}\n")
    if cfg.get("sound", True):
        beep("start")
    show_toast(cfg.get("title", "远行商人提醒"), title + " " + msg)


def alert_advance(cfg, st):
    am = cfg.get("advance_notify_minutes", 5)
    title = "即将刷新"
    msg = f"还有约 {am} 分钟，远行商人要来啦！"
    print(f"\n{CYAN}{BOLD}{'=' * 46}{RESET}")
    print(f"{CYAN}{BOLD}  ◷ {title}{RESET}")
    print(f"{CYAN}{BOLD}  {msg}{RESET}")
    print(f"{CYAN}{BOLD}{'=' * 46}{RESET}\n")
    if cfg.get("sound", True):
        beep("advance")
    show_toast(cfg.get("title", "远行商人提醒"), title + " " + msg)


def render(cfg, now, st, notified_start):
    clear_screen()
    line = "=" * 46
    print(f"{CYAN}{line}{RESET}")
    print(f"{CYAN}  {cfg.get('title', '远行商人提醒')}{RESET}")
    print(f"  当前时间：{now.strftime('%Y-%m-%d %H:%M:%S')}")
    print(f"{CYAN}{'-' * 46}{RESET}")
    if st["status"] == "open":
        print(f"  {GREEN}{BOLD}[营业中]{RESET} 远行商人已到！第{st['round_index']}轮")
        print(f"  本轮剩余：{YELLOW}{BOLD}{fmt_delta(st['remaining'])}{RESET}")
        print(f"  本轮结束：{st['end'].strftime('%H:%M')}　下一轮：{st['next'].strftime('%H:%M')}(第{st['next_index']}轮)")
    else:
        print(f"  {RED}{BOLD}[休息中]{RESET} 当前无远行商人（深夜不营业）")
        print(f"  距刷新：{YELLOW}{BOLD}{fmt_delta(st['countdown'])}{RESET}")
        print(f"  下一轮：{st['next'].strftime('%H:%M')}(第{st['round_index']}轮)")
    print(f"{CYAN}{'-' * 46}{RESET}")
    print(f"  今日时间表：{'  '.join(cfg['refresh_times'])}")
    print(f"  响铃：{'开' if cfg.get('sound', True) else '关'}　提前提醒：{cfg.get('advance_notify_minutes', 5)}分钟")
    print(f"  今天已提醒轮次：{', '.join(str(i) for i in sorted(notified_start)) or '无'}")
    print(f"{CYAN}{line}{RESET}")
    print(f"  按 Q 回车退出（或 Ctrl+C）。挂机中，实时刷新。")


def main():
    cfg, cfg_path = load_config()
    enable_ansi()
    notified_start = set()
    notified_advance = set()
    last_date = datetime.datetime.now().date()
    print(f"{CYAN}已启动：{cfg.get('title', '远行商人提醒')}{RESET}")
    print(f"配置文件：{cfg_path}")
    print(f"时间表：{'  '.join(cfg['refresh_times'])}　每轮 {cfg.get('round_duration_hours', 4)} 小时")
    print("挂机中……（按 Q 回车退出）\n")
    try:
        while True:
            now = datetime.datetime.now()
            if now.date() != last_date:
                last_date = now.date()
                notified_start.clear()
                notified_advance.clear()
            st = compute_state(cfg, now)
            adv = datetime.timedelta(minutes=cfg.get("advance_notify_minutes", 5))
            if st["status"] == "open":
                fresh = st["remaining"] >= (st["duration"] - datetime.timedelta(minutes=2))
                if cfg.get("notify_on_start", True) and st["current"] not in notified_start:
                    notified_start.add(st["current"])
                    if fresh:
                        alert_round(cfg, st)
            else:
                if adv.total_seconds() > 0 and st["countdown"] <= adv and st["next"] not in notified_advance:
                    notified_advance.add(st["next"])
                    alert_advance(cfg, st)
            render(cfg, now, st, notified_start)
            if key_pressed():
                break
            time.sleep(max(0.2, float(cfg.get("check_interval_seconds", 1))))
    except KeyboardInterrupt:
        pass
    finally:
        print("\n已退出，下次见~")


if __name__ == "__main__":
    main()
