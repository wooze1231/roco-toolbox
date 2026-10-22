import re, os

BASE = "C:/Users/wooze/WorkBuddy/2026-09-22-16-31-24/roco-merchant-reminder/android-app/app/src/main"
PKG = os.path.join(BASE, "java/com/wooze/rocomerchant")

# 框架色 -> 语义 attr（金标记/红绿状态/ripple/android.R.color.* 不在表内，保留 R.color.*）
MAPPING = {
    'page_bg': 'colorBg', 'card_bg': 'colorSurface', 'panel_bg': 'colorSurface', 'row_bg': 'colorSurface',
    'text_primary': 'colorOnSurface', 'text_secondary': 'colorOnSurfaceVariant', 'text_sub': 'colorOnSurfaceVariant',
    'text_soft': 'colorOnSurfaceVariant', 'text_danger': 'colorError', 'divider': 'colorOutline',
    'stroke_soft': 'colorOutline', 'line_soft': 'colorOutlineVariant', 'arrow_soft': 'colorOnSurfaceVariant',
    'row_soft_bg': 'colorSurfaceVariant', 'row_sel_bg': 'colorPrimaryContainer', 'row_danger_bg': 'colorErrorContainer',
    'chip_bg': 'colorSurfaceVariant', 'input_bg': 'colorSurfaceVariant', 'text_accent': 'colorPrimary',
    'pill_yellow': 'colorPrimaryContainer', 'pill_yellow_text': 'colorOnPrimaryContainer'
}

ACTIVITIES = ['MainActivity.kt', 'SettingsActivity.kt', 'ShinyActivity.kt', 'ShinySettingsActivity.kt',
              'WikiActivity.kt', 'HistoryActivity.kt', 'ItemEditActivity.kt', 'SubscribeActivity.kt']
UI_KT = [os.path.join(PKG, f) for f in ACTIVITIES] + [os.path.join(PKG, 'UiDialogs.kt')]

for path in UI_KT:
    s = open(path, encoding='utf-8').read()
    orig = s
    # 1) 所有框架色 R.color.X -> R.attr.ATTR（裸引用/三目/变量里都改；避免误伤 android.R.color.*）
    for name, attr in MAPPING.items():
        s = re.sub(r'(?<!android\.)R\.color\.%s\b' % re.escape(name), 'R.attr.%s' % attr, s)
    # 2) 残留的 ContextCompat.getColor( -> resolveColor(（gold/red/green/ripple/变量都会走到这里）
    s = s.replace('ContextCompat.getColor(', 'resolveColor(')
    if s != orig:
        open(path, 'w', encoding='utf-8').write(s)
        print('patched', os.path.basename(path))
    else:
        print('nochange', os.path.basename(path))
print('DONE')
