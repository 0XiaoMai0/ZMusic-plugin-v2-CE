param(
    [string]$LabDirectory = 'E:\Zhang\ZMusic-Audio-Lab',
    [string]$JavaDirectory = 'C:\Program Files\Zulu\zulu-21'
)
$ErrorActionPreference = 'Stop'
# 测试工具单独下载；服务器插件不包含这些原生文件。
$labPath = [IO.Path]::GetFullPath($LabDirectory)
New-Item -ItemType Directory -Force $labPath, (Join-Path $labPath 'official-core'), (Join-Path $labPath 'official-source') | Out-Null
$modCommit = '1377e445008fe257a349428f33deb9ef43a425d7'
$playerSource = Join-Path $labPath 'official-source\ZMusicPlayer.java'
Invoke-WebRequest "https://raw.githubusercontent.com/starhui-dev/zmusic-mod/$modCommit/zmusic-core/src/main/java/me/zhenxin/zmusic/ZMusicPlayer.java" -OutFile $playerSource
Invoke-WebRequest 'https://repo.maven.apache.org/maven2/org/projectlombok/lombok/1.18.38/lombok-1.18.38.jar' -OutFile (Join-Path $labPath 'lombok.jar')
Invoke-WebRequest 'https://repo.maven.apache.org/maven2/org/apache/logging/log4j/log4j-api/2.26.1/log4j-api-2.26.1.jar' -OutFile (Join-Path $labPath 'log4j-api.jar')
$archive = Join-Path $labPath 'official-player-windows.zip'
Invoke-WebRequest 'https://github.com/starhui-dev/zmusic-player/releases/download/v1.0.0-alpha.7/zmusic-x86_64-windows-msvc-v1.0.0-alpha.7.zip' -OutFile $archive
Expand-Archive -LiteralPath $archive -DestinationPath (Join-Path $labPath 'official-player-windows') -Force
$compiler = Join-Path $JavaDirectory 'bin\javac.exe'
$lombok = Join-Path $labPath 'lombok.jar'
$logging = Join-Path $labPath 'log4j-api.jar'
$classes = Join-Path $labPath 'official-core'
& $compiler --release 8 -encoding UTF-8 -cp "$lombok;$logging" -processorpath $lombok -d $classes $playerSource
if ($LASTEXITCODE -ne 0) { throw '官方模组播放核心编译失败' }
& $compiler --release 8 -encoding UTF-8 -cp "$classes;$logging" -d $classes (Join-Path $PSScriptRoot 'NativeModBridge.java')
if ($LASTEXITCODE -ne 0) { throw 'JNI 验证桥编译失败' }
$env:ZMUSIC_NATIVE_LAB = $labPath
$env:ZMUSIC_TEST_JAVA = Join-Path $JavaDirectory 'bin\java.exe'
Write-Output '模组音频测试环境已准备。先启动测试服务器，再运行 scripts/native-mod-smoke.cjs。'
