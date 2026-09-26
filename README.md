# 钱袋子 Android

钱袋子的原生 Android 客户端，使用 **Kotlin + Jetpack Compose** 从零构建，**不是 WebView 套壳应用**。
所有数据通过 API 与 [qiandaizi](https://github.com/qiancheng817/qiandaizi) 后端实时同步

## 下载安装

- 最新版本：**v1.9.0**
- 下载地址：[Qiandaizi-v1.9.0.apk](https://github.com/qiancheng817/qiandaizi-app/releases/download/v1.9.0/Qiandaizi-v1.9.0.apk)（约 12.5 MB，正式签名包）
- 系统要求：Android 8.0（API 26）及以上
- 安装时如提示风险，请选择"允许来自此来源的应用 / 继续安装"
- App 内支持检查更新：启动时静默检查，也可在「更多 → 关于我们 → 检查更新」手动触发并直接下载安装

## 功能特性

- 服务器地址 + 账号密码登录，支持多服务器、多账号
- 可随时切换服务器、切换账号或退出登录
- 账单数据与后端实时同步，自动携带登录凭证与账本
- 首页三个钱袋：我的钱袋 / 对方的钱袋 / 总钱袋，一行气泡左右切换，默认显示总钱袋
- 完整记账：收支类型、金额、分类、日期、账户、备注、归属人等
- AI 记账：自然语言一句话记账，支持 AI 结果编辑与删除
- 统计报表：收支概览、分类统计、归属统计、每日 / 月度趋势
- 预算、类目、钱包、攒钱目标、周期账单、预设账单等全套管理功能
- 回收站、操作日志、月账单、AI 模型设置、用户管理（管理员）
- 暖黄色主题、圆角卡片、底部凸起记一笔按钮，适配手机操作

## 底部导航

| Tab | 说明 |
| --- | --- |
| 首页 | 三钱袋气泡切换、当月收支卡片、预算进度、近期账单 |
| 统计 | 收支概览、分类 / 归属 / 每日 / 月度统计图表 |
| 记一笔 | 中间凸起的 **+** 按钮，手动记账表单 |
| AI 记账 | 自然语言 / 图片 AI 记账，结果确认后入账 |
| 更多 | 常用功能与设置入口的宫格分组页面 |

## 使用前提

本 App 只是客户端，需要自行部署后端服务：

1. 部署 [qiandaizi](https://github.com/qiancheng817/qiandaizi)（Node.js + Express + SQLite），默认端口 `9600`，接口前缀 `/api`
2. 手机与服务器网络可达（同一局域网或服务器有公网地址）
3. 打开 App，输入服务器地址，例如 `http://192.168.1.10:9600`
4. 使用后端账号密码登录即可

## 技术栈

- Kotlin 2.1、AGP 8.7、JVM 17
- Jetpack Compose（BOM 2024.12）、Material 3、material-icons-extended
- Retrofit 2.11 + OkHttp 4.12 + kotlinx.serialization
- DataStore（本地会话存储）、Coil（图片加载）
- compileSdk / targetSdk 35，minSdk 26

## 在线构建

项目通过 GitHub Actions 在线构建（本机无需 Android 环境）：

- 推送到 `main` 分支自动执行 [.github/workflows/build.yml](.github/workflows/build.yml)
- 自动执行 `assembleRelease`（正式签名包），并将 APK 发布到 Release（tag `v1.5.0`）
- 构建产物同时以 artifact（钱袋子-APK）形式保留 30 天

## 本地构建

如需本地编译，需安装 JDK 17 与 Android SDK：

```bash
./gradlew assembleDebug      # Debug APK
./gradlew assembleRelease    # Release APK
```

产物路径：`app/build/outputs/apk/debug/app-debug.apk`

## 项目结构

```
app/src/main/java/com/qiandaizi/app
├── MainActivity.kt          # 入口 Activity
├── core                     # 网络层、数据模型、会话、主题、全局状态
└── ui
    ├── RootGate.kt          # 路由与底部 5 Tab 主框架
    ├── home                 # 首页（三钱袋）
    ├── stats                # 统计报表
    ├── record               # 记一笔（手动记账）
    ├── ai                   # AI 记账
    ├── auth                 # 服务器配置 / 登录
    ├── common               # 通用组件与图表
    └── more                 # 更多页及各管理子页面
```

## 相关仓库

- 后端服务与 Web 端：[qiancheng817/qiandaizi](https://github.com/qiancheng817/qiandaizi)
- 设计风格参考：[qiancheng817/qiandaizi-app](https://github.com/qiancheng817/qiandaizi-app)
