# ShortTV — Android TV 短视频客户端

参考 **myDV 2.1.37**（`com.github.mytv.dv`，抖音第三方电视端）逆向分析结果，
从零实现的同构 Android TV 应用。Kotlin + Jetpack Compose + Media3 ExoPlayer。

## 交付物

| 文件 | 说明 |
|---|---|
| `ShortTV-1.2.0-debug.apk` | 可直接 sideload 安装的调试包（27.4 MB，minSdk 23 / targetSdk 35，默认抖音直连） |


| `ShortTV/` | 完整 Gradle 工程源码（28 个 Kotlin 文件） |
| `ShortTV/APK_ANALYSIS.md` | myDV 2.1.37 逆向分析报告 |

## 功能（与 myDV 同构）

- **推荐信息流**：全屏竖屏视频流，上下滑动切换，自动连播，播放进度本地持久化
- **播放器**：ExoPlayer + 封面渐显 + 右侧互动栏（头像/点赞/评论/收藏/分享/关注）
  + 双击点赞爱心动画 + D-pad 控制栏（播放/快进/快退/进度/倍速/弹幕开关/小窗）
- **弹幕**：Canvas 自绘滚动弹幕，字号/透明度/速度可调，点播 drip-feed + 直播实时推送
- **直播**：直播间网格、HLS 播放、清晰度切换、在线人数、直播弹幕与聊天列表
- **搜索**：热搜榜、输入联想、结果网格，点击进入连播
- **用户主页 / 合集 / 收藏**：作品网格、剧集列表、本地收藏管理
- **双端自适应**：自动识别 TV（Leanback）与手机/平板
  - 手机：竖屏锁定（不随传感器旋转）、底部导航栏、上下滑动切换视频、信息栏/互动栏常驻、控制栏点击唤出
  - TV：横屏全屏、顶部页签栏、沉浸播放（正常播放时隐藏顶部/底部/互动等全部遮挡，仅保留弹幕；按键/点按唤出），全屏时遥控器上下键直接切换视频
- **设置**：数据源切换（直连 / 自建服务端 / 演示，含直连通性测试）、服务端地址配置、播放/弹幕/小窗开关
- **首启协议弹窗**、**PiP 小窗**（按 Home 自动进入）、崩溃场景兜底

## 数据来源

应用支持三种数据源，**默认「抖音直连」——安装后零配置开箱即连抖音官方数据**
（与 myDV 一致：无需自建服务端，端侧完成 a_bogus / __ac_signature 签名）：

| 数据源 | 说明 |
|---|---|
| **抖音直连（默认）** | 端内 QuickJS 运行 myDV 同款签名引擎（`browser_shim.js` → `abogus_dy.js` → `sign.js`），请求抖音开放 Web API，实时推荐流 / 搜索 / 用户作品 / 直播 |
| **自建服务端** | 设置页填入兼容 API 地址后，所有数据改为请求自建同构后端（签名由后端承担） |
| **演示模式** | 内置 10 个公开测试视频（Google gtv-videos-bucket MP4 / Apple HLS / Mux HLS）+ 6 个直播间 + 热搜/弹幕/评论演示数据 |

直连失败（风控/网络异常）时自动回退演示数据，保证 UI 始终可用；也可随时在
设置页切回演示模式。

### 端侧直连签名（对齐 myDV）

- **a_bogus**：`getABogus(query, data, uri, userAgent)` 由 bdms SDK 计算，
  追加在推荐/搜索/用户作品等 Web API 查询串后
- **__ac_signature**：`getMSSDKSignature(msStub, userAgent)` 经 byted_acrawler
  WASM 签名器计算，用于直播接口（`X-MS-STUB` 为请求体 MD5）
- 签名全程运行在单个 QuickJS 上下文（`quickjs-abogus` 单线程），
  JS 资产随 APK 打包：`abogus_dy.js` / `bdms.js` / `browser_shim.js` / `sign.js`

### API 协议（自建服务端模式，JSON 与 myDV 服务端同构）

```
GET {base}/api/v1/feed?cursor=&count=            → { code, data: { items: Aweme[], has_more } }
GET {base}/api/v1/live?cursor=&count=            → { code, data: { items: LiveRoom[] } }
GET {base}/api/v1/live/enter?room_id=            → { code, data: LiveRoom }
GET {base}/api/v1/search?keyword=&cursor=        → { code, data: { items: Aweme[] } }
GET {base}/api/v1/search/suggest?keyword=        → { code, data: [{ content, hint }] }
GET {base}/api/v1/hot_search                     → { code, data: [{ word, hot_value }] }
GET {base}/api/v1/user?sec_uid=                  → { code, data: { user: Author } }
GET {base}/api/v1/user/works?sec_uid=&cursor=    → { code, data: { items: Aweme[] } }
GET {base}/api/v1/mix?mix_id=&cursor=            → { code, data: MixPage }
GET {base}/api/v1/comment?aweme_id=&cursor=      → { code, data: { items: Comment[] } }
GET {base}/api/v1/danmaku?aweme_id=              → { code, data: { items: DanmakuItem[] } }
```

核心模型：`Aweme / Video / BitRate / PlayAddr / Author / Statistics / MixInfo /
LiveRoom / DanmakuItem / Comment`（字段命名与 myDV 一致，见 `app/.../data/model/Models.kt`）。

## 构建

```bash
# 要求：JDK 17+、Android SDK（platforms;android-35、build-tools;35.0.0）
export ANDROID_HOME=/path/to/android-sdk
cd ShortTV
./gradlew assembleDebug          # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # release 包（需自行配置签名）
./gradlew lintDebug              # 静态检查（0 error）
```

或直接用 Android Studio 打开 `ShortTV/` 目录。

## 安装到电视

```bash
adb connect <电视IP>:5555
adb install ShortTV-1.2.0-debug.apk
```

安装后电视桌面会出现 ShortTV 图标（含 320×180 TV Banner，支持 Leanback 启动器）。

## 工程结构

```
app/src/main/java/com/shorttv/app/
├── ShortTVApplication.kt          # Application（Coil ImageLoader + 直连 API 单例）
├── MainActivity.kt                # 单 Activity + PiP 回调
├── direct/                        # 抖音直连（myDV 同款签名引擎 + 官方 API 客户端）
│   ├── AbogusSigner.kt            # QuickJS 签名运行时（a_bogus / __ac_signature）
│   ├── DouyinIdentity.kt          # 设备指纹 / UA 生成
│   └── DouyinDirectApi.kt         # 抖音 Web API 客户端
├── data/
│   ├── model/Models.kt            # kotlinx.serialization 数据模型
│   ├── remote/ApiClient.kt        # OkHttp API 客户端
│   ├── demo/DemoData.kt           # 内置演示数据（公开测试流）
│   ├── SettingsRepository.kt      # DataStore 设置 + 历史/收藏
│   └── VideoRepository.kt         # API/演示数据编排与回退
└── ui/
    ├── AppRoot.kt                 # Navigation + 协议弹窗
    ├── MainViewModel.kt           # 全局状态
    ├── theme/                     # 深色电视主题
    ├── tabs/HomeTabsScreen.kt     # 主页签（推荐/直播/搜索/设置）
    ├── home/FeedScreen.kt         # VerticalPager 推荐流
    ├── player/                    # 播放页/控制栏/弹幕/评论/播放列表
    ├── live/                      # 直播列表 + 直播播放
    ├── search/                    # 搜索页
    ├── user/ mix/ settings/       # 用户/合集/设置/收藏
    └── components/                # TV 焦点容器、卡片
```

## 技术栈

| 项 | 版本 |
|---|---|
| AGP | 8.7.3 |
| Gradle | 8.10.2 |
| Kotlin | 2.1.0（Compose Compiler Plugin） |
| Compose BOM | 2024.12.01 |
| Media3 | 1.5.1 |
| Coil | 2.7.0 |
| OkHttp | 4.12.0 |
| kotlinx.serialization | 1.7.3 |
| compileSdk / minSdk / targetSdk | 35 / 23 / 35 |

## 电视端适配要点

- `LEANBACK_LAUNCHER` + `android:banner` + leanback/touchscreen 均声明 optional
- 自研 `Modifier.tvClickable`：统一处理 D-pad 中心键/Enter/触屏，焦点环 + 缩放反馈
- 播放器进度条使用 Material3 Slider（原生支持 D-pad 左右调节）
- 所有列表支持 D-pad 导航；深色主题常驻

## 已知边界

- 演示模式视频为公开测试流（Google/Apple/Mux 境外 CDN），国内网络大多无法播放——
  属预期行为，真实数据请使用默认的直连模式
- 直连模式依赖抖音开放 Web API 与端侧签名，实测结论（2026-10）：
  - `tab/feed` 为「刷新」语义：响应无游标，每页仅回 2~3 条（新访客冷启动），
    客户端自动补页至 6+ 条并按 aweme_id 去重
  - 播放地址必须走 `www.douyin.com/aweme/v1/play/` 中转（douyinvod 直链 403），
    ExoPlayer 已注入 UA + Referer
  - 响应 JSON 采用逐条容错解析：单条字段类型异常只跳该条并降级提取
    aweme_id/desc/播放地址，不会因一个字段拖垮整页
  - 直播列表（webcast/feed）项自带流地址可直接播放；部分直播间
    `web/enter` 受内容限制返回 4001038（与 myDV 实际行为一致），
    此时提示而不再放演示数据
- 若官方调整签名算法或触发风控，会自动回退演示数据；可在设置页切换数据源
- 直播弹幕在演示模式下由本地生成器按节拍推送；接入真实 WS 推送只需向
  `LiveDanmakuController.push()` 填充消息
