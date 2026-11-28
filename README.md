# GPS 悬浮仪表 (GPS Overlay HUD)

一款**纯本地离线**、采用 **Google Material 3 + Jetpack Compose** 构建的 Android 骑行与户外专业遥测仪表盘应用。支持全局悬浮窗、横竖屏自适应 G 力最大轮廓记录、多模式轨迹后台保活录制、蓝牙心率融合、专业地形难度分析及社交分享图一键生成。

---

## 核心功能一览

### 1. 全局实时悬浮仪表（Overlay HUD）
- 基于 Android `WindowManager` 始终悬浮于地图导航或其他应用之上，单击可快速切换标准 / 精简模式。
- **高度可定制**：支持自由开关速度、G 力雷达图与峰值、海拔、蓝牙心率、航向罗盘、定位精度、经纬度坐标；支持整体尺寸缩放、骑行大字号调节、背景透明度调节，并**分别记忆竖屏与横屏的拖动位置**。
- **横屏摩托车适配**：支持横屏宽屏驾驶舱布局，横屏状态下 G 力方向与坐标系自动按横屏视角映射展示。
- **G 力最大形状记录**：实时在圆形雷达图上描绘各方向（左/右过弯、加速、重刹）最大 G 力包络轮廓，支持自定义峰值保留时长（10 秒 / 30 秒 / 1 分钟 / 3 分钟 / 永久）与一键水平零点校准。
- **安全预警**：可自定义超速阈值与心率上限，触发时悬浮窗与仪表卡片柔和变红并短震动提醒。

### 2. 硬件遥测详情页（Material 3 风格）
- **海拔与气压**：支持米/英尺切换、实时海拔变化曲线、估算坡度与升降速率，支持硬件气压计（QNH 参考海平面气压校准）。
- **指南针与航向**：真北/磁北切换、磁偏角显示、GPS 卫星行进航向对比、地磁干扰提醒及俯仰/横滚双轴水平仪。
- **GNSS 卫星信号**：实时北斗 / GPS / GLONASS / Galileo 卫星星空分布图与各通道信噪比明细。
- **专注防打扰模式**：通过屏幕固定 + 勿扰联动减少骑行途中通知弹窗与误触干扰。

### 3. 蓝牙心率融合（BLE）
- 支持小米手环与标准 BLE 心率带（Heart Rate Service `0x180D`）。
- 置顶显示**本机已连接与系统已配对设备**，提供易读的信号强度等级、距离估算、设备搜索与手环过滤，并支持**记住上次连接设备与打开自动重连**。

### 4. 三大轨迹录制模式（前台服务后台保活）
内置轻量前台定位保活服务，息屏放口袋长时间录制不断流；首次安装自动附带三条示例轨迹供直接体验：
- **卡丁车（Karting）**：第一次在发车区正常开始录制，结束后在记录详情上为主赛道设置有方向的起终线，可选加一道检查线并重算。下次录制前点击「使用上次的计时线」可实时计圈。第一次有效正向过线开始计时，下一次才完成一圈；定位断点不会被插值成圈。旧记录没有 GPS 采样时钟，重算结果是估算值。
- **徒步（Hiking）**：累计爬升/下降、最高/最低海拔、平均配速，以及**海拔 / 速度 / 心率 X 轴对齐分栏同步剖面图**（手指按住滑动即可查看该时刻所有指标并联动路线定位）。
- **越野穿越（Off-road Crossing · 摩托车翻山）**：专为骑摩托车翻山越岭打造，包含平均/最高时速、峰值横向压弯 G 力、同步多维剖面图，以及专属的：
  - **爬升难度评价（S1 轻松巡山 ~ S4 极限天路）**：综合指数、最大坡度、平均上坡、爬升密度与陡坡占比。
  - **路面复杂程度统计（T1 平顺铺装 ~ T4 极难越野）**：平顺 / 盘山弯道 / 复杂非铺装三段占比条、弯道密度、急弯/发卡弯数量、G 力颠簸指数与车速波动。

### 5. 社交分享图制作与 AI 遥测导出
- **社交分享图制作器**：支持通过零权限系统相册导入骑行/徒步照片，或选用**真透明 PNG** 与 6 套纯色渐变背景；可自由勾选叠加矢量轨迹路线、标题日期、里程、用时、爬升/圈速、速度、心率、峰值 G 力、剖面曲线以及越野穿越专属的「爬升难度 / 路面复杂度」徽章，支持保存相册或直接分享。
- **结构化 JSON + AI 分析 Prompt**：导出 JSON 时可勾选附带针对卡丁车 / 徒步 / 越野穿越定制的 AI 教练分析提示词，一键复制到剪贴板粘贴给 AI 即可生成复盘报告；同时支持标准 GPX 1.1 轨迹文件导出。

---

## 如何打包正式版（Release APK / AAB）

项目中的 `app/build.gradle.kts` 已预配置正式版签名读取规则（`keyAlias = "upload"`，通过环境变量读取密钥路径与密码）。以下提供 **GitHub 自动发版** 与 **本地手动打包** 两种方式。

### 第一步：云端一键生成正式签名密钥（无需本地安装 Java，推荐）

如果你的电脑没有安装 Java / `keytool`，可以直接在 GitHub 云端一键生成与本项目 `app/build.gradle.kts` 严格匹配（`alias = upload`）的正式签名证书：

1. （可选，公开仓库强烈推荐）先到仓库 **Settings → Secrets and variables → Actions** 提前建好 `STORE_PASSWORD` 和 `KEY_PASSWORD` 两个密码 Secret；若未提前设置，云端会自动生成 20 位高强度随机密码。
2. 打开仓库顶部 **Actions** → 左侧选择 **Generate Release Keystore (Cloud)** → 点击右侧 **Run workflow**（默认主体为 `CN=GPSHelper, O=Rhoeasy, C=CN`，可按需修改）并等待约 20 秒完成。
3. 在本次运行页面底部的 **Artifacts** 下载 `release-keystore-bundle.zip` 并解压，里面包含：
   - `my-upload-key.jks`：你的正式签名证书文件（请本地妥善备份，切勿提交到仓库）
   - `KEYSTORE_BASE64.txt`：已转换好的单行 Base64 文本
   - `SETUP_INFO.txt`：完整的配置说明与密码
4. **重要安全提示**：复制完成后，请立即在 GitHub Actions 页面删除这一次 `Generate Release Keystore (Cloud)` 运行记录，避免 Artifact 被他人下载。

<details>
<summary>备选：如果你本地已有 Java / Android Studio，也可以本地命令行生成</summary>

```bash
keytool -genkeypair -v \
  -keystore my-upload-key.jks \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -alias upload \
  -dname "CN=GPSHelper, O=Rhoeasy, C=CN"

# 转为单行 Base64
base64 -i my-upload-key.jks | tr -d '\n'
```

</details>

---

### 第二步：配置 GitHub Actions 自动打正式包 + 自动打 Tag

本仓库的 `.github/workflows/android.yml` 已配置完整的自动发布流水线。

1. 打开 GitHub 仓库 → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**，新增以下 3 个 Secret：

   | Secret 名称 | 说明 |
   | :--- | :--- |
   | `KEYSTORE_BASE64` | 第一步生成的 `my-upload-key.jks` 的 Base64 字符串 |
   | `STORE_PASSWORD` | 密钥库密码（keystore password） |
   | `KEY_PASSWORD` | 别名 `upload` 的密钥密码（key password） |

2. 确认仓库 Actions 写入权限：进入 **Settings** → **Actions** → **General** → **Workflow permissions**，勾选 **Read and write permissions**（用于自动推送 Tag 与发布 GitHub Release）。

3. 以后每次代码推送到 `main` 或 `master` 分支时，GitHub Actions 会自动执行：
   - 运行全部单元测试（`:app:testDebugUnitTest`）
   - 自动按语义化版本规则递增并推送 Git Tag（如 `v1.0.0` → `v1.0.1`）
   - 编译正式签名的 `GPS-HUD-vX.Y.Z-release.apk`（可直接装到手机）与 `GPS-HUD-vX.Y.Z-release.aab`（可上架 Google Play）
   - 自动创建对应版本的 **GitHub Release** 并挂载 APK 与 AAB 下载附件

#### 自动打 Tag 版本递增规则

| 提交信息示例 | 递增类型 | 版本示例 |
| :--- | :--- | :--- |
| 普通提交（如 `fix: 修复海拔显示`） | `patch`（默认） | `v1.0.0` → `v1.0.1` |
| `feat: 新增越野模式` 或包含 `[minor]` | `minor` | `v1.0.1` → `v1.1.0` |
| 包含 `[major]` 或 `BREAKING CHANGE` | `major` | `v1.1.0` → `v2.0.0` |

也可以在 GitHub 仓库的 **Actions → Android Release & Auto Tag → Run workflow** 中手动指定 `patch` / `minor` / `major` 触发发版。

---

### 备选：本地命令行编译正式包

如果你希望直接在本地电脑打包：

```bash
export KEYSTORE_PATH="$(pwd)/my-upload-key.jks"
export STORE_PASSWORD="你的密钥库密码"
export KEY_PASSWORD="你的密钥密码"

# 编译正式版 APK（安装测试/分发）与 AAB（应用商店）
gradle assembleRelease bundleRelease
```

产物输出路径：
- 正式版 APK：`app/build/outputs/apk/release/app-release.apk`
- 正式版 AAB：`app/build/outputs/bundle/release/app-release.aab`

---

## 技术栈与本地开发

- **语言与 UI**：Kotlin + Jetpack Compose + Material Design 3（Edge-to-Edge、自适应竖屏/横屏与平板布局）
- **数据存储**：AndroidX Room（本地离线 SQLite 存储轨迹、采样点与圈速）
- **硬件与传感器**：Android `LocationManager` + `GnssStatus` + `SensorManager`（加速度/线性加速度/旋转矢量/磁力计/气压计）+ `BluetoothGatt`（BLE 心率）
- **媒体与分享**：零权限 Android Photo Picker（`PickVisualMedia`）、`MediaStore` PNG 保存、`FileProvider` 分享
- **运行单元测试**：

```bash
gradle :app:testDebugUnitTest
```
