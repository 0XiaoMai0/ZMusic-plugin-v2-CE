# 第三方源码

`sources/jaad-0.9.4-sources.jar` 与 `sources/jump3r-1.0.5-sources.jar` 是 Maven Central 提供的原始源码归档。它们供审阅及重新构建内置库使用，**不要放进服务器 plugins 目录**。构建脚本固定依赖版本，并使用 Shadow 重定位类包。

运行 Jar 中保留第三方作者与许可证信息。主插件的源码和构建脚本位于本项目，官方基线仍为 2.14.0。
