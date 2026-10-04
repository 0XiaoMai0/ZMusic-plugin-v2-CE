# ZMusic 2.14.0 CE

本版基于官方 tag **2.14.0**，提交 `174b50ab5253469b9cd41584994ec67b1b8cbea5`，版本 `2.14.0-CE.6`。保留作者真心的信息与 GPL-3.0 许可证。旧 CE 仅参考音乐源接入，没有采用官方 dev/V5。

CE.6 移除官方更新接口及启动、管理员进服时的检查入口；默认关闭更新检查，旧配置的 `check-update` 自动保存为 `false`。`/zm update` 只显示社区版更新说明，避免安装官版覆盖社区版。详见 [CE.6 更新检查修复](CE6-关闭官方更新检查.md)。

CE.5 新增 QQ/微信扫码、QQ 手机短信和酷我密码/短信登录，自动创建凭据文件，修复 QQ 播放鉴权和酷我官网请求校验。使用方式见 [音乐账号登录](音乐账号登录.md)，本轮结果和未完成的真实账号验收见 [CE.5 验证报告](CE5-原生登录与QQ播放修复报告.md)。

CE.4 补齐所有指令的 Tab 参数树，按权限提示子命令、音乐平台、登录方式、歌单操作、已导入歌单编号、当前曲目序号和搜索缓存。本轮验证见 [指令补全报告](CE4-指令补全报告.md)。保留 CE.3 的中文提示和酷狗账号兼容修复，其历史记录见 [中文提示修复报告](CE3-中文提示修复报告.md)。下文原有完整播放及跨版本验证来自 CE.2，未在 CE.6 重跑全部矩阵。

网易云、QQ、酷狗、酷我与 B 站均通过原有客户端模组协议在游戏内点歌、放歌和停止，**无需打开网页播放器**。公开样例的验证范围见 [验证报告](CE-TEST-REPORT.md)。会员、购买、地区和版权权限仍由音乐平台决定。

## 安装和兼容

把 `ZMusic-CE-2.14.0.jar` 放入服务器 `plugins`，移走旧 ZMusic Jar 后正常重启。客户端安装与自身 Minecraft 版本及加载器匹配的 [官方 ZMusic Mod](https://github.com/starhui-dev/zmusic-mod)。插件负责选歌、协议和歌词，模组负责声音解码。

目标字节码为 Java 8；服务器使用核心要求的 Java。标准 Bukkit 消息替代逐版本 NMS，保留 1.8 ActionBar 反射及聊天降级。实测服务器覆盖 1.8.8、1.12.2、26.2、26.3 测试版，中间版本按公开 API 设计兼容，未逐个实测。官方模组没有 Forge 1.8 版本，服务器兼容 1.8 不代表每个旧客户端都有对应模组。

服务端没有原生二进制，无需 FFmpeg、Node.js 或 Python；纯 Java 逻辑设计支持 Windows、macOS、Linux。本次实际运行环境为 Windows。

## 点歌和歌词

输入 `/zm ` 后按 Tab 查看可用指令；选中子命令并输入空格后继续按 Tab。例如 `/zm play ` 补全平台，`/zm login kugou ` 补全登录操作，`/zm playlist 163 play ` 补全自己已导入的歌单编号。`/zmusic` 和 `/music` 使用同一套补全。补全读取本地状态和短期搜索缓存，不请求音乐平台；自由输入的歌名可以直接输入，Cookie、密码和验证码不会被回显为候选。

```text
/zm search 163 歌名
/zm search qq 歌名
/zm search kugou 歌名
/zm search kuwo 歌名
/zm search bilibili 视频名
/zm play kugou 歌名
/zm play bilibili BV1GJ411x7h7
/zm stop
/zm diagnose kugou 歌名
```

点击搜索结果按歌曲 ID 播放，短期缓存保留曲名和歌手。原有模组音量操作继续控制声音。`diagnose` 需要 `zmusic.admin`，记录搜索、真实音频和歌词，省略凭据及签名媒体地址。

LRC 支持多时间标签、毫秒、offset 和平台翻译。1.8 默认降级为 ActionBar；酷狗使用独立歌词接口，酷我 H5 失败时使用压缩 LRC。没有歌词的 B 站视频不编造歌词。

空地址、HTTP 403、HTML 错误页会报告失败。酷我返回明显短于歌曲/歌词时间轴的片段或提示音时会拒绝下发。

## B 站游戏内播放

从独立 DASH AAC 音轨读取分片 MP4，用 JAAD 和 jump3r 在内存中适配为 128 kbps MP3，再通过原有 `[Play]` 协议交给模组。**不调用 FFmpeg，不把音频保存到服务器磁盘。** 首次点歌有下载及适配延迟，实测样例约数秒到十余秒；同歌多人请求复用结果。

缓存最多 64 MiB、16 首，空闲 30 分钟后在后续点歌时清理；单个输入/输出各最多 24 MiB，两首同时适配。超长视频可能超过限制。

```json
"audio-stream": {
  "enabled": true,
  "bind": "0.0.0.0",
  "port": 18081,
  "public-url": "http://127.0.0.1:18081"
}
```

`public-url` 必须是**玩家客户端能够访问的地址**。同机测试可用 `127.0.0.1`；公网服须改成服务器域名/IP，开放端口或配置 HTTPS 反向代理。这是模组自动读取的音频端点，不需要玩家打开网页。多实例使用不同端口。

端点支持 HEAD、真实 Content-Length 和单区间 Range，使用随机令牌。媒体来源限定 B 站 HTTPS CDN，不向 CDN 转发 Cookie。重载/关闭时释放服务及缓存。旧候选版的 `web-player` 配置自动迁移为 `audio-stream`，其他设置保留并备份。

## 账号配置

不需要把密码或 Cookie 发到聊天里。详细操作见 [酷狗账号配置](酷狗账号配置.md)。保存到 UTF-8 文件 `plugins/ZMusic/kugou-cookies.txt` 后，每次请求都会重新读取，无需重启。

QQ 和酷我可以直接使用插件命令登录，无需手动复制 Cookie；首次启动自动创建缺少的账号文件，保留已有文件。详见 [音乐账号登录](音乐账号登录.md)。

其他平台文件为 `cookies.txt`（网易云）、`qq-cookies.txt`、`kuwo-cookies.txt`、`bilibili-cookies.txt`。`/zm login kugou status` 只检查是否保存，具体歌曲权限用 `/zm diagnose kugou 歌名` 验证。公开样例不需要账号；会员曲目未提供有效账号时不能宣称验证完成。

网易云默认 `api.netease = "direct"`，内置搜索、播放、歌词、歌单和 Cookie 状态。二维码、短信或密码登录需要自建兼容 NeteaseCloudMusicApi 并配置 URL。

## 构建和复测

```text
./gradlew clean build
./gradlew :zmusic-plugin:test -Dzmusic.liveTests=true
```

Java 21 toolchain 构建，输出 Java 8 字节码。Gson、JAAD、jump3r 重定位，第三方许可证随 Jar 提供，编解码库源码位于 `third-party/sources`。

`scripts` 仅供开发测试，不部署到服务器。`native-mod-smoke.cjs` 将真实玩家收到的 URL 送入官方模组 `ZMusicPlayer` 源码与官方原生库，检查持续播放、停止和歌词包；`setup-native-test.ps1` 准备 Windows 测试环境。该结果不是完整 Minecraft GUI 人工听音验收。其余脚本只检查协议/歌词。

网络诊断保存真实观察值，详见 `verification`。会员账号、macOS/Linux 运行及未覆盖版本需要在相应环境验收。
