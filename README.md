# ZMusic CE — 游戏内多平台点歌

当前版本 **2.14.0-CE.6**，基于官方 **2.14.0**，保留原作者真心与 GPL-3.0 许可证。支持网易云、QQ、酷狗、酷我和哔哩哔哩，通过官方 ZMusic 客户端模组在游戏内播放音乐。

[下载发布包](https://github.com/0XiaoMai0/ZMusic-plugin-v2-CE/releases) · [更新说明](CHANGELOG.md) · [完整使用说明](README-CE.md) · [音乐账号登录](音乐账号登录.md) · [MineBBS](https://www.minebbs.com/resources/zmusic-ce-qq.16728/)

## 这次更新

- 从旧 CE 的 2.12.0 基线更新到官方 2.14.0；移除逐版本 NMS 模块，改用公开 Bukkit 消息接口，保留 1.8 ActionBar 降级。
- 接入网易云、QQ、酷狗、酷我的搜索、播放与歌词；缓存搜索元数据，点击结果按歌曲编号播放。
- B 站使用纯 Java 在内存中将 AAC 音轨适配为 MP3，再由原有模组播放，不需要 FFmpeg、原生转码程序或网页播放器。
- QQ 加入 QQ/微信扫码与手机短信登录流程；酷我加入图形验证码后的账号密码、手机短信登录流程。成功登录保存凭据，首次启动自动创建缺少的账号文件。
- 修复 QQ 播放鉴权字段和酷我官网请求校验，兼容酷狗多种账号字段；明确提示账号、会员、版权或试听限制。
- 播放、登录、网络失败和诊断摘要使用中文，补齐 `/zm`、`/zmusic`、`/music` 的 Tab 补全与权限检查。
- 移除官方更新接口以及启动、管理员进服的官方检查，旧配置的 `check-update` 自动关闭；`/zm update` 仅说明社区版更新方式。

## 安装

1. 正常停止服务器并备份原插件配置、账号和歌单。
2. 移走旧 ZMusic Jar，把 `ZMusic-CE-2.14.0.jar` 放入 `plugins`，避免同时加载多个版本。
3. 客户端安装与 Minecraft 版本和加载器匹配的 [官方 ZMusic Mod](https://github.com/starhui-dev/zmusic-mod)。
4. 启动服务器。公网服使用 B 站播放或登录二维码时，配置 `audio-stream.public-url` 为管理员/玩家能访问的服务器地址。

示例配置：

```json
"audio-stream": {
  "enabled": true,
  "bind": "0.0.0.0",
  "port": 18081,
  "public-url": "https://music.example.com"
}
```

`public-url` 可以通过 HTTPS 反向代理连接服务端端口；同机测试用 `http://127.0.0.1:18081`。远程玩家不能使用服务器的 `127.0.0.1`。这个端点供游戏模组读取音频和管理员查看短期登录图片，不是网页播放器。

## 常用命令

输入命令后按 Tab 查看子命令与参数；歌曲搜索结果进入短期缓存后，可以补全已有曲名和编号。

```text
/zm search qq 歌名
/zm play 163 歌名
/zm play qq 歌名
/zm play kugou 歌名
/zm play kuwo 歌名
/zm play bilibili BV1GJ411x7h7
/zm stop
/zm help admin
```

管理员登录与诊断：

```text
/zm login qq qr
/zm login qq qr wechat
/zm login qq sendcode +86 <手机号>
/zm login qq verify +86 <手机号> <短信验证码>
/zm login kuwo captcha
/zm login kuwo password <账号> <密码> <图形验证码>
/zm login kuwo sendcode <手机号> <图形验证码>
/zm login kuwo verify <手机号> <短信验证码>
/zm login qq status
/zm diagnose qq 歌名
```

登录需要 `zmusic.admin` 或服务器控制台，控制台命令不加 `/`。验证码会话需要同一管理员完成；扫码确认后自动保存，不用向开发者提供账号 Cookie。账号为服务器共用。酷狗与已有 Cookie 导入见 [酷狗账号配置](酷狗账号配置.md)，其他说明见 [音乐账号登录](音乐账号登录.md)。

网易云默认 `direct` 模式提供公开音乐接口；官方原有扫码、手机/邮箱密码、短信登录需要配置兼容 NeteaseCloudMusicApi 服务。QQ 密码登录与酷我扫码登录未接入。密码、验证码和 Cookie 不会被补全为候选。

## 兼容与实测范围

| 项目 | 范围 |
|---|---|
| 服务端 | 面向 Spigot / Paper / Purpur 系，目标 1.8 至 26.x；中间版本未逐个运行 |
| 历史服务器验证 | CE.2：1.8.8、1.12.2、26.2、26.3 BETA，详见历史报告 |
| 本轮服务器验证 | CE.6：Purpur 26.2 加载与旧更新开关迁移；70 项测试通过、1 项可选网络测试跳过 |
| 登录真实接口 | CE.5：QQ/微信二维码及等待状态、酷我图形验证码、无效手机号与错误验证码拒绝 |
| 真实账号登录 | 本人扫码确认、短信收取、账号密码和会员曲目尚未完成真人验收；授权交换等成功路径由模拟响应测试覆盖 |
| 操作系统 | 纯 Java 设计兼容 Windows / macOS / Linux；目前实际运行环境为 Windows |
| 字节码 | Java 8；服务器运行 Java 版本按核心要求选择，构建使用 Java 21 |

公开歌曲的音频字节探测、歌词取得、官方模组解码核心验证与完整 Minecraft GUI 人工听音属于不同证据。不能据公开样例认为所有曲目或所有账号可用。官方客户端模组也需要对应版本：服务器兼容 1.8 不代表每种 1.8 客户端都有可用模组。

会员、购买和地区版权权限由音乐平台决定。例如 QQ《晴天》周杰伦原唱在未配置有效音乐凭据时没有取得 URL，而“宝宝巴士”的公开样例可取得 MP3。酷我验证的《晴天》是 KTV 伴奏版，不能据此认为原唱已通过。完整数据见 [CE.5 登录与播放报告](CE5-原生登录与QQ播放修复报告.md) 及 [CE.2 历史报告](CE-TEST-REPORT.md)。

## 构建

```text
./gradlew build
```

Windows 使用 `gradlew.bat build`。输出：`zmusic-plugin/build/libs/ZMusic-CE-2.14.0.jar`。Gradle 使用 Java 21 toolchain 并输出 Java 8 字节码。服务器运行不依赖 Node.js、Python 或 FFmpeg；`scripts` 仅供开发验证。Jar 约 1.62 MiB，包含重定位 Gson、JAAD、jump3r，第三方库对应源码随源码包提供。

## 来源与许可证

官方基线：[starhui-dev/zmusic-plugin 2.14.0](https://github.com/starhui-dev/zmusic-plugin/tree/2.14.0)，提交 `174b50ab5253469b9cd41584994ec67b1b8cbea5`。本项目为社区二次开发，保留原作者真心的信息与 [GPL-3.0](LICENSE)，交付插件时附对应可构建源码。

QQ 原生登录协议流程参考 [L-1124/QQMusicApi](https://github.com/L-1124/QQMusicApi)，作者 Luren 及项目贡献者，GPL-3.0，使用独立 Java 实现。第三方说明见 [third-party/README.md](third-party/README.md) 与 Jar 中 `META-INF/licenses/THIRD-PARTY.txt`。
