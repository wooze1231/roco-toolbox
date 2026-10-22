# 洛克助手（Roco Assistant）

《洛克王国世界》的**个人向**辅助工具：远行商人提醒、异色图鉴、炫彩色卡收录。

> ⚠️ 非官方项目，与腾讯及游戏官方无关。**请先阅读 [DISCLAIMER.md](DISCLAIMER.md)**。
> 仓库**不包含任何游戏素材**（色卡图、立绘、字体），需要自行获取。

## 功能

| 模块 | 说明 |
|---|---|
| 远行商人 | 每轮商品记录、倒计时、桌面组件、订阅提醒、历史轮次 |
| 异色图鉴 | 精灵异色记录、多选录入、截图 OCR 识别（离线） |
| 炫彩色卡 | 底色 / 混色 / 粒子三维记录，按类目筛选、搜索、分享、JSON 导入导出 |

## 构建

```bash
# 需要 JDK 21 + Android SDK（compileSdk 34）
cd android-app
./gradlew :app:assembleDebug          # 正式渠道 com.wooze.rocomerchant
./gradlew :app:assembleBetaDebug      # 测试渠道 com.wooze.rocomerchant.test（可与正式版并存）
```

### 缺失素材

仓库不含游戏素材，以下目录需自行补齐后重新构建：

```
android-app/app/src/main/assets/particles/   # base.png wave.png diamond.png heart.png star5.png
android-app/app/src/main/assets/cards/       # 赛季 / 黑白成品图
android-app/app/src/main/assets/fonts/       # ocr_subset.ttf（请换用可自由分发的字体）
```

缺失时应用仍可运行，只是色卡渲染会退化为纯色块、OCR 不可用。

## 目录

```
android-app/         Android 工程（Kotlin，无第三方 UI 框架）
  app/src/main/assets/   内置数据（图鉴 / 精灵 / 类别）
  app/src/main/java/...  业务代码
ocrexp/mock/         设计稿与可交互原型（HTML）
tools/               快照 / 回滚脚本
build_*.py           数据抓取与构建脚本
```

## 许可

- **代码**：MIT
- **数据**：来自 BWIKI 等社区站点，遵循其原始许可（CC BY-NC-SA），**不可商用**
- **游戏素材**：版权归腾讯及相关权利人，本仓库不分发

详见 [DISCLAIMER.md](DISCLAIMER.md)。
