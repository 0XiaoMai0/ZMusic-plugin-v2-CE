# ZMusic V2 CE 修复版源码说明

本目录是 ZMusic V2 服务端插件源码整理版，目标是用于 Paper/Purpur/Spigot 系服务端。

## 主要修复内容

- 修复网易云登录、Cookie 保存后仍被判定未登录的问题。
- 增强网易云 Cookie 清洗，避免非法请求头导致插件启动时报错。
- 增加 QQ 音乐、酷狗音乐、酷我音乐搜索源的基础支持。
- 增加 QQ/酷狗/酷我歌词与翻译歌词字段输出。
- 修复部分空播放地址被误判为播放成功的问题。
- 修复 Bukkit 插件消息发送线程问题，避免异步线程直接发客户端播放包。
- 保留 B 站原作者 VIP 转换逻辑，不包含本地 ffmpeg 转码器和 B 站本地转码服务代码。

## 构建方式

推荐使用 Java 21：

```powershell
$env:JAVA_HOME='C:\Program Files\Zulu\zulu-21'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
.\gradlew.bat :zmusic-plugin:shadowJar
```

构建产物：

```text
zmusic-plugin/build/libs/zmusic-plugin-2.12.0-all.jar
```

## 使用注意

- 建议在 `plugins/ZMusic/config.json` 中关闭自动更新：

```json
"checkUpdate": false
```

- 本修复版不会破解官方音乐平台会员/版权限制，只会在账号自身有权限、接口可返回可播放地址时尝试播放。
- B 站相关功能保持原作者 VIP 转换逻辑；本源码不包含内置 ffmpeg，也不包含本地 B 站转码服务。
