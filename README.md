## 📱 安装说明

**直接下载 APK：** 打开本仓库的 [Actions](../../actions) 页面 → 点最新一次 `Build APK` → 页面底部 **Artifacts** 里下载 `app-debug-apk` → 解压得到 `app-debug.apk` → 传到手机安装。

> 小米手机安装时若提示，需允许「安装未知来源应用」。

---

# XiaomiRawEditor · RAW 自然调色

一个轻量的 Android 图片 / RAW 调色 App：**真实自然、细节保留、明暗对比、上手容易**。

打开 → 选图 → 选风格 → 拖强度 → 导出 JPEG。三步完成。

## 风格（4 套自然风格）

| 风格 | 说明 |
|---|---|
| **Natural 自然** | 色彩真实，轻微提亮对比，最大限度保留细节 |
| **Natural+ 立体** | 在原色基础上加强明暗对比，画面更立体通透 |
| **Film Soft 柔和** | 胶片般柔和过渡，高光微收，层次丰富自然 |
| **Clarity 细节** | 强化局部细节与质感，保持自然不锐化过头 |

每套都有 **强度滑杆（0–100%）**，可自由控制风格浓度。

处理顺序：`曝光 → 对比 → 高光/阴影 → 色温/色调 → 饱和度 → 清晰度 → 锐化`，
高光抑制与阴影提亮保证亮部暗部都不糊，明暗对比自然不生硬。

## 如何拿到 APK

本项目用 **GitHub Actions 自动编译**，无需本地装 Android SDK。

1. 代码推到本仓库（`main` 分支）。
2. 打开 **Actions** 页，等 `Build APK` 跑完（约 3–5 分钟）。
3. 在运行记录里下载 **Artifacts → `app-debug-apk`**（zip 内含 `app-debug.apk`）。
4. 传到手机（数据线 / 网盘 / 微信文件传输）。
5. 手机上安装；若提示，允许「安装未知来源应用」。

## 本地构建（可选）

需要 **JDK 17 + Android SDK**（推荐直接装 Android Studio）：

```bash
gradle assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## RAW 支持说明

- 标准 **DNG**：通过 Android 系统媒体栈（`ImageDecoder`）解码，多数现代机型可用。
- **UltraRAW / 厂商私有 RAW**：可能是私有封装，第三方 App 通常**无法**读取；若解码失败，App 会给出明确提示，不影响普通图片与其他 DNG 的使用。

## 项目结构

```
app/src/main/
├── java/com/raweditor/app/
│   ├── MainActivity.kt      # 单页 UI：选图 → 风格 → 强度 → 导出
│   ├── RawDecoder.kt        # 图片 / DNG 解码
│   ├── ColorPipeline.kt     # 调色管线（ColorMatrix + 逐像素色调 + 锐化）
│   └── Presets.kt           # 4 套自然风格
├── cpp/raw_decoder.cpp      # 原生曝光/对比预处理（NDK）
└── res/                     # 布局与资源
```
