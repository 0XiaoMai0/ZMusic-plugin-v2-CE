# 仓库贡献指南

## 基线与范围

社区版基于官方 tag `2.14.0`，不要改用 dev/V5 或旧 CE 作为主分支。
主插件位于 `zmusic-plugin`，Addon 位于 `zmusic-addon`。逐版本 `zmusic-nms` 模块已移除。
跨版本通信使用公开 Bukkit API；1.8 ActionBar 反射实现保留于主插件 `nms` 包。
回复、注释和提交信息使用简体中文；代码、路径、API 字段和日志原文保持原样。

## 构建与测试

使用 Java 21 toolchain 和 Gradle Wrapper，编译目标 `--release 8`。
运行服务器的 Java 版本由服务器核心决定。

- `./gradlew clean build`：构建主插件和 Addon。
- `./gradlew :zmusic-plugin:test -Dzmusic.liveTests=true`：执行单元测试并记录真实音乐源诊断。
- `scripts/protocol-smoke.cjs`：实际玩家协议与歌词验证。
- `scripts/source-packets.cjs`：逐个音乐源验证播放包和有文本的歌词。

产物 `zmusic-plugin/build/libs/ZMusic-CE-2.14.0.jar` 包含重定位 Gson。
测试工具需要 Node.js，但服务器运行不需要 Node.js、Python 或 FFmpeg。
修改网络与歌词接口后使用公开曲目复测，会员曲目需要真实账号和权限。
接口失败必须如实记录；收到 URL、收到播放包、原生库解码、真实游戏模组播放是不同证据。

## 实现约束

B 站必须通过原有模组协议在游戏内播放，使用纯 Java AAC→MP3 适配；不得加入转码二进制、音频落盘或浏览器播放入口。
使用公开 API 和反射保持旧服兼容。玩家 API 调用经 `BukkitTaskScheduler` 调度。
不要向日志和诊断报告写入 Cookie、密码、签名媒体地址或浏览器会话令牌。
不要把账号、测试服世界、构建输出或 node_modules 加入源码交付。
保留作者信息和 GPL-3.0 许可证，交付 Jar 时附对应可构建源码。
跨平台和中间游戏版本未运行实测时，只说明设计兼容，不能宣称全面验证。
